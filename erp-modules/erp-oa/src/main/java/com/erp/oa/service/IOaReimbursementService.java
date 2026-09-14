package com.erp.oa.service;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.springframework.web.multipart.MultipartFile;
import com.erp.approval.api.domain.ApprovalBusinessCallbackRequest;
import com.erp.approval.api.domain.ApprovalBusinessCallbackResponse;
import com.erp.oa.domain.OaReimbursement;
import com.erp.oa.domain.OaReimbursementInvoice;
import com.erp.oa.domain.dto.OaInvoiceRecognitionUpdateRequest;

public interface IOaReimbursementService
{
    OaReimbursement saveDraft(OaReimbursement value,
            Long selectedShopDeptId);

    OaReimbursement submit(OaReimbursement value, Long selectedShopDeptId);

    OaReimbursement withdraw(Long reimbursementId, String reason,
            Long selectedShopDeptId);

    default OaReimbursement withdraw(Long reimbursementId, String reason,
            Long selectedShopDeptId, Long expectedApprovalInstanceId,
            Integer expectedApprovalRound)
    {
        if (expectedApprovalInstanceId != null || expectedApprovalRound != null)
            throw new IllegalStateException("当前服务不支持指定审批轮次撤回");
        return withdraw(reimbursementId, reason, selectedShopDeptId);
    }

    boolean isSubmissionEnabled();

    Map<String, Object> recognitionAvailability();

    OaReimbursement detail(Long reimbursementId, Long selectedShopDeptId);

    List<OaReimbursement> selectMyList(OaReimbursement filter,
            Long selectedShopDeptId);

    List<OaReimbursement> selectFinanceList(OaReimbursement filter,
            Long selectedShopDeptId);

    OaReimbursementInvoice uploadInvoice(Long reimbursementId,
            MultipartFile file, Long selectedShopDeptId);

    OaReimbursementInvoice recognizeInvoice(Long reimbursementId,
            Long invoiceId, String engine, Long selectedShopDeptId);

    OaReimbursementInvoice updateInvoiceRecognition(Long reimbursementId,
            Long invoiceId, OaInvoiceRecognitionUpdateRequest request,
            Long selectedShopDeptId);

    default void deleteInvoice(Long reimbursementId, Long invoiceId,
            Long selectedShopDeptId)
    {
        deleteInvoice(reimbursementId, invoiceId, selectedShopDeptId, null);
    }

    OaReimbursement deleteInvoice(Long reimbursementId, Long invoiceId,
            Long selectedShopDeptId, Long expectedRowVersion);

    InvoiceContent invoiceContent(Long reimbursementId, Long invoiceId,
            String mode, Long selectedShopDeptId);

    ApprovalBusinessCallbackResponse applyApprovalCallback(
            ApprovalBusinessCallbackRequest request);

    record InvoiceContent(Path path, String fileName, String contentType,
            long size, boolean inline) { }
}
