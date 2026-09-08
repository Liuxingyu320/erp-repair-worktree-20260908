package com.erp.inventory.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.MockedStatic;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.security.shop.ShopScopeService;
import com.erp.common.security.utils.SecurityUtils;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyConfirmationPolicy.Request;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyConfirmationStoredEvent;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyFacts;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyReadFact;
import com.erp.inventory.domain.transfer.InvTransferReceiptGeneratedId;
import com.erp.inventory.domain.vo.InvTransferReceiptDiscrepancyConfirmationCreationVo;
import com.erp.inventory.mapper.InvDeptScopeMapper;
import com.erp.inventory.mapper.InvTransferReceiptDiscrepancyConfirmationMapper;
import com.erp.inventory.mapper.InvTransferReceiptDiscrepancyPlanningMapper;

@DisplayName("V2调拨差异确认唯一写事务")
class InvTransferReceiptDiscrepancyConfirmationCreationServiceTest
{
    private static final String PLAN = "a".repeat(64);
    private static final Instant NOW = Instant.parse(
            "2026-08-02T12:00:00Z");

    @Test
    @DisplayName("按事项锁、权威读取、幂等锁和条件追加固定顺序写入")
    void shouldAppendInFixedLockOrder()
    {
        Fixture fixture = fixture();
        stubBoundary(fixture, fact());
        generated(fixture.writeMapper(), 801L);

        InvTransferReceiptDiscrepancyConfirmationCreationVo result =
                call(fixture, request("confirmed", null));

        assertThat(result.confirmationEventId()).isEqualTo("801");
        assertThat(result.partyRole()).isEqualTo("source");
        assertThat(result.confirmationState()).isEqualTo(
                "awaiting_counterparty");
        assertThat(result.readyForAdjudication()).isFalse();
        assertThat(result.replayed()).isFalse();
        InOrder order = inOrder(fixture.writeMapper(), fixture.readMapper());
        order.verify(fixture.writeMapper()).selectCaseIdForUpdate(700L);
        order.verify(fixture.readMapper()).selectCase(700L, 301L);
        order.verify(fixture.writeMapper())
                .selectByRequestIdForUpdate("confirm-1");
        order.verify(fixture.writeMapper()).insertConfirmationEvent(any(),
                any());
    }

    @Test
    @DisplayName("完全一致请求返回原事件且不重复追加")
    void shouldReplayExactExistingEvent()
    {
        Fixture fixture = fixture();
        InvTransferReceiptDiscrepancyReadFact fact = fact();
        fact.setSourceConfirmation(confirmation());
        stubBoundary(fixture, fact);
        when(fixture.writeMapper().selectByRequestIdForUpdate("confirm-1"))
                .thenReturn(stored("confirmed", null));

        InvTransferReceiptDiscrepancyConfirmationCreationVo result =
                call(fixture, request("confirmed", null));

        assertThat(result.confirmationEventId()).isEqualTo("801");
        assertThat(result.confirmationState()).isEqualTo(
                "awaiting_counterparty");
        assertThat(result.replayed()).isTrue();
        verify(fixture.writeMapper(), never())
                .insertConfirmationEvent(any(), any());
    }

    @Test
    @DisplayName("同一幂等标识被不同载荷占用时拒绝")
    void shouldRejectRequestIdReuseWithDifferentPayload()
    {
        Fixture fixture = fixture();
        stubBoundary(fixture, fact());
        when(fixture.writeMapper().selectByRequestIdForUpdate("confirm-1"))
                .thenReturn(stored("confirmed", null));

        assertThatThrownBy(() -> call(fixture,
                request("disputed", "数量有异议")))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("幂等标识已被不同");

        verify(fixture.writeMapper(), never())
                .insertConfirmationEvent(any(), any());
    }

