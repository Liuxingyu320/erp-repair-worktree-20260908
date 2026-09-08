package com.erp.oa.domain;

public class OaSignFinalConfirmationDocument
{
    private Long confirmationDocumentId;
    private Long confirmationId;
    private Long packageId;
    private Long documentId;
    private String finalDocumentVersion;
    private String finalPdfHash;

    public Long getConfirmationDocumentId() { return confirmationDocumentId; }
    public void setConfirmationDocumentId(Long confirmationDocumentId) { this.confirmationDocumentId = confirmationDocumentId; }
    public Long getConfirmationId() { return confirmationId; }
    public void setConfirmationId(Long confirmationId) { this.confirmationId = confirmationId; }
    public Long getPackageId() { return packageId; }
    public void setPackageId(Long packageId) { this.packageId = packageId; }
    public Long getDocumentId() { return documentId; }
    public void setDocumentId(Long documentId) { this.documentId = documentId; }
    public String getFinalDocumentVersion() { return finalDocumentVersion; }
    public void setFinalDocumentVersion(String finalDocumentVersion) { this.finalDocumentVersion = finalDocumentVersion; }
    public String getFinalPdfHash() { return finalPdfHash; }
    public void setFinalPdfHash(String finalPdfHash) { this.finalPdfHash = finalPdfHash; }
}
