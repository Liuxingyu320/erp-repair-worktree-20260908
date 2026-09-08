package com.erp.system.service.push;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import com.erp.system.api.domain.UserNotificationCommand;
import com.erp.system.config.PushNotificationProperties;
import com.erp.system.domain.SysUserDeviceToken;
import com.erp.system.service.push.PushDeliveryClient.DeliveryResult;
import com.erp.system.service.push.PushDeliveryClient.DeliveryStatus;

class ApnsPushDeliveryClientTest
{
    @Test
    void disabledOrIncompleteConfigurationMustNotReadPrivateKeyOrOpenConnection()
    {
        PushNotificationProperties properties = properties();
        AtomicInteger calls = new AtomicInteger();
        ApnsPushDeliveryClient.ApnsGateway gateway = envelope -> {
            calls.incrementAndGet();
            return ApnsPushDeliveryClient.ApnsGatewayResponse.accepted("unexpected");
        };
        ApnsPushDeliveryClient client = new ApnsPushDeliveryClient(properties, gateway);

        assertThat(client.deliver(iosToken(), command()).getStatus()).isEqualTo(DeliveryStatus.DISABLED);
        properties.getApns().setEnabled(true);
        assertThat(client.deliver(iosToken(), command()).getStatus()).isEqualTo(DeliveryStatus.DISABLED);
        assertThat(calls).hasValue(0);
    }

    @Test
    void environmentSelectsExactAppleGateway()
    {
        PushNotificationProperties properties = configuredProperties();
        properties.getApns().setEnvironment("sandbox");
        assertThat(ApnsPushDeliveryClient.resolveApnsHost(properties.getApns()))
                .isEqualTo("api.sandbox.push.apple.com");

        properties.getApns().setEnvironment("production");
        assertThat(ApnsPushDeliveryClient.resolveApnsHost(properties.getApns()))
                .isEqualTo("api.push.apple.com");
    }

    @Test
    void enabledClientUsesBundleTopicAndPayloadContainsOnlyWhitelistedRoute() throws Exception
    {
        PushNotificationProperties properties = configuredProperties();
        AtomicReference<ApnsPushDeliveryClient.ApnsEnvelope> sent = new AtomicReference<>();
        ApnsPushDeliveryClient client = new ApnsPushDeliveryClient(properties, envelope -> {
            sent.set(envelope);
            return ApnsPushDeliveryClient.ApnsGatewayResponse.accepted("apns-id-1");
        });

        DeliveryResult result = client.deliver(iosToken(), command());

        assertThat(result.getStatus()).isEqualTo(DeliveryStatus.DELIVERED);
        assertThat(result.getProviderCode()).isEqualTo("APNS_ACCEPTED");
        assertThat(sent.get().getToken()).isEqualTo("ios-token-654321");
        assertThat(sent.get().getTopic()).isEqualTo("com.erp.mobile");
        assertThat(sent.get().getPayload())
                .contains("合同待签署", "请进入系统签署合同", "OA_SIGN_PACKAGE_SIGN",
                        "packageId", "55", "businessKey", "SIGN_SENT:55:2")
                .doesNotContain("identityNumber", "salary", "contractBody", "hash", "must-not-leak");
    }

    @Test
    void appleRejectionsAreClassifiedAccordingToRetryPolicy()
    {
        assertThat(ApnsPushDeliveryClient.classifyRejection("BadDeviceToken"))
                .isEqualTo(DeliveryStatus.INVALID_TOKEN);
        assertThat(ApnsPushDeliveryClient.classifyRejection("Unregistered"))
                .isEqualTo(DeliveryStatus.INVALID_TOKEN);
        assertThat(ApnsPushDeliveryClient.classifyRejection("TooManyRequests"))
                .isEqualTo(DeliveryStatus.RETRYABLE_FAILURE);
        assertThat(ApnsPushDeliveryClient.classifyRejection("InternalServerError"))
                .isEqualTo(DeliveryStatus.RETRYABLE_FAILURE);
        assertThat(ApnsPushDeliveryClient.classifyRejection("ServiceUnavailable"))
                .isEqualTo(DeliveryStatus.RETRYABLE_FAILURE);
        assertThat(ApnsPushDeliveryClient.classifyRejection("TopicDisallowed"))
                .isEqualTo(DeliveryStatus.PERMANENT_FAILURE);
    }

    @Test
    void invalidTokenResponseNeverIncludesRawToken()
    {
        PushNotificationProperties properties = configuredProperties();
        ApnsPushDeliveryClient client = new ApnsPushDeliveryClient(properties,
                envelope -> ApnsPushDeliveryClient.ApnsGatewayResponse.rejected("BadDeviceToken"));

        DeliveryResult result = client.deliver(iosToken(), command());

        assertThat(result.getStatus()).isEqualTo(DeliveryStatus.INVALID_TOKEN);
        assertThat(result.getProviderCode()).isEqualTo("BadDeviceToken");
        assertThat(result.toString()).doesNotContain("ios-token-654321");
    }

    @Test
    void appleTokenInvalidationTimestampMustDisableTokenEvenForUnknownReason()
    {
        PushNotificationProperties properties = configuredProperties();
        ApnsPushDeliveryClient client = new ApnsPushDeliveryClient(properties,
                envelope -> ApnsPushDeliveryClient.ApnsGatewayResponse.rejected("UnknownReason", true));

        DeliveryResult result = client.deliver(iosToken(), command());

        assertThat(result.getStatus()).isEqualTo(DeliveryStatus.INVALID_TOKEN);
    }

    @Test
    void blankBusinessKeyMustNeverReachApns()
    {
        PushNotificationProperties properties = configuredProperties();
        AtomicInteger calls = new AtomicInteger();
        ApnsPushDeliveryClient client = new ApnsPushDeliveryClient(properties, envelope -> {
            calls.incrementAndGet();
            return ApnsPushDeliveryClient.ApnsGatewayResponse.accepted("unexpected");
        });
        UserNotificationCommand command = command();
        command.setBusinessKey(" ");

        DeliveryResult result = client.deliver(iosToken(), command);

        assertThat(result.getStatus()).isEqualTo(DeliveryStatus.PERMANENT_FAILURE);
        assertThat(calls).hasValue(0);
    }

    private static PushNotificationProperties properties()
    {
        return new PushNotificationProperties();
    }

    private static PushNotificationProperties configuredProperties()
    {
        PushNotificationProperties properties = properties();
        properties.getApns().setEnabled(true);
        properties.getApns().setTeamId("TEAM123456");
        properties.getApns().setKeyId("KEY1234567");
        properties.getApns().setBundleId("com.erp.mobile");
        properties.getApns().setPrivateKeyPath("/run/secrets/apns-key.p8");
        properties.getApns().setEnvironment("production");
        return properties;
    }

    private static SysUserDeviceToken iosToken()
    {
        SysUserDeviceToken token = new SysUserDeviceToken();
        token.setUserId(88L);
        token.setPlatform("IOS");
        token.setToken("ios-token-654321");
        token.setTokenHash("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");
        return token;
    }

    private static UserNotificationCommand command()
    {
        UserNotificationCommand command = new UserNotificationCommand();
        command.setChannel("MOBILE_PUSH");
        command.setRecipientUserId(88L);
        command.setBusinessKey("SIGN_SENT:55:2");
        command.setTitle("合同待签署");
        command.setBody("请进入系统签署合同");
        command.setRouteType("OA_SIGN_PACKAGE_SIGN");
        command.setRouteParams("{\"packageId\":55,\"identityNumber\":\"must-not-leak\",\"hash\":\"must-not-leak\"}");
        return command;
    }
}
