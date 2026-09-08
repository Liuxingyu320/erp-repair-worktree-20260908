package com.erp.inventory.domain.vo;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonIgnore;

public class InvTransferApprovalSummary implements Serializable
{
    private static final long serialVersionUID = 1L;

    private Long transferId;
    private String state;
    private Integer roundNo;
    private Integer currentNodeOrder;
    private Integer totalNodeCount;
    private String currentNodeName;
    private List<String> currentCandidateDisplayNames = new ArrayList<>();
    private Integer currentCandidateCount;
    private Integer completedNodeCount;
    private String summaryText;

    @JsonIgnore
    private String candidateUserIds;

    @JsonIgnore
    private String candidateUserNames;

    public Long getTransferId() { return transferId; }
    public void setTransferId(Long transferId) { this.transferId = transferId; }
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    public Integer getRoundNo() { return roundNo; }
    public void setRoundNo(Integer roundNo) { this.roundNo = roundNo; }
    public Integer getCurrentNodeOrder() { return currentNodeOrder; }
    public void setCurrentNodeOrder(Integer currentNodeOrder) { this.currentNodeOrder = currentNodeOrder; }
    public Integer getTotalNodeCount() { return totalNodeCount; }
    public void setTotalNodeCount(Integer totalNodeCount) { this.totalNodeCount = totalNodeCount; }
    public String getCurrentNodeName() { return currentNodeName; }
    public void setCurrentNodeName(String currentNodeName) { this.currentNodeName = currentNodeName; }
    public List<String> getCurrentCandidateDisplayNames() { return currentCandidateDisplayNames; }
    public void setCurrentCandidateDisplayNames(List<String> names) {
        this.currentCandidateDisplayNames = names == null ? new ArrayList<>() : new ArrayList<>(names);
    }
    public Integer getCurrentCandidateCount() { return currentCandidateCount; }
    public void setCurrentCandidateCount(Integer currentCandidateCount) { this.currentCandidateCount = currentCandidateCount; }
    public Integer getCompletedNodeCount() { return completedNodeCount; }
    public void setCompletedNodeCount(Integer completedNodeCount) { this.completedNodeCount = completedNodeCount; }
    public String getSummaryText() { return summaryText; }
    public void setSummaryText(String summaryText) { this.summaryText = summaryText; }
    public String getCandidateUserIds() { return candidateUserIds; }
    public void setCandidateUserIds(String candidateUserIds) { this.candidateUserIds = candidateUserIds; }
    public String getCandidateUserNames() { return candidateUserNames; }
    public void setCandidateUserNames(String candidateUserNames) { this.candidateUserNames = candidateUserNames; }
}
