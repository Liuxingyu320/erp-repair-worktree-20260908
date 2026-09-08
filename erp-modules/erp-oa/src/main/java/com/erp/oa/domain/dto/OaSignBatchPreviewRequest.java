package com.erp.oa.domain.dto;

public class OaSignBatchPreviewRequest
{
    private Long planId;
    private String postName;
    private Long deptId;
    private String keyword;
    private Long[] employeeIds;

    public Long getPlanId() { return planId; }
    public void setPlanId(Long planId) { this.planId = planId; }

    public String getPostName() { return postName; }
    public void setPostName(String postName) { this.postName = postName; }

    public Long getDeptId() { return deptId; }
    public void setDeptId(Long deptId) { this.deptId = deptId; }

    public String getKeyword() { return keyword; }
    public void setKeyword(String keyword) { this.keyword = keyword; }

    public Long[] getEmployeeIds() { return employeeIds; }
    public void setEmployeeIds(Long[] employeeIds) { this.employeeIds = employeeIds; }
}
