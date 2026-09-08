package com.erp.system.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.erp.common.core.exception.ServiceException;
import com.erp.common.datascope.annotation.DataScope;
import com.erp.common.datascope.aspect.DataScopeAspect;
import com.erp.system.api.domain.SysDept;
import com.erp.system.api.domain.SysRole;
import com.erp.system.api.domain.SysUser;
import com.erp.system.api.domain.SysUserProfile;
import com.erp.system.domain.SysPost;
import com.erp.system.domain.SysUserPost;
import com.erp.system.api.model.LoginUser;
import com.erp.system.domain.vo.HrEmployeeListVo;
import com.erp.system.domain.vo.HrEmployeeProfileVo;
import com.erp.system.domain.vo.HrEmployeeQuery;
import com.erp.system.mapper.HrSensitiveAccessLogMapper;
import com.erp.system.mapper.SysDeptMapper;
import com.erp.system.mapper.SysUserMapper;
import com.erp.system.mapper.SysUserProfileMapper;
import com.erp.system.mapper.HrOnboardingMapper;
import com.erp.system.mapper.SysPostMapper;
import com.erp.system.mapper.SysUserPostMapper;
import com.erp.system.service.IHrOnboardingPositionConfigService;
import com.erp.system.service.ISysUserService;
import com.erp.system.support.HrEmployeeFieldRegistry;
import com.erp.system.support.HrSensitiveFieldMasker;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import com.github.pagehelper.Page;

@ExtendWith(MockitoExtension.class)
class HrEmployeeProfileServiceImplTest
{
    @Mock private SysUserMapper userMapper;
    @Mock private SysDeptMapper deptMapper;
    @Mock private SysUserProfileMapper profileMapper;
    @Mock private HrOnboardingMapper onboardingMapper;
    @Mock private SysPostMapper postMapper;
    @Mock private SysUserPostMapper userPostMapper;
    @Mock private IHrOnboardingPositionConfigService onboardingConfigService;
    @Mock private ISysUserService userService;
    @Mock private HrSensitiveAccessLogMapper auditMapper;
    @Mock private SysUserProfileDerivationService derivationService;
    @Mock private HrSensitiveAuditService exportAuditService;
    @Mock private HrSensitiveWorkbookGenerator workbookGenerator;
    private HrEmployeeAccessService accessService;
    private HrEmployeeProfileServiceImpl service;

    @BeforeEach
    void setUp()
    {
        accessService = new HrEmployeeAccessService(userMapper,deptMapper,onboardingMapper);
        service = new HrEmployeeProfileServiceImpl(accessService, userMapper, profileMapper, auditMapper,
                derivationService, new HrEmployeeFieldRegistry(), new HrSensitiveFieldMasker(), postMapper,
                userPostMapper, onboardingConfigService, userService, exportAuditService, workbookGenerator);
    }

    @Test
    void summaryAggregatesAllScopedRowsAndDoesNotDependOnTheCurrentPage()
    {
        SysUser complete = employee(1L, "E001", "完整员工");
        fillCompletenessFields(complete);
        complete.setSetupStatus("complete");
        SysUser incomplete = employee(2L, "E002", "待补员工");
        incomplete.setSetupStatus("missingRole");
        when(userMapper.selectHrEmployeeList(any())).thenReturn(Arrays.asList(complete, incomplete));

        var result = service.summary(new HrEmployeeQuery());

        assertThat(result.getTotalEmployeeCount()).isEqualTo(2);
        assertThat(result.getIncompleteEmployeeCount()).isEqualTo(1);
        assertThat(result.getAccountConfigurationRiskCount()).isEqualTo(1);
        verify(userMapper).selectHrEmployeeList(any());
    }

    @Test
    void mappedEmployeeListPreservesDatabasePageTotal()
    {
        Page<SysUser> page=new Page<>(2,10); page.setTotal(25); page.add(employee(7L,"E007","分页员工"));
        when(userMapper.selectHrEmployeeList(any())).thenReturn(page);

        List<HrEmployeeListVo> result=service.list(new HrEmployeeQuery());

        assertThat(result).isInstanceOf(Page.class);
        assertThat(((Page<?>)result).getTotal()).isEqualTo(25);
    }

    @Test
    void employeeRowsExposeGranularLegacyAccountConfigurationRisks()
    {
        SysUser both=employee(1L,"E001","双缺失");both.setSetupStatus("missingRoleAndShopScope");
        SysUser role=employee(2L,"E002","角色缺失");role.setSetupStatus("missingRole");
        SysUser scope=employee(3L,"E003","范围缺失");scope.setSetupStatus("missingShopScope");
        SysUser configured=employee(4L,"E004","已配置");configured.setSetupStatus("complete");
        when(userMapper.selectHrEmployeeList(any())).thenReturn(List.of(both,role,scope,configured));

        List<HrEmployeeListVo> rows=service.list(new HrEmployeeQuery());

        assertThat(rows.get(0).getAccountConfigurationRiskCodes())
                .containsExactly("ROLE_CONFIGURATION_MISSING","DATA_SCOPE_CONFIGURATION_MISSING");
        assertThat(rows.get(1).getAccountConfigurationRiskCodes()).containsExactly("ROLE_CONFIGURATION_MISSING");
        assertThat(rows.get(2).getAccountConfigurationRiskCodes()).containsExactly("DATA_SCOPE_CONFIGURATION_MISSING");
        assertThat(rows.get(3).getAccountConfigurationRiskCodes()).isEmpty();
    }

    @Test
    void completenessFilterUsesCanonicalDerivedCalculationAndRejectsInvalidValues()
    {
        SysUser complete=employee(1L,"E001","完整员工");fillCompletenessFields(complete);
        SysUser incomplete=employee(2L,"E002","待补员工");
        when(userMapper.selectHrEmployeeList(any())).thenReturn(List.of(complete,incomplete));
        HrEmployeeQuery completeQuery=new HrEmployeeQuery();completeQuery.setCompletenessStatus("COMPLETE");
        HrEmployeeQuery incompleteQuery=new HrEmployeeQuery();incompleteQuery.setCompletenessStatus("incomplete");

        assertThat(service.list(completeQuery)).extracting(HrEmployeeListVo::getUserId).containsExactly(1L);
        assertThat(service.list(incompleteQuery)).extracting(HrEmployeeListVo::getUserId).containsExactly(2L);
        assertThat(service.summary(completeQuery).getTotalEmployeeCount()).isEqualTo(1);
        assertThat(service.completenessEmployees(incompleteQuery)).extracting(HrEmployeeListVo::getUserId)
                .containsExactly(2L);
        assertThat(service.completenessDepartments(completeQuery)).singleElement()
                .satisfies(row->assertThat(row.get("employeeCount")).isEqualTo(1));

        HrEmployeeQuery invalid=new HrEmployeeQuery();invalid.setCompletenessStatus("UNKNOWN");
        assertThatThrownBy(()->service.list(invalid)).isInstanceOf(ServiceException.class)
                .hasMessageContaining("完整度");
    }

