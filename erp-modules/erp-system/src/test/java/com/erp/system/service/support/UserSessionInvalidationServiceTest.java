package com.erp.system.service.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import com.erp.common.security.service.SessionRetentionDigest;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import com.erp.common.core.exception.ServiceException;
import com.erp.system.domain.SecuritySessionInvalidationOutbox;
import com.erp.system.mapper.SecuritySessionInvalidationOutboxMapper;

class UserSessionInvalidationServiceTest
{
    @Test
    void persistsOutboxBeforePublishingEvent()
    {
        SecuritySessionInvalidationOutboxMapper mapper = mock(SecuritySessionInvalidationOutboxMapper.class);
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        when(mapper.insert(any(SecuritySessionInvalidationOutbox.class))).thenReturn(1);
        UserSessionInvalidationService service = new UserSessionInvalidationService(mapper, publisher);

        String eventId = service.record(9L, UserSessionInvalidationService.PASSWORD_RESET);

        assertEquals(32, eventId.length());
        verify(mapper).insert(any(SecuritySessionInvalidationOutbox.class));
        verify(publisher).publishEvent(any(UserSecurityStateChangedEvent.class));
    }

    @Test
    void rejectsUnclassifiedReason()
    {
        UserSessionInvalidationService service = new UserSessionInvalidationService(
                mock(SecuritySessionInvalidationOutboxMapper.class),
                mock(ApplicationEventPublisher.class));
        assertThrows(ServiceException.class, () -> service.record(9L, "FREE_TEXT"));
    }

    @Test
    void recordAllDeduplicatesUsersBeforeWritingOutbox()
    {
        SecuritySessionInvalidationOutboxMapper mapper = mock(SecuritySessionInvalidationOutboxMapper.class);
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        when(mapper.insert(any(SecuritySessionInvalidationOutbox.class))).thenReturn(1);
        UserSessionInvalidationService service = new UserSessionInvalidationService(mapper, publisher);

        service.recordAll(Arrays.asList(9L, 9L, null, 10L),
                UserSessionInvalidationService.ROLE_GRANTS_CHANGED);

        verify(mapper, times(2)).insert(any(SecuritySessionInvalidationOutbox.class));
        verify(publisher, times(2)).publishEvent(any(UserSecurityStateChangedEvent.class));
    }
    @Test
    void selfPasswordChangePersistsOnlyDigestBeforePublishingMemoryOnlySession()
    {
        var mapper = mock(SecuritySessionInvalidationOutboxMapper.class);
        var publisher = mock(ApplicationEventPublisher.class);
        when(mapper.insert(any())).thenReturn(1);
        new UserSessionInvalidationService(mapper, publisher).record(42L,
                UserSessionInvalidationService.PASSWORD_CHANGED, "internal-session-42");
        var row = ArgumentCaptor.forClass(SecuritySessionInvalidationOutbox.class);
        var event = ArgumentCaptor.forClass(UserSecurityStateChangedEvent.class);
        var ordered = org.mockito.Mockito.inOrder(mapper, publisher);
        ordered.verify(mapper).insert(row.capture());
        ordered.verify(publisher).publishEvent(event.capture());
        assertEquals(SessionRetentionDigest.fromUserKey(42L, "internal-session-42"), row.getValue().getRetainedSessionDigest());
        assertFalse(com.alibaba.fastjson2.JSON.toJSONString(row.getValue()).contains("internal-session-42"));
        assertEquals("internal-session-42", event.getValue().getRetainedToken());
    }

    @Test
    void administrativeReasonCannotRetainSessionEvenWhenCallerPassesOne()
    {
        var mapper = mock(SecuritySessionInvalidationOutboxMapper.class);
        var publisher = mock(ApplicationEventPublisher.class);
        when(mapper.insert(any())).thenReturn(1);
        new UserSessionInvalidationService(mapper, publisher).record(42L,
                UserSessionInvalidationService.PASSWORD_RESET, "internal-session-42");
        var row = ArgumentCaptor.forClass(SecuritySessionInvalidationOutbox.class);
        var event = ArgumentCaptor.forClass(UserSecurityStateChangedEvent.class);
        verify(mapper).insert(row.capture()); verify(publisher).publishEvent(event.capture());
        assertNull(row.getValue().getRetainedSessionDigest()); assertNull(event.getValue().getRetainedToken());
    }

}
