package com.erp.oa.domain.vo;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

public class OaSignOnboardDataRequestView
{
    @JsonSerialize(using = ToStringSerializer.class)
    private Long requestId;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long rowId;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long taskId;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long packageId;
    private String requestNo;
    private String status;
    private String signingSequence;
    private List<String> allowedFields = new ArrayList<>();
    private Map<String, Object> factSnapshot = new LinkedHashMap<>();
    /** Server-calculated file list from the frozen plan version and current row facts. */
    private List<String> plannedDocumentNames = new ArrayList<>();
    private Map<String, Object> submittedValues = new LinkedHashMap<>();
    private String reviewReason;
    private String profileSyncStatus;
    private Date submittedTime;
    private boolean signatureCaptured;
    private Date signatureSampleTime;
    private String signatureConfirmationPrompt;
    private Long version;

    public Long getRequestId() { return requestId; }
    public void setRequestId(Long value) { requestId = value; }
    public Long getRowId() { return rowId; }
    public void setRowId(Long value) { rowId = value; }
    public Long getTaskId() { return taskId; }
    public void setTaskId(Long value) { taskId = value; }
    public Long getPackageId() { return packageId; }
    public void setPackageId(Long value) { packageId = value; }
    public String getRequestNo() { return requestNo; }
    public void setRequestNo(String value) { requestNo = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { status = value; }
    public String getSigningSequence() { return signingSequence; }
    public void setSigningSequence(String value) { signingSequence = value; }
    public List<String> getAllowedFields() { return new ArrayList<>(allowedFields); }
    public void setAllowedFields(List<String> value)
    {
        allowedFields = value == null ? new ArrayList<>() : new ArrayList<>(value);
    }
    public Map<String, Object> getFactSnapshot() { return new LinkedHashMap<>(factSnapshot); }
    public void setFactSnapshot(Map<String, Object> value)
    {
        factSnapshot = value == null ? new LinkedHashMap<>() : new LinkedHashMap<>(value);
    }
    public List<String> getPlannedDocumentNames() { return new ArrayList<>(plannedDocumentNames); }
    public void setPlannedDocumentNames(List<String> value)
    {
        plannedDocumentNames = value == null ? new ArrayList<>() : new ArrayList<>(value);
    }
    public Map<String, Object> getSubmittedValues() { return new LinkedHashMap<>(submittedValues); }
    public void setSubmittedValues(Map<String, Object> value)
    {
        submittedValues = value == null ? new LinkedHashMap<>() : new LinkedHashMap<>(value);
    }
    public String getReviewReason() { return reviewReason; }
    public void setReviewReason(String value) { reviewReason = value; }
    public String getProfileSyncStatus() { return profileSyncStatus; }
    public void setProfileSyncStatus(String value) { profileSyncStatus = value; }
    public Date getSubmittedTime() { return submittedTime; }
    public void setSubmittedTime(Date value) { submittedTime = value; }
    public boolean isSignatureCaptured() { return signatureCaptured; }
    public void setSignatureCaptured(boolean value) { signatureCaptured = value; }
    public Date getSignatureSampleTime() { return signatureSampleTime; }
    public void setSignatureSampleTime(Date value) { signatureSampleTime = value; }
    public String getSignatureConfirmationPrompt() { return signatureConfirmationPrompt; }
    public void setSignatureConfirmationPrompt(String value) { signatureConfirmationPrompt = value; }
    public Long getVersion() { return version; }
    public void setVersion(Long value) { version = value; }
}