    @Test
    void completenessScanAtCapIsExactAndForcesBoundedMapperQuery()
    {
        SysUser complete=employee(1L,"E001","完整员工");fillCompletenessFields(complete);
        when(userMapper.selectHrEmployeeList(any())).thenReturn(
                new ArrayList<>(Collections.nCopies(10000,complete)));
        HrEmployeeQuery query=new HrEmployeeQuery();query.setCompletenessStatus("COMPLETE");

        assertThat(service.summary(query).getTotalEmployeeCount()).isEqualTo(10000);

        ArgumentCaptor<HrEmployeeQuery> bounded=ArgumentCaptor.forClass(HrEmployeeQuery.class);
        verify(userMapper).selectHrEmployeeList(bounded.capture());
        assertThat(bounded.getValue().getMaxRows()).isEqualTo(10001);
    }

    @Test
    void completenessScanOverCapFailsBeforeDerivationAndSensitiveWorkbookAndIsAudited()
    {
        SysUser incomplete=employee(1L,"E001","待补员工");
        when(userMapper.selectHrEmployeeList(any())).thenReturn(
                new ArrayList<>(Collections.nCopies(10001,incomplete)));
        HrEmployeeQuery query=new HrEmployeeQuery();query.setCompletenessStatus("INCOMPLETE");

        assertThatThrownBy(()->service.list(query)).isInstanceOf(ServiceException.class)
                .hasMessageContaining("完整度扫描").hasMessageContaining("10000");
        verify(derivationService,never()).applyToUsers(any());
        org.mockito.Mockito.clearInvocations(userMapper,derivationService);

        assertThatThrownBy(()->service.exportSensitive(query,Set.of("idNumber"),9L,"hr","127.0.0.1"))
                .isInstanceOf(ServiceException.class).hasMessageContaining("完整度扫描");
        ArgumentCaptor<HrEmployeeQuery> bounded=ArgumentCaptor.forClass(HrEmployeeQuery.class);
        verify(userMapper).selectHrEmployeeList(bounded.capture());
        assertThat(bounded.getValue().getMaxRows()).isEqualTo(10001);
        verify(derivationService,never()).applyToUsers(any());
        verify(workbookGenerator,never()).generate(any(),any());
        verify(exportAuditService).recordFailure(any(),org.mockito.ArgumentMatchers.eq(9L),
                org.mockito.ArgumentMatchers.eq("hr"),org.mockito.ArgumentMatchers.eq("127.0.0.1"),
                org.mockito.ArgumentMatchers.eq("PREPARATION_FAILED"));
    }

    @Test
    void legacyMaskedSingleStarCannotOverwriteRawSensitiveValue()
    {
        SysUser employee=employee(7L,"E007","测试员工");
        employee.getProfile().setCurrentAddress("上海市中心路1号");
        when(userMapper.selectHrEmployeeForUpdate(any())).thenReturn(employee);
        when(userMapper.patchHrEmployeeUser(anyLong(),any(),any())).thenReturn(1);
        Map<String,Object> input=new LinkedHashMap<>();input.put("userId",7L);input.put("remark","只改备注");
        input.put("profile",Map.of("currentAddress","上海*"));

        service.updateLegacy(input,"hr-user");

        verify(profileMapper,never()).patchUserProfile(anyLong(),any(),any());
        ArgumentCaptor<Map<String,Object>> userPatch=ArgumentCaptor.forClass(Map.class);
        verify(userMapper).patchHrEmployeeUser(org.mockito.ArgumentMatchers.eq(7L),userPatch.capture(),any());
        assertThat(userPatch.getValue()).containsOnlyKeys("remark").containsEntry("remark","只改备注");
        assertThat(employee.getProfile().getCurrentAddress()).isEqualTo("上海市中心路1号");
    }

    @Test
    void scopedExportQueryCopyPreservesCompletenessFilter()
    {
        HrEmployeeQuery source=new HrEmployeeQuery();source.setKeyword("张三");
        source.setCompletenessStatus("INCOMPLETE");source.setAccountConfigurationStatus("MISSING");

        HrEmployeeQuery copy=org.springframework.test.util.ReflectionTestUtils.invokeMethod(service,"copyQuery",source);

        assertThat(copy).isNotSameAs(source);
        assertThat(copy.getKeyword()).isEqualTo("张三");
        assertThat(copy.getCompletenessStatus()).isEqualTo("INCOMPLETE");
        assertThat(copy.getAccountConfigurationStatus()).isEqualTo("MISSING");
    }

    @Test
    void listAndDetailAreRegistryMappedMaskedAndKeepEmployeeNumberAndRemark()
    {
        SysUser employee = employee(7L, "E007", "测试员工");
        employee.setRemark("稳定备注");
        employee.setPhonenumber("13800138000");
        employee.getProfile().setIdNumber("350000199001010000");
        employee.getProfile().setBankAccount("6222020200001234567");
        employee.getProfile().setCurrentAddress("福建省厦门市思明区");
        when(userMapper.selectHrEmployeeList(any())).thenReturn(Collections.singletonList(employee));

        List<HrEmployeeListVo> list = service.list(new HrEmployeeQuery());
        HrEmployeeProfileVo detail = service.get(7L);

        assertThat(list).singleElement().satisfies(row -> {
            assertThat(row.getEmployeeNo()).isEqualTo("E007");
            assertThat(row.getRemark()).isEqualTo("稳定备注");
            assertThat(row.getPhoneNumberMasked()).isEqualTo("138****8000");
            assertThat(row.getOnboardingDetailUrl()).isEqualTo("/hr/onboarding?linkedUserId=7");
        });
        assertThat(detail.getEmployeeNo()).isEqualTo("E007");
        assertThat(detail.getRemark()).isEqualTo("稳定备注");
        assertThat(detail.getIdNumberMasked()).isEqualTo("3500**********0000");
        assertThat(detail.getBankAccountMasked()).isEqualTo("6222***********4567");
        assertThat(detail.getCurrentAddressMasked()).isEqualTo("福建*******");
        assertThat(Arrays.stream(HrEmployeeListVo.class.getMethods()).map(Method::getName))
                .doesNotContain("getIdNumber", "getBankAccount", "getRegisteredResidence", "getCurrentAddress");
    }

    @Test
    void contractSalaryFieldsAreMaskedAndCannotBeOverwrittenByGenericProfileEdits()
    {
        SysUser employee=employee(7L,"E007","测试员工");
        employee.getProfile().setSalaryTotal(new java.math.BigDecimal("5000"));
        employee.getProfile().setBaseSalary(new java.math.BigDecimal("3000"));
        employee.getProfile().setPostSalary(new java.math.BigDecimal("1000"));
        employee.getProfile().setFieldAllowance(new java.math.BigDecimal("500"));
        employee.getProfile().setPerformanceSalary(new java.math.BigDecimal("500"));
        when(userMapper.selectHrEmployeeList(any())).thenReturn(List.of(employee));
        when(userMapper.selectHrEmployeeForUpdate(any())).thenReturn(employee);
        HrEmployeeProfileVo detail=service.get(7L);
        for (String field : List.of("salaryTotal","baseSalary","postSalary","fieldAllowance","performanceSalary"))
        {
            assertThat(detail.getFields()).containsEntry(field,"******");
            assertThat(detail.getProfile()).containsEntry(field,"******");
            assertThatThrownBy(()->service.update(7L,Map.of(field,9999),"hr")).hasMessageContaining("只读");
        }
        assertThat(employee.getProfile().getSalaryTotal()).isEqualByComparingTo("5000");
        verify(profileMapper,never()).patchUserProfile(anyLong(),any(),any());
    }

