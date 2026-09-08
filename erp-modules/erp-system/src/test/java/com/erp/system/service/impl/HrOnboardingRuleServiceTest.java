package com.erp.system.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import com.erp.system.api.domain.SysUser;
import com.erp.system.api.domain.SysUserProfile;
import com.erp.system.domain.HrOnboarding;
import com.erp.system.domain.HrOnboardingPositionConfig;
import com.erp.system.domain.vo.HrOnboardingCompletionVo;
import com.erp.system.service.ISysConfigService;
import com.erp.system.support.HrOnboardingFieldRegistry;

class HrOnboardingRuleServiceTest
{
    private static final String DUE_DAYS_KEY = "hr.onboarding.post_entry_due_days";
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final LocalDate TODAY = LocalDate.of(2026, 7, 11);

    private final ISysConfigService configService = mock(ISysConfigService.class);
    private final HrOnboardingFieldRegistry registry = new HrOnboardingFieldRegistry();
    private final HrOnboardingRuleService service = serviceAt(TODAY);

    @Test
    void ruleServiceSupportsInjectedConfigurationAndClock()
    {
        assertThat(HrOnboardingRuleService.class.getDeclaredConstructors()).anySatisfy(constructor ->
                assertThat(constructor.getParameterTypes()).containsExactly(
                        HrOnboardingFieldRegistry.class, ISysConfigService.class, Clock.class));
    }

    @Test
    void draftRequiresOnlySevenCreateFields()
    {
        HrOnboarding item = new HrOnboarding();
        item.setEmployeeName("测试员工");

        HrOnboardingCompletionVo result = service.evaluateCreate(item);

        assertThat(result.getMissingFields()).containsExactlyInAnyOrder(
                "phoneNumber", "expectedEntryDate", "targetDeptId", "targetPostId",
                "employeeCategory", "ownerUserId");
        assertThat(result.getPercentage()).isEqualTo(14);
        assertThat(result.getAllowedActions()).containsExactly("EDIT", "CANCEL");
    }

    @Test
    void readyRequiresOnlyCoreIdentityOrganizationAndEmploymentFields()
    {
        HrOnboarding item = new HrOnboarding();
        item.setEmployeeName("测试员工");

        HrOnboardingCompletionVo result = service.evaluateReady(item);

        assertThat(result.getMissingFields()).contains("phoneNumber", "expectedEntryDate",
                "targetDeptId", "targetPostId", "employeeCategory", "ownerUserId",
                "sex", "idType", "idNumber", "targetStoreId", "jobGrade",
                "directSupervisorUserId", "registeredResidence", "currentAddress", "workLocation");
        assertThat(result.getMissingFields()).doesNotContain(
                "birthDate", "maritalStatus", "ethnicity", "emergencyContact",
                "emergencyContactRelation", "emergencyContactPhone", "workCityLevel", "legalEntity");
        assertThat(result.getMissingLabels()).contains("手机号", "性别", "预计入职日期", "户口所在地");
        assertThat(result.getGroupedMissingFields()).containsKeys("BASIC", "ORGANIZATION", "IDENTITY", "EMPLOYMENT")
                .doesNotContainKey("CONTACT");
    }

    @Test
    void readyCompletionExposesCompletedAndRequiredFieldCounts()
    {
        HrOnboarding complete = completeReady();

        HrOnboardingCompletionVo result = service.evaluateReady(complete);

        assertThat(result.getRequiredFieldCount()).isEqualTo(16);
        assertThat(result.getCompletedFieldCount()).isEqualTo(16);
        assertThat(result.getPercentage()).isEqualTo(100);

        complete.setWorkLocation(null);
        HrOnboardingCompletionVo partial = service.evaluateReady(complete);
        assertThat(partial.getRequiredFieldCount()).isEqualTo(16);
        assertThat(partial.getCompletedFieldCount()).isEqualTo(15);
    }

    @Test
    void eightSupplementalFieldsDoNotBlockReadyOrConfirm()
    {
        HrOnboarding item = completeReady();
        item.setBirthDate(null);
        item.setMaritalStatus(null);
        item.setEthnicity(null);
        item.setEmergencyContact(null);
        item.setEmergencyContactRelation(null);
        item.setEmergencyContactPhone(null);
        item.setWorkCityLevel(null);
        item.setLegalEntity(null);

        HrOnboardingCompletionVo ready = service.evaluateReady(item);
        assertThat(ready.getMissingFields()).doesNotContain(
                "birthDate", "maritalStatus", "ethnicity", "emergencyContact",
                "emergencyContactRelation", "emergencyContactPhone", "workCityLevel", "legalEntity");
        assertThat(ready.getAllowedActions()).contains("MARK_READY");

        item.setStatus(HrOnboarding.STATUS_READY);
        item.setActualEntryDate(new Date());
        HrOnboardingCompletionVo confirm = service.evaluateConfirm(item,
                config("OPTIONAL", "OPTIONAL", "OPTIONAL", "0"));
        assertThat(confirm.getAllowedActions()).contains("CONFIRM");
    }

