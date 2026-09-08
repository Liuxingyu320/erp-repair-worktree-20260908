package com.erp.system.domain.vo;

import java.util.ArrayList;
import java.util.List;

/** Authoritative organization-scope difference preview. */
public class SysUserShopScopePreviewVo
{
    private List<Long> normalizedShopIds = new ArrayList<Long>();
    private List<Long> directAddedIds = new ArrayList<Long>();
    private List<Long> directRemovedIds = new ArrayList<Long>();
    private List<Long> preservedShopIds = new ArrayList<Long>();
    private List<String> warnings = new ArrayList<String>();
    private int effectiveAddedCount;
    private int effectiveRemovedCount;
    private String scopeVersion;

    public List<Long> getNormalizedShopIds() { return normalizedShopIds; }
    public void setNormalizedShopIds(List<Long> value) { normalizedShopIds = safe(value); }
    public List<Long> getDirectAddedIds() { return directAddedIds; }
    public void setDirectAddedIds(List<Long> value) { directAddedIds = safe(value); }
    public List<Long> getDirectRemovedIds() { return directRemovedIds; }
    public void setDirectRemovedIds(List<Long> value) { directRemovedIds = safe(value); }
    public List<Long> getPreservedShopIds() { return preservedShopIds; }
    public void setPreservedShopIds(List<Long> value) { preservedShopIds = safe(value); }
    public List<String> getWarnings() { return warnings; }
    public void setWarnings(List<String> value) { warnings = value == null ? new ArrayList<String>() : value; }
    public int getDirectAddedCount() { return directAddedIds.size(); }
    public int getDirectRemovedCount() { return directRemovedIds.size(); }
    public int getPreservedCount() { return preservedShopIds.size(); }
    public int getEffectiveAddedCount() { return effectiveAddedCount; }
    public void setEffectiveAddedCount(int value) { effectiveAddedCount = value; }
    public int getEffectiveRemovedCount() { return effectiveRemovedCount; }
    public void setEffectiveRemovedCount(int value) { effectiveRemovedCount = value; }
    public String getScopeVersion() { return scopeVersion; }
    public void setScopeVersion(String scopeVersion) { this.scopeVersion = scopeVersion; }

    private static List<Long> safe(List<Long> value)
    {
        return value == null ? new ArrayList<Long>() : value;
    }
}
