package com.erp.oa.domain.dto;

import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class OaSignPackageSignRequest
{
    @NotBlank(message = "文档版本不能为空")
    private String documentVersion;

    @Valid
    private List<OaSignDocumentHashRequest> documentHashes;

    /** Required for COMPANY_FIRST and bound to the already employee-visible candidate. */
    private String finalDocumentVersion;

    /** Required for COMPANY_FIRST; aggregates the stable body hashes of the candidate. */
    private String finalDocumentRootHash;

    /** Required for COMPANY_FIRST; exact pending-final PDF hashes viewed by the employee. */
    @Valid
    private List<OaSignFinalDocumentHashRequest> finalDocumentHashes;

    @NotBlank(message = "签署确认文本不能为空")
    private String signConfirmText;

    @NotBlank(message = "签名不能为空")
    private String signatureDataUrl;

    @NotBlank(message = "签署请求编号不能为空")
    @Size(max = 64, message = "签署请求编号长度不能超过64个字符")
    private String requestId;

    public String getDocumentVersion() { return documentVersion; }
    public void setDocumentVersion(String documentVersion) { this.documentVersion = documentVersion; }

    public List<OaSignDocumentHashRequest> getDocumentHashes() { return documentHashes; }
    public void setDocumentHashes(List<OaSignDocumentHashRequest> documentHashes) { this.documentHashes = documentHashes; }

    public String getFinalDocumentVersion() { return finalDocumentVersion; }
    public void setFinalDocumentVersion(String finalDocumentVersion) { this.finalDocumentVersion = finalDocumentVersion; }

    public String getFinalDocumentRootHash() { return finalDocumentRootHash; }
    public void setFinalDocumentRootHash(String finalDocumentRootHash) { this.finalDocumentRootHash = finalDocumentRootHash; }

    public List<OaSignFinalDocumentHashRequest> getFinalDocumentHashes() { return finalDocumentHashes; }
    public void setFinalDocumentHashes(List<OaSignFinalDocumentHashRequest> finalDocumentHashes) { this.finalDocumentHashes = finalDocumentHashes; }

    public String getSignConfirmText() { return signConfirmText; }
    public void setSignConfirmText(String signConfirmText) { this.signConfirmText = signConfirmText; }

    public String getSignatureDataUrl() { return signatureDataUrl; }
    public void setSignatureDataUrl(String signatureDataUrl) { this.signatureDataUrl = signatureDataUrl; }

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
}
