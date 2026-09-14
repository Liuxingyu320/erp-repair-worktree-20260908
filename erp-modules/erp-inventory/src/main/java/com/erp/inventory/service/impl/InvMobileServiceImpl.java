package com.erp.inventory.service.impl;

import java.time.LocalDate;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.erp.inventory.domain.InvStock;
import com.erp.inventory.domain.vo.InvReportSummary;
import com.erp.inventory.service.IInvReportService;
import com.erp.common.core.domain.todo.TodoConstants;
import com.erp.common.core.domain.todo.TodoQuery;
import com.erp.common.core.domain.todo.TodoSummary;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.security.auth.AuthUtil;
import com.erp.common.security.utils.SecurityUtils;
import com.erp.inventory.constant.InvTodoTypes;
import com.erp.inventory.domain.vo.MobileOption;
import com.erp.inventory.domain.vo.MobileTransferApprovalTodo;
import com.erp.inventory.domain.vo.MobileWorkbenchSummary;
import com.erp.inventory.mapper.InvMobileMapper;
import com.erp.inventory.service.IInvMobileService;
import com.erp.inventory.service.IInvTodoService;

@Service
public class InvMobileServiceImpl extends InvBaseService implements IInvMobileService
{
    private static final Logger log = LoggerFactory.getLogger(InvMobileServiceImpl.class);
    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 50;
    private static final Map<String, String> TYPE_PERMISSION_MAP = new HashMap<>();

    static
    {
        TYPE_PERMISSION_MAP.put("product", "inv:product:list");
        TYPE_PERMISSION_MAP.put("customer", "inv:customer:list");
        TYPE_PERMISSION_MAP.put("supplier", "inv:supplier:list");
        TYPE_PERMISSION_MAP.put("warehouse", "inv:stock:list");
    }

    @Autowired
    private InvMobileMapper mobileMapper;

    @Autowired
    private IInvTodoService todoService;

    @Autowired
    private IInvReportService reportService;

    @Override
    public List<MobileOption> selectOptions(String type, String keyword, Integer limit, Long selectedShopDeptId)
    {
        String normalizedType = normalizeType(type);
        checkOptionPermission(normalizedType);
        if ("warehouse".equals(normalizedType))
        {
            return Collections.emptyList();
        }
        String normalizedKeyword = normalizeKeyword(keyword);
        int safeLimit = normalizeLimit(limit);

        if ("product".equals(normalizedType))
        {
            return mobileMapper.selectProductOptions(normalizedKeyword, relatedScopedParams(selectedShopDeptId), safeLimit);
        }
        if ("supplier".equals(normalizedType))
        {
            return mobileMapper.selectSupplierOptions(normalizedKeyword, relatedScopedParams(selectedShopDeptId), safeLimit);
        }
        Map<String, Object> params = scopedParams(selectedShopDeptId);
        if ("customer".equals(normalizedType))
        {
            return mobileMapper.selectCustomerOptions(normalizedKeyword, params, safeLimit);
        }
        throw new ServiceException("不支持的手机端选项类型");
    }

    @Override
    public MobileWorkbenchSummary selectWorkbenchSummary(Long selectedShopDeptId)
    {
        Long selectedDeptId = resolveAndValidateShopDept(selectedShopDeptId);
        TodoQuery query = new TodoQuery();
        query.setScopeMode(TodoConstants.SCOPE_CURRENT_ORG);
        TodoSummary todoSummary = todoService.selectSummary(query, selectedDeptId);

        long purchaseReceiveCount = typeCount(todoSummary, InvTodoTypes.INV_PURCHASE_RECEIVE);
        long purchaseQcCount = typeCount(todoSummary, InvTodoTypes.INV_PURCHASE_QC);
        long transferReceiveCount = typeCount(todoSummary, InvTodoTypes.INV_TRANSFER_RECEIVE);
        long deliveryNoticeCount = typeCount(todoSummary, InvTodoTypes.INV_DELIVERY_EXECUTE);
        long transferDeliverCount = typeCount(todoSummary, InvTodoTypes.INV_TRANSFER_DELIVER);
        long lowStockCount = typeCount(todoSummary, InvTodoTypes.INV_LOW_STOCK)
                + typeCount(todoSummary, InvTodoTypes.INV_OUT_OF_STOCK);
        long pendingReceiveCount = purchaseReceiveCount + transferReceiveCount;
        long pendingDeliverCount = deliveryNoticeCount + transferDeliverCount;

        MobileWorkbenchSummary summary = new MobileWorkbenchSummary();
        summary.setSelectedDeptId(selectedDeptId);
        summary.setSelectedDeptType(deptScopeMapper.selectDeptTypeById(selectedDeptId));
        summary.setLowStockCount(lowStockCount);
        summary.setPurchaseReceiveCount(purchaseReceiveCount);
        summary.setPurchaseQcCount(purchaseQcCount);
        summary.setPurchasePendingCount(purchaseReceiveCount + purchaseQcCount);
        summary.setTransferReceiveCount(transferReceiveCount);
        summary.setDeliveryNoticeCount(deliveryNoticeCount);
        summary.setTransferDeliverCount(transferDeliverCount);
        summary.setPendingApprovalCount(todoSummary.getApproval());
        summary.setPendingReceiveCount(pendingReceiveCount);
        summary.setPendingDeliverCount(pendingDeliverCount);
        summary.setTodoCount(todoSummary.getTotal());
        summary.setPendingStockCheckCount(typeCount(todoSummary, InvTodoTypes.INV_STOCK_CHECK_EXECUTE));
        summary.setPendingReturnCount(typeCount(todoSummary, DEPT_TYPE_STORE.equals(summary.getSelectedDeptType())
                ? InvTodoTypes.INV_SALES_RETURN_CONFIRM : InvTodoTypes.INV_PURCHASE_RETURN_CONFIRM));
        if (DEPT_TYPE_STORE.equals(summary.getSelectedDeptType()))
        {
            populateTodaySales(summary, selectedDeptId);
        }
        return summary;
    }

