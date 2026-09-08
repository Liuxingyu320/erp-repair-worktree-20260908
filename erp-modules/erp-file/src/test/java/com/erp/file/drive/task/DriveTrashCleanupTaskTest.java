package com.erp.file.drive.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.lang.reflect.Method;
import com.erp.file.ErpFileApplication;
import com.erp.file.drive.config.DriveProperties;
import com.erp.file.drive.service.DriveTrashService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@DisplayName("云盘回收站定时清理")
class DriveTrashCleanupTaskTest
{
    @Test
    @DisplayName("应用启用调度且清理方法使用可配置时区和 cron")
    void shouldDeclareConfiguredSchedule() throws Exception
    {
        assertThat(ErpFileApplication.class.getAnnotation(EnableScheduling.class)).isNotNull();
        Method cleanup = DriveTrashCleanupTask.class.getMethod("cleanup");
        Scheduled scheduled = cleanup.getAnnotation(Scheduled.class);

        assertThat(scheduled.cron())
                .isEqualTo("${drive.cleanup-cron:0 30 2 * * *}");
        assertThat(scheduled.zone())
                .isEqualTo("${drive.cleanup-zone:Asia/Shanghai}");
    }

    @Test
    @DisplayName("功能关闭时定时任务不查询回收批次")
    void shouldSkipCleanupWhenFeatureDisabled()
    {
        DriveProperties properties = new DriveProperties();
        DriveTrashService service = mock(DriveTrashService.class);
        DriveTrashCleanupTask task = new DriveTrashCleanupTask(properties, service);

        task.cleanup();

        verify(service, never()).dispatchExpired(100);
    }

    @Test
    @DisplayName("功能开启时每次最多派发一百个到期批次")
    void shouldDispatchBoundedBatchWhenEnabled()
    {
        DriveProperties properties = new DriveProperties();
        properties.setEnabled(true);
        DriveTrashService service = mock(DriveTrashService.class);
        DriveTrashCleanupTask task = new DriveTrashCleanupTask(properties, service);

        task.cleanup();

        verify(service).dispatchExpired(100);
    }
}
