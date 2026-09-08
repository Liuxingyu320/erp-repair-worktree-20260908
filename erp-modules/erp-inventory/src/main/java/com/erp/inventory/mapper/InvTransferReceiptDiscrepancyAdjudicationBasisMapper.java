package com.erp.inventory.mapper;

import org.apache.ibatis.annotations.Param;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationBasisPolicy.PreparedBasis;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationBasisPolicy.PreparedConsumption;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyStoredAdjudicationBasis;
import com.erp.inventory.domain.transfer.InvTransferReceiptGeneratedId;

/** Narrow persistence boundary for issuing and consuming an opaque basis. */
public interface InvTransferReceiptDiscrepancyAdjudicationBasisMapper
{
    int insertIssuedBasis(@Param("basis") PreparedBasis basis,
            @Param("generatedId") InvTransferReceiptGeneratedId generatedId);

    InvTransferReceiptDiscrepancyStoredAdjudicationBasis
            selectByTokenHashForUpdate(
                    @Param("tokenHash") String tokenHash);

    int consumeIssuedBasis(
            @Param("consumption") PreparedConsumption consumption);
}
