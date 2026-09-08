package com.erp.oa.mapper;

import java.util.Date;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.erp.oa.domain.OaSignOnboardImportBatch;

public interface OaSignOnboardImportBatchMapper
{
    int insertBatch(OaSignOnboardImportBatch batch);

    OaSignOnboardImportBatch selectReusable(@Param("shopDeptId") Long shopDeptId,
            @Param("createdByUserId") Long createdByUserId,
            @Param("selectionHash") String selectionHash,
            @Param("fileSha256") String fileSha256,
            @Param("now") Date now);

    OaSignOnboardImportBatch selectById(Long batchId);

    List<OaSignOnboardImportBatch> selectByIds(@Param("batchIds") List<Long> batchIds);

    List<Long> selectStaleGeneratingBatchIds(
            @Param("staleBefore") Date staleBefore,
            @Param("maxRows") Integer maxRows);

    int claimGeneration(@Param("batchId") Long batchId,
            @Param("expectedVersion") Long expectedVersion,
            @Param("staleBefore") Date staleBefore);

    int updateSummary(@Param("batchId") Long batchId,
            @Param("status") String status,
            @Param("matchedCount") Integer matchedCount,
            @Param("excludedCount") Integer excludedCount,
            @Param("errorCount") Integer errorCount,
            @Param("warningCount") Integer warningCount,
            @Param("generatedCount") Integer generatedCount);

    int refreshAfterHardDelete(Long batchId);
}
