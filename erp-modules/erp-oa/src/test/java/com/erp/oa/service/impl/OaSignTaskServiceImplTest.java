package com.erp.oa.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import com.erp.common.core.context.SecurityContextHolder;
import com.erp.common.core.constant.SecurityConstants;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.core.exception.auth.NotPermissionException;
import com.erp.common.security.shop.ShopScopeService;
import com.erp.oa.constant.OaSignTaskStatus;
import com.erp.oa.domain.OaSignEvent;
import com.erp.oa.domain.OaSignNotificationOutbox;
import com.erp.oa.domain.OaSignPackage;
import com.erp.oa.domain.OaSignPackageDocument;
import com.erp.oa.domain.OaSignPlanVersion;
import com.erp.oa.domain.OaSignTask;
import com.erp.oa.domain.OaSignTaskEvent;
import com.erp.oa.domain.OaSignTaskHardDeleteOperation;
import com.erp.oa.domain.dto.OaSignTaskBatchDeleteAction;
import com.erp.oa.domain.dto.OaSignTaskConfirmRequest;
import com.erp.oa.domain.dto.OaSignTaskBatchDeleteRequest;
import com.erp.oa.domain.dto.OaSignTaskNotificationRetryRequest;
import com.erp.oa.domain.dto.OaSignTaskRetryRequest;
import com.erp.oa.domain.dto.OaSignExceptionResolutionRequest;
import com.erp.oa.domain.vo.OaSignTaskDetail;
import com.erp.oa.domain.vo.OaSignTaskBatchDeleteItem;
import com.erp.oa.domain.vo.OaSignTaskBatchDeleteResult;
import com.erp.oa.mapper.OaSignPlanVersionMapper;
import com.erp.oa.mapper.OaSignTaskEventMapper;
import com.erp.oa.mapper.OaSignTaskMapper;
import com.erp.oa.mapper.OaHrRenewalGuardMapper;
import com.erp.oa.service.IOaSignPackageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.erp.system.api.model.LoginUser;

@DisplayName("单HR签约任务服务")
class OaSignTaskServiceImplTest
{
    private OaSignTaskMapper taskMapper;
    private OaSignTaskEventMapper eventMapper;
    private OaSignTaskEventService eventService;
    private OaSignAutomationSettingsService settings;
    private IOaSignPackageService packageService;
    private ShopScopeService shopScopeService;
    private OaSignNotificationOutboxService notificationOutboxService;
    private OaSignConfirmationTokenService confirmationTokenService;
    private OaSignPlanVersionMapper planVersionMapper;
    private OaSignPackageLifecycleService packageLifecycleService;
    private OaHrRenewalGuardMapper renewalGuardMapper;
    private OaSignTaskWorkflowService workflowService;
    private OaSignTaskHardDeleteExecutor hardDeleteExecutor;
    private OaSignTaskHardDeleteLedgerService hardDeleteLedgerService;
    private OaSignTaskServiceImpl service;
    private OaSignTask task;
    private OaSignPackage signPackage;

