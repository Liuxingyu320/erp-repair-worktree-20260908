package com.erp.oa.service.impl;

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
import com.erp.oa.domain.OaReimbursement;
import com.erp.oa.domain.OaReimbursementApprovalStartOutbox;
import com.erp.oa.domain.vo.OaReimbursementApprovalStartOutboxQuery;
import com.erp.oa.domain.vo.OaReimbursementApprovalStartOutboxVo;
import com.erp.oa.domain.vo.OaReimbursementApprovalStartSummaryVo;
import com.erp.oa.mapper.OaReimbursementApprovalStartOutboxMapper;
import com.erp.oa.mapper.OaReimbursementMapper;

/** OA 报销审批发起发件箱的短事务状态服务。 */
@Service
public class OaReimbursementApprovalStartOutboxService
{
    public static final String BUSINESS_CODE = "OA_REIMBURSEMENT";
    public static final String PENDING = "PENDING";
    public static final String SUBMITTING = "SUBMITTING";
    public static final String REMOTE_SUCCEEDED = "REMOTE_SUCCEEDED";
    public static final String RETRY = "RETRY";
    public static final String FAILED = "FAILED";
    public static final String SUCCEEDED = "SUCCEEDED";

    private static final String REIMBURSEMENT_SUBMITTING = "submitting";
    private static final String REIMBURSEMENT_PENDING = "pending";
    private static final int MAX_BATCH_SIZE = 100;
    private static final int MAX_ERROR_CODE_LENGTH = 64;
    private static final int MAX_ERROR_MESSAGE_LENGTH = 255;
    private static final Set<String> OPS_STATUSES = Set.of(PENDING,
            SUBMITTING, REMOTE_SUCCEEDED, RETRY, FAILED, SUCCEEDED);
    private static final Set<String> CLAIMABLE_STATUSES = Set.of(PENDING,
            SUBMITTING, REMOTE_SUCCEEDED, RETRY);
    private static final Set<String> NON_REPLAYABLE_ERROR_CODES = Set.of(
            "REMOTE_ROUND_MISMATCH", "INSTANCE_CONFLICT",
            "REIMBURSEMENT_NOT_FOUND", "REIMBURSEMENT_STATE_CHANGED",
            "REIMBURSEMENT_VERSION_CONFLICT");

    private final OaReimbursementApprovalStartOutboxMapper mapper;
    private final OaReimbursementMapper reimbursementMapper;

    public OaReimbursementApprovalStartOutboxService(
            OaReimbursementApprovalStartOutboxMapper mapper,
            OaReimbursementMapper reimbursementMapper)
    {
        this.mapper = mapper;
        this.reimbursementMapper = reimbursementMapper;
    }

    /** 必须与报销单 SUBMITTING 事实在同一本地事务中落库。 */
    @Transactional(propagation = Propagation.MANDATORY,
            rollbackFor = Exception.class)
    public OaReimbursementApprovalStartOutbox enqueue(OaReimbursement reimbursement,
            ApprovalStartRequest request, String operator)
    {
        validateEnqueue(reimbursement, request, operator);
        String requestJson = JSON.toJSONString(request);
        OaReimbursementApprovalStartOutbox row =
                new OaReimbursementApprovalStartOutbox();
        row.setReimbursementId(reimbursement.getReimbursementId());
        row.setBusinessRound(request.getBusinessRound());
        row.setIdempotencyKey(request.getIdempotencyKey());
        row.setRequestJson(requestJson);
        row.setStatus(PENDING);
        row.setRetryCount(0);
        row.setReimbursementVersion(defaultVersion(reimbursement.getRowVersion()));
        row.setVersion(0L);
        row.setCreateBy(operator.trim());
        if (mapper.insertOutbox(row) == 1)
        {
            return row;
        }
        OaReimbursementApprovalStartOutbox existing = mapper
                .selectByReimbursementRound(reimbursement.getReimbursementId(),
                        request.getBusinessRound());
        if (existing == null
                || !Objects.equals(request.getIdempotencyKey(),
                        existing.getIdempotencyKey())
                || !Objects.equals(requestJson, existing.getRequestJson()))
        {
            throw new ServiceException("报销审批发起幂等冲突，请联系管理员");
        }
        return existing;
    }

    public OaReimbursementApprovalStartOutbox selectById(Long outboxId)
    {
        return outboxId == null ? null : mapper.selectById(outboxId);
    }

    public OaReimbursementApprovalStartOutbox selectByReimbursementRound(
            Long reimbursementId, Integer businessRound)
    {
        if (reimbursementId == null || businessRound == null) return null;
        return mapper.selectByReimbursementRound(reimbursementId, businessRound);
    }

    public List<OaReimbursementApprovalStartOutbox> selectDue(Date dueTime,
            Date staleSubmittingBefore, int limit)
    {
        return mapper.selectDueOutboxes(dueTime, staleSubmittingBefore,
                Math.max(1, Math.min(limit, MAX_BATCH_SIZE)));
    }

