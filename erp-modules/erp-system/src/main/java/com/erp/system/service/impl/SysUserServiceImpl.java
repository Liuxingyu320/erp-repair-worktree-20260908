package com.erp.system.service.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.CollectionUtils;
import com.erp.common.core.constant.HttpStatus;
import com.erp.common.core.constant.UserConstants;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.core.utils.SpringUtils;
import com.erp.common.core.utils.StringUtils;
import com.erp.common.core.utils.bean.BeanValidators;
import com.erp.common.datascope.annotation.DataScope;
import com.erp.common.security.utils.SecurityUtils;
import com.erp.system.api.domain.SignCandidateUser;
import com.erp.system.api.domain.SignCandidateUserQuery;
import com.erp.system.api.domain.SignReadinessIssue;
import com.erp.system.api.constant.SigningProfileCodes;
import com.erp.system.api.domain.SysRole;
import com.erp.system.api.domain.SysUser;
import com.erp.system.api.domain.SysUserProfile;
import com.erp.system.domain.SysPost;
import com.erp.system.domain.SysUserPost;
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
import com.erp.system.mapper.SysPostMapper;
import com.erp.system.mapper.SysConfigMapper;
import com.erp.system.mapper.SysRoleMapper;
import com.erp.system.mapper.SysUserMapper;
import com.erp.system.mapper.SysUserPostMapper;
import com.erp.system.mapper.SysUserProfileMapper;
import com.erp.system.mapper.SysUserRoleMapper;
import com.erp.system.mapper.SysUserShopMapper;
import com.erp.system.service.ISysConfigService;
import com.erp.system.service.ISysDeptService;
import com.erp.system.service.ISysUserService;
import com.erp.system.service.SigningProfileNormalizer;
import com.erp.system.service.credential.UserCredentialProvisioningService;
import com.erp.system.service.support.TemporaryCredentialPolicy;
import com.erp.system.service.support.TemporaryPasswordGenerator;
import com.erp.system.service.support.SysUserPiiAuditService;
import com.erp.system.service.support.SigningProfileFactsHash;
import com.erp.system.service.support.UserSessionInvalidationService;
import com.erp.system.service.support.RoleAssignmentGuard;
import com.erp.system.support.HrEmployeeStatusCatalog;

/**
 * 用户 业务层处理
 * 
 * @author erp
 */
@Service
public class SysUserServiceImpl implements ISysUserService
{
    private static final Logger log = LoggerFactory.getLogger(SysUserServiceImpl.class);
    private static final Pattern MAINLAND_MOBILE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");
    private static final Pattern MAINLAND_ID_CARD_PATTERN = Pattern.compile(
            "^[1-9]\\d{5}(18|19|20)\\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])\\d{3}[0-9Xx]$");
    private static final int[] ID_CARD_CHECK_WEIGHTS = {7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2};
    private static final char[] ID_CARD_CHECK_CODES = {'1', '0', 'X', '9', '8', '7', '6', '5', '4', '3', '2'};

    @Autowired
    private SysUserMapper userMapper;

    @Autowired
    private SysUserProfileMapper profileMapper;

    @Autowired
    private SysUserProfileDerivationService profileDerivationService;

    @Autowired
    private SigningProfileNormalizer signingProfileNormalizer;

    @Autowired
    private SysRoleMapper roleMapper;

    @Autowired
    private SysPostMapper postMapper;

    @Autowired
    private SysUserRoleMapper userRoleMapper;

    @Autowired
    private SysUserPostMapper userPostMapper;

    @Autowired
    private SysUserShopMapper userShopMapper;

    @Autowired
    private SysConfigMapper configMapper;

    @Autowired
    private ISysConfigService configService;

    @Autowired
    private ISysDeptService deptService;

    @Autowired
    private UserCredentialProvisioningService credentialProvisioningService;

    @Autowired
    protected Validator validator;

    @Autowired
    private TemporaryPasswordGenerator temporaryPasswordGenerator;

    @Autowired
    private UserSessionInvalidationService userSessionInvalidationService;

    @Autowired
    private SysUserPiiAuditService userPiiAuditService;

    private Clock clock = Clock.systemDefaultZone();

    private TemporaryCredentialPolicy temporaryCredentialPolicy = new TemporaryCredentialPolicy();

    @Autowired(required = false)
    public void setClock(Clock clock)
    {
        if (clock != null)
        {
            this.clock = clock;
        }
    }

    @Autowired(required = false)
    public void setTemporaryCredentialPolicy(TemporaryCredentialPolicy temporaryCredentialPolicy)
    {
        if (temporaryCredentialPolicy != null)
        {
            this.temporaryCredentialPolicy = temporaryCredentialPolicy;
        }
    }

    /**
     * 根据条件分页查询用户列表
     * 
     * @param user 用户信息
     * @return 用户信息集合信息
     */
    @Override
    @DataScope(deptAlias = "d", userAlias = "u")
    public List<SysUser> selectUserList(SysUser user)
    {
        List<SysUser> users = userMapper.selectUserList(user);
        profileDerivationService.applyToUsers(users);
        if (users != null) users.forEach(this::normalizeEmployeeStatusForRead);
        return users;
    }

    @Override
    @DataScope(deptAlias = "d", userAlias = "u")
    public List<SysUser> selectUserOptionList(SysUser user)
    {
        return userMapper.selectUserOptionList(user);
    }

