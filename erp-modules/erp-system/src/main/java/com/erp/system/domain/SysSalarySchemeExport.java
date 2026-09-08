package com.erp.system.domain;

import java.math.BigDecimal;
import com.erp.common.core.annotation.Excel;

/**
 * 薪资方案导出行。
 */
public class SysSalarySchemeExport
{
    @Excel(name = "方案名称")
    private String schemeName;

    @Excel(name = "社保口径")
    private String socialType;

    @Excel(name = "生效日期")
    private String effectiveDate;

    @Excel(name = "状态", readConverterExp = "0=正常,1=停用")
    private String status;

    @Excel(name = "岗位")
    private String postName;

    @Excel(name = "档位")
    private String gradeName;

    @Excel(name = "地区")
    private String regionName;

    @Excel(name = "基本工资")
    private BigDecimal baseSalary;

    @Excel(name = "管理津贴")
    private BigDecimal managementAllowance;

    @Excel(name = "加班费")
    private BigDecimal overtimePay;

    @Excel(name = "奖励津贴")
    private BigDecimal rewardAllowance;

    @Excel(name = "全勤奖")
    private BigDecimal fullAttendanceBonus;

    @Excel(name = "社保补贴")
    private BigDecimal socialSubsidy;

    @Excel(name = "通勤补贴")
    private BigDecimal commuteSubsidy;

    @Excel(name = "工资合计")
    private BigDecimal totalSalary;

    public static SysSalarySchemeExport from(SysSalaryScheme scheme, SysSalarySchemeItem item)
    {
        SysSalarySchemeExport row = new SysSalarySchemeExport();
        row.setSchemeName(scheme.getSchemeName());
        row.setSocialType(scheme.getSocialType());
        row.setEffectiveDate(scheme.getEffectiveDate());
        row.setStatus(scheme.getStatus());
        if (item != null)
        {
            row.setPostName(item.getPostName());
            row.setGradeName(item.getGradeName());
            row.setRegionName(item.getRegionName());
            row.setBaseSalary(item.getBaseSalary());
            row.setManagementAllowance(item.getManagementAllowance());
            row.setOvertimePay(item.getOvertimePay());
            row.setRewardAllowance(item.getRewardAllowance());
            row.setFullAttendanceBonus(item.getFullAttendanceBonus());
            row.setSocialSubsidy(item.getSocialSubsidy());
            row.setCommuteSubsidy(item.getCommuteSubsidy());
            row.setTotalSalary(item.getTotalSalary());
        }
        return row;
    }

    public String getSchemeName() { return schemeName; }
    public void setSchemeName(String schemeName) { this.schemeName = schemeName; }
    public String getSocialType() { return socialType; }
    public void setSocialType(String socialType) { this.socialType = socialType; }
    public String getEffectiveDate() { return effectiveDate; }
    public void setEffectiveDate(String effectiveDate) { this.effectiveDate = effectiveDate; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getPostName() { return postName; }
    public void setPostName(String postName) { this.postName = postName; }
    public String getGradeName() { return gradeName; }
    public void setGradeName(String gradeName) { this.gradeName = gradeName; }
    public String getRegionName() { return regionName; }
    public void setRegionName(String regionName) { this.regionName = regionName; }
    public BigDecimal getBaseSalary() { return baseSalary; }
    public void setBaseSalary(BigDecimal baseSalary) { this.baseSalary = baseSalary; }
    public BigDecimal getManagementAllowance() { return managementAllowance; }
    public void setManagementAllowance(BigDecimal managementAllowance) { this.managementAllowance = managementAllowance; }
    public BigDecimal getOvertimePay() { return overtimePay; }
    public void setOvertimePay(BigDecimal overtimePay) { this.overtimePay = overtimePay; }
    public BigDecimal getRewardAllowance() { return rewardAllowance; }
    public void setRewardAllowance(BigDecimal rewardAllowance) { this.rewardAllowance = rewardAllowance; }
    public BigDecimal getFullAttendanceBonus() { return fullAttendanceBonus; }
    public void setFullAttendanceBonus(BigDecimal fullAttendanceBonus) { this.fullAttendanceBonus = fullAttendanceBonus; }
    public BigDecimal getSocialSubsidy() { return socialSubsidy; }
    public void setSocialSubsidy(BigDecimal socialSubsidy) { this.socialSubsidy = socialSubsidy; }
    public BigDecimal getCommuteSubsidy() { return commuteSubsidy; }
    public void setCommuteSubsidy(BigDecimal commuteSubsidy) { this.commuteSubsidy = commuteSubsidy; }
    public BigDecimal getTotalSalary() { return totalSalary; }
    public void setTotalSalary(BigDecimal totalSalary) { this.totalSalary = totalSalary; }
}
