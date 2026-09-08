package com.erp.file.drive.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.servlet.autoconfigure.MultipartProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

@DisplayName("云盘安全默认配置")
class DrivePropertiesTest
{
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfiguration.class);

    @Test
    @DisplayName("无外部配置时云盘关闭并使用私有存储默认值")
    void shouldBindSecureDefaults()
    {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            DriveProperties properties = context.getBean(DriveProperties.class);

            assertThat(properties.isEnabled()).isFalse();
            assertThat(properties.getStorageType()).isEqualTo("local");
            assertThat(properties.getLocalPath()).isEqualTo("./uploadPath/private/drive");
            assertThat(properties.getMinioBucket()).isEqualTo("erp-drive-private");
            assertThat(properties.getMaxFileSize()).isEqualTo(104857600L);
            assertThat(properties.getPersonalQuota()).isEqualTo(2147483648L);
            assertThat(properties.getDepartmentQuota()).isEqualTo(21474836480L);
            assertThat(properties.getCompanyQuota()).isEqualTo(107374182400L);
            assertThat(properties.isQuotaPolicyEnabled()).isFalse();
            assertThat(properties.isOrganizationSyncEnabled()).isFalse();
            assertThat(properties.getOrganizationSyncCron()).isEqualTo("0 */10 * * * *");
            assertThat(properties.isCapacityReservationEnabled()).isFalse();
            assertThat(properties.getUploadReservationTimeoutMinutes()).isEqualTo(120);
            assertThat(properties.getUploadReservationCleanupCron())
                    .isEqualTo("0 */5 * * * *");
            assertThat(properties.getUploadReservationCleanupBatchSize()).isEqualTo(100);
            assertThat(properties.getTrashRetentionDays()).isEqualTo(30);
            assertThat(properties.getCleanupCron()).isEqualTo("0 30 2 * * *");
            assertThat(properties.getCleanupZone()).isEqualTo("Asia/Shanghai");
        });
    }

    @Test
    @DisplayName("开发与本地配置均绑定 100MiB 文件和 110MiB 请求上限")
    void shouldBindMultipartCeilingsFromYaml()
    {
        assertYamlCeilings("application-dev.yml");
        assertYamlCeilings("application-local.yml");
    }

    private void assertYamlCeilings(String resource)
    {
        contextRunner
                .withInitializer(context -> loadYaml(context.getEnvironment().getPropertySources(), resource))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    MultipartProperties multipart = context.getBean(MultipartProperties.class);
                    DriveProperties drive = context.getBean(DriveProperties.class);

                    assertThat(multipart.getMaxFileSize().toBytes()).isEqualTo(104857600L);
                    assertThat(multipart.getMaxRequestSize().toBytes()).isEqualTo(115343360L);
                    assertThat(drive.getMaxFileSize()).isEqualTo(104857600L);
                });
    }

    private static void loadYaml(
            org.springframework.core.env.MutablePropertySources target, String resource)
    {
        try
        {
            List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                    .load(resource, new ClassPathResource(resource));
            for (PropertySource<?> source : sources)
            {
                target.addLast(source);
            }
        }
        catch (IOException ex)
        {
            throw new IllegalStateException("Cannot load " + resource, ex);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties({DriveProperties.class, MultipartProperties.class})
    static class TestConfiguration
    {
    }
}
