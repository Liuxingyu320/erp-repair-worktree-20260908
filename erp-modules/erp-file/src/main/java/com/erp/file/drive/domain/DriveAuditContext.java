package com.erp.file.drive.domain;

/**
 * 可安全跨事务和线程保存的最小审计上下文。
 */
public record DriveAuditContext(Long operatorUserId, Long operatorDeptId,
        String operatorName, String requestId, String ipAddress, String userAgent)
{
}
