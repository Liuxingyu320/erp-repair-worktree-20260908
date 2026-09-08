package com.erp.oa.domain.vo;

import java.util.Date;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.erp.oa.constant.OaSignFileEvidenceType;
import com.erp.oa.constant.OaSignVerificationStatus;

/**
 * Business-facing verification result. Raw hashes are only populated after a
 * separate technical-evidence permission check.
 */
public class OaSignVerificationResult
{
    private OaSignVerificationStatus status;
    private String message;
    private Date checkedTime;
    private List<DocumentResult> documentResults;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private List<TechnicalEvidence> technicalEvidence;

    public OaSignVerificationStatus getStatus() { return status; }
    public void setStatus(OaSignVerificationStatus status) { this.status = status; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public Date getCheckedTime() { return checkedTime; }
    public void setCheckedTime(Date checkedTime) { this.checkedTime = checkedTime; }

    public List<DocumentResult> getDocumentResults() { return documentResults; }
    public void setDocumentResults(List<DocumentResult> documentResults) { this.documentResults = documentResults; }

    public List<TechnicalEvidence> getTechnicalEvidence() { return technicalEvidence; }
    public void setTechnicalEvidence(List<TechnicalEvidence> technicalEvidence)
    {
        this.technicalEvidence = technicalEvidence;
    }

    public static class DocumentResult
    {
        private Long documentId;
        private String documentName;
        private OaSignVerificationStatus status;
        private String message;

        public Long getDocumentId() { return documentId; }
        public void setDocumentId(Long documentId) { this.documentId = documentId; }

        public String getDocumentName() { return documentName; }
        public void setDocumentName(String documentName) { this.documentName = documentName; }

        public OaSignVerificationStatus getStatus() { return status; }
        public void setStatus(OaSignVerificationStatus status) { this.status = status; }

        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }

    public static class TechnicalEvidence
    {
        private Long evidenceId;
        private Long documentId;
        private OaSignFileEvidenceType evidenceType;
        private String fileUrl;
        private String expectedHash;
        private String actualHash;
        private Long expectedSize;
        private Long actualSize;
        private boolean matches;

        public Long getEvidenceId() { return evidenceId; }
        public void setEvidenceId(Long evidenceId) { this.evidenceId = evidenceId; }

        public Long getDocumentId() { return documentId; }
        public void setDocumentId(Long documentId) { this.documentId = documentId; }

        public OaSignFileEvidenceType getEvidenceType() { return evidenceType; }
        public void setEvidenceType(OaSignFileEvidenceType evidenceType) { this.evidenceType = evidenceType; }

        public String getFileUrl() { return fileUrl; }
        public void setFileUrl(String fileUrl) { this.fileUrl = fileUrl; }

        public String getExpectedHash() { return expectedHash; }
        public void setExpectedHash(String expectedHash) { this.expectedHash = expectedHash; }

        public String getActualHash() { return actualHash; }
        public void setActualHash(String actualHash) { this.actualHash = actualHash; }

        public Long getExpectedSize() { return expectedSize; }
        public void setExpectedSize(Long expectedSize) { this.expectedSize = expectedSize; }

        public Long getActualSize() { return actualSize; }
        public void setActualSize(Long actualSize) { this.actualSize = actualSize; }

        public boolean isMatches() { return matches; }
        public void setMatches(boolean matches) { this.matches = matches; }
    }
}
