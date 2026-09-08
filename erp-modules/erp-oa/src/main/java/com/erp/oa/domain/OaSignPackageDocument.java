package com.erp.oa.domain;

import java.util.Date;
import com.erp.common.core.web.domain.BaseEntity;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

public class OaSignPackageDocument extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long documentId;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long packageId;
    private Long templateId;
    private String templateType;
    private String documentName;
    private String templateVersionSnapshot;
    private String sourceFileUrlSnapshot;
    private String generatedFileUrl;
    private String generatedPdfUrl;
    private String signedFileUrl;
    private String certificateFileUrl;
    private String fileHashBeforeSign;
    private String fileHashAfterSign;
    private String reviewPdfUrl;
    private String reviewPdfHash;
    private String signedPdfUrl;
    private String signedPdfHash;
    private String finalPdfUrl;
    private String finalPdfHash;
    /** Hash of the stable正文 bytes before the fixed evidence page is appended. */
    private String finalContentHash;
    /** Immutable post-confirmation archive; unavailable before final confirmation. */
    private String finalArchivePdfUrl;
    private String finalArchivePdfHash;
    /** Employee-facing capability flags; storage paths are never serialized to mobile clients. */
    private Boolean signedFileAvailable;
    private Boolean certificateAvailable;
    private Boolean finalFileAvailable;
    private String signatureFileUrl;
    private String signatureHash;
    private String certificateHash;
    private String documentVersion;
    private String finalDocumentVersion;
    private String finalReadConfirmed;
    private Date finalReadConfirmedTime;
    private String employeeVisible;
    private String readConfirmationRequired;
    private String employeeSignRequired;
    private String signaturePositionJson;
    private String companySealPositionJson;
    private String companySealRequired;
    private String documentPolicyMode;
    private String readConfirmed;
    private String signed;
    private Integer sortOrder;
    private String status;
    private String errorMessage;

    public Long getDocumentId() { return documentId; }
    public void setDocumentId(Long documentId) { this.documentId = documentId; }

    public Long getPackageId() { return packageId; }
    public void setPackageId(Long packageId) { this.packageId = packageId; }

    public Long getTemplateId() { return templateId; }
    public void setTemplateId(Long templateId) { this.templateId = templateId; }

    public String getTemplateType() { return templateType; }
    public void setTemplateType(String templateType) { this.templateType = templateType; }

    public String getDocumentName() { return documentName; }
    public void setDocumentName(String documentName) { this.documentName = documentName; }

    public String getTemplateVersionSnapshot() { return templateVersionSnapshot; }
    public void setTemplateVersionSnapshot(String templateVersionSnapshot) { this.templateVersionSnapshot = templateVersionSnapshot; }

    public String getSourceFileUrlSnapshot() { return sourceFileUrlSnapshot; }
    public void setSourceFileUrlSnapshot(String sourceFileUrlSnapshot) { this.sourceFileUrlSnapshot = sourceFileUrlSnapshot; }

    public String getGeneratedFileUrl() { return generatedFileUrl; }
    public void setGeneratedFileUrl(String generatedFileUrl) { this.generatedFileUrl = generatedFileUrl; }

    public String getGeneratedPdfUrl() { return generatedPdfUrl; }
    public void setGeneratedPdfUrl(String generatedPdfUrl) { this.generatedPdfUrl = generatedPdfUrl; }

    public String getSignedFileUrl() { return signedFileUrl; }
    public void setSignedFileUrl(String signedFileUrl) { this.signedFileUrl = signedFileUrl; }

    public String getCertificateFileUrl() { return certificateFileUrl; }
    public void setCertificateFileUrl(String certificateFileUrl) { this.certificateFileUrl = certificateFileUrl; }

    public String getFileHashBeforeSign() { return fileHashBeforeSign; }
    public void setFileHashBeforeSign(String fileHashBeforeSign) { this.fileHashBeforeSign = fileHashBeforeSign; }

    public String getFileHashAfterSign() { return fileHashAfterSign; }
    public void setFileHashAfterSign(String fileHashAfterSign) { this.fileHashAfterSign = fileHashAfterSign; }

    public String getReviewPdfUrl() { return reviewPdfUrl; }
    public void setReviewPdfUrl(String reviewPdfUrl) { this.reviewPdfUrl = reviewPdfUrl; }

    public String getReviewPdfHash() { return reviewPdfHash; }
    public void setReviewPdfHash(String reviewPdfHash) { this.reviewPdfHash = reviewPdfHash; }

    public String getSignedPdfUrl() { return signedPdfUrl; }
    public void setSignedPdfUrl(String signedPdfUrl) { this.signedPdfUrl = signedPdfUrl; }

    public String getSignedPdfHash() { return signedPdfHash; }
    public void setSignedPdfHash(String signedPdfHash) { this.signedPdfHash = signedPdfHash; }

    public String getFinalPdfUrl() { return finalPdfUrl; }
    public void setFinalPdfUrl(String finalPdfUrl) { this.finalPdfUrl = finalPdfUrl; }

    public String getFinalPdfHash() { return finalPdfHash; }
    public void setFinalPdfHash(String finalPdfHash) { this.finalPdfHash = finalPdfHash; }

    public String getFinalContentHash() { return finalContentHash; }
    public void setFinalContentHash(String value) { finalContentHash = value; }
    public String getFinalArchivePdfUrl() { return finalArchivePdfUrl; }
    public void setFinalArchivePdfUrl(String value) { finalArchivePdfUrl = value; }
    public String getFinalArchivePdfHash() { return finalArchivePdfHash; }
    public void setFinalArchivePdfHash(String value) { finalArchivePdfHash = value; }
    public Boolean getSignedFileAvailable() { return signedFileAvailable; }
    public void setSignedFileAvailable(Boolean value) { signedFileAvailable = value; }
    public Boolean getCertificateAvailable() { return certificateAvailable; }
    public void setCertificateAvailable(Boolean value) { certificateAvailable = value; }
    public Boolean getFinalFileAvailable() { return finalFileAvailable; }
    public void setFinalFileAvailable(Boolean value) { finalFileAvailable = value; }

    public String getSignatureFileUrl() { return signatureFileUrl; }
    public void setSignatureFileUrl(String signatureFileUrl) { this.signatureFileUrl = signatureFileUrl; }

    public String getSignatureHash() { return signatureHash; }
    public void setSignatureHash(String signatureHash) { this.signatureHash = signatureHash; }

    public String getCertificateHash() { return certificateHash; }
    public void setCertificateHash(String certificateHash) { this.certificateHash = certificateHash; }

    public String getDocumentVersion() { return documentVersion; }
    public void setDocumentVersion(String documentVersion) { this.documentVersion = documentVersion; }

    public String getFinalDocumentVersion() { return finalDocumentVersion; }
    public void setFinalDocumentVersion(String finalDocumentVersion) { this.finalDocumentVersion = finalDocumentVersion; }

    public String getFinalReadConfirmed() { return finalReadConfirmed; }
    public void setFinalReadConfirmed(String finalReadConfirmed) { this.finalReadConfirmed = finalReadConfirmed; }
    public Date getFinalReadConfirmedTime() { return finalReadConfirmedTime; }
    public void setFinalReadConfirmedTime(Date value) { finalReadConfirmedTime = value; }

    public String getEmployeeVisible() { return employeeVisible; }
    public void setEmployeeVisible(String employeeVisible) { this.employeeVisible = employeeVisible; }

    public String getReadConfirmationRequired() { return readConfirmationRequired; }
    public void setReadConfirmationRequired(String readConfirmationRequired) { this.readConfirmationRequired = readConfirmationRequired; }

    public String getEmployeeSignRequired() { return employeeSignRequired; }
    public void setEmployeeSignRequired(String employeeSignRequired) { this.employeeSignRequired = employeeSignRequired; }

    public String getSignaturePositionJson() { return signaturePositionJson; }
    public void setSignaturePositionJson(String signaturePositionJson) { this.signaturePositionJson = signaturePositionJson; }

    public String getCompanySealPositionJson() { return companySealPositionJson; }
    public void setCompanySealPositionJson(String companySealPositionJson) { this.companySealPositionJson = companySealPositionJson; }

    public String getCompanySealRequired() { return companySealRequired; }
    public void setCompanySealRequired(String companySealRequired) { this.companySealRequired = companySealRequired; }

    public String getDocumentPolicyMode() { return documentPolicyMode; }
    public void setDocumentPolicyMode(String documentPolicyMode) { this.documentPolicyMode = documentPolicyMode; }

    public String getReadConfirmed() { return readConfirmed; }
    public void setReadConfirmed(String readConfirmed) { this.readConfirmed = readConfirmed; }

    public String getSigned() { return signed; }
    public void setSigned(String signed) { this.signed = signed; }

    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}
