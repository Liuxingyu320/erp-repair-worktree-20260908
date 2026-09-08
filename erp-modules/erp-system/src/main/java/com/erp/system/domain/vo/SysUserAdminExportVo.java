package com.erp.system.domain.vo;

import java.util.Date;
import com.erp.common.core.annotation.Excel;

public class SysUserAdminExportVo
{
    @Excel(name = "用户编号")
    private Long userId;
    @Excel(name = "登录账号（已脱敏）")
    private String userName;
    @Excel(name = "姓名")
    private String nickName;
    @Excel(name = "部门")
    private String deptName;
    @Excel(name = "岗位")
    private String postNames;
    @Excel(name = "账号状态", readConverterExp = "0=正常,1=停用")
    private String status;
    @Excel(name = "配置状态")
    private String setupStatus;
    @Excel(name = "创建时间", width = 22, dateFormat = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    public static SysUserAdminExportVo from(SysUserListVo source)
    {
        SysUserAdminExportVo target = new SysUserAdminExportVo();
        target.userId = source.getUserId();
        target.userName = source.getUserName();
        target.nickName = source.getNickName();
        target.deptName = source.getDept() == null ? null : source.getDept().getDeptName();
        target.postNames = source.getPostNames();
        target.status = source.getStatus();
        target.setupStatus = source.getSetupStatus();
        target.createTime = source.getCreateTime();
        return target;
    }

    public Long getUserId() { return userId; }
    public String getUserName() { return userName; }
    public String getNickName() { return nickName; }
    public String getDeptName() { return deptName; }
    public String getPostNames() { return postNames; }
    public String getStatus() { return status; }
    public String getSetupStatus() { return setupStatus; }
    public Date getCreateTime() { return createTime; }
}

