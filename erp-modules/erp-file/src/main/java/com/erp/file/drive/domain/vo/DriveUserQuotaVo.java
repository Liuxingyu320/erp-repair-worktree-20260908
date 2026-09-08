package com.erp.file.drive.domain.vo;

import java.util.List;

/**
 * 额度管理员查看的用户有效额度及来源。
 */
public record DriveUserQuotaVo(Long userId, String username, String displayName,
        Long deptId, String deptName, List<Long> postIds, List<String> postNames,
        long quotaBytes, long usedBytes, String quotaSourceType, Long quotaSourceId,
        String quotaSourceLabel, boolean overQuota, long overQuotaBytes)
{
    public DriveUserQuotaVo
    {
        postIds = postIds == null ? List.of() : List.copyOf(postIds);
        postNames = postNames == null ? List.of() : List.copyOf(postNames);
    }
}

