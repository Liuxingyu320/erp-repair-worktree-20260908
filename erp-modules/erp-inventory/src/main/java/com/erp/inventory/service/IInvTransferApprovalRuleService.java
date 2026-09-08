package com.erp.inventory.service;

import java.util.List;
import com.erp.inventory.domain.InvTransferApprovalNode;
import com.erp.inventory.domain.InvTransferApprovalRule;
import com.erp.inventory.domain.InvTransferOrder;
import com.erp.inventory.domain.dto.InvTransferApprovalRuleValidationRequest;
import com.erp.inventory.domain.vo.InvTransferApprovalRuleValidationResult;

public interface IInvTransferApprovalRuleService
{
    List<InvTransferApprovalRule> selectRuleList(InvTransferApprovalRule rule, Long selectedShopDeptId);
    InvTransferApprovalRule selectRuleById(Long ruleId, Long selectedShopDeptId);
    InvTransferApprovalRule saveRule(InvTransferApprovalRule rule, Long selectedShopDeptId);
    int deleteRuleById(Long ruleId, Integer expectedVersion, Long selectedShopDeptId);
    int saveNodes(Long ruleId, List<InvTransferApprovalNode> nodes, Long selectedShopDeptId);
    int deleteNodesByRuleId(Long ruleId, Long selectedShopDeptId);
    InvTransferApprovalRule previewRule(InvTransferOrder transfer, Long selectedShopDeptId);
    InvTransferApprovalRule matchRule(InvTransferOrder transfer);
    InvTransferApprovalRuleValidationResult validateRule(
            InvTransferApprovalRuleValidationRequest request, Long selectedShopDeptId);
}
