package com.erp.inventory.mapper;

import java.util.List;
import java.math.BigDecimal;
import org.apache.ibatis.annotations.Param;
import com.erp.inventory.domain.InvDeliveryNoticeDetail;

public interface InvDeliveryNoticeDetailMapper
{
    List<InvDeliveryNoticeDetail> selectInvDeliveryNoticeDetailByNoticeId(Long noticeId);
    int batchInsertInvDeliveryNoticeDetail(List<InvDeliveryNoticeDetail> details);
    int accumulateDelivery(@Param("detailId") Long detailId,
            @Param("expectedDeliveredQty") BigDecimal expectedDeliveredQty,
            @Param("batchQty") BigDecimal batchQty,
            @Param("batchCost") BigDecimal batchCost);
    int deleteInvDeliveryNoticeDetailByNoticeId(Long noticeId);
}
