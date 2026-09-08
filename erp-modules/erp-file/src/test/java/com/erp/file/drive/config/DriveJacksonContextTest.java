package com.erp.file.drive.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.erp.file.drive.filter.DriveFeatureFilter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

@DisplayName("云盘 Jackson 3 启动接线")
class DriveJacksonContextTest
{
    @Test
    @DisplayName("Boot 4 默认 JSON 映射器可以直接装配功能过滤器")
    void shouldWireDriveFilterWithBootJacksonMapper()
    {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class))
                .withUserConfiguration(Context.class, DriveFeatureFilter.class)
                .run(context -> {
                    assertThat(context).hasNotFailed()
                            .hasSingleBean(ObjectMapper.class)
                            .hasSingleBean(DriveFeatureFilter.class);
                    assertThat(context)
                            .doesNotHaveBean(com.fasterxml.jackson.databind.ObjectMapper.class);
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(DriveProperties.class)
    static class Context
    {
    }
}
