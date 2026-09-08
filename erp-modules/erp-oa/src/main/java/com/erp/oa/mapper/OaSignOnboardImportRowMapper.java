package com.erp.oa.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.erp.oa.domain.OaSignOnboardImportRow;

public interface OaSignOnboardImportRowMapper
{
    int insertRows(@Param("rows") List<OaSignOnboardImportRow> rows);
    OaSignOnboardImportRow selectById(Long rowId);
    List<OaSignOnboardImportRow> selectByBatchId(Long batchId);
    List<OaSignOnboardImportRow> selectByTaskIds(@Param("taskIds") List<Long> taskIds);
    List<OaSignOnboardImportRow> selectSignatureFirstCompanyWorkRows(
            @Param("assignedHrUserId") Long assignedHrUserId,
            @Param("scopeDeptIds") List<Long> scopeDeptIds,
            @Param("maxRows") Integer maxRows);
    List<OaSignOnboardImportRow> selectForGeneration(@Param("batchId") Long batchId,
            @Param("rowIds") List<Long> rowIds);

    int updateEditableWithVersion(OaSignOnboardImportRow row);
    int bindCompanyWorkGenerationRequest(@Param("rowId") Long rowId,
            @Param("requestId") String requestId,
            @Param("legalEntityId") Long legalEntityId,
            @Param("sealId") Long sealId,
            @Param("expectedVersion") Long expectedVersion);
    int linkDataRequest(@Param("rowId") Long rowId,
            @Param("dataRequestId") Long dataRequestId,
            @Param("status") String status,
            @Param("expectedVersion") Long expectedVersion);
    int linkStagedPackage(@Param("rowId") Long rowId,
            @Param("dataRequestId") Long dataRequestId,
            @Param("taskId") Long taskId,
            @Param("packageId") Long packageId,
            @Param("sourceEventVersion") Long sourceEventVersion,
            @Param("status") String status,
            @Param("expectedVersion") Long expectedVersion);
    int bindStagedCompanyWorkGenerationRequest(@Param("rowId") Long rowId,
            @Param("taskId") Long taskId,
            @Param("packageId") Long packageId,
            @Param("requestId") String requestId,
            @Param("legalEntityId") Long legalEntityId,
            @Param("sealId") Long sealId,
            @Param("expectedVersion") Long expectedVersion);
    int updateStatus(@Param("rowId") Long rowId,
            @Param("status") String status,
            @Param("expectedVersion") Long expectedVersion);
    int applyGenerationConfirmations(@Param("rowId") Long rowId,
            @Param("noExternalContractConfirmed") Boolean noExternalContractConfirmed,
            @Param("historicalSupplement") Boolean historicalSupplement,
            @Param("historicalReason") String historicalReason,
            @Param("warningConfirmed") Boolean warningConfirmed,
            @Param("warningReason") String warningReason,
            @Param("expectedVersion") Long expectedVersion);
    int claimGeneration(@Param("rowId") Long rowId,
            @Param("requestId") String requestId,
            @Param("expectedVersion") Long expectedVersion);
    int claimTaskOnlyGeneration(@Param("rowId") Long rowId,
            @Param("taskId") Long taskId,
            @Param("requestId") String requestId,
            @Param("expectedVersion") Long expectedVersion);
    int claimStagedGeneration(@Param("rowId") Long rowId,
            @Param("taskId") Long taskId,
            @Param("packageId") Long packageId,
            @Param("requestId") String requestId,
            @Param("expectedVersion") Long expectedVersion);
    int completeGeneration(@Param("rowId") Long rowId,
            @Param("taskId") Long taskId,
            @Param("packageId") Long packageId,
            @Param("generationRequestId") String generationRequestId,
            @Param("expectedVersion") Long expectedVersion);
    int recoverCompletedGeneration(@Param("rowId") Long rowId,
            @Param("batchId") Long batchId,
            @Param("taskId") Long taskId,
            @Param("packageId") Long packageId,
            @Param("shopDeptId") Long shopDeptId,
            @Param("generationRequestId") String generationRequestId,
            @Param("expectedVersion") Long expectedVersion);
    int failGeneration(@Param("rowId") Long rowId,
            @Param("status") String status,
            @Param("errorCodesJson") String errorCodesJson,
            @Param("generationRequestId") String generationRequestId,
            @Param("expectedVersion") Long expectedVersion);
    int markSentByTaskId(@Param("taskId") Long taskId);
    int terminalizeStagedFirstStage(@Param("taskId") Long taskId,
            @Param("packageId") Long packageId,
            @Param("employeeId") Long employeeId,
            @Param("terminalTaskStatus") String terminalTaskStatus,
            @Param("terminalPackageStatus") String terminalPackageStatus,
            @Param("rowTerminalStatus") String rowTerminalStatus);

    List<Long> selectBatchIdsByTaskOrPackage(@Param("taskId") Long taskId,
            @Param("packageId") Long packageId);
    List<Long> selectDataRequestIdsByTaskOrPackage(@Param("taskId") Long taskId,
            @Param("packageId") Long packageId);
    int unbindHardDeletedTask(@Param("taskId") Long taskId,
            @Param("packageId") Long packageId);
}
