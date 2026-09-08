package com.erp.oa.domain.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;

public class OaLaborContractSignRequest
{
    @AssertTrue(message = "请先确认已阅读并同意合同内容")
    private boolean confirmed;

    @NotBlank(message = "签名不能为空")
    private String signatureDataUrl;

    @NotBlank(message = "合同版本不能为空")
    private String documentVersion;

    @NotBlank(message = "合同文件哈希不能为空")
    private String previewFileHash;

    @NotBlank(message = "请输入二次确认短语")
    private String signConfirmText;

    private String signerIp;

    private String signerUserAgent;

    public boolean isConfirmed() { return confirmed; }
    public void setConfirmed(boolean confirmed) { this.confirmed = confirmed; }

    public String getSignatureDataUrl() { return signatureDataUrl; }
    public void setSignatureDataUrl(String signatureDataUrl) { this.signatureDataUrl = signatureDataUrl; }

    public String getDocumentVersion() { return documentVersion; }
    public void setDocumentVersion(String documentVersion) { this.documentVersion = documentVersion; }

    public String getPreviewFileHash() { return previewFileHash; }
    public void setPreviewFileHash(String previewFileHash) { this.previewFileHash = previewFileHash; }

    public String getSignConfirmText() { return signConfirmText; }
    public void setSignConfirmText(String signConfirmText) { this.signConfirmText = signConfirmText; }

    public String getSignerIp() { return signerIp; }
    public void setSignerIp(String signerIp) { this.signerIp = signerIp; }

    public String getSignerUserAgent() { return signerUserAgent; }
    public void setSignerUserAgent(String signerUserAgent) { this.signerUserAgent = signerUserAgent; }
}
