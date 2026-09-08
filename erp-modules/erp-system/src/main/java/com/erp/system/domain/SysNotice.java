package com.erp.system.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.erp.common.core.web.domain.BaseEntity;
import com.erp.common.core.xss.Xss;

/**
 * 通知公告表 sys_notice
 * 
 * @author erp
 */
public class SysNotice extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 公告ID */
    private Long noticeId;

    /** 公告标题 */
    private String noticeTitle;

    /** 公告类型（1通知 2公告） */
    private String noticeType;

    /** 公告内容 */
    private String noticeContent;

    /** 公告状态（0正常 1关闭） */
    private String status;

    /** 生命周期状态：DRAFT/SCHEDULED/PUBLISHED/OFFLINE */
    private String lifecycleStatus;

    /** 受众类型：ALL/DEPT/ROLE/USER/MIXED */
    private String audienceType;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date scheduledPublishTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date publishedTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date expireTime;

    /** 乐观锁与内容版本号 */
    private Long version;

    /** 上一版本公告ID */
    private Long previousNoticeId;

    /** 草稿受众规则 */
    private List<SysNoticeAudience> audiences = new ArrayList<>();

    /** 发布后接收人数 */
    private Integer recipientCount;

    /** 是否已读 */
    @JsonProperty("isRead")
    private boolean isRead;

    public Long getNoticeId()
    {
        return noticeId;
    }

    public void setNoticeId(Long noticeId)
    {
        this.noticeId = noticeId;
    }

    public void setNoticeTitle(String noticeTitle)
    {
        this.noticeTitle = noticeTitle;
    }

    @Xss(message = "公告标题不能包含脚本字符")
    @NotBlank(message = "公告标题不能为空")
    @Size(min = 0, max = 50, message = "公告标题不能超过50个字符")
    public String getNoticeTitle()
    {
        return noticeTitle;
    }

    public void setNoticeType(String noticeType)
    {
        this.noticeType = noticeType;
    }

    public String getNoticeType()
    {
        return noticeType;
    }

    public void setNoticeContent(String noticeContent)
    {
        this.noticeContent = noticeContent;
    }

    public String getNoticeContent()
    {
        return noticeContent;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public String getStatus()
    {
        return status;
    }

    public String getLifecycleStatus() { return lifecycleStatus; }
    public void setLifecycleStatus(String lifecycleStatus) { this.lifecycleStatus = lifecycleStatus; }
    public String getAudienceType() { return audienceType; }
    public void setAudienceType(String audienceType) { this.audienceType = audienceType; }
    public Date getScheduledPublishTime() { return scheduledPublishTime; }
    public void setScheduledPublishTime(Date scheduledPublishTime) { this.scheduledPublishTime = scheduledPublishTime; }
    public Date getPublishedTime() { return publishedTime; }
    public void setPublishedTime(Date publishedTime) { this.publishedTime = publishedTime; }
    public Date getExpireTime() { return expireTime; }
    public void setExpireTime(Date expireTime) { this.expireTime = expireTime; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
    public Long getPreviousNoticeId() { return previousNoticeId; }
    public void setPreviousNoticeId(Long previousNoticeId) { this.previousNoticeId = previousNoticeId; }
    public List<SysNoticeAudience> getAudiences() { return audiences; }
    public void setAudiences(List<SysNoticeAudience> audiences) { this.audiences = audiences; }
    public Integer getRecipientCount() { return recipientCount; }
    public void setRecipientCount(Integer recipientCount) { this.recipientCount = recipientCount; }

    public boolean getIsRead()
    {
        return isRead;
    }

    public void setIsRead(boolean isRead)
    {
        this.isRead = isRead;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this,ToStringStyle.MULTI_LINE_STYLE)
            .append("noticeId", getNoticeId())
            .append("noticeTitle", getNoticeTitle())
            .append("noticeType", getNoticeType())
            .append("noticeContent", getNoticeContent())
            .append("status", getStatus())
            .append("lifecycleStatus", getLifecycleStatus())
            .append("audienceType", getAudienceType())
            .append("scheduledPublishTime", getScheduledPublishTime())
            .append("publishedTime", getPublishedTime())
            .append("expireTime", getExpireTime())
            .append("version", getVersion())
            .append("previousNoticeId", getPreviousNoticeId())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("updateBy", getUpdateBy())
            .append("updateTime", getUpdateTime())
            .append("remark", getRemark())
            .toString();
    }
}
