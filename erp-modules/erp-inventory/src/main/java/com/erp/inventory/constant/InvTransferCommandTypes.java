package com.erp.inventory.constant;

public final class InvTransferCommandTypes
{
    public static final String DRAFT_SAVE = "TRANSFER_DRAFT_SAVE";
    public static final String SUBMIT = "TRANSFER_SUBMIT";
    public static final String APPROVE = "TRANSFER_APPROVE";
    public static final String DELIVER = "TRANSFER_DELIVER";
    public static final String SHIPMENT_CREATE_V2 =
            "TRANSFER_SHIPMENT_CREATE_V2";
    public static final String SHIPMENT_RECEIPT_CREATE_V2 =
            "TRANSFER_SHIPMENT_RECEIPT_CREATE_V2";
    public static final String RECEIVE = "TRANSFER_RECEIVE";
    public static final String SHIPMENT_RECEIVE = "TRANSFER_SHIPMENT_RECEIVE";
    public static final String DISCREPANCY_RESOLVE = "TRANSFER_DISCREPANCY_RESOLVE";
    public static final String DRAFT_DELETE = "TRANSFER_DRAFT_DELETE";
    public static final String CANCEL = "TRANSFER_CANCEL";
    public static final String WITHDRAW = "TRANSFER_WITHDRAW";

    private InvTransferCommandTypes()
    {
    }
}
