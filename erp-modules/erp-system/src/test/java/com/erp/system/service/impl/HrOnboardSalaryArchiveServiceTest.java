package com.erp.system.service.impl;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.erp.common.core.context.SecurityContextHolder;
import com.erp.system.api.domain.*;
import com.erp.system.domain.HrOnboardSalarySource;
import com.erp.system.domain.dto.HrOnboardSalaryArchiveRequest;
import com.erp.system.mapper.*;

class HrOnboardSalaryArchiveServiceTest
{
    private final HrOnboardSalaryArchiveMapper imports = mock(HrOnboardSalaryArchiveMapper.class);
    private final HrSalarySourceMapper evidence = mock(HrSalarySourceMapper.class);
    private final SysUserProfileMapper profiles = mock(SysUserProfileMapper.class);
    private final HrEmployeeAccessService access = mock(HrEmployeeAccessService.class);
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    private final HrSalarySourceService sources = new HrSalarySourceService(evidence, mock(SysConfigMapper.class), profiles, json);
    private final HrOnboardSalaryArchiveService service = new HrOnboardSalaryArchiveService(imports, profiles, access, json, sources);
    private final HrOnboardSalarySource row = new HrOnboardSalarySource();
    private final SysUserProfile profile = new SysUserProfile();
    private final SysUser user = new SysUser();
    private final AtomicReference<EmployeeSalarySource> current = new AtomicReference<>();
    private final Map<String, EmployeeSalarySource> commands = new LinkedHashMap<>();
    private static final String SALARY = "{\"baseSalary\":3000,\"postSalary\":1000,\"fieldAllowance\":500,\"performanceSalary\":500,\"salaryTotal\":5000,";

