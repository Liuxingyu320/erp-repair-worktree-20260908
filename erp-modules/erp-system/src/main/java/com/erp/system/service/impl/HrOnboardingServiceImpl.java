package com.erp.system.service.impl;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.github.pagehelper.PageHelper;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.core.utils.uuid.IdUtils;
import com.erp.common.datascope.annotation.DataScope;
import com.erp.common.security.utils.SecurityUtils;
import com.erp.system.api.domain.SysUser;
import com.erp.system.api.domain.SysUserProfile;
import com.erp.system.api.domain.SysDept;
import com.erp.system.domain.HrOnboarding;
import com.erp.system.domain.HrOnboardingOperationLog;
import com.erp.system.domain.HrOnboardingImportRow;
import com.erp.system.domain.HrOnboardingPositionConfig;
import com.erp.system.domain.SysPost;
import com.erp.system.domain.vo.HrOnboardingCompletionVo;
import com.erp.system.domain.vo.HrOnboardingCreateRequest;
import com.erp.system.domain.vo.HrOnboardingDetailVo;
import com.erp.system.domain.vo.HrOnboardingListVo;
import com.erp.system.domain.vo.HrOnboardingOwnerOptionVo;
import com.erp.system.domain.vo.HrOnboardingOwnerQuery;
import com.erp.system.domain.vo.HrOnboardingQuery;
import com.erp.system.domain.vo.HrOnboardingSummaryVo;
import com.erp.system.domain.vo.HrOnboardingUpdateRequest;
import com.erp.system.exception.HrOnboardingValidationException;
import com.erp.system.mapper.HrOnboardingMapper;
import com.erp.system.mapper.HrOnboardingOperationLogMapper;
import com.erp.system.mapper.SysPostMapper;
import com.erp.system.service.IHrOnboardingService;
import com.erp.system.service.IHrOnboardingPositionConfigService;
import com.erp.system.support.HrOnboardingNoGenerator;
import com.erp.system.support.HrSensitiveFieldMasker;

@Service
public class HrOnboardingServiceImpl implements IHrOnboardingService
{
    private static final Pattern CANCEL_AUDIT_PATTERN = Pattern.compile(
            "category=(PERSONAL|NO_SHOW|DATA_CORRECTION|SCHEDULE_CHANGE|OTHER); auditRef=([0-9a-f]{32})");
    private static final Pattern MAINLAND_MOBILE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");
    private static final Map<String, List<String>> TRANSITIONS;
    static
    {
        Map<String, List<String>> transitions = new LinkedHashMap<>();
        transitions.put(HrOnboarding.STATUS_DRAFT, Arrays.asList(HrOnboarding.STATUS_READY, HrOnboarding.STATUS_CANCELLED));
        transitions.put(HrOnboarding.STATUS_READY, Arrays.asList(HrOnboarding.STATUS_DRAFT,
                HrOnboarding.STATUS_CONFIRMED, HrOnboarding.STATUS_CANCELLED));
        transitions.put(HrOnboarding.STATUS_CANCELLED, Collections.singletonList(HrOnboarding.STATUS_DRAFT));
        transitions.put(HrOnboarding.STATUS_CONFIRMED, Collections.emptyList());
        TRANSITIONS = Collections.unmodifiableMap(transitions);
    }

    private final HrOnboardingMapper mapper;
    private final HrOnboardingOperationLogMapper logMapper;
    private final HrOnboardingAccessService accessService;
    private final HrOnboardingRuleService rules;
    private final HrSensitiveFieldMasker masker;
    private final HrOnboardingNoGenerator noGenerator;
    private final Clock clock;
    private final LongSupplier currentUserId;
    private final SysPostMapper postMapper;
    private final IHrOnboardingPositionConfigService positionConfigService;

    @Autowired
    public HrOnboardingServiceImpl(HrOnboardingMapper mapper, HrOnboardingOperationLogMapper logMapper,
            HrOnboardingAccessService accessService, HrOnboardingRuleService rules,
            HrSensitiveFieldMasker masker, HrOnboardingNoGenerator noGenerator,
            SysPostMapper postMapper, IHrOnboardingPositionConfigService positionConfigService)
    {
        this(mapper, logMapper, accessService, rules, masker, noGenerator, Clock.systemDefaultZone(),
                SecurityUtils::getUserId, postMapper, positionConfigService);
    }

    HrOnboardingServiceImpl(HrOnboardingMapper mapper, HrOnboardingOperationLogMapper logMapper,
            HrOnboardingAccessService accessService, HrOnboardingRuleService rules,
            HrSensitiveFieldMasker masker, HrOnboardingNoGenerator noGenerator, Clock clock)
    {
        this(mapper, logMapper, accessService, rules, masker, noGenerator, clock, SecurityUtils::getUserId);
    }

    HrOnboardingServiceImpl(HrOnboardingMapper mapper, HrOnboardingOperationLogMapper logMapper,
            HrOnboardingAccessService accessService, HrOnboardingRuleService rules,
            HrSensitiveFieldMasker masker, HrOnboardingNoGenerator noGenerator, Clock clock,
            LongSupplier currentUserId)
    {
        this(mapper, logMapper, accessService, rules, masker, noGenerator, clock, currentUserId, null, null);
    }

