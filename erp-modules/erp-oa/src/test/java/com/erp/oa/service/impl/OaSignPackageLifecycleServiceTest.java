package com.erp.oa.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.erp.common.core.exception.ServiceException;
import com.erp.oa.constant.OaSignPackageStatus;
import com.erp.oa.constant.OaSignTaskStatus;
import com.erp.oa.domain.OaSignEvent;
import com.erp.oa.domain.OaSignPackage;
import com.erp.oa.domain.OaSignTask;
import com.erp.oa.domain.dto.OaSignPackageRefuseRequest;
import com.erp.oa.domain.dto.OaSignExceptionResolutionRequest;
import com.erp.oa.mapper.OaSignEventMapper;
import com.erp.oa.mapper.OaSignOnboardDataRequestMapper;
import com.erp.oa.mapper.OaSignOnboardImportRowMapper;
import com.erp.oa.mapper.OaSignPackageMapper;
import com.erp.oa.mapper.OaSignTaskMapper;

class OaSignPackageLifecycleServiceTest
{
    private static final Instant NOW = Instant.parse("2026-07-17T02:03:04Z");

    private OaSignPackageMapper packageMapper;
    private OaSignTaskMapper taskMapper;
    private OaSignEventMapper eventMapper;
    private OaSignOnboardDataRequestMapper onboardDataRequestMapper;
    private OaSignOnboardImportRowMapper onboardImportRowMapper;
    private OaSignTaskEventService taskEventService;
    private OaSignNotificationOutboxService outboxService;
    private OaSignPackageLifecycleService service;
    private OaSignPackage signPackage;
    private OaSignTask task;

    @BeforeEach
    void setUp()
    {
        packageMapper = mock(OaSignPackageMapper.class);
        taskMapper = mock(OaSignTaskMapper.class);
        eventMapper = mock(OaSignEventMapper.class);
        onboardDataRequestMapper = mock(OaSignOnboardDataRequestMapper.class);
        onboardImportRowMapper = mock(OaSignOnboardImportRowMapper.class);
        taskEventService = mock(OaSignTaskEventService.class);
        outboxService = mock(OaSignNotificationOutboxService.class);
        service = new OaSignPackageLifecycleService(packageMapper, taskMapper, eventMapper,
                onboardDataRequestMapper, onboardImportRowMapper, taskEventService,
                outboxService, Clock.fixed(NOW, ZoneOffset.UTC));

        signPackage = new OaSignPackage();
        signPackage.setPackageId(90L);
        signPackage.setTaskId(9L);
        signPackage.setEmployeeId(201L);
        signPackage.setStatus(OaSignPackageStatus.PENDING_SIGN);
        signPackage.setVersion(4L);
        signPackage.setDocumentVersion("SP-90-V1");
        signPackage.setSignDeadline(Date.from(Instant.parse("2026-07-18T15:59:59Z")));
        signPackage.setDeadlinePolicySource("PLAN_VERSION");
        signPackage.setDeadlineDaysSnapshot(7);
        when(packageMapper.selectOaSignPackageById(90L)).thenReturn(signPackage);

        task = new OaSignTask();
        task.setTaskId(9L);
        task.setPackageId(90L);
        task.setEmployeeId(201L);
        task.setAssignedHrUserId(101L);
        task.setShopDeptId(1171L);
        task.setStatus(OaSignTaskStatus.PENDING_SIGN.name());
        task.setVersion(8L);
        when(taskMapper.selectOaSignTaskById(9L)).thenReturn(task);
        when(taskEventService.transition(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    OaSignTask current = invocation.getArgument(0);
                    OaSignTaskStatus target = invocation.getArgument(1);
                    current.setStatus(target.name());
                    current.setVersion(current.getVersion() + 1);
                    return current;
                });
        when(taskMapper.updateTerminalMetadata(eq(9L), any(), any(), eq(101L),
                any(), any(), any(), eq("OPEN"))).thenReturn(1);
        when(eventMapper.insertOaSignEvent(any())).thenAnswer(invocation -> {
            OaSignEvent event = invocation.getArgument(0);
            event.setEventId(700L);
            return 1;
        });
    }

