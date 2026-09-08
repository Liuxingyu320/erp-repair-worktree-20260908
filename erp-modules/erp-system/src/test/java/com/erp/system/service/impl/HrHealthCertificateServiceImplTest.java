package com.erp.system.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import com.erp.approval.api.RemoteApprovalService;
import com.erp.approval.api.domain.ApprovalBusinessCallbackRequest;
import com.erp.approval.api.domain.ApprovalBusinessCallbackResponse;
import com.erp.approval.api.domain.ApprovalStartRequest;
import com.erp.common.core.constant.SecurityConstants;
import com.erp.common.core.domain.R;
import com.erp.common.core.exception.ServiceException;
import com.erp.system.api.RemoteFileService;
import com.erp.system.api.domain.DriveBusinessFile;
import com.erp.system.api.domain.SysUser;
import com.erp.system.domain.HrHealthCertificate;
import com.erp.system.domain.HrHealthCertificateApprovalStartOutbox;
import com.erp.system.domain.dto.HrHealthCertificateReviewRequest;
import com.erp.system.domain.dto.HrHealthCertificateSubmitRequest;
import com.erp.system.domain.vo.HrHealthCertificateVo;
import com.erp.system.mapper.HrHealthCertificateMapper;
import com.fasterxml.jackson.databind.ObjectMapper;

class HrHealthCertificateServiceImplTest
{
    private HrHealthCertificateMapper mapper;
    private RemoteFileService remoteFileService;
    private HrEmployeeAccessService employeeAccessService;
    private HrHealthCertificateFeatureService featureService;
    private HrHealthCertificateApprovalStartOutboxService outboxService;
    private HrHealthCertificateApprovalStartAfterCommitTrigger afterCommitTrigger;
    private RemoteApprovalService approvalService;
    private HrHealthCertificateServiceImpl service;

    @BeforeEach
    void setUp()
    {
        mapper = mock(HrHealthCertificateMapper.class);
        remoteFileService = mock(RemoteFileService.class);
        employeeAccessService = mock(HrEmployeeAccessService.class);
        featureService = mock(HrHealthCertificateFeatureService.class);
        outboxService = mock(
                HrHealthCertificateApprovalStartOutboxService.class);
        afterCommitTrigger = mock(
                HrHealthCertificateApprovalStartAfterCommitTrigger.class);
        approvalService = mock(RemoteApprovalService.class);
        SysUser employee = new SysUser();employee.setUserId(7L);employee.setDeptId(10L);
        employee.setDelFlag("0");employee.setStatus("0");
        when(employeeAccessService.findActiveScoped(any())).thenReturn(employee);
        service = new HrHealthCertificateServiceImpl(mapper, employeeAccessService,
                mock(HrHealthCertificateAccessService.class),
                remoteFileService, featureService, outboxService,
                afterCommitTrigger,
                Clock.fixed(Instant.parse("2026-07-13T00:00:00Z"),
                        ZoneId.of("Asia/Shanghai")));
        ReflectionTestUtils.setField(service, "approvalService",
                approvalService);
        ReflectionTestUtils.setField(service, "objectMapper",
                new ObjectMapper());
    }

    @Test
    void draftValidatesControlledAttachmentBeforePersisting()
    {
        DriveBusinessFile file = new DriveBusinessFile();
        file.setNodeId(99L);
        file.setContentType("image/jpeg");
        when(remoteFileService.validateDriveBusinessFile(99L,
                "HEALTH_CERTIFICATE", SecurityConstants.INNER))
                .thenReturn(R.ok(file));
        when(mapper.insertCertificate(any())).thenAnswer(invocation -> {
            HrHealthCertificate value = invocation.getArgument(0);
            value.setCertificateId(88L);
            return 1;
        });
        when(mapper.selectById(88L)).thenReturn(vo(88L, 7L, 99L));

        HrHealthCertificateVo saved = service.saveMyDraft(7L,
                draft(99L), "employee");

        assertThat(saved.getCertificateId()).isEqualTo(88L);
        assertThat(saved.getAttachmentPresent()).isTrue();
        verify(remoteFileService).validateDriveBusinessFile(99L,
                "HEALTH_CERTIFICATE", SecurityConstants.INNER);
        verify(mapper).insertCertificate(any());
    }

