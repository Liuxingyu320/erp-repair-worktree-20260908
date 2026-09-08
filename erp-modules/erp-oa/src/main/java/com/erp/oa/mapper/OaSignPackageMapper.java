package com.erp.oa.mapper;

import java.util.Date;
import java.util.List;
import com.erp.oa.domain.OaSignPackage;
import com.erp.oa.domain.vo.OaOnboardPackageFact;
import com.erp.oa.domain.vo.OaSignReminderCandidate;
import org.apache.ibatis.annotations.Param;

public interface OaSignPackageMapper
{
    int insertOaSignPackage(OaSignPackage signPackage);

    int updateOaSignPackage(OaSignPackage signPackage);

    int updateDraftOaSignPackage(OaSignPackage signPackage);

    OaSignPackage selectOaSignPackageById(Long packageId);

    List<OaSignPackage> selectOaSignPackagesByIds(
            @Param("packageIds") List<Long> packageIds);

    OaSignPackage lockOaSignPackageById(Long packageId);

    List<Long> selectPackageIdsByTaskId(Long taskId);

    int countLifecycleReferences(Long packageId);

    List<OaSignPackage> selectOaSignPackageList(OaSignPackage signPackage);

    List<OaSignPackage> selectMyOaSignPackageList(OaSignPackage signPackage);

    List<OaOnboardPackageFact> selectLatestOnboardPackageFactsByEmployeeIds(
            @Param("employeeIds") List<Long> employeeIds);

    OaSignPackage selectOpenPackageByEmployeeAndPlan(@Param("employeeId") Long employeeId,
            @Param("sourcePlanId") Long sourcePlanId,
            @Param("shopDeptId") Long shopDeptId);

    OaSignPackage selectOpenPackageByEmployeeAndPlanForUpdate(@Param("employeeId") Long employeeId,
            @Param("sourcePlanId") Long sourcePlanId,
            @Param("shopDeptId") Long shopDeptId);

    int updateStatusWithVersion(@Param("packageId") Long packageId,
            @Param("currentStatus") String currentStatus,
            @Param("targetStatus") String targetStatus,
            @Param("expectedVersion") Long expectedVersion,
            @Param("transitionTime") Date transitionTime,
            @Param("updateBy") String updateBy);

    int markSentWithVersion(@Param("packageId") Long packageId,
            @Param("taskId") Long taskId,
            @Param("currentStatus") String currentStatus,
            @Param("expectedVersion") Long expectedVersion,
            @Param("documentVersion") String documentVersion,
            @Param("sentTime") Date sentTime,
            @Param("signDeadline") Date signDeadline,
            @Param("deadlinePolicySource") String deadlinePolicySource,
            @Param("deadlineDaysSnapshot") Integer deadlineDaysSnapshot,
            @Param("confirmStatus") String confirmStatus,
            @Param("updateBy") String updateBy);

    int markSignatureFirstSentWithVersion(@Param("packageId") Long packageId,
            @Param("taskId") Long taskId,
            @Param("expectedVersion") Long expectedVersion,
            @Param("documentVersion") String documentVersion,
            @Param("finalDocumentVersion") String finalDocumentVersion,
            @Param("finalDocumentRootHash") String finalDocumentRootHash,
            @Param("sentTime") Date sentTime,
            @Param("signDeadline") Date signDeadline,
            @Param("deadlinePolicySource") String deadlinePolicySource,
            @Param("deadlineDaysSnapshot") Integer deadlineDaysSnapshot,
            @Param("confirmStatus") String confirmStatus,
            @Param("updateBy") String updateBy);

    int sendStagedShell(@Param("signPackage") OaSignPackage signPackage,
            @Param("expectedVersion") Long expectedVersion);

    int recordStagedEmployeeSignature(@Param("signPackage") OaSignPackage signPackage,
            @Param("currentStatus") String currentStatus,
            @Param("expectedVersion") Long expectedVersion);

    int prepareStagedFinalCandidate(@Param("signPackage") OaSignPackage signPackage,
            @Param("expectedDocumentVersion") String expectedDocumentVersion,
            @Param("expectedVersion") Long expectedVersion);

    int sendStagedFinalCandidate(@Param("signPackage") OaSignPackage signPackage,
            @Param("expectedVersion") Long expectedVersion);

