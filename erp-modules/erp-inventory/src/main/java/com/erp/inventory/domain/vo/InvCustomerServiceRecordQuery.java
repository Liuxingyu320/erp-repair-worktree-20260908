package com.erp.inventory.domain.vo;

public class InvCustomerServiceRecordQuery
{
    private Long customerId;
    private Long shopDeptId;
    private String keyword;
    private String dateFrom;
    private String dateTo;
    private Integer pageSize;
    private Long snapshotMaxRecordId;
    private String beforeServiceDate;
    private Long beforeRecordId;
    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long value) { this.customerId = value; }
    public Long getShopDeptId() { return shopDeptId; }
    public void setShopDeptId(Long value) { this.shopDeptId = value; }
    public String getKeyword() { return keyword; }
    public void setKeyword(String value) { this.keyword = value; }
    public String getDateFrom() { return dateFrom; }
    public void setDateFrom(String value) { this.dateFrom = value; }
    public String getDateTo() { return dateTo; }
    public void setDateTo(String value) { this.dateTo = value; }
    public Integer getPageSize() { return pageSize; }
    public void setPageSize(Integer value) { this.pageSize = value; }
    public Long getSnapshotMaxRecordId() { return snapshotMaxRecordId; }
    public void setSnapshotMaxRecordId(Long value) { this.snapshotMaxRecordId = value; }
    public String getBeforeServiceDate() { return beforeServiceDate; }
    public void setBeforeServiceDate(String value) { this.beforeServiceDate = value; }
    public Long getBeforeRecordId() { return beforeRecordId; }
    public void setBeforeRecordId(Long value) { this.beforeRecordId = value; }
    public String getKeywordLike() { return keyword == null ? null : keyword.replace("!", "!!").replace("%", "!%").replace("_", "!_"); }
}
