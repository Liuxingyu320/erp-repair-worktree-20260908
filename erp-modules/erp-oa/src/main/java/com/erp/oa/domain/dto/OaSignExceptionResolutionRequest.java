package com.erp.oa.domain.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** HR request to close, reissue, or extend a refused/expired signing workflow. */
public class OaSignExceptionResolutionRequest
{
    @NotBlank(message = "请求编号不能为空")
    @Size(max = 64, message = "请求编号不能超过64个字符")
    private String requestId;

    @NotBlank(message = "处置动作不能为空")
    @Pattern(regexp = "^(CLOSE|REISSUE|EXTEND)$", message = "处置动作仅支持CLOSE、REISSUE或EXTEND")
    private String action;

    @NotBlank(message = "文档版本不能为空")
    @Size(max = 64, message = "文档版本不能超过64个字符")
    private String documentVersion;

    @NotBlank(message = "处置原因代码不能为空")
    @Size(max = 64, message = "处置原因代码不能超过64个字符")
    @Pattern(regexp = "^[A-Z][A-Z0-9_]{0,63}$", message = "处置原因代码格式不正确")
    private String reasonCode;

    @NotBlank(message = "处置原因说明不能为空")
    @Size(max = 500, message = "处置原因说明不能超过500个字符")
    private String reasonDetail;

    @NotNull(message = "预期版本不能为空")
    @PositiveOrZero(message = "预期版本不能小于0")
    private Long expectedVersion;

    @Min(value = 1, message = "延期天数不能小于1天")
    @Max(value = 365, message = "延期天数不能超过365天")
    private Integer extensionDays;

    @Positive(message = "替代方案版本编号必须大于0")
    private Long replacementPlanVersionId;

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getDocumentVersion() { return documentVersion; }
    public void setDocumentVersion(String documentVersion) { this.documentVersion = documentVersion; }

    public String getReasonCode() { return reasonCode; }
    public void setReasonCode(String reasonCode) { this.reasonCode = reasonCode; }

    public String getReasonDetail() { return reasonDetail; }
    public void setReasonDetail(String reasonDetail) { this.reasonDetail = reasonDetail; }

    public Long getExpectedVersion() { return expectedVersion; }
    public void setExpectedVersion(Long expectedVersion) { this.expectedVersion = expectedVersion; }

    public Integer getExtensionDays() { return extensionDays; }
    public void setExtensionDays(Integer extensionDays) { this.extensionDays = extensionDays; }

    public Long getReplacementPlanVersionId() { return replacementPlanVersionId; }
    public void setReplacementPlanVersionId(Long replacementPlanVersionId) { this.replacementPlanVersionId = replacementPlanVersionId; }
}
