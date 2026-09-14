package com.erp.inventory.domain.vo;

public class InvCustomerServiceRecordPage
{
    private java.util.List<com.erp.inventory.domain.InvCustomerServiceRecord> records;
    private Long total;
    private boolean hasMore;
    private Long snapshotMaxRecordId;
    private String nextServiceDate;
    private Long nextRecordId;
    public java.util.List<com.erp.inventory.domain.InvCustomerServiceRecord> getRecords() { return records; }
    public void setRecords(java.util.List<com.erp.inventory.domain.InvCustomerServiceRecord> value) { this.records = value; }
    public Long getTotal() { return total; }
    public void setTotal(Long value) { this.total = value; }
    public boolean isHasMore() { return hasMore; }
    public void setHasMore(boolean value) { this.hasMore = value; }
    public Long getSnapshotMaxRecordId() { return snapshotMaxRecordId; }
    public void setSnapshotMaxRecordId(Long value) { this.snapshotMaxRecordId = value; }
    public String getNextServiceDate() { return nextServiceDate; }
    public void setNextServiceDate(String value) { this.nextServiceDate = value; }
    public Long getNextRecordId() { return nextRecordId; }
    public void setNextRecordId(Long value) { this.nextRecordId = value; }
}
