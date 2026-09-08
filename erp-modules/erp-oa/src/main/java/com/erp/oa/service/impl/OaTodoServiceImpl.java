package com.erp.oa.service.impl;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.github.pagehelper.PageHelper;
import com.erp.common.core.constant.SecurityConstants;
import com.erp.common.core.domain.R;
import com.erp.common.core.domain.todo.TodoConstants;
import com.erp.common.core.domain.todo.TodoItem;
import com.erp.common.core.domain.todo.TodoKeys;
import com.erp.common.core.domain.todo.TodoQuery;
import com.erp.common.core.domain.todo.TodoSummary;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.security.auth.AuthUtil;
import com.erp.common.security.utils.SecurityUtils;
import com.erp.oa.constant.OaTodoTypes;
import com.erp.oa.domain.vo.OaTodoCountRow;
import com.erp.oa.domain.vo.OaTodoCandidate;
import com.erp.oa.mapper.OaDeptScopeMapper;
import com.erp.oa.mapper.OaTodoMapper;
import com.erp.oa.service.IOaTodoService;
import com.erp.system.api.RemoteConfigService;

@Service
public class OaTodoServiceImpl implements IOaTodoService
{
    static final String APPROVAL_URGENT_HOURS_KEY = "todo.approval.urgent.hours";
    static final String SUMMARY_RECENT_LIMIT_KEY = "todo.summary.recent.limit";
    static final int DEFAULT_APPROVAL_URGENT_HOURS = 24;
    static final int DEFAULT_SUMMARY_RECENT_LIMIT = 5;
    private static final Set<String> OPTIONAL_TODO_TABLES = Set.of(
            "oa_labor_contract",
            "oa_sign_package",
            "oa_sign_task",
            "oa_sign_notification_outbox",
            "oa_sign_onboard_data_request",
            "oa_sign_onboard_import_row");

    private final OaTodoMapper todoMapper;
    private final OaDeptScopeMapper deptScopeMapper;
    private RemoteConfigService remoteConfigService;
    private OaSignHrAccessService signHrAccessService;

    @Autowired
    public OaTodoServiceImpl(OaTodoMapper todoMapper, OaDeptScopeMapper deptScopeMapper)
    {
        this.todoMapper = todoMapper;
        this.deptScopeMapper = deptScopeMapper;
    }

    @Autowired(required = false)
    public void setRemoteConfigService(RemoteConfigService remoteConfigService)
    {
        this.remoteConfigService = remoteConfigService;
    }

    @Autowired(required = false)
    public void setSignHrAccessService(OaSignHrAccessService signHrAccessService)
    {
        this.signHrAccessService = signHrAccessService;
    }

    @Override
    public TodoSummary selectSummary(TodoQuery query, Long selectedDeptId)
    {
        TodoQuery safeQuery = validateQuery(query);
        QueryContext context = buildContext(safeQuery, selectedDeptId);
        int urgentHours = loadBoundedConfig(APPROVAL_URGENT_HOURS_KEY,
                DEFAULT_APPROVAL_URGENT_HOURS, 1, 168);
        List<OaTodoCountRow> countRows = todoMapper.selectOaTodoCounts(
                safeQuery, context.enabledTypes, context.currentScopeDeptIds,
                context.authorizedScopeDeptIds,
                context.userId, context.username, urgentHours);
        int recentLimit = loadBoundedConfig(SUMMARY_RECENT_LIMIT_KEY,
                DEFAULT_SUMMARY_RECENT_LIMIT, 1, 20);
        List<TodoItem> recentRows = todoMapper.selectRecentOaTodos(
                safeQuery, context.enabledTypes, context.currentScopeDeptIds,
                context.authorizedScopeDeptIds,
                context.userId, context.username, urgentHours, recentLimit);
        TodoSummary summary = summarize(countRows);
        summary.setRecent(decorate(recentRows));
        return summary;
    }

    @Override
    public List<TodoItem> selectTodoList(TodoQuery query, Long selectedDeptId)
    {
        TodoQuery safeQuery = validateQuery(query);
        QueryContext context = buildContext(safeQuery, selectedDeptId);
        int urgentHours = loadBoundedConfig(APPROVAL_URGENT_HOURS_KEY,
                DEFAULT_APPROVAL_URGENT_HOURS, 1, 168);
        startPage(safeQuery);
        List<TodoItem> rows = todoMapper.selectOaTodoList(
                safeQuery, context.enabledTypes, context.currentScopeDeptIds,
                context.authorizedScopeDeptIds,
                context.userId, context.username, urgentHours);
        decorate(rows);
        return rows;
    }

    protected void startPage(TodoQuery query)
    {
        PageHelper.startPage(query.getPageNum(), query.getPageSize());
    }

    protected Long currentUserId()
    {
        return SecurityUtils.getUserId();
    }

    protected String currentUsername()
    {
        return SecurityUtils.getUsername();
    }

    protected boolean currentUserIsAdmin()
    {
        return SecurityUtils.isAdmin();
    }

    protected boolean hasPermission(String permission)
    {
        return AuthUtil.hasPermi(permission);
    }

