package com.erp.system.domain.dto;

import java.util.ArrayList;
import java.util.List;
import jakarta.validation.constraints.Size;
import com.fasterxml.jackson.annotation.JsonAnySetter;

/** Request shared by organization-scope preview and save. */
public class SysUserShopScopeChangeRequest
{
    @Size(max = 2000, message = "单次组织授权不能超过2000项")
    private List<Long> shopDeptIds = new ArrayList<Long>();

    @Size(max = 64, message = "授权范围版本格式不正确")
    private String scopeVersion;

    public List<Long> getShopDeptIds()
    {
        return shopDeptIds;
    }

    public void setShopDeptIds(List<Long> shopDeptIds)
    {
        this.shopDeptIds = shopDeptIds == null ? new ArrayList<Long>() : shopDeptIds;
    }

    public String getScopeVersion()
    {
        return scopeVersion;
    }

    public void setScopeVersion(String scopeVersion)
    {
        this.scopeVersion = scopeVersion;
    }

    @JsonAnySetter
    public void rejectUnknownField(String field, Object ignored)
    {
        throw new IllegalArgumentException("组织授权接口不接受字段: " + field);
    }
}