    @Test
    void employeeRefusalAtomicallyTerminatesPackageAndTaskAndNotifiesHr()
    {
        when(packageMapper.markTerminalWithVersion(90L, OaSignPackageStatus.PENDING_SIGN,
                OaSignPackageStatus.REFUSED, 4L, Date.from(NOW),
                "TERMS_DISAGREED", "不同意合同条款", "employee:201")).thenReturn(1);

        OaSignPackage result = service.refuse(90L, refusal(), 201L, "127.0.0.1", "JUnit");

        assertThat(result.getStatus()).isEqualTo(OaSignPackageStatus.REFUSED);
        assertThat(result.getResolutionStatus()).isEqualTo("OPEN");
        assertThat(task.getStatus()).isEqualTo(OaSignTaskStatus.REFUSED.name());
        verify(taskMapper).updateTerminalMetadata(9L, OaSignTaskStatus.REFUSED.name(), 9L,
                101L, Date.from(NOW), "TERMS_DISAGREED", "不同意合同条款", "OPEN");
        verify(outboxService).enqueueRefused(task, result, 700L);
    }

    @Test
    void stagedFirstStageRefusalAlsoClosesDataRequestAndImportRow()
    {
        signPackage.setSigningSequence("SIGNATURE_FIRST");
        task.setScenario("ONBOARD");
        task.setSourceType("MANUAL_SIGN_EXCEL_IMPORT");
        when(onboardDataRequestMapper.countActiveStagedFirstStageLink(
                9L, 90L, 201L, OaSignTaskStatus.PENDING_SIGN.name(),
                OaSignPackageStatus.PENDING_SIGN)).thenReturn(1);
        when(packageMapper.markTerminalWithVersion(90L, OaSignPackageStatus.PENDING_SIGN,
                OaSignPackageStatus.REFUSED, 4L, Date.from(NOW),
                "TERMS_DISAGREED", "不同意合同条款", "employee:201")).thenReturn(1);
        when(onboardDataRequestMapper.cancelStagedFirstStageByTerminal(
                9L, 90L, 201L, OaSignTaskStatus.REFUSED.name(),
                OaSignPackageStatus.REFUSED)).thenReturn(1);
        when(onboardImportRowMapper.terminalizeStagedFirstStage(
                9L, 90L, 201L, OaSignTaskStatus.REFUSED.name(),
                OaSignPackageStatus.REFUSED, "REFUSED")).thenReturn(1);

        OaSignPackage result = service.refuse(90L, refusal(), 201L, null, null);

        assertThat(result.getStatus()).isEqualTo(OaSignPackageStatus.REFUSED);
        verify(onboardDataRequestMapper).cancelStagedFirstStageByTerminal(
                9L, 90L, 201L, OaSignTaskStatus.REFUSED.name(),
                OaSignPackageStatus.REFUSED);
        verify(onboardImportRowMapper).terminalizeStagedFirstStage(
                9L, 90L, 201L, OaSignTaskStatus.REFUSED.name(),
                OaSignPackageStatus.REFUSED, "REFUSED");
    }

    @Test
    void stagedFirstStageTerminalLinkageFailureAbortsBeforeNotification()
    {
        signPackage.setSigningSequence("SIGNATURE_FIRST");
        task.setScenario("ONBOARD");
        task.setSourceType("MANUAL_SIGN_EXCEL_IMPORT");
        when(onboardDataRequestMapper.countActiveStagedFirstStageLink(
                9L, 90L, 201L, OaSignTaskStatus.PENDING_SIGN.name(),
                OaSignPackageStatus.PENDING_SIGN)).thenReturn(1);
        when(packageMapper.markTerminalWithVersion(90L, OaSignPackageStatus.PENDING_SIGN,
                OaSignPackageStatus.REFUSED, 4L, Date.from(NOW),
                "TERMS_DISAGREED", "不同意合同条款", "employee:201")).thenReturn(1);

        assertThatThrownBy(() -> service.refuse(90L, refusal(), 201L, null, null))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("首阶段终态联动失败");

        verify(outboxService, never()).enqueueRefused(any(), any(), any());
    }