    @Test
    void readyBlocksWhenSourceIdsHaveNoDerivedOrganizationOrPositionResults()
    {
        HrOnboarding item = completeReady();
        clearDerivedResults(item);

        HrOnboardingCompletionVo result = service.evaluateReady(item);

        assertThat(result.getMissingFields()).isEmpty();
        assertThat(result.getBlockingCodes()).containsExactlyInAnyOrder(
                "DERIVED_COMPANY_MISSING", "DERIVED_STORE_MISSING", "DERIVED_POSITION_MISSING");
        assertThat(result.getAllowedActions()).doesNotContain("MARK_READY");
    }

    @Test
    void confirmBlocksWhenSourceIdsHaveNoDerivedOrganizationOrPositionResults()
    {
        HrOnboarding item = completeReady();
        item.setStatus(HrOnboarding.STATUS_READY);
        item.setActualEntryDate(new Date());
        clearDerivedResults(item);

        HrOnboardingCompletionVo result = service.evaluateConfirm(item,
                config("OPTIONAL", "OPTIONAL", "OPTIONAL", "0"));

        assertThat(result.getMissingFields()).isEmpty();
        assertThat(result.getBlockingCodes()).contains(
                "DERIVED_COMPANY_MISSING", "DERIVED_STORE_MISSING", "DERIVED_POSITION_MISSING");
        assertThat(result.getAllowedActions()).doesNotContain("CONFIRM");
    }

    @Test
    void companyDirectStoreWithoutDepartmentLayersCanBecomeReadyAndConfirmed()
    {
        HrOnboarding item = completeReady();
        clearDepartmentLayer(item);

        HrOnboardingCompletionVo ready = service.evaluateReady(item);
        assertThat(ready.getBlockingCodes()).isEmpty();
        assertThat(ready.getAllowedActions()).contains("MARK_READY");

        item.setStatus(HrOnboarding.STATUS_READY);
        item.setActualEntryDate(new Date());
        HrOnboardingCompletionVo confirm = service.evaluateConfirm(item,
                config("OPTIONAL", "OPTIONAL", "OPTIONAL", "0"));
        assertThat(confirm.getBlockingCodes()).isEmpty();
        assertThat(confirm.getAllowedActions()).contains("CONFIRM");
    }

    @Test
    void departmentLayerWithoutSupervisorBlocksReady()
    {
        HrOnboarding item = completeReady();
        item.setDepartmentSupervisor(null);

        HrOnboardingCompletionVo result = service.evaluateReady(item);

        assertThat(result.getBlockingCodes()).containsExactly("DERIVED_DEPARTMENT_SUPERVISOR_MISSING");
        assertThat(result.getAllowedActions()).doesNotContain("MARK_READY");
    }

    @Test
    void confirmRequiresReadyStatusAndActualEntryDate()
    {
        HrOnboarding item = completeReady();
        item.setStatus(HrOnboarding.STATUS_DRAFT);

        HrOnboardingCompletionVo result = service.evaluateConfirm(item, optionalConfig());

        assertThat(result.getBlockingCodes()).contains("STATUS_NOT_READY", "ACTUAL_ENTRY_DATE_REQUIRED");
        assertThat(result.getAllowedActions()).doesNotContain("CONFIRM");
    }

