package com.erp.system.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import com.erp.common.core.exception.ServiceException;
import com.erp.system.api.domain.SysDictData;
import com.erp.system.api.domain.SysDictType;
import com.erp.system.api.domain.SysRole;
import com.erp.system.domain.HrOnboardingPositionConfig;
import com.erp.system.domain.SysPost;
import com.erp.system.exception.HrOnboardingValidationException;
import com.erp.system.mapper.HrOnboardingPositionConfigMapper;
import com.erp.system.mapper.SysPostMapper;
import com.erp.system.service.ISysConfigService;
import com.erp.system.service.ISysDictTypeService;
import com.erp.system.service.ISysRoleService;

@ExtendWith(MockitoExtension.class)
class HrOnboardingPositionConfigServiceTest
{
    @Mock private HrOnboardingPositionConfigMapper mapper;
    @Mock private SysPostMapper postMapper;
    @Mock private ISysRoleService roleService;
    @Mock private ISysConfigService systemConfigService;
    @Mock private ISysDictTypeService dictTypeService;

    private HrOnboardingPositionConfigServiceImpl service;

    @BeforeEach
    void setUp()
    {
        service = new HrOnboardingPositionConfigServiceImpl(mapper, postMapper, roleService,
                systemConfigService, dictTypeService);
    }

    @Test
    void createPersistsResolvedDefaultsAndReplacesRolesInOneServiceCall()
    {
        activePost(8L);
        activeRole(3L);
        route("employeeCategory", "hr_employee_category", option("正式", "FORMAL"));
        route("contractType", "hr_contract_type", option("劳动合同", "LABOR"));
        route("socialType", "hr_social_type", option("本地社保", "LOCAL"));
        route("probationPeriod", "hr_probation_period", option("三个月", "3M"));
        HrOnboardingPositionConfig input = validConfig();
        when(mapper.selectByPair(8L, "FORMAL")).thenReturn(null);
        when(mapper.insert(any())).thenAnswer(invocation -> {
            invocation.<HrOnboardingPositionConfig>getArgument(0).setConfigId(19L);
            return 1;
        });
        when(mapper.selectById(19L)).thenAnswer(ignored -> input);

        service.create(input, "hr-admin");

        ArgumentCaptor<HrOnboardingPositionConfig> inserted =
                ArgumentCaptor.forClass(HrOnboardingPositionConfig.class);
        verify(mapper).insert(inserted.capture());
        assertThat(inserted.getValue().getDataScopeStrategy()).isEqualTo("TARGET_STORE");
        assertThat(inserted.getValue().getContractTypeMode()).isEqualTo("REQUIRED");
        assertThat(inserted.getValue().getSocialTypeMode()).isEqualTo("OPTIONAL");
        assertThat(inserted.getValue().getProbationPeriodMode()).isEqualTo("NOT_APPLICABLE");
        assertThat(inserted.getValue().getAccountEnabled()).isTrue();
        assertThat(inserted.getValue().getStatus()).isEqualTo("0");
        assertThat(inserted.getValue().getVersion()).isZero();
        verify(mapper).deleteRolesByConfigId(19L);
        verify(mapper).insertRole(19L, 3L, "hr-admin");
    }

    @Test
    void eachRuleModeMustBeExplicitAndSupported()
    {
        HrOnboardingPositionConfig input = validConfig();
        input.setSocialTypeMode("MAYBE");

        assertError(input, "POSITION_CONFIG_RULE_MODE_INVALID");
        verify(mapper, never()).insert(any());
    }

    @Test
    void requiredRuleRejectsBlankDefault()
    {
        HrOnboardingPositionConfig input = validConfig();
        input.setDefaultContractType("  ");

        assertError(input, "POSITION_CONFIG_DEFAULT_REQUIRED");
    }

