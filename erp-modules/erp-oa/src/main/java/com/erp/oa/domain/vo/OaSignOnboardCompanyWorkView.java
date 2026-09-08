package com.erp.oa.domain.vo;

import java.util.ArrayList;
import java.util.List;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

/** PII-minimized signature-first rows waiting for HR company and seal selection. */
public class OaSignOnboardCompanyWorkView
{
    private Integer totalCount = 0;
    private List<Item> items = new ArrayList<>();

    public Integer getTotalCount() { return totalCount; }
    public void setTotalCount(Integer value) { totalCount = value; }
    public List<Item> getItems() { return new ArrayList<>(items); }
    public void setItems(List<Item> value)
    {
        items = value == null ? new ArrayList<>() : new ArrayList<>(value);
    }

    public static class Item
    {
        private String sourceType = "ONBOARD_IMPORT_ROW";
        @JsonSerialize(using = ToStringSerializer.class)
        private Long batchId;
        private String batchNo;
        @JsonSerialize(using = ToStringSerializer.class)
        private Long rowId;
        @JsonSerialize(using = ToStringSerializer.class)
        private Long taskId;
        @JsonSerialize(using = ToStringSerializer.class)
        private Long packageId;
        private Integer sourceRowNumber;
        private Long version;
        @JsonSerialize(using = ToStringSerializer.class)
        private Long employeeId;
        private String employeeName;
        @JsonSerialize(using = ToStringSerializer.class)
        private Long shopDeptId;
        private String shopDeptName;
        private String status;
        private String planName;
        private List<String> templateNames = new ArrayList<>();
        private List<String> errorCodes = new ArrayList<>();
        private List<String> warningCodes = new ArrayList<>();
        private List<String> missingFields = new ArrayList<>();
        @JsonSerialize(using = ToStringSerializer.class)
        private Long matchedLegalEntityId;
        private String matchedLegalEntityName;
        @JsonSerialize(using = ToStringSerializer.class)
        private Long recommendedSealId;
        private List<OaSignOnboardImportRowView.CompanyCandidate> companyCandidates =
                new ArrayList<>();
        private List<OaSignOnboardImportRowView.SealCandidate> sealCandidates =
                new ArrayList<>();
        private Boolean signatureCaptured;
        private Boolean historicalSupplement;
        private String historicalReason;
        private Boolean noExternalContractConfirmed;
        private Boolean warningConfirmed;
        private String warningReason;

        public String getSourceType() { return sourceType; }
        public void setSourceType(String value) { sourceType = value; }
        public Long getBatchId() { return batchId; }
        public void setBatchId(Long value) { batchId = value; }
        public String getBatchNo() { return batchNo; }
        public void setBatchNo(String value) { batchNo = value; }
        public Long getRowId() { return rowId; }
        public void setRowId(Long value) { rowId = value; }
        public Long getTaskId() { return taskId; }
        public void setTaskId(Long value) { taskId = value; }
        public Long getPackageId() { return packageId; }
        public void setPackageId(Long value) { packageId = value; }
        public Integer getSourceRowNumber() { return sourceRowNumber; }
        public void setSourceRowNumber(Integer value) { sourceRowNumber = value; }
        public Long getVersion() { return version; }
        public void setVersion(Long value) { version = value; }
        public Long getEmployeeId() { return employeeId; }
        public void setEmployeeId(Long value) { employeeId = value; }
        public String getEmployeeName() { return employeeName; }
        public void setEmployeeName(String value) { employeeName = value; }
        public Long getShopDeptId() { return shopDeptId; }
        public void setShopDeptId(Long value) { shopDeptId = value; }
        public String getShopDeptName() { return shopDeptName; }
        public void setShopDeptName(String value) { shopDeptName = value; }
        public String getStatus() { return status; }
        public void setStatus(String value) { status = value; }
        public String getPlanName() { return planName; }
        public void setPlanName(String value) { planName = value; }
        public List<String> getTemplateNames() { return new ArrayList<>(templateNames); }
        public void setTemplateNames(List<String> value) { templateNames = copy(value); }
        public List<String> getErrorCodes() { return new ArrayList<>(errorCodes); }
        public void setErrorCodes(List<String> value) { errorCodes = copy(value); }
        public List<String> getWarningCodes() { return new ArrayList<>(warningCodes); }
        public void setWarningCodes(List<String> value) { warningCodes = copy(value); }
        public List<String> getMissingFields() { return new ArrayList<>(missingFields); }
        public void setMissingFields(List<String> value) { missingFields = copy(value); }
        public Long getMatchedLegalEntityId() { return matchedLegalEntityId; }
        public void setMatchedLegalEntityId(Long value) { matchedLegalEntityId = value; }
        public String getMatchedLegalEntityName() { return matchedLegalEntityName; }
        public void setMatchedLegalEntityName(String value) { matchedLegalEntityName = value; }
        public Long getRecommendedSealId() { return recommendedSealId; }
        public void setRecommendedSealId(Long value) { recommendedSealId = value; }
        public List<OaSignOnboardImportRowView.CompanyCandidate> getCompanyCandidates()
        {
            return new ArrayList<>(companyCandidates);
        }
        public void setCompanyCandidates(List<OaSignOnboardImportRowView.CompanyCandidate> value)
        {
            companyCandidates = value == null ? new ArrayList<>() : new ArrayList<>(value);
        }
        public List<OaSignOnboardImportRowView.SealCandidate> getSealCandidates()
        {
            return new ArrayList<>(sealCandidates);
        }
        public void setSealCandidates(List<OaSignOnboardImportRowView.SealCandidate> value)
        {
            sealCandidates = value == null ? new ArrayList<>() : new ArrayList<>(value);
        }
        public Boolean getSignatureCaptured() { return signatureCaptured; }
        public void setSignatureCaptured(Boolean value) { signatureCaptured = value; }
        public Boolean getHistoricalSupplement() { return historicalSupplement; }
        public void setHistoricalSupplement(Boolean value) { historicalSupplement = value; }
        public String getHistoricalReason() { return historicalReason; }
        public void setHistoricalReason(String value) { historicalReason = value; }
        public Boolean getNoExternalContractConfirmed() { return noExternalContractConfirmed; }
        public void setNoExternalContractConfirmed(Boolean value)
        {
            noExternalContractConfirmed = value;
        }
        public Boolean getWarningConfirmed() { return warningConfirmed; }
        public void setWarningConfirmed(Boolean value) { warningConfirmed = value; }
        public String getWarningReason() { return warningReason; }
        public void setWarningReason(String value) { warningReason = value; }

        private List<String> copy(List<String> source)
        {
            return source == null ? new ArrayList<>() : new ArrayList<>(source);
        }
    }
}