    @Test
    void inaccessibleAttachmentFailsBeforeEmployeeOrCertificateWrite()
    {
        when(remoteFileService.validateDriveBusinessFile(99L,
                "HEALTH_CERTIFICATE", SecurityConstants.INNER))
                .thenReturn(R.fail("无权"));

        assertThatThrownBy(() -> service.saveMyDraft(7L, draft(99L),
                "employee"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("无权绑定");
        verify(mapper, never()).insertCertificate(any());
    }

    @Test
    void closedIntakeRejectsDraftAndSubmitBeforeReadingCertificateTables()
    {
        doThrow(new ServiceException("FEATURE_DISABLED: 健康证受理维护中"))
                .when(featureService).requireIntakeEnabled();

        assertThatThrownBy(()->service.saveMyDraft(7L,draft(null),"employee"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("FEATURE_DISABLED");
        HrHealthCertificateSubmitRequest request=new HrHealthCertificateSubmitRequest();
        request.setCertificateId(88L);request.setVersion(0L);
        assertThatThrownBy(()->service.submitMine(7L,request,"employee"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("FEATURE_DISABLED");

        verify(mapper,never()).insertCertificate(any());
        verify(mapper,never()).selectById(any());
    }

    @Test
    void administratorOrDepartedAccountCannotStartNewIntake()
    {
        when(employeeAccessService.findActiveScoped(any()))
                .thenThrow(new ServiceException("无权访问该员工或记录不存在"));

        assertThatThrownBy(()->service.saveMyDraft(1L,draft(null),"admin"))
                .isInstanceOf(ServiceException.class).hasMessageContaining("在职员工");
        assertThatThrownBy(()->service.saveMyDraft(7L,draft(null),"departed"))
                .isInstanceOf(ServiceException.class).hasMessageContaining("在职员工");
        verify(mapper,never()).insertCertificate(any());
    }

    @Test
    void closedIntakeDoesNotBlockReviewingExistingPendingCertificate()
    {
        doThrow(new ServiceException("FEATURE_DISABLED"))
                .when(featureService).requireIntakeEnabled();
        HrHealthCertificateVo pending=vo(88L,7L,null);pending.setReviewStatus("PENDING_REVIEW");
        when(mapper.selectById(88L)).thenReturn(pending);
        when(mapper.lockEmployeeProfile(7L)).thenReturn(7L);
        when(mapper.reviewCertificate(88L,0L,"APPROVED","Y",9L,"hr",null)).thenReturn(1);
        HrHealthCertificateReviewRequest request=new HrHealthCertificateReviewRequest();
        request.setVersion(0L);request.setDecision("APPROVED");

        assertThat(service.review(88L,request,9L,"hr")).isNotNull();

        verify(featureService,never()).requireIntakeEnabled();
        verify(mapper).reviewCertificate(88L,0L,"APPROVED","Y",9L,"hr",null);
    }

    @Test
    void submitPersistsSnapshotAndOutboxWithoutCallingApprovalInTransaction()
    {
        HrHealthCertificateVo current = vo(88L, 7L, null);
        current.setEmployeeName("员工甲");
        current.setCurrentDeptName("门店一");
        when(mapper.selectByIdForUpdate(88L)).thenReturn(current);
        when(mapper.markApprovalSubmitting(88L, 7L, 0L, 1,
                "employee")).thenReturn(1);
        when(mapper.selectById(88L)).thenReturn(current);
        HrHealthCertificateApprovalStartOutbox outbox =
                new HrHealthCertificateApprovalStartOutbox();
        outbox.setOutboxId(501L);
        when(outboxService.enqueue(eq(current), any(), eq("employee")))
                .thenReturn(outbox);
        HrHealthCertificateSubmitRequest request =
                new HrHealthCertificateSubmitRequest();
        request.setCertificateId(88L);
        request.setVersion(0L);

        HrHealthCertificateVo result = service.submitMine(7L, request,
                "employee");

        ArgumentCaptor<ApprovalStartRequest> command = ArgumentCaptor
                .forClass(ApprovalStartRequest.class);
        verify(outboxService).enqueue(eq(current), command.capture(),
                eq("employee"));
        assertThat(command.getValue().getIdempotencyKey())
                .isEqualTo("HR_HEALTH_CERTIFICATE:88:1");
        assertThat(command.getValue().getApplicantDeptId()).isEqualTo(10L);
        assertThat(result.getReviewStatus())
                .isEqualTo("APPROVAL_SUBMITTING");
        verify(afterCommitTrigger).trigger(501L);
        verify(approvalService, never()).start(any(), any());
    }

    @Test
    void enqueueFailureDoesNotRegisterAfterCommitDispatch()
    {
        HrHealthCertificateVo current = vo(88L, 7L, null);
        when(mapper.selectByIdForUpdate(88L)).thenReturn(current);
        when(mapper.markApprovalSubmitting(88L, 7L, 0L, 1,
                "employee")).thenReturn(1);
        when(outboxService.enqueue(eq(current), any(), eq("employee")))
                .thenThrow(new ServiceException("injected outbox failure"));
        HrHealthCertificateSubmitRequest request =
                new HrHealthCertificateSubmitRequest();
        request.setCertificateId(88L);
        request.setVersion(0L);

        assertThatThrownBy(() -> service.submitMine(7L, request, "employee"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("outbox failure");

        verify(afterCommitTrigger, never()).trigger(any());
        verify(approvalService, never()).start(any(), any());
    }

    @Test
    void duplicateSubmittingRequestReusesSameOutboxRound()
    {
        HrHealthCertificateVo current = vo(88L, 7L, null);
        current.setReviewStatus("APPROVAL_SUBMITTING");
        current.setApprovalRound(2);
        current.setVersion(5L);
        when(mapper.selectByIdForUpdate(88L)).thenReturn(current);
        HrHealthCertificateApprovalStartOutbox outbox =
                new HrHealthCertificateApprovalStartOutbox();
        outbox.setOutboxId(502L);
        when(outboxService.selectByCertificateRound(88L, 2))
                .thenReturn(outbox);
        HrHealthCertificateSubmitRequest request =
                new HrHealthCertificateSubmitRequest();
        request.setCertificateId(88L);
        request.setVersion(0L);

        assertThat(service.submitMine(7L, request, "employee"))
                .isSameAs(current);

        verify(afterCommitTrigger).trigger(502L);
        verify(mapper, never()).markApprovalSubmitting(any(), any(), any(),
                any(), any());
        verify(outboxService, never()).enqueue(any(), any(), any());
    }

    @Test
    void earlyCallbackBeforeInstanceLinkExplicitlyRequestsRetry()
    {
        HrHealthCertificateVo current = vo(88L, 7L, null);
        current.setReviewStatus("APPROVAL_SUBMITTING");
        current.setApprovalRound(1);
        current.setApprovalInstanceId(null);
        when(mapper.selectByIdForUpdate(88L)).thenReturn(current);

        ApprovalBusinessCallbackResponse response = service
                .applyApprovalCallback(callback(9001L, 1, "APPROVE"));

        assertThat(response.getAccepted()).isFalse();
        assertThat(response.getInvalidated()).isFalse();
        assertThat(response.getCode()).isEqualTo("APPROVAL_LINK_PENDING");
        verify(mapper, never()).applyApprovalResult(any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void callbackFromOldRoundRemainsStaleAndCannotOverwriteCurrentInstance()
    {
        HrHealthCertificateVo current = vo(88L, 7L, null);
        current.setReviewStatus("APPROVAL_PENDING");
        current.setApprovalRound(2);
        current.setApprovalInstanceId(9002L);
        when(mapper.selectByIdForUpdate(88L)).thenReturn(current);

        ApprovalBusinessCallbackResponse response = service
                .applyApprovalCallback(callback(9001L, 1, "APPROVE"));

        assertThat(response.getAccepted()).isFalse();
        assertThat(response.getInvalidated()).isTrue();
        assertThat(response.getCode()).isEqualTo("STALE_APPROVAL_EVENT");
        verify(mapper, never()).applyApprovalResult(any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void ownerCanResolveOnlyTheAttachmentLinkedToOwnCertificate()
    {
        when(mapper.selectById(88L)).thenReturn(vo(88L, 7L, 99L));

        assertThat(service.resolveAttachmentNode(88L, 7L)).isEqualTo(99L);

        HrHealthCertificateVo missing = vo(89L, 7L, null);
        when(mapper.selectById(89L)).thenReturn(missing);
        assertThatThrownBy(() -> service.resolveAttachmentNode(89L, 7L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("不存在");
    }

    @Test
    void overlongIssuerFailsBeforeCertificateWrite()
    {
        HrHealthCertificate certificate=draft(null);
        certificate.setIssuerName("机".repeat(129));

        assertThatThrownBy(()->service.saveMyDraft(7L,certificate,"employee"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("发证机构不能超过128个字符");

        verify(mapper,never()).insertCertificate(any());
    }

    @Test
    void overlongRejectionReasonFailsBeforeReviewWrite()
    {
        HrHealthCertificateVo pending=vo(88L,7L,null);
        pending.setReviewStatus("PENDING_REVIEW");
        when(mapper.selectById(88L)).thenReturn(pending);
        HrHealthCertificateReviewRequest request=new HrHealthCertificateReviewRequest();
        request.setVersion(0L);
        request.setDecision("REJECTED");
        request.setRejectionReason("原".repeat(301));

        assertThatThrownBy(()->service.review(88L,request,9L,"hr"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("驳回原因不能超过300个字符");

        verify(mapper,never()).lockEmployeeProfile(any());
        verify(mapper,never()).reviewCertificate(any(),any(),any(),any(),any(),any(),any());
    }

    private HrHealthCertificate draft(Long nodeId)
    {
        HrHealthCertificate value = new HrHealthCertificate();
        value.setCertificateNo("HC-001");
        value.setIssuedDate(LocalDate.of(2026, 7, 1));
        value.setExpiresOn(LocalDate.of(2027, 6, 30));
        value.setAttachmentNodeId(nodeId);
        return value;
    }

    private HrHealthCertificateVo vo(Long certificateId, Long userId,
            Long nodeId)
    {
        HrHealthCertificateVo value = new HrHealthCertificateVo();
        value.setCertificateId(certificateId);
        value.setUserId(userId);
        value.setAttachmentNodeId(nodeId);
        value.setReviewStatus("DRAFT");
        value.setIssuedDate(LocalDate.of(2026, 7, 1));
        value.setExpiresOn(LocalDate.of(2027, 6, 30));
        value.setVersion(0L);
        return value;
    }

    private ApprovalBusinessCallbackRequest callback(Long instanceId,
            int round, String action)
    {
        ApprovalBusinessCallbackRequest request =
                new ApprovalBusinessCallbackRequest();
        request.setEventKey("event-" + instanceId + "-" + round);
        request.setInstanceId(instanceId);
        request.setBusinessCode("HR_HEALTH_CERTIFICATE");
        request.setBusinessId("88");
        request.setBusinessRound(round);
        request.setAction(action);
        request.setPayload("{\"targetStatus\":\"APPROVED\"}");
        return request;
    }
}
