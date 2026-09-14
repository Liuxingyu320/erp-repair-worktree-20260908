package com.erp.system.service.push;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.erp.common.core.exception.ServiceException;
import com.erp.system.api.domain.UserNotificationCommand;
import com.erp.system.config.PushNotificationProperties;
import com.erp.system.domain.SysUserDeviceToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.AndroidNotification;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.Notification;

/**
 * Android FCM推送。凭证只通过Application Default Credentials按需加载。
 */
@Component
public class FirebasePushDeliveryClient implements PushDeliveryClient
{
    private static final String FIREBASE_APP_NAME = "erp-system-fcm";

    private final PushNotificationProperties properties;

    private final FirebaseGateway injectedGateway;

    private volatile FirebaseGateway runtimeGateway;

    @Autowired
    public FirebasePushDeliveryClient(PushNotificationProperties properties)
    {
        this(properties, null);
    }

    FirebasePushDeliveryClient(PushNotificationProperties properties, FirebaseGateway gateway)
    {
        this.properties = properties;
        this.injectedGateway = gateway;
    }

    @Override
    public String platform()
    {
        return "ANDROID";
    }

    @Override
    public DeliveryResult deliver(SysUserDeviceToken deviceToken, UserNotificationCommand command)
    {
        if (!properties.getFcm().isComplete())
        {
            return DeliveryResult.disabled("FCM_DISABLED");
        }
        if (deviceToken == null || !"ANDROID".equalsIgnoreCase(deviceToken.getPlatform())
                || blank(deviceToken.getToken()))
        {
            return DeliveryResult.permanentFailure("INVALID_FCM_DEVICE");
        }

        final Map<String, String> routeData;
        try
        {
            routeData = PushDeliveryClient.whitelistedRouteData(command);
        }
        catch (ServiceException exception)
        {
            return DeliveryResult.permanentFailure("INVALID_ROUTE");
        }

        FirebaseEnvelope envelope = new FirebaseEnvelope(deviceToken.getToken(), command.getTitle(),
                command.getBody(), routeData);
        try
        {
            gateway().send(envelope);
            return DeliveryResult.delivered("FCM_ACCEPTED");
        }
        catch (DeliveryException exception)
        {
            return fromException(exception);
        }
        catch (RuntimeException exception)
        {
            return DeliveryResult.retryableFailure("FCM_RUNTIME_FAILURE");
        }
    }

    static DeliveryStatus classifyProviderCode(String providerCode)
    {
        if (providerCode == null)
        {
            return DeliveryStatus.RETRYABLE_FAILURE;
        }
        return switch (providerCode)
        {
            case "UNREGISTERED" -> DeliveryStatus.INVALID_TOKEN;
            case "QUOTA_EXCEEDED", "RESOURCE_EXHAUSTED", "UNAVAILABLE", "INTERNAL",
                    "DEADLINE_EXCEEDED" -> DeliveryStatus.RETRYABLE_FAILURE;
            case "INVALID_ARGUMENT", "FAILED_PRECONDITION", "SENDER_ID_MISMATCH",
                    "THIRD_PARTY_AUTH_ERROR", "PERMISSION_DENIED", "UNAUTHENTICATED" ->
                    DeliveryStatus.PERMANENT_FAILURE;
            default -> DeliveryStatus.RETRYABLE_FAILURE;
        };
    }

    private FirebaseGateway gateway() throws DeliveryException
    {
        if (injectedGateway != null)
        {
            return injectedGateway;
        }
        FirebaseGateway current = runtimeGateway;
        if (current == null)
        {
            synchronized (this)
            {
                current = runtimeGateway;
                if (current == null)
                {
                    current = createRuntimeGateway();
                    runtimeGateway = current;
                }
            }
        }
        return current;
    }

    private FirebaseGateway createRuntimeGateway() throws DeliveryException
    {
        try
        {
            FirebaseApp app;
            try
            {
                app = FirebaseApp.getInstance(FIREBASE_APP_NAME);
            }
            catch (IllegalStateException missing)
            {
                FirebaseOptions options = FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials.getApplicationDefault())
                        .setProjectId(properties.getFcm().getProjectId().trim())
                        .build();
                try
                {
                    app = FirebaseApp.initializeApp(options, FIREBASE_APP_NAME);
                }
                catch (IllegalStateException concurrentInitialization)
                {
                    app = FirebaseApp.getInstance(FIREBASE_APP_NAME);
                }
            }
            FirebaseMessaging messaging = FirebaseMessaging.getInstance(app);
            return envelope -> sendWithFirebase(messaging, envelope);
        }
        catch (IOException | RuntimeException exception)
        {
            throw DeliveryException.permanent("FCM_CREDENTIALS_INVALID", exception);
        }
    }

    private static String sendWithFirebase(FirebaseMessaging messaging, FirebaseEnvelope envelope)
            throws DeliveryException
    {
        try
        {
            return messaging.send(buildMessage(envelope));
        }
        catch (FirebaseMessagingException exception)
        {
            MessagingErrorCode errorCode = exception.getMessagingErrorCode();
            String providerCode = errorCode != null
                    ? errorCode.name()
                    : exception.getErrorCode() == null ? "FCM_UNKNOWN" : exception.getErrorCode().name();
            DeliveryStatus status = classifyProviderCode(providerCode);
            if (status == DeliveryStatus.INVALID_TOKEN)
            {
                throw DeliveryException.invalidToken(providerCode);
            }
            if (status == DeliveryStatus.PERMANENT_FAILURE)
            {
                throw DeliveryException.permanent(providerCode, exception);
            }
            throw DeliveryException.retryable(providerCode, exception);
        }
    }

    static Message buildMessage(FirebaseEnvelope envelope)
    {
        return Message.builder()
                .setToken(envelope.getToken())
                .setNotification(Notification.builder()
                        .setTitle(envelope.getTitle())
                        .setBody(envelope.getBody())
                        .build())
                .setAndroidConfig(AndroidConfig.builder()
                        .setPriority(AndroidConfig.Priority.HIGH)
                        .setNotification(AndroidNotification.builder()
                                .setChannelId("erp_messages")
                                .setSound("default")
                                .build())
                        .build())
                .putAllData(envelope.getData())
                .build();
    }

    private static DeliveryResult fromException(DeliveryException exception)
    {
        return switch (exception.getStatus())
        {
            case INVALID_TOKEN -> DeliveryResult.invalidToken(exception.getProviderCode());
            case PERMANENT_FAILURE -> DeliveryResult.permanentFailure(exception.getProviderCode());
            case DISABLED -> DeliveryResult.disabled(exception.getProviderCode());
            default -> DeliveryResult.retryableFailure(exception.getProviderCode());
        };
    }

    private static boolean blank(String value)
    {
        return value == null || value.trim().isEmpty();
    }

    @FunctionalInterface
    interface FirebaseGateway
    {
        String send(FirebaseEnvelope envelope) throws DeliveryException;
    }

    static final class FirebaseEnvelope
    {
        private final String token;

        private final String title;

        private final String body;

        private final Map<String, String> data;

        FirebaseEnvelope(String token, String title, String body, Map<String, String> data)
        {
            this.token = token;
            this.title = title;
            this.body = body;
            this.data = Collections.unmodifiableMap(new LinkedHashMap<>(data));
        }

        String getToken()
        {
            return token;
        }

        String getTitle()
        {
            return title;
        }

        String getBody()
        {
            return body;
        }

        Map<String, String> getData()
        {
            return data;
        }
    }
}
