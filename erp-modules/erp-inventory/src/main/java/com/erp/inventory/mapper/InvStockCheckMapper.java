package com.erp.inventory.mapper;

import java.util.List;
import com.erp.inventory.domain.InvStockCheck;
import com.erp.inventory.domain.vo.InvStockCheckCounterCandidate;
import org.apache.ibatis.annotations.Param;

public interface InvStockCheckMapper
{
    InvStockCheck selectInvStockCheckById(Long checkId);
    InvStockCheck selectInvStockCheckByIdForUpdate(Long checkId);
    List<InvStockCheck> selectInvStockCheckList(InvStockCheck stockCheck);
    List<InvStockCheck> selectInvStockCheckApprovalTodoList(InvStockCheck stockCheck);
    List<InvStockCheckCounterCandidate> selectCounterCandidates(
            @Param("inventoryDeptId") Long inventoryDeptId,
            @Param("keyword") String keyword);
    InvStockCheckCounterCandidate selectCounterCandidate(
            @Param("inventoryDeptId") Long inventoryDeptId,
            @Param("userId") Long userId);
    int insertInvStockCheck(InvStockCheck stockCheck);
    int finalizeNativeApprovalStart(@Param("checkId") Long checkId,
            @Param("businessRound") Integer businessRound,
            @Param("expectedRowVersion") Long expectedRowVersion,
            @Param("instanceId") Long instanceId,
            @Param("updateBy") String updateBy);
    int updateInvStockCheck(InvStockCheck stockCheck);
    int deleteInvStockCheckByIds(Long[] checkIds);
}
