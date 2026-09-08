package com.erp.file.drive.domain;

import java.util.Date;
import com.erp.common.core.web.domain.BaseEntity;

/**
 * 当前生效的个人盘额度策略。策略对象可以是全员、岗位或用户。
 */
public class DrivePersonalQuotaPolicy extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long policyId;
    private String subjectType;
    private Long subjectId;
    private String subjectName;
    private Long quotaBytes;
    private Integer priority;
    private Date expireTime;
    private String status;
    private Integer version;

    public Long getPolicyId() { return policyId; }
    public void setPolicyId(Long policyId) { this.policyId = policyId; }
    public String getSubjectType() { return subjectType; }
    public void setSubjectType(String subjectType) { this.subjectType = subjectType; }
    public Long getSubjectId() { return subjectId; }
    public void setSubjectId(Long subjectId) { this.subjectId = subjectId; }
    public String getSubjectName() { return subjectName; }
    public void setSubjectName(String subjectName) { this.subjectName = subjectName; }
    public Long getQuotaBytes() { return quotaBytes; }
    public void setQuotaBytes(Long quotaBytes) { this.quotaBytes = quotaBytes; }
    public Integer getPriority() { return priority; }
    public void setPriority(Integer priority) { this.priority = priority; }
    public Date getExpireTime() { return expireTime; }
    public void setExpireTime(Date expireTime) { this.expireTime = expireTime; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
}

