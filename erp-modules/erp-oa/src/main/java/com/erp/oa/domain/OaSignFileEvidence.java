package com.erp.oa.domain;

import java.util.Date;
import com.erp.common.core.web.domain.BaseEntity;
import com.erp.oa.constant.OaSignFileEvidenceType;

/**
 * Immutable metadata for one generated or supplied signing file.
 */
public class OaSignFileEvidence extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long evidenceId;
    private Long packageId;
    private Long documentId;
    private String documentVersion;
    private OaSignFileEvidenceType evidenceType;
    private String fileUrl;
    private String fileHash;
    private Long fileSize;
    private Long sourceEvidenceId;
    private Date generatedTime;

    public Long getEvidenceId() { return evidenceId; }
    public void setEvidenceId(Long evidenceId) { this.evidenceId = evidenceId; }

    public Long getPackageId() { return packageId; }
    public void setPackageId(Long packageId) { this.packageId = packageId; }

    public Long getDocumentId() { return documentId; }
    public void setDocumentId(Long documentId) { this.documentId = documentId; }

    public String getDocumentVersion() { return documentVersion; }
    public void setDocumentVersion(String documentVersion) { this.documentVersion = documentVersion; }

    public OaSignFileEvidenceType getEvidenceType() { return evidenceType; }
    public void setEvidenceType(OaSignFileEvidenceType evidenceType) { this.evidenceType = evidenceType; }

    public String getFileUrl() { return fileUrl; }
    public void setFileUrl(String fileUrl) { this.fileUrl = fileUrl; }

    public String getFileHash() { return fileHash; }
    public void setFileHash(String fileHash) { this.fileHash = fileHash; }

    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }

    public Long getSourceEvidenceId() { return sourceEvidenceId; }
    public void setSourceEvidenceId(Long sourceEvidenceId) { this.sourceEvidenceId = sourceEvidenceId; }

    public Date getGeneratedTime() { return generatedTime; }
    public void setGeneratedTime(Date generatedTime) { this.generatedTime = generatedTime; }
}
