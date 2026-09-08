package com.erp.oa.service.impl;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.erp.common.security.shop.ShopScopeService;
import com.erp.common.security.utils.SecurityUtils;
import com.erp.oa.constant.OaSignPackageStatus;
import com.erp.oa.constant.OaSignScenarioCodes;
import com.erp.oa.domain.OaSignPackage;
import com.erp.oa.domain.OaSignNotificationOutbox;
import com.erp.oa.domain.OaSignTask;
import com.erp.oa.domain.vo.OaTodoItem;
import com.erp.oa.domain.vo.OaTodoSummary;
import com.erp.oa.mapper.OaSignPackageMapper;
import com.erp.oa.mapper.OaSignNotificationOutboxMapper;
import com.erp.oa.mapper.OaSignTaskMapper;
import com.erp.system.api.model.LoginUser;

/**
 * @deprecated The unified {@link OaTodoServiceImpl} SQL projection is the only
 * active signing-todo provider. Kept temporarily for compatibility tests only.
 */
@Deprecated(forRemoval = true)
public class OaSignTodoProvider
{
    public static final String EMPLOYEE_SIGN_TYPE = "OA_SIGN_PACKAGE_SIGN";

    private static final List<HrTodoDefinition> HR_TODOS = List.of(
            new HrTodoDefinition("NEEDS_DATA", "OA_SIGN_NEEDS_DATA", "execution", "normal",
                    "补充签约资料", "oa:signTask:revalidate"),
            new HrTodoDefinition("PENDING_COMPANY", "OA_SIGN_COMPANY_FINALIZE", "execution", "important",
                    "选择公司并加盖印章", "oa:signPackage:send"),
            new HrTodoDefinition("FAILED", "OA_SIGN_SEND_FAILED", "risk", "important",
                    "处理合同任务异常", "oa:signTask:retry"),
            new HrTodoDefinition("REFUSED", "OA_SIGN_REFUSED", "risk", "urgent",
                    "处理员工拒签", "oa:signTask:resolveRefusal"),
            new HrTodoDefinition("EXPIRED", "OA_SIGN_EXPIRED", "risk", "urgent",
                    "处理逾期合同", "oa:signTask:resolveExpiry"));

    private final OaSignTaskMapper taskMapper;
    private final OaSignPackageMapper packageMapper;
    private final ShopScopeService shopScopeService;
    private final OaSignNotificationOutboxMapper outboxMapper;
    private final OaSignHrAccessService signHrAccessService;

    public OaSignTodoProvider(OaSignTaskMapper taskMapper,
            OaSignPackageMapper packageMapper,
            ShopScopeService shopScopeService,
            OaSignNotificationOutboxMapper outboxMapper,
            OaSignHrAccessService signHrAccessService)
    {
        this.taskMapper = taskMapper;
        this.packageMapper = packageMapper;
        this.shopScopeService = shopScopeService;
        this.outboxMapper = outboxMapper;
        this.signHrAccessService = signHrAccessService;
    }

    public List<OaTodoItem> list(String type, Long selectedShopDeptId)
    {
        long now = System.currentTimeMillis();
        Map<String, OaTodoItem> items = new LinkedHashMap<>();
        boolean currentHr = isCurrentHr();
        if (currentHr)
        {
            for (HrTodoDefinition definition : HR_TODOS)
            {
                if ((type == null || type.isBlank() || definition.type.equals(type))
                        && canViewHrTodo(definition.permission))
                {
                    appendHrTodos(items, definition, selectedShopDeptId, now);
                }
            }
            if ((type == null || type.isBlank() || "OA_SIGN_SEND_FAILED".equals(type))
                    && canViewHrTodo("oa:signTask:retry"))
            {
                appendDeadNotificationTodos(items, selectedShopDeptId, now);
            }
        }
        if (type == null || type.isBlank() || EMPLOYEE_SIGN_TYPE.equals(type))
        {
            appendEmployeeTodos(items, OaSignPackageStatus.PENDING_SIGN, now);
            appendEmployeeTodos(items, OaSignPackageStatus.PART_VIEWED, now);
            appendEmployeeTodos(items, OaSignPackageStatus.PENDING_FINAL_CONFIRM, now);
        }
        List<OaTodoItem> result = new ArrayList<>(items.values());
        result.sort(todoComparator());
        return result;
    }

    public OaTodoSummary summary(Long selectedShopDeptId)
    {
        List<OaTodoItem> items = list(null, selectedShopDeptId);
        OaTodoSummary summary = new OaTodoSummary();
        summary.setTotal((long) items.size());
        summary.setTypeCounts(count(items, OaTodoItem::getType));
        summary.setCategoryCounts(count(items, OaTodoItem::getCategory));
        summary.setPriorityCounts(count(items, OaTodoItem::getPriority));
        summary.setRecentItems(items.subList(0, Math.min(items.size(), 5)));
        summary.setGeneratedTime(new Date());
        return summary;
    }

