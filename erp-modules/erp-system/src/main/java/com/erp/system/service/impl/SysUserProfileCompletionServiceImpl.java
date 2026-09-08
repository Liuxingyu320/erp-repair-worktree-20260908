package com.erp.system.service.impl;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.erp.common.core.constant.UserConstants;
import com.erp.common.core.exception.ServiceException;
import com.erp.system.api.domain.SysDept;
import com.erp.system.api.domain.SysUser;
import com.erp.system.api.domain.SysUserProfile;
import com.erp.system.config.ProfileCompletionProperties;
import com.erp.system.domain.vo.SysProfileCompletionFieldVo;
import com.erp.system.domain.vo.SysProfileCompletionRequest;
import com.erp.system.domain.vo.SysProfileCompletionVo;
import com.erp.system.mapper.SysUserMapper;
import com.erp.system.mapper.SysUserProfileMapper;
import com.erp.system.service.ISysUserProfileCompletionService;
import com.erp.system.service.SigningProfileNormalizer;

/**
 * 登录员工资料完整度服务实现。
 */
@Service
public class SysUserProfileCompletionServiceImpl implements ISysUserProfileCompletionService
{
    private static final Pattern MAINLAND_MOBILE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");
    private static final Pattern MAINLAND_ID_CARD_PATTERN = Pattern.compile(
            "^[1-9]\\d{5}(18|19|20)\\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])\\d{3}[0-9Xx]$");
    private static final int[] ID_CARD_CHECK_WEIGHTS = {7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2};
    private static final char[] ID_CARD_CHECK_CODES = {'1', '0', 'X', '9', '8', '7', '6', '5', '4', '3', '2'};
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final SysUserMapper userMapper;
    private final SysUserProfileMapper profileMapper;
    private final Clock clock;
    private final SigningProfileNormalizer signingProfileNormalizer;
    private final ProfileCompletionProperties profileCompletionProperties;

    @Autowired
    public SysUserProfileCompletionServiceImpl(SysUserMapper userMapper, SysUserProfileMapper profileMapper,
            SigningProfileNormalizer signingProfileNormalizer,
            ProfileCompletionProperties profileCompletionProperties)
    {
        this(userMapper, profileMapper, Clock.systemDefaultZone(), signingProfileNormalizer,
                profileCompletionProperties);
    }

    SysUserProfileCompletionServiceImpl(SysUserMapper userMapper, SysUserProfileMapper profileMapper, Clock clock)
    {
        this(userMapper, profileMapper, clock, new SigningProfileNormalizer(), new ProfileCompletionProperties());
    }

    SysUserProfileCompletionServiceImpl(SysUserMapper userMapper, SysUserProfileMapper profileMapper, Clock clock,
            SigningProfileNormalizer signingProfileNormalizer)
    {
        this(userMapper, profileMapper, clock, signingProfileNormalizer, new ProfileCompletionProperties());
    }

    SysUserProfileCompletionServiceImpl(SysUserMapper userMapper, SysUserProfileMapper profileMapper, Clock clock,
            SigningProfileNormalizer signingProfileNormalizer,
            ProfileCompletionProperties profileCompletionProperties)
    {
        this.userMapper = userMapper;
        this.profileMapper = profileMapper;
        this.clock = clock;
        this.signingProfileNormalizer = signingProfileNormalizer;
        this.profileCompletionProperties = profileCompletionProperties;
    }

