package com.erp.oa.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public class OaSignDocumentHashRequest
{
    @NotNull(message = "签约文件编号不能为空")
    private Long documentId;

    @NotBlank(message = "阅读文件校验值不能为空")
    @Pattern(regexp = "^[a-fA-F0-9]{64}$", message = "阅读文件校验值格式不正确")
    private String reviewPdfHash;

    public Long getDocumentId() { return documentId; }
    public void setDocumentId(Long documentId) { this.documentId = documentId; }

    public String getReviewPdfHash() { return reviewPdfHash; }
    public void setReviewPdfHash(String reviewPdfHash) { this.reviewPdfHash = reviewPdfHash; }
}
