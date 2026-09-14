package com.erp.system.service.support;

import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import com.erp.common.security.service.TokenService;
import com.erp.common.security.service.SessionRetentionDigest;
import com.erp.system.domain.SecuritySessionInvalidationOutbox;
import com.erp.system.mapper.SecuritySessionInvalidationOutboxMapper;
import org.junit.jupiter.api.Test;

class SecuritySessionInvalidationOutboxDispatcherTest
{
    final SecuritySessionInvalidationOutboxMapper mapper = mock(SecuritySessionInvalidationOutboxMapper.class);
    final TokenService tokens = mock(TokenService.class);
    final SecuritySessionInvalidationOutboxDispatcher dispatcher = new SecuritySessionInvalidationOutboxDispatcher(mapper, tokens);
    final String digest = SessionRetentionDigest.fromUserKey(42L, "current-session");

    @Test void passwordChangeReplayUsesPersistedDigest()
    {
        dispatcher.dispatchOne(row(UserSessionInvalidationService.PASSWORD_CHANGED, digest));
        verify(tokens).invalidateUserSessionsExceptDigest(42L, digest);
        verify(tokens, never()).invalidateUserSessions(anyLong()); verify(mapper).markDone("event-1");
    }
    @Test void legacyNullDigestRetainsInvalidateAllBehavior()
    {
        dispatcher.dispatchOne(row(UserSessionInvalidationService.PASSWORD_CHANGED, null));
        verify(tokens).invalidateUserSessions(42L); verify(tokens, never()).invalidateUserSessionsExceptDigest(any(), any());
    }
    @Test void nonPasswordChangeReplayIgnoresEvenPersistedRetention()
    {
        dispatcher.dispatchOne(row(UserSessionInvalidationService.USER_DISABLED, digest));
        verify(tokens).invalidateUserSessions(42L); verify(tokens, never()).invalidateUserSessionsExceptDigest(any(), any());
    }
    @Test void failedReplayIsNotMarkedDoneOrLoggedWithTheRawMessage()
    {
        doThrow(new IllegalArgumentException("sensitive-error-content")).when(tokens).invalidateUserSessionsExceptDigest(42L, digest);
        dispatcher.dispatchOne(row(UserSessionInvalidationService.PASSWORD_CHANGED, digest));
        verify(mapper, never()).markDone(any());
        verify(mapper).releaseForRetry(eq("event-1"), any(), eq("IllegalArgumentException"), eq(false));
    }
    @Test void immediateListenerAlsoForbidsRetentionForAdministrativeEvents()
    {
        new UserSecurityStateChangedListener(tokens, mapper).onSecurityStateChanged(
                new UserSecurityStateChangedEvent("event-1", 42L, UserSessionInvalidationService.PASSWORD_RESET, "current-session"));
        verify(tokens).invalidateUserSessions(42L, null);
    }
    private SecuritySessionInvalidationOutbox row(String reason, String retained)
    {
        var row = new SecuritySessionInvalidationOutbox(); row.setEventId("event-1"); row.setUserId(42L);
        row.setReasonCode(reason); row.setRetainedSessionDigest(retained); return row;
    }
}
