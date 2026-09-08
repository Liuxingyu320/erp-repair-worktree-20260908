package com.erp.system.service.impl;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.security.utils.SecurityUtils;
import com.erp.system.api.domain.EmployeeSalaryValues;
import com.erp.system.api.domain.SysUser;
import com.erp.system.api.domain.SysUserProfile;
import com.erp.system.domain.HrOnboardSalarySource;
import com.erp.system.domain.HrOnboardSalaryTaskOwner;
import com.erp.system.domain.HrEmployeeSalaryImportAudit;
import com.erp.system.domain.dto.HrOnboardSalaryArchiveRequest;
import com.erp.system.domain.vo.HrEmployeeQuery;
import com.erp.system.mapper.HrOnboardSalaryArchiveMapper;
import com.erp.system.mapper.SysUserProfileMapper;

/** Archives only server-owned, explicitly confirmed Excel rows. */
@Service
public class HrOnboardSalaryArchiveService
{
    private final HrOnboardSalaryArchiveMapper mapper;
    private final SysUserProfileMapper profiles;
    private final HrEmployeeAccessService access;
    private final ObjectMapper json;

    public HrOnboardSalaryArchiveService(HrOnboardSalaryArchiveMapper mapper,
            SysUserProfileMapper profiles, HrEmployeeAccessService access,
            ObjectMapper json)
    {
        this.mapper = mapper;
        this.profiles = profiles;
        this.access = access;
        this.json = json;
    }

    @Transactional(rollbackFor = Exception.class)
    public Result archive(HrOnboardSalaryArchiveRequest request)
    {
        if (request == null || request.batchId() == null || request.rows() == null
                || request.rows().isEmpty() || request.rows().size() > 100)
            throw new ServiceException("请选择1至100条已匹配的工资导入行");
        if (request.rows().stream().anyMatch(row -> row == null || row.rowId() == null
                || row.rowId() <= 0 || row.version() == null || row.version() < 0))
            throw new ServiceException("工资导入行编号或版本无效");
        int updated = 0;
        int reused = 0;
        Set<Long> seenRows = new HashSet<>();
        Set<Long> seenEmployees = new HashSet<>();
        for (HrOnboardSalaryArchiveRequest.Row selected : request.rows().stream()
                .sorted(Comparator.comparing(HrOnboardSalaryArchiveRequest.Row::rowId)).toList())
        {
            if (!seenRows.add(selected.rowId()))
                throw new ServiceException("工资入档不能重复选择同一导入行");
            HrOnboardSalarySource source = mapper.lockSource(request.batchId(), selected.rowId());
            if (source == null || source.employeeId == null
                    || !Objects.equals(source.version, selected.version())
                    || !Set.of("ID_NUMBER", "PHONE_AND_NAME").contains(String.valueOf(source.matchType))
                    || Set.of("GENERATING", "CONFLICT", "EXCLUDED").contains(String.valueOf(source.status)))
                throw new ServiceException("工资导入行未匹配或已变化，请刷新后确认");
            Long owner = lockCurrentOwner(source);
            if (!SecurityUtils.isAdmin()
                    && !Objects.equals(owner, SecurityUtils.getUserId()))
                throw new ServiceException("工资导入行不属于当前合同经办人");
            if (!seenEmployees.add(source.employeeId))
                throw new ServiceException("同一员工不能在本次入档中出现多份工资");
            HrEmployeeQuery query = new HrEmployeeQuery();
            query.setUserId(source.employeeId);
            SysUser employee = access.lockActiveScoped(query);
            if (profiles.lockSigningProfileByUserId(source.employeeId) == null)
                throw new ServiceException("员工档案尚未建立，请先建立档案后重新导入");
            SysUserProfile before = profiles.selectUserProfileByUserId(source.employeeId);
            if (before == null) throw new ServiceException("员工档案不存在");
            JsonNode snapshot = parse(source.snapshotJson);
            if (!same(snapshot.path("phone").asText(), employee.getPhonenumber())
                    || !same(snapshot.path("idNumber").asText(), before.getIdNumber()))
                throw new ServiceException("员工身份信息已变化，请重新匹配Excel");
            EmployeeSalaryValues salary = salary(snapshot);
            String error = salary.validationError();
            if (error != null) throw new ServiceException(error);
            String salaryJson = write(salary);
            HrEmployeeSalaryImportAudit latest = mapper.latest(source.employeeId);
            if (latest != null)
            {
                if (latest.sourceRowId > source.rowId
                        || Objects.equals(latest.sourceRowId, source.rowId)
                        && latest.sourceVersion > source.version)
                    throw new ServiceException("员工已有更新的Excel工资入档，不能用旧导入覆盖");
                if (Objects.equals(latest.sourceRowId, source.rowId)
                        && Objects.equals(latest.sourceVersion, source.version))
                {
                    if (!sameSalary(parse(latest.salaryJson), parse(salaryJson))
                            || !sameSalary(parse(write(EmployeeSalaryValues.fromProfile(before))), parse(salaryJson)))
                        throw new ServiceException("档案工资或导入内容已变化，请重新导入确认");
                    reused++;
                    continue;
                }
            }
            if (mapper.updateSalary(source.employeeId, salary, SecurityUtils.getUsername()) != 1
                    || mapper.insertAudit(source, write(EmployeeSalaryValues.fromProfile(before)),
                            salaryJson, SecurityUtils.getUserId(), SecurityUtils.getUsername()) != 1)
                throw new ServiceException("工资入档未完成，本次变更已回滚");
            updated++;
        }
        return new Result(updated, reused);
    }

