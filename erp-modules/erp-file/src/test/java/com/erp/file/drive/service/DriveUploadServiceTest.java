package com.erp.file.drive.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.erp.file.drive.config.DriveProperties;
import com.erp.file.drive.constant.DriveConstants;
import com.erp.file.drive.constant.DriveErrorCodes;
import com.erp.file.drive.domain.DriveActor;
import com.erp.file.drive.domain.DriveAuditContext;
import com.erp.file.drive.domain.DriveCapacityConfig;
import com.erp.file.drive.domain.DriveNode;
import com.erp.file.drive.domain.DriveSpace;
import com.erp.file.drive.domain.DriveUploadReservation;
import com.erp.file.drive.domain.vo.DriveNodeVo;
import com.erp.file.drive.exception.DriveException;
import com.erp.file.drive.mapper.DriveNodeMapper;
import com.erp.file.drive.mapper.DriveUploadReservationMapper;
import com.erp.file.drive.metric.DriveMetrics;
import com.erp.file.drive.storage.DriveStorageProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.mock.web.MockMultipartFile;
import org.slf4j.LoggerFactory;

@DisplayName("云盘补偿上传")
class DriveUploadServiceTest
{
    private DriveStorageProvider storage;
    private DriveUploadPersistence persistence;
    private DriveNodeService nodeService;
    private DriveSpaceService spaceService;
    private DriveQuotaService quotaService;
    private DriveUploadReservationService reservationService;
    private DriveOperationLogService logService;
    private DriveMetrics metrics;
    private DriveUploadService service;
    private DriveActor actor;
    private DriveSpace space;

    @BeforeEach
    void setUp() throws Exception
    {
        storage = mock(DriveStorageProvider.class);
        persistence = mock(DriveUploadPersistence.class);
        nodeService = mock(DriveNodeService.class);
        spaceService = mock(DriveSpaceService.class);
        quotaService = mock(DriveQuotaService.class);
        reservationService = mock(DriveUploadReservationService.class);
        logService = mock(DriveOperationLogService.class);
        metrics = mock(DriveMetrics.class);
        service = new DriveUploadService(storage, persistence, nodeService, spaceService,
                quotaService, reservationService, new DriveFilePolicy(new DriveProperties()),
                new DriveNamePolicy(), logService, metrics);
        actor = new DriveActor(20L, 8L, "财务部", "alice",
                Set.of(DriveConstants.PERMISSION_ACCESS), false);
        space = personalSpace();
        when(spaceService.requireWritableSpace(4L, actor)).thenReturn(space);
        when(nodeService.requireParent(4L, 0L)).thenReturn(null);
        when(nodeService.availableFileName(4L, 0L, "报告.pdf")).thenReturn("报告.pdf");
        when(nodeService.toVo(any(), eq(actor))).thenReturn(nodeVo());
        when(reservationService.reserveBeforeStorage(eq(4L), any(), eq(12L), eq(actor)))
                .thenReturn("reservation-1");
        when(logService.captureContext(actor)).thenReturn(new DriveAuditContext(
                20L, 8L, "alice", "request-1", "127.0.0.1", "test"));
        doAnswer(invocation -> {
            try (InputStream input = invocation.getArgument(1))
            {
                input.readAllBytes();
            }
            return null;
        }).when(storage).put(any(), any());
    }

    @Test
    @DisplayName("快照额度已不足时不写存储也不进持久化事务")
    void shouldFailPreflightBeforeStorage() throws Exception
    {
        doThrow(new DriveException(DriveErrorCodes.DRIVE_QUOTA_EXCEEDED, "quota"))
                .when(quotaService).preflight(space, 12L);

        assertThatThrownBy(() -> service.upload(pdf(), 4L, 0L, actor))
                .extracting("businessCode")
                .isEqualTo(DriveErrorCodes.DRIVE_QUOTA_EXCEEDED);
        verifyNoInteractions(persistence);
        verify(metrics).recordUploadFailure(anyLong(),
                eq(DriveErrorCodes.DRIVE_QUOTA_EXCEEDED));
        verify(storage, never()).put(any(), any());
        verify(logService).failure(DriveConstants.ACTION_UPLOAD, actor,
                DriveErrorCodes.DRIVE_QUOTA_EXCEEDED);
    }