    @Test
    void enabledAccountRequiresRolesAndDictionaryMappings()
    {
        HrOnboardingPositionConfig noRoles = validConfig();
        noRoles.setRoleIds(Collections.emptyList());
        assertError(noRoles, "POSITION_CONFIG_ROLE_REQUIRED");

        HrOnboardingPositionConfig missingMapping = validConfig();
        activePost(8L);
        activeRole(3L);
        when(systemConfigService.selectConfigByKey("hr.onboarding.dict_type.employeeCategory"))
                .thenReturn("");
        assertThatThrownBy(() -> service.create(missingMapping, "hr-admin"))
                .isInstanceOf(HrOnboardingValidationException.class)
                .extracting("errorCode").isEqualTo("POSITION_CONFIG_DICTIONARY_MAPPING_MISSING");
    }

    @Test
    void disabledAccountMayDeliberatelyHaveNoRoles()
    {
        HrOnboardingPositionConfig input = validConfig();
        input.setAccountEnabled(false);
        input.setRoleIds(Collections.emptyList());
        activePost(8L);
        routeAllRequiredDictionaries();
        when(mapper.selectByPair(8L, "FORMAL")).thenReturn(null);
        when(mapper.insert(any())).thenAnswer(invocation -> {
            invocation.<HrOnboardingPositionConfig>getArgument(0).setConfigId(20L);
            return 1;
        });
        when(mapper.selectById(20L)).thenReturn(input);

        service.create(input, "hr-admin");

        verify(mapper).deleteRolesByConfigId(20L);
        verify(mapper, never()).insertRole(any(), any(), any());
    }

    @Test
    void disabledAccountStillRequiresDictionaryMappings()
    {
        HrOnboardingPositionConfig input = validConfig();
        input.setAccountEnabled(false);
        input.setRoleIds(Collections.emptyList());
        activePost(8L);

        assertThatThrownBy(() -> service.create(input, "hr-admin"))
                .isInstanceOf(HrOnboardingValidationException.class)
                .extracting("errorCode").isEqualTo("POSITION_CONFIG_DICTIONARY_MAPPING_MISSING");
        verify(mapper, never()).insert(any());
    }

    @Test
    void disabledAccountStillRejectsValuesOutsideActiveDictionaries()
    {
        HrOnboardingPositionConfig input = validConfig();
        input.setAccountEnabled(false);
        input.setRoleIds(Collections.emptyList());
        input.setEmployeeCategory("UNKNOWN");
        activePost(8L);
        route("employeeCategory", "hr_employee_category", option("正式", "FORMAL"));

        assertThatThrownBy(() -> service.create(input, "hr-admin"))
                .isInstanceOf(HrOnboardingValidationException.class)
                .extracting("errorCode").isEqualTo("POSITION_CONFIG_DICTIONARY_VALUE_INVALID");
        verify(mapper, never()).insert(any());
    }

    @Test
    void postAndEveryGrantedRoleMustBeActive()
    {
        HrOnboardingPositionConfig input = validConfig();
        SysPost stoppedPost = new SysPost();
        stoppedPost.setPostId(8L);
        stoppedPost.setStatus("1");
        when(postMapper.selectPostById(8L)).thenReturn(stoppedPost);
        assertThatThrownBy(() -> service.create(input, "hr-admin"))
                .isInstanceOf(HrOnboardingValidationException.class)
                .extracting("errorCode").isEqualTo("POSITION_CONFIG_POST_INACTIVE");

        activePost(8L);
        SysRole stoppedRole = new SysRole(3L);
        stoppedRole.setStatus("1");
        when(roleService.selectRoleById(3L)).thenReturn(stoppedRole);
        assertThatThrownBy(() -> service.create(input, "hr-admin"))
                .isInstanceOf(HrOnboardingValidationException.class)
                .extracting("errorCode").isEqualTo("POSITION_CONFIG_ROLE_INACTIVE");
    }

