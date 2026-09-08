package com.erp.system.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.erp.system.api.domain.SysUser;
import com.erp.system.domain.HrOnboarding;
import com.erp.system.domain.vo.HrOnboardingOwnerOptionVo;
import com.erp.system.domain.vo.HrOnboardingOwnerQuery;
import com.erp.system.domain.vo.HrOnboardingQuery;
import com.erp.system.domain.vo.HrOnboardingSummaryVo;

/**
 * HR入职单数据层。
 */
public interface HrOnboardingMapper
{
    List<HrOnboarding> selectOnboardingList(HrOnboardingQuery query);

    HrOnboardingSummaryVo selectOnboardingSummary(HrOnboardingQuery query);

    List<HrOnboarding> selectTodayOnboardingTasks(HrOnboardingQuery query);

    List<HrOnboarding> selectScopedByLinkedUserIds(@Param("query") HrOnboardingQuery query,
            @Param("userIds") List<Long> userIds);

    HrOnboarding selectScopedOnboardingByIdForUpdate(HrOnboardingQuery query);

    List<SysUser> selectScopedUserOptions(SysUser query);

    List<HrOnboardingOwnerOptionVo> selectScopedOwnerOptions(HrOnboardingOwnerQuery query);

    List<HrOnboarding> selectConflictCandidates(@Param("phone") String phone,
            @Param("idNumber") String idNumber, @Param("employeeNo") String employeeNo);

    List<HrOnboarding> selectScopedConflictCandidates(@Param("query") HrOnboardingQuery query,
            @Param("phone") String phone, @Param("idNumber") String idNumber,
            @Param("employeeNo") String employeeNo);
    List<HrOnboarding> selectScopedConflictCandidatesBatch(@Param("query") HrOnboardingQuery query,
            @Param("identities") List<HrOnboarding> identities);

    List<HrOnboarding> selectGlobalOpenIdentitySetForUpdate(@Param("phone") String phone,
            @Param("idNumber") String idNumber,
            @Param("employeeNo") String employeeNo);

    int insertOnboarding(HrOnboarding onboarding);

    int updateOnboardingByVersion(HrOnboarding onboarding);

    int confirmOnboardingByVersion(HrOnboarding onboarding);

    int updateOnboardingStatusByVersion(@Param("onboardingId") Long onboardingId,
            @Param("fromStatus") String fromStatus, @Param("toStatus") String toStatus,
            @Param("version") Integer version, @Param("operatorUserId") Long operatorUserId,
            @Param("operator") String operator,
            @Param("cancelReason") String cancelReason);
}
