package com.erp.system.support;

import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.stereotype.Component;
import com.erp.system.api.domain.SysUser;
import com.erp.system.api.domain.SysUserProfile;
import com.erp.system.domain.HrOnboarding;

/** Canonical registry for the approved employee master and reviewed signing facts. */
@Component
public class HrEmployeeFieldRegistry
{
    public enum StorageOwner { SYS_USER, SYS_USER_PROFILE, RELATION, DERIVED }
    public enum MaskingClass { NONE, PHONE, ID_NUMBER, BANK_ACCOUNT, ADDRESS, SALARY }
    public enum RequirementTier { REQUIRED, CONDITIONAL, OPTIONAL }
    public enum Responsibility { EMPLOYEE, ORG_ADMIN, HR, CONTRACT_SOCIAL, SYSTEM }
    public enum DueStage { PRE_ENTRY, POST_ENTRY_7D, LIFECYCLE, NONE }
    public enum RiskLevel { BLOCKING, IMPORTANT, NORMAL }

    private final List<FieldDefinition> fields;
    private final Map<String, FieldDefinition> byKey;
    private final Set<String> sensitiveRevealKeys;

    public HrEmployeeFieldRegistry()
    {
        List<FieldDefinition> values = new ArrayList<>();
        values.add(user("employeeName", "姓名", "nickName", "nick_name", true, "BASIC", MaskingClass.NONE));
        values.add(user("email", "邮箱", "email", "email", true, "BASIC", MaskingClass.NONE));
        values.add(user("phoneNumber", "手机号", "phonenumber", "phonenumber", true, "BASIC", MaskingClass.PHONE));
        values.add(user("sex", "性别", "sex", "sex", true, "BASIC", MaskingClass.NONE));
        values.add(user("remark", "备注", "remark", "remark", true, "RECORD", MaskingClass.NONE, false));
        values.add(profile("employeeNo", "工号", "employeeNo", "employee_no", false, "EMPLOYMENT", MaskingClass.NONE));
        values.add(derived("companyName", "所属公司", "companyName", "company_name", "ORGANIZATION"));
        values.add(derived("deptLevel1Name", "1级部门", "deptLevel1Name", "dept_level1_name", "ORGANIZATION"));
        values.add(derived("deptLevel2Name", "2级部门", "deptLevel2Name", "dept_level2_name", "ORGANIZATION"));
        values.add(derived("deptLevel3Name", "3级部门", "deptLevel3Name", "dept_level3_name", "ORGANIZATION"));
        values.add(derived("storeName", "4级门店", "storeName", "store_name", "ORGANIZATION"));
        values.add(derived("positionName", "职位", "positionNames", "position_names", "ORGANIZATION"));
        values.add(profile("jobGrade", "职级", "jobGrade", "job_grade", true, "ORGANIZATION", MaskingClass.NONE));
        values.add(derived("departmentSupervisor", "部门主管", "departmentSupervisor", "department_supervisor", "ORGANIZATION"));
        values.add(relation("directSupervisorUserId", "直属主管", "directSupervisorUserId", "direct_supervisor_user_id", true, "ORGANIZATION"));
        values.add(profile("employeeStatus", "员工状态", "employeeStatus", "employee_status", false, "EMPLOYMENT", MaskingClass.NONE));
        values.add(profile("employeeCategory", "人员类别", "employeeCategory", "employee_category", true, "EMPLOYMENT", MaskingClass.NONE));
        values.add(profile("birthDate", "出生日期", "birthDate", "birth_date", true, "BASIC", MaskingClass.NONE));
        values.add(profile("idType", "证件类型", "idType", "id_type", true, "IDENTITY", MaskingClass.NONE));
        values.add(profile("idNumber", "证件号码", "idNumber", "id_number", true, "IDENTITY", MaskingClass.ID_NUMBER));
        values.add(profile("bloodType", "血型", "bloodType", "blood_type", true, "BASIC", MaskingClass.NONE));
        values.add(profile("registeredResidence", "户口所在地", "registeredResidence", "registered_residence", true, "IDENTITY", MaskingClass.ADDRESS));
        values.add(profile("currentAddress", "现居住地址", "currentAddress", "current_address", true, "IDENTITY", MaskingClass.ADDRESS));
        values.add(profile("studentStatus", "当前在校状态", "studentStatus", "student_status", false, "SIGNING_FACT", MaskingClass.NONE));
        values.add(profile("schoolName", "当前在读学校", "schoolName", "school_name", false, "SIGNING_FACT", MaskingClass.NONE));
        values.add(profile("retirementStatus", "当前退休状态", "retirementStatus", "retirement_status", false, "SIGNING_FACT", MaskingClass.NONE));
        values.add(profile("incomeStartYearMonth", "主要劳动收入起始月", "incomeStartYearMonth", "income_start_year_month", false, "SIGNING_FACT", MaskingClass.NONE));
        values.add(profile("firstEducation", "第一学历", "firstEducation", "first_education", true, "EDUCATION", MaskingClass.NONE));
        values.add(profile("firstDegree", "第一学位", "firstDegree", "first_degree", true, "EDUCATION", MaskingClass.NONE));
        values.add(profile("firstGraduationDate", "毕业时间", "firstGraduationDate", "first_graduation_date", true, "EDUCATION", MaskingClass.NONE));
        values.add(profile("firstGraduationSchool", "第一学历毕业学校", "firstGraduationSchool", "first_graduation_school", true, "EDUCATION", MaskingClass.NONE));
        values.add(profile("firstMajor", "第一学历所学专业", "firstMajor", "first_major", true, "EDUCATION", MaskingClass.NONE));
        values.add(profile("highestEducation", "最高学历", "highestEducation", "highest_education", true, "EDUCATION", MaskingClass.NONE));
        values.add(profile("highestDegree", "最高学位", "highestDegree", "highest_degree", true, "EDUCATION", MaskingClass.NONE));
        values.add(profile("highestGraduationDate", "最高学历毕业时间", "highestGraduationDate", "highest_graduation_date", true, "EDUCATION", MaskingClass.NONE));
        values.add(profile("highestGraduationSchool", "最高学历毕业学校", "highestGraduationSchool", "highest_graduation_school", true, "EDUCATION", MaskingClass.NONE));
        values.add(profile("highestMajor", "最高学历所学专业", "highestMajor", "highest_major", true, "EDUCATION", MaskingClass.NONE));
        values.add(profile("politicalStatus", "政治面貌", "politicalStatus", "political_status", true, "BASIC", MaskingClass.NONE));
        values.add(profile("maritalStatus", "婚姻状况", "maritalStatus", "marital_status", true, "BASIC", MaskingClass.NONE));
        values.add(profile("nationality", "国籍", "nationality", "nationality", true, "BASIC", MaskingClass.NONE));
        values.add(profile("foreignNationalFlag", "是否外籍", "foreignNationalFlag", "foreign_national_flag", true, "BASIC", MaskingClass.NONE));
        values.add(profile("ethnicity", "民族", "ethnicity", "ethnicity", true, "BASIC", MaskingClass.NONE));
        values.add(profile("healthStatus", "健康状况", "healthStatus", "health_status", true, "BASIC", MaskingClass.NONE));
        values.add(profile("emergencyContact", "紧急联系人", "emergencyContact", "emergency_contact", true, "CONTACT", MaskingClass.NONE));
        values.add(profile("emergencyContactRelation", "与紧急联系人关系", "emergencyContactRelation", "emergency_contact_relation", true, "CONTACT", MaskingClass.NONE));
        values.add(profile("emergencyContactPhone", "紧急联系人电话", "emergencyContactPhone", "emergency_contact_phone", true, "CONTACT", MaskingClass.PHONE));
        values.add(profile("recruitmentChannel", "招聘渠道", "recruitmentChannel", "recruitment_channel", true, "EMPLOYMENT", MaskingClass.NONE));
        values.add(profile("officePhone", "办公电话", "officePhone", "office_phone", true, "CONTACT", MaskingClass.PHONE));
        values.add(profile("workStartDate", "参加工作时间", "workStartDate", "work_start_date", true, "EMPLOYMENT", MaskingClass.NONE));
        values.add(derived("workYears", "工龄", "workYears", "work_years", "EMPLOYMENT"));
        values.add(profile("entryDate", "入职时间", "entryDate", "entry_date", true, "EMPLOYMENT", MaskingClass.NONE));
        values.add(profile("probationPeriod", "试用期", "probationPeriod", "probation_period", true, "EMPLOYMENT", MaskingClass.NONE));
        values.add(profile("plannedRegularizationDate", "计划转正日期", "plannedRegularizationDate", "planned_regularization_date", true, "EMPLOYMENT", MaskingClass.NONE));
        values.add(profile("actualRegularizationDate", "实际转正日期", "actualRegularizationDate", "actual_regularization_date", true, "EMPLOYMENT", MaskingClass.NONE));
        values.add(derived("companyYears", "司龄", "companyYears", "company_years", "EMPLOYMENT"));
        values.add(profile("currentPositionStartDate", "本岗位任职日期", "currentPositionStartDate", "current_position_start_date", true, "EMPLOYMENT", MaskingClass.NONE));
        values.add(profile("contractStartDate", "现合同起始日", "contractStartDate", "contract_start_date", true, "CONTRACT", MaskingClass.NONE));
        values.add(profile("contractEndDate", "现合同到期日", "contractEndDate", "contract_end_date", true, "CONTRACT", MaskingClass.NONE));
        values.add(profile("contractType", "合同类型", "contractType", "contract_type", true, "CONTRACT", MaskingClass.NONE));
        values.add(derived("contractTerm", "合同期限", "contractTerm", "contract_term", "CONTRACT"));
        values.add(profile("renewalCount", "续签次数", "renewalCount", "renewal_count", true, "CONTRACT", MaskingClass.NONE));
        values.add(profile("workLocation", "工作所在地", "workLocation", "work_location", true, "EMPLOYMENT", MaskingClass.NONE));
        values.add(profile("workCityLevel", "工作所在城市级别", "workCityLevel", "work_city_level", true, "EMPLOYMENT", MaskingClass.NONE));
        values.add(profile("attendanceMethod", "考勤方式", "attendanceMethod", "attendance_method", true, "EMPLOYMENT", MaskingClass.NONE));
        values.add(profile("householdType", "户口性质", "householdType", "household_type", true, "SOCIAL", MaskingClass.NONE));
        values.add(profile("socialType", "社保类型", "socialType", "social_type", true, "SOCIAL", MaskingClass.NONE));
        values.add(profile("socialSecurityLocation", "社保缴纳地", "socialSecurityLocation", "social_security_location", true, "SOCIAL", MaskingClass.NONE));
        values.add(profile("housingFundLocation", "公积金缴纳地", "housingFundLocation", "housing_fund_location", true, "SOCIAL", MaskingClass.NONE));
        values.add(profile("leaveDate", "离职时间", "leaveDate", "leave_date", true, "EMPLOYMENT", MaskingClass.NONE));
        values.add(profile("bankName", "开户银行", "bankName", "bank_name", true, "BANK", MaskingClass.NONE));
        values.add(profile("bankAccount", "银行卡号", "bankAccount", "bank_account", true, "BANK", MaskingClass.BANK_ACCOUNT));
        values.add(profile("legalEntity", "法人单位", "legalEntity", "legal_entity", true, "CONTRACT", MaskingClass.NONE));

        values.add(profile("salaryTotal", "合同综合工资", "salaryTotal", "salary_total", false, "SALARY", MaskingClass.SALARY));
        values.add(profile("baseSalary", "底薪", "baseSalary", "base_salary", false, "SALARY", MaskingClass.SALARY));
        values.add(profile("postSalary", "综合岗位津贴", "postSalary", "post_salary", false, "SALARY", MaskingClass.SALARY));
        values.add(profile("fieldAllowance", "综合驻外补贴", "fieldAllowance", "field_allowance", false, "SALARY", MaskingClass.SALARY));
        values.add(profile("performanceSalary", "月度绩效津贴", "performanceSalary", "performance_salary", false, "SALARY", MaskingClass.SALARY));

        // The formal employee master is broader than the onboarding core. All legitimate post-entry fields
        // participate unless an explicit applicability rule below marks them not applicable.
        Set<String> workflowOnlyFacts = Set.of("studentStatus", "schoolName", "retirementStatus",
                "incomeStartYearMonth");
        for (FieldDefinition value : values)
            value.profileCompleteness = !"SALARY".equals(value.completenessGroup) && !"remark".equals(value.key) && !workflowOnlyFacts.contains(value.key);
        configureGovernancePolicy(values);

        declare(values, "employeeName", "employeeName");
        declare(values, "phoneNumber", "phoneNumber");
        declare(values, "sex", "sex");
        declare(values, "remark", "remark");
        declare(values, "jobGrade", "jobGrade");
        declare(values, "directSupervisorUserId", "directSupervisorUserId");
        declare(values, "employeeCategory", "employeeCategory");
        declare(values, "birthDate", "birthDate");
        declare(values, "idType", "idType");
        declare(values, "idNumber", "idNumber");
        declare(values, "registeredResidence", "registeredResidence");
        declare(values, "currentAddress", "currentAddress");
        declare(values, "maritalStatus", "maritalStatus");
        declare(values, "ethnicity", "ethnicity");
        declare(values, "emergencyContact", "emergencyContact");
        declare(values, "emergencyContactRelation", "emergencyContactRelation");
        declare(values, "emergencyContactPhone", "emergencyContactPhone");
        declare(values, "entryDate", "actualEntryDate");
        declare(values, "probationPeriod", "probationPeriod");
        declare(values, "currentPositionStartDate", "actualEntryDate");
        declare(values, "contractStartDate", "actualEntryDate");
        declare(values, "contractType", "contractType");
        declare(values, "workLocation", "workLocation");
        declare(values, "workCityLevel", "workCityLevel");
        declare(values, "socialType", "socialType");
        declare(values, "bankName", "bankName");
        declare(values, "bankAccount", "bankAccount");
        declare(values, "legalEntity", "legalEntity");

        fields = Collections.unmodifiableList(values);
        Map<String, FieldDefinition> keyed = new LinkedHashMap<>();
        for (FieldDefinition value : values)
        {
            if (keyed.put(value.key, value) != null) throw new IllegalStateException("Duplicate employee field " + value.key);
        }
        byKey = Collections.unmodifiableMap(keyed);
        LinkedHashSet<String> reveal = new LinkedHashSet<>();
        for (FieldDefinition value : values)
            if (value.maskingClass != MaskingClass.NONE) reveal.add(value.key);
        sensitiveRevealKeys = Collections.unmodifiableSet(reveal);
    }

