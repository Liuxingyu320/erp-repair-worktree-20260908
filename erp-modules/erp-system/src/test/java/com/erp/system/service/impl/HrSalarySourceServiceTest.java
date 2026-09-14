package com.erp.system.service.impl;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.erp.system.api.domain.*;
import com.erp.system.domain.SysConfig;
import com.erp.system.mapper.*;

class HrSalarySourceServiceTest
{
    private final HrSalarySourceMapper mapper=mock(HrSalarySourceMapper.class);
    private final SysConfigMapper config=mock(SysConfigMapper.class);
    private final HrSalarySourceService service=new HrSalarySourceService(mapper,config,mock(SysUserProfileMapper.class),new ObjectMapper().findAndRegisterModules());
    private EmployeeSalarySource salary(String total)
    {
        var value=new EmployeeSalarySource();value.employeeId=11L;value.sourceId="previous";value.verified=true;
        value.setBaseSalary(new BigDecimal(total));value.setPostSalary(BigDecimal.ZERO);value.setFieldAllowance(BigDecimal.ZERO);
        value.setPerformanceSalary(BigDecimal.ZERO);value.setSalaryTotal(new BigDecimal(total));return value;
    }
    private EmployeeSalarySource record(EmployeeSalarySource before, EmployeeSalarySource after)
    {
        when(mapper.insert(any())).thenReturn(1);when(mapper.pointTo(any())).thenReturn(1);
        service.recordChange(11L,"TRANSFER",22L,before,after,LocalDate.of(2026,9,11),9L,"test_hr");
        var capture=ArgumentCaptor.forClass(EmployeeSalarySource.class);verify(mapper).insert(capture.capture());return capture.getValue();
    }
    @Test void formalChangeKeepsVerifiedLineageAndAmounts()
    {
        var before=salary("5000");when(mapper.currentLocked(11L)).thenReturn(before);
        var source=record(before,salary("6000"));
        assertThat(source.previousSourceId).isEqualTo("previous");assertThat(source.verified).isTrue();
        assertThat(source.getSalaryTotal()).isEqualByComparingTo("6000");assertThat(source.commandId).isEqualTo("TRANSFER:22");
    }
    @Test void oldUnprovenSalaryIsRecordedAsPendingReconciliationNeverSilentlyVerified()
    {
        var source=record(salary("5000"),salary("6000"));
        assertThat(source.verified).isFalse();assertThat(source.reason).contains("前序工资来源待核对");
    }
    @Test void strictCutoverRejectsMissingOrMismatchedLineage()
    {
        var flag=new SysConfig();flag.setConfigValue("true");when(config.selectConfig(any())).thenReturn(flag);
        assertThatThrownBy(() -> record(salary("5000"),salary("6000"))).hasMessageContaining("来源待核对");
        verify(mapper,never()).insert(any());
    }
    @Test void unchangedSalaryPreservesCurrentVersion()
    {
        var before=salary("5000");when(mapper.currentLocked(11L)).thenReturn(before);
        service.recordChange(11L,"REGULARIZATION",22L,before,salary("5000.00"),LocalDate.now(),9L,"test_hr");
        verify(mapper,never()).insert(any());
    }
    @Test void alternativeOnboardingCannotInventSalary()
    {
        var value=salary("5000");when(mapper.currentLocked(11L)).thenReturn(value);
        service.requireOnboardingSalary(11L,value,salary("5000.00"));
        assertThatThrownBy(() -> service.requireOnboardingSalary(11L,value,salary("9000"))).hasMessageContaining("不能另填工资");
    }
    @Test void historicalFormalChangeCannotReplaceALaterSalaryVersion()
    {
        var before=salary("5000"); before.effectiveDate=LocalDate.of(2026,9,12);
        when(mapper.currentLocked(11L)).thenReturn(before);
        assertThatThrownBy(() -> record(before,salary("6000"))).hasMessageContaining("早于当前工资版本");
        verify(mapper,never()).insert(any());
    }
    @Test void fingerprintIsNullAwareAndIndependentOfDecimalScale()
    {
        assertThat(salary("5000").fingerprint()).isEqualTo(salary("5000.00").fingerprint());
        var missing=salary("5000");missing.setPostSalary(null);
        assertThat(missing.sameAmounts(salary("5000"))).isFalse();
    }
    @Test void historicalEvidenceCannotUseCurrentSourceAppend()
    {
        var source=salary("5000");source.sourceType="HISTORICAL_CONTRACT_EXCEL";
        assertThatThrownBy(() -> service.append(source)).hasMessageContaining("不能成为现行工资");
        verify(mapper,never()).insert(any());verify(mapper,never()).pointTo(any());
    }
    @Test void historicalAppendOnlyInsertsValidatedEvidence()
    {
        var source=salary("5000");source.sourceType="HISTORICAL_CONTRACT_EXCEL";
        source.batchId=3L;source.rowId=10L;source.rowVersion=2L;source.fileSha256="a".repeat(64);
        source.effectiveDate=LocalDate.of(2020,1,1);source.previousSourceId="current-payroll";
        when(mapper.insert(any())).thenReturn(1);
        service.appendHistorical(source);
        verify(mapper).insert(source);verify(mapper,never()).pointTo(any());assertThat(source.previousSourceId).isNull();
        source.fileSha256=null;
        assertThatThrownBy(() -> service.appendHistorical(source)).hasMessageContaining("来源不完整");
        verify(mapper,times(1)).insert(any());
    }

}
