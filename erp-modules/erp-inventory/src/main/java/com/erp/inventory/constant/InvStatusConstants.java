package com.erp.inventory.constant;

public final class InvStatusConstants
{
    private InvStatusConstants() {}

    public static final String DRAFT = "draft";
    public static final String SUBMITTED = "submitted";
    public static final String APPROVED = "approved";
    public static final String RESERVED = "reserved";
    public static final String REJECTED = "rejected";
    public static final String DISCREPANCY = "discrepancy";
    public static final String CLOSED = "closed";
    public static final String PARTIAL_DELIVERED = "partial_delivered";
    public static final String PARTIAL_RECEIVED = "partial_received";
    public static final String RECEIVED = "received";
    public static final String NOTICED = "noticed";
    public static final String DELIVERED = "delivered";
    public static final String PENDING_RECEIVE = "pending_receive";
    public static final String ABNORMAL = "abnormal";
    public static final String CHECKING = "checking";
    public static final String PENDING_APPROVAL = "pending_approval";
    public static final String INVALIDATED = "invalidated";
    public static final String COMPLETED = "completed";
    public static final String CANCELLED = "cancelled";
    public static final String RETURNED = "returned";
    public static final String EXECUTED = "executed";
    public static final String DELIVERING = "delivering";
    public static final String PENDING = "pending";

    public static final String QC_PENDING = "pending";
    public static final String QC_PASSED = "passed";
    public static final String QC_REJECTED = "rejected";
    public static final String QC_CONCESSION = "concession";

    public static final String MOVEMENT_PURCHASE_IN = "purchase_in";
    public static final String MOVEMENT_PURCHASE_RETURN_OUT = "purchase_return_out";
    public static final String MOVEMENT_SALES_OUT = "sales_out";
    public static final String MOVEMENT_SALES_RETURN_IN = "sales_return_in";
    public static final String MOVEMENT_TRANSFER_IN = "transfer_in";
    public static final String MOVEMENT_TRANSFER_OUT = "transfer_out";
    public static final String MOVEMENT_STOCK_CHECK_PROFIT = "stock_check_profit";
    public static final String MOVEMENT_STOCK_CHECK_LOSS = "stock_check_loss";
    public static final String MOVEMENT_ADJUSTMENT = "adjustment";
}