    @Test
    @DisplayName("条件追加零行时事务失败")
    void shouldFailWhenConditionalInsertWritesZeroRows()
    {
        Fixture fixture = fixture();
        stubBoundary(fixture, fact());

        assertThatThrownBy(() -> call(fixture,
                request("confirmed", null)))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("追加冲突").hasMessageContaining("回滚");
    }

    @Test
    @DisplayName("生成键缺失时事务失败")
    void shouldFailWhenGeneratedKeyIsMissing()
    {
        Fixture fixture = fixture();
        stubBoundary(fixture, fact());
        when(fixture.writeMapper().insertConfirmationEvent(any(), any()))
                .thenReturn(1);

        assertThatThrownBy(() -> call(fixture,
                request("confirmed", null)))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("主键生成失败");
    }

    @Test
    @DisplayName("当前组织不在权威关系链时不读取幂等事件或写入")
    void shouldStopBeforeIdempotencyWhenScopedReadFails()
    {
        Fixture fixture = fixture();
        when(fixture.writeMapper().selectCaseIdForUpdate(700L))
                .thenReturn(700L);

        assertThatThrownBy(() -> call(fixture,
                request("confirmed", null)))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("无权确认");

        verify(fixture.writeMapper(), never())
                .selectByRequestIdForUpdate(any());
        verify(fixture.writeMapper(), never())
                .insertConfirmationEvent(any(), any());
    }

    @Test
    @DisplayName("服务方法拥有本地回滚事务且没有Controller接线")
    void shouldOwnRequiredRollbackTransaction() throws Exception
    {
        Method method =
                InvTransferReceiptDiscrepancyConfirmationCreationService.class
                        .getDeclaredMethod("create", Request.class,
                                Long.class);
        Transactional transaction = method.getAnnotation(
                Transactional.class);

        assertThat(transaction).isNotNull();
        assertThat(transaction.propagation()).isEqualTo(Propagation.REQUIRED);
        assertThat(transaction.rollbackFor()).contains(Exception.class);
    }

    private static Fixture fixture()
    {
        InvTransferReceiptDiscrepancyConfirmationMapper writeMapper = mock(
                InvTransferReceiptDiscrepancyConfirmationMapper.class);
        InvTransferReceiptDiscrepancyPlanningMapper readMapper = mock(
                InvTransferReceiptDiscrepancyPlanningMapper.class);
        InvDeptScopeMapper deptScopeMapper = mock(InvDeptScopeMapper.class);
        ShopScopeService shopScopeService = mock(ShopScopeService.class);
        when(shopScopeService.resolveRequiredShopDept(301L))
                .thenReturn(301L);
        InvTransferReceiptDiscrepancyConfirmationCreationService service =
                new InvTransferReceiptDiscrepancyConfirmationCreationService(
                        writeMapper, readMapper, deptScopeMapper,
                        shopScopeService,
                        Clock.fixed(NOW, ZoneOffset.UTC));
        return new Fixture(service, writeMapper, readMapper);
    }

    private static void stubBoundary(Fixture fixture,
            InvTransferReceiptDiscrepancyReadFact fact)
    {
        when(fixture.writeMapper().selectCaseIdForUpdate(700L))
                .thenReturn(700L);
        when(fixture.readMapper().selectCase(700L, 301L)).thenReturn(fact);
    }

    private static void generated(
            InvTransferReceiptDiscrepancyConfirmationMapper mapper, Long id)
    {
        doAnswer(invocation -> {
            ((InvTransferReceiptGeneratedId) invocation.getArgument(1))
                    .setValue(id);
            return 1;
        }).when(mapper).insertConfirmationEvent(any(), any());
    }

    private static InvTransferReceiptDiscrepancyConfirmationCreationVo call(
            Fixture fixture, Request request)
    {
        try (MockedStatic<SecurityUtils> security =
                mockStatic(SecurityUtils.class))
        {
            security.when(SecurityUtils::getUserId).thenReturn(77L);
            security.when(SecurityUtils::getUsername).thenReturn("source-user");
            return fixture.service().create(request, 301L);
        }
    }

