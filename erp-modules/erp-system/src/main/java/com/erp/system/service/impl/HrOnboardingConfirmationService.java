package com.erp.system.service.impl;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.security.utils.SecurityUtils;
import com.erp.system.api.domain.SysUser;
import com.erp.system.api.domain.SysUserProfile;
import com.erp.system.api.domain.SysRole;
import com.erp.system.domain.HrOnboarding;
import com.erp.system.domain.HrOnboardingOperationLog;
import com.erp.system.domain.HrOnboardingPositionConfig;
import com.erp.system.domain.vo.HrOnboardingCompletionVo;
import com.erp.system.domain.vo.HrOnboardingConfirmRequest;
import com.erp.system.domain.vo.HrOnboardingConfirmResult;
import com.erp.system.domain.vo.HrOnboardingConflictVo;
import com.erp.system.domain.vo.HrOnboardingQuery;
import com.erp.system.exception.HrOnboardingValidationException;
import com.erp.system.mapper.HrOnboardingMapper;
import com.erp.system.mapper.HrOnboardingOperationLogMapper;
import com.erp.system.mapper.SysUserMapper;
import com.erp.system.mapper.SysRoleMapper;
import com.erp.system.mapper.SysUserPostMapper;
import com.erp.system.mapper.SysUserProfileMapper;
import com.erp.system.mapper.SysUserRoleMapper;
import com.erp.system.mapper.SysUserShopMapper;
import com.erp.system.service.IHrOnboardingPositionConfigService;
import com.erp.system.service.ISysUserService;
import com.erp.system.service.ISysRoleService;
import com.erp.system.service.ISysUserShopService;
import com.erp.system.service.credential.UserCredentialProvisioningService;
import com.erp.system.service.support.TemporaryCredentialPolicy;
import com.erp.system.support.HrEmployeeNoGenerator;
import com.erp.system.support.HrEmployeeFieldRegistry;
import com.erp.system.support.HrPositionNoFormatter;

@Service
public class HrOnboardingConfirmationService
{
    private static final String CREATE_NEW = "CREATE_NEW";
    private static final String BIND_EXISTING = "BIND_EXISTING";
    private static final String REHIRE_EXISTING = "REHIRE_EXISTING";
    private static final String CONFIGURATION_RISK = "ACCOUNT_CONFIGURATION_MISSING";
    private static final Pattern NUMBER_PATTERN = Pattern.compile("([0-9]+)");
    private static final Pattern MAINLAND_ID_CARD_PATTERN = Pattern.compile(
            "^[1-9]\\d{5}(18|19|20)\\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])\\d{3}[0-9Xx]$");
    private static final int[] ID_CARD_CHECK_WEIGHTS = {7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2};
    private static final char[] ID_CARD_CHECK_CODES = {'1', '0', 'X', '9', '8', '7', '6', '5', '4', '3', '2'};
    private static final Pattern MAINLAND_MOBILE_PATTERN = Pattern.compile("1[3-9][0-9]{9}");

    private final HrOnboardingMapper onboardingMapper;
    private final HrOnboardingOperationLogMapper logMapper;
    private final HrOnboardingAccessService accessService;
    private final HrOnboardingRuleService rules;
    private final IHrOnboardingPositionConfigService configService;
    private final HrOnboardingConflictService conflictService;
    private final HrEmployeeNoGenerator employeeNoGenerator;
    private final SysUserMapper userMapper;
    private final SysUserProfileMapper profileMapper;
    private final SysUserRoleMapper roleMapper;
    private final SysUserPostMapper postMapper;
    private final SysUserShopMapper shopMapper;
    private final ISysUserShopService userShopService;
    private final ISysUserService userService;
    private final SysRoleMapper systemRoleMapper;
    private final ISysRoleService roleService;
    private final Clock clock;
    private final TemporaryCredentialPolicy temporaryCredentialPolicy;
    private final LongSupplier currentUserId;
    private final BooleanSupplier currentUserAdmin;
    private final Supplier<String> passwordGenerator;
    private final Function<String, String> passwordEncoder;
    private final HrEmployeeFieldRegistry employeeFieldRegistry = new HrEmployeeFieldRegistry();

    @Autowired
    public HrOnboardingConfirmationService(HrOnboardingMapper onboardingMapper,
            HrOnboardingOperationLogMapper logMapper, HrOnboardingAccessService accessService,
            HrOnboardingRuleService rules, IHrOnboardingPositionConfigService configService,
            HrOnboardingConflictService conflictService, HrEmployeeNoGenerator employeeNoGenerator,
            SysUserMapper userMapper, SysUserProfileMapper profileMapper, SysUserRoleMapper roleMapper,
            SysUserPostMapper postMapper, SysUserShopMapper shopMapper, ISysUserShopService userShopService,
            ISysUserService userService, SysRoleMapper systemRoleMapper, ISysRoleService roleService,
            UserCredentialProvisioningService credentialProvisioningService)
    {
        this(onboardingMapper, logMapper, accessService, rules, configService, conflictService,
                employeeNoGenerator, userMapper, profileMapper, roleMapper, postMapper, shopMapper,
                userShopService, userService, systemRoleMapper, roleService,
                Clock.systemDefaultZone(), new TemporaryCredentialPolicy(), SecurityUtils::getUserId,
                SecurityUtils::isAdmin,
                credentialProvisioningService::generateTemporaryPassword, SecurityUtils::encryptPassword);
    }

