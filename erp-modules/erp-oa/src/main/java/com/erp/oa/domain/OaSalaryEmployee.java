package com.erp.oa.domain;

import com.erp.system.api.domain.EmployeeSalaryValues;

public class OaSalaryEmployee extends EmployeeSalaryValues
{
    private Long userId;
    private String userName;
    private Long deptId;
    private Long shopDeptId;
    public Long getUserId() { return userId; }
    public void setUserId(Long value) { userId = value; }
    public String getUserName() { return userName; }
    public void setUserName(String value) { userName = value; }
    public Long getDeptId() { return deptId; }
    public void setDeptId(Long value) { deptId = value; }
    public Long getShopDeptId() { return shopDeptId; }
    public void setShopDeptId(Long value) { shopDeptId = value; }
}