    private void populateTodaySales(MobileWorkbenchSummary summary, Long selectedDeptId)
    {
        // Keep the existing report definition, including delivered quantity > 0,
        // business order date, organization scope and report permission.
        summary.setTodaySalesStatus("forbidden");
        if (!AuthUtil.hasPermi("inv:report:list"))
        {
            return;
        }
        String today = LocalDate.now().toString();
        summary.setTodaySalesDate(today);
        InvStock query = new InvStock();
        query.getParams().put("beginTime", today);
        query.getParams().put("endTime", today);
        try
        {
            InvReportSummary report = reportService.selectReportSummary(query, selectedDeptId);
            summary.setTodaySalesCount(report == null ? null : report.getSalesOrderCount());
            summary.setTodaySalesStatus(summary.getTodaySalesCount() == null ? "unavailable" : "ready");
        }
        catch (RuntimeException error)
        {
            // A failed sales metric must not hide otherwise available warehouse/todo facts.
            summary.setTodaySalesStatus("unavailable");
            log.warn("Mobile sales count unavailable for selected department {}", selectedDeptId, error);
        }
    }

    @Override
    public List<MobileTransferApprovalTodo> selectPendingTransferApprovals(Long selectedShopDeptId)
    {
        Long selectedDeptId = resolveAndValidateShopDept(selectedShopDeptId);
        Map<String, Object> params = scopedParams(selectedDeptId);
        Long candidateUserId = SecurityUtils.getUserId();
        List<MobileTransferApprovalTodo> todos =
                mobileMapper.selectPendingTransferApprovalTasks(params,
                        candidateUserId);
        if (!canViewCost() && todos != null)
        {
            todos.forEach(todo -> {
                if (todo != null)
                {
                    todo.setTotalAmount(null);
                }
            });
        }
        return todos;
    }

    private boolean canViewCost()
    {
        if (SecurityUtils.isAdmin())
        {
            return true;
        }
        com.erp.system.api.model.LoginUser loginUser =
                SecurityUtils.getLoginUser();
        return loginUser != null && loginUser.getPermissions() != null
                && loginUser.getPermissions().contains(
                        InvTransferCostVisibilityPolicy.COST_VIEW_PERMISSION);
    }

    private Map<String, Object> relatedScopedParams(Long selectedShopDeptId)
    {
        Map<String, Object> params = new HashMap<>();
        List<Long> scopeDeptIds = resolveRelatedScopeDeptIds(selectedShopDeptId);
        if (scopeDeptIds != null && !scopeDeptIds.isEmpty())
        {
            params.put("scopeDeptIds", scopeDeptIds);
        }
        return params;
    }

    private Map<String, Object> scopedParams(Long selectedShopDeptId)
    {
        Map<String, Object> params = new HashMap<>();
        appendShopScopeToParams(params, selectedShopDeptId);
        return params;
    }

    private void checkOptionPermission(String normalizedType)
    {
        String requiredPermission = TYPE_PERMISSION_MAP.get(normalizedType);
        if (requiredPermission == null)
        {
            AuthUtil.checkLogin();
            throw new ServiceException("不支持的手机端选项类型");
        }
        AuthUtil.checkPermi(requiredPermission);
    }

    private String normalizeType(String type)
    {
        if (type == null)
        {
            return "";
        }
        return type.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeKeyword(String keyword)
    {
        if (keyword == null)
        {
            return null;
        }
        String trimmed = keyword.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private int normalizeLimit(Integer limit)
    {
        if (limit == null || limit <= 0)
        {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private long typeCount(TodoSummary summary, String type)
    {
        if (summary == null || summary.getTypeCounts() == null)
        {
            return 0L;
        }
        Long value = summary.getTypeCounts().get(type);
        return value == null ? 0L : value;
    }
}
