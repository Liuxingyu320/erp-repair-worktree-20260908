package com.erp.system.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import com.erp.system.api.domain.SysUser;
import com.erp.system.api.domain.SysUserProfile;
import com.erp.system.domain.HrOnboarding;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.apache.ibatis.io.Resources;
import org.junit.jupiter.api.Test;

class HrEmployeeFieldRegistryTest
{
    private final HrEmployeeFieldRegistry registry = new HrEmployeeFieldRegistry();

    @Test
    void registryContainsApprovedMasterFieldsAndReviewedSigningFacts()
    {
        assertThat(registry.getFields()).hasSize(77);
        assertThat(registry.getFields()).extracting(HrEmployeeFieldRegistry.FieldDefinition::getLabel)
                .containsExactly("姓名", "邮箱", "手机号", "性别", "备注", "工号", "所属公司", "1级部门",
                        "2级部门", "3级部门", "4级门店", "职位", "职级", "部门主管", "直属主管", "员工状态",
                        "人员类别", "出生日期", "证件类型", "证件号码", "血型", "户口所在地", "现居住地址",
                        "当前在校状态", "当前在读学校", "当前退休状态", "主要劳动收入起始月",
                        "第一学历", "第一学位", "毕业时间", "第一学历毕业学校", "第一学历所学专业", "最高学历",
                        "最高学位", "最高学历毕业时间", "最高学历毕业学校", "最高学历所学专业", "政治面貌",
                        "婚姻状况", "国籍", "是否外籍", "民族", "健康状况", "紧急联系人", "与紧急联系人关系",
                        "紧急联系人电话", "招聘渠道", "办公电话", "参加工作时间", "工龄", "入职时间", "试用期",
                        "计划转正日期", "实际转正日期", "司龄", "本岗位任职日期", "现合同起始日", "现合同到期日",
                        "合同类型", "合同期限", "续签次数", "工作所在地", "工作所在城市级别", "考勤方式", "户口性质",
                        "社保类型", "社保缴纳地", "公积金缴纳地", "离职时间", "开户银行", "银行卡号", "法人单位", "合同综合工资", "底薪", "综合岗位津贴", "综合驻外补贴", "月度绩效津贴");
        assertThat(registry.getFields()).allSatisfy(field -> {
            assertThat(field.getKey()).isNotBlank();
            assertThat(field.getStorageOwner()).isNotNull();
            assertThat(field.getCompletenessGroup()).isNotBlank();
            assertThat(field.getMaskingClass()).isNotNull();
            assertThat(field.getRequirementTier()).isNotNull();
            assertThat(field.getResponsibility()).isNotNull();
            assertThat(field.getDueStage()).isNotNull();
            assertThat(field.getRiskLevel()).isNotNull();
        });
        assertThat(registry.getFields().stream().map(HrEmployeeFieldRegistry.FieldDefinition::getKey))
                .doesNotHaveDuplicates();
        assertThat(registry.getFields().stream().map(HrEmployeeFieldRegistry.FieldDefinition::getLabel))
                .doesNotHaveDuplicates();
        assertThat(registry.getByKey("contractType")).isNotNull();
        assertThat(registry.getByKey("socialType")).isNotNull();
    }

    @Test
    void governancePolicySeparatesRequiredConditionalAndOptionalFields()
    {
        assertThat(registry.getByKey("employeeName").getRequirementTier())
                .isEqualTo(HrEmployeeFieldRegistry.RequirementTier.REQUIRED);
        assertThat(registry.getByKey("contractEndDate").getRequirementTier())
                .isEqualTo(HrEmployeeFieldRegistry.RequirementTier.CONDITIONAL);
        assertThat(registry.getByKey("highestEducation").getRequirementTier())
                .isEqualTo(HrEmployeeFieldRegistry.RequirementTier.OPTIONAL);
        assertThat(registry.getByKey("departmentSupervisor").getResponsibility())
                .isEqualTo(HrEmployeeFieldRegistry.Responsibility.ORG_ADMIN);
        assertThat(registry.getByKey("bankAccount").getDueStage())
                .isEqualTo(HrEmployeeFieldRegistry.DueStage.POST_ENTRY_7D);
        assertThat(Arrays.asList("birthDate", "maritalStatus", "ethnicity", "emergencyContact",
                "emergencyContactRelation", "emergencyContactPhone", "workCityLevel"))
                .allSatisfy(key -> assertThat(registry.getByKey(key).getRequirementTier())
                        .as(key).isEqualTo(HrEmployeeFieldRegistry.RequirementTier.OPTIONAL));
        assertThat(registry.getByKey("legalEntity").getDueStage())
                .isEqualTo(HrEmployeeFieldRegistry.DueStage.POST_ENTRY_7D);
        assertThat(registry.getByKey("legalEntity").getRiskLevel())
                .isEqualTo(HrEmployeeFieldRegistry.RiskLevel.IMPORTANT);
    }

