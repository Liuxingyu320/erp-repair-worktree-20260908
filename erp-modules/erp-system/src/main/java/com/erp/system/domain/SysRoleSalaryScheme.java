package com.erp.system.domain;

import jakarta.validation.constraints.NotNull;
import com.erp.common.core.web.domain.BaseEntity;

/**
 * 角色和薪资方案关联 sys_role_salary_scheme
 *
 * @author erp
 */
public class SysRoleSalaryScheme extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 关联ID */
    private Long relationId;

    /** 角色ID */
    private Long roleId;

    /** 方案ID */
    private Long schemeId;

    /** 默认档位ID */
    private Long itemId;

    /** 优先级 */
    private Integer priority;

    /** 生效日期 */
    private String effectiveDate;

    /** 方案名称 */
    private String schemeName;

    /** 社保口径 */
    private String socialType;

    /** 岗位名称 */
    private String postName;

    /** 地区 */
    private String regionName;

    /** 工资合计 */
    private java.math.BigDecimal totalSalary;

    public Long getRelationId()
    {
        return relationId;
    }

    public void setRelationId(Long relationId)
    {
        this.relationId = relationId;
    }

    @NotNull(message = "角色ID不能为空")
    public Long getRoleId()
    {
        return roleId;
    }

    public void setRoleId(Long roleId)
    {
        this.roleId = roleId;
    }

    @NotNull(message = "薪资方案不能为空")
    public Long getSchemeId()
    {
        return schemeId;
    }

    public void setSchemeId(Long schemeId)
    {
        this.schemeId = schemeId;
    }

    @NotNull(message = "默认档位不能为空")
    public Long getItemId()
    {
        return itemId;
    }

    public void setItemId(Long itemId)
    {
        this.itemId = itemId;
    }

    public Integer getPriority()
    {
        return priority;
    }

    public void setPriority(Integer priority)
    {
        this.priority = priority;
    }

    public String getEffectiveDate()
    {
        return effectiveDate;
    }

    public void setEffectiveDate(String effectiveDate)
    {
        this.effectiveDate = effectiveDate;
    }

    public String getSchemeName()
    {
        return schemeName;
    }

    public void setSchemeName(String schemeName)
    {
        this.schemeName = schemeName;
    }

    public String getSocialType()
    {
        return socialType;
    }

    public void setSocialType(String socialType)
    {
        this.socialType = socialType;
    }

    public String getPostName()
    {
        return postName;
    }

    public void setPostName(String postName)
    {
        this.postName = postName;
    }

    public String getRegionName()
    {
        return regionName;
    }

    public void setRegionName(String regionName)
    {
        this.regionName = regionName;
    }

    public java.math.BigDecimal getTotalSalary()
    {
        return totalSalary;
    }

    public void setTotalSalary(java.math.BigDecimal totalSalary)
    {
        this.totalSalary = totalSalary;
    }
}
