package com.erp.system.service.impl;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.erp.common.core.domain.todo.TodoConstants;
import com.erp.common.core.domain.todo.TodoItem;
import com.erp.common.core.domain.todo.TodoKeys;
import com.erp.common.core.domain.todo.TodoQuery;
import com.erp.common.core.domain.todo.TodoSummary;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.security.auth.AuthUtil;
import com.erp.common.security.utils.SecurityUtils;
import com.erp.system.constant.SysTodoTypes;
import com.erp.system.api.domain.SysDept;
import com.erp.system.api.domain.SysUser;
import com.erp.system.domain.vo.SysTodoCandidateRow;
import com.erp.system.domain.vo.SysHealthCertificateTodoCandidate;
import com.erp.system.domain.vo.SysTodoPage;
import com.erp.system.mapper.SysTodoMapper;
import com.erp.system.service.ISysConfigService;
import com.erp.system.service.ISysTodoService;
import com.erp.system.service.ISysUserShopService;
import com.erp.system.support.HrEmployeeCompletenessEvaluator;

@Service
public class SysTodoServiceImpl implements ISysTodoService
{
    static final String CONTRACT_WARNING_DAYS_KEY = "todo.contract.warning.days";
    static final String CONTRACT_URGENT_DAYS_KEY = "todo.contract.urgent.days";
    static final String HEALTH_CERT_WARNING_DAYS_KEY = "todo.health-certificate.warning-days";
    static final String APPROVAL_URGENT_HOURS_KEY = "todo.approval.urgent.hours";
    static final String SUMMARY_RECENT_LIMIT_KEY = "todo.summary.recent.limit";
    static final int DEFAULT_WARNING_DAYS = 30;
    static final int DEFAULT_URGENT_DAYS = 7;
    static final int DEFAULT_APPROVAL_URGENT_HOURS = 24;
    static final int DEFAULT_RECENT_LIMIT = 5;

    private final SysTodoMapper todoMapper;
    private final ISysUserShopService userShopService;
    private final HrEmployeeCompletenessEvaluator completenessEvaluator;
    private final ISysConfigService configService;
    private HrHealthCertificateFeatureService healthCertificateFeatureService;

    @Autowired
    public SysTodoServiceImpl(SysTodoMapper todoMapper, ISysUserShopService userShopService,
            HrEmployeeCompletenessEvaluator completenessEvaluator, ISysConfigService configService)
    {
        this.todoMapper = todoMapper;
        this.userShopService = userShopService;
        this.completenessEvaluator = completenessEvaluator;
        this.configService = configService;
    }

    @Autowired(required = false)
    public void setHealthCertificateFeatureService(
            HrHealthCertificateFeatureService healthCertificateFeatureService)
    {
        this.healthCertificateFeatureService = healthCertificateFeatureService;
    }

    @Override
    public TodoSummary selectSummary(TodoQuery query, Long selectedDeptId)
    {
        TodoQuery safeQuery = validateQuery(query);
        List<TodoItem> items = buildTodos(safeQuery, selectedDeptId);
        TodoSummary summary = new TodoSummary();
        summary.setSource(TodoConstants.SOURCE_SYSTEM);
        Map<String, Long> typeCounts = new LinkedHashMap<>();
        for (TodoItem item : items)
        {
            long count = 1L;
            typeCounts.merge(item.getType(), count, Long::sum);
            addCategory(summary, item.getCategory(), count);
            addPriority(summary, item.getPriority(), count);
        }
        summary.setTypeCounts(typeCounts);
        summary.recalculateTotal();
        int recentLimit = loadBoundedConfig(SUMMARY_RECENT_LIMIT_KEY, DEFAULT_RECENT_LIMIT, 1, 20);
        summary.setRecent(items.subList(0, Math.min(recentLimit, items.size())));
        return summary;
    }

