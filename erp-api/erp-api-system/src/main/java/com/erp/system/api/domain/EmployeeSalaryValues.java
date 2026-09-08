package com.erp.system.api.domain;

import java.math.BigDecimal;

/** The five amounts in the onboarding contract workbook. */
public class EmployeeSalaryValues
{
    private BigDecimal baseSalary;
    private BigDecimal postSalary;
    private BigDecimal fieldAllowance;
    private BigDecimal performanceSalary;
    private BigDecimal salaryTotal;

    public BigDecimal getBaseSalary() { return baseSalary; }
    public void setBaseSalary(BigDecimal value) { baseSalary = value; }
    public BigDecimal getPostSalary() { return postSalary; }
    public void setPostSalary(BigDecimal value) { postSalary = value; }
    public BigDecimal getFieldAllowance() { return fieldAllowance; }
    public void setFieldAllowance(BigDecimal value) { fieldAllowance = value; }
    public BigDecimal getPerformanceSalary() { return performanceSalary; }
    public void setPerformanceSalary(BigDecimal value) { performanceSalary = value; }
    public BigDecimal getSalaryTotal() { return salaryTotal; }
    public void setSalaryTotal(BigDecimal value) { salaryTotal = value; }

    public String validationError()
    {
        BigDecimal[] amounts = { baseSalary, postSalary, fieldAllowance,
                performanceSalary, salaryTotal };
        for (BigDecimal amount : amounts)
        {
            if (amount == null) return "员工档案工资未完整入档，请核对入职合同Excel";
            if (amount.signum() < 0 || amount.stripTrailingZeros().scale() > 2
                    || amount.compareTo(new BigDecimal("99999999999999.99")) > 0)
                return "员工档案工资金额无效";
        }
        if (salaryTotal.signum() <= 0) return "员工档案综合工资必须大于0";
        if (baseSalary.add(postSalary).add(fieldAllowance)
                .add(performanceSalary).compareTo(salaryTotal) != 0)
            return "员工档案综合工资与底薪、津贴合计不一致";
        return null;
    }

    public static EmployeeSalaryValues fromProfile(SysUserProfile profile)
    {
        EmployeeSalaryValues value = new EmployeeSalaryValues();
        if (profile != null)
        {
            value.setBaseSalary(profile.getBaseSalary());
            value.setPostSalary(profile.getPostSalary());
            value.setFieldAllowance(profile.getFieldAllowance());
            value.setPerformanceSalary(profile.getPerformanceSalary());
            value.setSalaryTotal(profile.getSalaryTotal());
        }
        return value;
    }
}