    @Test
    void duplicatePairAndStaleUpdatesReturnStableErrors()
    {
        HrOnboardingPositionConfig input = validConfig();
        activePost(8L);
        activeRole(3L);
        routeAllRequiredDictionaries();
        when(mapper.selectByPair(8L, "FORMAL")).thenReturn(new HrOnboardingPositionConfig());
        assertThatThrownBy(() -> service.create(input, "hr-admin"))
                .isInstanceOf(HrOnboardingValidationException.class)
                .extracting("errorCode").isEqualTo("POSITION_CONFIG_DUPLICATE");

        input.setConfigId(55L);
        input.setVersion(4);
        HrOnboardingPositionConfig stored = validConfig();
        stored.setConfigId(55L);
        stored.setVersion(4);
        when(mapper.selectByIdForUpdate(55L)).thenReturn(stored);
        when(mapper.selectByPair(8L, "FORMAL")).thenReturn(stored);
        when(mapper.updateByVersion(any())).thenReturn(0);
        assertThatThrownBy(() -> service.update(55L, input, "hr-admin"))
                .isInstanceOf(HrOnboardingValidationException.class)
                .extracting("errorCode").isEqualTo("POSITION_CONFIG_VERSION_CONFLICT");
        verify(mapper, never()).deleteRolesByConfigId(55L);
    }

    @Test
    void optionsUseOnlyActiveCatalogValuesAndReportMissingDictionaryMappings()
    {
        SysPost activePost = new SysPost();
        activePost.setPostId(8L); activePost.setPostName("店长"); activePost.setStatus("0");
        SysPost stoppedPost = new SysPost();
        stoppedPost.setPostId(9L); stoppedPost.setPostName("停用岗位"); stoppedPost.setStatus("1");
        when(postMapper.selectPostList(any())).thenReturn(Arrays.asList(activePost, stoppedPost));
        SysRole activeRole = new SysRole(3L);
        activeRole.setRoleName("门店角色"); activeRole.setStatus("0"); activeRole.setDelFlag("0");
        SysRole stoppedRole = new SysRole(4L);
        stoppedRole.setRoleName("停用角色"); stoppedRole.setStatus("1"); stoppedRole.setDelFlag("0");
        when(roleService.selectRoleList(any())).thenReturn(Arrays.asList(activeRole, stoppedRole));
        SysDictData activeCategory = option("正式", "FORMAL");
        SysDictData stoppedCategory = option("停用类别", "STOPPED");
        stoppedCategory.setStatus("1");
        route("employeeCategory", "hr_employee_category", activeCategory, stoppedCategory);

        java.util.Map<String, Object> options = service.options();

        assertThat((java.util.List<?>) options.get("posts")).singleElement().asString()
                .contains("label=店长", "value=8");
        assertThat((java.util.List<?>) options.get("roles")).singleElement().asString()
                .contains("label=门店角色", "value=3");
        assertThat((java.util.List<?>) options.get("employeeCategories")).singleElement().asString()
                .contains("label=正式", "value=FORMAL");
        assertThat((java.util.List<?>) options.get("dataScopeStrategies"))
                .extracting(Object::toString)
                .containsExactly(
                        "{label=目标门店, value=TARGET_STORE}",
                        "{label=目标组织, value=TARGET_DEPT}",
                        "{label=不配置数据范围, value=NONE}");
        @SuppressWarnings("unchecked")
        java.util.List<String> missing = (java.util.List<String>) options.get("missingDictionaryMappings");
        assertThat(missing)
                .containsExactlyElementsOf(HrOnboardingPositionConfigServiceImpl.DICTIONARY_FIELDS.stream()
                        .filter(field -> !"employeeCategory".equals(field)).toList());
    }