    @Test
    @DisplayName("对象存储失败时不调用数据库持久化")
    void shouldAvoidDatabaseWhenStorageFails() throws Exception
    {
        doThrow(new IOException("disk unavailable")).when(storage).put(any(), any());

        assertThatThrownBy(() -> service.upload(pdf(), 4L, 0L, actor))
                .extracting("businessCode")
                .isEqualTo(DriveErrorCodes.DRIVE_STORAGE_UNAVAILABLE);
        verifyNoInteractions(persistence);
        verify(metrics).recordUploadFailure(anyLong(),
                eq(DriveErrorCodes.DRIVE_STORAGE_UNAVAILABLE));
    }

    @Test
    @DisplayName("数据库提交失败时删除已写对象并记录安全失败")
    void shouldCompensateObjectWhenPersistenceFails() throws Exception
    {
        when(storage.exists(any())).thenReturn(true, false);
        when(persistence.persist(any(), eq(12L), eq("reservation-1")))
                .thenThrow(new DataIntegrityViolationException("commit"));

        assertThatThrownBy(() -> service.upload(pdf(), 4L, 0L, actor))
                .isInstanceOf(DriveException.class)
                .extracting("businessCode")
                .isEqualTo(DriveErrorCodes.DRIVE_STORAGE_UNAVAILABLE);

        ArgumentCaptor<String> storageKey = ArgumentCaptor.forClass(String.class);
        verify(storage).delete(storageKey.capture());
        assertThat(storageKey.getValue()).matches("\\d{4}/\\d{2}/[0-9a-f-]+\\.pdf");
        verify(reservationService).settleAfterCompensation(
                "reservation-1", true, actor);
        verify(logService).failure(DriveConstants.ACTION_UPLOAD, actor,
                DriveErrorCodes.DRIVE_STORAGE_UNAVAILABLE);
    }

    @Test
    @DisplayName("活动名称唯一索引冲突只重试元数据并复用对象")
    void shouldRetryNamedConstraintWithoutRewritingObject() throws Exception
    {
        DriveNode saved = fileNode("报告 (1).pdf");
        when(nodeService.availableFileName(4L, 0L, "报告.pdf"))
                .thenReturn("报告.pdf", "报告 (1).pdf");
        when(persistence.persist(any(), eq(12L), eq("reservation-1")))
                .thenThrow(new DuplicateKeyException("uk_drive_node_active_name"))
                .thenReturn(saved);

        DriveNodeVo result = service.upload(pdf(), 4L, 0L, actor);

        assertThat(result.nodeId()).isEqualTo(77L);
        verify(persistence, times(2)).persist(any(), eq(12L), eq("reservation-1"));
        verify(storage).put(any(), any());
        verify(storage, never()).delete(any());
    }

    @Test
    @DisplayName("无关唯一键错误不被误分类为名称冲突")
    void shouldNotRetryUnrelatedDuplicateKey() throws Exception
    {
        when(storage.exists(any())).thenReturn(true, false);
        when(persistence.persist(any(), eq(12L), eq("reservation-1")))
                .thenThrow(new DuplicateKeyException("uk_other_constraint"));

        assertThatThrownBy(() -> service.upload(pdf(), 4L, 0L, actor))
                .extracting("businessCode")
                .isEqualTo(DriveErrorCodes.DRIVE_STORAGE_UNAVAILABLE);
        verify(persistence).persist(any(), eq(12L), eq("reservation-1"));
        verify(storage).delete(any());
    }

