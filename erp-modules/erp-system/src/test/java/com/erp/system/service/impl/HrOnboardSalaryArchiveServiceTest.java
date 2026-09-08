package com.erp.system.service.impl;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.erp.common.core.context.SecurityContextHolder;
import com.erp.system.api.domain.EmployeeSalaryValues;
import com.erp.system.api.domain.SysUser;
import com.erp.system.api.domain.SysUserProfile;
import com.erp.system.domain.HrOnboardSalarySource;
import com.erp.system.domain.HrEmployeeSalaryImportAudit;
import com.erp.system.domain.dto.HrOnboardSalaryArchiveRequest;
import com.erp.system.mapper.HrOnboardSalaryArchiveMapper;
import com.erp.system.mapper.SysUserProfileMapper;

class HrOnboardSalaryArchiveServiceTest
{
    private final HrOnboardSalaryArchiveMapper mapper = mock(HrOnboardSalaryArchiveMapper.class);
    private final SysUserProfileMapper profiles = mock(SysUserProfileMapper.class);
    private final HrEmployeeAccessService access = mock(HrEmployeeAccessService.class);
    private final ObjectMapper json = new ObjectMapper();
    private final HrOnboardSalaryArchiveService service =
            new HrOnboardSalaryArchiveService(mapper, profiles, access, json);
    private final HrOnboardSalarySource source = new HrOnboardSalarySource();
    private final SysUserProfile profile = new SysUserProfile();
    private final SysUser employee = new SysUser();
    private static final String SALARY = "{\"baseSalary\":3000,\"postSalary\":1000,"
            + "\"fieldAllowance\":500,\"performanceSalary\":500,\"salaryTotal\":5000}";

    @BeforeEach void setup()
    {
        SecurityContextHolder.setUserId("1");
        SecurityContextHolder.setUserName("reviewer");
        source.rowId = 10L; source.batchId = 3L; source.version = 2L;
        source.employeeId = 11L; source.ownerUserId = 1L;
        source.status = "READY"; source.matchType = "PHONE_AND_NAME";
        source.snapshotJson = SALARY.substring(0, SALARY.length() - 1)
                + ",\"phone\":\"13800000011\",\"idNumber\":\"TEST_ID_11\"}";
        source.fileSha256 = "a".repeat(64);
        employee.setUserId(11L); employee.setPhonenumber("13800000011");
        profile.setUserId(11L); profile.setIdNumber("TEST_ID_11");
        when(mapper.lockSource(3L, 10L)).thenReturn(source);
        when(access.lockActiveScoped(any())).thenReturn(employee);
        when(profiles.lockSigningProfileByUserId(11L)).thenReturn(11L);
        when(profiles.selectUserProfileByUserId(11L)).thenReturn(profile);
        when(mapper.updateSalary(eq(11L), any(), any())).thenReturn(1);
        when(mapper.insertAudit(any(), any(), any(), any(), any())).thenReturn(1);
    }
    @AfterEach void clear() { SecurityContextHolder.remove(); }
    private HrOnboardSalaryArchiveRequest request()
    { return new HrOnboardSalaryArchiveRequest(3L, List.of(new HrOnboardSalaryArchiveRequest.Row(10L, 2L))); }

    @Test void archivesExcelTotalAndAllFourPartsWithBeforeEvidence()
    {
        profile.setSalaryTotal(new BigDecimal("9999"));
        assertThat(service.archive(request()).updated()).isEqualTo(1);
        ArgumentCaptor<EmployeeSalaryValues> values = ArgumentCaptor.forClass(EmployeeSalaryValues.class);
        verify(mapper).updateSalary(eq(11L), values.capture(), eq("reviewer"));
        assertThat(values.getValue().getSalaryTotal()).isEqualByComparingTo("5000");
        assertThat(values.getValue().getBaseSalary()).isEqualByComparingTo("3000");
        assertThat(values.getValue().getPostSalary()).isEqualByComparingTo("1000");
        assertThat(values.getValue().getFieldAllowance()).isEqualByComparingTo("500");
        assertThat(values.getValue().getPerformanceSalary()).isEqualByComparingTo("500");
        verify(mapper).insertAudit(eq(source), contains("9999"), contains("5000"), eq(1L), eq("reviewer"));
    }

    @Test void identicalRetryDoesNotRewriteTheProfileDespiteDecimalScaleDifferences()
    {
        HrEmployeeSalaryImportAudit audit = new HrEmployeeSalaryImportAudit();
        audit.sourceRowId = 10L; audit.sourceVersion = 2L; audit.salaryJson = SALARY;
        when(mapper.latest(11L)).thenReturn(audit);
        profile.setBaseSalary(new BigDecimal("3000.00"));
        profile.setPostSalary(new BigDecimal("1000.00"));
        profile.setFieldAllowance(new BigDecimal("500.00"));
        profile.setPerformanceSalary(new BigDecimal("500.00"));
        profile.setSalaryTotal(new BigDecimal("5000.00"));
        assertThat(service.archive(request()).reused()).isEqualTo(1);
        verify(mapper, never()).updateSalary(any(), any(), any());
        verify(mapper, never()).insertAudit(any(), any(), any(), any(), any());
    }

    @Test void oldWorkbookCannotOverwriteANewerImport()
    {
        HrEmployeeSalaryImportAudit newer = new HrEmployeeSalaryImportAudit();
        newer.sourceRowId = 20L; newer.sourceVersion = 0L;
        when(mapper.latest(11L)).thenReturn(newer);
        assertThatThrownBy(() -> service.archive(request())).hasMessageContaining("更新的Excel");
        verify(mapper, never()).updateSalary(any(), any(), any());
    }

    @Test void changedRowVersionIsRejectedBeforeReadingProfile()
    {
        source.version = 3L;
        assertThatThrownBy(() -> service.archive(request())).hasMessageContaining("已变化");
        verifyNoInteractions(access);
    }

    @Test void identityMismatchCannotUpdateAnotherEmployee()
    {
        employee.setPhonenumber("13800000022");
        assertThatThrownBy(() -> service.archive(request())).hasMessageContaining("身份信息");
        verify(mapper, never()).updateSalary(any(), any(), any());
    }

    @Test void incompleteOrInconsistentExcelSalaryIsNotArchived()
    {
        source.snapshotJson = source.snapshotJson.replace("\"salaryTotal\":5000", "\"salaryTotal\":6000");
        assertThatThrownBy(() -> service.archive(request())).hasMessageContaining("合计不一致");
        verify(mapper, never()).updateSalary(any(), any(), any());
    }

    @Test void profileScopeFailureCannotWriteOrCreateAudit()
    {
        when(access.lockActiveScoped(any())).thenThrow(new com.erp.common.core.exception.ServiceException("无权"));
        assertThatThrownBy(() -> service.archive(request())).hasMessageContaining("无权");
        verify(mapper, never()).updateSalary(any(), any(), any());
        verify(mapper, never()).insertAudit(any(), any(), any(), any(), any());
    }

    @Test void decimalPrecisionAndMissingSalaryAreRejected()
    {
        EmployeeSalaryValues values = new EmployeeSalaryValues();
        assertThat(values.validationError()).contains("未完整入档");
        values.setBaseSalary(new BigDecimal("1.001"));
        values.setPostSalary(BigDecimal.ZERO);
        values.setFieldAllowance(BigDecimal.ZERO);
        values.setPerformanceSalary(BigDecimal.ZERO);
        values.setSalaryTotal(new BigDecimal("1.001"));
        assertThat(values.validationError()).contains("金额无效");
    }
}
