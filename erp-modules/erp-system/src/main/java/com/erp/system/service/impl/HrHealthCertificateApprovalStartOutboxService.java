package com.erp.system.service.impl;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.alibaba.fastjson2.JSON;
import com.erp.approval.api.domain.ApprovalStartRequest;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.core.utils.StringUtils;
import com.erp.common.datascope.annotation.DataScope;
import com.erp.system.domain.HrHealthCertificateApprovalStartOutbox;
import com.erp.system.domain.vo.HrHealthCertificateApprovalStartOutboxQuery;
import com.erp.system.domain.vo.HrHealthCertificateApprovalStartOutboxVo;
import com.erp.system.domain.vo.HrHealthCertificateApprovalStartSummaryVo;
import com.erp.system.domain.vo.HrHealthCertificateVo;
import com.erp.system.mapper.HrHealthCertificateApprovalStartOutboxMapper;
import com.erp.system.mapper.HrHealthCertificateMapper;

/** 健康证统一审批发起发件箱的短事务状态服务。 */
@Service
public class HrHealthCertificateApprovalStartOutboxService
{
    public static final String BUSINESS_CODE = "HR_HEALTH_CERTIFICATE";
    public static final String PENDING = "PENDING";
    public static final String SUBMITTING = "SUBMITTING";
    public static final String REMOTE_SUCCEEDED = "REMOTE_SUCCEEDED";
    public static final String RETRY = "RETRY";
    public static final String FAILED = "FAILED";
    public static final String SUCCEEDED = "SUCCEEDED";

    private static final int MAX_BATCH_SIZE = 100;
    private static final int MAX_ERROR_CODE_LENGTH = 64;
    private static final int MAX_ERROR_MESSAGE_LENGTH = 255;
    private static final Set<String> OPS_STATUSES = Set.of(PENDING,
            SUBMITTING, REMOTE_SUCCEEDED, RETRY, FAILED, SUCCEEDED);
    private static final Set<String> CLAIMABLE_STATUSES = Set.of(PENDING,
            SUBMITTING, REMOTE_SUCCEEDED, RETRY);
    private static final Set<String> NON_REPLAYABLE_ERROR_CODES = Set.of(
            "REMOTE_ROUND_MISMATCH", "INSTANCE_CONFLICT",
            "CERTIFICATE_NOT_FOUND", "CERTIFICATE_STATE_CHANGED",
            "CERTIFICATE_VERSION_CONFLICT", "CERTIFICATE_ROUND_CHANGED");

    private final HrHealthCertificateApprovalStartOutboxMapper mapper;
    private final HrHealthCertificateMapper certificateMapper;

    public HrHealthCertificateApprovalStartOutboxService(
            HrHealthCertificateApprovalStartOutboxMapper mapper,
            HrHealthCertificateMapper certificateMapper)
    {
        this.mapper = mapper;
        this.certificateMapper = certificateMapper;
    }

    /** 必须与健康证进入 APPROVAL_SUBMITTING 在同一本地事务内落库。 */
    @Transactional(propagation = Propagation.MANDATORY,
            rollbackFor = Exception.class)
    public HrHealthCertificateApprovalStartOutbox enqueue(
            HrHealthCertificateVo certificate, ApprovalStartRequest request,
            String operator)
    {
        validateEnqueue(certificate, request, operator);
        HrHealthCertificateApprovalStartOutbox row =
                new HrHealthCertificateApprovalStartOutbox();
        row.setCertificateId(certificate.getCertificateId());
        row.setBusinessRound(request.getBusinessRound());
        row.setIdempotencyKey(request.getIdempotencyKey());
        row.setRequestJson(JSON.toJSONString(request));
        row.setStatus(PENDING);
        row.setRetryCount(0);
        row.setCertificateVersion(defaultVersion(certificate.getVersion()));
        row.setVersion(0L);
        row.setCreateBy(safeOperator(operator));
        if (mapper.insertOutbox(row) == 1)
        {
            return row;
        }
        HrHealthCertificateApprovalStartOutbox existing = mapper
                .selectByCertificateRound(certificate.getCertificateId(),
                        request.getBusinessRound());
        if (existing == null
                || !Objects.equals(row.getIdempotencyKey(),
                        existing.getIdempotencyKey())
                || !Objects.equals(row.getCertificateVersion(),
                        existing.getCertificateVersion())
                || !Objects.equals(row.getRequestJson(),
                        existing.getRequestJson()))
        {
            throw new ServiceException("健康证审批发起幂等冲突，请联系管理员");
        }
        return existing;
    }

