package com.erp.system.service.impl;

import java.nio.charset.StandardCharsets;
import com.erp.system.domain.dto.SysUserNotificationPageQuery;
import com.erp.system.domain.dto.SysUserNotificationReadAllRequest;
import com.erp.system.domain.vo.SysUserNotificationPageResult;
import com.erp.system.domain.vo.SysUserNotificationReadAllResult;
import org.springframework.transaction.annotation.Transactional;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.erp.common.core.exception.ServiceException;
import com.erp.system.api.domain.UserNotificationCommand;
import com.erp.system.api.domain.UserNotificationResult;
import com.erp.system.domain.SysUserDeviceToken;
import com.erp.system.domain.SysUserNotification;
import com.erp.system.domain.SysUserPushDelivery;
import com.erp.system.mapper.SysUserDeviceTokenMapper;
import com.erp.system.mapper.SysUserNotificationMapper;
import com.erp.system.mapper.SysUserPushDeliveryMapper;
import com.erp.system.service.ISysUserNotificationService;
import com.erp.system.service.push.PushDeliveryClient;
import com.erp.system.service.push.PushDeliveryClient.DeliveryResult;
import com.erp.system.service.push.PushDeliveryClient.DeliveryStatus;

/**
 * 定向用户消息与设备令牌服务实现。
 */
@Service
public class SysUserNotificationServiceImpl implements ISysUserNotificationService
{
    private static final Set<String> CHANNELS = Set.of("IN_APP", "MOBILE_PUSH");

    private static final Set<String> PLATFORMS = Set.of("ANDROID", "IOS");

    private static final String MOBILE_PUSH = "MOBILE_PUSH";

    private static final long STALE_SENDING_SECONDS = 300L;

    private final SysUserNotificationMapper notificationMapper;

    private final SysUserDeviceTokenMapper deviceTokenMapper;

    private final SysUserPushDeliveryMapper pushDeliveryMapper;

    private final List<PushDeliveryClient> deliveryClients;

    private final InAppNotificationWriter inAppWriter;

    private final Clock clock;

    @Autowired
    public SysUserNotificationServiceImpl(SysUserNotificationMapper notificationMapper,
            SysUserDeviceTokenMapper deviceTokenMapper,
            SysUserPushDeliveryMapper pushDeliveryMapper,
            List<PushDeliveryClient> deliveryClients, InAppNotificationWriter inAppWriter)
    {
        this(notificationMapper, deviceTokenMapper, pushDeliveryMapper,
                deliveryClients, inAppWriter, Clock.systemUTC());
    }

