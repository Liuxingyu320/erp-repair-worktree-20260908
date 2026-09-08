package com.erp.approval.domain.dto;

import java.util.ArrayList;
import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import com.erp.approval.domain.ApprovalRuleCondition;
import com.erp.approval.domain.ApprovalVersionNode;

/** Complete replacement of one draft definition. */
public class ApprovalVersionSaveRequest
{
    @NotNull
    private Long expectedLockVersion;
    @Valid
    private List<ApprovalRuleCondition> conditions = new ArrayList<>();
    @Valid
    private List<ApprovalVersionNode> nodes = new ArrayList<>();
    private String remark;

    public Long getExpectedLockVersion() { return expectedLockVersion; }
    public void setExpectedLockVersion(Long expectedLockVersion) { this.expectedLockVersion = expectedLockVersion; }
    public List<ApprovalRuleCondition> getConditions() { return conditions; }
    public void setConditions(List<ApprovalRuleCondition> value) { conditions = value == null ? new ArrayList<>() : value; }
    public List<ApprovalVersionNode> getNodes() { return nodes; }
    public void setNodes(List<ApprovalVersionNode> value) { nodes = value == null ? new ArrayList<>() : value; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