    @Test
    void listOptionalFieldsAndDetailRelationsAndRecordMetadataRoundTripFromAuthoritativeData()
    {
        SysUser employee=employee(7L,"E007","测试员工");employee.setDeptId(20L);employee.setPostNames("工程师");
        employee.setCreateBy("account-creator");employee.setUpdateBy("account-editor");
        employee.setCreateTime(java.sql.Date.valueOf("2026-01-01"));employee.setUpdateTime(java.sql.Date.valueOf("2026-01-02"));
        employee.getProfile().setDepartmentSupervisor("部门主管");employee.getProfile().setDirectSupervisor("直属主管");
        employee.getProfile().setContractStartDate(java.sql.Date.valueOf("2026-01-03"));
        employee.getProfile().setContractEndDate(java.sql.Date.valueOf("2027-01-02"));
        employee.getProfile().setContractType("固定期限");employee.getProfile().setSocialType("本地社保");
        employee.getProfile().setSocialSecurityLocation("上海");employee.getProfile().setHousingFundLocation("上海");
        employee.getProfile().setLegalEntity("测试公司");employee.getProfile().setRecruitmentChannel("校招");
        employee.getProfile().setLeaveDate(java.sql.Date.valueOf("2028-01-01"));
        employee.getProfile().setCreateBy("profile-creator");employee.getProfile().setUpdateBy("profile-editor");
        employee.getProfile().setCreateTime(java.sql.Date.valueOf("2026-01-04"));
        employee.getProfile().setUpdateTime(java.sql.Date.valueOf("2026-01-05"));
        when(userMapper.selectHrEmployeeList(any())).thenReturn(List.of(employee));
        when(userPostMapper.selectPostIdsByUserId(7L)).thenReturn(List.of(30L,31L));

        HrEmployeeListVo row=service.list(new HrEmployeeQuery()).get(0);
        HrEmployeeProfileVo detail=service.get(7L);

        assertThat(row.getDepartmentSupervisor()).isEqualTo("部门主管");
        assertThat(row.getDirectSupervisor()).isEqualTo("直属主管");
        assertThat(row.getContractStartDate()).isEqualTo("2026-01-03");
        assertThat(row.getContractEndDate()).isEqualTo("2027-01-02");
        assertThat(row.getContractType()).isEqualTo("固定期限");
        assertThat(row.getSocialType()).isEqualTo("本地社保");
        assertThat(row.getSocialSecurityLocation()).isEqualTo("上海");
        assertThat(row.getHousingFundLocation()).isEqualTo("上海");
        assertThat(row.getLegalEntity()).isEqualTo("测试公司");
        assertThat(row.getRecruitmentChannel()).isEqualTo("校招");
        assertThat(row.getLeaveDate()).isEqualTo("2028-01-01");
        assertThat(detail.getDeptId()).isEqualTo(20L);
        assertThat(detail.getPostIds()).containsExactly(30L,31L);
        assertThat(detail.getFields()).containsEntry("accountCreateBy","account-creator")
                .containsEntry("accountUpdateBy","account-editor")
                .containsEntry("profileCreateBy","profile-creator")
                .containsEntry("profileUpdateBy","profile-editor")
                .containsKeys("accountCreateTime","accountUpdateTime","profileCreateTime","profileUpdateTime");
    }

    @Test
    void patchPreservesOmittedSensitiveValuesRejectsMasksAndDoesNotPersistDerivedFields()
    {
        SysUser employee = employee(7L, "E007", "测试员工");
        employee.getProfile().setIdNumber("350000199001010000");
        employee.getProfile().setCompanyName("派生公司");
        when(userMapper.selectHrEmployeeForUpdate(any())).thenReturn(employee);
        when(userMapper.patchHrEmployeeUser(anyLong(),any(),any())).thenReturn(1);
        when(profileMapper.patchUserProfile(anyLong(),any(),any())).thenReturn(1);

        service.update(7L, Map.of("remark", "新备注", "jobGrade", "P5"), "hr-user");

        ArgumentCaptor<Map<String,Object>> profile = ArgumentCaptor.forClass(Map.class);
        verify(profileMapper).patchUserProfile(org.mockito.ArgumentMatchers.eq(7L),profile.capture(),any());
        assertThat(profile.getValue()).containsOnlyKeys("jobGrade").containsEntry("jobGrade","P5");

        assertThatThrownBy(() -> service.update(7L, Map.of("idNumber", "3500**********0000"), "hr-user"))
                .isInstanceOf(ServiceException.class).hasMessageContaining("脱敏");
        assertThatThrownBy(() -> service.update(7L, Map.of("companyName", "手工公司"), "hr-user"))
                .isInstanceOf(ServiceException.class).hasMessageContaining("只读");
    }

    @Test
    void patchLocksAndReauthorizesAuthoritativeEmployeeBeforeAnyMutation()
    {
        SysUser locked=employee(7L,"E007","受保护员工");
        when(userMapper.selectHrEmployeeForUpdate(any())).thenReturn(locked);
        doThrow(new ServiceException("不允许操作超级管理员用户")).when(userService).checkUserAllowed(locked);

        assertThatThrownBy(()->service.update(7L,Map.of("remark","禁止修改"),"hr"))
                .isInstanceOf(ServiceException.class).hasMessageContaining("超级管理员");

        var order=inOrder(userMapper,userService);
        order.verify(userMapper).selectHrEmployeeForUpdate(any());
        order.verify(userService).checkUserAllowed(locked);
        verify(userMapper,never()).patchHrEmployeeUser(anyLong(),any(),any());
        verify(profileMapper,never()).patchUserProfile(anyLong(),any(),any());
    }

