package com.erp.oa.domain.vo;

import java.util.Date;
import com.erp.oa.domain.OaSignPlanVersion;

/** Business-facing publication receipt. Integrity hashes remain internal. */
public class OaSignPlanVersionPublishResult
{
    private String action;
    private java.util.List<Long> previousActiveVersionIds;
    private Long versionId;
    private Long planId;
    private Integer versionNo;
    private String publishStatus;
    private String matchingStatus;
    private Long publishedByUserId;
    private String publishedBy;
    private Date publishedTime;

    public static OaSignPlanVersionPublishResult from(OaSignPlanVersion source)
    {
        OaSignPlanVersionPublishResult result = new OaSignPlanVersionPublishResult();
        if (source != null)
        {
            result.setVersionId(source.getVersionId());
            result.setPlanId(source.getPlanId());
            result.setVersionNo(source.getVersionNo());
            result.setPublishStatus(source.getPublishStatus());
            result.setMatchingStatus(source.getMatchingStatus());
            result.setPublishedByUserId(source.getPublishedByUserId());
            result.setPublishedBy(source.getPublishedBy());
            result.setPublishedTime(source.getPublishedTime());
        }
        return result;
    }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public java.util.List<Long> getPreviousActiveVersionIds() { return previousActiveVersionIds; }
    public void setPreviousActiveVersionIds(java.util.List<Long> values) { this.previousActiveVersionIds = values; }

    public Long getVersionId() { return versionId; }
    public void setVersionId(Long versionId) { this.versionId = versionId; }

    public Long getPlanId() { return planId; }
    public void setPlanId(Long planId) { this.planId = planId; }

    public Integer getVersionNo() { return versionNo; }
    public void setVersionNo(Integer versionNo) { this.versionNo = versionNo; }

    public String getPublishStatus() { return publishStatus; }
    public void setPublishStatus(String publishStatus) { this.publishStatus = publishStatus; }

    public String getMatchingStatus() { return matchingStatus; }
    public void setMatchingStatus(String matchingStatus) { this.matchingStatus = matchingStatus; }

    public Long getPublishedByUserId() { return publishedByUserId; }
    public void setPublishedByUserId(Long publishedByUserId) { this.publishedByUserId = publishedByUserId; }

    public String getPublishedBy() { return publishedBy; }
    public void setPublishedBy(String publishedBy) { this.publishedBy = publishedBy; }

    public Date getPublishedTime() { return publishedTime; }
    public void setPublishedTime(Date publishedTime) { this.publishedTime = publishedTime; }
}
