package com.erp.inventory.service.impl;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.util.List;
import com.erp.common.core.context.SecurityContextHolder;
import com.erp.common.core.domain.R;
import com.erp.common.core.exception.ServiceException;
import com.erp.inventory.constant.InvStatusConstants;
import com.erp.inventory.domain.*;
import com.erp.inventory.domain.dto.*;
import com.erp.inventory.mapper.*;
import com.erp.system.api.RemoteFileService;
import com.erp.system.api.domain.DriveBusinessFile;
import org.junit.jupiter.api.*;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;

/** Reuses the existing synthetic ledger fixture; executes actual receipt/disposition methods. */
class InvTransferEvidenceWorkflowTest
{
    private Object fixture;
    private InvTransferWorkflowResources resources;
    private InvTransferDiscrepancyProcessor processor;
    private RemoteFileService remote;

    @BeforeEach void prepare() throws Exception {
        SecurityContextHolder.setUserId("7"); SecurityContextHolder.setUserName("operator");
        Class<?> type = Class.forName(InvTransferDiscrepancyProcessorTest.class.getName() + "$Harness");
        Constructor<?> constructor = type.getDeclaredConstructor(); constructor.setAccessible(true);
        fixture = constructor.newInstance(); processor = field("processor");
        resources = (InvTransferWorkflowResources) ReflectionTestUtils.getField(processor, "resources");
        remote = mock(RemoteFileService.class);
        ReflectionTestUtils.setField(resources, "evidenceService", new InvTransferEvidenceService(remote));
        DriveBusinessFile file = new DriveBusinessFile(); file.setNodeId(1L);
        when(remote.validateDriveBusinessFile(1L, "TRANSFER_EVIDENCE", "inner")).thenReturn(R.ok(file));
        ReflectionTestUtils.invokeMethod(fixture, "addDetail", 11L, 111L, "10", "8", "0", "0", "2", "5.00");
    }
    @AfterEach void clearContext() { SecurityContextHolder.remove(); }
    @SuppressWarnings("unchecked") private <T> T field(String name) { return (T) ReflectionTestUtils.getField(fixture, name); }
    private InvTransferDiscrepancyResolveRequest request() {
        InvTransferDiscrepancyResolutionItem item = ReflectionTestUtils.invokeMethod(fixture, "item", 11L, "DAMAGED", "WRITE_OFF", "2");
        InvTransferDiscrepancyResolveRequest request = new InvTransferDiscrepancyResolveRequest();
        request.setVersion(0L); request.setRequestId("evidence-request-1"); request.setNote("核对残损照片");
        request.setResponsibleParty("LOGISTICS"); request.setItems(List.of(item)); return request;
    }
    @Test void actualDispositionChecksFileBeforeFirstWrite() {
        processor.resolveTransferDiscrepancy(1L, request(), 202L);
        InOrder order = inOrder(remote, resources.transferDiscrepancyMapper);
        order.verify(remote).validateDriveBusinessFile(1L, "TRANSFER_EVIDENCE", "inner");
        order.verify(resources.transferDiscrepancyMapper).resolve(anyLong(), anyLong(), any(), any(), any(), any(), anyLong(), any());
    }
    @Test void deniedFileStopsActualDispositionWithoutBusinessWrites() {
        when(remote.validateDriveBusinessFile(1L, "TRANSFER_EVIDENCE", "inner")).thenReturn(R.fail("denied"));
        assertThatThrownBy(() -> processor.resolveTransferDiscrepancy(1L, request(), 202L)).hasMessageContaining("无权绑定");
        verify(resources.transferDiscrepancyMapper, never()).resolve(anyLong(), anyLong(), any(), any(), any(), any(), anyLong(), any());
        verify(resources.dispositionMapper, never()).insertDisposition(any());
        assertThat((List<?>) field("dispositions")).isEmpty();
    }
    @Test void scopeFailureNeverReadsFileDetails() {
        when(resources.deptScopeMapper.countUserShopScope(anyLong(), anyLong())).thenReturn(0);
        when(resources.deptScopeMapper.countDeptInScope(anyLong(), anyLong())).thenReturn(0);
        assertThatThrownBy(() -> processor.resolveTransferDiscrepancy(1L, request(), 999L)).isInstanceOf(ServiceException.class);
        verifyNoInteractions(remote);
    }
    @Test void exactSuccessfulReplayRemainsValidAfterFileBecomesUnavailable() {
        InvTransferDiscrepancyResolveRequest request = request();
        processor.resolveTransferDiscrepancy(1L, request, 202L); clearInvocations(remote);
        when(remote.validateDriveBusinessFile(1L, "TRANSFER_EVIDENCE", "inner")).thenReturn(R.fail("trashed later"));
        processor.resolveTransferDiscrepancy(1L, request, 202L);
        verifyNoInteractions(remote); assertThat((List<?>) field("dispositions")).hasSize(1);
    }
    @Test void changedReplayCannotReplaceEvidenceOrCallFileService() {
        InvTransferDiscrepancyResolveRequest request = request();
        processor.resolveTransferDiscrepancy(1L, request, 202L); clearInvocations(remote);
        request.getItems().get(0).setAttachmentRefs("drive:2");
        assertThatThrownBy(() -> processor.resolveTransferDiscrepancy(1L, request, 202L)).isInstanceOf(ServiceException.class);
        verifyNoInteractions(remote); assertThat((List<?>) field("dispositions")).hasSize(1);
    }
    @Test void actualReceiptRejectsUnusableFileBeforeStockOrReceiptWrites() {
        InvTransferOrder order = field("order"); order.setStatus(InvStatusConstants.DELIVERED);
        InvTransferShipment shipment = new InvTransferShipment(); shipment.setShipmentId(800L);
        shipment.setTransferId(900L); shipment.setStatus(InvStatusConstants.PENDING_RECEIVE);
        when(resources.transferShipmentMapper.selectById(800L)).thenReturn(shipment);
        when(resources.transferShipmentMapper.selectByIdForUpdate(800L)).thenReturn(shipment);
        InvReceiveItem item = new InvReceiveItem(); item.setDetailId(1011L);
        item.setReceiveQuantity(new BigDecimal("2")); item.setAttachmentRefs("drive:1");
        InvReceiveRequest request = new InvReceiveRequest(); request.setWarehouseId(202L); request.setItems(List.of(item));
        when(remote.validateDriveBusinessFile(1L, "TRANSFER_EVIDENCE", "inner")).thenReturn(R.fail("unavailable"));
        InvTransferReceiptProcessor receipt = new InvTransferReceiptProcessor(resources, new InvTransferDirectionPolicy(resources.deptScopeMapper, null));
        assertThatThrownBy(() -> receipt.receiveTransferShipment(800L, request, 202L)).hasMessageContaining("无权绑定");
        verify(resources.transferDiscrepancyMapper, never()).insertDiscrepancy(any());
        verify(resources.stockMapper, never()).addInvStockWithCost(anyLong(), anyLong(), any(), any(), any());
        verify(resources.stockMapper, never()).insertInvStock(any());
    }
}
