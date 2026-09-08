package com.erp.approval.domain;

import java.util.Date;
import java.util.List;
import com.erp.common.core.web.domain.BaseEntity;

/** Immutable definition version once published. */
public class ApprovalRuleVersion extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long versionId;
    private Long ruleId;
    private Integer versionNo;
    private String versionStatus;
    private String definitionSnapshot;
    private String definitionChecksum;
    private Long publishedByUserId;
    private String publishedByName;
    private Date publishedTime;
    private Long lockVersion;
    private List<ApprovalRuleCondition> conditions;
    private List<ApprovalVersionNode> nodes;

    public Long getVersionId() { return versionId; }
    public void setVersionId(Long versionId) { this.versionId = versionId; }
    public Long getRuleId() { return ruleId; }
    public void setRuleId(Long ruleId) { this.ruleId = ruleId; }
    public Integer getVersionNo() { return versionNo; }
    public void setVersionNo(Integer versionNo) { this.versionNo = versionNo; }
    public String getVersionStatus() { return versionStatus; }
    public void setVersionStatus(String versionStatus) { this.versionStatus = versionStatus; }
    public String getDefinitionSnapshot() { return definitionSnapshot; }
    public void setDefinitionSnapshot(String definitionSnapshot) { this.definitionSnapshot = definitionSnapshot; }
    public String getDefinitionChecksum() { return definitionChecksum; }
    public void setDefinitionChecksum(String definitionChecksum) { this.definitionChecksum = definitionChecksum; }
    public Long getPublishedByUserId() { return publishedByUserId; }
    public void setPublishedByUserId(Long publishedByUserId) { this.publishedByUserId = publishedByUserId; }
    public String getPublishedByName() { return publishedByName; }
    public void setPublishedByName(String publishedByName) { this.publishedByName = publishedByName; }
    public Date getPublishedTime() { return publishedTime; }
    public void setPublishedTime(Date publishedTime) { this.publishedTime = publishedTime; }
    public Long getLockVersion() { return lockVersion; }
    public void setLockVersion(Long lockVersion) { this.lockVersion = lockVersion; }
    public List<ApprovalRuleCondition> getConditions() { return conditions; }
    public void setConditions(List<ApprovalRuleCondition> conditions) { this.conditions = conditions; }
    public List<ApprovalVersionNode> getNodes() { return nodes; }
    public void setNodes(List<ApprovalVersionNode> nodes) { this.nodes = nodes; }
}
