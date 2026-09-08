package com.erp.system.domain.vo;

/**
 * 公告受众选择器的最小选项。
 */
public class SysNoticeAudienceTargetVo
{
    private Long id;
    private String label;
    private String description;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
