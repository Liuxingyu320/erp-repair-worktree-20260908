package com.erp.system.api.model;

import java.io.Serializable;
import java.util.Date;
import java.util.Set;
import com.erp.system.api.domain.SysUser;

/**
 * 用户信息
 *
 * @author erp
 */
public class LoginUser implements Serializable
{
    private static final long serialVersionUID = 1L;

    /**
     * 用户唯一标识
     */
    private String token;

    /**
     * 用户名id
     */
    private Long userid;

    /**
     * 用户名
     */
    private String username;

    /**
     * 登录时间
     */
    private Long loginTime;

    /**
     * 过期时间
     */
    private Long expireTime;

    /**
     * 登录IP地址
     */
    private String ipaddr;

    /** 登录时固定的凭据状态，用于所有业务服务执行强制改密门禁。 */
    private String credentialState;

    /** 临时凭据失效时间。 */
    private Date temporaryPasswordExpiresAt;

    /**
     * 权限列表
     */
    private Set<String> permissions;

    /**
     * 角色列表
     */
    private Set<String> roles;

    /**
     * 用户信息
     */
    private SysUser sysUser;

    public String getToken()
    {
        return token;
    }

    public void setToken(String token)
    {
        this.token = token;
    }

    public Long getUserid()
    {
        return userid;
    }

    public void setUserid(Long userid)
    {
        this.userid = userid;
    }

    public String getUsername()
    {
        return username;
    }

    public void setUsername(String username)
    {
        this.username = username;
    }

    public Long getLoginTime()
    {
        return loginTime;
    }

    public void setLoginTime(Long loginTime)
    {
        this.loginTime = loginTime;
    }

    public Long getExpireTime()
    {
        return expireTime;
    }

    public void setExpireTime(Long expireTime)
    {
        this.expireTime = expireTime;
    }

    public String getIpaddr()
    {
        return ipaddr;
    }

    public void setIpaddr(String ipaddr)
    {
        this.ipaddr = ipaddr;
    }

    public String getCredentialState()
    {
        if (credentialState != null && !credentialState.isBlank())
        {
            return credentialState;
        }
        return sysUser == null ? SysUser.CREDENTIAL_STATE_ACTIVE : sysUser.resolvedCredentialState();
    }

    public void setCredentialState(String credentialState)
    {
        this.credentialState = credentialState;
    }

    public Date getTemporaryPasswordExpiresAt()
    {
        return temporaryPasswordExpiresAt != null ? temporaryPasswordExpiresAt
                : sysUser == null ? null : sysUser.getTemporaryPasswordExpiresAt();
    }

    public void setTemporaryPasswordExpiresAt(Date temporaryPasswordExpiresAt)
    {
        this.temporaryPasswordExpiresAt = temporaryPasswordExpiresAt;
    }

    public Set<String> getPermissions()
    {
        return permissions;
    }

    public void setPermissions(Set<String> permissions)
    {
        this.permissions = permissions;
    }

    public Set<String> getRoles()
    {
        return roles;
    }

    public void setRoles(Set<String> roles)
    {
        this.roles = roles;
    }

    public SysUser getSysUser()
    {
        return sysUser;
    }

    public void setSysUser(SysUser sysUser)
    {
        this.sysUser = sysUser;
    }
}
