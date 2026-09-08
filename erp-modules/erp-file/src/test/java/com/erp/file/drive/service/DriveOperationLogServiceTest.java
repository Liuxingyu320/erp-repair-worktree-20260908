package com.erp.file.drive.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import com.erp.file.drive.constant.DriveConstants;
import com.erp.file.drive.domain.DriveActor;
import com.erp.file.drive.domain.DriveAuditContext;
import com.erp.file.drive.domain.DriveNode;
import com.erp.file.drive.domain.DriveOperationLog;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@DisplayName("云盘操作审计")
class DriveOperationLogServiceTest
{
    @AfterEach
    void cleanThreadLocals()
    {
        RequestContextHolder.resetRequestAttributes();
        if (TransactionSynchronizationManager.isSynchronizationActive())
        {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    @DisplayName("审计上下文只捕获经校验的请求摘要并清洗转发地址和 User-Agent")
    void shouldCaptureOnlySafeRequestContext()
    {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Request-Id", "request_20260712-1");
        request.addHeader("X-Forwarded-For", "evil.example, 10.0.0.1");
        request.addHeader("User-Agent", "client\u0000" + "x".repeat(600));
        request.addHeader("Authorization", "Bearer must-not-leak");
        request.addHeader("storageKey", "private/key");
        request.setRemoteAddr("127.0.0.1");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        DriveOperationLogService service = new DriveOperationLogService(
                mock(DriveOperationLogPersistence.class));

        DriveAuditContext context = service.captureContext(actor());

        assertThat(context.requestId()).isEqualTo("request_20260712-1");
        assertThat(context.ipAddress()).isEqualTo("127.0.0.1");
        assertThat(context.userAgent()).hasSize(500).doesNotContain("\u0000");
        assertThat(context.toString()).doesNotContain(
                "must-not-leak", "private/key", "Authorization", "storageKey");
    }

    @Test
    @DisplayName("非法请求 ID 被替换且审计摘要截断并拒绝敏感字段")
    void shouldGenerateRequestIdAndSanitizeSummaries()
    {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Request-Id", "bad request id / token");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        DriveOperationLogPersistence persistence = mock(DriveOperationLogPersistence.class);
        DriveOperationLogService service = new DriveOperationLogService(persistence);

        service.success(DriveConstants.ACTION_UPLOAD, actor(), node(),
                "a".repeat(1200), "storageKey=private/key");

        ArgumentCaptor<DriveOperationLog> log = ArgumentCaptor.forClass(DriveOperationLog.class);
        verify(persistence).insert(log.capture());
        assertThat(log.getValue().getRequestId()).matches("[0-9a-f-]{36}");
        assertThat(log.getValue().getBeforeSummary()).hasSize(1000);
        assertThat(log.getValue().getAfterSummary()).isEqualTo("[REDACTED]");
    }

    @Test
    @DisplayName("审计写入失败不改变已完成业务结果")
    void shouldSwallowAuditPersistenceFailure()
    {
        DriveOperationLogPersistence persistence = mock(DriveOperationLogPersistence.class);
        when(persistence.insert(any())).thenThrow(new IllegalStateException("audit db unavailable"));
        DriveOperationLogService service = new DriveOperationLogService(persistence);

        assertThatCode(() -> service.success(
                DriveConstants.ACTION_UPLOAD, actor(), node(), null, "报告.pdf"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("事务中的成功日志仅在提交后插入一次")
    void shouldInsertSuccessOnlyAfterCommit()
    {
        DriveOperationLogPersistence persistence = mock(DriveOperationLogPersistence.class);
        DriveOperationLogService service = new DriveOperationLogService(persistence);
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);

        service.success(DriveConstants.ACTION_CREATE_FOLDER, actor(), node(), null, "月报");

        verify(persistence, never()).insert(any());
        List<TransactionSynchronization> synchronizations =
                TransactionSynchronizationManager.getSynchronizations();
        assertThat(synchronizations).hasSize(1);
        synchronizations.get(0).afterCommit();
        verify(persistence).insert(any());
    }

    @Test
    @DisplayName("事务回滚时不写成功日志")
    void shouldDropSuccessAuditOnRollback()
    {
        DriveOperationLogPersistence persistence = mock(DriveOperationLogPersistence.class);
        DriveOperationLogService service = new DriveOperationLogService(persistence);
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);

        service.success(DriveConstants.ACTION_CREATE_FOLDER, actor(), node(), null, "月报");
        for (TransactionSynchronization synchronization :
                TransactionSynchronizationManager.getSynchronizations())
        {
            synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        }

        verify(persistence, never()).insert(any());
    }

    @Test
    @DisplayName("异步清理使用已捕获上下文且不依赖请求线程")
    void shouldWriteWithCapturedAsyncContext()
    {
        DriveOperationLogPersistence persistence = mock(DriveOperationLogPersistence.class);
        DriveOperationLogService service = new DriveOperationLogService(persistence);
        DriveAuditContext context = new DriveAuditContext(
                0L, null, "system", "cleanup-8", "", "drive-cleanup");

        service.success(DriveConstants.ACTION_CLEANUP, context, node(),
                null, "count=2;bytes=12");

        ArgumentCaptor<DriveOperationLog> operation =
                ArgumentCaptor.forClass(DriveOperationLog.class);
        verify(persistence).insert(operation.capture());
        assertThat(operation.getValue().getOperatorUserId()).isZero();
        assertThat(operation.getValue().getOperatorName()).isEqualTo("system");
        assertThat(operation.getValue().getRequestId()).isEqualTo("cleanup-8");
    }

    private static DriveActor actor()
    {
        return new DriveActor(20L, 8L, "财务部", "alice",
                Set.of(DriveConstants.PERMISSION_ACCESS), false);
    }

    private static DriveNode node()
    {
        DriveNode node = new DriveNode();
        node.setNodeId(22L);
        node.setSpaceId(4L);
        node.setNodeType(DriveConstants.NODE_FILE);
        node.setNodeName("报告.pdf");
        return node;
    }
}
