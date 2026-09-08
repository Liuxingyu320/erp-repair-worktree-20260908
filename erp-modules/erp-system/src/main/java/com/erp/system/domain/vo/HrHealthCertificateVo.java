package com.erp.system.domain.vo;

import java.time.LocalDate;
import com.erp.system.domain.HrHealthCertificate;

public class HrHealthCertificateVo extends HrHealthCertificate
{
    private static final long serialVersionUID = 1L;

    private String employeeNo;
    private String employeeName;
    private Long currentDeptId;
    private String currentDeptName;
    private String healthCertificateStatus;
    private Long daysRemaining;
    private Boolean attachmentPresent;

    public String getEmployeeNo() { return employeeNo; }
    public void setEmployeeNo(String employeeNo) { this.employeeNo = employeeNo; }
    public String getEmployeeName() { return employeeName; }
    public void setEmployeeName(String employeeName) { this.employeeName = employeeName; }
    public Long getCurrentDeptId() { return currentDeptId; }
    public void setCurrentDeptId(Long currentDeptId) { this.currentDeptId = currentDeptId; }
    public String getCurrentDeptName() { return currentDeptName; }
    public void setCurrentDeptName(String currentDeptName) { this.currentDeptName = currentDeptName; }
    public String getHealthCertificateStatus() { return healthCertificateStatus; }
    public void setHealthCertificateStatus(String healthCertificateStatus) { this.healthCertificateStatus = healthCertificateStatus; }
    public Long getDaysRemaining() { return daysRemaining; }
    public void setDaysRemaining(Long daysRemaining) { this.daysRemaining = daysRemaining; }
    public Boolean getAttachmentPresent() { return attachmentPresent; }
    public void setAttachmentPresent(Boolean attachmentPresent) { this.attachmentPresent = attachmentPresent; }

    public static HrHealthCertificateVo notSubmitted(Long userId)
    {
        HrHealthCertificateVo value = new HrHealthCertificateVo();
        value.setUserId(userId);
        value.setHealthCertificateStatus("NOT_SUBMITTED");
        value.setAttachmentPresent(false);
        return value;
    }
}
