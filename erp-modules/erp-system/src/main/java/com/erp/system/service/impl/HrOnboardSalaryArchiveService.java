package com.erp.system.service.impl;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
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
import com.erp.system.api.domain.EmployeeSalarySource;
import com.erp.system.api.domain.EmployeeSalaryValues;
import com.erp.system.api.domain.SysUser;
import com.erp.system.api.domain.SysUserProfile;
import com.erp.system.domain.HrOnboardSalarySource;
import com.erp.system.domain.HrOnboardSalaryTaskOwner;
import com.erp.system.domain.dto.HrOnboardSalaryArchiveRequest;
import com.erp.system.domain.vo.HrEmployeeQuery;
import com.erp.system.mapper.HrOnboardSalaryArchiveMapper;
import com.erp.system.mapper.SysUserProfileMapper;

/** Explicit HR confirmation of server-owned Excel amounts, with durable replay and previous-value checks. */
@Service
public class HrOnboardSalaryArchiveService
{
    private final HrOnboardSalaryArchiveMapper mapper;
    private final SysUserProfileMapper profiles;
    private final HrEmployeeAccessService access;
    private final ObjectMapper json;
    private final HrSalarySourceService sources;

    public HrOnboardSalaryArchiveService(HrOnboardSalaryArchiveMapper mapper,
            SysUserProfileMapper profiles, HrEmployeeAccessService access,
            ObjectMapper json, HrSalarySourceService sources)
    { this.mapper = mapper; this.profiles = profiles; this.access = access; this.json = json; this.sources = sources; }

    public List<Preview> preview(HrOnboardSalaryArchiveRequest request)
    {
        validateRows(request);
        List<Preview> result = new ArrayList<>();
        for (var row : request.rows())
        {
            Context context = load(request.batchId(), row, false);
            requireVersion(context.source, row);
            EmployeeSalarySource current = sources.current(context.source.employeeId);
            EmployeeSalaryValues before = EmployeeSalaryValues.fromProfile(context.profile);
            boolean historical = historicalDate(context) != null;
            EmployeeSalarySource contract = historical
                    ? sources.historicalForRow(context.source.batchId, context.source.rowId) : null;
            result.add(new Preview(context.source.rowId, context.source.version, context.source.employeeId,
                    context.employee.getNickName(), context.salary, before,
                    current == null ? null : current.sourceId, before.fingerprint(),
                    current == null ? null : current.effectiveDate,
                    context.snapshot.path("contractStartDate").asText(null),
                    historical ? matchesHistorical(contract, context)
                        : current != null && current.verified && current.sameAmounts(before)
                            && current.sameAmounts(context.salary) && Objects.equals(current.rowId, context.source.rowId),
                    historical ? "HISTORICAL_CONTRACT_EXCEL"
                        : current == null ? "历史工资来源待核对" : current.sourceType));
        }
        return result;
    }

    /** A retry queries this first. It does not claim, generate, archive or change business state. */
    public Result status(HrOnboardSalaryArchiveRequest request)
    {
        validateConfirmation(request);
        int found = 0;
        List<String> sourceIds = new ArrayList<>();
        for (var row : request.rows())
        {
            EmployeeSalarySource previous = sources.command(command(request, row));
            if (previous != null)
            {
                requireSameCommand(previous, request, row);
                HrEmployeeQuery query = new HrEmployeeQuery(); query.setUserId(previous.employeeId);
                access.findScoped(query);
                found++; sourceIds.add(previous.sourceId);
            }
            else load(request.batchId(), row, false);
        }
        if (found != 0 && found != request.rows().size())
            throw new ServiceException("工资确认记录不完整，请联系管理员核对，不能重复覆盖");
        return new Result(0, found, found == 0 ? "NOT_FOUND" : "CONFIRMED", sourceIds);
    }

