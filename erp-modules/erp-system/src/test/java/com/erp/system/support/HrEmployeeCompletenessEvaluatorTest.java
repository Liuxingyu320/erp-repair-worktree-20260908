package com.erp.system.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.erp.system.api.domain.SysDept;
import com.erp.system.api.domain.SysUser;
import com.erp.system.api.domain.SysUserProfile;
import org.junit.jupiter.api.Test;

class HrEmployeeCompletenessEvaluatorTest
{
    private final HrEmployeeFieldRegistry registry = new HrEmployeeFieldRegistry();
    private final HrEmployeeCompletenessEvaluator evaluator = new HrEmployeeCompletenessEvaluator(registry);

    @Test
    void exposesTransparentCountsAndExcludesOnlyNonApplicableFields()
    {
        SysUser user = employee("正式");

        HrEmployeeCompletenessSnapshot result = evaluator.evaluate(user);

        long tracked = registry.getFields().stream()
                .filter(HrEmployeeFieldRegistry.FieldDefinition::isProfileCompleteness).count();
        assertThat(result.getTrackedFieldCount()).isEqualTo(tracked);
        assertThat(result.getApplicableFieldCount() + result.getNotApplicableFieldCount()).isEqualTo(tracked);
        assertThat(result.getCompletedFieldCount() + result.getMissingFields().size())
                .isEqualTo(result.getApplicableFieldCount());
        assertThat(result.getMissingFields()).contains("email", "firstEducation", "bankAccount")
                .doesNotContain("leaveDate", "probationPeriod", "storeName");
    }

    @Test
    void trialAndDepartedLifecycleFieldsChangeTheApplicableDenominator()
    {
        HrEmployeeCompletenessSnapshot trial = evaluator.evaluate(employee("试用"));
        HrEmployeeCompletenessSnapshot departed = evaluator.evaluate(employee("离职"));

        assertThat(trial.getMissingFields()).contains("probationPeriod", "plannedRegularizationDate")
                .doesNotContain("actualRegularizationDate", "leaveDate");
        assertThat(departed.getMissingFields()).contains("actualRegularizationDate", "leaveDate")
                .doesNotContain("probationPeriod", "plannedRegularizationDate");
    }

    @Test
    void reportsBusinessRequiredAndCoverageMetricsWithoutBreakingLegacyCoverage()
    {
        HrEmployeeCompletenessSnapshot result = evaluator.evaluate(employee("正式"));

        assertThat(result.getCoveragePercent()).isEqualTo(result.getCompletionPercent());
        assertThat(result.getRequiredCompletionPercent()).isGreaterThan(result.getCoveragePercent());
        assertThat(result.getMissingFields()).contains("email", "firstEducation", "bankAccount");
        assertThat(result.getMissingRequiredFields()).contains("bankAccount")
                .doesNotContain("email", "firstEducation");
        assertThat(result.getMissingOptionalFields()).contains("email", "firstEducation");
        assertThat(result.getMissingByResponsibility()).containsKey("CONTRACT_SOCIAL");
        assertThat(result.getRequiredCompletedFieldCount() + result.getMissingRequiredFields().size())
                .isEqualTo(result.getRequiredApplicableFieldCount());
    }

    private SysUser employee(String status)
    {
        SysUser user = new SysUser();
        user.setUserId(7L);
        user.setDeptId(20L);
        user.setNickName("测试员工");
        user.setPhonenumber("13800138000");
        user.setSex("0");
        user.setPostNames("店员");
        SysDept dept = new SysDept();
        dept.setDeptId(20L);
        dept.setDeptName("测试门店");
        dept.setDeptType("STORE");
        dept.setStatus("0");
        dept.setLeader("主管");
        user.setDept(dept);
        SysUserProfile profile = new SysUserProfile();
        profile.setUserId(7L);
        profile.setEmployeeNo("E007");
        profile.setEmployeeStatus(status);
        profile.setEmployeeCategory("全职");
        profile.setEntryDate(new java.util.Date());
        user.setProfile(profile);
        return user;
    }
}