    private FieldDefinition user(String key, String label, String property, String column, boolean editable,
            String group, MaskingClass masking) { return user(key, label, property, column, editable, group, masking, true); }
    private FieldDefinition user(String key, String label, String property, String column, boolean editable,
            String group, MaskingClass masking, boolean completeness)
    { return new FieldDefinition(key, label, StorageOwner.SYS_USER, property, column, editable, completeness, group, masking); }
    private FieldDefinition profile(String key, String label, String property, String column, boolean editable,
            String group, MaskingClass masking)
    { return new FieldDefinition(key, label, StorageOwner.SYS_USER_PROFILE, property, column, editable, true, group, masking); }
    private FieldDefinition relation(String key, String label, String property, String column, boolean editable,
            String group)
    { return new FieldDefinition(key, label, StorageOwner.RELATION, property, column, editable, true, group, MaskingClass.NONE); }
    private FieldDefinition derived(String key, String label, String property, String column, String group)
    { return new FieldDefinition(key, label, StorageOwner.DERIVED, property, column, false, true, group, MaskingClass.NONE); }

    public List<FieldDefinition> getFields() { return fields; }
    public FieldDefinition getByKey(String key) { return byKey.get(key); }
    public FieldDefinition getProfileFieldByPropertyName(String propertyName)
    {
        if (propertyName == null) return null;
        return fields.stream()
                .filter(field -> field.storageOwner != StorageOwner.SYS_USER
                        && field.storageOwner != StorageOwner.DERIVED
                        && propertyName.equals(field.propertyName))
                .findFirst().orElse(null);
    }
    public Set<String> getSensitiveRevealKeys() { return sensitiveRevealKeys; }

