package com.erp.system.service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.erp.system.api.domain.SignCandidateUser;
import com.erp.system.api.domain.SignCandidateUserQuery;
import com.erp.system.api.domain.SysUser;
import com.erp.system.domain.vo.SysUserImportResult;
import com.erp.system.api.domain.SysUserProfile;
import com.erp.system.domain.vo.SysTemporaryCredentialVo;
import com.erp.system.domain.vo.SysUserImportResultVo;
import com.erp.system.domain.vo.SysUserOptionVo;
import com.erp.system.domain.dto.SysUserPiiAccessReason;
import com.erp.system.domain.dto.SysUserPiiUpdateRequest;
import com.erp.system.domain.vo.SysSalaryUserOptionVo;
import com.erp.system.domain.vo.SysUserAssignmentVo;
import com.erp.system.domain.vo.SysUserListVo;
import com.erp.system.domain.vo.SysUserManageDetailVo;
import com.erp.system.domain.vo.SysUserPiiDetailVo;
import com.erp.system.domain.vo.SysUserPiiExportVo;
import com.erp.system.domain.vo.SysUserSetupSummaryVo;

/**
 * 用户 业务层
 * 
 * @author erp
 */
public interface ISysUserService
{
    /**
     * 根据条件分页查询用户列表
     * 
     * @param user 用户信息
     * @return 用户信息集合信息
     */
    public List<SysUser> selectUserList(SysUser user);

    /** 查询当前操作人数据范围内的用户选择器选项。 */
    public List<SysUser> selectUserOptionList(SysUser user);

    /** 批量返回当前操作人数据范围内的候选用户 ID。 */
    public Set<Long> selectVisibleUserIds(Collection<Long> userIds);

    List<SysUserListVo> selectUserManageList(SysUser user);

    SysUserSetupSummaryVo selectUserSetupSummary(SysUser user);

    SysUserManageDetailVo selectUserManageDetail(Long userId);

    SysUserPiiDetailVo selectUserPiiById(Long userId, SysUserPiiAccessReason reason,
            Long viewerUserId);

    int updateUserPii(Long userId, SysUserPiiUpdateRequest request,
            SysUserPiiAccessReason reason, Long viewerUserId, String operator);

    List<SysUserPiiExportVo> selectUserPiiExportList(SysUser user,
            SysUserPiiAccessReason reason, Long viewerUserId);

    /**
     * 根据条件查询签约候选员工列表
     *
     * @param query 查询条件
     * @return 签约候选员工集合信息
     */
    public List<SignCandidateUser> selectSignCandidateUsers(SignCandidateUserQuery query);

    /**
     * 根据条件分页查询已分配用户角色列表
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

    /**
     * 预览未保存用户的实时派生档案字段。
     *
     * @param user 归属部门、岗位和档案日期
     * @return 派生档案字段
     */
    public SysUserProfile previewDerivedProfile(SysUser user);

    /**
     * 根据用户ID查询用户所属角色组
     * 
     * @param userName 用户名
     * @return 结果
     */
    public String selectUserRoleGroup(String userName);

    /**
     * 根据用户ID查询用户所属岗位组
     * 
     * @param userName 用户名
     * @return 结果
     */
    public String selectUserPostGroup(String userName);

    /**
     * 校验用户名称是否唯一
     * 
     * @param user 用户信息
     * @return 结果
     */
    public boolean checkUserNameUnique(SysUser user);

    /**
     * 校验手机号码是否唯一
     *
     * @param user 用户信息
     * @return 结果
     */
    public boolean checkPhoneUnique(SysUser user);

    /**
     * 校验email是否唯一
     *
     * @param user 用户信息
     * @return 结果
     */
    public boolean checkEmailUnique(SysUser user);

    /**
     * 校验用户是否允许操作
     * 
     * @param user 用户信息
     */
    public void checkUserAllowed(SysUser user);

    /**
     * 校验用户是否有数据权限
     * 
     * @param userId 用户id
     */
    public void checkUserDataScope(Long userId);

    /**
     * 新增用户信息
     * 
     * @param user 用户信息
     * @return 结果
     */
    public int insertUser(SysUser user);

    /** Create an administrator-managed user with a unique one-time credential. */
    public SysTemporaryCredentialVo insertUserWithTemporaryCredential(SysUser user, String passwordPolicy);

    /**
     * 注册用户信息
     * 
     * @param user 用户信息
     * @return 结果
     */
    public boolean registerUser(SysUser user);

    /**
     * 修改用户信息
     * 
     * @param user 用户信息
     * @return 结果
     */
    public int updateUser(SysUser user);

    /**
     * 修改人事员工档案信息
     *
     * @param user 用户和员工档案信息
     * @return 结果
     */
    public int updateHrEmployeeProfile(SysUser user);

    /**
     * 用户授权角色
     * 
     * @param userId 用户ID
     * @param roleIds 角色组
     */
    public void insertUserAuth(Long userId, Long[] roleIds);

    /**
     * 修改用户状态
     * 
     * @param user 用户信息
     * @return 结果
     */
    public int updateUserStatus(SysUser user);

    /**
     * 修改用户基本信息
     * 
     * @param user 用户信息
     * @return 结果
     */
    public boolean updateUserProfile(SysUser user);

    /**
     * 修改当前登录用户可自行维护的基本资料和现住地。
     *
     * @param user 用户基本资料
     * @param currentAddress 现住地
     * @param operator 操作人
     * @return 结果
     */
    public boolean updateSelfProfile(SysUser user, Map<String, Object> profileValues, String operator);

    /**
     * 修改用户头像
     * 
     * @param userId 用户ID
     * @param avatar 头像地址
     * @return 结果
     */
    public boolean updateUserAvatar(Long userId, String avatar);

    /**
     * 更新用户登录信息（IP和登录时间）
     * 
     * @param user 用户信息
     * @return 结果
     */
    public boolean updateLoginInfo(SysUser user);

    /**
     * 重置用户密码
     * 
     * @param user 用户信息
     * @return 结果
     */
    public SysTemporaryCredentialVo resetTemporaryCredential(SysUser user, String passwordPolicy);

    /** 旧版管理员密码重置流程兼容入口。 */
    public int resetPwd(SysUser user);

    /**
     * 重置用户密码
     * 
     * @param userId 用户ID
     * @param password 密码
     * @return 结果
     */
    public int activateUserPassword(Long userId, String password, String updateBy);

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
     * 导入用户数据
     * 
     * @param userList 用户数据列表
     * @param isUpdateSupport 是否更新支持，如果已存在，则进行更新数据
     * @param operName 操作用户
     * @return 结果
     */
    public SysUserImportResultVo importUser(List<SysUser> userList, Boolean isUpdateSupport, String operName);
}
