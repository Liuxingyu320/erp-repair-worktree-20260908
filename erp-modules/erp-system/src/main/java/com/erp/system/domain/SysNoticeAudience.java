package com.erp.system.domain;

import java.io.Serializable;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 公告受众规则。
 */
@JsonIgnoreProperties(ignoreUnknown = false)
public class SysNoticeAudience implements Serializable
{
    private static final long serialVersionUID = 1L;

    private Long noticeId;

    @NotBlank(message = "受众目标类型不能为空")
    private String targetType;

    @NotNull(message = "受众目标不能为空")
    private Long targetId;

    private boolean includeChildren;

    public Long getNoticeId() { return noticeId; }
    public void setNoticeId(Long noticeId) { this.noticeId = noticeId; }
    public String getTargetType() { return targetType; }
    public void setTargetType(String targetType) { this.targetType = targetType; }
    public Long getTargetId() { return targetId; }
    public void setTargetId(Long targetId) { this.targetId = targetId; }
    public boolean isIncludeChildren() { return includeChildren; }
    public void setIncludeChildren(boolean includeChildren) { this.includeChildren = includeChildren; }
}
