package com.erp.system.domain.vo;

/**
 * 用户凭据创建结果。临时密码只允许在本次响应中返回。
 */
public class SysUserCredentialProvisioningResult
{
    private Long userId;
    private String userName;
    private String temporaryPassword;
    private boolean mustChangePassword;

    public SysUserCredentialProvisioningResult()
    {
    }

    public SysUserCredentialProvisioningResult(Long userId, String userName, String temporaryPassword,
            boolean mustChangePassword)
    {
        this.userId = userId;
        this.userName = userName;
        this.temporaryPassword = temporaryPassword;
        this.mustChangePassword = mustChangePassword;
    }

    public Long getUserId()
    {
        return userId;
    }

    public void setUserId(Long userId)
    {
        this.userId = userId;
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

    public boolean isMustChangePassword()
    {
        return mustChangePassword;
    }

    public void setMustChangePassword(boolean mustChangePassword)
    {
        this.mustChangePassword = mustChangePassword;
    }
}
