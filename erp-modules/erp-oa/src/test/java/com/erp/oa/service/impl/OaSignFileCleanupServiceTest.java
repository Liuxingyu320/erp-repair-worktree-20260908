package com.erp.oa.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.alibaba.fastjson2.JSON;
import com.erp.oa.domain.OaSignFileCleanup;
import com.erp.oa.mapper.OaSignFileCleanupMapper;

@DisplayName("签约文件清理台账服务")
class OaSignFileCleanupServiceTest
{
    private OaSignFileCleanupMapper mapper;
    private OaSignFileStorageService storageService;
    private OaSignFileCleanupService service;

    @BeforeEach
    void setUp()
    {
        mapper = mock(OaSignFileCleanupMapper.class);
        storageService = mock(OaSignFileStorageService.class);
        service = new OaSignFileCleanupService(mapper, storageService);
    }

    @Test
    @DisplayName("受管目录在删除事务内被持久化为可重试指令")
    void shouldPersistOneDeterministicCleanupInstruction()
    {
        when(mapper.insertCleanup(any())).thenAnswer(invocation -> {
            OaSignFileCleanup cleanup = invocation.getArgument(0);
            cleanup.setCleanupId(700L);
            return 1;
        });

        Long cleanupId = service.enqueue(9L, 90L, List.of("/managed/a.pdf", "/managed/a.pdf"));

        assertThat(cleanupId).isEqualTo(700L);
        ArgumentCaptor<OaSignFileCleanup> captor = ArgumentCaptor.forClass(OaSignFileCleanup.class);
        verify(mapper).insertCleanup(captor.capture());
        OaSignFileCleanup row = captor.getValue();
        assertThat(row.getTaskId()).isEqualTo(9L);
        assertThat(row.getPackageId()).isEqualTo(90L);
        assertThat(row.getStatus()).isEqualTo("PENDING");
        assertThat(row.getRetryCount()).isZero();
        assertThat(JSON.parseArray(row.getFileReferencesJson(), String.class))
                .containsExactly("/managed/a.pdf");
    }

    @Test
    @DisplayName("台账入队强制加入数据库硬删除事务")
    void shouldRequireTheHardDeleteTransaction() throws Exception
    {
        Method method = OaSignFileCleanupService.class.getMethod(
                "enqueue", Long.class, Long.class, java.util.Collection.class);
        Transactional transactional = method.getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.propagation()).isEqualTo(Propagation.MANDATORY);
        assertThat(transactional.rollbackFor()).contains(Exception.class);
    }

    @Test
    @DisplayName("清理成功与重试都使用乐观锁状态迁移")
    void shouldAdvanceOptimisticStateTransitions()
    {
        OaSignFileCleanup cleanup = cleanup();
        Date now = new Date(1_000L);
        Date leaseExpires = new Date(3_601_000L);
        when(mapper.claimForProcessing(700L, 0L, now, "worker-1", leaseExpires)).thenReturn(1);
        when(mapper.markRetry(700L, 1L, "worker-1", 1,
                new Date(61_000L), "ServiceException"))
                .thenReturn(1);

        assertThat(service.claim(cleanup, now, "worker-1", leaseExpires)).isTrue();
        service.markRetry(cleanup, 1, new Date(61_000L), "ServiceException");

        assertThat(cleanup.getStatus()).isEqualTo("RETRY");
        assertThat(cleanup.getRetryCount()).isEqualTo(1);
        assertThat(cleanup.getProcessingToken()).isNull();
        assertThat(cleanup.getLeaseExpiresTime()).isNull();
        assertThat(cleanup.getVersion()).isEqualTo(2L);
    }

    @Test
    @DisplayName("处理台账时仍由存储服务重新校验并精确删除")
    void shouldDelegateIdempotentManagedDirectoryDeletion()
    {
        OaSignFileCleanup cleanup = cleanup();
        cleanup.setFileReferencesJson("[\"/profile/private/sign-package/task-9/package-90/a.pdf\"]");

        service.deleteManagedFiles(cleanup);

        verify(storageService).deleteManagedPackageFiles(9L, 90L,
                List.of("/profile/private/sign-package/task-9/package-90/a.pdf"));
    }

    private static OaSignFileCleanup cleanup()
    {
        OaSignFileCleanup cleanup = new OaSignFileCleanup();
        cleanup.setCleanupId(700L);
        cleanup.setTaskId(9L);
        cleanup.setPackageId(90L);
        cleanup.setFileReferencesJson("[]");
        cleanup.setStatus("PENDING");
        cleanup.setRetryCount(0);
        cleanup.setVersion(0L);
        return cleanup;
    }
}
