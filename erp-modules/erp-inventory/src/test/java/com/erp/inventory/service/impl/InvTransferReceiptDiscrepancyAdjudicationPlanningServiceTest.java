package com.erp.inventory.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.security.shop.ShopScopeService;
import com.erp.common.security.utils.SecurityUtils;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationPolicy;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationPolicy.ActionInput;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationPolicy.Actor;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationPolicy.PreparedAdjudication;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationPolicy.Request;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyBoundaryPolicy;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyFacts;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyReadFact;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyStoredAdjudication;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyStoredAdjudicationAction;
import com.erp.inventory.domain.vo.InvTransferReceiptDiscrepancyAdjudicationPlanningVo;
import com.erp.inventory.mapper.InvTransferReceiptDiscrepancyPlanningMapper;
import com.erp.system.api.model.LoginUser;
import com.fasterxml.jackson.databind.ObjectMapper;

@DisplayName("V2调拨差异裁决只读服务")
class InvTransferReceiptDiscrepancyAdjudicationPlanningServiceTest
{
    private static final String PLAN = "a".repeat(64);
    private static final Instant NOW = Instant.parse(
            "2026-08-02T12:00:00Z");
    private static final String PERMISSION =
            InvTransferReceiptDiscrepancyAdjudicationPolicy
                    .REQUIRED_PERMISSION;

    @Test
    @DisplayName("生产构造器明确标记为Spring注入入口")
    void shouldExposeAnExplicitSpringInjectionConstructor() throws Exception
    {
        assertThat(InvTransferReceiptDiscrepancyAdjudicationPlanningService
                .class.getConstructor(
                        InvTransferReceiptDiscrepancyPlanningMapper.class,
                        ShopScopeService.class)
                .isAnnotationPresent(Autowired.class)).isTrue();
    }

    @Test
    @DisplayName("精确权限和组织范围内返回浏览器安全的已保存计划")
    void shouldReturnBrowserSafeScopedPlan() throws Exception
    {
        Fixture fixture = fixture();
        Planned planned = planned();
        when(fixture.scope().resolveScopeDeptIds(300L))
                .thenReturn(List.of(301L, 302L, 301L));
        when(fixture.mapper().selectCaseForAdjudication(700L,
                List.of(301L, 302L))).thenReturn(planned.fact());
        when(fixture.mapper().selectAdjudicationsByCaseId(700L))
                .thenReturn(List.of(planned.stored()));
        when(fixture.mapper().selectAdjudicationActionsByCaseId(700L))
                .thenReturn(List.of(planned.action()));

        InvTransferReceiptDiscrepancyAdjudicationPlanningVo result =
                call(fixture, Set.of(PERMISSION), 300L);

        assertThat(result.workflowState()).isEqualTo(
                "adjudication_planned");
        assertThat(result.readyForAdjudication()).isFalse();
        assertThat(result.adjudicationPlan()).isNotNull();
        assertThat(result.adjudicationPlan().actions()).hasSize(1);
        assertThat(result.adjudicationPlan().actions().get(0).quantity())
                .isEqualTo("1");
        assertThat(result.adjudicationPlan().actions().get(0)
                .coverageKind()).isEqualTo("resolution");
        assertThat(result.generatedAt()).isEqualTo(
                "2026-08-02T12:00:00Z");
        String json = new ObjectMapper().writeValueAsString(result);
        assertThat(json).doesNotContain("sourceCostPrice", "requestId",
                "factFingerprint", "decisionFingerprint",
                "adjudicatorUserId", "requiredPermission");

        InOrder order = inOrder(fixture.scope(), fixture.mapper());
        order.verify(fixture.scope()).resolveScopeDeptIds(300L);
        order.verify(fixture.mapper()).selectCaseForAdjudication(700L,
                List.of(301L, 302L));
        order.verify(fixture.mapper()).selectAdjudicationsByCaseId(700L);
        order.verify(fixture.mapper())
                .selectAdjudicationActionsByCaseId(700L);
    }