    @BeforeEach void setup()
    {
        SecurityContextHolder.setUserId("9"); SecurityContextHolder.setUserName("test_hr");
        row.rowId=10L; row.batchId=3L; row.employeeId=11L; row.version=2L; row.ownerUserId=9L;
        row.matchType="ID_NUMBER"; row.status="READY_TO_GENERATE"; row.fileSha256="a".repeat(64);
        row.snapshotJson=SALARY+"\"phone\":\"13800000011\",\"idNumber\":\"TEST_ID_11\"}";
        user.setUserId(11L); user.setNickName("测试员工"); user.setPhonenumber("13800000011");
        profile.setUserId(11L); profile.setIdNumber("TEST_ID_11"); profile.setSalaryTotal(new BigDecimal("9000"));
        when(imports.readSource(3L,10L)).thenReturn(row); when(imports.lockSource(3L,10L)).thenReturn(row);
        when(access.findActiveScoped(any())).thenReturn(user); when(access.lockActiveScoped(any())).thenReturn(user);
        when(profiles.selectSalaryProfileForUpdate(11L)).thenReturn(profile);
        when(profiles.selectUserProfileByUserId(11L)).thenReturn(profile);
        when(evidence.historicalForRow(3L,10L)).thenAnswer(call -> commands.values().stream()
                .filter(value -> "HISTORICAL_CONTRACT_EXCEL".equals(value.sourceType)).reduce((left,right) -> right).orElse(null));
        when(evidence.current(11L)).thenAnswer(call -> current.get());
        when(evidence.currentLocked(11L)).thenAnswer(call -> current.get());
        when(evidence.command(anyString())).thenAnswer(call -> commands.get(call.getArgument(0, String.class)));
        when(evidence.commandLocked(anyString())).thenAnswer(call -> commands.get(call.getArgument(0, String.class)));
        when(evidence.insert(any())).thenAnswer(call -> { var source=call.getArgument(0,EmployeeSalarySource.class); commands.put(source.commandId,source); return 1; });
        when(evidence.pointTo(any())).thenAnswer(call -> { current.set(call.getArgument(0)); return 1; });
        when(imports.updateSalary(eq(11L),any(),any())).thenAnswer(call -> {
            var salary=call.getArgument(1,EmployeeSalaryValues.class);
            profile.setBaseSalary(salary.getBaseSalary()); profile.setPostSalary(salary.getPostSalary());
            profile.setFieldAllowance(salary.getFieldAllowance()); profile.setPerformanceSalary(salary.getPerformanceSalary());
            profile.setSalaryTotal(salary.getSalaryTotal()); return 1;
        });
        when(imports.insertAudit(any(),any(),any(),any(),any())).thenReturn(1);
    }
    @AfterEach void clear() { SecurityContextHolder.remove(); }
    private HrOnboardSalaryArchiveRequest previewRequest() { return new HrOnboardSalaryArchiveRequest(3L,List.of(new HrOnboardSalaryArchiveRequest.Row(10L,2L))); }
    private HrOnboardSalaryArchiveRequest confirmation(String requestId)
    {
        var view=service.preview(previewRequest()).get(0);
        return new HrOnboardSalaryArchiveRequest(3L,List.of(new HrOnboardSalaryArchiveRequest.Row(10L,2L,view.expectedSourceId(),view.expectedProfileHash())),
                requestId,LocalDate.now(ZoneId.of("Asia/Shanghai")),"已核对合同工资",true);
    }
    @Test void previewDoesNotWriteAndShowsCurrentAndContractAmounts()
    {
        var preview=service.preview(previewRequest()).get(0);
        assertThat(preview.current().getSalaryTotal()).isEqualByComparingTo("9000");
        assertThat(preview.proposed().getSalaryTotal()).isEqualByComparingTo("5000");
        assertThat(preview.confirmed()).isFalse();
        verify(imports,never()).updateSalary(any(),any(),any()); verify(evidence,never()).insert(any());
    }
    @Test void explicitConfirmationWritesServerAmountsAndDurablyReplaysEvenAfterGenerationChangesRowVersion()
    {
        var request=confirmation("test_salary_1");
        assertThat(service.status(request).status()).isEqualTo("NOT_FOUND");
        assertThat(service.archive(request).updated()).isEqualTo(1);
        assertThat(current.get().getSalaryTotal()).isEqualByComparingTo("5000");
        assertThat(current.get().verified).isTrue(); assertThat(current.get().previousSourceId).isNull();
        row.version=5L; row.status="GENERATED";
        assertThat(service.status(request).status()).isEqualTo("CONFIRMED");
        assertThat(service.archive(request).reused()).isEqualTo(1);
        verify(imports,times(1)).updateSalary(any(),any(),any()); verify(evidence,times(1)).insert(any());
    }
    @Test void bareOldArchiveRequestCannotConfirmWages()
    {
        assertThatThrownBy(() -> service.archive(previewRequest())).hasMessageContaining("明确确认");
        verify(imports,never()).updateSalary(any(),any(),any());
    }
    @Test void changedProfileBetweenPreviewAndConfirmCannotBeOverwritten()
    {
        var request=confirmation("test_salary_2"); profile.setSalaryTotal(new BigDecimal("10000"));
        assertThatThrownBy(() -> service.archive(request)).hasMessageContaining("工资已变化");
        verify(imports,never()).updateSalary(any(),any(),any());
    }
    @Test void changedSourcePointerIsRejectedEvenWhenAmountsAreUnchanged()
    {
        var request=confirmation("test_salary_3"); var newer=new EmployeeSalarySource(); newer.sourceId="formal-transfer"; current.set(newer);
        assertThatThrownBy(() -> service.archive(request)).hasMessageContaining("工资已变化");
    }
    @Test void requestNumberCannotBeReusedForDifferentReasonOrSelection()
    {
        var request=confirmation("test_salary_4"); service.archive(request);
        var altered=new HrOnboardSalaryArchiveRequest(request.batchId(),request.rows(),request.requestId(),request.effectiveDate(),"不同确认依据",true);
        assertThatThrownBy(() -> service.archive(altered)).hasMessageContaining("请求号已用于其他内容");
        verify(evidence,times(1)).insert(any());
    }
    @Test void reConfirmingAfterFormalChangeRequiresFreshPreviewAndLinksPreviousSource()
    {
        service.archive(confirmation("test_salary_5"));
        var first=current.get(); first.sourceType="TRANSFER";
        var request=confirmation("test_salary_6");
        service.archive(request);
        assertThat(current.get().previousSourceId).isEqualTo(first.sourceId);
    }
    @Test void invalidSalaryAndIdentityNeverWrite()
    {
        var request=confirmation("test_salary_7"); row.snapshotJson=row.snapshotJson.replace("5000","6000");
        assertThatThrownBy(() -> service.archive(request)).hasMessageContaining("合计不一致");
        row.snapshotJson=SALARY+"\"phone\":\"13800000099\",\"idNumber\":\"TEST_ID_11\"}";
        assertThatThrownBy(() -> service.archive(request)).hasMessageContaining("身份信息");
        verify(imports,never()).updateSalary(any(),any(),any());
    }
    @Test void oldApplicableDateCannotOverwriteLaterFormalSalary()
    {
        service.archive(confirmation("test_salary_8")); var request=confirmation("test_salary_9");
        var older=new HrOnboardSalaryArchiveRequest(request.batchId(),request.rows(),request.requestId(),request.effectiveDate().minusDays(1),request.reason(),true);
        assertThatThrownBy(() -> service.archive(older)).hasMessageContaining("历史补签不能覆盖");
    }
    @Test void unauthorizedScopeOrOwnerCannotReadPreview()
    {
        row.ownerUserId=99L;
        assertThatThrownBy(() -> service.preview(previewRequest())).hasMessageContaining("经办人");
        verify(imports,never()).updateSalary(any(),any(),any());
    }
    private void historicalBeforeConfirmation()
    {
        row.historicalSupplement = false;
        row.snapshotJson = row.snapshotJson.replace("\"phone\":", "\"contractStartDate\":\"2020-01-01\",\"phone\":");
    }
    @Test void historicalDateProtectsCurrentSalaryBeforeGenerationPersistsConfirmation()
    {
        historicalBeforeConfirmation();
        var before = EmployeeSalaryValues.fromProfile(profile).fingerprint();
        var request = confirmation("history_before_flag");
        assertThat(service.archive(request).updated()).isEqualTo(1);
        var contract = commands.values().iterator().next();
        assertThat(contract.sourceType).isEqualTo("HISTORICAL_CONTRACT_EXCEL");
        assertThat(contract.effectiveDate).isEqualTo(LocalDate.of(2020,1,1));
        assertThat(contract.getSalaryTotal()).isEqualByComparingTo("5000");
        assertThat(contract.previousSourceId).isNull();
        assertThat(current.get()).isNull();
        assertThat(EmployeeSalaryValues.fromProfile(profile).fingerprint()).isEqualTo(before);
        verify(imports,never()).updateSalary(any(),any(),any());
        verify(imports,never()).insertAudit(any(),any(),any(),any(),any());
        verify(evidence,never()).pointTo(any());
        assertThat(service.preview(previewRequest()).get(0).confirmed()).isTrue();
    }
    @Test void historicalUnknownResponseReusesEvidenceAfterGenerationAndLaterSalaryChanges()
    {
        historicalBeforeConfirmation();
        var request=confirmation("history_unknown_result");service.archive(request);
        row.version=7L;row.historicalSupplement=true;row.status="GENERATED";
        profile.setSalaryTotal(new BigDecimal("12000"));
        var later=new EmployeeSalarySource();later.sourceId="formal-new";later.effectiveDate=LocalDate.now();current.set(later);
        assertThat(service.status(request).status()).isEqualTo("CONFIRMED");
        assertThat(service.archive(request).reused()).isEqualTo(1);
        assertThat(current.get()).isSameAs(later);
        assertThat(profile.getSalaryTotal()).isEqualByComparingTo("12000");
        verify(evidence,times(1)).insert(any());verify(evidence,never()).pointTo(any());
        verify(imports,never()).updateSalary(any(),any(),any());
    }
    @Test void contractStartingTodayRemainsNormalAutomaticPayrollSync()
    {
        row.snapshotJson=row.snapshotJson.replace("\"phone\":", "\"contractStartDate\":\""+LocalDate.now(ZoneId.of("Asia/Shanghai"))+"\",\"phone\":");
        service.archive(confirmation("normal_contract_today"));
        assertThat(current.get().sourceType).isEqualTo("ONBOARD_EXCEL");
        assertThat(profile.getSalaryTotal()).isEqualByComparingTo("5000");
        verify(evidence).pointTo(any());verify(imports).updateSalary(any(),any(),any());
    }
    @Test void historicalPreviewDoesNotReuseAnotherContractDateOrFile()
    {
        historicalBeforeConfirmation();service.archive(confirmation("history_reuse_exact"));
        assertThat(service.preview(previewRequest()).get(0).confirmed()).isTrue();
        row.snapshotJson=row.snapshotJson.replace("2020-01-01","2021-01-01");
        assertThat(service.preview(previewRequest()).get(0).confirmed()).isFalse();
        row.snapshotJson=row.snapshotJson.replace("2021-01-01","2020-01-01");row.fileSha256="b".repeat(64);
        assertThat(service.preview(previewRequest()).get(0).confirmed()).isFalse();
    }
    @Test void malformedHistoricalFlagCannotFallThroughToCurrentPayroll()
    {
        row.historicalSupplement=true;
        assertThatThrownBy(() -> service.preview(previewRequest())).hasMessageContaining("历史合同开始日期无效");
        row.historicalSupplement=false;
        row.snapshotJson=row.snapshotJson.replace("\"phone\":", "\"contractStartDate\":[2020,1,1],\"phone\":");
        assertThatThrownBy(() -> service.preview(previewRequest())).hasMessageContaining("合同开始日期无效");
        verify(imports,never()).updateSalary(any(),any(),any());verify(evidence,never()).pointTo(any());
    }

}
