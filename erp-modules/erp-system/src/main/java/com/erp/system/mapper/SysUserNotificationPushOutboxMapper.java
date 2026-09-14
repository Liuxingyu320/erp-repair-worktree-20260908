package com.erp.system.mapper;

import java.util.Date;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.erp.system.domain.SysUserNotificationPushOutbox;

public interface SysUserNotificationPushOutboxMapper
{
    int insert(SysUserNotificationPushOutbox row);

    List<SysUserNotificationPushOutbox> selectDue(@Param("now") Date now,
            @Param("staleBefore") Date staleBefore, @Param("limit") int limit);

    int claim(@Param("outboxId") Long outboxId, @Param("version") Long version,
            @Param("now") Date now, @Param("staleBefore") Date staleBefore);

    int finishAttempt(@Param("outboxId") Long outboxId, @Param("version") Long version,
            @Param("status") String status, @Param("lastResult") String lastResult,
            @Param("nextAttemptAt") Date nextAttemptAt, @Param("now") Date now);
}
