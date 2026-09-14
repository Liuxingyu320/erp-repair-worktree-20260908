package com.erp.system.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.erp.common.core.exception.ServiceException;
import com.erp.system.api.domain.UserNotificationCommand;
import com.erp.system.api.domain.UserNotificationResult;
import com.erp.system.config.InAppPushProperties;
import com.erp.system.domain.SysUserDeviceToken;
import com.erp.system.domain.SysUserNotification;
import com.erp.system.domain.SysUserPushDelivery;
import com.erp.system.mapper.SysUserDeviceTokenMapper;
import com.erp.system.mapper.SysUserNotificationMapper;
import com.erp.system.mapper.SysUserPushDeliveryMapper;
import com.erp.system.service.push.PushDeliveryClient;
import com.fasterxml.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class SysUserNotificationServiceImplTest
{
    private static final Instant NOW = Instant.parse("2026-07-17T04:00:00Z");

    @Mock
    private SysUserNotificationMapper notificationMapper;

    @Mock
    private SysUserDeviceTokenMapper deviceTokenMapper;

    @Mock
    private SysUserPushDeliveryMapper pushDeliveryMapper;

    private SysUserNotificationServiceImpl service;

    @BeforeEach
    void setUp()
    {
        service = serviceWith(List.of());
    }

    @Test
    void publishShouldPersistOneTargetedMessageAndReturnExistingMessageForDuplicateKey()
    {
        UserNotificationCommand command = command();
        when(notificationMapper.insertNotification(any(SysUserNotification.class))).thenAnswer(invocation -> {
            SysUserNotification notification = invocation.getArgument(0);
            notification.setNotificationId(81L);
            return 1;
        });

        UserNotificationResult created = service.publish(command);

        ArgumentCaptor<SysUserNotification> saved = ArgumentCaptor.forClass(SysUserNotification.class);
        verify(notificationMapper).insertNotification(saved.capture());
        assertThat(saved.getValue().getUserId()).isEqualTo(42L);
        assertThat(saved.getValue().getBusinessKey()).isEqualTo("SIGN_WAITING_HR:9:3");
        assertThat(saved.getValue().getReadStatus()).isEqualTo("0");
        assertThat(created.getAccepted()).isTrue();
        assertThat(created.getNotificationId()).isEqualTo(81L);
        assertThat(created.getStatus()).isEqualTo("CREATED");

        SysUserNotification existing = new SysUserNotification();
        existing.setNotificationId(81L);
        when(notificationMapper.insertNotification(any(SysUserNotification.class))).thenReturn(0);
        when(notificationMapper.selectByUserAndBusinessKey(42L, "SIGN_WAITING_HR:9:3"))
                .thenReturn(existing);

        UserNotificationResult duplicate = service.publish(command);

        assertThat(duplicate.getAccepted()).isTrue();
        assertThat(duplicate.getNotificationId()).isEqualTo(81L);
        assertThat(duplicate.getStatus()).isEqualTo("DUPLICATE");
    }

    @Test
    void currentUserQueriesAndReadMustAlwaysIncludeOwnerId()
    {
        when(notificationMapper.selectByUserId(42L)).thenReturn(Collections.emptyList());
        when(notificationMapper.countUnreadByUserId(42L)).thenReturn(3L);
        when(notificationMapper.markRead(77L, 42L)).thenReturn(1);

        assertThat(service.selectUserNotifications(42L)).isEmpty();
        assertThat(service.countUnread(42L)).isEqualTo(3L);
        assertThat(service.markRead(77L, 42L)).isEqualTo(1);

        verify(notificationMapper).selectByUserId(42L);
        verify(notificationMapper).countUnreadByUserId(42L);
        verify(notificationMapper).markRead(77L, 42L);
    }

    @Test
    void registerDeviceTokenMustIgnoreRequestedUserAndOnlyReturnMaskedSuffix() throws Exception
    {
        SysUserDeviceToken request = tokenRequest();
        request.setUserId(999L);
        when(deviceTokenMapper.upsertDeviceToken(any(SysUserDeviceToken.class))).thenReturn(1);

        UserNotificationResult result = service.registerDeviceToken(42L, request);

        ArgumentCaptor<SysUserDeviceToken> saved = ArgumentCaptor.forClass(SysUserDeviceToken.class);
        verify(deviceTokenMapper).upsertDeviceToken(saved.capture());
        assertThat(saved.getValue().getUserId()).isEqualTo(42L);
        assertThat(saved.getValue().getPlatform()).isEqualTo("ANDROID");
        assertThat(saved.getValue().getToken()).isEqualTo("very-secret-device-token-123456");
        assertThat(saved.getValue().getTokenHash()).isEqualTo(sha256("very-secret-device-token-123456"));
        assertThat(result.getAccepted()).isTrue();
        assertThat(result.getStatus()).isEqualTo("BOUND");
        assertThat(result.getTokenSuffix()).isEqualTo("123456");
        assertThat(result.toString()).doesNotContain("very-secret-device-token");
    }

    @Test
    void disableDeviceTokenMustBeOwnerBoundAndHashTheRawToken() throws Exception
    {
        SysUserDeviceToken request = tokenRequest();
        request.setUserId(999L);
        when(deviceTokenMapper.disableDeviceToken(42L, "ANDROID", sha256(request.getToken()))).thenReturn(1);

        int disabled = service.disableDeviceToken(42L, request);

        assertThat(disabled).isEqualTo(1);
        verify(deviceTokenMapper).disableDeviceToken(42L, "ANDROID", sha256(request.getToken()));
    }

    @Test
    void publishShouldRejectMissingRecipientOrUnsupportedChannel()
    {
        UserNotificationCommand missingRecipient = command();
        missingRecipient.setRecipientUserId(null);
        assertThatThrownBy(() -> service.publish(missingRecipient))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("接收用户");

        UserNotificationCommand badChannel = command();
        badChannel.setChannel("EMAIL");
        assertThatThrownBy(() -> service.publish(badChannel))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("消息渠道");
    }

    @Test
    void tokenShouldBeAcceptedFromJsonButNeverSerializedBack() throws Exception
    {
        ObjectMapper objectMapper = new ObjectMapper();
        String json = "{\"platform\":\"IOS\",\"token\":\"private-token-654321\","
                + "\"appId\":\"com.erp.mobile\"}";

        SysUserDeviceToken request = objectMapper.readValue(json, SysUserDeviceToken.class);

        assertThat(request.getToken()).isEqualTo("private-token-654321");
        request.setTokenHash(sha256(request.getToken()));
        assertThat(objectMapper.writeValueAsString(request))
                .doesNotContain("private-token-654321")
                .doesNotContain(request.getTokenHash())
                .doesNotContain("\"token\"");
    }

    @Test
    void migrationAndMappersShouldEnforceDedupeOwnershipAndAtomicRebinding() throws Exception
    {
        String sql = readRepoFile("sql/erp_system_user_notification_20260711.sql");
        String dockerSql = readRepoFile("docker/mysql/db/erp_system_user_notification_20260711.sql");
        String pushSql = readRepoFile("sql/erp_system_user_push_delivery_20260717.sql");
        String dockerPushSql = readRepoFile(
                "docker/mysql/db/erp_system_user_push_delivery_20260717.sql");
        String notificationMapperXml = readRepoFile(
                "erp-modules/erp-system/src/main/resources/mapper/system/SysUserNotificationMapper.xml");
        String pushMapperXml = readRepoFile(
                "erp-modules/erp-system/src/main/resources/mapper/system/SysUserPushDeliveryMapper.xml");
        String tokenMapperXml = readRepoFile(
                "erp-modules/erp-system/src/main/resources/mapper/system/SysUserDeviceTokenMapper.xml");

        assertThat(dockerSql).isEqualTo(sql);
        assertThat(sql)
                .contains("CREATE TABLE IF NOT EXISTS sys_user_notification")
                .contains("UNIQUE KEY uk_sys_user_notification_business (user_id, business_key)")
                .contains("CREATE TABLE IF NOT EXISTS sys_user_device_token")
                .contains("UNIQUE KEY uk_sys_user_device_token (platform, token_hash)")
                .doesNotContain("INSERT INTO sys_notice");
        assertThat(dockerPushSql).isEqualTo(pushSql);
        assertThat(pushSql)
                .contains(
                        "CREATE TABLE IF NOT EXISTS sys_user_push_delivery",
                        "business_key_hash char(64) NOT NULL",
                        "payload_hash char(64) NOT NULL",
                        "UNIQUE KEY uk_sys_user_push_delivery_business",
                        "(user_id, channel, business_key_hash)",
                        "KEY idx_sys_user_push_delivery_status",
                        "status varchar(20) NOT NULL DEFAULT 'PENDING'",
                        "version bigint NOT NULL DEFAULT 0")
                .doesNotContain("CHECK (");
        assertThat(notificationMapperXml)
                .contains("where user_id = #{userId}")
                .contains("and business_key = #{businessKey}")
                .contains("and user_id = #{userId}");
        assertThat(pushMapperXml)
                .contains(
                        "insert ignore into sys_user_push_delivery",
                        "where user_id = #{userId}",
                        "and channel = #{channel}",
                        "and business_key_hash = #{businessKeyHash}",
                        "status = 'SENDING'",
                        "attempt_count = attempt_count + 1",
                        "and (status != 'SENDING' or updated_time &lt;= #{staleSendingBefore})",
                        "status = 'SENT'",
                        "status = 'RETRY'",
                        "status = 'DEAD'",
                        "and version = #{version}");
        assertThat(tokenMapperXml)
                .contains("on duplicate key update")
                .contains("user_id = values(user_id)")
                .contains("where user_id = #{userId}")
                .contains("and token_hash = #{tokenHash}");
    }

    @Test
    void mapperXmlShouldBindEveryNotificationAndDeviceStatement() throws Exception
    {
        Configuration configuration = new Configuration();
        configuration.getTypeAliasRegistry().registerAlias("SysUserNotification", SysUserNotification.class);
        configuration.getTypeAliasRegistry().registerAlias("SysUserDeviceToken", SysUserDeviceToken.class);
        parseMapper(configuration, "mapper/system/SysUserNotificationMapper.xml");
        parseMapper(configuration, "mapper/system/SysUserPushDeliveryMapper.xml");
        parseMapper(configuration, "mapper/system/SysUserDeviceTokenMapper.xml");

        assertMapped(configuration, SysUserNotificationMapper.class, "insertNotification");
        assertMapped(configuration, SysUserNotificationMapper.class, "selectByUserAndBusinessKey");
        assertMapped(configuration, SysUserNotificationMapper.class, "selectByUserId");
        assertMapped(configuration, SysUserNotificationMapper.class, "countUnreadByUserId");
        assertMapped(configuration, SysUserNotificationMapper.class, "markRead");
        assertMapped(configuration, SysUserPushDeliveryMapper.class, "insertIgnore");
        assertMapped(configuration, SysUserPushDeliveryMapper.class, "selectByBusinessKeyHash");
        assertMapped(configuration, SysUserPushDeliveryMapper.class, "claimForSending");
        assertMapped(configuration, SysUserPushDeliveryMapper.class, "markSent");
        assertMapped(configuration, SysUserPushDeliveryMapper.class, "markSkipped");
        assertMapped(configuration, SysUserPushDeliveryMapper.class, "markRetry");
        assertMapped(configuration, SysUserPushDeliveryMapper.class, "markDead");
        assertMapped(configuration, SysUserDeviceTokenMapper.class, "upsertDeviceToken");
        assertMapped(configuration, SysUserDeviceTokenMapper.class, "disableDeviceToken");
        assertMapped(configuration, SysUserDeviceTokenMapper.class, "disableByTokenHash");
        assertMapped(configuration, SysUserDeviceTokenMapper.class, "selectEnabledByUserId");
    }

    @Test
    void mobilePushShouldUseEnabledDevicesWithoutWritingAnnouncementOrInAppRows()
    {
        PushDeliveryClient client = org.mockito.Mockito.mock(PushDeliveryClient.class);
        SysUserDeviceToken token = tokenRequest();
        UserNotificationCommand command = mobileCommand();
        token.setTokenHash("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        when(client.platform()).thenReturn("ANDROID");
        when(client.deliver(token, command))
                .thenReturn(PushDeliveryClient.DeliveryResult.delivered("FCM_ACCEPTED"));
        when(deviceTokenMapper.selectEnabledByUserId(42L)).thenReturn(List.of(token));
        allowNewPushDelivery();
        service = serviceWith(List.of(client));

        UserNotificationResult result = service.publish(command);

        assertThat(result.getAccepted()).isTrue();
        assertThat(result.getStatus()).isEqualTo("DELIVERED");
        verify(deviceTokenMapper).selectEnabledByUserId(42L);
        org.mockito.Mockito.verifyNoInteractions(notificationMapper);
    }

    @Test
    void invalidMobileTokenShouldBeDisabledAndReportedAsPermanentFailure()
    {
        PushDeliveryClient client = org.mockito.Mockito.mock(PushDeliveryClient.class);
        SysUserDeviceToken token = tokenRequest();
        UserNotificationCommand command = mobileCommand();
        token.setTokenHash("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        when(client.platform()).thenReturn("ANDROID");
        when(client.deliver(token, command))
                .thenReturn(PushDeliveryClient.DeliveryResult.invalidToken("UNREGISTERED"));
        when(deviceTokenMapper.selectEnabledByUserId(42L)).thenReturn(List.of(token));
        allowNewPushDelivery();
        service = serviceWith(List.of(client));

        UserNotificationResult result = service.publish(command);

        assertThat(result.getAccepted()).isFalse();
        assertThat(result.getStatus()).isEqualTo("PERMANENT_FAILURE");
        verify(deviceTokenMapper).disableByTokenHash("ANDROID", token.getTokenHash());
    }

    @Test
    void sentMobilePushReplayMustReturnDuplicateWithoutCallingProvider()
    {
        PushDeliveryClient client = org.mockito.Mockito.mock(PushDeliveryClient.class);
        UserNotificationCommand command = mobileCommand();
        SysUserPushDelivery sent = pushDelivery(command, "SENT", 4L, Date.from(NOW.minusSeconds(10)));
        when(pushDeliveryMapper.insertIgnore(any())).thenReturn(0);
        when(pushDeliveryMapper.selectByBusinessKeyHash(42L, "MOBILE_PUSH",
                SysUserNotificationServiceImpl.businessKeyHash(command.getBusinessKey())))
                .thenReturn(sent);
        service = serviceWith(List.of(client));

        UserNotificationResult result = service.publish(command);

        assertThat(result.getAccepted()).isTrue();
        assertThat(result.getStatus()).isEqualTo("DUPLICATE");
        org.mockito.Mockito.verifyNoInteractions(deviceTokenMapper, client);
    }

    @Test
    void freshSendingClaimMustReturnRetryableWithoutDuplicateProviderCall()
    {
        PushDeliveryClient client = org.mockito.Mockito.mock(PushDeliveryClient.class);
        UserNotificationCommand command = mobileCommand();
        SysUserPushDelivery sending = pushDelivery(command, "SENDING", 2L, Date.from(NOW));
        when(pushDeliveryMapper.insertIgnore(any())).thenReturn(0);
        when(pushDeliveryMapper.selectByBusinessKeyHash(42L, "MOBILE_PUSH",
                SysUserNotificationServiceImpl.businessKeyHash(command.getBusinessKey())))
                .thenReturn(sending);
        service = serviceWith(List.of(client));

        UserNotificationResult result = service.publish(command);

        assertThat(result.getAccepted()).isFalse();
        assertThat(result.getStatus()).isEqualTo("RETRYABLE_FAILURE");
        org.mockito.Mockito.verifyNoInteractions(deviceTokenMapper, client);
    }

    @Test
    void staleSendingClaimMayBeReclaimedUnderDocumentedAtLeastOncePolicy()
    {
        PushDeliveryClient client = org.mockito.Mockito.mock(PushDeliveryClient.class);
        SysUserDeviceToken token = tokenRequest();
        token.setTokenHash("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        UserNotificationCommand command = mobileCommand();
        SysUserPushDelivery stale = pushDelivery(command, "SENDING", 2L,
                Date.from(NOW.minusSeconds(301)));
        String keyHash = SysUserNotificationServiceImpl.businessKeyHash(command.getBusinessKey());
        when(pushDeliveryMapper.insertIgnore(any())).thenReturn(0);
        when(pushDeliveryMapper.selectByBusinessKeyHash(42L, "MOBILE_PUSH", keyHash))
                .thenReturn(stale);
        when(pushDeliveryMapper.claimForSending(501L, "SENDING", 2L,
                Date.from(NOW.minusSeconds(300)))).thenReturn(1);
        when(deviceTokenMapper.selectEnabledByUserId(42L)).thenReturn(List.of(token));
        when(client.platform()).thenReturn("ANDROID");
        when(client.deliver(token, command))
                .thenReturn(PushDeliveryClient.DeliveryResult.delivered("FCM_ACCEPTED"));
        when(pushDeliveryMapper.markSent(501L, 3L, "DELIVERED")).thenReturn(1);
        service = serviceWith(List.of(client));

        UserNotificationResult result = service.publish(command);

        assertThat(result.getAccepted()).isTrue();
        assertThat(result.getStatus()).isEqualTo("DELIVERED");
        verify(client).deliver(token, command);
    }

    @Test
    void retryLedgerMustBeClaimedAgainByCas()
    {
        PushDeliveryClient client = org.mockito.Mockito.mock(PushDeliveryClient.class);
        SysUserDeviceToken token = tokenRequest();
        token.setTokenHash("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        UserNotificationCommand command = mobileCommand();
        SysUserPushDelivery retry = pushDelivery(command, "RETRY", 6L,
                Date.from(NOW.minusSeconds(60)));
        String keyHash = SysUserNotificationServiceImpl.businessKeyHash(command.getBusinessKey());
        when(pushDeliveryMapper.insertIgnore(any())).thenReturn(0);
        when(pushDeliveryMapper.selectByBusinessKeyHash(42L, "MOBILE_PUSH", keyHash))
                .thenReturn(retry);
        when(pushDeliveryMapper.claimForSending(501L, "RETRY", 6L,
                Date.from(NOW.minusSeconds(300)))).thenReturn(1);
        when(deviceTokenMapper.selectEnabledByUserId(42L)).thenReturn(List.of(token));
        when(client.platform()).thenReturn("ANDROID");
        when(client.deliver(token, command))
                .thenReturn(PushDeliveryClient.DeliveryResult.delivered("FCM_ACCEPTED"));
        when(pushDeliveryMapper.markSent(501L, 7L, "DELIVERED")).thenReturn(1);
        service = serviceWith(List.of(client));

        UserNotificationResult result = service.publish(command);

        assertThat(result.getAccepted()).isTrue();
        assertThat(result.getStatus()).isEqualTo("DELIVERED");
        verify(client).deliver(token, command);
    }

    @Test
    void sameBusinessHashWithDifferentPayloadMustFailClosed()
    {
        PushDeliveryClient client = org.mockito.Mockito.mock(PushDeliveryClient.class);
        UserNotificationCommand command = mobileCommand();
        SysUserPushDelivery collision = pushDelivery(command, "SENT", 4L, Date.from(NOW));
        collision.setPayloadHash("ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff");
        when(pushDeliveryMapper.insertIgnore(any())).thenReturn(0);
        when(pushDeliveryMapper.selectByBusinessKeyHash(42L, "MOBILE_PUSH",
                SysUserNotificationServiceImpl.businessKeyHash(command.getBusinessKey())))
                .thenReturn(collision);
        service = serviceWith(List.of(client));

        assertThatThrownBy(() -> service.publish(command))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("载荷冲突");
        org.mockito.Mockito.verifyNoInteractions(deviceTokenMapper, client);
    }

    @Test
    void providerSuccessFollowedByMarkSentFailureMustExposeAmbiguousWindow()
    {
        PushDeliveryClient client = org.mockito.Mockito.mock(PushDeliveryClient.class);
        SysUserDeviceToken token = tokenRequest();
        token.setTokenHash("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        UserNotificationCommand command = mobileCommand();
        allowNewPushDelivery();
        when(pushDeliveryMapper.markSent(501L, 1L, "DELIVERED")).thenReturn(0);
        when(deviceTokenMapper.selectEnabledByUserId(42L)).thenReturn(List.of(token));
        when(client.platform()).thenReturn("ANDROID");
        when(client.deliver(token, command))
                .thenReturn(PushDeliveryClient.DeliveryResult.delivered("FCM_ACCEPTED"));
        service = serviceWith(List.of(client));

        assertThatThrownBy(() -> service.publish(command))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("账本状态已变化");
        verify(client).deliver(token, command);
    }

    @Test
    void disabledPushIsRetryableAndNeverMarkedSent()
    {
        when(deviceTokenMapper.selectEnabledByUserId(42L)).thenReturn(List.of(tokenRequest()));
        allowNewPushDelivery();

        UserNotificationResult result = service.publish(mobileCommand());

        assertThat(result.getAccepted()).isFalse();
        assertThat(result.getStatus()).isEqualTo("DISABLED");
        verify(pushDeliveryMapper).markRetry(501L, 1L, "DISABLED");
        org.mockito.Mockito.verify(pushDeliveryMapper, org.mockito.Mockito.never()).markSent(any(), any(), any());
    }

    @Test
    void missingDeviceIsSkippedAndSkippedReplayDoesNotClaimDelivery()
    {
        when(deviceTokenMapper.selectEnabledByUserId(42L)).thenReturn(List.of());
        allowNewPushDelivery();

        UserNotificationResult result = service.publish(mobileCommand());

        assertThat(result.getAccepted()).isTrue();
        assertThat(result.getStatus()).isEqualTo("NO_DEVICE");
        verify(pushDeliveryMapper).markSkipped(501L, 1L, "NO_DEVICE");
        org.mockito.Mockito.verify(pushDeliveryMapper, org.mockito.Mockito.never()).markSent(any(), any(), any());
    }

    @Test
    void historicalDisabledSentRecordMustNotClaimItWasDeliveredOrSendHistoricalPush()
    {
        UserNotificationCommand command = mobileCommand();
        SysUserPushDelivery old = pushDelivery(command, "SENT", 4L, Date.from(NOW));
        old.setLastResult("DISABLED");
        when(pushDeliveryMapper.insertIgnore(any())).thenReturn(0);
        when(pushDeliveryMapper.selectByBusinessKeyHash(42L, "MOBILE_PUSH",
                SysUserNotificationServiceImpl.businessKeyHash(command.getBusinessKey()))).thenReturn(old);

        UserNotificationResult result = service.publish(command);

        assertThat(result.getAccepted()).isFalse();
        assertThat(result.getStatus()).isEqualTo("DISABLED");
        org.mockito.Mockito.verifyNoInteractions(deviceTokenMapper);
    }

    private static UserNotificationCommand command()
    {
        UserNotificationCommand command = new UserNotificationCommand();
        command.setChannel("IN_APP");
        command.setRecipientUserId(42L);
        command.setBusinessKey("SIGN_WAITING_HR:9:3");
        command.setTitle("合同待确认");
        command.setBody("员工合同草稿已经生成，请核对后确认发送。");
        command.setRouteType("OA_SIGN_HR_TASK");
        command.setRouteParams("{\"taskId\":9}");
        return command;
    }

    private static SysUserDeviceToken tokenRequest()
    {
        SysUserDeviceToken token = new SysUserDeviceToken();
        token.setPlatform("android");
        token.setToken("very-secret-device-token-123456");
        token.setAppId("com.erp.mobile");
        token.setDeviceName("Pixel test device");
        return token;
    }

    private static UserNotificationCommand mobileCommand()
    {
        UserNotificationCommand command = command();
        command.setChannel("MOBILE_PUSH");
        return command;
    }

    private SysUserNotificationServiceImpl serviceWith(List<PushDeliveryClient> clients)
    {
        return new SysUserNotificationServiceImpl(notificationMapper, deviceTokenMapper,
                pushDeliveryMapper, clients,
                new InAppNotificationWriter(notificationMapper, null, new InAppPushProperties()),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private void allowNewPushDelivery()
    {
        when(pushDeliveryMapper.insertIgnore(any(SysUserPushDelivery.class))).thenAnswer(invocation -> {
            SysUserPushDelivery delivery = invocation.getArgument(0);
            delivery.setDeliveryId(501L);
            return 1;
        });
        when(pushDeliveryMapper.claimForSending(
                org.mockito.ArgumentMatchers.eq(501L),
                org.mockito.ArgumentMatchers.eq("PENDING"),
                org.mockito.ArgumentMatchers.eq(0L),
                any(Date.class))).thenReturn(1);
        org.mockito.Mockito.lenient().when(pushDeliveryMapper.markSent(
                org.mockito.ArgumentMatchers.eq(501L),
                org.mockito.ArgumentMatchers.eq(1L), any())).thenReturn(1);
        org.mockito.Mockito.lenient().when(pushDeliveryMapper.markSkipped(
                org.mockito.ArgumentMatchers.eq(501L),
                org.mockito.ArgumentMatchers.eq(1L), any())).thenReturn(1);
        org.mockito.Mockito.lenient().when(pushDeliveryMapper.markRetry(
                org.mockito.ArgumentMatchers.eq(501L),
                org.mockito.ArgumentMatchers.eq(1L), any())).thenReturn(1);
        org.mockito.Mockito.lenient().when(pushDeliveryMapper.markDead(
                org.mockito.ArgumentMatchers.eq(501L),
                org.mockito.ArgumentMatchers.eq(1L), any())).thenReturn(1);
    }

    private static SysUserPushDelivery pushDelivery(UserNotificationCommand command,
            String status, Long version, Date updatedTime)
    {
        SysUserPushDelivery delivery = new SysUserPushDelivery();
        delivery.setDeliveryId(501L);
        delivery.setUserId(command.getRecipientUserId());
        delivery.setChannel("MOBILE_PUSH");
        delivery.setBusinessKey(command.getBusinessKey());
        delivery.setBusinessKeyHash(
                SysUserNotificationServiceImpl.businessKeyHash(command.getBusinessKey()));
        delivery.setPayloadHash(SysUserNotificationServiceImpl.pushPayloadHash(command));
        delivery.setStatus(status);
        delivery.setAttemptCount(1);
        delivery.setVersion(version);
        delivery.setUpdateTime(updatedTime);
        return delivery;
    }

    private static String sha256(String value) throws Exception
    {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        StringBuilder result = new StringBuilder(digest.length * 2);
        for (byte item : digest)
        {
            result.append(String.format("%02x", item & 0xff));
        }
        return result.toString();
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

    private static void parseMapper(Configuration configuration, String resource) throws Exception
    {
        try (InputStream inputStream = Resources.getResourceAsStream(resource))
        {
            XMLMapperBuilder mapperParser = new XMLMapperBuilder(inputStream, configuration, resource,
                    configuration.getSqlFragments());
            mapperParser.parse();
        }
    }

    private static void assertMapped(Configuration configuration, Class<?> mapperType, String statementId)
    {
        assertThat(configuration.hasStatement(mapperType.getName() + "." + statementId))
                .as("mapped statement %s.%s", mapperType.getName(), statementId)
                .isTrue();
    }
}
