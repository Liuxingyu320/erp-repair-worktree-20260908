package com.erp.system.domain.vo;

import java.util.Collections;
import java.util.List;

public class LegacyCredentialRotationResultVo
{
    private final int rotatedCount;
    private final boolean accountsRemainDisabled;
    private final List<SysTemporaryCredentialVo> temporaryCredentials;

    public LegacyCredentialRotationResultVo(List<SysTemporaryCredentialVo> temporaryCredentials)
    {
        this.temporaryCredentials = Collections.unmodifiableList(temporaryCredentials);
        this.rotatedCount = temporaryCredentials.size();
        this.accountsRemainDisabled = true;
    }

    public int getRotatedCount() { return rotatedCount; }
    public boolean isAccountsRemainDisabled() { return accountsRemainDisabled; }
    public List<SysTemporaryCredentialVo> getTemporaryCredentials() { return temporaryCredentials; }
}