    HrOnboardingServiceImpl(HrOnboardingMapper mapper, HrOnboardingOperationLogMapper logMapper,
            HrOnboardingAccessService accessService, HrOnboardingRuleService rules,
            HrSensitiveFieldMasker masker, HrOnboardingNoGenerator noGenerator, Clock clock,
            LongSupplier currentUserId, SysPostMapper postMapper,
            IHrOnboardingPositionConfigService positionConfigService)
    {
        this.mapper = mapper;
        this.logMapper = logMapper;
        this.accessService = accessService;
        this.rules = rules;
        this.masker = masker;
        this.noGenerator = noGenerator;
        this.clock = clock;
        this.currentUserId = currentUserId;
        this.postMapper = postMapper;
        this.positionConfigService = positionConfigService;
    }

    /** Shared with Task 6 confirmation; this method validates only the state machine. */
    public void validateTransition(String from, String to)
    {
        if (!TRANSITIONS.getOrDefault(from, Collections.emptyList()).contains(to))
        {
            throw new ServiceException("不允许的入职状态变更: " + from + " -> " + to);
        }
    }

    @Override
    @DataScope(deptAlias = "d")
    public List<HrOnboardingListVo> list(HrOnboardingQuery query)
    {
        requireQuery(query);
        List<HrOnboarding> rows = mapper.selectOnboardingList(query);
        if (rows == null) return Collections.emptyList();
        return rows.stream().map(this::toListVo).collect(Collectors.toList());
    }

