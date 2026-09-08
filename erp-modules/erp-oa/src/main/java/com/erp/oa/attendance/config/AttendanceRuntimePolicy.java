package com.erp.oa.attendance.config;

import org.springframework.stereotype.Component;
import com.erp.common.core.constant.SecurityConstants;
import com.erp.common.core.domain.R;
import com.erp.system.api.RemoteConfigService;

/** Reads the sys_config keys seeded by the attendance migration. */
@Component
public class AttendanceRuntimePolicy
{
    public static final String CHALLENGE_TTL_KEY =
            "oa.attendance.challenge.ttl.seconds";
    public static final String PHOTO_MAX_BYTES_KEY =
            "oa.attendance.photo.max.bytes";

    private final AttendanceV2Properties properties;
    private final RemoteConfigService configService;

    public AttendanceRuntimePolicy(AttendanceV2Properties properties,
            RemoteConfigService configService)
    {
        this.properties = properties;
        this.configService = configService;
    }

    public int challengeTtlSeconds()
    {
        return Math.toIntExact(readLong(CHALLENGE_TTL_KEY,
                properties.getChallengeTtlSeconds(), 30, 600));
    }

    public long maxPhotoBytes()
    {
        return readLong(PHOTO_MAX_BYTES_KEY, properties.getMaxPhotoBytes(),
                128L * 1024L, 20L * 1024L * 1024L);
    }

    private long readLong(String key, long fallback, long min, long max)
    {
        try
        {
            R<String> response = configService.getConfigKey(key,
                    SecurityConstants.INNER);
            if (response == null || R.isError(response)
                    || response.getData() == null)
                return clamp(fallback, min, max);
            long value = Long.parseLong(response.getData().trim());
            return value < min || value > max
                    ? clamp(fallback, min, max) : value;
        }
        catch (RuntimeException ex)
        {
            // The feature gate already fails closed when sys_config is
            // unavailable.  This safe bounded fallback also protects tests and
            // an in-flight request from an invalid optional policy value.
            return clamp(fallback, min, max);
        }
    }

    private long clamp(long value, long min, long max)
    { return Math.max(min, Math.min(max, value)); }
}
