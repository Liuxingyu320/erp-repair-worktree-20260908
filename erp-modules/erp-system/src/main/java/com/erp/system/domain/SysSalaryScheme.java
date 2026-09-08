package com.erp.system.domain;

import java.util.List;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import com.erp.common.core.annotation.Excel;
import com.erp.common.core.web.domain.BaseEntity;

/**
 * 薪资方案 sys_salary_scheme
 *
 * @author erp
 */
public class SysSalaryScheme extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 方案ID */
    private Long schemeId;

    /** 方案名称 */
    @Excel(name = "方案名称")
    private String schemeName;

    /** 社保口径（有社保/无社保） */
    @Excel(name = "社保口径")
    private String socialType;

    /** 生效日期 */
    @Excel(name = "生效日期")
    private String effectiveDate;

    /** 状态（0正常 1停用） */
    @Excel(name = "状态", readConverterExp = "0=正常,1=停用")
    private String status;

    /** 明细数量 */
    private Integer itemCount;

    /** 绑定角色数量 */
    private Integer roleCount;

    /** 当前业务版本，更新时同时作为期望版本。 */
    private Integer version;

    /** 本次变更原因，不落库到方案主表。 */
    private String changeReason;

    /** 是否按紧急修正处理，不落库到方案主表。 */
    private Boolean emergencyCorrection;

    /** 档位明细 */
    private List<SysSalarySchemeItem> items;

    public Long getSchemeId()
    {
        return schemeId;
    }

    public void setSchemeId(Long schemeId)
    {
        this.schemeId = schemeId;
    }

    @NotBlank(message = "方案名称不能为空")
    @Size(max = 100, message = "方案名称长度不能超过100个字符")
    public String getSchemeName()
    {
        return schemeName;
    }

    public void setSchemeName(String schemeName)
    {
        this.schemeName = schemeName;
    }

    @NotBlank(message = "社保口径不能为空")
    @Size(max = 20, message = "社保口径长度不能超过20个字符")
    public String getSocialType()
    {
        return socialType;
    }

    public void setSocialType(String socialType)
    {
        this.socialType = socialType;
    }

    @NotBlank(message = "生效日期不能为空")
    @Size(max = 10, message = "生效日期长度不能超过10个字符")
    public String getEffectiveDate()
    {
        return effectiveDate;
    }

    public void setEffectiveDate(String effectiveDate)
    {
        this.effectiveDate = effectiveDate;
    }

    @NotNull(message = "状态不能为空")
    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public Integer getItemCount()
    {
        return itemCount;
    }

    public void setItemCount(Integer itemCount)
    {
        this.itemCount = itemCount;
    }

    public Integer getRoleCount()
    {
        return roleCount;
    }

    public void setRoleCount(Integer roleCount)
    {
        this.roleCount = roleCount;
    }

    public Integer getVersion()
    {
        return version;
    }

    public void setVersion(Integer version)
    {
        this.version = version;
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

    public List<SysSalarySchemeItem> getItems()
    {
        return items;
    }

    public void setItems(List<SysSalarySchemeItem> items)
    {
        this.items = items;
    }
}
