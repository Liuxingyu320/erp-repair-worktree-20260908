package com.erp.system.domain;

import java.math.BigDecimal;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import com.erp.common.core.annotation.Excel;
import com.erp.common.core.web.domain.BaseEntity;

/**
 * 薪资方案档位明细 sys_salary_scheme_item
 *
 * @author erp
 */
public class SysSalarySchemeItem extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 档位ID */
    private Long itemId;

    /** 方案ID */
    private Long schemeId;

    /** 岗位名称 */
    @Excel(name = "岗位")
    private String postName;

    /** 档位名称 */
    @Excel(name = "档位")
    private String gradeName;

    /** 地区 */
    @Excel(name = "地区")
    private String regionName;

    /** 基本工资 */
    @Excel(name = "基本工资")
    private BigDecimal baseSalary;

    /** 管理津贴 */
    @Excel(name = "管理津贴")
    private BigDecimal managementAllowance;

    /** 加班费 */
    @Excel(name = "加班费")
    private BigDecimal overtimePay;

    /** 奖励津贴 */
    @Excel(name = "奖励津贴")
    private BigDecimal rewardAllowance;

    /** 全勤奖 */
    @Excel(name = "全勤奖")
    private BigDecimal fullAttendanceBonus;

    /** 社保补贴 */
    @Excel(name = "社保补贴")
    private BigDecimal socialSubsidy;

    /** 通勤补贴 */
    @Excel(name = "通勤补贴")
    private BigDecimal commuteSubsidy;

    /** 工资合计 */
    @Excel(name = "工资合计")
    private BigDecimal totalSalary;

    /** 排序 */
    private Integer itemSort;

    /** 所属方案的期望版本。 */
    private Integer schemeVersion;

    /** 本次档位变更原因。 */
    private String changeReason;

    /** 是否按紧急修正处理。 */
    private Boolean emergencyCorrection;

    public Long getItemId()
    {
        return itemId;
    }

    public void setItemId(Long itemId)
    {
        this.itemId = itemId;
    }

    @NotNull(message = "方案ID不能为空")
    public Long getSchemeId()
    {
        return schemeId;
    }

    public void setSchemeId(Long schemeId)
    {
        this.schemeId = schemeId;
    }

    @NotBlank(message = "岗位不能为空")
    @Size(max = 64, message = "岗位长度不能超过64个字符")
    public String getPostName()
    {
        return postName;
    }

    public void setPostName(String postName)
    {
        this.postName = postName;
    }

    @Size(max = 64, message = "档位长度不能超过64个字符")
    public String getGradeName()
    {
        return gradeName;
    }

    public void setGradeName(String gradeName)
    {
        this.gradeName = gradeName;
    }

    @NotBlank(message = "地区不能为空")
    @Size(max = 100, message = "地区长度不能超过100个字符")
    public String getRegionName()
    {
        return regionName;
    }

    public void setRegionName(String regionName)
    {
        this.regionName = regionName;
    }

    public BigDecimal getBaseSalary()
    {
        return baseSalary;
    }

    public void setBaseSalary(BigDecimal baseSalary)
    {
        this.baseSalary = baseSalary;
    }

    public BigDecimal getManagementAllowance()
    {
        return managementAllowance;
    }

    public void setManagementAllowance(BigDecimal managementAllowance)
    {
        this.managementAllowance = managementAllowance;
    }

    public BigDecimal getOvertimePay()
    {
        return overtimePay;
    }

    public void setOvertimePay(BigDecimal overtimePay)
    {
        this.overtimePay = overtimePay;
    }

    public BigDecimal getRewardAllowance()
    {
        return rewardAllowance;
    }

    public void setRewardAllowance(BigDecimal rewardAllowance)
    {
        this.rewardAllowance = rewardAllowance;
    }

    public BigDecimal getFullAttendanceBonus()
    {
        return fullAttendanceBonus;
    }

    public void setFullAttendanceBonus(BigDecimal fullAttendanceBonus)
    {
        this.fullAttendanceBonus = fullAttendanceBonus;
    }

    public BigDecimal getSocialSubsidy()
    {
        return socialSubsidy;
    }

    public void setSocialSubsidy(BigDecimal socialSubsidy)
    {
        this.socialSubsidy = socialSubsidy;
    }

    public BigDecimal getCommuteSubsidy()
    {
        return commuteSubsidy;
    }

    public void setCommuteSubsidy(BigDecimal commuteSubsidy)
    {
        this.commuteSubsidy = commuteSubsidy;
    }

    public BigDecimal getTotalSalary()
    {
        return totalSalary;
    }

    public void setTotalSalary(BigDecimal totalSalary)
    {
        this.totalSalary = totalSalary;
    }

    public Integer getItemSort()
    {
        return itemSort;
    }

    public void setItemSort(Integer itemSort)
    {
        this.itemSort = itemSort;
    }

    public Integer getSchemeVersion()
    {
        return schemeVersion;
    }

    public void setSchemeVersion(Integer schemeVersion)
    {
        this.schemeVersion = schemeVersion;
    }

    @Size(max = 500, message = "变更原因不能超过500个字符")
    public String getChangeReason()
    {
        return changeReason;
    }

    public void setChangeReason(String changeReason)
    {
        this.changeReason = changeReason;
    }

    public Boolean getEmergencyCorrection()
    {
        return emergencyCorrection;
    }

    public void setEmergencyCorrection(Boolean emergencyCorrection)
    {
        this.emergencyCorrection = emergencyCorrection;
    }
}
