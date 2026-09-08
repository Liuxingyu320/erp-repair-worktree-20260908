package com.erp.inventory.domain.transfer;

import java.util.Date;

/** Server-only persisted binding for one opaque adjudication basis token. */
public class InvTransferReceiptDiscrepancyStoredAdjudicationBasis
{
    private Long basisId;
    private String tokenHash;
    private Long caseId;
    private Long caseVersion;
    private String factFingerprint;
    private Long sourceConfirmationEventId;
    private Long targetConfirmationEventId;
    private Long selectedShopDeptId;
    private String scopeDigest;
    private String requiredPermission;
    private Long adjudicatorUserId;
    private String adjudicatorName;
    private String basisStatus;
    private Date issuedAt;
    private Date expiresAt;
    private Date consumedAt;
    private String consumedRequestId;
    private Long consumedAdjudicationId;

    public Long getBasisId() { return basisId; }
    public void setBasisId(Long value) { basisId = value; }
    public String getTokenHash() { return tokenHash; }
    public void setTokenHash(String value) { tokenHash = value; }
    public Long getCaseId() { return caseId; }
    public void setCaseId(Long value) { caseId = value; }
    public Long getCaseVersion() { return caseVersion; }
    public void setCaseVersion(Long value) { caseVersion = value; }
    public String getFactFingerprint() { return factFingerprint; }
    public void setFactFingerprint(String value) { factFingerprint = value; }
    public Long getSourceConfirmationEventId() { return sourceConfirmationEventId; }
    public void setSourceConfirmationEventId(Long value) { sourceConfirmationEventId = value; }
    public Long getTargetConfirmationEventId() { return targetConfirmationEventId; }
    public void setTargetConfirmationEventId(Long value) { targetConfirmationEventId = value; }
    public Long getSelectedShopDeptId() { return selectedShopDeptId; }
    public void setSelectedShopDeptId(Long value) { selectedShopDeptId = value; }
    public String getScopeDigest() { return scopeDigest; }
    public void setScopeDigest(String value) { scopeDigest = value; }
    public String getRequiredPermission() { return requiredPermission; }
    public void setRequiredPermission(String value) { requiredPermission = value; }
    public Long getAdjudicatorUserId() { return adjudicatorUserId; }
    public void setAdjudicatorUserId(Long value) { adjudicatorUserId = value; }
    public String getAdjudicatorName() { return adjudicatorName; }
    public void setAdjudicatorName(String value) { adjudicatorName = value; }
    public String getBasisStatus() { return basisStatus; }
    public void setBasisStatus(String value) { basisStatus = value; }
    public Date getIssuedAt() { return issuedAt; }
    public void setIssuedAt(Date value) { issuedAt = value; }
    public Date getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Date value) { expiresAt = value; }
    public Date getConsumedAt() { return consumedAt; }
    public void setConsumedAt(Date value) { consumedAt = value; }
    public String getConsumedRequestId() { return consumedRequestId; }
    public void setConsumedRequestId(String value) { consumedRequestId = value; }
    public Long getConsumedAdjudicationId() { return consumedAdjudicationId; }
    public void setConsumedAdjudicationId(Long value) { consumedAdjudicationId = value; }
}
