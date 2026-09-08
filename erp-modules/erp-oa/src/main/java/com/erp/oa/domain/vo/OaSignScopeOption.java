package com.erp.oa.domain.vo;

public class OaSignScopeOption
{
    private Long deptId;
    private String deptName;
    private String deptType;
    private Long parentId;

    public Long getDeptId()
    {
        return deptId;
    }

    public void setDeptId(Long deptId)
    {
        this.deptId = deptId;
    }

    public String getDeptName()
    {
        return deptName;
    }

    public void setDeptName(String deptName)
    {
        this.deptName = deptName;
    }

    public String getDeptType()
    {
        return deptType;
    }

    public void setDeptType(String deptType)
    {
        this.deptType = deptType;
    }

    public Long getParentId()
    {
        return parentId;
    }

    public void setParentId(Long parentId)
    {
        this.parentId = parentId;
    }
}
