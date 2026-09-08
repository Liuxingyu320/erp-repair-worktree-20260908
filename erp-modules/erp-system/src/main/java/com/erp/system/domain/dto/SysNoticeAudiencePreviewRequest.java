package com.erp.system.domain.dto;

import java.util.ArrayList;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.erp.system.domain.SysNoticeAudience;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

/**
 * 公告受众预览请求。
 */
@JsonIgnoreProperties(ignoreUnknown = false)
public class SysNoticeAudiencePreviewRequest
{
    @NotBlank(message = "受众类型不能为空")
    private String audienceType;

    @Valid
    @NotEmpty(message = "至少选择一项公告受众")
    @Size(max = 500, message = "公告受众规则不能超过500项")
    private List<SysNoticeAudience> audiences = new ArrayList<>();

    public String getAudienceType() { return audienceType; }
    public void setAudienceType(String audienceType) { this.audienceType = audienceType; }
    public List<SysNoticeAudience> getAudiences() { return audiences; }
    public void setAudiences(List<SysNoticeAudience> audiences) { this.audiences = audiences; }
}
