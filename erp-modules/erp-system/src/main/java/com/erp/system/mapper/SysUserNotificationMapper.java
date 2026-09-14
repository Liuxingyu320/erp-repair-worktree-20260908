package com.erp.system.mapper;

import java.util.List;
import com.erp.system.domain.dto.SysUserNotificationPageQuery;
import org.apache.ibatis.annotations.Param;
import com.erp.system.domain.SysUserNotification;

/**
 * 定向用户消息数据层。
 */
public interface SysUserNotificationMapper
{
    int insertNotification(SysUserNotification notification);

    SysUserNotification selectByUserAndBusinessKey(@Param("userId") Long userId,
            @Param("businessKey") String businessKey);

    List<SysUserNotification> selectByUserId(@Param("userId") Long userId);

    Long selectMaxIdByUserId(@Param("userId") Long userId);

    long countPage(@Param("userId") Long userId, @Param("snapshotMaxId") Long snapshotMaxId,
            @Param("query") SysUserNotificationPageQuery query);

    List<SysUserNotification> selectPage(@Param("userId") Long userId, @Param("snapshotMaxId") Long snapshotMaxId,
            @Param("query") SysUserNotificationPageQuery query);

    List<String> selectRouteTypesByUserId(@Param("userId") Long userId);

    int markAllRead(@Param("userId") Long userId, @Param("snapshotMaxId") Long snapshotMaxId);

    long countUnreadByUserId(@Param("userId") Long userId);

    int markRead(@Param("notificationId") Long notificationId, @Param("userId") Long userId);
}