    @Override
    @DataScope(deptAlias = "d", userAlias = "u")
    public Set<Long> selectVisibleUserIds(Collection<Long> userIds)
    {
        if (userIds == null || userIds.isEmpty())
        {
            return Set.of();
        }
        LinkedHashSet<Long> candidates = userIds.stream()
                .filter(Objects::nonNull)
                .filter(userId -> userId > 0)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (candidates.isEmpty())
        {
            return Set.of();
        }
        SysUser query = new SysUser();
        query.getParams().put("candidateUserIds", candidates);
        List<Long> visible = userMapper.selectVisibleUserIds(query);
        return visible == null || visible.isEmpty() ? Set.of() : new LinkedHashSet<>(visible);
    }

    @Override
    @DataScope(deptAlias = "d", userAlias = "u")
    public List<SysUserListVo> selectUserManageList(SysUser user)
    {
        List<SysUserListVo> users = userMapper.selectUserManageList(user);
        if (users != null)
        {
            users.stream().map(SysUserListVo::getProfile).filter(Objects::nonNull)
                    .forEach(profile -> profile.setEmployeeStatus(
                            HrEmployeeStatusCatalog.normalizeForRead(profile.getEmployeeStatus())));
        }
        return users;
    }

    @Override
    @DataScope(deptAlias = "d", userAlias = "u")
    public SysUserSetupSummaryVo selectUserSetupSummary(SysUser user)
    {
        SysUserSetupSummaryVo summary = userMapper.selectUserSetupSummary(user);
        return summary == null ? new SysUserSetupSummaryVo() : summary;
    }

    @Override
    public SysUserManageDetailVo selectUserManageDetail(Long userId)
    {
        SysUserManageDetailVo user = userMapper.selectUserManageDetail(userId);
        if (user != null && user.getProfile() != null)
        {
            user.getProfile().setEmployeeStatus(
                    HrEmployeeStatusCatalog.normalizeForRead(user.getProfile().getEmployeeStatus()));
        }
        return user;
    }

