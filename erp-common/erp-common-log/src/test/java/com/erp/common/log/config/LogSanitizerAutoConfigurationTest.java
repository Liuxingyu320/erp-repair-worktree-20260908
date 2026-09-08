package com.erp.common.log.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import com.erp.common.log.sanitize.SensitiveLogSanitizer;

class LogSanitizerAutoConfigurationTest
{
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(LogSanitizerAutoConfiguration.class));

    @Test
    void registersSanitizerWithoutBusinessPackageScanning()
    {
        contextRunner.run(context -> assertThat(context)
                .hasSingleBean(SensitiveLogSanitizer.class));
    }

    @Test
    void preservesApplicationProvidedSanitizer()
    {
        SensitiveLogSanitizer custom = new SensitiveLogSanitizer();
        contextRunner.withBean(SensitiveLogSanitizer.class, () -> custom)
                .run(context -> assertThat(context.getBean(SensitiveLogSanitizer.class))
                        .isSameAs(custom));
    }
}
