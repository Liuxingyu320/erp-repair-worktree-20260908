package com.erp.system.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.erp.common.core.exception.ServiceException;
import com.erp.system.domain.SysHrSignEventOutbox;
import com.erp.system.mapper.SysHrSignEventOutboxMapper;

@ExtendWith(MockitoExtension.class)
class HrSignEventOutboxServiceTest
{
    private static final String CONFLICT_MESSAGE = "签约事件发件箱状态已变化，请等待下一轮调度";

    @Mock
    private SysHrSignEventOutboxMapper mapper;

    private HrSignEventOutboxService service;

    @BeforeEach
    void setUp()
    {
        service = new HrSignEventOutboxService(mapper);
    }

    @Test
    void selectDueLimitsBatchToOneHundred()
    {
        Date due = new Date(1_000L);
        Date stale = new Date(500L);
        when(mapper.selectDueOutboxes(due, stale, 100)).thenReturn(List.of());

        assertThat(service.selectDue(due, stale, 500)).isEmpty();

        verify(mapper).selectDueOutboxes(due, stale, 100);
    }

    @Test
    void selectDueRejectsMissingSchedulerTimes()
    {
        assertThatThrownBy(() -> service.selectDue(null, new Date(), 100))
                .isInstanceOf(ServiceException.class)
                .hasMessage("签约事件调度时间不能为空");
        verify(mapper, never()).selectDueOutboxes(any(), any(), eq(100));
    }

    @Test
    void claimCommitsOptimisticVersionLocally()
    {
        SysHrSignEventOutbox row = row("RETRY", 4L);
        when(mapper.claimForSending(11L, "RETRY", 4L)).thenReturn(1);

        assertThat(service.claim(row)).isTrue();

        assertThat(row.getStatus()).isEqualTo("SENDING");
        assertThat(row.getVersion()).isEqualTo(5L);
    }

    @Test
    void failedClaimDoesNotChangeLocalRow()
    {
        SysHrSignEventOutbox row = row("PENDING", 0L);
        when(mapper.claimForSending(11L, "PENDING", 0L)).thenReturn(0);

        assertThat(service.claim(row)).isFalse();

        assertThat(row.getStatus()).isEqualTo("PENDING");
        assertThat(row.getVersion()).isZero();
    }

    @Test
    void markSentUsesClaimedVersionAndSynchronizesLocalRow()
    {
        SysHrSignEventOutbox row = row("SENDING", 5L);
        when(mapper.markSent(11L, 5L, 91L, 200)).thenReturn(1);

        service.markSent(row, 91L, 200);

        assertThat(row.getStatus()).isEqualTo("SENT");
        assertThat(row.getVersion()).isEqualTo(6L);
        assertThat(row.getRemoteTaskId()).isEqualTo(91L);
        assertThat(row.getLastHttpStatus()).isEqualTo(200);
        assertThat(row.getLastError()).isNull();
        assertThat(row.getNextRetryTime()).isNull();
    }

    @Test
    void markRetryNeverSilentlyLosesAStateTransition()
    {
        Date nextRetry = new Date(60_000L);
        SysHrSignEventOutbox row = row("SENDING", 5L);
        when(mapper.markRetry(11L, 5L, 1, nextRetry, 503,
                "REMOTE_HTTP_503")).thenReturn(0);

        assertThatThrownBy(() -> service.markRetry(row, 1, nextRetry,
                503, "REMOTE_HTTP_503"))
                .isInstanceOf(ServiceException.class)
                .hasMessage(CONFLICT_MESSAGE);

        assertThat(row.getStatus()).isEqualTo("SENDING");
        assertThat(row.getVersion()).isEqualTo(5L);
    }

    @Test
    void markRetryTruncatesErrorAndSynchronizesLocalRow()
    {
        Date nextRetry = new Date(60_000L);
        SysHrSignEventOutbox row = row("SENDING", 5L);
        String longError = "X".repeat(1_200);
        String truncated = "X".repeat(1_000);
        when(mapper.markRetry(11L, 5L, 2, nextRetry, 503, truncated))
                .thenReturn(1);

        service.markRetry(row, 2, nextRetry, 503, longError);

        assertThat(row.getStatus()).isEqualTo("RETRY");
        assertThat(row.getVersion()).isEqualTo(6L);
        assertThat(row.getRetryCount()).isEqualTo(2);
        assertThat(row.getNextRetryTime()).isEqualTo(nextRetry);
        assertThat(row.getLastHttpStatus()).isEqualTo(503);
        assertThat(row.getLastError()).isEqualTo(truncated);
    }

    @Test
    void markDeadUsesClaimedVersionAndSynchronizesLocalRow()
    {
        SysHrSignEventOutbox row = row("SENDING", 9L);
        row.setNextRetryTime(new Date());
        when(mapper.markDead(11L, 9L, 422, "REMOTE_HTTP_422"))
                .thenReturn(1);

        service.markDead(row, 422, "REMOTE_HTTP_422");

        assertThat(row.getStatus()).isEqualTo("DEAD");
        assertThat(row.getVersion()).isEqualTo(10L);
        assertThat(row.getNextRetryTime()).isNull();
        assertThat(row.getLastHttpStatus()).isEqualTo(422);
        assertThat(row.getLastError()).isEqualTo("REMOTE_HTTP_422");
    }

    @Test
    void allStateTransitionsUseRequiresNewTransactions() throws Exception
    {
        assertRequiresNew("claim", SysHrSignEventOutbox.class);
        assertRequiresNew("markSent", SysHrSignEventOutbox.class,
                Long.class, Integer.class);
        assertRequiresNew("markRetry", SysHrSignEventOutbox.class,
                Integer.class, Date.class, Integer.class, String.class);
        assertRequiresNew("markDead", SysHrSignEventOutbox.class,
                Integer.class, String.class);
    }

    private void assertRequiresNew(String name, Class<?>... parameterTypes)
            throws Exception
    {
        Method method = HrSignEventOutboxService.class.getMethod(name,
                parameterTypes);
        Transactional tx = method.getAnnotation(Transactional.class);
        assertThat(tx).isNotNull();
        assertThat(tx.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
    }

    private SysHrSignEventOutbox row(String status, Long version)
    {
        SysHrSignEventOutbox row = new SysHrSignEventOutbox();
        row.setOutboxId(11L);
        row.setStatus(status);
        row.setVersion(version);
        row.setRetryCount(0);
        return row;
    }
}