    @Test
    void stoppedOrMissingDictionaryTypesNeverExposeCachedData()
    {
        SysDictData cachedCategory = option("正式", "FORMAL");
        SysDictData cachedSex = option("男", "M");
        when(systemConfigService.selectConfigByKey("hr.onboarding.dict_type.employeeCategory"))
                .thenReturn("hr_employee_category");
        when(systemConfigService.selectConfigByKey("hr.onboarding.dict_type.sex"))
                .thenReturn("sys_user_sex");
        SysDictType stopped = new SysDictType();
        stopped.setDictType("hr_employee_category");
        stopped.setStatus("1");
        org.mockito.Mockito.lenient().when(dictTypeService.selectDictTypeByType("hr_employee_category"))
                .thenReturn(stopped);
        org.mockito.Mockito.lenient().when(dictTypeService.selectDictDataByType("hr_employee_category"))
                .thenReturn(Collections.singletonList(cachedCategory));
        org.mockito.Mockito.lenient().when(dictTypeService.selectDictDataByType("sys_user_sex"))
                .thenReturn(Collections.singletonList(cachedSex));

        java.util.Map<String, Object> options = service.options();

        assertThat((java.util.List<?>) options.get("employeeCategories")).isEmpty();
        @SuppressWarnings("unchecked")
        java.util.Map<String, java.util.List<java.util.Map<String, Object>>> dictionaries =
                (java.util.Map<String, java.util.List<java.util.Map<String, Object>>>)
                        options.get("dictionaryDefaults");
        assertThat(dictionaries.get("sex")).isEmpty();
        @SuppressWarnings("unchecked")
        java.util.List<String> missing = (java.util.List<String>) options.get("missingDictionaryMappings");
        assertThat(missing).contains("employeeCategory", "sex");
        verify(dictTypeService, never()).selectDictDataByType("hr_employee_category");
        verify(dictTypeService, never()).selectDictDataByType("sys_user_sex");
    }

    @Test
    void roleOptionsUseScopedRoleServiceExcludeAdminAndSortDeterministically()
    {
        SysRole later = role(30L, "Beta", "store_beta", 20);
        SysRole admin = role(1L, "超级管理员", "admin", 0);
        SysRole superAdmin = role(2L, "Super Admin", "super-admin", 0);
        SysRole sameSortLaterName = role(20L, "Zulu", "store_zulu", 10);
        SysRole sameSortFirstNameHigherId = role(11L, "Alpha", "store_alpha_2", 10);
        SysRole sameSortFirstNameLowerId = role(10L, "Alpha", "store_alpha_1", 10);
        when(roleService.selectRoleList(any())).thenReturn(Arrays.asList(
                later, admin, superAdmin, sameSortLaterName,
                sameSortFirstNameHigherId, sameSortFirstNameLowerId));

        java.util.Map<String, Object> options = service.options();

        @SuppressWarnings("unchecked")
        List<java.util.Map<String, Object>> roles =
                (List<java.util.Map<String, Object>>) options.get("roles");
        assertThat(roles).extracting(option -> option.get("value"))
                .containsExactly(10L, 11L, 20L, 30L)
                .doesNotContain(1L, 2L);
        ArgumentCaptor<SysRole> query = ArgumentCaptor.forClass(SysRole.class);
        verify(roleService).selectRoleList(query.capture());
        assertThat(query.getValue().getStatus()).isEqualTo("0");
    }

