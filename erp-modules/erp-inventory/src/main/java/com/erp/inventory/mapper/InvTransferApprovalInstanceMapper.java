package com.erp.inventory.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.erp.inventory.domain.InvTransferApprovalInstance;

public interface InvTransferApprovalInstanceMapper
{
    int insertInstance(InvTransferApprovalInstance instance);
    InvTransferApprovalInstance selectInstanceByIdForUpdate(@Param("instanceId") Long instanceId);
    InvTransferApprovalInstance selectRunningInstanceByTransferIdForUpdate(@Param("transferId") Long transferId);
    List<InvTransferApprovalInstance> selectRunningInstancesByTransferIdForUpdate(
            @Param("transferId") Long transferId);
    List<InvTransferApprovalInstance> selectInstancesByTransferId(@Param("transferId") Long transferId);
    int closeRunningInstancesByTransferId(@Param("transferId") Long transferId,
            @Param("updateBy") String updateBy, @Param("reason") String reason);
    int updateInstance(InvTransferApprovalInstance instance);
}
