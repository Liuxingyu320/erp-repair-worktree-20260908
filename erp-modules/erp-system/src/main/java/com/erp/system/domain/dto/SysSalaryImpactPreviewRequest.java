package com.erp.system.domain.dto;

import java.util.List;
import jakarta.validation.constraints.NotBlank;
import com.erp.system.domain.SysRoleSalaryScheme;
import com.erp.system.domain.SysUserSalaryScheme;

/** 薪资方案、档位和绑定保存前的只读影响预览请求。 */
public class SysSalaryImpactPreviewRequest
{
    @NotBlank(message = "预览操作类型不能为空")
    private String operation;
    private Long schemeId;
    private Long itemId;
    private Long roleId;
    private Long userId;
    private Integer expectedVersion;
    private String effectiveDate;
    private List<Long> schemeIds;
    private List<SysRoleSalaryScheme> roleBindings;
    private List<SysUserSalaryScheme> userBindings;

    public String getOperation() { return operation; }
    public void setOperation(String operation) { this.operation = operation; }
    public Long getSchemeId() { return schemeId; }
    public void setSchemeId(Long schemeId) { this.schemeId = schemeId; }
    public Long getItemId() { return itemId; }
    public void setItemId(Long itemId) { this.itemId = itemId; }
    public Long getRoleId() { return roleId; }
    public void setRoleId(Long roleId) { this.roleId = roleId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Integer getExpectedVersion() { return expectedVersion; }
    public void setExpectedVersion(Integer expectedVersion) { this.expectedVersion = expectedVersion; }
    public String getEffectiveDate() { return effectiveDate; }
    public void setEffectiveDate(String effectiveDate) { this.effectiveDate = effectiveDate; }
    public List<Long> getSchemeIds() { return schemeIds; }
    public void setSchemeIds(List<Long> schemeIds) { this.schemeIds = schemeIds; }
    public List<SysRoleSalaryScheme> getRoleBindings() { return roleBindings; }
    public void setRoleBindings(List<SysRoleSalaryScheme> roleBindings) { this.roleBindings = roleBindings; }
    public List<SysUserSalaryScheme> getUserBindings() { return userBindings; }
    public void setUserBindings(List<SysUserSalaryScheme> userBindings) { this.userBindings = userBindings; }
}
