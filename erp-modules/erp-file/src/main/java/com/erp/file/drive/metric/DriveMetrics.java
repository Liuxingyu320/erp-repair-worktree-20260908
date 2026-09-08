package com.erp.file.drive.metric;

import java.time.Duration;
import java.util.Set;
import com.erp.file.drive.constant.DriveErrorCodes;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * 云盘低基数运行指标。所有标签均在服务端归类，避免文件名、用户输入等
 * 高基数或敏感信息进入监控系统。
 */
@Component
public class DriveMetrics
{
    private static final Set<String> STORAGE_OPERATIONS = Set.of("compensation_delete");
    private static final Set<String> CLEANUP_STAGES = Set.of(
            "dispatch", "executor_rejected", "mark_failed", "worker",
            "upload_reservation");

    private final MeterRegistry registry;

    public DriveMetrics(MeterRegistry registry)
    {
        this.registry = registry;
    }

    public long start()
    {
        return System.nanoTime();
    }

    public void recordUploadSuccess(long startedAt)
    {
        recordUpload(startedAt, "success", "none");
    }

    public void recordUploadFailure(long startedAt, String businessCode)
    {
        recordUpload(startedAt, "failure", uploadReason(businessCode));
    }

    public void recordStorageFailure(String operation)
    {
        try
        {
            registry.counter("erp.drive.storage.failure.total", "operation",
                    bounded(operation, STORAGE_OPERATIONS)).increment();
        }
        catch (RuntimeException ignored)
        {
            // Telemetry must never change the cloud-drive business result.
        }
    }

    public void recordCleanupFailure(String stage)
    {
        try
        {
            registry.counter("erp.drive.cleanup.failure.total", "stage",
                    bounded(stage, CLEANUP_STAGES)).increment();
        }
        catch (RuntimeException ignored)
        {
            // Telemetry must never change the cleanup result.
        }
    }

    private void recordUpload(long startedAt, String outcome, String reason)
    {
        try
        {
            long elapsed = Math.max(0L, System.nanoTime() - startedAt);
            registry.counter("erp.drive.upload.total",
                    "outcome", outcome, "reason", reason).increment();
            registry.timer("erp.drive.upload.duration",
                    "outcome", outcome, "reason", reason)
                    .record(Duration.ofNanos(elapsed));
        }
        catch (RuntimeException ignored)
        {
            // Metrics backends are best-effort and must not break uploads.
        }
    }

    private static String uploadReason(String businessCode)
    {
        if (businessCode == null)
        {
            return "other";
        }
        return switch (businessCode)
        {
            case DriveErrorCodes.DRIVE_STORAGE_UNAVAILABLE,
                    DriveErrorCodes.DRIVE_STORAGE_OBJECT_MISSING -> "storage";
            case DriveErrorCodes.DRIVE_QUOTA_EXCEEDED,
                    DriveErrorCodes.DRIVE_CAPACITY_EXCEEDED,
                    DriveErrorCodes.DRIVE_ORG_BUDGET_EXCEEDED -> "quota";
            case DriveErrorCodes.DRIVE_ACCESS_DENIED,
                    DriveErrorCodes.DRIVE_SPACE_NOT_FOUND,
                    DriveErrorCodes.DRIVE_NODE_NOT_FOUND -> "access";
            case DriveErrorCodes.DRIVE_FILE_TOO_LARGE,
                    DriveErrorCodes.DRIVE_FILE_TYPE_REJECTED -> "file_policy";
            case DriveErrorCodes.DRIVE_NAME_CONFLICT -> "conflict";
            case DriveErrorCodes.DRIVE_CONCURRENT_MODIFICATION -> "concurrent";
            case DriveErrorCodes.DRIVE_DISABLED -> "disabled";
            default -> "other";
        };
    }

    private static String bounded(String value, Set<String> allowed)
    {
        return value != null && allowed.contains(value) ? value : "other";
    }
}