    @Test
    @DisplayName("双方确认完成但尚无计划时返回真正可裁决状态")
    void shouldReturnReadyStateWithoutPlan()
    {
        Fixture fixture = fixture();
        InvTransferReceiptDiscrepancyReadFact fact = fact();
        when(fixture.scope().resolveScopeDeptIds(300L))
                .thenReturn(List.of(301L, 302L));
        when(fixture.mapper().selectCaseForAdjudication(700L,
                List.of(301L, 302L))).thenReturn(fact);
        when(fixture.mapper().selectAdjudicationsByCaseId(700L))
                .thenReturn(List.of());
        when(fixture.mapper().selectAdjudicationActionsByCaseId(700L))
                .thenReturn(List.of());

        InvTransferReceiptDiscrepancyAdjudicationPlanningVo result =
                call(fixture, Set.of(PERMISSION), 300L);

        assertThat(result.workflowState()).isEqualTo(
                "ready_for_adjudication");
        assertThat(result.readyForAdjudication()).isTrue();
        assertThat(result.adjudicationPlan()).isNull();
    }

    @Test
    @DisplayName("缺少精确权限时在组织解析和查询前拒绝")
    void shouldRejectMissingPermissionBeforeScopeOrDatabase()
    {
        Fixture fixture = fixture();

        assertThatThrownBy(() -> call(fixture, Set.of("*:*:*"), 300L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("读取权限无效");

        verifyNoInteractions(fixture.scope(), fixture.mapper());
    }

    @Test
    @DisplayName("空或非法组织范围不得形成无约束查询")
    void shouldRejectEmptyOrInvalidScopeBeforeDatabase()
    {
        Fixture empty = fixture();
        when(empty.scope().resolveScopeDeptIds(300L))
                .thenReturn(List.of());
        assertThatThrownBy(() -> call(empty, Set.of(PERMISSION), 300L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("组织范围无效");
        verifyNoInteractions(empty.mapper());

        Fixture invalid = fixture();
        when(invalid.scope().resolveScopeDeptIds(300L))
                .thenReturn(List.of(301L, 0L));
        assertThatThrownBy(() -> call(invalid, Set.of(PERMISSION), 300L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("组织范围无效");
        verifyNoInteractions(invalid.mapper());
    }

    @Test
    @DisplayName("不存在和范围外事项返回相同拒绝结果")
    void shouldNotRevealCaseExistenceOutsideScope()
    {
        Fixture fixture = fixture();
        when(fixture.scope().resolveScopeDeptIds(300L))
                .thenReturn(List.of(301L));

        assertThatThrownBy(() -> call(fixture, Set.of(PERMISSION), 300L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("不存在或当前范围无权读取");
    }

    @Test
    @DisplayName("读取方法固定使用可重复读的只读事务")
    void shouldUseRepeatableReadOnlyTransaction() throws Exception
    {
        Method method =
                InvTransferReceiptDiscrepancyAdjudicationPlanningService.class
                        .getDeclaredMethod("getPlanning", Long.class,
                                Long.class);
        Transactional transaction = method.getAnnotation(
                Transactional.class);

        assertThat(transaction).isNotNull();
        assertThat(transaction.readOnly()).isTrue();
        assertThat(transaction.isolation()).isEqualTo(
                Isolation.REPEATABLE_READ);
    }

    private static Fixture fixture()
    {
        InvTransferReceiptDiscrepancyPlanningMapper mapper = mock(
                InvTransferReceiptDiscrepancyPlanningMapper.class);
        ShopScopeService scope = mock(ShopScopeService.class);
        return new Fixture(
                new InvTransferReceiptDiscrepancyAdjudicationPlanningService(
                        mapper, scope, Clock.fixed(NOW, ZoneOffset.UTC)),
                mapper, scope);
    }

    private static InvTransferReceiptDiscrepancyAdjudicationPlanningVo call(
            Fixture fixture, Set<String> permissions, Long selectedDeptId)
    {
        LoginUser login = new LoginUser();
        login.setUserid(99L);
        login.setUsername("independent-user");
        login.setPermissions(permissions);
        try (MockedStatic<SecurityUtils> security =
                mockStatic(SecurityUtils.class))
        {
            security.when(SecurityUtils::getLoginUser).thenReturn(login);
            security.when(SecurityUtils::getUserId).thenReturn(99L);
            security.when(SecurityUtils::getUsername)
                    .thenReturn("independent-user");
            return fixture.service().getPlanning(700L, selectedDeptId);
        }
    }

    private static Planned planned()
    {
        InvTransferReceiptDiscrepancyReadFact fact = fact();
        PreparedAdjudication prepared =
                InvTransferReceiptDiscrepancyAdjudicationPolicy.prepare(
                        request(),
                        InvTransferReceiptDiscrepancyBoundaryPolicy
                                .resolveForAdjudication(fact, 700L),
                        new Actor(99L, "independent-user",
                                Set.of(PERMISSION)),
                        NOW);
        InvTransferReceiptDiscrepancyStoredAdjudication stored =
                new InvTransferReceiptDiscrepancyStoredAdjudication();
        stored.setAdjudicationId(901L);
        stored.setRequestId(prepared.requestId());
        stored.setCaseId(prepared.caseId());
        stored.setCaseVersionBefore(prepared.caseVersionBefore());
        stored.setCaseVersionAfter(prepared.caseVersionAfter());
        stored.setFactFingerprint(prepared.factFingerprint());
        stored.setSourceConfirmationEventId(
                prepared.sourceConfirmationEventId());
        stored.setTargetConfirmationEventId(
                prepared.targetConfirmationEventId());
        stored.setDiscrepancyType(prepared.discrepancyType());
        stored.setDiscrepancyQuantity(prepared.discrepancyQuantity());
        stored.setSourceCostPrice(prepared.sourceCostPrice());
        stored.setDiscrepancyAmount(prepared.discrepancyAmount());
        stored.setDecisionFingerprint(prepared.decisionFingerprint());
        stored.setAdjudicationNote(prepared.adjudicationNote());
        stored.setEvidenceRefs(prepared.evidenceRefs());
        stored.setRequiredPermission(prepared.requiredPermission());
        stored.setAdjudicatorUserId(prepared.adjudicatorUserId());
        stored.setAdjudicatorName(prepared.adjudicatorName());
        stored.setPlanStatus(prepared.planStatus());
        stored.setCreateTime(Date.from(NOW));

        var source = prepared.actions().get(0);
        InvTransferReceiptDiscrepancyStoredAdjudicationAction action =
                new InvTransferReceiptDiscrepancyStoredAdjudicationAction();
        action.setActionId(902L);
        action.setAdjudicationId(901L);
        action.setCaseId(prepared.caseId());
        action.setSequence(source.sequence());
        action.setActionType(source.actionType());
        action.setCoverageKind(source.coverageKind());
        action.setQuantity(source.quantity());
        action.setAmount(source.amount());
        action.setResponsibleParty(source.responsibleParty());
        action.setNote(source.note());
        action.setExecutionStatus(source.executionStatus());
        fact.setCaseStatus("adjudication_planned");
        fact.setCaseVersion(prepared.caseVersionAfter());
        return new Planned(fact, stored, action);
    }

    private static Request request()
    {
        return new Request("adjudicate-0001", 700L, 3L,
                fingerprint(), "独立裁决", "evidence-1", List.of(
                        new ActionInput(1, "reship",
                                new BigDecimal("1.0000"), "source",
                                "来源补发")));
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
        value.setCaseVersion(3L);
        value.setCaseCreateBy("receiving-user");
        value.setCaseCreateTime(Date.from(NOW.minusSeconds(600)));
        value.setSourceConfirmation(confirmation(1L, "source", 301L,
                91L));
        value.setTargetConfirmation(confirmation(2L, "target", 302L,
                92L));
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
            confirmation(Long id, String role, Long deptId, Long userId)
    {
        InvTransferReceiptDiscrepancyReadFact.Confirmation value =
                new InvTransferReceiptDiscrepancyReadFact.Confirmation();
        value.setEventId(id);
        value.setRequestId("confirm-" + role);
        value.setCaseVersion(3L);
        value.setFactFingerprint(fingerprint());
        value.setPartyRole(role);
        value.setPartyDeptId(deptId);
        value.setDecision("confirmed");
        value.setOperatorUserId(userId);
        value.setOperatorName(role + "-user");
        value.setCreateTime(Date.from(NOW.minusSeconds(id)));
        return value;
    }

    private record Fixture(
            InvTransferReceiptDiscrepancyAdjudicationPlanningService service,
            InvTransferReceiptDiscrepancyPlanningMapper mapper,
            ShopScopeService scope)
    {
    }

    private record Planned(
            InvTransferReceiptDiscrepancyReadFact fact,
            InvTransferReceiptDiscrepancyStoredAdjudication stored,
            InvTransferReceiptDiscrepancyStoredAdjudicationAction action)
    {
    }
}
