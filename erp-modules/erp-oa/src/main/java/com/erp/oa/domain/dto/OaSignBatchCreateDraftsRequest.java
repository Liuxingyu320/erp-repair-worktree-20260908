package com.erp.oa.domain.dto;

import java.util.List;

public class OaSignBatchCreateDraftsRequest
{
    private Long planId;
    private List<OaSignBatchPreviewRow> rows;
    private String emergencyReason;

    public Long getPlanId() { return planId; }
    public void setPlanId(Long planId) { this.planId = planId; }

    public List<OaSignBatchPreviewRow> getRows() { return rows; }
    public void setRows(List<OaSignBatchPreviewRow> rows) { this.rows = rows; }

    public String getEmergencyReason() { return emergencyReason; }
    public void setEmergencyReason(String emergencyReason) { this.emergencyReason = emergencyReason; }
}
