package com.erp.system.mapper;

import java.util.Date;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.erp.system.domain.SecuritySessionInvalidationOutbox;

public interface SecuritySessionInvalidationOutboxMapper
{
    int insert(SecuritySessionInvalidationOutbox outbox);

    int markDone(@Param("eventId") String eventId);

    int releaseForRetry(@Param("eventId") String eventId,
            @Param("nextAttemptAt") Date nextAttemptAt,
            @Param("lastErrorCode") String lastErrorCode,
            @Param("terminal") boolean terminal);

    int resetStaleClaims(@Param("staleBefore") Date staleBefore);

    int claimPending(@Param("lockOwner") String lockOwner, @Param("limit") int limit);

    List<SecuritySessionInvalidationOutbox> selectClaimed(String lockOwner);
}

