package com.erp.inventory.domain.transfer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.erp.common.core.exception.ServiceException;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationBasisPolicy.IssuedBasis;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationBasisPolicy.PreparedBasis;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationBasisPolicy.PreparedConsumption;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationBasisPolicy.VerifiedBasis;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationPolicy.Actor;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyBoundaryPolicy.Resolved;

@DisplayName("V2调拨差异裁决不透明依据令牌策略")
class InvTransferReceiptDiscrepancyAdjudicationBasisPolicyTest
{
    private static final String PLAN = "a".repeat(64);
    private static final Instant NOW = Instant.parse(
            "2026-08-03T00:00:00Z");
    private static final String PERMISSION =
            InvTransferReceiptDiscrepancyAdjudicationPolicy
                    .REQUIRED_PERMISSION;

    @Test
    @DisplayName("签发256位不透明令牌且持久化绑定不包含令牌原文")
    void shouldIssueOpaqueTokenWithoutPersistingRawValue()
    {
        IssuedBasis issued = issued();

        assertThat(issued.token())
                .matches("adjb_v1_[A-Za-z0-9_-]{43}");
        assertThat(issued.prepared().tokenHash())
                .matches("[a-f0-9]{64}")
                .isNotEqualTo(issued.token());
        assertThat(issued.prepared().factFingerprint())
                .isEqualTo(fingerprint("封签完整但箱内短少"));
        assertThat(issued.prepared().toString())
                .contains("tokenHash=[REDACTED]",
                        "factFingerprint=[REDACTED]",
                        "scopeDeptIds=[REDACTED]")
                .doesNotContain(issued.token(),
                        issued.prepared().tokenHash(),
                        issued.prepared().factFingerprint(),
                        issued.prepared().scopeDigest());
        assertThat(issued.toString())
                .contains("[REDACTED]")
                .doesNotContain(issued.token());
        assertThat(issued.prepared().expiresAt())
                .isEqualTo(NOW.plusSeconds(300));
        assertThat(issued.prepared().scopeDeptIds())
                .containsExactly(301L, 302L);
    }

    @Test
    @DisplayName("同一上下文可核验且组织范围顺序不影响摘要")
    void shouldVerifyExactContextWithCanonicalScope()
    {
        IssuedBasis issued = issued();
        var verified =
                InvTransferReceiptDiscrepancyAdjudicationBasisPolicy
                        .verifyIssued(issued.token(),
                                stored(issued.prepared()), resolved(fact()),
                                actor(99L), 301L, List.of(302L, 301L),
                                NOW.plusSeconds(60));

        assertThat(verified.basisId()).isEqualTo(801L);
        assertThat(verified.caseId()).isEqualTo(700L);
        assertThat(verified.caseVersion()).isEqualTo(3L);
        assertThat(verified.factFingerprint())
                .isEqualTo(fingerprint("封签完整但箱内短少"));
        assertThat(verified.toString())
                .contains("tokenHash=[REDACTED]",
                        "factFingerprint=[REDACTED]",
                        "scopeDigest=[REDACTED]")
                .doesNotContain(verified.tokenHash(),
                        verified.factFingerprint(),
                        verified.scopeDigest());
    }

    @Test
    @DisplayName("篡改以及跨用户、组织或范围使用全部失败关闭")
    void shouldRejectTamperingAndAccessBoundaryChanges()
    {
        IssuedBasis issued = issued();
        var stored = stored(issued.prepared());
        String token = issued.token();
        char replacement = token.endsWith("A") ? 'B' : 'A';
        String tampered = token.substring(0, token.length() - 1)
                + replacement;

        assertInvalid(() -> verify(tampered, stored, resolved(fact()),
                actor(99L), 301L, List.of(301L, 302L),
                NOW.plusSeconds(1)));
        assertInvalid(() -> verify(token, stored, resolved(fact()),
                actor(100L), 301L, List.of(301L, 302L),
                NOW.plusSeconds(1)));
        assertInvalid(() -> verify(token, stored, resolved(fact()),
                actor(99L), 302L, List.of(301L, 302L),
                NOW.plusSeconds(1)));
        assertInvalid(() -> verify(token, stored, resolved(fact()),
                actor(99L), 301L, List.of(301L, 302L, 303L),
                NOW.plusSeconds(1)));
    }

