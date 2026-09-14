package com.erp.system.service.impl;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.test.util.ReflectionTestUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.security.auth.AuthUtil;
import com.erp.oa.api.domain.HrEmployeeSigningSnapshot;
import com.erp.oa.api.domain.HrRenewalGuard;
import com.erp.system.domain.SysHrLifecycleAction;
import com.erp.system.domain.dto.HrRegularizationRequest;
import com.erp.system.domain.dto.HrRenewalDecisionRequest;
import com.erp.system.domain.vo.HrEmployeeLifecycleContextVo;
import com.erp.system.mapper.*;
import com.erp.system.service.ISysUserShopService;

class HrEmployeeLifecycleEntryTest
{
    private HrRegularizationLifecycleTest fixture;
    private HrLifecycleServiceImpl service;
    private SysUserProfileMapper profiles;
    private SysHrLifecycleActionMapper actions;
    private SysHrRenewalGuardMapper guards;
    private HrSalarySourceService salaries;
    private ObjectMapper json;

    @BeforeEach void setup()
    {
        fixture = new HrRegularizationLifecycleTest(); fixture.setUp();
        ReflectionTestUtils.invokeMethod(fixture, "stubFreshRegularization");
        service = field(fixture, "service"); profiles = field(fixture, "profileMapper");
        actions = field(fixture, "actionMapper"); guards = field(service, "renewalGuardMapper");
        salaries = field(service, "salarySources"); json = field(fixture, "objectMapper");
        HrEmployeeSigningSnapshot before = ReflectionTestUtils.invokeMethod(fixture, "probationSnapshot");
        before.setAccountStatus("0");
        when(profiles.selectSigningSnapshotByUserIdForUpdate(9L)).thenReturn(before);
        when(profiles.updateRegularizationDateOnly(eq(9L), any(), any())).thenReturn(1);
    }
    @SuppressWarnings("unchecked") private <T> T field(Object target, String name) { return (T) ReflectionTestUtils.getField(target, name); }
    private HrRegularizationRequest simple()
    {
        HrRegularizationRequest request = new HrRegularizationRequest(); request.setRequestId("regular-date-1");
        request.setPreservePositionSalary(true); request.setActualRegularizationDate(LocalDate.of(2026,7,10)); return request;
    }
    private Long regularize(HrRegularizationRequest request) { return service.confirmRegularization(9L, request,88L,"配置HR",false,"ip","test"); }

