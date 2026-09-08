package com.erp.approval.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import com.erp.approval.api.domain.LegacyApprovalDetail;
import com.erp.approval.api.domain.LegacyApprovalInstanceSummary;
import com.erp.approval.api.domain.LegacyApprovalPage;
import com.erp.approval.api.domain.LegacyApprovalQuery;
import com.erp.approval.api.domain.LegacyApprovalTemplateSummary;
import com.erp.approval.legacy.LegacyApprovalBridge;
import com.erp.common.core.exception.ServiceException;

@Service
public class LegacyApprovalMonitorService
{
    private final List<LegacyApprovalBridge> bridges;

    public LegacyApprovalMonitorService(List<LegacyApprovalBridge> bridges)
    {
        this.bridges = List.copyOf(bridges);
    }

    public List<LegacyApprovalTemplateSummary> templates()
    {
        return bridges.stream().flatMap(item -> item.templates().stream())
                .sorted(Comparator.comparing(
                        LegacyApprovalTemplateSummary::getBusinessCode))
                .toList();
    }

    public LegacyApprovalPage instances(LegacyApprovalQuery query)
    {
        LegacyApprovalQuery filter = copyOf(query);
        int pageNum = bounded(filter.getPageNum(), 1, 100000, 1);
        int pageSize = bounded(filter.getPageSize(), 1, 100, 20);
        filter.setPageNum(pageNum);
        filter.setPageSize(pageSize);
        if (filter.getBusinessCode() != null
                && !filter.getBusinessCode().isBlank())
        {
            return bridge(filter.getBusinessCode()).instances(filter);
        }
        int needed = pageNum * pageSize;
        List<LegacyApprovalInstanceSummary> combined = new ArrayList<>();
        long total = 0;
        for (LegacyApprovalBridge bridge : bridges)
        {
            LegacyApprovalPage page = leadingPage(bridge, filter, needed);
            total = addWithoutOverflow(total, page.getTotal());
            combined.addAll(page.getRows());
        }
        combined.sort(Comparator.comparing(
                LegacyApprovalInstanceSummary::getStartedTime,
                Comparator.nullsLast(Comparator.reverseOrder())));
        int from = Math.min((pageNum - 1) * pageSize, combined.size());
        int to = Math.min(from + pageSize, combined.size());
        LegacyApprovalPage result = new LegacyApprovalPage();
        result.setRows(combined.subList(from, to));
        result.setTotal(total);
        return result;
    }

    private LegacyApprovalPage leadingPage(LegacyApprovalBridge bridge,
            LegacyApprovalQuery filter, int needed)
    {
        int fetchSize = Math.min(100, needed);
        int target = needed;
        int fetchPage = 1;
        long total = 0;
        List<LegacyApprovalInstanceSummary> rows = new ArrayList<>();
        while (rows.size() < target)
        {
            LegacyApprovalQuery bridgeQuery = copyOf(filter);
            bridgeQuery.setPageNum(fetchPage);
            bridgeQuery.setPageSize(fetchSize);
            LegacyApprovalPage page = bridge.instances(bridgeQuery);
            List<LegacyApprovalInstanceSummary> pageRows = page.getRows();
            if (fetchPage == 1)
            {
                total = Math.max(0, page.getTotal());
                target = (int) Math.min((long) needed, total);
            }
            if (target == 0 || pageRows.isEmpty())
            {
                break;
            }
            int remaining = target - rows.size();
            rows.addAll(pageRows.subList(0,
                    Math.min(remaining, pageRows.size())));
            if (pageRows.size() < fetchSize)
            {
                break;
            }
            fetchPage++;
        }
        LegacyApprovalPage result = new LegacyApprovalPage();
        result.setRows(rows);
        result.setTotal(total);
        return result;
    }

    public LegacyApprovalDetail detail(String businessCode, Long instanceId)
    {
        return bridge(businessCode).detail(businessCode, instanceId);
    }

    private LegacyApprovalBridge bridge(String businessCode)
    {
        return bridges.stream()
                .filter(item -> item.businessCodes().contains(businessCode))
                .findFirst()
                .orElseThrow(() -> new ServiceException(
                        "未注册旧审批适配器: " + businessCode));
    }

    private static LegacyApprovalQuery copyOf(LegacyApprovalQuery source)
    {
        LegacyApprovalQuery copy = new LegacyApprovalQuery();
        if (source != null)
        {
            copy.setBusinessCode(source.getBusinessCode());
            copy.setBusinessId(source.getBusinessId());
            copy.setStatus(source.getStatus());
            copy.setPageNum(source.getPageNum());
            copy.setPageSize(source.getPageSize());
        }
        return copy;
    }

    private static long addWithoutOverflow(long current, long increment)
    {
        if (increment <= 0)
        {
            return current;
        }
        return current > Long.MAX_VALUE - increment
                ? Long.MAX_VALUE : current + increment;
    }

    private static int bounded(Integer value, int min, int max,
            int defaultValue)
    {
        int normalized = value == null ? defaultValue : value;
        return Math.max(min, Math.min(max, normalized));
    }
}
