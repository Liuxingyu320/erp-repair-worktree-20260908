package com.erp.system.service.push;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import com.erp.system.api.domain.UserNotificationCommand;
import com.erp.system.config.PushNotificationProperties;
import com.erp.system.domain.SysUserDeviceToken;
import com.erp.system.service.push.PushDeliveryClient.DeliveryResult;
import com.erp.system.service.push.PushDeliveryClient.DeliveryStatus;

class FirebasePushDeliveryClientTest
{
    @Test
    void disabledOrIncompleteConfigurationMustNotInitializeFirebase()
    {
        PushNotificationProperties properties = properties();
        AtomicInteger calls = new AtomicInteger();
        FirebasePushDeliveryClient.FirebaseGateway gateway = envelope -> {
            calls.incrementAndGet();
            return "unexpected";
        };
        FirebasePushDeliveryClient client = new FirebasePushDeliveryClient(properties, gateway);

        DeliveryResult disabled = client.deliver(androidToken(), command());

        assertThat(disabled.getStatus()).isEqualTo(DeliveryStatus.DISABLED);
        assertThat(calls).hasValue(0);

        properties.getFcm().setEnabled(true);
        DeliveryResult incomplete = client.deliver(androidToken(), command());

        assertThat(incomplete.getStatus()).isEqualTo(DeliveryStatus.DISABLED);
        assertThat(calls).hasValue(0);
    }

    @Test
    void enabledClientSendsOnlyGenericNotificationAndWhitelistedRouteData() throws Exception
    {
        PushNotificationProperties properties = properties();
        properties.getFcm().setEnabled(true);
        properties.getFcm().setProjectId("erp-production");
        AtomicReference<FirebasePushDeliveryClient.FirebaseEnvelope> sent = new AtomicReference<>();
        FirebasePushDeliveryClient client = new FirebasePushDeliveryClient(properties, envelope -> {
            sent.set(envelope);
            return "projects/erp-production/messages/abc";
        });

        DeliveryResult result = client.deliver(androidToken(), command());

        assertThat(result.getStatus()).isEqualTo(DeliveryStatus.DELIVERED);
        assertThat(result.getProviderCode()).isEqualTo("FCM_ACCEPTED");
        assertThat(sent.get().getToken()).isEqualTo("android-token-123456");
        assertThat(sent.get().getTitle()).isEqualTo("合同待处理");
        assertThat(sent.get().getBody()).isEqualTo("请进入系统处理合同任务");
        assertThat(sent.get().getData()).containsExactlyInAnyOrderEntriesOf(Map.of(
                "routeType", "OA_SIGN_HR_TASK",
                "taskId", "9",
                "businessKey", "SIGN_WAITING_HR:9:3"));
        assertThat(sent.get().getData().toString())
                .doesNotContain("identityNumber", "salary", "contractBody", "hash");
    }

    @Test
    void providerCodesAreClassifiedWithoutRetryingInvalidTokens()
    {
        assertThat(FirebasePushDeliveryClient.classifyProviderCode("UNREGISTERED"))
                .isEqualTo(DeliveryStatus.INVALID_TOKEN);
        assertThat(FirebasePushDeliveryClient.classifyProviderCode("QUOTA_EXCEEDED"))
                .isEqualTo(DeliveryStatus.RETRYABLE_FAILURE);
        assertThat(FirebasePushDeliveryClient.classifyProviderCode("UNAVAILABLE"))
                .isEqualTo(DeliveryStatus.RETRYABLE_FAILURE);
        assertThat(FirebasePushDeliveryClient.classifyProviderCode("INTERNAL"))
                .isEqualTo(DeliveryStatus.RETRYABLE_FAILURE);
        assertThat(FirebasePushDeliveryClient.classifyProviderCode("SENDER_ID_MISMATCH"))
                .isEqualTo(DeliveryStatus.PERMANENT_FAILURE);
        assertThat(FirebasePushDeliveryClient.classifyProviderCode("INVALID_ARGUMENT"))
                .isEqualTo(DeliveryStatus.PERMANENT_FAILURE);
        assertThat(FirebasePushDeliveryClient.classifyProviderCode("PERMISSION_DENIED"))
                .isEqualTo(DeliveryStatus.PERMANENT_FAILURE);
        assertThat(FirebasePushDeliveryClient.classifyProviderCode("UNAUTHENTICATED"))
                .isEqualTo(DeliveryStatus.PERMANENT_FAILURE);
        assertThat(FirebasePushDeliveryClient.classifyProviderCode("DEADLINE_EXCEEDED"))
                .isEqualTo(DeliveryStatus.RETRYABLE_FAILURE);
    }

