package com.erp.oa.mapper;

import java.util.Date;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.erp.oa.domain.OaReimbursementApprovalStartOutbox;
import com.erp.oa.domain.vo.OaReimbursementApprovalStartOutboxQuery;
import com.erp.oa.domain.vo.OaReimbursementApprovalStartOutboxVo;
import com.erp.oa.domain.vo.OaReimbursementApprovalStartSummaryVo;

public interface OaReimbursementApprovalStartOutboxMapper
{
    int insertOutbox(OaReimbursementApprovalStartOutbox outbox);

    OaReimbursementApprovalStartOutbox selectById(
            @Param("outboxId") Long outboxId);

    OaReimbursementApprovalStartOutbox selectByIdForUpdate(
            @Param("outboxId") Long outboxId);

    OaReimbursementApprovalStartOutbox selectByReimbursementRound(
            @Param("reimbursementId") Long reimbursementId,
            @Param("businessRound") Integer businessRound);

    List<OaReimbursementApprovalStartOutbox> selectDueOutboxes(
            @Param("dueTime") Date dueTime,
            @Param("staleSubmittingBefore") Date staleSubmittingBefore,
            @Param("limit") int limit);

    List<OaReimbursementApprovalStartOutboxVo> selectOpsOutboxes(
            OaReimbursementApprovalStartOutboxQuery query);

    OaReimbursementApprovalStartSummaryVo selectSummary(
            OaReimbursementApprovalStartOutboxQuery query);

    OaReimbursementApprovalStartOutbox selectScopedByIdForUpdate(
            OaReimbursementApprovalStartOutboxQuery query);

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
            @Param("query") OaReimbursementApprovalStartOutboxQuery query,
            @Param("version") Long version,
            @Param("operator") String operator);
}
