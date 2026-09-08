package com.erp.oa.domain.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import com.erp.common.core.exception.ServiceException;

public class OaSignOnboardDataReviewRequest
{
    @NotBlank(message = "审核动作不能为空")
    private String action;
    @NotNull(message = "任务版本不能为空")
    @Positive(message = "任务版本无效")
    private Long version;
    private String reason;
    private String servicePersonType;
    private String insuranceType;

    @JsonAnySetter
    public void rejectUnknown(String field, Object ignored)
    {
        throw new ServiceException("不允许审核字段：" + field);
    }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getServicePersonType() { return servicePersonType; }
    public void setServicePersonType(String servicePersonType) { this.servicePersonType = servicePersonType; }
    public String getInsuranceType() { return insuranceType; }
    public void setInsuranceType(String insuranceType) { this.insuranceType = insuranceType; }
}
