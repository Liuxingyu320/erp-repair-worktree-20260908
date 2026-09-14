package com.erp.system.mapper;

import java.util.Date;
import org.apache.ibatis.annotations.Param;
import com.erp.system.domain.SysUserPushDelivery;

/**
 * Persistence boundary for durable mobile-push idempotency claims.
 */
public interface SysUserPushDeliveryMapper
{
    int insertIgnore(SysUserPushDelivery delivery);

    SysUserPushDelivery selectByBusinessKeyHash(@Param("userId") Long userId,
            @Param("channel") String channel,
            @Param("businessKeyHash") String businessKeyHash);

    int claimForSending(@Param("deliveryId") Long deliveryId,
            @Param("expectedStatus") String expectedStatus,
            @Param("version") Long version,
            @Param("staleSendingBefore") Date staleSendingBefore);

    int markSent(@Param("deliveryId") Long deliveryId,
            @Param("version") Long version,
            @Param("lastResult") String lastResult);

    int markSkipped(@Param("deliveryId") Long deliveryId,
            @Param("version") Long version,
            @Param("lastResult") String lastResult);

    int markRetry(@Param("deliveryId") Long deliveryId,
            @Param("version") Long version,
            @Param("lastError") String lastError);

    int markDead(@Param("deliveryId") Long deliveryId,
            @Param("version") Long version,
            @Param("lastError") String lastError);
}
