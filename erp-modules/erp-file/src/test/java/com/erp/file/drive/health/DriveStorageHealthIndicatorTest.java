package com.erp.file.drive.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;
import com.erp.file.drive.config.DriveProperties;
import com.erp.file.drive.storage.DriveStorageProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

@DisplayName("云盘存储健康检查")
class DriveStorageHealthIndicatorTest
{
    @Test
    @DisplayName("Spring 上下文能够选择生产构造器完成接线")
    void shouldWireProductionConstructorInSpringContext()
    {
        DriveProperties properties = new DriveProperties();
        DriveStorageProvider storage = mock(DriveStorageProvider.class);

        new ApplicationContextRunner()
                .withBean(DriveProperties.class, () -> properties)
                .withBean(DriveStorageProvider.class, () -> storage)
                .withUserConfiguration(DriveStorageHealthIndicator.class)
                .run(context -> assertThat(context).hasNotFailed()
                        .hasSingleBean(DriveStorageHealthIndicator.class));
    }

    @Test
    @DisplayName("云盘关闭时保持整体健康且不访问存储")
    void shouldStayHealthyWithoutProbingWhenDisabled()
    {
        DriveProperties properties = new DriveProperties();
        properties.setEnabled(false);
        DriveStorageProvider storage = mock(DriveStorageProvider.class);
        DriveStorageHealthIndicator indicator = new DriveStorageHealthIndicator(
                properties, storage, () -> 0L);

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("enabled", false);
        verifyNoInteractions(storage);
    }

    @Test
    @DisplayName("健康结果按短 TTL 缓存并能在存储恢复后回到正常")
    void shouldCacheAndRecoverStorageHealth() throws Exception
    {
        DriveProperties properties = new DriveProperties();
        properties.setEnabled(true);
        properties.setStorageType("minio");
        properties.setHealthCacheMillis(1000L);
        DriveStorageProvider storage = mock(DriveStorageProvider.class);
        doNothing().doThrow(new IOException("private endpoint"))
                .doNothing().when(storage).validate();
        AtomicLong now = new AtomicLong(0L);
        DriveStorageHealthIndicator indicator = new DriveStorageHealthIndicator(
                properties, storage, now::get);

        assertThat(indicator.health().getStatus()).isEqualTo(Status.UP);
        now.set(500L);
        assertThat(indicator.health().getStatus()).isEqualTo(Status.UP);
        verify(storage).validate();

        now.set(1001L);
        Health down = indicator.health();
        assertThat(down.getStatus()).isEqualTo(Status.DOWN);
        assertThat(down.getDetails()).containsEntry("storageType", "minio");
        assertThat(down.getDetails()).containsEntry("failureType", "IOException");

        now.set(2002L);
        assertThat(indicator.health().getStatus()).isEqualTo(Status.UP);
        verify(storage, times(3)).validate();
    }

    @Test
    @DisplayName("系统时间回拨时重新探测而不是无限复用旧缓存")
    void shouldProbeAgainWhenClockMovesBackward() throws Exception
    {
        DriveProperties properties = new DriveProperties();
        properties.setEnabled(true);
        properties.setHealthCacheMillis(1000L);
        DriveStorageProvider storage = mock(DriveStorageProvider.class);
        doNothing().doThrow(new IOException("storage unavailable"))
                .when(storage).validate();
        AtomicLong now = new AtomicLong(1000L);
        DriveStorageHealthIndicator indicator = new DriveStorageHealthIndicator(
                properties, storage, now::get);

        assertThat(indicator.health().getStatus()).isEqualTo(Status.UP);
        now.set(500L);

        assertThat(indicator.health().getStatus()).isEqualTo(Status.DOWN);
        verify(storage, times(2)).validate();
    }
}
