package com.erp.system.domain.vo;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;

/** One-time response model. Never persist or log instances of this class. */
public class SysTemporaryCredentialVo
{
    private Long userId;

    private String userName;

    private String temporaryPassword;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date expiresAt;

    public SysTemporaryCredentialVo()
    {
    }

    public SysTemporaryCredentialVo(Long userId, String userName, String temporaryPassword, Date expiresAt)
    {
        this.userId = userId;
        this.userName = userName;
        this.temporaryPassword = temporaryPassword;
        this.expiresAt = expiresAt;
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

    public Date getExpiresAt()
    {
        return expiresAt;
    }

    public void setExpiresAt(Date expiresAt)
    {
        this.expiresAt = expiresAt;
    }
}
