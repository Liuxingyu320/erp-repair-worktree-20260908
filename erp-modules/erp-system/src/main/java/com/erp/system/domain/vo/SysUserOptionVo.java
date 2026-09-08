package com.erp.system.domain.vo;

import com.erp.system.api.domain.SysUser;

/** 用户选择器安全选项；不包含手机号、证件、地址等敏感字段。 */
public class SysUserOptionVo
{
    private Long userId;
    private String userName;
    private String nickName;
    private Long deptId;
    private String deptName;
    private String postNames;
    private String status;

    public static SysUserOptionVo from(SysUser user)
    {
        if (user == null)
        {
            return null;
        }
        SysUserOptionVo option = new SysUserOptionVo();
        option.setUserId(user.getUserId());
        option.setUserName(user.getUserName());
        option.setNickName(user.getNickName());
        option.setDeptId(user.getDeptId());
        option.setDeptName(user.getDept() == null ? null : user.getDept().getDeptName());
        option.setPostNames(user.getPostNames());
        option.setStatus(user.getStatus());
        return option;
    }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }
    public String getNickName() { return nickName; }
    public void setNickName(String nickName) { this.nickName = nickName; }
    public Long getDeptId() { return deptId; }
    public void setDeptId(Long deptId) { this.deptId = deptId; }
    public String getDeptName() { return deptName; }
    public void setDeptName(String deptName) { this.deptName = deptName; }
    public String getPostNames() { return postNames; }
    public void setPostNames(String postNames) { this.postNames = postNames; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
