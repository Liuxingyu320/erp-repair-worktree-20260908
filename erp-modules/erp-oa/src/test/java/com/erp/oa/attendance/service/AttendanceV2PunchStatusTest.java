package com.erp.oa.attendance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import com.erp.common.core.context.SecurityContextHolder;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.security.shop.ShopScopeService;
import com.erp.oa.attendance.config.AttendanceRuntimePolicy;
import com.erp.oa.attendance.config.AttendanceV2Properties;
import com.erp.oa.attendance.domain.AttendanceModels.Challenge;
import com.erp.oa.attendance.domain.AttendanceModels.PunchEvent;
import com.erp.oa.attendance.dto.AttendanceRequests.PunchStatusRequest;
import com.erp.oa.attendance.dto.AttendanceViews.PunchStatusResult;
import com.erp.oa.attendance.mapper.AttendanceV2Mapper;
import com.erp.oa.attendance.support.AttendanceAddressResolver;
import com.erp.oa.attendance.support.AttendanceChallengePolicy;
import com.erp.oa.attendance.support.AttendanceCoordinateTransformer;
import com.erp.oa.attendance.support.AttendanceDaySettlementCalculator;
import com.erp.oa.attendance.support.AttendanceGeoFence;
import com.erp.oa.attendance.support.AttendanceLocationAuditPolicy;
import com.erp.oa.attendance.support.AttendanceRuleEngine;
import com.erp.oa.service.BusinessFeatureGate;

