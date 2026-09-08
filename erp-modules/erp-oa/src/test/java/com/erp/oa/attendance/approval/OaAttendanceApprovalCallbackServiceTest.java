package com.erp.oa.attendance.approval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.erp.approval.api.domain.ApprovalBusinessCallbackRequest;
import com.erp.approval.api.domain.ApprovalBusinessCallbackResponse;
import com.erp.approval.api.domain.ApprovalBusinessActionValidationRequest;
import com.erp.approval.api.domain.ApprovalBusinessActionValidationResponse;
import com.fasterxml.jackson.databind.ObjectMapper;

@DisplayName("考勤审批回调")
class OaAttendanceApprovalCallbackServiceTest
{
    private OaAttendanceApprovalCallbackMapper mapper;
    private OaAttendanceApprovalCallbackService service;

    @BeforeEach
    void setUp()
    {
        mapper = mock(OaAttendanceApprovalCallbackMapper.class);
        service = new OaAttendanceApprovalCallbackService(mapper,
                new ObjectMapper());
    }

    @Test
    @DisplayName("只在实例、轮次和目标状态全部匹配时批准请假")
    void shouldApproveMatchingLeaveCallback()
    {
        OaAttendanceApprovalState current = state();
        when(mapper.selectLeaveForUpdate(12L)).thenReturn(current);
        when(mapper.updateLeaveDecision(eq(12L), eq("PENDING"),
                eq(3L), eq("APPROVED"), eq("event-1"), any()))
                .thenReturn(1);

        ApprovalBusinessCallbackResponse response = service.apply(
                request(OaAttendanceApprovalCallbackService.LEAVE,
                        "APPROVE", "APPROVED"));

        assertThat(response.getAccepted()).isTrue();
        assertThat(response.getInvalidated()).isFalse();
        assertThat(response.getCode()).isEqualTo("ACCEPTED");
        verify(mapper).invalidateLeaveDayResults(eq(12L), any());
    }

    @Test
    @DisplayName("目标状态被篡改时不更新业务")
    void shouldRejectMismatchedPayloadTarget()
    {
        when(mapper.selectCorrectionForUpdate(12L)).thenReturn(state());

        ApprovalBusinessCallbackResponse response = service.apply(
                request(OaAttendanceApprovalCallbackService.CORRECTION,
                        "APPROVE", "REJECTED"));

        assertThat(response.getAccepted()).isFalse();
        assertThat(response.getInvalidated()).isFalse();
        assertThat(response.getCode()).isEqualTo("INVALID_APPROVAL_TARGET");
        verify(mapper, never()).updateCorrectionDecision(any(), any(), any(),
                any(), any(), any());
    }

    @Test
    @DisplayName("重复事件键幂等返回成功")
    void shouldAcceptDuplicateEvent()
    {
        OaAttendanceApprovalState current = state();
        current.setLastApprovalEventKey("event-1");
        when(mapper.selectLeaveForUpdate(12L)).thenReturn(current);

        ApprovalBusinessCallbackResponse response = service.apply(
                request(OaAttendanceApprovalCallbackService.LEAVE,
                        "APPROVE", "APPROVED"));

        assertThat(response.getAccepted()).isTrue();
        assertThat(response.getCode()).isEqualTo("IDEMPOTENT");
        verify(mapper, never()).updateLeaveDecision(any(), any(), any(),
                any(), any(), any());
    }

    @Test
    @DisplayName("补卡审批终止后重开每日结算")
    void shouldMapTerminationToCancelledBusinessStatus()
    {
        when(mapper.selectCorrectionForUpdate(12L)).thenReturn(state());
        when(mapper.updateCorrectionDecision(eq(12L), eq("PENDING"),
                eq(3L), eq("CANCELLED"), eq("event-1"), any()))
                .thenReturn(1);

        ApprovalBusinessCallbackResponse response = service.apply(
                request(OaAttendanceApprovalCallbackService.CORRECTION,
                        "TERMINATE", "TERMINATED"));

        assertThat(response.getAccepted()).isTrue();
        verify(mapper).updateCorrectionDecision(eq(12L), eq("PENDING"),
                eq(3L), eq("CANCELLED"), eq("event-1"), any());
        verify(mapper).invalidateCorrectionDayResult(eq(12L), any());
    }

