package com.erp.system.domain;

import java.time.LocalDate;
import java.util.Date;
import com.erp.common.core.web.domain.BaseEntity;

/** 员工健康证历史记录。 */
public class HrHealthCertificate extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long certificateId;
    private Long userId;
    private Long deptIdSnapshot;
    private String certificateNo;
    private LocalDate issuedDate;
    private LocalDate validFrom;
    private LocalDate expiresOn;
    private String issuerName;
    private Long attachmentNodeId;
    private String reviewStatus;
    private String currentFlag;
    private Long reviewedByUserId;
    private String reviewedByName;
    private Date reviewedTime;
    private String rejectionReason;
    private Long approvalInstanceId;
    private Integer approvalRound;
    private String lastApprovalEventKey;
    private Long version;
    private String delFlag;

    public Long getCertificateId() { return certificateId; }
    public void setCertificateId(Long certificateId) { this.certificateId = certificateId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getDeptIdSnapshot() { return deptIdSnapshot; }
    public void setDeptIdSnapshot(Long deptIdSnapshot) { this.deptIdSnapshot = deptIdSnapshot; }
    public String getCertificateNo() { return certificateNo; }
    public void setCertificateNo(String certificateNo) { this.certificateNo = certificateNo; }
    public LocalDate getIssuedDate() { return issuedDate; }
    public void setIssuedDate(LocalDate issuedDate) { this.issuedDate = issuedDate; }
    public LocalDate getValidFrom() { return validFrom; }
    public void setValidFrom(LocalDate validFrom) { this.validFrom = validFrom; }
    public LocalDate getExpiresOn() { return expiresOn; }
    public void setExpiresOn(LocalDate expiresOn) { this.expiresOn = expiresOn; }
    public String getIssuerName() { return issuerName; }
    public void setIssuerName(String issuerName) { this.issuerName = issuerName; }
    public Long getAttachmentNodeId() { return attachmentNodeId; }
    public void setAttachmentNodeId(Long attachmentNodeId) { this.attachmentNodeId = attachmentNodeId; }
    public String getReviewStatus() { return reviewStatus; }
    public void setReviewStatus(String reviewStatus) { this.reviewStatus = reviewStatus; }
    public String getCurrentFlag() { return currentFlag; }
    public void setCurrentFlag(String currentFlag) { this.currentFlag = currentFlag; }
    public Long getReviewedByUserId() { return reviewedByUserId; }
    public void setReviewedByUserId(Long reviewedByUserId) { this.reviewedByUserId = reviewedByUserId; }
    public String getReviewedByName() { return reviewedByName; }
    public void setReviewedByName(String reviewedByName) { this.reviewedByName = reviewedByName; }
    public Date getReviewedTime() { return reviewedTime; }
    public void setReviewedTime(Date reviewedTime) { this.reviewedTime = reviewedTime; }
    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }
    public Long getApprovalInstanceId() { return approvalInstanceId; }
    public void setApprovalInstanceId(Long approvalInstanceId) { this.approvalInstanceId = approvalInstanceId; }
    public Integer getApprovalRound() { return approvalRound; }
    public void setApprovalRound(Integer approvalRound) { this.approvalRound = approvalRound; }
    public String getLastApprovalEventKey() { return lastApprovalEventKey; }
    public void setLastApprovalEventKey(String lastApprovalEventKey) { this.lastApprovalEventKey = lastApprovalEventKey; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
    public String getDelFlag() { return delFlag; }
    public void setDelFlag(String delFlag) { this.delFlag = delFlag; }
}
