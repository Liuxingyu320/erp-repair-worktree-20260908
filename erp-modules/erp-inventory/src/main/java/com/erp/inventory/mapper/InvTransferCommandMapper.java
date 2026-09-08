package com.erp.inventory.mapper;

import org.apache.ibatis.annotations.Param;
import com.erp.inventory.domain.InvTransferCommand;

public interface InvTransferCommandMapper
{
    int insertIfAbsent(InvTransferCommand command);

    InvTransferCommand selectByRequestIdForUpdate(
            @Param("requestId") String requestId);

    int completeIfPending(@Param("requestId") String requestId,
            @Param("requestFingerprint") String requestFingerprint,
            @Param("resultType") String resultType,
            @Param("resultPayload") String resultPayload);
}