    private void appendHrTodos(Map<String, OaTodoItem> target,
            HrTodoDefinition definition,
            Long selectedShopDeptId,
            long now)
    {
        OaSignTask query = new OaSignTask();
        query.setStatus(definition.status);
        Long assignedHrUserId = signHrAccessService.currentTaskOwnerFilter();
        if (assignedHrUserId != null)
        {
            query.setAssignedHrUserId(assignedHrUserId);
        }
        shopScopeService.appendShopScope(query, selectedShopDeptId);
        List<OaSignTask> tasks = taskMapper.selectOaSignTaskList(query);
        if (tasks == null)
        {
            return;
        }
        for (OaSignTask task : tasks)
        {
            if (!definition.status.equals(task.getStatus()))
            {
                continue;
            }
            if (("REFUSED".equals(task.getStatus()) || "EXPIRED".equals(task.getStatus()))
                    && !"OPEN".equals(task.getResolutionStatus()))
            {
                continue;
            }
            OaTodoItem item = new OaTodoItem();
            item.setTodoKey("OA_SIGN_TASK:" + task.getTaskId() + ":" + definition.type);
            item.setSource("oa");
            item.setType(definition.type);
            item.setCategory(definition.category);
            item.setBusinessId(task.getTaskId());
            item.setBusinessNo(task.getTaskNo());
            item.setTitle(definition.title);
            String employeeDisplay = value(task.getEmployeeName());
            if (employeeDisplay.isBlank())
            {
                employeeDisplay = "员工";
            }
            item.setSummary(employeeDisplay + "（账号 " + task.getEmployeeId() + "） · "
                    + scenarioLabel(task.getScenario()));
            item.setStatus(task.getStatus());
            item.setPriority(definition.priority);
            item.setCreatedTime(task.getCreatedTime());
            item.setWaitingSeconds(waitingSeconds(task.getCreatedTime(), now));
            item.setDeptId(task.getShopDeptId());
            item.setScopeMode(selectedShopDeptId == null ? "all_authorized" : "current_org");
            item.setRouteType("OA_SIGN_HR_TASK");
            item.getRouteParams().put("taskId", task.getTaskId());
            item.setRequiredPermission(definition.permission);
            target.put(item.getTodoKey(), item);
        }
    }

    private void appendEmployeeTodos(Map<String, OaTodoItem> target, String status, long now)
    {
        Long userId = SecurityUtils.getUserId();
        if (userId == null)
        {
            return;
        }
        OaSignPackage query = new OaSignPackage();
        query.setEmployeeId(userId);
        query.setStatus(status);
        List<OaSignPackage> packages = packageMapper.selectMyOaSignPackageList(query);
        if (packages == null)
        {
            return;
        }
        for (OaSignPackage signPackage : packages)
        {
            if (!userId.equals(signPackage.getEmployeeId())
                    || (!OaSignPackageStatus.PENDING_SIGN.equals(signPackage.getStatus())
                    && !OaSignPackageStatus.PART_VIEWED.equals(signPackage.getStatus())
                    && !OaSignPackageStatus.PENDING_FINAL_CONFIRM.equals(signPackage.getStatus())))
            {
                continue;
            }
            OaTodoItem item = new OaTodoItem();
            item.setTodoKey("OA_SIGN_PACKAGE:" + signPackage.getPackageId() + ":SIGN");
            item.setSource("oa");
            item.setType(EMPLOYEE_SIGN_TYPE);
            item.setCategory("personal");
            item.setBusinessId(signPackage.getPackageId());
            item.setBusinessNo(signPackage.getPackageNo());
            item.setTitle(OaSignPackageStatus.PENDING_FINAL_CONFIRM.equals(signPackage.getStatus())
                    ? "阅读并确认最终合同" : "阅读并签署合同");
            item.setSummary(signPackage.getEmployeeNameSnapshot() + " · "
                    + value(signPackage.getShopDeptName()));
            item.setStatus(signPackage.getStatus());
            item.setPriority(employeePriority(signPackage, now));
            item.setCreatedTime(signPackage.getCreateTime());
            item.setWaitingSeconds(waitingSeconds(signPackage.getCreateTime(), now));
            item.setDeptId(signPackage.getShopDeptId());
            item.setDeptName(signPackage.getShopDeptName());
            item.setScopeMode("all_authorized");
            item.setRouteType(EMPLOYEE_SIGN_TYPE);
            item.getRouteParams().put("packageId", signPackage.getPackageId());
            item.setRequiredPermission("authenticated");
            target.put(item.getTodoKey(), item);
        }
    }

    private String scenarioLabel(String scenario)
    {
        return switch (OaSignScenarioCodes.normalizeTaskScenario(scenario))
        {
            case OaSignScenarioCodes.ONBOARD -> "入职";
            case OaSignScenarioCodes.RENEWAL -> "续签";
            case OaSignScenarioCodes.TRANSFER -> "调岗";
            case OaSignScenarioCodes.REGULARIZE -> "转正";
            case OaSignScenarioCodes.OFFBOARD -> "离职";
            default -> value(scenario);
        };
    }

