package com.erp.system.mapper;

import java.util.Date;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.erp.system.domain.SysHrSignEventOutbox;

/**
 * 人事签约事件发件箱数据层。
 */
public interface SysHrSignEventOutboxMapper
{
    int insertOutbox(SysHrSignEventOutbox outbox);

    SysHrSignEventOutbox selectByActionAndEventVersion(@Param("actionId") Long actionId,
            @Param("eventVersion") Long eventVersion);

    List<SysHrSignEventOutbox> selectDueOutboxes(@Param("dueTime") Date dueTime,
            @Param("staleSendingBefore") Date staleSendingBefore,
            @Param("limit") int limit);

    int claimForSending(@Param("outboxId") Long outboxId,
            @Param("expectedStatus") String expectedStatus,
            @Param("version") Long version);

    int markSent(@Param("outboxId") Long outboxId,
            @Param("version") Long version,
            @Param("remoteTaskId") Long remoteTaskId,
            @Param("lastHttpStatus") Integer lastHttpStatus);

    int markRetry(@Param("outboxId") Long outboxId,
            @Param("version") Long version,
            @Param("retryCount") Integer retryCount,
            @Param("nextRetryTime") Date nextRetryTime,
            @Param("lastHttpStatus") Integer lastHttpStatus,
            @Param("lastError") String lastError);

    int markDead(@Param("outboxId") Long outboxId,
            @Param("version") Long version,
            @Param("lastHttpStatus") Integer lastHttpStatus,
            @Param("lastError") String lastError);
}