    @Test
    @DisplayName("请假驳回后也重开每日结算以清除待审证据")
    void shouldInvalidateLeaveDayResultsAfterRejection()
    {
        when(mapper.selectLeaveForUpdate(12L)).thenReturn(state());
        when(mapper.updateLeaveDecision(eq(12L), eq("PENDING"),
                eq(3L), eq("REJECTED"), eq("event-1"), any()))
                .thenReturn(1);

        ApprovalBusinessCallbackResponse response = service.apply(
                request(OaAttendanceApprovalCallbackService.LEAVE,
                        "REJECT", "REJECTED"));

        assertThat(response.getAccepted()).isTrue();
        verify(mapper).invalidateLeaveDayResults(eq(12L), any());
    }

    @Test
    @DisplayName("已批准请假可由终止事件撤销并重开每日结算")
    void shouldTerminateApprovedLeaveAndInvalidateDayResults()
    {
        OaAttendanceApprovalState current = state();
        current.setStatus("APPROVED");
        when(mapper.selectLeaveForUpdate(12L)).thenReturn(current);
        when(mapper.updateLeaveDecision(eq(12L), eq("APPROVED"),
                eq(3L), eq("CANCELLED"), eq("event-1"), any()))
                .thenReturn(1);

        ApprovalBusinessCallbackResponse response = service.apply(
                request(OaAttendanceApprovalCallbackService.LEAVE,
                        "TERMINATE", "TERMINATED"));

        assertThat(response.getAccepted()).isTrue();
        verify(mapper).invalidateLeaveDayResults(eq(12L), any());
    }

    @Test
    @DisplayName("补卡批准后必须使原每日结算失效")
    void shouldInvalidateCorrectionDayResultAfterApproval()
    {
        when(mapper.selectCorrectionForUpdate(12L)).thenReturn(state());
        when(mapper.updateCorrectionDecision(eq(12L), eq("PENDING"),
                eq(3L), eq("APPROVED"), eq("event-1"), any()))
                .thenReturn(1);

        ApprovalBusinessCallbackResponse response = service.apply(
                request(OaAttendanceApprovalCallbackService.CORRECTION,
                        "APPROVE", "APPROVED"));

        assertThat(response.getAccepted()).isTrue();
        verify(mapper).invalidateCorrectionDayResult(eq(12L), any());
    }

    @Test
    @DisplayName("同意前实例、门店与附件快照一致才放行")
    void shouldAcceptMatchingLeaveEvidenceBeforeApprove()
    {
        when(mapper.selectLeave(12L)).thenReturn(state());
        when(mapper.countLeaveAttachments(12L)).thenReturn(2);

        ApprovalBusinessActionValidationResponse response =
                service.validateAction(validationRequest(2, true));

        assertThat(response.getAccepted()).isTrue();
        assertThat(response.getCode()).isEqualTo("ACCEPTED");
    }

    @Test
    @DisplayName("实时附件数量与提交快照不一致时拒绝同意")
    void shouldRejectChangedLeaveEvidenceBeforeApprove()
    {
        when(mapper.selectLeave(12L)).thenReturn(state());
        when(mapper.countLeaveAttachments(12L)).thenReturn(1);

        ApprovalBusinessActionValidationResponse response =
                service.validateAction(validationRequest(2, true));

        assertThat(response.getAccepted()).isFalse();
        assertThat(response.getCode()).isEqualTo("ATTACHMENT_COUNT_CHANGED");
    }

    @Test
    @DisplayName("必传附件为空时服务端拒绝同意")
    void shouldRejectMissingRequiredLeaveEvidenceBeforeApprove()
    {
        when(mapper.selectLeave(12L)).thenReturn(state());
        when(mapper.countLeaveAttachments(12L)).thenReturn(0);

        ApprovalBusinessActionValidationResponse response =
                service.validateAction(validationRequest(0, true));

        assertThat(response.getAccepted()).isFalse();
        assertThat(response.getCode()).isEqualTo(
                "REQUIRED_ATTACHMENT_MISSING");
    }

    @Test
    @DisplayName("门店锚点与业务归属不一致时拒绝同意")
    void shouldRejectMismatchedLeaveShopAnchorBeforeApprove()
    {
        ApprovalBusinessActionValidationRequest request =
                validationRequest(2, true);
        request.setAnchorDeptId(202L);
        when(mapper.selectLeave(12L)).thenReturn(state());

        ApprovalBusinessActionValidationResponse response =
                service.validateAction(request);

        assertThat(response.getAccepted()).isFalse();
        assertThat(response.getCode()).isEqualTo("BUSINESS_SCOPE_MISMATCH");
        verify(mapper, never()).countLeaveAttachments(any());
    }

