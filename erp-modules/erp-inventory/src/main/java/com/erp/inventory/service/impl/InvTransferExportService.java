package com.erp.inventory.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.erp.common.core.exception.ServiceException;
import com.erp.inventory.domain.InvTransferOrder;
import com.erp.inventory.mapper.InvTransferOrderMapper;

/** Bounded, scope-aware query path dedicated to synchronous transfer export. */
@Service
public class InvTransferExportService extends InvBaseService
{
    public static final int MAX_SYNC_EXPORT_ROWS = 10_000;
    static final int EXPORT_PROBE_ROWS = MAX_SYNC_EXPORT_ROWS + 1;

    @Autowired
    private InvTransferOrderMapper transferOrderMapper;

    public List<InvTransferOrder> selectForExport(InvTransferOrder query,
            Long selectedShopDeptId)
    {
        if (query == null)
        {
            throw new ServiceException("导出查询条件不能为空");
        }

        // The mapper still contains the framework-owned dataScope fragment.
        // This export path uses an explicit organization-id allowlist instead,
        // so never accept a request-bound SQL fragment from the query object.
        query.getParams().remove("dataScope");
        Long scopeRoot = resolveAndValidateShopDept(selectedShopDeptId);
        List<Long> scopeDeptIds = deptScopeMapper.selectSubDeptIds(scopeRoot);
        // Fail closed if an organization exists but currently resolves to no
        // readable inventory departments. Never turn an empty scope into an
        // unscoped export.
        query.getParams().put("scopeDeptIds",
                scopeDeptIds == null || scopeDeptIds.isEmpty()
                        ? List.of(-1L) : scopeDeptIds);
        query.getParams().put("rowLimit", EXPORT_PROBE_ROWS);

        List<InvTransferOrder> rows = transferOrderMapper
                .selectInvTransferOrderList(query);
        if (rows == null || rows.isEmpty())
        {
            return List.of();
        }
        if (rows.size() > MAX_SYNC_EXPORT_ROWS)
        {
            throw new ServiceException("导出数据超过 10000 行，请缩小筛选范围后重试");
        }
        return rows;
    }
}
