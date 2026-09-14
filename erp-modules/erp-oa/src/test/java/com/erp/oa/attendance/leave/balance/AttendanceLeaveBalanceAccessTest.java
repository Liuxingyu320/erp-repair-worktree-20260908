package com.erp.oa.attendance.leave.balance;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.util.Set;
import org.junit.jupiter.api.Test;
import com.erp.common.security.utils.SecurityUtils;
import com.erp.system.api.model.LoginUser;

class AttendanceLeaveBalanceAccessTest
{
    @Test void selfCannotReadAnotherEmployeeEvenWithSelfPermission() {
        var mapper=mock(AttendanceLeaveBalanceMapper.class);var access=new AttendanceLeaveBalanceAccess(mapper);LoginUser login=new LoginUser();login.setPermissions(Set.of(AttendanceLeaveBalanceAccess.PREFIX+"self"));
        try(var security=mockStatic(SecurityUtils.class)) {security.when(SecurityUtils::getLoginUser).thenReturn(login);security.when(SecurityUtils::getUserId).thenReturn(11L);
            access.employee(AttendanceLeaveBalanceCalculatorTest.employee(),true,"self");
            var other=AttendanceLeaveBalanceCalculatorTest.employee();other.userId=12L;assertThatThrownBy(()->access.employee(other,true,"self")).hasMessageContaining("本人");
        }verifyNoInteractions(mapper);
    }
    @Test void hrNeedsBothFunctionalPermissionAndCurrentOrganizationScope() {
        var mapper=mock(AttendanceLeaveBalanceMapper.class);var access=new AttendanceLeaveBalanceAccess(mapper);LoginUser login=new LoginUser();login.setPermissions(Set.of(AttendanceLeaveBalanceAccess.PREFIX+"read"));
        try(var security=mockStatic(SecurityUtils.class)) {security.when(SecurityUtils::getLoginUser).thenReturn(login);security.when(SecurityUtils::getUserId).thenReturn(99L);
            assertThatThrownBy(()->access.employee(AttendanceLeaveBalanceCalculatorTest.employee(),false,"read")).hasMessageContaining("授权组织");
            when(mapper.countDeptPermission(99L,10L)).thenReturn(1);access.employee(AttendanceLeaveBalanceCalculatorTest.employee(),false,"read");
            assertThatThrownBy(()->access.require("adjust")).hasMessageContaining("操作权限");
        }
    }
    @Test void emptyScopeNeverMeansAllEmployees() {
        var mapper=mock(AttendanceLeaveBalanceMapper.class);var access=new AttendanceLeaveBalanceAccess(mapper);
        try(var security=mockStatic(SecurityUtils.class)){security.when(SecurityUtils::getUserId).thenReturn(99L);assertThatThrownBy(()->access.department(null)).hasMessageContaining("范围");assertThatThrownBy(()->access.department(10L)).hasMessageContaining("范围");}
    }
    @Test void ruleOwnerMustBelongToLegalEntityAndAuthorizedOrganization() {
        var mapper=mock(AttendanceLeaveBalanceMapper.class);var access=new AttendanceLeaveBalanceAccess(mapper);LoginUser login=new LoginUser();login.setPermissions(Set.of(AttendanceLeaveBalanceAccess.PREFIX+"rule"));
        try(var security=mockStatic(SecurityUtils.class)){security.when(SecurityUtils::getLoginUser).thenReturn(login);security.when(SecurityUtils::getUserId).thenReturn(99L);when(mapper.countDeptPermission(99L,10L)).thenReturn(1);
            assertThatThrownBy(()->access.owner(10L,20L)).hasMessageContaining("主体");when(mapper.countDeptEntity(10L,20L)).thenReturn(1);access.owner(10L,20L);
        }
    }
    @Test void unknownLoginCannotUseInternalScheduledBehaviorThroughHttpAccess() {
        var access=new AttendanceLeaveBalanceAccess(mock(AttendanceLeaveBalanceMapper.class));try(var security=mockStatic(SecurityUtils.class)){assertThatThrownBy(()->access.require("self")).hasMessageContaining("权限");}
    }
}
