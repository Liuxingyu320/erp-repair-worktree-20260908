package com.erp.inventory.mapper;

import java.util.List;
import com.erp.inventory.domain.InvDeliveryNotice;

public interface InvDeliveryNoticeMapper
{
    InvDeliveryNotice selectInvDeliveryNoticeById(Long noticeId);
    InvDeliveryNotice selectInvDeliveryNoticeByIdForUpdate(Long noticeId);
    List<InvDeliveryNotice> selectInvDeliveryNoticeList(InvDeliveryNotice notice);
    int insertInvDeliveryNotice(InvDeliveryNotice notice);
    int updateInvDeliveryNotice(InvDeliveryNotice notice);
    List<InvDeliveryNotice> selectInvDeliveryNoticeBySalesOrderId(Long salesOrderId);
    List<InvDeliveryNotice> selectInvDeliveryNoticeBySalesOrderIdForUpdate(Long salesOrderId);
}
