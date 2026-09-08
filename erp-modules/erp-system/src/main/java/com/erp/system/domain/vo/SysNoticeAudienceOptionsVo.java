package com.erp.system.domain.vo;

import java.util.Collections;
import java.util.List;

/**
 * 公告受众选择器选项。
 */
public class SysNoticeAudienceOptionsVo
{
    private List<SysNoticeAudienceTargetVo> departments = Collections.emptyList();
    private List<SysNoticeAudienceTargetVo> roles = Collections.emptyList();
    private List<SysNoticeAudienceTargetVo> users = Collections.emptyList();

    public List<SysNoticeAudienceTargetVo> getDepartments() { return departments; }
    public void setDepartments(List<SysNoticeAudienceTargetVo> departments) { this.departments = departments; }
    public List<SysNoticeAudienceTargetVo> getRoles() { return roles; }
    public void setRoles(List<SysNoticeAudienceTargetVo> roles) { this.roles = roles; }
    public List<SysNoticeAudienceTargetVo> getUsers() { return users; }
    public void setUsers(List<SysNoticeAudienceTargetVo> users) { this.users = users; }
}
