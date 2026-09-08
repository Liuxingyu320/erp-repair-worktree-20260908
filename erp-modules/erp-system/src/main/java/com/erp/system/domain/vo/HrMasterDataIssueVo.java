package com.erp.system.domain.vo;

import com.erp.common.core.annotation.Excel;

/** One actionable read-only finding in the HR master-data governance queue. */
public class HrMasterDataIssueVo
{
    @Excel(name = "问题代码") private String issueCode;
    @Excel(name = "问题名称") private String issueName;
    @Excel(name = "优先级") private String severity;
    private String riskLevel;
    @Excel(name = "资源类型") private String resourceType;
    @Excel(name = "资源ID") private String resourceId;
    @Excel(name = "资源名称") private String resourceName;
    private Long deptId;
    @Excel(name = "组织") private String deptName;
    @Excel(name = "人员类别") private String employeeCategory;
    @Excel(name = "影响员工数") private long affectedEmployeeCount;
    @Excel(name = "问题说明") private String detail;
    @Excel(name = "处理建议") private String suggestion;
    private String actionUrl;

    public String getIssueCode(){return issueCode;} public void setIssueCode(String v){issueCode=v;}
    public String getIssueName(){return issueName;} public void setIssueName(String v){issueName=v;}
    public String getSeverity(){return severity;} public void setSeverity(String v){severity=v;}
    public String getRiskLevel(){return riskLevel;} public void setRiskLevel(String v){riskLevel=v;}
    public String getResourceType(){return resourceType;} public void setResourceType(String v){resourceType=v;}
    public String getResourceId(){return resourceId;} public void setResourceId(String v){resourceId=v;}
    public String getResourceName(){return resourceName;} public void setResourceName(String v){resourceName=v;}
    public Long getDeptId(){return deptId;} public void setDeptId(Long v){deptId=v;}
    public String getDeptName(){return deptName;} public void setDeptName(String v){deptName=v;}
    public String getEmployeeCategory(){return employeeCategory;} public void setEmployeeCategory(String v){employeeCategory=v;}
    public long getAffectedEmployeeCount(){return affectedEmployeeCount;} public void setAffectedEmployeeCount(long v){affectedEmployeeCount=v;}
    public String getDetail(){return detail;} public void setDetail(String v){detail=v;}
    public String getSuggestion(){return suggestion;} public void setSuggestion(String v){suggestion=v;}
    public String getActionUrl(){return actionUrl;} public void setActionUrl(String v){actionUrl=v;}
}
