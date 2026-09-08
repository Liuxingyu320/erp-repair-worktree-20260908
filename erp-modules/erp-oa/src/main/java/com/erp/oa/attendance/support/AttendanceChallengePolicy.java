package com.erp.oa.attendance.support;

import java.time.LocalDateTime;
import org.springframework.stereotype.Component;
import com.erp.common.core.exception.ServiceException;
import com.erp.oa.attendance.domain.AttendanceModels.Challenge;

@Component
public class AttendanceChallengePolicy
{
    public void requireUsable(Challenge challenge, Long userId,
            String punchType, LocalDateTime now)
    {
        if (challenge == null || userId == null
                || !userId.equals(challenge.userId)
                || punchType == null
                || !punchType.equals(challenge.punchType))
        {
            throw new ServiceException("PUNCH_CHALLENGE_INVALID");
        }
        if (!"ISSUED".equals(challenge.status)
                || challenge.consumedAt != null)
        {
            throw new ServiceException("PUNCH_CHALLENGE_ALREADY_USED");
        }
        if (challenge.expiresAt == null || challenge.expiresAt.isBefore(now))
        {
            throw new ServiceException("PUNCH_CHALLENGE_EXPIRED");
        }
    }
}
