package com.erp.approval.domain;

import java.util.Date;
import com.erp.common.core.web.domain.BaseEntity;

/** One immutable business submission round in the unified engine. */
public class ApprovalInstance extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long instanceId;
    private Long rootInstanceId;
    private Long previousInstanceId;
    private Long templateId;
    /** Read-only monitor projection. */
    private String templateName;
    private String businessCode;
    private String businessSource;
    private String businessId;
    private String businessSubtype;
    private Integer businessRound;
    private String idempotencyKey;
    private Long applicantUserId;
    private String applicantName;
    private Long applicantDeptId;
    private String applicantDeptName;
    private Long anchorDeptId;
    private String anchorDeptName;
    private Long ruleId;
    private Long ruleVersionId;
    private Integer ruleVersionNo;
    private String businessSnapshot;
    private String businessDigest;
    private String routeSnapshot;
    private String status;
    private Long currentNodeId;
    private Integer currentNodeOrder;
    /** Read-only monitor projection. */
    private String currentNodeName;
    private String callbackStatus;
    private Integer approvedActionCount;
    private Date startedTime;
    private Date finishedTime;
    private Long lockVersion;

    public Long getInstanceId() { return instanceId; }
    public void setInstanceId(Long instanceId) { this.instanceId = instanceId; }
    public Long getRootInstanceId() { return rootInstanceId; }
    public void setRootInstanceId(Long rootInstanceId) { this.rootInstanceId = rootInstanceId; }
    public Long getPreviousInstanceId() { return previousInstanceId; }
    public void setPreviousInstanceId(Long previousInstanceId) { this.previousInstanceId = previousInstanceId; }
    public Long getTemplateId() { return templateId; }
    public void setTemplateId(Long templateId) { this.templateId = templateId; }
    public String getTemplateName() { return templateName; }
    public void setTemplateName(String templateName) { this.templateName = templateName; }
    public String getBusinessCode() { return businessCode; }
    public void setBusinessCode(String businessCode) { this.businessCode = businessCode; }
    public String getBusinessSource() { return businessSource; }
    public void setBusinessSource(String businessSource) { this.businessSource = businessSource; }
    public String getBusinessId() { return businessId; }
    public void setBusinessId(String businessId) { this.businessId = businessId; }
    public String getBusinessSubtype() { return businessSubtype; }
    public void setBusinessSubtype(String businessSubtype) { this.businessSubtype = businessSubtype; }
    public Integer getBusinessRound() { return businessRound; }
    public void setBusinessRound(Integer businessRound) { this.businessRound = businessRound; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public Long getApplicantUserId() { return applicantUserId; }
    public void setApplicantUserId(Long applicantUserId) { this.applicantUserId = applicantUserId; }
    public String getApplicantName() { return applicantName; }
    public void setApplicantName(String applicantName) { this.applicantName = applicantName; }
    public Long getApplicantDeptId() { return applicantDeptId; }
    public void setApplicantDeptId(Long applicantDeptId) { this.applicantDeptId = applicantDeptId; }
    public String getApplicantDeptName() { return applicantDeptName; }
    public void setApplicantDeptName(String applicantDeptName) { this.applicantDeptName = applicantDeptName; }
    public Long getAnchorDeptId() { return anchorDeptId; }
    public void setAnchorDeptId(Long anchorDeptId) { this.anchorDeptId = anchorDeptId; }
    public String getAnchorDeptName() { return anchorDeptName; }
    public void setAnchorDeptName(String anchorDeptName) { this.anchorDeptName = anchorDeptName; }
    public Long getRuleId() { return ruleId; }
    public void setRuleId(Long ruleId) { this.ruleId = ruleId; }
    public Long getRuleVersionId() { return ruleVersionId; }
    public void setRuleVersionId(Long ruleVersionId) { this.ruleVersionId = ruleVersionId; }
    public Integer getRuleVersionNo() { return ruleVersionNo; }
    public void setRuleVersionNo(Integer ruleVersionNo) { this.ruleVersionNo = ruleVersionNo; }
    public String getBusinessSnapshot() { return businessSnapshot; }
    public void setBusinessSnapshot(String businessSnapshot) { this.businessSnapshot = businessSnapshot; }
    public String getBusinessDigest() { return businessDigest; }
    public void setBusinessDigest(String businessDigest) { this.businessDigest = businessDigest; }
    public String getRouteSnapshot() { return routeSnapshot; }
    public void setRouteSnapshot(String routeSnapshot) { this.routeSnapshot = routeSnapshot; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getCurrentNodeId() { return currentNodeId; }
    public void setCurrentNodeId(Long currentNodeId) { this.currentNodeId = currentNodeId; }
    public Integer getCurrentNodeOrder() { return currentNodeOrder; }
    public void setCurrentNodeOrder(Integer currentNodeOrder) { this.currentNodeOrder = currentNodeOrder; }
    public String getCurrentNodeName() { return currentNodeName; }
    public void setCurrentNodeName(String currentNodeName) { this.currentNodeName = currentNodeName; }
    public String getCallbackStatus() { return callbackStatus; }
    public void setCallbackStatus(String callbackStatus) { this.callbackStatus = callbackStatus; }
    public Integer getApprovedActionCount() { return approvedActionCount; }
    public void setApprovedActionCount(Integer approvedActionCount) { this.approvedActionCount = approvedActionCount; }
    public Date getStartedTime() { return startedTime; }
    public void setStartedTime(Date startedTime) { this.startedTime = startedTime; }
    public Date getFinishedTime() { return finishedTime; }
    public void setFinishedTime(Date finishedTime) { this.finishedTime = finishedTime; }
    public Long getLockVersion() { return lockVersion; }
    public void setLockVersion(Long lockVersion) { this.lockVersion = lockVersion; }
}
