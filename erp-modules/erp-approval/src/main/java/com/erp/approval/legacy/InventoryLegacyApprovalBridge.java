package com.erp.approval.legacy;

import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;
import com.erp.approval.api.domain.LegacyApprovalDetail;
import com.erp.approval.api.domain.LegacyApprovalPage;
import com.erp.approval.api.domain.LegacyApprovalQuery;
import com.erp.approval.api.domain.LegacyApprovalTemplateSummary;
import com.erp.approval.legacy.client.InventoryLegacyApprovalClient;
import com.erp.common.core.constant.SecurityConstants;
import com.erp.common.core.domain.R;
import com.erp.common.core.exception.ServiceException;

@Component
public class InventoryLegacyApprovalBridge implements LegacyApprovalBridge
{
    private static final Set<String> CODES = Set.of(
            "INV_TRANSFER", "INV_STOCK_CHECK");
    private final InventoryLegacyApprovalClient client;

    public InventoryLegacyApprovalBridge(InventoryLegacyApprovalClient client)
    {
        this.client = client;
    }

    @Override
    public Set<String> businessCodes() { return CODES; }

    @Override
    public List<LegacyApprovalTemplateSummary> templates()
    {
        return require(client.templates(SecurityConstants.INNER),
                "查询库存旧审批模板失败");
    }

    @Override
    public LegacyApprovalPage instances(LegacyApprovalQuery query)
    {
        LegacyApprovalQuery filter = query == null
                ? new LegacyApprovalQuery() : query;
        return require(client.instances(filter.getBusinessCode(),
                filter.getBusinessId(), filter.getStatus(),
                filter.getPageNum(), filter.getPageSize(),
                SecurityConstants.INNER), "查询库存旧审批实例失败");
    }

    @Override
    public LegacyApprovalDetail detail(String businessCode,
            Long legacyInstanceId)
    {
        if (!CODES.contains(businessCode))
        {
            throw new ServiceException("库存Legacy Bridge不支持该业务");
        }
        return require(client.detail(businessCode, legacyInstanceId,
                SecurityConstants.INNER), "查询库存旧审批详情失败");
    }

    private <T> T require(R<T> response, String message)
    {
        if (response == null || !R.isSuccess(response)
                || response.getData() == null)
        {
            throw new ServiceException(response == null ? message
                    : (response.getMsg() == null ? message : response.getMsg()));
        }
        return response.getData();
    }
}
