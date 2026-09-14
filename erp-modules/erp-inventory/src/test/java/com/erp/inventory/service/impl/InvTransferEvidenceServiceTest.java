package com.erp.inventory.service.impl;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.erp.common.core.domain.R;
import com.erp.common.core.exception.ServiceException;
import com.erp.system.api.RemoteFileService;
import com.erp.system.api.domain.DriveBusinessFile;
import org.junit.jupiter.api.Test;

class InvTransferEvidenceServiceTest
{
    @Test void parsesExactLongIdsWithoutFloatingPoint() {
        assertThat(InvTransferEvidenceService.parse("drive:1,drive:9223372036854775807"))
                .containsExactly(1L, Long.MAX_VALUE);
    }
    @Test void rejectsMalformedDuplicateExternalAndOverflowReferencesBeforeFileReads() {
        RemoteFileService remote = mock(RemoteFileService.class);
        InvTransferEvidenceService service = new InvTransferEvidenceService(remote);
        for (String value : new String[]{"node-1", "https://example.com/a.jpg", "1", "drive:0",
                "drive:-1", "drive:01", "drive:9223372036854775808", "drive:1,drive:1", "drive:1,"})
            assertThatThrownBy(() -> service.validate(value)).isInstanceOf(ServiceException.class);
        verifyNoInteractions(remote);
    }
    @Test void validatesEachIdUnderCurrentUserAndSpecificUsage() {
        RemoteFileService remote = mock(RemoteFileService.class);
        DriveBusinessFile file = new DriveBusinessFile(); file.setNodeId(Long.MAX_VALUE);
        when(remote.validateDriveBusinessFile(Long.MAX_VALUE, "TRANSFER_EVIDENCE", "inner")).thenReturn(R.ok(file));
        new InvTransferEvidenceService(remote).validate("drive:9223372036854775807");
        verify(remote).validateDriveBusinessFile(Long.MAX_VALUE, "TRANSFER_EVIDENCE", "inner");
    }
    @Test void rejectsUnavailableDeniedAndMismatchedNodes() {
        RemoteFileService remote = mock(RemoteFileService.class);
        InvTransferEvidenceService service = new InvTransferEvidenceService(remote);
        assertThatThrownBy(() -> service.validate("drive:1")).isInstanceOf(ServiceException.class);
        when(remote.validateDriveBusinessFile(1L, "TRANSFER_EVIDENCE", "inner")).thenReturn(R.fail("denied"));
        assertThatThrownBy(() -> service.validate("drive:1")).isInstanceOf(ServiceException.class);
        DriveBusinessFile wrong = new DriveBusinessFile(); wrong.setNodeId(2L);
        when(remote.validateDriveBusinessFile(1L, "TRANSFER_EVIDENCE", "inner")).thenReturn(R.ok(wrong));
        assertThatThrownBy(() -> service.validate("drive:1")).isInstanceOf(ServiceException.class);
    }
    @Test void missingDependencyNeverAcceptsNewReferences() {
        InvTransferEvidenceService service = new InvTransferEvidenceService(null);
        service.validate(null);
        assertThatThrownBy(() -> service.validate("drive:1")).hasMessageContaining("暂不可用");
    }
    @Test void historicTextCanOnlyBeRetainedForNonRequiredEvidence() {
        RemoteFileService remote = mock(RemoteFileService.class);
        InvTransferEvidenceService service = new InvTransferEvidenceService(remote);
        service.validateResolution("old-evidence", "old-evidence", null, false);
        service.validateResolution("old-qc", null, "old-qc", false);
        assertThatThrownBy(() -> service.validateResolution("old-evidence", "old-evidence", null, true)).isInstanceOf(ServiceException.class);
        assertThatThrownBy(() -> service.validateResolution("forged", "old-evidence", null, false)).isInstanceOf(ServiceException.class);
        verifyNoInteractions(remote);
    }
    @Test void historicControlledNodeIsRevalidatedAndRequiredBlankRejected() {
        InvTransferEvidenceService service = new InvTransferEvidenceService(mock(RemoteFileService.class));
        assertThatThrownBy(() -> service.validateResolution("drive:1", "drive:1", null, false)).isInstanceOf(ServiceException.class);
        assertThatThrownBy(() -> service.validateResolution("", null, null, true)).isInstanceOf(ServiceException.class);
    }
}