    @Test
    void stagedFirstStageWithoutExactImportLinkFailsBeforePackageMutation()
    {
        signPackage.setSigningSequence("SIGNATURE_FIRST");
        task.setSourceType("MANUAL_SIGN_EXCEL_IMPORT");

        assertThatThrownBy(() -> service.refuse(90L, refusal(), 201L, null, null))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("关联不完整或重复");

        verify(packageMapper, never()).markTerminalWithVersion(any(), any(), any(), any(),
                any(), any(), any(), any());
    }

    @Test
    void duplicateRefusalReturnsSameTerminalWithoutNewEventOrNotification()
    {
        signPackage.setStatus(OaSignPackageStatus.REFUSED);
        OaSignEvent repeated = new OaSignEvent();
        repeated.setPackageId(90L);
        when(eventMapper.selectEventByTypeAndRequestId("PACKAGE_REFUSED", "req-1"))
                .thenReturn(repeated);

        assertThat(service.refuse(90L, refusal(), 201L, null, null)).isSameAs(signPackage);

        verify(packageMapper, never()).markTerminalWithVersion(any(), any(), any(), any(),
                any(), any(), any(), any());
        verify(outboxService, never()).enqueueRefused(any(), any(), any());
    }

    @Test
    void finalStageRefusalUsesFinalDocumentVersionAfterTerminalTransition()
    {
        signPackage.setStatus(OaSignPackageStatus.PENDING_FINAL_CONFIRM);
        signPackage.setFinalDocumentVersion("SP-90-V2");
        task.setStatus(OaSignTaskStatus.PENDING_FINAL_CONFIRM.name());
        OaSignPackageRefuseRequest request = refusal();
        request.setDocumentVersion("SP-90-V2");
        when(packageMapper.markTerminalWithVersion(90L,
                OaSignPackageStatus.PENDING_FINAL_CONFIRM, OaSignPackageStatus.REFUSED,
                4L, Date.from(NOW), "TERMS_DISAGREED", "不同意合同条款", "employee:201"))
                .thenReturn(1);

        OaSignPackage result = service.refuse(90L, request, 201L, null, null);

        assertThat(result.getStatus()).isEqualTo(OaSignPackageStatus.REFUSED);
        assertThat(result.getFinalDocumentVersion()).isEqualTo("SP-90-V2");
    }

    @Test
    void refusalFailsClosedForMissingDeadlinePolicyBeforeAnyMutation()
    {
        signPackage.setDeadlinePolicySource(null);

        assertThatThrownBy(() -> service.refuse(90L, refusal(), 201L, null, null))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("期限策略缺失");

        verify(packageMapper, never()).markTerminalWithVersion(any(), any(), any(), any(),
                any(), any(), any(), any());
    }

    @Test
    void dueCandidateExpiresButPendingCompanyNeverDoes()
    {
        signPackage.setSignDeadline(Date.from(Instant.parse("2026-07-17T02:03:03Z")));
        when(packageMapper.markTerminalWithVersion(90L, OaSignPackageStatus.PENDING_SIGN,
                OaSignPackageStatus.EXPIRED, 4L, Date.from(NOW),
                "SIGN_DEADLINE_ELAPSED", "员工未在签署截止时间前完成签约", "system"))
                .thenReturn(1);

        assertThat(service.expireOne(signPackage)).isTrue();
        verify(outboxService).enqueueExpired(task, signPackage);

        signPackage.setStatus(OaSignPackageStatus.PENDING_COMPANY);
        signPackage.setVersion(5L);
        assertThat(service.expireOne(signPackage)).isFalse();
    }

