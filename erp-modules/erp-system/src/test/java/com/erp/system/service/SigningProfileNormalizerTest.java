package com.erp.system.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import com.erp.common.core.exception.ServiceException;
import com.erp.system.api.domain.SysUserProfile;

@DisplayName("签约档案写入归一化")
class SigningProfileNormalizerTest
{
    private final SigningProfileNormalizer normalizer = new SigningProfileNormalizer();

    @ParameterizedTest(name = "{0} -> {2} / {3}")
    @MethodSource("contractMappings")
    @DisplayName("旧合同值转换为稳定机器码")
    void shouldNormalizeLegacyContractValues(String legacyType, String existingTerm,
            String expectedType, String expectedTerm)
    {
        SysUserProfile profile = new SysUserProfile();
        profile.setContractType(legacyType);
        profile.setContractTerm(existingTerm);

        normalizer.normalize(profile);

        assertThat(profile.getContractType()).isEqualTo(expectedType);
        assertThat(profile.getContractTerm()).isEqualTo(expectedTerm);
    }

    private static Stream<Arguments> contractMappings()
    {
        return Stream.of(
                Arguments.of("固定期限劳动合同", null, "LABOR_CONTRACT", "FIXED_TERM"),
                Arguments.of("劳动合同", "", "LABOR_CONTRACT", "FIXED_TERM"),
                Arguments.of("无固定期限劳动合同", null, "LABOR_CONTRACT", "OPEN_ENDED"),
                Arguments.of("劳务协议", "2年", "SERVICE_CONTRACT", "2年"),
                Arguments.of("劳务合同", null, "SERVICE_CONTRACT", null),
                Arguments.of("实习协议", null, "INTERNSHIP_AGREEMENT", null),
                Arguments.of("外包合同", null, "OUTSOURCING_CONTRACT", null),
                Arguments.of("LABOR_CONTRACT", "FIXED_TERM", "LABOR_CONTRACT", "FIXED_TERM"));
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("socialMappings")
    @DisplayName("旧社保值转换为稳定机器码且不覆盖缴纳地")
    void shouldNormalizeLegacySocialValues(String legacyValue, String expectedValue)
    {
        SysUserProfile profile = new SysUserProfile();
        profile.setSocialType(legacyValue);
        profile.setSocialSecurityLocation("上海市");

        normalizer.normalize(profile);

        assertThat(profile.getSocialType()).isEqualTo(expectedValue);
        assertThat(profile.getSocialSecurityLocation()).isEqualTo("上海市");
    }

    private static Stream<Arguments> socialMappings()
    {
        return Stream.of(
                Arguments.of("本地社保", "SOCIAL_INSURED"),
                Arguments.of("异地社保", "SOCIAL_INSURED"),
                Arguments.of("有社保", "SOCIAL_INSURED"),
                Arguments.of("无需缴纳", "SOCIAL_UNINSURED"),
                Arguments.of("无社保", "SOCIAL_UNINSURED"),
                Arguments.of("劳务派遣", "DISPATCHED"),
                Arguments.of("待确认", "PENDING_CONFIRMATION"),
                Arguments.of("SOCIAL_INSURED", "SOCIAL_INSURED"));
    }

    @Test
    @DisplayName("空值保持空")
    void shouldPreserveEmptyValues()
    {
        SysUserProfile profile = new SysUserProfile();

        normalizer.normalize(profile);

        assertThat(profile.getContractType()).isNull();
        assertThat(profile.getContractTerm()).isNull();
        assertThat(profile.getSocialType()).isNull();
    }

    @Test
    @DisplayName("非空未知值必须明确拒绝")
    void shouldRejectUnknownValues()
    {
        SysUserProfile profile = new SysUserProfile();
        profile.setContractType("临时协议");

        assertThatThrownBy(() -> normalizer.normalize(profile))
                .isInstanceOf(ServiceException.class)
                .hasMessage("合同类型不受支持: 临时协议");

        profile.setContractType("LABOR_CONTRACT");
        profile.setSocialType("商业保险");
        assertThatThrownBy(() -> normalizer.normalize(profile))
                .isInstanceOf(ServiceException.class)
                .hasMessage("社保类型不受支持: 商业保险");
    }

    @Test
    void shouldNormalizeAndValidateEmployeeStatus()
    {
        SysUserProfile profile = new SysUserProfile();
        profile.setEmployeeStatus("在职");

        normalizer.normalize(profile);

        assertThat(profile.getEmployeeStatus()).isEqualTo("正式");
        profile.setEmployeeStatus("临时状态");
        assertThatThrownBy(() -> normalizer.normalize(profile))
                .isInstanceOf(ServiceException.class).hasMessageContaining("员工状态不受支持");
    }
}
