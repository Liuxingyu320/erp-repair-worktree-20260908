package com.erp.system.service.impl;

import java.time.LocalDate;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.erp.common.core.exception.ServiceException;
import com.erp.system.api.domain.EmployeeSalarySource;
import com.erp.system.api.domain.EmployeeSalaryValues;
import com.erp.system.mapper.HrSalarySourceMapper;
import com.erp.system.mapper.SysConfigMapper;
import com.erp.system.api.domain.SysUserProfile;
import com.erp.system.mapper.SysUserProfileMapper;
import com.erp.system.domain.SysConfig;

@Service
public class HrSalarySourceService
{
    private final HrSalarySourceMapper mapper;
    private final SysConfigMapper config;
    private final SysUserProfileMapper profiles;
    private final ObjectMapper json;

    public HrSalarySourceService(HrSalarySourceMapper mapper, SysConfigMapper config,
            SysUserProfileMapper profiles, ObjectMapper json)
    { this.mapper = mapper; this.config = config; this.profiles = profiles; this.json = json; }

    public EmployeeSalarySource current(Long employeeId) { return mapper.current(employeeId); }
    public EmployeeSalarySource currentLocked(Long employeeId) { return mapper.currentLocked(employeeId); }
    public EmployeeSalarySource commandLocked(String commandId) { return mapper.commandLocked(commandId); }
    public EmployeeSalarySource command(String commandId) { return mapper.command(commandId); }

    public EmployeeSalarySource historicalForRow(Long batchId, Long rowId)
    { return mapper.historicalForRow(batchId, rowId); }

    /** Contract evidence is immutable but is never a current-payroll version. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void appendHistorical(EmployeeSalarySource source)
    {
        if (source == null || !"HISTORICAL_CONTRACT_EXCEL".equals(source.sourceType)
                || !source.verified || source.employeeId == null || source.batchId == null
                || source.rowId == null || source.rowVersion == null || source.effectiveDate == null
                || source.fileSha256 == null || !source.fileSha256.matches("[a-fA-F0-9]{64}"))
            throw new ServiceException("历史合同工资来源不完整");
        String error = source.validationError();
        if (error != null) throw new ServiceException(error);
        source.sourceId = UUID.randomUUID().toString();
        source.previousSourceId = null;
        if (mapper.insert(source) != 1) throw new ServiceException("历史合同工资留存未完成，已回滚");
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void append(EmployeeSalarySource source)
    {
        if ("HISTORICAL_CONTRACT_EXCEL".equals(source.sourceType))
            throw new ServiceException("历史合同工资不能成为现行工资");
        String error = source.validationError();
        if (error != null) throw new ServiceException(error);
        source.sourceId = UUID.randomUUID().toString();
        if (mapper.insert(source) != 1 || mapper.pointTo(source) < 1)
            throw new ServiceException("工资来源记录未完成，已回滚");
    }

    /** The caller already holds the profile lock and has persisted the formal HR action. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordChange(Long employeeId, String type, Long actionId, Object before, Object after,
            LocalDate effectiveDate, Long operatorId, String operatorName)
    {
        EmployeeSalaryValues oldSalary = amounts(before);
        EmployeeSalaryValues newSalary = amounts(after);
        EmployeeSalarySource previous = currentLocked(employeeId);
        boolean validPrevious = previous != null && previous.verified && previous.sameAmounts(oldSalary);
        SysConfig query = new SysConfig(); query.setConfigKey("feature.hr.salarySource.strict.enabled");
        SysConfig flag = config.selectConfig(query);
        if (flag != null && "true".equalsIgnoreCase(flag.getConfigValue()) && !validPrevious)
            throw new ServiceException("工资来源待核对，请在员工档案确认合同工资后重试");
        if (oldSalary.sameAmounts(newSalary) && validPrevious) return;
        if (previous != null && previous.effectiveDate != null && effectiveDate != null
                && effectiveDate.isBefore(previous.effectiveDate))
            throw new ServiceException("人事变更日期早于当前工资版本，请核对后续工资，不能用历史变更覆盖");
        EmployeeSalarySource source = json.convertValue(newSalary, EmployeeSalarySource.class);
        source.employeeId = employeeId;
        source.previousSourceId = previous == null ? null : previous.sourceId;
        source.sourceType = type;
        source.businessId = String.valueOf(actionId);
        source.commandId = type + ":" + actionId;
        source.beforeHash = oldSalary.fingerprint();
        source.requestHash = newSalary.fingerprint();
        source.effectiveDate = effectiveDate;
        source.operatorUserId = operatorId; source.operatorName = operatorName;
        source.reason = validPrevious ? "正式人事变更" : "正式人事变更；前序工资来源待核对";
        source.verified = validPrevious;
        append(source);
    }

    public void requireOnboardingSalary(Long employeeId, Object before, Object after)
    {
        var source = currentLocked(employeeId);
        if (source == null || !source.verified || !source.sameAmounts(amounts(before)) || !source.sameAmounts(amounts(after)))
            throw new ServiceException("请先在员工档案批量入职合同中确认工资；入职确认不能另填工资");
    }

    public EmployeeSalaryValues amounts(Object value)
    {
        var tree = json.valueToTree(value);
        var amounts = json.createObjectNode();
        for (String field : new String[] { "baseSalary", "postSalary", "fieldAllowance", "performanceSalary", "salaryTotal" })
            if (tree != null && tree.has(field)) amounts.set(field, tree.get(field));
        return json.convertValue(amounts, EmployeeSalaryValues.class);
    }

    public boolean matchesProfile(EmployeeSalarySource source)
    {
        SysUserProfile profile = source == null ? null : profiles.selectUserProfileByUserId(source.employeeId);
        return source != null && source.verified && profile != null
                && source.sameAmounts(EmployeeSalaryValues.fromProfile(profile));
    }

    public java.util.List<java.util.Map<String, Object>> contracts(EmployeeSalarySource source)
    { return source == null ? java.util.List.of() : mapper.contracts(source.employeeId, source.sourceId); }
}
