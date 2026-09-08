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
import com.erp.inventory.domain.InvStockCheck;
import com.erp.inventory.domain.InvStockCheckApprovalStartOutbox;
import com.erp.inventory.domain.vo.InvStockCheckApprovalStartOutboxQuery;
import com.erp.inventory.domain.vo.InvStockCheckApprovalStartOutboxVo;
import com.erp.inventory.domain.vo.InvStockCheckApprovalStartSummaryVo;
import com.erp.inventory.mapper.InvStockCheckApprovalStartOutboxMapper;
import com.erp.inventory.mapper.InvStockCheckMapper;

/** 盘点统一审批发起发件箱的短事务状态服务。 */
@Service
public class InvStockCheckApprovalStartOutboxService
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
            "STOCK_CHECK_NOT_FOUND", "STOCK_CHECK_STATE_CHANGED",
            "STOCK_CHECK_VERSION_CONFLICT");

    private final InvStockCheckApprovalStartOutboxMapper mapper;
    private final InvStockCheckMapper stockCheckMapper;

    public InvStockCheckApprovalStartOutboxService(
            InvStockCheckApprovalStartOutboxMapper mapper,
            InvStockCheckMapper stockCheckMapper)
    {
        this.mapper = mapper;
        this.stockCheckMapper = stockCheckMapper;
    }

    /** 必须与盘点提交事实在同一本地事务内落库。 */
    @Transactional(propagation = Propagation.MANDATORY,
            rollbackFor = Exception.class)
    public InvStockCheckApprovalStartOutbox enqueue(InvStockCheck check,
            ApprovalStartRequest request, String operator)
    {
        validateEnqueue(check, request, operator);
        InvStockCheckApprovalStartOutbox row =
                new InvStockCheckApprovalStartOutbox();
        row.setCheckId(check.getCheckId());
        row.setBusinessRound(request.getBusinessRound());
        row.setIdempotencyKey(request.getIdempotencyKey());
        row.setRequestJson(JSON.toJSONString(request));
        row.setStatus(PENDING);
        row.setRetryCount(0);
        row.setCheckRowVersion(defaultVersion(check.getRowVersion()));
        row.setVersion(0L);
        row.setCreateBy(operator.trim());
        if (mapper.insertOutbox(row) == 1)
        {
            return row;
        }
        InvStockCheckApprovalStartOutbox existing = mapper
                .selectByCheckRound(check.getCheckId(),
                        request.getBusinessRound());
        if (existing == null || !Objects.equals(request.getIdempotencyKey(),
                existing.getIdempotencyKey()))
        {
            throw new ServiceException("盘点审批发起幂等冲突，请联系管理员");
        }
        return existing;
    }

    public InvStockCheckApprovalStartOutbox selectById(Long outboxId)
    {
        return outboxId == null ? null : mapper.selectById(outboxId);
    }

    public List<InvStockCheckApprovalStartOutbox> selectDue(Date dueTime,
            Date staleSubmittingBefore, int limit)
    {
        return mapper.selectDueOutboxes(dueTime, staleSubmittingBefore,
                Math.max(1, Math.min(limit, MAX_BATCH_SIZE)));
    }

    @DataScope(deptAlias = "d")
    public List<InvStockCheckApprovalStartOutboxVo> selectOps(
            InvStockCheckApprovalStartOutboxQuery query)
    {
        requireScopedQuery(query);
        query.setStatus(normalizeStatus(query.getStatus()));
        List<InvStockCheckApprovalStartOutboxVo> rows = mapper
                .selectOpsOutboxes(query);
        return rows == null ? Collections.emptyList() : rows;
    }

    @DataScope(deptAlias = "d")
    public InvStockCheckApprovalStartSummaryVo selectSummary(
            InvStockCheckApprovalStartOutboxQuery query)
    {
        requireScopedQuery(query);
        InvStockCheckApprovalStartSummaryVo summary = mapper
                .selectSummary(query);
        return summary == null ? new InvStockCheckApprovalStartSummaryVo()
                : summary;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW,
            rollbackFor = Exception.class)
    public boolean claim(InvStockCheckApprovalStartOutbox row)
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
    public void recordRemoteSucceeded(InvStockCheckApprovalStartOutbox row,
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

    /** 盘点单关联与发件箱完成必须同成同败；这个事务不包含远端调用。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW,
            rollbackFor = Exception.class)
    public void finalizeRemoteSuccess(
            InvStockCheckApprovalStartOutbox candidate, String operator)
    {
        if (candidate == null || candidate.getOutboxId() == null)
        {
            throw new PermanentFailure("OUTBOX_NOT_FOUND",
                    "盘点审批发起发件箱不存在");
        }
        InvStockCheckApprovalStartOutbox row = mapper
                .selectByIdForUpdate(candidate.getOutboxId());
        if (row == null)
        {
            throw new PermanentFailure("OUTBOX_NOT_FOUND",
                    "盘点审批发起发件箱不存在");
        }
        if (SUCCEEDED.equals(row.getStatus()))
        {
            syncCandidate(candidate, row);
            return;
        }
        if (!SUBMITTING.equals(row.getStatus())
                && !REMOTE_SUCCEEDED.equals(row.getStatus()))
        {
            throw new ServiceException("盘点审批发起状态已变化，等待下轮调度");
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
                    "审批中心实例业务轮次与盘点轮次不一致");
        }
        InvStockCheck check = stockCheckMapper
                .selectInvStockCheckByIdForUpdate(row.getCheckId());
        assertCheckCanFinalize(row, check);
        if (check.getApprovalInstanceId() == null)
        {
            Long rowVersion = row.getCheckRowVersion();
            if (rowVersion == null || !Objects.equals(rowVersion,
                    defaultVersion(check.getRowVersion())))
            {
                throw new PermanentFailure("STOCK_CHECK_VERSION_CONFLICT",
                        "盘点单版本已不是审批发起快照版本");
            }
            if (stockCheckMapper.finalizeNativeApprovalStart(
                    check.getCheckId(), row.getBusinessRound(), rowVersion,
                    row.getRemoteInstanceId(), safeOperator(operator)) != 1)
            {
                throw new ServiceException("盘点单审批关联并发冲突，等待下轮调度");
            }
        }
        else if (!Objects.equals(check.getApprovalInstanceId(),
                row.getRemoteInstanceId()))
        {
            throw new PermanentFailure("INSTANCE_CONFLICT",
                    "盘点单已关联其他审批实例");
        }
        assertUpdated(mapper.markSucceeded(row.getOutboxId(),
                row.getStatus(), row.getVersion()));
        row.setStatus(SUCCEEDED);
        row.setVersion(row.getVersion() + 1);
        syncCandidate(candidate, row);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW,
            rollbackFor = Exception.class)
    public void markRetry(InvStockCheckApprovalStartOutbox row,
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
    public void markFailed(InvStockCheckApprovalStartOutbox row,
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
    public void replayFailed(InvStockCheckApprovalStartOutboxQuery query,
            Long version, String operator)
    {
        requireScopedQuery(query);
        if (query.getOutboxId() == null || version == null
                || StringUtils.isBlank(operator))
        {
            throw new ServiceException("发件箱记录、版本和操作人不能为空");
        }
        InvStockCheckApprovalStartOutbox current = mapper
                .selectScopedByIdForUpdate(query);
        assertReplayable(current, version);
        if (mapper.replayFailedScoped(query, version,
                safeOperator(operator)) != 1)
        {
            throw new ServiceException("发件箱状态已变化，请刷新后重试");
        }
    }

    private void validateEnqueue(InvStockCheck check,
            ApprovalStartRequest request, String operator)
    {
        if (check == null || check.getCheckId() == null
                || request == null || request.getBusinessRound() == null
                || StringUtils.isBlank(request.getIdempotencyKey())
                || StringUtils.isBlank(operator))
        {
            throw new ServiceException("盘点审批发起入队参数不完整");
        }
        if (!InventoryUnifiedApprovalService.STOCK_CHECK.equals(
                request.getBusinessCode())
                || !Objects.equals(String.valueOf(check.getCheckId()),
                        request.getBusinessId())
                || !Objects.equals(check.getApprovalRound(),
                        request.getBusinessRound())
                || !InventoryUnifiedApprovalService.ENGINE_NATIVE.equals(
                        check.getApprovalEngine())
                || !InvStatusConstants.PENDING_APPROVAL.equals(
                        check.getStatus()))
        {
            throw new ServiceException("盘点单与审批发起快照不一致");
        }
    }

    private void assertCheckCanFinalize(
            InvStockCheckApprovalStartOutbox row, InvStockCheck check)
    {
        if (check == null)
        {
            throw new PermanentFailure("STOCK_CHECK_NOT_FOUND", "盘点单不存在");
        }
        if (!InvStatusConstants.PENDING_APPROVAL.equals(check.getStatus())
                || !InventoryUnifiedApprovalService.ENGINE_NATIVE.equals(
                        check.getApprovalEngine())
                || !Objects.equals(row.getBusinessRound(),
                        check.getApprovalRound()))
        {
            throw new PermanentFailure("STOCK_CHECK_STATE_CHANGED",
                    "盘点单已不属于当前待关联审批轮次");
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
            InvStockCheckApprovalStartOutboxQuery query)
    {
        if (query == null)
        {
            throw new ServiceException("盘点审批发起查询条件不能为空");
        }
    }

    private void assertReplayable(InvStockCheckApprovalStartOutbox current,
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
            throw new ServiceException("盘点审批发起状态已变化，等待下轮调度");
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
            InvStockCheckApprovalStartOutbox candidate,
            InvStockCheckApprovalStartOutbox source)
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
