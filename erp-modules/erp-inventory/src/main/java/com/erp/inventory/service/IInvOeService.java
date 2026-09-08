package com.erp.inventory.service;

import java.util.List;
import com.erp.inventory.domain.InvOeItem;
import com.erp.inventory.domain.vo.InvOePurchaseReferencePolicyVo;

public interface IInvOeService
{
    List<InvOeItem> selectOeList(InvOeItem item);
    InvOeItem selectOeById(Long oeItemId);
    InvOePurchaseReferencePolicyVo getPurchaseReferencePolicy();
    InvOeItem saveOe(InvOeItem item, Long selectedDeptId);
    void deleteOeByIds(Long[] oeItemIds, Long selectedDeptId);
    String importOe(List<InvOeItem> items, boolean updateSupport, Long selectedDeptId);
}
