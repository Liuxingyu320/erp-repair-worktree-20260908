package com.erp.system.support;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import org.springframework.stereotype.Component;
import com.erp.system.api.domain.SysUserProfile;
import com.erp.system.domain.hr.HrCompletenessResult;

/**
 * 人事档案完整度计算。
 */
@Component
public class HrProfileCompletenessCalculator
{
    private static final List<RequiredField> ONBOARDING_FIELDS = List.of(
            field("姓名", p -> has(p.getEmployeeName())),
            field("工号", p -> has(p.getEmployeeNo())),
            field("所属公司", p -> has(p.getCompanyName())),
            field("1级部门", p -> has(p.getDeptLevel1Name())),
            field("2级部门", p -> has(p.getDeptLevel2Name())),
            field("3级部门", p -> has(p.getDeptLevel3Name())),
            field("4级门店", p -> has(p.getStoreName())),
            field("职位", p -> has(p.getPositionNames())),
            field("职级", p -> has(p.getJobGrade())),
            field("手机号", p -> has(p.getPhoneNumber())),
            field("部门主管", p -> has(p.getDepartmentSupervisor())),
            field("直属主管", p -> has(p.getDirectSupervisor())),
            field("员工状态", p -> has(p.getEmployeeStatus())),
            field("人员类别", p -> has(p.getEmployeeCategory())),
            field("性别", p -> has(p.getSex())),
            field("出生日期", p -> p.getBirthDate() != null),
            field("证件类型", p -> has(p.getIdType())),
            field("证件号码", p -> has(p.getIdNumber())),
            field("户籍地址", p -> has(p.getRegisteredResidence())),
            field("现居住地址", p -> has(p.getCurrentAddress())),
            field("婚姻状况", p -> has(p.getMaritalStatus())),
            field("民族", p -> has(p.getEthnicity())),
            field("紧急联系人", p -> has(p.getEmergencyContact())),
            field("与紧急联系人关系", p -> has(p.getEmergencyContactRelation())),
            field("紧急联系人电话", p -> has(p.getEmergencyContactPhone())),
            field("入职日期", p -> p.getEntryDate() != null),
            field("入职岗位", p -> has(p.getPositionNames())),
            field("岗位职级", p -> has(p.getJobGrade())),
            field("工作所在地", p -> has(p.getWorkLocation())),
            field("工作所在城市级别", p -> has(p.getWorkCityLevel())),
            field("开户银行", p -> has(p.getBankName())),
            field("银行卡号", p -> has(p.getBankAccount())),
            field("合同类型", p -> has(p.getContractType())),
            field("社保类型", p -> has(p.getSocialSecurityType())),
            field("法人单位", p -> has(p.getLegalEntity()))
    );

    public HrCompletenessResult calculate(SysUserProfile profile)
    {
        HrCompletenessResult result = new HrCompletenessResult();
        List<String> missing = missingFields(profile, ONBOARDING_FIELDS);
        result.setMissingOnboardingFields(missing);
        result.setOnboardingCompleteness(percent(ONBOARDING_FIELDS.size() - missing.size(), ONBOARDING_FIELDS.size()));
        result.setOnboardingComplete(missing.isEmpty());
        result.setMissingProfileFields(new ArrayList<>(missing));
        result.setProfileCompleteness(result.getOnboardingCompleteness());
        return result;
    }

    private static RequiredField field(String label, Predicate<SysUserProfile> present)
    {
        return new RequiredField(label, present);
    }

    private static List<String> missingFields(SysUserProfile profile, List<RequiredField> fields)
    {
        List<String> missing = new ArrayList<>();
        for (RequiredField field : fields)
        {
            if (!field.present.test(profile))
            {
                missing.add(field.label);
            }
        }
        return missing;
    }

    private static int percent(int present, int total)
    {
        return total == 0 ? 100 : Math.round((present * 100.0f) / total);
    }

    private static boolean has(String value)
    {
        return value != null && !value.trim().isEmpty();
    }

    private static class RequiredField
    {
        private final String label;
        private final Predicate<SysUserProfile> present;

        private RequiredField(String label, Predicate<SysUserProfile> present)
        {
            this.label = label;
            this.present = present;
        }
    }
}