    protected Set<String> resolveEnabledTypes()
    {
        Set<String> enabled = new LinkedHashSet<>();
        Set<String> availableTables = availableTodoTables();
        addIf(enabled, OaTodoTypes.OA_PURCHASE_APPROVAL,
                all("oa:purchase:add", "oa:purchase:list"));
        addIf(enabled, OaTodoTypes.OA_LABOR_CONTRACT_SIGN,
                hasTables(availableTables, "oa_labor_contract"));
        addIf(enabled, OaTodoTypes.OA_SIGN_PACKAGE_SIGN,
                hasTables(availableTables, "oa_sign_package"));
        addIf(enabled, OaTodoTypes.OA_SIGN_ONBOARD_DATA_REQUEST,
                hasTables(availableTables,
                        "oa_sign_onboard_data_request",
                        "oa_sign_onboard_import_row"));
        if (currentUserIsConfiguredSignHr()
                && hasTables(availableTables, "oa_sign_task", "oa_sign_package"))
        {
            addIf(enabled, OaTodoTypes.OA_SIGN_NEEDS_DATA,
                    all("oa:signTask:list", "oa:signTask:revalidate"));
            addIf(enabled, OaTodoTypes.OA_SIGN_COMPANY_FINALIZE,
                    all("oa:signTask:list", "oa:signPackage:send"));
            addIf(enabled, OaTodoTypes.OA_SIGN_SEND_FAILED,
                    hasTables(availableTables, "oa_sign_notification_outbox")
                            && all("oa:signTask:list", "oa:signTask:retry"));
            addIf(enabled, OaTodoTypes.OA_SIGN_REFUSED,
                    all("oa:signTask:list", "oa:signTask:resolveRefusal"));
            addIf(enabled, OaTodoTypes.OA_SIGN_EXPIRED,
                    all("oa:signTask:list", "oa:signTask:resolveExpiry"));
        }
        return enabled;
    }

    protected Set<String> availableTodoTables()
    {
        try
        {
            Set<String> tables = todoMapper.selectExistingTodoTables(
                    OPTIONAL_TODO_TABLES);
            return tables == null ? Collections.emptySet() : tables;
        }
        catch (RuntimeException ignored)
        {
            return Collections.emptySet();
        }
    }

    private boolean hasTables(Set<String> availableTables,
            String... requiredTables)
    {
        if (availableTables == null || requiredTables == null)
        {
            return false;
        }
        for (String table : requiredTables)
        {
            if (!availableTables.contains(table))
            {
                return false;
            }
        }
        return true;
    }

