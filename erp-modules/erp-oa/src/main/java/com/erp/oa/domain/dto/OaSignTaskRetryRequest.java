package com.erp.oa.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class OaSignTaskRetryRequest
{
    @NotBlank(message = "请求编号不能为空")
    @Size(max = 64, message = "请求编号不能超过64个字符")
    private String requestId;

    @Size(max = 64, message = "原因代码不能超过64个字符")
    private String reasonCode;

    @Size(max = 1000, message = "原因说明不能超过1000个字符")
    private String reasonDetail;

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getReasonCode() { return reasonCode; }
    public void setReasonCode(String reasonCode) { this.reasonCode = reasonCode; }
    public String getReasonDetail() { return reasonDetail; }
    public void setReasonDetail(String reasonDetail) { this.reasonDetail = reasonDetail; }
}
