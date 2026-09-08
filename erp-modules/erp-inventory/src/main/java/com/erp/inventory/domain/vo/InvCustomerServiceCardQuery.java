package com.erp.inventory.domain.vo;

import com.erp.common.core.web.domain.BaseEntity;

public class InvCustomerServiceCardQuery extends BaseEntity
{
    private static final long serialVersionUID = 1L;
    private String keyword;
    private String preferenceTag;
    private String status;
    private Long shopDeptId;

    public String getKeyword() { return keyword; }
    public void setKeyword(String value) { keyword = value; }
    public String getPreferenceTag() { return preferenceTag; }
    public void setPreferenceTag(String value) { preferenceTag = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { status = value; }
    public Long getShopDeptId() { return shopDeptId; }
    public void setShopDeptId(Long value) { shopDeptId = value; }
}
