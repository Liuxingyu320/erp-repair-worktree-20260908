package com.erp.system.domain.dto;

public class HrHealthCertificateReviewRequest
{
    private String decision;
    private String rejectionReason;
    private Long version;

    public String getDecision() { return decision; }
    public void setDecision(String decision) { this.decision = decision; }
    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}
