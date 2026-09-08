package com.erp.system.mapper;

import java.util.Date;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.erp.system.domain.HrHealthCertificateApprovalStartOutbox;
import com.erp.system.domain.vo.HrHealthCertificateApprovalStartOutboxQuery;
import com.erp.system.domain.vo.HrHealthCertificateApprovalStartOutboxVo;
import com.erp.system.domain.vo.HrHealthCertificateApprovalStartSummaryVo;

public interface HrHealthCertificateApprovalStartOutboxMapper
{
    int insertOutbox(HrHealthCertificateApprovalStartOutbox outbox);

    HrHealthCertificateApprovalStartOutbox selectById(
            @Param("outboxId") Long outboxId);

    HrHealthCertificateApprovalStartOutbox selectByIdForUpdate(
            @Param("outboxId") Long outboxId);

    HrHealthCertificateApprovalStartOutbox selectByCertificateRound(
            @Param("certificateId") Long certificateId,
            @Param("businessRound") Integer businessRound);

    List<HrHealthCertificateApprovalStartOutbox> selectDueOutboxes(
            @Param("dueTime") Date dueTime,
            @Param("staleSubmittingBefore") Date staleSubmittingBefore,
            @Param("limit") int limit);

    List<HrHealthCertificateApprovalStartOutboxVo> selectOpsOutboxes(
            HrHealthCertificateApprovalStartOutboxQuery query);

    HrHealthCertificateApprovalStartSummaryVo selectSummary(
            HrHealthCertificateApprovalStartOutboxQuery query);

    HrHealthCertificateApprovalStartOutbox selectScopedByIdForUpdate(
            HrHealthCertificateApprovalStartOutboxQuery query);

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
            @Param("query") HrHealthCertificateApprovalStartOutboxQuery query,
            @Param("version") Long version,
            @Param("operator") String operator);
}
