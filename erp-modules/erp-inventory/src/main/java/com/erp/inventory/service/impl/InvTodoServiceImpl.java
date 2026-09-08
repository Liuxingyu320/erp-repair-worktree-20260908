package com.erp.inventory.service.impl;

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
import com.erp.inventory.constant.InvTodoTypes;
import com.erp.inventory.domain.InvTransferShipment;
import com.erp.inventory.domain.vo.InvTodoCountRow;
import com.erp.inventory.mapper.InvDeptScopeMapper;
import com.erp.inventory.mapper.InvTodoMapper;
import com.erp.inventory.mapper.InvTransferShipmentMapper;
import com.erp.inventory.service.IInvTodoService;
import com.erp.system.api.RemoteConfigService;

@Service
public class InvTodoServiceImpl implements IInvTodoService
{
    static final String APPROVAL_URGENT_HOURS_KEY = "todo.approval.urgent.hours";
    static final String STOCK_CHECK_DUE_SOON_HOURS_KEY = "todo.stock-check.due-soon.hours";
    static final String SUMMARY_RECENT_LIMIT_KEY = "todo.summary.recent.limit";
    static final int DEFAULT_APPROVAL_URGENT_HOURS = 24;
    static final int DEFAULT_STOCK_CHECK_DUE_SOON_HOURS = 24;
    static final int DEFAULT_SUMMARY_RECENT_LIMIT = 5;

    private final InvTodoMapper todoMapper;
    private final InvDeptScopeMapper deptScopeMapper;
    private final InvTransferShipmentMapper transferShipmentMapper;
    private RemoteConfigService remoteConfigService;

    @Autowired
    public InvTodoServiceImpl(InvTodoMapper todoMapper, InvDeptScopeMapper deptScopeMapper,
            InvTransferShipmentMapper transferShipmentMapper)
    {
        this.todoMapper = todoMapper;
        this.deptScopeMapper = deptScopeMapper;
        this.transferShipmentMapper = transferShipmentMapper;
    }

    @Autowired(required = false)
    public void setRemoteConfigService(RemoteConfigService remoteConfigService)
    {
        this.remoteConfigService = remoteConfigService;
    }

