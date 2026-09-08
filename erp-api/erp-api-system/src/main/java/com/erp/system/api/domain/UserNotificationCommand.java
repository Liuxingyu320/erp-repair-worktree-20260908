package com.erp.system.api.domain;

import java.io.Serializable;

/**
 * 定向用户消息发布命令。
 */
public class UserNotificationCommand implements Serializable
{
    private static final long serialVersionUID = 1L;

    private String channel;

    private Long recipientUserId;

    private String businessKey;

    private String title;

    private String body;

    private String routeType;

    private String routeParams;

    public String getChannel()
    {
        return channel;
    }

    public void setChannel(String channel)
    {
        this.channel = channel;
    }

    public Long getRecipientUserId()
    {
        return recipientUserId;
    }

    public void setRecipientUserId(Long recipientUserId)
    {
        this.recipientUserId = recipientUserId;
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
}
