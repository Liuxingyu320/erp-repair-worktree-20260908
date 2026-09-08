package com.erp.system.domain.vo;

import java.util.ArrayList;
import java.util.List;

/**
 * 用户店铺授权范围视图。
 */
public class SysUserShopScopeVo
{
    private List<Long> shopIds = new ArrayList<Long>();

    private List<Long> editableShopIds = new ArrayList<Long>();

    private List<Long> preservedShopIds = new ArrayList<Long>();

    private int outOfScopeCount;

    public List<Long> getShopIds()
    {
        return shopIds;
    }

    public void setShopIds(List<Long> shopIds)
    {
        this.shopIds = shopIds == null ? new ArrayList<Long>() : shopIds;
    }

    public List<Long> getEditableShopIds()
    {
        return editableShopIds;
    }

    public void setEditableShopIds(List<Long> editableShopIds)
    {
        this.editableShopIds = editableShopIds == null ? new ArrayList<Long>() : editableShopIds;
    }

    public List<Long> getPreservedShopIds()
    {
        return preservedShopIds;
    }

    public void setPreservedShopIds(List<Long> preservedShopIds)
    {
        this.preservedShopIds = preservedShopIds == null ? new ArrayList<Long>() : preservedShopIds;
    }

    public int getOutOfScopeCount()
    {
        return outOfScopeCount;
    }

    public void setOutOfScopeCount(int outOfScopeCount)
    {
        this.outOfScopeCount = outOfScopeCount;
    }
}
