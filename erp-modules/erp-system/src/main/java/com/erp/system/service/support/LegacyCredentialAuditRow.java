package com.erp.system.service.support;

public class LegacyCredentialAuditRow
{
    private final Long userId;
    private final Long deptId;
    private final String accountStatus;
    private final String classification;

    public LegacyCredentialAuditRow(Long userId, Long deptId, String accountStatus, String classification)
    {
        this.userId = userId;
        this.deptId = deptId;
        this.accountStatus = accountStatus;
        this.classification = classification;
    }

    public Long getUserId() { return userId; }
    public Long getDeptId() { return deptId; }
    public String getAccountStatus() { return accountStatus; }
    public String getClassification() { return classification; }
}