    /**
     * Whether a field participates in the legacy coverage metric for this employee.
     * This deliberately preserves the original applicability rules so existing API fields stay compatible.
     */
    public boolean isApplicableForCompleteness(SysUser user, FieldDefinition field)
    {
        if (field == null || !field.profileCompleteness) return false;
        return isConditionApplicable(user, field, false);
    }

    /** Whether a field participates in the business-required metric for this employee. */
    public boolean isRequiredForCompleteness(SysUser user, FieldDefinition field)
    {
        if (field == null || !field.profileCompleteness || field.requirementTier == RequirementTier.OPTIONAL)
            return false;
        return field.requirementTier == RequirementTier.REQUIRED
                ? isApplicableForCompleteness(user, field)
                : isConditionApplicable(user, field, true);
    }

    private boolean isConditionApplicable(SysUser user, FieldDefinition field, boolean businessRequired)
    {
        SysUserProfile profile=user==null?null:user.getProfile();
        String status = profile == null ? null : HrEmployeeStatusCatalog.normalizeForRead(profile.getEmployeeStatus());
        if ("probationPeriod".equals(field.key) || "plannedRegularizationDate".equals(field.key))
            return "试用".equals(status);
        if ("actualRegularizationDate".equals(field.key))
            return "正式".equals(status)||"离职".equals(status);
        if ("leaveDate".equals(field.key)) return "离职".equals(status);
        if ("nationality".equals(field.key)) return profile!=null&&"1".equals(profile.getForeignNationalFlag());
        if ("ethnicity".equals(field.key)) return profile==null||!"1".equals(profile.getForeignNationalFlag());
        if ("storeName".equals(field.key))
            return user!=null&&user.getDept()!=null&&"STORE".equals(user.getDept().getDeptType())
                    || profile!=null&&notBlank(profile.getStoreName());
        if ("departmentSupervisor".equals(field.key))
        {
            Object supervisor=read(user,field);
            return (supervisor!=null&&notBlank(String.valueOf(supervisor)))
                    ||hasWarning(profile,"未配置部门负责人");
        }
        if ("deptLevel1Name".equals(field.key)||"deptLevel2Name".equals(field.key)
                ||"deptLevel3Name".equals(field.key))
            return read(user,field)!=null&&notBlank(String.valueOf(read(user,field)));
        if ("contractStartDate".equals(field.key)||"contractEndDate".equals(field.key)
                ||"contractType".equals(field.key)||"contractTerm".equals(field.key)
                ||"renewalCount".equals(field.key)||"legalEntity".equals(field.key))
            return contractApplicable(profile);
        if (businessRequired && "workYears".equals(field.key))
            return profile != null && profile.getWorkStartDate() != null;
        if (businessRequired && "companyYears".equals(field.key))
            return profile != null && profile.getEntryDate() != null;
        if (businessRequired && ("socialSecurityLocation".equals(field.key)
                || "housingFundLocation".equals(field.key)))
            return socialApplicable(profile);
        return true;
    }

