package com.erp.inventory.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doReturn;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.erp.common.core.context.SecurityContextHolder;
import com.erp.common.core.exception.ServiceException;
import com.erp.inventory.domain.InvTransferDetail;
import com.erp.inventory.domain.InvTransferOrder;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationAsyncWorkflowChildFact;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationAsyncWorkflowLink;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationAsyncWorkflowPolicy;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationAsyncWorkflowPolicy.PreparedLink;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationExecutionPolicy;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationExecutionPolicy.Actor;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyReturnFact;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyReturnSerialFact;
import com.erp.inventory.domain.transfer.InvTransferReceiptGeneratedId;
import com.erp.inventory.domain.transfer.InvTransferReceiptLocationCandidate;
import com.erp.inventory.domain.transfer.InvTransferReceiptTargetBalance;
import com.erp.inventory.domain.transfer.InvTransferReceiptTargetLot;
import com.erp.inventory.domain.transfer.InvTransferReceiptTargetStock;
import com.erp.inventory.mapper.InvTransferReceiptDiscrepancyAdjudicationAsyncWorkflowMapper;
import com.erp.inventory.mapper.InvTransferReceiptDiscrepancyReturnMapper;

@DisplayName("V2差异裁决退回子调拨与隔离预留原子效果所有者")
class InvTransferReceiptDiscrepancyReturnEffectOwnerTest
{
    private static final Instant NOW = Instant.parse(
            "2026-08-03T09:00:00Z");
    private static final String REQUEST_ID = "return-dispatch-0001";
    private static final String PERMISSION =
            InvTransferReceiptDiscrepancyAdjudicationExecutionPolicy
                    .REQUIRED_PERMISSION;

    @BeforeEach
    void setUp()
    {
        SecurityContextHolder.setUserId("99");
        SecurityContextHolder.setUserName("return-user");
    }

    @AfterEach
    void tearDown()
    {
        SecurityContextHolder.remove();
    }

    @Test
    @DisplayName("按关系来源草稿库存序列号提交回读和关系追加顺序执行")
    void shouldDispatchInFixedAtomicOrder()
    {
        Fixture fixture = fixture();
        stubSuccessfulLotDispatch(fixture);

        var result = fixture.owner().dispatch(REQUEST_ID, 700L, 5L,
                800L, 900L, 0L, 302L, actor());

        assertThat(result.replayed()).isFalse();
        assertThat(result.link().childTransferId()).isEqualTo(9101L);
        assertThat(result.link().effectReference())
                .isEqualTo("return_transfer:9101");
        assertThat(result.link().inventorySource())
                .isEqualTo("quarantine_detail");

        InOrder order = inOrder(fixture.workflowMapper(),
                fixture.returnMapper(), fixture.transferService());
        order.verify(fixture.workflowMapper()).selectLinkForUpdate(900L);
        order.verify(fixture.returnMapper()).selectSourceForUpdate(
                700L, 5L, 800L, 900L, 0L);
        order.verify(fixture.transferService()).saveDraft(
                any(), anyList(), eq(302L));
        order.verify(fixture.returnMapper())
                .selectCreatedDraftDetailForUpdate(900L, 9101L);
        order.verify(fixture.returnMapper()).selectDisposedQuantity(5001L);
        order.verify(fixture.returnMapper()).selectStockForUpdate(any());
        order.verify(fixture.returnMapper()).selectLotForUpdate(any());
        order.verify(fixture.returnMapper()).selectLocationForUpdate(any());
        order.verify(fixture.returnMapper()).selectBalanceForUpdate(any());
        order.verify(fixture.returnMapper())
                .selectEligibleSerialsForUpdate(any());
        order.verify(fixture.returnMapper()).reserveStock(any());
        order.verify(fixture.returnMapper()).reserveBalance(any());
        order.verify(fixture.returnMapper()).insertReservation(any(), any());
        order.verify(fixture.transferService())
                .finalizeAdjudicationReturnSubmission(any(), anyList());
        order.verify(fixture.returnMapper()).selectCreatedChildForUpdate(
                900L, 9101L);
        order.verify(fixture.workflowMapper()).insertLink(any());
        verify(fixture.returnMapper(), never())
                .reserveSerial(any(), any());
        verify(fixture.returnMapper(), never())
                .insertReservationSerial(any(), any(), any());
    }

