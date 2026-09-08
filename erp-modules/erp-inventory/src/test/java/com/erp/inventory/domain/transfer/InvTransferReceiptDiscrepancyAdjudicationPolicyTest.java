package com.erp.inventory.domain.transfer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.erp.common.core.exception.ServiceException;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationPolicy.ActionInput;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationPolicy.Actor;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationPolicy.PreparedAdjudication;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationPolicy.Request;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyBoundaryPolicy.Resolved;

@DisplayName("V2调拨差异独立裁决纯计划")
class InvTransferReceiptDiscrepancyAdjudicationPolicyTest
{
    private static final String PLAN = "a".repeat(64);
    private static final Instant CREATED = Instant.parse(
            "2026-08-02T10:00:00Z");
    private static final Instant ADJUDICATED = Instant.parse(
            "2026-08-02T12:00:00Z");
    private static final Actor ADJUDICATOR = new Actor(99L,
            "independent-user", Set.of(
                    InvTransferReceiptDiscrepancyAdjudicationPolicy
                            .REQUIRED_PERMISSION));

    @Test
    @DisplayName("乱序输入形成排序后数量金额守恒的短缺计划")
    void shouldPrepareDeterministicConservedShortagePlan()
    {
        Resolved resolved = resolved(fact());
        Request request = request(resolved, List.of(
                action(2, "responsibility_adjustment", "0.4000",
                        "carrier", "承运责任调整"),
                action(1, "reship", "0.6000", "source", "来源补发")));

        PreparedAdjudication result =
                InvTransferReceiptDiscrepancyAdjudicationPolicy.prepare(
                        request, resolved, ADJUDICATOR, ADJUDICATED);

        assertThat(result.caseId()).isEqualTo(700L);
        assertThat(result.caseVersionBefore()).isEqualTo(3L);
        assertThat(result.caseVersionAfter()).isEqualTo(4L);
        assertThat(result.sourceConfirmationEventId()).isEqualTo(1L);
        assertThat(result.targetConfirmationEventId()).isEqualTo(2L);
        assertThat(result.requiredPermission()).isEqualTo(
                "inv:transfer:discrepancy:adjudicate");
        assertThat(result.caseStatusBefore()).isEqualTo(
                "awaiting_confirmation");
        assertThat(result.planStatus()).isEqualTo(
                "adjudication_planned");
        assertThat(result.planStatus()).isNotEqualTo("resolved");
        assertThat(result.decisionFingerprint()).matches("[a-f0-9]{64}");
        assertThat(result.actions()).extracting(
                InvTransferReceiptDiscrepancyAdjudicationPolicy
                        .PreparedAction::sequence)
                .containsExactly(1, 2);
        assertThat(result.actions()).extracting(
                InvTransferReceiptDiscrepancyAdjudicationPolicy
                        .PreparedAction::amount)
                .containsExactly(new BigDecimal("6.000000"),
                        new BigDecimal("4.000000"));
        assertThat(result.actions()).extracting(
                InvTransferReceiptDiscrepancyAdjudicationPolicy
                        .PreparedAction::coverageKind)
                .containsOnly("resolution");
        assertThat(result.actions()).allSatisfy(action ->
                assertThat(action.executionStatus()).isEqualTo("pending"));
    }