    @Test
    @DisplayName("审批实例或业务轮次不一致时拒绝同意")
    void shouldRejectStaleLeaveInstanceBeforeApprove()
    {
        ApprovalBusinessActionValidationRequest request =
                validationRequest(2, true);
        request.setInstanceId(45L);
        when(mapper.selectLeave(12L)).thenReturn(state());

        ApprovalBusinessActionValidationResponse response =
                service.validateAction(request);

        assertThat(response.getAccepted()).isFalse();
        assertThat(response.getCode()).isEqualTo("BUSINESS_LINK_MISMATCH");
        verify(mapper, never()).countLeaveAttachments(any());
    }

    @Test
    @DisplayName("快照门店与业务门店不一致时拒绝同意")
    void shouldRejectMismatchedSnapshotShopBeforeApprove()
    {
        ApprovalBusinessActionValidationRequest request =
                validationRequest(2, true);
        request.setBusinessSnapshot("{\"leaveRequestId\":12,"
                + "\"shopId\":202,\"attachmentCount\":2,"
                + "\"attachmentRequired\":true}");
        when(mapper.selectLeave(12L)).thenReturn(state());

        ApprovalBusinessActionValidationResponse response =
                service.validateAction(request);

        assertThat(response.getAccepted()).isFalse();
        assertThat(response.getCode()).isEqualTo("SNAPSHOT_SCOPE_MISMATCH");
        verify(mapper, never()).countLeaveAttachments(any());
    }

    @Test
    @DisplayName("非法快照JSON失败关闭")
    void shouldRejectInvalidSnapshotJsonBeforeApprove()
    {
        ApprovalBusinessActionValidationRequest request =
                validationRequest(2, true);
        request.setBusinessSnapshot("not-json");
        when(mapper.selectLeave(12L)).thenReturn(state());

        ApprovalBusinessActionValidationResponse response =
                service.validateAction(request);

        assertThat(response.getAccepted()).isFalse();
        assertThat(response.getCode()).isEqualTo(
                "INVALID_BUSINESS_SNAPSHOT");
        verify(mapper, never()).countLeaveAttachments(any());
    }

    @Test
    @DisplayName("旧快照缺少附件策略时失败关闭并要求退回重提")
    void shouldRejectIncompleteLegacyEvidenceSnapshot()
    {
        ApprovalBusinessActionValidationRequest request =
                validationRequest(2, true);
        request.setBusinessSnapshot(
                "{\"leaveRequestId\":12,\"shopId\":101}");
        when(mapper.selectLeave(12L)).thenReturn(state());

        ApprovalBusinessActionValidationResponse response =
                service.validateAction(request);

        assertThat(response.getAccepted()).isFalse();
        assertThat(response.getCode()).isEqualTo(
                "INCOMPLETE_EVIDENCE_SNAPSHOT");
        verify(mapper, never()).countLeaveAttachments(any());
    }

    private OaAttendanceApprovalState state()
    {
        OaAttendanceApprovalState value = new OaAttendanceApprovalState();
        value.setBusinessId(12L);
        value.setUserId(7L);
        value.setShopId(101L);
        value.setStatus("PENDING");
        value.setBusinessRound(1);
        value.setApprovalInstanceId(44L);
        value.setRowVersion(3L);
        return value;
    }

    private ApprovalBusinessCallbackRequest request(String businessCode,
            String action, String targetStatus)
    {
        ApprovalBusinessCallbackRequest value =
                new ApprovalBusinessCallbackRequest();
        value.setEventKey("event-1");
        value.setInstanceId(44L);
        value.setBusinessCode(businessCode);
        value.setBusinessId("12");
        value.setBusinessRound(1);
        value.setAction(action);
        value.setPayload("{\"targetStatus\":\"" + targetStatus + "\"}");
        return value;
    }

    private ApprovalBusinessActionValidationRequest validationRequest(
            int attachmentCount, boolean attachmentRequired)
    {
        ApprovalBusinessActionValidationRequest value =
                new ApprovalBusinessActionValidationRequest();
        value.setInstanceId(44L);
        value.setBusinessCode(OaAttendanceApprovalCallbackService.LEAVE);
        value.setBusinessId("12");
        value.setBusinessRound(1);
        value.setAction("APPROVE");
        value.setApplicantUserId(7L);
        value.setAnchorDeptId(101L);
        value.setBusinessSnapshot("{\"leaveRequestId\":12,"
                + "\"shopId\":101,\"attachmentCount\":"
                + attachmentCount + ",\"attachmentRequired\":"
                + attachmentRequired + "}");
        return value;
    }
}
