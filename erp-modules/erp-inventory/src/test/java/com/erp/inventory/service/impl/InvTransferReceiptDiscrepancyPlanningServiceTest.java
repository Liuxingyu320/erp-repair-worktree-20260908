package com.erp.inventory.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.erp.common.core.context.SecurityContextHolder;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.security.shop.ShopScopeService;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyFacts;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyReadFact;
import com.erp.inventory.domain.vo.InvTransferReceiptDiscrepancyPlanningVo;
import com.erp.inventory.mapper.InvDeptScopeMapper;
import com.erp.inventory.mapper.InvTransferReceiptDiscrepancyPlanningMapper;
import com.fasterxml.jackson.databind.ObjectMapper;

@DisplayName("V2调拨差异权威只读服务")
class InvTransferReceiptDiscrepancyPlanningServiceTest
{
    private static final String PLAN = "a".repeat(64);
    private static final Instant CREATED = Instant.parse(
            "2026-08-02T10:00:00Z");
    private static final Instant GENERATED = Instant.parse(
            "2026-08-02T11:00:00Z");

    private final InvTransferReceiptDiscrepancyPlanningMapper mapper =
            mock(InvTransferReceiptDiscrepancyPlanningMapper.class);
    private final InvDeptScopeMapper deptScopeMapper = mock(
            InvDeptScopeMapper.class);
    private final ShopScopeService shopScopeService = mock(
            ShopScopeService.class);
    private InvTransferReceiptDiscrepancyPlanningService service;

    @BeforeEach
    void setUp()
    {
        SecurityContextHolder.setUserId("7");
        SecurityContextHolder.setUserName("discrepancy-user");
        when(shopScopeService.resolveRequiredShopDept(301L))
                .thenReturn(301L);
        when(shopScopeService.resolveRequiredShopDept(302L))
                .thenReturn(302L);
        when(shopScopeService.resolveRequiredShopDept(999L))
                .thenReturn(999L);
        service = new InvTransferReceiptDiscrepancyPlanningService(mapper,
                deptScopeMapper, shopScopeService,
                Clock.fixed(GENERATED, ZoneOffset.UTC));
    }

    @AfterEach
    void tearDown()
    {
        SecurityContextHolder.remove();
    }

    @Test
    @DisplayName("双方当前确认形成浏览器安全的可裁决投影")
    void shouldReturnReadyBrowserSafeProjection() throws Exception
    {
        InvTransferReceiptDiscrepancyReadFact fact = fact();
        fact.setSourceConfirmation(confirmation(1L, "source", 301L,
                "confirmed", fact.getFactFingerprint(), 0L));
        fact.setTargetConfirmation(confirmation(2L, "target", 302L,
                "confirmed", fact.getFactFingerprint(), 0L));
        when(mapper.selectCase(700L, 301L)).thenReturn(fact);

        InvTransferReceiptDiscrepancyPlanningVo result = service.getPlanning(
                700L, 301L);

        assertThat(result.currentPartyRole()).isEqualTo("source");
        assertThat(result.confirmationState()).isEqualTo(
                "ready_for_adjudication");
        assertThat(result.readyForAdjudication()).isTrue();
        assertThat(result.discrepancyQuantity()).isEqualTo("1");
        assertThat(result.discrepancyAmount()).isEqualTo("10");
        assertThat(result.sourceParty().latestConfirmation()
                .matchesCurrentFact()).isTrue();
        assertThat(result.generatedAt()).isEqualTo(
                "2026-08-02T11:00:00Z");
        String json = new ObjectMapper().writeValueAsString(result);
        assertThat(json).doesNotContain("sourceCostPrice", "requestId",
                "operatorUserId", "caseCreateBy", "10.000000");
    }

