package com.erp.system.service.impl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.erp.common.core.exception.ServiceException;
import com.erp.system.api.domain.ReviewedSignProfileSupplement;
import com.erp.system.api.domain.ReviewedSignProfileSupplementResult;
import com.erp.system.api.domain.SysUserProfile;
import com.erp.system.domain.SysSignProfileSupplementAudit;
import com.erp.system.mapper.SysSignProfileSupplementAuditMapper;
import com.erp.system.mapper.SysUserProfileMapper;
import com.erp.system.service.ISysSigningProfileSupplementService;
import com.erp.system.service.support.SigningProfileFactsHash;

/** Transactional, idempotent System-side boundary for reviewed signing facts. */
@Service
public class SysSigningProfileSupplementServiceImpl implements ISysSigningProfileSupplementService
{
    public static final String STUDENT = "STUDENT";
    public static final String NON_STUDENT = "NON_STUDENT";
    public static final String RETIRED = "RETIRED";
    public static final String NOT_RETIRED = "NOT_RETIRED";

    private static final String OPERATOR = "OA_SIGN_DATA_REVIEW";
    private static final Pattern REQUEST_ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{0,63}");
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern YEAR_MONTH = Pattern.compile("(?:19|20)\\d{2}-(?:0[1-9]|1[0-2])");
    private static final Set<String> STUDENT_STATUSES = Set.of(STUDENT, NON_STUDENT);
    private static final Set<String> RETIREMENT_STATUSES = Set.of(RETIRED, NOT_RETIRED);

    private final SysSignProfileSupplementAuditMapper auditMapper;
    private final SysUserProfileMapper profileMapper;