    int markViewedWithVersion(@Param("packageId") Long packageId,
            @Param("currentStatus") String currentStatus,
            @Param("expectedVersion") Long expectedVersion,
            @Param("viewedTime") Date viewedTime,
            @Param("updateBy") String updateBy);

    int markTerminalWithVersion(@Param("packageId") Long packageId,
            @Param("currentStatus") String currentStatus,
            @Param("targetStatus") String targetStatus,
            @Param("expectedVersion") Long expectedVersion,
            @Param("terminalTime") Date terminalTime,
            @Param("reasonCode") String reasonCode,
            @Param("reasonDetail") String reasonDetail,
            @Param("updateBy") String updateBy);

    List<OaSignPackage> selectExpiredCandidates(@Param("dueTime") Date dueTime,
            @Param("limit") int limit);

    List<OaSignReminderCandidate> selectReminderCandidates(@Param("scanTime") Date scanTime,
            @Param("limit") int limit);

    int voidWithVersion(@Param("packageId") Long packageId,
            @Param("currentStatus") String currentStatus,
            @Param("expectedVersion") Long expectedVersion,
            @Param("voidReason") String voidReason,
            @Param("updateBy") String updateBy);

    int updateResolutionWithVersion(@Param("packageId") Long packageId,
            @Param("currentStatus") String currentStatus,
            @Param("expectedVersion") Long expectedVersion,
            @Param("currentResolutionStatus") String currentResolutionStatus,
            @Param("targetResolutionStatus") String targetResolutionStatus,
            @Param("resolvedBy") Long resolvedBy,
            @Param("resolvedTime") Date resolvedTime,
            @Param("reasonCode") String reasonCode,
            @Param("reasonDetail") String reasonDetail,
            @Param("reissuedToPackageId") Long reissuedToPackageId,
            @Param("updateBy") String updateBy);

    int extendDeadlineWithVersion(@Param("packageId") Long packageId,
            @Param("currentStatus") String currentStatus,
            @Param("expectedVersion") Long expectedVersion,
            @Param("currentResolutionStatus") String currentResolutionStatus,
            @Param("signDeadline") Date signDeadline,
            @Param("resolvedBy") Long resolvedBy,
            @Param("resolvedTime") Date resolvedTime,
            @Param("reasonCode") String reasonCode,
            @Param("reasonDetail") String reasonDetail,
            @Param("updateBy") String updateBy);

    int bindReissueSource(@Param("packageId") Long packageId,
            @Param("expectedVersion") Long expectedVersion,
            @Param("reissueOfPackageId") Long reissueOfPackageId,
            @Param("updateBy") String updateBy);

    int updateTaskConfirmation(@Param("packageId") Long packageId,
            @Param("taskId") Long taskId,
            @Param("confirmStatus") String confirmStatus,
            @Param("planVersionId") Long planVersionId);

    int clearPendingCompanySelection(@Param("packageId") Long packageId);

    int updateOnboardPersonalFacts(@Param("packageId") Long packageId,
            @Param("studentStatusSnapshot") String studentStatusSnapshot,
            @Param("retirementStatusSnapshot") String retirementStatusSnapshot,
            @Param("incomeStartYearMonth") String incomeStartYearMonth,
            @Param("assignedHrUserId") Long assignedHrUserId);

    int updateFinalizedPackage(@Param("signPackage") OaSignPackage signPackage,
            @Param("currentStatus") String currentStatus,
            @Param("expectedVersion") Long expectedVersion);

    int updatePreparedFinalCandidate(@Param("signPackage") OaSignPackage signPackage,
            @Param("expectedDocumentVersion") String expectedDocumentVersion,
            @Param("expectedVersion") Long expectedVersion);

    int updateFinalConfirmed(@Param("packageId") Long packageId,
            @Param("currentStatus") String currentStatus,
            @Param("expectedVersion") Long expectedVersion,
            @Param("confirmedTime") java.util.Date confirmedTime,
            @Param("finalArchiveRootHash") String finalArchiveRootHash,
            @Param("evidenceGeneratedTime") java.util.Date evidenceGeneratedTime,
            @Param("updateBy") String updateBy);

    int deleteOaSignPackageById(Long packageId);
}
