package com.erp.inventory.domain;

import java.math.BigDecimal;
import java.util.List;
import com.erp.common.core.annotation.Excel;
import com.erp.common.core.web.domain.BaseEntity;

public class InvDeliveryNotice extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long noticeId;

    @Excel(name = "通知单号")
    private String noticeNo;

    private Long salesOrderId;

    @Excel(name = "销售单号")
    private String salesOrderNo;

    @Excel(name = "客户名称")
    private String customerName;

    @Excel(name = "状态", readConverterExp = "pending=待发货,delivering=发货中,completed=已发货,cancelled=已取消")
    private String status;

    private String statusGroup;

    private Long shopDeptId;

    @Excel(name = "销售门店")
    private String shopDeptName;

    private Long warehouseId;

    @Excel(name = "发货仓库")
    private String warehouseName;

    @Excel(name = "通知总数量")
    private BigDecimal noticeQuantity;

    @Excel(name = "已发货数量")
    private BigDecimal deliveredQuantity;

    @Excel(name = "待发货数量")
    private BigDecimal remainingQuantity;

    private List<InvDeliveryNoticeDetail> details;

    public Long getNoticeId() { return noticeId; }
    public void setNoticeId(Long noticeId) { this.noticeId = noticeId; }
    public String getNoticeNo() { return noticeNo; }
    public void setNoticeNo(String noticeNo) { this.noticeNo = noticeNo; }
    public Long getSalesOrderId() { return salesOrderId; }
    public void setSalesOrderId(Long salesOrderId) { this.salesOrderId = salesOrderId; }
    public String getSalesOrderNo() { return salesOrderNo; }
    public void setSalesOrderNo(String salesOrderNo) { this.salesOrderNo = salesOrderNo; }
    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getStatusGroup() { return statusGroup; }
    public void setStatusGroup(String statusGroup) { this.statusGroup = statusGroup; }
    public Long getShopDeptId() { return shopDeptId; }
    public void setShopDeptId(Long shopDeptId) { this.shopDeptId = shopDeptId; }
    public String getShopDeptName() { return shopDeptName; }
    public void setShopDeptName(String shopDeptName) { this.shopDeptName = shopDeptName; }
    public Long getWarehouseId() { return warehouseId; }
    public void setWarehouseId(Long warehouseId) { this.warehouseId = warehouseId; }
    public String getWarehouseName() { return warehouseName; }
    public void setWarehouseName(String warehouseName) { this.warehouseName = warehouseName; }
    public BigDecimal getNoticeQuantity() { return noticeQuantity; }
    public void setNoticeQuantity(BigDecimal noticeQuantity) { this.noticeQuantity = noticeQuantity; }
    public BigDecimal getDeliveredQuantity() { return deliveredQuantity; }
    public void setDeliveredQuantity(BigDecimal deliveredQuantity) { this.deliveredQuantity = deliveredQuantity; }
    public BigDecimal getRemainingQuantity() { return remainingQuantity; }
    public void setRemainingQuantity(BigDecimal remainingQuantity) { this.remainingQuantity = remainingQuantity; }
    public List<InvDeliveryNoticeDetail> getDetails() { return details; }
    public void setDetails(List<InvDeliveryNoticeDetail> details) { this.details = details; }
}
