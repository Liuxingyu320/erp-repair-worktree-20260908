package com.erp.approval.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.erp.approval.constant.ApprovalBusinessCodes;

@DisplayName("考勤审批权限策略")
class ApprovalPermissionPolicyAttendanceTest
{
    private final ApprovalPermissionPolicy policy =
            new ApprovalPermissionPolicy();

    @Test
    @DisplayName("请假与补卡使用互相独立的审批权限")
    void shouldUseSeparateLeaveAndCorrectionPermissions()
    {
        assertThat(policy.requiredPermission(
                ApprovalBusinessCodes.OA_ATTENDANCE_LEAVE))
                .isEqualTo("oa:attendance:leave:approve");
        assertThat(policy.requiredPermission(
                ApprovalBusinessCodes.OA_ATTENDANCE_CORRECTION))
                .isEqualTo("oa:attendance:correction:approve");
        assertThat(policy.isCandidatePermissionAllowed(
                ApprovalBusinessCodes.OA_ATTENDANCE_LEAVE,
                "oa:attendance:correction:approve")).isFalse();
        assertThat(policy.supportedBusinessCodes())
                .contains(ApprovalBusinessCodes.OA_ATTENDANCE_LEAVE,
                        ApprovalBusinessCodes.OA_ATTENDANCE_CORRECTION);
    }
}
