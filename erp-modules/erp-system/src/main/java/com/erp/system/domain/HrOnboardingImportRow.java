package com.erp.system.domain;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import com.erp.common.core.web.domain.BaseEntity;

/** Raw staging row. Never return this type directly from a controller. */
public class HrOnboardingImportRow extends BaseEntity
{
    private static final long serialVersionUID = 1L;
    private Long rowId;
    private Long batchId;
    private Integer sourceRowNumber;
    private String category;
    private String rowStatus;
    private Integer version;
    private String payloadJson;
    private transient HrOnboarding payload;
    /** Original cell display values used only for safe diagnostics/export, never returned directly. */
    private transient Map<String, String> rawSourceValues = new LinkedHashMap<>();
    private String warningCodes;
    private String errorCodes;
    private String candidateSummary;
    private String candidateType;
    private Long candidateUserId;
    private Long candidateOnboardingId;
    private String decision;
    private Long bindUserId;
    private Long resultOnboardingId;
    private String resultCode;
    private String resultMessage;
    private Long processedByUserId;
    private String processedBy;

    public List<String> getWarningCodeList() { return codes(warningCodes); }
    public void setWarningCodeList(List<String> codes) { warningCodes = join(codes); }
    public List<String> getErrorCodeList() { return codes(errorCodes); }
    public void setErrorCodeList(List<String> codes) { errorCodes = join(codes); }
    private static List<String> codes(String value) { return value == null || value.isEmpty() ? new ArrayList<>() : Arrays.stream(value.split(",")).filter(v -> !v.isEmpty()).collect(Collectors.toCollection(ArrayList::new)); }
    private static String join(List<String> values) { return values == null || values.isEmpty() ? null : String.join(",", values); }

    public Long getRowId() { return rowId; }
    public void setRowId(Long rowId) { this.rowId = rowId; }
    public Long getBatchId() { return batchId; }
    public void setBatchId(Long batchId) { this.batchId = batchId; }
    public Integer getSourceRowNumber() { return sourceRowNumber; }
    public void setSourceRowNumber(Integer sourceRowNumber) { this.sourceRowNumber = sourceRowNumber; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getRowStatus() { return rowStatus; }
    public void setRowStatus(String rowStatus) { this.rowStatus = rowStatus; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public String getPayloadJson() { return payloadJson; }
    public void setPayloadJson(String payloadJson) { this.payloadJson = payloadJson; }
    public HrOnboarding getPayload() { return payload; }
    public void setPayload(HrOnboarding payload) { this.payload = payload; }
    public Map<String, String> getRawSourceValues() { return rawSourceValues; }
    public void setRawSourceValues(Map<String, String> rawSourceValues) {
        this.rawSourceValues = rawSourceValues == null ? new LinkedHashMap<>() : new LinkedHashMap<>(rawSourceValues);
    }
    public String getWarningCodes() { return warningCodes; }
    public void setWarningCodes(String warningCodes) { this.warningCodes = warningCodes; }
    public String getErrorCodes() { return errorCodes; }
    public void setErrorCodes(String errorCodes) { this.errorCodes = errorCodes; }
    public String getCandidateSummary() { return candidateSummary; }
    public void setCandidateSummary(String candidateSummary) { this.candidateSummary = candidateSummary; }
    public String getCandidateType() { return candidateType; }
    public void setCandidateType(String candidateType) { this.candidateType = candidateType; }
    public Long getCandidateUserId() { return candidateUserId; }
    public void setCandidateUserId(Long candidateUserId) { this.candidateUserId = candidateUserId; }
    public Long getCandidateOnboardingId() { return candidateOnboardingId; }
    public void setCandidateOnboardingId(Long candidateOnboardingId) { this.candidateOnboardingId = candidateOnboardingId; }
    public String getDecision() { return decision; }
    public void setDecision(String decision) { this.decision = decision; }
    public Long getBindUserId() { return bindUserId; }
    public void setBindUserId(Long bindUserId) { this.bindUserId = bindUserId; }
    public Long getResultOnboardingId() { return resultOnboardingId; }
    public void setResultOnboardingId(Long resultOnboardingId) { this.resultOnboardingId = resultOnboardingId; }
    public String getResultCode() { return resultCode; }
    public void setResultCode(String resultCode) { this.resultCode = resultCode; }
    public String getResultMessage() { return resultMessage; }
    public void setResultMessage(String resultMessage) { this.resultMessage = resultMessage; }
    public Long getProcessedByUserId() { return processedByUserId; }
    public void setProcessedByUserId(Long processedByUserId) { this.processedByUserId = processedByUserId; }
    public String getProcessedBy() { return processedBy; }
    public void setProcessedBy(String processedBy) { this.processedBy = processedBy; }
}
