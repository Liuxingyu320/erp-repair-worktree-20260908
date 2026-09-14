package com.erp.system.domain.vo;

import java.util.List;
import org.springframework.beans.BeanUtils;
import com.erp.system.domain.SysUserNotification;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

/** New endpoint representation; the legacy domain/list JSON contract is unchanged. */
public record SysUserNotificationPageResult(List<Row> rows, long total, long unreadCount,
        String snapshotMaxId, List<String> routeTypes, int pageNum, int pageSize)
{
    public static class Row extends SysUserNotification
    {
        private static final long serialVersionUID = 1L;
        @Override @JsonSerialize(using = ToStringSerializer.class)
        public Long getNotificationId() { return super.getNotificationId(); }
        public static Row from(SysUserNotification source)
        {
            Row row = new Row(); BeanUtils.copyProperties(source, row); return row;
        }
    }
}
