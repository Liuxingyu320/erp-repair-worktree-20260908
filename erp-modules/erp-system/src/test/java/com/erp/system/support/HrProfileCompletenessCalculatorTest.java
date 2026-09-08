package com.erp.system.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Date;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Component;
import com.erp.system.api.domain.SysUserProfile;
import com.erp.system.domain.hr.HrCompletenessResult;

@DisplayName("人事资料完整度计算")
class HrProfileCompletenessCalculatorTest
{
    private final HrProfileCompletenessCalculator calculator = new HrProfileCompletenessCalculator();

    @Test
    @DisplayName("缺少入职必填项时应返回缺失字段并禁止确认入职")
    void shouldReturnMissingRequiredFields()
    {
        SysUserProfile profile = new SysUserProfile();
        profile.setEmployeeName("张三");
        profile.setEmployeeStatus("待入职");
        profile.setEmployeeCategory("全职");
        profile.setContractType("劳动合同");

        HrCompletenessResult result = calculator.calculate(profile);

        assertThat(result.getOnboardingComplete()).isFalse();
        assertThat(result.getMissingOnboardingFields()).contains("手机号", "证件号码", "银行卡号", "社保类型");
        assertThat(result.getOnboardingCompleteness()).isLessThan(100);
    }

    @Test
    @DisplayName("入职必填项齐全时允许确认入职")
    void shouldMarkOnboardingComplete()
    {
        SysUserProfile profile = completeProfile();

        HrCompletenessResult result = calculator.calculate(profile);

        assertThat(result.getOnboardingComplete()).isTrue();
        assertThat(result.getMissingOnboardingFields()).isEmpty();
        assertThat(result.getOnboardingCompleteness()).isEqualTo(100);
    }

    @Test
    @DisplayName("完整度计算器应作为Spring组件供服务层注入")
    void shouldBeSpringComponent()
    {
        assertThat(HrProfileCompletenessCalculator.class.getAnnotation(Component.class)).isNotNull();
    }

    private static SysUserProfile completeProfile()
    {
        SysUserProfile profile = new SysUserProfile();
        profile.setEmployeeName("李四");
        profile.setEmployeeNo("E1001");
        profile.setCompanyName("金英灵韵");
        profile.setDeptLevel1Name("北京区域");
        profile.setDeptLevel2Name("北京区域运营");
        profile.setDeptLevel3Name("北京区域运营");
        profile.setStoreName("北京柏悦");
        profile.setPositionNames("茶艺师");
        profile.setJobGrade("6");
        profile.setPhoneNumber("13800000000");
        profile.setDepartmentSupervisor("店长");
        profile.setDirectSupervisor("店长");
        profile.setEmployeeStatus("待入职");
        profile.setEmployeeCategory("全职");
        profile.setSex("1");
        profile.setBirthDate(new Date());
        profile.setIdType("居民身份证");
        profile.setIdNumber("110101199001011234");
        profile.setRegisteredResidence("北京市");
        profile.setCurrentAddress("北京市朝阳区");
        profile.setMaritalStatus("未婚");
        profile.setEthnicity("汉族");
        profile.setEmergencyContact("王五");
        profile.setEmergencyContactRelation("父母");
        profile.setEmergencyContactPhone("13900000000");
        profile.setEntryDate(new Date());
        profile.setWorkLocation("北京");
        profile.setWorkCityLevel("一线");
        profile.setBankName("中国银行");
        profile.setBankAccount("6222000000000000");
        profile.setContractType("劳动合同");
        profile.setSocialSecurityType("有");
        profile.setLegalEntity("北京金英灵韵茶业有限公司");
        return profile;
    }
}
