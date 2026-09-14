package com.erp.oa.service.impl;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import com.erp.oa.domain.*;
import com.erp.oa.mapper.OaSignSalarySourceMapper;
import com.erp.system.api.domain.EmployeeSalarySource;

class OaSignSalarySourceServiceTest
{
    private final OaSignSalarySourceMapper mapper=mock(OaSignSalarySourceMapper.class);
    private final OaSignSalarySourceService service=new OaSignSalarySourceService(mapper);
    private EmployeeSalarySource source()
    {
        var value=new EmployeeSalarySource();value.employeeId=11L;value.sourceId="source-1";value.sourceType="ONBOARD_EXCEL";
        value.batchId=3L;value.rowId=10L;value.verified=true;
        value.setBaseSalary(new BigDecimal("5000"));value.setPostSalary(BigDecimal.ZERO);value.setFieldAllowance(BigDecimal.ZERO);
        value.setPerformanceSalary(BigDecimal.ZERO);value.setSalaryTotal(new BigDecimal("5000"));return value;
    }
    private OaSignPackage signPackage()
    {
        var value=new OaSignPackage();value.setPackageId(99L);value.setEmployeeId(11L);value.setBaseSalary(new BigDecimal("5000"));
        value.setPostSalary(BigDecimal.ZERO);value.setFieldAllowance(BigDecimal.ZERO);value.setPerformanceSalary(BigDecimal.ZERO);
        value.setSalaryTotal(new BigDecimal("5000"));return value;
    }
    @Test void generationRequiresExactEmployeeImportAndAmountsEvenWithoutFrontend()
    {
        var batch=new OaSignOnboardImportBatch();batch.setBatchId(3L);
        var row=new OaSignOnboardImportRow();row.setRowId(10L);row.setEmployeeId(11L);
        var salary=new OaSignOnboardContractSnapshot();salary.setBaseSalary(new BigDecimal("5000"));salary.setPostSalary(BigDecimal.ZERO);
        salary.setFieldAllowance(BigDecimal.ZERO);salary.setPerformanceSalary(BigDecimal.ZERO);salary.setSalaryTotal(new BigDecimal("5000"));
        assertThatThrownBy(() -> service.requireConfirmed(batch,row,salary)).hasMessageContaining("尚未同步");
        var source=source();when(mapper.current(11L)).thenReturn(source);
        assertThat(service.requireConfirmed(batch,row,salary)).isEqualTo("source-1");
        source.rowId=20L;assertThatThrownBy(() -> service.requireConfirmed(batch,row,salary)).hasMessageContaining("尚未同步");
        source.rowId=10L;salary.setSalaryTotal(new BigDecimal("8000"));
        assertThatThrownBy(() -> service.requireConfirmed(batch,row,salary)).hasMessageContaining("尚未同步");
    }
    @Test void concurrentSalaryChangeCannotBindGeneratedOldAmount()
    {
        var source=source();source.sourceId="source-new";when(mapper.currentLocked(11L)).thenReturn(source);
        assertThatThrownBy(() -> service.bind(signPackage(),"source-1")).hasMessageContaining("生成期间工资已变化");
        verify(mapper,never()).bind(any(),any(),any());
    }
    @Test void bindingIsImmutableAndRepeatable()
    {
        var source=source();when(mapper.currentLocked(11L)).thenReturn(source);when(mapper.bindingLocked(99L)).thenReturn(source);
        service.bind(signPackage(),"source-1"); service.bind(signPackage(),"source-1");
        var old=source();old.sourceId="old";when(mapper.bindingLocked(99L)).thenReturn(old);
        assertThatThrownBy(() -> service.bind(signPackage(),"source-1")).hasMessageContaining("其他工资版本");
    }
    @Test void sendingStopsIfCurrentSalaryVersionWasCorrected()
    {
        var source=source();when(mapper.isExcelPackage(99L)).thenReturn(1);when(mapper.bindingLocked(99L)).thenReturn(source);
        when(mapper.currentLocked(11L)).thenReturn(source);service.requireSend(signPackage());
        var newer=source();newer.sourceId="source-new";when(mapper.currentLocked(11L)).thenReturn(newer);
        assertThatThrownBy(() -> service.requireSend(signPackage())).hasMessageContaining("不能发送旧金额");
    }
    @Test void alreadyExistingNonExcelContractPathIsNotRewritten()
    {
        when(mapper.isExcelPackage(99L)).thenReturn(0);service.requireSend(signPackage());
        verify(mapper,never()).current(any());verify(mapper,never()).binding(any());
    }
    private EmployeeSalarySource historical()
    {
        var source=source();source.sourceType="HISTORICAL_CONTRACT_EXCEL";
        source.rowVersion=2L;source.fileSha256="a".repeat(64);source.effectiveDate=java.time.LocalDate.of(2020,1,1);return source;
    }
    @Test void historicalGenerationStillRequiresConfirmationAndExactContractEvidence()
    {
        var batch=new OaSignOnboardImportBatch();batch.setBatchId(3L);batch.setFileSha256("a".repeat(64));
        var row=new OaSignOnboardImportRow();row.setRowId(10L);row.setEmployeeId(11L);row.setVersion(4L);
        var salary=new OaSignOnboardContractSnapshot();salary.setBaseSalary(new BigDecimal("5000"));
        salary.setPostSalary(BigDecimal.ZERO);salary.setFieldAllowance(BigDecimal.ZERO);salary.setPerformanceSalary(BigDecimal.ZERO);
        salary.setSalaryTotal(new BigDecimal("5000"));salary.setContractStartDate(java.time.LocalDate.of(2020,1,1));
        var source=historical();when(mapper.historicalForRow(3L,10L)).thenReturn(source);
        assertThatThrownBy(() -> service.requireConfirmed(batch,row,salary)).hasMessageContaining("历史补签确认");
        row.setHistoricalSupplement(true);row.setHistoricalReason("补签历史合同");
        assertThat(service.requireConfirmed(batch,row,salary)).isEqualTo(source.sourceId);
        var bound=historical();bound.sourceId="original-bound";row.setPackageId(99L);
        when(mapper.binding(99L)).thenReturn(bound);
        assertThat(service.requireConfirmed(batch,row,salary)).isEqualTo("original-bound");
        row.setPackageId(null);
        source.fileSha256="b".repeat(64);
        assertThatThrownBy(() -> service.requireConfirmed(batch,row,salary)).hasMessageContaining("尚未同步");
        source.fileSha256="a".repeat(64);source.rowVersion=8L;
        assertThatThrownBy(() -> service.requireConfirmed(batch,row,salary)).hasMessageContaining("尚未同步");
        source.rowVersion=2L;source.effectiveDate=java.time.LocalDate.of(2021,1,1);
        assertThatThrownBy(() -> service.requireConfirmed(batch,row,salary)).hasMessageContaining("尚未同步");
        verify(mapper,never()).current(any());
    }
    @Test void historicalBindingUsesEvidenceWithoutCurrentPayrollAndRemainsImmutable()
    {
        var source=historical();when(mapper.historicalForPackageLocked(99L,11L,"source-1")).thenReturn(source);
        when(mapper.bindingLocked(99L)).thenReturn(source);
        service.bind(signPackage(),source.sourceId);service.bind(signPackage(),source.sourceId);
        verify(mapper,times(2)).bindHistorical(99L,11L,"source-1");verify(mapper,never()).bind(any(),any(),any());
        verify(mapper,never()).currentLocked(any());
        var other=historical();other.sourceId="other";when(mapper.bindingLocked(99L)).thenReturn(other);
        assertThatThrownBy(() -> service.bind(signPackage(),source.sourceId)).hasMessageContaining("其他工资版本");
    }
    @Test void historicalSendAllowsCurrentSalaryChangesButRejectsBrokenSourceContext()
    {
        var source=historical();when(mapper.isExcelPackage(99L)).thenReturn(1);
        when(mapper.bindingLocked(99L)).thenReturn(source);
        when(mapper.historicalForPackageLocked(99L,11L,"source-1")).thenReturn(source);
        service.requireSend(signPackage());verify(mapper,never()).currentLocked(any());
        when(mapper.historicalForPackageLocked(99L,11L,"source-1")).thenReturn(null);
        assertThatThrownBy(() -> service.requireSend(signPackage())).hasMessageContaining("历史合同工资来源或绑定已变化");
    }
    @Test void historicalSourceCannotBypassMissingContractContextThroughCurrentPointer()
    {
        when(mapper.currentLocked(11L)).thenReturn(historical());
        assertThatThrownBy(() -> service.bind(signPackage(),"source-1")).hasMessageContaining("生成期间工资已变化");
        verify(mapper,never()).bind(any(),any(),any());verify(mapper,never()).bindHistorical(any(),any(),any());
    }

}
