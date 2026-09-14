package com.erp.oa.attendance.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import com.erp.approval.api.RemoteApprovalService;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.security.shop.ShopScopeService;
import com.erp.oa.attendance.correction.AttendanceCorrectionApprovalAfterCommitTrigger;
import com.erp.oa.attendance.correction.AttendanceCorrectionApprovalOutboxService;
import com.erp.oa.attendance.correction.AttendanceCorrectionMapper;
import com.erp.oa.attendance.correction.AttendanceCorrectionService;
import com.erp.oa.attendance.leave.AttendanceLeaveApprovalAfterCommitTrigger;
import com.erp.oa.attendance.leave.AttendanceLeaveApprovalOutboxService;
import com.erp.oa.attendance.leave.AttendanceLeaveAttachmentStorage;
import com.erp.oa.attendance.leave.AttendanceLeaveMapper;
import com.erp.oa.attendance.leave.AttendanceLeaveService;
import com.erp.oa.service.BusinessFeatureGate;

class AttendanceLeaveCorrectionFeatureGateTest
{
    @Test
    void leaveRequestFailsBeforeReadingDataWhenFeatureIsDisabled()
    {
        AttendanceLeaveMapper mapper = mock(AttendanceLeaveMapper.class);
        BusinessFeatureGate gate = disabledGate();
        AttendanceLeaveService service = new AttendanceLeaveService(mapper,
                mock(AttendanceLeaveAttachmentStorage.class),
                mock(AttendanceLeaveApprovalOutboxService.class),
                mock(AttendanceLeaveApprovalAfterCommitTrigger.class),
                mock(RemoteApprovalService.class),
                mock(ShopScopeService.class), gate, legacyQuota());

        assertThatThrownBy(() -> service.listMy(null, null, null, 101L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("BUSINESS_FEATURE_DISABLED");
        verifyNoInteractions(mapper);
    }

    @Test
    void correctionFailsBeforeResolvingShopWhenFeatureIsDisabled()
    {
        AttendanceCorrectionMapper mapper = mock(
                AttendanceCorrectionMapper.class);
        ShopScopeService shop = mock(ShopScopeService.class);
        AttendanceCorrectionService service = new AttendanceCorrectionService(
                mapper, mock(AttendanceCorrectionApprovalOutboxService.class),
                mock(AttendanceCorrectionApprovalAfterCommitTrigger.class),
                shop, disabledGate());

        assertThatThrownBy(() -> service.eligibleSchedules(
                LocalDate.parse("2026-08-01"),
                LocalDate.parse("2026-08-20"), 101L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("BUSINESS_FEATURE_DISABLED");
        verifyNoInteractions(mapper, shop);
    }

    private BusinessFeatureGate disabledGate()
    {
        BusinessFeatureGate gate = mock(BusinessFeatureGate.class);
        doThrow(new ServiceException("BUSINESS_FEATURE_DISABLED"))
                .when(gate).requireEnabled(BusinessFeatureGate.ATTENDANCE_V2);
        return gate;
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
