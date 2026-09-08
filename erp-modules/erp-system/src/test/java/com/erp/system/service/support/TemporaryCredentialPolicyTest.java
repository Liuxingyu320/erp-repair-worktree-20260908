package com.erp.system.service.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Date;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("临时凭证过期策略")
class TemporaryCredentialPolicyTest
{
    private static final Clock FIXED = Clock.fixed(Instant.parse("2026-07-11T00:00:00Z"), ZoneId.of("UTC"));

    @Test
    @DisplayName("固定时钟下到期时间为创建时间 +24 小时")
    void expiresAtIsExactlyTwentyFourHoursFromClock()
    {
        TemporaryCredentialPolicy policy = new TemporaryCredentialPolicy();

        Instant expires = policy.expiresAtInstant(FIXED);
        Date expiresDate = policy.expiresAt(FIXED);

        assertThat(expires).isEqualTo(Instant.parse("2026-07-12T00:00:00Z"));
        assertThat(expiresDate).isEqualTo(Date.from(Instant.parse("2026-07-12T00:00:00Z")));
        assertThat(Duration.between(FIXED.instant(), expires)).isEqualTo(Duration.ofHours(24));
        assertThat(policy.ttl()).isEqualTo(Duration.ofHours(24));
    }

    @Test
    @DisplayName("拒绝非正 TTL")
    void rejectsNonPositiveTtl()
    {
        assertThatThrownBy(() -> new TemporaryCredentialPolicy(Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TemporaryCredentialPolicy(Duration.ofHours(-1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TemporaryCredentialPolicy(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("拒绝空时钟")
    void rejectsNullClock()
    {
        TemporaryCredentialPolicy policy = new TemporaryCredentialPolicy();
        assertThatThrownBy(() -> policy.expiresAt(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> policy.expiresAtInstant(null)).isInstanceOf(NullPointerException.class);
    }
}
