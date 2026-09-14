package com.erp.oa.attendance.leave;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import com.erp.approval.api.RemoteApprovalService;
import com.erp.approval.api.domain.ApprovalStartRequest;
import com.erp.common.security.shop.ShopScopeService;
import com.erp.oa.attendance.leave.AttendanceLeaveModels.LeaveRequest;
import com.erp.oa.attendance.leave.AttendanceLeaveModels.LeaveType;
import com.erp.oa.service.BusinessFeatureGate;

class AttendanceLeaveApprovalSnapshotTest
{
    @Test
    void approvalSnapshotCarriesImmutableAttachmentPolicyAndCount()
    {
        ShopScopeService shopScopeService = mock(ShopScopeService.class);
        when(shopScopeService.resolveShopDeptName(101L)).thenReturn("一店");
        AttendanceLeaveService service = new AttendanceLeaveService(
                mock(AttendanceLeaveMapper.class),
                mock(AttendanceLeaveAttachmentStorage.class),
                mock(AttendanceLeaveApprovalOutboxService.class),
                mock(AttendanceLeaveApprovalAfterCommitTrigger.class),
                mock(RemoteApprovalService.class), shopScopeService,
                mock(BusinessFeatureGate.class), legacyQuota());

        LeaveRequest request = new LeaveRequest();
        request.leaveRequestId = 42L;
        request.leaveRequestNo = "AL-42";
        request.userId = 9L;
        request.userName = "张三";
        request.shopId = 101L;
        request.leaveTypeName = "病假";
        request.startTime = LocalDateTime.of(2026, 8, 24, 9, 0);
        request.endTime = LocalDateTime.of(2026, 8, 24, 18, 0);
        request.totalMinutes = 480;
        request.reason = "就医";
        request.businessRound = 1;

        LeaveType type = new LeaveType();
        type.typeCode = "SICK";
        type.attachmentRequired = true;

        ApprovalStartRequest approval = service.buildApprovalRequest(request,
                type, 2);

        assertThat(approval.getVariables())
                .containsEntry("attachmentCount", 2)
                .containsEntry("attachmentRequired", true);
    }

    private static com.erp.oa.attendance.leave.balance.AttendanceLeaveQuotaService legacyQuota()
    {
        return org.mockito.Mockito.mock(com.erp.oa.attendance.leave.balance.AttendanceLeaveQuotaService.class, invocation -> {
            String method=invocation.getMethod().getName();
            if("hydrate".equals(method) || "copyPolicy".equals(method))return invocation.getArgument(0);
            return org.mockito.Answers.RETURNS_DEFAULTS.answer(invocation);
        });
    }
}
