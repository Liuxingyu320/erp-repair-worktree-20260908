package com.erp.file.drive.domain;

/**
 * 一次个人额度解析的不可变结果。
 */
public record DriveEffectiveQuota(long quotaBytes, String sourceType,
        Long sourceId, String sourceLabel)
{
}

