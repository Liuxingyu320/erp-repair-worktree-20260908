package com.erp.oa.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Date;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import com.erp.oa.constant.OaSignTaskStatus;
import com.erp.oa.constant.OaSignPackageStatus;
import com.erp.oa.domain.OaSignPackage;
import com.erp.oa.domain.OaSignPlanVersion;
import com.erp.oa.domain.OaSignTask;
import com.erp.oa.mapper.OaSignPlanVersionMapper;
import com.erp.oa.mapper.OaSignTaskMapper;
import com.erp.oa.service.IOaSignPackageService;

class OaSignTaskWorkflowServiceTest
{
    private OaSignTaskMapper taskMapper;
    private OaSignTaskEventService eventService;
    private IOaSignPackageService packageService;
    private OaSignNotificationOutboxService outboxService;
    private OaSignPlanVersionMapper planVersionMapper;
    private OaSignTaskWorkflowService workflowService;
    private OaSignTask task;
    private OaSignPackage signPackage;

    @BeforeEach
    void setUp()
    {
        taskMapper = mock(OaSignTaskMapper.class);
        eventService = mock(OaSignTaskEventService.class);
        packageService = mock(IOaSignPackageService.class);
        outboxService = mock(OaSignNotificationOutboxService.class);
        planVersionMapper = mock(OaSignPlanVersionMapper.class);
        workflowService = new OaSignTaskWorkflowService(taskMapper, eventService,
                packageService, outboxService, planVersionMapper,
                Clock.fixed(Instant.parse("2026-07-17T02:03:04.567Z"),
                        ZoneId.of("Asia/Shanghai")));
        task = new OaSignTask();
        task.setTaskId(9L);
        task.setPackageId(90L);
        task.setPlanVersionId(55L);
        task.setAssignedHrUserId(101L);
        task.setShopDeptId(1171L);
        task.setStatus("VALIDATING");
        task.setVersion(1L);
        signPackage = new OaSignPackage();
        signPackage.setPackageId(90L);
        signPackage.setDocumentVersion("SP-90-V1");
        signPackage.setEmployeeId(201L);
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
    void productionConstructorIsTheExplicitSpringInjectionCandidate() throws Exception
    {
        assertThat(OaSignTaskWorkflowService.class.getConstructor(
                OaSignTaskMapper.class,
                OaSignTaskEventService.class,
                IOaSignPackageService.class,
                OaSignNotificationOutboxService.class,
                OaSignPlanVersionMapper.class).getAnnotation(Autowired.class)).isNotNull();
    }

    @Test
    void successWorkflowsOwnTheAtomicTransactionButPublicFailureRecorderDoesNot() throws Exception
    {
        Method validation = OaSignTaskWorkflowService.class.getMethod("completeValidation",
                OaSignTask.class, Long.class, String.class, String.class);
        Method sending = OaSignTaskWorkflowService.class.getMethod("sendPrepared",
                OaSignTask.class, OaSignPackage.class, Long.class, String.class, String.class);
        Method staleConfirmation = Arrays.stream(OaSignTaskWorkflowService.class.getMethods())
                .filter(candidate -> "invalidateStaleConfirmation".equals(candidate.getName()))
                .findFirst().orElse(null);
        Method publicSend = OaSignTaskServiceImpl.class.getMethod("send", Long.class,
                com.erp.oa.domain.dto.OaSignTaskRetryRequest.class, Long.class, String.class, String.class);

        assertThat(validation.getAnnotation(Transactional.class)).isNotNull();
        assertThat(sending.getAnnotation(Transactional.class)).isNotNull();
        assertThat(staleConfirmation).isNotNull();
        assertThat(staleConfirmation.getAnnotation(Transactional.class)).isNotNull();
        assertThat(publicSend.getAnnotation(Transactional.class)).isNull();
    }

    @Test
    void validationCompletionChangesStateToReadyWithoutReview()
    {
        when(packageService.preparePackageDocuments(90L, 1171L)).thenReturn(signPackage);

        OaSignPackage result = workflowService.completeValidation(task, 1171L, "127.0.0.1", "JUnit");

        assertThat(result).isSameAs(signPackage);
        assertThat(task.getStatus()).isEqualTo("READY_TO_SEND");
        verify(taskMapper, never()).updatePackageLink(any(), any(), any(), any());
        verify(packageService).markTaskConfirmation(90L, 9L, "NOT_REQUIRED", 55L);
        verify(outboxService).enqueueWaitingHr(task, signPackage);
    }

    @Test
    void preparedSendChangesStateAndEnqueuesEmployeeNotificationInOneWorkflow()
    {
        task.setStatus("SENDING");
        OaSignPlanVersion version = new OaSignPlanVersion();
        version.setVersionId(55L);
        version.setSignDeadlineDays(7);
        version.setPublishStatus("PUBLISHED");
        when(planVersionMapper.selectPlanVersionById(55L)).thenReturn(version);
        when(taskMapper.updateSentLifecycle(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(1);
        when(packageService.sendPreparedPackage(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(signPackage);

        workflowService.sendPrepared(task, signPackage, 1171L, "127.0.0.1", "JUnit");

        assertThat(task.getStatus()).isEqualTo("PENDING_SIGN");
        ArgumentCaptor<Date> sentCaptor = ArgumentCaptor.forClass(Date.class);
        ArgumentCaptor<Date> deadlineCaptor = ArgumentCaptor.forClass(Date.class);
        verify(packageService).sendPreparedPackage(
                org.mockito.ArgumentMatchers.eq(90L), org.mockito.ArgumentMatchers.eq(9L),
                org.mockito.ArgumentMatchers.eq("SP-90-V1"), org.mockito.ArgumentMatchers.eq(1171L),
                sentCaptor.capture(), deadlineCaptor.capture(),
                org.mockito.ArgumentMatchers.eq("PLAN_VERSION"), org.mockito.ArgumentMatchers.eq(7));
        assertThat(sentCaptor.getValue()).isEqualTo(Date.from(Instant.parse("2026-07-17T02:03:04Z")));
        assertThat(deadlineCaptor.getValue()).isEqualTo(Date.from(Instant.parse("2026-07-24T15:59:59Z")));
        verify(taskMapper).updateSentLifecycle(9L, "PENDING_SIGN", 2L, 101L,
                sentCaptor.getValue(), deadlineCaptor.getValue(), "PLAN_VERSION", 7);
        assertThat(task.getSentTime()).isEqualTo(sentCaptor.getValue());
        assertThat(task.getSignDeadline()).isEqualTo(deadlineCaptor.getValue());
        verify(outboxService).enqueueSent(task, signPackage);
    }

    @Test
    void signatureFirstSendMovesDirectlyToFinalConfirmation()
    {
        task.setStatus("SENDING");
        OaSignPlanVersion version = new OaSignPlanVersion();
        version.setVersionId(55L);
        version.setSignDeadlineDays(7);
        version.setPublishStatus("PUBLISHED");
        signPackage.setStatus(OaSignPackageStatus.PENDING_FINAL_CONFIRM);
        when(planVersionMapper.selectPlanVersionById(55L)).thenReturn(version);
        when(taskMapper.updateSentLifecycle(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(1);
        when(packageService.sendPreparedPackage(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(signPackage);

        workflowService.sendPrepared(task, signPackage, 1171L, "127.0.0.1", "JUnit");

        assertThat(task.getStatus()).isEqualTo("PENDING_FINAL_CONFIRM");
        verify(outboxService).enqueueFinalReady(task, signPackage);
        verify(outboxService, never()).enqueueSent(any(), any());
    }

    @Test
    void preparedSendFailsClosedBeforeWritesWhenFrozenDeadlinePolicyIsMissing()
    {
        task.setStatus("SENDING");
        OaSignPlanVersion version = new OaSignPlanVersion();
        version.setVersionId(55L);
        version.setSignDeadlineDays(null);
        version.setPublishStatus("PUBLISHED");
        when(planVersionMapper.selectPlanVersionById(55L)).thenReturn(version);

        assertThatThrownBy(() -> workflowService.sendPrepared(
                task, signPackage, 1171L, "127.0.0.1", "JUnit"))
                .isInstanceOf(com.erp.common.core.exception.ServiceException.class)
                .hasMessageContaining("签署期限");

        verify(packageService, never()).sendPreparedPackage(any(), any(), any(), any(),
                any(), any(), any(), any());
        verify(eventService, never()).transition(any(), any(), any(), any(), any(), any(), any(), any(), any());
        verify(taskMapper, never()).updateSentLifecycle(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void staleConfirmationIsClearedAcrossTaskEventAndPackageInOneWorkflow() throws Exception
    {
        task.setStatus("READY_TO_SEND");
        task.setVersion(4L);
        task.setConfirmedBy(101L);
        task.setConfirmedSnapshotHash("stale-hash");
        when(taskMapper.clearConfirmation(9L, "WAITING_HR_CONFIRM", 5L, 101L)).thenReturn(1);
        Method method = Arrays.stream(OaSignTaskWorkflowService.class.getMethods())
                .filter(candidate -> "invalidateStaleConfirmation".equals(candidate.getName()))
                .findFirst().orElse(null);
        assertThat(method).isNotNull();

        method.invoke(workflowService, task, "127.0.0.1", "JUnit");

        assertThat(task.getStatus()).isEqualTo("WAITING_HR_CONFIRM");
        assertThat(task.getConfirmedBy()).isNull();
        assertThat(task.getConfirmedSnapshotHash()).isNull();
        verify(taskMapper).clearConfirmation(9L, "WAITING_HR_CONFIRM", 5L, 101L);
        verify(packageService).markTaskConfirmation(90L, 9L, "WAITING_HR_CONFIRM", 55L);
    }

    @Test
    void staleConfirmationWorkflowRejectsZeroRowBeforePackageMutation() throws Exception
    {
        task.setStatus("READY_TO_SEND");
        task.setVersion(4L);
        when(taskMapper.clearConfirmation(9L, "WAITING_HR_CONFIRM", 5L, 101L)).thenReturn(0);
        Method method = Arrays.stream(OaSignTaskWorkflowService.class.getMethods())
                .filter(candidate -> "invalidateStaleConfirmation".equals(candidate.getName()))
                .findFirst().orElse(null);
        assertThat(method).isNotNull();

        assertThatThrownBy(() -> method.invoke(workflowService, task, null, null))
                .hasCauseInstanceOf(com.erp.common.core.exception.ServiceException.class)
                .hasRootCauseMessage("任务确认已被其他操作更新");
        verify(packageService, never()).markTaskConfirmation(any(), any(), any(), any());
    }
}
