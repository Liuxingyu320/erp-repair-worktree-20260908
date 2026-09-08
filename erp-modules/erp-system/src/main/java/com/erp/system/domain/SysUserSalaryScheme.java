package com.erp.system.domain;

import java.math.BigDecimal;
import jakarta.validation.constraints.NotNull;
import com.erp.common.core.web.domain.BaseEntity;

/**
 * 用户和薪资方案关联 sys_user_salary_scheme
 *
 * @author erp
 */
public class SysUserSalaryScheme extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 关联ID */
    private Long relationId;

    /** 用户ID */
    private Long userId;

    /** 核算门店/部门ID */
    private Long shopDeptId;

    /** 方案ID */
    private Long schemeId;

    /** 默认档位ID */
    private Long itemId;

    /** 优先级 */
    private Integer priority;

    /** 生效日期 */
    private String effectiveDate;

    /** 失效日期 */
    private String endDate;

    /** 状态 */
    private String status;

    /** 用户账号 */
    private String userName;

    /** 用户昵称 */
    private String nickName;

    /** 部门ID */
    private Long deptId;

    /** 部门名称 */
    private String deptName;

    /** 核算门店名称 */
    private String shopDeptName;

    /** 方案名称 */
    private String schemeName;

    /** 社保口径 */
    private String socialType;

    /** 岗位名称 */
    private String postName;

    /** 档位名称 */
    private String gradeName;

    /** 地区 */
    private String regionName;

    /** 工资合计 */
    private BigDecimal totalSalary;

    public Long getRelationId()
    {
        return relationId;
    }

    public void setRelationId(Long relationId)
    {
        this.relationId = relationId;
    }

    @NotNull(message = "用户ID不能为空")
    public Long getUserId()
    {
        return userId;
    }

    public void setUserId(Long userId)
    {
        this.userId = userId;
    }

    @NotNull(message = "核算门店不能为空")
    public Long getShopDeptId()
    {
        return shopDeptId;
    }

    public void setShopDeptId(Long shopDeptId)
    {
        this.shopDeptId = shopDeptId;
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

    public String getEndDate()
    {
        return endDate;
    }

    public void setEndDate(String endDate)
    {
        this.endDate = endDate;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public String getUserName()
    {
        return userName;
    }

    public void setUserName(String userName)
    {
        this.userName = userName;
    }

    public String getNickName()
    {
        return nickName;
    }

    public void setNickName(String nickName)
    {
        this.nickName = nickName;
    }

    public Long getDeptId()
    {
        return deptId;
    }

    public void setDeptId(Long deptId)
    {
        this.deptId = deptId;
    }

    public String getDeptName()
    {
        return deptName;
    }

    public void setDeptName(String deptName)
    {
        this.deptName = deptName;
    }

    public String getShopDeptName()
    {
        return shopDeptName;
    }

    public void setShopDeptName(String shopDeptName)
    {
        this.shopDeptName = shopDeptName;
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

    public String getGradeName()
    {
        return gradeName;
    }

    public void setGradeName(String gradeName)
    {
        this.gradeName = gradeName;
    }

    public String getRegionName()
    {
        return regionName;
    }

    public void setRegionName(String regionName)
    {
        this.regionName = regionName;
    }

    public BigDecimal getTotalSalary()
    {
        return totalSalary;
    }

    public void setTotalSalary(BigDecimal totalSalary)
    {
        this.totalSalary = totalSalary;
    }
}
