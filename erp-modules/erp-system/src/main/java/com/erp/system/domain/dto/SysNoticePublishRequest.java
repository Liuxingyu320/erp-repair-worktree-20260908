package com.erp.system.domain.dto;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * 显式发布或计划发布请求。
 */
@JsonIgnoreProperties(ignoreUnknown = false)
public class SysNoticePublishRequest
{
    @NotNull(message = "公告版本不能为空")
    @Min(value = 1, message = "公告版本无效")
    private Long version;

    @NotBlank(message = "发布方式不能为空")
    @Pattern(regexp = "IMMEDIATE|SCHEDULED", message = "发布方式无效")
    private String publishMode;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date scheduledPublishTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date expireTime;

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
    public String getPublishMode() { return publishMode; }
    public void setPublishMode(String publishMode) { this.publishMode = publishMode; }
    public Date getScheduledPublishTime() { return scheduledPublishTime; }
    public void setScheduledPublishTime(Date scheduledPublishTime) { this.scheduledPublishTime = scheduledPublishTime; }
    public Date getExpireTime() { return expireTime; }
    public void setExpireTime(Date expireTime) { this.expireTime = expireTime; }
}
