package com.erp.system.mapper;

import java.util.Date;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.erp.system.domain.HrOnboardingImportBatch;
import com.erp.system.domain.HrOnboardingImportRow;

public interface HrOnboardingImportMapper
{
    int insertBatch(HrOnboardingImportBatch batch);
    int insertRow(HrOnboardingImportRow row);
    HrOnboardingImportBatch selectBatchById(Long batchId);
    List<HrOnboardingImportRow> selectRowsByBatchId(Long batchId);
    HrOnboardingImportRow selectRowByIdForUpdate(Long rowId);
    int claimPreviewedBatch(@Param("batchId") Long batchId, @Param("version") Integer version,
            @Param("operatorUserId") Long operatorUserId, @Param("operator") String operator);
    int claimStaleProcessingBatch(@Param("batchId") Long batchId, @Param("version") Integer version,
            @Param("operatorUserId") Long operatorUserId, @Param("leaseCutoff") Date leaseCutoff,
            @Param("operator") String operator);
    int heartbeatProcessingBatch(@Param("batchId") Long batchId,@Param("version") Integer version,
            @Param("operatorUserId") Long operatorUserId,@Param("operator") String operator);
    int touchProcessingBatchLease(@Param("batchId") Long batchId,@Param("version") Integer version,
            @Param("operator") String operator);
    int finishBatch(@Param("batchId") Long batchId,@Param("version") Integer version,
            @Param("successRows") int successRows,
            @Param("failureRows") int failureRows, @Param("status") String status,
            @Param("operatorUserId") Long operatorUserId, @Param("operator") String operator);
    int markRowSuccess(@Param("rowId") Long rowId, @Param("batchId") Long batchId,
            @Param("onboardingId") Long onboardingId,
            @Param("operatorUserId") Long operatorUserId, @Param("operator") String operator);
    int recordRowDecision(@Param("rowId") Long rowId, @Param("batchId") Long batchId,
            @Param("decision") String decision,
            @Param("bindUserId") Long bindUserId, @Param("operator") String operator);
    int markRowFailure(@Param("rowId") Long rowId, @Param("batchId") Long batchId,
            @Param("resultCode") String resultCode,
            @Param("resultMessage") String resultMessage, @Param("operatorUserId") Long operatorUserId,
            @Param("operator") String operator);
    int deleteRowsForExpiredBatches(@Param("cutoff") Date cutoff, @Param("limit") int limit,
            @Param("statuses") List<String> statuses);
    int deleteExpiredBatches(@Param("cutoff") Date cutoff, @Param("limit") int limit,
            @Param("statuses") List<String> statuses);
}
