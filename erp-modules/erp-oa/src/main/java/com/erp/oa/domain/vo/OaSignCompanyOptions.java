package com.erp.oa.domain.vo;

import java.util.List;
import com.erp.oa.domain.OaCompanySealConfig;
import com.erp.system.api.domain.SysLegalEntity;

public class OaSignCompanyOptions
{
    private OaLegalEntityCandidate automaticCandidate;
    private List<SysLegalEntity> legalEntities;
    private List<OaCompanySealConfig> seals;
    private Long recommendedSealId;
    private Boolean companySealRequired;
    private String recommendedLegalEntityName;
    private String recommendedLegalRepresentative;
    private String recommendedRegisteredAddress;

    public OaLegalEntityCandidate getAutomaticCandidate() { return automaticCandidate; }
    public void setAutomaticCandidate(OaLegalEntityCandidate automaticCandidate) { this.automaticCandidate = automaticCandidate; }
    public List<SysLegalEntity> getLegalEntities() { return legalEntities; }
    public void setLegalEntities(List<SysLegalEntity> legalEntities) { this.legalEntities = legalEntities; }
    public List<OaCompanySealConfig> getSeals() { return seals; }
    public void setSeals(List<OaCompanySealConfig> seals) { this.seals = seals; }
    public Long getRecommendedSealId() { return recommendedSealId; }
    public void setRecommendedSealId(Long recommendedSealId) { this.recommendedSealId = recommendedSealId; }
    public Boolean getCompanySealRequired() { return companySealRequired; }
    public void setCompanySealRequired(Boolean companySealRequired) { this.companySealRequired = companySealRequired; }
    public String getRecommendedLegalEntityName() { return recommendedLegalEntityName; }
    public void setRecommendedLegalEntityName(String recommendedLegalEntityName) { this.recommendedLegalEntityName = recommendedLegalEntityName; }
    public String getRecommendedLegalRepresentative() { return recommendedLegalRepresentative; }
    public void setRecommendedLegalRepresentative(String recommendedLegalRepresentative) { this.recommendedLegalRepresentative = recommendedLegalRepresentative; }
    public String getRecommendedRegisteredAddress() { return recommendedRegisteredAddress; }
    public void setRecommendedRegisteredAddress(String recommendedRegisteredAddress) { this.recommendedRegisteredAddress = recommendedRegisteredAddress; }
}
