package com.erp.inventory.service;

import java.util.List;
import com.erp.inventory.domain.InvStockCheck;
import com.erp.inventory.domain.InvStockCheckApprovalInstance;
import com.erp.inventory.domain.InvStockCheckDetail;
import com.erp.inventory.domain.dto.InvStockCheckApprovalRequest;

public interface IInvStockCheckApprovalService
{
    InvStockCheckApprovalInstance createPendingApproval(InvStockCheck check,
            List<InvStockCheckDetail> details);

    void approve(Long checkId, InvStockCheckApprovalRequest request, Long selectedShopDeptId);

    void reject(Long checkId, InvStockCheckApprovalRequest request, Long selectedShopDeptId);

    void cancelRunning(Long checkId);

    List<InvStockCheckApprovalInstance> selectTrack(Long checkId, Long selectedShopDeptId);
}
