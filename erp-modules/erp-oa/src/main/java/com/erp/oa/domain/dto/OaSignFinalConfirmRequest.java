package com.erp.oa.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class OaSignFinalConfirmRequest
{
    @NotBlank(message = "最终合同版本不能为空")
    @Size(max = 64, message = "最终合同版本长度不能超过64个字符")
    private String finalDocumentVersion;

    @NotBlank(message = "最终合同文件集合校验值不能为空")
    @Size(max = 64, message = "最终合同文件集合校验值长度不能超过64个字符")
    private String documentRootHash;

    @NotBlank(message = "确认内容不能为空")
    @Size(max = 500, message = "确认内容不能超过500个字符")
    private String confirmationText;

    @NotBlank(message = "请求编号不能为空")
    @Size(max = 64, message = "请求编号不能超过64个字符")
    private String requestId;

    public String getFinalDocumentVersion() { return finalDocumentVersion; }
    public void setFinalDocumentVersion(String finalDocumentVersion) { this.finalDocumentVersion = finalDocumentVersion; }
    public String getDocumentRootHash() { return documentRootHash; }
    public void setDocumentRootHash(String documentRootHash) { this.documentRootHash = documentRootHash; }
    public String getConfirmationText() { return confirmationText; }
    public void setConfirmationText(String confirmationText) { this.confirmationText = confirmationText; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
}
