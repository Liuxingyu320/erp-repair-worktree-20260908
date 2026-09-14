package com.erp.oa.attendance.leave;

import static org.assertj.core.api.Assertions.*;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import com.erp.oa.attendance.leave.AttendanceLeaveModels.*;
import com.erp.oa.attendance.leave.balance.AttendanceLeaveQuotaModels.PolicySnapshot;
class AttendanceLeaveAmountPolicyTest
{
    LeaveType type(String mode){LeaveType t=new LeaveType();t.unitMode=mode;t.stepMinutes=1;t.rowVersion=1L;return t;}
    LeaveRequest request(String days,int minutes){LeaveRequest r=new LeaveRequest();r.requestedDays=days==null?null:new BigDecimal(days);r.totalMinutes=minutes;return r;}
    PolicySnapshot policy(){PolicySnapshot p=new PolicySnapshot();p.minutesPerDay=new BigDecimal("420");return p;}
    @Test void explicitDaysAreIndependentOfNaturalIntervalMinutes(){assertThat(AttendanceLeaveAmountPolicy.units(request("1.5",14400),type("MIXED"),policy())).isEqualTo(630_000_000L);}
    @Test void existingMinuteRequestKeepsItsMinuteMeaning(){assertThat(AttendanceLeaveAmountPolicy.units(request(null,75),type("MINUTE"),policy())).isEqualTo(75_000_000L);}
    @Test void minuteModeDoesNotSilentlyAcceptDays(){assertThatThrownBy(()->AttendanceLeaveAmountPolicy.units(request("1",75),type("MINUTE"),policy())).hasMessageContaining("按分钟");}
    @Test void dayAndHalfDayRequireExplicitUserAmount(){for(String mode:new String[]{"DAY","HALF_DAY"})assertThatThrownBy(()->AttendanceLeaveAmountPolicy.units(request(null,420),type(mode),policy())).hasMessageContaining("明确填写申请天数");}
    @Test void noDefaultDailyMinutesIsInvented(){PolicySnapshot p=policy();p.minutesPerDay=null;assertThatThrownBy(()->AttendanceLeaveAmountPolicy.units(request("1",420),type("DAY"),p)).hasMessageContaining("每日分钟换算未配置");}
    @Test void dayModeRequiresWholeDaysAndHalfModeRequiresHalfSteps(){assertThatThrownBy(()->AttendanceLeaveAmountPolicy.units(request("0.5",420),type("DAY"),policy())).hasMessageContaining("整天");assertThatThrownBy(()->AttendanceLeaveAmountPolicy.units(request("0.25",420),type("HALF_DAY"),policy())).hasMessageContaining("半天");assertThat(AttendanceLeaveAmountPolicy.units(request("0.5",600),type("HALF_DAY"),policy())).isEqualTo(210_000_000L);}
    @Test void configuredMinuteMinimumMaximumAndStepApplyToConfirmedDays(){LeaveType t=type("MIXED");t.minMinutes=210;t.maxMinutesPerRequest=420;t.stepMinutes=30;assertThatThrownBy(()->AttendanceLeaveAmountPolicy.units(request("0.25",600),t,policy())).hasMessageContaining("最低");assertThatThrownBy(()->AttendanceLeaveAmountPolicy.units(request("1.5",600),t,policy())).hasMessageContaining("上限");assertThatThrownBy(()->AttendanceLeaveAmountPolicy.units(request("0.6",600),t,policy())).hasMessageContaining("步长");}
    @Test void negativeExcessiveAndOverpreciseDaysAreRejected(){for(String amount:new String[]{"0","-1","367","0.1234567"})assertThatThrownBy(()->AttendanceLeaveAmountPolicy.units(request(amount,600),type("MIXED"),policy())).hasMessageContaining("申请天数必须");}
    @Test void changedRuleAndDayConversionCannotReuseOldPolicySnapshot(){PolicySnapshot a=policy(),b=policy();a.ruleId=1L;b.ruleId=2L;assertThatThrownBy(()->AttendanceLeaveAmountPolicy.requireSame(a,b)).hasMessageContaining("政策或单位已变化");b.ruleId=1L;b.minutesPerDay=new BigDecimal("480");assertThatThrownBy(()->AttendanceLeaveAmountPolicy.requireSame(a,b)).hasMessageContaining("政策或单位已变化");}
    @Test void equalDecimalScalesHaveOneFingerprintAndFirstReadCanMatchAutomatically(){PolicySnapshot a=policy(),b=policy();b.minutesPerDay=new BigDecimal("420.000000");assertThat(AttendanceLeaveAmountPolicy.fingerprint(a)).isEqualTo(AttendanceLeaveAmountPolicy.fingerprint(b));assertThatCode(()->AttendanceLeaveAmountPolicy.requireSame(null,b)).doesNotThrowAnyException();}

