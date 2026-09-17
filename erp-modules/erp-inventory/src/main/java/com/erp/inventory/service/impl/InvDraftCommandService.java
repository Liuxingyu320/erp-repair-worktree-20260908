package com.erp.inventory.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.erp.inventory.domain.*;
import com.erp.inventory.service.*;

@Service
public class InvDraftCommandService extends InvBaseService
{
    private final InvQualityCommandExecutor commands;
    private final IInvPurchaseService purchases;
    private final IInvSalesReturnService salesReturns;
    private final IInvPurchaseReturnService purchaseReturns;

    public InvDraftCommandService(InvQualityCommandExecutor commands, IInvPurchaseService purchases,
            IInvSalesReturnService salesReturns, IInvPurchaseReturnService purchaseReturns)
    {
        this.commands = commands; this.purchases = purchases;
        this.salesReturns = salesReturns; this.purchaseReturns = purchaseReturns;
    }

    @Transactional(rollbackFor = Exception.class)
    public InvPurchaseOrder purchase(String requestId, InvPurchaseOrder body, Long dept, boolean submit)
    {
        requireWarehouseContext(dept, "请选择正确的业务组织");

        return commands.execute(requestId, submit ? "PURCHASE_DRAFT_SUBMIT" : "PURCHASE_DRAFT_SAVE", dept,
                "purchase:" + (body.getOrderId() == null ? "new" : body.getOrderId()), body, InvPurchaseOrder.class,
                () -> submit ? purchases.submitPurchase(body, body.getDetails(), dept) : purchases.saveDraft(body, body.getDetails(), dept));
    }

    @Transactional(rollbackFor = Exception.class)
    public InvSalesReturn salesReturn(String requestId, InvSalesReturn body, Long dept, boolean submit)
    {
        requireStoreContext(dept, "请选择正确的业务组织");

        return commands.execute(requestId, submit ? "SALES_RETURN_DRAFT_SUBMIT" : "SALES_RETURN_DRAFT_SAVE", dept,
                "salesReturn:" + (body.getReturnId() == null ? "new" : body.getReturnId()), body, InvSalesReturn.class,
                () -> submit ? salesReturns.submitReturn(body, body.getDetails(), dept) : salesReturns.saveDraft(body, body.getDetails(), dept));
    }

    @Transactional(rollbackFor = Exception.class)
    public InvPurchaseReturn purchaseReturn(String requestId, InvPurchaseReturn body, Long dept, boolean submit)
    {
        requireWarehouseContext(dept, "请选择正确的业务组织");

        return commands.execute(requestId, submit ? "PURCHASE_RETURN_DRAFT_SUBMIT" : "PURCHASE_RETURN_DRAFT_SAVE", dept,
                "purchaseReturn:" + (body.getReturnId() == null ? "new" : body.getReturnId()), body, InvPurchaseReturn.class,
                () -> submit ? purchaseReturns.submitReturn(body, body.getDetails(), dept) : purchaseReturns.saveDraft(body, body.getDetails(), dept));
    }
}
