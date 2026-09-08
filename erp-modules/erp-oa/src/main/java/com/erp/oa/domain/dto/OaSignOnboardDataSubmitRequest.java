package com.erp.oa.domain.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import com.erp.common.core.exception.ServiceException;
import jakarta.validation.constraints.Size;

/** Employee-editable objective facts only. */
public class OaSignOnboardDataSubmitRequest
{
    @NotNull(message = "任务版本不能为空")
    @Positive(message = "任务版本无效")
    private Long version;
    private String currentAddress;
    private String studentStatus;
    private String schoolName;
    private String retirementStatus;
    private String incomeStartYearMonth;
    @Size(max = 80, message = "事实确认短语不能超过80个字符")
    private String factConfirmationText;
    @Size(max = 64, message = "签名请求编号不能超过64个字符")
    private String signatureRequestId;
    private String signatureDataUrl;

    @JsonAnySetter
    public void rejectHrOnlyOrUnknownField(String field, Object ignored)
    {
        throw new ServiceException("员工不允许提交字段：" + field);
    }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
    public String getCurrentAddress() { return currentAddress; }
    public void setCurrentAddress(String currentAddress) { this.currentAddress = currentAddress; }
    public String getStudentStatus() { return studentStatus; }
    public void setStudentStatus(String studentStatus) { this.studentStatus = studentStatus; }
    public String getSchoolName() { return schoolName; }
    public void setSchoolName(String schoolName) { this.schoolName = schoolName; }
    public String getRetirementStatus() { return retirementStatus; }
    public void setRetirementStatus(String retirementStatus) { this.retirementStatus = retirementStatus; }
    public String getIncomeStartYearMonth() { return incomeStartYearMonth; }
    public void setIncomeStartYearMonth(String incomeStartYearMonth) { this.incomeStartYearMonth = incomeStartYearMonth; }
    public String getFactConfirmationText() { return factConfirmationText; }
    public void setFactConfirmationText(String value) { factConfirmationText = value; }
    public String getSignatureRequestId() { return signatureRequestId; }
    public void setSignatureRequestId(String value) { signatureRequestId = value; }
    public String getSignatureDataUrl() { return signatureDataUrl; }
    public void setSignatureDataUrl(String value) { signatureDataUrl = value; }
}