    @Override
    public SysTodoPage selectTodoPage(TodoQuery query, Long selectedDeptId)
    {
        TodoQuery safeQuery = validateQuery(query);
        List<TodoItem> items = buildTodos(safeQuery, selectedDeptId);
        int from = Math.min((safeQuery.getPageNum() - 1) * safeQuery.getPageSize(), items.size());
        int to = Math.min(from + safeQuery.getPageSize(), items.size());
        return new SysTodoPage(items.subList(from, to), items.size());
    }

    protected Set<String> resolveEnabledTypes()
    {
        Set<String> enabled = new LinkedHashSet<>();
        addIf(enabled, SysTodoTypes.HR_PROFILE_INCOMPLETE,
                all("hr:completeness:list", "hr:employee:list", "hr:employee:edit"));
        addIf(enabled, SysTodoTypes.HR_ONBOARDING_CONFIRM,
                all("hr:onboarding:list", "hr:onboarding:confirm", "hr:employee:edit"));
        addIf(enabled, SysTodoTypes.HR_CONTRACT_DUE,
                all("hr:employee:list", "hr:employee:edit"));
        addIf(enabled, SysTodoTypes.HR_OFFBOARD_ACCOUNT,
                all("hr:employee:list", "system:user:edit"));
        addIf(enabled, SysTodoTypes.HR_HEALTH_CERT_DUE,
                all("hr:healthCertificate:list", "hr:healthCertificate:remind"));
        addIf(enabled, SysTodoTypes.HR_HEALTH_CERT_REVIEW,
                all("hr:healthCertificate:list", "hr:healthCertificate:query",
                        "hr:healthCertificate:review"));
        addIf(enabled, SysTodoTypes.HR_HEALTH_CERT_RETURNED,
                all("hr:healthCertificate:self:edit", "hr:healthCertificate:self:submit")
                        && healthCertificateIntakeEnabled());
        return enabled;
    }