    @Override
    public SysProfileCompletionVo evaluate(Long userId)
    {
        // 资料补全是登录后的强制业务门禁，不能因环境配置缺失或误配置而静默放行。
        // 仅保留系统超级管理员豁免。
        if (UserConstants.isAdmin(userId))
        {
            return completeResult();
        }
        SysUser user = userMapper.selectUserById(userId);
        SysUserProfile profile = profileMapper.selectUserProfileByUserId(userId);
        return evaluate(user == null ? new SysUser(userId) : user, profile);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SysProfileCompletionVo save(Long userId, SysProfileCompletionRequest request, String operator)
    {
        if (UserConstants.isAdmin(userId))
        {
            return completeResult();
        }
        SysUser currentUser = userMapper.selectUserById(userId);
        if (currentUser == null)
        {
            throw new ServiceException("当前账号不存在");
        }
        SysUserProfile profile = profileMapper.selectUserProfileByUserId(userId);
        boolean createProfile = profile == null;
        if (createProfile)
        {
            profile = new SysUserProfile();
            profile.setUserId(userId);
        }

        SysProfileCompletionRequest payload = request == null ? new SysProfileCompletionRequest() : request;
        merge(currentUser, profile, payload);
        signingProfileNormalizer.normalize(profile);
        validate(currentUser, profile, payload);
        validatePhoneUnique(userId, currentUser.getPhonenumber());

        String auditUser = trim(operator);
        SysUser updateUser = new SysUser(userId);
        updateUser.setNickName(currentUser.getNickName());
        updateUser.setPhonenumber(currentUser.getPhonenumber());
        updateUser.setSex(currentUser.getSex());
        updateUser.setUpdateBy(auditUser);
        if (userMapper.updateHrEmployeeProfileUser(updateUser) <= 0)
        {
            throw new ServiceException("保存用户基础资料失败");
        }

        profile.setUpdateBy(auditUser);
        if (createProfile)
        {
            profile.setCreateBy(auditUser);
            if (profileMapper.insertUserProfile(profile) <= 0)
            {
                throw new ServiceException("创建员工档案失败");
            }
        }
        else if (profileMapper.updateProfileCompletionFields(personalProfileUpdate(profile, auditUser)) <= 0)
        {
            throw new ServiceException("保存员工档案失败");
        }
        return evaluate(currentUser, profile);
    }

    private SysProfileCompletionVo evaluate(SysUser user, SysUserProfile profile)
    {
        SysProfileCompletionVo result = new SysProfileCompletionVo();
        addMissing(result, "nickName", "姓名", !isValidText(user.getNickName(), 30));
        addMissing(result, "phonenumber", "手机号", !isValidMainlandMobile(user.getPhonenumber()));
        addMissing(result, "sex", "性别", !isValidSex(user.getSex()));
        addMissing(result, "idType", "证件类型", profile == null || !isValidText(profile.getIdType(), 64));
        addMissing(result, "idNumber", "证件号码", profile == null || !isValidIdNumber(profile));
        addMissing(result, "registeredResidence", "户籍地址",
                profile == null || !isValidText(profile.getRegisteredResidence(), 255));
        addMissing(result, "currentAddress", "现居住地址",
                profile == null || !isValidText(profile.getCurrentAddress(), 255));
        result.setCompletionRequired(!result.getMissingFields().isEmpty());
        result.setValues(editableValues(user, profile));
        result.setCompletedDisplayValues(completedDisplayValues(profile));
        result.setReadonlySummary(readonlySummary(user, profile));
        return result;
    }

    private SysProfileCompletionVo completeResult()
    {
        SysProfileCompletionVo result = new SysProfileCompletionVo();
        result.setCompletionRequired(false);
        return result;
    }

    private void merge(SysUser user, SysUserProfile profile, SysProfileCompletionRequest request)
    {
        user.setNickName(mergeText(request.getNickName(), user.getNickName()));
        user.setPhonenumber(mergeText(request.getPhonenumber(), user.getPhonenumber()));
        user.setSex(mergeText(request.getSex(), user.getSex()));

        if (request.getBirthDate() != null)
        {
            profile.setBirthDate(request.getBirthDate());
        }
        profile.setIdType(mergeText(request.getIdType(), profile.getIdType()));
        profile.setIdNumber(mergeText(request.getIdNumber(), profile.getIdNumber()));
        profile.setRegisteredResidence(mergeText(request.getRegisteredResidence(), profile.getRegisteredResidence()));
        profile.setCurrentAddress(mergeText(request.getCurrentAddress(), profile.getCurrentAddress()));
        profile.setMaritalStatus(mergeText(request.getMaritalStatus(), profile.getMaritalStatus()));
        profile.setEthnicity(mergeText(request.getEthnicity(), profile.getEthnicity()));
    }

    private void validate(SysUser user, SysUserProfile profile, SysProfileCompletionRequest request)
    {
        require(user.getNickName(), "请填写姓名");
        if (user.getNickName().length() > 30)
        {
            throw new ServiceException("姓名不能超过30个字符");
        }
        require(user.getPhonenumber(), "请填写手机号");
        if (!MAINLAND_MOBILE_PATTERN.matcher(user.getPhonenumber()).matches())
        {
            throw new ServiceException("请输入正确的手机号");
        }
        if (!isValidSex(user.getSex()))
        {
            throw new ServiceException("请选择性别");
        }
        if (request.getBirthDate() != null && profile.getBirthDate() != null
                && toLocalDate(profile.getBirthDate()).isAfter(LocalDate.now(clock)))
        {
            throw new ServiceException("出生日期不能晚于今天");
        }
        requireWithMax(profile.getIdType(), 64, "请选择证件类型", "证件类型不能超过64个字符");
        require(profile.getIdNumber(), "请填写证件号码");
        if (isMainlandIdType(profile.getIdType()))
        {
            if (!isValidMainlandIdCard(profile.getIdNumber()))
            {
                throw new ServiceException("请输入正确的居民身份证号码");
            }
            profile.setIdNumber(profile.getIdNumber().toUpperCase());
        }
        else if (profile.getIdNumber().length() < 3 || profile.getIdNumber().length() > 64)
        {
            throw new ServiceException("证件号码长度应为3至64个字符");
        }
        requireWithMax(profile.getRegisteredResidence(), 255, "请填写户籍地址", "户籍地址不能超过255个字符");
        requireWithMax(profile.getCurrentAddress(), 255, "请填写现居住地址", "现居住地址不能超过255个字符");
        if (request.getMaritalStatus() != null)
            optionalWithMax(profile.getMaritalStatus(), 32, "婚姻状况不能超过32个字符");
        if (request.getEthnicity() != null)
            optionalWithMax(profile.getEthnicity(), 64, "民族不能超过64个字符");
    }

    private void validatePhoneUnique(Long userId, String phonenumber)
    {
        SysUser duplicate = userMapper.checkPhoneUnique(phonenumber);
        if (duplicate != null && !userId.equals(duplicate.getUserId()))
        {
            throw new ServiceException("手机号已被其他账号使用");
        }
    }

    private SysUserProfile personalProfileUpdate(SysUserProfile source, String auditUser)
    {
        SysUserProfile update = new SysUserProfile();
        update.setUserId(source.getUserId());
        update.setBirthDate(source.getBirthDate());
        update.setIdType(source.getIdType());
        update.setIdNumber(source.getIdNumber());
        update.setRegisteredResidence(source.getRegisteredResidence());
        update.setCurrentAddress(source.getCurrentAddress());
        update.setMaritalStatus(source.getMaritalStatus());
        update.setEthnicity(source.getEthnicity());
        update.setUpdateBy(auditUser);
        return update;
    }

    private boolean isValidMainlandIdCard(String idNumber)
    {
        if (!MAINLAND_ID_CARD_PATTERN.matcher(idNumber).matches())
        {
            return false;
        }
        String normalized = idNumber.toUpperCase();
        try
        {
            LocalDate.parse(normalized.substring(6, 14), DateTimeFormatter.BASIC_ISO_DATE);
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

    private SysProfileCompletionRequest editableValues(SysUser user, SysUserProfile profile)
    {
        SysProfileCompletionRequest values = new SysProfileCompletionRequest();
        values.setNickName(trim(user.getNickName()));
        values.setPhonenumber(trim(user.getPhonenumber()));
        values.setSex(isValidSex(user.getSex()) ? user.getSex() : null);
        if (profile != null)
        {
            values.setBirthDate(profile.getBirthDate());
            values.setIdType(trim(profile.getIdType()));
            values.setRegisteredResidence(trim(profile.getRegisteredResidence()));
            values.setCurrentAddress(trim(profile.getCurrentAddress()));
            values.setMaritalStatus(trim(profile.getMaritalStatus()));
            values.setEthnicity(trim(profile.getEthnicity()));
        }
        return values;
    }

    private Map<String, String> completedDisplayValues(SysUserProfile profile)
    {
        Map<String, String> values = new LinkedHashMap<>();
        if (profile == null)
        {
            return values;
        }
        if (isValidIdNumber(profile))
        {
            values.put("idNumber", mask(profile.getIdNumber(), 3, 4));
        }
        return values;
    }

    private Map<String, String> readonlySummary(SysUser user, SysUserProfile profile)
    {
        Map<String, String> summary = new LinkedHashMap<>();
        SysDept dept = user.getDept();
        summary.put("department", dept == null ? null : trim(dept.getDeptName()));
        summary.put("positionNames", trim(user.getPostNames()));
        if (profile != null)
        {
            summary.put("employeeNo", trim(profile.getEmployeeNo()));
            summary.put("companyName", trim(profile.getCompanyName()));
            summary.put("deptLevel1Name", trim(profile.getDeptLevel1Name()));
            summary.put("deptLevel2Name", trim(profile.getDeptLevel2Name()));
            summary.put("deptLevel3Name", trim(profile.getDeptLevel3Name()));
            summary.put("storeName", trim(profile.getStoreName()));
            summary.put("jobGrade", trim(profile.getJobGrade()));
            summary.put("departmentSupervisor", trim(profile.getDepartmentSupervisor()));
            summary.put("directSupervisor", trim(profile.getDirectSupervisor()));
            summary.put("employeeStatus", trim(profile.getEmployeeStatus()));
            summary.put("employeeCategory", trim(profile.getEmployeeCategory()));
            summary.put("entryDate", formatDate(profile.getEntryDate()));
        }
        return summary;
    }

    private void addMissing(SysProfileCompletionVo result, String key, String label, boolean missing)
    {
        if (missing)
        {
            result.getMissingFields().add(new SysProfileCompletionFieldVo(key, label));
        }
    }

    private String mergeText(String requested, String current)
    {
        return requested == null ? trim(current) : trim(requested);
    }

    private void require(String value, String message)
    {
        if (isBlank(value))
        {
            throw new ServiceException(message);
        }
    }

    private void requireWithMax(String value, int maxLength, String requiredMessage, String lengthMessage)
    {
        require(value, requiredMessage);
        if (value.length() > maxLength)
        {
            throw new ServiceException(lengthMessage);
        }
    }

    private void optionalWithMax(String value, int maxLength, String lengthMessage)
    {
        String normalized = trim(value);
        if (normalized != null && normalized.length() > maxLength)
        {
            throw new ServiceException(lengthMessage);
        }
    }

    private boolean isValidSex(String sex)
    {
        return "0".equals(trim(sex)) || "1".equals(trim(sex));
    }

    private boolean isValidText(String value, int maxLength)
    {
        String normalized = trim(value);
        return normalized != null && normalized.length() <= maxLength;
    }

    private boolean isValidMainlandMobile(String phonenumber)
    {
        String normalized = trim(phonenumber);
        return normalized != null && MAINLAND_MOBILE_PATTERN.matcher(normalized).matches();
    }

    private boolean isValidIdNumber(SysUserProfile profile)
    {
        if (profile == null || !isValidText(profile.getIdType(), 64))
        {
            return false;
        }
        String idNumber = trim(profile.getIdNumber());
        if (idNumber == null)
        {
            return false;
        }
        if (isMainlandIdType(profile.getIdType()))
        {
            return isValidMainlandIdCard(idNumber);
        }
        return idNumber.length() >= 3 && idNumber.length() <= 64;
    }

    private boolean isMainlandIdType(String idType)
    {
        return trim(idType) != null && trim(idType).contains("身份证");
    }

    private LocalDate toLocalDate(Date value)
    {
        ZoneId zone = clock.getZone() == null ? ZoneId.systemDefault() : clock.getZone();
        return Instant.ofEpochMilli(value.getTime()).atZone(zone).toLocalDate();
    }

    private String formatDate(Date value)
    {
        return value == null ? null : DATE_FORMATTER.format(toLocalDate(value));
    }

    private String mask(String value, int prefixLength, int suffixLength)
    {
        String normalized = trim(value);
        if (normalized == null || normalized.length() <= prefixLength + suffixLength)
        {
            return "***********";
        }
        return normalized.substring(0, prefixLength) + "***********"
                + normalized.substring(normalized.length() - suffixLength);
    }

    private boolean isBlank(String value)
    {
        return trim(value) == null;
    }

    private String trim(String value)
    {
        if (value == null)
        {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
