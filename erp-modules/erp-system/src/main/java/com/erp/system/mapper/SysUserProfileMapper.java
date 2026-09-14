package com.erp.system.mapper;

import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.erp.oa.api.domain.HrEmployeeSigningSnapshot;
import com.erp.system.api.domain.SysUserProfile;
import com.erp.system.api.domain.ReviewedSignProfileSupplement;
import java.util.Map;

/**
 * 用户员工档案 数据层
 *
 * @author erp
 */
public interface SysUserProfileMapper
{
    SysUserProfile selectSalaryProfileForUpdate(@org.apache.ibatis.annotations.Param("userId") Long userId);
    /**
     * 通过用户ID查询员工档案
     *
     * @param userId 用户ID
     * @return 员工档案
     */
    public SysUserProfile selectUserProfileByUserId(Long userId);

    /**
     * 通过工号查询员工档案
     *
     * @param employeeNo 工号
     * @return 员工档案
     */
    public SysUserProfile selectUserProfileByEmployeeNo(String employeeNo);

    /** Lock and read the global employee-number sequence row. */
    Long selectEmployeeNoSequenceForUpdate(@Param("sequenceKey") String sequenceKey);

    /** Advance the employee-number sequence with a compare-and-set guard. */
    int advanceEmployeeNoSequence(@Param("sequenceKey") String sequenceKey,
            @Param("expectedValue") Long expectedValue, @Param("nextValue") Long nextValue);

    /** Read the canonical code of an enabled post. */
    String selectActivePostCodeById(Long postId);

    /**
     * 新增员工档案
     *
     * @param profile 员工档案
     * @return 结果
     */
    public int insertUserProfile(SysUserProfile profile);

    /** Insert only the profile identity/audit columns; duplicate user keys are a successful no-op. */
    int insertUserProfileIfAbsent(@Param("userId") Long userId,@Param("operator") String operator);

    /** Update only the reviewed personal-fact whitelist owned by the OA data-completion flow. */
    int updateReviewedSigningFacts(@Param("request") ReviewedSignProfileSupplement request,
            @Param("operator") String operator);

    /**
     * 修改员工档案
     *
     * @param profile 员工档案
     * @return 结果
     */
    public int updateUserProfile(SysUserProfile profile);

    /** Explicit employee-master profile PATCH; values are registry-whitelisted by the service. */
    public int patchUserProfile(@Param("userId") Long userId, @Param("values") Map<String,Object> values,
            @Param("operator") String operator);

    /** Update only fields owned by onboarding confirmation; preserve the rest of an existing profile. */
    public int updateOnboardingProfile(SysUserProfile profile);

    /** Update only fields that an employee may maintain during profile completion. */
    public int updateProfileCompletionFields(SysUserProfile profile);

    /** Date-only regularization preserves every position and payroll column. */
    int updateRegularizationDateOnly(@Param("employeeId") Long employeeId,
            @Param("effectiveDate") java.time.LocalDate effectiveDate, @Param("updateBy") String updateBy);

    /** 锁定员工、档案及其稳定组织岗位信息后读取签约快照。 */
    HrEmployeeSigningSnapshot selectSigningSnapshotByUserIdForUpdate(Long userId);

    /** 先锁定员工档案主行，避免并发等待时读取到跨提交时点的JOIN快照。 */
    Long lockSigningProfileByUserId(Long userId);

    /** 只写确认入职拥有的档案字段。 */
    int updateLifecycleOnboardingProfile(@Param("snapshot") HrEmployeeSigningSnapshot snapshot,
            @Param("operatorName") String operatorName);

    /** 分页读取临近合同到期且未离职的员工ID。 */
    List<Long> selectRenewalCandidateUserIds(
            @Param("windowStart") LocalDate windowStart,
            @Param("windowEnd") LocalDate windowEnd,
            @Param("afterUserId") Long afterUserId,
            @Param("limit") int limit);

    /** 仅更新续签拥有的合同字段并用旧合同和次数做乐观保护。 */
    int updateRenewalProfile(
            @Param("snapshot") HrEmployeeSigningSnapshot snapshot,
            @Param("oldContractEndDate") LocalDate oldContractEndDate,
            @Param("oldRenewalCount") Integer oldRenewalCount,
            @Param("operatorName") String operatorName);

    /** 仅更新转正确认拥有的档案字段，并以试用状态做原子保护。 */
    int updateRegularizationProfile(
            @Param("snapshot") HrEmployeeSigningSnapshot snapshot,
            @Param("operatorName") String operatorName);

    /** 只更新调岗拥有的档案字段。 */
    int updateTransferProfile(
            @Param("snapshot") HrEmployeeSigningSnapshot snapshot,
            @Param("operatorName") String operatorName);

    /** 只更新离职拥有的档案字段，并以旧状态和旧离职日做并发保护。 */
    int updateOffboardingProfile(
            @Param("snapshot") HrEmployeeSigningSnapshot snapshot,
            @Param("expectedEmployeeStatus") String expectedEmployeeStatus,
            @Param("expectedLeaveDate") LocalDate expectedLeaveDate,
            @Param("operatorName") String operatorName);
}
