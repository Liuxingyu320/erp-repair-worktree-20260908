package com.erp.inventory.domain.vo;

import java.util.ArrayList;
import java.util.List;

public class InvTransferApprovalRuleValidationResult
{
    private boolean matched;
    private boolean valid;
    private Long ruleId;
    private String ruleName;
    private List<String> blockingIssues = new ArrayList<>();
    private List<String> warnings = new ArrayList<>();
    private List<String> overlaps = new ArrayList<>();
    private List<String> matchReasons = new ArrayList<>();
    private List<String> nonMatchReasons = new ArrayList<>();
    private List<NodeResult> nodes = new ArrayList<>();

    public boolean isMatched()
    {
        return matched;
    }

    public void setMatched(boolean matched)
    {
        this.matched = matched;
    }

    public boolean isValid()
    {
        return valid;
    }

    public void setValid(boolean valid)
    {
        this.valid = valid;
    }

    public Long getRuleId()
    {
        return ruleId;
    }

    public void setRuleId(Long ruleId)
    {
        this.ruleId = ruleId;
    }

    public String getRuleName()
    {
        return ruleName;
    }

    public void setRuleName(String ruleName)
    {
        this.ruleName = ruleName;
    }

    public List<String> getBlockingIssues()
    {
        return blockingIssues;
    }

    public void setBlockingIssues(List<String> blockingIssues)
    {
        this.blockingIssues = blockingIssues == null ? new ArrayList<>() : blockingIssues;
    }

    public List<String> getWarnings()
    {
        return warnings;
    }

    public void setWarnings(List<String> warnings)
    {
        this.warnings = warnings == null ? new ArrayList<>() : warnings;
    }

    public List<String> getOverlaps()
    {
        return overlaps;
    }

    public void setOverlaps(List<String> overlaps)
    {
        this.overlaps = overlaps == null ? new ArrayList<>() : overlaps;
    }

    public List<String> getMatchReasons()
    {
        return matchReasons;
    }

    public void setMatchReasons(List<String> matchReasons)
    {
        this.matchReasons = matchReasons == null ? new ArrayList<>() : matchReasons;
    }

    public List<String> getNonMatchReasons()
    {
        return nonMatchReasons;
    }

    public void setNonMatchReasons(List<String> nonMatchReasons)
    {
        this.nonMatchReasons = nonMatchReasons == null ? new ArrayList<>() : nonMatchReasons;
    }

    public List<NodeResult> getNodes()
    {
        return nodes;
    }

    public void setNodes(List<NodeResult> nodes)
    {
        this.nodes = nodes == null ? new ArrayList<>() : nodes;
    }

    public static class NodeResult
    {
        private Integer nodeOrder;
        private String nodeName;
        private String nodeRole;
        private String postCode;
        private String postName;
        private Long approvalScopeDeptId;
        private List<Long> candidateDeptIds = new ArrayList<>();
        private List<Candidate> candidates = new ArrayList<>();
        private String status;
        private String message;
        private String candidateSource;

        public Integer getNodeOrder()
        {
            return nodeOrder;
        }

        public void setNodeOrder(Integer nodeOrder)
        {
            this.nodeOrder = nodeOrder;
        }

        public String getNodeName()
        {
            return nodeName;
        }

        public void setNodeName(String nodeName)
        {
            this.nodeName = nodeName;
        }

        public String getNodeRole()
        {
            return nodeRole;
        }

        public void setNodeRole(String nodeRole)
        {
            this.nodeRole = nodeRole;
        }

        public String getPostCode()
        {
            return postCode;
        }

        public void setPostCode(String postCode)
        {
            this.postCode = postCode;
        }

        public String getPostName()
        {
            return postName;
        }

        public void setPostName(String postName)
        {
            this.postName = postName;
        }

        public Long getApprovalScopeDeptId()
        {
            return approvalScopeDeptId;
        }

        public void setApprovalScopeDeptId(Long approvalScopeDeptId)
        {
            this.approvalScopeDeptId = approvalScopeDeptId;
        }

        public List<Long> getCandidateDeptIds()
        {
            return candidateDeptIds;
        }

        public void setCandidateDeptIds(List<Long> candidateDeptIds)
        {
            this.candidateDeptIds = candidateDeptIds == null ? new ArrayList<>() : candidateDeptIds;
        }

        public List<Candidate> getCandidates()
        {
            return candidates;
        }

        public void setCandidates(List<Candidate> candidates)
        {
            this.candidates = candidates == null ? new ArrayList<>() : candidates;
        }

        public String getStatus()
        {
            return status;
        }

        public void setStatus(String status)
        {
            this.status = status;
        }

        public String getMessage()
        {
            return message;
        }

        public void setMessage(String message)
        {
            this.message = message;
        }

        public String getCandidateSource()
        {
            return candidateSource;
        }

        public void setCandidateSource(String candidateSource)
        {
            this.candidateSource = candidateSource;
        }
    }

    public static class Candidate
    {
        private Long userId;
        private String userName;

        public Candidate()
        {
        }

        public Candidate(Long userId, String userName)
        {
            this.userId = userId;
            this.userName = userName;
        }

        public Long getUserId()
        {
            return userId;
        }

        public void setUserId(Long userId)
        {
            this.userId = userId;
        }

        public String getUserName()
        {
            return userName;
        }

        public void setUserName(String userName)
        {
            this.userName = userName;
        }
    }
}
