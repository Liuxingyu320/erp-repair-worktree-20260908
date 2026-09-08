package com.erp.system.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import com.erp.common.core.xss.Xss;
import com.erp.system.api.domain.SysUser;
import com.fasterxml.jackson.annotation.JsonAnySetter;

/**
 * Fixed account-management write contract. Personal data and credential state are
 * intentionally absent and must be written through their dedicated APIs.
 */
public class SysUserManageUpdateRequest
{
    private Long userId;
    private Long deptId;

    @NotBlank(message = "用户账号不能为空")
    @Size(max = 30, message = "用户账号长度不能超过30个字符")
    @Xss(message = "用户账号不能包含脚本字符")
    private String userName;

    @Size(max = 30, message = "用户昵称长度不能超过30个字符")
    @Xss(message = "用户昵称不能包含脚本字符")
    private String nickName;

    private String status;

    @Size(max = 500, message = "备注长度不能超过500个字符")
    private String remark;

    private Long[] roleIds;
    private Long[] postIds;

    public SysUser toSysUser()
    {
        SysUser user = new SysUser();
        user.setUserId(userId);
        user.setDeptId(deptId);
        user.setUserName(userName);
        user.setNickName(nickName);
        user.setStatus(status);
        user.setRemark(remark);
        user.setRoleIds(roleIds);
        user.setPostIds(postIds);
        return user;
    }

    @JsonAnySetter
    public void rejectUnknownField(String field, Object ignored)
    {
        throw new IllegalArgumentException("用户基础管理接口不接受字段: " + field);
    }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getDeptId() { return deptId; }
    public void setDeptId(Long deptId) { this.deptId = deptId; }
    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }
    public String getNickName() { return nickName; }
    public void setNickName(String nickName) { this.nickName = nickName; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public Long[] getRoleIds() { return roleIds; }
    public void setRoleIds(Long[] roleIds) { this.roleIds = roleIds; }
    public Long[] getPostIds() { return postIds; }
    public void setPostIds(Long[] postIds) { this.postIds = postIds; }
}