    @BeforeEach
    void setUp()
    {
        taskMapper = mock(OaSignTaskMapper.class);
        eventMapper = mock(OaSignTaskEventMapper.class);
        eventService = mock(OaSignTaskEventService.class);
        settings = mock(OaSignAutomationSettingsService.class);
        packageService = mock(IOaSignPackageService.class);
        shopScopeService = mock(ShopScopeService.class);
        notificationOutboxService = mock(OaSignNotificationOutboxService.class);
        confirmationTokenService = mock(OaSignConfirmationTokenService.class);
        planVersionMapper = mock(OaSignPlanVersionMapper.class);
        packageLifecycleService = mock(OaSignPackageLifecycleService.class);
        renewalGuardMapper = mock(OaHrRenewalGuardMapper.class);
        hardDeleteExecutor = mock(OaSignTaskHardDeleteExecutor.class);
        hardDeleteLedgerService = mock(OaSignTaskHardDeleteLedgerService.class);
        when(hardDeleteLedgerService.appendResult(any(), any(), anyInt()))
                .thenAnswer(invocation -> appendDeleteResult(invocation.getArgument(0),
                        invocation.getArgument(1), invocation.getArgument(2)));
        OaSignPlanVersion planVersion = new OaSignPlanVersion();
        planVersion.setVersionId(55L);
        planVersion.setPublishStatus("PUBLISHED");
        planVersion.setMatchingStatus("ENABLED");
        planVersion.setScenario("ONBOARD");
        planVersion.setShopDeptId(1171L);
        planVersion.setLegalEntityId(12L);
        planVersion.setSignDeadlineDays(7);
        when(planVersionMapper.selectPlanVersionById(55L)).thenReturn(planVersion);
        when(taskMapper.updateSentLifecycle(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(1);
        workflowService = new OaSignTaskWorkflowService(taskMapper, eventService,
                packageService, notificationOutboxService, planVersionMapper,
                Clock.fixed(Instant.parse("2026-07-17T02:03:04Z"), ZoneId.of("Asia/Shanghai")));
        service = new OaSignTaskServiceImpl(taskMapper, eventMapper, eventService, settings,
                packageService, shopScopeService, workflowService, notificationOutboxService,
                confirmationTokenService, packageLifecycleService, renewalGuardMapper,
                planVersionMapper, hardDeleteExecutor, hardDeleteLedgerService);
        when(settings.resolveRequiredHrUserId()).thenReturn(101L);
        SecurityContextHolder.setUserId("101");
        SecurityContextHolder.setUserName("唯一HR");
        LoginUser businessUser = new LoginUser();
        businessUser.setPermissions(Set.of("oa:signTask:list"));
        SecurityContextHolder.set(SecurityConstants.LOGIN_USER, businessUser);

        task = task(OaSignTaskStatus.WAITING_HR_CONFIRM, 3L);
        signPackage = signPackage();
        when(taskMapper.selectOaSignTaskById(9L)).thenReturn(task);
        when(eventMapper.selectTaskEvents(9L)).thenReturn(Collections.emptyList());
        when(packageService.getPackageForVerification(90L, 1171L)).thenReturn(signPackage);
        when(confirmationTokenService.issue(any(), any(), any(), any()))
                .thenReturn("confirmation-token");
        when(shopScopeService.resolveScopeDeptIds(1171L)).thenReturn(List.of(1171L));
        when(eventService.transition(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    OaSignTask current = invocation.getArgument(0);
                    OaSignTaskStatus target = invocation.getArgument(1);
                    current.setStatus(target.name());
                    current.setVersion(current.getVersion() + 1);
                    return current;
                });
    }

    @Test
    @DisplayName("唯一HR可按业务事件重新入队失败通知且不修改合同状态")
    void shouldRequeueDeadNotificationWithoutChangingTaskStatus()
    {
        task.setStatus("PENDING_SIGN");
        OaSignTaskNotificationRetryRequest request = new OaSignTaskNotificationRetryRequest();
        request.setRequestId("retry-notification-9");
        request.setBusinessKey("SIGN_SENT:90:SP-90-V1");
        when(notificationOutboxService.requeueDead(task, request.getBusinessKey())).thenReturn(2);

        int result = service.retryNotification(9L, request, 1171L);

        assertThat(result).isEqualTo(2);
        assertThat(task.getStatus()).isEqualTo("PENDING_SIGN");
        verify(notificationOutboxService).requeueDead(task, "SIGN_SENT:90:SP-90-V1");
    }

    @Test
    @DisplayName("取消任务先锁续签占位再通过任务专用路径撤回签约包")
    void shouldCancelTaskAndPackageThroughBoundWorkflow()
    {
        task.setStatus(OaSignTaskStatus.PENDING_COMPANY.name());
        signPackage.setStatus("pending_company");
        when(packageService.getPackageDetail(90L, 1171L)).thenReturn(signPackage);
        OaSignTaskRetryRequest request = new OaSignTaskRetryRequest();
        request.setRequestId("cancel-9");
        request.setReasonCode("HR_CANCELLED");
        request.setReasonDetail("业务取消");

        OaSignTaskDetail detail = service.cancel(9L, request, 1171L,
                "127.0.0.1", "JUnit");

        assertThat(detail.getTask().getStatus()).isEqualTo(OaSignTaskStatus.CANCELLED.name());
        verify(eventService).lockRenewalGuardBeforeTerminal(task, OaSignTaskStatus.CANCELLED);
        verify(packageService).voidPackageForTask(90L, 9L, 1171L, "业务取消");
        verify(packageService, never()).voidPackage(any(), any(), any());
    }

    @Test
    @DisplayName("系统管理员可按角色权限重试历史任务通知")
    void shouldAllowNotificationRetryBySystemAdmin()
    {
        task.setStatus("PENDING_SIGN");
        SecurityContextHolder.setUserId("1");
        OaSignTaskNotificationRetryRequest request = new OaSignTaskNotificationRetryRequest();
        request.setRequestId("admin-retry-notification-9");
        request.setBusinessKey("SIGN_SENT:90:SP-90-V1");

        when(notificationOutboxService.requeueDead(task, request.getBusinessKey())).thenReturn(2);

        assertThat(service.retryNotification(9L, request, 1171L)).isEqualTo(2);
        verify(notificationOutboxService).requeueDead(task, request.getBusinessKey());
    }

    @Test
    @DisplayName("管理员批量删除按任务隔离成功和业务拒绝")
    void shouldIsolateEachAdministratorHardDeleteResult()
    {
        SecurityContextHolder.setUserId("1");
        OaSignTaskBatchDeleteRequest request = deleteRequest("hard-delete-1",
                deleteAction(9L, 3L), deleteAction(10L, 4L));
        OaSignTaskHardDeleteOperation operation = deleteOperation(2, 0);
        OaSignTaskBatchDeleteResult initial = deleteResult(2);
        OaSignTaskBatchDeleteResult first = appendDeleteResult(initial,
                deleteItem(9L, "DELETED", "DELETED", "已删除"), 2);
        OaSignTaskHardDeleteExecutor.DeleteCommit commit =
                new OaSignTaskHardDeleteExecutor.DeleteCommit(
                        9L, "ST-9", 90L, Set.of(), 700L, first);
        when(hardDeleteLedgerService.claim(eq("hard-delete-1"), eq(1L), any(), eq(2)))
                .thenReturn(new OaSignTaskHardDeleteLedgerService.Claim(operation, initial, false));
        when(hardDeleteExecutor.deleteDatabaseRecords(
                9L, 3L, 1171L, operation, initial, 2)).thenReturn(commit);
        when(hardDeleteExecutor.deleteDatabaseRecords(
                10L, 4L, 1171L, operation, first, 2)).thenThrow(
                new OaSignTaskHardDeleteExecutor.DeleteRejected(
                        "FINAL_TASK_PROTECTED", "已完成任务不允许删除"));

        OaSignTaskBatchDeleteResult result = service.hardDeleteUnfinishedTasks(request, 1171L);

        assertThat(result.getTotalCount()).isEqualTo(2);
        assertThat(result.getDeletedCount()).isEqualTo(1);
        assertThat(result.getFailedCount()).isEqualTo(1);
        assertThat(result.getItems()).extracting("taskId", "result", "code")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(9L, "DELETED", "DELETED"),
                        org.assertj.core.groups.Tuple.tuple(10L, "REJECTED", "FINAL_TASK_PROTECTED"));
        verify(hardDeleteExecutor).deleteCommittedFiles(commit);
        verify(hardDeleteLedgerService).recordProgressRequiresNew(eq(operation), any());
        verify(hardDeleteLedgerService).complete(eq(operation), any());
    }

    @Test
    @DisplayName("非系统管理员即使是签约经办人也不能硬删除任务")
    void shouldRejectHardDeleteByNonAdministrator()
    {
        OaSignTaskBatchDeleteRequest request = deleteRequest("hard-delete-denied",
                deleteAction(9L, 3L));

        assertThatThrownBy(() -> service.hardDeleteUnfinishedTasks(request, 1171L))
                .isInstanceOf(NotPermissionException.class);
        verify(hardDeleteExecutor, never()).deleteDatabaseRecords(
                any(), any(), any(), any(), any(), anyInt());
    }

    @Test
    @DisplayName("数据与清理台账已提交时文件暂未删除仍返回稳定成功结果")
    void shouldKeepStableSuccessWhenDurableFileCleanupIsQueued()
    {
        SecurityContextHolder.setUserId("1");
        OaSignTaskBatchDeleteRequest request = deleteRequest("hard-delete-file-failure",
                deleteAction(9L, 3L));
        OaSignTaskHardDeleteOperation operation = deleteOperation(1, 0);
        OaSignTaskBatchDeleteResult initial = deleteResult(1);
        OaSignTaskBatchDeleteResult completed = appendDeleteResult(initial,
                deleteItem(9L, "DELETED", "DELETED",
                        "数据已删除，文件由持久清理台账保证"), 1);
        OaSignTaskHardDeleteExecutor.DeleteCommit commit =
                new OaSignTaskHardDeleteExecutor.DeleteCommit(
                        9L, "ST-9", 90L, Set.of(), 700L, completed);
        when(hardDeleteLedgerService.claim(eq("hard-delete-file-failure"), eq(1L), any(), eq(1)))
                .thenReturn(new OaSignTaskHardDeleteLedgerService.Claim(operation, initial, false));
        when(hardDeleteExecutor.deleteDatabaseRecords(
                9L, 3L, 1171L, operation, initial, 1)).thenReturn(commit);
        doThrow(new ServiceException("磁盘不可用")).when(hardDeleteExecutor).deleteCommittedFiles(commit);

        OaSignTaskBatchDeleteResult result = service.hardDeleteUnfinishedTasks(request, 1171L);

        assertThat(result.getDeletedCount()).isEqualTo(1);
        assertThat(result.getFailedCount()).isZero();
        assertThat(result.getItems()).singleElement().satisfies(item -> {
            assertThat(item.getResult()).isEqualTo("DELETED");
            assertThat(item.getCode()).isEqualTo("DELETED");
            assertThat(item.getMessage()).contains("持久清理台账");
        });
        verify(hardDeleteLedgerService).complete(operation, completed);
    }

    @Test
    @DisplayName("已完成的同一请求直接返回持久结果且不再删除")
    void shouldReplayCompletedHardDeleteWithoutExecutingAgain()
    {
        SecurityContextHolder.setUserId("1");
        OaSignTaskBatchDeleteRequest request = deleteRequest("hard-delete-replay",
                deleteAction(9L, 3L));
        OaSignTaskHardDeleteOperation operation = deleteOperation(1, 1);
        operation.setStatus(OaSignTaskHardDeleteLedgerService.COMPLETED);
        OaSignTaskBatchDeleteResult persisted = appendDeleteResult(deleteResult(1),
                deleteItem(9L, "DELETED", "DELETED", "已删除"), 1);
        when(hardDeleteLedgerService.claim(eq("hard-delete-replay"), eq(1L), any(), eq(1)))
                .thenReturn(new OaSignTaskHardDeleteLedgerService.Claim(operation, persisted, true));

        OaSignTaskBatchDeleteResult replay = service.hardDeleteUnfinishedTasks(request, 1171L);

        assertThat(replay).isSameAs(persisted);
        verify(hardDeleteExecutor, never()).deleteDatabaseRecords(
                any(), any(), any(), any(), any(), anyInt());
        verify(hardDeleteLedgerService, never()).complete(any(), any());
    }

    @Test
    @DisplayName("中断请求从持久进度后继续且不重复删除已处理任务")
    void shouldResumeHardDeleteFromPersistedProgress()
    {
        SecurityContextHolder.setUserId("1");
        OaSignTaskBatchDeleteRequest request = deleteRequest("hard-delete-resume",
                deleteAction(9L, 3L), deleteAction(10L, 4L));
        OaSignTaskHardDeleteOperation operation = deleteOperation(2, 1);
        OaSignTaskBatchDeleteResult persisted = appendDeleteResult(deleteResult(2),
                deleteItem(9L, "DELETED", "DELETED", "已删除"), 2);
        OaSignTaskBatchDeleteResult completed = appendDeleteResult(persisted,
                deleteItem(10L, "DELETED", "DELETED", "已删除"), 2);
        OaSignTaskHardDeleteExecutor.DeleteCommit commit =
                new OaSignTaskHardDeleteExecutor.DeleteCommit(
                        10L, "ST-10", null, Set.of(), null, completed);
        when(hardDeleteLedgerService.claim(eq("hard-delete-resume"), eq(1L), any(), eq(2)))
                .thenReturn(new OaSignTaskHardDeleteLedgerService.Claim(operation, persisted, false));
        when(hardDeleteExecutor.deleteDatabaseRecords(
                10L, 4L, 1171L, operation, persisted, 2)).thenReturn(commit);

        OaSignTaskBatchDeleteResult result = service.hardDeleteUnfinishedTasks(request, 1171L);

        assertThat(result.getDeletedCount()).isEqualTo(2);
        verify(hardDeleteExecutor, never()).deleteDatabaseRecords(
                eq(9L), any(), any(), any(), any(), anyInt());
        verify(hardDeleteExecutor).deleteDatabaseRecords(
                10L, 4L, 1171L, operation, persisted, 2);
        verify(hardDeleteLedgerService).complete(operation, completed);
    }

    @Test
    @DisplayName("草稿校验完成后无需审批直接进入待发送并通知经办人")
    void shouldBecomeReadyToSendAndNotifyAssignedHr()
    {
        task.setStatus("VALIDATING");
        task.setVersion(1L);
        OaSignTaskRetryRequest request = new OaSignTaskRetryRequest();
        request.setRequestId("validate-9");
        when(packageService.preparePackageDocuments(90L, 1171L)).thenReturn(signPackage);

        OaSignTaskDetail result = service.revalidate(9L, request, 1171L, "127.0.0.1", "JUnit");

        assertThat(result.getTask().getStatus()).isEqualTo("READY_TO_SEND");
        verify(notificationOutboxService).enqueueWaitingHr(task, signPackage);
    }

    @Test
    @DisplayName("没有草稿时重新校验保持待补资料而不是误转失败")
    void shouldRequireDraftBeforeEnteringValidation()
    {
        task.setStatus(OaSignTaskStatus.NEEDS_DATA.name());
        task.setPackageId(null);
        OaSignTaskRetryRequest request = new OaSignTaskRetryRequest();
        request.setRequestId("validate-without-draft-9");

        assertThatThrownBy(() -> service.revalidate(9L, request, 1171L, "127.0.0.1", "JUnit"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("先创建并补充签约草稿");
        assertThat(task.getStatus()).isEqualTo(OaSignTaskStatus.NEEDS_DATA.name());
        verify(eventService, never()).transition(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("已确认的失败任务禁止重新生成文件并要求走发送重试")
    void shouldRejectRevalidationForConfirmedFailedTask()
    {
        task.setStatus(OaSignTaskStatus.FAILED.name());
        task.setConfirmedSnapshotHash("confirmed-snapshot");
        OaSignTaskRetryRequest request = new OaSignTaskRetryRequest();
        request.setRequestId("revalidate-confirmed-failed-9");

        assertThatThrownBy(() -> service.revalidate(9L, request, 1171L, null, null))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("已完成确认")
                .hasMessageContaining("发送重试");
        verify(eventService, never()).transition(any(), any(), any(), any(), any(), any(), any(), any(), any());
        verify(packageService, never()).preparePackageDocuments(any(), any());
    }

    @Test
    @DisplayName("未确认的失败任务仍可重新校验并生成新版本")
    void shouldAllowRevalidationForUnconfirmedFailedTask()
    {
        task.setStatus(OaSignTaskStatus.FAILED.name());
        task.setConfirmedSnapshotHash(null);
        OaSignTaskRetryRequest request = new OaSignTaskRetryRequest();
        request.setRequestId("revalidate-unconfirmed-failed-9");
        when(packageService.preparePackageDocuments(90L, 1171L)).thenReturn(signPackage);

        OaSignTaskDetail result = service.revalidate(9L, request, 1171L, null, null);

        assertThat(result.getTask().getStatus()).isEqualTo(OaSignTaskStatus.READY_TO_SEND.name());
        verify(packageService).preparePackageDocuments(90L, 1171L);
    }

    @Test
    @DisplayName("七指标服务按当前HR负责人和组织范围过滤")
    void shouldAggregateMetricsForConfiguredHrAndShopScope() throws Exception
    {
        AtomicReference<OaSignTask> capturedFilter = new AtomicReference<>();
        OaSignTaskMapper metricsMapper = mock(OaSignTaskMapper.class, invocation -> {
            if ("selectTaskMetrics".equals(invocation.getMethod().getName()))
            {
                capturedFilter.set(invocation.getArgument(0));
                return invocation.getMethod().getReturnType().getConstructor().newInstance();
            }
            return org.mockito.Answers.RETURNS_DEFAULTS.answer(invocation);
        });
        OaSignTaskServiceImpl metricsService = new OaSignTaskServiceImpl(metricsMapper, eventMapper,
                eventService, settings, packageService, shopScopeService, workflowService,
                notificationOutboxService, confirmationTokenService,
                packageLifecycleService, renewalGuardMapper, planVersionMapper,
                hardDeleteExecutor, hardDeleteLedgerService);
        Method method = Arrays.stream(OaSignTaskServiceImpl.class.getMethods())
                .filter(candidate -> "getTaskMetrics".equals(candidate.getName()))
                .findFirst().orElse(null);
        assertThat(method).isNotNull();

        Object result = method.invoke(metricsService, 1171L);

        assertThat(result).isNotNull();
        assertThat(capturedFilter.get()).isNotNull();
        assertThat(capturedFilter.get().getAssignedHrUserId()).isEqualTo(101L);
        verify(shopScopeService).appendShopScope(capturedFilter.get(), 1171L);
    }

    @AfterEach
    void tearDown()
    {
        SecurityContextHolder.remove();
    }

    @Test
    @DisplayName("创建任务固定分配配置的唯一HR并按dedupeKey幂等")
    void shouldAssignConfiguredHrAndDeduplicateCreation()
    {
        OaSignTask input = new OaSignTask();
        input.setScenario("ONBOARD");
        input.setEmployeeId(201L);
        input.setShopDeptId(1171L);
        input.setSourceType("HR_PROFILE");
        input.setSourceBusinessId("201");
        input.setSourceEventVersion("v1");
        input.setDedupeKey("ONBOARD:201:v1");
        when(settings.resolveRequiredHrUserId()).thenReturn(101L);
        when(taskMapper.insertOaSignTask(any())).thenAnswer(invocation -> {
            OaSignTask inserted = invocation.getArgument(0);
            inserted.setTaskId(9L);
            return 1;
        });

        OaSignTask created = service.createTask(input);

        assertThat(created.getAssignedHrUserId()).isEqualTo(101L);
        assertThat(created.getStatus()).isEqualTo("NEW");
        assertThat(created.getTaskNo()).startsWith("ST");
        when(taskMapper.selectOaSignTaskByDedupeKey("ONBOARD:201:v1")).thenReturn(created);
        assertThat(service.createTask(input)).isSameAs(created);
        verify(settings, never()).resolveRequiredHrUserId();
    }

    @Test
    @DisplayName("ONBOARD不同来源并发命中员工开放任务唯一约束时返回canonical任务")
    void shouldReturnCanonicalOpenOnboardTaskAfterGuardCollision()
    {
        OaSignTask input = new OaSignTask();
        input.setScenario("ONBOARD");
        input.setEmployeeId(201L);
        input.setShopDeptId(1171L);
        input.setSourceType("HR_PROFILE_CONTRACT_INITIATION");
        input.setSourceBusinessId("201");
        input.setSourceEventVersion("20260717");
        input.setDedupeKey("ONBOARD:PROFILE:201:20260717");
        OaSignTask canonical = new OaSignTask();
        canonical.setTaskId(88L);
        canonical.setScenario("ONBOARD");
        canonical.setEmployeeId(201L);
        canonical.setSourceType("HR_LIFECYCLE_ACTION");
        canonical.setDedupeKey("ONBOARD:LIFECYCLE:991:1");
        canonical.setStatus("NEW");
        when(taskMapper.insertOaSignTask(any()))
                .thenThrow(new DuplicateKeyException("open ONBOARD employee guard"));
        when(taskMapper.selectOpenOnboardTaskByEmployeeId(201L)).thenReturn(canonical);

        OaSignTask result = service.createTask(input);

        assertThat(result).isSameAs(canonical);
        verify(taskMapper).selectOpenOnboardTaskByEmployeeId(201L);
    }

    @Test
    @DisplayName("确认必须精确匹配短语、文档版本和服务端签名令牌")
    void shouldConfirmFrozenSnapshotExactlyOnce()
    {
        String snapshotHash = OaSignTaskServiceImpl.calculateSnapshotHash(task, signPackage);
        OaSignTaskConfirmRequest request = confirmRequest("confirmation-token");
        when(taskMapper.updateConfirmation(eq(9L), eq(101L), any(Date.class), eq(snapshotHash),
                eq("READY_TO_SEND"), eq(4L), eq(101L))).thenReturn(1);

        OaSignTaskDetail detail = service.confirm(9L, request, 1171L, "127.0.0.1", "JUnit");

        assertThat(detail.getTask().getStatus()).isEqualTo("READY_TO_SEND");
        assertThat(detail.getTask().getConfirmedSnapshotHash()).isNull();
        assertThat(task.getConfirmedSnapshotHash()).isEqualTo(snapshotHash);
        verify(taskMapper).updateConfirmation(eq(9L), eq(101L), any(Date.class), eq(snapshotHash),
                eq("READY_TO_SEND"), eq(4L), eq(101L));
        verify(confirmationTokenService).verify("confirmation-token", 9L, 101L,
                "SP-90-V1", snapshotHash);
        verify(packageService).markTaskConfirmation(90L, 9L, "CONFIRMED", 55L);
    }

    @Test
    @DisplayName("确认短语不精确或快照令牌已失效时拒绝确认")
    void shouldRejectFuzzyPhraseOrChangedSnapshot()
    {
        String snapshotHash = OaSignTaskServiceImpl.calculateSnapshotHash(task, signPackage);
        OaSignTaskConfirmRequest fuzzy = confirmRequest("confirmation-token");
        fuzzy.setConfirmText(" 确认本次签约资料和文件无误 ");

        assertThatThrownBy(() -> service.confirm(9L, fuzzy, 1171L, null, null))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("确认短语");
        verify(eventService, never()).transition(any(), any(), any(), any(), any(), any(), any(), any(), any());

        OaSignTaskConfirmRequest changed = confirmRequest("stale-confirmation-token");
        doThrow(new ServiceException("资料或文件已变化，请重新确认"))
                .when(confirmationTokenService).verify("stale-confirmation-token", 9L, 101L,
                        "SP-90-V1", snapshotHash);
        assertThatThrownBy(() -> service.confirm(9L, changed, 1171L, null, null))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("资料或文件已变化");
    }

    @Test
    @DisplayName("已确认任务的幂等请求仍必须携带精确短语和requestId")
    void shouldValidateConfirmationContractBeforeIdempotentReturn()
    {
        String snapshotHash = OaSignTaskServiceImpl.calculateSnapshotHash(task, signPackage);
        task.setStatus("READY_TO_SEND");
        task.setConfirmedSnapshotHash(snapshotHash);
        OaSignTaskConfirmRequest fuzzy = confirmRequest("confirmation-token");
        fuzzy.setConfirmText("已确认");
        OaSignTaskConfirmRequest missingRequestId = confirmRequest("confirmation-token");
        missingRequestId.setRequestId("");

        assertThatThrownBy(() -> service.confirm(9L, fuzzy, 1171L, null, null))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("确认短语");
        assertThatThrownBy(() -> service.confirm(9L, missingRequestId, 1171L, null, null))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("请求编号");
    }

    @Test
    @DisplayName("同组织其他HR不能处理未分配给自己的历史任务")
    void shouldRejectRoleAuthorizedOperatorForAnotherOwnersTask()
    {
        SecurityContextHolder.setUserId("102");

        assertThatThrownBy(() -> service.confirm(
                9L, confirmRequest("confirmation-token"), 1171L, null, null))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("无权访问");
        verify(taskMapper, never()).updateConfirmation(any(), any(), any(), any(), any(), any(),
                any());
    }

    @Test
    @DisplayName("角色配置签约页面权限后仍只列出分配给自己的任务")
    void shouldFilterRoleAuthorizedTaskListByCurrentOwner()
    {
        SecurityContextHolder.setUserId("102");
        when(taskMapper.selectOaSignTaskList(any())).thenReturn(Collections.emptyList());

        assertThat(service.selectTaskList(new OaSignTask(), 1171L)).isEmpty();
        ArgumentCaptor<OaSignTask> captor = ArgumentCaptor.forClass(OaSignTask.class);
        verify(taskMapper).selectOaSignTaskList(captor.capture());
        assertThat(captor.getValue().getAssignedHrUserId()).isEqualTo(102L);
    }

    @Test
    @DisplayName("系统管理员任务列表保留跨负责人查看能力")
    void shouldKeepSystemAdminTaskListUnfilteredByOwner()
    {
        SecurityContextHolder.setUserId("1");
        when(taskMapper.selectOaSignTaskList(any())).thenReturn(Collections.emptyList());

        service.selectTaskList(new OaSignTask(), 1171L);

        ArgumentCaptor<OaSignTask> captor = ArgumentCaptor.forClass(OaSignTask.class);
        verify(taskMapper).selectOaSignTaskList(captor.capture());
        assertThat(captor.getValue().getAssignedHrUserId()).isNull();
    }

    @Test
    @DisplayName("普通唯一HR任务列表返回副本并裁剪确认摘要hash")
    void shouldRedactTaskHashFromBusinessListWithoutMutatingPersistenceEntity()
    {
        task.setConfirmedSnapshotHash("task-snapshot-secret");
        when(taskMapper.selectOaSignTaskList(any())).thenReturn(List.of(task));

        List<OaSignTask> result = service.selectTaskList(new OaSignTask(), 1171L);

        assertThat(result).singleElement().satisfies(row -> {
            assertThat(row).isNotSameAs(task);
            assertThat(row.getConfirmedSnapshotHash()).isNull();
        });
        assertThat(task.getConfirmedSnapshotHash()).isEqualTo("task-snapshot-secret");
    }

    @Test
    @DisplayName("任务列表把历史续签筛选别名规范为统一场景码")
    void shouldCanonicalizeRenewalTaskFilter()
    {
        OaSignTask filter = new OaSignTask();
        filter.setScenario("renew");
        when(taskMapper.selectOaSignTaskList(any())).thenReturn(Collections.emptyList());

        service.selectTaskList(filter, 1171L);

        ArgumentCaptor<OaSignTask> captor = ArgumentCaptor.forClass(OaSignTask.class);
        verify(taskMapper).selectOaSignTaskList(captor.capture());
        assertThat(captor.getValue().getScenario()).isEqualTo("RENEWAL");
    }

    @Test
    @DisplayName("任务详情返回最近通知状态且仅为DEAD通知暴露人工重试键")
    void shouldExposeLatestNotificationSummaryAndDeadReplayKey()
    {
        OaSignNotificationOutbox latest = new OaSignNotificationOutbox();
        latest.setChannel("MOBILE_PUSH");
        latest.setStatus("DEAD");
        latest.setRetryCount(5);
        latest.setBusinessKey("SIGN_FINAL_READY:90:final-v2");
        latest.setUpdatedTime(new Date(1_721_181_784_000L));
        when(notificationOutboxService.latestForTask(9L)).thenReturn(latest);

        OaSignTaskDetail detail = service.getTaskDetail(9L, 1171L);

        assertThat(detail.getLatestNotificationStatus()).isEqualTo("DEAD");
        assertThat(detail.getLatestNotificationChannel()).isEqualTo("MOBILE_PUSH");
        assertThat(detail.getLatestNotificationRetryCount()).isEqualTo(5);
        assertThat(detail.getLatestNotificationBusinessKey())
                .isEqualTo("SIGN_FINAL_READY:90:final-v2");
        assertThat(detail.getLatestNotificationTime()).isEqualTo(latest.getUpdatedTime());

        latest.setStatus("SENT");
        OaSignTaskDetail sentDetail = service.getTaskDetail(9L, 1171L);
        assertThat(sentDetail.getLatestNotificationStatus()).isEqualTo("SENT");
        assertThat(sentDetail.getLatestNotificationBusinessKey()).isNull();
    }

    @Test
    @DisplayName("系统管理员按业务权限查看安全裁剪后的任务详情")
    void shouldAllowSystemAdminBusinessView() throws Exception
    {
        OaSignPackageDocument document = signPackage.getDocuments().get(0);
        document.setFileHashBeforeSign("before-secret");
        OaSignEvent packageEvent = new OaSignEvent();
        packageEvent.setPackageId(90L);
        packageEvent.setEventHash("package-event-secret");
        signPackage.setEvents(List.of(packageEvent));
        SecurityContextHolder.setUserId("1");

        OaSignTaskDetail detail = service.getTaskDetail(9L, 1171L);
        String json = new ObjectMapper().writeValueAsString(detail);

        assertThat(detail.getTask()).isNotSameAs(task);
        assertThat(detail.getSignPackage()).isNotSameAs(signPackage);
        assertThat(detail.getSignPackage().getDocuments().get(0).getFileHashBeforeSign())
                .isNull();
        assertThat(detail.getSignPackage().getEvents().get(0).getEventHash())
                .isNull();
        assertThat(json)
                .contains("\"businessActionsAllowed\":true")
                .contains("\"technicalEvidenceView\":false")
                .contains("\"hasConfirmation\":false");
        verify(packageService).getPackageForVerification(90L, 1171L);
    }

    @Test
    @DisplayName("被精确配置的管理员按业务HR视图处理分配给自己的任务")
    void shouldPreferCurrentHrBusinessViewForConfiguredAdmin() throws Exception
    {
        SecurityContextHolder.setUserId("1");
        when(settings.resolveRequiredHrUserId()).thenReturn(1L);
        task.setAssignedHrUserId(1L);
        task.setConfirmedSnapshotHash("admin-current-secret");

        OaSignTaskDetail detail = service.getTaskDetail(9L, 1171L);
        String json = new ObjectMapper().writeValueAsString(detail);

        assertThat(detail.getTask()).isNotSameAs(task);
        assertThat(detail.getTask().getConfirmedSnapshotHash()).isNull();
        assertThat(json)
                .contains("\"businessActionsAllowed\":true")
                .contains("\"technicalEvidenceView\":false")
                .doesNotContain("admin-current-secret");
    }

    @Test
    @DisplayName("仅有技术证据权限的用户不会因默认接收人配置获得业务权限")
    void shouldKeepTechnicalEvidenceViewForConfiguredDefaultReceiver() throws Exception
    {
        SecurityContextHolder.setUserId("102");
        LoginUser loginUser = new LoginUser();
        loginUser.setPermissions(Set.of("oa:signTask:technicalEvidence"));
        SecurityContextHolder.set(SecurityConstants.LOGIN_USER, loginUser);
        when(settings.resolveRequiredHrUserId()).thenReturn(102L);
        task.setAssignedHrUserId(102L);
        task.setConfirmedSnapshotHash("technical-current-secret");

        OaSignTaskDetail detail = service.getTaskDetail(9L, 1171L);
        String json = new ObjectMapper().writeValueAsString(detail);

        assertThat(detail.getTask().getConfirmedSnapshotHash()).isEqualTo("technical-current-secret");
        assertThat(json)
                .contains("\"businessActionsAllowed\":false")
                .contains("\"technicalEvidenceView\":true")
                .contains("technical-current-secret");
    }

    @Test
    @DisplayName("普通唯一HR任务详情使用安全副本且序列化不泄露任何原始hash")
    void shouldRedactTechnicalHashesFromBusinessHrTaskDetail() throws Exception
    {
        task.setConfirmedSnapshotHash("task-snapshot-secret");
        String currentSnapshotHash = OaSignTaskServiceImpl.calculateSnapshotHash(task, signPackage);
        OaSignTaskEvent taskEvent = new OaSignTaskEvent();
        taskEvent.setTaskId(9L);
        taskEvent.setPrevEventHash("task-prev-secret");
        taskEvent.setEventHash("task-event-secret");
        taskEvent.setRequestId("task-request-secret");
        taskEvent.setIpAddress("192.0.2.10");
        taskEvent.setUserAgent("secret-agent");
        when(eventMapper.selectTaskEvents(9L)).thenReturn(List.of(taskEvent));

        OaSignPackageDocument document = signPackage.getDocuments().get(0);
        document.setFileHashBeforeSign("before-secret");
        document.setFileHashAfterSign("after-secret");
        document.setSignedPdfHash("signed-secret");
        document.setSignatureHash("signature-secret");
        document.setCertificateHash("certificate-secret");
        OaSignEvent packageEvent = new OaSignEvent();
        packageEvent.setPackageId(90L);
        packageEvent.setDocumentHash("package-document-secret");
        packageEvent.setPrevEventHash("package-prev-secret");
        packageEvent.setEventHash("package-event-secret");
        packageEvent.setEventPayload("reviewPdfHash=payload-secret");
        packageEvent.setRequestId("package-request-secret");
        packageEvent.setIpAddress("192.0.2.11");
        packageEvent.setUserAgent("package-secret-agent");
        signPackage.setEvents(List.of(packageEvent));

        OaSignTaskDetail detail = service.getTaskDetail(9L, 1171L);

        assertThat(detail.getTask()).isNotSameAs(task);
        assertThat(detail.getTask().getConfirmedSnapshotHash()).isNull();
        assertThat(detail.getSnapshotHash()).isNull();
        assertThat(detail.getConfirmationToken()).isNull();
        assertThat(detail.getEvents()).singleElement().satisfies(event -> {
            assertThat(event).isNotSameAs(taskEvent);
            assertThat(event.getPrevEventHash()).isNull();
            assertThat(event.getEventHash()).isNull();
            assertThat(event.getRequestId()).isNull();
            assertThat(event.getIpAddress()).isNull();
            assertThat(event.getUserAgent()).isNull();
        });
        assertThat(detail.getSignPackage()).isNotSameAs(signPackage);
        assertThat(detail.getSignPackage().getDocuments()).allSatisfy(row -> {
            assertThat(row.getFileHashBeforeSign()).isNull();
            assertThat(row.getFileHashAfterSign()).isNull();
            assertThat(row.getReviewPdfHash()).isNull();
            assertThat(row.getSignedPdfHash()).isNull();
            assertThat(row.getSignatureHash()).isNull();
            assertThat(row.getCertificateHash()).isNull();
        });
        assertThat(detail.getSignPackage().getDocuments().get(0)).isNotSameAs(document);
        assertThat(detail.getSignPackage().getEvents()).singleElement().satisfies(event -> {
            assertThat(event).isNotSameAs(packageEvent);
            assertThat(event.getDocumentHash()).isNull();
            assertThat(event.getPrevEventHash()).isNull();
            assertThat(event.getEventHash()).isNull();
            assertThat(event.getEventPayload()).isNull();
            assertThat(event.getRequestId()).isNull();
            assertThat(event.getIpAddress()).isNull();
            assertThat(event.getUserAgent()).isNull();
        });
        String json = new ObjectMapper().writeValueAsString(detail);
        assertThat(json)
                .contains("\"businessActionsAllowed\":true")
                .contains("\"technicalEvidenceView\":false")
                .contains("\"hasConfirmation\":true");
        assertThat(json).doesNotContain("task-snapshot-secret", "task-prev-secret", "task-event-secret",
                "hash-b", "before-secret", "after-secret", "signed-secret", "signature-secret",
                "certificate-secret", "package-document-secret", "package-prev-secret",
                "package-event-secret", "payload-secret", currentSnapshotHash);

        assertThat(task.getConfirmedSnapshotHash()).isEqualTo("task-snapshot-secret");
        assertThat(taskEvent.getEventHash()).isEqualTo("task-event-secret");
        assertThat(document.getReviewPdfHash()).isEqualTo("hash-b");
        assertThat(packageEvent.getEventHash()).isEqualTo("package-event-secret");
    }

    @Test
    @DisplayName("技术证据管理员只读详情保留任务、文档和事件原始hash")
    void shouldKeepTechnicalHashesForTechnicalEvidenceReader()
    {
        task.setConfirmedSnapshotHash("task-snapshot-secret");
        OaSignTaskEvent taskEvent = new OaSignTaskEvent();
        taskEvent.setTaskId(9L);
        taskEvent.setPrevEventHash("task-prev-secret");
        taskEvent.setEventHash("task-event-secret");
        when(eventMapper.selectTaskEvents(9L)).thenReturn(List.of(taskEvent));
        OaSignPackageDocument document = signPackage.getDocuments().get(0);
        document.setFileHashBeforeSign("before-secret");
        OaSignEvent packageEvent = new OaSignEvent();
        packageEvent.setPackageId(90L);
        packageEvent.setDocumentHash("package-document-secret");
        packageEvent.setPrevEventHash("package-prev-secret");
        packageEvent.setEventHash("package-event-secret");
        signPackage.setEvents(List.of(packageEvent));
        SecurityContextHolder.setUserId("102");
        LoginUser loginUser = new LoginUser();
        loginUser.setPermissions(Set.of("oa:signTask:technicalEvidence"));
        SecurityContextHolder.set(SecurityConstants.LOGIN_USER, loginUser);

        OaSignTaskDetail detail = service.getTaskDetail(9L, 1171L);

        assertThat(detail.getTask().getConfirmedSnapshotHash()).isEqualTo("task-snapshot-secret");
        assertThat(detail.getEvents()).singleElement().satisfies(event -> {
            assertThat(event.getPrevEventHash()).isEqualTo("task-prev-secret");
            assertThat(event.getEventHash()).isEqualTo("task-event-secret");
        });
        assertThat(detail.getSignPackage()).isSameAs(signPackage);
        assertThat(detail.getSignPackage().getDocuments()).first().satisfies(row -> {
            assertThat(row.getFileHashBeforeSign()).isEqualTo("before-secret");
            assertThat(row.getReviewPdfHash()).isEqualTo("hash-b");
        });
        assertThat(detail.getSignPackage().getEvents()).singleElement().satisfies(event -> {
            assertThat(event.getDocumentHash()).isEqualTo("package-document-secret");
            assertThat(event.getPrevEventHash()).isEqualTo("package-prev-secret");
            assertThat(event.getEventHash()).isEqualTo("package-event-secret");
        });
    }

    @Test
    @DisplayName("系统管理员可按高权限处理历史签约任务")
    void shouldAllowTaskConfirmationBySystemAdmin()
    {
        SecurityContextHolder.setUserId("1");
        String snapshotHash = OaSignTaskServiceImpl.calculateSnapshotHash(task, signPackage);
        when(taskMapper.updateConfirmation(eq(9L), eq(1L), any(Date.class), eq(snapshotHash),
                eq("READY_TO_SEND"), eq(4L), eq(101L))).thenReturn(1);

        OaSignTaskDetail detail = service.confirm(
                9L, confirmRequest("confirmation-token"), 1171L, null, null);

        assertThat(detail.getTask().getStatus()).isEqualTo("READY_TO_SEND");
        verify(taskMapper).updateConfirmation(eq(9L), eq(1L), any(Date.class), eq(snapshotHash),
                eq("READY_TO_SEND"), eq(4L), eq(101L));
    }

    @Test
    @DisplayName("确认后的发送复用已冻结文件且不重新生成")
    void shouldSendConfirmedPreparedPackage()
    {
        task.setStatus("READY_TO_SEND");
        task.setVersion(4L);
        task.setConfirmedSnapshotHash(OaSignTaskServiceImpl.calculateSnapshotHash(task, signPackage));
        OaSignTaskRetryRequest request = new OaSignTaskRetryRequest();
        request.setRequestId("send-9");
        when(packageService.sendPreparedPackage(eq(90L), eq(9L), eq("SP-90-V1"), eq(1171L),
                any(Date.class), any(Date.class), eq("PLAN_VERSION"), eq(7)))
                .thenReturn(signPackage);

        OaSignTaskDetail result = service.send(9L, request, 1171L, "127.0.0.1", "JUnit");

        assertThat(result.getTask().getStatus()).isEqualTo("PENDING_SIGN");
        verify(packageService).sendPreparedPackage(eq(90L), eq(9L), eq("SP-90-V1"), eq(1171L),
                any(Date.class), any(Date.class), eq("PLAN_VERSION"), eq(7));
        verify(notificationOutboxService).enqueueSent(task, signPackage);
    }

    @Test
    @DisplayName("旧审批快照不再阻断无审批发送流程")
    void shouldIgnoreLegacyConfirmationSnapshotWhenSending()
    {
        OaSignTaskWorkflowService atomicWorkflow = mock(OaSignTaskWorkflowService.class);
        OaSignTaskServiceImpl atomicService = new OaSignTaskServiceImpl(taskMapper, eventMapper,
                eventService, settings, packageService, shopScopeService, atomicWorkflow,
                notificationOutboxService, confirmationTokenService,
                packageLifecycleService, renewalGuardMapper, planVersionMapper,
                hardDeleteExecutor, hardDeleteLedgerService);
        task.setStatus("READY_TO_SEND");
        task.setVersion(4L);
        task.setConfirmedSnapshotHash("stale-confirmation-hash");
        OaSignTaskRetryRequest request = new OaSignTaskRetryRequest();
        request.setRequestId("send-stale-9");

        OaSignTaskDetail result = atomicService.send(9L, request, 1171L, "127.0.0.1", "JUnit");

        assertThat(result.getTask().getStatus()).isEqualTo(OaSignTaskStatus.SENDING.name());
        verify(atomicWorkflow).sendPrepared(task, signPackage, 1171L, "127.0.0.1", "JUnit");
        verify(atomicWorkflow, never()).invalidateStaleConfirmation(any(), any(), any());
        verify(taskMapper, never()).clearConfirmation(any(), any(), any(), any());
        verify(packageService, never()).markTaskConfirmation(any(), any(), any(), any());
    }

    @Test
    @DisplayName("发送失败保留任务并进入可重试失败态")
    void shouldKeepTaskFailedWhenPreparedPackageSendFails()
    {
        task.setStatus("READY_TO_SEND");
        task.setVersion(4L);
        task.setConfirmedSnapshotHash(OaSignTaskServiceImpl.calculateSnapshotHash(task, signPackage));
        OaSignTaskRetryRequest request = new OaSignTaskRetryRequest();
        request.setRequestId("send-failed-9");
        when(packageService.sendPreparedPackage(eq(90L), eq(9L), eq("SP-90-V1"), eq(1171L),
                any(Date.class), any(Date.class), eq("PLAN_VERSION"), eq(7)))
                .thenThrow(new ServiceException("LibreOffice暂时不可用"));

        OaSignTaskDetail result = service.send(9L, request, 1171L, null, null);

        assertThat(result.getTask().getStatus()).isEqualTo("FAILED");
        assertThat(result.getTask().getFailureCode()).isEqualTo("SEND_FAILED");
        assertThat(result.getTask().getFailureDetail()).contains("LibreOffice");
    }

    @Test
    @DisplayName("确认和发送requestId在任务继续流转后重放仍幂等")
    void shouldReplayConfirmedAndSentRequestsIdempotently()
    {
        String snapshotHash = OaSignTaskServiceImpl.calculateSnapshotHash(task, signPackage);
        task.setStatus("PENDING_SIGN");
        task.setVersion(7L);
        task.setConfirmedSnapshotHash(snapshotHash);
        OaSignTaskEvent confirmed = new OaSignTaskEvent();
        confirmed.setTaskId(9L);
        confirmed.setToStatus("READY_TO_SEND");
        OaSignTaskEvent sending = new OaSignTaskEvent();
        sending.setTaskId(9L);
        sending.setToStatus("SENDING");
        when(eventMapper.selectEventByRequestId("confirm-9")).thenReturn(confirmed);
        when(eventMapper.selectEventByRequestId("send-9")).thenReturn(sending);
        OaSignTaskRetryRequest sendRequest = new OaSignTaskRetryRequest();
        sendRequest.setRequestId("send-9");

        assertThat(service.confirm(9L, confirmRequest("confirmation-token"), 1171L, null, null)
                .getTask().getStatus()).isEqualTo("PENDING_SIGN");
        assertThat(service.send(9L, sendRequest, 1171L, null, null)
                .getTask().getStatus()).isEqualTo("PENDING_SIGN");
        verify(packageService, never()).sendPreparedPackage(any(), any(), any(), any(),
                any(), any(), any(), any());
    }

    @Test
    @DisplayName("已成功确认的requestId在令牌过期后重放仍幂等返回")
    void shouldReplaySuccessfulConfirmationAfterTokenExpires()
    {
        task.setStatus("PENDING_SIGN");
        task.setVersion(7L);
        OaSignTaskEvent confirmed = new OaSignTaskEvent();
        confirmed.setTaskId(9L);
        confirmed.setToStatus("READY_TO_SEND");
        when(eventMapper.selectEventByRequestId("confirm-9")).thenReturn(confirmed);
        doThrow(new ServiceException("确认令牌无效或已过期"))
                .when(confirmationTokenService).verify(eq("expired-token"), any(), any(), any(), any());

        OaSignTaskDetail detail = service.confirm(9L, confirmRequest("expired-token"),
                1171L, null, null);

        assertThat(detail.getTask().getStatus()).isEqualTo("PENDING_SIGN");
        verify(confirmationTokenService, never()).verify(any(), any(), any(), any(), any());
        verify(eventService, never()).transition(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("其他任务的requestId不能被当作当前任务幂等请求")
    void shouldRejectConfirmationRequestIdUsedByAnotherTaskBeforeTokenVerification()
    {
        OaSignTaskEvent otherTaskEvent = new OaSignTaskEvent();
        otherTaskEvent.setTaskId(10L);
        otherTaskEvent.setToStatus("READY_TO_SEND");
        when(eventMapper.selectEventByRequestId("confirm-9")).thenReturn(otherTaskEvent);

        assertThatThrownBy(() -> service.confirm(9L, confirmRequest("confirmation-token"),
                1171L, null, null))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("已用于其他任务操作");
        verify(confirmationTokenService, never()).verify(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("快照字段顺序固定且文件查询顺序不影响摘要")
    void shouldCalculateOrderIndependentDocumentSnapshot()
    {
        String first = OaSignTaskServiceImpl.calculateSnapshotHash(task, signPackage);
        Collections.reverse(signPackage.getDocuments());
        String second = OaSignTaskServiceImpl.calculateSnapshotHash(task, signPackage);

        assertThat(first).isEqualTo(second).hasSize(64);
    }

    @Test
    @DisplayName("拒签任务替代版本创建新任务和新包并保留旧终态")
    void shouldReissueIntoLinkedDraftWithoutRevivingTerminalSource()
    {
        LoginUser loginUser = new LoginUser();
        loginUser.setPermissions(Set.of("oa:signTask:resolveRefusal"));
        SecurityContextHolder.set(SecurityConstants.LOGIN_USER, loginUser);
        task.setStatus(OaSignTaskStatus.REFUSED.name());
        task.setVersion(8L);
        task.setResolutionStatus("OPEN");
        task.setTerminalTime(Date.from(Instant.parse("2026-07-17T01:00:00Z")));
        task.setSourceType("HR_ONBOARD");
        task.setSourceBusinessId("employee-201");
        task.setSourceEventVersion("3");
        signPackage.setStatus("refused");
        signPackage.setVersion(4L);
        signPackage.setResolutionStatus("OPEN");
        signPackage.setTerminalTime(task.getTerminalTime());
        signPackage.setLegalEntityNameSnapshot("不得继承的旧公司主体");
        signPackage.setContractTermCodeSnapshot("OPEN_ENDED");
        signPackage.setSealNameSnapshot("不得继承的旧印章");
        signPackage.setSignedTime(task.getTerminalTime());
        when(taskMapper.lockOaSignTaskById(9L)).thenReturn(task);
        when(packageLifecycleService.lockForResolution(90L)).thenReturn(signPackage);
        when(taskMapper.insertOaSignTask(any())).thenAnswer(invocation -> {
            OaSignTask replacement = invocation.getArgument(0);
            replacement.setTaskId(91L);
            return 1;
        });
        OaSignPackage replacementPackage = new OaSignPackage();
        replacementPackage.setPackageId(900L);
        replacementPackage.setTaskId(91L);
        replacementPackage.setStatus("draft");
        replacementPackage.setVersion(0L);
        when(packageService.createPackage(any(), eq(1171L))).thenReturn(replacementPackage);
        when(packageService.getPackageForVerification(900L, 1171L)).thenReturn(replacementPackage);
        OaSignExceptionResolutionRequest request = resolutionRequest("REISSUE");
        request.setReplacementPlanVersionId(55L);

        OaSignTaskDetail result = service.resolveException(9L, request, 1171L,
                "127.0.0.1", "JUnit");

        assertThat(result.getTask().getTaskId()).isEqualTo(91L);
        ArgumentCaptor<OaSignTask> taskCaptor = ArgumentCaptor.forClass(OaSignTask.class);
        verify(taskMapper).insertOaSignTask(taskCaptor.capture());
        assertThat(taskCaptor.getValue().getReissueOfTaskId()).isEqualTo(9L);
        assertThat(taskCaptor.getValue().getDedupeKey()).isEqualTo("SIGN_REISSUE:9");
        assertThat(taskCaptor.getValue().getPlanVersionId()).isEqualTo(55L);
        ArgumentCaptor<OaSignPackage> packageCaptor = ArgumentCaptor.forClass(OaSignPackage.class);
        verify(packageService).createPackage(packageCaptor.capture(), eq(1171L));
        assertThat(packageCaptor.getValue().getTaskId()).isEqualTo(91L);
        assertThat(packageCaptor.getValue().getContractTermCodeSnapshot()).isEqualTo("OPEN_ENDED");
        assertThat(packageCaptor.getValue().getLegalEntityNameSnapshot()).isNull();
        assertThat(packageCaptor.getValue().getSealNameSnapshot()).isNull();
        assertThat(packageCaptor.getValue().getSignedTime()).isNull();
        verify(packageLifecycleService).markReissued(eq(task), eq(signPackage),
                eq(taskCaptor.getValue()), eq(replacementPackage), eq(request), eq(101L),
                eq("唯一HR"), eq("127.0.0.1"), eq("JUnit"));
        assertThat(task.getStatus()).isEqualTo(OaSignTaskStatus.REFUSED.name());
        assertThat(signPackage.getStatus()).isEqualTo("refused");
    }

    @Test
    @DisplayName("拒签与过期处置权限不能互相越权")
    void shouldEnforceResolutionPermissionAgainstPersistedTaskStatus()
    {
        LoginUser loginUser = new LoginUser();
        loginUser.setPermissions(Set.of("oa:signTask:resolveRefusal"));
        SecurityContextHolder.set(SecurityConstants.LOGIN_USER, loginUser);
        task.setStatus(OaSignTaskStatus.EXPIRED.name());

        assertThatThrownBy(() -> service.resolveException(9L, resolutionRequest("CLOSE"),
                1171L, null, null))
                .isInstanceOf(NotPermissionException.class);

        loginUser.setPermissions(Set.of("oa:signTask:resolveExpiry"));
        task.setStatus(OaSignTaskStatus.REFUSED.name());
        assertThatThrownBy(() -> service.resolveException(9L, resolutionRequest("CLOSE"),
                1171L, null, null))
                .isInstanceOf(NotPermissionException.class);
    }

    @Test
    @DisplayName("实际转正日属于HR确认令牌绑定的业务快照")
    void shouldBindActualRegularizationDateIntoConfirmationSnapshot()
    {
        signPackage.setActualRegularizationDate("2026-07-10");
        String first = OaSignTaskServiceImpl.calculateSnapshotHash(task, signPackage);

        signPackage.setActualRegularizationDate("2026-07-11");
        String second = OaSignTaskServiceImpl.calculateSnapshotHash(task, signPackage);

        assertThat(second).isNotEqualTo(first);
    }

    @Test
    @DisplayName("合同期限快照有值时绑定确认摘要且历史空值保持兼容")
    void shouldBindPresentContractTermWithoutChangingLegacyBlankSemantics()
    {
        signPackage.setContractTermCodeSnapshot(null);
        String legacyNull = OaSignTaskServiceImpl.calculateSnapshotHash(task, signPackage);
        signPackage.setContractTermCodeSnapshot("   ");
        String legacyBlank = OaSignTaskServiceImpl.calculateSnapshotHash(task, signPackage);
        signPackage.setContractTermCodeSnapshot("FIXED_TERM");
        String fixedTerm = OaSignTaskServiceImpl.calculateSnapshotHash(task, signPackage);
        signPackage.setContractTermCodeSnapshot("OPEN_ENDED");
        String openEnded = OaSignTaskServiceImpl.calculateSnapshotHash(task, signPackage);

        assertThat(legacyBlank).isEqualTo(legacyNull);
        assertThat(fixedTerm).isNotEqualTo(legacyNull).isNotEqualTo(openEnded);
    }

    private OaSignTask task(OaSignTaskStatus status, Long version)
    {
        OaSignTask value = new OaSignTask();
        value.setTaskId(9L);
        value.setTaskNo("ST202607110009");
        value.setScenario("ONBOARD");
        value.setEmployeeId(201L);
        value.setShopDeptId(1171L);
        value.setLegalEntityId(12L);
        value.setAssignedHrUserId(101L);
        value.setPlanVersionId(55L);
        value.setPackageId(90L);
        value.setStatus(status.name());
        value.setVersion(version);
        return value;
    }

    private OaSignPackage signPackage()
    {
        OaSignPackage value = new OaSignPackage();
        value.setPackageId(90L);
        value.setTaskId(9L);
        value.setEmployeeId(201L);
        value.setEmployeeNameSnapshot("测试员工");
        value.setShopDeptId(1171L);
        value.setDeptIdSnapshot(1171L);
        value.setScenario("ONBOARD");
        value.setEmploymentType("full_time");
        value.setContractTermCodeSnapshot("FIXED_TERM");
        value.setContractStartDate("2026-07-11");
        value.setContractEndDate("2029-07-10");
        value.setBaseSalary(new java.math.BigDecimal("5000.00"));
        value.setSourcePlanId(5L);
        value.setPlanVersionId(55L);
        value.setDocumentVersion("SP-90-V1");
        value.setStatus("draft");
        OaSignPackageDocument first = document(2L, "劳动合同", "hash-b");
        OaSignPackageDocument second = document(1L, "入职确认", "hash-a");
        value.setDocuments(new java.util.ArrayList<>(List.of(first, second)));
        return value;
    }

    private OaSignPackageDocument document(Long id, String name, String hash)
    {
        OaSignPackageDocument document = new OaSignPackageDocument();
        document.setDocumentId(id);
        document.setTemplateId(id + 10);
        document.setDocumentName(name);
        document.setTemplateVersionSnapshot("v1");
        document.setDocumentVersion("SP-90-V1");
        document.setReviewPdfHash(hash);
        document.setEmployeeSignRequired("Y");
        document.setSortOrder(id.intValue());
        return document;
    }

    private OaSignTaskBatchDeleteRequest deleteRequest(String requestId,
            OaSignTaskBatchDeleteAction... actions)
    {
        OaSignTaskBatchDeleteRequest request = new OaSignTaskBatchDeleteRequest();
        request.setRequestId(requestId);
        request.setItems(List.of(actions));
        request.setIrreversibleConfirmed(true);
        return request;
    }

    private OaSignTaskBatchDeleteAction deleteAction(Long taskId, Long expectedVersion)
    {
        OaSignTaskBatchDeleteAction action = new OaSignTaskBatchDeleteAction();
        action.setTaskId(taskId);
        action.setExpectedVersion(expectedVersion);
        return action;
    }

    private OaSignTaskHardDeleteOperation deleteOperation(int totalCount, int processedCount)
    {
        OaSignTaskHardDeleteOperation operation = new OaSignTaskHardDeleteOperation();
        operation.setOperationId(7000L);
        operation.setRequestId("hard-delete-operation");
        operation.setAdministratorUserId(1L);
        operation.setStatus(OaSignTaskHardDeleteLedgerService.PROCESSING);
        operation.setClaimToken("claim-token");
        operation.setTotalCount(totalCount);
        operation.setProcessedCount(processedCount);
        operation.setVersion((long) processedCount);
        return operation;
    }

    private OaSignTaskBatchDeleteResult deleteResult(int totalCount)
    {
        OaSignTaskBatchDeleteResult result = new OaSignTaskBatchDeleteResult();
        result.setTotalCount(totalCount);
        return result;
    }

    private OaSignTaskBatchDeleteItem deleteItem(Long taskId,
            String result, String code, String message)
    {
        OaSignTaskBatchDeleteItem item = new OaSignTaskBatchDeleteItem();
        item.setTaskId(taskId);
        item.setResult(result);
        item.setCode(code);
        item.setMessage(message);
        return item;
    }

    private OaSignTaskBatchDeleteResult appendDeleteResult(
            OaSignTaskBatchDeleteResult current, OaSignTaskBatchDeleteItem item, int totalCount)
    {
        OaSignTaskBatchDeleteResult result = new OaSignTaskBatchDeleteResult();
        result.setTotalCount(totalCount);
        java.util.ArrayList<OaSignTaskBatchDeleteItem> items = new java.util.ArrayList<>();
        if (current != null && current.getItems() != null)
        {
            items.addAll(current.getItems());
        }
        items.add(item);
        result.setItems(items);
        result.setDeletedCount((int) items.stream()
                .filter(value -> "DELETED".equals(value.getResult())).count());
        result.setFailedCount(items.size() - result.getDeletedCount());
        return result;
    }

    private OaSignTaskConfirmRequest confirmRequest(String confirmationToken)
    {
        OaSignTaskConfirmRequest request = new OaSignTaskConfirmRequest();
        request.setDocumentVersion("SP-90-V1");
        request.setConfirmationToken(confirmationToken);
        request.setConfirmText("确认本次签约资料和文件无误");
        request.setRequestId("confirm-9");
        return request;
    }

    private OaSignExceptionResolutionRequest resolutionRequest(String action)
    {
        OaSignExceptionResolutionRequest request = new OaSignExceptionResolutionRequest();
        request.setRequestId("resolve-9");
        request.setAction(action);
        request.setDocumentVersion("SP-90-V1");
        request.setReasonCode("HR_APPROVED");
        request.setReasonDetail("创建替代签约版本");
        request.setExpectedVersion(8L);
        return request;
    }
}
