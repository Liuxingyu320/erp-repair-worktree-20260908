package com.erp.system.domain.dto;

import java.util.LinkedHashSet;
import java.util.Set;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import com.erp.system.api.domain.SysUser;

/** 用户编辑差量请求；changes 只承载 changedFields 声明的实际变化值。 */
public class SysUserChangeRequest
{
    @NotNull(message = "用户ID不能为空")
    private Long userId;

    @NotEmpty(message = "至少提交一个变化字段")
    @Size(max = 80, message = "单次最多修改80个字段")
    private Set<@Pattern(regexp = "^[A-Za-z][A-Za-z0-9]*(\\.[A-Za-z][A-Za-z0-9]*)?$",
            message = "变化字段格式不正确") String> changedFields = new LinkedHashSet<>();

    @NotNull(message = "变化内容不能为空")
    private SysUser changes;

    public Long getUserId()
    {
        return userId;
    }

    public void setUserId(Long userId)
    {
        this.userId = userId;
    }

    public Set<String> getChangedFields()
    {
        return changedFields;
    }

    public void setChangedFields(Set<String> changedFields)
    {
        this.changedFields = changedFields == null ? new LinkedHashSet<>() : changedFields;
    }

    public SysUser getChanges()
    {
        return changes;
    }

    public void setChanges(SysUser changes)
    {
        this.changes = changes;
    }
}
