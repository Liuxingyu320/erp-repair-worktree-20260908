package com.erp.system.domain.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 公告状态操作使用的乐观版本请求。
 */
@JsonIgnoreProperties(ignoreUnknown = false)
public class SysNoticeVersionRequest
{
    @NotNull(message = "公告版本不能为空")
    @Min(value = 1, message = "公告版本无效")
    private Long version;

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}
