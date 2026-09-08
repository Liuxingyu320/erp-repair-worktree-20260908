package com.erp.system.domain.vo;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.erp.common.core.web.domain.BaseEntity;

/**
 * HR入职单查询条件。
 */
public class HrOnboardingQuery extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long onboardingId;
    private Long linkedUserId;
    private String keyword;
    private String status;
    private Long targetDeptId;
    private Long targetStoreId;
    private String employeeCategory;
    private Long ownerUserId;
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private Date expectedEntryDateFrom;
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private Date expectedEntryDateTo;
    @JsonIgnore
    private Date summaryDate;

    public Long getOnboardingId() { return onboardingId; }
    public void setOnboardingId(Long onboardingId) { this.onboardingId = onboardingId; }
    public Long getLinkedUserId() { return linkedUserId; }
    public void setLinkedUserId(Long linkedUserId) { this.linkedUserId = linkedUserId; }
    public String getKeyword() { return keyword; }
    public void setKeyword(String keyword) { this.keyword = keyword; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getTargetDeptId() { return targetDeptId; }
    public void setTargetDeptId(Long targetDeptId) { this.targetDeptId = targetDeptId; }
    public Long getTargetStoreId() { return targetStoreId; }
    public void setTargetStoreId(Long targetStoreId) { this.targetStoreId = targetStoreId; }
    public String getEmployeeCategory() { return employeeCategory; }
    public void setEmployeeCategory(String employeeCategory) { this.employeeCategory = employeeCategory; }
    public Long getOwnerUserId() { return ownerUserId; }
    public void setOwnerUserId(Long ownerUserId) { this.ownerUserId = ownerUserId; }
    public Date getExpectedEntryDateFrom() { return expectedEntryDateFrom; }
    public void setExpectedEntryDateFrom(Date expectedEntryDateFrom) { this.expectedEntryDateFrom = expectedEntryDateFrom; }
    public Date getExpectedEntryDateTo() { return expectedEntryDateTo; }
    public void setExpectedEntryDateTo(Date expectedEntryDateTo) { this.expectedEntryDateTo = expectedEntryDateTo; }
    public Date getSummaryDate() { return summaryDate; }
    public void setSummaryDate(Date summaryDate) { this.summaryDate = summaryDate; }
}
