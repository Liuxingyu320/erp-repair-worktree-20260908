package com.erp.inventory.mapper;

import java.util.List;
import com.erp.inventory.domain.InvPurchaseDetail;

public interface InvPurchaseDetailMapper
{
    List<InvPurchaseDetail> selectInvPurchaseDetailByOrderId(Long orderId);
    List<InvPurchaseDetail> selectInvPurchaseDetailByOrderIdForUpdate(Long orderId);
    int insertInvPurchaseDetail(InvPurchaseDetail detail);
    int updateInvPurchaseDetail(InvPurchaseDetail detail);
    int deleteInvPurchaseDetailByOrderId(Long orderId);
    int batchInsertInvPurchaseDetail(List<InvPurchaseDetail> details);
}
