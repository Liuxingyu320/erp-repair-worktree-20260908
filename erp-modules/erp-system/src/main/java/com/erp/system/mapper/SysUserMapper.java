package com.erp.system.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;
import com.erp.system.api.domain.SignCandidateUser;
import com.erp.system.api.domain.SignCandidateUserQuery;
import com.erp.system.api.domain.SysUser;
import com.erp.system.domain.vo.HrOnboardingQuery;
import com.erp.system.domain.vo.HrEmployeeQuery;
import com.erp.system.domain.vo.SysSalaryUserOptionVo;
import com.erp.system.domain.vo.SysUserAssignmentVo;
import com.erp.system.domain.vo.SysUserListVo;
import com.erp.system.domain.vo.SysUserManageDetailVo;
import com.erp.system.domain.vo.SysUserPiiDetailVo;
import com.erp.system.domain.vo.SysUserPiiExportVo;
import com.erp.system.domain.vo.SysUserSetupSummaryVo;

/**
 * 用户表 数据层
 * 
 * @author erp
 */
public interface SysUserMapper
{
    /**
     * 根据条件分页查询用户列表
     * 
     * @param sysUser 用户信息
     * @return 用户信息集合信息
     */
    public List<SysUser> selectUserList(SysUser sysUser);

    /** 安全、最小字段的用户选择器查询。 */
    public List<SysUser> selectUserOptionList(SysUser sysUser);

    /** 仅返回数据范围内的候选用户 ID。 */
    public List<Long> selectVisibleUserIds(SysUser sysUser);

    /** Fixed masked contract for externally exposed user-management lists. */
    List<SysUserListVo> selectUserManageList(SysUser sysUser);

    /** Aggregated setup health using the same scoped filters as the user list. */
    SysUserSetupSummaryVo selectUserSetupSummary(SysUser sysUser);

    /** Fixed non-PII detail contract for the account-management dialog. */
    SysUserManageDetailVo selectUserManageDetail(Long userId);

    /** Full PII is only available through the dedicated protected endpoint. */
    SysUserPiiDetailVo selectUserPiiById(Long userId);

    /** Sensitive export query; callers must apply permission, scope, reason and audit controls. */
    List<SysUserPiiExportVo> selectUserPiiExportList(SysUser sysUser);

    /** Dedicated employee-master query. Every caller must pass through HrEmployeeAccessService. */
    public List<SysUser> selectHrEmployeeList(HrEmployeeQuery query);

    /** Scoped authoritative employee row/profile lock for PATCH transactions. */
    public SysUser selectHrEmployeeForUpdate(HrEmployeeQuery query);

    /**
     * 根据条件查询签约候选员工列表
     *
     * @param query 查询条件
     * @return 签约候选员工集合信息
     */
    public List<SignCandidateUser> selectSignCandidateUsers(SignCandidateUserQuery query);

    /**
     * 根据条件分页查询已配用户角色列表
     * 
     * @param user 用户信息
     * @return 用户信息集合信息
     */
    public List<SysUser> selectAllocatedList(SysUser user);

    List<SysUserAssignmentVo> selectAllocatedAssignmentList(SysUser user);

    /**
     * 根据条件分页查询未分配用户角色列表
     * 
     * @param user 用户信息
     * @return 用户信息集合信息
     */
    public List<SysUser> selectUnallocatedList(SysUser user);

    List<SysUserAssignmentVo> selectUnallocatedAssignmentList(SysUser user);

    List<SysSalaryUserOptionVo> selectSalaryUserOptions(SysUser user);

    /**
     * 通过用户名查询用户
     * 
     * @param userName 用户名
     * @return 用户对象信息
     */
    public SysUser selectUserByUserName(String userName);

    /**
     * 通过用户ID查询用户
     * 
     * @param userId 用户ID
     * @return 用户对象信息
     */
    public SysUser selectUserById(Long userId);

    public List<SysUser> selectScopedOnboardingConflictUsers(@Param("query") HrOnboardingQuery query,
            @Param("phone") String phone, @Param("idNumber") String idNumber,
            @Param("employeeNo") String employeeNo);
    public List<SysUser> selectScopedOnboardingConflictUsersBatch(@Param("query") HrOnboardingQuery query,
            @Param("identities") List<com.erp.system.domain.HrOnboarding> identities);

