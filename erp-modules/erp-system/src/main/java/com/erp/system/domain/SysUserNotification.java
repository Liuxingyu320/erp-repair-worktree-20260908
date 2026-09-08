package com.erp.system.domain;

import java.util.Date;
import com.erp.common.core.web.domain.BaseEntity;

/**
 * 指定用户可见的站内消息 sys_user_notification。
 */
public class SysUserNotification extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long notificationId;

    private Long userId;

    private String channel;

    private String businessKey;

    private String title;

    private String body;

    private String routeType;

    private String routeParams;

    /** 0未读，1已读。 */
    private String readStatus;

    private Date readTime;

    public Long getNotificationId()
    {
        return notificationId;
    }

    public void setNotificationId(Long notificationId)
    {
        this.notificationId = notificationId;
    }

    public Long getUserId()
    {
        return userId;
    }

    public void setUserId(Long userId)
    {
        this.userId = userId;
    }

    public String getChannel()
    {
        return channel;
    }

    public void setChannel(String channel)
    {
        this.channel = channel;
    }

    public String getBusinessKey()
    {
        return businessKey;
    }

    public void setBusinessKey(String businessKey)
    {
        this.businessKey = businessKey;
    }

    public String getTitle()
    {
        return title;
    }

    public void setTitle(String title)
    {
        this.title = title;
    }

    public String getBody()
    {
        return body;
    }

    public void setBody(String body)
    {
        this.body = body;
    }

    public String getRouteType()
    {
        return routeType;
    }

    public void setRouteType(String routeType)
    {
        this.routeType = routeType;
    }

    public String getRouteParams()
    {
        return routeParams;
    }

    public void setRouteParams(String routeParams)
    {
        this.routeParams = routeParams;
    }

    public String getReadStatus()
    {
        return readStatus;
    }

    public void setReadStatus(String readStatus)
    {
        this.readStatus = readStatus;
    }

    public Date getReadTime()
    {
        return readTime;
    }

    public void setReadTime(Date readTime)
    {
        this.readTime = readTime;
    }
}