    protected boolean currentUserIsConfiguredSignHr()
    {
        if (signHrAccessService == null)
        {
            return false;
        }
        try
        {
            return signHrAccessService.isCurrentHr();
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
        String source = safe.getSource();
        if (source != null
                && !TodoConstants.SOURCE_INVENTORY.equals(source)
                && !TodoConstants.SOURCE_OA.equals(source)
                && !TodoConstants.SOURCE_SYSTEM.equals(source))
        {
            throw new ServiceException("不支持的待办来源");
        }
        return safe;
    }

    private QueryContext buildContext(TodoQuery query, Long selectedDeptId)
    {
        Long userId = currentUserId();
        if (userId == null)
        {
            throw new ServiceException("无法识别当前用户");
        }
        String username = currentUsername();
        if (username == null || username.trim().isEmpty())
        {
            throw new ServiceException("无法识别当前用户名称");
        }
        username = username.trim();
        List<Long> authorized = currentUserIsAdmin()
                ? deptScopeMapper.selectAllActiveOaDeptIds()
                : deptScopeMapper.selectUserAuthorizedOaDeptIds(userId);
        authorized = normalizeDeptIds(authorized);
        List<Long> current = selectedDeptId != null && selectedDeptId > 0
                && authorized.contains(selectedDeptId)
                ? List.of(selectedDeptId) : Collections.emptyList();
        Set<String> enabledTypes = query.getSource() == null
                || TodoConstants.SOURCE_OA.equals(query.getSource())
                ? resolveEnabledTypes() : Collections.emptySet();
        return new QueryContext(current, authorized, enabledTypes, userId, username);
    }

    private List<Long> normalizeDeptIds(List<Long> deptIds)
    {
        if (deptIds == null || deptIds.isEmpty())
        {
            return Collections.emptyList();
        }
        return deptIds.stream().filter(Objects::nonNull).filter(id -> id > 0)
                .distinct().collect(Collectors.toList());
    }

    private TodoSummary summarize(List<OaTodoCountRow> rows)
    {
        TodoSummary summary = new TodoSummary();
        summary.setSource(TodoConstants.SOURCE_OA);
        Map<String, Long> typeCounts = new LinkedHashMap<>();
        if (rows != null)
        {
            for (OaTodoCountRow row : rows)
            {
                if (row == null || row.getCount() <= 0)
                {
                    continue;
                }
                typeCounts.merge(row.getType(), row.getCount(), Long::sum);
                addCategory(summary, row.getCategory(), row.getCount());
                addPriority(summary, row.getPriority(), row.getCount());
            }
        }
        summary.setTypeCounts(typeCounts);
        summary.recalculateTotal();
        return summary;
    }

    private void addCategory(TodoSummary summary, String category, long count)
    {
        if (TodoConstants.CATEGORY_APPROVAL.equals(category)) summary.setApproval(summary.getApproval() + count);
        else if (TodoConstants.CATEGORY_EXECUTION.equals(category)) summary.setExecution(summary.getExecution() + count);
        else if (TodoConstants.CATEGORY_RETURNED.equals(category)) summary.setReturned(summary.getReturned() + count);
        else if (TodoConstants.CATEGORY_RISK.equals(category)) summary.setRisk(summary.getRisk() + count);
        else if (TodoConstants.CATEGORY_PERSONAL.equals(category)) summary.setPersonal(summary.getPersonal() + count);
    }

    private void addPriority(TodoSummary summary, String priority, long count)
    {
        if (TodoConstants.PRIORITY_URGENT.equals(priority)) summary.setUrgent(summary.getUrgent() + count);
        else if (TodoConstants.PRIORITY_IMPORTANT.equals(priority)) summary.setImportant(summary.getImportant() + count);
        else if (TodoConstants.PRIORITY_NORMAL.equals(priority)) summary.setNormal(summary.getNormal() + count);
    }

    private List<TodoItem> decorate(List<TodoItem> rows)
    {
        if (rows == null || rows.isEmpty())
        {
            return rows == null ? Collections.emptyList() : rows;
        }
        Set<String> keys = new LinkedHashSet<>();
        for (TodoItem item : rows)
        {
            if (item == null)
            {
                throw new IllegalStateException("todo item must not be null");
            }
            String key = TodoKeys.build(item.getSource(), item.getType(),
                    item.getBusinessId(), item.getRouteType());
            if (!keys.add(key))
            {
                throw new IllegalStateException("duplicate todoKey: " + key);
            }
            item.setTodoKey(key);
            Map<String, String> params = new LinkedHashMap<>();
            params.put("businessId", String.valueOf(item.getBusinessId()));
            if (isSignHrTodo(item.getType()))
            {
                params.put("taskId", String.valueOf(item.getBusinessId()));
                if (item instanceof OaTodoCandidate candidate)
                {
                    put(params, "notificationBusinessKey", candidate.getRouteReference());
                }
            }
            if (OaTodoTypes.OA_SIGN_ONBOARD_DATA_REQUEST.equals(item.getType())
                    && item instanceof OaTodoCandidate candidate)
            {
                put(params, "requestId", candidate.getRouteReference());
            }
            if (!isPersonal(item.getType()))
            {
                put(params, "contextDeptId", item.getDeptId());
                put(params, "contextDeptName", item.getDeptName());
                put(params, "contextDeptType", item.getDeptType());
                put(params, "scopeMode", item.getScopeMode());
            }
            item.setRouteParams(params);
        }
        return rows;
    }

    private boolean isPersonal(String type)
    {
        return OaTodoTypes.OA_LABOR_CONTRACT_SIGN.equals(type)
                || OaTodoTypes.OA_SIGN_PACKAGE_SIGN.equals(type)
                || OaTodoTypes.OA_SIGN_ONBOARD_DATA_REQUEST.equals(type);
    }

    private boolean isSignHrTodo(String type)
    {
        return OaTodoTypes.OA_SIGN_NEEDS_DATA.equals(type)
                || OaTodoTypes.OA_SIGN_COMPANY_FINALIZE.equals(type)
                || OaTodoTypes.OA_SIGN_SEND_FAILED.equals(type)
                || OaTodoTypes.OA_SIGN_REFUSED.equals(type)
                || OaTodoTypes.OA_SIGN_EXPIRED.equals(type);
    }

    private void put(Map<String, String> params, String key, Object value)
    {
        if (value != null) params.put(key, String.valueOf(value));
    }

    private int loadBoundedConfig(String key, int fallback, int min, int max)
    {
        if (remoteConfigService == null) return fallback;
        try
        {
            R<String> result = remoteConfigService.getConfigKey(key, SecurityConstants.INNER);
            if (result == null || result.getCode() != R.SUCCESS || result.getData() == null) return fallback;
            int value = Integer.parseInt(result.getData().trim());
            return value >= min && value <= max ? value : fallback;
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

    private void addIf(Set<String> enabled, String type, boolean condition)
    {
        if (condition) enabled.add(type);
    }

    private static final class QueryContext
    {
        private final List<Long> currentScopeDeptIds;
        private final List<Long> authorizedScopeDeptIds;
        private final Set<String> enabledTypes;
        private final Long userId;
        private final String username;

        private QueryContext(List<Long> currentScopeDeptIds, List<Long> authorizedScopeDeptIds,
                Set<String> enabledTypes, Long userId, String username)
        {
            this.currentScopeDeptIds = currentScopeDeptIds;
            this.authorizedScopeDeptIds = authorizedScopeDeptIds;
            this.enabledTypes = enabledTypes;
            this.userId = userId;
            this.username = username;
        }
    }
}
