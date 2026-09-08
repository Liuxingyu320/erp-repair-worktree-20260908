package com.erp.oa.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.erp.oa.domain.OaPurchase;

public interface OaPurchaseMapper
{
    int insertOaPurchase(OaPurchase purchase);

    int updateOaPurchase(OaPurchase purchase);

    int markApprovalSubmitting(@Param("purchaseId") Long purchaseId,
            @Param("expectedStatus") String expectedStatus,
            @Param("expectedVersion") Long expectedVersion,
            @Param("businessRound") Integer businessRound,
            @Param("updateBy") String updateBy);

    int finalizeApprovalStart(@Param("purchaseId") Long purchaseId,
            @Param("businessRound") Integer businessRound,
            @Param("expectedVersion") Long expectedVersion,
            @Param("instanceId") Long instanceId,
            @Param("updateBy") String updateBy);

    OaPurchase selectOaPurchaseById(Long purchaseId);

    OaPurchase selectOaPurchaseByIdForUpdate(Long purchaseId);

    List<OaPurchase> selectOaPurchaseList(OaPurchase purchase);

    List<OaPurchase> selectMyOaPurchaseList(OaPurchase purchase);
}
