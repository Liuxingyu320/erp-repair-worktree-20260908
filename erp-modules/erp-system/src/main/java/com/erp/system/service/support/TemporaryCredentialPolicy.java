package com.erp.system.service.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Objects;

/**
 * Single source of truth for temporary credential expiry (24 hours from the given clock).
 */
public final class TemporaryCredentialPolicy
{
    public static final Duration DEFAULT_TTL = Duration.ofHours(24);

    private final Duration ttl;

    public TemporaryCredentialPolicy()
    {
        this(DEFAULT_TTL);
    }

    public TemporaryCredentialPolicy(Duration ttl)
    {
        if (ttl == null || ttl.isZero() || ttl.isNegative())
        {
            throw new IllegalArgumentException("temporary credential ttl must be positive");
        }
        this.ttl = ttl;
    }

    public Duration ttl()
    {
        return ttl;
    }

    public Instant expiresAtInstant(Clock clock)
    {
        Objects.requireNonNull(clock, "clock");
        return clock.instant().plus(ttl);
    }

    public Date expiresAt(Clock clock)
    {
        return Date.from(expiresAtInstant(clock));
    }
}