    private void appendDeadNotificationTodos(Map<String, OaTodoItem> target,
            Long selectedShopDeptId, long now)
    {
        Long currentUserId = SecurityUtils.getUserId();
        if (currentUserId == null || outboxMapper == null)
        {
            return;
        }
        List<Long> scopeDeptIds = shopScopeService.resolveScopeDeptIds(selectedShopDeptId);
        List<OaSignNotificationOutbox> rows = outboxMapper.selectDeadNotificationsForHr(
                currentUserId, scopeDeptIds, 100);
        if (rows == null || rows.isEmpty())
        {
            return;
        }
        for (OaSignNotificationOutbox row : rows)
        {
            JSONObject payload = parsePayload(row.getPayloadJson());
            Long taskId = payload == null ? null : payload.getLong("taskId");
            if (taskId == null || row.getBusinessKey() == null)
            {
                continue;
            }
            OaSignTask task = taskMapper.selectOaSignTaskById(taskId);
            if (task == null)
            {
                continue;
            }
            if (task.getShopDeptId() != null
                    && (scopeDeptIds == null || !scopeDeptIds.contains(task.getShopDeptId())))
            {
                continue;
            }
            OaTodoItem item = new OaTodoItem();
            item.setTodoKey("OA_SIGN_OUTBOX:" + row.getBusinessKey() + ":SEND_FAILED");
            item.setSource("oa");
            item.setType("OA_SIGN_SEND_FAILED");
            item.setCategory("risk");
            item.setBusinessId(task.getTaskId());
            item.setBusinessNo(task.getTaskNo());
            item.setTitle("合同通知发送异常");
            item.setSummary("系统通知多次发送失败，请检查账号或重新触发发送");
            item.setStatus("DEAD");
            item.setPriority("important");
            Date createdTime = row.getUpdatedTime() == null ? row.getCreatedTime() : row.getUpdatedTime();
            item.setCreatedTime(createdTime);
            item.setWaitingSeconds(waitingSeconds(createdTime, now));
            item.setDeptId(task.getShopDeptId());
            item.setScopeMode(selectedShopDeptId == null ? "all_authorized" : "current_org");
            item.setRouteType("OA_SIGN_HR_TASK");
            item.getRouteParams().put("taskId", task.getTaskId());
            item.getRouteParams().put("notificationBusinessKey", row.getBusinessKey());
            item.setRequiredPermission("oa:signTask:retry");
            target.put(item.getTodoKey(), item);
        }
    }

    private JSONObject parsePayload(String payloadJson)
    {
        try
        {
            return JSON.parseObject(payloadJson);
        }
        catch (RuntimeException ignored)
        {
            return null;
        }
    }

    private boolean canViewHrTodo(String actionPermission)
    {
        return hasPermission("oa:signTask:admin")
                || (hasPermission("oa:signTask:list") && hasPermission(actionPermission));
    }

    private boolean isCurrentHr()
    {
        try
        {
            return signHrAccessService.isCurrentHr();
        }
        catch (RuntimeException ignored)
        {
            return false;
        }
    }

    private boolean hasPermission(String permission)
    {
        LoginUser loginUser = SecurityUtils.getLoginUser();
        if (loginUser == null || loginUser.getPermissions() == null)
        {
            return false;
        }
        Set<String> permissions = loginUser.getPermissions();
        return permissions.contains("*:*:*") || permissions.contains(permission);
    }

    private String employeePriority(OaSignPackage signPackage, long now)
    {
        if (signPackage.getSignDeadline() != null
                && signPackage.getSignDeadline().getTime() - now <= 24L * 60 * 60 * 1000)
        {
            return "urgent";
        }
        return "important";
    }

    private Comparator<OaTodoItem> todoComparator()
    {
        return Comparator.comparingInt((OaTodoItem item) -> priorityRank(item.getPriority()))
                .thenComparing(OaTodoItem::getCreatedTime, Comparator.nullsLast(Date::compareTo))
                .thenComparing(OaTodoItem::getBusinessId, Comparator.nullsLast(Long::compareTo));
    }

    private int priorityRank(String priority)
    {
        return switch (priority)
        {
            case "urgent" -> 0;
            case "important" -> 1;
            default -> 2;
        };
    }

    private long waitingSeconds(Date createdTime, long now)
    {
        return createdTime == null ? 0L : Math.max(0L, (now - createdTime.getTime()) / 1000);
    }

    private Map<String, Long> count(List<OaTodoItem> items,
            java.util.function.Function<OaTodoItem, String> classifier)
    {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (OaTodoItem item : items)
        {
            counts.merge(classifier.apply(item), 1L, Long::sum);
        }
        return counts;
    }

    private String value(String value)
    {
        return value == null ? "" : value;
    }

    private record HrTodoDefinition(String status, String type, String category,
            String priority, String title, String permission)
    {
    }
}
