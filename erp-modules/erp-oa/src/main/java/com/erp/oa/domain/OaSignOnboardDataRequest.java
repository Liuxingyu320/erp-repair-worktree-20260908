package com.erp.oa.domain;

import java.util.Date;

/** Employee personal-fact completion request. HR-only fields never enter allowedFieldsJson. */
public class OaSignOnboardDataRequest
{
    private Long requestId;
    private String requestNo;
    private Long batchId;
    private Long rowId;
    private Long employeeId;
    private String allowedFieldsJson;
    /** COMPANY_FIRST or SIGNATURE_FIRST, frozen when HR creates this request. */
    private String signingSequence;
    /** Read-only web fact sheet shown before a signature-first sample is captured. */
    private String factSnapshotJson;
    /** Versioned employee-visible fact + planned-document confirmation contract. */
    private String confirmationSnapshotVersion;
    private String confirmationSnapshotHash;
    private String submittedValuesJson;
    /** HR-only facts frozen at the first approval; never returned to the employee view. */
    private String approvedHrValuesJson;
    private String status;
    private Date submittedTime;
    private String factConfirmationText;
    private String signatureRequestId;
    /** Canonical hash of submitted facts, confirmation phrase and signature PNG hash. */
    private String signaturePayloadHash;
    /** Task/request-scoped sample bytes; never exposed through the employee view. */
    private byte[] signatureSampleBytes;
    private String signatureSampleHash;
    private Date signatureSampleTime;
    private Long reviewedByUserId;
    private String reviewReason;
    private Date reviewedTime;
    private String profileSyncStatus;
    private String profileSyncRequestId;
    private String profileBeforeHash;
    private String profileAfterHash;
    private Long version;
    private Date createTime;
    private Date updateTime;

    public Long getRequestId() { return requestId; }
    public void setRequestId(Long requestId) { this.requestId = requestId; }
    public String getRequestNo() { return requestNo; }
    public void setRequestNo(String requestNo) { this.requestNo = requestNo; }
    public Long getBatchId() { return batchId; }
    public void setBatchId(Long batchId) { this.batchId = batchId; }
    public Long getRowId() { return rowId; }
    public void setRowId(Long rowId) { this.rowId = rowId; }
    public Long getEmployeeId() { return employeeId; }
    public void setEmployeeId(Long employeeId) { this.employeeId = employeeId; }
    public String getAllowedFieldsJson() { return allowedFieldsJson; }
    public void setAllowedFieldsJson(String allowedFieldsJson) { this.allowedFieldsJson = allowedFieldsJson; }
    public String getSigningSequence() { return signingSequence; }
    public void setSigningSequence(String value) { signingSequence = value; }
    public String getFactSnapshotJson() { return factSnapshotJson; }
    public void setFactSnapshotJson(String value) { factSnapshotJson = value; }
    public String getConfirmationSnapshotVersion() { return confirmationSnapshotVersion; }
    public void setConfirmationSnapshotVersion(String value) { confirmationSnapshotVersion = value; }
    public String getConfirmationSnapshotHash() { return confirmationSnapshotHash; }
    public void setConfirmationSnapshotHash(String value) { confirmationSnapshotHash = value; }
    public String getSubmittedValuesJson() { return submittedValuesJson; }
    public void setSubmittedValuesJson(String submittedValuesJson) { this.submittedValuesJson = submittedValuesJson; }
    public String getApprovedHrValuesJson() { return approvedHrValuesJson; }
    public void setApprovedHrValuesJson(String approvedHrValuesJson) { this.approvedHrValuesJson = approvedHrValuesJson; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Date getSubmittedTime() { return submittedTime; }
    public void setSubmittedTime(Date submittedTime) { this.submittedTime = submittedTime; }
    public String getFactConfirmationText() { return factConfirmationText; }
    public void setFactConfirmationText(String value) { factConfirmationText = value; }
    public String getSignatureRequestId() { return signatureRequestId; }
    public void setSignatureRequestId(String value) { signatureRequestId = value; }
    public String getSignaturePayloadHash() { return signaturePayloadHash; }
    public void setSignaturePayloadHash(String value) { signaturePayloadHash = value; }
    public byte[] getSignatureSampleBytes()
    {
        return signatureSampleBytes == null ? null : signatureSampleBytes.clone();
    }
    public void setSignatureSampleBytes(byte[] value)
    {
        signatureSampleBytes = value == null ? null : value.clone();
    }
    public String getSignatureSampleHash() { return signatureSampleHash; }
    public void setSignatureSampleHash(String value) { signatureSampleHash = value; }
    public Date getSignatureSampleTime() { return signatureSampleTime; }
    public void setSignatureSampleTime(Date value) { signatureSampleTime = value; }
    public Long getReviewedByUserId() { return reviewedByUserId; }
    public void setReviewedByUserId(Long reviewedByUserId) { this.reviewedByUserId = reviewedByUserId; }
    public String getReviewReason() { return reviewReason; }
    public void setReviewReason(String reviewReason) { this.reviewReason = reviewReason; }
    public Date getReviewedTime() { return reviewedTime; }
    public void setReviewedTime(Date reviewedTime) { this.reviewedTime = reviewedTime; }
    public String getProfileSyncStatus() { return profileSyncStatus; }
    public void setProfileSyncStatus(String profileSyncStatus) { this.profileSyncStatus = profileSyncStatus; }
    public String getProfileSyncRequestId() { return profileSyncRequestId; }
    public void setProfileSyncRequestId(String profileSyncRequestId) { this.profileSyncRequestId = profileSyncRequestId; }
    public String getProfileBeforeHash() { return profileBeforeHash; }
    public void setProfileBeforeHash(String profileBeforeHash) { this.profileBeforeHash = profileBeforeHash; }
    public String getProfileAfterHash() { return profileAfterHash; }
    public void setProfileAfterHash(String profileAfterHash) { this.profileAfterHash = profileAfterHash; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }
    public Date getUpdateTime() { return updateTime; }
    public void setUpdateTime(Date updateTime) { this.updateTime = updateTime; }
}
