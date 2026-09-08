package com.erp.system.api.domain;

import java.io.Serializable;

/**
 * 受控业务附件绑定时返回的最小文件元数据。
 */
public class DriveBusinessFile implements Serializable
{
    private static final long serialVersionUID = 1L;

    private Long nodeId;
    private String fileName;
    private String contentType;
    private Long size;

    public Long getNodeId()
    {
        return nodeId;
    }

    public void setNodeId(Long nodeId)
    {
        this.nodeId = nodeId;
    }

    public String getFileName()
    {
        return fileName;
    }

    public void setFileName(String fileName)
    {
        this.fileName = fileName;
    }

    public String getContentType()
    {
        return contentType;
    }

    public void setContentType(String contentType)
    {
        this.contentType = contentType;
    }

    public Long getSize()
    {
        return size;
    }

    public void setSize(Long size)
    {
        this.size = size;
    }
}
