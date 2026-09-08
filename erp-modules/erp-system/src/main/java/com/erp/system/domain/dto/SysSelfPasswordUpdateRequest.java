package com.erp.system.domain.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Fixed, non-loggable request used when a signed-in user changes their password. */
public class SysSelfPasswordUpdateRequest
{
    @NotBlank(message = "当前密码不能为空")
    @Size(max = 20, message = "当前密码长度不能超过20个字符")
    private String oldPassword;

    @NotBlank(message = "新密码不能为空")
    @Size(min = 8, max = 20, message = "新密码长度必须在8到20个字符之间")
    private String newPassword;

    @JsonAnySetter
    public void rejectUnknownField(String field, Object ignored)
    {
        throw new IllegalArgumentException("本人改密接口不接受字段: " + field);
    }

    public String getOldPassword()
    {
        return oldPassword;
    }

    public void setOldPassword(String oldPassword)
    {
        this.oldPassword = oldPassword;
    }

    public String getNewPassword()
    {
        return newPassword;
    }

    public void setNewPassword(String newPassword)
    {
        this.newPassword = newPassword;
    }
}
