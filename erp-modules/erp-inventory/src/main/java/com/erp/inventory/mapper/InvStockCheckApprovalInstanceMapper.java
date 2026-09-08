package com.erp.inventory.mapper;

import java.util.List;
import com.erp.inventory.domain.InvStockCheckApprovalInstance;

public interface InvStockCheckApprovalInstanceMapper
{
    InvStockCheckApprovalInstance selectByIdForUpdate(Long instanceId);
    InvStockCheckApprovalInstance selectRunningByCheckIdForUpdate(Long checkId);
    List<InvStockCheckApprovalInstance> selectByCheckId(Long checkId);
    int insertInstance(InvStockCheckApprovalInstance instance);
    int updateInstance(InvStockCheckApprovalInstance instance);
}
