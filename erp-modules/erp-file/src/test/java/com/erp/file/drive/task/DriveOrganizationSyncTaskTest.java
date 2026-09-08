package com.erp.file.drive.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.lang.reflect.Method;
import com.erp.file.drive.config.DriveProperties;
import com.erp.file.drive.service.DriveOrganizationReconcileService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

@DisplayName("云盘组织盘定时对账")
class DriveOrganizationSyncTaskTest
{
    @Test
    @DisplayName("使用可配置的十分钟 cron")
    void shouldDeclareConfigurableSchedule() throws Exception
    {
        Method method = DriveOrganizationSyncTask.class.getMethod("synchronize");
        Scheduled scheduled = method.getAnnotation(Scheduled.class);
        assertThat(scheduled.cron())
                .isEqualTo("${drive.organization-sync-cron:0 */10 * * * *}");
    }

    @Test
    @DisplayName("主开关或组织同步开关关闭时不扫描")
    void shouldRequireBothFeatureFlags()
    {
        DriveProperties properties = new DriveProperties();
        DriveOrganizationReconcileService service = mock(DriveOrganizationReconcileService.class);
        DriveOrganizationSyncTask task = new DriveOrganizationSyncTask(properties, service);

        task.synchronize();
        properties.setEnabled(true);
        task.synchronize();

        verify(service, never()).reconcileAll("system");
        properties.setOrganizationSyncEnabled(true);
        task.synchronize();
        verify(service).reconcileAll("system");
    }
}
