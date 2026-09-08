package com.erp.inventory.domain.transfer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.erp.common.core.exception.ServiceException;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyConfirmationProjection.CaseBoundary;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyConfirmationProjection.Result;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyConfirmationProjection.Snapshot;

@DisplayName("V2调拨差异双方事实确认契约")
class InvTransferReceiptDiscrepancyConfirmationPolicyTest
{
    private static final String HASH = "a".repeat(64);
    private static final Instant NOW = Instant.parse(
            "2026-08-02T11:00:00Z");

    @Test
    @DisplayName("只有双方在同一版本和指纹上确认才可裁决")
    void shouldRequireBothCurrentConfirmationsForAdjudication()
    {
        Result empty = project(null, null);
        Result one = project(snapshot(1L, "source", 301L, "confirmed",
                HASH, 0L), null);
        Result both = project(snapshot(1L, "source", 301L, "confirmed",
                HASH, 0L), snapshot(2L, "target", 302L, "confirmed",
                        HASH, 0L));

        assertThat(empty.confirmationState()).isEqualTo(
                "awaiting_confirmation");
        assertThat(one.confirmationState()).isEqualTo(
                "awaiting_counterparty");
        assertThat(one.readyForAdjudication()).isFalse();
        assertThat(both.confirmationState()).isEqualTo(
                "ready_for_adjudication");
        assertThat(both.readyForAdjudication()).isTrue();
    }

    @Test
    @DisplayName("陈旧版本事件保留审计但不计入当前双方确认")
    void shouldIgnoreStaleEventForCurrentProjection()
    {
        Result result = project(snapshot(1L, "source", 301L, "confirmed",
                HASH, 0L), snapshot(2L, "target", 302L, "confirmed",
                        "b".repeat(64), 0L));

        assertThat(result.confirmationState()).isEqualTo(
                "awaiting_counterparty");
        assertThat(result.target().currentFact()).isFalse();
    }

    @Test
    @DisplayName("任一方当前质疑都会阻断裁决")
    void shouldBlockAdjudicationWhenEitherPartyDisputes()
    {
        Result result = project(snapshot(1L, "source", 301L, "confirmed",
                HASH, 0L), snapshot(2L, "target", 302L, "disputed",
                        HASH, 0L));

        assertThat(result.confirmationState()).isEqualTo("disputed");
        assertThat(result.readyForAdjudication()).isFalse();
    }

    @Test
    @DisplayName("事件角色或组织与固定方向不一致时拒绝投影")
    void shouldRejectEventFromWrongPartyDimension()
    {
        Snapshot forged = snapshot(1L, "source", 302L, "confirmed",
                HASH, 0L);

        assertThatThrownBy(() -> project(forged, null))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("确认事件不可核验");
    }

    @Test
    @DisplayName("服务端从当前组织推导目标方且追加后形成可裁决")
    void shouldDerivePartyAndProjectPreparedEvent()
    {
        InvTransferReceiptDiscrepancyConfirmationPolicy.Prepared prepared =
                InvTransferReceiptDiscrepancyConfirmationPolicy.prepare(
                        request("confirmed", null), boundary(), 302L, 99L,
                        "target-user", NOW,
                        snapshot(1L, "source", 301L, "confirmed", HASH, 0L),
                        null);

        assertThat(prepared.partyRole()).isEqualTo("target");
        assertThat(prepared.partyDeptId()).isEqualTo(302L);
        assertThat(prepared.confirmationStateBefore()).isEqualTo(
                "awaiting_counterparty");
        assertThat(prepared.confirmationStateAfter()).isEqualTo(
                "ready_for_adjudication");
        assertThat(prepared.readyForAdjudicationAfter()).isTrue();
    }

    @Test
    @DisplayName("请求版本或指纹不能替代权威事项事实")
    void shouldRejectStaleCommandFacts()
    {
        InvTransferReceiptDiscrepancyConfirmationPolicy.Request stale =
                new InvTransferReceiptDiscrepancyConfirmationPolicy.Request(
                        "confirm-1", 700L, 1L, HASH, "confirmed", null);

        assertThatThrownBy(() ->
                InvTransferReceiptDiscrepancyConfirmationPolicy.prepare(
                        stale, boundary(), 301L, 98L, "source-user", NOW,
                        null, null))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("确认请求不可核验");
    }

    @Test
    @DisplayName("质疑事实时必须提交说明")
    void shouldRequireNoteForDispute()
    {
        assertThatThrownBy(() ->
                InvTransferReceiptDiscrepancyConfirmationPolicy.prepare(
                        request("disputed", "  "), boundary(), 301L, 98L,
                        "source-user", NOW, null, null))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("必须填写说明");
    }

    @Test
    @DisplayName("同一组织不能兼任调出方和调入方")
    void shouldRejectSameOrganizationOnBothSides()
    {
        CaseBoundary invalid = new CaseBoundary(700L, 0L, HASH,
                "awaiting_confirmation", 301L, 301L);

        assertThatThrownBy(() ->
                InvTransferReceiptDiscrepancyConfirmationPolicy.prepare(
                        request("confirmed", null), invalid, 301L, 98L,
                        "source-user", NOW, null, null))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("确认边界不可核验");
    }

    private static Result project(Snapshot source, Snapshot target)
    {
        return InvTransferReceiptDiscrepancyConfirmationProjection.project(
                boundary(), source, target);
    }

    private static CaseBoundary boundary()
    {
        return new CaseBoundary(700L, 0L, HASH,
                "awaiting_confirmation", 301L, 302L);
    }

    private static InvTransferReceiptDiscrepancyConfirmationPolicy.Request
            request(String decision, String note)
    {
        return new InvTransferReceiptDiscrepancyConfirmationPolicy.Request(
                "confirm-1", 700L, 0L, HASH, decision, note);
    }

    private static Snapshot snapshot(Long eventId, String role, Long deptId,
            String decision, String hash, Long version)
    {
        return new Snapshot(eventId, "request-" + eventId, version, hash,
                role, deptId, decision,
                "disputed".equals(decision) ? "数量有异议" : null,
                90L + eventId, role + "-user", NOW);
    }
}