    /** Registry-owned source-aware completion check, shared by list/detail/summary/dept aggregates. */
    public boolean isCompleteForCompleteness(SysUser user, FieldDefinition field)
    {
        if(user==null||field==null)return false;
        SysUserProfile profile=user.getProfile();
        return switch(field.key) {
            case "companyName" -> validDepartmentSource(user,profile);
            case "deptLevel1Name", "deptLevel2Name", "deptLevel3Name", "storeName" ->
                    validDepartmentSource(user,profile);
            case "positionName" -> notBlank(user.getPostNames())
                    ||profile!=null&&notBlank(profile.getPositionNames());
            case "departmentSupervisor" -> validDepartmentSource(user,profile)
                    &&(user.getDept()!=null&&notBlank(user.getDept().getLeader())
                    ||profile!=null&&notBlank(profile.getDepartmentSupervisor()));
            case "workYears" -> profile!=null&&validPastOrToday(profile.getWorkStartDate());
            case "companyYears" -> profile!=null&&validCompanyDates(profile);
            case "contractTerm" -> profile!=null&&validRange(profile.getContractStartDate(),profile.getContractEndDate());
            default -> present(read(user,field));
        };
    }

    private boolean validDepartmentSource(SysUser user,SysUserProfile profile)
    {
        if(user.getDeptId()==null||user.getDept()==null||!"0".equals(user.getDept().getStatus()))return false;
        if(profile==null||profile.getDerivedWarnings()==null)return true;
        return profile.getDerivedWarnings().stream().noneMatch(value->value!=null
                &&(value.contains("组织")||value.contains("公司")));
    }

