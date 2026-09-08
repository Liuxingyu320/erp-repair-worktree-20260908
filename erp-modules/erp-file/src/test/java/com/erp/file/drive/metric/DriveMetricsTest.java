package com.erp.file.drive.metric;

import static org.assertj.core.api.Assertions.assertThat;

import com.erp.file.drive.constant.DriveErrorCodes;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("云盘运行指标")
class DriveMetricsTest
{
    private SimpleMeterRegistry registry;
    private DriveMetrics metrics;

    @BeforeEach
    void setUp()
    {
        registry = new SimpleMeterRegistry();
        metrics = new DriveMetrics(registry);
    }

    @Test
    @DisplayName("上传结果只使用有界标签并记录耗时")
    void shouldRecordBoundedUploadMetrics()
    {
        long successStart = metrics.start();
        metrics.recordUploadSuccess(successStart);
        metrics.recordUploadFailure(metrics.start(),
                DriveErrorCodes.DRIVE_STORAGE_UNAVAILABLE);
        metrics.recordUploadFailure(metrics.start(),
                DriveErrorCodes.DRIVE_CAPACITY_EXCEEDED);
        metrics.recordUploadFailure(metrics.start(), "user-supplied-secret");

        assertThat(registry.get("erp.drive.upload.total")
                .tag("outcome", "success").tag("reason", "none")
                .counter().count()).isEqualTo(1D);
        assertThat(registry.get("erp.drive.upload.total")
                .tag("outcome", "failure").tag("reason", "storage")
                .counter().count()).isEqualTo(1D);
        assertThat(registry.get("erp.drive.upload.total")
                .tag("outcome", "failure").tag("reason", "quota")
                .counter().count()).isEqualTo(1D);
        assertThat(registry.get("erp.drive.upload.total")
                .tag("outcome", "failure").tag("reason", "other")
                .counter().count()).isEqualTo(1D);
        assertThat(registry.get("erp.drive.upload.duration")
                .tag("outcome", "success").tag("reason", "none")
                .timer().count()).isEqualTo(1L);
        assertThat(registry.getMeters())
                .allMatch(meter -> meter.getId().getTags().stream()
                        .noneMatch(tag -> tag.getValue().contains("secret")));
    }

    @Test
    @DisplayName("存储和回收清理失败使用固定阶段标签")
    void shouldRecordStorageAndCleanupFailures()
    {
        metrics.recordStorageFailure("compensation_delete");
        metrics.recordStorageFailure("untrusted-stage");
        metrics.recordCleanupFailure("worker");
        metrics.recordCleanupFailure("upload_reservation");
        metrics.recordCleanupFailure("untrusted-stage");

        assertThat(registry.get("erp.drive.storage.failure.total")
                .tag("operation", "compensation_delete").counter().count())
                .isEqualTo(1D);
        assertThat(registry.get("erp.drive.storage.failure.total")
                .tag("operation", "other").counter().count())
                .isEqualTo(1D);
        assertThat(registry.get("erp.drive.cleanup.failure.total")
                .tag("stage", "worker").counter().count())
                .isEqualTo(1D);
        assertThat(registry.get("erp.drive.cleanup.failure.total")
                .tag("stage", "upload_reservation").counter().count())
                .isEqualTo(1D);
        assertThat(registry.get("erp.drive.cleanup.failure.total")
                .tag("stage", "other").counter().count())
                .isEqualTo(1D);
    }
}
