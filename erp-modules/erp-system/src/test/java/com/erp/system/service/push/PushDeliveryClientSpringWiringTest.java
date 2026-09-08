package com.erp.system.service.push;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import com.erp.system.config.PushNotificationProperties;

class PushDeliveryClientSpringWiringTest
{
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(PushClientsConfiguration.class);

    @Test
    void springCreatesBothProductionClientsWithoutDefaultConstructors()
    {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(PushNotificationProperties.class);
            assertThat(context).hasSingleBean(ApnsPushDeliveryClient.class);
            assertThat(context).hasSingleBean(FirebasePushDeliveryClient.class);
        });
    }

    @Configuration(proxyBeanMethods = false)
    @Import({ PushNotificationProperties.class, ApnsPushDeliveryClient.class,
            FirebasePushDeliveryClient.class })
    static class PushClientsConfiguration
    {
    }
}
