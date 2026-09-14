package com.erp.oa.attendance.leave.balance;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.bind.annotation.GetMapping;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.security.annotation.*;
import com.erp.common.security.shop.ShopScopeService;
import com.erp.oa.mapper.OaDeptScopeMapper;
import com.erp.oa.service.BusinessFeatureGate;
import com.fasterxml.jackson.databind.ObjectMapper;

class AttendanceLeaveBalanceOptionsTest
{
    final AttendanceLeaveBalanceOptionsMapper mapper=mock(AttendanceLeaveBalanceOptionsMapper.class);
    final AttendanceLeaveBalanceAccess access=mock(AttendanceLeaveBalanceAccess.class);
    final BusinessFeatureGate gate=mock(BusinessFeatureGate.class);
    final ShopScopeService shops=mock(ShopScopeService.class);
    final AttendanceLeaveBalanceOptionsController controller=new AttendanceLeaveBalanceOptionsController(mapper,access,shops,gate);
    final MockHttpServletRequest request=new MockHttpServletRequest();
    @BeforeEach void setup(){request.addHeader("Dept-NumId","10");when(shops.resolveRequiredShopDept(any())).thenAnswer(i->i.getArgument(0));var scope=mock(OaDeptScopeMapper.class);when(scope.countActiveStoreDept(10L)).thenReturn(1);ReflectionTestUtils.setField(controller,"deptScopeMapper",scope);}
    @Test void everyRouteIsReadOnlyAndUsesOnlySpecifiedBalancePermissions() throws Exception
    {
        for(var method:AttendanceLeaveBalanceOptionsController.class.getDeclaredMethods())if(method.isAnnotationPresent(GetMapping.class)){
            var permission=method.getAnnotation(RequiresPermissions.class);assertThat(permission).isNotNull();assertThat(permission.value()).allMatch(p->p.startsWith(AttendanceLeaveBalanceAccess.PREFIX));
        }
        var types=AttendanceLeaveBalanceOptionsController.class.getMethod("types").getAnnotation(RequiresPermissions.class);
        assertThat(types.logical()).isEqualTo(Logical.OR);assertThat(types.value()).containsExactly(AttendanceLeaveBalanceAccess.PREFIX+"self",AttendanceLeaveBalanceAccess.PREFIX+"read",AttendanceLeaveBalanceAccess.PREFIX+"adjust",AttendanceLeaveBalanceAccess.PREFIX+"rule",AttendanceLeaveBalanceAccess.PREFIX+"convert");
        assertThat(AttendanceLeaveBalanceOptionsController.class.getAnnotation(org.springframework.transaction.annotation.Transactional.class).readOnly()).isTrue();
    }
    @Test void eachPermittedRoleCanReadTypesWithoutGivingOtherEmployeePermissions()
    {
        for(String allowed:List.of("self","read","adjust","rule","convert")){
            doAnswer(i->{if(!allowed.equals(i.getArgument(0)))throw new ServiceException("denied",403);return null;}).when(access).require(anyString());
            controller.types();
        }
        verify(mapper,times(5)).selectTypes();
        doThrow(new ServiceException("denied",403)).when(access).require(anyString());
        assertThatThrownBy(controller::types).hasMessageContaining("denied");verifyNoMoreInteractions(mapper);
    }
    @Test void featureAndScopeFailuresNeverReachCandidateSql()
    {
        doThrow(new ServiceException("disabled")).when(gate).requireEnabled(anyString());assertThatThrownBy(controller::types).hasMessageContaining("disabled");verifyNoInteractions(mapper);
        reset(gate);doThrow(new ServiceException("scope",403)).when(access).department(30L);
        assertThatThrownBy(()->controller.companies(30L)).hasMessageContaining("scope");assertThatThrownBy(()->controller.employees(30L,null,1,20)).hasMessageContaining("scope");verifyNoInteractions(mapper);
    }
    @Test void employeePaginationIsBoundedAndKeywordsRemainLiteral()
    {
        when(mapper.countEmployees(10L,"%_\\")).thenReturn(45);
        var response=controller.employees(10L," %_\\ ",2,20);var data=(AttendanceLeaveBalanceOptionsController.OptionsPage)response.get("data");assertThat(data.total()).isEqualTo(45);
        verify(mapper).selectEmployees(10L,"%_\\",20L,20);
        clearInvocations(mapper);
        for(int size:new int[]{0,-1,101})assertThatThrownBy(()->controller.employees(10L,null,1,size)).hasMessageContaining("每页");
        assertThatThrownBy(()->controller.employees(10L,null,0,20)).hasMessageContaining("页码");
        assertThatThrownBy(()->controller.employees(10L,"x".repeat(65),1,20)).hasMessageContaining("64");verifyNoInteractions(mapper);
    }
    @Test void hugePageCannotOverflowIntoFirstPage()
    {
        when(mapper.countEmployees(10L,null)).thenReturn(20);var data=(AttendanceLeaveBalanceOptionsController.OptionsPage)controller.employees(10L,null,Integer.MAX_VALUE,100).get("data");
        assertThat(data.rows()).isEmpty();verify(mapper,never()).selectEmployees(any(),any(),anyLong(),anyInt());
    }
    @Test void overtimeRejectsDifferentOrMissingCurrentStoreBeforeCandidateSql()
    {
        assertThatThrownBy(()->controller.overtimeSources(20L,day(1),day(2),null,1,20,request)).hasMessageContaining("当前门店");
        request.removeHeader("Dept-NumId");when(shops.resolveRequiredShopDept(null)).thenThrow(new ServiceException("请选择门店"));
        assertThatThrownBy(()->controller.overtimeSources(10L,day(1),day(2),null,1,20,request)).hasMessageContaining("门店");verifyNoInteractions(mapper);
    }
    @Test void overtimeAcceptsExactly31InclusiveDaysAndRejectsBadRanges()
    {
        controller.overtimeSources(10L,day(1),LocalDate.of(2026,10,1),null,1,20,request);verify(mapper).countOvertimeSources(10L,day(1),LocalDate.of(2026,10,1),null);clearInvocations(mapper);
        assertThatThrownBy(()->controller.overtimeSources(10L,day(1),LocalDate.of(2026,10,2),null,1,20,request)).hasMessageContaining("31天");
        assertThatThrownBy(()->controller.overtimeSources(10L,day(2),day(1),null,1,20,request)).hasMessageContaining("31天");verifyNoInteractions(mapper);
    }
    @Test void minimalDtosHaveStringIdsAndNumericPageTotals() throws Exception
    {
        var type=new AttendanceLeaveBalanceOptionsMapper.TypeOption();type.leaveTypeId=String.valueOf(Long.MAX_VALUE);type.balanceRequired=true;
        var source=new AttendanceLeaveBalanceOptionsMapper.SourceOption();source.dayResultId=String.valueOf(Long.MAX_VALUE);source.rowVersion=source.dayResultId;
        var json=new ObjectMapper();var tree=json.readTree(json.writeValueAsString(new AttendanceLeaveBalanceOptionsController.OptionsPage(List.of(source),1)));
        assertThat(tree.get("total").isInt()).isTrue();assertThat(tree.get("rows").get(0).get("dayResultId").asText()).isEqualTo(String.valueOf(Long.MAX_VALUE));
        assertThat(tree.get("rows").get(0).get("rowVersion").isTextual()).isTrue();assertThat(json.readTree(json.writeValueAsString(type)).get("leaveTypeId").isTextual()).isTrue();
        assertThat(Arrays.stream(AttendanceLeaveBalanceOptionsMapper.EmployeeOption.class.getFields()).map(f->f.getName())).containsExactlyInAnyOrder("userId","userName","account","deptName");
    }
    static LocalDate day(int day){return LocalDate.of(2026,9,day);}
}
