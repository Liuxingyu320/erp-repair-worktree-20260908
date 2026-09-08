package com.erp.system.domain.vo;

import com.erp.system.domain.dto.SysUserPiiUpdateRequest;

/** Stable full-PII response returned only by the dedicated protected endpoint. */
public class SysUserPiiDetailVo extends SysUserPiiUpdateRequest
{
    private Long userId;
    private String userName;
    private String nickName;

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }
    public String getNickName() { return nickName; }
    public void setNickName(String nickName) { this.nickName = nickName; }
}
