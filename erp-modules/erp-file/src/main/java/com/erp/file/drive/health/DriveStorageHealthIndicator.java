package com.erp.file.drive.health;

import java.util.Objects;
import java.util.function.LongSupplier;
import com.erp.file.drive.config.DriveProperties;
import com.erp.file.drive.storage.DriveStorageProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * 云盘私有存储健康检查。结果短时缓存，避免每次健康探测都访问存储端点。
 */
@Component
public class DriveStorageHealthIndicator implements HealthIndicator
{
    private static final long MIN_CACHE_MILLIS = 1000L;

    private final DriveProperties properties;
    private final DriveStorageProvider storage;
    private final LongSupplier clock;
    private volatile CachedHealth cached;

    @Autowired
    public DriveStorageHealthIndicator(DriveProperties properties,
            DriveStorageProvider storage)
    {
        this(properties, storage, System::currentTimeMillis);
    }

    DriveStorageHealthIndicator(DriveProperties properties,
            DriveStorageProvider storage, LongSupplier clock)
    {
        this.properties = Objects.requireNonNull(properties);
        this.storage = Objects.requireNonNull(storage);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public Health health()
    {
        if (!properties.isEnabled())
        {
            cached = null;
            return Health.up().withDetail("enabled", false).build();
        }

        long now = clock.getAsLong();
        long ttl = Math.max(MIN_CACHE_MILLIS, properties.getHealthCacheMillis());
        CachedHealth snapshot = cached;
        if (isFresh(snapshot, now, ttl))
        {
            return snapshot.health();
        }

        synchronized (this)
        {
            now = clock.getAsLong();
            snapshot = cached;
            if (isFresh(snapshot, now, ttl))
            {
                return snapshot.health();
            }
            Health result = probe();
            cached = new CachedHealth(now, result);
            return result;
        }
    }

    private static boolean isFresh(CachedHealth snapshot, long now, long ttl)
    {
        return snapshot != null
                && now >= snapshot.checkedAt()
                && now - snapshot.checkedAt() < ttl;
    }

    private Health probe()
    {
        try
        {
            storage.validate();
            return Health.up()
                    .withDetail("enabled", true)
                    .withDetail("storageType", properties.getStorageType())
                    .build();
        }
        catch (Exception ex)
        {
            return Health.down()
                    .withDetail("enabled", true)
                    .withDetail("storageType", properties.getStorageType())
                    .withDetail("failureType", ex.getClass().getSimpleName())
                    .build();
        }
    }

    private record CachedHealth(long checkedAt, Health health)
    {
    }
}
