package com.erp.oa.service.impl;

import java.util.Objects;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.erp.common.core.exception.ServiceException;
import com.erp.oa.domain.OaSignOnboardImportBatch;
import com.erp.oa.domain.OaSignOnboardImportRow;
import com.erp.oa.domain.OaSignOnboardContractSnapshot;
import com.erp.oa.domain.OaSignPackage;
import com.erp.oa.mapper.OaSignSalarySourceMapper;
import com.erp.system.api.domain.EmployeeSalarySource;
import com.erp.system.api.domain.EmployeeSalaryValues;

/** Read-only access to System-owned evidence; only the package/source binding belongs to OA. */
@Service
public class OaSignSalarySourceService
{
    private final OaSignSalarySourceMapper mapper;
    public OaSignSalarySourceService(OaSignSalarySourceMapper mapper) { this.mapper = mapper; }

    public String requireConfirmed(OaSignOnboardImportBatch batch, OaSignOnboardImportRow row,
            OaSignOnboardContractSnapshot snapshot)
    {
        boolean historical = Boolean.TRUE.equals(row.getHistoricalSupplement())
                || snapshot.getContractStartDate() != null
                    && snapshot.getContractStartDate().isBefore(LocalDate.now(ZoneId.of("Asia/Shanghai")));
        EmployeeSalarySource source;
        if (historical)
        {
            // A generated contract keeps its original immutable binding, even if another
            // equivalent confirmation command was recorded before/after its generation.
            source = row.getPackageId() == null ? null : mapper.binding(row.getPackageId());
            if (source == null) source = mapper.historicalForRow(batch.getBatchId(), row.getRowId());
        }
        else source = mapper.current(row.getEmployeeId());
        EmployeeSalaryValues salary = new EmployeeSalaryValues();
        salary.setBaseSalary(snapshot.getBaseSalary()); salary.setPostSalary(snapshot.getPostSalary());
        salary.setFieldAllowance(snapshot.getFieldAllowance()); salary.setPerformanceSalary(snapshot.getPerformanceSalary());
        salary.setSalaryTotal(snapshot.getSalaryTotal());
        if (historical && (!Boolean.TRUE.equals(row.getHistoricalSupplement())
                || row.getHistoricalReason() == null || row.getHistoricalReason().isBlank()
                || snapshot.getContractStartDate() == null))
            throw new ServiceException("请先完成历史补签确认并填写原因");
        if (source == null || !source.verified
                || !(historical ? "HISTORICAL_CONTRACT_EXCEL" : "ONBOARD_EXCEL").equals(source.sourceType)
                || !Objects.equals(source.employeeId, row.getEmployeeId())
                || !Objects.equals(source.batchId, batch.getBatchId()) || !Objects.equals(source.rowId, row.getRowId())
                || historical && (source.rowVersion == null || row.getVersion() == null
                    || source.rowVersion > row.getVersion() || source.fileSha256 == null
                    || !source.fileSha256.equals(batch.getFileSha256())
                    || !Objects.equals(source.effectiveDate, snapshot.getContractStartDate()))
                || salary.validationError() != null || !source.sameAmounts(salary))
            throw new ServiceException("合同工资尚未同步或已变化，请在员工档案批量入职合同中重新生成，系统会自动同步工资");
        return source.sourceId;
    }

    @Transactional(rollbackFor = Exception.class)
    public void bind(OaSignPackage signPackage, String sourceId)
    {
        if (signPackage == null || sourceId == null) throw new ServiceException("缺少合同工资来源");
        // Match archive/generation order before the historical row/source join takes locks.
        mapper.lockPackageTask(signPackage.getPackageId());
        mapper.lockProfile(signPackage.getEmployeeId());
        EmployeeSalarySource historical = mapper.historicalForPackageLocked(
                signPackage.getPackageId(), signPackage.getEmployeeId(), sourceId);
        if (historical != null)
        {
            requireHistorical(historical, signPackage, sourceId);
            mapper.bindHistorical(signPackage.getPackageId(), signPackage.getEmployeeId(), sourceId);
            requireBinding(signPackage, sourceId);
            return;
        }
        EmployeeSalarySource current = mapper.currentLocked(signPackage.getEmployeeId());
        if (current == null || !current.verified || "HISTORICAL_CONTRACT_EXCEL".equals(current.sourceType)
                || !sourceId.equals(current.sourceId)
                || !current.sameAmounts(amounts(signPackage)))
            throw new ServiceException("合同生成期间工资已变化，请重新核对工资版本");
        mapper.bind(signPackage.getPackageId(), signPackage.getEmployeeId(), sourceId);
        requireBinding(signPackage, sourceId);
    }

    private void requireBinding(OaSignPackage signPackage, String sourceId)
    {
        EmployeeSalarySource bound = mapper.bindingLocked(signPackage.getPackageId());
        if (bound == null || !Objects.equals(bound.employeeId, signPackage.getEmployeeId())
                || !sourceId.equals(bound.sourceId))
            throw new ServiceException("合同已关联其他工资版本，请通过合同更正处理");
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void requireSend(OaSignPackage signPackage)
    {
        if (mapper.isExcelPackage(signPackage.getPackageId()) == 0) return;
        // Match archive/generation order before the historical row/source join takes locks.
        mapper.lockPackageTask(signPackage.getPackageId());
        mapper.lockProfile(signPackage.getEmployeeId());
        EmployeeSalarySource bound = mapper.bindingLocked(signPackage.getPackageId());
        if (bound != null && "HISTORICAL_CONTRACT_EXCEL".equals(bound.sourceType))
        {
            requireHistorical(mapper.historicalForPackageLocked(signPackage.getPackageId(),
                    signPackage.getEmployeeId(), bound.sourceId), signPackage, bound.sourceId);
            return;
        }
        EmployeeSalarySource current = mapper.currentLocked(signPackage.getEmployeeId());
        // Preserve the already-prepared historical signature loop; never exempt a new draft
        // or a package whose employee has entered the new salary-source flow.
        if (bound == null && current == null && mapper.currentSourceIdLocked(signPackage.getEmployeeId()) == null
                && mapper.isLegacyPreparedSignature(signPackage.getPackageId()) == 1) return;
        if (bound == null || current == null || !bound.verified || !current.verified
                || !Objects.equals(bound.employeeId, signPackage.getEmployeeId())
                || !Objects.equals(bound.sourceId, current.sourceId) || !bound.sameAmounts(amounts(signPackage)))
            throw new ServiceException("合同工资来源未确认或已更正，请回员工档案核对后重新生成，不能发送旧金额");
    }

    private void requireHistorical(EmployeeSalarySource source, OaSignPackage signPackage, String sourceId)
    {
        if (source == null || !source.verified || !"HISTORICAL_CONTRACT_EXCEL".equals(source.sourceType)
                || !Objects.equals(source.sourceId, sourceId)
                || !Objects.equals(source.employeeId, signPackage.getEmployeeId())
                || amounts(signPackage).validationError() != null || !source.sameAmounts(amounts(signPackage)))
            throw new ServiceException("历史合同工资来源或绑定已变化，请重新核对历史补签，不能发送");
    }

    private EmployeeSalaryValues amounts(OaSignPackage value)
    {
        EmployeeSalaryValues salary = new EmployeeSalaryValues();
        salary.setBaseSalary(value.getBaseSalary()); salary.setPostSalary(value.getPostSalary());
        salary.setFieldAllowance(value.getFieldAllowance()); salary.setPerformanceSalary(value.getPerformanceSalary());
        salary.setSalaryTotal(value.getSalaryTotal()); return salary;
    }
}
