package com.erp.system.domain.vo;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonFormat;

public class HrOnboardingConfirmResult implements Serializable
{
    private static final long serialVersionUID = 1L;
    private Long onboardingId;
    private Long userId;
    private String employeeNo;
    private String accountStatus;
    private String oneTimePassword;
    /**
     * Only populated on first create-new account; never on bind/replay.
     * Serialized as ISO-8601 with timezone offset (UTC).
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ssXXX", timezone = "UTC")
    private Date oneTimePasswordExpiresAt;
    private boolean replayed;
    private List<String> riskCodes = new ArrayList<>();

    @com.fasterxml.jackson.databind.annotation.JsonSerialize(using = com.fasterxml.jackson.databind.ser.std.ToStringSerializer.class)
    public Long getOnboardingId() { return onboardingId; }
    public void setOnboardingId(Long onboardingId) { this.onboardingId = onboardingId; }
    @com.fasterxml.jackson.databind.annotation.JsonSerialize(using = com.fasterxml.jackson.databind.ser.std.ToStringSerializer.class)
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getEmployeeNo() { return employeeNo; }
    public void setEmployeeNo(String employeeNo) { this.employeeNo = employeeNo; }
    public String getAccountStatus() { return accountStatus; }
    public void setAccountStatus(String accountStatus) { this.accountStatus = accountStatus; }
    public String getOneTimePassword() { return oneTimePassword; }
    public void setOneTimePassword(String oneTimePassword) { this.oneTimePassword = oneTimePassword; }
    public Date getOneTimePasswordExpiresAt() { return oneTimePasswordExpiresAt; }
    public void setOneTimePasswordExpiresAt(Date oneTimePasswordExpiresAt)
    {
        this.oneTimePasswordExpiresAt = oneTimePasswordExpiresAt == null
                ? null : new Date(oneTimePasswordExpiresAt.getTime());
    }
    public boolean isReplayed() { return replayed; }
    public void setReplayed(boolean replayed) { this.replayed = replayed; }
    public List<String> getRiskCodes() { return riskCodes; }
    public void setRiskCodes(List<String> riskCodes) { this.riskCodes = riskCodes == null ? new ArrayList<>() : riskCodes; }
}
