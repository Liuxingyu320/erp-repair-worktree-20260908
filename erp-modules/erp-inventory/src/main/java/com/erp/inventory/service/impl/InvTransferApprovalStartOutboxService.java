package com.erp.inventory.service.impl;

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
import com.erp.inventory.constant.InvStatusConstants;
import com.erp.inventory.domain.InvTransferApprovalStartOutbox;
import com.erp.inventory.domain.InvTransferOrder;
import com.erp.inventory.domain.vo.InvTransferApprovalStartOutboxQuery;
import com.erp.inventory.domain.vo.InvTransferApprovalStartOutboxVo;
import com.erp.inventory.domain.vo.InvTransferApprovalStartSummaryVo;
import com.erp.inventory.mapper.InvTransferApprovalStartOutboxMapper;
import com.erp.inventory.mapper.InvTransferOrderMapper;

/** 调拨统一审批发起发件箱的短事务状态服务。 */
@Service
public class InvTransferApprovalStartOutboxService
{
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
            "TRANSFER_NOT_FOUND", "TRANSFER_STATE_CHANGED",
            "TRANSFER_VERSION_CONFLICT");

    private final InvTransferApprovalStartOutboxMapper mapper;
    private final InvTransferOrderMapper transferOrderMapper;

    public InvTransferApprovalStartOutboxService(
            InvTransferApprovalStartOutboxMapper mapper,
            InvTransferOrderMapper transferOrderMapper)
    {
        this.mapper = mapper;
        this.transferOrderMapper = transferOrderMapper;
    }

    /** 必须与调拨单提交事实在同一本地事务内落库。 */
    @Transactional(propagation = Propagation.MANDATORY,
            rollbackFor = Exception.class)
    public InvTransferApprovalStartOutbox enqueue(InvTransferOrder transfer,
            ApprovalStartRequest request, String operator)
    {
        validateEnqueue(transfer, request, operator);
        InvTransferApprovalStartOutbox row = new InvTransferApprovalStartOutbox();
        row.setTransferId(transfer.getTransferId());
        row.setBusinessRound(request.getBusinessRound());
        row.setIdempotencyKey(request.getIdempotencyKey());
        row.setRequestJson(JSON.toJSONString(request));
        row.setStatus(PENDING);
        row.setRetryCount(0);
        row.setTransferVersion(defaultVersion(transfer.getVersion()));
        row.setVersion(0L);
        row.setCreateBy(operator.trim());
        if (mapper.insertOutbox(row) == 1)
        {
            return row;
        }
        InvTransferApprovalStartOutbox existing = mapper
                .selectByTransferRound(transfer.getTransferId(),
                        request.getBusinessRound());
        if (existing == null || !Objects.equals(request.getIdempotencyKey(),
                existing.getIdempotencyKey()))
        {
            throw new ServiceException("调拨审批发起幂等冲突，请联系管理员");
        }
        return existing;
    }

    public InvTransferApprovalStartOutbox selectById(Long outboxId)
    {
        return outboxId == null ? null : mapper.selectById(outboxId);
    }

    public List<InvTransferApprovalStartOutbox> selectDue(Date dueTime,
            Date staleSubmittingBefore, int limit)
    {
        return mapper.selectDueOutboxes(dueTime, staleSubmittingBefore,
                Math.max(1, Math.min(limit, MAX_BATCH_SIZE)));
    }

    @DataScope(deptAlias = "d")
    public List<InvTransferApprovalStartOutboxVo> selectOps(
            InvTransferApprovalStartOutboxQuery query)
    {
        requireScopedQuery(query);
        query.setStatus(normalizeStatus(query.getStatus()));
        List<InvTransferApprovalStartOutboxVo> rows = mapper
                .selectOpsOutboxes(query);
        return rows == null ? Collections.emptyList() : rows;
    }

    @DataScope(deptAlias = "d")
    public InvTransferApprovalStartSummaryVo selectSummary(
            InvTransferApprovalStartOutboxQuery query)
    {
        requireScopedQuery(query);
        InvTransferApprovalStartSummaryVo summary = mapper
                .selectSummary(query);
        return summary == null ? new InvTransferApprovalStartSummaryVo()
                : summary;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW,
            rollbackFor = Exception.class)
    public boolean claim(InvTransferApprovalStartOutbox row)
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
    public void recordRemoteSucceeded(InvTransferApprovalStartOutbox row,
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

    /**
     * 调拨单关联与发件箱完成必须同成同败；这个事务不包含远端调用。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW,
            rollbackFor = Exception.class)
    public void finalizeRemoteSuccess(InvTransferApprovalStartOutbox candidate,
            String operator)
    {
        if (candidate == null || candidate.getOutboxId() == null)
        {
            throw new PermanentFailure("OUTBOX_NOT_FOUND",
                    "调拨审批发起发件箱不存在");
        }
        InvTransferApprovalStartOutbox row = mapper
                .selectByIdForUpdate(candidate.getOutboxId());
        if (row == null)
        {
            throw new PermanentFailure("OUTBOX_NOT_FOUND",
                    "调拨审批发起发件箱不存在");
        }
        if (SUCCEEDED.equals(row.getStatus()))
        {
            syncCandidate(candidate, row);
            return;
        }
        if (!SUBMITTING.equals(row.getStatus())
                && !REMOTE_SUCCEEDED.equals(row.getStatus()))
        {
            throw new ServiceException("调拨审批发起状态已变化，等待下轮调度");
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
                    "审批中心实例业务轮次与调拨轮次不一致");
        }
        InvTransferOrder transfer = transferOrderMapper
                .selectInvTransferOrderByIdForUpdate(row.getTransferId());
        assertTransferCanFinalize(row, transfer);
        if (transfer.getApprovalInstanceId() == null)
        {
            Long version = row.getTransferVersion();
            if (version == null || !Objects.equals(version,
                    defaultVersion(transfer.getVersion())))
            {
                throw new PermanentFailure("TRANSFER_VERSION_CONFLICT",
                        "调拨单版本已不是审批发起快照版本");
            }
            if (transferOrderMapper.finalizeNativeApprovalStart(
                    transfer.getTransferId(), row.getBusinessRound(), version,
                    row.getRemoteInstanceId(), safeOperator(operator)) != 1)
            {
                throw new ServiceException("调拨单审批关联并发冲突，等待下轮调度");
            }
        }
        else if (!Objects.equals(transfer.getApprovalInstanceId(),
                row.getRemoteInstanceId()))
        {
            throw new PermanentFailure("INSTANCE_CONFLICT",
                    "调拨单已关联其他审批实例");
        }
        assertUpdated(mapper.markSucceeded(row.getOutboxId(),
                row.getStatus(), row.getVersion()));
        row.setStatus(SUCCEEDED);
        row.setVersion(row.getVersion() + 1);
        syncCandidate(candidate, row);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW,
            rollbackFor = Exception.class)
    public void markRetry(InvTransferApprovalStartOutbox row,
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
    public void markFailed(InvTransferApprovalStartOutbox row,
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
    public void replayFailed(InvTransferApprovalStartOutboxQuery query,
            Long version, String operator)
    {
        requireScopedQuery(query);
        if (query.getOutboxId() == null || version == null
                || StringUtils.isBlank(operator))
        {
            throw new ServiceException("发件箱记录、版本和操作人不能为空");
        }
        InvTransferApprovalStartOutbox current = mapper
                .selectScopedByIdForUpdate(query);
        assertReplayable(current, version);
        if (mapper.replayFailedScoped(query, version,
                safeOperator(operator)) != 1)
        {
            throw new ServiceException("发件箱状态已变化，请刷新后重试");
        }
    }

    private void validateEnqueue(InvTransferOrder transfer,
            ApprovalStartRequest request, String operator)
    {
        if (transfer == null || transfer.getTransferId() == null
                || request == null || request.getBusinessRound() == null
                || StringUtils.isBlank(request.getIdempotencyKey())
                || StringUtils.isBlank(operator))
        {
            throw new ServiceException("调拨审批发起入队参数不完整");
        }
        if (!InventoryUnifiedApprovalService.TRANSFER.equals(
                request.getBusinessCode())
                || !Objects.equals(String.valueOf(transfer.getTransferId()),
                        request.getBusinessId())
                || !Objects.equals(transfer.getApprovalRound(),
                        request.getBusinessRound())
                || !InventoryUnifiedApprovalService.ENGINE_NATIVE.equals(
                        transfer.getApprovalEngine())
                || !InvStatusConstants.SUBMITTED.equals(transfer.getStatus()))
        {
            throw new ServiceException("调拨单与审批发起快照不一致");
        }
    }

    private void assertTransferCanFinalize(
            InvTransferApprovalStartOutbox row, InvTransferOrder transfer)
    {
        if (transfer == null)
        {
            throw new PermanentFailure("TRANSFER_NOT_FOUND", "调拨单不存在");
        }
        if (!InvStatusConstants.SUBMITTED.equals(transfer.getStatus())
                || !InventoryUnifiedApprovalService.ENGINE_NATIVE.equals(
                        transfer.getApprovalEngine())
                || !Objects.equals(row.getBusinessRound(),
                        transfer.getApprovalRound()))
        {
            throw new PermanentFailure("TRANSFER_STATE_CHANGED",
                    "调拨单已不属于当前待关联审批轮次");
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

    private void requireScopedQuery(InvTransferApprovalStartOutboxQuery query)
    {
        if (query == null)
        {
            throw new ServiceException("调拨审批发起查询条件不能为空");
        }
    }

    private void assertReplayable(InvTransferApprovalStartOutbox current,
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
            throw new ServiceException("调拨审批发起状态已变化，等待下轮调度");
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
            InvTransferApprovalStartOutbox candidate,
            InvTransferApprovalStartOutbox source)
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