    @Test
    void conditionalModesAreExplicitAndMissingOrDisabledConfigIsANonBlockingRisk()
    {
        HrOnboarding item = completeReady();
        item.setStatus(HrOnboarding.STATUS_READY);
        item.setActualEntryDate(new Date());

        HrOnboardingCompletionVo required = service.evaluateConfirm(item, config("REQUIRED", "REQUIRED", "REQUIRED", "0"));
        assertThat(required.getMissingFields()).contains("contractType", "socialType", "probationPeriod");

        HrOnboardingCompletionVo notApplicable = service.evaluateConfirm(item,
                config("NOT_APPLICABLE", "NOT_APPLICABLE", "NOT_APPLICABLE", "0"));
        assertThat(notApplicable.getMissingFields()).doesNotContain("contractType", "socialType", "probationPeriod");

        HrOnboardingCompletionVo noConfig = service.evaluateConfirm(item, null);
        assertThat(noConfig.getBlockingCodes()).doesNotContain("POSITION_CONFIG_MISSING");
        assertThat(noConfig.getRiskCodes()).contains("ACCOUNT_CONFIGURATION_MISSING");

        HrOnboardingCompletionVo disabled = service.evaluateConfirm(item, optionalConfig());
        assertThat(disabled.getRiskCodes()).contains("ACCOUNT_CONFIGURATION_MISSING");
    }

    @Test
    void onlyStatusZeroMakesPositionConfigurationActive()
    {
        HrOnboarding item = completeReady();
        item.setStatus(HrOnboarding.STATUS_READY);
        item.setActualEntryDate(new Date());

        for (String status : Arrays.asList(null, "1", "2", "INVALID"))
        {
            HrOnboardingCompletionVo result = service.evaluateConfirm(item,
                    config("REQUIRED", "REQUIRED", "REQUIRED", status));
            assertThat(result.getRiskCodes()).as("status %s", status)
                    .contains("ACCOUNT_CONFIGURATION_MISSING");
            assertThat(result.getMissingFields()).as("status %s", status)
                    .doesNotContain("contractType", "socialType", "probationPeriod");
        }
    }

    @Test
    void allowedActionsAreChosenByBackendFromStatusAndCompleteness()
    {
        HrOnboarding draft = completeReady();
        draft.setStatus(HrOnboarding.STATUS_DRAFT);
        assertThat(service.evaluateReady(draft).getAllowedActions())
                .containsExactly("EDIT", "MARK_READY", "CANCEL");

        draft.setStatus(HrOnboarding.STATUS_READY);
        draft.setActualEntryDate(new Date());
        assertThat(service.evaluateConfirm(draft, config("OPTIONAL", "OPTIONAL", "OPTIONAL", "0")).getAllowedActions())
                .containsExactly("EDIT", "RETURN_TO_DRAFT", "CONFIRM", "CANCEL");

        draft.setStatus(HrOnboarding.STATUS_CANCELLED);
        assertThat(service.evaluateReady(draft).getAllowedActions()).containsExactly("RESTORE");
    }

    @Test
    void profileEvaluationUsesMappedRegistryFieldsAndReportsDeterministicGroupsAndPercentage()
    {
        SysUser user = new SysUser();
        user.setNickName("测试员工");
        user.setPhonenumber("13800138000");
        SysUserProfile profile = new SysUserProfile();
        user.setSex(null);
        user.setProfile(profile);

        HrOnboardingCompletionVo partial = service.evaluateProfile(user);
        assertThat(partial.getPercentage()).isGreaterThan(0).isLessThan(100);
        assertThat(partial.getMissingFields()).contains("sex", "birthDate", "idNumber");
        assertThat(partial.getMissingLabels()).contains("性别", "出生日期", "证件号码");
        assertThat(partial.getGroupedMissingFields()).containsKeys("BASIC", "IDENTITY");

        user.setSex("0");
        HrOnboardingCompletionVo oneMoreComplete = service.evaluateProfile(user);
        assertThat(oneMoreComplete.getPercentage()).isGreaterThan(partial.getPercentage());
        assertThat(oneMoreComplete.getMissingFields()).doesNotContain("sex");
    }

    @Test
    void profileEvaluationIncludesAllProfileFieldsFromTheOnboardingRegistry()
    {
        SysUser user = new SysUser();
        user.setNickName("测试员工");
        user.setPhonenumber("13800138000");
        SysUserProfile profile = new SysUserProfile();
        user.setProfile(profile);

        HrOnboardingCompletionVo missing = service.evaluateProfile(user);
        assertThat(missing.getMissingFields()).contains(
                "employeeNo", "bankName", "bankAccount", "contractType", "socialType");

        profile.setEmployeeNo("E0001");
        profile.setBankName("测试银行");
        profile.setBankAccount("6222020200001234567");
        profile.setContractType("劳动合同");
        profile.setSocialType("社保");

        HrOnboardingCompletionVo completed = service.evaluateProfile(user);
        assertThat(completed.getMissingFields()).doesNotContain(
                "employeeNo", "bankName", "bankAccount", "contractType", "socialType");
        assertThat(completed.getPercentage()).isGreaterThan(missing.getPercentage());
    }

