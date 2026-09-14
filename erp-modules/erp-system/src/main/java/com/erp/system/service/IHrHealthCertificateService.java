package com.erp.system.service;

import java.util.List;
import java.util.Map;
import com.erp.approval.api.domain.ApprovalBusinessCallbackRequest;
import com.erp.approval.api.domain.ApprovalBusinessCallbackResponse;
import com.erp.system.domain.HrHealthCertificate;
import com.erp.system.domain.dto.HrHealthCertificateReviewRequest;
import com.erp.system.domain.dto.HrHealthCertificateSubmitRequest;
import com.erp.system.domain.vo.HrHealthCertificateVo;
import com.erp.system.domain.vo.HrHealthCertificateOpsSummaryVo;

public interface IHrHealthCertificateService
{
    List<HrHealthCertificateVo> selectMine(Long userId);

    HrHealthCertificateVo saveMyDraft(Long userId,
            HrHealthCertificate certificate, String operator);

    HrHealthCertificateVo submitMine(Long userId,
            HrHealthCertificateSubmitRequest request, String operator);

    String withdrawMine(Long userId, Long certificateId, String operator);

    List<HrHealthCertificateVo> selectList(HrHealthCertificateVo query);

    HrHealthCertificateOpsSummaryVo selectOpsSummary(
            HrHealthCertificateVo query);

    List<HrHealthCertificateVo> selectEmployee(Long userId);

    HrHealthCertificateVo review(Long certificateId,
            HrHealthCertificateReviewRequest request, Long reviewerUserId,
            String reviewerName);

    Map<Long, HrHealthCertificateVo> selectCurrentProjection(
            List<Long> userIds);

    Map<Long, HrHealthCertificateVo> selectCurrentProjectionAt(List<Long> userIds, java.time.LocalDate asOfDate);

    Long resolveAttachmentNode(Long certificateId, Long requesterUserId);

    ApprovalBusinessCallbackResponse applyApprovalCallback(
            ApprovalBusinessCallbackRequest request);
}
