package com.erp.system.api.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.erp.common.core.annotation.Excel;
import com.erp.common.core.annotation.Excels;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("用户员工档案签约字段契约")
class SysUserProfileContractTest
{
    private static final String[] SALARY_FIELDS = {
            "baseSalary", "postSalary", "fieldAllowance", "performanceSalary",
            "salaryTotal", "salaryVersion"
    };

    @Test
    @DisplayName("员工档案提供合同类型和社保类型访问器")
    void shouldExposeContractAndSocialTypeFields() throws Exception
    {
        SysUserProfile profile = new SysUserProfile();

        Method setContractType = SysUserProfile.class.getMethod("setContractType", String.class);
        Method getContractType = SysUserProfile.class.getMethod("getContractType");
        Method setSocialType = SysUserProfile.class.getMethod("setSocialType", String.class);
        Method getSocialType = SysUserProfile.class.getMethod("getSocialType");

        setContractType.invoke(profile, "劳动合同");
        setSocialType.invoke(profile, "有社保");

        assertThat(getContractType.invoke(profile)).isEqualTo("劳动合同");
        assertThat(getSocialType.invoke(profile)).isEqualTo("有社保");
    }

    @Test
    @DisplayName("用户导入导出包含合同类型和社保类型")
    void shouldExposeContractAndSocialTypeInExcelMetadata() throws Exception
    {
        Excels excels = SysUser.class.getDeclaredField("profile").getAnnotation(Excels.class);
        Map<String, String> excelFields = Arrays.stream(excels.value())
                .collect(Collectors.toMap(Excel::name, Excel::targetAttr));

        assertThat(excelFields)
                .containsEntry("合同类型", "contractType")
                .containsEntry("社保类型", "socialType");
    }

    @Test
    @DisplayName("普通用户和员工档案JSON不输出签约薪资")
    void shouldHideSigningSalaryFromOrdinaryJson() throws Exception
    {
        SysUserProfile profile = new SysUserProfile();
        profile.setBaseSalary(new BigDecimal("5000.00"));
        profile.setPostSalary(new BigDecimal("2000.00"));
        profile.setFieldAllowance(new BigDecimal("300.00"));
        profile.setPerformanceSalary(new BigDecimal("700.00"));
        profile.setSalaryTotal(new BigDecimal("8000.00"));
        profile.setSalaryVersion("2026-V1");

        SysUser user = new SysUser();
        user.setProfile(profile);

        ObjectMapper objectMapper = new ObjectMapper();
        assertSalaryFieldsAbsent(objectMapper.valueToTree(profile));
        assertSalaryFieldsAbsent(objectMapper.valueToTree(user).path("profile"));
    }

    private static void assertSalaryFieldsAbsent(JsonNode json)
    {
        for (String salaryField : SALARY_FIELDS)
        {
            assertThat(json.has(salaryField)).as(salaryField).isFalse();
        }
    }
}
