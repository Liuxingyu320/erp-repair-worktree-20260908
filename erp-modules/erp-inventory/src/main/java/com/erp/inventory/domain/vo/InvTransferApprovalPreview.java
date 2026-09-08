package com.erp.inventory.domain.vo;

import java.util.ArrayList;
import java.util.List;

/** Safe candidate preview for one transfer rule and target store. */
public class InvTransferApprovalPreview
{
    private Long ruleId;
    private String ruleName;
    private Long targetDeptId;
    private String targetDeptName;
    private Integer managerPostSort;
    private Integer executiveBoundarySort;
    private boolean blocked;
    private String blockedReason;
    private List<String> warnings = new ArrayList<>();
    private List<Node> nodes = new ArrayList<>();

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

    public Long getTargetDeptId()
    {
        return targetDeptId;
    }

    public void setTargetDeptId(Long targetDeptId)
    {
        this.targetDeptId = targetDeptId;
    }

    public String getTargetDeptName()
    {
        return targetDeptName;
    }

    public void setTargetDeptName(String targetDeptName)
    {
        this.targetDeptName = targetDeptName;
    }

    public Integer getManagerPostSort()
    {
        return managerPostSort;
    }

    public void setManagerPostSort(Integer managerPostSort)
    {
        this.managerPostSort = managerPostSort;
    }

    public Integer getExecutiveBoundarySort()
    {
        return executiveBoundarySort;
    }

    public void setExecutiveBoundarySort(Integer executiveBoundarySort)
    {
        this.executiveBoundarySort = executiveBoundarySort;
    }

    public boolean isBlocked()
    {
        return blocked;
    }

    public void setBlocked(boolean blocked)
    {
        this.blocked = blocked;
    }

    public String getBlockedReason()
    {
        return blockedReason;
    }

    public void setBlockedReason(String blockedReason)
    {
        this.blockedReason = blockedReason;
    }

    public List<String> getWarnings()
    {
        return warnings;
    }

    public void setWarnings(List<String> warnings)
    {
        this.warnings = warnings == null ? new ArrayList<>() : warnings;
    }

    public List<Node> getNodes()
    {
        return nodes;
    }

    public void setNodes(List<Node> nodes)
    {
        this.nodes = nodes == null ? new ArrayList<>() : nodes;
    }

    public static class Node
    {
        private Integer nodeOrder;
        private String nodeName;
        private String nodeRole;
        private Integer resolvedPostSort;
        private boolean required;
        private String state;
        private Integer candidateCount;
        private List<String> candidateDisplayNames = new ArrayList<>();

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

        public Integer getResolvedPostSort()
        {
            return resolvedPostSort;
        }

        public void setResolvedPostSort(Integer resolvedPostSort)
        {
            this.resolvedPostSort = resolvedPostSort;
        }

        public boolean isRequired()
        {
            return required;
        }

        public void setRequired(boolean required)
        {
            this.required = required;
        }

        public String getState()
        {
            return state;
        }

        public void setState(String state)
        {
            this.state = state;
        }

        public Integer getCandidateCount()
        {
            return candidateCount;
        }

        public void setCandidateCount(Integer candidateCount)
        {
            this.candidateCount = candidateCount;
        }

        public List<String> getCandidateDisplayNames()
        {
            return candidateDisplayNames;
        }

        public void setCandidateDisplayNames(List<String> candidateDisplayNames)
        {
            this.candidateDisplayNames = candidateDisplayNames == null
                    ? new ArrayList<>() : candidateDisplayNames;
        }
    }
}