    private Long lockCurrentOwner(HrOnboardSalarySource source)
    {
        List<HrOnboardSalaryTaskOwner> tasks = mapper.lockTasks(source);
        if (tasks.isEmpty() && source.taskId == null) return source.ownerUserId;
        if (tasks.size() != 1) throw new ServiceException("Excel签约任务来源不唯一或绑定已失效");
        HrOnboardSalaryTaskOwner task = tasks.get(0);
        if (source.sourceEventVersion == null
                || source.taskId != null && !Objects.equals(source.taskId, task.taskId)
                || !Objects.equals(source.employeeId, task.employeeId)
                || !"ONBOARD".equalsIgnoreCase(task.scenario)
                || !"MANUAL_SIGN_EXCEL_IMPORT".equalsIgnoreCase(task.sourceType)
                || !String.valueOf(source.rowId).equals(task.sourceBusinessId)
                || !String.valueOf(source.sourceEventVersion).equals(task.sourceEventVersion))
            throw new ServiceException("Excel签约任务来源不规范，请重新核对导入行");
        return task.assignedHrUserId;
    }

    private EmployeeSalaryValues salary(JsonNode snapshot)
    {
        EmployeeSalaryValues value = new EmployeeSalaryValues();
        value.setBaseSalary(money(snapshot, "baseSalary"));
        value.setPostSalary(money(snapshot, "postSalary"));
        value.setFieldAllowance(money(snapshot, "fieldAllowance"));
        value.setPerformanceSalary(money(snapshot, "performanceSalary"));
        value.setSalaryTotal(money(snapshot, "salaryTotal"));
        return value;
    }

    private BigDecimal money(JsonNode snapshot, String key)
    {
        JsonNode value = snapshot.get(key);
        if (value == null || value.isNull()) return null;
        try { return new BigDecimal(value.asText()); }
        catch (NumberFormatException error) { throw new ServiceException("Excel工资金额格式无效"); }
    }

    private boolean sameSalary(JsonNode left, JsonNode right)
    {
        for (String field : new String[] { "baseSalary", "postSalary", "fieldAllowance",
                "performanceSalary", "salaryTotal" })
        {
            BigDecimal first = money(left, field);
            BigDecimal second = money(right, field);
            if (first == null || second == null || first.compareTo(second) != 0) return false;
        }
        return true;
    }

    private boolean same(String left, String right)
    {
        return left != null && right != null && !left.isBlank()
                && left.trim().equalsIgnoreCase(right.trim());
    }

    private JsonNode parse(String value)
    {
        try
        {
            JsonNode result = json.readTree(value);
            if (result == null || !result.isObject()) throw new IllegalArgumentException();
            return result;
        }
        catch (Exception error) { throw new ServiceException("工资导入来源数据损坏"); }
    }

    private String write(Object value)
    {
        try { return json.writeValueAsString(value); }
        catch (Exception error) { throw new ServiceException("工资入档审计序列化失败"); }
    }

    public record Result(int updated, int reused) { }
}
