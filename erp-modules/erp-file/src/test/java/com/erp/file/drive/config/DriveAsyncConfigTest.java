package com.erp.file.drive.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.concurrent.ThreadPoolExecutor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@DisplayName("云盘清理异步执行器")
class DriveAsyncConfigTest
{
    @Test
    @DisplayName("使用有界专用线程池且拒绝时不在调用线程执行")
    void shouldConfigureBoundedPurgeExecutor() throws Exception
    {
        assertThat(DriveAsyncConfig.class.getAnnotation(EnableAsync.class)).isNotNull();
        Method factory = DriveAsyncConfig.class.getMethod("drivePurgeExecutor");
        assertThat(factory.getAnnotation(Bean.class).name())
                .containsExactly("drivePurgeExecutor");
        ThreadPoolTaskExecutor executor = new DriveAsyncConfig().drivePurgeExecutor();
        executor.initialize();
        try
        {
            assertThat(executor.getCorePoolSize()).isEqualTo(1);
            assertThat(executor.getMaxPoolSize()).isEqualTo(2);
            assertThat(executor.getThreadNamePrefix()).isEqualTo("drive-purge-");
            assertThat(executor.getThreadPoolExecutor().getQueue().remainingCapacity())
                    .isEqualTo(100);
            assertThat(executor.getThreadPoolExecutor().getRejectedExecutionHandler())
                    .isInstanceOf(ThreadPoolExecutor.AbortPolicy.class);
        }
        finally
        {
            executor.shutdown();
        }
    }
}