    private boolean contractApplicable(SysUserProfile profile)
    {
        if(profile==null)return true;
        String mode=profile.getContractType();
        return !("NOT_APPLICABLE".equalsIgnoreCase(mode)||"NONE".equalsIgnoreCase(mode)
                ||"不适用".equals(mode)||"无需合同".equals(mode));
    }

    private boolean socialApplicable(SysUserProfile profile)
    {
        if (profile == null) return true;
        String mode = profile.getSocialType();
        return !("NOT_APPLICABLE".equalsIgnoreCase(mode)||"NONE".equalsIgnoreCase(mode)
                ||"不适用".equals(mode)||"无需社保".equals(mode)||"不缴纳".equals(mode));
    }

    private boolean hasWarning(SysUserProfile profile,String warning)
    {return profile!=null&&profile.getDerivedWarnings()!=null&&profile.getDerivedWarnings().contains(warning);}

    private boolean validCompanyDates(SysUserProfile profile)
    {
        java.util.Date end="离职".equals(profile.getEmployeeStatus())?profile.getLeaveDate():new java.util.Date();
        return validRange(profile.getEntryDate(),end);
    }

    private boolean validPastOrToday(java.util.Date value)
    {return value!=null&&!new java.sql.Date(value.getTime()).toLocalDate().isAfter(LocalDate.now());}

