package com.erp.system.domain;

import java.util.Date;
import com.erp.common.core.web.domain.BaseEntity;

/** HR onboarding import batch summary; confirmed summaries are retained. */
public class HrOnboardingImportBatch extends BaseEntity
{
    private static final long serialVersionUID = 1L;
    public static final String PREVIEWED = "PREVIEWED";
    public static final String PROCESSING = "PROCESSING";
    public static final String COMPLETED = "COMPLETED";
    public static final String COMPLETED_WITH_ERRORS = "COMPLETED_WITH_ERRORS";
    public static final String FAILED = "FAILED";

    private Long batchId;
    private String batchNo;
    private String fileName;
    private Long fileSize;
    private String fileHash;
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
    private Long creatorUserId;
    private String creatorName;
    private Long confirmedByUserId;
    private String confirmedBy;
    private Date confirmedTime;
    private Date expiresTime;
    private String resultSummary;

    public Long getBatchId() { return batchId; }
    public void setBatchId(Long batchId) { this.batchId = batchId; }
    public String getBatchNo() { return batchNo; }
    public void setBatchNo(String batchNo) { this.batchNo = batchNo; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }
    public String getFileHash() { return fileHash; }
    public void setFileHash(String fileHash) { this.fileHash = fileHash; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public Integer getTotalRows() { return totalRows; }
    public void setTotalRows(Integer totalRows) { this.totalRows = totalRows; }
    public Integer getImportableRows() { return importableRows; }
    public void setImportableRows(Integer importableRows) { this.importableRows = importableRows; }
    public Integer getWarningRows() { return warningRows; }
    public void setWarningRows(Integer warningRows) { this.warningRows = warningRows; }
    public Integer getInvalidRows() { return invalidRows; }
    public void setInvalidRows(Integer invalidRows) { this.invalidRows = invalidRows; }
    public Integer getDuplicateRows() { return duplicateRows; }
    public void setDuplicateRows(Integer duplicateRows) { this.duplicateRows = duplicateRows; }
    public Integer getBindableRows() { return bindableRows; }
    public void setBindableRows(Integer bindableRows) { this.bindableRows = bindableRows; }
    public Integer getSuccessRows() { return successRows; }
    public void setSuccessRows(Integer successRows) { this.successRows = successRows; }
    public Integer getFailureRows() { return failureRows; }
    public void setFailureRows(Integer failureRows) { this.failureRows = failureRows; }
    public Long getCreatorUserId() { return creatorUserId; }
    public void setCreatorUserId(Long creatorUserId) { this.creatorUserId = creatorUserId; }
    public String getCreatorName() { return creatorName; }
    public void setCreatorName(String creatorName) { this.creatorName = creatorName; }
    public Long getConfirmedByUserId() { return confirmedByUserId; }
    public void setConfirmedByUserId(Long confirmedByUserId) { this.confirmedByUserId = confirmedByUserId; }
    public String getConfirmedBy() { return confirmedBy; }
    public void setConfirmedBy(String confirmedBy) { this.confirmedBy = confirmedBy; }
    public Date getConfirmedTime() { return confirmedTime; }
    public void setConfirmedTime(Date confirmedTime) { this.confirmedTime = confirmedTime; }
    public Date getExpiresTime() { return expiresTime; }
    public void setExpiresTime(Date expiresTime) { this.expiresTime = expiresTime; }
    public String getResultSummary() { return resultSummary; }
    public void setResultSummary(String resultSummary) { this.resultSummary = resultSummary; }
}
