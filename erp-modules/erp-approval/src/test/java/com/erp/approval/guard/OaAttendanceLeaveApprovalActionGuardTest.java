package com.erp.approval.guard;

import static com.erp.approval.constant.ApprovalRuntimeConstants.ACTION_APPROVE;
import static com.erp.approval.constant.ApprovalRuntimeConstants.ACTION_REJECT;
import static com.erp.approval.constant.ApprovalRuntimeConstants.ACTION_RETURN;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.erp.approval.api.domain.ApprovalBusinessActionValidationRequest;
import com.erp.approval.api.domain.ApprovalBusinessActionValidationResponse;
import com.erp.approval.constant.ApprovalBusinessCodes;
import com.erp.approval.domain.ApprovalInstance;
import com.erp.approval.guard.client.OaApprovalActionGuardClient;
import com.erp.common.core.constant.SecurityConstants;
import com.erp.common.core.domain.R;
import com.erp.common.core.exception.ServiceException;

@DisplayName("请假审批动作证据守卫")
class OaAttendanceLeaveApprovalActionGuardTest
{
    private OaApprovalActionGuardClient client;
    private OaAttendanceLeaveApprovalActionGuard guard;

    @BeforeEach
    void setUp()
    {
        client = mock(OaApprovalActionGuardClient.class);
        guard = new OaAttendanceLeaveApprovalActionGuard(client);
    }

    @Test
    @DisplayName("OA 业务证据拒绝时审批中心失败关闭")
    void shouldFailClosedWhenBusinessOwnerRejectsEvidence()
    {
        ApprovalBusinessActionValidationResponse rejected =
                new ApprovalBusinessActionValidationResponse();
        rejected.setAccepted(false);
        rejected.setCode("ATTACHMENT_COUNT_CHANGED");
        rejected.setMessage("请假附件与提交时快照不一致，请退回核对");
        when(client.validateAction(any(), eq(SecurityConstants.INNER)))
                .thenReturn(R.ok(rejected));

        assertThatThrownBy(() -> guard.validate(instance(), ACTION_APPROVE,
                9L)).isInstanceOf(ServiceException.class)
                .hasMessageContaining("附件")
                .hasMessageContaining("快照");
    }

    @Test
    @DisplayName("OA 无响应时不得继续同意")
    void shouldFailClosedWhenBusinessOwnerIsUnavailable()
    {
        when(client.validateAction(any(), eq(SecurityConstants.INNER)))
                .thenThrow(new RuntimeException("timeout"));

        assertThatThrownBy(() -> guard.validate(instance(), ACTION_APPROVE,
                9L)).isInstanceOf(ServiceException.class)
                .hasMessageContaining("暂不可用");
    }

    @Test
    @DisplayName("拒绝动作不被缺失证据阻断")
    void shouldAllowRejectSoApplicantCanCorrectEvidence()
    {
        guard.validate(instance(), ACTION_REJECT, 9L);

        verify(client, never()).validateAction(
                any(ApprovalBusinessActionValidationRequest.class), any());
    }

    @Test
    @DisplayName("退回动作不被缺失证据阻断")
    void shouldAllowReturnSoApplicantCanCorrectEvidence()
    {
        guard.validate(instance(), ACTION_RETURN, 9L);

        verify(client, never()).validateAction(
                any(ApprovalBusinessActionValidationRequest.class), any());
    }

    private ApprovalInstance instance()
    {
        ApprovalInstance value = new ApprovalInstance();
        value.setInstanceId(44L);
        value.setBusinessCode(ApprovalBusinessCodes.OA_ATTENDANCE_LEAVE);
        value.setBusinessId("12");
        value.setBusinessRound(1);
        value.setApplicantUserId(7L);
        value.setAnchorDeptId(101L);
        value.setBusinessSnapshot("{\"attachmentCount\":1}");
        return value;
    }
}
