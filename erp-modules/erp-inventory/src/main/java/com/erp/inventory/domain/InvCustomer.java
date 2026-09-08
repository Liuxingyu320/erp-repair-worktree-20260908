package com.erp.inventory.domain;

import java.math.BigDecimal;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import com.erp.common.core.annotation.Excel;
import com.erp.common.core.web.domain.BaseEntity;

public class InvCustomer extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long customerId;

    @Excel(name = "客户名称")
    private String customerName;

    @Excel(name = "客户编码")
    private String customerCode;

    @Excel(name = "联系人")
    private String contactPerson;

    @Excel(name = "联系电话")
    private String contactPhone;

    @Excel(name = "电子邮箱")
    private String contactEmail;

    @Excel(name = "地址")
    private String address;

    @Excel(name = "信用额度")
    private BigDecimal creditLimit;

    @Excel(name = "已用额度")
    private BigDecimal creditUsed;

    @Excel(name = "账期")
    private String paymentTerms;

    @Excel(name = "客户等级")
    private String customerLevel;

    private Long shopDeptId;

    @Excel(name = "所属店铺")
    private String shopDeptName;

    @Excel(name = "状态", readConverterExp = "0=正常,1=停用")
    private String status;

    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }

    @NotBlank(message = "客户名称不能为空")
    @Size(min = 0, max = 128, message = "客户名称不能超过128个字符")
    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }

    public String getCustomerCode() { return customerCode; }
    public void setCustomerCode(String customerCode) { this.customerCode = customerCode; }

    public String getContactPerson() { return contactPerson; }
    public void setContactPerson(String contactPerson) { this.contactPerson = contactPerson; }

    public String getContactPhone() { return contactPhone; }
    public void setContactPhone(String contactPhone) { this.contactPhone = contactPhone; }

    public String getContactEmail() { return contactEmail; }
    public void setContactEmail(String contactEmail) { this.contactEmail = contactEmail; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public BigDecimal getCreditLimit() { return creditLimit; }
    public void setCreditLimit(BigDecimal creditLimit) { this.creditLimit = creditLimit; }

    public BigDecimal getCreditUsed() { return creditUsed; }
    public void setCreditUsed(BigDecimal creditUsed) { this.creditUsed = creditUsed; }

    public String getPaymentTerms() { return paymentTerms; }
    public void setPaymentTerms(String paymentTerms) { this.paymentTerms = paymentTerms; }

    public String getCustomerLevel() { return customerLevel; }
    public void setCustomerLevel(String customerLevel) { this.customerLevel = customerLevel; }

    public Long getShopDeptId() { return shopDeptId; }
    public void setShopDeptId(Long shopDeptId) { this.shopDeptId = shopDeptId; }
    public String getShopDeptName() { return shopDeptName; }
    public void setShopDeptName(String shopDeptName) { this.shopDeptName = shopDeptName; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