    @Override
    public SysUserPiiDetailVo selectUserPiiById(Long userId, SysUserPiiAccessReason reason,
            Long viewerUserId)
    {
        SysUserPiiDetailVo detail = userMapper.selectUserPiiById(userId);
        userPiiAuditService.recordRead(viewerUserId, userId, reason, detail != null);
        if (detail != null) detail.clearProvidedValues();
        return detail;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateUserPii(Long userId, SysUserPiiUpdateRequest request,
            SysUserPiiAccessReason reason, Long viewerUserId, String operator)
    {
        if (request == null)
        {
            throw new ServiceException("个人信息修改内容不能为空");
        }
        SysUserPiiDetailVo current = userMapper.selectUserPiiById(userId);
        if (current == null)
        {
            throw new ServiceException("用户不存在");
        }
        Map<String, Object> currentValues = current.getAllValues();
        Map<String, Object> changed = new LinkedHashMap<>();
        request.getProvidedValues().forEach((field, value) -> {
            if (!Objects.equals(currentValues.get(field), value)) changed.put(field, value);
        });
        Set<String> changedFields = new LinkedHashSet<>(changed.keySet());
        if (changed.isEmpty())
        {
            userPiiAuditService.recordUpdate(viewerUserId, userId, reason, changedFields);
            return 0;
        }

        assertPiiUnique(userId, changed);
        Map<String, Object> baseValues = new LinkedHashMap<>();
        for (String field : List.of("email", "phonenumber", "sex"))
        {
            if (changed.containsKey(field)) baseValues.put(field, changed.remove(field));
        }
        int rows = 0;
        if (!baseValues.isEmpty()) rows += userMapper.updateUserPii(userId, baseValues, operator);
        if (!changed.isEmpty())
        {
            profileMapper.insertUserProfileIfAbsent(userId, operator);
            rows += profileMapper.patchUserProfile(userId, changed, operator);
        }
        if (rows <= 0)
        {
            throw new ServiceException("个人信息未保存，请重试");
        }
        userPiiAuditService.recordUpdate(viewerUserId, userId, reason, changedFields);
        return 1;
    }

    private void assertPiiUnique(Long userId, Map<String, Object> changed)
    {
        if (changed.containsKey("phonenumber") && changed.get("phonenumber") != null)
        {
            SysUser probe = new SysUser(userId);
            probe.setPhonenumber(String.valueOf(changed.get("phonenumber")));
            if (!checkPhoneUnique(probe)) throw new ServiceException("手机号码已存在");
        }
        if (changed.containsKey("email") && changed.get("email") != null)
        {
            SysUser probe = new SysUser(userId);
            probe.setEmail(String.valueOf(changed.get("email")));
            if (!checkEmailUnique(probe)) throw new ServiceException("邮箱账号已存在");
        }
    }

    @Override
    @DataScope(deptAlias = "d", userAlias = "u")
    public List<SysUserPiiExportVo> selectUserPiiExportList(SysUser user,
            SysUserPiiAccessReason reason, Long viewerUserId)
    {
        List<SysUserPiiExportVo> rows = userMapper.selectUserPiiExportList(user);
        userPiiAuditService.recordExport(viewerUserId, reason, rows);
        return rows;
    }

    /**
     * 根据条件查询签约候选员工列表
     *
     * @param query 查询条件
     * @return 签约候选员工集合信息
     */
    @Override
    public List<SignCandidateUser> selectSignCandidateUsers(SignCandidateUserQuery query)
    {
        List<SignCandidateUser> candidates = userMapper.selectSignCandidateUsers(query);
        if (candidates == null)
        {
            return new ArrayList<>();
        }
        candidates.forEach(candidate -> {
            if (candidate != null)
            {
                candidate.setProfileFactsHash(SigningProfileFactsHash.of(candidate));
            }
            applySignReadiness(candidate);
        });
        return candidates;
    }

    private void applySignReadiness(SignCandidateUser candidate)
    {
        if (candidate == null)
        {
            return;
        }
        List<SignReadinessIssue> issues = new ArrayList<>();
        if (!isValidMainlandMobile(candidate.getPhonenumber()))
        {
            issues.add(issue("MISSING_PHONE", "phonenumber", "手机号缺失或格式不正确"));
        }
        if (!isValidMainlandIdCard(candidate.getIdNumber()))
        {
            issues.add(issue("INVALID_ID_CARD", "idNumber", "身份证号缺失或格式不正确"));
        }
        if (isBlank(candidate.getCurrentAddress()))
        {
            issues.add(issue("MISSING_ADDRESS", "currentAddress", "现居住地址未填写"));
        }
        if (isBlank(candidate.getPostNames()))
        {
            issues.add(issue("MISSING_POST", "postNames", "岗位未配置"));
        }
        if (isBlank(candidate.getJobGrade()))
        {
            issues.add(issue("MISSING_GRADE", "jobGrade", "职级未填写"));
        }
        if (isBlank(candidate.getLegalEntity()))
        {
            issues.add(issue("MISSING_LEGAL_ENTITY", "legalEntity", "法人单位未填写"));
        }
        addContractDateIssue(issues, candidate.getContractStartDate(), candidate.getContractEndDate());
        if (!SigningProfileCodes.isKnownContractType(trimToNull(candidate.getContractType())))
        {
            String value = trimToNull(candidate.getContractType());
            String message = value == null ? "合同类型未填写" : "不支持的合同类型：" + value;
            issues.add(issue("UNSUPPORTED_CONTRACT_TYPE", "contractType", message));
        }
        if (!SigningProfileCodes.isKnownSocialType(trimToNull(candidate.getSocialType())))
        {
            String value = trimToNull(candidate.getSocialType());
            String message = value == null ? "社保类型未填写" : "不支持的社保类型：" + value;
            issues.add(issue("UNSUPPORTED_SOCIAL_TYPE", "socialType", message));
        }
        candidate.setReadinessIssues(issues);
    }

    private void addContractDateIssue(List<SignReadinessIssue> issues, String startValue, String endValue)
    {
        String start = trimToNull(startValue);
        String end = trimToNull(endValue);
        if (start == null || end == null)
        {
            issues.add(issue("INVALID_CONTRACT_DATES", "contractDates", "合同开始日期和结束日期必须完整"));
            return;
        }
        try
        {
            if (LocalDate.parse(start).isAfter(LocalDate.parse(end)))
            {
                issues.add(issue("INVALID_CONTRACT_DATES", "contractDates", "合同开始日期不能晚于合同结束日期"));
            }
        }
        catch (DateTimeParseException e)
        {
            issues.add(issue("INVALID_CONTRACT_DATES", "contractDates", "合同日期格式应为yyyy-MM-dd"));
        }
    }

    private SignReadinessIssue issue(String code, String field, String message)
    {
        return new SignReadinessIssue(code, field, message);
    }

    private boolean isValidMainlandMobile(String value)
    {
        String normalized = trimToNull(value);
        return normalized != null && MAINLAND_MOBILE_PATTERN.matcher(normalized).matches();
    }

    private boolean isValidMainlandIdCard(String value)
    {
        String normalized = trimToNull(value);
        if (normalized == null || !MAINLAND_ID_CARD_PATTERN.matcher(normalized).matches())
        {
            return false;
        }
        normalized = normalized.toUpperCase();
        try
        {
            LocalDate.parse(normalized.substring(6, 14), java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
        }
        catch (DateTimeParseException e)
        {
            return false;
        }
        int sum = 0;
        for (int index = 0; index < ID_CARD_CHECK_WEIGHTS.length; index++)
        {
            sum += Character.digit(normalized.charAt(index), 10) * ID_CARD_CHECK_WEIGHTS[index];
        }
        return ID_CARD_CHECK_CODES[sum % 11] == normalized.charAt(17);
    }

    private boolean isBlank(String value)
    {
        return trimToNull(value) == null;
    }

    private String trimToNull(String value)
    {
        if (value == null)
        {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * 根据条件分页查询已分配用户角色列表
     * 
     * @param user 用户信息
     * @return 用户信息集合信息
     */
    @Override
    @DataScope(deptAlias = "d", userAlias = "u")
    public List<SysUser> selectAllocatedList(SysUser user)
    {
        return userMapper.selectAllocatedList(user);
    }

    @Override
    @DataScope(deptAlias = "d", userAlias = "u")
    public List<SysUserAssignmentVo> selectAllocatedAssignmentList(SysUser user)
    {
        return userMapper.selectAllocatedAssignmentList(user);
    }

    /**
     * 根据条件分页查询未分配用户角色列表
     * 
     * @param user 用户信息
     * @return 用户信息集合信息
     */
    @Override
    @DataScope(deptAlias = "d", userAlias = "u")
    public List<SysUser> selectUnallocatedList(SysUser user)
    {
        return userMapper.selectUnallocatedList(user);
    }

    @Override
    @DataScope(deptAlias = "d", userAlias = "u")
    public List<SysUserAssignmentVo> selectUnallocatedAssignmentList(SysUser user)
    {
        return userMapper.selectUnallocatedAssignmentList(user);
    }

    @Override
    @DataScope(deptAlias = "d", userAlias = "u")
    public List<SysSalaryUserOptionVo> selectSalaryUserOptions(SysUser user)
    {
        return userMapper.selectSalaryUserOptions(user);
    }

    /**
     * 通过用户名查询用户
     * 
     * @param userName 用户名
     * @return 用户对象信息
     */
    @Override
    public SysUser selectUserByUserName(String userName)
    {
        return userMapper.selectUserByUserName(userName);
    }

    /**
     * 通过用户ID查询用户
     * 
     * @param userId 用户ID
     * @return 用户对象信息
     */
    @Override
    public SysUser selectUserById(Long userId)
    {
        return normalizeEmployeeStatusForRead(
                profileDerivationService.applyToUser(userMapper.selectUserById(userId)));
    }

    private SysUser normalizeEmployeeStatusForRead(SysUser user)
    {
        if (user != null && user.getProfile() != null)
            user.getProfile().setEmployeeStatus(
                    HrEmployeeStatusCatalog.normalizeForRead(user.getProfile().getEmployeeStatus()));
        return user;
    }

    @Override
    public SysUserProfile previewDerivedProfile(SysUser user)
    {
        return profileDerivationService.preview(user);
    }

    /**
     * 查询用户所属角色组
     * 
     * @param userName 用户名
     * @return 结果
     */
    @Override
    public String selectUserRoleGroup(String userName)
    {
        List<SysRole> list = roleMapper.selectRolesByUserName(userName);
        if (CollectionUtils.isEmpty(list))
        {
            return StringUtils.EMPTY;
        }
        return list.stream().map(SysRole::getRoleName).collect(Collectors.joining(","));
    }

    /**
     * 查询用户所属岗位组
     * 
     * @param userName 用户名
     * @return 结果
     */
    @Override
    public String selectUserPostGroup(String userName)
    {
        List<SysPost> list = postMapper.selectPostsByUserName(userName);
        if (CollectionUtils.isEmpty(list))
        {
            return StringUtils.EMPTY;
        }
        return list.stream().map(SysPost::getPostName).collect(Collectors.joining(","));
    }

    /**
     * 校验用户名称是否唯一
     * 
     * @param user 用户信息
     * @return 结果
     */
    @Override
    public boolean checkUserNameUnique(SysUser user)
    {
        Long userId = StringUtils.isNull(user.getUserId()) ? -1L : user.getUserId();
        SysUser info = userMapper.checkUserNameUnique(user.getUserName());
        if (StringUtils.isNotNull(info) && info.getUserId().longValue() != userId.longValue())
        {
            return UserConstants.NOT_UNIQUE;
        }
        return UserConstants.UNIQUE;
    }

    /**
     * 校验手机号码是否唯一
     *
     * @param user 用户信息
     * @return
     */
    @Override
    public boolean checkPhoneUnique(SysUser user)
    {
        Long userId = StringUtils.isNull(user.getUserId()) ? -1L : user.getUserId();
        SysUser info = userMapper.checkPhoneUnique(user.getPhonenumber());
        if (StringUtils.isNotNull(info) && info.getUserId().longValue() != userId.longValue())
        {
            return UserConstants.NOT_UNIQUE;
        }
        return UserConstants.UNIQUE;
    }

    /**
     * 校验email是否唯一
     *
     * @param user 用户信息
     * @return
     */
    @Override
    public boolean checkEmailUnique(SysUser user)
    {
        Long userId = StringUtils.isNull(user.getUserId()) ? -1L : user.getUserId();
        SysUser info = userMapper.checkEmailUnique(user.getEmail());
        if (StringUtils.isNotNull(info) && info.getUserId().longValue() != userId.longValue())
        {
            return UserConstants.NOT_UNIQUE;
        }
        return UserConstants.UNIQUE;
    }

    /**
     * 校验用户是否允许操作
     * 
     * @param user 用户信息
     */
    @Override
    public void checkUserAllowed(SysUser user)
    {
        if (StringUtils.isNotNull(user.getUserId()) && isSuperAdminUser(user))
        {
            throw new ServiceException("不允许操作超级管理员用户");
        }
    }

    private boolean isSuperAdminUser(SysUser user)
    {
        if (user.isAdmin())
        {
            return true;
        }
        List<SysRole> roles = roleMapper.selectRolePermissionByUserId(user.getUserId());
        return roles != null && roles.stream().anyMatch(SysRole::isAdmin);
    }

    /**
     * 校验用户是否有数据权限
     * 
     * @param userId 用户id
     */
    @Override
    public void checkUserDataScope(Long userId)
    {
        if (!SecurityUtils.isAdmin())
        {
            SysUser user = new SysUser();
            user.setUserId(userId);
            List<SysUser> users = SpringUtils.getAopProxy(this).selectUserList(user);
            if (StringUtils.isEmpty(users))
            {
                throw new ServiceException("没有权限访问用户数据！", HttpStatus.FORBIDDEN);
            }
        }
    }

    /**
     * 新增保存用户信息
     * 
     * @param user 用户信息
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertUser(SysUser user)
    {
        configMapper.lockSignHrState();
        normalizeSigningProfile(user);
        applyEmployeeAccountStatus(user);
        if (StringUtils.isEmpty(user.getMustChangePassword()))
        {
            user.setMustChangePassword("1");
        }
        // 新增用户信息
        List<SysRole> assignedRoles = RoleAssignmentGuard.lockNewUserRoles(roleMapper, user.getRoleIds());
        int rows = userMapper.insertUser(user);
        if (rows > 0)
        {
            saveUserProfile(user, true);
        }
        // 新增用户岗位关联
        insertUserPost(user);
        // 新增用户与角色管理
        RoleAssignmentGuard.insertNewUserRoles(userRoleMapper, user.getUserId(), assignedRoles);
        if (rows > 0)
        {
            configMapper.syncSignHrPermissions();
        }
        return rows;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SysTemporaryCredentialVo insertUserWithTemporaryCredential(SysUser user, String passwordPolicy)
    {
        String temporaryPassword = temporaryPasswordGenerator.generate(passwordPolicy);
        Date expiresAt = temporaryCredentialExpiry();
        user.setPassword(SecurityUtils.encryptPassword(temporaryPassword));
        user.setPwdUpdateDate(null);
        user.setCredentialState(SysUser.CREDENTIAL_STATE_TEMPORARY);
        user.setTemporaryPasswordExpiresAt(expiresAt);
        int rows = insertUser(user);
        user.setPassword(null);
        if (rows <= 0)
        {
            throw new ServiceException("新增用户失败");
        }
        return new SysTemporaryCredentialVo(user.getUserId(), user.getUserName(),
                temporaryPassword, expiresAt);
    }

    /**
     * 注册用户信息
     * 
     * @param user 用户信息
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean registerUser(SysUser user)
    {
        configMapper.lockSignHrState();
        boolean inserted = userMapper.insertUser(user) > 0;
        if (inserted)
        {
            configMapper.syncSignHrPermissions();
        }
        return inserted;
    }

    /**
     * 修改保存用户信息
     * 
     * @param user 用户信息
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateUser(SysUser user)
    {
        configMapper.lockSignHrState();
        Long userId = user.getUserId();
        normalizeSigningProfile(user);
        applyEmployeeAccountStatus(user);
        assertConfiguredHrRemainsValid(userId, user.getStatus(), user.getDelFlag(), false);
        assertAssignedEmployeePostUnchanged(user);
        // Omitted roles in an ordinary profile save preserve the current assignments.
        if (user.getRoleIds() != null)
            RoleAssignmentGuard.replaceRoles(roleMapper, userRoleMapper, userId, user.getRoleIds());
        // 删除用户与岗位关联
        userPostMapper.deleteUserPostByUserId(userId);
        // 新增用户与岗位管理
        insertUserPost(user);
        int rows = userMapper.updateUser(user);
        if (rows > 0)
        {
            saveUserProfile(user, false);
            configMapper.syncSignHrPermissions();
            userSessionInvalidationService.record(userId,
                    UserSessionInvalidationService.USER_ROLES_CHANGED);
        }
        return rows;
    }

    /**
     * 修改人事员工档案信息，不变更用户角色和岗位关联。
     *
     * @param user 用户和员工档案信息
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateHrEmployeeProfile(SysUser user)
    {
        configMapper.lockSignHrState();
        normalizeSigningProfile(user);
        applyEmployeeAccountStatus(user);
        assertConfiguredHrRemainsValid(user.getUserId(), user.getStatus(), user.getDelFlag(), false);
        int rows = userMapper.updateHrEmployeeProfileUser(user);
        if (rows > 0)
        {
            saveUserProfile(user, false);
            configMapper.syncSignHrPermissions();
        }
        return rows;
    }

    /**
     * 用户授权角色
     * 
     * @param userId 用户ID
     * @param roleIds 角色组
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void insertUserAuth(Long userId, Long[] roleIds)
    {
        if (roleIds == null) throw new ServiceException("角色选择不能为空，请明确提交角色列表");
        checkUserAllowed(new SysUser(userId));
        checkUserDataScope(userId);
        configMapper.lockSignHrState();
        RoleAssignmentGuard.replaceRoles(roleMapper, userRoleMapper, userId, roleIds);
        configMapper.syncSignHrPermissions();
        userSessionInvalidationService.record(userId,
                UserSessionInvalidationService.USER_ROLES_CHANGED);
    }

    /**
     * 修改用户状态
     * 
     * @param user 用户信息
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateUserStatus(SysUser user)
    {
        configMapper.lockSignHrState();
        assertConfiguredHrRemainsValid(user.getUserId(), user.getStatus(), user.getDelFlag(), false);
        int rows = userMapper.updateUserStatus(user.getUserId(), user.getStatus());
        if (rows > 0)
        {
            configMapper.syncSignHrPermissions();
            if (UserConstants.USER_DISABLE.equals(user.getStatus()))
            {
                userSessionInvalidationService.record(user.getUserId(),
                        UserSessionInvalidationService.USER_DISABLED);
            }
        }
        return rows;
    }

    /**
     * 修改用户基本信息
     * 
     * @param user 用户信息
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateUserProfile(SysUser user)
    {
        configMapper.lockSignHrState();
        assertConfiguredHrRemainsValid(user.getUserId(), user.getStatus(), user.getDelFlag(), false);
        boolean updated = userMapper.updateUser(user) > 0;
        if (updated)
        {
            configMapper.syncSignHrPermissions();
        }
        return updated;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateSelfProfile(SysUser user, Map<String, Object> profileValues, String operator)
    {
        configMapper.lockSignHrState();
        assertConfiguredHrRemainsValid(user.getUserId(), user.getStatus(), user.getDelFlag(), false);
        user.setUpdateBy(operator);
        if (userMapper.updateUser(user) <= 0)
        {
            return false;
        }

        if (profileValues != null && !profileValues.isEmpty())
        {
            profileMapper.insertUserProfileIfAbsent(user.getUserId(), operator);
            if (profileMapper.patchUserProfile(user.getUserId(), profileValues, operator) <= 0)
            {
                throw new ServiceException("保存员工个人资料失败");
            }
        }
        configMapper.syncSignHrPermissions();
        return true;
    }

    /**
     * 修改用户头像
     * 
     * @param userId 用户ID
     * @param avatar 头像地址
     * @return 结果
     */
    @Override
    public boolean updateUserAvatar(Long userId, String avatar)
    {
        return userMapper.updateUserAvatar(userId, avatar) > 0;
    }

    /**
     * 更新用户登录信息（IP和登录时间）
     * 
     * @param user 用户信息
     * @return 结果
     */
    public boolean updateLoginInfo(SysUser user)
    {
        return userMapper.updateLoginInfo(user) > 0;
    }

    /**
     * 重置用户密码
     * 
     * @param user 用户信息
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public SysTemporaryCredentialVo resetTemporaryCredential(SysUser user, String passwordPolicy)
    {
        String temporaryPassword = temporaryPasswordGenerator.generate(passwordPolicy);
        Date expiresAt = temporaryCredentialExpiry();
        String encodedPassword = SecurityUtils.encryptPassword(temporaryPassword);
        int rows = userMapper.resetUserTemporaryCredential(user.getUserId(), encodedPassword,
                expiresAt, user.getUpdateBy());
        if (rows <= 0)
        {
            throw new ServiceException("重新生成临时密码失败");
        }
        userSessionInvalidationService.record(user.getUserId(),
                UserSessionInvalidationService.PASSWORD_RESET);
        return new SysTemporaryCredentialVo(user.getUserId(), user.getUserName(),
                temporaryPassword, expiresAt);
    }

    /**
     * 重置用户密码
     * 
     * @param userId 用户ID
     * @param password 密码
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int activateUserPassword(Long userId, String password, String updateBy)
    {
        return userMapper.activateUserPassword(userId, password, updateBy);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int changeOwnPassword(Long userId, String expectedPasswordHash, String passwordHash,
            String updateBy, String retainedUserKey)
    {
        if (userId == null || userId <= 0 || StringUtils.isEmpty(expectedPasswordHash)
                || StringUtils.isEmpty(passwordHash) || StringUtils.isEmpty(retainedUserKey))
            throw new ServiceException("登录状态已过期，请重新登录", 401);
        int rows = userMapper.changeOwnPasswordIfCurrent(userId, expectedPasswordHash, passwordHash, updateBy);
        if (rows != 1)
            throw new ServiceException("账号状态或密码已变更，请重新登录后再试", 409);
        userSessionInvalidationService.record(userId,
                UserSessionInvalidationService.PASSWORD_CHANGED, retainedUserKey);
        return rows;
    }

    private Date temporaryCredentialExpiry()
    {
        return temporaryCredentialPolicy.expiresAt(clock);
    }

    /**
     * 新增用户岗位信息
     * 
     * @param user 用户对象
     */
    public void insertUserPost(SysUser user)
    {
        Long[] posts = user.getPostIds();
        if (StringUtils.isNotEmpty(posts))
        {
            // 新增用户与岗位管理
            List<SysUserPost> list = new ArrayList<SysUserPost>();
            for (Long postId : posts)
            {
                SysUserPost up = new SysUserPost();
                up.setUserId(user.getUserId());
                up.setPostId(postId);
                list.add(up);
            }
            userPostMapper.batchUserPost(list);
        }
    }

    /**
     * 通过用户ID删除用户
     * 
     * @param userId 用户ID
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteUserById(Long userId)
    {
        configMapper.lockSignHrState();
        assertConfiguredHrRemainsValid(userId, null, null, true);
        // 删除用户与角色关联
        userRoleMapper.deleteUserRoleByUserId(userId);
        // 删除用户与岗位表
        userPostMapper.deleteUserPostByUserId(userId);
        // 删除用户与店铺关联
        userShopMapper.deleteUserShopByUserId(userId);
        int rows = userMapper.deleteUserById(userId);
        if (rows > 0)
        {
            configMapper.syncSignHrPermissions();
            userSessionInvalidationService.record(userId,
                    UserSessionInvalidationService.USER_DELETED);
        }
        return rows;
    }

    /**
     * 批量删除用户信息
     * 
     * @param userIds 需要删除的用户ID
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteUserByIds(Long[] userIds)
    {
        configMapper.lockSignHrState();
        for (Long userId : userIds)
        {
            assertConfiguredHrRemainsValid(userId, null, null, true);
        }
        for (Long userId : userIds)
        {
            checkUserAllowed(new SysUser(userId));
            checkUserDataScope(userId);
        }
        // 删除用户与角色关联
        userRoleMapper.deleteUserRole(userIds);
        // 删除用户与岗位关联
        userPostMapper.deleteUserPost(userIds);
        // 删除用户与店铺关联
        userShopMapper.deleteUserShopByUserIds(userIds);
        int rows = userMapper.deleteUserByIds(userIds);
        if (rows > 0)
        {
            configMapper.syncSignHrPermissions();
            for (Long userId : userIds)
            {
                userSessionInvalidationService.record(userId,
                        UserSessionInvalidationService.USER_DELETED);
            }
        }
        return rows;
    }

    /**
     * 导入用户数据
     * 
     * @param userList 用户数据列表
     * @param isUpdateSupport 是否更新支持，如果已存在，则进行更新数据
     * @param operName 操作用户
     * @return 结果
    */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public SysUserImportResultVo importUser(List<SysUser> userList, Boolean isUpdateSupport, String operName)
    {
        if (StringUtils.isNull(userList) || userList.size() == 0)
        {
            throw new ServiceException("导入用户数据不能为空！");
        }
        if (userList.size() > 500)
        {
            throw new ServiceException("单次最多导入500个用户");
        }
        configMapper.lockSignHrState();
        boolean changed = false;
        int rowNumber = 0;
        SysUserImportResultVo result = new SysUserImportResultVo();
        result.setTotalCount(userList.size());
        for (SysUser user : userList)
        {
            rowNumber++;
            try
            {
                normalizeSigningProfile(user);
                // 验证是否存在这个用户
                SysUser u = findImportMatchedUser(user);
                if (StringUtils.isNull(u))
                {
                    BeanValidators.validateWithException(validator, user);
                    deptService.checkDeptDataScope(user.getDeptId());
                    user.setCreateBy(operName);
                    String passwordPolicy = configService.selectConfigByKey("sys.account.chrtype");
                    SysTemporaryCredentialVo credential = createImportedUser(user, passwordPolicy);
                    result.addTemporaryCredential(credential);
                    result.incrementCreatedCount();
                    changed = true;
                }
                else if (isUpdateSupport)
                {
                    BeanValidators.validateWithException(validator, user);
                    checkUserAllowed(u);
                    checkUserDataScope(u.getUserId());
                    deptService.checkDeptDataScope(user.getDeptId());
                    user.setUserId(u.getUserId());
                    user.setUpdateBy(operName);
                    applyEmployeeAccountStatus(user);
                    assertConfiguredHrRemainsValid(user.getUserId(), user.getStatus(), user.getDelFlag(), false);
                    int rows = userMapper.updateUser(user);
                    if (rows > 0)
                    {
                        saveUserProfile(user, false);
                        changed = true;
                    }
                    result.incrementUpdatedCount();
                }
                else
                {
                    result.addFailure(rowNumber, user.getUserName(), "账号已存在");
                }
            }
            catch (Exception e)
            {
                result.addFailure(rowNumber, user == null ? null : user.getUserName(), safeImportFailureMessage(e));
                log.warn("用户导入行失败 row={}, errorType={}", rowNumber, e.getClass().getSimpleName());
            }
        }
        if (result.getFailureCount() > 0)
        {
            if (TransactionSynchronizationManager.isActualTransactionActive())
            {
                TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            }
            result.markRolledBack();
            return result;
        }
        if (changed)
        {
            configMapper.syncSignHrPermissions();
        }
        result.markCommitted();
        return result;
    }

    private SysTemporaryCredentialVo createImportedUser(SysUser user, String passwordPolicy)
    {
        String temporaryPassword = temporaryPasswordGenerator.generate(passwordPolicy);
        Date expiresAt = temporaryCredentialExpiry();
        user.setPassword(SecurityUtils.encryptPassword(temporaryPassword));
        user.setPwdUpdateDate(null);
        user.setCredentialState(SysUser.CREDENTIAL_STATE_TEMPORARY);
        user.setTemporaryPasswordExpiresAt(expiresAt);
        applyEmployeeAccountStatus(user);
        int rows = userMapper.insertUser(user);
        if (rows <= 0)
        {
            user.setPassword(null);
            throw new ServiceException("新增账号失败");
        }
        saveUserProfile(user, true);
        user.setPassword(null);
        return new SysTemporaryCredentialVo(user.getUserId(), user.getUserName(), temporaryPassword, expiresAt);
    }

    @Override
    public int resetPwd(SysUser user)
    {
        return userMapper.resetUserPwd(user.getUserId(), user.getPassword(), "1");
    }

    private String safeImportFailureMessage(Exception exception)
    {
        String message = exception.getMessage();
        if (StringUtils.isEmpty(message))
        {
            return "数据校验失败";
        }
        String normalized = message.replace('\r', ' ').replace('\n', ' ').replace('<', ' ').replace('>', ' ').trim();
        return normalized.length() > 200 ? normalized.substring(0, 200) : normalized;
    }

    private SysUser findImportMatchedUser(SysUser user)
    {
        SysUser matchedUser = userMapper.selectUserByUserName(user.getUserName());
        if (StringUtils.isNotNull(matchedUser) || !user.hasProfile())
        {
            return matchedUser;
        }
        String employeeNo = user.getProfile().getEmployeeNo();
        if (StringUtils.isEmpty(employeeNo))
        {
            return null;
        }
        SysUserProfile matchedProfile = profileMapper.selectUserProfileByEmployeeNo(employeeNo);
        if (StringUtils.isNull(matchedProfile) || StringUtils.isNull(matchedProfile.getUserId()))
        {
            return null;
        }
        return userMapper.selectUserById(matchedProfile.getUserId());
    }

    private void applyEmployeeAccountStatus(SysUser user)
    {
        if (StringUtils.isNotNull(user) && user.hasProfile() && "离职".equals(user.getProfile().getEmployeeStatus()))
        {
            user.setStatus("1");
        }
    }

    private void assertConfiguredHrRemainsValid(Long userId, String targetStatus,
            String targetDelFlag, boolean deleting)
    {
        Long configuredHrUserId = configMapper.selectConfiguredSignHrUserId();
        if (!Objects.equals(configuredHrUserId, userId))
        {
            return;
        }
        if (deleting || (targetStatus != null && !UserConstants.NORMAL.equals(targetStatus))
                || (targetDelFlag != null && !UserConstants.NORMAL.equals(targetDelFlag)))
        {
            throw new ServiceException("该用户是默认签约任务接收人，请先更换签约默认接收人配置");
        }
    }

    private void normalizeSigningProfile(SysUser user)
    {
        if (StringUtils.isNotNull(user) && user.hasProfile())
        {
            signingProfileNormalizer.normalize(user.getProfile());
        }
    }

    private void saveUserProfile(SysUser user, boolean insert)
    {
        if (StringUtils.isNull(user) || StringUtils.isNull(user.getUserId()) || !user.hasProfile())
        {
            return;
        }
        SysUserProfile profile = user.getProfile();
        profile.setUserId(user.getUserId());
        if (profile.getDeptId() == null && user.getDeptId() != null)
        {
            profile.setDeptId(user.getDeptId());
        }
        if (insert)
        {
            profile.setCreateBy(user.getCreateBy());
        }
        profile.setUpdateBy(user.getUpdateBy());
        SysUserProfile existing = profileMapper.selectUserProfileByUserId(user.getUserId());
        java.math.BigDecimal[] submittedSalary = { profile.getBaseSalary(), profile.getPostSalary(),
                profile.getFieldAllowance(), profile.getPerformanceSalary(), profile.getSalaryTotal() };
        java.math.BigDecimal[] currentSalary = existing == null ? new java.math.BigDecimal[5]
                : new java.math.BigDecimal[] { existing.getBaseSalary(), existing.getPostSalary(),
                    existing.getFieldAllowance(), existing.getPerformanceSalary(), existing.getSalaryTotal() };
        for (int index = 0; index < submittedSalary.length; index++)
            if (submittedSalary[index] != null && (currentSalary[index] == null
                    || submittedSalary[index].compareTo(currentSalary[index]) != 0))
                throw new ServiceException("普通员工资料不能修改工资，请在员工档案批量入职合同中确认");

        if (StringUtils.isNull(existing))
        {
            if (StringUtils.isEmpty(profile.getCreateBy()))
            {
                profile.setCreateBy(user.getUpdateBy());
            }
            profileMapper.insertUserProfile(profile);
        }
        else
        {
            profile.setProfileId(existing.getProfileId());
            String assignedEmployeeNo = StringUtils.trim(existing.getEmployeeNo());
            String submittedEmployeeNo = StringUtils.trim(profile.getEmployeeNo());
            if (StringUtils.isNotEmpty(assignedEmployeeNo))
            {
                if (StringUtils.isNotEmpty(submittedEmployeeNo)
                        && !StringUtils.equals(assignedEmployeeNo, submittedEmployeeNo))
                {
                    throw new ServiceException("员工号一经分配不可修改");
                }
                profile.setEmployeeNo(assignedEmployeeNo);
            }
            if (profile.getDeptId() == null)
            {
                profile.setDeptId(existing.getDeptId());
            }
            if (StringUtils.isEmpty(profile.getUpdateBy()))
            {
                profile.setUpdateBy(user.getCreateBy());
            }
            profileMapper.updateUserProfile(profile);
        }
    }

    private void assertAssignedEmployeePostUnchanged(SysUser user)
    {
        if (user == null || user.getUserId() == null)
        {
            return;
        }
        SysUserProfile profile = profileMapper.selectUserProfileByUserId(user.getUserId());
        if (profile == null || StringUtils.isEmpty(StringUtils.trim(profile.getEmployeeNo())))
        {
            return;
        }
        List<Long> current = userPostMapper.selectPostIdsByUserId(user.getUserId());
        List<Long> normalizedCurrent = current == null ? List.of() : current.stream()
                .filter(Objects::nonNull).distinct().sorted().collect(Collectors.toList());
        List<Long> requested = user.getPostIds() == null ? List.of()
                : java.util.Arrays.stream(user.getPostIds()).filter(Objects::nonNull)
                        .distinct().sorted().collect(Collectors.toList());
        if (!normalizedCurrent.equals(requested))
        {
            throw new ServiceException("已分配员工号的岗位变更，请通过员工档案的“确认调岗”办理");
        }
    }

}
