package com.erp.system.service.support;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import com.erp.common.core.exception.ServiceException;
import com.erp.system.domain.SecuritySessionInvalidationOutbox;
import com.erp.system.mapper.SecuritySessionInvalidationOutboxMapper;

@Service
public class UserSessionInvalidationService
{
    public static final String PASSWORD_RESET = "PASSWORD_RESET";
    public static final String PASSWORD_CHANGED = "PASSWORD_CHANGED";
    public static final String USER_DISABLED = "USER_DISABLED";
    public static final String USER_DELETED = "USER_DELETED";
    public static final String USER_ROLES_CHANGED = "USER_ROLES_CHANGED";
    public static final String ROLE_GRANTS_CHANGED = "ROLE_GRANTS_CHANGED";
    public static final String MENU_GRANTS_CHANGED = "MENU_GRANTS_CHANGED";
    public static final String LEGACY_CREDENTIAL_ROTATED = "LEGACY_CREDENTIAL_ROTATED";

    private static final Set<String> ALLOWED_REASONS = Set.of(
            PASSWORD_RESET, PASSWORD_CHANGED, USER_DISABLED, USER_DELETED,
            USER_ROLES_CHANGED, ROLE_GRANTS_CHANGED, MENU_GRANTS_CHANGED,
            LEGACY_CREDENTIAL_ROTATED);
    private static final Pattern EVENT_ID = Pattern.compile("[a-f0-9]{32}");

    private final SecuritySessionInvalidationOutboxMapper mapper;
    private final ApplicationEventPublisher publisher;

    public UserSessionInvalidationService(SecuritySessionInvalidationOutboxMapper mapper,
            ApplicationEventPublisher publisher)
    {
        this.mapper = mapper;
        this.publisher = publisher;
    }

    public String record(Long userId, String reasonCode)
    {
        return record(userId, reasonCode, null);
    }

    public String record(Long userId, String reasonCode, String retainedToken)
    {
        if (userId == null || userId <= 0 || !ALLOWED_REASONS.contains(reasonCode))
        {
            throw new ServiceException("无效的安全会话失效事件");
        }
        String eventId = UUID.randomUUID().toString().replace("-", "");
        if (!EVENT_ID.matcher(eventId).matches())
        {
            throw new IllegalStateException("invalid security event id");
        }
        SecuritySessionInvalidationOutbox outbox = new SecuritySessionInvalidationOutbox();
        outbox.setEventId(eventId);
        outbox.setUserId(userId);
        outbox.setReasonCode(reasonCode);
        if (mapper.insert(outbox) != 1)
        {
            throw new ServiceException("安全会话失效任务写入失败");
        }
        publisher.publishEvent(new UserSecurityStateChangedEvent(eventId, userId,
                reasonCode, retainedToken));
        return eventId;
    }

    public void recordAll(Iterable<Long> userIds, String reasonCode)
    {
        if (userIds == null)
        {
            return;
        }
        Set<Long> distinctUserIds = new LinkedHashSet<>();
        for (Long userId : userIds)
        {
            if (userId != null)
            {
                distinctUserIds.add(userId);
            }
        }
        for (Long userId : distinctUserIds)
        {
            record(userId, reasonCode);
        }
    }
}