    @Transactional(rollbackFor = Exception.class)
    public Result archive(HrOnboardSalaryArchiveRequest request)
    {
        validateConfirmation(request);
        Result recovered = status(request);
        if ("CONFIRMED".equals(recovered.status())) return recovered;
        int updated = 0, reused = 0;
        Set<Long> seenEmployees = new HashSet<>();
        List<String> sourceIds = new ArrayList<>();
        // Sort employees; within each row follow existing OA task -> profile -> import row locking.
        List<HrOnboardSalaryArchiveRequest.Row> ordered = new ArrayList<>(request.rows());
        ordered.sort(Comparator.comparing(row -> {
            HrOnboardSalarySource source = mapper.readSource(request.batchId(), row.rowId());
            if (source == null || source.employeeId == null) throw new ServiceException("工资导入行尚未匹配员工");
            return source.employeeId;
        }));
        for (var row : ordered)
        {
            Context context = load(request.batchId(), row, true);
            HrOnboardSalarySource imported = context.source;
            if (!seenEmployees.add(imported.employeeId)) throw new ServiceException("同一员工不能同时确认多份工资");
            EmployeeSalarySource replay = sources.commandLocked(command(request, row));
            if (replay != null)
            {
                requireSameCommand(replay, request, row);
                sourceIds.add(replay.sourceId); reused++; continue;
            }
            requireVersion(imported, row);
            if (Set.of("GENERATING", "CONFLICT", "EXCLUDED").contains(String.valueOf(imported.status)))
                throw new ServiceException("导入行正在处理或有冲突，请刷新后确认工资");
            LocalDate historicalDate = historicalDate(context);
            EmployeeSalarySource current = sources.currentLocked(imported.employeeId);
            EmployeeSalaryValues before = EmployeeSalaryValues.fromProfile(context.profile);
            if (!Objects.equals(row.expectedSourceId(), current == null ? null : current.sourceId)
                    || !Objects.equals(row.expectedProfileHash(), before.fingerprint()))
                throw new ServiceException("员工工资已变化，请重新预览差异后确认");
            if (historicalDate == null && current != null && current.effectiveDate != null && request.effectiveDate().isBefore(current.effectiveDate))
                throw new ServiceException("适用日期早于当前工资版本，历史补签不能覆盖后续工资");
            if (request.reason() == null || request.reason().trim().length() < 4)
                throw new ServiceException("请填写至少4字的工资确认或更正依据");
            EmployeeSalarySource source = json.convertValue(context.salary, EmployeeSalarySource.class);
            source.employeeId = imported.employeeId;
            source.previousSourceId = current == null ? null : current.sourceId;
            source.sourceType = historicalDate == null ? "ONBOARD_EXCEL" : "HISTORICAL_CONTRACT_EXCEL";
            source.businessId = String.valueOf(imported.rowId);
            source.batchId = imported.batchId; source.rowId = imported.rowId; source.rowVersion = imported.version;
            source.fileSha256 = imported.fileSha256;
            source.commandId = command(request, row); source.requestHash = requestHash(request);
            source.beforeHash = before.fingerprint(); source.effectiveDate = historicalDate == null ? request.effectiveDate() : historicalDate;
            source.operatorUserId = SecurityUtils.getUserId(); source.operatorName = SecurityUtils.getUsername();
            source.reason = request.reason().trim(); source.verified = true;
            if (historicalDate != null)
            {
                sources.appendHistorical(source);
                sourceIds.add(source.sourceId); updated++;
                continue;
            }
            if (mapper.updateSalary(imported.employeeId, context.salary, SecurityUtils.getUsername()) != 1)
                throw new ServiceException("工资入档未完成，本次变更已回滚");
            // The original import audit remains immutable. New confirmations always have their own source evidence.
            var audit = mapper.auditForRow(imported.rowId, imported.version);
            if (audit == null)
                if (mapper.insertAudit(imported, write(before), write(context.salary),
                        SecurityUtils.getUserId(), SecurityUtils.getUsername()) != 1)
                    throw new ServiceException("工资入档审计未完成，已回滚");
            sources.append(source);
            sourceIds.add(source.sourceId); updated++;
        }
        return new Result(updated, reused, "CONFIRMED", sourceIds);
    }

