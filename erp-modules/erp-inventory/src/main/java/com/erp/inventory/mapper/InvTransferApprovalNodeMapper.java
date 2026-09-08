package com.erp.inventory.mapper;

import java.util.List;
import com.erp.inventory.domain.InvTransferApprovalNode;

public interface InvTransferApprovalNodeMapper
{
    List<InvTransferApprovalNode> selectNodesByRuleId(Long ruleId);
    int batchInsertNodes(List<InvTransferApprovalNode> nodes);
    int deleteNodesByRuleId(Long ruleId);
}
