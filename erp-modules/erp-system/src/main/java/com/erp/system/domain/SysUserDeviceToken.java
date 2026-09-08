package com.erp.system.domain;

import java.util.Date;
import com.erp.common.core.web.domain.BaseEntity;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 用户移动设备推送令牌 sys_user_device_token。
 *
 * 该对象仅作为入参和持久化载体，不得直接作为接口响应或写入日志。
 */
public class SysUserDeviceToken extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long tokenId;

    private Long userId;

    private String platform;

    private String token;

    private String tokenHash;

    private String appId;

    private String deviceName;

    private String enabled;

    private Date lastRegisteredTime;

    private Date disabledTime;

    public Long getTokenId()
    {
        return tokenId;
    }

    public void setTokenId(Long tokenId)
    {
        this.tokenId = tokenId;
    }

    public Long getUserId()
    {
        return userId;
    }

    public void setUserId(Long userId)
    {
        this.userId = userId;
    }

    public String getPlatform()
    {
        return platform;
    }

    public void setPlatform(String platform)
    {
        this.platform = platform;
    }

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    public String getToken()
    {
        return token;
    }

    public void setToken(String token)
    {
        this.token = token;
    }

    @JsonIgnore
    public String getTokenHash()
    {
        return tokenHash;
    }

    public void setTokenHash(String tokenHash)
    {
        this.tokenHash = tokenHash;
    }

    public String getAppId()
    {
        return appId;
    }

    public void setAppId(String appId)
    {
        this.appId = appId;
    }

    public String getDeviceName()
    {
        return deviceName;
    }

    public void setDeviceName(String deviceName)
    {
        this.deviceName = deviceName;
    }

    public String getEnabled()
    {
        return enabled;
    }

    public void setEnabled(String enabled)
    {
        this.enabled = enabled;
    }

    public Date getLastRegisteredTime()
    {
        return lastRegisteredTime;
    }

    public void setLastRegisteredTime(Date lastRegisteredTime)
    {
        this.lastRegisteredTime = lastRegisteredTime;
    }

    public Date getDisabledTime()
    {
        return disabledTime;
    }

    public void setDisabledTime(Date disabledTime)
    {
        this.disabledTime = disabledTime;
    }
}