    @Test
    @DisplayName("最后一行确定性吸收六位金额舍入尾差")
    void shouldAssignRoundingRemainderToLastAction()
    {
        InvTransferReceiptDiscrepancyReadFact fact = fact();
        fact.setSourceCostPrice(new BigDecimal("0.333333"));
        fact.setDiscrepancyAmount(new BigDecimal("0.333333"));
        refreshFingerprint(fact);
        Resolved resolved = resolved(fact);
        Request request = request(resolved, List.of(
                action(1, "reship", "0.5000", "source", "补发一半"),
                action(2, "responsibility_adjustment", "0.5000",
                        "carrier", "调整一半")));

        PreparedAdjudication result =
                InvTransferReceiptDiscrepancyAdjudicationPolicy.prepare(
                        request, resolved, ADJUDICATOR, ADJUDICATED);

        assertThat(result.actions()).extracting(
                InvTransferReceiptDiscrepancyAdjudicationPolicy
                        .PreparedAction::amount)
                .containsExactly(new BigDecimal("0.166667"),
                        new BigDecimal("0.166666"));
        assertThat(result.actions().stream()
                .map(InvTransferReceiptDiscrepancyAdjudicationPolicy
                        .PreparedAction::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo(result.discrepancyAmount());
    }

    @Test
    @DisplayName("输入列表顺序不影响动作顺序和决定指纹")
    void shouldIgnoreInputListOrder()
    {
        Resolved resolved = resolved(fact());
        ActionInput first = action(1, "reship", "0.6000", "source",
                "来源补发");
        ActionInput second = action(2, "responsibility_adjustment",
                "0.4000", "carrier", "承运责任调整");

        PreparedAdjudication left = prepare(resolved,
                request(resolved, List.of(second, first)));
        PreparedAdjudication right = prepare(resolved,
                request(resolved, List.of(first, second)));

        assertThat(left.actions()).isEqualTo(right.actions());
        assertThat(left.decisionFingerprint()).isEqualTo(
                right.decisionFingerprint());
    }

    @Test
    @DisplayName("动作说明变化必然改变决定指纹")
    void shouldFingerprintActionNote()
    {
        Resolved resolved = resolved(fact());
        PreparedAdjudication left = prepare(resolved, request(resolved,
                List.of(action(1, "reship", "1.0000", "source",
                        "原说明"))));
        PreparedAdjudication right = prepare(resolved, request(resolved,
                List.of(action(1, "reship", "1.0000", "source",
                        "新说明"))));

        assertThat(left.decisionFingerprint()).isNotEqualTo(
                right.decisionFingerprint());
    }

    @Test
    @DisplayName("缺少独立裁决权限时拒绝形成计划")
    void shouldRequireDedicatedPermission()
    {
        Resolved resolved = resolved(fact());

        assertThatThrownBy(() ->
                InvTransferReceiptDiscrepancyAdjudicationPolicy.prepare(
                        request(resolved, oneReship()), resolved,
                        new Actor(99L, "user", Set.of()), ADJUDICATED))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("请求或权限无效");
    }

    @Test
    @DisplayName("任一双方确认人不得担任本事项裁决人")
    void shouldSeparateAdjudicatorFromBothConfirmers()
    {
        Resolved resolved = resolved(fact());

        assertThatThrownBy(() ->
                InvTransferReceiptDiscrepancyAdjudicationPolicy.prepare(
                        request(resolved, oneReship()), resolved,
                        new Actor(91L, "source-user", Set.of(
                                InvTransferReceiptDiscrepancyAdjudicationPolicy
                                        .REQUIRED_PERMISSION)),
                        ADJUDICATED))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("独立裁决职责不可核验");
    }

    @Test
    @DisplayName("缺少一方当前确认或一方提出异议时拒绝裁决")
    void shouldRequireTwoCurrentConfirmedEvents()
    {
        InvTransferReceiptDiscrepancyReadFact missing = fact();
        missing.setTargetConfirmation(null);
        Resolved missingResolved = resolved(missing);
        assertThatThrownBy(() -> prepare(missingResolved,
                request(missingResolved, oneReship())))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("双方事实确认");

        InvTransferReceiptDiscrepancyReadFact disputed = fact();
        disputed.setTargetConfirmation(confirmation(2L, "target", 302L,
                "disputed", disputed.getFactFingerprint(), 3L, 92L));
        Resolved disputedResolved = resolved(disputed);
        assertThatThrownBy(() -> prepare(disputedResolved,
                request(disputedResolved, oneReship())))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("双方事实确认");
    }

    @Test
    @DisplayName("动作数量没有完整覆盖差异数量时拒绝")
    void shouldRequireFullQuantityConservation()
    {
        Resolved resolved = resolved(fact());

        assertThatThrownBy(() -> prepare(resolved, request(resolved,
                List.of(action(1, "reship", "0.9999", "source",
                        "不足全量")))))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("未完整覆盖");
    }

    @Test
    @DisplayName("受损必须完整处置且责任调整只能作为不超量叠加维度")
    void shouldRequireDamagedResolutionBeforeResponsibilityOverlay()
    {
        InvTransferReceiptDiscrepancyReadFact damaged = fact();
        damaged.setDiscrepancyType("damaged");
        refreshFingerprint(damaged);
        Resolved resolved = resolved(damaged);

        assertThatThrownBy(() -> prepare(resolved, request(resolved,
                List.of(action(1, "responsibility_adjustment", "1.0000",
                        "carrier", "仅调整责任")))))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("处置动作数量未完整覆盖");

        PreparedAdjudication prepared = prepare(resolved,
                request(resolved, List.of(
                        action(1, "damage_write_off", "1.0000",
                                "company", "完整写销受损库存"),
                        action(2, "responsibility_adjustment", "0.4000",
                                "carrier", "调整部分承运责任"))));

        assertThat(prepared.actions()).extracting(
                InvTransferReceiptDiscrepancyAdjudicationPolicy
                        .PreparedAction::coverageKind)
                .containsExactly("resolution", "responsibility");
        assertThat(prepared.actions()).extracting(
                InvTransferReceiptDiscrepancyAdjudicationPolicy
                        .PreparedAction::amount)
                .containsExactly(new BigDecimal("10.000000"),
                        new BigDecimal("4.000000"));

        assertThatThrownBy(() -> prepare(resolved, request(resolved,
                List.of(
                        action(1, "damage_write_off", "1.0000",
                                "company", "完整写销"),
                        action(2, "responsibility_adjustment", "0.6000",
                                "carrier", "第一段责任"),
                        action(3, "responsibility_adjustment", "0.5000",
                                "source", "第二段责任")))))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("责任调整数量超过");
    }

    @Test
    @DisplayName("重复或不连续的动作序号被拒绝")
    void shouldRequireContiguousUniqueSequences()
    {
        Resolved resolved = resolved(fact());

        assertThatThrownBy(() -> prepare(resolved, request(resolved,
                List.of(
                        action(1, "reship", "0.5000", "source", "第一行"),
                        action(1, "responsibility_adjustment", "0.5000",
                                "carrier", "重复序号")))))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("序号、类型、数量或责任方无效");
    }

    @Test
    @DisplayName("动作类型必须与残损或短缺类型兼容")
    void shouldEnforceActionCompatibility()
    {
        InvTransferReceiptDiscrepancyReadFact damaged = fact();
        damaged.setDiscrepancyType("damaged");
        refreshFingerprint(damaged);
        Resolved damagedResolved = resolved(damaged);
        assertThatThrownBy(() -> prepare(damagedResolved,
                request(damagedResolved, oneReship())))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("序号、类型、数量或责任方无效");

        Resolved shortageResolved = resolved(fact());
        assertThatThrownBy(() -> prepare(shortageResolved,
                request(shortageResolved, List.of(action(1,
                        "damage_write_off", "1.0000", "company",
                        "错误动作")))))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("序号、类型、数量或责任方无效");
    }

    @Test
    @DisplayName("陈旧版本或事实指纹的请求被拒绝")
    void shouldRejectStaleRequestBoundary()
    {
        Resolved resolved = resolved(fact());
        Request staleVersion = new Request("adjudicate-0001", 700L, 2L,
                resolved.boundary().factFingerprint(), "独立裁决",
                "evidence-1", oneReship());
        Request staleFingerprint = new Request("adjudicate-0001", 700L,
                3L, "b".repeat(64), "独立裁决", "evidence-1",
                oneReship());

        assertThatThrownBy(() -> prepare(resolved, staleVersion))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("依据已变化");
        assertThatThrownBy(() -> prepare(resolved, staleFingerprint))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("依据已变化");
    }

    @Test
    @DisplayName("空动作类型和责任方按业务边界失败关闭")
    void shouldFailClosedForNullActionFields()
    {
        Resolved resolved = resolved(fact());

        assertThatThrownBy(() -> prepare(resolved, request(resolved,
                List.of(new ActionInput(1, null,
                        new BigDecimal("1.0000"), "source", "空类型")))))
                .isInstanceOf(ServiceException.class);
        assertThatThrownBy(() -> prepare(resolved, request(resolved,
                List.of(new ActionInput(1, "reship",
                        new BigDecimal("1.0000"), null, "空责任方")))))
                .isInstanceOf(ServiceException.class);
    }

    private static PreparedAdjudication prepare(Resolved resolved,
            Request request)
    {
        return InvTransferReceiptDiscrepancyAdjudicationPolicy.prepare(
                request, resolved, ADJUDICATOR, ADJUDICATED);
    }

    private static Request request(Resolved resolved,
            List<ActionInput> actions)
    {
        return new Request("adjudicate-0001",
                resolved.boundary().discrepancyCaseId(),
                resolved.boundary().caseVersion(),
                resolved.boundary().factFingerprint(), "独立裁决",
                "evidence-1", actions);
    }

    private static List<ActionInput> oneReship()
    {
        return List.of(action(1, "reship", "1.0000", "source",
                "来源补发"));
    }

    private static ActionInput action(int sequence, String actionType,
            String quantity, String responsibleParty, String note)
    {
        return new ActionInput(sequence, actionType,
                new BigDecimal(quantity), responsibleParty, note);
    }

    private static Resolved resolved(
            InvTransferReceiptDiscrepancyReadFact fact)
    {
        return InvTransferReceiptDiscrepancyBoundaryPolicy
                .resolveForAdjudication(fact, 700L);
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
        value.setCaseStatus("awaiting_confirmation");
        value.setCaseVersion(3L);
        value.setCaseCreateBy("receiving-user");
        value.setCaseCreateTime(Date.from(CREATED));
        refreshFingerprint(value);
        value.setSourceConfirmation(confirmation(1L, "source", 301L,
                "confirmed", value.getFactFingerprint(), 3L, 91L));
        value.setTargetConfirmation(confirmation(2L, "target", 302L,
                "confirmed", value.getFactFingerprint(), 3L, 92L));
        return value;
    }

    private static void refreshFingerprint(
            InvTransferReceiptDiscrepancyReadFact value)
    {
        value.setFactFingerprint(
                InvTransferReceiptDiscrepancyFacts.fingerprint(PLAN, 81L,
                        value.getDiscrepancyType(),
                        value.getDiscrepancyQuantity(),
                        value.getSourceCostPrice(),
                        value.getDiscrepancyAmount(),
                        value.getDiscrepancyNote(),
                        value.getAttachmentRefs()));
        if (value.getSourceConfirmation() != null)
        {
            value.getSourceConfirmation().setFactFingerprint(
                    value.getFactFingerprint());
        }
        if (value.getTargetConfirmation() != null)
        {
            value.getTargetConfirmation().setFactFingerprint(
                    value.getFactFingerprint());
        }
    }

    private static InvTransferReceiptDiscrepancyReadFact.Confirmation
            confirmation(Long id, String role, Long deptId, String decision,
                    String fingerprint, Long version, Long userId)
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
        value.setOperatorUserId(userId);
        value.setOperatorName(role + "-user");
        value.setCreateTime(Date.from(CREATED.plusSeconds(id)));
        return value;
    }
}
