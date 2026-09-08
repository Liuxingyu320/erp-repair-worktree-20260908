package com.erp.oa.domain.vo;

/** 员工最新入职签约包及最新已签包的最小事实投影。 */
public class OaOnboardPackageFact
{
    private Long employeeId;
    private Long latestPackageId;
    private Long latestTaskId;
    private String latestStatus;
    private Long latestShopDeptId;
    private Long signedPackageId;
    private Long signedTaskId;
    private Long signedShopDeptId;
    private Boolean signedEvidenceComplete;

    public Long getEmployeeId() { return employeeId; }
    public void setEmployeeId(Long employeeId) { this.employeeId = employeeId; }
    public Long getLatestPackageId() { return latestPackageId; }
    public void setLatestPackageId(Long latestPackageId) { this.latestPackageId = latestPackageId; }
    public Long getLatestTaskId() { return latestTaskId; }
    public void setLatestTaskId(Long latestTaskId) { this.latestTaskId = latestTaskId; }
    public String getLatestStatus() { return latestStatus; }
    public void setLatestStatus(String latestStatus) { this.latestStatus = latestStatus; }
    public Long getLatestShopDeptId() { return latestShopDeptId; }
    public void setLatestShopDeptId(Long latestShopDeptId) { this.latestShopDeptId = latestShopDeptId; }
    public Long getSignedPackageId() { return signedPackageId; }
    public void setSignedPackageId(Long signedPackageId) { this.signedPackageId = signedPackageId; }
    public Long getSignedTaskId() { return signedTaskId; }
    public void setSignedTaskId(Long signedTaskId) { this.signedTaskId = signedTaskId; }
    public Long getSignedShopDeptId() { return signedShopDeptId; }
    public void setSignedShopDeptId(Long signedShopDeptId) { this.signedShopDeptId = signedShopDeptId; }
    public Boolean getSignedEvidenceComplete() { return signedEvidenceComplete; }
    public void setSignedEvidenceComplete(Boolean signedEvidenceComplete)
    {
        this.signedEvidenceComplete = signedEvidenceComplete;
    }
}
