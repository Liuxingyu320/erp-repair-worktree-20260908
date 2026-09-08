package com.erp.oa.domain.vo;

import java.nio.file.Path;

public class OaSignPackageFile
{
    private final Path path;
    private final String fileName;
    private final String contentType;
    private final String fileHash;
    private final boolean deleteAfterStreaming;

    public OaSignPackageFile(Path path, String fileName, String contentType)
    {
        this(path, fileName, contentType, null, false);
    }

    public OaSignPackageFile(Path path, String fileName, String contentType,
            String fileHash)
    {
        this(path, fileName, contentType, fileHash, false);
    }

    public OaSignPackageFile(Path path, String fileName, String contentType,
            String fileHash, boolean deleteAfterStreaming)
    {
        this.path = path;
        this.fileName = fileName;
        this.contentType = contentType;
        this.fileHash = fileHash;
        this.deleteAfterStreaming = deleteAfterStreaming;
    }

    public Path getPath()
    {
        return path;
    }

    public String getFileName()
    {
        return fileName;
    }

    public String getContentType()
    {
        return contentType;
    }

    public String getFileHash()
    {
        return fileHash;
    }

    public boolean isDeleteAfterStreaming()
    {
        return deleteAfterStreaming;
    }
}
