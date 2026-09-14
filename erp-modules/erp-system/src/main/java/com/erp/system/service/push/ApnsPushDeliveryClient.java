package com.erp.system.service.push;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.alibaba.fastjson2.JSONObject;
import com.erp.common.core.exception.ServiceException;
import com.erp.system.api.domain.UserNotificationCommand;
import com.erp.system.config.PushNotificationProperties;
import com.erp.system.domain.SysUserDeviceToken;
import com.eatthepath.pushy.apns.ApnsClient;
import com.eatthepath.pushy.apns.ApnsClientBuilder;
import com.eatthepath.pushy.apns.DeliveryPriority;
import com.eatthepath.pushy.apns.PushType;
import com.eatthepath.pushy.apns.PushNotificationResponse;
import com.eatthepath.pushy.apns.auth.ApnsSigningKey;
import com.eatthepath.pushy.apns.util.SimpleApnsPushNotification;
import com.eatthepath.pushy.apns.util.TokenUtil;

/**
 * iOS APNs HTTP/2推送。P8私钥或P12推送证书只在首次实际投递时按需读取。
 */
@Component
public class ApnsPushDeliveryClient implements PushDeliveryClient
{
    private static final int APNS_MAX_PAYLOAD_BYTES = 4096;

    private static final long APNS_RESPONSE_TIMEOUT_SECONDS = 15;

    private final PushNotificationProperties properties;

    private final ApnsGateway injectedGateway;

    private volatile ApnsGateway runtimeGateway;

    private volatile ApnsClient runtimeClient;

    @Autowired
    public ApnsPushDeliveryClient(PushNotificationProperties properties)
    {
        this(properties, null);
    }

    ApnsPushDeliveryClient(PushNotificationProperties properties, ApnsGateway gateway)
    {
        this.properties = properties;
        this.injectedGateway = gateway;
    }

    @Override
    public String platform()
    {
        return "IOS";
    }

    @Override
    public DeliveryResult deliver(SysUserDeviceToken deviceToken, UserNotificationCommand command)
    {
        if (!properties.getApns().isComplete())
        {
            return DeliveryResult.disabled("APNS_DISABLED");
        }
        if (deviceToken == null || !"IOS".equalsIgnoreCase(deviceToken.getPlatform())
                || blank(deviceToken.getToken()))
        {
            return DeliveryResult.permanentFailure("INVALID_APNS_DEVICE");
        }

        final String payload;
        try
        {
            payload = buildPayload(command);
        }
        catch (ServiceException exception)
        {
            return DeliveryResult.permanentFailure("INVALID_ROUTE");
        }
        if (payload.getBytes(StandardCharsets.UTF_8).length > APNS_MAX_PAYLOAD_BYTES)
        {
            return DeliveryResult.permanentFailure("APNS_PAYLOAD_TOO_LARGE");
        }

        ApnsEnvelope envelope = new ApnsEnvelope(deviceToken.getToken(),
                properties.getApns().getBundleId().trim(), payload);
        try
        {
            ApnsGatewayResponse response = gateway().send(envelope);
            if (response.isAccepted())
            {
                return DeliveryResult.delivered("APNS_ACCEPTED");
            }
            if (response.isTokenInvalid())
            {
                return DeliveryResult.invalidToken(response.getRejectionReason());
            }
            DeliveryStatus status = classifyRejection(response.getRejectionReason());
            if (status == DeliveryStatus.INVALID_TOKEN)
            {
                return DeliveryResult.invalidToken(response.getRejectionReason());
            }
            if (status == DeliveryStatus.RETRYABLE_FAILURE)
            {
                return DeliveryResult.retryableFailure(response.getRejectionReason());
            }
            return DeliveryResult.permanentFailure(response.getRejectionReason());
        }
        catch (DeliveryException exception)
        {
            return fromException(exception);
        }
        catch (RuntimeException exception)
        {
            return DeliveryResult.retryableFailure("APNS_RUNTIME_FAILURE");
        }
    }

    static String resolveApnsHost(PushNotificationProperties.Apns properties)
    {
        return "sandbox".equalsIgnoreCase(properties.getEnvironment())
                ? ApnsClientBuilder.DEVELOPMENT_APNS_HOST
                : ApnsClientBuilder.PRODUCTION_APNS_HOST;
    }

    static DeliveryStatus classifyRejection(String rejectionReason)
    {
        if (rejectionReason == null)
        {
            return DeliveryStatus.RETRYABLE_FAILURE;
        }
        return switch (rejectionReason)
        {
            case "BadDeviceToken", "DeviceTokenNotForTopic", "ExpiredToken", "Unregistered" ->
                    DeliveryStatus.INVALID_TOKEN;
            case "TooManyRequests", "TooManyProviderTokenUpdates", "InternalServerError",
                    "ServiceUnavailable", "Shutdown" -> DeliveryStatus.RETRYABLE_FAILURE;
            default -> DeliveryStatus.PERMANENT_FAILURE;
        };
    }

    @PreDestroy
    public void close()
    {
        ApnsClient client = runtimeClient;
        if (client != null)
        {
            client.close();
        }
    }

