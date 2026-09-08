package com.erp.oa.service.impl;

import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import com.erp.common.core.exception.ServiceException;
import com.erp.oa.constant.OaOnboardSalaryVersionPolicy;
import com.erp.oa.domain.OaSignOnboardContractSnapshot;
import com.erp.oa.domain.OaSignOnboardImportBatch;
import com.erp.oa.domain.OaSignOnboardImportRow;
import com.erp.oa.domain.dto.OaSignOnboardImportRowUpdateRequest;
import com.erp.system.api.constant.SigningProfileCodes;
import com.erp.system.api.domain.SignCandidateUser;

/**
 * Pure row-level policy for onboarding imports.
 *
 * <p>Database access, authorization, company/seal matching, plan selection and transactions stay
 * in {@link OaSignOnboardImportService}. This policy owns only authoritative identity comparison,
 * editable fact normalization, row validation and employee-maintainable missing-field derivation.
 */
final class OaSignOnboardRowPolicy
{
    private static final Set<String> EMPLOYEE_FIELDS = Set.of(
            "currentAddress", "studentStatus", "schoolName", "retirementStatus",
            "incomeStartYearMonth");

    boolean profileFactsMatch(OaSignOnboardContractSnapshot snapshot,
            SignCandidateUser candidate)
    {
        String frozen = trim(snapshot == null ? null : snapshot.getProfileFactsHash());
        String current = trim(candidate == null ? null : candidate.getProfileFactsHash());
        return frozen == null && current == null || frozen != null && frozen.equals(current);
    }

    boolean identityFactsMatch(OaSignOnboardContractSnapshot snapshot,
            SignCandidateUser candidate)
    {
        return snapshot != null && candidate != null
                && sameName(snapshot.getEmployeeName(), displayName(candidate))
                && Objects.equals(trim(snapshot.getPhone()), trim(candidate.getPhonenumber()))
                && Objects.equals(normalizeId(snapshot.getIdNumber()),
                        normalizeId(candidate.getIdNumber()));
    }

    boolean identityFactsMatch(OaSignOnboardImportBatch batch,
            OaSignOnboardContractSnapshot snapshot, SignCandidateUser candidate)
    {
        return identityFactsMatch(snapshot, candidate)
                && !(batch != null && Integer.valueOf(0).equals(batch.getSelectedCount())
                        && "离职".equals(trim(candidate.getEmployeeStatus())));
    }

    void applyAuthoritativeIdentity(OaSignOnboardContractSnapshot snapshot,
            SignCandidateUser candidate)
    {
        snapshot.setEmployeeName(displayName(candidate));
        snapshot.setPhone(trim(candidate.getPhonenumber()));
        snapshot.setIdNumber(normalizeId(candidate.getIdNumber()));
    }