    @Test void dateOnlyPreservesPayrollPostAssociationsAndSource()
    {
        assertThat(regularize(simple())).isEqualTo(801L);
        verify(profiles).updateRegularizationDateOnly(9L,LocalDate.of(2026,7,10),"配置HR");
        verify(profiles, never()).updateRegularizationProfile(any(),any());
        verifyNoInteractions(salaries);
        SysUserPostMapper posts = field(fixture,"userPostMapper"); verifyNoInteractions(posts);
        SysPostMapper canonical = field(fixture,"postMapper"); verify(canonical, never()).selectPostByIdForUpdate(any());
        ArgumentCaptor<SysHrLifecycleAction> action=ArgumentCaptor.forClass(SysHrLifecycleAction.class);
        verify(actions).insertAction(action.capture());
        assertThat(action.getValue().getRiskCodesJson()).isEqualTo("[]");
        HrEmployeeSigningSnapshot after=ReflectionTestUtils.invokeMethod(service,"readRegularizationSnapshot",action.getValue().getAfterSnapshotJson(),"after");
        assertThat(after.getEmployeeStatus()).isEqualTo("正式"); assertThat(after.getPostId()).isEqualTo(401L);
        assertThat(after.getSalaryTotal()).isEqualByComparingTo("8000");
        SysHrSignEventOutboxMapper outbox=field(fixture,"outboxMapper"); verify(outbox).insertOutbox(any());
    }
    @Test void dateOnlyKeepsUnknownSalaryNullInsteadOfInventingZero()
    {
        HrEmployeeSigningSnapshot before=profiles.selectSigningSnapshotByUserIdForUpdate(9L);
        before.setBaseSalary(null); before.setSalaryTotal(null); before.setSalaryVersion(null);
        regularize(simple());
        ArgumentCaptor<SysHrLifecycleAction> action=ArgumentCaptor.forClass(SysHrLifecycleAction.class); verify(actions).insertAction(action.capture());
        HrEmployeeSigningSnapshot after=ReflectionTestUtils.invokeMethod(service,"readRegularizationSnapshot",action.getValue().getAfterSnapshotJson(),"after");
        assertThat(after.getBaseSalary()).isNull(); assertThat(after.getSalaryTotal()).isNull(); verifyNoInteractions(salaries);
    }
    @Test void dateOnlyRejectsClientSalaryOrPositionValues()
    {
        HrRegularizationRequest money=simple(); money.setBaseSalary(BigDecimal.ZERO);
        assertThatThrownBy(()->regularize(money)).hasMessageContaining("仅填写实际日期");
        HrRegularizationRequest post=simple(); post.setPostId(402L);
        assertThatThrownBy(()->regularize(post)).hasMessageContaining("仅填写实际日期"); verify(actions,never()).insertAction(any());
    }
    @Test void legacyMissingAndMaskedPayrollDoesNotBecomeDateOnly()
    {
        HrRegularizationRequest request=simple(); request.setPreservePositionSalary(false);
        assertThatThrownBy(()->regularize(request)).hasMessageContaining("岗位ID");
        HrRegularizationRequest legacy=ReflectionTestUtils.invokeMethod(fixture,"validRequest"); legacy.setBaseSalary(null);
        assertThatThrownBy(()->regularize(legacy)).isInstanceOf(ServiceException.class); verify(actions,never()).insertAction(any());
    }
    @Test void dateOnlyRechecksDateStateAndCas()
    {
        HrRegularizationRequest future=simple(); future.setActualRegularizationDate(LocalDate.of(2026,7,13));
        assertThatThrownBy(()->regularize(future)).hasMessageContaining("不能晚于上海");
        when(profiles.updateRegularizationDateOnly(any(),any(),any())).thenReturn(0);
        assertThatThrownBy(()->regularize(simple())).hasMessageContaining("状态已变化");
    }
    @Test void dateOnlyRejectsDisabledAndOutOfScope()
    {
        profiles.selectSigningSnapshotByUserIdForUpdate(9L).setAccountStatus("1");
        assertThatThrownBy(()->regularize(simple())).hasMessageContaining("账号已停用");
        ISysUserShopService shops=field(fixture,"userShopService"); doThrow(new ServiceException("越界")).when(shops).checkUserShopScope(any(),any(),eq(false));
        assertThatThrownBy(()->regularize(simple())).hasMessageContaining("越界"); verify(actions,never()).insertAction(any());
    }
    @Test void actualSalaryChangeRequiresAdditionalPermission()
    {
        try (MockedStatic<AuthUtil> auth=mockStatic(AuthUtil.class)) {
            auth.when(()->AuthUtil.checkPermi("hr:employee:salary:edit")).thenThrow(new ServiceException("无调薪权限"));
            HrRegularizationRequest legacy=ReflectionTestUtils.invokeMethod(fixture,"validRequest");
            assertThatThrownBy(()->regularize(legacy)).hasMessageContaining("无调薪权限"); verify(actions,never()).insertAction(any());
        }
    }
    @Test void unchangedLegacySalaryDoesNotRequireExtraPermission()
    {
        HrRegularizationRequest request=ReflectionTestUtils.invokeMethod(fixture,"validRequest");
        request.setBaseSalary(new BigDecimal("5000"));request.setSalaryTotal(new BigDecimal("8000"));request.setSalaryVersion("2026-V1");
        try(MockedStatic<AuthUtil> auth=mockStatic(AuthUtil.class)) { regularize(request); auth.verifyNoInteractions(); }
        verifyNoInteractions(salaries);
    }
    @Test void simpleReplayMatchesImmutableBeforeAndRejectsChangedPayload()
    {
        regularize(simple()); ArgumentCaptor<SysHrLifecycleAction> action=ArgumentCaptor.forClass(SysHrLifecycleAction.class); verify(actions).insertAction(action.capture());
        when(actions.selectByRequestIdForUpdate("regular-date-1")).thenReturn(action.getValue());
        HrEmployeeSigningSnapshot after=ReflectionTestUtils.invokeMethod(service,"readRegularizationSnapshot",action.getValue().getAfterSnapshotJson(),"after");
        when(profiles.selectSigningSnapshotByUserIdForUpdate(9L)).thenReturn(after);
        assertThat(regularize(simple())).isEqualTo(801L); verify(actions,times(1)).insertAction(any());
        HrRegularizationRequest changed=simple(); changed.setActualRegularizationDate(LocalDate.of(2026,7,11));
        assertThatThrownBy(()->regularize(changed)).hasMessageContaining("payload不一致");
    }
    private HrEmployeeLifecycleContextVo context()
    {
        HrEmployeeLifecycleContextVo c=new HrEmployeeLifecycleContextVo(); c.userId="9";c.employeeName="员工";c.employeeStatus="试用";
        c.accountStatus="0";c.scopeDeptId=20L;c.postId="401";c.contractStartDate=LocalDate.of(2025,8,2);c.contractEndDate=LocalDate.of(2026,8,1);
        c.contractTypeCode="LABOR_CONTRACT";c.contractTermCode="FIXED_TERM";c.legalEntityId="300";c.legalEntityCode="LE-SH";c.legalEntityName="主体";c.renewalCount=2;
        when(actions.selectLifecycleContext(9L)).thenReturn(c);return c;
    }
    @Test void contextUsesHrAndShopScopeWithoutLocksOrWrites() throws Exception
    {
        context(); HrEmployeeLifecycleContextVo c=service.lifecycleContext(9L,"REGULARIZE",88L);assertThat(c.eligible).isTrue();
        String result=json.writeValueAsString(c); assertThat(result).doesNotContain("scopeDeptId","Salary","salary","Snapshot");
        assertThatThrownBy(()->service.lifecycleContext(9L,"REGULARIZE",89L)).hasMessageContaining("HR本人");
        verify(actions,never()).insertAction(any()); verify(guards,never()).insertIdle(any(),any());
        ISysUserShopService shops=field(fixture,"userShopService");verify(shops).checkUserShopScope(88L,20L,false);
    }
    @Test void renewalContextRequiresCurrentDecisionAndIdleGuard()
    {
        context();assertThat(service.lifecycleContext(9L,"RENEWAL",88L).blockedReason).contains("尚无");
        context();when(actions.selectRenewalCycleTypes(9L,"9:2026-08-01:2")).thenReturn(List.of("RENEWAL_DECISION"));
        assertThat(service.lifecycleContext(9L,"RENEWAL",88L).eligible).isTrue();
        context();HrRenewalGuard guard=new HrRenewalGuard();guard.setStatus("RESERVED");when(guards.selectCurrent(9L,"RENEWAL")).thenReturn(guard);
        assertThat(service.lifecycleContext(9L,"RENEWAL",88L).blockedReason).contains("未完成");
        context();when(guards.selectCurrent(9L,"RENEWAL")).thenReturn(null);when(actions.countUnfinishedRenewal(9L)).thenReturn(1);
        assertThat(service.lifecycleContext(9L,"RENEWAL",88L).blockedReason).contains("未完成");verify(guards,never()).insertIdle(any(),any());
    }
    @Test void renewalWriteRejectsStaleCycleAndChangedLegalEntity()
    {
        HrRenewalLifecycleTest renewal=new HrRenewalLifecycleTest();renewal.setUp();
        HrEmployeeSigningSnapshot before=ReflectionTestUtils.invokeMethod(renewal,"activeSnapshot");
        SysConfigMapper config=field(renewal,"configMapper");when(config.selectConfiguredSignHrUserId()).thenReturn(88L);
        SysUserProfileMapper profiles=field(renewal,"profileMapper");when(profiles.selectSigningSnapshotByUserIdForUpdate(9L)).thenReturn(before);
        HrRenewalDecisionRequest request=new HrRenewalDecisionRequest();request.setRequestId("renew-1");request.setDecision(HrRenewalDecisionRequest.Decision.RENEW);
        request.setContractStartDate(before.getContractEndDate().plusDays(1));request.setContractEndDate(before.getContractEndDate().plusYears(1));
        request.setContractTypeCode(before.getContractTypeCode());request.setContractTermCode(before.getContractTermCode());request.setLegalEntityId(before.getLegalEntityId());request.setLegalEntityCode(before.getLegalEntityCode());request.setLegalEntityName(before.getLegalEntityName());request.setExpectedCycleKey("9:2026-07-01:2");
        HrLifecycleServiceImpl service=field(renewal,"service");
        assertThatThrownBy(()->service.confirmRenewal(9L,request,88L,"HR",false,"ip","test")).hasMessageContaining("周期已变化");
        request.setExpectedCycleKey("9:"+before.getContractEndDate()+":"+before.getRenewalCount());request.setLegalEntityId(301L);
        assertThatThrownBy(()->service.confirmRenewal(9L,request,88L,"HR",false,"ip","test")).hasMessageContaining("法律主体已变化");
    }
    @Test void actualMapperDateOnlyUpdateAndHistoryStayNarrow() throws Exception
    {
        org.apache.ibatis.session.Configuration configuration=new org.apache.ibatis.session.Configuration();
        for(String name:List.of("SysUserProfileMapper","SysHrLifecycleActionMapper","SysHrRenewalGuardMapper")) {
            String resource="mapper/system/"+name+".xml";
            try(var stream=getClass().getClassLoader().getResourceAsStream(resource)) {
                new org.apache.ibatis.builder.xml.XMLMapperBuilder(stream,configuration,resource,configuration.getSqlFragments()).parse();
            }
        }
        String update=configuration.getMappedStatement("com.erp.system.mapper.SysUserProfileMapper.updateRegularizationDateOnly")
                .getBoundSql(Map.of("employeeId",9L,"effectiveDate",LocalDate.of(2026,7,10),"updateBy","HR")).getSql();
        assertThat(update).contains("employee_status = '正式'","employee_status = '试用'").doesNotContain("salary","post_id","position_names","job_grade");
        String history=configuration.getMappedStatement("com.erp.system.mapper.SysHrLifecycleActionMapper.selectLifecycleHistory")
                .getBoundSql(Map.of("employeeId",9L,"scenario","REGULARIZE")).getSql();
        assertThat(history).contains("employee_id = ?","REGULARIZATION_CONFIRMED","limit 20").doesNotContain("snapshot_json","risk_detail","salary");
    }
}
