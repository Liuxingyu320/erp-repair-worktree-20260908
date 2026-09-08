package com.erp.inventory.domain.dto;

import java.util.List;
import com.erp.inventory.domain.InvPurchaseDetail;
import com.erp.inventory.domain.InvPurchaseOrder;

public class InvPurchaseSaveRequest extends InvPurchaseOrder
{
    private static final long serialVersionUID = 1L;

    private List<InvPurchaseDetail> details;

    @Override
    public List<InvPurchaseDetail> getDetails() { return details; }
    @Override
    public void setDetails(List<InvPurchaseDetail> details) { this.details = details; }
}
