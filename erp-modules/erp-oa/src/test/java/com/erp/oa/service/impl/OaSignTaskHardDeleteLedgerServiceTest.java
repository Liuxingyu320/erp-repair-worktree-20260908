package com.erp.oa.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.alibaba.fastjson2.JSON;
import com.erp.common.core.exception.ServiceException;
import com.erp.oa.domain.OaSignTaskHardDeleteOperation;
import com.erp.oa.domain.vo.OaSignTaskBatchDeleteItem;
import com.erp.oa.domain.vo.OaSignTaskBatchDeleteResult;
import com.erp.oa.mapper.OaSignTaskHardDeleteOperationMapper;

@DisplayName("签约任务硬删除持久幂等台账")
class OaSignTaskHardDeleteLedgerServiceTest
{
    private static final Instant NOW = Instant.parse("2026-07-20T08:00:00Z");
    private static final String HASH = "a".repeat(64);

    private OaSignTaskHardDeleteOperationMapper mapper;
    private OaSignTaskHardDeleteLedgerService service;

    @BeforeEach
    void setUp()
    {
        mapper = mock(OaSignTaskHardDeleteOperationMapper.class);
        service = new OaSignTaskHardDeleteLedgerService(mapper,
                Clock.fixed(NOW, ZoneOffset.UTC), 600L);
    }

