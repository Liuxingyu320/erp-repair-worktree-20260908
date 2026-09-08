package com.erp.oa.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** Exact employee-visible pending-final file bound to a company-first signature request. */
public class OaSignFinalDocumentHashRequest
{
    @NotNull(message = "最终合同文件编号不能为空")
    private Long documentId;

    @NotBlank(message = "最终合同文件校验值不能为空")
    @Pattern(regexp = "^[a-fA-F0-9]{64}$", message = "最终合同文件校验值格式不正确")
    private String finalPdfHash;

    public Long getDocumentId() { return documentId; }
    public void setDocumentId(Long documentId) { this.documentId = documentId; }

    public String getFinalPdfHash() { return finalPdfHash; }
    public void setFinalPdfHash(String finalPdfHash) { this.finalPdfHash = finalPdfHash; }
}
