package com.erp.inventory.service;

import java.util.List;
import com.erp.inventory.domain.InvStockCheck;
import com.erp.inventory.domain.InvStockCheckDetail;
import com.erp.inventory.domain.dto.InvStockCheckAssignmentRequest;
import com.erp.inventory.domain.vo.InvStockCheckCounterCandidate;

public interface IInvStockCheckService
{
    InvStockCheck createCheck(InvStockCheck stockCheck, Long selectedShopDeptId);
    List<InvStockCheckCounterCandidate> selectCounterCandidates(Long warehouseId,
            String keyword, Long selectedShopDeptId);
    InvStockCheck assignCounter(Long checkId, InvStockCheckAssignmentRequest assignment,
            Long selectedShopDeptId);
    InvStockCheck saveDraft(InvStockCheck stockCheck, Long selectedShopDeptId);
    InvStockCheck getCheckDetail(Long checkId, Long selectedShopDeptId);
    List<InvStockCheck> selectCheckList(InvStockCheck stockCheck, Long selectedShopDeptId);
    List<InvStockCheck> selectApprovalTodoList(InvStockCheck stockCheck, Long selectedShopDeptId);
    InvStockCheck inputActualQty(Long checkId, InvStockCheck stockCheck, Long selectedShopDeptId);
    void submitCheck(Long checkId, List<InvStockCheckDetail> details, Long selectedShopDeptId);
    void restartCheck(Long checkId, Long selectedShopDeptId);
    void cancelCheck(Long checkId, Long selectedShopDeptId);
    String withdrawApproval(Long checkId, Long selectedShopDeptId);
    void deleteCheck(Long[] checkIds, Long selectedShopDeptId);
}