    @Test
    void gatewayFailureIsReturnedAsTypedResultWithoutLeakingToken()
    {
        PushNotificationProperties properties = properties();
        properties.getFcm().setEnabled(true);
        properties.getFcm().setProjectId("erp-production");
        FirebasePushDeliveryClient client = new FirebasePushDeliveryClient(properties, envelope -> {
            throw PushDeliveryClient.DeliveryException.invalidToken("UNREGISTERED");
        });

        DeliveryResult result = client.deliver(androidToken(), command());

        assertThat(result.getStatus()).isEqualTo(DeliveryStatus.INVALID_TOKEN);
        assertThat(result.getProviderCode()).isEqualTo("UNREGISTERED");
        assertThat(result.toString()).doesNotContain("android-token-123456");
    }

    @Test
    void routeIdsMustBePositiveIntegersAndCannotBeCoercedFromDecimals()
    {
        PushNotificationProperties properties = properties();
        properties.getFcm().setEnabled(true);
        properties.getFcm().setProjectId("erp-production");
        AtomicInteger calls = new AtomicInteger();
        FirebasePushDeliveryClient client = new FirebasePushDeliveryClient(properties, envelope -> {
            calls.incrementAndGet();
            return "unexpected";
        });
        UserNotificationCommand command = command();
        command.setRouteParams("{\"taskId\":9.5}");

        DeliveryResult result = client.deliver(androidToken(), command);

        assertThat(result.getStatus()).isEqualTo(DeliveryStatus.PERMANENT_FAILURE);
        assertThat(calls).hasValue(0);
    }

    @Test
    void blankBusinessKeyMustNeverReachFirebase()
    {
        PushNotificationProperties properties = properties();
        properties.getFcm().setEnabled(true);
        properties.getFcm().setProjectId("erp-production");
        AtomicInteger calls = new AtomicInteger();
        FirebasePushDeliveryClient client = new FirebasePushDeliveryClient(properties, envelope -> {
            calls.incrementAndGet();
            return "unexpected";
        });
        UserNotificationCommand command = command();
        command.setBusinessKey(" ");

        DeliveryResult result = client.deliver(androidToken(), command);

        assertThat(result.getStatus()).isEqualTo(DeliveryStatus.PERMANENT_FAILURE);
        assertThat(calls).hasValue(0);
    }

    @Test
    void officialSdkVersionsAndExternalOnlyCredentialConfigurationArePinned() throws Exception
    {
        String pom = readRepoFile("erp-modules/erp-system/pom.xml");
        String bootstrap = readRepoFile("erp-modules/erp-system/src/main/resources/bootstrap.yml");

        assertThat(pom)
                .contains("<artifactId>firebase-admin</artifactId>", "<version>9.9.0</version>")
                .contains("<artifactId>pushy</artifactId>", "<version>0.15.6</version>");
        assertThat(bootstrap)
                .contains("enabled: ${PUSH_FCM_ENABLED:false}")
                .contains("project-id: ${FIREBASE_PROJECT_ID:}")
                .contains("enabled: ${PUSH_APNS_ENABLED:false}")
                .contains("private-key-path: ${APNS_PRIVATE_KEY_PATH:}")
                .contains("environment: ${APNS_ENVIRONMENT:production}")
                .doesNotContain("BEGIN PRIVATE KEY", "private_key_id", "client_email");
    }

    private static PushNotificationProperties properties()
    {
        return new PushNotificationProperties();
    }

    private static SysUserDeviceToken androidToken()
    {
        SysUserDeviceToken token = new SysUserDeviceToken();
        token.setUserId(42L);
        token.setPlatform("ANDROID");
        token.setToken("android-token-123456");
        token.setTokenHash("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        return token;
    }

    private static UserNotificationCommand command()
    {
        UserNotificationCommand command = new UserNotificationCommand();
        command.setChannel("MOBILE_PUSH");
        command.setRecipientUserId(42L);
        command.setBusinessKey("SIGN_WAITING_HR:9:3");
        command.setTitle("合同待处理");
        command.setBody("请进入系统处理合同任务");
        command.setRouteType("OA_SIGN_HR_TASK");
        command.setRouteParams("{\"taskId\":9,\"identityNumber\":\"must-not-leak\",\"hash\":\"must-not-leak\"}");
        return command;
    }

    private static String readRepoFile(String relativePath) throws Exception
    {
        Path base = Paths.get(System.getProperty("user.dir"));
        Path path = base.resolve(relativePath);
        if (!Files.exists(path))
        {
            path = base.resolve("../..").resolve(relativePath).normalize();
        }
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