    void applyUpdate(OaSignOnboardImportRowUpdateRequest request,
            OaSignOnboardContractSnapshot value, OaSignOnboardImportRow row)
    {
        if (request.getLegalEntityId() != null)
        {
            if (request.getLegalEntityId() <= 0)
                throw new ServiceException("公司主数据编号无效");
            boolean changed = !Objects.equals(request.getLegalEntityId(),
                    row.getMatchedLegalEntityId());
            row.setMatchedLegalEntityId(request.getLegalEntityId());
            row.setCompanyMatchMode("HR_CONFIRMED");
            if (changed)
            {
                row.setRecommendedSealId(null);
                row.setSealRecommendationMode(null);
            }
        }
        if (request.getSealId() != null)
        {
            if (request.getSealId() <= 0) throw new ServiceException("印章编号无效");
            if (row.getMatchedLegalEntityId() == null)
                throw new ServiceException("请先确认公司再选择印章");
            row.setRecommendedSealId(request.getSealId());
            row.setSealRecommendationMode("HR_SELECTED");
        }
        if (request.getCurrentAddress() != null)
        {
            value.setCurrentAddress(text(request.getCurrentAddress(), 255));
            value.setAddressSource("HR");
        }
        if (request.getContractTypeCode() != null)
            value.setContractTypeCode(upper(request.getContractTypeCode()));
        if (request.getSocialTypeCode() != null)
            value.setSocialTypeCode(upper(request.getSocialTypeCode()));
        if (request.getContractTermCode() != null)
            value.setContractTermCode(upper(request.getContractTermCode()));
        if (request.getEmployeePost() != null)
            value.setEmployeePost(text(request.getEmployeePost(), 128));
        if (request.getJobGradeCode() != null)
            value.setJobGradeCode(trim(request.getJobGradeCode().replace("级", "")));
        if (request.getWorkLocation() != null)
            value.setWorkLocation(text(request.getWorkLocation(), 128));
        if (request.getCityLevel() != null)
            value.setCityLevel(text(request.getCityLevel(), 64));
        if (request.getContractStartDate() != null)
            value.setContractStartDate(request.getContractStartDate());
        if (request.getContractEndDate() != null)
            value.setContractEndDate(request.getContractEndDate());
        if (request.getProbationStartDate() != null)
            value.setProbationStartDate(request.getProbationStartDate());
        if (request.getProbationEndDate() != null)
            value.setProbationEndDate(request.getProbationEndDate());
        if (request.getSalaryTotal() != null) value.setSalaryTotal(request.getSalaryTotal());
        if (request.getBaseSalary() != null) value.setBaseSalary(request.getBaseSalary());
        if (request.getPostSalary() != null) value.setPostSalary(request.getPostSalary());
        if (request.getFieldAllowance() != null)
            value.setFieldAllowance(request.getFieldAllowance());
        if (request.getPerformanceSalary() != null)
            value.setPerformanceSalary(request.getPerformanceSalary());
        if (request.getServicePersonType() != null)
            value.setServicePersonType(upper(request.getServicePersonType()));
        if (request.getInsuranceType() != null)
            value.setInsuranceType(upper(request.getInsuranceType()));
        if (request.getStudentStatus() != null)
            value.setStudentStatus(upper(request.getStudentStatus()));
        if (request.getSchoolName() != null)
            value.setSchoolName(text(request.getSchoolName(), 128));
        if (request.getRetirementStatus() != null)
            value.setRetirementStatus(upper(request.getRetirementStatus()));
        if (request.getIncomeStartYearMonth() != null)
            value.setIncomeStartYearMonth(trim(request.getIncomeStartYearMonth()));
        if (request.getWarningConfirmed() != null)
            row.setWarningConfirmed(request.getWarningConfirmed());
        if (request.getWarningReason() != null)
            row.setWarningReason(text(request.getWarningReason(), 500));
        if (request.getHistoricalSupplement() != null)
            row.setHistoricalSupplement(request.getHistoricalSupplement());
        if (request.getHistoricalReason() != null)
            row.setHistoricalReason(text(request.getHistoricalReason(), 500));
        if (request.getNoExternalContractConfirmed() != null)
            row.setNoExternalContractConfirmed(request.getNoExternalContractConfirmed());
        if (Boolean.TRUE.equals(row.getWarningConfirmed()) && blank(row.getWarningReason()))
            throw new ServiceException("确认薪资警告时必须填写原因");
        if (Boolean.TRUE.equals(row.getHistoricalSupplement()) && blank(row.getHistoricalReason()))
            throw new ServiceException("历史补签必须填写原因");
    }

    boolean editsPersonalFacts(OaSignOnboardImportRowUpdateRequest request)
    {
        return request != null && (request.getCurrentAddress() != null
                || request.getStudentStatus() != null
                || request.getSchoolName() != null
                || request.getRetirementStatus() != null
                || request.getIncomeStartYearMonth() != null);
    }

    void validateEditable(OaSignOnboardContractSnapshot value, Set<String> errors)
    {
        if (!Set.of(SigningProfileCodes.LABOR_CONTRACT, SigningProfileCodes.SERVICE_CONTRACT)
                .contains(value.getContractTypeCode()))
            errors.add("INVALID_CONTRACT_TYPE");
        if (!Set.of(SigningProfileCodes.SOCIAL_INSURED, SigningProfileCodes.SOCIAL_UNINSURED)
                .contains(value.getSocialTypeCode()))
            errors.add("INVALID_SOCIAL_TYPE");
        value.setSalaryVersion(OaOnboardSalaryVersionPolicy.requiredSalaryVersion(
                value.getSocialTypeCode()));
        if (value.getSalaryVersion() == null) errors.add("SALARY_VERSION_NOT_DERIVABLE");
        if (SigningProfileCodes.SERVICE_CONTRACT.equals(value.getContractTypeCode())
                && SigningProfileCodes.SOCIAL_INSURED.equals(value.getSocialTypeCode()))
            errors.add("UNSUPPORTED_COMBINATION");
        if (!Set.of(SigningProfileCodes.FIXED_TERM, SigningProfileCodes.OPEN_ENDED)
                .contains(value.getContractTermCode()))
            errors.add("INVALID_CONTRACT_TERM");
        if (blank(value.getJobGradeCode()) || !value.getJobGradeCode().matches("[2-9]"))
            errors.add("INVALID_JOB_GRADE");
        if (value.getContractStartDate() == null || value.getContractEndDate() == null
                || !value.getContractEndDate().isAfter(value.getContractStartDate()))
            errors.add("INVALID_CONTRACT_DATES");
        if ((value.getProbationStartDate() == null) != (value.getProbationEndDate() == null)
                || value.getProbationStartDate() != null
                && (value.getProbationEndDate().isBefore(value.getProbationStartDate())
                || value.getProbationStartDate().isBefore(value.getContractStartDate())
                || value.getProbationEndDate().isAfter(value.getContractEndDate())))
            errors.add("INVALID_PROBATION_DATES");
        if (!validSalary(value)) errors.add("INVALID_SALARY_TOTAL");
        if (!validFact(value.getStudentStatus(), "STUDENT", "NON_STUDENT"))
            errors.add("INVALID_STUDENT_STATUS");
        if (!validFact(value.getRetirementStatus(), "RETIRED", "NOT_RETIRED"))
            errors.add("INVALID_RETIREMENT_STATUS");
        if (!blank(value.getIncomeStartYearMonth())
                && !value.getIncomeStartYearMonth().matches("[0-9]{4}-(0[1-9]|1[0-2])"))
            errors.add("INVALID_INCOME_START_YEAR_MONTH");
    }

