package com.erp.system.domain.hr;

import com.erp.system.api.domain.SysUserProfile;

/**
 * 人事员工档案摘要。
 */
public class HrProfileSummary
{
    private SysUserProfile profile;
    private HrCompletenessResult completeness;
    private Boolean accountBound;
    private Boolean accountEnabled;
    private String accountUserName;

    public SysUserProfile getProfile()
    {
        return profile;
    }

    public void setProfile(SysUserProfile profile)
    {
        this.profile = profile;
    }

    public HrCompletenessResult getCompleteness()
    {
        return completeness;
    }

    public void setCompleteness(HrCompletenessResult completeness)
    {
        this.completeness = completeness;
    }

    public Boolean getAccountBound()
    {
        return accountBound;
    }

    public void setAccountBound(Boolean accountBound)
    {
        this.accountBound = accountBound;
    }

    public Boolean getAccountEnabled()
    {
        return accountEnabled;
    }

    public void setAccountEnabled(Boolean accountEnabled)
    {
        this.accountEnabled = accountEnabled;
    }

    public String getAccountUserName()
    {
        return accountUserName;
    }

    public void setAccountUserName(String accountUserName)
    {
        this.accountUserName = accountUserName;
    }
}
