package com.erp.approval.domain.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class ApprovalPublishRequest
{
    @NotNull
    private Long expectedLockVersion;
    @Size(max = 500)
    private String remark;
    public Long getExpectedLockVersion() { return expectedLockVersion; }
    public void setExpectedLockVersion(Long expectedLockVersion) { this.expectedLockVersion = expectedLockVersion; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