    @Test
    void noDerivedFieldIsWritableAndSensitiveFieldsHaveAnExplicitMaskingClass()
    {
        assertThat(registry.getFields())
                .filteredOn(f -> f.getStorageOwner() == HrEmployeeFieldRegistry.StorageOwner.DERIVED)
                .allSatisfy(field -> assertThat(field.isEditable()).isFalse());
        assertThat(registry.getByKey("directSupervisorUserId").getStorageOwner())
                .isEqualTo(HrEmployeeFieldRegistry.StorageOwner.RELATION);
        assertThat(registry.getByKey("employeeNo").isEditable()).isFalse();
        assertThat(registry.getByKey("employeeStatus").isEditable()).isFalse();
        assertThat(registry.getByKey("studentStatus").isEditable()).isFalse();
        assertThat(registry.getByKey("retirementStatus").isEditable()).isFalse();
        assertThat(registry.getSensitiveRevealKeys()).containsExactlyInAnyOrder(
                "phoneNumber", "idNumber", "registeredResidence", "currentAddress",
                "emergencyContactPhone", "officePhone", "bankAccount",
                "salaryTotal", "baseSalary", "postSalary", "fieldAllowance", "performanceSalary");
    }

    @Test
    void everyWritableProfileFieldIsCoveredByBothMasterWritesAndConfirmationMapping() throws Exception
    {
        String profiles = resource("mapper/system/SysUserProfileMapper.xml");
        String insert = fragment(profiles, "<insert id=\"insertUserProfile\"", "</insert>");
        String update = fragment(profiles, "<update id=\"updateUserProfile\"", "</update>");
        String confirmation = fragment(profiles, "<update id=\"updateOnboardingProfile\"", "</update>");

        Set<String> writableColumns = registry.getFields().stream()
                .filter(HrEmployeeFieldRegistry.FieldDefinition::isEditable)
                .filter(f -> f.getStorageOwner() == HrEmployeeFieldRegistry.StorageOwner.SYS_USER_PROFILE
                        || f.getStorageOwner() == HrEmployeeFieldRegistry.StorageOwner.RELATION)
                .map(HrEmployeeFieldRegistry.FieldDefinition::getColumnName)
                .collect(Collectors.toSet());
        assertThat(insert).contains(writableColumns.toArray(String[]::new));
        assertThat(update).contains(writableColumns.toArray(String[]::new));

        Set<String> confirmationKeys = Arrays.stream(new String[] {
                "employeeNo", "jobGrade", "directSupervisorUserId", "employeeStatus", "employeeCategory",
                "birthDate", "idType", "idNumber", "registeredResidence", "currentAddress", "maritalStatus",
                "ethnicity", "emergencyContact", "emergencyContactRelation", "emergencyContactPhone", "entryDate",
                "probationPeriod", "plannedRegularizationDate", "currentPositionStartDate", "contractStartDate",
                "contractType", "workLocation", "workCityLevel", "socialType", "bankName", "bankAccount", "legalEntity"
        }).collect(Collectors.toSet());
        assertThat(confirmation).contains(confirmationKeys.stream().map(k -> registry.getByKey(k).getColumnName())
                .toArray(String[]::new));
    }

    @Test
    void declaredOnboardingMappingsCoverSystemUserAndProfileWithoutHardCodedProfileSubset()
    {
        HrOnboarding source=new HrOnboarding();
        source.setEmployeeName("映射姓名"); source.setPhoneNumber("13800138000");
        source.setSex("1"); source.setRemark("映射备注"); source.setJobGrade("P6");
        source.setIdNumber("350000199001010000");
        BeanWrapper sourceValues=new BeanWrapperImpl(source);
        for(var field:registry.getFields())
        {
            if(field.getOnboardingPropertyName()==null)continue;
            Class<?> type=sourceValues.getPropertyType(field.getOnboardingPropertyName());
            if(sourceValues.getPropertyValue(field.getOnboardingPropertyName())!=null)continue;
            Object value=type==Long.class?77L:type==java.util.Date.class?new java.util.Date(123456L):"映射值";
            sourceValues.setPropertyValue(field.getOnboardingPropertyName(),value);
        }
        SysUser target=new SysUser(); target.setProfile(new SysUserProfile());

        registry.copyDeclaredOnboardingMappings(source,target);

        assertThat(registry.getFields()).filteredOn(f->f.getOnboardingPropertyName()!=null)
                .anySatisfy(f->{assertThat(f.getStorageOwner()).isEqualTo(HrEmployeeFieldRegistry.StorageOwner.SYS_USER);
                    assertThat(f.getKey()).isEqualTo("remark");})
                .anySatisfy(f->{assertThat(f.getStorageOwner()).isEqualTo(HrEmployeeFieldRegistry.StorageOwner.SYS_USER_PROFILE);
                    assertThat(f.getKey()).isEqualTo("jobGrade");});
        assertThat(target.getNickName()).isEqualTo("映射姓名");
        assertThat(target.getPhonenumber()).isEqualTo("13800138000");
        assertThat(target.getSex()).isEqualTo("1");
        assertThat(target.getRemark()).isEqualTo("映射备注");
        assertThat(target.getProfile().getJobGrade()).isEqualTo("P6");
        assertThat(target.getProfile().getIdNumber()).isEqualTo("350000199001010000");
        assertThat(registry.getFields()).filteredOn(f->f.getOnboardingPropertyName()!=null)
                .allSatisfy(field->assertThat(registry.read(target,field))
                        .as(field.getKey()).isEqualTo(sourceValues.getPropertyValue(field.getOnboardingPropertyName())));
    }

    private String resource(String path) throws Exception
    {
        return new String(Resources.getResourceAsStream(path).readAllBytes(), StandardCharsets.UTF_8);
    }

    private String fragment(String source, String start, String end)
    {
        int from = source.indexOf(start);
        int to = source.indexOf(end, from);
        assertThat(from).isGreaterThanOrEqualTo(0);
        assertThat(to).isGreaterThan(from);
        return source.substring(from, to + end.length());
    }
}
