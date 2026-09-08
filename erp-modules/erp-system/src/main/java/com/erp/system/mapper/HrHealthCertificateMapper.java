package com.erp.system.mapper;

import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.erp.system.domain.HrHealthCertificate;
import com.erp.system.domain.vo.HrHealthCertificateVo;
import com.erp.system.domain.vo.HrHealthCertificateOpsSummaryVo;

public interface HrHealthCertificateMapper
{
    HrHealthCertificateVo selectById(Long certificateId);

    HrHealthCertificateVo selectByIdForUpdate(Long certificateId);

    List<HrHealthCertificateVo> selectByUserId(Long userId);

    List<HrHealthCertificateVo> selectCurrentByUserIds(
            @Param("userIds") List<Long> userIds);

    List<HrHealthCertificateVo> selectScopedList(
            HrHealthCertificateVo query);

    HrHealthCertificateOpsSummaryVo selectOpsSummary(
            HrHealthCertificateVo query);

    List<HrHealthCertificateVo> selectReminderCandidates(
            @Param("expiresThrough") LocalDate expiresThrough,
            @Param("afterCertificateId") Long afterCertificateId,
            @Param("limit") int limit);

    List<Long> selectReminderRecipientUserIds(
            @Param("deptId") Long deptId);

    int insertCertificate(HrHealthCertificate certificate);

    int updateDraft(HrHealthCertificate certificate);

    int markApprovalSubmitting(@Param("certificateId") Long certificateId,
            @Param("userId") Long userId,
            @Param("version") Long version,
            @Param("approvalRound") Integer approvalRound,
            @Param("updateBy") String updateBy);

    int markApprovalPending(@Param("certificateId") Long certificateId,
            @Param("version") Long version,
            @Param("approvalInstanceId") Long approvalInstanceId,
            @Param("approvalRound") Integer approvalRound,
            @Param("updateBy") String updateBy);

    Long lockEmployeeProfile(Long userId);

    int clearCurrentByUserId(@Param("userId") Long userId,
            @Param("updateBy") String updateBy);

    int reviewCertificate(@Param("certificateId") Long certificateId,
            @Param("version") Long version,
            @Param("decision") String decision,
            @Param("currentFlag") String currentFlag,
            @Param("reviewerUserId") Long reviewerUserId,
            @Param("reviewerName") String reviewerName,
            @Param("rejectionReason") String rejectionReason);

    int applyApprovalResult(@Param("certificateId") Long certificateId,
            @Param("version") Long version,
            @Param("approvalInstanceId") Long approvalInstanceId,
            @Param("approvalRound") Integer approvalRound,
            @Param("eventKey") String eventKey,
            @Param("targetStatus") String targetStatus,
            @Param("currentFlag") String currentFlag,
            @Param("operatorUserId") Long operatorUserId,
            @Param("operatorName") String operatorName,
            @Param("reason") String reason);
}
