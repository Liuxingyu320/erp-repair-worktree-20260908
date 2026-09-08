package com.erp.oa.domain.vo;

import java.util.ArrayList;
import java.util.List;
import com.erp.oa.domain.OaSignPackage;

/** Business-only result produced by one signing scenario rule. */
public class OaSignDraftDecision
{
    private Action action;
    private Long planVersionId;
    private String riskLevel;
    private List<String> reasonCodes = new ArrayList<>();
    private OaSignPackage draftPackage;

    public enum Action
    {
        CREATE_DRAFT,
        NEEDS_DATA,
        NO_ACTION
    }

    public Action getAction() { return action; }
    public void setAction(Action action) { this.action = action; }
    public Long getPlanVersionId() { return planVersionId; }
    public void setPlanVersionId(Long planVersionId) { this.planVersionId = planVersionId; }
    public String getRiskLevel() { return riskLevel; }
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }
    public List<String> getReasonCodes() { return new ArrayList<>(reasonCodes); }
    public void setReasonCodes(List<String> reasonCodes)
    {
        this.reasonCodes = reasonCodes == null ? new ArrayList<>() : new ArrayList<>(reasonCodes);
    }
    public OaSignPackage getDraftPackage() { return draftPackage; }
    public void setDraftPackage(OaSignPackage draftPackage) { this.draftPackage = draftPackage; }
}