    @DataScope(deptAlias = "d")
    public List<OaReimbursementApprovalStartOutboxVo> selectOps(
            OaReimbursementApprovalStartOutboxQuery query)
    {
        requireScopedQuery(query);
        query.setStatus(normalizeStatus(query.getStatus()));
        List<OaReimbursementApprovalStartOutboxVo> rows = mapper
                .selectOpsOutboxes(query);
        return rows == null ? Collections.emptyList() : rows;
    }

    @DataScope(deptAlias = "d")
    public OaReimbursementApprovalStartSummaryVo selectSummary(
            OaReimbursementApprovalStartOutboxQuery query)
    {
        requireScopedQuery(query);
        OaReimbursementApprovalStartSummaryVo summary = mapper
                .selectSummary(query);
        return summary == null ? new OaReimbursementApprovalStartSummaryVo()
                : summary;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW,
            rollbackFor = Exception.class)
    public boolean claim(OaReimbursementApprovalStartOutbox row)
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

    /** 远端成功必须先独立固化，不与后续本地关联共享回滚边界。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW,
            rollbackFor = Exception.class)
    public void recordRemoteSucceeded(OaReimbursementApprovalStartOutbox row,
            Long instanceId, String remoteStatus,
            Integer remoteBusinessRound, Integer httpStatus)
    {
        if (row == null || instanceId == null || instanceId <= 0
                || !Objects.equals(row.getBusinessRound(),
                        remoteBusinessRound))
        {
            throw new ServiceException("审批中心成功结果不完整");
        }
        assertUpdated(mapper.markRemoteSucceeded(row.getOutboxId(),
                row.getStatus(), row.getVersion(), instanceId,
                truncate(remoteStatus, 32), remoteBusinessRound,
                httpStatus));
        row.setStatus(REMOTE_SUCCEEDED);
        row.setRemoteInstanceId(instanceId);
        row.setRemoteStatus(truncate(remoteStatus, 32));
        row.setRemoteBusinessRound(remoteBusinessRound);
        row.setLastHttpStatus(httpStatus);
        row.setVersion(row.getVersion() + 1);
    }

    /** 报销单关联与发件箱完成同成同败，此事务内不调用远端。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW,
            rollbackFor = Exception.class)
    public void finalizeRemoteSuccess(OaReimbursementApprovalStartOutbox candidate,
            String operator)
    {
        if (candidate == null || candidate.getOutboxId() == null)
        {
            throw new PermanentFailure("OUTBOX_NOT_FOUND",
                    "报销审批发起发件箱不存在");
        }
        OaReimbursementApprovalStartOutbox row = mapper.selectByIdForUpdate(
                candidate.getOutboxId());
        if (row == null)
        {
            throw new PermanentFailure("OUTBOX_NOT_FOUND",
                    "报销审批发起发件箱不存在");
        }
        if (SUCCEEDED.equals(row.getStatus()))
        {
            syncCandidate(candidate, row);
            return;
        }
        if (!SUBMITTING.equals(row.getStatus())
                && !REMOTE_SUCCEEDED.equals(row.getStatus()))
        {
            throw new ServiceException("报销审批发起状态已变化，等待下轮调度");
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
                    "审批中心实例业务轮次与报销轮次不一致");
        }

        OaReimbursement reimbursement = reimbursementMapper
                .selectByIdForUpdate(row.getReimbursementId());
        if (reimbursement == null)
        {
            throw new PermanentFailure("REIMBURSEMENT_NOT_FOUND", "报销申请不存在");
        }
        if (reimbursement.getApprovalInstanceId() != null)
        {
            if (!Objects.equals(reimbursement.getApprovalInstanceId(),
                    row.getRemoteInstanceId()))
            {
                throw new PermanentFailure("INSTANCE_CONFLICT",
                        "报销申请已关联其他审批实例");
            }
            if (!REIMBURSEMENT_PENDING.equals(reimbursement.getStatus())
                    || !Objects.equals(reimbursement.getApprovalRound(),
                            row.getBusinessRound()))
            {
                throw new PermanentFailure("REIMBURSEMENT_STATE_CHANGED",
                        "报销申请已不属于当前待关联审批轮次");
            }
        }
        else
        {
            assertReimbursementCanFinalize(row, reimbursement);
            if (reimbursementMapper.finalizeApprovalStart(row.getReimbursementId(),
                    row.getBusinessRound(), row.getReimbursementVersion(),
                    row.getRemoteInstanceId(), safeOperator(operator)) != 1)
            {
                throw new ServiceException("报销单审批关联并发冲突，等待下轮调度");
            }
        }
        assertUpdated(mapper.markSucceeded(row.getOutboxId(),
                row.getStatus(), row.getVersion()));
        row.setStatus(SUCCEEDED);
        row.setVersion(row.getVersion() + 1);
        syncCandidate(candidate, row);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW,
            rollbackFor = Exception.class)
    public void markRetry(OaReimbursementApprovalStartOutbox row,
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
        row.setVersion(row.getVersion() + 1);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW,
            rollbackFor = Exception.class)
    public void markFailed(OaReimbursementApprovalStartOutbox row,
            Integer httpStatus, String errorCode, String errorMessage)
    {
        assertUpdated(mapper.markFailed(row.getOutboxId(), row.getStatus(),
                row.getVersion(), httpStatus,
                truncate(errorCode, MAX_ERROR_CODE_LENGTH),
                truncate(errorMessage, MAX_ERROR_MESSAGE_LENGTH),
                row.getRemoteInstanceId(), row.getRemoteStatus(),
                row.getRemoteBusinessRound()));
        row.setStatus(FAILED);
        row.setVersion(row.getVersion() + 1);
    }

    @DataScope(deptAlias = "d")
    @Transactional(propagation = Propagation.REQUIRES_NEW,
            rollbackFor = Exception.class)
    public void replayFailed(OaReimbursementApprovalStartOutboxQuery query,
            Long version, String operator)
    {
        requireScopedQuery(query);
        if (query.getOutboxId() == null || version == null
                || StringUtils.isBlank(operator))
        {
            throw new ServiceException("发件箱记录、版本和操作人不能为空");
        }
        OaReimbursementApprovalStartOutbox current = mapper
                .selectScopedByIdForUpdate(query);
        assertReplayable(current, version);
        if (mapper.replayFailedScoped(query, version,
                safeOperator(operator)) != 1)
        {
            throw new ServiceException("发件箱状态已变化，请刷新后重试");
        }
    }

    private void validateEnqueue(OaReimbursement reimbursement,
            ApprovalStartRequest request, String operator)
    {
        if (reimbursement == null || reimbursement.getReimbursementId() == null
                || request == null || request.getBusinessRound() == null
                || StringUtils.isBlank(request.getIdempotencyKey())
                || StringUtils.isBlank(operator))
        {
            throw new ServiceException("报销审批发起入队参数不完整");
        }
        if (!BUSINESS_CODE.equals(request.getBusinessCode())
                || !Objects.equals(String.valueOf(reimbursement.getReimbursementId()),
                        request.getBusinessId())
                || !Objects.equals(reimbursement.getApprovalRound(),
                        request.getBusinessRound())
                || !REIMBURSEMENT_SUBMITTING.equals(reimbursement.getStatus())
                || reimbursement.getApprovalInstanceId() != null
                || reimbursement.getRowVersion() == null)
        {
            throw new ServiceException("报销单与审批发起快照不一致");
        }
    }

    private void assertReimbursementCanFinalize(
            OaReimbursementApprovalStartOutbox row, OaReimbursement reimbursement)
    {
        if (!REIMBURSEMENT_SUBMITTING.equals(reimbursement.getStatus())
                || !Objects.equals(row.getBusinessRound(),
                        reimbursement.getApprovalRound()))
        {
            throw new PermanentFailure("REIMBURSEMENT_STATE_CHANGED",
                    "报销申请已不属于当前待关联审批轮次");
        }
        if (!Objects.equals(row.getReimbursementVersion(),
                reimbursement.getRowVersion()))
        {
            throw new PermanentFailure("REIMBURSEMENT_VERSION_CONFLICT",
                    "报销申请版本已变化，禁止关联过期审批实例");
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

    private void requireScopedQuery(OaReimbursementApprovalStartOutboxQuery query)
    {
        if (query == null)
        {
            throw new ServiceException("报销审批发起查询条件不能为空");
        }
    }

    private void assertReplayable(OaReimbursementApprovalStartOutbox current,
            Long version)
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
            throw new ServiceException("报销审批发起状态已变化，等待下轮调度");
        }
    }

    private static Long defaultVersion(Long version)
    {
        return version == null ? 0L : version;
    }

    private static String safeOperator(String operator)
    {
        return StringUtils.isBlank(operator) ? "approval-outbox"
                : operator.trim();
    }

    private static String truncate(String value, int maxLength)
    {
        return value == null || value.length() <= maxLength ? value
                : value.substring(0, maxLength);
    }

    private static void syncCandidate(
            OaReimbursementApprovalStartOutbox candidate,
            OaReimbursementApprovalStartOutbox source)
    {
        candidate.setStatus(source.getStatus());
        candidate.setVersion(source.getVersion());
        candidate.setRemoteInstanceId(source.getRemoteInstanceId());
        candidate.setRemoteStatus(source.getRemoteStatus());
        candidate.setRemoteBusinessRound(source.getRemoteBusinessRound());
    }

    /** 业务事实已无法自动关联，需要可观测后人工处理。 */
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
