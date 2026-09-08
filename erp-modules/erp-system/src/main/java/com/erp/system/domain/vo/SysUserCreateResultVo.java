package com.erp.system.domain.vo;

/** Result returned only from a successful administrator-created user request. */
public class SysUserCreateResultVo
{
    private SysTemporaryCredentialVo temporaryCredential;

    public SysUserCreateResultVo()
    {
    }

    public SysUserCreateResultVo(SysTemporaryCredentialVo temporaryCredential)
    {
        this.temporaryCredential = temporaryCredential;
    }

    public SysTemporaryCredentialVo getTemporaryCredential()
    {
        return temporaryCredential;
    }

    public void setTemporaryCredential(SysTemporaryCredentialVo temporaryCredential)
    {
        this.temporaryCredential = temporaryCredential;
    }
}
