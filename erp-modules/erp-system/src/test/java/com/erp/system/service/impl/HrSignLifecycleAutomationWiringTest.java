package com.erp.system.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.erp.oa.api.RemoteSignTaskService;
import com.erp.system.mapper.SysHrLifecycleActionMapper;

class HrSignLifecycleAutomationWiringTest
{
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(LifecycleAutomationConfiguration.class);

    @Test
    void automationBeansAreAbsentWhenPropertyIsMissing()
    {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(HrSignEventDispatcher.class);
            assertThat(context).doesNotHaveBean(HrSignEventCompensationScanner.class);
        });
    }

    @Test
    void automationBeansAreAbsentWhenPropertyIsFalse()
    {
        contextRunner
                .withPropertyValues("hr.sign.lifecycle-automation.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(HrSignEventDispatcher.class);
                    assertThat(context).doesNotHaveBean(HrSignEventCompensationScanner.class);
                });
    }

    @Test
    void automationBeansAreLoadedOnlyWhenPropertyIsTrue()
    {
        contextRunner
                .withPropertyValues("hr.sign.lifecycle-automation.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(HrSignEventDispatcher.class);
                    assertThat(context).hasSingleBean(HrSignEventCompensationScanner.class);
                });
    }

    @Configuration(proxyBeanMethods = false)
    @Import({ HrSignEventDispatcher.class,
            HrSignEventCompensationScanner.class })
    static class LifecycleAutomationConfiguration
    {
        @Bean
        HrSignEventOutboxService outboxService()
        {
            return mock(HrSignEventOutboxService.class);
        }

        @Bean
        RemoteSignTaskService remoteSignTaskService()
        {
            return mock(RemoteSignTaskService.class);
        }

        @Bean
        ObjectMapper objectMapper()
        {
            return new ObjectMapper();
        }

        @Bean
        SysHrLifecycleActionMapper lifecycleActionMapper()
        {
            return mock(SysHrLifecycleActionMapper.class);
        }

        @Bean
        HrSignEventCompensationService compensationService()
        {
            return mock(HrSignEventCompensationService.class);
        }
    }
}
