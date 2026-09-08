package com.erp.system.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

class HrOnboardingFieldRegistryTest
{
    private final HrOnboardingFieldRegistry registry = new HrOnboardingFieldRegistry();

    @Test
    void registryContainsExactlyThirtyFiveUniqueBusinessFieldsWithCompleteMetadata()
    {
        assertThat(registry.getFields()).hasSize(35);
        assertThat(registry.getFields().stream().map(HrOnboardingFieldRegistry.FieldRule::getLabel))
                .containsExactlyElementsOf(Arrays.asList("姓名", "工号", "所属公司", "1级部门", "2级部门", "3级部门",
                        "4级门店", "职位", "职级", "手机号", "部门主管", "直属主管", "员工状态", "人员类别",
                        "性别", "出生日期", "证件类型", "证件号码", "户口所在地", "现居住地址", "婚姻状况", "民族",
                        "紧急联系人", "与紧急联系人关系", "紧急联系人电话", "预计入职日期", "入职岗位", "岗位职级",
                        "工作所在地", "工作所在城市级别", "开户银行", "银行卡号", "合同类型", "社保类型", "法人单位"));
        assertThat(registry.getFields()).allSatisfy(field -> {
            assertThat(field.getKey()).isNotBlank();
            assertThat(field.getLabel()).isNotBlank();
            assertThat(field.getStorageOwner()).isNotNull();
            assertThat(field.getMaskingClass()).isNotNull();
            assertThat(field.getCompletenessGroup()).isNotBlank();
        });
    }

    @Test
    void acceptedAliasesResolveToCanonicalFieldsWithoutIncreasingBusinessFieldCount()
    {
        assertThat(registry.resolveHeader("入职日期").getKey()).isEqualTo("expectedEntryDate");
        assertThat(registry.resolveHeader("入职岗位").getKey()).isEqualTo("positionName");
        assertThat(registry.resolveHeader("岗位职级").getKey()).isEqualTo("jobGrade");
        assertThat(registry.resolveHeader("户籍地址").getKey()).isEqualTo("registeredResidence");
        assertThat(registry.resolveHeader("预计入职日期").getLabel()).isEqualTo("预计入职日期");
        assertThat(registry.resolveHeader("入职日期").getLabel()).isEqualTo("预计入职日期");
        assertThat(registry.resolveHeader("户口所在地").getLabel()).isEqualTo("户口所在地");
        assertThat(registry.resolveHeader("户籍地址").getLabel()).isEqualTo("户口所在地");
        assertThat(registry.resolveHeader("入职岗位")).isSameAs(registry.getByKey("positionName"));
        assertThat(registry.resolveHeader("岗位职级")).isSameAs(registry.getByKey("jobGrade"));
        assertThat(registry.getFields()).allSatisfy(field ->
                assertThat(registry.resolveHeader(field.getLabel())).isSameAs(registry.getByKey(field.getKey())));

        Set<String> canonicalKeys = registry.getAcceptedHeaders().stream()
                .map(header -> registry.resolveHeader(header).getKey())
                .collect(Collectors.toSet());
        assertThat(canonicalKeys).hasSize(33);
        assertThat(registry.getBusinessFields()).hasSize(33);
        assertThat(registry.getFields().stream().distinct()).hasSize(35);
    }

    @Test
    void profileCompletenessMetadataUsesOnlyCurrentlyReadableUniversalFields()
    {
        assertThat(registry.getByKey("employeeNo").getStorageOwner())
                .isEqualTo(HrOnboardingFieldRegistry.StorageOwner.SYS_USER_PROFILE);
        assertThat(registry.getByKey("employeeStatus").getStorageOwner())
                .isEqualTo(HrOnboardingFieldRegistry.StorageOwner.SYS_USER_PROFILE);
        assertThat(registry.getByKey("employeeNo").isProfileCompleteness()).isTrue();
        assertThat(registry.getByKey("bankName").isProfileCompleteness()).isTrue();
        assertThat(registry.getByKey("bankAccount").isProfileCompleteness()).isTrue();
        assertThat(registry.getByKey("contractType").isProfileCompleteness()).isTrue();
        assertThat(registry.getByKey("socialType").isProfileCompleteness()).isTrue();
        assertThat(registry.getByKey("companyName").isProfileCompleteness()).isFalse();
        assertThat(registry.getByKey("deptLevel1Name").isProfileCompleteness()).isFalse();
        assertThat(registry.getByKey("storeName").isProfileCompleteness()).isFalse();
        assertThat(registry.getByKey("positionName").isProfileCompleteness()).isFalse();
        assertThat(registry.getByKey("departmentSupervisor").isProfileCompleteness()).isFalse();
        assertThat(registry.getByKey("directSupervisorUserId").isProfileCompleteness()).isFalse();
    }
}
