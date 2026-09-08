package com.erp.oa.mapper;

import java.util.List;
import java.util.Date;
import org.apache.ibatis.annotations.Param;
import com.erp.oa.domain.OaSignTask;
import com.erp.oa.domain.vo.OaSignTaskMetrics;

public interface OaSignTaskMapper
{
    int insertOaSignTask(OaSignTask task);

    OaSignTask selectOaSignTaskById(Long taskId);

    List<OaSignTask> selectOaSignTasksByIds(@Param("taskIds") List<Long> taskIds);

    List<OaSignTask> selectOaSignTasksBySourceEvents(
            @Param("sourceEvents") List<OaSignTask> sourceEvents);

    OaSignTask lockOaSignTaskById(Long taskId);

    int countLifecycleReferences(Long taskId);

    OaSignTask selectOaSignTaskByDedupeKey(String dedupeKey);

    OaSignTask selectCanonicalTaskBySourceEvent(OaSignTask task);

    List<OaSignTask> selectExactTasksBySourceEvent(OaSignTask task);

    OaSignTask selectOpenRenewalTaskForUpdate(@Param("employeeId") Long employeeId);

    OaSignTask selectOpenOnboardTaskByEmployeeId(@Param("employeeId") Long employeeId);

    OaSignTask selectOaSignTaskByTaskNo(String taskNo);

    List<OaSignTask> selectOaSignTaskList(OaSignTask task);

    List<OaSignTask> selectLatestOnboardTasksByEmployeeIds(
            @Param("employeeIds") List<Long> employeeIds);

    OaSignTaskMetrics selectTaskMetrics(OaSignTask task);

    int updateStatusWithVersion(@Param("taskId") Long taskId,
            @Param("fromStatus") String fromStatus,
            @Param("toStatus") String toStatus,
            @Param("version") Long version,
            @Param("failureCode") String failureCode,
            @Param("failureDetail") String failureDetail,
            @Param("nextRetryTime") Date nextRetryTime,
            @Param("assignedHrUserId") Long assignedHrUserId);

    int updateStatusWithRetryIncrement(@Param("taskId") Long taskId,
            @Param("fromStatus") String fromStatus,
            @Param("toStatus") String toStatus,
            @Param("version") Long version,
            @Param("failureCode") String failureCode,
            @Param("failureDetail") String failureDetail,
            @Param("nextRetryTime") Date nextRetryTime,
            @Param("assignedHrUserId") Long assignedHrUserId);

    int updateConfirmation(@Param("taskId") Long taskId,
            @Param("confirmedBy") Long confirmedBy,
            @Param("confirmedTime") Date confirmedTime,
            @Param("confirmedSnapshotHash") String confirmedSnapshotHash,
            @Param("expectedStatus") String expectedStatus,
            @Param("version") Long version,
            @Param("assignedHrUserId") Long assignedHrUserId);

    int clearConfirmation(@Param("taskId") Long taskId,
            @Param("expectedStatus") String expectedStatus,
            @Param("version") Long version,
            @Param("assignedHrUserId") Long assignedHrUserId);

    int updatePackageLink(@Param("taskId") Long taskId,
            @Param("packageId") Long packageId,
            @Param("planVersionId") Long planVersionId,
            @Param("assignedHrUserId") Long assignedHrUserId);

    int bindVersionedPackage(@Param("taskId") Long taskId,
            @Param("packageId") Long packageId,
            @Param("planVersionId") Long planVersionId,
            @Param("riskLevel") String riskLevel,
            @Param("version") Long version,
            @Param("assignedHrUserId") Long assignedHrUserId);

    /** 为有明确方案版本的NO_ACTION决定冻结审计版本和风险，不创建签约包。 */
    int bindNoActionDecision(@Param("taskId") Long taskId,
            @Param("planVersionId") Long planVersionId,
            @Param("riskLevel") String riskLevel,
            @Param("version") Long version,
            @Param("assignedHrUserId") Long assignedHrUserId);

    /** 在待校验状态内冻结业务风险；版本由紧随其后的状态迁移统一递增。 */
    int updateRiskLevelForValidation(@Param("taskId") Long taskId,
            @Param("riskLevel") String riskLevel,
            @Param("version") Long version,
            @Param("assignedHrUserId") Long assignedHrUserId);

    int updateSentTime(@Param("taskId") Long taskId, @Param("sentTime") Date sentTime);

    int updateSentLifecycle(@Param("taskId") Long taskId,
            @Param("expectedStatus") String expectedStatus,
            @Param("version") Long version,
            @Param("assignedHrUserId") Long assignedHrUserId,
            @Param("sentTime") Date sentTime,
            @Param("signDeadline") Date signDeadline,
            @Param("deadlinePolicySource") String deadlinePolicySource,
            @Param("deadlineDaysSnapshot") Integer deadlineDaysSnapshot);

    int resumeEmployeeDeadlineAfterCompanyStage(@Param("taskId") Long taskId,
            @Param("expectedStatus") String expectedStatus,
            @Param("version") Long version,
            @Param("assignedHrUserId") Long assignedHrUserId,
            @Param("currentDeadline") Date currentDeadline,
            @Param("signDeadline") Date signDeadline);

    int updateTerminalMetadata(@Param("taskId") Long taskId,
            @Param("expectedStatus") String expectedStatus,
            @Param("version") Long version,
            @Param("assignedHrUserId") Long assignedHrUserId,
            @Param("terminalTime") Date terminalTime,
            @Param("terminalReasonCode") String terminalReasonCode,
            @Param("terminalReasonDetail") String terminalReasonDetail,
            @Param("resolutionStatus") String resolutionStatus);

    int updateResolutionWithVersion(@Param("taskId") Long taskId,
            @Param("currentStatus") String currentStatus,
            @Param("expectedVersion") Long expectedVersion,
            @Param("assignedHrUserId") Long assignedHrUserId,
            @Param("currentResolutionStatus") String currentResolutionStatus,
            @Param("targetResolutionStatus") String targetResolutionStatus,
            @Param("resolvedBy") Long resolvedBy,
            @Param("resolvedTime") Date resolvedTime,
            @Param("reasonCode") String reasonCode,
            @Param("reasonDetail") String reasonDetail,
            @Param("reissuedToTaskId") Long reissuedToTaskId);

    int extendDeadlineWithVersion(@Param("taskId") Long taskId,
            @Param("currentStatus") String currentStatus,
            @Param("expectedVersion") Long expectedVersion,
            @Param("assignedHrUserId") Long assignedHrUserId,
            @Param("currentResolutionStatus") String currentResolutionStatus,
            @Param("signDeadline") Date signDeadline,
            @Param("resolvedBy") Long resolvedBy,
            @Param("resolvedTime") Date resolvedTime,
            @Param("reasonCode") String reasonCode,
            @Param("reasonDetail") String reasonDetail);

    int updateCompletedTime(@Param("taskId") Long taskId, @Param("completedTime") Date completedTime);

    int deleteReassignmentsByTaskId(Long taskId);

    int deleteOaSignTaskById(Long taskId);

    int deleteOaSignTaskByIdAndVersion(@Param("taskId") Long taskId,
            @Param("expectedVersion") Long expectedVersion);
}
