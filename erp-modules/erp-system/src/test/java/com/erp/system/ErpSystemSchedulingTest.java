package com.erp.system;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.EnableScheduling;

@DisplayName("System模块调度入口")
class ErpSystemSchedulingTest
{
    @Test
    @DisplayName("应用入口必须启用Spring调度以运行续签扫描")
    void shouldEnableSchedulingAtApplicationBoundary()
    {
        assertThat(ErpSystemApplication.class)
                .hasAnnotation(EnableScheduling.class);
    }
}