    @Test
    void profileEvaluationExcludesDerivedAndRelationFieldsAndCanReachOneHundredPercent()
    {
        SysUser user = completeProfileUser();
        user.getProfile().setDirectSupervisor("旧主管姓名");

        HrOnboardingCompletionVo result = service.evaluateProfile(user);

        assertThat(result.getMissingFields()).doesNotContain(
                "companyName", "deptLevel1Name", "deptLevel2Name", "deptLevel3Name",
                "storeName", "positionName", "departmentSupervisor", "directSupervisorUserId");
        assertThat(result.getPercentage()).isEqualTo(100);
    }

    @Test
    void missingDueDaysConfigurationFallsBackToSevenNaturalDays()
    {
        HrOnboarding item = completeReady();
        item.setActualEntryDate(date(LocalDate.of(2026, 1, 1)));

        HrOnboardingCompletionVo result = service.evaluateReady(item);

        assertThat(result.getPostEntryDueDate()).isEqualTo(date(LocalDate.of(2026, 1, 8)));
        assertThat(result.getPostEntryOverdue()).isTrue();
    }

    @Test
    void configuredDueDaysOverrideTheSevenDayDefault()
    {
        when(configService.selectConfigByKey(DUE_DAYS_KEY)).thenReturn("9");
        HrOnboarding item = completeReady();
        item.setActualEntryDate(date(LocalDate.of(2026, 7, 1)));

        HrOnboardingCompletionVo result = service.evaluateReady(item);

        assertThat(result.getPostEntryDueDate()).isEqualTo(date(LocalDate.of(2026, 7, 10)));
        assertThat(result.getPostEntryOverdue()).isTrue();
    }

    @Test
    void invalidDueDaysConfigurationFallsBackToSeven()
    {
        when(configService.selectConfigByKey(DUE_DAYS_KEY)).thenReturn("0");
        HrOnboarding item = completeReady();
        item.setActualEntryDate(date(LocalDate.of(2026, 7, 1)));

        HrOnboardingCompletionVo result = service.evaluateReady(item);

        assertThat(result.getPostEntryDueDate()).isEqualTo(date(LocalDate.of(2026, 7, 8)));
        verify(configService).selectConfigByKey(DUE_DAYS_KEY);
    }

    @Test
    void batchReadyEvaluationResolvesPostEntryDueDaysOnce()
    {
        when(configService.selectConfigByKey(DUE_DAYS_KEY)).thenReturn("9");
        HrOnboarding first=completeReady();first.setOnboardingId(1L);first.setActualEntryDate(date(LocalDate.of(2026,7,1)));
        HrOnboarding second=completeReady();second.setOnboardingId(2L);second.setActualEntryDate(date(LocalDate.of(2026,7,2)));

        var result=service.evaluateReadyBatch(List.of(first,second));

        assertThat(result).containsOnlyKeys(1L,2L);
        assertThat(result.get(1L).getPostEntryDueDate()).isEqualTo(date(LocalDate.of(2026,7,10)));
        assertThat(result.get(2L).getPostEntryDueDate()).isEqualTo(date(LocalDate.of(2026,7,11)));
        verify(configService,times(1)).selectConfigByKey(DUE_DAYS_KEY);
    }

    @Test
    void postEntryDeadlineIsNotOverdueOnTheDueDate()
    {
        HrOnboarding item = completeReady();
        item.setActualEntryDate(date(LocalDate.of(2026, 7, 4)));

        HrOnboardingCompletionVo result = serviceAt(LocalDate.of(2026, 7, 11)).evaluateReady(item);

        assertThat(result.getPostEntryDueDate()).isEqualTo(date(LocalDate.of(2026, 7, 11)));
        assertThat(result.getPostEntryOverdue()).isFalse();
    }

    @Test
    void postEntryDeadlineBecomesOverdueOnTheNextLocalDay()
    {
        HrOnboarding item = completeReady();
        item.setActualEntryDate(date(LocalDate.of(2026, 7, 5)));

        HrOnboardingCompletionVo result = serviceAt(LocalDate.of(2026, 7, 13)).evaluateReady(item);

        assertThat(result.getPostEntryDueDate()).isEqualTo(date(LocalDate.of(2026, 7, 12)));
        assertThat(result.getPostEntryOverdue()).isTrue();
    }