    @Test
    @DisplayName("首次请求原子登记并持有自己的执行令牌")
    void shouldOwnTheAtomicRegistrationForANewRequest()
    {
        AtomicReference<OaSignTaskHardDeleteOperation> registered = new AtomicReference<>();
        when(mapper.register(any())).thenAnswer(invocation -> {
            OaSignTaskHardDeleteOperation candidate = invocation.getArgument(0);
            candidate.setOperationId(7001L);
            registered.set(candidate);
            return 1;
        });
        when(mapper.lockByRequestId("request-new")).thenAnswer(invocation -> registered.get());

        OaSignTaskHardDeleteLedgerService.Claim claim =
                service.claim("request-new", 1L, HASH, 2);

        assertThat(claim.completed()).isFalse();
        assertThat(claim.operation().getOperationId()).isEqualTo(7001L);
        assertThat(claim.operation().getStatus())
                .isEqualTo(OaSignTaskHardDeleteLedgerService.PROCESSING);
        assertThat(claim.operation().getClaimToken()).isNotBlank();
        assertThat(claim.operation().getLeaseExpiresTime())
                .isEqualTo(Date.from(NOW.plusSeconds(600)));
        assertThat(claim.result().getTotalCount()).isEqualTo(2);
        assertThat(claim.result().getItems()).isEmpty();
        verify(mapper, never()).claimForProcessing(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("同一管理员与同一载荷重放时返回已完成快照")
    void shouldReplayCompletedResultForTheSameCommand()
    {
        OaSignTaskBatchDeleteResult completed = result(1, item(9L, "DELETED"));
        OaSignTaskHardDeleteOperation persisted = operation(
                OaSignTaskHardDeleteLedgerService.COMPLETED, 1, 1, completed);
        when(mapper.register(any())).thenReturn(2);
        when(mapper.lockByRequestId("request-1")).thenReturn(persisted);

        OaSignTaskHardDeleteLedgerService.Claim claim =
                service.claim("request-1", 1L, HASH, 1);

        assertThat(claim.completed()).isTrue();
        assertThat(claim.result().getItems()).singleElement()
                .satisfies(value -> assertThat(value.getTaskId()).isEqualTo(9L));
        verify(mapper, never()).claimForProcessing(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("请求编号不能换管理员或换删除载荷")
    void shouldRejectRequestIdReuseWithAnotherAdministratorOrPayload()
    {
        OaSignTaskHardDeleteOperation persisted = operation(
                OaSignTaskHardDeleteLedgerService.COMPLETED, 1, 1,
                result(1, item(9L, "DELETED")));
        when(mapper.register(any())).thenReturn(2);
        when(mapper.lockByRequestId("request-1")).thenReturn(persisted);

        assertThatThrownBy(() -> service.claim("request-1", 2L, HASH, 1))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("其他管理员");
        assertThatThrownBy(() -> service.claim("request-1", 1L, "b".repeat(64), 1))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("不同删除内容");
    }

    @Test
    @DisplayName("未过期租约不允许并发进程重复执行")
    void shouldRejectConcurrentClaimWhileLeaseIsActive()
    {
        OaSignTaskHardDeleteOperation persisted = operation(
                OaSignTaskHardDeleteLedgerService.PROCESSING, 2, 0, result(2));
        persisted.setClaimToken("other-process");
        persisted.setLeaseExpiresTime(Date.from(NOW.plusSeconds(120)));
        when(mapper.register(any())).thenReturn(2);
        when(mapper.lockByRequestId("request-1")).thenReturn(persisted);

        assertThatThrownBy(() -> service.claim("request-1", 1L, HASH, 2))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("正在处理");
        verify(mapper, never()).claimForProcessing(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("过期租约可接管并保留已持久的部分结果")
    void shouldReclaimExpiredLeaseAndResumePersistedProgress()
    {
        OaSignTaskBatchDeleteResult partial = result(2, item(9L, "DELETED"));
        OaSignTaskHardDeleteOperation persisted = operation(
                OaSignTaskHardDeleteLedgerService.PROCESSING, 2, 1, partial);
        persisted.setClaimToken("crashed-process");
        persisted.setLeaseExpiresTime(Date.from(NOW.minusSeconds(1)));
        persisted.setVersion(5L);
        when(mapper.register(any())).thenReturn(2);
        when(mapper.lockByRequestId("request-1")).thenReturn(persisted);
        when(mapper.claimForProcessing(any(), any(), any(), any(), any())).thenReturn(1);

        OaSignTaskHardDeleteLedgerService.Claim claim =
                service.claim("request-1", 1L, HASH, 2);

        assertThat(claim.completed()).isFalse();
        assertThat(claim.operation().getProcessedCount()).isEqualTo(1);
        assertThat(claim.operation().getClaimToken()).isNotEqualTo("crashed-process");
        assertThat(claim.operation().getVersion()).isEqualTo(6L);
        assertThat(claim.result().getItems()).singleElement()
                .satisfies(value -> assertThat(value.getTaskId()).isEqualTo(9L));
        verify(mapper).claimForProcessing(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("进度与完成均使用所有权令牌和版本CAS")
    void shouldPersistContiguousProgressAndCompletionWithCas()
    {
        OaSignTaskHardDeleteOperation operation = operation(
                OaSignTaskHardDeleteLedgerService.PROCESSING, 1, 0, result(1));
        OaSignTaskBatchDeleteResult completed = result(1, item(9L, "DELETED"));
        when(mapper.saveProgress(any(), any(), any(), any(), any(), any(), any())).thenReturn(1);
        when(mapper.markCompleted(any(), any(), any(), any(), any())).thenReturn(1);

        service.recordProgressRequiresNew(operation, completed);
        service.complete(operation, completed);

        assertThat(operation.getProcessedCount()).isEqualTo(1);
        assertThat(operation.getStatus()).isEqualTo(OaSignTaskHardDeleteLedgerService.COMPLETED);
        assertThat(operation.getClaimToken()).isNull();
        assertThat(operation.getVersion()).isEqualTo(2L);
        verify(mapper).saveProgress(7000L, 0L, "claim-token", 0, 1,
                JSON.toJSONString(completed), Date.from(NOW.plusSeconds(600)));
        verify(mapper).markCompleted(7000L, 1L, "claim-token", 1,
                JSON.toJSONString(completed));
    }

    @Test
    @DisplayName("非预期中断可标记重试并释放执行租约")
    void shouldReleaseLeaseForRetryAfterFailure()
    {
        OaSignTaskHardDeleteOperation operation = operation(
                OaSignTaskHardDeleteLedgerService.PROCESSING, 1, 0, result(1));
        when(mapper.markRetry(any(), any(), any(), any())).thenReturn(1);

        service.markRetry(operation, new IllegalStateException("database unavailable"));

        assertThat(operation.getStatus()).isEqualTo(OaSignTaskHardDeleteLedgerService.RETRY);
        assertThat(operation.getClaimToken()).isNull();
        assertThat(operation.getLastError()).isEqualTo("IllegalStateException");
        verify(mapper).markRetry(7000L, 0L, "claim-token", "IllegalStateException");
    }

    private OaSignTaskHardDeleteOperation operation(String status, int totalCount,
            int processedCount, OaSignTaskBatchDeleteResult snapshot)
    {
        OaSignTaskHardDeleteOperation operation = new OaSignTaskHardDeleteOperation();
        operation.setOperationId(7000L);
        operation.setRequestId("request-1");
        operation.setAdministratorUserId(1L);
        operation.setPayloadHash(HASH);
        operation.setStatus(status);
        operation.setClaimToken(OaSignTaskHardDeleteLedgerService.COMPLETED.equals(status)
                ? null : "claim-token");
        operation.setLeaseExpiresTime(Date.from(NOW.plusSeconds(600)));
        operation.setTotalCount(totalCount);
        operation.setProcessedCount(processedCount);
        operation.setResultJson(JSON.toJSONString(snapshot));
        operation.setVersion(0L);
        return operation;
    }

    private OaSignTaskBatchDeleteResult result(int totalCount,
            OaSignTaskBatchDeleteItem... items)
    {
        OaSignTaskBatchDeleteResult result = new OaSignTaskBatchDeleteResult();
        result.setTotalCount(totalCount);
        result.setItems(java.util.List.of(items));
        int deleted = (int) java.util.Arrays.stream(items)
                .filter(value -> "DELETED".equals(value.getResult())).count();
        result.setDeletedCount(deleted);
        result.setFailedCount(items.length - deleted);
        return result;
    }

    private OaSignTaskBatchDeleteItem item(Long taskId, String result)
    {
        OaSignTaskBatchDeleteItem item = new OaSignTaskBatchDeleteItem();
        item.setTaskId(taskId);
        item.setResult(result);
        item.setCode(result);
        item.setMessage(result);
        return item;
    }
}