    public HrHealthCertificateApprovalStartOutbox selectById(Long outboxId)
    {
        return outboxId == null ? null : mapper.selectById(outboxId);
    }

    public HrHealthCertificateApprovalStartOutbox selectByCertificateRound(
            Long certificateId, Integer businessRound)
    {
        if (certificateId == null || businessRound == null)
        {
            return null;
        }
        return mapper.selectByCertificateRound(certificateId, businessRound);
    }

    public List<HrHealthCertificateApprovalStartOutbox> selectDue(Date dueTime,
            Date staleSubmittingBefore, int limit)
    {
        if (dueTime == null || staleSubmittingBefore == null)
        {
            throw new ServiceException("健康证审批发起调度时间不能为空");
        }
        return mapper.selectDueOutboxes(dueTime, staleSubmittingBefore,
                Math.max(1, Math.min(limit, MAX_BATCH_SIZE)));
    }

    @DataScope(deptAlias = "d")
    public List<HrHealthCertificateApprovalStartOutboxVo> selectOps(
            HrHealthCertificateApprovalStartOutboxQuery query)
    {
        requireScopedQuery(query);
        query.setStatus(normalizeStatus(query.getStatus()));
        List<HrHealthCertificateApprovalStartOutboxVo> rows = mapper
                .selectOpsOutboxes(query);
        return rows == null ? Collections.emptyList() : rows;
    }

    @DataScope(deptAlias = "d")
    public HrHealthCertificateApprovalStartSummaryVo selectSummary(
            HrHealthCertificateApprovalStartOutboxQuery query)
    {
        requireScopedQuery(query);
        HrHealthCertificateApprovalStartSummaryVo summary = mapper
                .selectSummary(query);
        return summary == null
                ? new HrHealthCertificateApprovalStartSummaryVo() : summary;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW,
            rollbackFor = Exception.class)
    public boolean claim(HrHealthCertificateApprovalStartOutbox row)
    {
        if (row == null || row.getOutboxId() == null
                || row.getVersion() == null
                || StringUtils.isBlank(row.getStatus())
                || !CLAIMABLE_STATUSES.contains(row.getStatus()))
        {
            return false;
        }
        if (mapper.claimForSubmitting(row.getOutboxId(), row.getStatus(),
                row.getVersion()) != 1)
        {
            return false;
        }
        row.setStatus(SUBMITTING);
        row.setVersion(row.getVersion() + 1);
        return true;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW,
            rollbackFor = Exception.class)
    public void recordRemoteSucceeded(
            HrHealthCertificateApprovalStartOutbox row, Long instanceId,
            String remoteStatus, Integer remoteBusinessRound,
            Integer httpStatus)
    {
        if (row == null || instanceId == null || instanceId <= 0)
        {
            throw new ServiceException("审批中心成功结果不完整");
        }
        String safeRemoteStatus = truncate(remoteStatus, 32);
        assertUpdated(mapper.markRemoteSucceeded(row.getOutboxId(),
                row.getStatus(), row.getVersion(), instanceId,
                safeRemoteStatus, remoteBusinessRound, httpStatus));
        row.setStatus(REMOTE_SUCCEEDED);
        row.setRemoteInstanceId(instanceId);
        row.setRemoteStatus(safeRemoteStatus);
        row.setRemoteBusinessRound(remoteBusinessRound);
        row.setLastHttpStatus(httpStatus);
        row.setVersion(row.getVersion() + 1);
    }

