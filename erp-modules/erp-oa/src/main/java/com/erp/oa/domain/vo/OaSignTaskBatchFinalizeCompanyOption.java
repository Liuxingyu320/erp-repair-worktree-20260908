package com.erp.oa.domain.vo;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

/** One active legal entity and its currently verifiable contract seals. */
public class OaSignTaskBatchFinalizeCompanyOption
{
    @JsonSerialize(using = ToStringSerializer.class)
    private Long legalEntityId;
    private String legalEntityCode;
    private String legalEntityName;
    private String unifiedSocialCreditCode;
    private String registeredAddress;
    private String legalRepresentative;
    private Boolean departmentCandidate;
    private Boolean excelCandidate;
    private BigDecimal excelMatchScore;
    private Boolean contractReady;
    private Boolean selectable;
    private List<String> missingMasterFields = new ArrayList<>();
    private List<OaSignTaskBatchFinalizeSealOption> availableContractSeals = new ArrayList<>();
    @JsonSerialize(using = ToStringSerializer.class)
    private Long recommendedSealId;
    private String sealRecommendationMode;
    private List<String> sealValidationMessages = new ArrayList<>();

    public Long getLegalEntityId() { return legalEntityId; }
    public void setLegalEntityId(Long legalEntityId) { this.legalEntityId = legalEntityId; }
    public String getLegalEntityCode() { return legalEntityCode; }
    public void setLegalEntityCode(String legalEntityCode) { this.legalEntityCode = legalEntityCode; }
    public String getLegalEntityName() { return legalEntityName; }
    public void setLegalEntityName(String legalEntityName) { this.legalEntityName = legalEntityName; }
    public String getUnifiedSocialCreditCode() { return unifiedSocialCreditCode; }
    public void setUnifiedSocialCreditCode(String value) { unifiedSocialCreditCode = value; }
    public String getRegisteredAddress() { return registeredAddress; }
    public void setRegisteredAddress(String registeredAddress) { this.registeredAddress = registeredAddress; }
    public String getLegalRepresentative() { return legalRepresentative; }
    public void setLegalRepresentative(String legalRepresentative) { this.legalRepresentative = legalRepresentative; }
    public Boolean getDepartmentCandidate() { return departmentCandidate; }
    public void setDepartmentCandidate(Boolean departmentCandidate) { this.departmentCandidate = departmentCandidate; }
    public Boolean getExcelCandidate() { return excelCandidate; }
    public void setExcelCandidate(Boolean excelCandidate) { this.excelCandidate = excelCandidate; }
    public BigDecimal getExcelMatchScore() { return excelMatchScore; }
    public void setExcelMatchScore(BigDecimal excelMatchScore) { this.excelMatchScore = excelMatchScore; }
    public Boolean getContractReady() { return contractReady; }
    public void setContractReady(Boolean contractReady) { this.contractReady = contractReady; }
    public Boolean getSelectable() { return selectable; }
    public void setSelectable(Boolean selectable) { this.selectable = selectable; }
    public List<String> getMissingMasterFields() { return missingMasterFields; }
    public void setMissingMasterFields(List<String> values)
    {
        missingMasterFields = values == null ? new ArrayList<>() : new ArrayList<>(values);
    }
    public List<OaSignTaskBatchFinalizeSealOption> getAvailableContractSeals() { return availableContractSeals; }
    public void setAvailableContractSeals(List<OaSignTaskBatchFinalizeSealOption> values)
    {
        availableContractSeals = values == null ? new ArrayList<>() : new ArrayList<>(values);
    }
    public Long getRecommendedSealId() { return recommendedSealId; }
    public void setRecommendedSealId(Long recommendedSealId) { this.recommendedSealId = recommendedSealId; }
    public String getSealRecommendationMode() { return sealRecommendationMode; }
    public void setSealRecommendationMode(String value) { sealRecommendationMode = value; }
    public List<String> getSealValidationMessages() { return sealValidationMessages; }
    public void setSealValidationMessages(List<String> values)
    {
        sealValidationMessages = values == null ? new ArrayList<>() : new ArrayList<>(values);
    }
}