    @Override
    public HrOnboardingDetailVo get(Long onboardingId)
    {
        return toDetailVo(accessService.findScoped(scopedQuery(onboardingId)));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public HrOnboardingDetailVo create(HrOnboardingCreateRequest input, String operator)
    {
        HrOnboarding row = fromCreate(input);
        row.setStatus(HrOnboarding.STATUS_DRAFT);
        row.setVersion(0);
        row.setSourceType("MANUAL");
        row.setCreateBy(operator);
        row.setUpdateBy(operator);
        validateCreate(row);
        accessService.validateTargets(row);

        for (int attempt = 0; attempt < 3; attempt++)
        {
            row.setOnboardingNo(noGenerator.next());
            try
            {
                mapper.insertOnboarding(row);
                log(row, "CREATE", null, HrOnboarding.STATUS_DRAFT, operator,
                        "employeeName,phoneNumber,expectedEntryDate,targetDeptId,targetPostId,employeeCategory,ownerUserId",
                        "创建入职单");
                return toDetailVo(accessService.findScoped(scopedQuery(row.getOnboardingId())));
            }
            catch (DataIntegrityViolationException failure)
            {
                if (!isOnboardingNoCollision(failure)) throw failure;
                if (attempt == 2) throw failure("ONBOARDING_NO_COLLISION", "入职单编号生成冲突，请重试");
            }
        }
        throw failure("ONBOARDING_NO_COLLISION", "入职单编号生成冲突，请重试");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public HrOnboarding createImported(HrOnboardingImportRow imported, String operator)
    {
        if (imported == null || imported.getPayload() == null)
            throw failure("IMPORT_PAYLOAD_INVALID", "导入暂存数据无效");
        HrOnboarding row = imported.getPayload();
        row.setStatus(HrOnboarding.STATUS_DRAFT);
        row.setVersion(0);
        row.setSourceType("IMPORT");
        row.setCreateBy(operator);
        row.setUpdateBy(operator);
        validateCreate(row);
        accessService.validateTargets(row);
        for (int attempt = 0; attempt < 3; attempt++)
        {
            row.setOnboardingNo(noGenerator.next());
            try
            {
                mapper.insertOnboarding(row);
                log(row, "IMPORT_CREATE", null, HrOnboarding.STATUS_DRAFT, operator,
                        "sourceRowNumber", "从入职导入暂存创建入职单");
                return row;
            }
            catch (DataIntegrityViolationException failure)
            {
                if (!isOnboardingNoCollision(failure) || attempt == 2) throw failure;
            }
        }
        throw failure("ONBOARDING_NO_COLLISION", "入职单编号生成冲突，请重试");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public HrOnboardingDetailVo update(Long onboardingId, HrOnboardingUpdateRequest input, String operator)
    {
        HrOnboarding row = accessService.lockScopedForUpdate(scopedQuery(onboardingId));
        requireVersion(row, input == null ? null : input.getVersion());
        if (!HrOnboarding.STATUS_DRAFT.equals(row.getStatus())) throw new ServiceException("仅草稿状态允许编辑");
        rejectMaskedValues(input);
        List<String> changed = applyPatch(row, input);
        validateChangedOptionalFields(row, changed);
        String previousDepartmentSupervisor = row.getDepartmentSupervisor();
        accessService.validateTargets(row);
        if (!Objects.equals(previousDepartmentSupervisor, row.getDepartmentSupervisor()))
            changed.add("departmentSupervisor");
        if (changed.isEmpty()) return toDetailVo(accessService.findScoped(scopedQuery(onboardingId)));
        row.setUpdateBy(operator);
        if (mapper.updateOnboardingByVersion(row) != 1) throw versionConflict();
        log(row, "UPDATE", HrOnboarding.STATUS_DRAFT, HrOnboarding.STATUS_DRAFT, operator,
                String.join(",", changed), "更新字段: " + String.join(",", changed));
        return toDetailVo(accessService.findScoped(scopedQuery(onboardingId)));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public HrOnboardingDetailVo markReady(Long onboardingId, Integer version, String operator)
    {
        HrOnboarding row = accessService.lockScopedForUpdate(scopedQuery(onboardingId));
        requireVersion(row, version);
        HrOnboardingCompletionVo completion = rules.evaluateReady(row);
        if (!completion.getMissingFields().isEmpty() || !completion.getBlockingCodes().isEmpty())
        {
            throw validationFailure(completion);
        }
        return transition(row, version, HrOnboarding.STATUS_READY, null, operator, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public HrOnboardingDetailVo returnToDraft(Long onboardingId, Integer version, String operator)
    {
        HrOnboarding row = accessService.lockScopedForUpdate(scopedQuery(onboardingId));
        requireVersion(row, version);
        return transition(row, version, HrOnboarding.STATUS_DRAFT, null, operator, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public HrOnboardingDetailVo cancel(Long onboardingId, Integer version, String reason, String operator)
    {
        if (reason == null || reason.trim().isEmpty())
        {
            throw new HrOnboardingValidationException("CANCEL_REASON_REQUIRED", "取消原因不能为空",
                    Collections.singletonMap("reason", "取消原因不能为空"), Collections.emptyList());
        }
        HrOnboarding row = accessService.lockScopedForUpdate(scopedQuery(onboardingId));
        requireVersion(row, version);
        String normalizedReason = normalizeReason(reason);
        String cancelAudit = "category=" + reasonCategory(normalizedReason)
                + "; auditRef=" + IdUtils.simpleUUID();
        return transition(row, version, HrOnboarding.STATUS_CANCELLED, normalizedReason, operator, cancelAudit);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public HrOnboardingDetailVo restore(Long onboardingId, Integer version, String operator)
    {
        HrOnboarding row = accessService.lockScopedForUpdate(scopedQuery(onboardingId));
        requireVersion(row, version);
        String restoreAudit = restoreAuditSummary(row.getOnboardingId());
        return transition(row, version, HrOnboarding.STATUS_DRAFT, null, operator, restoreAudit);
    }

    @Override
    @DataScope(deptAlias = "d")
    public HrOnboardingSummaryVo summary(HrOnboardingQuery query)
    {
        requireQuery(query);
        LocalDate today = LocalDate.now(clock);
        query.setSummaryDate(Date.from(today.atStartOfDay(clock.getZone()).toInstant()));
        HrOnboardingSummaryVo result = mapper.selectOnboardingSummary(query);
        if (result == null) result = new HrOnboardingSummaryVo();
        List<HrOnboarding> rows = mapper.selectTodayOnboardingTasks(query);
        if (rows == null) rows = Collections.emptyList();
        result.setTodayTasks(rows.stream().map(this::toSafeTodayListVo).collect(Collectors.toList()));
        return result;
    }

    @Override
    public Map<String, Object> formOptions()
    {
        return formOptions(true, true);
    }

    @Override
    public Map<String, Object> formOptions(boolean includeOwners, boolean includeSupervisors)
    {
        List<SysDept> departments = accessService.listScopedDepartments(new SysDept());
        boolean includePeople = includeOwners || includeSupervisors;
        List<SysUser> users = includePeople
                ? accessService.listScopedUsers(new SysUser())
                : Collections.emptyList();
        SysPost postQuery = new SysPost();
        postQuery.setStatus("0");
        List<SysPost> posts = postMapper.selectPostList(postQuery);
        if (posts == null) posts = Collections.emptyList();

        Map<String, Object> configOptions = positionConfigService.options();
        @SuppressWarnings("unchecked")
        Map<String, List<Map<String, Object>>> dictionaries =
                (Map<String, List<Map<String, Object>>>) configOptions.getOrDefault(
                        "dictionaryDefaults", Collections.emptyMap());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("organizations", departments.stream()
                .filter(dept -> !"STORE".equals(dept.getDeptType()))
                .map(dept -> option(dept.getDeptName(), dept.getDeptId())).collect(Collectors.toList()));
        result.put("stores", departments.stream().filter(dept -> "STORE".equals(dept.getDeptType()))
                .map(dept -> option(dept.getDeptName(), dept.getDeptId())).collect(Collectors.toList()));
        List<Map<String, Object>> people = users.stream()
                .map(user -> option(user.getNickName(), user.getUserId())).collect(Collectors.toList());
        result.put("posts", posts.stream().filter(post -> "0".equals(post.getStatus()))
                .map(post -> option(post.getPostName(), post.getPostId())).collect(Collectors.toList()));
        result.put("supervisors", includeSupervisors ? people : Collections.emptyList());
        result.put("owners", includeOwners ? new ArrayList<>(people) : Collections.emptyList());
        result.put("employeeCategories", dictionary(dictionaries, "employeeCategory"));
        result.put("sexOptions", dictionary(dictionaries, "sex"));
        result.put("idTypes", dictionary(dictionaries, "idType"));
        result.put("maritalStatuses", dictionary(dictionaries, "maritalStatus"));
        result.put("ethnicities", dictionary(dictionaries, "ethnicity"));
        result.put("workCityLevels", dictionary(dictionaries, "workCityLevel"));
        result.put("contractTypes", dictionary(dictionaries, "contractType"));
        result.put("socialTypes", dictionary(dictionaries, "socialType"));
        result.put("probationPeriods", dictionary(dictionaries, "probationPeriod"));
        HrOnboardingPositionConfig active = new HrOnboardingPositionConfig();
        active.setStatus("0");
        result.put("resolvedPositionDefaults", positionConfigService.list(active).stream()
                .map(this::resolvedDefaults).collect(Collectors.toList()));
        result.put("missingDictionaryMappings", configOptions.getOrDefault(
                "missingDictionaryMappings", Collections.emptyList()));
        return result;
    }

    @Override
    public List<HrOnboardingOwnerOptionVo> ownerOptions(HrOnboardingOwnerQuery query,
            int pageNum, int pageSize)
    {
        HrOnboardingOwnerQuery normalized = query == null ? new HrOnboardingOwnerQuery() : query;
        String keyword = normalized.getKeyword() == null ? "" : normalized.getKeyword().trim();
        if (keyword.length() > 64) throw new ServiceException("负责人搜索关键词不能超过64个字符");
        normalized.setKeyword(keyword);
        List<Long> userIds = normalized.getUserIds() == null
                ? Collections.emptyList()
                : normalized.getUserIds().stream()
                        .filter(Objects::nonNull)
                        .filter(id -> id > 0)
                        .distinct()
                        .collect(Collectors.toList());
        if (userIds.size() > 6) throw new ServiceException("负责人回显人员不能超过6个");
        normalized.setUserIds(userIds);
        if (normalized.getIncludeChildren() == null) normalized.setIncludeChildren(Boolean.TRUE);
        accessService.validateScopedOwnerDepartment(normalized);
        PageHelper.startPage(pageNum, pageSize);
        try
        {
            return accessService.listScopedOwnerOptions(normalized);
        }
        finally
        {
            PageHelper.clearPage();
        }
    }

    private List<Map<String, Object>> dictionary(Map<String, List<Map<String, Object>>> dictionaries,
            String key)
    {
        return dictionaries.getOrDefault(key, Collections.emptyList());
    }

    private Map<String, Object> resolvedDefaults(HrOnboardingPositionConfig config)
    {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("configId", config.getConfigId());
        result.put("postId", config.getPostId());
        result.put("employeeCategory", config.getEmployeeCategory());
        result.put("dataScopeStrategy", config.getDataScopeStrategy());
        result.put("contractTypeMode", config.getContractTypeMode());
        result.put("defaultContractType", config.getDefaultContractType());
        result.put("socialTypeMode", config.getSocialTypeMode());
        result.put("defaultSocialType", config.getDefaultSocialType());
        result.put("probationPeriodMode", config.getProbationPeriodMode());
        result.put("defaultProbationPeriod", config.getDefaultProbationPeriod());
        result.put("jobGrade", config.getJobGrade());
        result.put("accountEnabled", config.getAccountEnabled());
        result.put("roleIds", config.getRoleIds());
        return result;
    }

    private Map<String, Object> option(Object label, Object value)
    {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("label", label);
        result.put("value", value);
        return result;
    }

    private HrOnboardingDetailVo transition(HrOnboarding row, Integer version, String toStatus,
            String reason, String operator, String reasonAudit)
    {
        String fromStatus = row.getStatus();
        validateTransition(fromStatus, toStatus);
        String activeCancelReason = HrOnboarding.STATUS_CANCELLED.equals(toStatus) ? reason : null;
        if (mapper.updateOnboardingStatusByVersion(row.getOnboardingId(), fromStatus, toStatus,
                version, currentUserId.getAsLong(), operator, activeCancelReason) != 1) throw versionConflict();
        String summary = stateChangeSummary(fromStatus, toStatus, reasonAudit);
        log(row, "STATE_CHANGE", fromStatus, toStatus, operator, "status", summary);
        return toDetailVo(accessService.findScoped(scopedQuery(row.getOnboardingId())));
    }

    private HrOnboardingDetailVo toDetailVo(HrOnboarding row)
    {
        HrOnboardingDetailVo result = masker.toMaskedDetailVo(row);
        applyDecisionFields(result, row);
        HrOnboardingCompletionVo completion = rules.evaluateReady(row);
        result.setMissingOnboardingFields(completion.getGroupedMissingFields());
        result.setOnboardingCompletionPercent(completion.getPercentage());
        result.setOnboardingCompletedFieldCount(completion.getCompletedFieldCount());
        result.setOnboardingRequiredFieldCount(completion.getRequiredFieldCount());
        result.setPostEntryDueDate(completion.getPostEntryDueDate());
        result.setPostEntryOverdue(completion.getPostEntryOverdue());
        result.setReadinessBlockingCodes(completion.getBlockingCodes());
        List<String> risks = new ArrayList<>(completion.getRiskCodes());
        if (row.getAccountRiskCode() != null && !row.getAccountRiskCode().trim().isEmpty()) risks.add(row.getAccountRiskCode());
        result.setAccountConfigurationRiskCodes(risks.stream().distinct().collect(Collectors.toList()));
        HrOnboardingCompletionVo profileCompletion = rules.evaluateProfile(toProfileProjection(row));
        result.setMissingProfileFields(profileCompletion.getGroupedMissingFields());
        result.setProfileCompletionPercent(profileCompletion.getPercentage());
        List<HrOnboardingOperationLog> logs = logMapper.selectByOnboardingId(row.getOnboardingId());
        if (logs != null)
        {
            result.setOperationLogs(logs.stream().map(this::toLogVo).collect(Collectors.toList()));
        }
        return result;
    }

    private HrOnboardingListVo toListVo(HrOnboarding row)
    {
        HrOnboardingListVo result = masker.toListVo(row);
        applyDecisionFields(result, row);
        return result;
    }

    private HrOnboardingListVo toSafeTodayListVo(HrOnboarding row)
    {
        HrOnboardingListVo result = masker.toListVo(row);
        result.setMissingCount(row.getReadinessMissingCount() == null ? 0 : row.getReadinessMissingCount());
        List<String> actions;
        if (HrOnboarding.STATUS_CONFIRMED.equals(row.getStatus())) actions = new ArrayList<>();
        else if (HrOnboarding.STATUS_CANCELLED.equals(row.getStatus()))
            actions = new ArrayList<>(Collections.singletonList("RESTORE"));
        else if (HrOnboarding.STATUS_READY.equals(row.getStatus()))
            actions = new ArrayList<>(Arrays.asList("RETURN_TO_DRAFT", "CONFIRM", "CANCEL"));
        else
        {
            actions = new ArrayList<>(Collections.singletonList("EDIT"));
            if (Boolean.TRUE.equals(row.getMarkReadyAllowed())) actions.add("MARK_READY");
            actions.add("CANCEL");
        }
        result.setAllowedActions(actions);
        result.setCurrentAction(currentAction(row, actions));
        return result;
    }

    private void applyDecisionFields(HrOnboardingListVo result, HrOnboarding row)
    {
        HrOnboardingCompletionVo completion = rules.evaluateReady(row);
        result.setMissingCount(completion.getMissingFields().size());
        List<String> actions = allowedActions(row, completion);
        result.setAllowedActions(actions);
        result.setCurrentAction(currentAction(row, actions));
    }

    private List<String> allowedActions(HrOnboarding row, HrOnboardingCompletionVo completion)
    {
        if (HrOnboarding.STATUS_CONFIRMED.equals(row.getStatus())) return new ArrayList<>();
        if (HrOnboarding.STATUS_CANCELLED.equals(row.getStatus())) return new ArrayList<>(Collections.singletonList("RESTORE"));
        if (HrOnboarding.STATUS_READY.equals(row.getStatus()))
            return new ArrayList<>(Arrays.asList("RETURN_TO_DRAFT", "CONFIRM", "CANCEL"));
        List<String> actions = new ArrayList<>(Collections.singletonList("EDIT"));
        if (completion.getMissingFields().isEmpty() && completion.getBlockingCodes().isEmpty()) actions.add("MARK_READY");
        actions.add("CANCEL");
        return actions;
    }

    private String currentAction(HrOnboarding row, List<String> actions)
    {
        if (HrOnboarding.STATUS_CANCELLED.equals(row.getStatus())) return "RESTORE";
        if (HrOnboarding.STATUS_READY.equals(row.getStatus())) return "CONFIRM";
        if (HrOnboarding.STATUS_DRAFT.equals(row.getStatus())) return actions.contains("MARK_READY") ? "MARK_READY" : "EDIT";
        return null;
    }

    private HrOnboardingDetailVo.OperationLogVo toLogVo(HrOnboardingOperationLog source)
    {
        HrOnboardingDetailVo.OperationLogVo target = new HrOnboardingDetailVo.OperationLogVo();
        target.setOperationType(source.getOperationType());
        target.setOperatorName(source.getOperatorName());
        target.setFromStatus(source.getFromStatus());
        target.setToStatus(source.getToStatus());
        target.setChangedFieldKeys(Arrays.stream(Objects.toString(source.getChangedFieldKeys(), "").split(","))
                .map(String::trim).filter(value -> !value.isEmpty()).collect(Collectors.toList()));
        target.setOperationTime(source.getOperationTime());
        target.setSummary(source.getOperationSummary());
        return target;
    }

    private void log(HrOnboarding row, String type, String from, String to, String operator,
            String changedKeys, String summary)
    {
        HrOnboardingOperationLog log = new HrOnboardingOperationLog();
        log.setOnboardingId(row.getOnboardingId());
        log.setOperationType(type);
        log.setFromStatus(from);
        log.setToStatus(to);
        log.setOperatorName(operator);
        log.setOperatorUserId(currentUserId.getAsLong());
        log.setChangedFieldKeys(changedKeys);
        log.setOperationSummary(summary);
        log.setOperationTime(new Date(clock.millis()));
        logMapper.insertOperationLog(log);
    }

    private HrOnboarding fromCreate(HrOnboardingCreateRequest input)
    {
        if (input == null) throw failure("ONBOARDING_VALIDATION_FAILED", "新建参数不能为空");
        HrOnboarding row = new HrOnboarding();
        row.setEmployeeName(input.getEmployeeName()); row.setPhoneNumber(input.getPhoneNumber());
        row.setExpectedEntryDate(input.getExpectedEntryDate()); row.setTargetDeptId(input.getTargetDeptId());
        row.setTargetPostId(input.getTargetPostId()); row.setEmployeeCategory(input.getEmployeeCategory());
        row.setOwnerUserId(input.getOwnerUserId());
        return row;
    }

    private void validateCreate(HrOnboarding row)
    {
        HrOnboardingCompletionVo completion = rules.evaluateCreate(row);
        if (!completion.getMissingFields().isEmpty()) throw validationFailure(completion);
    }

    private HrOnboardingValidationException validationFailure(HrOnboardingCompletionVo completion)
    {
        Map<String, String> errors = new LinkedHashMap<>();
        for (int i = 0; i < completion.getMissingFields().size(); i++)
        {
            String label = i < completion.getMissingLabels().size() ? completion.getMissingLabels().get(i) : "必填字段";
            errors.put(completion.getMissingFields().get(i), label + "不能为空");
        }
        return new HrOnboardingValidationException("ONBOARDING_VALIDATION_FAILED", "请补全入职信息",
                errors, completion.getBlockingCodes());
    }

    private List<String> applyPatch(HrOnboarding row, HrOnboardingUpdateRequest in)
    {
        List<String> changed = new ArrayList<>();
        if (in == null) return changed;
        if (in.getEmployeeName() != null)
            applyStringPatch(changed, "employeeName", row.getEmployeeName(), in.getEmployeeName(), row::setEmployeeName);
        if (in.isPhoneNumberPresent())
            applyStringPatch(changed, "phoneNumber", row.getPhoneNumber(), in.getPhoneNumber(), row::setPhoneNumber);
        if (in.getTargetDeptId() != null)
            applyValuePatch(changed, "targetDeptId", row.getTargetDeptId(), in.getTargetDeptId(), row::setTargetDeptId);
        if (in.isTargetStoreIdPresent())
            applyValuePatch(changed, "targetStoreId", row.getTargetStoreId(), in.getTargetStoreId(), row::setTargetStoreId);
        if (in.getTargetPostId() != null)
            applyValuePatch(changed, "targetPostId", row.getTargetPostId(), in.getTargetPostId(), row::setTargetPostId);
        if (in.isDirectSupervisorUserIdPresent())
            applyValuePatch(changed, "directSupervisorUserId", row.getDirectSupervisorUserId(),
                    in.getDirectSupervisorUserId(), row::setDirectSupervisorUserId);
        if (in.getOwnerUserId() != null)
            applyValuePatch(changed, "ownerUserId", row.getOwnerUserId(), in.getOwnerUserId(), row::setOwnerUserId);
        if (in.getJobGrade() != null)
            applyStringPatch(changed, "jobGrade", row.getJobGrade(), in.getJobGrade(), row::setJobGrade);
        if (in.getEmployeeCategory() != null)
            applyStringPatch(changed, "employeeCategory", row.getEmployeeCategory(),
                    in.getEmployeeCategory(), row::setEmployeeCategory);
        if (in.getSex() != null)
            applyStringPatch(changed, "sex", row.getSex(), in.getSex(), row::setSex);
        if (in.isBirthDatePresent())
            applyValuePatch(changed, "birthDate", row.getBirthDate(), in.getBirthDate(), row::setBirthDate);
        if (in.getIdType() != null)
            applyStringPatch(changed, "idType", row.getIdType(), in.getIdType(), row::setIdType);
        if (in.isIdNumberPresent())
            applyStringPatch(changed, "idNumber", row.getIdNumber(), in.getIdNumber(), row::setIdNumber);
        if (in.isRegisteredResidencePresent())
            applyStringPatch(changed, "registeredResidence", row.getRegisteredResidence(),
                    in.getRegisteredResidence(), row::setRegisteredResidence);
        if (in.isCurrentAddressPresent())
            applyStringPatch(changed, "currentAddress", row.getCurrentAddress(),
                    in.getCurrentAddress(), row::setCurrentAddress);
        if (in.getMaritalStatus() != null)
            applyStringPatch(changed, "maritalStatus", row.getMaritalStatus(),
                    in.getMaritalStatus(), row::setMaritalStatus);
        if (in.getEthnicity() != null)
            applyStringPatch(changed, "ethnicity", row.getEthnicity(), in.getEthnicity(), row::setEthnicity);
        if (in.getEmergencyContact() != null)
            applyStringPatch(changed, "emergencyContact", row.getEmergencyContact(),
                    in.getEmergencyContact(), row::setEmergencyContact);
        if (in.getEmergencyContactRelation() != null)
            applyStringPatch(changed, "emergencyContactRelation", row.getEmergencyContactRelation(),
                    in.getEmergencyContactRelation(), row::setEmergencyContactRelation);
        if (in.isEmergencyContactPhonePresent())
            applyStringPatch(changed, "emergencyContactPhone", row.getEmergencyContactPhone(),
                    in.getEmergencyContactPhone(), row::setEmergencyContactPhone);
        if (in.getExpectedEntryDate() != null)
            applyValuePatch(changed, "expectedEntryDate", row.getExpectedEntryDate(),
                    in.getExpectedEntryDate(), row::setExpectedEntryDate);
        if (in.getWorkLocation() != null)
            applyStringPatch(changed, "workLocation", row.getWorkLocation(),
                    in.getWorkLocation(), row::setWorkLocation);
        if (in.getWorkCityLevel() != null)
            applyStringPatch(changed, "workCityLevel", row.getWorkCityLevel(),
                    in.getWorkCityLevel(), row::setWorkCityLevel);
        if (in.getBankName() != null)
            applyStringPatch(changed, "bankName", row.getBankName(), in.getBankName(), row::setBankName);
        if (in.isBankAccountPresent())
            applyStringPatch(changed, "bankAccount", row.getBankAccount(), in.getBankAccount(), row::setBankAccount);
        if (in.getContractType() != null)
            applyStringPatch(changed, "contractType", row.getContractType(),
                    in.getContractType(), row::setContractType);
        if (in.getSocialType() != null)
            applyStringPatch(changed, "socialType", row.getSocialType(), in.getSocialType(), row::setSocialType);
        if (in.getProbationPeriod() != null)
            applyStringPatch(changed, "probationPeriod", row.getProbationPeriod(),
                    in.getProbationPeriod(), row::setProbationPeriod);
        if (in.getLegalEntity() != null)
            applyStringPatch(changed, "legalEntity", row.getLegalEntity(),
                    in.getLegalEntity(), row::setLegalEntity);
        if (in.getRemark() != null)
            applyStringPatch(changed, "remark", row.getRemark(), in.getRemark(), row::setRemark);
        return changed;
    }

    private void validateChangedOptionalFields(HrOnboarding row, List<String> changed)
    {
        String emergencyPhone = normalizePatchString(row.getEmergencyContactPhone());
        if (changed.contains("emergencyContactPhone") && emergencyPhone != null
                && !MAINLAND_MOBILE_PATTERN.matcher(emergencyPhone).matches())
        {
            Map<String, String> errors = new LinkedHashMap<>();
            errors.put("emergencyContactPhone", "紧急联系人电话格式不正确");
            throw new HrOnboardingValidationException("ONBOARDING_VALIDATION_FAILED", "请修正入职信息",
                    errors, Collections.emptyList());
        }
    }

    private void applyStringPatch(List<String> changed, String field, String currentValue,
            String suppliedValue, Consumer<String> setter)
    {
        if (Objects.equals(normalizePatchString(currentValue), normalizePatchString(suppliedValue))) return;
        setter.accept(suppliedValue == null ? null : suppliedValue.trim());
        changed.add(field);
    }

    private <T> void applyValuePatch(List<String> changed, String field, T currentValue,
            T suppliedValue, Consumer<T> setter)
    {
        if (Objects.equals(currentValue, suppliedValue)) return;
        setter.accept(suppliedValue);
        changed.add(field);
    }

    private String normalizePatchString(String value)
    {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private void rejectMaskedValues(HrOnboardingUpdateRequest input)
    {
        if (input == null) return;
        Map<String, String> rejected = new LinkedHashMap<>();
        rejectMask(rejected, "phoneNumber", input.isPhoneNumberPresent() ? input.getPhoneNumber() : null);
        rejectMask(rejected, "idNumber", input.isIdNumberPresent() ? input.getIdNumber() : null);
        rejectMask(rejected, "registeredResidence", input.isRegisteredResidencePresent() ? input.getRegisteredResidence() : null);
        rejectMask(rejected, "currentAddress", input.isCurrentAddressPresent() ? input.getCurrentAddress() : null);
        rejectMask(rejected, "emergencyContactPhone", input.isEmergencyContactPhonePresent() ? input.getEmergencyContactPhone() : null);
        rejectMask(rejected, "bankAccount", input.isBankAccountPresent() ? input.getBankAccount() : null);
        if (!rejected.isEmpty())
            throw new HrOnboardingValidationException("MASKED_VALUE_NOT_ACCEPTED", "不能提交脱敏占位值", rejected,
                    Collections.singletonList("MASKED_VALUE_NOT_ACCEPTED"));
    }

    private void rejectMask(Map<String, String> errors, String field, String value)
    {
        if (value != null && value.codePoints().anyMatch(this::isMaskGlyph))
            errors.put(field, "不能提交脱敏占位值");
    }

    private boolean isMaskGlyph(int value)
    {
        return value == '*' || value == '＊' || value == '•' || value == '●' || value == '○'
                || value == '◯' || value == '◎' || value == '◉' || value == '◌' || value == '◍'
                || value == '◦' || value == '∙';
    }

    private void requireVersion(HrOnboarding row, Integer supplied)
    {
        if (supplied == null || !Objects.equals(row.getVersion(), supplied)) throw versionConflict();
    }

    private HrOnboardingValidationException versionConflict()
    {
        return failure("ONBOARDING_VERSION_CONFLICT", "入职单已被其他用户修改，请刷新后重试");
    }

    private HrOnboardingValidationException failure(String code, String message)
    {
        return new HrOnboardingValidationException(code, message);
    }

    private boolean isOnboardingNoCollision(Throwable failure)
    {
        for (Throwable current = failure; current != null; current = current.getCause())
            if (current.getMessage() != null && current.getMessage().contains("uk_hr_onboarding_no")) return true;
        return false;
    }

    private String normalizeReason(String reason)
    {
        if (reason == null) return null;
        String normalized = reason.replaceAll("[\\r\\n\\t]+", " ").trim();
        return normalized.length() <= 200 ? normalized : normalized.substring(0, 200);
    }

    private String stateChangeSummary(String fromStatus, String toStatus, String reasonAudit)
    {
        String summary = fromStatus + " -> " + toStatus;
        if (HrOnboarding.STATUS_CANCELLED.equals(toStatus))
            return summary + "; 取消原因已记录，内容未写入日志; 原因审计: "
                    + reasonAudit;
        if (HrOnboarding.STATUS_CANCELLED.equals(fromStatus) && HrOnboarding.STATUS_DRAFT.equals(toStatus))
            return summary + "; 取消记录已恢复，原原因未写入日志; 原因审计: "
                    + reasonAudit;
        return summary;
    }

    private String restoreAuditSummary(Long onboardingId)
    {
        List<HrOnboardingOperationLog> logs = logMapper.selectByOnboardingId(onboardingId);
        if (logs != null)
        {
            for (int index = logs.size() - 1; index >= 0; index--)
            {
                HrOnboardingOperationLog candidate = logs.get(index);
                if (candidate == null || !HrOnboarding.STATUS_CANCELLED.equals(candidate.getToStatus())) continue;
                if (candidate.getLogId() == null || candidate.getOperationSummary() == null) break;
                Matcher audit = CANCEL_AUDIT_PATTERN.matcher(candidate.getOperationSummary());
                if (audit.find())
                    return "sourceCancelLogId=" + candidate.getLogId()
                            + "; category=" + audit.group(1) + "; auditRef=" + audit.group(2);
                break;
            }
        }
        throw new ServiceException("取消审计记录缺失，无法恢复");
    }

    private String reasonCategory(String reason)
    {
        if (containsAny(reason, "个人", "家庭", "健康", "身体", "私事")) return "PERSONAL";
        if (containsAny(reason, "未到", "未报到", "爽约", "失联", "未入职")) return "NO_SHOW";
        if (containsAny(reason, "重复", "数据", "更正", "纠正", "信息错误", "录入错误"))
            return "DATA_CORRECTION";
        if (containsAny(reason, "改期", "延期", "时间调整", "日程", "计划变更")) return "SCHEDULE_CHANGE";
        return "OTHER";
    }

    private boolean containsAny(String value, String... keywords)
    {
        for (String keyword : keywords) if (value.contains(keyword)) return true;
        return false;
    }

    private SysUser toProfileProjection(HrOnboarding row)
    {
        SysUser user = new SysUser();
        user.setNickName(row.getEmployeeName());
        user.setPhonenumber(row.getPhoneNumber());
        user.setSex(row.getSex());
        SysUserProfile profile = new SysUserProfile();
        profile.setEmployeeNo(row.getEmployeeNo());
        profile.setCompanyName(row.getCompanyName());
        profile.setDeptLevel1Name(row.getDeptLevel1Name());
        profile.setDeptLevel2Name(row.getDeptLevel2Name());
        profile.setDeptLevel3Name(row.getDeptLevel3Name());
        profile.setStoreName(row.getStoreName());
        profile.setPositionNames(row.getPositionName());
        profile.setJobGrade(row.getJobGrade());
        profile.setDepartmentSupervisor(row.getDepartmentSupervisor());
        profile.setEmployeeCategory(row.getEmployeeCategory());
        profile.setBirthDate(row.getBirthDate());
        profile.setIdType(row.getIdType());
        profile.setIdNumber(row.getIdNumber());
        profile.setRegisteredResidence(row.getRegisteredResidence());
        profile.setCurrentAddress(row.getCurrentAddress());
        profile.setMaritalStatus(row.getMaritalStatus());
        profile.setEthnicity(row.getEthnicity());
        profile.setEmergencyContact(row.getEmergencyContact());
        profile.setEmergencyContactRelation(row.getEmergencyContactRelation());
        profile.setEmergencyContactPhone(row.getEmergencyContactPhone());
        profile.setWorkLocation(row.getWorkLocation());
        profile.setWorkCityLevel(row.getWorkCityLevel());
        profile.setBankName(row.getBankName());
        profile.setBankAccount(row.getBankAccount());
        profile.setContractType(row.getContractType());
        profile.setSocialType(row.getSocialType());
        profile.setLegalEntity(row.getLegalEntity());
        user.setProfile(profile);
        return user;
    }

    private HrOnboardingQuery scopedQuery(Long onboardingId)
    {
        HrOnboardingQuery query = new HrOnboardingQuery();
        query.setOnboardingId(onboardingId);
        return query;
    }

    private void requireQuery(HrOnboardingQuery query)
    {
        if (query == null) throw new ServiceException("查询参数不能为空");
    }
}
