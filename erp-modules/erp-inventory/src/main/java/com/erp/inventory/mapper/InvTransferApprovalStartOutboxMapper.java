package com.erp.inventory.mapper;

import java.util.Date;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.erp.inventory.domain.InvTransferApprovalStartOutbox;
import com.erp.inventory.domain.vo.InvTransferApprovalStartOutboxQuery;
import com.erp.inventory.domain.vo.InvTransferApprovalStartOutboxVo;
import com.erp.inventory.domain.vo.InvTransferApprovalStartSummaryVo;

public interface InvTransferApprovalStartOutboxMapper
{
    int insertOutbox(InvTransferApprovalStartOutbox outbox);

    InvTransferApprovalStartOutbox selectById(@Param("outboxId") Long outboxId);

    InvTransferApprovalStartOutbox selectByIdForUpdate(
            @Param("outboxId") Long outboxId);

    InvTransferApprovalStartOutbox selectByTransferRound(
            @Param("transferId") Long transferId,
            @Param("businessRound") Integer businessRound);

    List<InvTransferApprovalStartOutbox> selectDueOutboxes(
            @Param("dueTime") Date dueTime,
            @Param("staleSubmittingBefore") Date staleSubmittingBefore,
            @Param("limit") int limit);

    List<InvTransferApprovalStartOutboxVo> selectOpsOutboxes(
            InvTransferApprovalStartOutboxQuery query);

    InvTransferApprovalStartSummaryVo selectSummary(
            InvTransferApprovalStartOutboxQuery query);

    InvTransferApprovalStartOutbox selectScopedByIdForUpdate(
            InvTransferApprovalStartOutboxQuery query);

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
            @Param("query") InvTransferApprovalStartOutboxQuery query,
            @Param("version") Long version,
            @Param("operator") String operator);
}