    @Test
    void patchWritesOnlyPresentStorageKeysAndChecksChangedContactsForUniqueness()
    {
        SysUser locked=employee(7L,"E007","原姓名");
        locked.setEmail("old@example.com"); locked.setPhonenumber("13800138000");
        locked.getProfile().setIdNumber("350000199001010000");
        when(userMapper.selectHrEmployeeForUpdate(any())).thenReturn(locked);
        when(userService.checkPhoneUnique(any())).thenReturn(true);
        when(userService.checkEmailUnique(any())).thenReturn(true);
        when(userMapper.patchHrEmployeeUser(anyLong(),any(),any())).thenReturn(1);
        when(profileMapper.patchUserProfile(anyLong(),any(),any())).thenReturn(1);

        service.update(7L,Map.of("email","new@example.com","phoneNumber","13900139000","jobGrade","P6"),"hr");

        ArgumentCaptor<Map<String,Object>> userPatch=ArgumentCaptor.forClass(Map.class);
        verify(userMapper).patchHrEmployeeUser(org.mockito.ArgumentMatchers.eq(7L),userPatch.capture(),
                org.mockito.ArgumentMatchers.eq("hr"));
        assertThat(userPatch.getValue()).containsOnlyKeys("email","phoneNumber")
                .containsEntry("email","new@example.com").containsEntry("phoneNumber","13900139000");
        ArgumentCaptor<Map<String,Object>> profilePatch=ArgumentCaptor.forClass(Map.class);
        verify(profileMapper).patchUserProfile(org.mockito.ArgumentMatchers.eq(7L),profilePatch.capture(),
                org.mockito.ArgumentMatchers.eq("hr"));
        assertThat(profilePatch.getValue()).containsOnlyKeys("jobGrade").containsEntry("jobGrade","P6");
        verify(userService).checkPhoneUnique(any());
        verify(userService).checkEmailUnique(any());
    }

    @Test
    void patchRejectsConflictingPhoneAndEmailButAllowsUnchangedSelfValues()
    {
        SysUser locked=employee(7L,"E007","员工");
        locked.setEmail("self@example.com"); locked.setPhonenumber("13800138000");
        when(userMapper.selectHrEmployeeForUpdate(any())).thenReturn(locked);
        when(userService.checkPhoneUnique(any())).thenReturn(false);
        assertThatThrownBy(()->service.update(7L,Map.of("phoneNumber","13900139000"),"hr"))
                .isInstanceOf(ServiceException.class).hasMessageContaining("手机号已存在");

        when(userService.checkPhoneUnique(any())).thenReturn(true);
        when(userService.checkEmailUnique(any())).thenReturn(false);
        assertThatThrownBy(()->service.update(7L,Map.of("email","taken@example.com"),"hr"))
                .isInstanceOf(ServiceException.class).hasMessageContaining("邮箱已存在");

        when(userService.checkEmailUnique(any())).thenReturn(true);
        when(userMapper.patchHrEmployeeUser(anyLong(),any(),any())).thenReturn(1);
        service.update(7L,Map.of("phoneNumber","13800138000","email","self@example.com"),"hr");
        verify(userMapper).patchHrEmployeeUser(anyLong(),any(),any());
    }

    @Test
    void canonicalPatchRejectsAccountStatusWrites()
    {
        SysUser locked=employee(7L,"E007","员工");locked.setStatus("1");
        when(userMapper.selectHrEmployeeForUpdate(any())).thenReturn(locked);
        assertThatThrownBy(()->service.update(7L,Map.of("status","0"),"hr"))
                .isInstanceOf(ServiceException.class).hasMessageContaining("未知员工档案字段");
        verify(userMapper,never()).patchHrEmployeeUser(anyLong(),any(),any());
    }

    @Test
    void legacyAdapterAcceptsExactCurrentDrawerShapeButOnlyPatchesCanonicalWritableUnmaskedFields()
    {
        SysUser locked=employee(7L,"E007","旧姓名");locked.setPhonenumber("13800138000");
        locked.getProfile().setIdNumber("350000199001010000");
        locked.getProfile().setBankAccount("6222020200001234567");
        when(userMapper.selectHrEmployeeForUpdate(any())).thenReturn(locked);
        when(userMapper.patchHrEmployeeUser(anyLong(),any(),any())).thenReturn(1);
        when(profileMapper.patchUserProfile(anyLong(),any(),any())).thenReturn(1);
        when(userService.checkEmailUnique(any())).thenReturn(true);
        when(userMapper.selectHrEmployeeList(any())).thenReturn(Collections.singletonList(employee(9L,"E009","主管")));

        Map<String,Object> profile=new LinkedHashMap<>();
        profile.put("userId",7L);profile.put("profileId",99L);profile.put("employeeNo","E999");
        profile.put("employeeStatus","正式");profile.put("companyName","派生公司");
        profile.put("deptLevel1Name","一级");profile.put("deptLevel2Name","二级");
        profile.put("deptLevel3Name","三级");profile.put("storeName","门店");
        profile.put("positionName","岗位");profile.put("positionNames","岗位");
        profile.put("workYears","9年");profile.put("companyYears","3年");profile.put("contractTerm","2年");
        profile.put("createBy","system");profile.put("createTime","2026-01-01");
        profile.put("updateBy","auditor");profile.put("updateTime","2026-01-02");
        profile.put("derivedWarnings",List.of("metadata"));
        profile.put("jobGrade","P6");profile.put("firstEducation","本科");
        profile.put("politicalStatus","群众");profile.put("healthStatus","健康");
        profile.put("recruitmentChannel","校招");
        profile.put("idNumber","3500**********0000");profile.put("bankAccount","6222***********4567");
        profile.put("currentAddress","上海*******");profile.put("emergencyContactPhone","138****8000");
        HrEmployeeFieldRegistry legacyRegistry=new HrEmployeeFieldRegistry();
        BeanWrapper profileTypes=new BeanWrapperImpl(new SysUserProfile());
        for(HrEmployeeFieldRegistry.FieldDefinition field:legacyRegistry.getFields())
        {
            if(field.getStorageOwner()==HrEmployeeFieldRegistry.StorageOwner.SYS_USER||profile.containsKey(field.getKey()))continue;
            Object value;
            if(field.getMaskingClass()!=HrEmployeeFieldRegistry.MaskingClass.NONE)value="138****8000";
            else
            {
                Class<?> type=profileTypes.getPropertyType(field.getPropertyName());
                value=type==java.util.Date.class?"2026-01-01":type==Integer.class?1:type==Long.class?9L:"已填写";
            }
            profile.put(field.getKey(),value);
        }
        Map<String,Object> payload=new LinkedHashMap<>();payload.put("userId",7L);payload.put("nickName","新姓名");
        payload.put("phonenumber","138****8000");payload.put("email","new@example.com");
        payload.put("sex","1");payload.put("status","0");payload.put("remark","新备注");payload.put("profile",profile);

        service.updateLegacy(payload,"hr-user");

        ArgumentCaptor<Map<String,Object>> userPatch=ArgumentCaptor.forClass(Map.class);
        verify(userMapper).patchHrEmployeeUser(org.mockito.ArgumentMatchers.eq(7L),userPatch.capture(),any());
        assertThat(userPatch.getValue()).containsOnlyKeys("employeeName","email","sex","remark")
                .containsEntry("employeeName","新姓名").containsEntry("email","new@example.com");
        ArgumentCaptor<Map<String,Object>> profilePatch=ArgumentCaptor.forClass(Map.class);
        verify(profileMapper).patchUserProfile(org.mockito.ArgumentMatchers.eq(7L),profilePatch.capture(),any());
        assertThat(profilePatch.getValue()).containsEntry("jobGrade","P6").containsEntry("firstEducation","本科")
                .doesNotContainKeys("employeeNo","companyName","positionName","positionNames","workYears",
                        "companyYears","contractTerm","idNumber","bankAccount","currentAddress",
                        "emergencyContactPhone","profileId","createBy","createTime","updateBy","updateTime",
                        "derivedWarnings","status");

        Map<String,Object> dangerous=new LinkedHashMap<>(payload);dangerous.put("password","pwned");
        assertThatThrownBy(()->service.updateLegacy(dangerous,"hr-user"))
                .isInstanceOf(ServiceException.class).hasMessageContaining("未知");
    }

