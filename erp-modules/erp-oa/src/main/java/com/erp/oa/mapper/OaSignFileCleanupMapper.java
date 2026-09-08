package com.erp.oa.mapper;

import java.util.Date;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.erp.oa.domain.OaSignFileCleanup;

public interface OaSignFileCleanupMapper
{
    int insertCleanup(OaSignFileCleanup cleanup);

    OaSignFileCleanup selectById(@Param("cleanupId") Long cleanupId);

    List<OaSignFileCleanup> selectDue(@Param("dueTime") Date dueTime,
            @Param("limit") int limit);

    int claimForProcessing(@Param("cleanupId") Long cleanupId,
            @Param("version") Long version,
            @Param("dueTime") Date dueTime,
            @Param("processingToken") String processingToken,
            @Param("leaseExpiresTime") Date leaseExpiresTime);

    int markCompleted(@Param("cleanupId") Long cleanupId,
            @Param("version") Long version,
            @Param("processingToken") String processingToken);

    int markRetry(@Param("cleanupId") Long cleanupId,
            @Param("version") Long version,
            @Param("processingToken") String processingToken,
            @Param("retryCount") Integer retryCount,
            @Param("nextRetryTime") Date nextRetryTime,
            @Param("lastError") String lastError);
}
