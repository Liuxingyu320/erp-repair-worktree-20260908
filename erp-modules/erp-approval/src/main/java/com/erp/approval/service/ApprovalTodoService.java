package com.erp.approval.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import com.erp.approval.mapper.ApprovalCandidateDirectoryMapper;
import com.erp.approval.constant.ApprovalBusinessCodes;
import com.erp.approval.domain.vo.ApprovalTodoCountRow;
import com.erp.approval.domain.vo.ApprovalTodoAccessRule;
import com.erp.approval.domain.vo.ApprovalTodoRow;
import com.erp.approval.mapper.ApprovalRuntimeMapper;
import com.erp.common.core.domain.todo.TodoConstants;
import com.erp.common.core.domain.todo.TodoItem;
import com.erp.common.core.domain.todo.TodoKeys;
import com.erp.common.core.domain.todo.TodoQuery;
import com.erp.common.core.domain.todo.TodoSummary;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.security.auth.AuthUtil;
import com.github.pagehelper.Page;

@Service
public class ApprovalTodoService
{
    private final ApprovalRuntimeMapper runtimeMapper;
    private final ApprovalPermissionPolicy permissionPolicy;
    private final ApprovalCandidateDirectoryMapper directoryMapper;

    public ApprovalTodoService(ApprovalRuntimeMapper runtimeMapper,
            ApprovalPermissionPolicy permissionPolicy,
            ApprovalCandidateDirectoryMapper directoryMapper)
    {
        this.runtimeMapper = runtimeMapper;
        this.permissionPolicy = permissionPolicy;
        this.directoryMapper = directoryMapper;
    }

    public List<TodoItem> list(TodoQuery query, Long userId)
    {
        requireUser(userId);
        List<ApprovalTodoAccessRule> accessRules =
                permittedAccessRules(userId);
        List<ApprovalTodoRow> source = runtimeMapper.selectTodoRows(userId,
                query, accessRules);
        List<TodoItem> result;
        if (source instanceof Page<?> page)
        {
            Page<TodoItem> mapped = new Page<>(page.getPageNum(),
                    page.getPageSize());
            mapped.setTotal(page.getTotal());
            mapped.setPages(page.getPages());
            result = mapped;
        }
        else
        {
            result = new ArrayList<>(source.size());
        }
        for (ApprovalTodoRow row : source)
        {
            result.add(decorate(row));
        }
        return result;
    }

    public TodoSummary summary(TodoQuery query, Long userId)
    {
        requireUser(userId);
        List<ApprovalTodoAccessRule> accessRules =
                permittedAccessRules(userId);
        List<ApprovalTodoCountRow> counts = runtimeMapper.selectTodoTypeCounts(
                userId, query, accessRules);
        Map<String, Long> typeCounts = new LinkedHashMap<>();
        long total = 0L;
        for (ApprovalTodoCountRow row : counts)
        {
            if (row == null || row.getTodoType() == null)
            {
                continue;
            }
            long count = row.getTodoCount() == null ? 0L
                    : Math.max(0L, row.getTodoCount());
            typeCounts.merge(row.getTodoType(), count, Long::sum);
            total += count;
        }
        List<TodoItem> recent = runtimeMapper.selectRecentTodoRows(userId,
                query, accessRules).stream().map(this::decorate).toList();
        TodoSummary summary = new TodoSummary();
        summary.setSource("approval");
        summary.setApproval(total);
        summary.setExecution(0);
        summary.setReturned(0);
        summary.setRisk(0);
        summary.setPersonal(0);
        summary.setNormal(total);
        summary.setTypeCounts(typeCounts);
        summary.setRecent(recent);
        summary.recalculateTotal();
        return summary;
    }

    private static void requireUser(Long userId)
    {
        if (userId == null)
        {
            throw new ServiceException("未获取到当前用户");
        }
    }

    private List<ApprovalTodoAccessRule> permittedAccessRules(Long userId)
    {
        Set<String> supported = permissionPolicy.supportedBusinessCodes();
        if (supported == null || supported.isEmpty())
        {
            return List.of();
        }
        return supported.stream()
                .sorted()
                .map(code -> {
                    String basePermission =
                            permissionPolicy.requiredPermission(code);
                    boolean baseAllowed = isCurrentlyAllowed(userId,
                            basePermission);
                    List<String> specialPermissions =
                            permissionPolicy.candidatePermissions(code).stream()
                                    .filter(permission -> !basePermission
                                            .equals(permission))
                                    .sorted()
                                    .toList();
                    List<String> allowedSpecialPermissions =
                            specialPermissions.stream()
                                    .filter(permission -> isCurrentlyAllowed(
                                            userId, permission))
                                    .toList();
                    return new ApprovalTodoAccessRule(code, baseAllowed,
                            specialPermissions, allowedSpecialPermissions);
                })
                .filter(rule -> rule.isBasePermissionAllowed()
                        || !rule.getAllowedSpecialCandidatePermissions()
                                .isEmpty())
                .toList();
    }

