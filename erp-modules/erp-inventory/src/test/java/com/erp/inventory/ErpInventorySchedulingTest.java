package com.erp.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.scheduling.annotation.EnableScheduling;

@DisplayName("Inventory模块审批发起恢复能力")
class ErpInventorySchedulingTest
{
    @Test
    @DisplayName("应用入口必须启用Spring调度以重试审批发起任务")
    void shouldEnableSchedulingAtApplicationBoundary()
    {
        assertThat(ErpInventoryApplication.class)
                .hasAnnotation(EnableScheduling.class);
    }

    @Test
    @DisplayName("本地服务发现必须包含统一审批中心")
    void shouldDiscoverApprovalServiceInLocalProfile() throws IOException
    {
        ClassPathResource resource = new ClassPathResource("bootstrap.yml");
        String bootstrap = resource.getContentAsString(StandardCharsets.UTF_8);

        assertThat(bootstrap)
                .contains("erp-approval:")
                .contains("uri: http://127.0.0.1:9206");
    }
}
