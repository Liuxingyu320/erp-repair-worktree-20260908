package com.erp.inventory.service;

import java.util.List;
import java.util.Map;
import com.erp.inventory.domain.InvTransferApprovalInstance;
import com.erp.inventory.domain.InvTransferApprovalTask;
import com.erp.inventory.domain.InvTransferOrder;
import com.erp.inventory.domain.dto.InvTransferApprovalRequest;
import com.erp.inventory.domain.vo.InvTransferApprovalSummary;
import com.erp.inventory.domain.vo.InvTransferApprovalTrack;
import com.erp.inventory.domain.vo.InvTransferApprovalPreview;

public interface IInvTransferApprovalService
{
    InvTransferApprovalInstance createInstanceForSubmit(InvTransferOrder transfer);
    void cancelRunning(Long transferId);
    void assertApprovedForDelivery(InvTransferOrder transfer);
    boolean canApproveTask(InvTransferApprovalTask task, Long userId);
    void approve(InvTransferApprovalRequest request, Long selectedShopDeptId);
    InvTransferApprovalTrack selectTrack(InvTransferOrder transfer);
    Map<Long, InvTransferApprovalSummary> selectApprovalSummaries(List<Long> transferIds);
    InvTransferApprovalPreview previewCandidates(Long ruleId, Long targetDeptId, Long selectedShopDeptId);
}
