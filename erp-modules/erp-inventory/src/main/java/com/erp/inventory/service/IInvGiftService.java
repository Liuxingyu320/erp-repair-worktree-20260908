package com.erp.inventory.service;

import java.util.List;
import com.erp.inventory.domain.InvGiftBox;

public interface IInvGiftService
{
    List<InvGiftBox> selectGiftList(InvGiftBox gift);
    InvGiftBox selectGiftById(Long giftId);
    InvGiftBox saveGift(InvGiftBox gift, Long selectedDeptId);
    void deleteGiftByIds(Long[] giftIds, Long selectedDeptId);
    String importGift(List<InvGiftBox> gifts, boolean updateSupport, Long selectedDeptId);
}