    private boolean validRange(java.util.Date start,java.util.Date end)
    {return start!=null&&end!=null&&!start.after(end);}
    private boolean present(Object value){return value!=null&&(!(value instanceof String text)||!text.isBlank());}
    private boolean notBlank(String value){return value!=null&&!value.isBlank();}

    public void copyDeclaredOnboardingMappings(HrOnboarding source, SysUser target)
    {
        copyDeclaredOnboardingMappings(source, target, null);
    }

    public void copyDeclaredOnboardingMappings(HrOnboarding source, SysUser target, StorageOwner owner)
    {
        if (source == null || target == null) return;
        BeanWrapper sourceWrapper = new BeanWrapperImpl(source);
        for (FieldDefinition field : fields)
        {
            if (field.onboardingPropertyName == null || (owner != null && field.storageOwner != owner)) continue;
            Object value=sourceWrapper.getPropertyValue(field.onboardingPropertyName);
            if(value instanceof java.util.Date date)value=new java.util.Date(date.getTime());
            write(target, field, value);
        }
    }

    private static FieldDefinition byKey(List<FieldDefinition> values, String key)
    {
        return values.stream().filter(value -> key.equals(value.key)).findFirst()
                .orElseThrow(() -> new IllegalStateException("Unknown employee field " + key));
    }

    private static void declare(List<FieldDefinition> values, String key, String onboardingPropertyName)
    {
        byKey(values, key).onboardingPropertyName = onboardingPropertyName;
    }