    private ApnsGateway gateway() throws DeliveryException
    {
        if (injectedGateway != null)
        {
            return injectedGateway;
        }
        ApnsGateway current = runtimeGateway;
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

    private ApnsGateway createRuntimeGateway() throws DeliveryException
    {
        try
        {
            PushNotificationProperties.Apns apns = properties.getApns();
            ApnsClientBuilder builder = new ApnsClientBuilder()
                    .setApnsServer(resolveApnsHost(apns));
            configureAuthentication(builder, apns);
            ApnsClient client = builder.build();
            runtimeClient = client;
            return envelope -> sendWithApns(client, envelope);
        }
        catch (Exception exception)
        {
            throw DeliveryException.permanent("APNS_CREDENTIALS_INVALID", exception);
        }
    }

    static void configureAuthentication(ApnsClientBuilder builder, PushNotificationProperties.Apns apns)
            throws Exception
    {
        if ("certificate".equalsIgnoreCase(apns.getAuthMode()))
        {
            builder.setClientCredentials(new File(apns.getCertificatePath()),
                    apns.getCertificatePassword() == null ? "" : apns.getCertificatePassword());
        }
        else if ("token".equalsIgnoreCase(apns.getAuthMode()))
        {
            builder.setSigningKey(ApnsSigningKey.loadFromPkcs8File(new File(apns.getPrivateKeyPath()),
                    apns.getTeamId().trim(), apns.getKeyId().trim()));
        }
        else
        {
            throw new IllegalArgumentException("Unsupported APNs authentication mode");
        }
    }

    private static ApnsGatewayResponse sendWithApns(ApnsClient client, ApnsEnvelope envelope)
            throws DeliveryException
    {
        SimpleApnsPushNotification notification = buildNotification(envelope);
        try
        {
            PushNotificationResponse<SimpleApnsPushNotification> response = client.sendNotification(notification)
                    .get(APNS_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (response.isAccepted())
            {
                return ApnsGatewayResponse.accepted(response.getApnsId() == null
                        ? "APNS_ACCEPTED" : response.getApnsId().toString());
            }
            return ApnsGatewayResponse.rejected(response.getRejectionReason().orElse("APNS_REJECTED"),
                    response.getTokenInvalidationTimestamp().isPresent());
        }
        catch (InterruptedException exception)
        {
            Thread.currentThread().interrupt();
            throw DeliveryException.retryable("APNS_INTERRUPTED", exception);
        }
        catch (TimeoutException exception)
        {
            throw DeliveryException.retryable("APNS_TIMEOUT", exception);
        }
        catch (ExecutionException exception)
        {
            throw DeliveryException.retryable("APNS_CONNECTION_FAILURE", exception.getCause());
        }
    }

    static SimpleApnsPushNotification buildNotification(ApnsEnvelope envelope)
    {
        return new SimpleApnsPushNotification(TokenUtil.sanitizeTokenString(envelope.getToken()),
                envelope.getTopic(), envelope.getPayload(), Instant.now().plusSeconds(86400),
                DeliveryPriority.IMMEDIATE, PushType.ALERT);
    }

    private static String buildPayload(UserNotificationCommand command)
    {
        Map<String, String> routeData = PushDeliveryClient.whitelistedRouteData(command);
        JSONObject alert = new JSONObject();
        alert.put("title", command.getTitle());
        alert.put("body", command.getBody());
        JSONObject aps = new JSONObject();
        aps.put("alert", alert);
        aps.put("sound", "default");
        JSONObject payload = new JSONObject();
        payload.put("aps", aps);
        routeData.forEach(payload::put);
        return payload.toJSONString();
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
    interface ApnsGateway
    {
        ApnsGatewayResponse send(ApnsEnvelope envelope) throws DeliveryException;
    }

    static final class ApnsEnvelope
    {
        private final String token;

        private final String topic;

        private final String payload;

        ApnsEnvelope(String token, String topic, String payload)
        {
            this.token = token;
            this.topic = topic;
            this.payload = payload;
        }

        String getToken()
        {
            return token;
        }

        String getTopic()
        {
            return topic;
        }

        String getPayload()
        {
            return payload;
        }
    }

    static final class ApnsGatewayResponse
    {
        private final boolean accepted;

        private final String providerId;

        private final String rejectionReason;

        private final boolean tokenInvalid;

        private ApnsGatewayResponse(boolean accepted, String providerId, String rejectionReason,
                boolean tokenInvalid)
        {
            this.accepted = accepted;
            this.providerId = providerId;
            this.rejectionReason = rejectionReason;
            this.tokenInvalid = tokenInvalid;
        }

        static ApnsGatewayResponse accepted(String providerId)
        {
            return new ApnsGatewayResponse(true, providerId, null, false);
        }

        static ApnsGatewayResponse rejected(String rejectionReason)
        {
            return rejected(rejectionReason, false);
        }

        static ApnsGatewayResponse rejected(String rejectionReason, boolean tokenInvalid)
        {
            return new ApnsGatewayResponse(false, null, rejectionReason, tokenInvalid);
        }

        boolean isAccepted()
        {
            return accepted;
        }

        String getProviderId()
        {
            return providerId;
        }

        String getRejectionReason()
        {
            return rejectionReason;
        }

        boolean isTokenInvalid()
        {
            return tokenInvalid;
        }
    }
}
