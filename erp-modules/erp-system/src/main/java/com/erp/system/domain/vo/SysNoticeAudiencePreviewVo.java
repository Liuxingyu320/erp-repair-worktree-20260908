package com.erp.system.domain.vo;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 公告受众去重预览，不返回接收人个人信息。
 */
public class SysNoticeAudiencePreviewVo
{
    private int recipientCount;
    private int ruleCount;
    private Map<String, Integer> organizationCounts = Collections.emptyMap();
    private List<String> warnings = Collections.emptyList();

    public int getRecipientCount() { return recipientCount; }
    public void setRecipientCount(int recipientCount) { this.recipientCount = recipientCount; }
    public int getRuleCount() { return ruleCount; }
    public void setRuleCount(int ruleCount) { this.ruleCount = ruleCount; }
    public Map<String, Integer> getOrganizationCounts() { return organizationCounts; }
    public void setOrganizationCounts(Map<String, Integer> organizationCounts) { this.organizationCounts = organizationCounts; }
    public List<String> getWarnings() { return warnings; }
    public void setWarnings(List<String> warnings) { this.warnings = warnings; }
}
