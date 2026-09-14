package com.erp.job.mapper;
import java.util.List;
import com.erp.job.domain.SysJobDeleteBatch;
import com.erp.job.domain.SysJobDeleteIntent;
import org.apache.ibatis.annotations.Param;
public interface SysJobDeleteMapper
{
    int insertBatchIfAbsent(SysJobDeleteBatch batch);
    SysJobDeleteBatch lockBatch(@Param("batchId") String batchId);
    SysJobDeleteBatch selectBatch(@Param("batchId") String batchId);
    int insertIntent(SysJobDeleteIntent intent);
    List<SysJobDeleteIntent> selectBatchIntents(@Param("batchId") String batchId);
    List<SysJobDeleteIntent> selectBatchIntentsForUpdate(@Param("batchId") String batchId);
    List<SysJobDeleteIntent> selectPending(@Param("limit") int limit);
    SysJobDeleteIntent lockIntent(@Param("intentId") String intentId);
    int updateResult(@Param("intentId") String intentId, @Param("status") String status, @Param("errorCode") String errorCode);
}
