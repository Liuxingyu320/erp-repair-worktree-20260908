package com.erp.file.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import com.erp.file.service.MinioSysFileServiceImpl;
import io.minio.MinioClient;

@DisplayName("MinIO文件存储配置")
class MinioConfigTest
{
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfiguration.class, MinioConfig.class, MinioSysFileServiceImpl.class);

    @Test
    @DisplayName("本地禁用MinIO时不创建MinIO客户端和存储服务")
    void shouldNotCreateMinioBeansWhenDisabled()
    {
        contextRunner
                .withPropertyValues(
                        "minio.enabled=false",
                        "minio.url=http://127.0.0.1:9000",
                        "minio.bucketName=erp")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(MinioClient.class);
                    assertThat(context).doesNotHaveBean(MinioSysFileServiceImpl.class);
                });
    }

    @Test
    @DisplayName("启用MinIO并提供凭据时创建MinIO客户端和存储服务")
    void shouldCreateMinioBeansWhenEnabled()
    {
        contextRunner
                .withPropertyValues(
                        "minio.enabled=true",
                        "minio.url=http://127.0.0.1:9000",
                        "minio.accessKey=minioadmin",
                        "minio.secretKey=minioadmin",
                        "minio.bucketName=erp")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(MinioClient.class);
                    assertThat(context).hasSingleBean(MinioSysFileServiceImpl.class);
                });
    }

    @Configuration
    @EnableConfigurationProperties
    static class TestConfiguration
    {
    }
}
