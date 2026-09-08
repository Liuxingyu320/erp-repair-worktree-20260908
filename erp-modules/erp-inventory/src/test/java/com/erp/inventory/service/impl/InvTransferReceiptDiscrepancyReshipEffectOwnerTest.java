package com.erp.inventory.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.erp.common.core.exception.ServiceException;
import com.erp.inventory.domain.InvTransferDetail;
import com.erp.inventory.domain.InvTransferOrder;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationAsyncWorkflowChildFact;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationAsyncWorkflowLink;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationAsyncWorkflowPolicy;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationAsyncWorkflowPolicy.PreparedLink;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationExecutionPolicy;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationExecutionPolicy.Actor;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyReshipFact;
import com.erp.inventory.mapper.InvTransferReceiptDiscrepancyAdjudicationAsyncWorkflowMapper;
import com.erp.inventory.mapper.InvTransferReceiptDiscrepancyReshipMapper;
import com.erp.inventory.service.IInvTransferService;

@DisplayName("V2调拨差异裁决补发子调拨原子效果所有者")
class InvTransferReceiptDiscrepancyReshipEffectOwnerTest
{
    private static final Instant NOW = Instant.parse(
            "2026-08-03T08:00:00Z");
    private static final String REQUEST_ID = "reship-dispatch-0001";
    private static final String PERMISSION =
            InvTransferReceiptDiscrepancyAdjudicationExecutionPolicy
                    .REQUIRED_PERMISSION;

