package com.erp.common.log.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import com.erp.common.log.sanitize.SensitiveLogSanitizer;

/**
 * Registers the log sanitizer for every service that depends on erp-common-log.
 * Business applications scan only their own package, so this bean must not rely
 * on component scanning.
 */
@AutoConfiguration
public class LogSanitizerAutoConfiguration
{
    @Bean
    @ConditionalOnMissingBean
    public SensitiveLogSanitizer sensitiveLogSanitizer()
    {
        return new SensitiveLogSanitizer();
    }
}
