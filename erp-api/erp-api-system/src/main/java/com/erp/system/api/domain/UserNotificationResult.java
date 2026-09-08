package com.erp.system.api.domain;

import java.io.Serializable;

/**
 * 定向用户消息处理结果。结果只允许暴露设备令牌后六位。
 */
public class UserNotificationResult implements Serializable
{
    private static final long serialVersionUID = 1L;

    private boolean accepted;

    private Long notificationId;

    private String status;

    private String message;

    private String tokenSuffix;

    public boolean getAccepted()
    {
        return accepted;
    }

    public void setAccepted(boolean accepted)
    {
        this.accepted = accepted;
    }

    public Long getNotificationId()
    {
        return notificationId;
    }

    public void setNotificationId(Long notificationId)
    {
        this.notificationId = notificationId;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public String getMessage()
    {
        return message;
    }

    public void setMessage(String message)
    {
        this.message = message;
    }

    public String getTokenSuffix()
    {
        return tokenSuffix;
    }

    public void setTokenSuffix(String tokenSuffix)
    {
        this.tokenSuffix = tokenSuffix;
    }

    @Override
    public String toString()
    {
        return "UserNotificationResult{" +
                "accepted=" + accepted +
                ", notificationId=" + notificationId +
                ", status='" + status + '\'' +
                ", message='" + message + '\'' +
                ", tokenSuffix='" + tokenSuffix + '\'' +
                '}';
    }
}
