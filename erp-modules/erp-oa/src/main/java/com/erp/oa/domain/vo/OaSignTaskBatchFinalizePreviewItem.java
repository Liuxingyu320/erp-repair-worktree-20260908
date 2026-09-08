package com.erp.oa.domain.vo;

import java.util.ArrayList;
import java.util.List;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

/** Batch-finalize preview for one task. */
public class OaSignTaskBatchFinalizePreviewItem
{
    @JsonSerialize(using = ToStringSerializer.class)
    private Long taskId;
    private String taskNo;
    private String taskStatus;
    private Long taskVersion;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long packageId;
    private String packageNo;
    private String packageStatus;
    private Long packageVersion;
    private String signingSequence;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long employeeId;
    private String employeeName;
    private String excelRecommendedCompany;
    private String excelRecommendedLegalRepresentative;
    private String excelRecommendedRegisteredAddress;
    private String excelMatchMode;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long recommendedLegalEntityId;
    private String recommendedLegalEntitySource;
    private Boolean companySealRequired;
    private Boolean readyForExecution;
    private List<String> blockingReasons = new ArrayList<>();
    private List<OaSignTaskBatchFinalizeCompanyOption> companyCandidates = new ArrayList<>();

    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public String getTaskNo() { return taskNo; }
    public void setTaskNo(String taskNo) { this.taskNo = taskNo; }
    public String getTaskStatus() { return taskStatus; }
    public void setTaskStatus(String taskStatus) { this.taskStatus = taskStatus; }
    public Long getTaskVersion() { return taskVersion; }
    public void setTaskVersion(Long taskVersion) { this.taskVersion = taskVersion; }
    public Long getPackageId() { return packageId; }
    public void setPackageId(Long packageId) { this.packageId = packageId; }
    public String getPackageNo() { return packageNo; }
    public void setPackageNo(String packageNo) { this.packageNo = packageNo; }
    public String getPackageStatus() { return packageStatus; }
    public void setPackageStatus(String packageStatus) { this.packageStatus = packageStatus; }
    public Long getPackageVersion() { return packageVersion; }
    public void setPackageVersion(Long packageVersion) { this.packageVersion = packageVersion; }
    public String getSigningSequence() { return signingSequence; }
    public void setSigningSequence(String signingSequence) { this.signingSequence = signingSequence; }
    public Long getEmployeeId() { return employeeId; }
    public void setEmployeeId(Long employeeId) { this.employeeId = employeeId; }
    public String getEmployeeName() { return employeeName; }
    public void setEmployeeName(String employeeName) { this.employeeName = employeeName; }
    public String getExcelRecommendedCompany() { return excelRecommendedCompany; }
    public void setExcelRecommendedCompany(String value) { excelRecommendedCompany = value; }
    public String getExcelRecommendedLegalRepresentative() { return excelRecommendedLegalRepresentative; }
    public void setExcelRecommendedLegalRepresentative(String value) { excelRecommendedLegalRepresentative = value; }
    public String getExcelRecommendedRegisteredAddress() { return excelRecommendedRegisteredAddress; }
    public void setExcelRecommendedRegisteredAddress(String value) { excelRecommendedRegisteredAddress = value; }
    public String getExcelMatchMode() { return excelMatchMode; }
    public void setExcelMatchMode(String excelMatchMode) { this.excelMatchMode = excelMatchMode; }
    public Long getRecommendedLegalEntityId() { return recommendedLegalEntityId; }
    public void setRecommendedLegalEntityId(Long value) { recommendedLegalEntityId = value; }
    public String getRecommendedLegalEntitySource() { return recommendedLegalEntitySource; }
    public void setRecommendedLegalEntitySource(String value) { recommendedLegalEntitySource = value; }
    public Boolean getCompanySealRequired() { return companySealRequired; }
    public void setCompanySealRequired(Boolean companySealRequired) { this.companySealRequired = companySealRequired; }
    public Boolean getReadyForExecution() { return readyForExecution; }
    public void setReadyForExecution(Boolean readyForExecution) { this.readyForExecution = readyForExecution; }
    public List<String> getBlockingReasons() { return blockingReasons; }
    public void setBlockingReasons(List<String> values)
    {
        blockingReasons = values == null ? new ArrayList<>() : new ArrayList<>(values);
    }
    public List<OaSignTaskBatchFinalizeCompanyOption> getCompanyCandidates() { return companyCandidates; }
    public void setCompanyCandidates(List<OaSignTaskBatchFinalizeCompanyOption> values)
    {
        companyCandidates = values == null ? new ArrayList<>() : new ArrayList<>(values);
    }
}