    public SysSigningProfileSupplementServiceImpl(SysSignProfileSupplementAuditMapper auditMapper,
            SysUserProfileMapper profileMapper)
    {
        this.auditMapper = auditMapper;
        this.profileMapper = profileMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReviewedSignProfileSupplementResult supplement(ReviewedSignProfileSupplement submitted)
    {
        ReviewedSignProfileSupplement request = normalizeAndValidate(submitted);
        String requestHash = hashRequest(request);
        SysSignProfileSupplementAudit claim = new SysSignProfileSupplementAudit();
        claim.setRequestId(request.getRequestId());
        claim.setEmployeeId(request.getEmployeeId());
        claim.setRequestHash(requestHash);
        claim.setFieldMask(fieldMask(request));
        claim.setCreateBy(OPERATOR);
        auditMapper.insertIfAbsent(claim);

        SysSignProfileSupplementAudit audit = auditMapper.selectByRequestIdForUpdate(request.getRequestId());
        if (audit == null)
        {
            throw new ServiceException("签约档案同步幂等账本写入失败");
        }
        if (!request.getRequestId().equals(audit.getRequestId())
                || !request.getEmployeeId().equals(audit.getEmployeeId())
                || !requestHash.equals(audit.getRequestHash()))
        {
            throw new ServiceException("签约档案同步 requestId 已被不同请求使用");
        }
        if ("COMPLETED".equals(audit.getStatus()))
        {
            if (audit.getBeforeHash() == null || audit.getAfterHash() == null)
            {
                throw new ServiceException("签约档案同步幂等账本完成记录不完整");
            }
            return new ReviewedSignProfileSupplementResult(false, true,
                    audit.getBeforeHash(), audit.getAfterHash(), audit.getFieldMask());
        }
        if (!"PENDING".equals(audit.getStatus()))
        {
            throw new ServiceException("签约档案同步幂等账本状态无效");
        }

        if (auditMapper.lockActiveEmployee(request.getEmployeeId()) == null)
        {
            throw new ServiceException("签约档案同步目标员工不存在或已停用");
        }
        if (profileMapper.lockSigningProfileByUserId(request.getEmployeeId()) == null)
        {
            throw new ServiceException("签约档案同步目标员工档案不存在或未初始化");
        }
        SysUserProfile before = profileMapper.selectUserProfileByUserId(request.getEmployeeId());
        if (before == null)
        {
            throw new ServiceException("签约档案同步目标档案不存在");
        }
        String beforeHash = SigningProfileFactsHash.of(before);
        if (!request.getExpectedProfileHash().equals(beforeHash))
        {
            throw new ServiceException("员工档案个人事实已更新，请重新获取资料并审核");
        }
        validateEffectiveFacts(before, request);

        if (profileMapper.updateReviewedSigningFacts(request, OPERATOR) != 1)
        {
            throw new ServiceException("签约档案客观事实更新失败");
        }
        SysUserProfile after = profileMapper.selectUserProfileByUserId(request.getEmployeeId());
        if (after == null)
        {
            throw new ServiceException("签约档案更新结果读取失败");
        }
        String afterHash = SigningProfileFactsHash.of(after);
        if (auditMapper.markCompleted(audit.getAuditId(), beforeHash, afterHash) != 1)
        {
            throw new ServiceException("签约档案同步审计完成状态写入失败");
        }
        return new ReviewedSignProfileSupplementResult(true, false,
                beforeHash, afterHash, audit.getFieldMask());
    }

    private ReviewedSignProfileSupplement normalizeAndValidate(ReviewedSignProfileSupplement source)
    {
        if (source == null)
        {
            throw new ServiceException("签约档案同步请求不能为空");
        }
        ReviewedSignProfileSupplement value = new ReviewedSignProfileSupplement();
        value.setRequestId(trim(source.getRequestId()));
        value.setEmployeeId(source.getEmployeeId());
        value.setExpectedProfileHash(lower(source.getExpectedProfileHash()));
        value.setCurrentAddress(optionalText(source.getCurrentAddress(), 255, "现居住地址"));
        value.setStudentStatus(upper(source.getStudentStatus()));
        value.setSchoolName(optionalText(source.getSchoolName(), 200, "在读学校"));
        value.setRetirementStatus(upper(source.getRetirementStatus()));
        value.setIncomeStartYearMonth(optionalText(source.getIncomeStartYearMonth(), 7,
                "主要劳动收入起始月"));

        if (value.getRequestId() == null || !REQUEST_ID.matcher(value.getRequestId()).matches())
        {
            throw new ServiceException("签约档案同步 requestId 格式无效");
        }
        if (value.getEmployeeId() == null || value.getEmployeeId() <= 0)
        {
            throw new ServiceException("签约档案同步员工ID无效");
        }
        if (value.getExpectedProfileHash() == null
                || !SHA256.matcher(value.getExpectedProfileHash()).matches())
        {
            throw new ServiceException("签约档案同步期望档案哈希无效");
        }
        if (value.getStudentStatus() != null && !STUDENT_STATUSES.contains(value.getStudentStatus()))
        {
            throw new ServiceException("在校状态只允许 STUDENT 或 NON_STUDENT");
        }
        if (value.getRetirementStatus() != null
                && !RETIREMENT_STATUSES.contains(value.getRetirementStatus()))
        {
            throw new ServiceException("退休状态只允许 RETIRED 或 NOT_RETIRED");
        }
        if (value.getIncomeStartYearMonth() != null
                && !YEAR_MONTH.matcher(value.getIncomeStartYearMonth()).matches())
        {
            throw new ServiceException("主要劳动收入起始月必须为 yyyy-MM");
        }
        if (NON_STUDENT.equals(value.getStudentStatus()) && value.getSchoolName() != null)
        {
            throw new ServiceException("非在校状态不能同时同步在读学校");
        }
        if (STUDENT.equals(value.getStudentStatus()) && value.getIncomeStartYearMonth() != null)
        {
            throw new ServiceException("在校状态不能同时同步主要劳动收入起始月");
        }
        if (value.getCurrentAddress() == null && value.getStudentStatus() == null
                && value.getSchoolName() == null && value.getRetirementStatus() == null
                && value.getIncomeStartYearMonth() == null)
        {
            throw new ServiceException("签约档案同步至少需要一项审核后个人事实");
        }
        return value;
    }

    private void validateEffectiveFacts(SysUserProfile before, ReviewedSignProfileSupplement patch)
    {
        String studentStatus = patch.getStudentStatus() != null
                ? patch.getStudentStatus() : trim(before.getStudentStatus());
        String schoolName = patch.getSchoolName() != null
                ? patch.getSchoolName() : trim(before.getSchoolName());
        String incomeStartYearMonth = patch.getIncomeStartYearMonth() != null
                ? patch.getIncomeStartYearMonth() : trim(before.getIncomeStartYearMonth());

        if (NON_STUDENT.equals(patch.getStudentStatus()))
        {
            schoolName = null;
        }
        if (STUDENT.equals(patch.getStudentStatus()))
        {
            incomeStartYearMonth = null;
        }

        if (studentStatus != null && !STUDENT_STATUSES.contains(studentStatus))
        {
            throw new ServiceException("员工档案现有在校状态无效，请先更正后重试");
        }
        if (STUDENT.equals(studentStatus))
        {
            if (schoolName == null)
            {
                throw new ServiceException("在校时必须提供在读学校");
            }
            if (incomeStartYearMonth != null)
            {
                throw new ServiceException("在校状态不能同时保留主要劳动收入起始月");
            }
        }
        else if (NON_STUDENT.equals(studentStatus))
        {
            if (schoolName != null)
            {
                throw new ServiceException("非在校状态不能保留在读学校");
            }
        }
        else if (schoolName != null)
        {
            throw new ServiceException("未确认在校状态时不能同步在读学校");
        }

        if (incomeStartYearMonth != null && !NON_STUDENT.equals(studentStatus))
        {
            throw new ServiceException("只有最终状态为 NON_STUDENT 时才能同步主要劳动收入起始月");
        }
    }

    private String optionalText(String source, int maxLength, String label)
    {
        if (source == null)
        {
            return null;
        }
        String value = source.trim();
        if (value.isEmpty())
        {
            throw new ServiceException(label + "不能是空字符串");
        }
        if (value.length() > maxLength)
        {
            throw new ServiceException(label + "超过长度限制");
        }
        return value;
    }

    private String upper(String value)
    {
        String normalized = trim(value);
        return normalized == null ? null : normalized.toUpperCase(java.util.Locale.ROOT);
    }

    private String lower(String value)
    {
        String normalized = trim(value);
        return normalized == null ? null : normalized.toLowerCase(java.util.Locale.ROOT);
    }

    private String trim(String value)
    {
        if (value == null)
        {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String fieldMask(ReviewedSignProfileSupplement request)
    {
        List<String> fields = new ArrayList<>();
        if (request.getCurrentAddress() != null) fields.add("currentAddress");
        if (request.getStudentStatus() != null)
        {
            fields.add("studentStatus");
            if (NON_STUDENT.equals(request.getStudentStatus())) fields.add("schoolName");
            if (STUDENT.equals(request.getStudentStatus())) fields.add("incomeStartYearMonth");
        }
        if (request.getSchoolName() != null) fields.add("schoolName");
        if (request.getIncomeStartYearMonth() != null) fields.add("incomeStartYearMonth");
        if (request.getRetirementStatus() != null) fields.add("retirementStatus");
        return String.join(",", fields);
    }

    private String hashRequest(ReviewedSignProfileSupplement value)
    {
        return sha256(canonical("v2", String.valueOf(value.getEmployeeId()),
                value.getExpectedProfileHash(), value.getCurrentAddress(), value.getStudentStatus(),
                value.getSchoolName(), value.getRetirementStatus(), value.getIncomeStartYearMonth()));
    }

    private String canonical(String... values)
    {
        StringBuilder result = new StringBuilder();
        for (String value : values)
        {
            if (value == null)
            {
                result.append("-1:");
            }
            else
            {
                result.append(value.length()).append(':').append(value);
            }
            result.append(';');
        }
        return result.toString();
    }

    private String sha256(String value)
    {
        try
        {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        }
        catch (NoSuchAlgorithmException exception)
        {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
