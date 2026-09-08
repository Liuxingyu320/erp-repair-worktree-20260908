package com.erp.inventory.domain;

import java.util.Date;
import com.erp.common.core.web.domain.BaseEntity;

/**
 * Append-only business-content revision of a stable transfer order.
 */
public class InvTransferRevision extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long revisionId;
    private Long transferId;
    private Integer revisionNo;
    private Long parentRevisionId;
    private Integer approvalRound;
    private Long approvalInstanceId;
    private String status;
    private String headerSnapshot;
    private String detailSnapshot;
    private String snapshotHash;
    private String decisionAction;
    private String decisionReason;
    private Long decisionUserId;
    private String decisionUsername;
    private Date decisionTime;
    private Date sealedTime;

    public Long getRevisionId() { return revisionId; }
    public void setRevisionId(Long value) { revisionId = value; }
    public Long getTransferId() { return transferId; }
    public void setTransferId(Long value) { transferId = value; }
    public Integer getRevisionNo() { return revisionNo; }
    public void setRevisionNo(Integer value) { revisionNo = value; }
    public Long getParentRevisionId() { return parentRevisionId; }
    public void setParentRevisionId(Long value) { parentRevisionId = value; }
    public Integer getApprovalRound() { return approvalRound; }
    public void setApprovalRound(Integer value) { approvalRound = value; }
    public Long getApprovalInstanceId() { return approvalInstanceId; }
    public void setApprovalInstanceId(Long value) { approvalInstanceId = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { status = value; }
    public String getHeaderSnapshot() { return headerSnapshot; }
    public void setHeaderSnapshot(String value) { headerSnapshot = value; }
    public String getDetailSnapshot() { return detailSnapshot; }
    public void setDetailSnapshot(String value) { detailSnapshot = value; }
    public String getSnapshotHash() { return snapshotHash; }
    public void setSnapshotHash(String value) { snapshotHash = value; }
    public String getDecisionAction() { return decisionAction; }
    public void setDecisionAction(String value) { decisionAction = value; }
    public String getDecisionReason() { return decisionReason; }
    public void setDecisionReason(String value) { decisionReason = value; }
    public Long getDecisionUserId() { return decisionUserId; }
    public void setDecisionUserId(Long value) { decisionUserId = value; }
    public String getDecisionUsername() { return decisionUsername; }
    public void setDecisionUsername(String value) { decisionUsername = value; }
    public Date getDecisionTime() { return decisionTime; }
    public void setDecisionTime(Date value) { decisionTime = value; }
    public Date getSealedTime() { return sealedTime; }
    public void setSealedTime(Date value) { sealedTime = value; }
}
