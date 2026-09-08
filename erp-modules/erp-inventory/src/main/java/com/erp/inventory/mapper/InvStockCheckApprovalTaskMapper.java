package com.erp.inventory.mapper;

import java.util.List;
import com.erp.inventory.domain.InvStockCheckApprovalTask;

public interface InvStockCheckApprovalTaskMapper
{
    InvStockCheckApprovalTask selectByInstanceIdForUpdate(Long instanceId);
    List<InvStockCheckApprovalTask> selectByInstanceId(Long instanceId);
    int insertTask(InvStockCheckApprovalTask task);
    int updateTask(InvStockCheckApprovalTask task);
}
