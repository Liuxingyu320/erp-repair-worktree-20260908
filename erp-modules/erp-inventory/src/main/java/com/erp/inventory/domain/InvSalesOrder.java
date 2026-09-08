package com.erp.inventory.domain;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import jakarta.validation.constraints.NotBlank;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.erp.common.core.annotation.Excel;
import com.erp.common.core.web.domain.BaseEntity;

public class InvSalesOrder extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long orderId;

    @Excel(name = "销售单号")
    private String orderNo;

    @NotBlank(message = "销售标题不能为空")
    @Excel(name = "销售标题")
    private String orderTitle;

    @Excel(name = "客户")
    private String customerName;

    private Long customerId;

    @Excel(name = "销售门店")
    private String targetDeptName;

    private Long targetDeptId;

    @Excel(name = "销售总数量")
    private BigDecimal totalQuantity;

    @Excel(name = "已发货数量")
    private BigDecimal deliveredQuantity;

    @Excel(name = "待发货数量")
    private BigDecimal remainingQuantity;

    @Excel(name = "总金额")
    private BigDecimal totalAmount;

    @JsonFormat(pattern = "yyyy-MM-dd")
    @Excel(name = "销售日期", dateFormat = "yyyy-MM-dd")
    private Date orderDate;

    @Excel(name = "状态", readConverterExp = "draft=草稿,submitted=已提交,noticed=已生成发货通知,delivered=已出库,cancelled=已取消")
    private String status;

    private Long shopDeptId;

    private Long applicantId;

    @Excel(name = "申请人账号")
    private String applicantName;

    @Excel(name = "申请人姓名")
    private String applicantNickName;

    private Long applicantDeptId;

    @Excel(name = "申请部门")
    private String applicantDeptName;

    private List<InvSalesDetail> details;

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public String getOrderTitle() { return orderTitle; }
    public void setOrderTitle(String orderTitle) { this.orderTitle = orderTitle; }
    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }
    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }
    public String getTargetDeptName() { return targetDeptName; }
    public void setTargetDeptName(String targetDeptName) { this.targetDeptName = targetDeptName; }
    public Long getTargetDeptId() { return targetDeptId; }
    public void setTargetDeptId(Long targetDeptId) { this.targetDeptId = targetDeptId; }
    public BigDecimal getTotalQuantity() { return totalQuantity; }
    public void setTotalQuantity(BigDecimal totalQuantity) { this.totalQuantity = totalQuantity; }
    public BigDecimal getDeliveredQuantity() { return deliveredQuantity; }
    public void setDeliveredQuantity(BigDecimal deliveredQuantity) { this.deliveredQuantity = deliveredQuantity; }
    public BigDecimal getRemainingQuantity() { return remainingQuantity; }
    public void setRemainingQuantity(BigDecimal remainingQuantity) { this.remainingQuantity = remainingQuantity; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
    public Date getOrderDate() { return orderDate; }
    public void setOrderDate(Date orderDate) { this.orderDate = orderDate; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getShopDeptId() { return shopDeptId; }
    public void setShopDeptId(Long shopDeptId) { this.shopDeptId = shopDeptId; }
    public Long getApplicantId() { return applicantId; }
    public void setApplicantId(Long applicantId) { this.applicantId = applicantId; }
    public String getApplicantName() { return applicantName; }
    public void setApplicantName(String applicantName) { this.applicantName = applicantName; }
    public String getApplicantNickName() { return applicantNickName; }
    public void setApplicantNickName(String applicantNickName) { this.applicantNickName = applicantNickName; }
    public Long getApplicantDeptId() { return applicantDeptId; }
    public void setApplicantDeptId(Long applicantDeptId) { this.applicantDeptId = applicantDeptId; }
    public String getApplicantDeptName() { return applicantDeptName; }
    public void setApplicantDeptName(String applicantDeptName) { this.applicantDeptName = applicantDeptName; }
    public List<InvSalesDetail> getDetails() { return details; }
    public void setDetails(List<InvSalesDetail> details) { this.details = details; }
}
