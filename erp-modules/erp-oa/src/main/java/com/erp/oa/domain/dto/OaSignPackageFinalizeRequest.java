package com.erp.oa.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public class OaSignPackageFinalizeRequest
{
    private Long legalEntityId;

    private Long sealId;

    @Size(max = 500, message = "人工改选公司原因不能超过500个字符")
    private String correctionReason;

    @NotBlank(message = "请求编号不能为空")
    @Size(max = 64, message = "请求编号不能超过64个字符")
    private String requestId;

    @NotNull(message = "签约包预期版本不能为空")
    @PositiveOrZero(message = "签约包预期版本不能小于0")
    private Long expectedVersion;

    @NotNull(message = "签约任务预期版本不能为空")
    @PositiveOrZero(message = "签约任务预期版本不能小于0")
    private Long expectedTaskVersion;

    private String signingSequence;

    public Long getLegalEntityId() { return legalEntityId; }
    public void setLegalEntityId(Long legalEntityId) { this.legalEntityId = legalEntityId; }
    public Long getSealId() { return sealId; }
    public void setSealId(Long sealId) { this.sealId = sealId; }
    public String getCorrectionReason() { return correctionReason; }
    public void setCorrectionReason(String correctionReason) { this.correctionReason = correctionReason; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String value) { requestId = value; }
    public Long getExpectedVersion() { return expectedVersion; }
    public void setExpectedVersion(Long value) { expectedVersion = value; }
    public Long getExpectedTaskVersion() { return expectedTaskVersion; }
    public void setExpectedTaskVersion(Long value) { expectedTaskVersion = value; }
    public String getSigningSequence() { return signingSequence; }
    public void setSigningSequence(String value) { signingSequence = value; }
}
