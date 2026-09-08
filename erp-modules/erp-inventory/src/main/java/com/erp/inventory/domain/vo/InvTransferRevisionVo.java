package com.erp.inventory.domain.vo;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonFormat;

public class InvTransferRevisionVo
{
    private Long revisionId;
    private Integer revisionNo;
    private Long parentRevisionId;
    private Integer approvalRound;
    private Long approvalInstanceId;
    private String status;
    private String snapshotHash;
    private String decisionAction;
    private String decisionReason;
    private Long decisionUserId;
    private String decisionUsername;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date decisionTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date sealedTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updateTime;

    private InvTransferRevisionHeaderVo header;
    private List<InvTransferRevisionDetailVo> details = new ArrayList<>();

    public Long getRevisionId() { return revisionId; }
    public void setRevisionId(Long value) { revisionId = value; }
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
    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date value) { createTime = value; }
    public Date getUpdateTime() { return updateTime; }
    public void setUpdateTime(Date value) { updateTime = value; }
    public InvTransferRevisionHeaderVo getHeader() { return header; }
    public void setHeader(InvTransferRevisionHeaderVo value) { header = value; }
    public List<InvTransferRevisionDetailVo> getDetails() { return details; }
    public void setDetails(List<InvTransferRevisionDetailVo> value) {
        details = value == null ? new ArrayList<>() : new ArrayList<>(value);
    }
}