    private static void configureGovernancePolicy(List<FieldDefinition> values)
    {
        govern(values, RequirementTier.REQUIRED, Responsibility.EMPLOYEE, DueStage.PRE_ENTRY,
                RiskLevel.BLOCKING, "employeeName", "phoneNumber", "sex", "idType",
                "idNumber", "registeredResidence", "currentAddress", "foreignNationalFlag");
        govern(values, RequirementTier.REQUIRED, Responsibility.EMPLOYEE, DueStage.POST_ENTRY_7D,
                RiskLevel.IMPORTANT, "bankName", "bankAccount");
        govern(values, RequirementTier.CONDITIONAL, Responsibility.EMPLOYEE, DueStage.PRE_ENTRY,
                RiskLevel.IMPORTANT, "nationality");
        govern(values, RequirementTier.OPTIONAL, Responsibility.EMPLOYEE, DueStage.NONE,
                RiskLevel.NORMAL, "email", "bloodType", "politicalStatus", "healthStatus",
                "firstEducation", "firstDegree", "firstGraduationDate", "firstGraduationSchool",
                "firstMajor", "highestEducation", "highestDegree", "highestGraduationDate",
                "highestGraduationSchool", "highestMajor", "studentStatus", "schoolName",
                "retirementStatus", "incomeStartYearMonth", "birthDate", "maritalStatus", "ethnicity",
                "emergencyContact", "emergencyContactRelation", "emergencyContactPhone");

        govern(values, RequirementTier.REQUIRED, Responsibility.HR, DueStage.PRE_ENTRY,
                RiskLevel.BLOCKING, "employeeStatus", "employeeCategory", "workLocation");
        govern(values, RequirementTier.REQUIRED, Responsibility.HR, DueStage.POST_ENTRY_7D,
                RiskLevel.IMPORTANT, "employeeNo", "workStartDate", "entryDate", "currentPositionStartDate");
        govern(values, RequirementTier.CONDITIONAL, Responsibility.HR, DueStage.LIFECYCLE,
                RiskLevel.IMPORTANT, "probationPeriod", "plannedRegularizationDate",
                "actualRegularizationDate", "leaveDate");
        govern(values, RequirementTier.OPTIONAL, Responsibility.HR, DueStage.NONE,
                RiskLevel.NORMAL, "recruitmentChannel", "attendanceMethod", "officePhone", "workCityLevel");

        govern(values, RequirementTier.REQUIRED, Responsibility.ORG_ADMIN, DueStage.PRE_ENTRY,
                RiskLevel.BLOCKING, "companyName", "positionName");
        govern(values, RequirementTier.REQUIRED, Responsibility.ORG_ADMIN, DueStage.PRE_ENTRY,
                RiskLevel.IMPORTANT, "jobGrade", "departmentSupervisor", "directSupervisorUserId");
        govern(values, RequirementTier.CONDITIONAL, Responsibility.ORG_ADMIN, DueStage.PRE_ENTRY,
                RiskLevel.IMPORTANT, "deptLevel1Name", "deptLevel2Name", "deptLevel3Name", "storeName");

        govern(values, RequirementTier.REQUIRED, Responsibility.CONTRACT_SOCIAL, DueStage.POST_ENTRY_7D,
                RiskLevel.IMPORTANT, "socialType");
        govern(values, RequirementTier.CONDITIONAL, Responsibility.CONTRACT_SOCIAL, DueStage.POST_ENTRY_7D,
                RiskLevel.IMPORTANT, "legalEntity");
        govern(values, RequirementTier.CONDITIONAL, Responsibility.CONTRACT_SOCIAL, DueStage.POST_ENTRY_7D,
                RiskLevel.IMPORTANT, "contractStartDate", "contractEndDate", "contractType",
                "socialSecurityLocation", "housingFundLocation");
        govern(values, RequirementTier.CONDITIONAL, Responsibility.CONTRACT_SOCIAL, DueStage.LIFECYCLE,
                RiskLevel.NORMAL, "renewalCount");
        govern(values, RequirementTier.OPTIONAL, Responsibility.CONTRACT_SOCIAL, DueStage.NONE,
                RiskLevel.NORMAL, "householdType");
        govern(values, RequirementTier.CONDITIONAL, Responsibility.HR, DueStage.LIFECYCLE,
                RiskLevel.IMPORTANT, "salaryTotal", "baseSalary", "postSalary",
                "fieldAllowance", "performanceSalary");

        govern(values, RequirementTier.CONDITIONAL, Responsibility.SYSTEM, DueStage.LIFECYCLE,
                RiskLevel.NORMAL, "workYears", "companyYears", "contractTerm");
        govern(values, RequirementTier.OPTIONAL, Responsibility.SYSTEM, DueStage.NONE,
                RiskLevel.NORMAL, "remark");

        for (FieldDefinition value : values)
            if (value.requirementTier == null || value.responsibility == null
                    || value.dueStage == null || value.riskLevel == null)
                throw new IllegalStateException("Missing employee-field governance policy: " + value.key);
    }