    private LocalDate historicalDate(Context context)
    {
        JsonNode dateValue = context.snapshot.path("contractStartDate");
        if (!dateValue.isMissingNode() && !dateValue.isNull() && !dateValue.isTextual())
            throw new ServiceException("合同开始日期无效，请核对导入行");
        String text = dateValue.asText(null);
        LocalDate date;
        try { date = text == null || text.isBlank() ? null : LocalDate.parse(text); }
        catch (java.time.format.DateTimeParseException error)
        { throw new ServiceException("合同开始日期无效，请核对导入行"); }
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
        // Generation saves historical confirmation after automatic salary synchronization.
        // Use the same server-owned past-contract date fact even before that flag is persisted.
        if (!Boolean.TRUE.equals(context.source.historicalSupplement)
                && (date == null || !date.isBefore(today))) return null;
        if (date == null || date.isAfter(today))
            throw new ServiceException("历史合同开始日期无效，请核对导入行");
        return date;
    }

    private boolean matchesHistorical(EmployeeSalarySource source, Context context)
    {
        return source != null && source.verified && "HISTORICAL_CONTRACT_EXCEL".equals(source.sourceType)
                && Objects.equals(source.employeeId, context.source.employeeId)
                && Objects.equals(source.batchId, context.source.batchId)
                && Objects.equals(source.rowId, context.source.rowId)
                && source.rowVersion != null && context.source.version != null
                && source.rowVersion <= context.source.version
                && source.fileSha256 != null && source.fileSha256.equals(context.source.fileSha256)
                && Objects.equals(source.effectiveDate, historicalDate(context)) && source.sameAmounts(context.salary);
    }

    private Context load(Long batchId, HrOnboardSalaryArchiveRequest.Row selected, boolean lock)
    {
        HrOnboardSalarySource source = mapper.readSource(batchId, selected.rowId());
        if (source == null || source.employeeId == null
                || !Set.of("ID_NUMBER", "PHONE_AND_NAME").contains(String.valueOf(source.matchType)))
            throw new ServiceException("工资导入行尚未匹配员工");
        HrEmployeeQuery query = new HrEmployeeQuery(); query.setUserId(source.employeeId);
        checkOwner(source, lock);
        SysUserProfile profile = lock ? profiles.selectSalaryProfileForUpdate(source.employeeId)
                : profiles.selectUserProfileByUserId(source.employeeId);
        if (profile == null) throw new ServiceException("员工档案尚未建立");
        SysUser employee = lock ? access.lockActiveScoped(query) : access.findActiveScoped(query);
        Long employeeId = source.employeeId;
        if (lock)
        {
            source = mapper.lockSource(batchId, selected.rowId());
            if (source == null || !Objects.equals(source.employeeId, employeeId))
                throw new ServiceException("导入行匹配已变化，请刷新");
            checkOwner(source, true);
        }
        JsonNode snapshot = parse(source.snapshotJson);
        if (!same(snapshot.path("phone").asText(), employee.getPhonenumber())
                || !same(snapshot.path("idNumber").asText(), profile.getIdNumber()))
            throw new ServiceException("员工身份信息已变化，请重新匹配Excel");
        EmployeeSalaryValues salary = sources.amounts(snapshot);
        String error = salary.validationError();
        if (error != null) throw new ServiceException(error);
        return new Context(source, employee, profile, snapshot, salary);
    }

    private void checkOwner(HrOnboardSalarySource source, boolean lock)
    {
        List<HrOnboardSalaryTaskOwner> tasks = lock ? mapper.lockTasks(source) : mapper.readTasks(source);
        Long owner = source.ownerUserId;
        if (!tasks.isEmpty() || source.taskId != null)
        {
            if (tasks.size() != 1) throw new ServiceException("Excel签约任务来源不唯一或绑定已失效");
            var task = tasks.get(0);
            if (source.sourceEventVersion == null
                    || source.taskId != null && !Objects.equals(source.taskId, task.taskId)
                    || !Objects.equals(source.employeeId, task.employeeId)
                    || !"ONBOARD".equalsIgnoreCase(task.scenario)
                    || !"MANUAL_SIGN_EXCEL_IMPORT".equalsIgnoreCase(task.sourceType)
                    || !String.valueOf(source.rowId).equals(task.sourceBusinessId)
                    || !String.valueOf(source.sourceEventVersion).equals(task.sourceEventVersion))
                throw new ServiceException("Excel签约任务来源不规范，请重新核对导入行");
            owner = task.assignedHrUserId;
        }
        if (!SecurityUtils.isAdmin() && !Objects.equals(owner, SecurityUtils.getUserId()))
            throw new ServiceException("工资导入行不属于当前合同经办人");
    }

