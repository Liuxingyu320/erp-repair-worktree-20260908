package com.erp.inventory.domain.dto;

import com.erp.inventory.domain.InvTransferApprovalRule;
import com.erp.inventory.domain.InvTransferOrder;

public class InvTransferApprovalRuleValidationRequest
{
    private InvTransferApprovalRule rule;
    private InvTransferOrder transferSample;

    public InvTransferApprovalRule getRule()
    {
        return rule;
    }

    public void setRule(InvTransferApprovalRule rule)
    {
        this.rule = rule;
    }

    public InvTransferOrder getTransferSample()
    {
        return transferSample;
    }

    public void setTransferSample(InvTransferOrder transferSample)
    {
        this.transferSample = transferSample;
    }
}
