package com.erp.oa.mapper;

import java.util.Date;
import org.apache.ibatis.annotations.Param;
import com.erp.oa.domain.OaSignTaskHardDeleteOperation;

/** Persistence boundary for administrator hard-delete command idempotency. */
public interface OaSignTaskHardDeleteOperationMapper
{
    int register(OaSignTaskHardDeleteOperation operation);

    OaSignTaskHardDeleteOperation lockByRequestId(@Param("requestId") String requestId);

    int claimForProcessing(@Param("operationId") Long operationId,
            @Param("version") Long version,
            @Param("dueTime") Date dueTime,
            @Param("claimToken") String claimToken,
            @Param("leaseExpiresTime") Date leaseExpiresTime);

    int saveProgress(@Param("operationId") Long operationId,
            @Param("version") Long version,
            @Param("claimToken") String claimToken,
            @Param("expectedProcessedCount") Integer expectedProcessedCount,
            @Param("processedCount") Integer processedCount,
            @Param("resultJson") String resultJson,
            @Param("leaseExpiresTime") Date leaseExpiresTime);

    int markCompleted(@Param("operationId") Long operationId,
            @Param("version") Long version,
            @Param("claimToken") String claimToken,
            @Param("processedCount") Integer processedCount,
            @Param("resultJson") String resultJson);

    int markRetry(@Param("operationId") Long operationId,
            @Param("version") Long version,
            @Param("claimToken") String claimToken,
            @Param("lastError") String lastError);
}
