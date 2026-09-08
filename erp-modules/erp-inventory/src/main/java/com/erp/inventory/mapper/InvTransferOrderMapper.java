package com.erp.inventory.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.erp.inventory.domain.InvTransferOrder;
import com.erp.inventory.domain.vo.InvTransferOpsSummaryVo;

public interface InvTransferOrderMapper
{
    InvTransferOrder selectInvTransferOrderById(Long transferId);
    InvTransferOrder selectInvTransferOrderByIdForUpdate(Long transferId);
    List<InvTransferOrder> selectInvTransferOrderList(InvTransferOrder order);
    InvTransferOpsSummaryVo selectOpsSummary(InvTransferOrder order);
    int insertInvTransferOrder(InvTransferOrder order);
    int updateDraftIfVersionMatches(InvTransferOrder order);
    int finalizeNativeApprovalStart(@Param("transferId") Long transferId,
            @Param("businessRound") Integer businessRound,
            @Param("expectedVersion") Long expectedVersion,
            @Param("instanceId") Long instanceId,
            @Param("updateBy") String updateBy);
    int updateInvTransferOrder(InvTransferOrder order);
    int deleteInvTransferOrderById(Long transferId);
    InvTransferOrder selectByPurchaseId(@Param("purchaseId") Long purchaseId);
    InvTransferOrder selectBySourceBusinessTypeIdWarehouse(@Param("sourceBusinessType") String sourceBusinessType,
            @Param("sourceBusinessId") Long sourceBusinessId,
            @Param("fromWarehouseId") Long fromWarehouseId);
    InvTransferOrder selectBySourceBusinessTypeIdWarehouseForUpdate(
            @Param("sourceBusinessType") String sourceBusinessType,
            @Param("sourceBusinessId") Long sourceBusinessId,
            @Param("fromWarehouseId") Long fromWarehouseId);
}
