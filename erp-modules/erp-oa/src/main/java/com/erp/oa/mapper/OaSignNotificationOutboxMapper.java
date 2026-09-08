package com.erp.oa.mapper;

import java.util.Date;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.erp.oa.domain.OaSignNotificationOutbox;
import com.erp.oa.domain.vo.OaSignNotificationFailure;
import com.erp.oa.domain.vo.OaSignReminderCandidate;

public interface OaSignNotificationOutboxMapper
{
    int insertOaSignNotificationOutbox(OaSignNotificationOutbox outbox);

    OaSignNotificationOutbox selectByBusinessKey(@Param("channel") String channel,
            @Param("recipientUserId") Long recipientUserId,
            @Param("businessKey") String businessKey);

    List<OaSignNotificationOutbox> selectDueNotifications(@Param("dueTime") Date dueTime,
            @Param("staleSendingBefore") Date staleSendingBefore,
            @Param("limit") int limit);

    int claimForSending(@Param("outboxId") Long outboxId,
            @Param("expectedStatus") String expectedStatus,
            @Param("version") Long version);

    int markSent(@Param("outboxId") Long outboxId,
            @Param("version") Long version,
            @Param("lastResult") String lastResult);

    int markRetry(@Param("outboxId") Long outboxId,
            @Param("version") Long version,
            @Param("retryCount") Integer retryCount,
            @Param("nextRetryTime") Date nextRetryTime,
            @Param("lastError") String lastError);

    int markDead(@Param("outboxId") Long outboxId,
            @Param("version") Long version,
            @Param("lastError") String lastError);

    List<OaSignNotificationOutbox> selectDeadNotificationsForHr(
            @Param("hrUserId") Long hrUserId,
            @Param("scopeDeptIds") List<Long> scopeDeptIds,
            @Param("limit") int limit);

    List<OaSignNotificationFailure> selectNotificationFailuresForHr(
            @Param("hrUserId") Long hrUserId,
            @Param("scopeDeptIds") List<Long> scopeDeptIds,
            @Param("limit") int limit);

    List<OaSignNotificationOutbox> selectNotificationsForTaskBusinessKey(
            @Param("hrUserId") Long hrUserId,
            @Param("taskId") Long taskId,
            @Param("businessKey") String businessKey);

    OaSignNotificationOutbox selectLatestNotificationForTask(@Param("taskId") Long taskId);

    Long lockCurrentReminderCandidate(@Param("candidate") OaSignReminderCandidate candidate,
            @Param("enqueueTime") Date enqueueTime);

    int requeueDead(@Param("outboxId") Long outboxId,
            @Param("version") Long version,
            @Param("taskId") Long taskId,
            @Param("hrUserId") Long hrUserId);

    int deleteByTaskId(Long taskId);
}
