package com.erp.system.domain;

import java.util.Date;

/** Privacy-safe idempotency and audit record for one reviewed profile supplement. */
public class SysSignProfileSupplementAudit
{
    private Long auditId;
    private String requestId;
    private Long employeeId;
    private String requestHash;
    private String fieldMask;
    private String beforeHash;
    private String afterHash;
    private String status;
    private String createBy;
    private Date createTime;
    private Date updateTime;

    public Long getAuditId() { return auditId; }
    public void setAuditId(Long auditId) { this.auditId = auditId; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public Long getEmployeeId() { return employeeId; }
    public void setEmployeeId(Long employeeId) { this.employeeId = employeeId; }
    public String getRequestHash() { return requestHash; }
    public void setRequestHash(String requestHash) { this.requestHash = requestHash; }
    public String getFieldMask() { return fieldMask; }
    public void setFieldMask(String fieldMask) { this.fieldMask = fieldMask; }
    public String getBeforeHash() { return beforeHash; }
    public void setBeforeHash(String beforeHash) { this.beforeHash = beforeHash; }
    public String getAfterHash() { return afterHash; }
    public void setAfterHash(String afterHash) { this.afterHash = afterHash; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getCreateBy() { return createBy; }
    public void setCreateBy(String createBy) { this.createBy = createBy; }
    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }
    public Date getUpdateTime() { return updateTime; }
    public void setUpdateTime(Date updateTime) { this.updateTime = updateTime; }
}