    @Test
    void savingAdminRoleIsRejectedBeforePersistence()
    {
        HrOnboardingPositionConfig input = validConfig();
        activePost(8L);
        org.mockito.Mockito.doThrow(new ServiceException("不允许操作超级管理员角色"))
                .when(roleService).checkRoleAllowed(any(SysRole.class));

        assertThatThrownBy(() -> service.create(input, "hr-admin"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("超级管理员");
        verify(mapper, never()).insert(any());
    }

    @Test
    void savingOutOfScopeRoleIsRejectedBeforePersistence()
    {
        HrOnboardingPositionConfig input = validConfig();
        activePost(8L);
        org.mockito.Mockito.doThrow(new ServiceException("没有权限访问角色数据"))
                .when(roleService).checkRoleDataScope(3L);

        assertThatThrownBy(() -> service.create(input, "hr-admin"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("权限");
        verify(roleService).checkRoleAllowed(any(SysRole.class));
        verify(roleService).checkRoleDataScope(3L);
        verify(mapper, never()).insert(any());
    }

    @Test
    void updateCanReEnableDisabledRowAndDoesNotRejectItsOwnPair()
    {
        HrOnboardingPositionConfig stored = validConfig();
        stored.setConfigId(55L);
        stored.setVersion(4);
        stored.setStatus("1");
        HrOnboardingPositionConfig input = validConfig();
        input.setVersion(4);
        input.setStatus("0");
        activePost(8L);
        activeRole(3L);
        routeAllRequiredDictionaries();
        when(mapper.selectByIdForUpdate(55L)).thenReturn(stored);
        when(mapper.selectByPair(8L, "FORMAL")).thenReturn(stored);
        when(mapper.updateByVersion(any())).thenReturn(1);
        when(mapper.selectById(55L)).thenReturn(input);

        service.update(55L, input, "hr-admin");

        ArgumentCaptor<HrOnboardingPositionConfig> updated =
                ArgumentCaptor.forClass(HrOnboardingPositionConfig.class);
        verify(mapper).updateByVersion(updated.capture());
        assertThat(updated.getValue().getStatus()).isEqualTo("0");
        verify(mapper).deleteRolesByConfigId(55L);
        verify(mapper).insertRole(55L, 3L, "hr-admin");
    }

    @Test
    void updateWithNullStatusPreservesStoredStatus()
    {
        HrOnboardingPositionConfig stored = validConfig();
        stored.setConfigId(55L);
        stored.setVersion(4);
        stored.setStatus("1");
        HrOnboardingPositionConfig input = validConfig();
        input.setVersion(4);
        input.setStatus(null);
        activePost(8L);
        activeRole(3L);
        routeAllRequiredDictionaries();
        when(mapper.selectByIdForUpdate(55L)).thenReturn(stored);
        when(mapper.selectByPair(8L, "FORMAL")).thenReturn(stored);
        when(mapper.updateByVersion(any())).thenReturn(1);
        when(mapper.selectById(55L)).thenReturn(input);

        service.update(55L, input, "hr-admin");

        ArgumentCaptor<HrOnboardingPositionConfig> updated =
                ArgumentCaptor.forClass(HrOnboardingPositionConfig.class);
        verify(mapper).updateByVersion(updated.capture());
        assertThat(updated.getValue().getStatus()).isEqualTo("1");
    }

    @Test
    void updateRejectsUnsupportedStatus()
    {
        HrOnboardingPositionConfig stored = validConfig();
        stored.setConfigId(55L);
        stored.setVersion(4);
        stored.setStatus("1");
        HrOnboardingPositionConfig input = validConfig();
        input.setVersion(4);
        input.setStatus("2");
        when(mapper.selectByIdForUpdate(55L)).thenReturn(stored);

        assertThatThrownBy(() -> service.update(55L, input, "hr-admin"))
                .isInstanceOf(HrOnboardingValidationException.class)
                .extracting("errorCode").isEqualTo("POSITION_CONFIG_STATUS_INVALID");
        verify(mapper, never()).updateByVersion(any());
    }

    @Test
    void updateCannotBypassDedicatedDisableOperation()
    {
        HrOnboardingPositionConfig stored = validConfig();
        stored.setConfigId(55L);
        stored.setVersion(4);
        stored.setStatus("0");
        HrOnboardingPositionConfig input = validConfig();
        input.setVersion(4);
        input.setStatus("1");
        when(mapper.selectByIdForUpdate(55L)).thenReturn(stored);

        assertThatThrownBy(() -> service.update(55L, input, "hr-admin"))
                .isInstanceOf(HrOnboardingValidationException.class)
                .extracting("errorCode").isEqualTo("POSITION_CONFIG_DISABLE_REQUIRED");
        verify(mapper, never()).updateByVersion(any());
    }

    @Test
    void onlyPositionPairUniqueViolationMapsToDuplicateError()
    {
        HrOnboardingPositionConfig input = validConfig();
        activePost(8L);
        activeRole(3L);
        routeAllRequiredDictionaries();
        when(mapper.selectByPair(8L, "FORMAL")).thenReturn(null);
        DataIntegrityViolationException uniqueViolation = new DataIntegrityViolationException(
                "write failed", new SQLException("Duplicate entry for key 'uk_hr_onboarding_position_category'"));
        when(mapper.insert(any())).thenThrow(uniqueViolation);

        assertThatThrownBy(() -> service.create(input, "hr-admin"))
                .isInstanceOf(HrOnboardingValidationException.class)
                .extracting("errorCode").isEqualTo("POSITION_CONFIG_DUPLICATE");
    }

    @Test
    void unrelatedIntegrityViolationIsRethrownUnchanged()
    {
        HrOnboardingPositionConfig input = validConfig();
        activePost(8L);
        activeRole(3L);
        routeAllRequiredDictionaries();
        when(mapper.selectByPair(8L, "FORMAL")).thenReturn(null);
        DataIntegrityViolationException foreignKeyViolation = new DataIntegrityViolationException(
                "write failed", new SQLException("Cannot add or update a child row: a foreign key constraint fails"));
        when(mapper.insert(any())).thenThrow(foreignKeyViolation);

        assertThatThrownBy(() -> service.create(input, "hr-admin"))
                .isSameAs(foreignKeyViolation);
    }

    private void assertError(HrOnboardingPositionConfig input, String errorCode)
    {
        assertThatThrownBy(() -> service.create(input, "hr-admin"))
                .isInstanceOf(HrOnboardingValidationException.class)
                .extracting("errorCode").isEqualTo(errorCode);
    }

    private void activePost(Long id)
    {
        SysPost post = new SysPost();
        post.setPostId(id);
        post.setStatus("0");
        when(postMapper.selectPostById(id)).thenReturn(post);
    }

    private void activeRole(Long id)
    {
        SysRole role = new SysRole(id);
        role.setStatus("0");
        role.setDelFlag("0");
        when(roleService.selectRoleById(id)).thenReturn(role);
    }

    private SysRole role(Long id, String name, String key, Integer sort)
    {
        SysRole role = new SysRole(id);
        role.setRoleName(name);
        role.setRoleKey(key);
        role.setRoleSort(sort);
        role.setStatus("0");
        role.setDelFlag("0");
        return role;
    }

    private void routeAllRequiredDictionaries()
    {
        route("employeeCategory", "hr_employee_category", option("正式", "FORMAL"));
        route("contractType", "hr_contract_type", option("劳动合同", "LABOR"));
        route("socialType", "hr_social_type", option("本地社保", "LOCAL"));
    }

    private void route(String fieldKey, String type, SysDictData... values)
    {
        when(systemConfigService.selectConfigByKey("hr.onboarding.dict_type." + fieldKey)).thenReturn(type);
        SysDictType activeType = new SysDictType();
        activeType.setDictType(type);
        activeType.setStatus("0");
        when(dictTypeService.selectDictTypeByType(type)).thenReturn(activeType);
        when(dictTypeService.selectDictDataByType(type)).thenReturn(Arrays.asList(values));
    }

    private SysDictData option(String label, String value)
    {
        SysDictData data = new SysDictData();
        data.setDictLabel(label);
        data.setDictValue(value);
        data.setStatus("0");
        return data;
    }

    private HrOnboardingPositionConfig validConfig()
    {
        HrOnboardingPositionConfig config = new HrOnboardingPositionConfig();
        config.setPostId(8L);
        config.setEmployeeCategory("FORMAL");
        config.setDataScopeStrategy("TARGET_STORE");
        config.setContractTypeMode("REQUIRED");
        config.setDefaultContractType("LABOR");
        config.setSocialTypeMode("OPTIONAL");
        config.setDefaultSocialType("LOCAL");
        config.setProbationPeriodMode("NOT_APPLICABLE");
        config.setDefaultProbationPeriod(null);
        config.setJobGrade("P3");
        config.setAccountEnabled(true);
        config.setRoleIds(Collections.singletonList(3L));
        return config;
    }
}