    @Test
    @DisplayName("跨事项、版本或事实变化使用全部失败关闭")
    void shouldRejectCaseVersionAndFactChanges()
    {
        IssuedBasis issued = issued();
        var stored = stored(issued.prepared());

        assertInvalid(() -> verify(issued.token(), stored,
                resolved(fact(701L, 3L, "封签完整但箱内短少")),
                actor(99L), 301L, List.of(301L, 302L),
                NOW.plusSeconds(1)));
        assertInvalid(() -> verify(issued.token(), stored,
                resolved(fact(700L, 4L, "封签完整但箱内短少")),
                actor(99L), 301L, List.of(301L, 302L),
                NOW.plusSeconds(1)));
        assertInvalid(() -> verify(issued.token(), stored,
                resolved(fact(700L, 3L, "短少事实已重新确认")),
                actor(99L), 301L, List.of(301L, 302L),
                NOW.plusSeconds(1)));
    }

    @Test
    @DisplayName("过期、未来签发或已消费令牌全部失败关闭")
    void shouldRejectExpiredFutureAndConsumedRows()
    {
        IssuedBasis issued = issued();
        var valid = stored(issued.prepared());
        assertInvalid(() -> verify(issued.token(), valid,
                resolved(fact()), actor(99L), 301L,
                List.of(301L, 302L), NOW.plusSeconds(300)));
        assertInvalid(() -> verify(issued.token(), valid,
                resolved(fact()), actor(99L), 301L,
                List.of(301L, 302L), NOW.minusSeconds(1)));

        var consumed = stored(issued.prepared());
        consumed.setBasisStatus(
                InvTransferReceiptDiscrepancyAdjudicationBasisPolicy
                        .CONSUMED_STATUS);
        consumed.setConsumedAt(Date.from(NOW.plusSeconds(30)));
        consumed.setConsumedRequestId("adjudicate-0001");
        consumed.setConsumedAdjudicationId(901L);
        assertInvalid(() -> verify(issued.token(), consumed,
                resolved(fact()), actor(99L), 301L,
                List.of(301L, 302L), NOW.plusSeconds(31)));
    }

    @Test
    @DisplayName("消费准备绑定同一用户请求裁决并严格限制在签发窗口内")
    void shouldPrepareOneExactConsumptionWithinIssueWindow()
    {
        IssuedBasis issued = issued();
        VerifiedBasis verified =
                InvTransferReceiptDiscrepancyAdjudicationBasisPolicy
                        .verifyIssued(issued.token(),
                                stored(issued.prepared()), resolved(fact()),
                                actor(99L), 301L, List.of(301L, 302L),
                                NOW.plusSeconds(1));

        PreparedConsumption consumption =
                InvTransferReceiptDiscrepancyAdjudicationBasisPolicy
                        .prepareConsumption(verified, "adjudicate-0001",
                                901L, NOW.plusSeconds(30), actor(99L));

        assertThat(consumption.basisId()).isEqualTo(801L);
        assertThat(consumption.requestId()).isEqualTo("adjudicate-0001");
        assertThat(consumption.adjudicationId()).isEqualTo(901L);
        assertThat(consumption.toString())
                .contains("tokenHash=[REDACTED]",
                        "factFingerprint=[REDACTED]",
                        "scopeDigest=[REDACTED]")
                .doesNotContain(consumption.tokenHash(),
                        consumption.factFingerprint(),
                        consumption.scopeDigest());
        assertThatThrownBy(() ->
                InvTransferReceiptDiscrepancyAdjudicationBasisPolicy
                        .prepareConsumption(verified, "adjudicate-0001",
                                901L, verified.expiresAt(), actor(99L)))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("消费上下文不可核验");
    }

