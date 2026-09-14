package com.erp.oa.attendance.leave.balance;

import com.erp.common.core.exception.ServiceException;
import com.erp.common.security.utils.SecurityUtils;
import com.erp.oa.attendance.leave.balance.AttendanceLeaveBalanceModels.EmployeeContext;
import org.springframework.stereotype.Component;

/** Checks the current employee department, never an arbitrary client region. */
@Component
public class AttendanceLeaveBalanceAccess
{
    public static final String PREFIX = "oa:attendance:leave:balance:";
    private final AttendanceLeaveBalanceMapper mapper;
    public AttendanceLeaveBalanceAccess(AttendanceLeaveBalanceMapper mapper) { this.mapper = mapper; }
    public Long actor() { return SecurityUtils.getUserId(); }
    public boolean admin() { return SecurityUtils.isAdmin(); }
    public void require(String action)
    {
        var login = SecurityUtils.getLoginUser();
        if (login == null || actor() == null || (!admin() && (login.getPermissions() == null
                || (!login.getPermissions().contains("*:*:*") && !login.getPermissions().contains(PREFIX + action)))))
            throw new ServiceException("没有假期额度操作权限", 403);
    }
    public void employee(EmployeeContext context, boolean self, String action)
    {
        require(action);
        if (context == null || context.userId == null) throw new ServiceException("员工资料不存在");
        if (self)
        {
            if (!context.userId.equals(actor())) throw new ServiceException("只能查询或计算本人额度", 403);
        }
        else department(context.deptId);
    }
    public void department(Long deptId)
    {
        if (deptId == null || (!admin() && mapper.countDeptPermission(actor(), deptId) <= 0))
            throw new ServiceException("员工或规则不在当前授权组织范围", 403);
    }
    public void owner(Long deptId, Long legalEntityId)
    {
        require("rule");
        department(deptId);
        if (legalEntityId == null || mapper.countDeptEntity(deptId, legalEntityId) <= 0)
            throw new ServiceException("法人主体与规则归属组织不匹配");
    }
}