    private static Request request(String decision, String note)
    {
        return new Request("confirm-1", 700L, 0L,
                fingerprint(), decision, note);
    }

    private static InvTransferReceiptDiscrepancyReadFact fact()
    {
        InvTransferReceiptDiscrepancyReadFact value =
                new InvTransferReceiptDiscrepancyReadFact();
        value.setDiscrepancyCaseId(700L);
        value.setReceiptId(600L);
        value.setReceiptAllocationId(601L);
        value.setShipmentId(91L);
        value.setTransferId(900L);
        value.setShipmentAllocationId(81L);
        value.setShipmentDetailId(71L);
        value.setTransferDetailId(11L);
        value.setSourceDeptId(301L);
        value.setTargetDeptId(302L);
        value.setSourceName("苏州门店");
        value.setTargetName("南京门店");
        value.setOrderNo("TF202608020001");
        value.setShipmentNo("TS20260802ABCD");
        value.setReceiptNo("TR20260802ABCD");
        value.setReceiptPlanVersion(PLAN);
        value.setItemType("product");
        value.setItemId(1001L);
        value.setItemCode("SKU-1001");
        value.setItemName("演示商品");
        value.setUnit("件");
        value.setDiscrepancyType("shortage");
        value.setDiscrepancyQuantity(new BigDecimal("1.0000"));
        value.setSourceCostPrice(new BigDecimal("10.000000"));
        value.setDiscrepancyAmount(new BigDecimal("10.000000"));
        value.setDiscrepancyNote("封签完整但箱内短少");
        value.setAttachmentRefs("attachment-1");
        value.setFactFingerprint(fingerprint());
        value.setCaseStatus("awaiting_confirmation");
        value.setCaseVersion(0L);
        value.setCaseCreateBy("receiving-user");
        value.setCaseCreateTime(Date.from(NOW.minusSeconds(600)));
        return value;
    }

    private static String fingerprint()
    {
        return InvTransferReceiptDiscrepancyFacts.fingerprint(PLAN, 81L,
                "shortage", new BigDecimal("1.0000"),
                new BigDecimal("10.000000"),
                new BigDecimal("10.000000"), "封签完整但箱内短少",
                "attachment-1");
    }

    private static InvTransferReceiptDiscrepancyReadFact.Confirmation
            confirmation()
    {
        InvTransferReceiptDiscrepancyReadFact.Confirmation value =
                new InvTransferReceiptDiscrepancyReadFact.Confirmation();
        value.setEventId(801L);
        value.setRequestId("confirm-1");
        value.setCaseVersion(0L);
        value.setFactFingerprint(fingerprint());
        value.setPartyRole("source");
        value.setPartyDeptId(301L);
        value.setDecision("confirmed");
        value.setOperatorUserId(77L);
        value.setOperatorName("source-user");
        value.setCreateTime(Date.from(NOW.minusSeconds(60)));
        return value;
    }

    private static InvTransferReceiptDiscrepancyConfirmationStoredEvent
            stored(String decision, String note)
    {
        InvTransferReceiptDiscrepancyConfirmationStoredEvent value =
                new InvTransferReceiptDiscrepancyConfirmationStoredEvent();
        value.setEventId(801L);
        value.setRequestId("confirm-1");
        value.setCaseId(700L);
        value.setCaseVersion(0L);
        value.setFactFingerprint(fingerprint());
        value.setPartyRole("source");
        value.setPartyDeptId(301L);
        value.setDecision(decision);
        value.setNote(note);
        value.setOperatorUserId(77L);
        value.setOperatorName("source-user");
        value.setCreateTime(Date.from(NOW.minusSeconds(60)));
        return value;
    }

    private record Fixture(
            InvTransferReceiptDiscrepancyConfirmationCreationService service,
            InvTransferReceiptDiscrepancyConfirmationMapper writeMapper,
            InvTransferReceiptDiscrepancyPlanningMapper readMapper)
    {
    }
}
