package com.erp.oa.attendance.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.erp.oa.attendance.support.AmapAttendanceAddressResolver;
import com.erp.oa.attendance.support.AttendanceAddressResolver;
import com.erp.oa.attendance.support.AttendanceCoordinateTransformer;
import com.erp.oa.attendance.support.UnavailableAttendanceAddressResolver;
import com.fasterxml.jackson.databind.ObjectMapper;

class AttendanceAddressResolverConfigurationTest
{
    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(
                    TestConfiguration.class,
                    AttendanceAddressResolverConfiguration.class);

    @Test
    void defaultsToTheFailClosedUnavailableResolver()
    {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed()
                    .hasSingleBean(AttendanceAddressResolver.class);
            assertThat(context.getBean(AttendanceAddressResolver.class))
                    .isInstanceOf(UnavailableAttendanceAddressResolver.class);
        });
    }

    @Test
    void createsAmapOnlyWhenProviderAndPrivateKeyAreConfigured()
    {
        contextRunner.withPropertyValues(
                "oa.attendance-v2.address.provider=AMAP",
                "oa.attendance-v2.address.amap-web-service-key=unit-test-only")
                .run(context -> {
                    assertThat(context).hasNotFailed()
                            .hasSingleBean(AttendanceAddressResolver.class);
                    assertThat(context.getBean(
                            AttendanceAddressResolver.class))
                            .isInstanceOf(
                                    AmapAttendanceAddressResolver.class);
                });
    }

    @Test
    void refusesToStartAmapModeWithoutThePrivateKey()
    {
        contextRunner.withPropertyValues(
                "oa.attendance-v2.address.provider=AMAP")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseMessage(
                                    "AMAP_ATTENDANCE_KEY_REQUIRED");
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(AttendanceV2Properties.class)
    static class TestConfiguration
    {
        @Bean
        AttendanceCoordinateTransformer coordinates()
        {
            return new AttendanceCoordinateTransformer();
        }

        @Bean
        ObjectMapper objectMapper()
        {
            return new ObjectMapper();
        }
    }
}
