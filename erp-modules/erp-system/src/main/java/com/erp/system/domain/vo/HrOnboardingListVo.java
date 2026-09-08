package com.erp.system.domain.vo;

import java.io.Serializable;
import java.util.Date;
import java.util.ArrayList;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * HR入职单列表安全视图。
 */
public class HrOnboardingListVo implements Serializable
{
    private static final long serialVersionUID = 1L;

    private Long onboardingId;
    private String onboardingNo;
    private String employeeName;
    private String phoneNumberMasked;
    private Long targetDeptId;
    private Long targetStoreId;
    private Long targetPostId;
    private String companyName;
    private String deptLevel1Name;
    private String deptLevel2Name;
    private String deptLevel3Name;
    private String storeName;
    private String positionName;
    private String employeeCategory;
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private Date expectedEntryDate;
    private String status;
    private Long ownerUserId;
    private String ownerName;
    private Integer version;
    private Integer missingCount = 0;
    private List<String> allowedActions = new ArrayList<>();
    private String currentAction;

    public Long getOnboardingId() { return onboardingId; }
    public void setOnboardingId(Long onboardingId) { this.onboardingId = onboardingId; }
    public String getOnboardingNo() { return onboardingNo; }
    public void setOnboardingNo(String onboardingNo) { this.onboardingNo = onboardingNo; }
    public String getEmployeeName() { return employeeName; }
    public void setEmployeeName(String employeeName) { this.employeeName = employeeName; }
    public String getPhoneNumberMasked() { return phoneNumberMasked; }
    public void setPhoneNumberMasked(String phoneNumberMasked) { this.phoneNumberMasked = phoneNumberMasked; }
    public Long getTargetDeptId() { return targetDeptId; }
    public void setTargetDeptId(Long targetDeptId) { this.targetDeptId = targetDeptId; }
    public Long getTargetStoreId() { return targetStoreId; }
    public void setTargetStoreId(Long targetStoreId) { this.targetStoreId = targetStoreId; }
    public Long getTargetPostId() { return targetPostId; }
    public void setTargetPostId(Long targetPostId) { this.targetPostId = targetPostId; }
    public String getCompanyName() { return companyName; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }
    public String getDeptLevel1Name() { return deptLevel1Name; }
    public void setDeptLevel1Name(String deptLevel1Name) { this.deptLevel1Name = deptLevel1Name; }
    public String getDeptLevel2Name() { return deptLevel2Name; }
    public void setDeptLevel2Name(String deptLevel2Name) { this.deptLevel2Name = deptLevel2Name; }
    public String getDeptLevel3Name() { return deptLevel3Name; }
    public void setDeptLevel3Name(String deptLevel3Name) { this.deptLevel3Name = deptLevel3Name; }
    public String getStoreName() { return storeName; }
    public void setStoreName(String storeName) { this.storeName = storeName; }
    public String getPositionName() { return positionName; }
    public void setPositionName(String positionName) { this.positionName = positionName; }
    public String getEmployeeCategory() { return employeeCategory; }
    public void setEmployeeCategory(String employeeCategory) { this.employeeCategory = employeeCategory; }
    public Date getExpectedEntryDate() { return expectedEntryDate; }
    public void setExpectedEntryDate(Date expectedEntryDate) { this.expectedEntryDate = expectedEntryDate; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getOwnerUserId() { return ownerUserId; }
    public void setOwnerUserId(Long ownerUserId) { this.ownerUserId = ownerUserId; }
    public String getOwnerName() { return ownerName; }
    public void setOwnerName(String ownerName) { this.ownerName = ownerName; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public Integer getMissingCount() { return missingCount; }
    public void setMissingCount(Integer missingCount) { this.missingCount = missingCount; }
    public List<String> getAllowedActions() { return allowedActions; }
    public void setAllowedActions(List<String> allowedActions) { this.allowedActions = allowedActions; }
    public String getCurrentAction() { return currentAction; }
    public void setCurrentAction(String currentAction) { this.currentAction = currentAction; }
}