    @Test
    void postEntryDeadlineAcceptsJdbcDateValues()
    {
        HrOnboarding item = completeReady();
        item.setActualEntryDate(java.sql.Date.valueOf(LocalDate.of(2026, 7, 4)));
        AtomicReference<HrOnboardingCompletionVo> result = new AtomicReference<>();

        assertThatCode(() -> result.set(service.evaluateReady(item))).doesNotThrowAnyException();
        assertThat(result.get().getPostEntryDueDate()).isEqualTo(date(LocalDate.of(2026, 7, 11)));
    }

    private HrOnboarding completeReady()
    {
        HrOnboarding item = new HrOnboarding();
        item.setEmployeeName("测试员工");
        item.setPhoneNumber("13800138000");
        item.setExpectedEntryDate(new Date());
        item.setTargetDeptId(1L);
        item.setTargetStoreId(2L);
        item.setTargetPostId(3L);
        item.setCompanyName("测试公司");
        item.setDeptLevel1Name("人力资源部");
        item.setStoreName("测试门店");
        item.setPositionName("人事专员");
        item.setDepartmentSupervisor("人事经理");
        item.setEmployeeCategory("全职");
        item.setOwnerUserId(4L);
        item.setSex("0");
        item.setBirthDate(new Date());
        item.setIdType("身份证");
        item.setIdNumber("350000199001010000");
        item.setJobGrade("P1");
        item.setDirectSupervisorUserId(5L);
        item.setRegisteredResidence("福建省泉州市");
        item.setCurrentAddress("福建省厦门市");
        item.setMaritalStatus("未婚");
        item.setEthnicity("汉族");
        item.setEmergencyContact("家属");
        item.setEmergencyContactRelation("父母");
        item.setEmergencyContactPhone("13900139000");
        item.setWorkLocation("厦门");
        item.setWorkCityLevel("二线");
        item.setLegalEntity("测试公司");
        return item;
    }

    private void clearDerivedResults(HrOnboarding item)
    {
        item.setCompanyName(null);
        item.setDeptLevel1Name(null);
        item.setDeptLevel2Name(null);
        item.setDeptLevel3Name(null);
        item.setStoreName(null);
        item.setPositionName(null);
        item.setDepartmentSupervisor(null);
    }

    private void clearDepartmentLayer(HrOnboarding item)
    {
        item.setDeptLevel1Name(null);
        item.setDeptLevel2Name(null);
        item.setDeptLevel3Name(null);
        item.setDepartmentSupervisor(null);
    }

    private SysUser completeProfileUser()
    {
        SysUser user = new SysUser();
        user.setNickName("测试员工");
        user.setPhonenumber("13800138000");
        user.setSex("0");
        SysUserProfile profile = new SysUserProfile();
        profile.setEmployeeNo("E0001");
        profile.setJobGrade("P1");
        profile.setEmployeeStatus("正式");
        profile.setEmployeeCategory("全职");
        profile.setBirthDate(new Date());
        profile.setIdType("身份证");
        profile.setIdNumber("350000199001010000");
        profile.setRegisteredResidence("福建省泉州市");
        profile.setCurrentAddress("福建省厦门市");
        profile.setMaritalStatus("未婚");
        profile.setEthnicity("汉族");
        profile.setEmergencyContact("家属");
        profile.setEmergencyContactRelation("父母");
        profile.setEmergencyContactPhone("13900139000");
        profile.setWorkLocation("厦门");
        profile.setWorkCityLevel("二线");
        profile.setBankName("测试银行");
        profile.setBankAccount("6222020200001234567");
        profile.setContractType("劳动合同");
        profile.setSocialType("社保");
        profile.setLegalEntity("测试公司");
        user.setProfile(profile);
        return user;
    }

    private HrOnboardingPositionConfig optionalConfig()
    {
        return config("OPTIONAL", "OPTIONAL", "OPTIONAL", "1");
    }

    private HrOnboardingPositionConfig config(String contract, String social, String probation, String status)
    {
        HrOnboardingPositionConfig config = new HrOnboardingPositionConfig();
        config.setContractTypeMode(contract);
        config.setSocialTypeMode(social);
        config.setProbationPeriodMode(probation);
        config.setStatus(status);
        return config;
    }

    private HrOnboardingRuleService serviceAt(LocalDate currentDate)
    {
        Clock clock = Clock.fixed(currentDate.atStartOfDay(ZONE).toInstant(), ZONE);
        return new HrOnboardingRuleService(registry, configService, clock);
    }

    private Date date(LocalDate value)
    {
        return Date.from(value.atStartOfDay(ZONE).toInstant());
    }
}
