package com.erp.inventory.domain.vo;

/** 销售和移动端选择客户时使用的安全最小字段集。 */
public class InvCustomerOptionVo
{
    private Long customerId;
    private String customerName;
    private String customerCode;
    private String contactPhone;
    private String status;

    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long value) { customerId = value; }
    public String getCustomerName() { return customerName; }
    public void setCustomerName(String value) { customerName = value; }
    public String getCustomerCode() { return customerCode; }
    public void setCustomerCode(String value) { customerCode = value; }
    public String getContactPhone() { return contactPhone; }
    public void setContactPhone(String value) { contactPhone = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { status = value; }
}
