package com.erp.inventory.constant;

/**
 * Stable inventory todo type identifiers.
 */
public final class InvTodoTypes
{
    public static final String INV_TRANSFER_APPROVAL = "INV_TRANSFER_APPROVAL";
    public static final String INV_STOCK_CHECK_APPROVAL = "INV_STOCK_CHECK_APPROVAL";
    public static final String INV_STOCK_CHECK_EXECUTE = "INV_STOCK_CHECK_EXECUTE";

    public static final String INV_PURCHASE_QC = "INV_PURCHASE_QC";
    public static final String INV_PURCHASE_RECEIVE = "INV_PURCHASE_RECEIVE";
    public static final String INV_SALES_NOTICE_CREATE = "INV_SALES_NOTICE_CREATE";
    public static final String INV_DELIVERY_EXECUTE = "INV_DELIVERY_EXECUTE";
    public static final String INV_TRANSFER_DELIVER = "INV_TRANSFER_DELIVER";
    public static final String INV_TRANSFER_SOURCE_CONFIRM =
            "INV_TRANSFER_SOURCE_CONFIRM";
    public static final String INV_TRANSFER_RECEIVE = "INV_TRANSFER_RECEIVE";
    public static final String INV_TRANSFER_DISCREPANCY = "INV_TRANSFER_DISCREPANCY";
    public static final String INV_PURCHASE_RETURN_CONFIRM = "INV_PURCHASE_RETURN_CONFIRM";
    public static final String INV_SALES_RETURN_CONFIRM = "INV_SALES_RETURN_CONFIRM";

    public static final String INV_TRANSFER_RETURNED = "INV_TRANSFER_RETURNED";
    public static final String INV_TRANSFER_SOURCE_RESELECT =
            "INV_TRANSFER_SOURCE_RESELECT";
    public static final String INV_STOCK_CHECK_RETURNED = "INV_STOCK_CHECK_RETURNED";
    public static final String INV_STOCK_CHECK_RESTART = "INV_STOCK_CHECK_RESTART";

    public static final String INV_OUT_OF_STOCK = "INV_OUT_OF_STOCK";
    public static final String INV_LOW_STOCK = "INV_LOW_STOCK";

    private InvTodoTypes()
    {
    }
}
