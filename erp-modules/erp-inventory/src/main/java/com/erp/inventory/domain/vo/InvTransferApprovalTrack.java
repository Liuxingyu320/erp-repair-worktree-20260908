package com.erp.inventory.domain.vo;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class InvTransferApprovalTrack implements Serializable
{
    private static final long serialVersionUID = 1L;

    private Long transferId;
    private String orderNo;
    private String documentStatus;
    private String state;
    private String traceCompleteness = "complete";
    private String summaryText;
    private Node currentNode;
    private List<Round> rounds = new ArrayList<>();

    public Long getTransferId() { return transferId; }
    public void setTransferId(Long transferId) { this.transferId = transferId; }
    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public String getDocumentStatus() { return documentStatus; }
    public void setDocumentStatus(String documentStatus) { this.documentStatus = documentStatus; }
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    public String getTraceCompleteness() { return traceCompleteness; }
    public void setTraceCompleteness(String traceCompleteness) { this.traceCompleteness = traceCompleteness; }
    public String getSummaryText() { return summaryText; }
    public void setSummaryText(String summaryText) { this.summaryText = summaryText; }
    public Node getCurrentNode() { return currentNode; }
    public void setCurrentNode(Node currentNode) { this.currentNode = currentNode; }
    public List<Round> getRounds() { return rounds; }
    public void setRounds(List<Round> rounds) {
        this.rounds = rounds == null ? new ArrayList<>() : new ArrayList<>(rounds);
    }

    public static class Round implements Serializable
    {
        private static final long serialVersionUID = 1L;

        private Integer roundNo;
        private String status;
        private Date submittedAt;
        private Date finishedAt;
        private List<Node> nodes = new ArrayList<>();

        public Integer getRoundNo() { return roundNo; }
        public void setRoundNo(Integer roundNo) { this.roundNo = roundNo; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public Date getSubmittedAt() { return submittedAt; }
        public void setSubmittedAt(Date submittedAt) { this.submittedAt = submittedAt; }
        public Date getFinishedAt() { return finishedAt; }
        public void setFinishedAt(Date finishedAt) { this.finishedAt = finishedAt; }
        public List<Node> getNodes() { return nodes; }
        public void setNodes(List<Node> nodes) {
            this.nodes = nodes == null ? new ArrayList<>() : new ArrayList<>(nodes);
        }
    }

    public static class Node implements Serializable
    {
        private static final long serialVersionUID = 1L;

        private Integer nodeOrder;
        private String nodeName;
        private String postName;
        private String state;
        private List<String> candidateDisplayNames = new ArrayList<>();
        private Integer candidateCount;
        private String approvalModeText;
        private String decision;
        private String actualApproverDisplayName;
        private Date handledAt;
        private String comment;

        public Integer getNodeOrder() { return nodeOrder; }
        public void setNodeOrder(Integer nodeOrder) { this.nodeOrder = nodeOrder; }
        public String getNodeName() { return nodeName; }
        public void setNodeName(String nodeName) { this.nodeName = nodeName; }
        public String getPostName() { return postName; }
        public void setPostName(String postName) { this.postName = postName; }
        public String getState() { return state; }
        public void setState(String state) { this.state = state; }
        public List<String> getCandidateDisplayNames() { return candidateDisplayNames; }
        public void setCandidateDisplayNames(List<String> names) {
            this.candidateDisplayNames = names == null ? new ArrayList<>() : new ArrayList<>(names);
        }
        public Integer getCandidateCount() { return candidateCount; }
        public void setCandidateCount(Integer candidateCount) { this.candidateCount = candidateCount; }
        public String getApprovalModeText() { return approvalModeText; }
        public void setApprovalModeText(String approvalModeText) { this.approvalModeText = approvalModeText; }
        public String getDecision() { return decision; }
        public void setDecision(String decision) { this.decision = decision; }
        public String getActualApproverDisplayName() { return actualApproverDisplayName; }
        public void setActualApproverDisplayName(String actualApproverDisplayName) { this.actualApproverDisplayName = actualApproverDisplayName; }
        public Date getHandledAt() { return handledAt; }
        public void setHandledAt(Date handledAt) { this.handledAt = handledAt; }
        public String getComment() { return comment; }
        public void setComment(String comment) { this.comment = comment; }
    }
}