    private void validateRows(HrOnboardSalaryArchiveRequest request)
    {
        if (request == null || request.batchId() == null || request.batchId() <= 0 || request.rows() == null
                || request.rows().isEmpty() || request.rows().size() > 100)
            throw new ServiceException("请选择1至100条已匹配的工资导入行");
        Set<Long> ids = new HashSet<>();
        for (var row : request.rows())
            if (row == null || row.rowId() == null || row.rowId() <= 0 || row.version() == null
                    || row.version() < 0 || !ids.add(row.rowId()))
                throw new ServiceException("工资导入行编号、版本无效或重复");
    }
    private void validateConfirmation(HrOnboardSalaryArchiveRequest request)
    {
        validateRows(request);
        if (!Boolean.TRUE.equals(request.confirmed()) || request.requestId() == null
                || !request.requestId().matches("[A-Za-z0-9_-]{8,64}"))
            throw new ServiceException("请先预览并明确确认工资入档");
        if (request.effectiveDate() == null || request.effectiveDate().isAfter(LocalDate.now(ZoneId.of("Asia/Shanghai"))))
            throw new ServiceException("适用日期不能为空或晚于今天；本入口不自动执行未来调薪");
        if (request.reason() == null || request.reason().trim().length() < 4 || request.reason().length() > 500)
            throw new ServiceException("请填写4至500字的确认依据");
        for (var row : request.rows())
            if (row.expectedProfileHash() == null || !row.expectedProfileHash().matches("[a-f0-9]{64}"))
                throw new ServiceException("缺少已预览的工资版本，请重新预览");
    }
    private void requireVersion(HrOnboardSalarySource source, HrOnboardSalaryArchiveRequest.Row row)
    {
        if (!Objects.equals(source.version, row.version())) throw new ServiceException("工资导入行已变化，请刷新后确认");
    }
    private void requireSameCommand(EmployeeSalarySource source, HrOnboardSalaryArchiveRequest request,
            HrOnboardSalaryArchiveRequest.Row row)
    {
        if (!Objects.equals(source.operatorUserId, SecurityUtils.getUserId())
                || !Objects.equals(source.batchId, request.batchId()) || !Objects.equals(source.rowId, row.rowId())
                || !Objects.equals(source.requestHash, requestHash(request)))
            throw new ServiceException("工资确认请求号已用于其他内容，请先核对原操作");
    }
    private String command(HrOnboardSalaryArchiveRequest request, HrOnboardSalaryArchiveRequest.Row row)
    { return request.requestId() + ":" + row.rowId(); }
    private String requestHash(HrOnboardSalaryArchiveRequest request)
    {
        try { return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(write(request).getBytes(java.nio.charset.StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
    }
    private boolean same(String left, String right)
    { return left != null && right != null && !left.isBlank() && left.trim().equalsIgnoreCase(right.trim()); }
    private JsonNode parse(String value)
    {
        try { JsonNode result = json.readTree(value); if (result != null && result.isObject()) return result; }
        catch (Exception ignored) { }
        throw new ServiceException("工资导入来源数据损坏");
    }
    private String write(Object value)
    {
        try { return json.writeValueAsString(value); }
        catch (Exception error) { throw new ServiceException("工资确认记录序列化失败"); }
    }
    private record Context(HrOnboardSalarySource source, SysUser employee, SysUserProfile profile,
            JsonNode snapshot, EmployeeSalaryValues salary) { }
    public record Preview(Long rowId, Long version, Long employeeId, String employeeName,
            EmployeeSalaryValues proposed, EmployeeSalaryValues current, String expectedSourceId,
            String expectedProfileHash, LocalDate currentEffectiveDate, String contractStartDate,
            boolean confirmed, String sourceType) { }
    public record Result(int updated, int reused, String status, List<String> sourceIds) { }
}
