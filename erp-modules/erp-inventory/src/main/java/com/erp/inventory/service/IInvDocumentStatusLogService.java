package com.erp.inventory.service;

import java.util.List;
import com.erp.inventory.domain.InvDocumentStatusLog;

public interface IInvDocumentStatusLogService
{
    List<InvDocumentStatusLog> selectDocumentStatusLogList(InvDocumentStatusLog documentStatusLog,
            Long selectedShopDeptId);
}
