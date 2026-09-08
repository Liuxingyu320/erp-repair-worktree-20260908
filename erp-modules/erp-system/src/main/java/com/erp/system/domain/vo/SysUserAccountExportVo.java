package com.erp.system.domain.vo;

import java.util.Date;
import com.erp.common.core.annotation.Excel;
import com.erp.common.core.annotation.Excel.ColumnType;
import com.erp.system.api.domain.SysUser;
import com.erp.system.api.domain.SysUserProfile;

/**
 * 用户管理安全导出台账。
 *
 * <p>仅包含账号治理所需字段；证件、银行卡、地址、紧急联系人等员工档案
 * 只能通过 HR 专用导出入口获取。</p>
 */
public class SysUserAccountExportVo
{
    @Excel(name = "用户编号", cellType = ColumnType.NUMERIC)
    private Long userId;

    @Excel(name = "用户账号")
    private String userName;

    @Excel(name = "姓名")
    private String nickName;

    @Excel(name = "工号")
    private String employeeNo;

    @Excel(name = "部门")
    private String deptName;

    @Excel(name = "岗位")
    private String postNames;

    @Excel(name = "手机号", cellType = ColumnType.TEXT)
    private String phonenumber;

    @Excel(name = "账号状态", readConverterExp = "0=正常,1=停用")
    private String status;

    @Excel(name = "角色数", cellType = ColumnType.NUMERIC)
    private Integer roleCount;

    @Excel(name = "管理范围数", cellType = ColumnType.NUMERIC)
    private Integer shopScopeCount;

    @Excel(name = "管理范围")
    private String shopScopeNames;

    @Excel(name = "配置状态", readConverterExp = "missingRole=未分配角色,missingShopScope=未授权管理范围,complete=已完成")
    private String setupStatus;

    @Excel(name = "最后登录时间", width = 30, dateFormat = "yyyy-MM-dd HH:mm:ss")
    private Date loginDate;

    @Excel(name = "创建时间", width = 30, dateFormat = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    public static SysUserAccountExportVo from(SysUser user)
    {
        SysUserAccountExportVo row = new SysUserAccountExportVo();
        row.userId = user.getUserId();
        row.userName = user.getUserName();
        row.nickName = user.getNickName();
        SysUserProfile profile = user.getProfile();
        row.employeeNo = profile == null ? null : profile.getEmployeeNo();
        row.deptName = user.getDept() == null ? null : user.getDept().getDeptName();
        row.postNames = user.getPostNames();
        row.phonenumber = user.getPhonenumber();
        row.status = user.getStatus();
        row.roleCount = user.getRoleCount();
        row.shopScopeCount = user.getShopScopeCount();
        row.shopScopeNames = user.getShopScopeNames();
        row.setupStatus = user.getSetupStatus();
        row.loginDate = user.getLoginDate();
        row.createTime = user.getCreateTime();
        return row;
    }

    public Long getUserId() { return userId; }
    public String getUserName() { return userName; }
    public String getNickName() { return nickName; }
    public String getEmployeeNo() { return employeeNo; }
    public String getDeptName() { return deptName; }
    public String getPostNames() { return postNames; }
    public String getPhonenumber() { return phonenumber; }
    public String getStatus() { return status; }
    public Integer getRoleCount() { return roleCount; }
    public Integer getShopScopeCount() { return shopScopeCount; }
    public String getShopScopeNames() { return shopScopeNames; }
    public String getSetupStatus() { return setupStatus; }
    public Date getLoginDate() { return loginDate; }
    public Date getCreateTime() { return createTime; }
}
