package com.erp.system.support;

import java.time.Clock;
import java.util.Collections;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.erp.system.api.domain.SysUser;
import com.erp.system.api.domain.SysUserProfile;
import com.erp.system.exception.HrOnboardingValidationException;
import com.erp.system.mapper.SysUserMapper;
import com.erp.system.mapper.SysUserProfileMapper;
import com.erp.system.service.ISysConfigService;

@Component
public class HrEmployeeNoGenerator
{
    private static final String PREFIX_KEY = "hr.employee.no.prefix";
    private static final String SEQUENCE_KEY = "GLOBAL";
    private static final long MAX_SEQUENCE = 99999L;
    private static final Pattern PREFIX_PATTERN = Pattern.compile("[A-Z]{1,4}");
    private final ISysConfigService configService;
    private final SysUserMapper userMapper;
    private final SysUserProfileMapper profileMapper;

    @Autowired
    public HrEmployeeNoGenerator(ISysConfigService configService, SysUserMapper userMapper,
            SysUserProfileMapper profileMapper)
    {
        this(configService, userMapper, profileMapper, Clock.systemDefaultZone());
    }

    public HrEmployeeNoGenerator(ISysConfigService configService, SysUserMapper userMapper,
            SysUserProfileMapper profileMapper, Clock clock)
    {
        this.configService = configService;
        this.userMapper = userMapper;
        this.profileMapper = profileMapper;
    }

    public String generate(Long onboardingId, Date entryDate)
    {
        if (onboardingId == null) throw failure("EMPLOYEE_NO_SOURCE_MISSING", "入职单ID不能为空");
        String configured = configService == null ? null : configService.selectConfigByKey(PREFIX_KEY);
        String prefix = configured == null || configured.trim().isEmpty()
                ? "E" : configured.trim().toUpperCase(Locale.ROOT);
        if (!PREFIX_PATTERN.matcher(prefix).matches())
        {
            throw failure("EMPLOYEE_NO_PREFIX_INVALID", "工号前缀必须为1至4位英文字母");
        }
        for (int attempt = 0; attempt < 100; attempt++)
        {
            Long current = profileMapper.selectEmployeeNoSequenceForUpdate(SEQUENCE_KEY);
            if (current == null)
            {
                throw failure("EMPLOYEE_NO_SEQUENCE_MISSING", "工号序列表未初始化，请先执行数据库迁移");
            }
            if (current >= MAX_SEQUENCE)
            {
                throw failure("EMPLOYEE_NO_EXHAUSTED", "工号生成空间已耗尽，请联系管理员");
            }
            long next = current + 1L;
            if (profileMapper.advanceEmployeeNoSequence(SEQUENCE_KEY, current, next) != 1)
            {
                continue;
            }
            String candidate = prefix + String.format("%05d", next);
            if (available(candidate))
            {
                return candidate;
            }
        }
        throw failure("EMPLOYEE_NO_EXHAUSTED", "工号生成空间已耗尽，请联系管理员");
    }

    public void validateOwnedBy(String employeeNo, Long userId)
    {
        if (employeeNo == null || employeeNo.trim().isEmpty()) return;
        SysUser usernameOwner = userMapper.selectUserByUserName(employeeNo);
        if (usernameOwner != null && (userId == null || !userId.equals(usernameOwner.getUserId())))
            throw failure("EMPLOYEE_NO_CONFLICT", "工号已被其他账号占用");
        SysUserProfile profileOwner = profileMapper.selectUserProfileByEmployeeNo(employeeNo);
        if (profileOwner != null && (userId == null || !userId.equals(profileOwner.getUserId())))
            throw failure("EMPLOYEE_NO_CONFLICT", "工号已被其他员工档案占用");
    }

    private boolean available(String candidate)
    {
        return userMapper.selectUserByUserName(candidate) == null
                && profileMapper.selectUserProfileByEmployeeNo(candidate) == null;
    }

    private HrOnboardingValidationException failure(String code, String message)
    {
        return new HrOnboardingValidationException(code, message, Collections.emptyMap(),
                Collections.singletonList(code));
    }
}