    @Test
    @DisplayName("陈旧的对方事件可见但不能形成双方确认")
    void shouldKeepStaleEventVisibleWithoutReadiness()
    {
        InvTransferReceiptDiscrepancyReadFact fact = fact();
        fact.setSourceConfirmation(confirmation(1L, "source", 301L,
                "confirmed", fact.getFactFingerprint(), 0L));
        fact.setTargetConfirmation(confirmation(2L, "target", 302L,
                "confirmed", "b".repeat(64), 0L));
        when(mapper.selectCase(700L, 302L)).thenReturn(fact);

        InvTransferReceiptDiscrepancyPlanningVo result = service.getPlanning(
                700L, 302L);

        assertThat(result.currentPartyRole()).isEqualTo("target");
        assertThat(result.confirmationState()).isEqualTo(
                "awaiting_counterparty");
        assertThat(result.readyForAdjudication()).isFalse();
        assertThat(result.targetParty().latestConfirmation()
                .matchesCurrentFact()).isFalse();
    }

    @Test
    @DisplayName("事项不存在和组织不匹配使用同一拒绝结果")
    void shouldNotRevealCaseExistenceOutsideExactPartyScope()
    {
        assertThatThrownBy(() -> service.getPlanning(700L, 999L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("不存在或当前组织无权读取");

        verify(mapper).selectCase(700L, 999L);
    }

    @Test
    @DisplayName("金额或指纹被篡改时拒绝返回任何确认投影")
    void shouldRejectForgedAuthoritativeFact()
    {
        InvTransferReceiptDiscrepancyReadFact fact = fact();
        fact.setDiscrepancyAmount(new BigDecimal("9.000000"));
        when(mapper.selectCase(700L, 301L)).thenReturn(fact);

        assertThatThrownBy(() -> service.getPlanning(700L, 301L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("金额或事实指纹不可核验");
    }

    @Test
    @DisplayName("同一组织出现在双方时拒绝形成伪双确认边界")
    void shouldRejectSameOrganizationOnBothSides()
    {
        InvTransferReceiptDiscrepancyReadFact fact = fact();
        fact.setTargetDeptId(301L);
        when(mapper.selectCase(700L, 301L)).thenReturn(fact);

        assertThatThrownBy(() -> service.getPlanning(700L, 301L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("权威事实不可核验");
    }

    @Test
    @DisplayName("无效事项标识在组织解析和查询前拒绝")
    void shouldRejectInvalidIdBeforeScopeOrQuery()
    {
        assertThatThrownBy(() -> service.getPlanning(0L, 301L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("事项标识无效");

        verify(shopScopeService, never()).resolveRequiredShopDept(301L);
        verify(mapper, never()).selectCase(0L, 301L);
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
        value.setFactFingerprint(
                InvTransferReceiptDiscrepancyFacts.fingerprint(PLAN, 81L,
                        "shortage", value.getDiscrepancyQuantity(),
                        value.getSourceCostPrice(),
                        value.getDiscrepancyAmount(),
                        value.getDiscrepancyNote(),
                        value.getAttachmentRefs()));
        value.setCaseStatus("awaiting_confirmation");
        value.setCaseVersion(0L);
        value.setCaseCreateBy("receiving-user");
        value.setCaseCreateTime(Date.from(CREATED));
        return value;
    }

    private static InvTransferReceiptDiscrepancyReadFact.Confirmation
            confirmation(Long id, String role, Long deptId, String decision,
                    String fingerprint, Long version)
    {
        InvTransferReceiptDiscrepancyReadFact.Confirmation value =
                new InvTransferReceiptDiscrepancyReadFact.Confirmation();
        value.setEventId(id);
        value.setRequestId("request-" + id);
        value.setCaseVersion(version);
        value.setFactFingerprint(fingerprint);
        value.setPartyRole(role);
        value.setPartyDeptId(deptId);
        value.setDecision(decision);
        value.setNote("disputed".equals(decision) ? "数量有异议" : null);
        value.setOperatorUserId(90L + id);
        value.setOperatorName(role + "-user");
        value.setCreateTime(Date.from(CREATED.plusSeconds(id)));
        return value;
    }
}