    /** 业务关联与 outbox 完成同成同败，且事务内不调用远端。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW,
            rollbackFor = Exception.class)
    public void finalizeRemoteSuccess(
            HrHealthCertificateApprovalStartOutbox candidate,
            String operator)
    {
        if (candidate == null || candidate.getOutboxId() == null)
        {
            throw new PermanentFailure("OUTBOX_NOT_FOUND",
                    "健康证审批发起发件箱不存在");
        }
        HrHealthCertificateApprovalStartOutbox row = mapper
                .selectByIdForUpdate(candidate.getOutboxId());
        if (row == null)
        {
            throw new PermanentFailure("OUTBOX_NOT_FOUND",
                    "健康证审批发起发件箱不存在");
        }
        if (SUCCEEDED.equals(row.getStatus()))
        {
            syncCandidate(candidate, row);
            return;
        }
        if (!SUBMITTING.equals(row.getStatus())
                && !REMOTE_SUCCEEDED.equals(row.getStatus()))
        {
            throw new ServiceException("健康证审批发起状态已变化，等待下轮调度");
        }
        if (row.getRemoteInstanceId() == null
                || row.getRemoteInstanceId() <= 0)
        {
            throw new PermanentFailure("REMOTE_INSTANCE_MISSING",
                    "审批中心实例关联缺失");
        }
        if (!Objects.equals(row.getBusinessRound(),
                row.getRemoteBusinessRound()))
        {
            throw new PermanentFailure("REMOTE_ROUND_MISMATCH",
                    "审批中心实例业务轮次与健康证轮次不一致");
        }

        HrHealthCertificateVo certificate = certificateMapper
                .selectByIdForUpdate(row.getCertificateId());
        assertCertificateCanFinalize(row, certificate);
        if ("APPROVAL_SUBMITTING".equals(certificate.getReviewStatus()))
        {
            if (certificate.getApprovalInstanceId() != null)
            {
                throw new PermanentFailure("INSTANCE_CONFLICT",
                        "健康证待关联状态已存在审批实例");
            }
            if (!Objects.equals(row.getCertificateVersion(),
                    certificate.getVersion()))
            {
                throw new PermanentFailure("CERTIFICATE_VERSION_CONFLICT",
                        "健康证版本已不是审批发起快照版本");
            }
            if (certificateMapper.markApprovalPending(row.getCertificateId(),
                    row.getCertificateVersion(), row.getRemoteInstanceId(),
                    row.getBusinessRound(), safeOperator(operator)) != 1)
            {
                throw new ServiceException("健康证审批关联并发冲突，等待下轮调度");
            }
        }
        else if (!Objects.equals(certificate.getApprovalInstanceId(),
                row.getRemoteInstanceId()))
        {
            throw new PermanentFailure("INSTANCE_CONFLICT",
                    "健康证已关联其他审批实例");
        }

        assertUpdated(mapper.markSucceeded(row.getOutboxId(),
                row.getStatus(), row.getVersion()));
        row.setStatus(SUCCEEDED);
        row.setVersion(row.getVersion() + 1);
        syncCandidate(candidate, row);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW,
            rollbackFor = Exception.class)
    public void markRetry(HrHealthCertificateApprovalStartOutbox row,
            int retryCount, Date nextRetryTime, Integer httpStatus,
            String errorCode, String errorMessage)
    {
        assertUpdated(mapper.markRetry(row.getOutboxId(), row.getStatus(),
                row.getVersion(), retryCount, nextRetryTime, httpStatus,
                truncate(errorCode, MAX_ERROR_CODE_LENGTH),
                truncate(errorMessage, MAX_ERROR_MESSAGE_LENGTH)));
        row.setStatus(RETRY);
        row.setRetryCount(retryCount);
        row.setNextRetryTime(nextRetryTime);
        row.setLastHttpStatus(httpStatus);
        row.setVersion(row.getVersion() + 1);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW,
            rollbackFor = Exception.class)
    public void markFailed(HrHealthCertificateApprovalStartOutbox row,
            Integer httpStatus, String errorCode, String errorMessage)
    {
        assertUpdated(mapper.markFailed(row.getOutboxId(), row.getStatus(),
                row.getVersion(), httpStatus,
                truncate(errorCode, MAX_ERROR_CODE_LENGTH),
                truncate(errorMessage, MAX_ERROR_MESSAGE_LENGTH),
                row.getRemoteInstanceId(), row.getRemoteStatus(),
                row.getRemoteBusinessRound()));
        row.setStatus(FAILED);
        row.setLastHttpStatus(httpStatus);
        row.setVersion(row.getVersion() + 1);
    }

    @DataScope(deptAlias = "d")
    @Transactional(propagation = Propagation.REQUIRES_NEW,
            rollbackFor = Exception.class)
    public void replayFailed(
            HrHealthCertificateApprovalStartOutboxQuery query, Long version,
            String operator)
    {
        requireScopedQuery(query);
        if (query.getOutboxId() == null || version == null
                || StringUtils.isBlank(operator))
        {
            throw new ServiceException("发件箱记录、版本和操作人不能为空");
        }
        HrHealthCertificateApprovalStartOutbox current = mapper
                .selectScopedByIdForUpdate(query);
        assertReplayable(current, version);
        if (mapper.replayFailedScoped(query, version,
                safeOperator(operator)) != 1)
        {
            throw new ServiceException("发件箱状态已变化，请刷新后重试");
        }
    }

    private void validateEnqueue(HrHealthCertificateVo certificate,
            ApprovalStartRequest request, String operator)
    {
        if (certificate == null || certificate.getCertificateId() == null
                || certificate.getVersion() == null || request == null
                || request.getBusinessRound() == null
                || StringUtils.isBlank(request.getIdempotencyKey())
                || StringUtils.isBlank(operator))
        {
            throw new ServiceException("健康证审批发起入队参数不完整");
        }
        String expectedKey = BUSINESS_CODE + ":"
                + certificate.getCertificateId() + ":"
                + request.getBusinessRound();
        if (!BUSINESS_CODE.equals(request.getBusinessCode())
                || !Objects.equals(String.valueOf(
                        certificate.getCertificateId()),
                        request.getBusinessId())
                || !Objects.equals(certificate.getApprovalRound(),
                        request.getBusinessRound())
                || !Objects.equals(certificate.getUserId(),
                        request.getApplicantId())
                || !"APPROVAL_SUBMITTING".equals(
                        certificate.getReviewStatus())
                || !expectedKey.equals(request.getIdempotencyKey()))
        {
            throw new ServiceException("健康证与审批发起快照不一致");
        }
    }

    private void assertCertificateCanFinalize(
            HrHealthCertificateApprovalStartOutbox row,
            HrHealthCertificateVo certificate)
    {
        if (certificate == null)
        {
            throw new PermanentFailure("CERTIFICATE_NOT_FOUND", "健康证不存在");
        }
        if (!Objects.equals(row.getBusinessRound(),
                certificate.getApprovalRound()))
        {
            throw new PermanentFailure("CERTIFICATE_ROUND_CHANGED",
                    "健康证已不属于当前审批轮次");
        }
        if (!"APPROVAL_SUBMITTING".equals(certificate.getReviewStatus())
                && !"APPROVAL_PENDING".equals(
                        certificate.getReviewStatus()))
        {
            throw new PermanentFailure("CERTIFICATE_STATE_CHANGED",
                    "健康证已不处于待发起或审批中状态");
        }
    }

    private String normalizeStatus(String status)
    {
        if (StringUtils.isBlank(status)) return null;
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        if (!OPS_STATUSES.contains(normalized))
        {
            throw new ServiceException("不支持的发件箱状态");
        }
        return normalized;
    }

    private void requireScopedQuery(
            HrHealthCertificateApprovalStartOutboxQuery query)
    {
        if (query == null)
        {
            throw new ServiceException("健康证审批发起查询条件不能为空");
        }
    }

    private void assertReplayable(
            HrHealthCertificateApprovalStartOutbox current, Long version)
    {
        if (current == null)
        {
            throw new ServiceException("发件箱不存在或无权访问", 404);
        }
        if (!FAILED.equals(current.getStatus())
                || !Objects.equals(version, current.getVersion()))
        {
            throw new ServiceException("发件箱状态已变化，请刷新后重试");
        }
        if (current.getLastErrorCode() != null
                && NON_REPLAYABLE_ERROR_CODES.contains(
                        current.getLastErrorCode()))
        {
            throw new ServiceException(
                    "该失败原因禁止普通重放，请人工核验业务与远端实例");
        }
    }

    private void assertUpdated(int affected)
    {
        if (affected != 1)
        {
            throw new ServiceException("健康证审批发起状态已变化，等待下轮调度");
        }
    }

    private static Long defaultVersion(Long version)
    {
        return version == null ? 0L : version;
    }

    private static String safeOperator(String operator)
    {
        String value = StringUtils.isBlank(operator) ? "approval-outbox"
                : operator.trim();
        return truncate(value, 64);
    }

    private static String truncate(String value, int maxLength)
    {
        return value == null || value.length() <= maxLength ? value
                : value.substring(0, maxLength);
    }

    private static void syncCandidate(
            HrHealthCertificateApprovalStartOutbox candidate,
            HrHealthCertificateApprovalStartOutbox source)
    {
        candidate.setStatus(source.getStatus());
        candidate.setVersion(source.getVersion());
        candidate.setRemoteInstanceId(source.getRemoteInstanceId());
        candidate.setRemoteStatus(source.getRemoteStatus());
        candidate.setRemoteBusinessRound(source.getRemoteBusinessRound());
    }

    /** 业务事实已无法自动关联，需要进入运维失败态。 */
    public static class PermanentFailure extends RuntimeException
    {
        private static final long serialVersionUID = 1L;
        private final String errorCode;

        public PermanentFailure(String errorCode, String message)
        {
            super(message);
            this.errorCode = errorCode;
        }

        public String getErrorCode()
        {
            return errorCode;
        }
    }
}