    private static void govern(List<FieldDefinition> values, RequirementTier requirementTier,
            Responsibility responsibility, DueStage dueStage, RiskLevel riskLevel, String... keys)
    {
        for (String key : keys)
        {
            FieldDefinition field = byKey(values, key);
            if (field.requirementTier != null)
                throw new IllegalStateException("Duplicate employee-field governance policy: " + key);
            field.requirementTier = requirementTier;
            field.responsibility = responsibility;
            field.dueStage = dueStage;
            field.riskLevel = riskLevel;
        }
    }

    public Object read(SysUser user, FieldDefinition field)
    {
        if (user == null || field == null) return null;
        Object bean = field.storageOwner == StorageOwner.SYS_USER ? user : user.getProfile();
        if (bean == null) return null;
        return new BeanWrapperImpl(bean).getPropertyValue(field.propertyName);
    }

    public void write(SysUser user, FieldDefinition field, Object value)
    {
        if (field.storageOwner == StorageOwner.DERIVED) throw new IllegalArgumentException("Derived field is read-only");
        Object bean = field.storageOwner == StorageOwner.SYS_USER ? user : profile(user);
        BeanWrapper wrapper = new BeanWrapperImpl(bean);
        Class<?> targetType = wrapper.getPropertyType(field.propertyName);
        wrapper.setPropertyValue(field.propertyName, convert(value, targetType));
    }

    private SysUserProfile profile(SysUser user)
    {
        if (user.getProfile() == null)
        {
            SysUserProfile profile = new SysUserProfile();
            profile.setUserId(user.getUserId());
            user.setProfile(profile);
        }
        return user.getProfile();
    }

    private Object convert(Object value, Class<?> targetType)
    {
        if (value == null || targetType == null || targetType.isInstance(value)) return value;
        String text = String.valueOf(value).trim();
        if (java.util.Date.class.isAssignableFrom(targetType)) return Date.valueOf(LocalDate.parse(text));
        if (targetType == Long.class) return Long.valueOf(text);
        if (targetType == Integer.class) return Integer.valueOf(text);
        return text;
    }

    public static final class FieldDefinition
    {
        private final String key;
        private final String label;
        private final StorageOwner storageOwner;
        private final String propertyName;
        private final String columnName;
        private final boolean editable;
        private boolean profileCompleteness;
        private final String completenessGroup;
        private final MaskingClass maskingClass;
        private String onboardingPropertyName;
        private RequirementTier requirementTier;
        private Responsibility responsibility;
        private DueStage dueStage;
        private RiskLevel riskLevel;

        private FieldDefinition(String key, String label, StorageOwner storageOwner, String propertyName,
                String columnName, boolean editable, boolean profileCompleteness, String completenessGroup,
                MaskingClass maskingClass)
        {
            this.key = key; this.label = label; this.storageOwner = storageOwner; this.propertyName = propertyName;
            this.columnName = columnName; this.editable = editable; this.profileCompleteness = profileCompleteness;
            this.completenessGroup = completenessGroup; this.maskingClass = maskingClass;
        }
        public String getKey() { return key; }
        public String getLabel() { return label; }
        public StorageOwner getStorageOwner() { return storageOwner; }
        public String getPropertyName() { return propertyName; }
        public String getColumnName() { return columnName; }
        public boolean isEditable() { return editable; }
        public boolean isProfileCompleteness() { return profileCompleteness; }
        public String getCompletenessGroup() { return completenessGroup; }
        public MaskingClass getMaskingClass() { return maskingClass; }
        public String getOnboardingPropertyName() { return onboardingPropertyName; }
        public RequirementTier getRequirementTier() { return requirementTier; }
        public Responsibility getResponsibility() { return responsibility; }
        public DueStage getDueStage() { return dueStage; }
        public RiskLevel getRiskLevel() { return riskLevel; }
    }
}
