package com.erp.system.domain;

import java.io.Serializable;
import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * 公告发布时固化的接收人快照。
 */
public class SysNoticeRecipient implements Serializable
{
    private static final long serialVersionUID = 1L;

    private Long noticeId;
    private Long userId;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date deliveredTime;

    private String recipientSource;

    public Long getNoticeId() { return noticeId; }
    public void setNoticeId(Long noticeId) { this.noticeId = noticeId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Date getDeliveredTime() { return deliveredTime; }
    public void setDeliveredTime(Date deliveredTime) { this.deliveredTime = deliveredTime; }
    public String getRecipientSource() { return recipientSource; }
    public void setRecipientSource(String recipientSource) { this.recipientSource = recipientSource; }
}
