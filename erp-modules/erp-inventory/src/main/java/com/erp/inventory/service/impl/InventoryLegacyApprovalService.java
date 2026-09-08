package com.erp.inventory.service.impl;

import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import com.erp.approval.api.domain.LegacyApprovalDetail;
import com.erp.approval.api.domain.LegacyApprovalInstanceSummary;
import com.erp.approval.api.domain.LegacyApprovalPage;
import com.erp.approval.api.domain.LegacyApprovalQuery;
import com.erp.approval.api.domain.LegacyApprovalTemplateSummary;
import com.erp.common.core.exception.ServiceException;
import com.erp.inventory.mapper.InvLegacyApprovalMapper;

/** Read-only inventory implementation behind the central Legacy Bridge. */
@Service
public class InventoryLegacyApprovalService
{
    private static final Set<String> CODES = Set.of(
            InventoryUnifiedApprovalService.TRANSFER,
            InventoryUnifiedApprovalService.STOCK_CHECK);
    private final InvLegacyApprovalMapper mapper;

    public InventoryLegacyApprovalService(InvLegacyApprovalMapper mapper)
    {
        this.mapper = mapper;
    }

    public List<LegacyApprovalTemplateSummary> templates()
    {
        return List.of(template(InventoryUnifiedApprovalService.TRANSFER,
                        "调拨旧审批", "INV_TRANSFER",
                        "固定四级链：四级负责人→三级负责人→运营总监→总经理"),
                template(InventoryUnifiedApprovalService.STOCK_CHECK,
                        "盘点旧审批", "INV_STOCK_CHECK",
                        "运营总监责任范围审批，最终通过前复检库存快照"));
    }

    public LegacyApprovalPage instances(LegacyApprovalQuery query)
    {
        LegacyApprovalQuery filter = query == null
                ? new LegacyApprovalQuery() : query;
        if (filter.getBusinessCode() != null
                && !filter.getBusinessCode().isBlank()
                && !CODES.contains(filter.getBusinessCode()))
        {
            LegacyApprovalPage empty = new LegacyApprovalPage();
            empty.setTotal(0);
            return empty;
        }
        int pageNum = bounded(filter.getPageNum(), 1, 100000, 1);
        int pageSize = bounded(filter.getPageSize(), 1, 100, 20);
        filter.setPageNum(pageNum);
        filter.setPageSize(pageSize);
        LegacyApprovalPage page = new LegacyApprovalPage();
        page.setTotal(mapper.countInstances(filter));
        page.setRows(mapper.selectInstances(filter,
                (pageNum - 1) * pageSize, pageSize));
        return page;
    }

    public LegacyApprovalDetail detail(String businessCode,
            Long legacyInstanceId)
    {
        if (!CODES.contains(businessCode) || legacyInstanceId == null)
        {
            throw new ServiceException("旧审批实例参数无效");
        }
        LegacyApprovalInstanceSummary instance = mapper.selectInstance(
                businessCode, legacyInstanceId);
        if (instance == null)
        {
            throw new ServiceException("旧审批实例不存在");
        }
        LegacyApprovalDetail detail = new LegacyApprovalDetail();
        detail.setInstance(instance);
        detail.setTasks(mapper.selectTasks(businessCode, legacyInstanceId));
        return detail;
    }

    private LegacyApprovalTemplateSummary template(String businessCode,
            String name, String adapterCode, String summary)
    {
        LegacyApprovalTemplateSummary template =
                new LegacyApprovalTemplateSummary();
        template.setBusinessCode(businessCode);
        template.setTemplateName(name);
        template.setBusinessSource("inventory");
        template.setEngineMode("LEGACY");
        template.setAdapterCode(adapterCode);
        template.setConfigSummary(summary);
        template.setSupportedAdminActions("只读查看；处理动作继续由旧业务接口执行");
        return template;
    }

    private static int bounded(Integer value, int min, int max,
            int defaultValue)
    {
        int normalized = value == null ? defaultValue : value;
        return Math.max(min, Math.min(max, normalized));
    }
}
