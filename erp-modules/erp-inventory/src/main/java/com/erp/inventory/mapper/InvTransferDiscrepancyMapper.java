package com.erp.inventory.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.erp.inventory.domain.InvTransferDiscrepancy;
import com.erp.inventory.domain.InvTransferDiscrepancyDetail;

public interface InvTransferDiscrepancyMapper
{
    int insertDiscrepancy(InvTransferDiscrepancy discrepancy);
    int batchInsertDetails(List<InvTransferDiscrepancyDetail> details);
    InvTransferDiscrepancy selectById(Long discrepancyId);
    InvTransferDiscrepancy selectByIdForUpdate(Long discrepancyId);
    List<InvTransferDiscrepancy> selectByTransferId(Long transferId);
    List<InvTransferDiscrepancyDetail> selectDetails(Long discrepancyId);
    int countOpenByTransferId(@Param("transferId") Long transferId,
            @Param("excludeDiscrepancyId") Long excludeDiscrepancyId);
    int resolve(@Param("discrepancyId") Long discrepancyId,
            @Param("version") Long version, @Param("status") String status,
            @Param("decision") String decision,
            @Param("responsibleParty") String responsibleParty,
            @Param("note") String note,
            @Param("handledByUserId") Long handledByUserId,
            @Param("handledByName") String handledByName);
}
