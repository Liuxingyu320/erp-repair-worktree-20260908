package com.erp.inventory.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.erp.inventory.domain.InvTransferApprovalRule;
import com.erp.inventory.domain.InvTransferOrder;

public interface InvTransferApprovalRuleMapper
{
    List<InvTransferApprovalRule> selectRuleList(InvTransferApprovalRule rule);
    InvTransferApprovalRule selectRuleById(Long ruleId);
    List<InvTransferApprovalRule> selectEnabledRulesForMatch(InvTransferOrder transfer);
    int insertRule(InvTransferApprovalRule rule);
    int updateRule(InvTransferApprovalRule rule);
    int deleteRuleByIdAndVersion(@Param("ruleId") Long ruleId,
            @Param("expectedVersion") Integer expectedVersion);
}
