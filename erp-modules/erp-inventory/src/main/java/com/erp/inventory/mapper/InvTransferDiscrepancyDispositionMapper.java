package com.erp.inventory.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.erp.inventory.domain.InvTransferDiscrepancyDisposition;

public interface InvTransferDiscrepancyDispositionMapper
{
    int insertDisposition(InvTransferDiscrepancyDisposition disposition);

    List<InvTransferDiscrepancyDisposition> selectByDiscrepancyId(
            Long discrepancyId);

    List<InvTransferDiscrepancyDisposition> selectLatestByDiscrepancyId(
            Long discrepancyId);

    List<InvTransferDiscrepancyDisposition> selectByRequestId(
            @Param("discrepancyId") Long discrepancyId,
            @Param("requestId") String requestId);

    List<InvTransferDiscrepancyDisposition> selectByRequestIdForUpdate(
            @Param("discrepancyId") Long discrepancyId,
            @Param("requestId") String requestId);
}
