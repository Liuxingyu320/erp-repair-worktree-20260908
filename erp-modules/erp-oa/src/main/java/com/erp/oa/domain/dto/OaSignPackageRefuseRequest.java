package com.erp.oa.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Employee request to put a signing package into an immutable refused state. */
public class OaSignPackageRefuseRequest
{
    @NotBlank(message = "请求编号不能为空")
    @Size(max = 64, message = "请求编号不能超过64个字符")
    private String requestId;

    @NotBlank(message = "文档版本不能为空")
    @Size(max = 64, message = "文档版本不能超过64个字符")
    private String documentVersion;

    @NotBlank(message = "拒签原因代码不能为空")
    @Size(max = 64, message = "拒签原因代码不能超过64个字符")
    @Pattern(regexp = "^[A-Z][A-Z0-9_]{0,63}$", message = "拒签原因代码格式不正确")
    private String reasonCode;

    @NotBlank(message = "拒签原因说明不能为空")
    @Size(max = 500, message = "拒签原因说明不能超过500个字符")
    private String reasonDetail;

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }

    public String getDocumentVersion() { return documentVersion; }
    public void setDocumentVersion(String documentVersion) { this.documentVersion = documentVersion; }

    public String getReasonCode() { return reasonCode; }
    public void setReasonCode(String reasonCode) { this.reasonCode = reasonCode; }

    public String getReasonDetail() { return reasonDetail; }
    public void setReasonDetail(String reasonDetail) { this.reasonDetail = reasonDetail; }
}