    @Test
    void maskedDetailProvidesOldDrawerAliasesAndSafeProfileWithoutRawPii()
    {
        SysUser employee=employee(7L,"E007","测试员工");employee.setPhonenumber("13800138000");
        employee.setEmail("safe@example.com");employee.setSex("1");employee.setStatus("0");
        employee.getProfile().setIdNumber("350000199001010000");
        employee.getProfile().setBankAccount("6222020200001234567");
        employee.getProfile().setDirectSupervisor("直属主管甲");
        when(userMapper.selectHrEmployeeList(any())).thenReturn(Collections.singletonList(employee));

        HrEmployeeProfileVo detail=service.get(7L);

        assertThat(detail.getNickName()).isEqualTo("测试员工");
        assertThat(detail.getPhonenumber()).isEqualTo("138****8000");
        assertThat(detail.getEmail()).isEqualTo("safe@example.com");
        assertThat(detail.getSex()).isEqualTo("1");assertThat(detail.getStatus()).isEqualTo("0");
        assertThat(detail.getProfile()).containsEntry("userId",7L).containsEntry("employeeNo","E007")
                .containsEntry("directSupervisor","直属主管甲")
                .containsEntry("idNumber","3500**********0000")
                .containsEntry("bankAccount","6222***********4567");
        String serialized=com.alibaba.fastjson2.JSON.toJSONString(detail);
        assertThat(serialized).contains("\"fields\"","\"profile\"","\"nickName\"","138****8000")
                .doesNotContain("13800138000","350000199001010000","6222020200001234567");
    }

    @Test
    void detailNormalizesDateOnlyValuesAndResolvesOrganizationLabels()
    {
        SysUser employee=employee(7L,"E007","测试员工");
        employee.getDept().setDeptName("行政部门");
        employee.setPostNames("行政经理");
        employee.getProfile().setEntryDate(Date.from(Instant.parse("2026-06-15T16:00:00Z")));
        when(userMapper.selectHrEmployeeList(any())).thenReturn(Collections.singletonList(employee));

        HrEmployeeProfileVo detail=service.get(7L);

        assertThat(detail.getFields()).containsEntry("entryDate","2026-06-16");
        assertThat(detail.getProfile()).containsEntry("entryDate","2026-06-16");
        assertThat(detail.getDepartmentName()).isEqualTo("行政部门");
        assertThat(detail.getPostNames()).isEqualTo("行政经理");
    }

    @Test
    void formalEmployeeNumberIsReadOnlyAndCompletenessIsCalculatedByTheServer()
    {
        SysUser employee = employee(7L, "E007", "测试员工");
        employee.getProfile().setEmployeeStatus("正式");
        when(userMapper.selectHrEmployeeForUpdate(any())).thenReturn(employee);
        when(userMapper.selectHrEmployeeList(any())).thenReturn(Collections.singletonList(employee));

        assertThatThrownBy(() -> service.update(7L, Map.of("employeeNo", "E999"), "hr-user"))
                .isInstanceOf(ServiceException.class).hasMessageContaining("工号");
        assertThat(service.get(7L).getProfileCompletionPercent()).isBetween(0, 99);
        assertThat(service.get(7L).getMissingProfileFields()).isNotEmpty();
        verify(profileMapper, never()).patchUserProfile(anyLong(),any(),any());
    }

    @Test
    void initializeProfileCreatesOnlyTheMissingRowAndReturnsAnEditableProfile()
    {
        SysUser employee=employee(7L,null,"缺档员工");employee.setProfile(null);
        SysUserProfile persisted=new SysUserProfile();persisted.setProfileId(70L);persisted.setUserId(7L);
        when(userMapper.selectHrEmployeeForUpdate(any())).thenReturn(employee);
        when(profileMapper.selectUserProfileByUserId(7L)).thenReturn(null,persisted);
        when(profileMapper.insertUserProfileIfAbsent(7L,"hr-user")).thenReturn(1);

        HrEmployeeProfileVo result=service.initializeProfile(7L,"hr-user");

        assertThat(result.getUserId()).isEqualTo(7L);
        assertThat(result.getProfileInitialized()).isTrue();
        ArgumentCaptor<HrEmployeeQuery> lockQuery=ArgumentCaptor.forClass(HrEmployeeQuery.class);
        verify(userMapper).selectHrEmployeeForUpdate(lockQuery.capture());
        assertThat(lockQuery.getValue().getParams()).containsEntry("_hrActiveGovernanceOnly",true);
        verify(profileMapper).insertUserProfileIfAbsent(7L,"hr-user");
        verify(userService).checkUserAllowed(employee);
    }

    @Test
    void initializeProfileIsIdempotentWhenTheProfileAlreadyExists()
    {
        SysUser employee=employee(7L,"E007","已有档案");
        SysUserProfile persisted=employee.getProfile();persisted.setProfileId(70L);
        when(userMapper.selectHrEmployeeForUpdate(any())).thenReturn(employee);
        when(profileMapper.selectUserProfileByUserId(7L)).thenReturn(persisted);

        HrEmployeeProfileVo result=service.initializeProfile(7L,"hr-user");

        assertThat(result.getProfileInitialized()).isTrue();
        verify(profileMapper,never()).insertUserProfileIfAbsent(anyLong(),any());
    }

    @Test
    void initializeProfileAbsorbsAConcurrentDuplicateAndRejectsAnUnpersistedResult()
    {
        SysUser employee=employee(7L,null,"并发员工");employee.setProfile(null);
        SysUserProfile concurrent=new SysUserProfile();concurrent.setProfileId(71L);concurrent.setUserId(7L);
        when(userMapper.selectHrEmployeeForUpdate(any())).thenReturn(employee);
        when(profileMapper.selectUserProfileByUserId(7L)).thenReturn(null,concurrent);
        when(profileMapper.insertUserProfileIfAbsent(7L,"hr-user")).thenReturn(0);

        assertThat(service.initializeProfile(7L,"hr-user").getProfileInitialized()).isTrue();

        org.mockito.Mockito.clearInvocations(userMapper,profileMapper,userService);
        employee.setProfile(null);
        when(userMapper.selectHrEmployeeForUpdate(any())).thenReturn(employee);
        when(profileMapper.selectUserProfileByUserId(7L)).thenReturn(null);
        when(profileMapper.insertUserProfileIfAbsent(7L,"hr-user")).thenReturn(0);
        assertThatThrownBy(()->service.initializeProfile(7L,"hr-user"))
                .isInstanceOf(ServiceException.class).hasMessage("员工档案初始化失败");
    }

