package com.erp.system.api.domain;

/**
 * 签约候选员工查询条件
 */
public class SignCandidateUserQuery
{
    private String postName;

    private Long deptId;

    private String keyword;

    private Long[] userIds;

    /** Exact mobile numbers used by the Excel-only onboarding match. */
    private String[] phoneNumbers;

    /**
     * Narrowly scoped switch for the Excel onboarding matcher to classify
     * disabled/departed records. Normal signing selectors keep this false so an
     * inactive account can never become a signable candidate by accident.
     */
    private Boolean includeInactiveEmployees;

    private Integer limit;

    public String getPostName()
    {
        return postName;
    }

    public void setPostName(String postName)
    {
        this.postName = postName;
    }

    public Long getDeptId()
    {
        return deptId;
    }

    public void setDeptId(Long deptId)
    {
        this.deptId = deptId;
    }

    public String getKeyword()
    {
        return keyword;
    }

    public void setKeyword(String keyword)
    {
        this.keyword = keyword;
    }

    public Long[] getUserIds()
    {
        return userIds;
    }

    public void setUserIds(Long[] userIds)
    {
        this.userIds = userIds;
    }

    public String[] getPhoneNumbers()
    {
        return phoneNumbers;
    }

    public void setPhoneNumbers(String[] phoneNumbers)
    {
        this.phoneNumbers = phoneNumbers;
    }

    public Boolean getIncludeInactiveEmployees()
    {
        return includeInactiveEmployees;
    }

    public void setIncludeInactiveEmployees(Boolean includeInactiveEmployees)
    {
        this.includeInactiveEmployees = includeInactiveEmployees;
    }

    public Integer getLimit()
    {
        return limit;
    }

    public void setLimit(Integer limit)
    {
        this.limit = limit;
    }
}
