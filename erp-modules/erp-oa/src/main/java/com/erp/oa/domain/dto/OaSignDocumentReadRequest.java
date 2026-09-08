package com.erp.oa.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public class OaSignDocumentReadRequest
{
    @NotBlank(message = "阅读请求编号不能为空")
    @Size(max = 64, message = "阅读请求编号不能超过64个字符")
    private String requestId;

    @NotNull(message = "签约包预期版本不能为空")
    @PositiveOrZero(message = "签约包预期版本不合法")
    private Long expectedVersion;

    @NotBlank(message = "文档版本不能为空")
    private String documentVersion;

    @NotBlank(message = "阅读文件校验值不能为空")
    @Pattern(regexp = "^[a-fA-F0-9]{64}$", message = "阅读文件校验值格式不正确")
    private String reviewPdfHash;

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }

    public Long getExpectedVersion() { return expectedVersion; }
    public void setExpectedVersion(Long expectedVersion) { this.expectedVersion = expectedVersion; }

    public String getDocumentVersion() { return documentVersion; }
    public void setDocumentVersion(String documentVersion) { this.documentVersion = documentVersion; }

    public String getReviewPdfHash() { return reviewPdfHash; }
    public void setReviewPdfHash(String reviewPdfHash) { this.reviewPdfHash = reviewPdfHash; }
}