    @Test
    void accessBoundaryUsesARealDataScopeProxyAndMapperBoundSqlPlaceholder() throws Exception
    {
        Method list = HrEmployeeAccessService.class.getMethod("listScoped", HrEmployeeQuery.class);
        Method activeList = HrEmployeeAccessService.class.getMethod("listActiveScoped", HrEmployeeQuery.class);
        Method lock = HrEmployeeAccessService.class.getMethod("lockScoped", HrEmployeeQuery.class);
        Method activeLock = HrEmployeeAccessService.class.getMethod("lockActiveScoped", HrEmployeeQuery.class);
        assertThat(list.getAnnotation(DataScope.class)).isNotNull();
        assertThat(list.getAnnotation(DataScope.class).deptAlias()).isEqualTo("d");
        assertThat(activeList.getAnnotation(DataScope.class).deptAlias()).isEqualTo("d");
        assertThat(lock.getAnnotation(DataScope.class).deptAlias()).isEqualTo("d");
        assertThat(activeLock.getAnnotation(DataScope.class).deptAlias()).isEqualTo("d");

        AspectJProxyFactory factory = new AspectJProxyFactory(accessService);
        factory.setProxyTargetClass(true);
        factory.addAspect(new DataScopeAspect());
        HrEmployeeAccessService proxy = factory.getProxy();
        when(userMapper.selectHrEmployeeList(any())).thenReturn(Collections.emptyList());
        when(userMapper.selectHrEmployeeForUpdate(any())).thenReturn(employee(7L,"E007","锁定员工"));
        loginAsDepartmentScopedUser();
        try
        {
            proxy.listScoped(new HrEmployeeQuery());
            ArgumentCaptor<HrEmployeeQuery> query = ArgumentCaptor.forClass(HrEmployeeQuery.class);
            verify(userMapper).selectHrEmployeeList(query.capture());
            assertThat(query.getValue().getParams().get("dataScope")).asString().contains("d.dept_id = 20");
            assertThat(query.getValue().getParams()).containsEntry("_hrActiveGovernanceOnly",false);
            clearInvocations(userMapper);
            HrEmployeeQuery hostileQuery=new HrEmployeeQuery();
            hostileQuery.getParams().put("_hrActiveGovernanceOnly",false);
            proxy.listActiveScoped(hostileQuery);
            verify(userMapper).selectHrEmployeeList(query.capture());
            assertThat(query.getValue().getParams()).containsEntry("_hrActiveGovernanceOnly",true);
            HrEmployeeQuery lockQuery=new HrEmployeeQuery();lockQuery.setUserId(7L);
            proxy.lockScoped(lockQuery);
            assertThat(lockQuery.getParams().get("dataScope")).asString().contains("d.dept_id = 20");
            assertThat(lockQuery.getParams()).containsEntry("_hrActiveGovernanceOnly",false);
        }
        finally
        {
            com.erp.common.core.context.SecurityContextHolder.remove();
        }
    }

    @Test
    void formOptionsAndDerivedPreviewValidateOrganizationThroughScopedAccessBoundary() throws Exception
    {
        Method departments=HrEmployeeAccessService.class.getMethod("listScopedDepartments",SysDept.class);
        Method department=HrEmployeeAccessService.class.getMethod("requireScopedDepartment",Long.class);
        Method supervisors=HrEmployeeAccessService.class.getMethod("listScopedUserOptions",SysUser.class);
        assertThat(departments.getAnnotation(DataScope.class).deptAlias()).isEqualTo("d");
        assertThat(department.getAnnotation(DataScope.class).deptAlias()).isEqualTo("d");
        assertThat(supervisors.getAnnotation(DataScope.class).deptAlias()).isEqualTo("d");
        SysDept allowed=new SysDept(); allowed.setDeptId(20L); allowed.setDeptName("研发部");
        when(deptMapper.selectDeptList(any())).thenReturn(Collections.singletonList(allowed));

        SysPost post=post(30L,"开发工程师","0");
        when(postMapper.selectPostList(any())).thenReturn(Collections.singletonList(post));
        when(onboardingConfigService.loadDictionaries(any())).thenReturn(
                Map.of("sex",List.of(Map.of("label","女","value","1"))));

        Map<String,Object> options=service.formOptions();
        assertThat(options).containsKeys("fields","departments","supervisors","posts","dictionaries","enumOptions");
        assertThat(options.get("enumOptions").toString()).contains("employeeStatus","foreignNationalFlag");
        ArgumentCaptor<List<String>> routedFields=ArgumentCaptor.forClass(List.class);
        verify(onboardingConfigService).loadDictionaries(routedFields.capture());
        assertThat(routedFields.getValue()).hasSize(77).contains("sex","bloodType","attendanceMethod","socialType",
                "studentStatus", "retirementStatus");
        verify(deptMapper).selectDeptList(any());
        verify(onboardingMapper).selectScopedUserOptions(any());
        verify(userMapper,never()).selectUserList(any());
    }

    @Test
    void patchSupportsDepartmentOnlyPostOnlyAndPreservesOmittedRelations()
    {
        SysUser employee=employee(7L,"E007","测试员工");
        SysDept allowed=new SysDept(); allowed.setDeptId(30L); allowed.setDeptName("新部门");
        when(userMapper.selectHrEmployeeForUpdate(any())).thenReturn(employee);
        when(deptMapper.selectDeptList(any())).thenReturn(Collections.singletonList(allowed));
        when(postMapper.selectPostById(40L)).thenReturn(post(40L,"工程师","0"));
        when(userMapper.patchHrEmployeeUser(anyLong(),any(),any())).thenReturn(1);
        when(userPostMapper.batchUserPost(any())).thenReturn(1);

        service.update(7L,Map.of("deptId",30L),"hr-user");

        ArgumentCaptor<Map<String,Object>> user=ArgumentCaptor.forClass(Map.class);
        verify(userMapper).patchHrEmployeeUser(org.mockito.ArgumentMatchers.eq(7L),user.capture(),any());
        assertThat(user.getValue()).containsOnlyKeys("deptId").containsEntry("deptId",30L);
        verify(userPostMapper,never()).deleteUserPostByUserId(anyLong());

        org.mockito.Mockito.clearInvocations(userMapper,profileMapper,userPostMapper);
        service.update(7L,Map.of("postIds",List.of(40L)),"hr-user");
        verify(userPostMapper).deleteUserPostByUserId(7L);
        ArgumentCaptor<List<SysUserPost>> posts=ArgumentCaptor.forClass(List.class);
        verify(userPostMapper).batchUserPost(posts.capture());
        assertThat(posts.getValue()).extracting(SysUserPost::getPostId).containsExactly(40L);

        org.mockito.Mockito.clearInvocations(userPostMapper);
        service.update(7L,Map.of("remark","关系保持"),"hr-user");
        verify(userPostMapper,never()).deleteUserPostByUserId(anyLong());
        verify(userPostMapper,never()).batchUserPost(any());
    }