    @Test
    void stagedFirstStageExpiryAlsoClosesDataRequestAndImportRow()
    {
        signPackage.setSigningSequence("SIGNATURE_FIRST");
        signPackage.setSignDeadline(Date.from(Instant.parse("2026-07-17T02:03:03Z")));
        task.setScenario("ONBOARD");
        task.setSourceType("MANUAL_SIGN_EXCEL_IMPORT");
        when(onboardDataRequestMapper.countActiveStagedFirstStageLink(
                9L, 90L, 201L, OaSignTaskStatus.PENDING_SIGN.name(),
                OaSignPackageStatus.PENDING_SIGN)).thenReturn(1);
        when(packageMapper.markTerminalWithVersion(90L, OaSignPackageStatus.PENDING_SIGN,
                OaSignPackageStatus.EXPIRED, 4L, Date.from(NOW),
                "SIGN_DEADLINE_ELAPSED", "员工未在签署截止时间前完成签约", "system"))
                .thenReturn(1);
        when(onboardDataRequestMapper.cancelStagedFirstStageByTerminal(
                9L, 90L, 201L, OaSignTaskStatus.EXPIRED.name(),
                OaSignPackageStatus.EXPIRED)).thenReturn(1);
        when(onboardImportRowMapper.terminalizeStagedFirstStage(
                9L, 90L, 201L, OaSignTaskStatus.EXPIRED.name(),
                OaSignPackageStatus.EXPIRED, "EXPIRED")).thenReturn(1);

        assertThat(service.expireOne(signPackage)).isTrue();

        verify(onboardDataRequestMapper).cancelStagedFirstStageByTerminal(
                9L, 90L, 201L, OaSignTaskStatus.EXPIRED.name(),
                OaSignPackageStatus.EXPIRED);
        verify(onboardImportRowMapper).terminalizeStagedFirstStage(
                9L, 90L, 201L, OaSignTaskStatus.EXPIRED.name(),
                OaSignPackageStatus.EXPIRED, "EXPIRED");
    }

    @Test
    void finalConfirmationStageNeverTouchesOnboardFirstStageLinkage()
    {
        signPackage.setStatus(OaSignPackageStatus.PENDING_FINAL_CONFIRM);
        signPackage.setFinalDocumentVersion("SP-90-V2");
        task.setStatus(OaSignTaskStatus.PENDING_FINAL_CONFIRM.name());
        OaSignPackageRefuseRequest request = refusal();
        request.setDocumentVersion("SP-90-V2");
        when(packageMapper.markTerminalWithVersion(90L,
                OaSignPackageStatus.PENDING_FINAL_CONFIRM, OaSignPackageStatus.REFUSED,
                4L, Date.from(NOW), "TERMS_DISAGREED", "不同意合同条款", "employee:201"))
                .thenReturn(1);

        service.refuse(90L, request, 201L, null, null);

        verify(onboardImportRowMapper, never()).terminalizeStagedFirstStage(
                any(), any(), any(), any(), any(), any());
    }

    @Test
    void sentPackageWithoutManagedTaskFailsClosedBeforeTerminalMutation()
    {
        signPackage.setTaskId(null);

        assertThatThrownBy(() -> service.refuse(90L, refusal(), 201L, null, null))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("缺少受控任务关联");

        verify(packageMapper, never()).markTerminalWithVersion(any(), any(), any(), any(),
                any(), any(), any(), any());
    }