    SysUserNotificationServiceImpl(SysUserNotificationMapper notificationMapper,
            SysUserDeviceTokenMapper deviceTokenMapper,
            SysUserPushDeliveryMapper pushDeliveryMapper,
            List<PushDeliveryClient> deliveryClients, InAppNotificationWriter inAppWriter, Clock clock)
    {
        this.notificationMapper = notificationMapper;
        this.deviceTokenMapper = deviceTokenMapper;
        this.pushDeliveryMapper = pushDeliveryMapper;
        this.deliveryClients = deliveryClients == null ? Collections.emptyList() : List.copyOf(deliveryClients);
        this.inAppWriter = inAppWriter;
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    @Override
    public UserNotificationResult publish(UserNotificationCommand command)
    {
        validatePublish(command);
        if (MOBILE_PUSH.equals(normalize(command.getChannel())))
        {
            return publishMobilePush(command);
        }
        SysUserNotification notification = new SysUserNotification();
        notification.setUserId(command.getRecipientUserId());
        notification.setChannel(normalize(command.getChannel()));
        notification.setBusinessKey(required(command.getBusinessKey(), "消息业务键", 180));
        notification.setTitle(required(command.getTitle(), "消息标题", 120));
        notification.setBody(required(command.getBody(), "消息正文", 1000));
        notification.setRouteType(required(command.getRouteType(), "消息路由类型", 64));
        notification.setRouteParams(optional(command.getRouteParams(), 2000, "消息路由参数"));
        notification.setReadStatus("0");

        int inserted = inAppWriter.insert(notification);
        if (inserted > 0)
        {
            return result(true, notification.getNotificationId(), "CREATED", "消息已创建", null);
        }

        SysUserNotification existing = notificationMapper.selectByUserAndBusinessKey(
                command.getRecipientUserId(), notification.getBusinessKey());
        if (existing == null)
        {
            throw new ServiceException("消息幂等写入失败");
        }
        return result(true, existing.getNotificationId(), "DUPLICATE", "消息已存在", null);
    }

    /**
     * Suppresses OA request replays after a durable SENT record. Reclaiming a stale SENDING
     * record deliberately remains an at-least-once policy: a process failure after provider
     * success but before markSent cannot be resolved as exactly-once without provider/client
     * idempotency.
     */
    private UserNotificationResult publishMobilePush(UserNotificationCommand command)
    {
        normalizeMobileCommand(command);
        String businessKeyHash = businessKeyHash(command.getBusinessKey());
        String payloadHash = pushPayloadHash(command);
        SysUserPushDelivery delivery = insertOrLoadPushDelivery(
                command, businessKeyHash, payloadHash);
        validatePushDeliveryIdentity(delivery, command, businessKeyHash, payloadHash);

        if ("SENT".equals(delivery.getStatus()))
        {
            return completedPushResult(delivery);
        }
        if ("SKIPPED".equals(delivery.getStatus()))
        {
            return noDevicePushResult();
        }
        if ("DEAD".equals(delivery.getStatus()))
        {
            return permanentPushResult();
        }
        if (!isClaimable(delivery))
        {
            return retryablePushResult();
        }

        Date staleSendingBefore = Date.from(clock.instant().minusSeconds(STALE_SENDING_SECONDS));
        if (pushDeliveryMapper.claimForSending(delivery.getDeliveryId(), delivery.getStatus(),
                delivery.getVersion(), staleSendingBefore) != 1)
        {
            SysUserPushDelivery concurrent = pushDeliveryMapper.selectByBusinessKeyHash(
                    command.getRecipientUserId(), MOBILE_PUSH, businessKeyHash);
            validatePushDeliveryIdentity(concurrent, command, businessKeyHash, payloadHash);
            return resultAfterConcurrentClaim(concurrent);
        }
        delivery.setStatus("SENDING");
        delivery.setVersion(delivery.getVersion() + 1);

        UserNotificationResult result;
        try
        {
            result = deliverMobilePush(command);
        }
        catch (RuntimeException failure)
        {
            try
            {
                assertPushLedgerUpdated(pushDeliveryMapper.markRetry(delivery.getDeliveryId(),
                        delivery.getVersion(), "SYSTEM_FAILURE"));
            }
            catch (RuntimeException ledgerFailure)
            {
                failure.addSuppressed(ledgerFailure);
            }
            throw failure;
        }

        String status = result == null || result.getStatus() == null
                ? "UNKNOWN" : result.getStatus();
        if ("NO_DEVICE".equals(status))
        {
            assertPushLedgerUpdated(pushDeliveryMapper.markSkipped(delivery.getDeliveryId(),
                    delivery.getVersion(), status));
            return result;
        }
        if (result != null && result.getAccepted() && "DELIVERED".equals(status))
        {
            assertPushLedgerUpdated(pushDeliveryMapper.markSent(delivery.getDeliveryId(),
                    delivery.getVersion(), status));
            return result;
        }
        if ("PERMANENT_FAILURE".equalsIgnoreCase(status))
        {
            assertPushLedgerUpdated(pushDeliveryMapper.markDead(delivery.getDeliveryId(),
                    delivery.getVersion(), status));
            return result;
        }
        assertPushLedgerUpdated(pushDeliveryMapper.markRetry(delivery.getDeliveryId(),
                delivery.getVersion(), status));
        return result;
    }

    private UserNotificationResult deliverMobilePush(UserNotificationCommand command)
    {
        List<SysUserDeviceToken> devices = deviceTokenMapper.selectEnabledByUserId(command.getRecipientUserId());
        if (devices == null || devices.isEmpty())
        {
            return noDevicePushResult();
        }

        int delivered = 0;
        int retryable = 0;
        int permanent = 0;
        int disabled = 0;
        for (SysUserDeviceToken device : devices)
        {
            PushDeliveryClient client = findDeliveryClient(device == null ? null : device.getPlatform());
            DeliveryResult delivery;
            try
            {
                delivery = client == null
                        ? DeliveryResult.disabled("PUSH_DISABLED")
                        : client.deliver(device, command);
            }
            catch (RuntimeException exception)
            {
                delivery = DeliveryResult.retryableFailure("PUSH_CLIENT_FAILURE");
            }

            DeliveryStatus status = delivery == null ? DeliveryStatus.RETRYABLE_FAILURE : delivery.getStatus();
            switch (status)
            {
                case DELIVERED -> delivered++;
                case RETRYABLE_FAILURE -> retryable++;
                case INVALID_TOKEN ->
                {
                    permanent++;
                    disableInvalidToken(device);
                }
                case PERMANENT_FAILURE -> permanent++;
                case DISABLED -> disabled++;
            }
        }

        if (delivered > 0)
        {
            return result(true, null, "DELIVERED", "移动消息已投递", null);
        }
        if (retryable > 0)
        {
            return result(false, null, "RETRYABLE_FAILURE", "移动消息暂时投递失败", null);
        }
        if (permanent > 0)
        {
            return result(false, null, "PERMANENT_FAILURE", "移动消息无法投递", null);
        }
        if (disabled > 0)
        {
            return result(false, null, "DISABLED", "移动推送未启用，未投递", null);
        }
        return noDevicePushResult();
    }

    private void normalizeMobileCommand(UserNotificationCommand command)
    {
        command.setChannel(MOBILE_PUSH);
        command.setBusinessKey(required(command.getBusinessKey(), "消息业务键", 180));
        command.setTitle(required(command.getTitle(), "消息标题", 120));
        command.setBody(required(command.getBody(), "消息正文", 1000));
        command.setRouteType(required(command.getRouteType(), "消息路由类型", 64));
        command.setRouteParams(optional(command.getRouteParams(), 2000, "消息路由参数"));
    }

    private SysUserPushDelivery insertOrLoadPushDelivery(UserNotificationCommand command,
            String businessKeyHash, String payloadHash)
    {
        SysUserPushDelivery candidate = new SysUserPushDelivery();
        candidate.setUserId(command.getRecipientUserId());
        candidate.setChannel(MOBILE_PUSH);
        candidate.setBusinessKey(command.getBusinessKey());
        candidate.setBusinessKeyHash(businessKeyHash);
        candidate.setPayloadHash(payloadHash);
        candidate.setStatus("PENDING");
        candidate.setAttemptCount(0);
        candidate.setVersion(0L);
        if (pushDeliveryMapper.insertIgnore(candidate) == 1 && candidate.getDeliveryId() != null)
        {
            return candidate;
        }
        SysUserPushDelivery existing = pushDeliveryMapper.selectByBusinessKeyHash(
                command.getRecipientUserId(), MOBILE_PUSH, businessKeyHash);
        if (existing == null)
        {
            throw new ServiceException("移动推送幂等账本写入失败");
        }
        return existing;
    }

    private void validatePushDeliveryIdentity(SysUserPushDelivery delivery,
            UserNotificationCommand command, String businessKeyHash, String payloadHash)
    {
        if (delivery == null || delivery.getDeliveryId() == null || delivery.getDeliveryId() <= 0
                || delivery.getVersion() == null || delivery.getVersion() < 0
                || delivery.getStatus() == null || delivery.getStatus().isBlank()
                || !Objects.equals(delivery.getUserId(), command.getRecipientUserId())
                || !MOBILE_PUSH.equals(delivery.getChannel())
                || !Objects.equals(delivery.getBusinessKeyHash(), businessKeyHash)
                || !Objects.equals(delivery.getBusinessKey(), command.getBusinessKey())
                || !Objects.equals(delivery.getPayloadHash(), payloadHash))
        {
            throw new ServiceException("移动推送业务键或载荷冲突");
        }
    }

    private boolean isClaimable(SysUserPushDelivery delivery)
    {
        if ("PENDING".equals(delivery.getStatus()) || "RETRY".equals(delivery.getStatus()))
        {
            return true;
        }
        if ("SENDING".equals(delivery.getStatus()))
        {
            Instant staleBefore = clock.instant().minusSeconds(STALE_SENDING_SECONDS);
            return delivery.getUpdateTime() != null
                    && !delivery.getUpdateTime().toInstant().isAfter(staleBefore);
        }
        throw new ServiceException("移动推送幂等账本状态无效");
    }

    private UserNotificationResult resultAfterConcurrentClaim(SysUserPushDelivery delivery)
    {
        if ("SENT".equals(delivery.getStatus()))
        {
            return completedPushResult(delivery);
        }
        if ("SKIPPED".equals(delivery.getStatus()))
        {
            return noDevicePushResult();
        }
        if ("DEAD".equals(delivery.getStatus()))
        {
            return permanentPushResult();
        }
        return retryablePushResult();
    }

    private static UserNotificationResult duplicatePushResult()
    {
        return result(true, null, "DUPLICATE", "移动推送已投递", null);
    }

    private static UserNotificationResult noDevicePushResult()
    {
        // Terminal skip remains accepted for existing callers; it is never recorded as delivery.
        return result(true, null, "NO_DEVICE", "用户没有可用移动设备，已跳过推送", null);
    }

    private static UserNotificationResult completedPushResult(SysUserPushDelivery delivery)
    {
        // Preserve the factual outcome of legacy SENT records, without replaying historical pushes.
        if ("NO_DEVICE".equals(delivery.getLastResult())) return noDevicePushResult();
        if ("DISABLED".equals(delivery.getLastResult()))
        {
            return result(false, null, "DISABLED", "历史推送未启用，未投递", null);
        }
        return duplicatePushResult();
    }

    private static UserNotificationResult retryablePushResult()
    {
        return result(false, null, "RETRYABLE_FAILURE", "移动推送正在处理", null);
    }

    private static UserNotificationResult permanentPushResult()
    {
        return result(false, null, "PERMANENT_FAILURE", "移动推送已终止重试", null);
    }

    private static void assertPushLedgerUpdated(int affected)
    {
        if (affected != 1)
        {
            throw new ServiceException("移动推送幂等账本状态已变化");
        }
    }

    static String businessKeyHash(String businessKey)
    {
        return sha256(required(businessKey, "消息业务键", 180));
    }

    static String pushPayloadHash(UserNotificationCommand command)
    {
        if (command == null || command.getRecipientUserId() == null)
        {
            throw new ServiceException("移动推送载荷不完整");
        }
        StringBuilder fingerprint = new StringBuilder();
        appendFingerprint(fingerprint, String.valueOf(command.getRecipientUserId()));
        appendFingerprint(fingerprint, normalize(command.getChannel()));
        appendFingerprint(fingerprint, trimToNull(command.getBusinessKey()));
        appendFingerprint(fingerprint, trimToNull(command.getTitle()));
        appendFingerprint(fingerprint, trimToNull(command.getBody()));
        appendFingerprint(fingerprint, trimToNull(command.getRouteType()));
        appendFingerprint(fingerprint, trimToNull(command.getRouteParams()));
        return sha256(fingerprint.toString());
    }

    private static void appendFingerprint(StringBuilder target, String value)
    {
        String safe = value == null ? "" : value;
        target.append(safe.length()).append(':').append(safe).append(';');
    }

    private static String trimToNull(String value)
    {
        if (value == null)
        {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private PushDeliveryClient findDeliveryClient(String platform)
    {
        if (platform != null)
        {
            for (PushDeliveryClient client : deliveryClients)
            {
                if (client != null && platform.equalsIgnoreCase(client.platform()))
                {
                    return client;
                }
            }
        }
        for (PushDeliveryClient client : deliveryClients)
        {
            if (client != null && "*".equals(client.platform()))
            {
                return client;
            }
        }
        return null;
    }

    private void disableInvalidToken(SysUserDeviceToken device)
    {
        if (device != null && device.getPlatform() != null && device.getTokenHash() != null)
        {
            deviceTokenMapper.disableByTokenHash(normalize(device.getPlatform()),
                    device.getTokenHash().toLowerCase(Locale.ROOT));
        }
    }

    @Override
    public List<SysUserNotification> selectUserNotifications(Long userId)
    {
        requireUser(userId);
        return notificationMapper.selectByUserId(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public SysUserNotificationPageResult page(Long userId, SysUserNotificationPageQuery query)
    {
        requireUser(userId);
        if (query == null) throw new ServiceException("消息查询参数不能为空");
        query.validate();
        Long snapshot = query.getParsedSnapshotMaxId();
        if (snapshot == null) snapshot = notificationMapper.selectMaxIdByUserId(userId);
        if (snapshot == null) snapshot = 0L;
        long total = notificationMapper.countPage(userId, snapshot, query);
        List<SysUserNotificationPageResult.Row> rows = notificationMapper.selectPage(userId, snapshot, query)
                .stream().map(SysUserNotificationPageResult.Row::from).toList();
        return new SysUserNotificationPageResult(rows, total, notificationMapper.countUnreadByUserId(userId),
                snapshot.toString(), notificationMapper.selectRouteTypesByUserId(userId), query.getPageNum(), query.getPageSize());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SysUserNotificationReadAllResult markAllRead(Long userId, SysUserNotificationReadAllRequest request)
    {
        requireUser(userId);
        if (request == null) throw new ServiceException("消息快照上限不能为空");
        Long snapshot = request.validate();
        int changed = notificationMapper.markAllRead(userId, snapshot);
        return new SysUserNotificationReadAllResult(changed, snapshot.toString(), notificationMapper.countUnreadByUserId(userId));
    }

    @Override
    public long countUnread(Long userId)
    {
        requireUser(userId);
        return notificationMapper.countUnreadByUserId(userId);
    }

    @Override
    public int markRead(Long notificationId, Long userId)
    {
        requireUser(userId);
        if (notificationId == null || notificationId <= 0)
        {
            throw new ServiceException("消息ID不能为空");
        }
        return notificationMapper.markRead(notificationId, userId);
    }

    @Override
    public UserNotificationResult registerDeviceToken(Long currentUserId, SysUserDeviceToken request)
    {
        requireUser(currentUserId);
        if (request == null)
        {
            throw new ServiceException("设备令牌不能为空");
        }
        String platform = validatePlatform(request.getPlatform());
        String rawToken = required(request.getToken(), "设备令牌", 4096);

        SysUserDeviceToken token = new SysUserDeviceToken();
        token.setUserId(currentUserId);
        token.setPlatform(platform);
        token.setToken(rawToken);
        token.setTokenHash(sha256(rawToken));
        token.setAppId(required(request.getAppId(), "应用ID", 100));
        token.setDeviceName(optional(request.getDeviceName(), 200, "设备名称"));
        token.setEnabled("1");
        deviceTokenMapper.upsertDeviceToken(token);

        return result(true, null, "BOUND", "设备已绑定", suffix(rawToken));
    }

    @Override
    public int disableDeviceToken(Long currentUserId, SysUserDeviceToken request)
    {
        requireUser(currentUserId);
        if (request == null)
        {
            throw new ServiceException("设备令牌不能为空");
        }
        String platform = validatePlatform(request.getPlatform());
        String rawToken = required(request.getToken(), "设备令牌", 4096);
        return deviceTokenMapper.disableDeviceToken(currentUserId, platform, sha256(rawToken));
    }

    @Override
    public List<SysUserDeviceToken> selectEnabledDeviceTokens(Long userId)
    {
        requireUser(userId);
        return deviceTokenMapper.selectEnabledByUserId(userId);
    }

    @Override
    public int disableInvalidDeviceToken(String platform, String tokenHash)
    {
        String normalizedPlatform = validatePlatform(platform);
        String normalizedHash = required(tokenHash, "设备令牌摘要", 64);
        if (!normalizedHash.matches("[0-9a-fA-F]{64}"))
        {
            throw new ServiceException("设备令牌摘要格式不正确");
        }
        return deviceTokenMapper.disableByTokenHash(normalizedPlatform, normalizedHash.toLowerCase(Locale.ROOT));
    }

    private static void validatePublish(UserNotificationCommand command)
    {
        if (command == null)
        {
            throw new ServiceException("消息命令不能为空");
        }
        if (command.getRecipientUserId() == null || command.getRecipientUserId() <= 0)
        {
            throw new ServiceException("消息接收用户不能为空");
        }
        String channel = normalize(command.getChannel());
        if (!CHANNELS.contains(channel))
        {
            throw new ServiceException("消息渠道只支持IN_APP或MOBILE_PUSH");
        }
    }

    private static String validatePlatform(String value)
    {
        String platform = normalize(value);
        if (!PLATFORMS.contains(platform))
        {
            throw new ServiceException("设备平台只支持ANDROID或IOS");
        }
        return platform;
    }

    private static void requireUser(Long userId)
    {
        if (userId == null || userId <= 0)
        {
            throw new ServiceException("当前用户不能为空");
        }
    }

    private static String required(String value, String label, int maxLength)
    {
        String normalized = value == null ? null : value.trim();
        if (normalized == null || normalized.isEmpty())
        {
            throw new ServiceException(label + "不能为空");
        }
        if (normalized.length() > maxLength)
        {
            throw new ServiceException(label + "长度不能超过" + maxLength);
        }
        return normalized;
    }

    private static String optional(String value, int maxLength, String label)
    {
        String normalized = value == null ? null : value.trim();
        if (normalized != null && normalized.length() > maxLength)
        {
            throw new ServiceException(label + "长度不能超过" + maxLength);
        }
        return normalized;
    }

    private static String normalize(String value)
    {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private static String sha256(String value)
    {
        try
        {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte item : digest)
            {
                hex.append(String.format("%02x", item & 0xff));
            }
            return hex.toString();
        }
        catch (NoSuchAlgorithmException exception)
        {
            throw new IllegalStateException("JVM不支持SHA-256", exception);
        }
    }

    private static String suffix(String token)
    {
        return token.substring(Math.max(0, token.length() - 6));
    }

    private static UserNotificationResult result(boolean accepted, Long notificationId, String status,
            String message, String tokenSuffix)
    {
        UserNotificationResult result = new UserNotificationResult();
        result.setAccepted(accepted);
        result.setNotificationId(notificationId);
        result.setStatus(status);
        result.setMessage(message);
        result.setTokenSuffix(tokenSuffix);
        return result;
    }
}