    @Override
    public TodoSummary selectSummary(TodoQuery query, Long selectedDeptId)
    {
        TodoQuery safeQuery = validateQuery(query);
        QueryContext context = buildContext(safeQuery, selectedDeptId);
        int urgentHours = loadBoundedConfig(APPROVAL_URGENT_HOURS_KEY,
                DEFAULT_APPROVAL_URGENT_HOURS, 1, 168);
        int stockCheckDueSoonHours = loadBoundedConfig(STOCK_CHECK_DUE_SOON_HOURS_KEY,
                DEFAULT_STOCK_CHECK_DUE_SOON_HOURS, 1, 168);
        List<InvTodoCountRow> countRows = todoMapper.selectInventoryTodoCounts(
                safeQuery, context.enabledTypes, context.currentScopeDeptIds,
                context.authorizedScopeDeptIds, context.userId, context.username,
                urgentHours, stockCheckDueSoonHours);
        int recentLimit = loadBoundedConfig(SUMMARY_RECENT_LIMIT_KEY,
                DEFAULT_SUMMARY_RECENT_LIMIT, 1, 20);
        List<TodoItem> recentRows = todoMapper.selectRecentInventoryTodos(
                safeQuery, context.enabledTypes, context.currentScopeDeptIds,
                context.authorizedScopeDeptIds, context.userId, context.username,
                urgentHours, stockCheckDueSoonHours, recentLimit);

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
        int stockCheckDueSoonHours = loadBoundedConfig(STOCK_CHECK_DUE_SOON_HOURS_KEY,
                DEFAULT_STOCK_CHECK_DUE_SOON_HOURS, 1, 168);
        startPage(safeQuery);
        List<TodoItem> rows = todoMapper.selectInventoryTodoList(
                safeQuery, context.enabledTypes, context.currentScopeDeptIds,
                context.authorizedScopeDeptIds, context.userId, context.username,
                urgentHours, stockCheckDueSoonHours);
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
        addIf(enabled, InvTodoTypes.INV_TRANSFER_APPROVAL,
                all("inv:transfer:approve", "inv:transfer:list"));
        addIf(enabled, InvTodoTypes.INV_STOCK_CHECK_APPROVAL,
                all("inv:stockCheck:approve", "inv:stockCheck:list"));
        addIf(enabled, InvTodoTypes.INV_STOCK_CHECK_EXECUTE,
                all("inv:stockCheck:submit", "inv:stockCheck:list"));
        addIf(enabled, InvTodoTypes.INV_PURCHASE_QC,
                all("inv:purchase:qc", "inv:purchase:list"));
        addIf(enabled, InvTodoTypes.INV_PURCHASE_RECEIVE,
                all("inv:purchase:receive", "inv:purchase:list"));
        addIf(enabled, InvTodoTypes.INV_SALES_NOTICE_CREATE,
                all("inv:deliveryNotice:add", "inv:sales:list"));
        addIf(enabled, InvTodoTypes.INV_DELIVERY_EXECUTE,
                all("inv:deliveryNotice:deliver", "inv:deliveryNotice:list"));
        addIf(enabled, InvTodoTypes.INV_TRANSFER_DELIVER,
                all("inv:transfer:deliver", "inv:transfer:list"));
        addIf(enabled, InvTodoTypes.INV_TRANSFER_SOURCE_CONFIRM,
                all("inv:transfer:deliver", "inv:transfer:list"));
        addIf(enabled, InvTodoTypes.INV_TRANSFER_RECEIVE,
                all("inv:transfer:receive", "inv:transfer:list"));
        addIf(enabled, InvTodoTypes.INV_TRANSFER_DISCREPANCY,
                all("inv:transfer:discrepancy:handle", "inv:transfer:list"));
        addIf(enabled, InvTodoTypes.INV_PURCHASE_RETURN_CONFIRM,
                all("inv:purchaseReturn:confirm", "inv:purchaseReturn:list"));
        addIf(enabled, InvTodoTypes.INV_SALES_RETURN_CONFIRM,
                all("inv:salesReturn:confirm", "inv:salesReturn:list"));
        addIf(enabled, InvTodoTypes.INV_STOCK_CHECK_RETURNED,
                all("inv:stockCheck:submit", "inv:stockCheck:list"));
        addIf(enabled, InvTodoTypes.INV_STOCK_CHECK_RESTART,
                all("inv:stockCheck:submit", "inv:stockCheck:list"));
        addIf(enabled, InvTodoTypes.INV_TRANSFER_RETURNED,
                hasPermission("inv:transfer:list")
                        && any("inv:transfer:add", "inv:transfer:edit"));
        addIf(enabled, InvTodoTypes.INV_TRANSFER_SOURCE_RESELECT,
                hasPermission("inv:transfer:list")
                        && any("inv:transfer:add", "inv:transfer:edit"));
        boolean risk = hasPermission("inv:stock:list")
                && any("inv:purchase:add", "inv:transfer:add", "inv:stock:adjust");
        addIf(enabled, InvTodoTypes.INV_OUT_OF_STOCK, risk);
        addIf(enabled, InvTodoTypes.INV_LOW_STOCK, risk);
        return enabled;
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
                ? deptScopeMapper.selectAllActiveInventoryDeptIds()
                : deptScopeMapper.selectUserAuthorizedInventoryDeptIds(userId);
        authorized = normalizeDeptIds(authorized);
        List<Long> current = selectedDeptId != null && selectedDeptId > 0 && authorized.contains(selectedDeptId)
                ? List.of(selectedDeptId)
                : Collections.emptyList();
        Set<String> enabledTypes = query.getSource() == null
                || TodoConstants.SOURCE_INVENTORY.equals(query.getSource())
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

    private TodoSummary summarize(List<InvTodoCountRow> rows)
    {
        TodoSummary summary = new TodoSummary();
        summary.setSource(TodoConstants.SOURCE_INVENTORY);
        Map<String, Long> typeCounts = new LinkedHashMap<>();
        if (rows != null)
        {
            for (InvTodoCountRow row : rows)
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
        if (TodoConstants.CATEGORY_APPROVAL.equals(category))
        {
            summary.setApproval(summary.getApproval() + count);
        }
        else if (TodoConstants.CATEGORY_EXECUTION.equals(category))
        {
            summary.setExecution(summary.getExecution() + count);
        }
        else if (TodoConstants.CATEGORY_RETURNED.equals(category))
        {
            summary.setReturned(summary.getReturned() + count);
        }
        else if (TodoConstants.CATEGORY_RISK.equals(category))
        {
            summary.setRisk(summary.getRisk() + count);
        }
        else if (TodoConstants.CATEGORY_PERSONAL.equals(category))
        {
            summary.setPersonal(summary.getPersonal() + count);
        }
    }

    private void addPriority(TodoSummary summary, String priority, long count)
    {
        if (TodoConstants.PRIORITY_URGENT.equals(priority))
        {
            summary.setUrgent(summary.getUrgent() + count);
        }
        else if (TodoConstants.PRIORITY_IMPORTANT.equals(priority))
        {
            summary.setImportant(summary.getImportant() + count);
        }
        else if (TodoConstants.PRIORITY_NORMAL.equals(priority))
        {
            summary.setNormal(summary.getNormal() + count);
        }
    }

    private List<TodoItem> decorate(List<TodoItem> rows)
    {
        if (rows == null || rows.isEmpty())
        {
            return rows == null ? Collections.emptyList() : rows;
        }
        Map<Long, InvTransferShipment> shipments = loadShipments(rows);
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
            if (InvTodoTypes.INV_TRANSFER_APPROVAL.equals(item.getType()))
            {
                params.put("approvalEngine", "LEGACY");
            }
            put(params, "contextDeptId", item.getDeptId());
            put(params, "contextDeptName", item.getDeptName());
            put(params, "contextDeptType", item.getDeptType());
            put(params, "scopeMode", item.getScopeMode());
            if (InvTodoTypes.INV_TRANSFER_RECEIVE.equals(item.getType()))
            {
                InvTransferShipment shipment = shipments.get(item.getBusinessId());
                params.put("shipmentId", String.valueOf(item.getBusinessId()));
                if (shipment != null && shipment.getTransferId() != null)
                {
                    params.put("transferId", String.valueOf(shipment.getTransferId()));
                }
            }
            if (InvTodoTypes.INV_TRANSFER_RETURNED.equals(item.getType()))
            {
                item.setRequiredPermission(hasPermission("inv:transfer:add")
                        ? "inv:transfer:add" : "inv:transfer:edit");
            }
            item.setRouteParams(params);
        }
        return rows;
    }

    private Map<Long, InvTransferShipment> loadShipments(List<TodoItem> rows)
    {
        List<Long> shipmentIds = rows.stream()
                .filter(Objects::nonNull)
                .filter(item -> InvTodoTypes.INV_TRANSFER_RECEIVE.equals(item.getType()))
                .map(TodoItem::getBusinessId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (shipmentIds.isEmpty())
        {
            return Collections.emptyMap();
        }
        List<InvTransferShipment> shipments = transferShipmentMapper.selectByIds(shipmentIds);
        if (shipments == null)
        {
            return Collections.emptyMap();
        }
        return shipments.stream().filter(Objects::nonNull)
                .filter(shipment -> shipment.getShipmentId() != null)
                .collect(Collectors.toMap(InvTransferShipment::getShipmentId,
                        shipment -> shipment, (left, right) -> left, LinkedHashMap::new));
    }

    private void put(Map<String, String> params, String key, Object value)
    {
        if (value != null)
        {
            params.put(key, String.valueOf(value));
        }
    }

    private int loadBoundedConfig(String key, int fallback, int min, int max)
    {
        if (remoteConfigService == null)
        {
            return fallback;
        }
        try
        {
            R<String> result = remoteConfigService.getConfigKey(key, SecurityConstants.INNER);
            if (result == null || result.getCode() != R.SUCCESS || result.getData() == null)
            {
                return fallback;
            }
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
            if (!hasPermission(permission))
            {
                return false;
            }
        }
        return true;
    }

    private boolean any(String... permissions)
    {
        for (String permission : permissions)
        {
            if (hasPermission(permission))
            {
                return true;
            }
        }
        return false;
    }

    private void addIf(Set<String> enabled, String type, boolean condition)
    {
        if (condition)
        {
            enabled.add(type);
        }
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
