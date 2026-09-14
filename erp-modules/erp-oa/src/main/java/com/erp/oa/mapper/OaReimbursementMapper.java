package com.erp.oa.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.erp.oa.domain.OaReimbursement;
import com.erp.oa.domain.OaReimbursementExportBatch;
import com.erp.oa.domain.OaReimbursementInvoice;
import com.erp.oa.domain.OaReimbursementItem;

public interface OaReimbursementMapper
{
    int claimExportCommand(@Param("actorId") Long actorId, @Param("requestId") String requestId, @Param("payloadHash") String payloadHash);
    com.erp.oa.domain.OaReimbursementExportCommand lockExportCommand(@Param("actorId") Long actorId, @Param("requestId") String requestId);
    int completeExportCommand(@Param("actorId") Long actorId, @Param("requestId") String requestId, @Param("payloadHash") String payloadHash, @Param("batchId") Long batchId);
    OaReimbursementExportBatch selectExportByCommand(@Param("actorId") Long actorId, @Param("requestId") String requestId);
    List<OaReimbursementExportBatch> selectExportHistory(@Param("actorId") Long actorId);

    int insertReimbursement(OaReimbursement value);

    int updateReimbursement(OaReimbursement value);

    int markApprovalSubmitting(@Param("reimbursementId") Long reimbursementId,
            @Param("expectedStatus") String expectedStatus,
            @Param("expectedVersion") Long expectedVersion,
            @Param("businessRound") Integer businessRound,
            @Param("updateBy") String updateBy);

    int finalizeApprovalStart(@Param("reimbursementId") Long reimbursementId,
            @Param("businessRound") Integer businessRound,
            @Param("expectedVersion") Long expectedVersion,
            @Param("instanceId") Long instanceId,
            @Param("updateBy") String updateBy);

    OaReimbursement selectById(Long reimbursementId);

    OaReimbursement selectByIdForUpdate(Long reimbursementId);

    List<OaReimbursement> selectMyList(OaReimbursement filter);

    List<OaReimbursement> selectFinanceList(OaReimbursement filter);

    List<OaReimbursement> selectFinanceListByIds(
            @Param("reimbursementIds") List<Long> reimbursementIds,
            @Param("scopeDeptIds") List<Long> scopeDeptIds);

    int deleteItemsByReimbursementId(Long reimbursementId);

    int clearInvoiceItemLinks(Long reimbursementId);

    int insertItem(OaReimbursementItem item);

    int bindInvoiceItem(@Param("invoiceId") Long invoiceId,
            @Param("reimbursementId") Long reimbursementId,
            @Param("itemId") Long itemId);

    int deleteItem(@Param("reimbursementId") Long reimbursementId,
            @Param("itemId") Long itemId);

    List<OaReimbursementItem> selectItemsByReimbursementId(
            Long reimbursementId);

    int countInvoices(Long reimbursementId);

    OaReimbursementInvoice selectInvoiceByReimbursementAndSha(
            @Param("reimbursementId") Long reimbursementId,
            @Param("sha256") String sha256);

    int insertInvoice(OaReimbursementInvoice invoice);

    OaReimbursementInvoice selectInvoiceById(Long invoiceId);

    List<OaReimbursementInvoice> selectInvoicesByReimbursementId(
            Long reimbursementId);

    int upsertInvoiceRecognition(OaReimbursementInvoice invoice);

    Long selectApprovedDuplicateReimbursement(
            @Param("sha256") String sha256,
            @Param("reimbursementId") Long reimbursementId);

    Long selectApprovedDuplicateByNumber(
            @Param("invoiceCode") String invoiceCode,
            @Param("invoiceNumber") String invoiceNumber,
            @Param("reimbursementId") Long reimbursementId);

    int countInvoicesNotReady(Long reimbursementId);

    int updateInvoiceDuplicate(
            @Param("invoiceId") Long invoiceId,
            @Param("reimbursementId") Long reimbursementId,
            @Param("duplicateStatus") String duplicateStatus,
            @Param("duplicateReimbursementId")
                    Long duplicateReimbursementId);

    int deleteInvoiceRecognition(
            @Param("invoiceId") Long invoiceId,
            @Param("reimbursementId") Long reimbursementId);

    int deleteInvoice(@Param("invoiceId") Long invoiceId,
            @Param("reimbursementId") Long reimbursementId);

    int insertExportBatch(OaReimbursementExportBatch batch);

    int insertExportBatchItem(@Param("batchId") Long batchId,
            @Param("reimbursement") OaReimbursement reimbursement);

    OaReimbursementExportBatch selectExportBatchById(Long batchId);

    int markExported(@Param("reimbursementIds") List<Long> reimbursementIds,
            @Param("updateBy") String updateBy);
}
