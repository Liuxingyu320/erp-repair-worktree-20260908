package com.erp.inventory.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import com.erp.common.core.annotation.Excel;
import com.erp.common.core.web.domain.BaseEntity;

public class InvSupplier extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long supplierId;

    @Excel(name = "供应商名称")
    private String supplierName;

    @Excel(name = "供应商编码")
    private String supplierCode;

    @Excel(name = "联系人")
    private String contactPerson;

    @Excel(name = "联系电话")
    private String contactPhone;

    @Excel(name = "电子邮箱")
    private String contactEmail;

    @Excel(name = "地址")
    private String address;

    @Excel(name = "结算方式")
    private String settlementMethod;

    @Excel(name = "合作状态", readConverterExp = "0=合作中,1=暂停,2=终止")
    private String cooperationStatus;

    private Long shopDeptId;

    @Excel(name = "所属店铺")
    private String shopDeptName;

    @Excel(name = "状态", readConverterExp = "0=正常,1=停用")
    private String status;

    public Long getSupplierId() { return supplierId; }
    public void setSupplierId(Long supplierId) { this.supplierId = supplierId; }

    @NotBlank(message = "供应商名称不能为空")
    @Size(min = 0, max = 128, message = "供应商名称不能超过128个字符")
    public String getSupplierName() { return supplierName; }
    public void setSupplierName(String supplierName) { this.supplierName = supplierName; }

    @Size(max = 64, message = "供应商编码不能超过64个字符")
    public String getSupplierCode() { return supplierCode; }
    public void setSupplierCode(String supplierCode) { this.supplierCode = supplierCode; }

    @Size(max = 64, message = "联系人不能超过64个字符")
    public String getContactPerson() { return contactPerson; }
    public void setContactPerson(String contactPerson) { this.contactPerson = contactPerson; }

    @Size(max = 32, message = "联系电话不能超过32个字符")
    public String getContactPhone() { return contactPhone; }
    public void setContactPhone(String contactPhone) { this.contactPhone = contactPhone; }

    @Email(message = "电子邮箱格式无效")
    @Size(max = 64, message = "电子邮箱不能超过64个字符")
    public String getContactEmail() { return contactEmail; }
    public void setContactEmail(String contactEmail) { this.contactEmail = contactEmail; }

    @Size(max = 256, message = "地址不能超过256个字符")
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    @Size(max = 64, message = "结算方式不能超过64个字符")
    public String getSettlementMethod() { return settlementMethod; }
    public void setSettlementMethod(String settlementMethod) { this.settlementMethod = settlementMethod; }

    @Pattern(regexp = "[012]", message = "合作状态无效")
    public String getCooperationStatus() { return cooperationStatus; }
    public void setCooperationStatus(String cooperationStatus) { this.cooperationStatus = cooperationStatus; }

    public Long getShopDeptId() { return shopDeptId; }
    public void setShopDeptId(Long shopDeptId) { this.shopDeptId = shopDeptId; }
    public String getShopDeptName() { return shopDeptName; }
    public void setShopDeptName(String shopDeptName) { this.shopDeptName = shopDeptName; }

    @Pattern(regexp = "[01]", message = "供应商状态无效")
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    @Override
    @Size(max = 500, message = "备注不能超过500个字符")
    public String getRemark() { return super.getRemark(); }

    @Override
    public void setRemark(String remark) { super.setRemark(remark); }
}
