package com.erp.system.domain;

/** Metadata-only PII access audit record. It must never contain personal values. */
public class SysUserPiiAccessAudit
{
    private Long viewerUserId;
    private Long targetUserId;
    private String reasonCode;
    private String actionCode;
    private String resultCode;
    private String changedFields;
    private Integer rowCount;
    private String datasetDigest;

    public Long getViewerUserId() { return viewerUserId; }
    public void setViewerUserId(Long viewerUserId) { this.viewerUserId = viewerUserId; }
    public Long getTargetUserId() { return targetUserId; }
    public void setTargetUserId(Long targetUserId) { this.targetUserId = targetUserId; }
    public String getReasonCode() { return reasonCode; }
    public void setReasonCode(String reasonCode) { this.reasonCode = reasonCode; }
    public String getActionCode() { return actionCode; }
    public void setActionCode(String actionCode) { this.actionCode = actionCode; }
    public String getResultCode() { return resultCode; }
    public void setResultCode(String resultCode) { this.resultCode = resultCode; }
    public String getChangedFields() { return changedFields; }
    public void setChangedFields(String changedFields) { this.changedFields = changedFields; }
    public Integer getRowCount() { return rowCount; }
    public void setRowCount(Integer rowCount) { this.rowCount = rowCount; }
    public String getDatasetDigest() { return datasetDigest; }
    public void setDatasetDigest(String datasetDigest) { this.datasetDigest = datasetDigest; }
}
