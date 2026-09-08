package com.erp.oa.domain.vo;

/**
 * Minimal business-facing projection for a failed signing notification.
 * Delivery payloads, recipients and error details intentionally stay server-side.
 */
public class OaSignNotificationFailure
{
    private Long taskId;
    private String notificationBusinessKey;

    public Long getTaskId()
    {
        return taskId;
    }

    public void setTaskId(Long taskId)
    {
        this.taskId = taskId;
    }

    public String getNotificationBusinessKey()
    {
        return notificationBusinessKey;
    }

    public void setNotificationBusinessKey(String notificationBusinessKey)
    {
        this.notificationBusinessKey = notificationBusinessKey;
    }
}
