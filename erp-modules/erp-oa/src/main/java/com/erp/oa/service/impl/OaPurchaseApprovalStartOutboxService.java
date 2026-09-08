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
import com.erp.oa.domain.OaPurchase;
import com.erp.oa.domain.OaPurchaseApprovalStartOutbox;
import com.erp.oa.domain.vo.OaPurchaseApprovalStartOutboxQuery;
import com.erp.oa.domain.vo.OaPurchaseApprovalStartOutboxVo;
import com.erp.oa.domain.vo.OaPurchaseApprovalStartSummaryVo;
import com.erp.oa.mapper.OaPurchaseApprovalStartOutboxMapper;
import com.erp.oa.mapper.OaPurchaseMapper;

/** OA 采购审批发起发件箱的短事务状态服务。 */
@Service
public class OaPurchaseApprovalStartOutboxService
{
    public static final String BUSINESS_CODE = "OA_PURCHASE";
    public static final String PENDING = "PENDING";
    public static final String SUBMITTING = "SUBMITTING";
    public static final String REMOTE_SUCCEEDED = "REMOTE_SUCCEEDED";
    public static final String RETRY = "RETRY";
    public static final String FAILED = "FAILED";
    public static final String SUCCEEDED = "SUCCEEDED";

    private static final String PURCHASE_SUBMITTING = "submitting";
    private static final String PURCHASE_PENDING = "pending";
    private static final int MAX_BATCH_SIZE = 100;
    private static final int MAX_ERROR_CODE_LENGTH = 64;
    private static final int MAX_ERROR_MESSAGE_LENGTH = 255;
    private static final Set<String> OPS_STATUSES = Set.of(PENDING,
            SUBMITTING, REMOTE_SUCCEEDED, RETRY, FAILED, SUCCEEDED);
    private static final Set<String> CLAIMABLE_STATUSES = Set.of(PENDING,
            SUBMITTING, REMOTE_SUCCEEDED, RETRY);
    private static final Set<String> NON_REPLAYABLE_ERROR_CODES = Set.of(
            "REMOTE_ROUND_MISMATCH", "INSTANCE_CONFLICT",
            "PURCHASE_NOT_FOUND", "PURCHASE_STATE_CHANGED",
            "PURCHASE_VERSION_CONFLICT");

    private final OaPurchaseApprovalStartOutboxMapper mapper;
    private final OaPurchaseMapper purchaseMapper;

    public OaPurchaseApprovalStartOutboxService(
            OaPurchaseApprovalStartOutboxMapper mapper,
            OaPurchaseMapper purchaseMapper)
    {
        this.mapper = mapper;
        this.purchaseMapper = purchaseMapper;
    }

    /** 必须与采购单 SUBMITTING 事实在同一本地事务中落库。 */
    @Transactional(propagation = Propagation.MANDATORY,
            rollbackFor = Exception.class)
    public OaPurchaseApprovalStartOutbox enqueue(OaPurchase purchase,
            ApprovalStartRequest request, String operator)
    {
        validateEnqueue(purchase, request, operator);
        String requestJson = JSON.toJSONString(request);
        OaPurchaseApprovalStartOutbox row =
                new OaPurchaseApprovalStartOutbox();
        row.setPurchaseId(purchase.getPurchaseId());
        row.setBusinessRound(request.getBusinessRound());
        row.setIdempotencyKey(request.getIdempotencyKey());
        row.setRequestJson(requestJson);
        row.setStatus(PENDING);
        row.setRetryCount(0);
        row.setPurchaseVersion(defaultVersion(purchase.getRowVersion()));
        row.setVersion(0L);
        row.setCreateBy(operator.trim());
        if (mapper.insertOutbox(row) == 1)
        {
            return row;
        }
        OaPurchaseApprovalStartOutbox existing = mapper
                .selectByPurchaseRound(purchase.getPurchaseId(),
                        request.getBusinessRound());
        if (existing == null
                || !Objects.equals(request.getIdempotencyKey(),
                        existing.getIdempotencyKey())
                || !Objects.equals(requestJson, existing.getRequestJson()))
        {
            throw new ServiceException("采购审批发起幂等冲突，请联系管理员");
        }
        return existing;
    }

    public OaPurchaseApprovalStartOutbox selectById(Long outboxId)
    {
        return outboxId == null ? null : mapper.selectById(outboxId);
    }

    public OaPurchaseApprovalStartOutbox selectByPurchaseRound(
            Long purchaseId, Integer businessRound)
    {
        if (purchaseId == null || businessRound == null) return null;
        return mapper.selectByPurchaseRound(purchaseId, businessRound);
    }

    public List<OaPurchaseApprovalStartOutbox> selectDue(Date dueTime,
            Date staleSubmittingBefore, int limit)
    {
        return mapper.selectDueOutboxes(dueTime, staleSubmittingBefore,
                Math.max(1, Math.min(limit, MAX_BATCH_SIZE)));
    }

    @DataScope(deptAlias = "d")
    public List<OaPurchaseApprovalStartOutboxVo> selectOps(
            OaPurchaseApprovalStartOutboxQuery query)
    {
        requireScopedQuery(query);
        query.setStatus(normalizeStatus(query.getStatus()));
        List<OaPurchaseApprovalStartOutboxVo> rows = mapper
                .selectOpsOutboxes(query);
        return rows == null ? Collections.emptyList() : rows;
    }

