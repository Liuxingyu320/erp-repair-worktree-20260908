package com.erp.system.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Date;

import org.junit.jupiter.api.Test;

import com.erp.system.domain.HrOnboarding;
import com.erp.system.domain.vo.HrOnboardingDetailVo;
import com.erp.system.domain.vo.HrOnboardingListVo;

class HrSensitiveFieldMaskerTest
{
    private final HrSensitiveFieldMasker masker = new HrSensitiveFieldMasker();

    @Test
    void masksNullEmptyShortAndNormalValuesDeterministically()
    {
        assertThat(masker.maskPhone(null)).isNull();
        assertThat(masker.maskPhone("")).isEmpty();
        assertThat(masker.maskPhone("123")).isEqualTo("***");
        assertThat(masker.maskPhone("13800138000")).isEqualTo("138****8000");

        assertThat(masker.maskIdNumber("12")).isEqualTo("**");
        assertThat(masker.maskIdNumber("350000199001010000")).isEqualTo("3500**********0000");
        assertThat(masker.maskBankAccount("1234")).isEqualTo("****");
        assertThat(masker.maskBankAccount("6222020200001234567")).isEqualTo("6222***********4567");
        assertThat(masker.maskAddress("厦")).isEqualTo("*");
        assertThat(masker.maskAddress("福建省厦门市思明区")).isEqualTo("福建*******");
        assertThat(masker.maskAddress("中😀测试")).isEqualTo("中😀**");
    }

    @Test
    void listAndDetailDtosExposeOnlyExplicitMaskedSensitiveProperties()
    {
        HrOnboarding source = source();

        HrOnboardingListVo list = masker.toListVo(source);
        HrOnboardingDetailVo detail = masker.toMaskedDetailVo(source);

        assertThat(list.getPhoneNumberMasked()).isEqualTo("138****8000");
        assertThat(detail.getIdNumberMasked()).isEqualTo("3500**********0000");
        assertThat(detail.getBankAccountMasked()).isEqualTo("6222***********4567");
        assertThat(detail.getRegisteredResidenceMasked()).isEqualTo("福建*******");
        assertThat(detail.getCurrentAddressMasked()).isEqualTo("福建*******");
        assertThat(list.getOwnerName()).isEqualTo("招聘负责人");
        assertThat(detail.getOwnerName()).isEqualTo("招聘负责人");
        assertThat(detail.getDirectSupervisorUserId()).isEqualTo(66L);
        assertThat(detail.getJobGrade()).isEqualTo("P3");
        assertThat(detail.getSex()).isEqualTo("1");
        assertThat(detail.getIdType()).isEqualTo("ID_CARD");
        assertThat(detail.getEmergencyContact()).isEqualTo("家属");
        assertThat(detail.getContractType()).isEqualTo("FIXED");
        assertThat(detail.getRemark()).isEqualTo("入职备注");
        assertThat(detail.getPreferredConflictAction()).isEqualTo("BIND_EXISTING");
        assertThat(detail.getPreferredBindUserId()).isEqualTo(99L);

        assertThat(propertyNames(HrOnboardingListVo.class)).doesNotContain("phoneNumber", "idNumber", "bankAccount",
                "registeredResidence", "currentAddress");
        assertThat(propertyNames(HrOnboardingDetailVo.class)).doesNotContain("phoneNumber", "idNumber", "bankAccount",
                "registeredResidence", "currentAddress");
    }

    private HrOnboarding source()
    {
        HrOnboarding source = new HrOnboarding();
        source.setOnboardingId(1L);
        source.setEmployeeName("测试员工");
        source.setPhoneNumber("13800138000");
        source.setIdNumber("350000199001010000");
        source.setBankAccount("6222020200001234567");
        source.setRegisteredResidence("福建省泉州市鲤城区");
        source.setCurrentAddress("福建省厦门市思明区");
        source.setOwnerName("招聘负责人");
        source.setDirectSupervisorUserId(66L);
        source.setJobGrade("P3");
        source.setSex("1");
        source.setIdType("ID_CARD");
        source.setEmergencyContact("家属");
        source.setContractType("FIXED");
        source.setRemark("入职备注");
        source.setPreferredConflictAction("BIND_EXISTING");
        source.setPreferredBindUserId(99L);
        source.setExpectedEntryDate(new Date());
        return source;
    }

    private java.util.List<String> propertyNames(Class<?> type)
    {
        return Arrays.stream(type.getMethods())
                .map(Method::getName)
                .filter(name -> name.startsWith("get"))
                .map(name -> Character.toLowerCase(name.charAt(3)) + name.substring(4))
                .collect(java.util.stream.Collectors.toList());
    }
}
