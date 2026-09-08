package com.erp.inventory.mapper;

import java.util.Date;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.erp.inventory.domain.InvStockCheckApprovalStartOutbox;
import com.erp.inventory.domain.vo.InvStockCheckApprovalStartOutboxQuery;
import com.erp.inventory.domain.vo.InvStockCheckApprovalStartOutboxVo;
import com.erp.inventory.domain.vo.InvStockCheckApprovalStartSummaryVo;

public interface InvStockCheckApprovalStartOutboxMapper
{
    int insertOutbox(InvStockCheckApprovalStartOutbox outbox);

    InvStockCheckApprovalStartOutbox selectById(
            @Param("outboxId") Long outboxId);

    InvStockCheckApprovalStartOutbox selectByIdForUpdate(
            @Param("outboxId") Long outboxId);

    InvStockCheckApprovalStartOutbox selectByCheckRound(
            @Param("checkId") Long checkId,
            @Param("businessRound") Integer businessRound);

    List<InvStockCheckApprovalStartOutbox> selectDueOutboxes(
            @Param("dueTime") Date dueTime,
            @Param("staleSubmittingBefore") Date staleSubmittingBefore,
            @Param("limit") int limit);

    List<InvStockCheckApprovalStartOutboxVo> selectOpsOutboxes(
            InvStockCheckApprovalStartOutboxQuery query);

    InvStockCheckApprovalStartSummaryVo selectSummary(
            InvStockCheckApprovalStartOutboxQuery query);

    InvStockCheckApprovalStartOutbox selectScopedByIdForUpdate(
            InvStockCheckApprovalStartOutboxQuery query);

    int claimForSubmitting(@Param("outboxId") Long outboxId,
            @Param("expectedStatus") String expectedStatus,
            @Param("version") Long version);

    int markRemoteSucceeded(@Param("outboxId") Long outboxId,
            @Param("expectedStatus") String expectedStatus,
            @Param("version") Long version,
            @Param("remoteInstanceId") Long remoteInstanceId,
            @Param("remoteStatus") String remoteStatus,
            @Param("remoteBusinessRound") Integer remoteBusinessRound,
            @Param("lastHttpStatus") Integer lastHttpStatus);

    int markRetry(@Param("outboxId") Long outboxId,
            @Param("expectedStatus") String expectedStatus,
            @Param("version") Long version,
            @Param("retryCount") Integer retryCount,
            @Param("nextRetryTime") Date nextRetryTime,
            @Param("lastHttpStatus") Integer lastHttpStatus,
            @Param("lastErrorCode") String lastErrorCode,
            @Param("lastErrorMessage") String lastErrorMessage);

    int markFailed(@Param("outboxId") Long outboxId,
            @Param("expectedStatus") String expectedStatus,
            @Param("version") Long version,
            @Param("lastHttpStatus") Integer lastHttpStatus,
            @Param("lastErrorCode") String lastErrorCode,
            @Param("lastErrorMessage") String lastErrorMessage,
            @Param("remoteInstanceId") Long remoteInstanceId,
            @Param("remoteStatus") String remoteStatus,
            @Param("remoteBusinessRound") Integer remoteBusinessRound);

    int markSucceeded(@Param("outboxId") Long outboxId,
            @Param("expectedStatus") String expectedStatus,
            @Param("version") Long version);

    int replayFailedScoped(
            @Param("query") InvStockCheckApprovalStartOutboxQuery query,
            @Param("version") Long version,
            @Param("operator") String operator);
}
