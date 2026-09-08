package com.erp.system.domain.vo;

public class LegacyCredentialRotationCandidateVo
{
    private final Long userId;
    private final Long deptId;
    private final String accountStatus;

    public LegacyCredentialRotationCandidateVo(Long userId, Long deptId, String accountStatus)
    {
        this.userId = userId;
        this.deptId = deptId;
        this.accountStatus = accountStatus;
    }

    public Long getUserId() { return userId; }
    public Long getDeptId() { return deptId; }
    public String getAccountStatus() { return accountStatus; }
}

