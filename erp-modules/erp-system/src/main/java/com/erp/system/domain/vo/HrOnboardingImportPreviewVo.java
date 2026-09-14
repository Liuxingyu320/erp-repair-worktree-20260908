package com.erp.system.domain.vo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Privacy-safe import batch response. */
public class HrOnboardingImportPreviewVo
{
    private Long batchId;
    private String batchNo;
    private String fileName;
    private String status;
    private Integer version;
    private Integer totalRows;
    private Integer importableRows;
    private Integer warningRows;
    private Integer invalidRows;
    private Integer duplicateRows;
    private Integer bindableRows;
    private Integer successRows;
    private Integer failureRows;
    private List<RowVo> rows = new ArrayList<>();
    private List<Map<String, Object>> errors = new ArrayList<>();

    @com.fasterxml.jackson.databind.annotation.JsonSerialize(using = com.fasterxml.jackson.databind.ser.std.ToStringSerializer.class)
    public Long getBatchId() { return batchId; } public void setBatchId(Long v) { batchId=v; }
    public String getBatchNo() { return batchNo; } public void setBatchNo(String v) { batchNo=v; }
    public String getFileName() { return fileName; } public void setFileName(String v) { fileName=v; }
    public String getStatus() { return status; } public void setStatus(String v) { status=v; }
    public Integer getVersion() { return version; } public void setVersion(Integer v) { version=v; }
    public Integer getTotalRows() { return totalRows; } public void setTotalRows(Integer v) { totalRows=v; }
    public Integer getImportableRows() { return importableRows; } public void setImportableRows(Integer v) { importableRows=v; }
    public Integer getWarningRows() { return warningRows; } public void setWarningRows(Integer v) { warningRows=v; }
    public Integer getInvalidRows() { return invalidRows; } public void setInvalidRows(Integer v) { invalidRows=v; }
    public Integer getDuplicateRows() { return duplicateRows; } public void setDuplicateRows(Integer v) { duplicateRows=v; }
    public Integer getBindableRows() { return bindableRows; } public void setBindableRows(Integer v) { bindableRows=v; }
    public Integer getSuccessRows() { return successRows; } public void setSuccessRows(Integer v) { successRows=v; }
    public Integer getFailureRows() { return failureRows; } public void setFailureRows(Integer v) { failureRows=v; }
    public List<RowVo> getRows() { return rows; } public void setRows(List<RowVo> v) { rows=v; }
    public List<Map<String,Object>> getErrors() { return errors; } public void setErrors(List<Map<String,Object>> v) { errors=v; }

    public static class RowVo
    {
        private Long rowId; private Integer sourceRowNumber; private String category; private String rowStatus;
        private String employeeName; private String phoneNumberMasked; private String idNumberMasked;
        private String bankAccountMasked; private String registeredResidenceMasked; private String currentAddressMasked;
        private String warningCodes; private String errorCodes; private String candidateSummary;
        private Long candidateUserId; private Long candidateOnboardingId;
        private String companyName; private String deptLevel1Name; private String deptLevel2Name;
        private String deptLevel3Name; private String storeName; private String positionName;
        private String employeeCategory; private String expectedEntryDateText;
        private String emergencyContactPhoneMasked;
        private String resultCode; private String resultMessage; private Long resultOnboardingId;
        @com.fasterxml.jackson.databind.annotation.JsonSerialize(using = com.fasterxml.jackson.databind.ser.std.ToStringSerializer.class)
    public Long getRowId(){return rowId;} public void setRowId(Long v){rowId=v;}
        public Integer getSourceRowNumber(){return sourceRowNumber;} public void setSourceRowNumber(Integer v){sourceRowNumber=v;}
        public String getCategory(){return category;} public void setCategory(String v){category=v;}
        public String getRowStatus(){return rowStatus;} public void setRowStatus(String v){rowStatus=v;}
        public String getEmployeeName(){return employeeName;} public void setEmployeeName(String v){employeeName=v;}
        public String getPhoneNumberMasked(){return phoneNumberMasked;} public void setPhoneNumberMasked(String v){phoneNumberMasked=v;}
        public String getIdNumberMasked(){return idNumberMasked;} public void setIdNumberMasked(String v){idNumberMasked=v;}
        public String getBankAccountMasked(){return bankAccountMasked;} public void setBankAccountMasked(String v){bankAccountMasked=v;}
        public String getRegisteredResidenceMasked(){return registeredResidenceMasked;} public void setRegisteredResidenceMasked(String v){registeredResidenceMasked=v;}
        public String getCurrentAddressMasked(){return currentAddressMasked;} public void setCurrentAddressMasked(String v){currentAddressMasked=v;}
        public String getWarningCodes(){return warningCodes;} public void setWarningCodes(String v){warningCodes=v;}
        public String getErrorCodes(){return errorCodes;} public void setErrorCodes(String v){errorCodes=v;}
        public String getCandidateSummary(){return candidateSummary;} public void setCandidateSummary(String v){candidateSummary=v;}
        @com.fasterxml.jackson.databind.annotation.JsonSerialize(using = com.fasterxml.jackson.databind.ser.std.ToStringSerializer.class)
    public Long getCandidateUserId(){return candidateUserId;} public void setCandidateUserId(Long v){candidateUserId=v;}
        @com.fasterxml.jackson.databind.annotation.JsonSerialize(using = com.fasterxml.jackson.databind.ser.std.ToStringSerializer.class)
    public Long getCandidateOnboardingId(){return candidateOnboardingId;} public void setCandidateOnboardingId(Long v){candidateOnboardingId=v;}
        public String getCompanyName(){return companyName;} public void setCompanyName(String v){companyName=v;}
        public String getDeptLevel1Name(){return deptLevel1Name;} public void setDeptLevel1Name(String v){deptLevel1Name=v;}
        public String getDeptLevel2Name(){return deptLevel2Name;} public void setDeptLevel2Name(String v){deptLevel2Name=v;}
        public String getDeptLevel3Name(){return deptLevel3Name;} public void setDeptLevel3Name(String v){deptLevel3Name=v;}
        public String getStoreName(){return storeName;} public void setStoreName(String v){storeName=v;}
        public String getPositionName(){return positionName;} public void setPositionName(String v){positionName=v;}
        public String getEmployeeCategory(){return employeeCategory;} public void setEmployeeCategory(String v){employeeCategory=v;}
        public String getExpectedEntryDateText(){return expectedEntryDateText;} public void setExpectedEntryDateText(String v){expectedEntryDateText=v;}
        public String getEmergencyContactPhoneMasked(){return emergencyContactPhoneMasked;} public void setEmergencyContactPhoneMasked(String v){emergencyContactPhoneMasked=v;}
        public String getResultCode(){return resultCode;} public void setResultCode(String v){resultCode=v;}
        public String getResultMessage(){return resultMessage;} public void setResultMessage(String v){resultMessage=v;}
        @com.fasterxml.jackson.databind.annotation.JsonSerialize(using = com.fasterxml.jackson.databind.ser.std.ToStringSerializer.class)
    public Long getResultOnboardingId(){return resultOnboardingId;} public void setResultOnboardingId(Long v){resultOnboardingId=v;}
    }
}
