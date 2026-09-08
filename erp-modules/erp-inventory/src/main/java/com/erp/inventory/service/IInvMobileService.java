package com.erp.inventory.service;

import java.util.List;
import com.erp.inventory.domain.vo.MobileOption;
import com.erp.inventory.domain.vo.MobileTransferApprovalTodo;
import com.erp.inventory.domain.vo.MobileWorkbenchSummary;

public interface IInvMobileService
{
    List<MobileOption> selectOptions(String type, String keyword, Integer limit, Long selectedShopDeptId);

    MobileWorkbenchSummary selectWorkbenchSummary(Long selectedShopDeptId);

    List<MobileTransferApprovalTodo> selectPendingTransferApprovals(Long selectedShopDeptId);
}
