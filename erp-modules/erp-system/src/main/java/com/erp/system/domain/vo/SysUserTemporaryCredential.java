package com.erp.system.domain.vo;

/**
 * 批量导入中一次性返回的用户临时凭据。
 */
public class SysUserTemporaryCredential
{
    private String userName;
    private String temporaryPassword;

    public SysUserTemporaryCredential()
    {
    }

    public SysUserTemporaryCredential(String userName, String temporaryPassword)
    {
        this.userName = userName;
        this.temporaryPassword = temporaryPassword;
    }

    public String getUserName()
    {
        return userName;
    }

    public void setUserName(String userName)
    {
        this.userName = userName;
    }

    public String getTemporaryPassword()
    {
        return temporaryPassword;
    }

    public void setTemporaryPassword(String temporaryPassword)
    {
        this.temporaryPassword = temporaryPassword;
    }
}
