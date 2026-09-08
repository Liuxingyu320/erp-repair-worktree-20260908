package com.erp.oa;

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

@DisplayName("OA 上传传输限制")
class OaMultipartPropertiesTest
{
    private static final long MEBIBYTE = 1024L * 1024L;

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfiguration.class);

    @Test
    @DisplayName("默认配置允许10MiB签约Excel并为multipart边界预留空间")
    void shouldMatchOnboardExcelBusinessLimit()
    {
        contextRunner
                .withInitializer(context -> loadYaml(
                        context.getEnvironment().getPropertySources(), "bootstrap.yml"))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    MultipartProperties multipart = context.getBean(MultipartProperties.class);

                    assertThat(multipart.getMaxFileSize().toBytes()).isEqualTo(10L * MEBIBYTE);
                    assertThat(multipart.getMaxRequestSize().toBytes()).isEqualTo(11L * MEBIBYTE);
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
        catch (IOException exception)
        {
            throw new IllegalStateException("无法加载OA配置：" + resource, exception);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(MultipartProperties.class)
    static class TestConfiguration
    {
    }
}