    @org.springframework.web.bind.annotation.RestController
    static class SnapshotEndpoint {
        @org.springframework.web.bind.annotation.GetMapping("/synthetic-quota")
        public LeaveRequest value(){LeaveRequest r=new LeaveRequest();r.quotaUnits=9007199254740993L;r.quotaPolicySnapshot=new PolicySnapshot();r.quotaPolicySnapshot.leaveTypeId=9007199254740993L;r.quotaPolicySnapshot.leaveTypeVersion=9007199254740995L;r.quotaPolicySnapshot.ruleId=9007199254740997L;r.quotaPolicySnapshot.mappingId=9007199254740999L;r.quotaPolicySnapshot.mappingVersion=9007199254741001L;r.quotaPolicySnapshot.legalEntityId=9007199254741003L;return r;}
    }
    @Test void realSpringMvcKeepsQuotaAndPolicyLongsAsExactStrings()throws Exception{
        var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(new SnapshotEndpoint()).build();
        String response=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/synthetic-quota")).andReturn().getResponse().getContentAsString();
        var tree=new com.fasterxml.jackson.databind.ObjectMapper().readTree(response);
        assertThat(tree.get("quotaUnits").isTextual()).isTrue();assertThat(tree.get("quotaUnits").asText()).isEqualTo("9007199254740993");
        var policy=tree.get("quotaPolicySnapshot");for(String field:java.util.List.of("leaveTypeId","leaveTypeVersion","ruleId","mappingId","mappingVersion","legalEntityId"))assertThat(policy.get(field).isTextual()).as(field).isTrue();
        assertThat(policy.get("legalEntityId").asText()).isEqualTo("9007199254741003");
    }

    @Test void realPreviewControllerBindsExactRouteIdentityAndDeclaresSelfPermission()throws Exception{
        AttendanceLeaveService service=org.mockito.Mockito.mock(AttendanceLeaveService.class);AttendanceLeaveController controller=new AttendanceLeaveController(service);
        var scope=org.mockito.Mockito.mock(com.erp.oa.mapper.OaDeptScopeMapper.class);org.mockito.Mockito.when(scope.countActiveStoreDept(10L)).thenReturn(1);org.springframework.test.util.ReflectionTestUtils.setField(controller,"deptScopeMapper",scope);
        var response=new com.erp.oa.attendance.leave.balance.AttendanceLeaveQuotaModels.PolicyPreview();response.leaveRequestId=9007199254740993L;response.rowVersion=9007199254740995L;response.quotaUnits=420000000L;response.quotaPolicySnapshot=policy();response.quotaPolicySnapshot.leaveTypeId=9007199254740997L;
        org.mockito.Mockito.when(service.previewPolicy(org.mockito.ArgumentMatchers.eq(9007199254740993L),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.eq(10L))).thenReturn(response);
        var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(controller).build();
        String body="{\"leaveRequestId\":\"11\",\"rowVersion\":\"9007199254740995\",\"leaveTypeId\":1,\"startTime\":\"2026-09-13T09:00:00\",\"endTime\":\"2026-09-13T17:00:00\",\"requestedDays\":1,\"reason\":\"synthetic preview\"}";
        var result=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/attendance-v2/leave/9007199254740993/policy-preview").header("Dept-NumId","10").contentType("application/json").content(body)).andReturn();assertThat(result.getResponse().getStatus()).isEqualTo(200);
        var tree=new com.fasterxml.jackson.databind.ObjectMapper().readTree(result.getResponse().getContentAsString()).get("data");assertThat(tree.get("leaveRequestId").asText()).isEqualTo("9007199254740993");assertThat(tree.get("rowVersion").isTextual()).isTrue();assertThat(tree.get("quotaPolicySnapshot").get("leaveTypeId").isTextual()).isTrue();
        var captured=org.mockito.ArgumentCaptor.forClass(AttendanceLeaveRequests.SaveDraft.class);org.mockito.Mockito.verify(service).previewPolicy(org.mockito.ArgumentMatchers.eq(9007199254740993L),captured.capture(),org.mockito.ArgumentMatchers.eq(10L));assertThat(captured.getValue().leaveRequestId).isEqualTo(9007199254740993L);assertThat(captured.getValue().rowVersion).isEqualTo(9007199254740995L);assertThat(captured.getValue().requestedDays).isEqualByComparingTo("1");
        var method=AttendanceLeaveController.class.getMethod("previewPolicy",Long.class,AttendanceLeaveRequests.SaveDraft.class,jakarta.servlet.http.HttpServletRequest.class);assertThat(method.getAnnotation(com.erp.common.security.annotation.RequiresPermissions.class).value()).containsExactly("oa:attendance:leave:self");
    }
    @Test void realPreviewControllerRejectsInvalidDraftBeforeCallingService()throws Exception{
        AttendanceLeaveService service=org.mockito.Mockito.mock(AttendanceLeaveService.class);var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(new AttendanceLeaveController(service)).build();
        var result=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/attendance-v2/leave/10/policy-preview").contentType("application/json").content("{}")).andReturn();assertThat(result.getResponse().getStatus()).isEqualTo(400);org.mockito.Mockito.verifyNoInteractions(service);
    }
}