    protected Long currentUserId() { return SecurityUtils.getUserId(); }
    protected boolean currentUserIsAdmin() { return SecurityUtils.isAdmin(); }
    protected boolean hasPermission(String permission) { return AuthUtil.hasPermi(permission); }
    protected LocalDate currentDate() { return LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")); }
    protected long currentTimeMillis() { return System.currentTimeMillis(); }

    protected boolean healthCertificateIntakeEnabled()
    {
        try
        {
            return healthCertificateFeatureService != null
                    && healthCertificateFeatureService.capability().isIntakeEnabled();
        }
        catch (RuntimeException ignored)
        {
            return false;
        }
    }

    private TodoQuery validateQuery(TodoQuery query)
    {
        TodoQuery safe = query == null ? new TodoQuery() : query;
        if (safe.getCategory() != null && !TodoConstants.CATEGORIES.contains(safe.getCategory()))
        {
            throw new ServiceException("不支持的待办分类");
        }
        if (safe.getPriority() != null && !TodoConstants.PRIORITIES.contains(safe.getPriority()))
        {
            throw new ServiceException("不支持的待办优先级");
        }
        String scopeMode = safe.getScopeMode();
        if (scopeMode == null || scopeMode.isBlank()
                || TodoConstants.SCOPE_CURRENT_ORG.equals(scopeMode)
                || TodoConstants.SCOPE_ALL_AUTHORIZED.equals(scopeMode))
        {
            safe.setScopeMode(TodoConstants.SCOPE_ACTIONABLE);
        }
        else if (!TodoConstants.SCOPE_ACTIONABLE.equals(scopeMode))
        {
            throw new ServiceException("不支持的待办组织范围");
        }
        if (safe.getSource() != null && !TodoConstants.SOURCE_SYSTEM.equals(safe.getSource()))
        {
            if (!TodoConstants.SOURCE_INVENTORY.equals(safe.getSource())
                    && !TodoConstants.SOURCE_OA.equals(safe.getSource()))
            {
                throw new ServiceException("不支持的待办来源");
            }
        }
        return safe;
    }

    private List<TodoItem> buildTodos(TodoQuery query, Long selectedDeptId)
    {
        Set<String> enabledTypes = query.getSource() == null
                || TodoConstants.SOURCE_SYSTEM.equals(query.getSource())
                ? resolveEnabledTypes() : Collections.emptySet();
        if (enabledTypes.isEmpty())
        {
            return new ArrayList<>();
        }
        ScopeContext scope = buildScope(query, selectedDeptId);
        Set<String> employeeTypes = new LinkedHashSet<>(enabledTypes);
        employeeTypes.remove(SysTodoTypes.HR_HEALTH_CERT_REVIEW);
        employeeTypes.remove(SysTodoTypes.HR_HEALTH_CERT_RETURNED);
        List<SysTodoCandidateRow> candidates = employeeTypes.isEmpty()
                ? Collections.emptyList()
                : todoMapper.selectScopedTodoCandidates(
                        query, employeeTypes, scope.currentDeptIds, scope.authorizedDeptIds, currentDate());
        Set<String> healthWorkflowTypes = new LinkedHashSet<>();
        if (enabledTypes.contains(SysTodoTypes.HR_HEALTH_CERT_REVIEW))
        {
            healthWorkflowTypes.add(SysTodoTypes.HR_HEALTH_CERT_REVIEW);
        }
        if (enabledTypes.contains(SysTodoTypes.HR_HEALTH_CERT_RETURNED))
        {
            healthWorkflowTypes.add(SysTodoTypes.HR_HEALTH_CERT_RETURNED);
        }
        List<SysHealthCertificateTodoCandidate> healthCandidates = healthWorkflowTypes.isEmpty()
                ? Collections.emptyList()
                : todoMapper.selectHealthCertificateTodoCandidates(
                        query, healthWorkflowTypes, scope.currentDeptIds,
                        scope.authorizedDeptIds, currentUserId());
        int warningDays = loadBoundedConfig(CONTRACT_WARNING_DAYS_KEY, DEFAULT_WARNING_DAYS, 1, 365);
        int urgentDays = Math.min(warningDays,
                loadBoundedConfig(CONTRACT_URGENT_DAYS_KEY, DEFAULT_URGENT_DAYS, 0, 90));
        int healthWarningDays = loadMaxThresholdConfig(HEALTH_CERT_WARNING_DAYS_KEY,
                DEFAULT_WARNING_DAYS, 1, 365);
        int approvalUrgentHours = loadBoundedConfig(APPROVAL_URGENT_HOURS_KEY,
                DEFAULT_APPROVAL_URGENT_HOURS, 1, 168);
        Map<String, TodoGroup> groups = new LinkedHashMap<>();
        for (SysTodoCandidateRow row : candidates == null ? Collections.<SysTodoCandidateRow>emptyList() : candidates)
        {
            if (row == null || row.getDeptId() == null || row.getDeptId() <= 0)
            {
                continue;
            }
            if (enabledTypes.contains(SysTodoTypes.HR_PROFILE_INCOMPLETE) && isIncomplete(row))
            {
                add(groups, row, SysTodoTypes.HR_PROFILE_INCOMPLETE, TodoConstants.CATEGORY_RISK,
                        TodoConstants.PRIORITY_NORMAL);
            }
            if (enabledTypes.contains(SysTodoTypes.HR_ONBOARDING_CONFIRM) && isPendingOnboarding(row))
            {
                add(groups, row, SysTodoTypes.HR_ONBOARDING_CONFIRM, TodoConstants.CATEGORY_EXECUTION,
                        TodoConstants.PRIORITY_NORMAL);
            }
            if (enabledTypes.contains(SysTodoTypes.HR_CONTRACT_DUE))
            {
                Long days = contractDays(row, warningDays);
                if (days != null)
                {
                    add(groups, row, SysTodoTypes.HR_CONTRACT_DUE, TodoConstants.CATEGORY_RISK,
                            days <= urgentDays ? TodoConstants.PRIORITY_URGENT : TodoConstants.PRIORITY_IMPORTANT);
                }
            }
            if (enabledTypes.contains(SysTodoTypes.HR_OFFBOARD_ACCOUNT) && isEnabledOffboardAccount(row))
            {
                add(groups, row, SysTodoTypes.HR_OFFBOARD_ACCOUNT, TodoConstants.CATEGORY_RISK,
                        TodoConstants.PRIORITY_IMPORTANT);
            }
            if (enabledTypes.contains(SysTodoTypes.HR_HEALTH_CERT_DUE))
            {
                Long days = healthCertificateDays(row, healthWarningDays);
                if (days != null)
                {
                    add(groups, row, SysTodoTypes.HR_HEALTH_CERT_DUE,
                            TodoConstants.CATEGORY_RISK,
                            days <= urgentDays ? TodoConstants.PRIORITY_URGENT
                                    : TodoConstants.PRIORITY_IMPORTANT);
                }
            }
        }
        List<TodoItem> personalItems = new ArrayList<>();
        for (SysHealthCertificateTodoCandidate row : healthCandidates == null
                ? Collections.<SysHealthCertificateTodoCandidate>emptyList() : healthCandidates)
        {
            if (row == null || row.getCertificateId() == null || row.getCertificateId() <= 0)
            {
                continue;
            }
            if (enabledTypes.contains(SysTodoTypes.HR_HEALTH_CERT_REVIEW)
                    && "PENDING_REVIEW".equals(row.getReviewStatus())
                    && row.getDeptId() != null && row.getDeptId() > 0)
            {
                addHealthReview(groups, row,
                        isApprovalUrgent(row.getCreateTime(), approvalUrgentHours)
                                ? TodoConstants.PRIORITY_URGENT
                                : TodoConstants.PRIORITY_IMPORTANT);
            }
            if (enabledTypes.contains(SysTodoTypes.HR_HEALTH_CERT_RETURNED)
                    && ("REJECTED".equals(row.getReviewStatus())
                            || "RETURNED".equals(row.getReviewStatus()))
                    && currentUserId().equals(row.getUserId()))
            {
                personalItems.add(toReturnedHealthCertificateItem(row));
            }
        }
        Map<String, String> searchTerms = new LinkedHashMap<>();
        List<TodoItem> items = groups.values().stream().map(group -> {
            TodoItem item = toItem(group, query);
            searchTerms.put(item.getTodoKey(), group.searchText.toString());
            return item;
        }).collect(Collectors.toCollection(ArrayList::new));
        items.addAll(personalItems);
        return items.stream()
                .filter(item -> query.getType() == null || query.getType().equals(item.getType()))
                .filter(item -> query.getCategory() == null || query.getCategory().equals(item.getCategory()))
                .filter(item -> matchesKeyword(item,
                        searchTerms.get(item.getTodoKey()), query.getKeyword()))
                .sorted(todoComparator()).collect(Collectors.toCollection(ArrayList::new));
    }

    private ScopeContext buildScope(TodoQuery query, Long selectedDeptId)
    {
        Long userId = currentUserId();
        if (userId == null)
        {
            throw new ServiceException("无法识别当前用户");
        }
        boolean admin = currentUserIsAdmin();
        List<Long> authorized = normalize(admin
                ? todoMapper.selectAllActiveHrDeptIds()
                : userShopService.selectShopDeptIdsByUserId(userId));
        List<Long> current = Collections.emptyList();
        if (TodoConstants.SCOPE_CURRENT_ORG.equals(query.getScopeMode())
                && selectedDeptId != null && selectedDeptId > 0)
        {
            current = List.of(selectedDeptId);
        }
        return new ScopeContext(current, authorized);
    }

    private boolean isIncomplete(SysTodoCandidateRow row)
    {
        if ("取消入职".equals(row.getOnboardingStatus()) || "离职".equals(row.getEmployeeStatus()))
        {
            return false;
        }
        return completenessEvaluator.evaluate(toEmployeeProjection(row)).getRequiredCompletionPercent() < 100;
    }

    private SysUser toEmployeeProjection(SysTodoCandidateRow row)
    {
        SysUser user = new SysUser();
        user.setUserId(row.getUserId());
        user.setDeptId(row.getDeptId());
        user.setNickName(row.getEmployeeName());
        user.setPhonenumber(row.getPhoneNumber());
        user.setEmail(row.getEmployeeEmail());
        user.setSex(row.getEmployeeSex());
        user.setRemark(row.getEmployeeRemark());
        user.setPostNames(row.getPostNames());
        user.setProfile(row);
        SysDept dept = new SysDept();
        dept.setDeptId(row.getDeptId());
        dept.setDeptName(row.getDeptName());
        dept.setDeptType(row.getDeptType());
        dept.setLeader(row.getDeptLeader());
        dept.setStatus(row.getDeptStatus());
        user.setDept(dept);
        return user;
    }

    private boolean isPendingOnboarding(SysTodoCandidateRow row)
    {
        return "待入职".equals(row.getEmployeeStatus()) && "待确认".equals(row.getOnboardingStatus());
    }

    private Long contractDays(SysTodoCandidateRow row, int warningDays)
    {
        if (row.getContractEndDate() == null || "离职".equals(row.getEmployeeStatus()))
        {
            return null;
        }
        LocalDate end = toLocalDate(row.getContractEndDate());
        long days = ChronoUnit.DAYS.between(currentDate(), end);
        return days >= 0 && days <= warningDays ? days : null;
    }

    private boolean isEnabledOffboardAccount(SysTodoCandidateRow row)
    {
        boolean offboarding = "待离职".equals(row.getEmployeeStatus()) || "离职".equals(row.getEmployeeStatus());
        return offboarding && row.getUserId() != null
                && "0".equals(row.getLinkedAccountStatus()) && "0".equals(row.getLinkedAccountDelFlag());
    }

    private Long healthCertificateDays(SysTodoCandidateRow row,
            int warningDays)
    {
        if (row.getHealthCertificateExpiresOn() == null
                || "离职".equals(row.getEmployeeStatus())) return null;
        long days = ChronoUnit.DAYS.between(currentDate(),
                toLocalDate(row.getHealthCertificateExpiresOn()));
        return days <= warningDays ? days : null;
    }

    private void add(Map<String, TodoGroup> groups, SysTodoCandidateRow row,
            String type, String category, String priority)
    {
        String key = row.getDeptId() + ":" + type;
        TodoGroup group = groups.computeIfAbsent(key,
                ignored -> new TodoGroup(row, type, category, priority));
        group.count++;
        group.addSearchTerms(row);
        group.createdTime = earlier(group.createdTime, row.getCreateTime());
        if (priorityRank(priority) < priorityRank(group.priority))
        {
            group.priority = priority;
        }
    }

    private void addHealthReview(Map<String, TodoGroup> groups,
            SysHealthCertificateTodoCandidate row, String priority)
    {
        String key = row.getDeptId() + ":" + SysTodoTypes.HR_HEALTH_CERT_REVIEW;
        TodoGroup group = groups.computeIfAbsent(key,
                ignored -> new TodoGroup(row, SysTodoTypes.HR_HEALTH_CERT_REVIEW,
                        TodoConstants.CATEGORY_APPROVAL, priority));
        group.count++;
        group.addSearchTerms(row);
        group.createdTime = earlier(group.createdTime, row.getCreateTime());
        if (priorityRank(priority) < priorityRank(group.priority))
        {
            group.priority = priority;
        }
    }

    private boolean isApprovalUrgent(Date createdTime, int urgentHours)
    {
        return createdTime != null
                && currentTimeMillis() - createdTime.getTime() >= urgentHours * 3600_000L;
    }

    private TodoItem toReturnedHealthCertificateItem(
            SysHealthCertificateTodoCandidate row)
    {
        TodoItem item = new TodoItem();
        item.setSource(TodoConstants.SOURCE_SYSTEM);
        item.setType(SysTodoTypes.HR_HEALTH_CERT_RETURNED);
        item.setCategory(TodoConstants.CATEGORY_RETURNED);
        item.setBusinessId(row.getCertificateId());
        item.setBusinessNo(row.getCertificateNo() == null || row.getCertificateNo().isBlank()
                ? "HEALTH-CERT-" + row.getCertificateId() : row.getCertificateNo());
        item.setTitle("健康证审核被驳回");
        String employee = row.getEmployeeName() == null || row.getEmployeeName().isBlank()
                ? "本人" : row.getEmployeeName();
        String reason = row.getRejectionReason() == null || row.getRejectionReason().isBlank()
                ? "请修改后重新提交" : row.getRejectionReason();
        item.setSummary(employee + " · " + reason);
        item.setStatus("REJECTED");
        item.setPriority(TodoConstants.PRIORITY_IMPORTANT);
        Date createdTime = row.getUpdateTime() == null ? row.getCreateTime() : row.getUpdateTime();
        item.setCreatedTime(createdTime);
        item.setWaitingSeconds(waitingSeconds(createdTime));
        item.setRouteType("resubmit_health_certificate");
        item.setRequiredPermission("hr:healthCertificate:self:edit");
        item.setTodoKey(TodoKeys.build(TodoConstants.SOURCE_SYSTEM,
                SysTodoTypes.HR_HEALTH_CERT_RETURNED, row.getCertificateId(), "resubmit"));
        Map<String, String> params = new LinkedHashMap<>();
        params.put("certificateId", String.valueOf(row.getCertificateId()));
        params.put("healthCertificateView", "mine");
        params.put("reviewStatus", "REJECTED");
        item.setRouteParams(params);
        return item;
    }

    private TodoItem toItem(TodoGroup group, TodoQuery query)
    {
        TodoItem item = new TodoItem();
        item.setSource(TodoConstants.SOURCE_SYSTEM);
        item.setType(group.type);
        item.setCategory(group.category);
        item.setBusinessId(group.deptId);
        item.setBusinessNo(group.deptId + "-" + group.type);
        item.setTitle(title(group.type, group.deptName));
        item.setSummary("共 " + group.count + " 人待处理");
        item.setStatus("pending");
        item.setPriority(group.priority);
        item.setCreatedTime(group.createdTime);
        item.setWaitingSeconds(waitingSeconds(group.createdTime));
        item.setDeptId(group.deptId);
        item.setDeptName(group.deptName);
        item.setDeptType(group.deptType);
        String scopeMode = TodoConstants.SCOPE_ACTIONABLE;
        item.setScopeMode(scopeMode);
        item.setRouteType(group.type);
        item.setRequiredPermission(requiredPermission(group.type));
        item.setTodoKey(TodoKeys.build(TodoConstants.SOURCE_SYSTEM, group.type, group.deptId, "manage"));
        Map<String, String> params = new LinkedHashMap<>();
        params.put("count", String.valueOf(group.count));
        params.put("affectedCount", String.valueOf(group.count));
        params.put("contextDeptId", String.valueOf(group.deptId));
        params.put("contextDeptName", group.deptName == null ? "" : group.deptName);
        params.put("contextDeptType", group.deptType == null ? "" : group.deptType);
        params.put("scopeMode", scopeMode);
        addFilterParams(params, group.type, group.deptId);
        item.setRouteParams(params);
        return item;
    }

    private void addFilterParams(Map<String, String> params, String type, Long deptId)
    {
        if (SysTodoTypes.HR_PROFILE_INCOMPLETE.equals(type))
        {
            params.put("deptId", String.valueOf(deptId));
            params.put("completenessStatus", "INCOMPLETE");
            params.put("completenessMetric", "REQUIRED");
        }
        else if (SysTodoTypes.HR_ONBOARDING_CONFIRM.equals(type))
        {
            params.put("targetDeptId", String.valueOf(deptId));
            params.put("status", "READY");
        }
        else if (SysTodoTypes.HR_CONTRACT_DUE.equals(type))
        {
            params.put("deptId", String.valueOf(deptId));
            params.put("contractDue", "true");
        }
        else if (SysTodoTypes.HR_OFFBOARD_ACCOUNT.equals(type))
        {
            params.put("deptId", String.valueOf(deptId));
            params.put("offboardAccountOnly", "true");
        }
        else if (SysTodoTypes.HR_HEALTH_CERT_DUE.equals(type))
        {
            params.put("deptId", String.valueOf(deptId));
            params.put("currentDeptId", String.valueOf(deptId));
            params.put("healthCertificateStatus", "EXPIRING_OR_EXPIRED");
            params.put("healthCertificateView", "admin");
        }
        else if (SysTodoTypes.HR_HEALTH_CERT_REVIEW.equals(type))
        {
            params.put("deptId", String.valueOf(deptId));
            params.put("currentDeptId", String.valueOf(deptId));
            params.put("reviewStatus", "PENDING_REVIEW");
            params.put("healthCertificateView", "admin");
        }
    }

    private int loadBoundedConfig(String key, int fallback, int min, int max)
    {
        try
        {
            String value = configService.selectConfigByKey(key);
            int parsed = Integer.parseInt(value);
            return parsed >= min && parsed <= max ? parsed : fallback;
        }
        catch (Exception ignored)
        {
            return fallback;
        }
    }

    private int loadMaxThresholdConfig(String key, int fallback, int min, int max)
    {
        try
        {
            int highest = Integer.MIN_VALUE;
            for (String token : configService.selectConfigByKey(key).split(","))
            {
                int value = Integer.parseInt(token.trim());
                if (value >= min && value <= max)
                {
                    highest = Math.max(highest, value);
                }
            }
            return highest == Integer.MIN_VALUE ? fallback : highest;
        }
        catch (Exception ignored)
        {
            return fallback;
        }
    }

    private boolean all(String... permissions)
    {
        for (String permission : permissions)
        {
            if (!hasPermission(permission)) return false;
        }
        return true;
    }

    private void addIf(Set<String> enabled, String type, boolean allowed)
    {
        if (allowed) enabled.add(type);
    }

    private List<Long> normalize(List<Long> ids)
    {
        if (ids == null) return Collections.emptyList();
        return ids.stream().filter(id -> id != null && id > 0).distinct().collect(Collectors.toList());
    }

    private LocalDate toLocalDate(Date value)
    {
        if (value instanceof java.sql.Date) return ((java.sql.Date) value).toLocalDate();
        return value.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }
    private Date earlier(Date left, Date right)
    {
        if (left == null) return right;
        if (right == null) return left;
        return left.before(right) ? left : right;
    }
    private long waitingSeconds(Date created)
    {
        return created == null ? 0L : Math.max(0L, (currentTimeMillis() - created.getTime()) / 1000L);
    }
    private boolean matchesKeyword(TodoItem item, String additionalTerms,
            String keyword)
    {
        if (keyword == null) return true;
        String normalized = keyword.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) return true;
        return (safeSearch(item.getBusinessNo()) + " "
                + safeSearch(item.getTitle()) + " "
                + safeSearch(item.getSummary()) + " "
                + safeSearch(additionalTerms))
                .toLowerCase(Locale.ROOT).contains(normalized);
    }
    private String safeSearch(String value)
    {
        return value == null ? "" : value;
    }

