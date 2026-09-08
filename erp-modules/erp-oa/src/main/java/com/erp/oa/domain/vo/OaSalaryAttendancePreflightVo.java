package com.erp.oa.domain.vo;

import java.util.ArrayList;
import java.util.List;

public class OaSalaryAttendancePreflightVo
{
    private Long shopDeptId;
    private String salaryMonth;
    private boolean blocked;
    private int exceptionRecordCount;
    private int affectedEmployeeCount;
    private int returnedIssueCount;
    private boolean truncated;
    private List<OaSalaryAttendanceIssueVo> issues = new ArrayList<>();

    public Long getShopDeptId() { return shopDeptId; }
    public void setShopDeptId(Long shopDeptId) { this.shopDeptId = shopDeptId; }
    public String getSalaryMonth() { return salaryMonth; }
    public void setSalaryMonth(String salaryMonth) { this.salaryMonth = salaryMonth; }
    public boolean isBlocked() { return blocked; }
    public void setBlocked(boolean blocked) { this.blocked = blocked; }
    public int getExceptionRecordCount() { return exceptionRecordCount; }
    public void setExceptionRecordCount(int exceptionRecordCount) { this.exceptionRecordCount = exceptionRecordCount; }
    public int getAffectedEmployeeCount() { return affectedEmployeeCount; }
    public void setAffectedEmployeeCount(int affectedEmployeeCount) { this.affectedEmployeeCount = affectedEmployeeCount; }
    public int getReturnedIssueCount() { return returnedIssueCount; }
    public void setReturnedIssueCount(int returnedIssueCount) { this.returnedIssueCount = returnedIssueCount; }
    public boolean isTruncated() { return truncated; }
    public void setTruncated(boolean truncated) { this.truncated = truncated; }
    public List<OaSalaryAttendanceIssueVo> getIssues() { return issues; }
    public void setIssues(List<OaSalaryAttendanceIssueVo> issues) { this.issues = issues; }
}
