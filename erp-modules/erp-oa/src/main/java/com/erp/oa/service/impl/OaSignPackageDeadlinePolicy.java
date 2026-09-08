package com.erp.oa.service.impl;

import java.time.Instant;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.core.utils.StringUtils;
import com.erp.oa.domain.OaSignPackage;
import com.erp.oa.domain.OaSignPlan;

/**
 * Owns sign-package deadline snapshots and employee signing clock rules.
 *
 * <p>The policy is deterministic for a supplied current instant/time. Loading
 * plans, persisting snapshots and scheduling expiry remain caller concerns.</p>
 */
final class OaSignPackageDeadlinePolicy
{
    void validateSendDeadline(Date sentTime, Date signDeadline,
            String deadlinePolicySource, Integer deadlineDaysSnapshot)
    {
        if (sentTime == null || signDeadline == null || !sentTime.before(signDeadline)
                || StringUtils.isBlank(deadlinePolicySource)
                || deadlineDaysSnapshot == null || deadlineDaysSnapshot < 1
                || deadlineDaysSnapshot > 365)
        {
            throw new ServiceException("签约包发送期限策略不完整");
        }
    }

    DeadlineSnapshot manualDeadlineSnapshot(OaSignPlan plan, Instant currentInstant)
    {
        Integer days = plan == null ? null : plan.getSignDeadlineDays();
        if (plan == null || !"0".equals(plan.getStatus())
                || days == null || days < 1 || days > 365)
        {
            throw new ServiceException("应急手工签约包的签署期限必须在1至365天之间");
        }
        Instant sentInstant = currentInstant.truncatedTo(ChronoUnit.SECONDS);
        Instant deadlineInstant = sentInstant
                .atZone(OaSignTaskWorkflowService.SIGNING_ZONE)
                .toLocalDate().plusDays(days).atTime(LocalTime.of(23, 59, 59))
                .atZone(OaSignTaskWorkflowService.SIGNING_ZONE).toInstant();
        return new DeadlineSnapshot(Date.from(sentInstant), Date.from(deadlineInstant),
                "MANUAL_PLAN_SNAPSHOT", days);
    }

    void assertSigningDeadlineOpen(OaSignPackage signPackage, Date currentTime)
    {
        if (signPackage.getSignDeadline() == null
                || StringUtils.isBlank(signPackage.getDeadlinePolicySource())
                || signPackage.getDeadlineDaysSnapshot() == null
                || signPackage.getDeadlineDaysSnapshot() <= 0)
        {
            throw new ServiceException("签署期限策略缺失，请联系合同经办人");
        }
        if (!signPackage.getSignDeadline().after(currentTime))
        {
            throw new ServiceException("签约包已超过签署截止时间");
        }
    }

    void assertStagedSignatureCapturedByDeadline(OaSignPackage signPackage,
            Date signatureCapturedTime)
    {
        if (signPackage.getSignDeadline() == null
                || StringUtils.isBlank(signPackage.getDeadlinePolicySource())
                || signPackage.getDeadlineDaysSnapshot() == null
                || signPackage.getDeadlineDaysSnapshot() <= 0)
        {
            throw new ServiceException("签署期限策略缺失，请联系合同经办人");
        }
        if (signatureCapturedTime == null
                || signatureCapturedTime.after(signPackage.getSignDeadline()))
        {
            throw new ServiceException("员工未在签署截止时间前提交手写签名");
        }
    }

    Date resumeEmployeeDeadlineAfterCompanyStage(OaSignPackage signPackage,
            Date resumedTime)
    {
        if (signPackage == null || resumedTime == null
                || signPackage.getSignDeadline() == null
                || StringUtils.isBlank(signPackage.getDeadlinePolicySource())
                || signPackage.getDeadlineDaysSnapshot() == null
                || signPackage.getDeadlineDaysSnapshot() <= 0)
        {
            throw new ServiceException("签署期限策略缺失，无法恢复员工签署时钟");
        }
        Date pausedTime = signPackage.getInitialSignedTime() == null
                ? signPackage.getSignedTime() : signPackage.getInitialSignedTime();
        if (pausedTime == null || resumedTime.before(pausedTime)
                || !signPackage.getSignDeadline().after(pausedTime))
        {
            throw new ServiceException("公司处理阶段的签署时间快照不完整");
        }
        try
        {
            long pausedMillis = Math.subtractExact(
                    resumedTime.getTime(), pausedTime.getTime());
            return new Date(Math.addExact(
                    signPackage.getSignDeadline().getTime(), pausedMillis));
        }
        catch (ArithmeticException exception)
        {
            throw new ServiceException("恢复员工签署截止时间失败")
                    .setDetailMessage(exception.getMessage());
        }
    }

    record DeadlineSnapshot(Date sentTime, Date signDeadline,
            String policySource, Integer days) {}
}