    private String title(String type, String deptName)
    {
        String prefix = deptName == null || deptName.trim().isEmpty() ? "该组织" : deptName;
        if (SysTodoTypes.HR_PROFILE_INCOMPLETE.equals(type)) return prefix + "员工资料待补全";
        if (SysTodoTypes.HR_ONBOARDING_CONFIRM.equals(type)) return prefix + "待确认入职";
        if (SysTodoTypes.HR_CONTRACT_DUE.equals(type)) return prefix + "员工合同即将到期";
        if (SysTodoTypes.HR_HEALTH_CERT_DUE.equals(type)) return prefix + "员工健康证即将到期或已过期";
        if (SysTodoTypes.HR_HEALTH_CERT_REVIEW.equals(type)) return prefix + "健康证待审核";
        return prefix + "离职账号待停用";
    }

    private String requiredPermission(String type)
    {
        if (SysTodoTypes.HR_ONBOARDING_CONFIRM.equals(type)) return "hr:onboarding:confirm";
        if (SysTodoTypes.HR_OFFBOARD_ACCOUNT.equals(type)) return "system:user:edit";
        if (SysTodoTypes.HR_HEALTH_CERT_DUE.equals(type)) return "hr:healthCertificate:list";
        if (SysTodoTypes.HR_HEALTH_CERT_REVIEW.equals(type)) return "hr:healthCertificate:review";
        return "hr:employee:edit";
    }

