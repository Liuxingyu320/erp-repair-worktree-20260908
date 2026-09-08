package com.erp.inventory.mapper;

import java.util.List;
import com.erp.inventory.domain.InvSalesDetail;

public interface InvSalesDetailMapper
{
    List<InvSalesDetail> selectInvSalesDetailByOrderId(Long orderId);
    List<InvSalesDetail> selectInvSalesDetailByOrderIdForUpdate(Long orderId);
    int insertInvSalesDetail(InvSalesDetail detail);
    int updateInvSalesDetail(InvSalesDetail detail);
    int deleteInvSalesDetailByOrderId(Long orderId);
    int batchInsertInvSalesDetail(List<InvSalesDetail> details);
}