    private boolean isCurrentlyAllowed(Long userId, String permission)
    {
        return hasPermission(permission)
                && hasActiveAssignment(userId, permission);
    }

    protected boolean hasPermission(String permission)
    {
        return AuthUtil.hasPermi(permission);
    }

    protected boolean hasActiveAssignment(Long userId, String permission)
    {
        return directoryMapper.selectActiveUserById(userId, permission) != null;
    }

    private TodoItem decorate(ApprovalTodoRow row)
    {
        Long businessId;
        try
        {
            businessId = Long.valueOf(row.getBusinessId());
        }
        catch (RuntimeException exception)
        {
            throw new ServiceException("工作台待办的业务ID必须是正整数: "
                    + row.getBusinessId());
        }
        String type = todoType(row.getBusinessCode());
        TodoItem item = new TodoItem();
        item.setSource(row.getBusinessSource());
        item.setType(type);
        item.setCategory(TodoConstants.CATEGORY_APPROVAL);
        item.setBusinessId(businessId);
        item.setBusinessNo(row.getBusinessId());
        item.setTitle(title(row.getBusinessCode()) + " #" + row.getBusinessId());
        item.setSummary("申请人 " + safe(row.getApplicantName())
                + "，等待" + safe(row.getNodeName()) + "审批");
        item.setStatus("PENDING");
        item.setPriority(TodoConstants.PRIORITY_NORMAL);
        item.setCreatedTime(row.getCreatedTime());
        item.setDeptId(row.getAnchorDeptId());
        item.setDeptName(row.getAnchorDeptName());
        item.setDeptType(row.getAnchorDeptType());
        item.setScopeMode(TodoConstants.SCOPE_ALL_AUTHORIZED);
        item.setRouteType("approve");
        item.setRequiredPermission(permissionPolicy.permissionForCandidate(
                row.getBusinessCode(), row.getCandidateSourceCode()));
        item.setTodoKey(TodoKeys.build(item.getSource(), item.getType(),
                businessId, item.getRouteType()));
        Map<String, String> params = new LinkedHashMap<>();
        params.put("businessId", row.getBusinessId());
        params.put("approvalEngine", "NATIVE");
        params.put("approvalTaskId", String.valueOf(row.getTaskId()));
        params.put("approvalInstanceId", String.valueOf(row.getInstanceId()));
        if (row.getAnchorDeptId() != null)
        {
            params.put("contextDeptId", String.valueOf(row.getAnchorDeptId()));
        }
        if (row.getAnchorDeptName() != null)
        {
            params.put("contextDeptName", row.getAnchorDeptName());
        }
        if (row.getAnchorDeptType() != null)
        {
            params.put("contextDeptType", row.getAnchorDeptType());
        }
        item.setRouteParams(params);
        return item;
    }

    private String todoType(String businessCode)
    {
        return switch (businessCode)
        {
            case ApprovalBusinessCodes.OA_PURCHASE -> "OA_PURCHASE_APPROVAL";
            case ApprovalBusinessCodes.OA_REIMBURSEMENT ->
                    "OA_REIMBURSEMENT_APPROVAL";
            case ApprovalBusinessCodes.INV_TRANSFER -> "INV_TRANSFER_APPROVAL";
            case ApprovalBusinessCodes.INV_STOCK_CHECK -> "INV_STOCK_CHECK_APPROVAL";
            case ApprovalBusinessCodes.HR_HEALTH_CERTIFICATE -> "HR_HEALTH_CERT_REVIEW";
            case ApprovalBusinessCodes.OA_ATTENDANCE_LEAVE ->
                    "OA_ATTENDANCE_LEAVE_APPROVAL";
            case ApprovalBusinessCodes.OA_ATTENDANCE_CORRECTION ->
                    "OA_ATTENDANCE_CORRECTION_APPROVAL";
            default -> businessCode + "_APPROVAL";
        };
    }

    private String title(String businessCode)
    {
        return switch (businessCode)
        {
            case ApprovalBusinessCodes.OA_PURCHASE -> "OA采购申请";
            case ApprovalBusinessCodes.OA_REIMBURSEMENT -> "费用报销";
            case ApprovalBusinessCodes.INV_TRANSFER -> "调拨申请";
            case ApprovalBusinessCodes.INV_STOCK_CHECK -> "库存盘点";
            case ApprovalBusinessCodes.HR_HEALTH_CERTIFICATE -> "健康证审核";
            case ApprovalBusinessCodes.OA_ATTENDANCE_LEAVE -> "请假申请";
            case ApprovalBusinessCodes.OA_ATTENDANCE_CORRECTION -> "考勤补卡";
            default -> "审批申请";
        };
    }

    private static String safe(String value)
    {
        return value == null || value.isBlank() ? "未命名" : value;
    }
}
