package com.erp.oa.domain.vo;

import java.nio.file.Path;

public class StagedSignFile
{
    private final Path stagingDirectory;
    private final Path tempPath;
    private final String archiveRelativePath;
    private final String serverFilename;
    private final String fileHash;
    private final long fileSize;
    private Path archivePath;
    private String publicUrl;

    public StagedSignFile(Path stagingDirectory, Path tempPath, String archiveRelativePath,
            String serverFilename, String fileHash, long fileSize)
    {
        this.stagingDirectory = stagingDirectory;
        this.tempPath = tempPath;
        this.archiveRelativePath = archiveRelativePath;
        this.serverFilename = serverFilename;
        this.fileHash = fileHash;
        this.fileSize = fileSize;
    }

    public Path getStagingDirectory() { return stagingDirectory; }
    public Path getTempPath() { return tempPath; }
    public String getArchiveRelativePath() { return archiveRelativePath; }
    public String getServerFilename() { return serverFilename; }
    public String getFileHash() { return fileHash; }
    public long getFileSize() { return fileSize; }
    public Path getArchivePath() { return archivePath; }
    public String getPublicUrl() { return publicUrl; }

    public void markPromoted(Path archivePath, String publicUrl)
    {
        this.archivePath = archivePath;
        this.publicUrl = publicUrl;
    }
}
