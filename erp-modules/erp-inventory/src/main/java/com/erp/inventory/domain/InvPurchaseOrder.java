package com.erp.inventory.domain;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.erp.common.core.annotation.Excel;
import com.erp.common.core.web.domain.BaseEntity;

public class InvPurchaseOrder extends BaseEntity
{
    /** Revision of the opened draft; required when modifying an existing document. */
    private Long version;

    @tools.jackson.databind.annotation.JsonSerialize(using = tools.jackson.databind.ser.std.ToStringSerializer.class)
    @com.fasterxml.jackson.databind.annotation.JsonSerialize(using = com.fasterxml.jackson.databind.ser.std.ToStringSerializer.class)
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }

    private static final long serialVersionUID = 1L;

    private Long orderId;

    @Excel(name = "采购单号")
    private String orderNo;

    @NotBlank(message = "采购标题不能为空")
    @Excel(name = "采购标题")
    private String orderTitle;

    @Excel(name = "供应商")
    @Size(max = 128, message = "供应商名称不能超过128个字符")
    private String supplierName;

    private Long supplierId;

    @Excel(name = "总金额")
    private BigDecimal totalAmount;

    @JsonFormat(pattern = "yyyy-MM-dd")
    @Excel(name = "采购日期", dateFormat = "yyyy-MM-dd")
    private Date orderDate;

    @Excel(name = "状态", readConverterExp = "draft=草稿,submitted=已提交,received=已收货,cancelled=已取消")
    private String status;

    private Long shopDeptId;

    @Excel(name = "采购店铺")
    private String shopDeptName;

    private Long applicantId;

    @Excel(name = "申请人账号")
    private String applicantName;

    @Excel(name = "申请人姓名")
    private String applicantNickName;

    private Long applicantDeptId;

    @Excel(name = "申请部门")
    private String applicantDeptName;

    @Excel(name = "质检状态", readConverterExp = "pending=待质检,passed=质检合格,rejected=质检不合格,concession=让步接收")
    private String qcStatus;

    @Excel(name = "采购总数量")
    private BigDecimal totalQuantity;

    @Excel(name = "已收货数量")
    private BigDecimal receivedQuantity;

    @Excel(name = "待收货数量")
    private BigDecimal remainingQuantity;

    /** 兼容期业务阶段投影，不替换现有 status/qcStatus 字段。 */
    private String businessStage;

    private List<InvPurchaseDetail> details;

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public String getOrderTitle() { return orderTitle; }
    public void setOrderTitle(String orderTitle) { this.orderTitle = orderTitle; }
    public String getSupplierName() { return supplierName; }
    public void setSupplierName(String supplierName) { this.supplierName = supplierName; }
    public Long getSupplierId() { return supplierId; }
    public void setSupplierId(Long supplierId) { this.supplierId = supplierId; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
    public Date getOrderDate() { return orderDate; }
    public void setOrderDate(Date orderDate) { this.orderDate = orderDate; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getShopDeptId() { return shopDeptId; }
    public void setShopDeptId(Long shopDeptId) { this.shopDeptId = shopDeptId; }
    public String getShopDeptName() { return shopDeptName; }
    public void setShopDeptName(String shopDeptName) { this.shopDeptName = shopDeptName; }
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
    public String getQcStatus() { return qcStatus; }
    public void setQcStatus(String qcStatus) { this.qcStatus = qcStatus; }
    public BigDecimal getTotalQuantity() { return totalQuantity; }
    public void setTotalQuantity(BigDecimal totalQuantity) { this.totalQuantity = totalQuantity; }
    public BigDecimal getReceivedQuantity() { return receivedQuantity; }
    public void setReceivedQuantity(BigDecimal receivedQuantity) { this.receivedQuantity = receivedQuantity; }
    public BigDecimal getRemainingQuantity() { return remainingQuantity; }
    public void setRemainingQuantity(BigDecimal remainingQuantity) { this.remainingQuantity = remainingQuantity; }
    public String getBusinessStage() { return businessStage; }
    public void setBusinessStage(String businessStage) { this.businessStage = businessStage; }
    public List<InvPurchaseDetail> getDetails() { return details; }
    public void setDetails(List<InvPurchaseDetail> details) { this.details = details; }
}