    @Test
    @DisplayName("序列号退回逐件冻结并在专属预留之后追加单件绑定")
    void shouldReserveEverySelectedSerial()
    {
        Fixture fixture = fixture();
        InvTransferReceiptDiscrepancyReturnFact fact = fact("serial",
                "1.0000");
        stubCommon(fixture, fact,
                List.of(serial(fact, 81L, 82L, "SER-82")));

        fixture.owner().dispatch(REQUEST_ID, 700L, 5L, 800L, 900L,
                0L, 302L, actor());

        InOrder order = inOrder(fixture.returnMapper());
        order.verify(fixture.returnMapper()).reserveStock(any());
        order.verify(fixture.returnMapper()).reserveBalance(any());
        order.verify(fixture.returnMapper()).reserveSerial(any(), any());
        order.verify(fixture.returnMapper()).insertReservation(any(), any());
        order.verify(fixture.returnMapper()).insertReservationSerial(
                any(), any(), eq(9901L));
    }

    @Test
    @DisplayName("一致不可变关系只读重放且不创建草稿或冻结库存")
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
        verifyNoInteractions(fixture.returnMapper(),
                fixture.transferService());
        verify(fixture.workflowMapper(), never()).insertLink(any());
    }

    @Test
    @DisplayName("隔离预留写入或唯一关系冲突均失败并声明外层事务回滚")
    void shouldRejectReservationOrLinkConflict()
    {
        Fixture reservationConflict = fixture();
        stubSuccessfulLotDispatch(reservationConflict);
        doReturn(0).when(reservationConflict.returnMapper())
                .insertReservation(any(), any());
        assertThatThrownBy(() -> reservationConflict.owner().dispatch(
                REQUEST_ID, 700L, 5L, 800L, 900L, 0L, 302L, actor()))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("预留台账写入冲突")
                .hasMessageContaining("已回滚");
        verify(reservationConflict.transferService(), never())
                .finalizeAdjudicationReturnSubmission(any(), anyList());

        Fixture linkConflict = fixture();
        stubSuccessfulLotDispatch(linkConflict);
        when(linkConflict.workflowMapper().insertLink(any())).thenReturn(0);
        assertThatThrownBy(() -> linkConflict.owner().dispatch(REQUEST_ID,
                700L, 5L, 800L, 900L, 0L, 302L, actor()))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("唯一关系追加冲突")
                .hasMessageContaining("已回滚");
    }

    @Test
    @DisplayName("身份权限强制事务和生产零入口保持关闭")
    void shouldRequireIdentityPermissionTransactionAndRemainUnwired()
            throws Exception
    {
        Fixture fixture = fixture();
        Actor denied = new Actor(99L, "return-user", Set.of());
        assertThatThrownBy(() -> fixture.owner().dispatch(REQUEST_ID,
                700L, 5L, 800L, 900L, 0L, 302L, denied))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("执行上下文无效");
        verifyNoInteractions(fixture.workflowMapper(),
                fixture.returnMapper(), fixture.transferService());

        Actor impersonated = new Actor(98L, "other-user",
                Set.of(PERMISSION));
        assertThatThrownBy(() -> fixture.owner().dispatch(REQUEST_ID,
                700L, 5L, 800L, 900L, 0L, 302L, impersonated))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("执行上下文无效");

        Method method = InvTransferReceiptDiscrepancyReturnEffectOwner.class
                .getMethod("dispatch", String.class, Long.class,
                        Long.class, Long.class, Long.class, Long.class,
                        Long.class, Actor.class);
        Transactional transaction = method.getAnnotation(
                Transactional.class);
        assertThat(transaction).isNotNull();
        assertThat(transaction.propagation())
                .isEqualTo(Propagation.MANDATORY);
        assertThat(List.of(transaction.rollbackFor()))
                .contains(Exception.class);
        assertThat(productionReferences(
                InvTransferReceiptDiscrepancyReturnEffectOwner.class
                        .getSimpleName()))
                .containsExactly(Path.of("service", "impl",
                        "InvTransferReceiptDiscrepancyReturnEffectOwner.java"));
        assertThat(productionReferences(
                "finalizeAdjudicationReturnSubmission"))
                .containsExactlyInAnyOrder(
                        Path.of("service", "impl",
                                "InvTransferReceiptDiscrepancyReturnEffectOwner.java"),
                        Path.of("service", "impl",
                                "InvTransferServiceImpl.java"));
    }

    private static void stubSuccessfulLotDispatch(Fixture fixture)
    {
        stubCommon(fixture, fact("lot", "2.0000"), List.of());
    }

    private static void stubCommon(Fixture fixture,
            InvTransferReceiptDiscrepancyReturnFact fact,
            List<InvTransferReceiptDiscrepancyReturnSerialFact> serials)
    {
        InvTransferOrder draft = draft();
        InvTransferOrder submitted = submitted();
        when(fixture.workflowMapper().selectLinkForUpdate(900L))
                .thenReturn(null);
        when(fixture.returnMapper().selectSourceForUpdate(
                700L, 5L, 800L, 900L, 0L)).thenReturn(fact);
        when(fixture.transferService().saveDraft(
                any(), anyList(), eq(302L))).thenReturn(draft);
        when(fixture.returnMapper().selectCreatedDraftDetailForUpdate(
                900L, 9101L)).thenReturn(detail(fact.getQuantity()));
        when(fixture.returnMapper().selectDisposedQuantity(5001L))
                .thenReturn(BigDecimal.ZERO);
        when(fixture.returnMapper().selectStockForUpdate(fact))
                .thenReturn(stock());
        when(fixture.returnMapper().selectLotForUpdate(fact))
                .thenReturn(lot());
        when(fixture.returnMapper().selectLocationForUpdate(fact))
                .thenReturn(location());
        when(fixture.returnMapper().selectBalanceForUpdate(fact))
                .thenReturn(balance());
        when(fixture.returnMapper().selectEligibleSerialsForUpdate(fact))
                .thenReturn(serials);
        when(fixture.returnMapper().reserveStock(any())).thenReturn(1);
        when(fixture.returnMapper().reserveBalance(any())).thenReturn(1);
        when(fixture.returnMapper().reserveSerial(any(), any()))
                .thenReturn(1);
        when(fixture.returnMapper().insertReservation(any(), any()))
                .thenAnswer(call -> {
                    InvTransferReceiptGeneratedId generated = call.getArgument(1);
                    generated.setValue(9901L);
                    return 1;
                });
        when(fixture.returnMapper().insertReservationSerial(
                any(), any(), eq(9901L))).thenReturn(1);
        when(fixture.transferService()
                .finalizeAdjudicationReturnSubmission(any(), anyList()))
                .thenReturn(submitted);
        when(fixture.returnMapper().selectCreatedChildForUpdate(
                900L, 9101L)).thenReturn(child(fact));
        when(fixture.workflowMapper().insertLink(any())).thenReturn(1);
    }

    private static PreparedLink prepared()
    {
        InvTransferReceiptDiscrepancyReturnFact fact = fact("lot",
                "2.0000");
        return InvTransferReceiptDiscrepancyAdjudicationAsyncWorkflowPolicy
                .prepare(fact.toWorkflowSource(REQUEST_ID, 99L,
                        "return-user", NOW), child(fact).toPolicyChild());
    }

    private static InvTransferReceiptDiscrepancyReturnFact fact(
            String trackingPolicy, String quantity)
    {
        InvTransferReceiptDiscrepancyReturnFact value =
                new InvTransferReceiptDiscrepancyReturnFact();
        value.setCaseId(700L);
        value.setCaseVersionBefore(5L);
        value.setAdjudicationId(800L);
        value.setActionId(900L);
        value.setActionVersionBefore(0L);
        value.setDiscrepancyType("damaged");
        value.setActionType("return_to_source");
        value.setParentTransferId(600L);
        value.setParentTransferType("warehouse");
        value.setFromDeptId(301L);
        value.setFromWarehouseId(301L);
        value.setToDeptId(302L);
        value.setToWarehouseId(302L);
        value.setReceiptId(5000L);
        value.setReceiptAllocationId(5001L);
        value.setShipmentId(5002L);
        value.setShipmentAllocationId(5003L);
        value.setTargetWarehouseId(302L);
        value.setItemType("product");
        value.setItemId(4001L);
        value.setProductId(4001L);
        value.setTrackingPolicy(trackingPolicy);
        value.setDamagedQuantity(new BigDecimal("3.0000"));
        value.setQuantity(new BigDecimal(quantity));
        value.setSourceCostPrice(new BigDecimal("10.000000"));
        value.setAmount(new BigDecimal(quantity)
                .multiply(new BigDecimal("10.000000")).setScale(6));
        value.setQuarantineBalanceId(6001L);
        value.setQuarantineLotId(6002L);
        value.setQuarantineLocationId(6003L);
        value.setDecisionFingerprint("a".repeat(64));
        return value;
    }

    private static InvTransferOrder draft()
    {
        InvTransferOrder value = new InvTransferOrder();
        value.setTransferId(9101L);
        value.setStatus("draft");
        value.setTransferType("store_return");
        value.setFromDeptId(302L);
        value.setFromWarehouseId(302L);
        value.setToDeptId(301L);
        value.setToWarehouseId(301L);
        value.setSourceBusinessType("transfer_discrepancy_return");
        value.setSourceBusinessId(900L);
        return value;
    }

    private static InvTransferOrder submitted()
    {
        InvTransferOrder value = draft();
        value.setStatus("submitted");
        value.setApprovalRound(1);
        return value;
    }

    private static InvTransferDetail detail(BigDecimal quantity)
    {
        InvTransferDetail value = new InvTransferDetail();
        value.setDetailId(9102L);
        value.setTransferId(9101L);
        value.setItemType("product");
        value.setItemId(4001L);
        value.setProductId(4001L);
        value.setQuantity(quantity);
        value.setDeliveredQuantity(BigDecimal.ZERO);
        value.setReceivedQuantity(BigDecimal.ZERO);
        return value;
    }

    private static InvTransferReceiptTargetStock stock()
    {
        InvTransferReceiptTargetStock value =
                new InvTransferReceiptTargetStock();
        value.setStockId(7001L);
        value.setItemType("product");
        value.setItemId(4001L);
        value.setProductId(4001L);
        value.setShopDeptId(302L);
        value.setWarehouseId(302L);
        value.setCurrentQuantity(new BigDecimal("10.0000"));
        value.setAvailableQuantity(new BigDecimal("4.0000"));
        value.setLockedQuantity(new BigDecimal("3.0000"));
        value.setQuarantineQuantity(new BigDecimal("3.0000"));
        value.setCostPrice(new BigDecimal("10.000000"));
        value.setTotalCost(new BigDecimal("100.000000"));
        value.setVersion(4L);
        return value;
    }

    private static InvTransferReceiptTargetLot lot()
    {
        InvTransferReceiptTargetLot value =
                new InvTransferReceiptTargetLot();
        value.setLotId(6002L);
        value.setWarehouseId(302L);
        value.setItemType("product");
        value.setItemId(4001L);
        value.setProductId(4001L);
        value.setReceiptDisposition("damaged");
        value.setQcStatus("quarantine");
        value.setLotStatus("active");
        return value;
    }

    private static InvTransferReceiptLocationCandidate location()
    {
        InvTransferReceiptLocationCandidate value =
                new InvTransferReceiptLocationCandidate();
        value.setLocationId(6003L);
        value.setWarehouseId(302L);
        value.setLocationType("quarantine");
        value.setStatus("0");
        value.setVirtualFlag("0");
        return value;
    }

    private static InvTransferReceiptTargetBalance balance()
    {
        InvTransferReceiptTargetBalance value =
                new InvTransferReceiptTargetBalance();
        value.setBalanceId(6001L);
        value.setWarehouseId(302L);
        value.setItemType("product");
        value.setItemId(4001L);
        value.setProductId(4001L);
        value.setLotId(6002L);
        value.setLocationId(6003L);
        value.setCurrentQuantity(new BigDecimal("3.0000"));
        value.setAvailableQuantity(BigDecimal.ZERO);
        value.setLockedQuantity(BigDecimal.ZERO);
        value.setQuarantineQuantity(new BigDecimal("3.0000"));
        value.setCostPrice(new BigDecimal("10.000000"));
        value.setTotalCost(new BigDecimal("30.000000"));
        value.setVersion(5L);
        return value;
    }

    private static InvTransferReceiptDiscrepancyReturnSerialFact serial(
            InvTransferReceiptDiscrepancyReturnFact fact,
            Long receiptSerialId, Long serialId, String serialNo)
    {
        InvTransferReceiptDiscrepancyReturnSerialFact value =
                new InvTransferReceiptDiscrepancyReturnSerialFact();
        value.setReceiptSerialId(receiptSerialId);
        value.setReceiptId(fact.getReceiptId());
        value.setReceiptAllocationId(fact.getReceiptAllocationId());
        value.setShipmentId(fact.getShipmentId());
        value.setShipmentAllocationId(fact.getShipmentAllocationId());
        value.setSerialId(serialId);
        value.setSerialNoSnapshot(serialNo);
        value.setCurrentSerialNo(serialNo);
        value.setDisposition("damaged");
        value.setItemType(fact.getItemType());
        value.setItemId(fact.getItemId());
        value.setCurrentWarehouseId(fact.getTargetWarehouseId());
        value.setCurrentBalanceId(fact.getQuarantineBalanceId());
        value.setCurrentLotId(fact.getQuarantineLotId());
        value.setCurrentLocationId(fact.getQuarantineLocationId());
        value.setReceiptStatusAfter("quarantine");
        value.setCurrentStatus("quarantine");
        return value;
    }

    private static InvTransferReceiptDiscrepancyAdjudicationAsyncWorkflowChildFact
            child(InvTransferReceiptDiscrepancyReturnFact fact)
    {
        var value =
                new InvTransferReceiptDiscrepancyAdjudicationAsyncWorkflowChildFact();
        value.setTransferId(9101L);
        value.setTransferType("store_return");
        value.setSourceBusinessType("transfer_discrepancy_return");
        value.setSourceBusinessId(900L);
        value.setSourceLocationDeptId(302L);
        value.setTargetLocationDeptId(301L);
        value.setStatus("submitted");
        value.setDetailCount(1);
        value.setItemType("product");
        value.setItemId(4001L);
        value.setProductId(4001L);
        value.setRequestedQuantity(fact.getQuantity());
        value.setDeliveredQuantity(BigDecimal.ZERO);
        value.setReservationKind("quarantine_detail");
        value.setReservationCount(1);
        value.setReservedQuantity(fact.getQuantity());
        value.setReservationStatus("ACTIVE");
        value.setConsumedQuantity(BigDecimal.ZERO);
        value.setReleasedQuantity(BigDecimal.ZERO);
        value.setV2ShipmentCount(0);
        value.setLegacyShipmentCount(0);
        value.setReceiptCount(0);
        value.setOpenDiscrepancyCount(0);
        return value;
    }

    private static Actor actor()
    {
        return new Actor(99L, "return-user", Set.of(PERMISSION));
    }

    private static Fixture fixture()
    {
        InvTransferReceiptDiscrepancyReturnMapper returnMapper =
                mock(InvTransferReceiptDiscrepancyReturnMapper.class);
        InvTransferReceiptDiscrepancyAdjudicationAsyncWorkflowMapper
                workflowMapper = mock(
                        InvTransferReceiptDiscrepancyAdjudicationAsyncWorkflowMapper
                                .class);
        InvTransferServiceImpl transferService =
                mock(InvTransferServiceImpl.class);
        var owner = new InvTransferReceiptDiscrepancyReturnEffectOwner(
                returnMapper, workflowMapper, transferService,
                Clock.fixed(NOW, ZoneOffset.UTC));
        return new Fixture(owner, returnMapper, workflowMapper,
                transferService);
    }

    private static List<Path> productionReferences(String token)
            throws Exception
    {
        Path root = Path.of(System.getProperty("user.dir"), "src", "main",
                "java", "com", "erp", "inventory").normalize();
        try (var paths = Files.walk(root))
        {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> contains(path, token))
                    .map(root::relativize).toList();
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
            InvTransferReceiptDiscrepancyReturnEffectOwner owner,
            InvTransferReceiptDiscrepancyReturnMapper returnMapper,
            InvTransferReceiptDiscrepancyAdjudicationAsyncWorkflowMapper
                    workflowMapper,
            InvTransferServiceImpl transferService)
    {
    }
}