    @Test
    void relationPatchRejectsOutOfScopeDepartmentInactivePostAndAmbiguousPostShapes()
    {
        SysUser employee=employee(7L,"E007","测试员工");
        when(userMapper.selectHrEmployeeForUpdate(any())).thenReturn(employee);
        when(deptMapper.selectDeptList(any())).thenReturn(Collections.emptyList());
        when(postMapper.selectPostById(40L)).thenReturn(post(40L,"停用岗","1"));

        assertThatThrownBy(()->service.update(7L,Map.of("deptId",99L),"hr"))
                .isInstanceOf(ServiceException.class).hasMessageContaining("无权");
        assertThatThrownBy(()->service.update(7L,Map.of("postIds",List.of(40L)),"hr"))
                .isInstanceOf(ServiceException.class).hasMessageContaining("岗位");
        Map<String,Object> ambiguous=new LinkedHashMap<>(); ambiguous.put("postId",40L);
        ambiguous.put("postIds",List.of(41L));
        assertThatThrownBy(()->service.update(7L,ambiguous,"hr"))
                .isInstanceOf(ServiceException.class).hasMessageContaining("岗位");
        verify(userMapper,never()).patchHrEmployeeUser(anyLong(),any(),any());
    }

    @Test
    void postRelationFailurePropagatesInsideRollbackTransaction() throws Exception
    {
        SysUser employee=employee(7L,"E007","测试员工");
        when(userMapper.selectHrEmployeeForUpdate(any())).thenReturn(employee);
        when(postMapper.selectPostById(40L)).thenReturn(post(40L,"正常岗","0"));
        when(userPostMapper.batchUserPost(any())).thenThrow(new IllegalStateException("post relation failed"));

        assertThatThrownBy(()->service.update(7L,Map.of("postId",40L),"hr"))
                .isInstanceOf(IllegalStateException.class).hasMessage("post relation failed");
        Transactional tx=HrEmployeeProfileServiceImpl.class
                .getMethod("update",Long.class,Map.class,String.class).getAnnotation(Transactional.class);
        assertThat(tx).isNotNull(); assertThat(tx.rollbackFor()).containsExactly(Exception.class);
    }

    @Test
    void derivedPreviewNormalizesRelationShapeAndValidatesPosts()
    {
        SysDept dept=new SysDept(); dept.setDeptId(30L);
        when(deptMapper.selectDeptList(any())).thenReturn(Collections.singletonList(dept));
        when(postMapper.selectPostById(40L)).thenReturn(post(40L,"工程师","0"));
        SysUserProfile preview=new SysUserProfile(); preview.setCompanyName("预览公司"); preview.setPositionNames("工程师");
        when(derivationService.preview(any())).thenReturn(preview);

        HrEmployeeProfileVo result=service.derivedPreview(Map.of("deptId",30L,"postIds",List.of(40L)));

        assertThat(result.getFields()).containsEntry("companyName","预览公司").containsEntry("positionName","工程师");
        ArgumentCaptor<SysUser> input=ArgumentCaptor.forClass(SysUser.class);
        verify(derivationService).preview(input.capture());
        assertThat(input.getValue().getDeptId()).isEqualTo(30L);
        assertThat(input.getValue().getPostIds()).containsExactly(40L);
    }

    @Test
    void realDerivedPreviewHandlesRelationOnlyAndSysUserOnlyInputWithoutNullProfile()
    {
        SysDept group=new SysDept(); group.setDeptId(1L); group.setParentId(0L); group.setAncestors("0");
        group.setDeptName("集团"); group.setDeptType("GROUP"); group.setStatus("0");
        SysDept company=new SysDept(); company.setDeptId(2L); company.setParentId(1L); company.setAncestors("0,1");
        company.setDeptName("公司"); company.setDeptType("COMPANY"); company.setStatus("0");
        when(deptMapper.selectDeptList(any())).thenReturn(List.of(group,company));
        when(postMapper.selectPostById(40L)).thenReturn(post(40L,"工程师","0"));
        when(postMapper.selectPostAll()).thenReturn(List.of(post(40L,"工程师","0")));
        SysUserProfileDerivationService realDerivation=new SysUserProfileDerivationService(deptMapper,postMapper);
        HrEmployeeProfileServiceImpl realService=new HrEmployeeProfileServiceImpl(accessService,userMapper,profileMapper,
                auditMapper,realDerivation,new HrEmployeeFieldRegistry(),new HrSensitiveFieldMasker(),postMapper,
                userPostMapper,onboardingConfigService,userService);

        assertThat(realService.derivedPreview(Map.of("deptId",2L,"postId",40L)).getFields())
                .containsEntry("companyName","公司").containsEntry("positionName","工程师");
        assertThat(realService.derivedPreview(Map.of("employeeName","仅姓名")).getEmployeeName())
                .isEqualTo("仅姓名");
    }

    @Test
    void everySensitivePatchFieldRejectsUnicodeAndAsciiMaskGlyphsButAllowsRealValues()
    {
        SysUser employee=employee(7L,"E007","测试员工");
        when(userMapper.selectHrEmployeeForUpdate(any())).thenReturn(employee);
        for(String key:new HrEmployeeFieldRegistry().getSensitiveRevealKeys())
        {
            if (!new HrEmployeeFieldRegistry().getByKey(key).isEditable()) continue;
            for(String masked:List.of("138••••0013","3500●●0000","６２２２＊＊１２３４","真实○○掩码"))
                assertThatThrownBy(()->service.update(7L,Map.of(key,masked),"hr"))
                        .as(key+":"+masked).isInstanceOf(ServiceException.class).hasMessageContaining("脱敏");
        }
        when(profileMapper.patchUserProfile(anyLong(),any(),any())).thenReturn(1);
        service.update(7L,Map.of("currentAddress","上海市浦东新区世纪大道"),"hr");
        service.update(7L,Map.of("currentAddress","上海市中·心路1号"),"hr");
    }