    List<String> employeeMissingFields(OaSignOnboardContractSnapshot value)
    {
        List<String> fields = new ArrayList<>();
        if (blank(value.getCurrentAddress())) fields.add("currentAddress");
        boolean service = SigningProfileCodes.SERVICE_CONTRACT.equals(
                value.getContractTypeCode());
        int age = ageAt(value.getIdNumber(), value.getContractStartDate());
        if ((service || age >= 16 && age < 18) && blank(value.getStudentStatus()))
        {
            fields.add("studentStatus");
            // The employee UI renders only allowed fields. Include schoolName with
            // studentStatus so choosing STUDENT can be completed in the same submission.
            fields.add("schoolName");
            if (age >= 16 && age < 18) fields.add("incomeStartYearMonth");
        }
        if (service && blank(value.getRetirementStatus())) fields.add("retirementStatus");
        if ("STUDENT".equals(value.getStudentStatus()) && blank(value.getSchoolName()))
            fields.add("schoolName");
        if (age >= 16 && age < 18
                && "NON_STUDENT".equals(value.getStudentStatus())
                && blank(value.getIncomeStartYearMonth()))
            fields.add("incomeStartYearMonth");
        fields.retainAll(EMPLOYEE_FIELDS);
        return fields;
    }

    int ageAt(String idNumber, LocalDate date)
    {
        try
        {
            if (idNumber == null || !idNumber.matches("[0-9]{17}[0-9X]")) return -1;
            LocalDate birth = LocalDate.parse(
                    idNumber.substring(6, 14), DateTimeFormatter.BASIC_ISO_DATE);
            return Period.between(birth, date == null ? LocalDate.now() : date).getYears();
        }
        catch (Exception ignored)
        {
            return -1;
        }
    }

    boolean validSalary(OaSignOnboardContractSnapshot value)
    {
        if (value.getSalaryTotal() == null || value.getBaseSalary() == null
                || value.getPostSalary() == null || value.getFieldAllowance() == null
                || value.getPerformanceSalary() == null)
            return false;
        if (value.getSalaryTotal().signum() <= 0 || value.getBaseSalary().signum() < 0
                || value.getPostSalary().signum() < 0
                || value.getFieldAllowance().signum() < 0
                || value.getPerformanceSalary().signum() < 0)
            return false;
        return value.getBaseSalary().add(value.getPostSalary()).add(value.getFieldAllowance())
                .add(value.getPerformanceSalary()).compareTo(value.getSalaryTotal()) == 0;
    }

    private boolean validFact(String value, String... allowed)
    {
        if (blank(value)) return true;
        return List.of(allowed).contains(upper(value));
    }

    private boolean sameName(String first, String second)
    {
        return Objects.equals(trim(first), trim(second));
    }

    private String normalizeId(String value)
    {
        return value == null ? null : value.replace(" ", "").toUpperCase(Locale.ROOT);
    }

    private String displayName(SignCandidateUser value)
    {
        return value == null ? null
                : blank(value.getNickName()) ? trim(value.getUserName()) : trim(value.getNickName());
    }

    private String text(String value, int max)
    {
        String result = trim(value);
        if (result != null && result.length() > max)
            throw new ServiceException("填写内容不能超过" + max + "个字符");
        return result;
    }

    private String upper(String value)
    {
        value = trim(value);
        return value == null ? null : value.toUpperCase(Locale.ROOT);
    }

    private String trim(String value)
    {
        if (value == null) return null;
        String result = value.trim();
        return result.isEmpty() ? null : result;
    }

    private boolean blank(String value)
    {
        return trim(value) == null;
    }
}
