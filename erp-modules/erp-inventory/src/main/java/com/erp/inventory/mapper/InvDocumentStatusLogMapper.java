package com.erp.inventory.mapper;

import java.util.List;
import com.erp.inventory.domain.InvDocumentStatusLog;

public interface InvDocumentStatusLogMapper
{
    List<InvDocumentStatusLog> selectInvDocumentStatusLogList(InvDocumentStatusLog documentStatusLog);
}
