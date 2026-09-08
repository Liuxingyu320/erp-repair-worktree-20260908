package com.erp.inventory.mapper;

import java.util.List;
import com.erp.inventory.domain.InvTransferDetail;

public interface InvTransferDetailMapper
{
    List<InvTransferDetail> selectByTransferId(Long transferId);
    List<InvTransferDetail> selectByTransferIdForUpdate(Long transferId);
    int insertInvTransferDetail(InvTransferDetail detail);
    int batchInsertInvTransferDetail(List<InvTransferDetail> details);
    int updateDeliveredQuantity(InvTransferDetail detail);
    int decreaseDeliveredQuantity(InvTransferDetail detail);
    int updateReceivedQuantity(InvTransferDetail detail);
    int deleteByTransferId(Long transferId);
}
