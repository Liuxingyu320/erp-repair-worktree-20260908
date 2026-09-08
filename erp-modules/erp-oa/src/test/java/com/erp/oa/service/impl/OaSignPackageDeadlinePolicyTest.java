package com.erp.oa.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Date;
import org.junit.jupiter.api.Test;
import com.erp.common.core.exception.ServiceException;
import com.erp.oa.domain.OaSignPackage;
import com.erp.oa.domain.OaSignPlan;

class OaSignPackageDeadlinePolicyTest
{
    private final OaSignPackageDeadlinePolicy policy =
            new OaSignPackageDeadlinePolicy();

    @Test
    void sendDeadlineRequiresOrderedTimesSourceAndOneTo365Days()
    {
        Date sentTime = Date.from(Instant.parse("2026-07-17T02:03:04Z"));
        Date deadline = Date.from(Instant.parse("2026-07-24T15:59:59Z"));

        assertThatCode(() -> policy.validateSendDeadline(
                sentTime, deadline, "PLAN_VERSION", 1)).doesNotThrowAnyException();
        assertThatCode(() -> policy.validateSendDeadline(
                sentTime, deadline, "PLAN_VERSION", 365)).doesNotThrowAnyException();

        assertThatThrownBy(() -> policy.validateSendDeadline(
                sentTime, sentTime, "PLAN_VERSION", 7))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("发送期限策略不完整");
        assertThatThrownBy(() -> policy.validateSendDeadline(
                sentTime, deadline, " ", 7))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("发送期限策略不完整");
        assertThatThrownBy(() -> policy.validateSendDeadline(
                sentTime, deadline, "PLAN_VERSION", 366))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("发送期限策略不完整");
    }

    @Test
    void manualPlanSnapshotUsesShanghaiEndOfDayAndSecondPrecision()
    {
        OaSignPlan plan = new OaSignPlan();
        plan.setStatus("0");
        plan.setSignDeadlineDays(7);

        OaSignPackageDeadlinePolicy.DeadlineSnapshot snapshot =
                policy.manualDeadlineSnapshot(
                        plan, Instant.parse("2026-07-17T02:03:04.987Z"));

        assertThat(snapshot.sentTime())
                .isEqualTo(Date.from(Instant.parse("2026-07-17T02:03:04Z")));
        assertThat(snapshot.signDeadline())
                .isEqualTo(Date.from(Instant.parse("2026-07-24T15:59:59Z")));
        assertThat(snapshot.policySource()).isEqualTo("MANUAL_PLAN_SNAPSHOT");
        assertThat(snapshot.days()).isEqualTo(7);
    }

    @Test
    void manualPlanSnapshotRejectsMissingInactiveOrOutOfRangePlans()
    {
        Instant now = Instant.parse("2026-07-17T02:03:04Z");
        assertThatThrownBy(() -> policy.manualDeadlineSnapshot(null, now))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("1至365天");

        OaSignPlan inactive = new OaSignPlan();
        inactive.setStatus("1");
        inactive.setSignDeadlineDays(7);
        assertThatThrownBy(() -> policy.manualDeadlineSnapshot(inactive, now))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("1至365天");

        OaSignPlan oversized = new OaSignPlan();
        oversized.setStatus("0");
        oversized.setSignDeadlineDays(366);
        assertThatThrownBy(() -> policy.manualDeadlineSnapshot(oversized, now))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("1至365天");
    }

    @Test
    void signingDeadlineMustBeCompleteAndStrictlyAfterTheCurrentTime()
    {
        Date now = Date.from(Instant.parse("2026-07-17T02:03:04Z"));
        OaSignPackage signPackage = deadlinePackage(
                Date.from(Instant.parse("2026-07-17T02:03:05Z")));

        assertThatCode(() -> policy.assertSigningDeadlineOpen(signPackage, now))
                .doesNotThrowAnyException();

        signPackage.setSignDeadline(now);
        assertThatThrownBy(() -> policy.assertSigningDeadlineOpen(signPackage, now))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("已超过签署截止时间");

        signPackage.setSignDeadline(new Date(now.getTime() + 1_000L));
        signPackage.setDeadlinePolicySource(" ");
        assertThatThrownBy(() -> policy.assertSigningDeadlineOpen(signPackage, now))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("期限策略缺失");
    }

    @Test
    void stagedSignatureMayBeCapturedExactlyAtButNotAfterTheDeadline()
    {
        Date deadline = Date.from(Instant.parse("2026-07-17T02:03:04Z"));
        OaSignPackage signPackage = deadlinePackage(deadline);

        assertThatCode(() -> policy.assertStagedSignatureCapturedByDeadline(
                signPackage, deadline)).doesNotThrowAnyException();

        assertThatThrownBy(() -> policy.assertStagedSignatureCapturedByDeadline(
                signPackage, new Date(deadline.getTime() + 1_000L)))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("截止时间前提交");
        assertThatThrownBy(() -> policy.assertStagedSignatureCapturedByDeadline(
                signPackage, null))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("截止时间前提交");
    }

    @Test
    void companyStagePauseExtendsTheDeadlineAndPrefersInitialSignatureTime()
    {
        OaSignPackage signPackage = deadlinePackage(
                Date.from(Instant.parse("2026-07-24T15:59:59Z")));
        signPackage.setSignedTime(Date.from(Instant.parse("2026-07-17T01:00:00Z")));
        signPackage.setInitialSignedTime(Date.from(Instant.parse("2026-07-17T02:00:00Z")));

        Date resumed = policy.resumeEmployeeDeadlineAfterCompanyStage(
                signPackage, Date.from(Instant.parse("2026-07-17T05:00:00Z")));

        assertThat(resumed)
                .isEqualTo(Date.from(Instant.parse("2026-07-24T18:59:59Z")));
    }

    @Test
    void companyStageResumeFailsClosedForMissingSnapshotsAndOverflow()
    {
        OaSignPackage missing = deadlinePackage(
                Date.from(Instant.parse("2026-07-24T15:59:59Z")));
        assertThatThrownBy(() -> policy.resumeEmployeeDeadlineAfterCompanyStage(
                missing, Date.from(Instant.parse("2026-07-17T05:00:00Z"))))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("时间快照不完整");

        OaSignPackage overflow = deadlinePackage(new Date(Long.MAX_VALUE - 1));
        overflow.setInitialSignedTime(new Date(1L));
        assertThatThrownBy(() -> policy.resumeEmployeeDeadlineAfterCompanyStage(
                overflow, new Date(Long.MAX_VALUE)))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("恢复员工签署截止时间失败");
    }

    private OaSignPackage deadlinePackage(Date deadline)
    {
        OaSignPackage value = new OaSignPackage();
        value.setSignDeadline(deadline);
        value.setDeadlinePolicySource("PLAN_VERSION");
        value.setDeadlineDaysSnapshot(7);
        return value;
    }
}
