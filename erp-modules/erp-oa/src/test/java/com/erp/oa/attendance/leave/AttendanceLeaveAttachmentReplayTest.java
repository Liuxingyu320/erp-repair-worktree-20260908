package com.erp.oa.attendance.leave;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import com.erp.approval.api.RemoteApprovalService;
import com.erp.common.core.context.SecurityContextHolder;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.security.shop.ShopScopeService;
import com.erp.oa.attendance.leave.AttendanceLeaveAttachmentStorage.PreparedAttachment;
import com.erp.oa.attendance.leave.AttendanceLeaveModels.LeaveAttachment;
import com.erp.oa.attendance.leave.AttendanceLeaveModels.LeaveRequest;
import com.erp.oa.service.BusinessFeatureGate;

class AttendanceLeaveAttachmentReplayTest
{
    private AttendanceLeaveMapper mapper;
    private AttendanceLeaveAttachmentStorage storage;
    private AttendanceLeaveService service;

    @BeforeEach
    void setUp()
    {
        SecurityContextHolder.setUserId("9");
        SecurityContextHolder.setUserName("tester");
        mapper = mock(AttendanceLeaveMapper.class);
        storage = mock(AttendanceLeaveAttachmentStorage.class);
        ShopScopeService shop = mock(ShopScopeService.class);
        when(shop.resolveRequiredShopDept(101L)).thenReturn(101L);
        BusinessFeatureGate gate = mock(BusinessFeatureGate.class);
        doNothing().when(gate).requireEnabled(
                BusinessFeatureGate.ATTENDANCE_V2);
        service = new AttendanceLeaveService(mapper, storage,
                mock(AttendanceLeaveApprovalOutboxService.class),
                mock(AttendanceLeaveApprovalAfterCommitTrigger.class),
                mock(RemoteApprovalService.class), shop, gate);
    }

    @AfterEach
    void tearDown()
    {
        SecurityContextHolder.remove();
    }

    @Test
    void sameContentReplayIgnoresStaleVersionAndAttachmentLimit()
    {
        LeaveRequest parent = parent();
        PreparedAttachment prepared = prepared("hash-1");
        LeaveAttachment existing = new LeaveAttachment();
        existing.attachmentId = 7L;
        existing.leaveRequestId = 42L;
        existing.sha256 = "hash-1";
        when(mapper.selectLeaveRequestByIdForUpdate(42L)).thenReturn(parent);
        when(storage.prepare(any())).thenReturn(prepared);
        when(mapper.selectLeaveAttachmentByHash(42L, "hash-1"))
                .thenReturn(existing);

        LeaveAttachment result = service.uploadAttachment(42L, 1L,
                file(), 101L);

        assertThat(result).isSameAs(existing);
        assertThat(result.requestRowVersion).isEqualTo(5L);
        verify(mapper, never()).countLeaveAttachments(any());
        verify(storage, never()).store(eq(42L), any(PreparedAttachment.class));
        verify(mapper, never()).updateLeaveDraft(any());
    }

    @Test
    void differentContentStillEnforcesVersionBeforeWritingToDisk()
    {
        when(mapper.selectLeaveRequestByIdForUpdate(42L))
                .thenReturn(parent());
        PreparedAttachment prepared = prepared("hash-2");
        when(storage.prepare(any())).thenReturn(prepared);

        assertThatThrownBy(() -> service.uploadAttachment(42L, 1L,
                file(), 101L)).isInstanceOf(ServiceException.class)
                .hasMessageContaining("LEAVE_VERSION_CONFLICT");

        verify(mapper, never()).countLeaveAttachments(any());
        verify(storage, never()).store(eq(42L), any(PreparedAttachment.class));
    }

    private LeaveRequest parent()
    {
        LeaveRequest value = new LeaveRequest();
        value.leaveRequestId = 42L;
        value.userId = 9L;
        value.shopId = 101L;
        value.status = "DRAFT";
        value.rowVersion = 5L;
        return value;
    }

    private PreparedAttachment prepared(String hash)
    {
        return new PreparedAttachment("proof.pdf", "application/pdf", "pdf",
                new byte[] { 1, 2, 3 }, hash);
    }

    private MockMultipartFile file()
    {
        return new MockMultipartFile("file", "proof.pdf",
                "application/pdf", new byte[] { 1, 2, 3 });
    }
}
