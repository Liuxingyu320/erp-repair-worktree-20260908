package com.erp.oa.domain.vo;

import java.util.Arrays;

/** In-memory response for an authenticated signing-template file request. */
public final class OaSignTemplateFile
{
    private final byte[] content;
    private final String fileName;
    private final String contentType;

    public OaSignTemplateFile(byte[] content, String fileName, String contentType)
    {
        if (content == null || content.length == 0)
        {
            throw new IllegalArgumentException("模板文件内容不能为空");
        }
        this.content = Arrays.copyOf(content, content.length);
        this.fileName = fileName;
        this.contentType = contentType;
    }

    public byte[] getContent()
    {
        return Arrays.copyOf(content, content.length);
    }

    public String getFileName()
    {
        return fileName;
    }

    public String getContentType()
    {
        return contentType;
    }
}
