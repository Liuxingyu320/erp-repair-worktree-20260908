package com.erp.system.service.push;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import com.erp.system.config.PushNotificationProperties;

class ApnsPushDeliveryClientWiringTest
{
    @Test
    void createsClientWithPropertiesConstructor()
    {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext())
        {
            context.registerBean(PushNotificationProperties.class);
            context.register(ApnsPushDeliveryClient.class);
            context.register(FirebasePushDeliveryClient.class);
            context.refresh();

            assertThat(context.getBean(ApnsPushDeliveryClient.class)).isNotNull();
            assertThat(context.getBean(FirebasePushDeliveryClient.class)).isNotNull();
        }
    }
}