    @Test
    @DisplayName("已消费令牌仅允许原用户原范围原请求原裁决精确重放")
    void shouldVerifyOnlyExactConsumedReplay()
    {
        IssuedBasis issued = issued();
        var basis = stored(issued.prepared());
        basis.setBasisStatus(
                InvTransferReceiptDiscrepancyAdjudicationBasisPolicy
                        .CONSUMED_STATUS);
        basis.setConsumedAt(Date.from(NOW.plusSeconds(30)));
        basis.setConsumedRequestId("adjudicate-0001");
        basis.setConsumedAdjudicationId(901L);
        var current = fact();
        current.setCaseStatus("adjudication_planned");
        current.setCaseVersion(4L);
        var adjudication = storedAdjudication();

        VerifiedBasis replay =
                InvTransferReceiptDiscrepancyAdjudicationBasisPolicy
                        .verifyConsumedReplay(issued.token(), basis,
                                current, adjudication, actor(99L), 301L,
                                List.of(302L, 301L), "adjudicate-0001");

        assertThat(replay.basisId()).isEqualTo(801L);
        assertInvalid(() ->
                InvTransferReceiptDiscrepancyAdjudicationBasisPolicy
                        .verifyConsumedReplay(issued.token(), basis,
                                current, adjudication, actor(100L), 301L,
                                List.of(301L, 302L), "adjudicate-0001"));
        assertInvalid(() ->
                InvTransferReceiptDiscrepancyAdjudicationBasisPolicy
                        .verifyConsumedReplay(issued.token(), basis,
                                current, adjudication, actor(99L), 301L,
                                List.of(301L, 303L), "adjudicate-0001"));
        assertInvalid(() ->
                InvTransferReceiptDiscrepancyAdjudicationBasisPolicy
                        .verifyConsumedReplay(issued.token(), basis,
                                current, adjudication, actor(99L), 301L,
                                List.of(301L, 302L), "adjudicate-0002"));
        adjudication.setAdjudicationId(902L);
        assertInvalid(() ->
                InvTransferReceiptDiscrepancyAdjudicationBasisPolicy
                        .verifyConsumedReplay(issued.token(), basis,
                                current, adjudication, actor(99L), 301L,
                                List.of(301L, 302L), "adjudicate-0001"));
    }

    @Test
    @DisplayName("签发拒绝确认人代替独立裁决人以及越界组织")
    void shouldRequireIndependentActorAndScopedCase()
    {
        assertThatThrownBy(() ->
                InvTransferReceiptDiscrepancyAdjudicationBasisPolicy.issue(
                        resolved(fact()), actor(91L), 301L,
                        List.of(301L, 302L), NOW, entropy()))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("签发上下文不可核验");
        assertThatThrownBy(() ->
                InvTransferReceiptDiscrepancyAdjudicationBasisPolicy.issue(
                        resolved(fact()), actor(99L), 999L,
                        List.of(999L), NOW, entropy()))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("签发上下文不可核验");
    }

    private static IssuedBasis issued()
    {
        return InvTransferReceiptDiscrepancyAdjudicationBasisPolicy.issue(
                resolved(fact()), actor(99L), 301L,
                List.of(302L, 301L, 302L), NOW, entropy());
    }

    private static byte[] entropy()
    {
        byte[] value = new byte[32];
        for (int index = 0; index < value.length; index++)
        {
            value[index] = (byte) index;
        }
        return value;
    }

    private static Actor actor(Long userId)
    {
        return new Actor(userId, "independent-user",
                Set.of(PERMISSION));
    }

    private static void verify(String token,
            InvTransferReceiptDiscrepancyStoredAdjudicationBasis stored,
            Resolved resolved, Actor actor, Long selectedShopDeptId,
            List<Long> scopeDeptIds, Instant now)
    {
        InvTransferReceiptDiscrepancyAdjudicationBasisPolicy.verifyIssued(
                token, stored, resolved, actor, selectedShopDeptId,
                scopeDeptIds, now);
    }

    private static void assertInvalid(Runnable action)
    {
        assertThatThrownBy(action::run)
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("令牌无效或已过期");
    }

