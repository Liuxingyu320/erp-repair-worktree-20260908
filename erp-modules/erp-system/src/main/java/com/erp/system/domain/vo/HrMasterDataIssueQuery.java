package com.erp.system.domain.vo;

import com.erp.common.core.web.domain.BaseEntity;

/** Filters for the in-memory, data-scoped master-data readiness queue. */
public class HrMasterDataIssueQuery extends BaseEntity
{
    private static final long serialVersionUID = 1L;
    private String issueCode;
    private String severity;
    private String resourceType;
    private String keyword;
    private Long deptId;
    private Boolean affectedOnly;

    public String getIssueCode(){return issueCode;} public void setIssueCode(String v){issueCode=v;}
    public String getSeverity(){return severity;} public void setSeverity(String v){severity=v;}
    public String getResourceType(){return resourceType;} public void setResourceType(String v){resourceType=v;}
    public String getKeyword(){return keyword;} public void setKeyword(String v){keyword=v;}
    public Long getDeptId(){return deptId;} public void setDeptId(Long v){deptId=v;}
    public Boolean getAffectedOnly(){return affectedOnly;} public void setAffectedOnly(Boolean v){affectedOnly=v;}
}
