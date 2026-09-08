package com.erp.oa.service.impl;

public class GeneratedSignDocument
{
    private final String sourceFileUrl;
    private final String sourceFileHash;
    private final String reviewPdfUrl;
    private final String reviewPdfHash;
    private final String documentVersion;
    private final String sourceArchiveRelativePath;
    private final long sourceFileSize;
    private final String reviewPdfArchiveRelativePath;
    private final long reviewPdfSize;

    public GeneratedSignDocument(String fileUrl, String pdfUrl, String sha256)
    {
        this(fileUrl, sha256, pdfUrl, pdfUrl == null ? null : sha256, null);
    }

    public GeneratedSignDocument(String sourceFileUrl, String sourceFileHash, String reviewPdfUrl,
            String reviewPdfHash, String documentVersion)
    {
        this(sourceFileUrl, sourceFileHash, reviewPdfUrl, reviewPdfHash, documentVersion,
                null, 0L, null, 0L);
    }

    public GeneratedSignDocument(String sourceFileUrl, String sourceFileHash, String reviewPdfUrl,
            String reviewPdfHash, String documentVersion, String sourceArchiveRelativePath,
            long sourceFileSize, String reviewPdfArchiveRelativePath, long reviewPdfSize)
    {
        this.sourceFileUrl = sourceFileUrl;
        this.sourceFileHash = sourceFileHash;
        this.reviewPdfUrl = reviewPdfUrl;
        this.reviewPdfHash = reviewPdfHash;
        this.documentVersion = documentVersion;
        this.sourceArchiveRelativePath = sourceArchiveRelativePath;
        this.sourceFileSize = sourceFileSize;
        this.reviewPdfArchiveRelativePath = reviewPdfArchiveRelativePath;
        this.reviewPdfSize = reviewPdfSize;
    }

    public String getSourceFileUrl()
    {
        return sourceFileUrl;
    }

    public String getSourceFileHash()
    {
        return sourceFileHash;
    }

    public String getReviewPdfUrl()
    {
        return reviewPdfUrl;
    }

    public String getReviewPdfHash()
    {
        return reviewPdfHash;
    }

    public String getDocumentVersion()
    {
        return documentVersion;
    }

    public String getSourceArchiveRelativePath() { return sourceArchiveRelativePath; }
    public long getSourceFileSize() { return sourceFileSize; }
    public String getReviewPdfArchiveRelativePath() { return reviewPdfArchiveRelativePath; }
    public long getReviewPdfSize() { return reviewPdfSize; }

    public String getFileUrl()
    {
        return sourceFileUrl;
    }

    public String getPdfUrl()
    {
        return reviewPdfUrl;
    }

    public String getSha256()
    {
        return reviewPdfHash == null ? sourceFileHash : reviewPdfHash;
    }
}
