package com.erp.inventory.domain.dto;

import java.util.List;
import com.erp.inventory.domain.InvSalesDetail;
import com.erp.inventory.domain.InvSalesOrder;

public class InvSalesSaveRequest extends InvSalesOrder
{
    private static final long serialVersionUID = 1L;

    private List<InvSalesDetail> details;

    @Override
    public List<InvSalesDetail> getDetails() { return details; }
    @Override
    public void setDetails(List<InvSalesDetail> details) { this.details = details; }
}