    private static InvTransferReceiptDiscrepancyStoredAdjudicationBasis
            stored(PreparedBasis source)
    {
        var value =
                new InvTransferReceiptDiscrepancyStoredAdjudicationBasis();
        value.setBasisId(801L);
        value.setTokenHash(source.tokenHash());
        value.setCaseId(source.caseId());
        value.setCaseVersion(source.caseVersion());
        value.setFactFingerprint(source.factFingerprint());
        value.setSourceConfirmationEventId(
                source.sourceConfirmationEventId());
        value.setTargetConfirmationEventId(
                source.targetConfirmationEventId());
        value.setSelectedShopDeptId(source.selectedShopDeptId());
        value.setScopeDigest(source.scopeDigest());
        value.setRequiredPermission(source.requiredPermission());
        value.setAdjudicatorUserId(source.adjudicatorUserId());
        value.setAdjudicatorName(source.adjudicatorName());
        value.setBasisStatus(source.basisStatus());
        value.setIssuedAt(Date.from(source.issuedAt()));
        value.setExpiresAt(Date.from(source.expiresAt()));
        return value;
    }

    private static InvTransferReceiptDiscrepancyStoredAdjudication
            storedAdjudication()
    {
        var value = new InvTransferReceiptDiscrepancyStoredAdjudication();
        value.setAdjudicationId(901L);
        value.setRequestId("adjudicate-0001");
        value.setCaseId(700L);
        value.setCaseVersionBefore(3L);
        value.setCaseVersionAfter(4L);
        value.setFactFingerprint(fingerprint("封签完整但箱内短少"));
        value.setSourceConfirmationEventId(1L);
        value.setTargetConfirmationEventId(2L);
        value.setRequiredPermission(PERMISSION);
        value.setAdjudicatorUserId(99L);
        value.setAdjudicatorName("independent-user");
        value.setPlanStatus("adjudication_planned");
        value.setCreateTime(Date.from(NOW.plusSeconds(20)));
        return value;
    }

    private static Resolved resolved(
            InvTransferReceiptDiscrepancyReadFact fact)
    {
        return InvTransferReceiptDiscrepancyBoundaryPolicy
                .resolveForAdjudication(fact,
                        fact.getDiscrepancyCaseId());
    }

    private static InvTransferReceiptDiscrepancyReadFact fact()
    {
        return fact(700L, 3L, "封签完整但箱内短少");
    }

    private static InvTransferReceiptDiscrepancyReadFact fact(Long caseId,
            Long caseVersion, String note)
    {
        var value = new InvTransferReceiptDiscrepancyReadFact();
        value.setDiscrepancyCaseId(caseId);
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
        value.setOrderNo("TF202608030001");
        value.setShipmentNo("TS20260803ABCD");
        value.setReceiptNo("TR20260803ABCD");
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
        value.setDiscrepancyNote(note);
        value.setAttachmentRefs("attachment-1");
        value.setFactFingerprint(fingerprint(note));
        value.setCaseStatus("awaiting_confirmation");
        value.setCaseVersion(caseVersion);
        value.setCaseCreateBy("receiving-user");
        value.setCaseCreateTime(Date.from(NOW.minusSeconds(600)));
        value.setSourceConfirmation(confirmation(1L, "source", 301L,
                91L, caseVersion, note));
        value.setTargetConfirmation(confirmation(2L, "target", 302L,
                92L, caseVersion, note));
        return value;
    }

    private static String fingerprint(String note)
    {
        return InvTransferReceiptDiscrepancyFacts.fingerprint(PLAN, 81L,
                "shortage", new BigDecimal("1.0000"),
                new BigDecimal("10.000000"),
                new BigDecimal("10.000000"), note, "attachment-1");
    }

    private static InvTransferReceiptDiscrepancyReadFact.Confirmation
            confirmation(Long id, String role, Long deptId, Long userId,
                    Long caseVersion, String note)
    {
        var value =
                new InvTransferReceiptDiscrepancyReadFact.Confirmation();
        value.setEventId(id);
        value.setRequestId("confirm-" + role);
        value.setCaseVersion(caseVersion);
        value.setFactFingerprint(fingerprint(note));
        value.setPartyRole(role);
        value.setPartyDeptId(deptId);
        value.setDecision("confirmed");
        value.setOperatorUserId(userId);
        value.setOperatorName(role + "-user");
        value.setCreateTime(Date.from(NOW.minusSeconds(id)));
        return value;
    }
}
