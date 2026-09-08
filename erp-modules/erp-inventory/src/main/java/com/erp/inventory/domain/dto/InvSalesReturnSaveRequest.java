package com.erp.inventory.domain.dto;

import java.util.List;
import com.erp.inventory.domain.InvSalesReturn;
import com.erp.inventory.domain.InvSalesReturnDetail;

public class InvSalesReturnSaveRequest extends InvSalesReturn
{
    private static final long serialVersionUID = 1L;

    private List<InvSalesReturnDetail> details;

    @Override
    public List<InvSalesReturnDetail> getDetails() { return details; }
    @Override
    public void setDetails(List<InvSalesReturnDetail> details) { this.details = details; }
}