class AttendanceV2PunchStatusTest
{
    private static final Long USER_ID = 9L;
    private static final Long SHOP_ID = 101L;
    private static final Long SCHEDULE_ID = 31L;
    private static final Long EVENT_ID = 41L;
    private static final Long CHALLENGE_ID = 51L;
    private static final String REQUEST_ID = "attendance-request-0001";
    private static final String TOKEN = "a".repeat(64);
    private static final LocalDate BUSINESS_DATE = LocalDate.of(2026, 8, 31);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 31,
            10, 0);

    private AttendanceV2Mapper mapper;
    private ShopScopeService shopScope;
    private AttendanceV2Service service;

    @BeforeEach
    void setUp()
    {
        SecurityContextHolder.setUserId(String.valueOf(USER_ID));
        SecurityContextHolder.setUserName("tester");
        mapper = mock(AttendanceV2Mapper.class);
        shopScope = mock(ShopScopeService.class);
        BusinessFeatureGate featureGate = mock(BusinessFeatureGate.class);
        doNothing().when(featureGate).requireEnabled(
                BusinessFeatureGate.ATTENDANCE_V2);
        when(shopScope.resolveRequiredShopDept(SHOP_ID)).thenReturn(SHOP_ID);
        when(mapper.selectDatabaseNow()).thenReturn(NOW);
        AttendanceRuleEngine rules = new AttendanceRuleEngine();
        service = new AttendanceV2Service(mapper, rules,
                mock(AttendanceLocationAuditPolicy.class),
                new AttendanceCoordinateTransformer(),
                new AttendanceGeoFence(),
                mock(AttendanceAddressResolver.class),
                mock(AttendanceChallengePolicy.class),
                new AttendanceDaySettlementCalculator(rules),
                mock(AttendanceEvidenceStorageService.class),
                new AttendanceV2Properties(),
                mock(AttendanceRuntimePolicy.class), shopScope, featureGate,
                Clock.fixed(Instant.parse("2026-08-31T02:00:00Z"),
                        ZoneId.of("Asia/Shanghai")));
    }

    @AfterEach
    void tearDown()
    {
        SecurityContextHolder.remove();
    }

    @Test
    void committedAcceptedEventWinsBeforeChallengeLock()
    {
        PunchEvent event = acceptedEvent();
        Challenge challenge = consumedChallenge();
        when(mapper.selectPunchByClientRequestForUser(USER_ID, REQUEST_ID,
                false)).thenReturn(event);
        when(mapper.selectChallengeByToken(TOKEN)).thenReturn(challenge);
        when(mapper.selectEvidenceIdForPunchInScope(EVENT_ID, SCHEDULE_ID,
                USER_ID)).thenReturn(61L);

        PunchStatusResult result = service.punchStatus(request(), SHOP_ID);

        assertThat(result.status).isEqualTo("ACCEPTED");
        assertThat(result.clientRequestId).isEqualTo(REQUEST_ID);
        assertThat(result.event).isSameAs(event);
        assertThat(result.evidenceId).isEqualTo(61L);
        verify(mapper, never()).selectChallengeByTokenForUpdate(any());
        verify(mapper, never()).selectPunchByClientRequestForUser(USER_ID,
                REQUEST_ID, true);
    }

    @Test
    void missingEventLocksChallengeThenReturnsPendingWhileUnexpired()
    {
        Challenge challenge = issuedChallenge(NOW.plusSeconds(1));
        when(mapper.selectChallengeByTokenForUpdate(TOKEN))
                .thenReturn(challenge);

        PunchStatusResult result = service.punchStatus(request(), SHOP_ID);

        assertThat(result.status).isEqualTo("PENDING");
        assertThat(result.clientRequestId).isEqualTo(REQUEST_ID);
        assertThat(result.event).isNull();
        assertThat(result.evidenceId).isNull();
        InOrder order = inOrder(mapper);
        order.verify(mapper).selectPunchByClientRequestForUser(USER_ID,
                REQUEST_ID, false);
        order.verify(mapper).selectChallengeByTokenForUpdate(TOKEN);
        order.verify(mapper).selectPunchByClientRequestForUser(USER_ID,
                REQUEST_ID, true);
        order.verify(mapper).selectDatabaseNow();
    }

    @Test
    void exactExpiryBoundaryRemainsPending()
    {
        when(mapper.selectChallengeByTokenForUpdate(TOKEN))
                .thenReturn(issuedChallenge(NOW));

        assertThat(service.punchStatus(request(), SHOP_ID).status)
                .isEqualTo("PENDING");
    }

    @Test
    void onlyExpiredAndUnconsumedChallengeReturnsNotAccepted()
    {
        when(mapper.selectChallengeByTokenForUpdate(TOKEN))
                .thenReturn(issuedChallenge(NOW.minusSeconds(1)));

        PunchStatusResult result = service.punchStatus(request(), SHOP_ID);

        assertThat(result.status).isEqualTo("NOT_ACCEPTED");
        assertThat(result.clientRequestId).isEqualTo(REQUEST_ID);
    }

    @Test
    void cleanupMarkedExpiredAndUnconsumedChallengeReturnsNotAccepted()
    {
        Challenge challenge = issuedChallenge(NOW.minusSeconds(1));
        challenge.status = "EXPIRED";
        when(mapper.selectChallengeByTokenForUpdate(TOKEN))
                .thenReturn(challenge);

        assertThat(service.punchStatus(request(), SHOP_ID).status)
                .isEqualTo("NOT_ACCEPTED");
    }

    @Test
    void revokedChallengeNeverConfirmsNotAccepted()
    {
        Challenge challenge = issuedChallenge(NOW.minusSeconds(1));
        challenge.status = "REVOKED";
        when(mapper.selectChallengeByTokenForUpdate(TOKEN))
                .thenReturn(challenge);

        assertContextInvalid(() -> service.punchStatus(request(), SHOP_ID));
    }

    @Test
    void concurrentCommitSeenAfterChallengeLockReturnsAccepted()
    {
        Challenge challenge = consumedChallenge();
        PunchEvent event = acceptedEvent();
        when(mapper.selectChallengeByTokenForUpdate(TOKEN))
                .thenReturn(challenge);
        when(mapper.selectPunchByClientRequestForUser(USER_ID, REQUEST_ID,
                true)).thenReturn(event);
        when(mapper.selectEvidenceIdForPunchInScope(EVENT_ID, SCHEDULE_ID,
                USER_ID)).thenReturn(61L);

        PunchStatusResult result = service.punchStatus(request(), SHOP_ID);

        assertThat(result.status).isEqualTo("ACCEPTED");
        assertThat(result.event).isSameAs(event);
        verify(mapper, never()).selectDatabaseNow();
    }

    @Test
    void consumedChallengeWithoutExactRequestEventFailsClosed()
    {
        when(mapper.selectChallengeByTokenForUpdate(TOKEN))
                .thenReturn(consumedChallenge());

        assertContextInvalid(() -> service.punchStatus(request(), SHOP_ID));

        verify(mapper, never()).selectDatabaseNow();
        verify(mapper, never()).selectEvidenceIdForPunchInScope(any(), any(),
                any());
    }

    @Test
    void wrongTokenForAnExistingRequestFailsClosedWithoutEvidence()
    {
        PunchEvent event = acceptedEvent();
        Challenge wrong = consumedChallenge();
        wrong.challengeToken = "b".repeat(64);
        when(mapper.selectPunchByClientRequestForUser(USER_ID, REQUEST_ID,
                false)).thenReturn(event);
        when(mapper.selectChallengeByToken(TOKEN)).thenReturn(wrong);

        assertContextInvalid(() -> service.punchStatus(request(), SHOP_ID));

        verify(mapper, never()).selectEvidenceIdForPunchInScope(any(), any(),
                any());
    }

    @Test
    void requestPairedWithAnotherOwnedChallengeFailsClosed()
    {
        PunchEvent event = acceptedEvent();
        Challenge another = consumedChallenge();
        another.challengeId = 52L;
        another.consumedEventId = 42L;
        when(mapper.selectPunchByClientRequestForUser(USER_ID, REQUEST_ID,
                false)).thenReturn(event);
        when(mapper.selectChallengeByToken(TOKEN)).thenReturn(another);

        assertContextInvalid(() -> service.punchStatus(request(), SHOP_ID));

        verify(mapper, never()).selectEvidenceIdForPunchInScope(any(), any(),
                any());
    }

    @Test
    void foreignOwnerOrOrganizationFailsClosedWithoutRequery()
    {
        Challenge foreign = issuedChallenge(NOW.plusMinutes(1));
        foreign.userId = 10L;
        when(mapper.selectChallengeByTokenForUpdate(TOKEN))
                .thenReturn(foreign);

        assertContextInvalid(() -> service.punchStatus(request(), SHOP_ID));

        verify(mapper, never()).selectPunchByClientRequestForUser(USER_ID,
                REQUEST_ID, true);
        verify(mapper, never()).selectEvidenceIdForPunchInScope(any(), any(),
                any());
    }

    @Test
    void acceptedEventFromAnotherOrganizationFailsClosed()
    {
        PunchEvent event = acceptedEvent();
        event.shopId = 202L;
        Challenge challenge = consumedChallenge();
        challenge.shopId = 202L;
        when(mapper.selectPunchByClientRequestForUser(USER_ID, REQUEST_ID,
                false)).thenReturn(event);
        when(mapper.selectChallengeByToken(TOKEN)).thenReturn(challenge);

        assertContextInvalid(() -> service.punchStatus(request(), SHOP_ID));

        verify(mapper, never()).selectEvidenceIdForPunchInScope(any(), any(),
                any());
    }

    @Test
    void acceptedEventWithoutPrivateEvidenceFailsClosed()
    {
        when(mapper.selectPunchByClientRequestForUser(USER_ID, REQUEST_ID,
                false)).thenReturn(acceptedEvent());
        when(mapper.selectChallengeByToken(TOKEN))
                .thenReturn(consumedChallenge());

        assertContextInvalid(() -> service.punchStatus(request(), SHOP_ID));
    }

    @Test
    void acceptedEventWithoutResolvedAddressFailsClosedBeforeEvidenceLookup()
    {
        PunchEvent event = acceptedEvent();
        event.resolvedAddress = null;
        when(mapper.selectPunchByClientRequestForUser(USER_ID, REQUEST_ID,
                false)).thenReturn(event);
        when(mapper.selectChallengeByToken(TOKEN))
                .thenReturn(consumedChallenge());

        assertContextInvalid(() -> service.punchStatus(request(), SHOP_ID));

        verify(mapper, never()).selectEvidenceIdForPunchInScope(any(), any(),
                any());
    }

    @Test
    void malformedCorrelationKeysFailBeforeScopeOrDataLookup()
    {
        PunchStatusRequest malformed = request();
        malformed.clientRequestId = "bad";

        assertContextInvalid(() -> service.punchStatus(malformed, SHOP_ID));

        verify(shopScope, never()).resolveRequiredShopDept(any());
        verify(mapper, never()).selectPunchByClientRequestForUser(any(), any(),
                anyBoolean());
    }

    private void assertContextInvalid(ThrowingCallable action)
    {
        assertThatThrownBy(action).isInstanceOf(ServiceException.class)
                .hasMessage("PUNCH_STATUS_CONTEXT_INVALID");
    }

    private PunchStatusRequest request()
    {
        PunchStatusRequest value = new PunchStatusRequest();
        value.clientRequestId = REQUEST_ID;
        value.challengeToken = TOKEN;
        return value;
    }

    private Challenge issuedChallenge(LocalDateTime expiresAt)
    {
        Challenge value = challenge();
        value.status = "ISSUED";
        value.expiresAt = expiresAt;
        return value;
    }

    private Challenge consumedChallenge()
    {
        Challenge value = challenge();
        value.status = "CONSUMED";
        value.expiresAt = NOW.plusMinutes(1);
        value.consumedAt = NOW.minusSeconds(1);
        value.consumedEventId = EVENT_ID;
        return value;
    }

    private Challenge challenge()
    {
        Challenge value = new Challenge();
        value.challengeId = CHALLENGE_ID;
        value.challengeToken = TOKEN;
        value.scheduleId = SCHEDULE_ID;
        value.userId = USER_ID;
        value.shopId = SHOP_ID;
        value.businessDate = BUSINESS_DATE;
        value.punchType = "IN";
        value.issuedAt = NOW.minusMinutes(1);
        return value;
    }

    private PunchEvent acceptedEvent()
    {
        PunchEvent value = new PunchEvent();
        value.punchEventId = EVENT_ID;
        value.eventNo = "AP2026083100001";
        value.scheduleId = SCHEDULE_ID;
        value.challengeId = CHALLENGE_ID;
        value.userId = USER_ID;
        value.userName = "测试用户";
        value.shopId = SHOP_ID;
        value.businessDate = BUSINESS_DATE;
        value.punchType = "IN";
        value.verificationStatus = "ACCEPTED";
        value.resolvedAddress = "浙江省杭州市拱墅区测试路 1 号";
        value.clientRequestId = REQUEST_ID;
        return value;
    }
}