    private Comparator<TodoItem> todoComparator()
    {
        return Comparator.comparingInt((TodoItem item) -> priorityRank(item.getPriority()))
                .thenComparing(TodoItem::getCreatedTime, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(TodoItem::getBusinessId,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(TodoItem::getTodoKey,
                        Comparator.nullsLast(Comparator.naturalOrder()));
    }
    private int priorityRank(String priority)
    {
        if (TodoConstants.PRIORITY_URGENT.equals(priority)) return 0;
        if (TodoConstants.PRIORITY_IMPORTANT.equals(priority)) return 1;
        return 2;
    }

    private void addCategory(TodoSummary summary, String category, long count)
    {
        if (TodoConstants.CATEGORY_EXECUTION.equals(category)) summary.setExecution(summary.getExecution() + count);
        else if (TodoConstants.CATEGORY_RISK.equals(category)) summary.setRisk(summary.getRisk() + count);
        else if (TodoConstants.CATEGORY_APPROVAL.equals(category)) summary.setApproval(summary.getApproval() + count);
        else if (TodoConstants.CATEGORY_RETURNED.equals(category)) summary.setReturned(summary.getReturned() + count);
        else if (TodoConstants.CATEGORY_PERSONAL.equals(category)) summary.setPersonal(summary.getPersonal() + count);
    }
    private void addPriority(TodoSummary summary, String priority, long count)
    {
        if (TodoConstants.PRIORITY_URGENT.equals(priority)) summary.setUrgent(summary.getUrgent() + count);
        else if (TodoConstants.PRIORITY_IMPORTANT.equals(priority)) summary.setImportant(summary.getImportant() + count);
        else summary.setNormal(summary.getNormal() + count);
    }

    private static class ScopeContext
    {
        private final List<Long> currentDeptIds;
        private final List<Long> authorizedDeptIds;
        ScopeContext(List<Long> currentDeptIds, List<Long> authorizedDeptIds)
        {
            this.currentDeptIds = currentDeptIds;
            this.authorizedDeptIds = authorizedDeptIds;
        }
    }

    private static class TodoGroup
    {
        private final Long deptId;
        private final String deptName;
        private final String deptType;
        private final String type;
        private final String category;
        private String priority;
        private Date createdTime;
        private int count;
        private final StringBuilder searchText = new StringBuilder();
        TodoGroup(SysTodoCandidateRow row, String type, String category, String priority)
        {
            this.deptId = row.getDeptId();
            this.deptName = row.getDeptName();
            this.deptType = row.getDeptType();
            this.type = type;
            this.category = category;
            this.priority = priority;
            this.createdTime = row.getCreateTime();
        }
        TodoGroup(SysHealthCertificateTodoCandidate row, String type,
                String category, String priority)
        {
            this.deptId = row.getDeptId();
            this.deptName = row.getDeptName();
            this.deptType = row.getDeptType();
            this.type = type;
            this.category = category;
            this.priority = priority;
            this.createdTime = row.getCreateTime();
        }
        void addSearchTerms(SysTodoCandidateRow row)
        {
            append(row.getEmployeeName());
            append(row.getEmployeeNo());
            append(row.getDeptName());
        }
        void addSearchTerms(SysHealthCertificateTodoCandidate row)
        {
            append(row.getEmployeeName());
            append(row.getEmployeeNo());
            append(row.getCertificateNo());
            append(row.getRejectionReason());
            append(row.getDeptName());
        }
        private void append(String value)
        {
            if (value != null && !value.isBlank())
            {
                searchText.append(' ').append(value);
            }
        }
    }
}
