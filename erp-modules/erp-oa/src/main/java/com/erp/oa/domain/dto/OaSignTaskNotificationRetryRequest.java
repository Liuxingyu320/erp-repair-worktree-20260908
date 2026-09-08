package com.erp.oa.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request to requeue one failed signing-notification business event. */
public class OaSignTaskNotificationRetryRequest
{
    @NotBlank(message = "请求编号不能为空")
    @Size(max = 64, message = "请求编号不能超过64个字符")
    private String requestId;

    @NotBlank(message = "通知业务键不能为空")
    @Size(max = 180, message = "通知业务键不能超过180个字符")
    private String businessKey;

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getBusinessKey() { return businessKey; }
    public void setBusinessKey(String businessKey) { this.businessKey = businessKey; }
}
