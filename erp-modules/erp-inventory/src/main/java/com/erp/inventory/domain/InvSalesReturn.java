package com.erp.inventory.domain;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import com.erp.common.core.annotation.Excel;
import com.erp.common.core.web.domain.BaseEntity;

public class InvSalesReturn extends BaseEntity
{
    /** Revision of the opened draft; required when modifying an existing document. */
    private Long version;

    @tools.jackson.databind.annotation.JsonSerialize(using = tools.jackson.databind.ser.std.ToStringSerializer.class)
    @com.fasterxml.jackson.databind.annotation.JsonSerialize(using = com.fasterxml.jackson.databind.ser.std.ToStringSerializer.class)
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }

    private static final long serialVersionUID = 1L;

    private Long returnId;

    @Excel(name = "销售退货单号")
    private String returnNo;
    private Long salesOrderId;

    @Excel(name = "原销售单号")
    private String salesOrderNo;

    @Excel(name = "退货主题")
    private String returnTitle;

    @Excel(name = "客户")
    private String customerName;

    @Excel(name = "退货金额")
    private BigDecimal totalAmount;

    @Excel(name = "退货总数量")
    private BigDecimal totalQuantity;

    @Excel(name = "已退货数量")
    private BigDecimal returnedQuantity;

    @Excel(name = "待退货数量")
    private BigDecimal remainingQuantity;

    @Excel(name = "退货日期", dateFormat = "yyyy-MM-dd")
    private Date returnDate;

    @Excel(name = "状态", readConverterExp = "draft=草稿,submitted=已提交,returned=已退货,cancelled=已取消")
    private String status;
    private Long shopDeptId;

    @Excel(name = "退货店铺")
    private String shopDeptName;

    private Long applicantId;

    @Excel(name = "申请人账号")
    private String applicantName;

    @Excel(name = "申请人姓名")
    private String applicantNickName;

    private Long applicantDeptId;

    @Excel(name = "申请部门")
    private String applicantDeptName;

    private List<InvSalesReturnDetail> details;

    public Long getReturnId() { return returnId; }
    public void setReturnId(Long returnId) { this.returnId = returnId; }

    public String getReturnNo() { return returnNo; }
    public void setReturnNo(String returnNo) { this.returnNo = returnNo; }

    public Long getSalesOrderId() { return salesOrderId; }
    public void setSalesOrderId(Long salesOrderId) { this.salesOrderId = salesOrderId; }

    public String getSalesOrderNo() { return salesOrderNo; }
    public void setSalesOrderNo(String salesOrderNo) { this.salesOrderNo = salesOrderNo; }

    @NotBlank(message = "退货主题不能为空")
    @Size(min = 0, max = 128, message = "退货主题不能超过128个字符")
    public String getReturnTitle() { return returnTitle; }
    public void setReturnTitle(String returnTitle) { this.returnTitle = returnTitle; }

    @NotBlank(message = "客户名称不能为空")
    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }

    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }

    public BigDecimal getTotalQuantity() { return totalQuantity; }
    public void setTotalQuantity(BigDecimal totalQuantity) { this.totalQuantity = totalQuantity; }

    public BigDecimal getReturnedQuantity() { return returnedQuantity; }
    public void setReturnedQuantity(BigDecimal returnedQuantity) { this.returnedQuantity = returnedQuantity; }

    public BigDecimal getRemainingQuantity() { return remainingQuantity; }
    public void setRemainingQuantity(BigDecimal remainingQuantity) { this.remainingQuantity = remainingQuantity; }

    public Date getReturnDate() { return returnDate; }
    public void setReturnDate(Date returnDate) { this.returnDate = returnDate; }

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

    public List<InvSalesReturnDetail> getDetails() { return details; }
    public void setDetails(List<InvSalesReturnDetail> details) { this.details = details; }
}
