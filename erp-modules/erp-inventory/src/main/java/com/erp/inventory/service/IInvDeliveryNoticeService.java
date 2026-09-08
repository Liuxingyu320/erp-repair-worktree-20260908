package com.erp.inventory.service;

import java.util.List;
import com.erp.inventory.domain.InvDeliveryNotice;
import com.erp.inventory.domain.dto.InvDeliverRequest;

public interface IInvDeliveryNoticeService
{
    InvDeliveryNotice createNotice(Long salesOrderId, Long selectedShopDeptId);
    InvDeliveryNotice getNoticeDetail(Long noticeId, Long selectedShopDeptId);
    List<InvDeliveryNotice> selectNoticeList(InvDeliveryNotice notice, Long selectedShopDeptId);
    String deliverNotice(Long noticeId, InvDeliverRequest request, Long selectedShopDeptId);
    void cancelNotice(Long noticeId, Long selectedShopDeptId);
}
