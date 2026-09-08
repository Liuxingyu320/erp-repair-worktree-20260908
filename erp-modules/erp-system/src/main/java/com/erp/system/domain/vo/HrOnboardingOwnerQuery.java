package com.erp.system.domain.vo;

import java.util.ArrayList;
import java.util.List;
import com.erp.common.core.web.domain.BaseEntity;

/** Scoped, paged owner-picker search criteria. */
public class HrOnboardingOwnerQuery extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private String keyword;
    private Long deptId;
    private Boolean includeChildren = Boolean.TRUE;
    private List<Long> userIds = new ArrayList<>();

    public String getKeyword() { return keyword; }
    public void setKeyword(String keyword) { this.keyword = keyword; }
    public Long getDeptId() { return deptId; }
    public void setDeptId(Long deptId) { this.deptId = deptId; }
    public Boolean getIncludeChildren() { return includeChildren; }
    public void setIncludeChildren(Boolean includeChildren)
    {
        this.includeChildren = includeChildren == null ? Boolean.TRUE : includeChildren;
    }
    public List<Long> getUserIds() { return userIds; }
    public void setUserIds(List<Long> userIds)
    {
        this.userIds = userIds == null ? new ArrayList<>() : new ArrayList<>(userIds);
    }
}
