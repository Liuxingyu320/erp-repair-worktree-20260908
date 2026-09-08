package com.erp.oa.attendance.support;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import com.erp.common.core.exception.ServiceException;
import com.erp.oa.attendance.domain.AttendanceModels.Challenge;

class AttendanceChallengePolicyTest
{
    private final AttendanceChallengePolicy policy =
            new AttendanceChallengePolicy();

    @Test
    void challengeIsBoundToUserTypeExpiryAndSingleUse()
    {
        LocalDateTime now = LocalDateTime.of(2026, 8, 20, 8, 0);
        Challenge value = new Challenge();
        value.userId = 7L;
        value.punchType = "IN";
        value.status = "ISSUED";
        value.expiresAt = now.plusMinutes(3);
        assertThatCode(() -> policy.requireUsable(value, 7L, "IN", now))
                .doesNotThrowAnyException();

        assertThatThrownBy(() -> policy.requireUsable(value, 8L, "IN", now))
                .isInstanceOf(ServiceException.class)
                .hasMessage("PUNCH_CHALLENGE_INVALID");
        assertThatThrownBy(() -> policy.requireUsable(value, 7L, "IN",
                now.plusMinutes(4))).hasMessage("PUNCH_CHALLENGE_EXPIRED");
        value.consumedAt = now;
        assertThatThrownBy(() -> policy.requireUsable(value, 7L, "IN", now))
                .hasMessage("PUNCH_CHALLENGE_ALREADY_USED");
    }
}
