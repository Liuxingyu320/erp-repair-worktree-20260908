package com.erp.inventory.mapper;

import java.util.List;
import com.erp.inventory.domain.InvStockLog;

public interface InvStockLogMapper
{
    List<InvStockLog> selectInvStockLogList(InvStockLog stockLog);
    int insertInvStockLog(InvStockLog stockLog);
}
