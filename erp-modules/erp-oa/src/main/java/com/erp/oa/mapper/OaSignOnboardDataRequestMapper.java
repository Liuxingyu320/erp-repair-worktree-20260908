package com.erp.oa.mapper;

import java.util.Date;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.erp.oa.domain.OaSignOnboardDataRequest;

public interface OaSignOnboardDataRequestMapper
{
    int insertRequest(OaSignOnboardDataRequest request);
    OaSignOnboardDataRequest selectById(Long requestId);
    List<OaSignOnboardDataRequest> selectSummariesByIds(
            @Param("requestIds") List<Long> requestIds);
    OaSignOnboardDataRequest selectByRowId(Long rowId);
    List<OaSignOnboardDataRequest> selectMine(@Param("employeeId") Long employeeId);
    int reopenRejected(@Param("requestId") Long requestId,
            @Param("expectedVersion") Long expectedVersion);
    int refreshEmployeeFields(@Param("requestId") Long requestId,
            @Param("allowedFieldsJson") String allowedFieldsJson,
            @Param("signingSequence") String signingSequence,
            @Param("factSnapshotJson") String factSnapshotJson,
            @Param("confirmationSnapshotVersion") String confirmationSnapshotVersion,
            @Param("confirmationSnapshotHash") String confirmationSnapshotHash,
            @Param("profileSyncRequestId") String profileSyncRequestId,
            @Param("profileBeforeHash") String profileBeforeHash,
            @Param("expectedVersion") Long expectedVersion);
    int submit(@Param("requestId") Long requestId,
            @Param("employeeId") Long employeeId,
            @Param("submittedValuesJson") String submittedValuesJson,
            @Param("factConfirmationText") String factConfirmationText,
            @Param("signatureRequestId") String signatureRequestId,
            @Param("signaturePayloadHash") String signaturePayloadHash,
            @Param("signatureSampleBytes") byte[] signatureSampleBytes,
            @Param("signatureSampleHash") String signatureSampleHash,
            @Param("signatureSampleTime") Date signatureSampleTime,
            @Param("targetStatus") String targetStatus,
            @Param("profileSyncStatus") String profileSyncStatus,
            @Param("expectedVersion") Long expectedVersion);
    int resubmitPreservingSignature(@Param("requestId") Long requestId,
            @Param("employeeId") Long employeeId,
            @Param("submittedValuesJson") String submittedValuesJson,
            @Param("signaturePayloadHash") String signaturePayloadHash,
            @Param("expectedSignaturePayloadHash") String expectedSignaturePayloadHash,
            @Param("targetStatus") String targetStatus,
            @Param("profileSyncStatus") String profileSyncStatus,
            @Param("expectedVersion") Long expectedVersion);
    int review(@Param("requestId") Long requestId,
            @Param("fromStatus") String fromStatus,
            @Param("targetStatus") String targetStatus,
            @Param("reviewedByUserId") Long reviewedByUserId,
            @Param("reviewReason") String reviewReason,
            @Param("approvedHrValuesJson") String approvedHrValuesJson,
            @Param("profileSyncStatus") String profileSyncStatus,
            @Param("profileSyncRequestId") String profileSyncRequestId,
            @Param("expectedVersion") Long expectedVersion);
    int updateProfileSync(@Param("requestId") Long requestId,
            @Param("status") String status,
            @Param("profileBeforeHash") String profileBeforeHash,
            @Param("profileAfterHash") String profileAfterHash,
            @Param("expectedVersion") Long expectedVersion);
    int countActiveStagedFirstStageLink(@Param("taskId") Long taskId,
            @Param("packageId") Long packageId,
            @Param("employeeId") Long employeeId,
            @Param("currentTaskStatus") String currentTaskStatus,
            @Param("currentPackageStatus") String currentPackageStatus);
    int cancelStagedFirstStageByTerminal(@Param("taskId") Long taskId,
            @Param("packageId") Long packageId,
            @Param("employeeId") Long employeeId,
            @Param("terminalTaskStatus") String terminalTaskStatus,
            @Param("terminalPackageStatus") String terminalPackageStatus);
    int deleteRequestsByIds(@Param("requestIds") List<Long> requestIds);
}
