package com.erp.oa.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class OaSignTaskConfirmRequest
{
    @NotBlank(message = "文档版本不能为空")
    @Size(max = 64, message = "文档版本不能超过64个字符")
    private String documentVersion;

    @NotBlank(message = "确认令牌不能为空")
    @Size(max = 2048, message = "确认令牌不能超过2048个字符")
    private String confirmationToken;

    @NotBlank(message = "确认短语不能为空")
    @Size(max = 100, message = "确认短语不能超过100个字符")
    private String confirmText;

    @NotBlank(message = "请求编号不能为空")
    @Size(max = 64, message = "请求编号不能超过64个字符")
    private String requestId;

    public String getDocumentVersion() { return documentVersion; }
    public void setDocumentVersion(String documentVersion) { this.documentVersion = documentVersion; }
    public String getConfirmationToken() { return confirmationToken; }
    public void setConfirmationToken(String confirmationToken) { this.confirmationToken = confirmationToken; }
    public String getConfirmText() { return confirmText; }
    public void setConfirmText(String confirmText) { this.confirmText = confirmText; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
}
