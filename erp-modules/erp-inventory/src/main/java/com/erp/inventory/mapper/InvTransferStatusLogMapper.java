package com.erp.inventory.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.erp.inventory.domain.InvTransferStatusLog;

public interface InvTransferStatusLogMapper
{
    int insertLog(InvTransferStatusLog log);
    List<InvTransferStatusLog> selectLogsByTransferId(@Param("transferId") Long transferId);
}