    public SysUser selectScopedOnboardingConflictUserForUpdate(@Param("query") HrOnboardingQuery query,
            @Param("userId") Long userId, @Param("phone") String phone,
            @Param("idNumber") String idNumber, @Param("employeeNo") String employeeNo);

    /**
     * 新增用户信息
     * 
     * @param user 用户信息
     * @return 结果
     */
    public int insertUser(SysUser user);

    /**
     * 修改用户信息
     * 
     * @param user 用户信息
     * @return 结果
     */
    public int updateUser(SysUser user);

    /** Update only explicitly supplied sys_user PII columns. */
    int updateUserPii(@Param("userId") Long userId,
            @Param("values") Map<String, Object> values,
            @Param("operator") String operator);

    /**
     * 修改人事员工档案关联的用户基础信息，不变更角色和岗位关联。
     *
     * @param user 用户信息
     * @return 结果
     */
    public int updateHrEmployeeProfileUser(SysUser user);

    /** Explicit employee-master PATCH; values must contain only registry-approved SYS_USER keys. */
    public int patchHrEmployeeUser(@Param("userId") Long userId,
            @Param("values") java.util.Map<String, Object> values, @Param("operator") String operator);

    /** 以原组织为条件原子更新员工当前组织。 */
    int updateTransferDept(@Param("userId") Long userId,
            @Param("targetDeptId") Long targetDeptId,
            @Param("expectedDeptId") Long expectedDeptId,
            @Param("operator") String operator);

    /** 离职时只停用账号，不删除岗位、角色或门店授权。 */
    int disableUserForOffboarding(@Param("userId") Long userId,
            @Param("expectedStatus") String expectedStatus,
            @Param("operatorName") String operatorName);

    /**
     * 修改用户头像
     * 
     * @param userId 用户ID
     * @param avatar 头像地址
     * @return 结果
     */
    public int updateUserAvatar(@Param("userId") Long userId, @Param("avatar") String avatar);

    /**
     * 修改用户状态
     * 
     * @param userId 用户ID
     * @param status 状态
     * @return 结果
     */
    public int updateUserStatus(@Param("userId") Long userId, @Param("status") String status);

    /**
     * 更新用户登录信息（IP和登录时间）
     * 
     * @param user 用户信息
     * @return 结果
     */
    public int updateLoginInfo(SysUser user);

    /**
     * 重置用户密码
     * 
     * @param userId 用户ID
     * @param password 密码
     * @return 结果
     */
    public int resetUserPwd(@Param("userId") Long userId, @Param("password") String password,
            @Param("mustChangePassword") String mustChangePassword);

    /** Administrator-issued one-time password. */
    public int resetUserTemporaryCredential(@Param("userId") Long userId,
            @Param("password") String password,
            @Param("temporaryPasswordExpiresAt") java.util.Date temporaryPasswordExpiresAt,
            @Param("updateBy") String updateBy);

    /** User-owned password change: activate the credential and clear temporary expiry. */
    public int activateUserPassword(@Param("userId") Long userId,
            @Param("password") String password,
            @Param("updateBy") String updateBy);

    /**
     * 通过用户ID删除用户
     * 
     * @param userId 用户ID
     * @return 结果
     */
    public int deleteUserById(Long userId);

    /**
     * 批量删除用户信息
     * 
     * @param userIds 需要删除的用户ID
     * @return 结果
     */
    public int deleteUserByIds(Long[] userIds);

    /**
     * 校验用户名称是否唯一
     * 
     * @param userName 用户名称
     * @return 结果
     */
    public SysUser checkUserNameUnique(String userName);

    /**
     * 校验手机号码是否唯一
     *
     * @param phonenumber 手机号码
     * @return 结果
     */
    public SysUser checkPhoneUnique(String phonenumber);

    /**
     * 校验email是否唯一
     *
     * @param email 用户邮箱
     * @return 结果
     */
    public SysUser checkEmailUnique(String email);
}