    @Test
    @DisplayName("成功上传记录内容 SHA-256 和一条成功审计")
    void shouldPersistDigestAndSuccessAudit() throws Exception
    {
        when(persistence.persist(any(), eq(12L), eq("reservation-1"))).thenAnswer(invocation -> {
            DriveNode node = invocation.getArgument(0);
            node.setNodeId(77L);
            return node;
        });

        service.upload(pdf(), 4L, 0L, actor);

        ArgumentCaptor<DriveNode> node = ArgumentCaptor.forClass(DriveNode.class);
        verify(persistence).persist(node.capture(), eq(12L), eq("reservation-1"));
        String expected = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest("pdf-content!".getBytes(StandardCharsets.UTF_8)));
        assertThat(node.getValue().getSha256()).isEqualTo(expected);
        assertThat(node.getValue().getStorageKey()).doesNotContain("报告");
        verify(logService).success(DriveConstants.ACTION_UPLOAD, actor,
                node.getValue(), null, "报告.pdf");
        verify(metrics).recordUploadSuccess(anyLong());
    }

    @Test
    @DisplayName("父目录在预检后进入回收站时事务拒绝并由外层补偿")
    void shouldCompensateWhenParentBecomesTrashed() throws Exception
    {
        DriveNode preflightParent = folder(8L, "ACTIVE", "0");
        when(nodeService.requireParent(4L, 8L)).thenReturn(preflightParent);
        when(nodeService.availableFileName(4L, 8L, "报告.pdf")).thenReturn("报告.pdf");
        when(storage.exists(any())).thenReturn(true, false);
        when(persistence.persist(any(), eq(12L), eq("reservation-1")))
                .thenThrow(new DriveException(DriveErrorCodes.DRIVE_NODE_NOT_FOUND, "parent"));

        assertThatThrownBy(() -> service.upload(pdf(), 4L, 8L, actor))
                .extracting("businessCode")
                .isEqualTo(DriveErrorCodes.DRIVE_NODE_NOT_FOUND);
        verify(storage).delete(any());
    }

    @Test
    @DisplayName("补偿删除连续失败时最多尝试三次且客户端错误不泄露键")
    void shouldBoundCompensationRetriesWithoutKeyLeak() throws Exception
    {
        when(storage.exists(any())).thenReturn(true);
        when(persistence.persist(any(), eq(12L), eq("reservation-1")))
                .thenThrow(new DataIntegrityViolationException("commit"));
        AtomicReference<String> attemptedKey = new AtomicReference<>();
        doAnswer(invocation -> {
            String key = invocation.getArgument(0);
            attemptedKey.set(key);
            throw new IOException("delete failed for " + key);
        }).when(storage).delete(any());
        Logger logger = (Logger) LoggerFactory.getLogger(DriveUploadService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        Throwable thrown;
        try
        {
            thrown = org.assertj.core.api.Assertions.catchThrowable(
                    () -> service.upload(pdf(), 4L, 0L, actor));
        }
        finally
        {
            logger.detachAppender(appender);
        }

        verify(storage, times(3)).delete(any());
        assertThat(thrown).isInstanceOf(DriveException.class);
        assertThat(thrown.getMessage()).doesNotContain("/", ".pdf");
        assertThat(appender.list).singleElement().satisfies(event -> {
            assertThat(event.getFormattedMessage()).contains("keyFingerprint=")
                    .doesNotContain(attemptedKey.get());
            if (event.getThrowableProxy() != null)
            {
                assertThat(event.getThrowableProxy().getMessage()).doesNotContain(attemptedKey.get());
            }
        });
    }

    @Test
    @DisplayName("持久化边界从锁定后的父目录重建祖先并在父目录失效时不占额度")
    void persistenceShouldRecheckLockedParent()
    {
        DriveNodeMapper mapper = mock(DriveNodeMapper.class);
        DriveQuotaService quota = mock(DriveQuotaService.class);
        DriveUploadReservationMapper reservations = mock(DriveUploadReservationMapper.class);
        DriveCapacityService capacity = mock(DriveCapacityService.class);
        DriveProperties properties = new DriveProperties();
        DriveUploadPersistence realPersistence = new DriveUploadPersistence(mapper, quota,
                reservations, capacity, properties);
        DriveNode movedParent = folder(8L, "ACTIVE", "0,6");
        when(mapper.selectByIdForUpdate(8L)).thenReturn(movedParent);
        when(mapper.insertNode(any())).thenReturn(1);
        DriveNode child = fileNode("报告.pdf");
        child.setParentId(8L);

        realPersistence.persist(child, 12L);

        assertThat(child.getAncestors()).isEqualTo("0,6,8");
        verify(quota).reserve(4L, 12L);

        movedParent.setStatus(DriveConstants.STATUS_TRASHED);
        assertThatThrownBy(() -> realPersistence.persist(fileNodeWithParent("新.pdf", 8L), 12L))
                .extracting("businessCode")
                .isEqualTo(DriveErrorCodes.DRIVE_NODE_NOT_FOUND);
        verify(quota, times(1)).reserve(4L, 12L);
    }

    @Test
    @DisplayName("持久化在同一事务将容量预占转为已提交用量")
    void persistenceShouldAtomicallyConsumeReservation()
    {
        DriveNodeMapper mapper = mock(DriveNodeMapper.class);
        DriveQuotaService quota = mock(DriveQuotaService.class);
        DriveUploadReservationMapper reservations = mock(DriveUploadReservationMapper.class);
        DriveCapacityService capacity = mock(DriveCapacityService.class);
        DriveProperties properties = new DriveProperties();
        properties.setCapacityReservationEnabled(true);
        DriveUploadPersistence realPersistence = new DriveUploadPersistence(mapper, quota,
                reservations, capacity, properties);
        DriveNode node = fileNode("report.pdf");
        node.setStorageKey("2026/07/object.pdf");
        DriveUploadReservation reservation = new DriveUploadReservation();
        reservation.setReservationId("reservation-1");
        reservation.setSpaceId(4L);
        reservation.setStorageKey(node.getStorageKey());
        reservation.setReservedBytes(12L);
        reservation.setStatus(DriveConstants.RESERVATION_RESERVED);
        reservation.setVersion(2);
        DriveCapacityConfig config = new DriveCapacityConfig();
        config.setEnforcementMode(DriveConstants.CAPACITY_BLOCK);
        when(capacity.lockForAllocationChange()).thenReturn(config);
        when(reservations.selectForUpdate("reservation-1")).thenReturn(reservation);
        when(mapper.insertNode(node)).thenReturn(1);
        when(reservations.deleteExpected("reservation-1", 2,
                DriveConstants.RESERVATION_RESERVED)).thenReturn(1);

        assertThat(realPersistence.persist(node, 12L, "reservation-1"))
                .isSameAs(node);

        verify(capacity).requireCurrentAccountedUsageWithinCapacity(config);
        verify(quota).reserve(4L, 12L);
        verify(reservations).deleteExpected("reservation-1", 2,
                DriveConstants.RESERVATION_RESERVED);
    }

    private static MockMultipartFile pdf()
    {
        return new MockMultipartFile("file", "报告.pdf", "application/pdf",
                "pdf-content!".getBytes(StandardCharsets.UTF_8));
    }

    private static DriveSpace personalSpace()
    {
        DriveSpace value = new DriveSpace();
        value.setSpaceId(4L);
        value.setSpaceType(DriveConstants.SPACE_PERSONAL);
        value.setOwnerUserId(20L);
        value.setSpaceName("我的文件");
        value.setQuotaBytes(1000L);
        value.setUsedBytes(0L);
        value.setStatus(DriveConstants.STATUS_ACTIVE);
        return value;
    }

    private static DriveNode fileNode(String name)
    {
        return fileNodeWithParent(name, 0L);
    }

    private static DriveNode fileNodeWithParent(String name, Long parentId)
    {
        DriveNode node = new DriveNode();
        node.setNodeId(77L);
        node.setSpaceId(4L);
        node.setParentId(parentId);
        node.setAncestors("0");
        node.setNodeType(DriveConstants.NODE_FILE);
        node.setNodeName(name);
        node.setNormalizedName(name.toLowerCase());
        node.setExtension("pdf");
        node.setContentType("application/pdf");
        node.setSizeBytes(12L);
        node.setStatus(DriveConstants.STATUS_ACTIVE);
        node.setActiveFlag(1);
        node.setVersion(0);
        return node;
    }

    private static DriveNode folder(Long nodeId, String status, String ancestors)
    {
        DriveNode node = new DriveNode();
        node.setNodeId(nodeId);
        node.setSpaceId(4L);
        node.setParentId(0L);
        node.setAncestors(ancestors);
        node.setNodeType(DriveConstants.NODE_FOLDER);
        node.setNodeName("目录");
        node.setStatus(status);
        return node;
    }

    private static DriveNodeVo nodeVo()
    {
        return new DriveNodeVo(77L, 4L, "我的文件", 0L,
                DriveConstants.NODE_FILE, "报告.pdf", "pdf", "application/pdf",
                12L, DriveConstants.STATUS_ACTIVE, 0, "alice", null, "alice", null,
                "/报告.pdf", List.of(), List.of(), true, true, true);
    }
}