    @Test
    @DisplayName("严格按关系锁来源锁标准提交回读冻结和关系追加顺序执行")
    void shouldDispatchInFixedAtomicOrder()
    {
        Fixture fixture = fixture();
        InvTransferReceiptDiscrepancyReshipFact fact = fact();
        InvTransferOrder submitted = new InvTransferOrder();
        submitted.setTransferId(9101L);
        when(fixture.workflowMapper().selectLinkForUpdate(900L))
                .thenReturn(null);
        when(fixture.reshipMapper().selectSourceForUpdate(
                700L, 5L, 800L, 900L, 0L)).thenReturn(fact);
        when(fixture.transferService().submitTransfer(
                any(), anyList(), eq(302L))).thenReturn(submitted);
        when(fixture.reshipMapper().selectCreatedChildForUpdate(
                900L, 9101L)).thenReturn(child("1.0000"));
        when(fixture.workflowMapper().insertLink(any())).thenReturn(1);

        var result = fixture.owner().dispatch(REQUEST_ID, 700L, 5L,
                800L, 900L, 0L, 302L, actor());

        assertThat(result.replayed()).isFalse();
        assertThat(result.link().childTransferId()).isEqualTo(9101L);
        assertThat(result.link().effectReference())
                .isEqualTo("reship_transfer:9101");
        InOrder order = inOrder(fixture.workflowMapper(),
                fixture.reshipMapper(), fixture.transferService());
        order.verify(fixture.workflowMapper()).selectLinkForUpdate(900L);
        order.verify(fixture.reshipMapper()).selectSourceForUpdate(
                700L, 5L, 800L, 900L, 0L);
        order.verify(fixture.transferService()).submitTransfer(
                any(), anyList(), eq(302L));
        order.verify(fixture.reshipMapper()).selectCreatedChildForUpdate(
                900L, 9101L);
        order.verify(fixture.workflowMapper()).insertLink(any());

        ArgumentCaptor<InvTransferOrder> orderCaptor =
                ArgumentCaptor.forClass(InvTransferOrder.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<InvTransferDetail>> detailCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(fixture.transferService()).submitTransfer(
                orderCaptor.capture(), detailCaptor.capture(), eq(302L));
        assertThat(orderCaptor.getValue().getTransferType())
                .isEqualTo("warehouse");
        assertThat(orderCaptor.getValue().getFromWarehouseId())
                .isEqualTo(301L);
        assertThat(orderCaptor.getValue().getToWarehouseId())
                .isEqualTo(302L);
        assertThat(orderCaptor.getValue().getSourceBusinessType())
                .isEqualTo("transfer_discrepancy_reship");
        assertThat(orderCaptor.getValue().getSourceBusinessId())
                .isEqualTo(900L);
        assertThat(detailCaptor.getValue()).singleElement()
                .satisfies(detail -> {
                    assertThat(detail.getItemType()).isEqualTo("product");
                    assertThat(detail.getItemId()).isEqualTo(4001L);
                    assertThat(detail.getQuantity())
                            .isEqualByComparingTo("1.0000");
                });
    }

    @Test
    @DisplayName("一致关系只读重放且不再创建子调拨")
    void shouldReplayExactStoredLinkWithoutWrites()
    {
        Fixture fixture = fixture();
        PreparedLink prepared = prepared();
        InvTransferReceiptDiscrepancyAdjudicationAsyncWorkflowLink stored =
                mock(InvTransferReceiptDiscrepancyAdjudicationAsyncWorkflowLink
                        .class);
        when(stored.toPolicyLink()).thenReturn(prepared);
        when(fixture.workflowMapper().selectLinkForUpdate(900L))
                .thenReturn(stored);

        var result = fixture.owner().dispatch(REQUEST_ID, 700L, 5L,
                800L, 900L, 0L, 302L, actor());

        assertThat(result.replayed()).isTrue();
        assertThat(result.link()).isEqualTo(prepared);
        verifyNoInteractions(fixture.reshipMapper(),
                fixture.transferService());
        verify(fixture.workflowMapper(), never()).insertLink(any());
    }

    @Test
    @DisplayName("不同请求占用已有关系时在任何调拨写入前失败")
    void shouldRejectConflictingStoredLinkBeforeTransferWrites()
    {
        Fixture fixture = fixture();
        InvTransferReceiptDiscrepancyAdjudicationAsyncWorkflowLink stored =
                mock(InvTransferReceiptDiscrepancyAdjudicationAsyncWorkflowLink
                        .class);
        when(stored.toPolicyLink()).thenReturn(prepared());
        when(fixture.workflowMapper().selectLinkForUpdate(900L))
                .thenReturn(stored);

        assertThatThrownBy(() -> fixture.owner().dispatch(
                "reship-dispatch-0002", 700L, 5L, 800L, 900L, 0L,
                302L, actor()))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("不同请求占用");

        verifyNoInteractions(fixture.reshipMapper(),
                fixture.transferService());
        verify(fixture.workflowMapper(), never()).insertLink(any());
    }

    @Test
    @DisplayName("来源缺失或子调拨冻结事实无效均失败并声明回滚")
    void shouldRejectMissingSourceAndInvalidChildFact()
    {
        Fixture missing = fixture();
        when(missing.workflowMapper().selectLinkForUpdate(900L))
                .thenReturn(null);
        assertThatThrownBy(() -> missing.owner().dispatch(REQUEST_ID,
                700L, 5L, 800L, 900L, 0L, 302L, actor()))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("来源事实不存在");
        verifyNoInteractions(missing.transferService());

        Fixture invalid = fixture();
        stubCreatedTransfer(invalid, child("0.5000"));
        assertThatThrownBy(() -> invalid.owner().dispatch(REQUEST_ID,
                700L, 5L, 800L, 900L, 0L, 302L, actor()))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("创建或冻结事实无效");
        verify(invalid.workflowMapper(), never()).insertLink(any());
    }

    @Test
    @DisplayName("唯一关系非单行时失败并由外层事务回滚")
    void shouldRejectConditionalLinkConflict()
    {
        Fixture fixture = fixture();
        stubCreatedTransfer(fixture, child("1.0000"));
        when(fixture.workflowMapper().insertLink(any())).thenReturn(0);

        assertThatThrownBy(() -> fixture.owner().dispatch(REQUEST_ID,
                700L, 5L, 800L, 900L, 0L, 302L, actor()))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("唯一关系追加冲突")
                .hasMessageContaining("已回滚");
    }

    @Test
    @DisplayName("权限先验校验外层强制事务且生产入口保持零调用")
    void shouldRequirePermissionMandatoryTransactionAndRemainUnwired()
            throws Exception
    {
        Fixture fixture = fixture();
        Actor denied = new Actor(99L, "reship-user", Set.of());
        assertThatThrownBy(() -> fixture.owner().dispatch(REQUEST_ID,
                700L, 5L, 800L, 900L, 0L, 302L, denied))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("执行上下文无效");
        verifyNoInteractions(fixture.workflowMapper(),
                fixture.reshipMapper(), fixture.transferService());

        Method method = InvTransferReceiptDiscrepancyReshipEffectOwner.class
                .getMethod("dispatch", String.class, Long.class,
                        Long.class, Long.class, Long.class, Long.class,
                        Long.class, Actor.class);
        Transactional transaction = method.getAnnotation(
                Transactional.class);
        assertThat(transaction).isNotNull();
        assertThat(transaction.propagation()).isEqualTo(
                Propagation.MANDATORY);
        assertThat(List.of(transaction.rollbackFor()))
                .contains(Exception.class);
        assertThat(productionReferences()).containsExactly(
                Path.of("service", "impl",
                        "InvTransferReceiptDiscrepancyReshipEffectOwner.java"));
    }

    private static void stubCreatedTransfer(Fixture fixture,
            InvTransferReceiptDiscrepancyAdjudicationAsyncWorkflowChildFact
                    child)
    {
        InvTransferOrder submitted = new InvTransferOrder();
        submitted.setTransferId(9101L);
        when(fixture.workflowMapper().selectLinkForUpdate(900L))
                .thenReturn(null);
        when(fixture.reshipMapper().selectSourceForUpdate(
                700L, 5L, 800L, 900L, 0L)).thenReturn(fact());
        when(fixture.transferService().submitTransfer(
                any(), anyList(), eq(302L))).thenReturn(submitted);
        when(fixture.reshipMapper().selectCreatedChildForUpdate(
                900L, 9101L)).thenReturn(child);
    }

    private static PreparedLink prepared()
    {
        InvTransferReceiptDiscrepancyReshipFact fact = fact();
        return InvTransferReceiptDiscrepancyAdjudicationAsyncWorkflowPolicy
                .prepare(fact.toWorkflowSource(REQUEST_ID, 99L,
                        "reship-user", NOW), child("1.0000").toPolicyChild());
    }

    private static InvTransferReceiptDiscrepancyAdjudicationAsyncWorkflowChildFact
            child(String reservedQuantity)
    {
        var child =
                new InvTransferReceiptDiscrepancyAdjudicationAsyncWorkflowChildFact();
        child.setTransferId(9101L);
        child.setTransferType("warehouse");
        child.setSourceBusinessType("transfer_discrepancy_reship");
        child.setSourceBusinessId(900L);
        child.setSourceLocationDeptId(301L);
        child.setTargetLocationDeptId(302L);
        child.setStatus("submitted");
        child.setDetailCount(1);
        child.setItemType("product");
        child.setItemId(4001L);
        child.setProductId(4001L);
        child.setRequestedQuantity(new BigDecimal("1.0000"));
        child.setDeliveredQuantity(BigDecimal.ZERO);
        child.setReservationKind("available_stock");
        child.setReservationCount(1);
        child.setReservedQuantity(new BigDecimal(reservedQuantity));
        child.setReservationStatus("ACTIVE");
        child.setConsumedQuantity(BigDecimal.ZERO);
        child.setReleasedQuantity(BigDecimal.ZERO);
        child.setV2ShipmentCount(0);
        child.setLegacyShipmentCount(0);
        child.setReceiptCount(0);
        child.setOpenDiscrepancyCount(0);
        return child;
    }

    private static InvTransferReceiptDiscrepancyReshipFact fact()
    {
        var fact = new InvTransferReceiptDiscrepancyReshipFact();
        fact.setCaseId(700L);
        fact.setCaseVersionBefore(5L);
        fact.setAdjudicationId(800L);
        fact.setActionId(900L);
        fact.setActionVersionBefore(0L);
        fact.setDiscrepancyType("shortage");
        fact.setActionType("reship");
        fact.setParentTransferId(600L);
        fact.setParentTransferType("warehouse");
        fact.setFromDeptId(301L);
        fact.setFromWarehouseId(301L);
        fact.setToDeptId(302L);
        fact.setToWarehouseId(302L);
        fact.setReceiptAllocationId(5001L);
        fact.setShipmentAllocationId(5002L);
        fact.setItemType("product");
        fact.setItemId(4001L);
        fact.setProductId(4001L);
        fact.setTrackingPolicy("serial");
        fact.setQuantity(new BigDecimal("1.0000"));
        fact.setSourceCostPrice(new BigDecimal("10.000000"));
        fact.setAmount(new BigDecimal("10.000000"));
        fact.setDecisionFingerprint("a".repeat(64));
        return fact;
    }

    private static Actor actor()
    {
        return new Actor(99L, "reship-user", Set.of(PERMISSION));
    }

    private static Fixture fixture()
    {
        InvTransferReceiptDiscrepancyReshipMapper reshipMapper =
                mock(InvTransferReceiptDiscrepancyReshipMapper.class);
        InvTransferReceiptDiscrepancyAdjudicationAsyncWorkflowMapper
                workflowMapper = mock(
                        InvTransferReceiptDiscrepancyAdjudicationAsyncWorkflowMapper
                                .class);
        IInvTransferService transferService =
                mock(IInvTransferService.class);
        var owner = new InvTransferReceiptDiscrepancyReshipEffectOwner(
                reshipMapper, workflowMapper, transferService,
                Clock.fixed(NOW, ZoneOffset.UTC));
        return new Fixture(owner, reshipMapper, workflowMapper,
                transferService);
    }

    private static List<Path> productionReferences() throws Exception
    {
        String simpleName =
                InvTransferReceiptDiscrepancyReshipEffectOwner.class
                        .getSimpleName();
        Path root = Path.of(System.getProperty("user.dir"), "src", "main",
                "java", "com", "erp", "inventory").normalize();
        try (var paths = Files.walk(root))
        {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> contains(path, simpleName))
                    .map(root::relativize)
                    .toList();
        }
    }

    private static boolean contains(Path path, String value)
    {
        try
        {
            return Files.readString(path, StandardCharsets.UTF_8)
                    .contains(value);
        }
        catch (java.io.IOException error)
        {
            throw new IllegalStateException(error);
        }
    }

    private record Fixture(
            InvTransferReceiptDiscrepancyReshipEffectOwner owner,
            InvTransferReceiptDiscrepancyReshipMapper reshipMapper,
            InvTransferReceiptDiscrepancyAdjudicationAsyncWorkflowMapper
                    workflowMapper,
            IInvTransferService transferService)
    {
    }
}
