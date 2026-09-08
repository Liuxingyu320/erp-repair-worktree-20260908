package com.erp.oa.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Confirmation that the logged-in employee opened the current pending-final document. */
public class OaSignFinalDocumentReadRequest
{
    @NotBlank(message = "最终文档版本不能为空")
    private String finalDocumentVersion;

    @NotBlank(message = "最终文件校验值不能为空")
    @Pattern(regexp = "^[a-fA-F0-9]{64}$", message = "最终文件校验值格式不正确")
    private String finalPdfHash;

    @NotBlank(message = "阅读请求编号不能为空")
    @Size(max = 64, message = "阅读请求编号不能超过64个字符")
    private String requestId;

    public String getFinalDocumentVersion() { return finalDocumentVersion; }
    public void setFinalDocumentVersion(String value) { finalDocumentVersion = value; }
    public String getFinalPdfHash() { return finalPdfHash; }
    public void setFinalPdfHash(String value) { finalPdfHash = value; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String value) { requestId = value; }
}
