package com.erp.oa.domain.vo;

public class SignedPdfResult
{
    private final String archiveRelativePath;
    private final String signedPdfUrl;
    private final String signedPdfHash;
    /** Stable marked-content hash, excluding the replaceable evidence page. */
    private final String contentPdfHash;
    private final long signedPdfSize;
    private final String signatureArchiveRelativePath;
    private final String signatureFileUrl;
    private final String signatureHash;
    private final long signatureFileSize;
    private final String companySealHash;
    private final int pageCount;

    public SignedPdfResult(String archiveRelativePath, String signedPdfUrl, String signedPdfHash,
            long signedPdfSize, String signatureArchiveRelativePath, String signatureFileUrl,
            String signatureHash, String companySealHash, int pageCount)
    {
        this(archiveRelativePath, signedPdfUrl, signedPdfHash, signedPdfSize,
                signatureArchiveRelativePath, signatureFileUrl, signatureHash, 0L,
                companySealHash, pageCount, signedPdfHash);
    }

    public SignedPdfResult(String archiveRelativePath, String signedPdfUrl, String signedPdfHash,
            long signedPdfSize, String signatureArchiveRelativePath, String signatureFileUrl,
            String signatureHash, long signatureFileSize, String companySealHash, int pageCount)
    {
        this(archiveRelativePath, signedPdfUrl, signedPdfHash, signedPdfSize,
                signatureArchiveRelativePath, signatureFileUrl, signatureHash,
                signatureFileSize, companySealHash, pageCount, signedPdfHash);
    }

    public SignedPdfResult(String archiveRelativePath, String signedPdfUrl, String signedPdfHash,
            long signedPdfSize, String signatureArchiveRelativePath, String signatureFileUrl,
            String signatureHash, long signatureFileSize, String companySealHash, int pageCount,
            String contentPdfHash)
    {
        this.archiveRelativePath = archiveRelativePath;
        this.signedPdfUrl = signedPdfUrl;
        this.signedPdfHash = signedPdfHash;
        this.contentPdfHash = contentPdfHash;
        this.signedPdfSize = signedPdfSize;
        this.signatureArchiveRelativePath = signatureArchiveRelativePath;
        this.signatureFileUrl = signatureFileUrl;
        this.signatureHash = signatureHash;
        this.signatureFileSize = signatureFileSize;
        this.companySealHash = companySealHash;
        this.pageCount = pageCount;
    }

    public String getArchiveRelativePath() { return archiveRelativePath; }
    public String getSignedPdfUrl() { return signedPdfUrl; }
    public String getSignedPdfHash() { return signedPdfHash; }
    public String getContentPdfHash() { return contentPdfHash; }
    public long getSignedPdfSize() { return signedPdfSize; }
    public String getSignatureArchiveRelativePath() { return signatureArchiveRelativePath; }
    public String getSignatureFileUrl() { return signatureFileUrl; }
    public String getSignatureHash() { return signatureHash; }
    public long getSignatureFileSize() { return signatureFileSize; }
    public String getCompanySealHash() { return companySealHash; }
    public int getPageCount() { return pageCount; }
}