    HrOnboardingConfirmationService(HrOnboardingMapper onboardingMapper,
            HrOnboardingOperationLogMapper logMapper, HrOnboardingAccessService accessService,
            HrOnboardingRuleService rules, IHrOnboardingPositionConfigService configService,
            HrOnboardingConflictService conflictService, HrEmployeeNoGenerator employeeNoGenerator,
            SysUserMapper userMapper, SysUserProfileMapper profileMapper, SysUserRoleMapper roleMapper,
            SysUserPostMapper postMapper, SysUserShopMapper shopMapper, ISysUserShopService userShopService,
            ISysUserService userService, SysRoleMapper systemRoleMapper, ISysRoleService roleService,
            Clock clock, LongSupplier currentUserId, BooleanSupplier currentUserAdmin,
            Supplier<String> passwordGenerator, Function<String, String> passwordEncoder)
    {
        this(onboardingMapper, logMapper, accessService, rules, configService, conflictService,
                employeeNoGenerator, userMapper, profileMapper, roleMapper, postMapper, shopMapper,
                userShopService, userService, systemRoleMapper, roleService, clock,
                new TemporaryCredentialPolicy(), currentUserId, currentUserAdmin, passwordGenerator,
                passwordEncoder);
    }

    HrOnboardingConfirmationService(HrOnboardingMapper onboardingMapper,
            HrOnboardingOperationLogMapper logMapper, HrOnboardingAccessService accessService,
            HrOnboardingRuleService rules, IHrOnboardingPositionConfigService configService,
            HrOnboardingConflictService conflictService, HrEmployeeNoGenerator employeeNoGenerator,
            SysUserMapper userMapper, SysUserProfileMapper profileMapper, SysUserRoleMapper roleMapper,
            SysUserPostMapper postMapper, SysUserShopMapper shopMapper, ISysUserShopService userShopService,
            ISysUserService userService, SysRoleMapper systemRoleMapper, ISysRoleService roleService,
            Clock clock, TemporaryCredentialPolicy temporaryCredentialPolicy, LongSupplier currentUserId,
            BooleanSupplier currentUserAdmin, Supplier<String> passwordGenerator,
            Function<String, String> passwordEncoder)
    {
        this.onboardingMapper = onboardingMapper;
        this.logMapper = logMapper;
        this.accessService = accessService;
        this.rules = rules;
        this.configService = configService;
        this.conflictService = conflictService;
        this.employeeNoGenerator = employeeNoGenerator;
        this.userMapper = userMapper;
        this.profileMapper = profileMapper;
        this.roleMapper = roleMapper;
        this.postMapper = postMapper;
        this.shopMapper = shopMapper;
        this.userShopService = userShopService;
        this.userService = userService;
        this.systemRoleMapper = systemRoleMapper;
        this.roleService = roleService;
        this.clock = clock;
        this.temporaryCredentialPolicy = temporaryCredentialPolicy == null
                ? new TemporaryCredentialPolicy() : temporaryCredentialPolicy;
        this.currentUserId = currentUserId;
        this.currentUserAdmin = currentUserAdmin;
        this.passwordGenerator = passwordGenerator;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(rollbackFor = Exception.class)
    public HrOnboardingConfirmResult confirm(Long onboardingId, HrOnboardingConfirmRequest request, String operator)
    {
        validateRequest(request);
        HrOnboarding scopedSnapshot = accessService.findScoped(query(onboardingId));
        List<HrOnboarding> lockedIdentitySet = accessService.lockGlobalOpenIdentitySetForUpdate(
                scopedSnapshot.getPhoneNumber(), scopedSnapshot.getIdNumber(),
                scopedSnapshot.getEmployeeNo());
        HrOnboarding row = accessService.lockScopedForUpdate(query(onboardingId));
        if (HrOnboarding.STATUS_CONFIRMED.equals(row.getStatus())) return replay(row, request.getIdempotencyKey());
        requireVersion(row, request.getVersion());
        if (!HrOnboarding.STATUS_READY.equals(row.getStatus()))
            throw failure("ONBOARDING_STATUS_NOT_READY", "仅待确认状态允许确认入职");
        accessService.validateTargets(row);

        row.setActualEntryDate(copy(request.getActualEntryDate()));
        HrOnboardingPositionConfig config = configService.resolveActive(
                row.getTargetPostId(), row.getEmployeeCategory());
        applyDefaults(row, config);
        HrOnboardingCompletionVo completion = rules.evaluateConfirm(row, config);
        if (completion != null && (!completion.getMissingFields().isEmpty()
                || !completion.getBlockingCodes().isEmpty()))
        {
            throw new HrOnboardingValidationException("ONBOARDING_CONFIRM_INCOMPLETE", "入职资料未满足确认条件",
                    Collections.emptyMap(), completion.getBlockingCodes());
        }

        if (lockedIdentitySet != null && lockedIdentitySet.stream().anyMatch(candidate -> candidate != null
                && !row.getOnboardingId().equals(candidate.getOnboardingId())))
            throw failure("ONBOARDING_OPEN_CONFLICT", "存在相同身份信息的未完成入职单");
        List<Long> configuredRoleIds = validateConfiguredRoles(config);

        List<HrOnboardingConflictVo> conflicts = conflictService.findConflicts(row);
        validateConflictDecision(request, conflicts);
        boolean configured = config != null && "0".equals(config.getStatus());
        List<String> risks = completion == null || completion.getRiskCodes() == null
                ? new ArrayList<>() : new ArrayList<>(completion.getRiskCodes());
        if (!configured && !risks.contains(CONFIGURATION_RISK)) risks.add(CONFIGURATION_RISK);
        boolean accountEnabled = configured && Boolean.TRUE.equals(config.getAccountEnabled());

        AccountResolution account;
        if (BIND_EXISTING.equals(request.getConflictAction()))
            account = bindExisting(row, request.getBindUserId(), config, configuredRoleIds,
                    accountEnabled, operator);
        else if (REHIRE_EXISTING.equals(request.getConflictAction()))
            account = rehireExisting(row, request.getBindUserId(), config, configuredRoleIds,
                    accountEnabled, operator);
        else
            account = createNew(row, config, configuredRoleIds, accountEnabled, operator);

        row.setEmployeeNo(account.employeeNo);
        row.setLinkedUserId(account.userId);
        row.setConfirmIdempotencyKey(request.getIdempotencyKey().trim());
        row.setAccountConfigurationStatus(configured ? "CONFIGURED" : "MISSING");
        row.setAccountRiskCode(configured ? null : CONFIGURATION_RISK);
        row.setConfirmedByUserId(currentUserId.getAsLong());
        row.setConfirmedBy(operator);
        row.setConfirmedTime(new Date(clock.millis()));
        row.setUpdateBy(operator);
        if (onboardingMapper.confirmOnboardingByVersion(row) != 1) throw versionConflict();
        logConfirmation(row, request.getConflictAction(), operator);
        return result(row, account.userId, account.employeeNo, accountEnabled,
                account.oneTimePassword, account.oneTimePasswordExpiresAt, false, risks);
    }

    private AccountResolution createNew(HrOnboarding row, HrOnboardingPositionConfig config,
            List<Long> configuredRoleIds, boolean accountEnabled, String operator)
    {
        applyDerivedPersonalDefaults(row);
        String loginAccount = phoneLoginAccount(row);
        String employeeNo = employeeNoGenerator.generate(row.getOnboardingId(), row.getActualEntryDate());
        String rawPassword = passwordGenerator.get();
        if (rawPassword == null || rawPassword.length() != 16)
            throw failure("ACCOUNT_PASSWORD_GENERATION_FAILED", "初始密码生成失败");
        Date expiresAt = temporaryCredentialPolicy.expiresAt(clock);
        SysUser user = baseUser(row, accountEnabled, operator);
        user.setUserName(loginAccount);
        user.setPhonenumber(loginAccount);
        user.setPassword(passwordEncoder.apply(rawPassword));
        user.setPwdUpdateDate(null);
        user.setCredentialState(SysUser.CREDENTIAL_STATE_TEMPORARY);
        user.setTemporaryPasswordExpiresAt(expiresAt);
        if (userMapper.insertUser(user) != 1) throw failure("ACCOUNT_CREATE_FAILED", "账号创建失败");
        SysUserProfile profile = profile(row, user.getUserId(), employeeNo, config, operator);
        if (profileMapper.insertUserProfile(profile) != 1) throw failure("PROFILE_CREATE_FAILED", "员工档案创建失败");
        mergeConfiguredRelations(user.getUserId(), row, config, configuredRoleIds, false, operator);
        return new AccountResolution(user.getUserId(), employeeNo, rawPassword, expiresAt);
    }

    private String phoneLoginAccount(HrOnboarding row)
    {
        String phone = trimToNull(row.getPhoneNumber());
        if (phone == null || !MAINLAND_MOBILE_PATTERN.matcher(phone).matches())
            throw failure("ACCOUNT_LOGIN_PHONE_INVALID", "手机号格式不正确，无法创建登录账号");
        if (userMapper.checkUserNameUnique(phone) != null || userMapper.checkPhoneUnique(phone) != null)
            throw failure("ACCOUNT_LOGIN_PHONE_CONFLICT", "手机号已被现有账号占用，请绑定已有账号或更换手机号");
        return phone;
    }

    private AccountResolution bindExisting(HrOnboarding row, Long userId, HrOnboardingPositionConfig config,
            List<Long> configuredRoleIds, boolean accountEnabled, String operator)
    {
        SysUser existing;
        try
        {
            existing = accessService.lockScopedBindCandidateForUpdate(query(row.getOnboardingId()), userId,
                    row.getPhoneNumber(), row.getIdNumber(), row.getEmployeeNo());
        }
        catch (ServiceException denied)
        {
            throw failure("ONBOARDING_BIND_NOT_ELIGIBLE", "绑定账号不存在或不可用");
        }
        try
        {
            userService.checkUserAllowed(existing);
        }
        catch (ServiceException protectedUser)
        {
            throw failure("ONBOARDING_BIND_PROTECTED", "不允许绑定受保护的管理员账号");
        }
        if (!eligibleLockedCandidate(row, userId, existing))
            throw failure("ONBOARDING_BIND_NOT_ELIGIBLE", "绑定账号状态或身份信息已变化");
        SysUserProfile existingProfile = existing.getProfile();
        if (existingProfile == null) existingProfile = profileMapper.selectUserProfileByUserId(userId);
        if (existingProfile == null || existingProfile.getBirthDate() == null) applyDerivedPersonalDefaults(row);
        String employeeNo = existingProfile == null ? null : trimToNull(existingProfile.getEmployeeNo());
        if (employeeNo == null) employeeNo = employeeNoGenerator.generate(row.getOnboardingId(), row.getActualEntryDate());
        else employeeNoGenerator.validateOwnedBy(employeeNo, userId);

        SysUser update = baseUser(row, accountEnabled, operator);
        update.setUserId(userId);
        SysUserProfile formal = profile(existingProfile, row, userId, employeeNo, config, operator);
        update.setProfile(formal);
        if (userService.updateHrEmployeeProfile(update) != 1)
            throw failure("ACCOUNT_UPDATE_FAILED", "账号和员工档案更新失败");
        mergeConfiguredRelations(userId, row, config, configuredRoleIds, true, operator);
        return new AccountResolution(userId, employeeNo, null, null);
    }

    private AccountResolution rehireExisting(HrOnboarding row, Long userId,
            HrOnboardingPositionConfig config, List<Long> configuredRoleIds,
            boolean accountEnabled, String operator)
    {
        SysUser existing;
        try
        {
            existing = accessService.lockScopedBindCandidateForUpdate(query(row.getOnboardingId()), userId,
                    row.getPhoneNumber(), row.getIdNumber(), row.getEmployeeNo());
        }
        catch (ServiceException denied)
        {
            throw failure("ONBOARDING_REHIRE_NOT_ELIGIBLE", "原员工账号不存在或不在当前数据范围");
        }
        try
        {
            userService.checkUserAllowed(existing);
        }
        catch (ServiceException protectedUser)
        {
            throw failure("ONBOARDING_REHIRE_PROTECTED", "不允许恢复受保护的管理员账号");
        }
        if (!eligibleLockedRehireCandidate(row, userId, existing))
            throw failure("ONBOARDING_REHIRE_NOT_ELIGIBLE", "原员工账号状态或身份信息已变化");

        SysUserProfile existingProfile = existing.getProfile();
        if (existingProfile == null) existingProfile = profileMapper.selectUserProfileByUserId(userId);
        if (existingProfile == null)
            throw failure("ONBOARDING_REHIRE_PROFILE_REQUIRED", "原员工档案不存在，不能按再入职恢复");
        String employeeNo = trimToNull(existingProfile.getEmployeeNo());
        if (employeeNo == null)
            throw failure("ONBOARDING_REHIRE_EMPLOYEE_NO_REQUIRED", "原员工号缺失，请先修复员工档案");
        employeeNoGenerator.validateOwnedBy(employeeNo, userId);

        SysUser update = baseUser(row, accountEnabled, operator);
        update.setUserId(userId);
        SysUserProfile formal = profile(existingProfile, row, userId, employeeNo, config, operator);
        // The profile is the current employment state. Historical departures remain
        // in lifecycle/audit records, while the new stint starts with clean lifecycle dates.
        // The next signing event writes the new contract end date after rehire.
        formal.setLeaveDate(null);
        formal.setActualRegularizationDate(null);
        formal.setContractEndDate(null);
        update.setProfile(formal);
        if (userService.updateHrEmployeeProfile(update) != 1)
            throw failure("ACCOUNT_UPDATE_FAILED", "原账号和员工档案恢复失败");
        replaceRehireRelations(userId, row, config, configuredRoleIds, operator);
        return new AccountResolution(userId, employeeNo, null, null);
    }

    private List<Long> validateConfiguredRoles(HrOnboardingPositionConfig config)
    {
        if (config == null || !"0".equals(config.getStatus()) || config.getRoleIds() == null)
            return Collections.emptyList();
        Set<Long> ordered = new TreeSet<>();
        for (Long roleId : config.getRoleIds()) if (roleId != null) ordered.add(roleId);
        for (Long roleId : ordered)
        {
            SysRole role = systemRoleMapper.selectRoleByIdForUpdate(roleId);
            if (role == null) throw failure("ONBOARDING_ROLE_INACTIVE", "配置角色不存在或已停用");
            try
            {
                roleService.checkRoleAllowed(role);
                roleService.checkRoleDataScope(roleId);
            }
            catch (ServiceException denied)
            {
                throw failure("ONBOARDING_ROLE_NOT_ALLOWED", "配置角色受保护或超出当前数据权限");
            }
            if (!"0".equals(role.getStatus()) || !"0".equals(role.getDelFlag()))
                throw failure("ONBOARDING_ROLE_INACTIVE", "配置角色不存在或已停用");
        }
        return new ArrayList<>(ordered);
    }

    private boolean eligibleLockedCandidate(HrOnboarding source, Long selectedUserId, SysUser candidate)
    {
        if (candidate == null || !selectedUserId.equals(candidate.getUserId())
                || !"0".equals(candidate.getStatus()) || "2".equals(candidate.getDelFlag())) return false;
        SysUserProfile profile = candidate.getProfile();
        if (profile != null && "离职".equals(profile.getEmployeeStatus())) return false;
        return sameIdentity(source.getPhoneNumber(), candidate.getPhonenumber())
                || sameIdentity(source.getIdNumber(), profile == null ? null : profile.getIdNumber())
                || sameIdentity(source.getEmployeeNo(), profile == null ? null : profile.getEmployeeNo());
    }

    private boolean eligibleLockedRehireCandidate(HrOnboarding source, Long selectedUserId,
            SysUser candidate)
    {
        if (candidate == null || !selectedUserId.equals(candidate.getUserId())
                || !"1".equals(candidate.getStatus()) || "2".equals(candidate.getDelFlag())) return false;
        SysUserProfile profile = candidate.getProfile();
        if (profile == null || !"离职".equals(profile.getEmployeeStatus())) return false;
        if (!sameTrimmed(source.getEmployeeName(), candidate.getNickName())) return false;
        if (trimToNull(source.getIdNumber()) != null)
            return sameTrimmed(source.getIdNumber(), profile.getIdNumber());
        if (trimToNull(source.getEmployeeNo()) != null)
            return sameTrimmed(source.getEmployeeNo(), profile.getEmployeeNo());
        return sameTrimmed(source.getPhoneNumber(), candidate.getPhonenumber());
    }

    private void replaceRehireRelations(Long userId, HrOnboarding row,
            HrOnboardingPositionConfig config, List<Long> configuredRoleIds, String operator)
    {
        // A disabled former account may still carry stale role/shop rows. Rehire uses
        // only the currently approved position configuration before the account can
        // become active.
        roleMapper.deleteUserRoleByUserId(userId);
        postMapper.deleteUserPostByUserId(userId);
        shopMapper.deleteUserShopByUserId(userId);
        if (config == null || !"0".equals(config.getStatus())) return;
        for (Long roleId : configuredRoleIds)
            if (roleMapper.insertUserRoleIfAbsent(userId, roleId) != 1)
                throw failure("ONBOARDING_ROLE_ASSIGNMENT_FAILED", "再入职账号角色配置失败");
        if (row.getTargetPostId() != null
                && postMapper.insertUserPostIfAbsent(userId, row.getTargetPostId()) != 1)
            throw failure("ONBOARDING_POST_ASSIGNMENT_FAILED", "再入职账号岗位配置失败");
        Long[] shops = configuredShopIds(row, config);
        for (Long shopId : shops)
        {
            userShopService.checkUserShopScope(currentUserId.getAsLong(), shopId,
                    currentUserAdmin.getAsBoolean());
            if (shopMapper.insertUserShopIfAbsent(userId, shopId, operator) != 1)
                throw failure("ONBOARDING_SHOP_ASSIGNMENT_FAILED", "再入职账号门店权限配置失败");
        }
    }

    private boolean sameIdentity(String source, String candidate)
    {
        return source != null && !source.trim().isEmpty() && source.equals(candidate);
    }

    private boolean sameTrimmed(String source, String candidate)
    {
        return trimToNull(source) != null && trimToNull(candidate) != null
                && source.trim().equals(candidate.trim());
    }

    private void mergeConfiguredRelations(Long userId, HrOnboarding row, HrOnboardingPositionConfig config,
            List<Long> configuredRoleIds, boolean existingAccount, String operator)
    {
        if (config == null || !"0".equals(config.getStatus())) return;
        Set<Long> existingRoles = existingAccount ? safeSet(roleMapper.selectRoleIdsByUserId(userId))
                : Collections.emptySet();
        for (Long roleId : configuredRoleIds)
        {
            if (!existingRoles.contains(roleId) && roleMapper.insertUserRoleIfAbsent(userId, roleId) != 1)
                throw failure("ONBOARDING_ROLE_ASSIGNMENT_FAILED", "账号角色配置失败");
        }

        Set<Long> existingPosts = existingAccount ? safeSet(postMapper.selectPostIdsByUserId(userId))
                : Collections.emptySet();
        Long postId = row.getTargetPostId();
        if (postId != null)
        {
            if (existingAccount && (existingPosts.size() != 1 || !existingPosts.contains(postId)))
            {
                postMapper.deleteUserPostByUserId(userId);
                postMapper.insertUserPostIfAbsent(userId, postId);
            }
            else if (!existingAccount)
            {
                postMapper.insertUserPostIfAbsent(userId, postId);
            }
        }

        Long[] shops = configuredShopIds(row, config);
        if (!existingAccount)
        {
            userShopService.saveUserShops(userId, shops, operator, currentUserId.getAsLong(),
                    currentUserAdmin.getAsBoolean());
            return;
        }
        Set<Long> existingShops = safeSet(shopMapper.selectAllShopDeptIdsByUserId(userId));
        for (Long shopId : shops)
        {
            if (!existingShops.contains(shopId))
            {
                userShopService.checkUserShopScope(currentUserId.getAsLong(), shopId,
                        currentUserAdmin.getAsBoolean());
                shopMapper.insertUserShopIfAbsent(userId, shopId, operator);
            }
        }
    }

    private Long[] configuredShopIds(HrOnboarding row, HrOnboardingPositionConfig config)
    {
        Long value = null;
        if ("TARGET_STORE".equals(config.getDataScopeStrategy())) value = row.getTargetStoreId();
        else if ("TARGET_DEPT".equals(config.getDataScopeStrategy())) value = row.getTargetDeptId();
        return value == null ? new Long[0] : new Long[] { value };
    }

    private void applyDefaults(HrOnboarding row, HrOnboardingPositionConfig config)
    {
        if (config == null || !"0".equals(config.getStatus())) return;
        if ("NOT_APPLICABLE".equals(config.getContractTypeMode())) row.setContractType(null);
        else if (trimToNull(row.getContractType()) == null) row.setContractType(config.getDefaultContractType());
        if ("NOT_APPLICABLE".equals(config.getSocialTypeMode())) row.setSocialType(null);
        else if (trimToNull(row.getSocialType()) == null) row.setSocialType(config.getDefaultSocialType());
        if ("NOT_APPLICABLE".equals(config.getProbationPeriodMode())) row.setProbationPeriod(null);
        else if (trimToNull(row.getProbationPeriod()) == null) row.setProbationPeriod(config.getDefaultProbationPeriod());
        if (trimToNull(row.getJobGrade()) == null) row.setJobGrade(config.getJobGrade());
    }

    private void applyDerivedPersonalDefaults(HrOnboarding row)
    {
        if (row == null || row.getBirthDate() != null) return;
        String idType = trimToNull(row.getIdType());
        String idNumber = trimToNull(row.getIdNumber());
        if (idType == null || !idType.contains("身份证") || !isValidMainlandIdCard(idNumber)) return;
        try
        {
            LocalDate birthDate = LocalDate.parse(idNumber.substring(6, 14), DateTimeFormatter.BASIC_ISO_DATE);
            if (birthDate.isAfter(LocalDate.now(clock))) return;
            row.setBirthDate(Date.from(birthDate.atStartOfDay(clock.getZone()).toInstant()));
        }
        catch (DateTimeParseException ignored)
        {
            // A malformed or impossible date remains an optional empty value.
        }
    }

    private boolean isValidMainlandIdCard(String idNumber)
    {
        if (idNumber == null || !MAINLAND_ID_CARD_PATTERN.matcher(idNumber).matches()) return false;
        String normalized = idNumber.toUpperCase();
        try
        {
            LocalDate.parse(normalized.substring(6, 14), DateTimeFormatter.BASIC_ISO_DATE);
        }
        catch (DateTimeParseException invalidDate)
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

    private SysUser baseUser(HrOnboarding row, boolean accountEnabled, String operator)
    {
        SysUser user = new SysUser();
        user.setDeptId(row.getTargetDeptId());
        employeeFieldRegistry.copyDeclaredOnboardingMappings(row, user,
                HrEmployeeFieldRegistry.StorageOwner.SYS_USER);
        user.setStatus(accountEnabled ? "0" : "1");
        user.setCreateBy(operator);
        user.setUpdateBy(operator);
        return user;
    }

    private SysUserProfile profile(HrOnboarding row, Long userId, String employeeNo,
            HrOnboardingPositionConfig config, String operator)
    {
        return profile(null, row, userId, employeeNo, config, operator);
    }

    private SysUserProfile profile(SysUserProfile existing, HrOnboarding row, Long userId,
            String employeeNo, HrOnboardingPositionConfig config, String operator)
    {
        Date existingBirthDate = existing == null ? null : copy(existing.getBirthDate());
        String existingMaritalStatus = existing == null ? null : existing.getMaritalStatus();
        String existingEthnicity = existing == null ? null : existing.getEthnicity();
        String existingEmergencyContact = existing == null ? null : existing.getEmergencyContact();
        String existingEmergencyRelation = existing == null ? null : existing.getEmergencyContactRelation();
        String existingEmergencyPhone = existing == null ? null : existing.getEmergencyContactPhone();
        String existingWorkCityLevel = existing == null ? null : existing.getWorkCityLevel();
        String existingLegalEntity = existing == null ? null : existing.getLegalEntity();
        SysUserProfile profile = existing == null ? new SysUserProfile() : existing;
        SysUser aggregate = new SysUser();
        aggregate.setUserId(userId);
        aggregate.setProfile(profile);
        employeeFieldRegistry.copyDeclaredOnboardingMappings(row, aggregate,
                HrEmployeeFieldRegistry.StorageOwner.SYS_USER_PROFILE);
        employeeFieldRegistry.copyDeclaredOnboardingMappings(row, aggregate,
                HrEmployeeFieldRegistry.StorageOwner.RELATION);
        if (existing != null)
        {
            if (row.getBirthDate() == null) profile.setBirthDate(existingBirthDate);
            if (trimToNull(row.getMaritalStatus()) == null) profile.setMaritalStatus(existingMaritalStatus);
            if (trimToNull(row.getEthnicity()) == null) profile.setEthnicity(existingEthnicity);
            if (trimToNull(row.getEmergencyContact()) == null) profile.setEmergencyContact(existingEmergencyContact);
            if (trimToNull(row.getEmergencyContactRelation()) == null)
                profile.setEmergencyContactRelation(existingEmergencyRelation);
            if (trimToNull(row.getEmergencyContactPhone()) == null)
                profile.setEmergencyContactPhone(existingEmergencyPhone);
            if (trimToNull(row.getWorkCityLevel()) == null) profile.setWorkCityLevel(existingWorkCityLevel);
            if (trimToNull(row.getLegalEntity()) == null) profile.setLegalEntity(existingLegalEntity);
        }
        profile.setUserId(userId);
        profile.setEmployeeNo(employeeNo);
        profile.setPositionNo(positionNo(employeeNo, row.getTargetPostId()));
        applyProbation(profile, row, config);
        if (existing == null) profile.setCreateBy(operator);
        profile.setUpdateBy(operator);
        return profile;
    }

    private String positionNo(String employeeNo, Long postId)
    {
        String postCode = postId == null ? null : profileMapper.selectActivePostCodeById(postId);
        if (postCode == null)
        {
            throw failure("ONBOARDING_POST_INACTIVE", "目标岗位不存在或已停用");
        }
        try
        {
            return HrPositionNoFormatter.format(employeeNo, postCode);
        }
        catch (ServiceException invalid)
        {
            throw failure("POSITION_NO_INVALID", invalid.getMessage());
        }
    }

    private void applyProbation(SysUserProfile profile, HrOnboarding row, HrOnboardingPositionConfig config)
    {
        String value = trimToNull(row.getProbationPeriod());
        boolean applicable = config != null && !"NOT_APPLICABLE".equals(config.getProbationPeriodMode())
                && value != null && !"0".equals(value) && !"无".equals(value);
        if (!applicable)
        {
            profile.setEmployeeStatus("正式");
            profile.setProbationPeriod(null);
            profile.setPlannedRegularizationDate(null);
            return;
        }
        profile.setEmployeeStatus("试用");
        profile.setProbationPeriod(value);
        LocalDate entry = Instant.ofEpochMilli(row.getActualEntryDate().getTime())
                .atZone(clock.getZone()).toLocalDate();
        LocalDate planned = value.toUpperCase().contains("DAY") || value.contains("天")
                ? entry.plusDays(periodNumber(value)) : entry.plusMonths(periodNumber(value));
        profile.setPlannedRegularizationDate(Date.from(planned.atStartOfDay(clock.getZone()).toInstant()));
    }

    private int periodNumber(String value)
    {
        Matcher matcher = NUMBER_PATTERN.matcher(value);
        if (matcher.find()) return Math.max(1, Integer.parseInt(matcher.group(1)));
        if (value.contains("一")) return 1;
        if (value.contains("二")) return 2;
        if (value.contains("三")) return 3;
        if (value.contains("六")) return 6;
        throw failure("PROBATION_PERIOD_INVALID", "试用期无法换算计划转正日期");
    }

    private HrOnboardingConfirmResult replay(HrOnboarding row, String idempotencyKey)
    {
        if (idempotencyKey == null || !idempotencyKey.trim().equals(row.getConfirmIdempotencyKey()))
            throw failure("ONBOARDING_ALREADY_CONFIRMED", "该入职单已确认");
        SysUser linked = row.getLinkedUserId() == null ? null : userMapper.selectUserById(row.getLinkedUserId());
        boolean enabled = linked != null && "0".equals(linked.getStatus());
        List<String> risks = CONFIGURATION_RISK.equals(row.getAccountRiskCode())
                ? Collections.singletonList(CONFIGURATION_RISK) : Collections.emptyList();
        return result(row, row.getLinkedUserId(), row.getEmployeeNo(), enabled, null, null, true, risks);
    }

    private void validateConflictDecision(HrOnboardingConfirmRequest request,
            List<HrOnboardingConflictVo> conflicts)
    {
        if (CREATE_NEW.equals(request.getConflictAction()))
        {
            if (conflicts.stream().anyMatch(item -> Boolean.TRUE.equals(item.getBlocking())))
                throw failure("ONBOARDING_CONFLICT_BLOCKING", "存在阻断性员工或入职冲突");
            return;
        }
        boolean eligible = conflicts.stream().anyMatch(item -> request.getBindUserId().equals(item.getCandidateUserId())
                && (BIND_EXISTING.equals(request.getConflictAction())
                    && Boolean.TRUE.equals(item.getEligibleForBind())
                    && item.getAllowedDecisions().contains(BIND_EXISTING)
                    || REHIRE_EXISTING.equals(request.getConflictAction())
                    && Boolean.TRUE.equals(item.getEligibleForRehire())
                    && item.getAllowedDecisions().contains(REHIRE_EXISTING)));
        if (!eligible)
            throw failure(REHIRE_EXISTING.equals(request.getConflictAction())
                    ? "ONBOARDING_REHIRE_NOT_ELIGIBLE" : "ONBOARDING_BIND_NOT_ELIGIBLE",
                    REHIRE_EXISTING.equals(request.getConflictAction())
                    ? "原账号不在当前数据范围的可恢复候选中"
                    : "绑定账号不在当前数据范围的可绑定候选中");
    }

    private void validateRequest(HrOnboardingConfirmRequest request)
    {
        if (request == null) throw failure("ONBOARDING_CONFIRM_REQUEST_REQUIRED", "确认参数不能为空");
        if (request.getVersion() == null) throw failure("ONBOARDING_VERSION_REQUIRED", "版本号不能为空");
        if (request.getActualEntryDate() == null)
            throw failure("ACTUAL_ENTRY_DATE_REQUIRED", "实际入职日期不能为空");
        String key = trimToNull(request.getIdempotencyKey());
        if (key == null || key.length() > 64)
            throw failure("IDEMPOTENCY_KEY_INVALID", "幂等键不能为空且不能超过64个字符");
        if (!CREATE_NEW.equals(request.getConflictAction()) && !BIND_EXISTING.equals(request.getConflictAction())
                && !REHIRE_EXISTING.equals(request.getConflictAction()))
            throw failure("CONFLICT_ACTION_INVALID", "冲突处理决定无效");
        if ((BIND_EXISTING.equals(request.getConflictAction())
                || REHIRE_EXISTING.equals(request.getConflictAction())) && request.getBindUserId() == null)
            throw failure("BIND_USER_REQUIRED", "绑定账号不能为空");
        if (CREATE_NEW.equals(request.getConflictAction()) && request.getBindUserId() != null)
            throw failure("BIND_USER_NOT_ALLOWED", "新建账号不能携带绑定账号");
    }

    private void requireVersion(HrOnboarding row, Integer version)
    {
        if (row.getVersion() == null || !row.getVersion().equals(version)) throw versionConflict();
    }

    private HrOnboardingConfirmResult result(HrOnboarding row, Long userId, String employeeNo,
            boolean enabled, String oneTimePassword, Date oneTimePasswordExpiresAt, boolean replayed,
            List<String> risks)
    {
        HrOnboardingConfirmResult result = new HrOnboardingConfirmResult();
        result.setOnboardingId(row.getOnboardingId());
        result.setUserId(userId);
        result.setEmployeeNo(employeeNo);
        result.setAccountStatus(enabled ? "ENABLED" : "DISABLED");
        result.setOneTimePassword(oneTimePassword);
        result.setOneTimePasswordExpiresAt(oneTimePasswordExpiresAt);
        result.setReplayed(replayed);
        result.setRiskCodes(new ArrayList<>(risks));
        return result;
    }

    private void logConfirmation(HrOnboarding row, String action, String operator)
    {
        HrOnboardingOperationLog log = new HrOnboardingOperationLog();
        log.setOnboardingId(row.getOnboardingId());
        log.setOperationType("CONFIRM");
        log.setFromStatus(HrOnboarding.STATUS_READY);
        log.setToStatus(HrOnboarding.STATUS_CONFIRMED);
        log.setOperatorUserId(currentUserId.getAsLong());
        log.setOperatorName(operator);
        log.setChangedFieldKeys("status,actualEntryDate,linkedUserId,employeeNo,accountConfigurationStatus");
        log.setDecisionSummary("conflictAction=" + action);
        log.setOperationSummary("确认入职并创建或绑定正式员工档案");
        log.setOperationTime(new Date(clock.millis()));
        log.setCreateBy(operator);
        logMapper.insertOperationLog(log);
    }

    private Set<Long> safeSet(List<Long> values)
    {
        return values == null ? Collections.emptySet() : new HashSet<>(values);
    }

    private HrOnboardingQuery query(Long onboardingId)
    {
        HrOnboardingQuery query = new HrOnboardingQuery();
        query.setOnboardingId(onboardingId);
        return query;
    }

    private Date copy(Date value) { return value == null ? null : new Date(value.getTime()); }
    private String trimToNull(String value)
    {
        if (value == null || value.trim().isEmpty()) return null;
        return value.trim();
    }

    private HrOnboardingValidationException versionConflict()
    {
        return failure("ONBOARDING_VERSION_CONFLICT", "入职单已被其他操作更新，请刷新后重试");
    }

    private HrOnboardingValidationException failure(String code, String message)
    {
        return new HrOnboardingValidationException(code, message, Collections.emptyMap(),
                Collections.singletonList(code));
    }

    private static class AccountResolution
    {
        private final Long userId;
        private final String employeeNo;
        private final String oneTimePassword;
        private final Date oneTimePasswordExpiresAt;
        private AccountResolution(Long userId, String employeeNo, String oneTimePassword,
                Date oneTimePasswordExpiresAt)
        {
            this.userId = userId;
            this.employeeNo = employeeNo;
            this.oneTimePassword = oneTimePassword;
            this.oneTimePasswordExpiresAt = oneTimePasswordExpiresAt == null
                    ? null : new Date(oneTimePasswordExpiresAt.getTime());
        }
    }
}