    @Test
    void hrCanCloseRefusedPackageWithoutChangingItsTerminalStatus()
    {
        signPackage.setStatus(OaSignPackageStatus.REFUSED);
        signPackage.setResolutionStatus("OPEN");
        task.setStatus(OaSignTaskStatus.REFUSED.name());
        task.setResolutionStatus("OPEN");
        when(packageMapper.updateResolutionWithVersion(90L, OaSignPackageStatus.REFUSED, 4L,
                "OPEN", "CLOSED", 101L, Date.from(NOW), "EMPLOYEE_CONTACTED",
                "已联系员工，关闭本次签约", null, "唯一HR")).thenReturn(1);
        when(taskMapper.updateResolutionWithVersion(9L, OaSignTaskStatus.REFUSED.name(), 8L,
                101L, "OPEN", "CLOSED", 101L, Date.from(NOW), "EMPLOYEE_CONTACTED",
                "已联系员工，关闭本次签约", null)).thenReturn(1);

        OaSignPackage result = service.resolve(task, signPackage,
                resolution("CLOSE", null, null), 101L, "唯一HR", null, null);

        assertThat(result.getStatus()).isEqualTo(OaSignPackageStatus.REFUSED);
        assertThat(result.getResolutionStatus()).isEqualTo("CLOSED");
        assertThat(task.getStatus()).isEqualTo(OaSignTaskStatus.REFUSED.name());
        assertThat(task.getResolutionStatus()).isEqualTo("CLOSED");
    }

    @Test
    void extensionMovesBothDeadlinesByCalendarDaysBeforeExpiry()
    {
        Date oldDeadline = Date.from(Instant.parse("2026-07-18T15:59:59Z"));
        Date newDeadline = Date.from(Instant.parse("2026-07-20T15:59:59Z"));
        signPackage.setSignDeadline(oldDeadline);
        task.setSignDeadline(oldDeadline);
        when(packageMapper.extendDeadlineWithVersion(90L, OaSignPackageStatus.PENDING_SIGN, 4L,
                null, newDeadline, 101L, Date.from(NOW), "BUSINESS_APPROVED",
                "员工申请延期两天", "唯一HR")).thenReturn(1);
        when(taskMapper.extendDeadlineWithVersion(9L, OaSignTaskStatus.PENDING_SIGN.name(), 8L,
                101L, null, newDeadline, 101L, Date.from(NOW), "BUSINESS_APPROVED",
                "员工申请延期两天")).thenReturn(1);

        OaSignPackage result = service.resolve(task, signPackage,
                resolution("EXTEND", 2, null), 101L, "唯一HR", null, null);

        assertThat(result.getSignDeadline()).isEqualTo(newDeadline);
        assertThat(task.getSignDeadline()).isEqualTo(newDeadline);
        assertThat(result.getStatus()).isEqualTo(OaSignPackageStatus.PENDING_SIGN);
        assertThat(task.getStatus()).isEqualTo(OaSignTaskStatus.PENDING_SIGN.name());
    }

    private OaSignPackageRefuseRequest refusal()
    {
        OaSignPackageRefuseRequest request = new OaSignPackageRefuseRequest();
        request.setRequestId("req-1");
        request.setDocumentVersion("SP-90-V1");
        request.setReasonCode("TERMS_DISAGREED");
        request.setReasonDetail("不同意合同条款");
        return request;
    }

    private OaSignExceptionResolutionRequest resolution(String action,
            Integer extensionDays, Long replacementPlanVersionId)
    {
        OaSignExceptionResolutionRequest request = new OaSignExceptionResolutionRequest();
        request.setRequestId("resolve-1");
        request.setAction(action);
        request.setDocumentVersion("SP-90-V1");
        request.setReasonCode("EXTEND".equals(action) ? "BUSINESS_APPROVED" : "EMPLOYEE_CONTACTED");
        request.setReasonDetail("EXTEND".equals(action) ? "员工申请延期两天" : "已联系员工，关闭本次签约");
        request.setExpectedVersion(8L);
        request.setExtensionDays(extensionDays);
        request.setReplacementPlanVersionId(replacementPlanVersionId);
        return request;
    }
}
