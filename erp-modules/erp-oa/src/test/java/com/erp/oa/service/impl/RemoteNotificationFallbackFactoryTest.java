package com.erp.oa.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.erp.common.core.constant.SecurityConstants;
import com.erp.common.core.domain.R;
import com.erp.system.api.RemoteNotificationService;
import com.erp.system.api.domain.UserNotificationResult;
import com.erp.system.api.factory.RemoteNotificationFallbackFactory;
import feign.FeignException;
import feign.Request;
import feign.RequestTemplate;
import feign.Response;

class RemoteNotificationFallbackFactoryTest
{
    @Test
    void fallbackPreservesHttpClassificationWithoutExposingRequestPayload()
    {
        RemoteNotificationFallbackFactory factory = new RemoteNotificationFallbackFactory();

        R<UserNotificationResult> forbidden = publish(factory, 403);
        R<UserNotificationResult> rateLimited = publish(factory, 429);

        assertThat(forbidden.getCode()).isEqualTo(403);
        assertThat(rateLimited.getCode()).isEqualTo(429);
        assertThat(forbidden.getMsg()).doesNotContain("合同正文", "packageId");
    }

    private static R<UserNotificationResult> publish(RemoteNotificationFallbackFactory factory, int status)
    {
        RemoteNotificationService fallback = factory.create(httpFailure(status));
        com.erp.system.api.domain.UserNotificationCommand command =
                new com.erp.system.api.domain.UserNotificationCommand();
        command.setBody("合同正文");
        command.setRouteParams("{\"packageId\":90}");
        return fallback.publish(command, SecurityConstants.INNER);
    }

    private static FeignException httpFailure(int status)
    {
        Request request = Request.create(Request.HttpMethod.POST, "http://erp-system/user-notification",
                Map.of(), null, StandardCharsets.UTF_8, new RequestTemplate());
        Response response = Response.builder().status(status).reason("test")
                .request(request).build();
        return FeignException.errorStatus("publish", response);
    }
}
