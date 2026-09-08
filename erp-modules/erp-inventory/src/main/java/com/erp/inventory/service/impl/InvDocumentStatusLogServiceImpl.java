package com.erp.inventory.service.impl;

import java.util.Collections;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.security.utils.SecurityUtils;
import com.erp.inventory.domain.InvDocumentStatusLog;
import com.erp.inventory.mapper.InvDocumentStatusLogMapper;
import com.erp.inventory.service.IInvDocumentStatusLogService;

@Service
public class InvDocumentStatusLogServiceImpl extends InvBaseService implements IInvDocumentStatusLogService
{
    @Autowired
    private InvDocumentStatusLogMapper documentStatusLogMapper;

    @Override
    public List<InvDocumentStatusLog> selectDocumentStatusLogList(InvDocumentStatusLog documentStatusLog,
            Long selectedShopDeptId)
    {
        InvDocumentStatusLog query = documentStatusLog == null ? new InvDocumentStatusLog() : documentStatusLog;
        appendAuditScope(query, selectedShopDeptId);
        return documentStatusLogMapper.selectInvDocumentStatusLogList(query);
    }

    private void appendAuditScope(InvDocumentStatusLog query, Long selectedShopDeptId)
    {
        if (selectedShopDeptId != null && selectedShopDeptId != 0)
        {
            Long scopeRoot = requireSelectedShopDept(selectedShopDeptId);
            if (!SecurityUtils.isAdmin())
            {
                assertUserShopScope(scopeRoot);
            }
            List<Long> scopeDeptIds = deptScopeMapper.selectSubDeptIds(scopeRoot);
            query.getParams().put("scopeDeptIds", nonEmptyScope(scopeDeptIds));
            return;
        }
        if (!SecurityUtils.isAdmin())
        {
            requireSelectedShopDept(selectedShopDeptId);
        }
    }

    private List<Long> nonEmptyScope(List<Long> scopeDeptIds)
    {
        return scopeDeptIds == null || scopeDeptIds.isEmpty() ? Collections.singletonList(-1L) : scopeDeptIds;
    }

    @Override
    protected void assertUserShopScope(Long shopDeptId)
    {
        if (deptScopeMapper.countUserShopScope(SecurityUtils.getUserId(), shopDeptId) <= 0)
        {
            throw new ServiceException("当前用户无权选择该店铺");
        }
    }
}
