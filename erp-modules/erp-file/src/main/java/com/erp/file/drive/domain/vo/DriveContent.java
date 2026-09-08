package com.erp.file.drive.domain.vo;

import org.springframework.core.io.Resource;

/**
 * 已完成授权和完整性检查、可安全交给 HTTP 层的文件内容。
 */
public record DriveContent(Resource resource, long size, String fileName,
        String contentType, boolean inline)
{
}