    @DataScope(deptAlias = "d")
    public OaPurchaseApprovalStartSummaryVo selectSummary(
            OaPurchaseApprovalStartOutboxQuery query)
    {
        requireScopedQuery(query);
        OaPurchaseApprovalStartSummaryVo summary = mapper
                .selectSummary(query);
        return summary == null ? new OaPurchaseApprovalStartSummaryVo()
                : summary;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW,
            rollbackFor = Exception.class)
    public boolean claim(OaPurchaseApprovalStartOutbox row)
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
    public void recordRemoteSucceeded(OaPurchaseApprovalStartOutbox row,
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

    /** 采购单关联与发件箱完成同成同败，此事务内不调用远端。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW,
            rollbackFor = Exception.class)
    public void finalizeRemoteSuccess(OaPurchaseApprovalStartOutbox candidate,
            String operator)
    {
        if (candidate == null || candidate.getOutboxId() == null)
        {
            throw new PermanentFailure("OUTBOX_NOT_FOUND",
                    "采购审批发起发件箱不存在");
        }
        OaPurchaseApprovalStartOutbox row = mapper.selectByIdForUpdate(
                candidate.getOutboxId());
        if (row == null)
        {
            throw new PermanentFailure("OUTBOX_NOT_FOUND",
                    "采购审批发起发件箱不存在");
        }
        if (SUCCEEDED.equals(row.getStatus()))
        {
            syncCandidate(candidate, row);
            return;
        }
        if (!SUBMITTING.equals(row.getStatus())
                && !REMOTE_SUCCEEDED.equals(row.getStatus()))
        {
            throw new ServiceException("采购审批发起状态已变化，等待下轮调度");
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
                    "审批中心实例业务轮次与采购轮次不一致");
        }

        OaPurchase purchase = purchaseMapper.selectOaPurchaseByIdForUpdate(
                row.getPurchaseId());
        if (purchase == null)
        {
            throw new PermanentFailure("PURCHASE_NOT_FOUND", "采购申请不存在");
        }
        if (purchase.getApprovalInstanceId() != null)
        {
            if (!Objects.equals(purchase.getApprovalInstanceId(),
                    row.getRemoteInstanceId()))
            {
                throw new PermanentFailure("INSTANCE_CONFLICT",
                        "采购申请已关联其他审批实例");
            }
            if (!PURCHASE_PENDING.equals(purchase.getStatus())
                    || !Objects.equals(purchase.getApprovalRound(),
                            row.getBusinessRound()))
            {
                throw new PermanentFailure("PURCHASE_STATE_CHANGED",
                        "采购申请已不属于当前待关联审批轮次");
            }
        }
        else
        {
            assertPurchaseCanFinalize(row, purchase);
            if (purchaseMapper.finalizeApprovalStart(row.getPurchaseId(),
                    row.getBusinessRound(), row.getPurchaseVersion(),
                    row.getRemoteInstanceId(), safeOperator(operator)) != 1)
            {
                throw new ServiceException("采购单审批关联并发冲突，等待下轮调度");
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
    public void markRetry(OaPurchaseApprovalStartOutbox row,
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
    public void markFailed(OaPurchaseApprovalStartOutbox row,
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
    public void replayFailed(OaPurchaseApprovalStartOutboxQuery query,
            Long version, String operator)
    {
        requireScopedQuery(query);
        if (query.getOutboxId() == null || version == null
                || StringUtils.isBlank(operator))
        {
            throw new ServiceException("发件箱记录、版本和操作人不能为空");
        }
        OaPurchaseApprovalStartOutbox current = mapper
                .selectScopedByIdForUpdate(query);
        assertReplayable(current, version);
        if (mapper.replayFailedScoped(query, version,
                safeOperator(operator)) != 1)
        {
            throw new ServiceException("发件箱状态已变化，请刷新后重试");
        }
    }

    private void validateEnqueue(OaPurchase purchase,
            ApprovalStartRequest request, String operator)
    {
        if (purchase == null || purchase.getPurchaseId() == null
                || request == null || request.getBusinessRound() == null
                || StringUtils.isBlank(request.getIdempotencyKey())
                || StringUtils.isBlank(operator))
        {
            throw new ServiceException("采购审批发起入队参数不完整");
        }
        if (!BUSINESS_CODE.equals(request.getBusinessCode())
                || !Objects.equals(String.valueOf(purchase.getPurchaseId()),
                        request.getBusinessId())
                || !Objects.equals(purchase.getApprovalRound(),
                        request.getBusinessRound())
                || !PURCHASE_SUBMITTING.equals(purchase.getStatus())
                || purchase.getApprovalInstanceId() != null
                || purchase.getRowVersion() == null)
        {
            throw new ServiceException("采购单与审批发起快照不一致");
        }
    }

    private void assertPurchaseCanFinalize(
            OaPurchaseApprovalStartOutbox row, OaPurchase purchase)
    {
        if (!PURCHASE_SUBMITTING.equals(purchase.getStatus())
                || !Objects.equals(row.getBusinessRound(),
                        purchase.getApprovalRound()))
        {
            throw new PermanentFailure("PURCHASE_STATE_CHANGED",
                    "采购申请已不属于当前待关联审批轮次");
        }
        if (!Objects.equals(row.getPurchaseVersion(),
                purchase.getRowVersion()))
        {
            throw new PermanentFailure("PURCHASE_VERSION_CONFLICT",
                    "采购申请版本已变化，禁止关联过期审批实例");
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

    private void requireScopedQuery(OaPurchaseApprovalStartOutboxQuery query)
    {
        if (query == null)
        {
            throw new ServiceException("采购审批发起查询条件不能为空");
        }
    }

    private void assertReplayable(OaPurchaseApprovalStartOutbox current,
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
            throw new ServiceException("采购审批发起状态已变化，等待下轮调度");
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
            OaPurchaseApprovalStartOutbox candidate,
            OaPurchaseApprovalStartOutbox source)
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
