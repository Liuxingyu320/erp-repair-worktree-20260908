package com.erp.file.drive.domain;

import java.util.Set;

/**
 * 当前云盘请求使用的不可变登录上下文。
 */
public record DriveActor(Long userId, Long deptId, String deptName, String username,
        Set<String> permissions, boolean admin)
{
    public DriveActor
    {
        permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
    }

    public boolean hasPermission(String permission)
    {
        return permissions.contains(permission);
    }
}