    @Test
    void completenessCountsFormalPostEntryFieldsAndKeepsConditionalFieldsApplicable()
    {
        SysUser coreOnly=coreComplete(employee(1L,"E001","仅核心字段"),"正式");
        SysUser complete=coreComplete(employee(2L,"E002","完整员工"),"正式"); fillCompletenessFields(complete);
        SysUser trial=coreComplete(employee(3L,"E003","试用员工"),"试用"); fillCompletenessFields(trial);
        trial.getProfile().setProbationPeriod(null); trial.getProfile().setPlannedRegularizationDate(null);
        SysUser departed=coreComplete(employee(4L,"E004","离职员工"),"离职"); fillCompletenessFields(departed);
        departed.getProfile().setLeaveDate(null);
        SysUser foreign=coreComplete(employee(5L,"E005","外籍员工"),"正式"); fillCompletenessFields(foreign);
        foreign.getProfile().setForeignNationalFlag("1"); foreign.getProfile().setNationality(null);
        foreign.getProfile().setEthnicity(null);
        SysUser contract=coreComplete(employee(6L,"E006","合同员工"),"正式"); fillCompletenessFields(contract);
        contract.getProfile().setContractType("固定期限"); contract.getProfile().setContractEndDate(null);
        SysUser invalidDerived=coreComplete(employee(7L,"E007","无效组织"),"正式");fillCompletenessFields(invalidDerived);
        invalidDerived.getDept().setStatus("1");
        SysUser noContract=coreComplete(employee(8L,"E008","合同不适用"),"正式");fillCompletenessFields(noContract);
        noContract.getProfile().setContractType("NOT_APPLICABLE");noContract.getProfile().setContractStartDate(null);
        noContract.getProfile().setContractEndDate(null);noContract.getProfile().setRenewalCount(null);
        noContract.getProfile().setLegalEntity(null);noContract.getProfile().setContractTerm(null);
        SysUser missingSupervisor=coreComplete(employee(9L,"E009","主管缺失"),"正式");fillCompletenessFields(missingSupervisor);
        missingSupervisor.getProfile().setDepartmentSupervisor(null);
        missingSupervisor.getProfile().setDerivedWarnings(List.of(SysUserProfileDerivationService.SUPERVISOR_WARNING));
        List<SysUser> employees=List.of(coreOnly,complete,trial,departed,foreign,contract,invalidDerived,
                noContract,missingSupervisor);
        when(userMapper.selectHrEmployeeList(any())).thenAnswer(invocation->{
            HrEmployeeQuery query=invocation.getArgument(0);
            return query.getUserId()==null?employees:employees.stream()
                    .filter(user->query.getUserId().equals(user.getUserId())).toList();
        });

        List<HrEmployeeListVo> rows=service.list(new HrEmployeeQuery());

        HrEmployeeListVo completeRow=rows.get(1);
        assertThat(completeRow.getProfileCompletedFieldCount())
                .isEqualTo(completeRow.getProfileApplicableFieldCount());
        assertThat(completeRow.getProfileTrackedFieldCount())
                .isEqualTo(completeRow.getProfileApplicableFieldCount()
                        + completeRow.getProfileNotApplicableFieldCount());
        assertThat(completeRow.getAccountEnabled()).isTrue();
        assertThat(rows.get(0).getMissingProfileFields()).contains("firstEducation","bankAccount");
        HrEmployeeProfileVo detail=service.get(1L);
        assertThat(detail.getProfileCompletionPercent()).isEqualTo(rows.get(0).getProfileCompletionPercent());
        assertThat(detail.getProfileApplicableFieldCount()).isEqualTo(rows.get(0).getProfileApplicableFieldCount());
        assertThat(rows.get(0).getProfileCompletionPercent()).isLessThan(100);
        assertThat(service.get(1L).getMissingProfileFields()).contains("firstEducation","politicalStatus",
                "healthStatus","recruitmentChannel","bankAccount","contractStartDate","contractType","contractTerm");
        assertThat(rows.get(1).getProfileCompletionPercent()).isEqualTo(100);
        assertThat(service.get(2L).getMissingProfileFields()).doesNotContain("probationPeriod","leaveDate","storeName");
        assertThat(service.get(3L).getMissingProfileFields()).contains("probationPeriod","plannedRegularizationDate");
        assertThat(service.get(4L).getMissingProfileFields()).contains("leaveDate");
        assertThat(service.get(5L).getMissingProfileFields()).contains("nationality").doesNotContain("ethnicity");
        assertThat(service.get(6L).getMissingProfileFields()).contains("contractEndDate","contractTerm");
        assertThat(service.get(7L).getMissingProfileFields()).contains("companyName");
        assertThat(service.get(8L).getMissingProfileFields()).doesNotContain("contractStartDate","contractEndDate",
                "contractType","contractTerm","renewalCount","legalEntity");
        assertThat(service.get(9L).getMissingProfileFields()).contains("departmentSupervisor");
        int average=(int)rows.stream().mapToInt(HrEmployeeListVo::getProfileCompletionPercent).average().orElse(0);
        assertThat(service.summary(new HrEmployeeQuery()).getAverageProfileCompletionPercent()).isEqualTo(average);
        assertThat(service.completenessDepartments(new HrEmployeeQuery())).singleElement()
                .satisfies(dept->assertThat(dept.get("profileCompletionPercent")).isEqualTo(average));
    }

    private SysUser employee(Long id, String employeeNo, String name)
    {
        SysUser user = new SysUser();
        user.setUserId(id);
        user.setStatus("0");
        user.setDelFlag("0");
        user.setNickName(name);
        user.setDeptId(20L);
        SysDept dept = new SysDept();
        dept.setDeptId(20L);
        dept.setDeptName("研发部");
        user.setDept(dept);
        dept.setStatus("0");
        SysUserProfile profile = new SysUserProfile();
        profile.setUserId(id);
        profile.setEmployeeNo(employeeNo);
        user.setProfile(profile);
        return user;
    }

    private SysPost post(Long id,String name,String status)
    { SysPost post=new SysPost();post.setPostId(id);post.setPostName(name);post.setPostSort(1);post.setStatus(status);return post; }

    private SysUser coreComplete(SysUser user,String status)
    {
        user.setPhonenumber("13800138000");user.setSex("0");
        SysUserProfile p=user.getProfile();p.setEmployeeStatus(status);p.setEmployeeCategory("FORMAL");
        p.setEntryDate(new java.util.Date());p.setCurrentPositionStartDate(new java.util.Date());
        p.setJobGrade("P5");p.setIdType("身份证");p.setIdNumber("350000199001010000");
        p.setBirthDate(new java.util.Date());p.setWorkLocation("上海");
        return user;
    }

    private void fillCompletenessFields(SysUser user)
    {
        HrEmployeeFieldRegistry registry = new HrEmployeeFieldRegistry();
        for (HrEmployeeFieldRegistry.FieldDefinition field : registry.getFields())
        {
            if (!field.isProfileCompleteness() || registry.read(user, field) != null) continue;
            Object bean = field.getStorageOwner() == HrEmployeeFieldRegistry.StorageOwner.SYS_USER
                    ? user : user.getProfile();
            BeanWrapper wrapper = new BeanWrapperImpl(bean);
            Class<?> type = wrapper.getPropertyType(field.getPropertyName());
            Object value = type == java.util.Date.class ? new java.util.Date()
                    : type == Integer.class ? 1 : type == Long.class ? 1L : "已填写";
            wrapper.setPropertyValue(field.getPropertyName(), value);
        }
        user.setPostNames("工程师");
    }

    private void loginAsDepartmentScopedUser()
    {
        SysRole role = new SysRole();
        role.setRoleId(2L);
        role.setDataScope("3");
        role.setStatus("0");
        SysUser current = new SysUser();
        current.setUserId(9L);
        current.setDeptId(20L);
        current.setRoles(Collections.singletonList(role));
        LoginUser login = new LoginUser();
        login.setSysUser(current);
        com.erp.common.core.context.SecurityContextHolder.set(
                com.erp.common.core.constant.SecurityConstants.LOGIN_USER, login);
        com.erp.common.core.context.SecurityContextHolder.setUserId("9");
    }
}
