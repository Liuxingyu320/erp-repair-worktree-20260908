package com.erp.system.service.impl;

import java.util.List;
import org.springframework.stereotype.Service;
import com.erp.approval.api.domain.LegacyApprovalDetail;
import com.erp.approval.api.domain.LegacyApprovalInstanceSummary;
import com.erp.approval.api.domain.LegacyApprovalPage;
import com.erp.approval.api.domain.LegacyApprovalQuery;
import com.erp.approval.api.domain.LegacyApprovalTemplateSummary;
import com.erp.common.core.exception.ServiceException;
import com.erp.system.mapper.HrHealthCertificateLegacyApprovalMapper;

/** System-service side of the central, read-only Legacy Bridge. */
@Service
public class SystemLegacyApprovalService
{
    public static final String HEALTH_CERTIFICATE = "HR_HEALTH_CERTIFICATE";

    private final HrHealthCertificateLegacyApprovalMapper mapper;

    public SystemLegacyApprovalService(
            HrHealthCertificateLegacyApprovalMapper mapper)
    {
        this.mapper = mapper;
    }

    public List<LegacyApprovalTemplateSummary> templates()
    {
        LegacyApprovalTemplateSummary template =
                new LegacyApprovalTemplateSummary();
        template.setBusinessCode(HEALTH_CERTIFICATE);
        template.setTemplateName("健康证旧审核");
        template.setBusinessSource("system");
        template.setEngineMode("LEGACY");
        template.setAdapterCode(HEALTH_CERTIFICATE);
        template.setConfigSummary("旧健康证人事审核单节点；仅展示切换前记录");
        template.setSupportedAdminActions(
                "只读查看；待审核记录继续由健康证原审核入口处理");
        return List.of(template);
    }

    public LegacyApprovalPage instances(LegacyApprovalQuery query)
    {
        LegacyApprovalQuery filter = query == null
                ? new LegacyApprovalQuery() : query;
        if (filter.getBusinessCode() != null
                && !filter.getBusinessCode().isBlank()
                && !HEALTH_CERTIFICATE.equals(filter.getBusinessCode()))
        {
            LegacyApprovalPage empty = new LegacyApprovalPage();
            empty.setTotal(0);
            return empty;
        }
        filter.setBusinessCode(HEALTH_CERTIFICATE);
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
        if (!HEALTH_CERTIFICATE.equals(businessCode)
                || legacyInstanceId == null)
        {
            throw new ServiceException("健康证旧审批实例参数无效");
        }
        LegacyApprovalInstanceSummary instance =
                mapper.selectInstance(legacyInstanceId);
        if (instance == null)
        {
            throw new ServiceException("健康证旧审批实例不存在");
        }
        LegacyApprovalDetail detail = new LegacyApprovalDetail();
        detail.setInstance(instance);
        detail.setTasks(mapper.selectTasks(legacyInstanceId));
        return detail;
    }

    private static int bounded(Integer value, int min, int max,
            int defaultValue)
    {
        int normalized = value == null ? defaultValue : value;
        return Math.max(min, Math.min(max, normalized));
    }
}
