package com.erp.system.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

import com.erp.common.core.constant.Constants;
import com.erp.common.core.constant.SecurityConstants;
import com.erp.common.core.constant.UserConstants;
import com.erp.common.core.context.SecurityContextHolder;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.datascope.annotation.DataScope;
import com.erp.common.datascope.aspect.DataScopeAspect;
import com.erp.system.api.domain.SysRole;
import com.erp.system.api.domain.SysUser;
import com.erp.system.api.domain.SysDept;
import com.erp.system.api.model.LoginUser;
import com.erp.system.domain.HrOnboarding;
import com.erp.system.domain.HrOnboardingOperationLog;
import com.erp.system.domain.HrOnboardingPositionConfig;
import com.erp.system.domain.SysPost;
import com.erp.system.domain.vo.HrOnboardingCancelRequest;
import com.erp.system.domain.vo.HrOnboardingCreateRequest;
import com.erp.system.domain.vo.HrOnboardingDetailVo;
import com.erp.system.domain.vo.HrOnboardingOwnerOptionVo;
import com.erp.system.domain.vo.HrOnboardingOwnerQuery;
import com.erp.system.domain.vo.HrOnboardingQuery;
import com.erp.system.domain.vo.HrOnboardingSummaryVo;
import com.erp.system.domain.vo.HrOnboardingUpdateRequest;
import com.erp.system.exception.HrOnboardingValidationException;
import com.erp.system.mapper.HrOnboardingMapper;
import com.erp.system.mapper.HrOnboardingOperationLogMapper;
import com.erp.system.mapper.SysDeptMapper;
import com.erp.system.mapper.SysPostMapper;
import com.erp.system.mapper.SysUserMapper;
import com.erp.system.service.IHrOnboardingPositionConfigService;
import com.erp.system.support.HrOnboardingFieldRegistry;
import com.erp.system.support.HrOnboardingNoGenerator;
import com.erp.system.support.HrSensitiveFieldMasker;
import com.github.pagehelper.PageHelper;

@ExtendWith(MockitoExtension.class)
class HrOnboardingServiceImplTest
{
    @Mock private HrOnboardingMapper mapper;
    @Mock private HrOnboardingOperationLogMapper logMapper;
    @Mock private HrOnboardingAccessService accessService;
    @Mock private HrOnboardingNoGenerator noGenerator;
    @Mock private SysPostMapper formPostMapper;
    @Mock private IHrOnboardingPositionConfigService positionConfigService;

    private HrOnboardingServiceImpl service;

    @BeforeEach
    void setUp()
    {
        HrOnboardingRuleService rules = new HrOnboardingRuleService(new HrOnboardingFieldRegistry(), null,
                Clock.fixed(Instant.parse("2026-07-11T00:00:00Z"), ZoneOffset.UTC));
        service = new HrOnboardingServiceImpl(mapper, logMapper, accessService, rules,
                new HrSensitiveFieldMasker(), noGenerator,
                Clock.fixed(Instant.parse("2026-07-11T00:00:00Z"), ZoneOffset.UTC), () -> 9L);
    }

    @Test
    void transitionValidatorAllowsOnlyApprovedStateMachine()
    {
        for (String[] transition : Arrays.asList(
                pair("DRAFT", "READY"), pair("DRAFT", "CANCELLED"),
                pair("READY", "DRAFT"), pair("READY", "CONFIRMED"),
                pair("READY", "CANCELLED"), pair("CANCELLED", "DRAFT")))
        {
            service.validateTransition(transition[0], transition[1]);
        }

        for (String from : Arrays.asList("DRAFT", "READY", "CONFIRMED", "CANCELLED"))
        {
            for (String to : Arrays.asList("DRAFT", "READY", "CONFIRMED", "CANCELLED"))
            {
                if (isAllowed(from, to)) continue;
                assertThatThrownBy(() -> service.validateTransition(from, to))
                        .isInstanceOf(ServiceException.class);
            }
        }
    }

    @Test
    void ownerOptionsNormalizesBoundInputsBeforeDelegatingToTheScopedBoundary()
    {
        PageHelper.clearPage();
        HrOnboardingOwnerOptionVo option = new HrOnboardingOwnerOptionVo();
        option.setUserId(9L);
        option.setLabel("招聘负责人");
        when(accessService.listScopedOwnerOptions(any())).thenAnswer(invocation -> {
            assertThat(PageHelper.getLocalPage()).isNotNull();
            assertThat(PageHelper.getLocalPage().getPageNum()).isEqualTo(2);
            return Collections.singletonList(option);
        });
        org.mockito.Mockito.doAnswer(invocation -> {
            assertThat(PageHelper.getLocalPage()).isNull();
            return null;
        }).when(accessService).validateScopedOwnerDepartment(any());
        HrOnboardingOwnerQuery query = new HrOnboardingOwnerQuery();
        query.setKeyword("  招聘  ");
        query.setUserIds(Arrays.asList(9L, null, 9L, -1L, 8L));

        List<HrOnboardingOwnerOptionVo> rows = service.ownerOptions(query, 2, 20);

        assertThat(rows).containsExactly(option);
        ArgumentCaptor<HrOnboardingOwnerQuery> normalized = ArgumentCaptor.forClass(HrOnboardingOwnerQuery.class);
        verify(accessService).listScopedOwnerOptions(normalized.capture());
        assertThat(normalized.getValue().getKeyword()).isEqualTo("招聘");
        assertThat(normalized.getValue().getUserIds()).containsExactly(9L, 8L);
        assertThat(normalized.getValue().getIncludeChildren()).isTrue();
        org.mockito.InOrder order = org.mockito.Mockito.inOrder(accessService);
        order.verify(accessService).validateScopedOwnerDepartment(normalized.getValue());
        order.verify(accessService).listScopedOwnerOptions(normalized.getValue());
        assertThat(PageHelper.getLocalPage()).isNull();
    }

    @Test
    void ownerOptionsRejectsOversizedKeywordAndHydrationSetBeforeQuerying()
    {
        HrOnboardingOwnerQuery keyword = new HrOnboardingOwnerQuery();
        keyword.setKeyword(String.join("", Collections.nCopies(65, "字")));
        assertThatThrownBy(() -> service.ownerOptions(keyword, 1, 20))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("64");

        HrOnboardingOwnerQuery ids = new HrOnboardingOwnerQuery();
        ids.setUserIds(Arrays.asList(1L, 2L, 3L, 4L, 5L, 6L, 7L));
        assertThatThrownBy(() -> service.ownerOptions(ids, 1, 20))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("6");
        verify(accessService, never()).listScopedOwnerOptions(any());
        verify(accessService, never()).validateScopedOwnerDepartment(any());
    }

    @Test
    void leanMobileFormOptionsSkipBothFullPeopleLists()
    {
        HrOnboardingServiceImpl formService = new HrOnboardingServiceImpl(
                mapper, logMapper, accessService,
                new HrOnboardingRuleService(new HrOnboardingFieldRegistry(), null,
                        Clock.fixed(Instant.parse("2026-07-11T00:00:00Z"), ZoneOffset.UTC)),
                new HrSensitiveFieldMasker(), noGenerator,
                Clock.fixed(Instant.parse("2026-07-11T00:00:00Z"), ZoneOffset.UTC),
                () -> 9L, formPostMapper, positionConfigService);
        when(accessService.listScopedDepartments(any())).thenReturn(Collections.emptyList());
        when(formPostMapper.selectPostList(any())).thenReturn(Collections.emptyList());
        when(positionConfigService.options()).thenReturn(Collections.emptyMap());
        when(positionConfigService.list(any())).thenReturn(Collections.emptyList());

        Map<String, Object> result = formService.formOptions(false, false);

        assertThat((List<?>) result.get("owners")).isEmpty();
        assertThat((List<?>) result.get("supervisors")).isEmpty();
        verify(accessService, never()).listScopedUsers(any());
    }

    @Test
    void formOptionsContainOnlyScopedPeopleAndOrganizationsWithUnifiedOptionShape()
    {
        HrOnboardingServiceImpl formService = new HrOnboardingServiceImpl(mapper, logMapper, accessService,
                new HrOnboardingRuleService(new HrOnboardingFieldRegistry(), null,
                        Clock.fixed(Instant.parse("2026-07-11T00:00:00Z"), ZoneOffset.UTC)),
                new HrSensitiveFieldMasker(), noGenerator,
                Clock.fixed(Instant.parse("2026-07-11T00:00:00Z"), ZoneOffset.UTC), () -> 9L,
                formPostMapper, positionConfigService);
        SysDept organization = new SysDept();
        organization.setDeptId(10L); organization.setDeptName("华东公司"); organization.setDeptType("COMPANY");
        SysDept store = new SysDept();
        store.setDeptId(11L); store.setDeptName("厦门店"); store.setDeptType("STORE");
        when(accessService.listScopedDepartments(any(SysDept.class))).thenReturn(Arrays.asList(organization, store));
        SysUser user = new SysUser();
        user.setUserId(12L); user.setNickName("范围内主管"); user.setPhonenumber("13800138000");
        when(accessService.listScopedUsers(any(SysUser.class))).thenReturn(Collections.singletonList(user));
        SysPost post = new SysPost();
        post.setPostId(13L); post.setPostName("店长"); post.setStatus("0");
        when(formPostMapper.selectPostList(any())).thenReturn(Collections.singletonList(post));
        Map<String, Object> configOptions = new LinkedHashMap<>();
        Map<String, java.util.List<Map<String, Object>>> dictionaries = new LinkedHashMap<>();
        for (String key : HrOnboardingPositionConfigServiceImpl.DICTIONARY_FIELDS)
            dictionaries.put(key, Collections.singletonList(option("标签-" + key, "value-" + key)));
        configOptions.put("dictionaryDefaults", dictionaries);
        configOptions.put("missingDictionaryMappings", Collections.singletonList("ethnicity"));
        when(positionConfigService.options()).thenReturn(configOptions);
        HrOnboardingPositionConfig defaults = new HrOnboardingPositionConfig();
        defaults.setConfigId(20L); defaults.setPostId(13L); defaults.setEmployeeCategory("FORMAL");
        defaults.setContractTypeMode("REQUIRED"); defaults.setSocialTypeMode("OPTIONAL");
        defaults.setProbationPeriodMode("NOT_APPLICABLE"); defaults.setStatus("0");
        when(positionConfigService.list(any())).thenReturn(Collections.singletonList(defaults));

        Map<String, Object> result = formService.formOptions();

        assertThat((java.util.List<?>) result.get("organizations")).singleElement()
                .isEqualTo(option("华东公司", 10L));
        assertThat((java.util.List<?>) result.get("stores")).singleElement()
                .isEqualTo(option("厦门店", 11L));
        assertThat((java.util.List<?>) result.get("supervisors")).singleElement()
                .isEqualTo(option("范围内主管", 12L));
        assertThat(result.toString()).doesNotContain("13800138000");
        assertThat((java.util.List<?>) result.get("employeeCategories")).singleElement()
                .isEqualTo(option("标签-employeeCategory", "value-employeeCategory"));
        assertThat((java.util.List<?>) result.get("resolvedPositionDefaults")).singleElement()
                .asString().contains("contractTypeMode=REQUIRED", "socialTypeMode=OPTIONAL",
                        "probationPeriodMode=NOT_APPLICABLE");
        assertThat(result.get("missingDictionaryMappings")).isEqualTo(Collections.singletonList("ethnicity"));
    }

    @Test
    void editIsDraftOnlyAndConfirmedIsTerminal()
    {
        HrOnboardingUpdateRequest input = new HrOnboardingUpdateRequest();
        input.setVersion(2);
        HrOnboarding ready = row("READY", 2);
        when(accessService.lockScopedForUpdate(argThat(q -> q != null && Long.valueOf(1L).equals(q.getOnboardingId())))).thenReturn(ready);

        assertThatThrownBy(() -> service.update(1L, input, "operator"))
                .isInstanceOf(ServiceException.class);
        verify(mapper, never()).updateOnboardingByVersion(any());

        HrOnboarding confirmed = row("CONFIRMED", 2);
        when(accessService.lockScopedForUpdate(argThat(q -> q != null && Long.valueOf(2L).equals(q.getOnboardingId())))).thenReturn(confirmed);
        assertThatThrownBy(() -> service.cancel(2L, 2, "撤销", "operator"))
                .isInstanceOf(ServiceException.class);
    }

    @Test
    void cancelRequiresReason()
    {
        assertThatThrownBy(() -> service.cancel(1L, 3, "  ", "operator"))
                .isInstanceOf(HrOnboardingValidationException.class)
                .extracting("errorCode").isEqualTo("CANCEL_REASON_REQUIRED");
    }

    @Test
    void cancelAndRestoreRetainTheSamePrivacySafeReasonAuditWithoutLoggingPii()
    {
        java.util.List<HrOnboardingOperationLog> storedLogs = new ArrayList<>();
        when(logMapper.insertOperationLog(any())).thenAnswer(invocation -> {
            HrOnboardingOperationLog entry = invocation.getArgument(0);
            entry.setLogId(101L + storedLogs.size());
            storedLogs.add(entry);
            return 1;
        });
        when(logMapper.selectByOnboardingId(1L)).thenAnswer(ignored -> new ArrayList<>(storedLogs));
        HrOnboarding draft = row("DRAFT", 3);
        HrOnboarding cancelled = row("CANCELLED", 4);
        when(accessService.lockScopedForUpdate(any(HrOnboardingQuery.class))).thenReturn(draft, cancelled);
        when(mapper.updateOnboardingStatusByVersion(eq(1L), eq("DRAFT"), eq("CANCELLED"), eq(3),
                eq(9L), eq("operator"), any())).thenReturn(1);
        when(mapper.updateOnboardingStatusByVersion(1L, "CANCELLED", "DRAFT", 4, 9L,
                "operator", null)).thenReturn(1);
        when(accessService.findScoped(any(HrOnboardingQuery.class)))
                .thenReturn(cancelled, row("DRAFT", 5));

        String reason = "重复候选人\r\n电话 13800138000；身份证 350000199001010000；银行卡 6222020200001234567；地址 厦门市思明区";
        String normalized = "重复候选人 电话 13800138000；身份证 350000199001010000；银行卡 6222020200001234567；地址 厦门市思明区";
        cancelled.setCancelReason(normalized);
        service.cancel(1L, 3, reason, "operator");
        service.restore(1L, 4, "operator");

        verify(mapper).updateOnboardingStatusByVersion(1L, "DRAFT", "CANCELLED", 3, 9L,
                "operator", normalized);
        verify(mapper).updateOnboardingStatusByVersion(1L, "CANCELLED", "DRAFT", 4, 9L,
                "operator", null);
        ArgumentCaptor<HrOnboardingOperationLog> log = ArgumentCaptor.forClass(HrOnboardingOperationLog.class);
        verify(logMapper, times(2)).insertOperationLog(log.capture());
        String cancelSummary = log.getAllValues().get(0).getOperationSummary();
        String restoreSummary = log.getAllValues().get(1).getOperationSummary();
        String auditRef = auditRef(cancelSummary);
        assertThat(cancelSummary).contains("category=DATA_CORRECTION", "auditRef=" + auditRef);
        assertThat(restoreSummary).contains("sourceCancelLogId=101", "auditRef=" + auditRef);
        assertThat(log.getAllValues()).allSatisfy(entry -> assertThat(entry.getOperationSummary()).doesNotContain(
                "重复候选人", "13800138000", "350000199001010000", "6222020200001234567", "厦门市思明区",
                "sha256", "fingerprint", "length="));
    }

    @Test
    void separateCancellationEventsUseDifferentOpaqueAuditReferences()
    {
        HrOnboarding first = row("DRAFT", 1);
        HrOnboarding second = row("DRAFT", 1);
        second.setOnboardingId(2L);
        when(accessService.lockScopedForUpdate(any(HrOnboardingQuery.class))).thenReturn(first, second);
        when(mapper.updateOnboardingStatusByVersion(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(1);
        when(accessService.findScoped(any(HrOnboardingQuery.class))).thenReturn(row("CANCELLED", 2));

        service.cancel(1L, 1, "个人原因甲", "operator");
        service.cancel(2L, 1, "个人原因乙", "operator");

        ArgumentCaptor<HrOnboardingOperationLog> logs = ArgumentCaptor.forClass(HrOnboardingOperationLog.class);
        verify(logMapper, times(2)).insertOperationLog(logs.capture());
        assertThat(auditRef(logs.getAllValues().get(0).getOperationSummary())).hasSize(32)
                .isNotEqualTo(auditRef(logs.getAllValues().get(1).getOperationSummary()));
        assertThat(logs.getAllValues()).allSatisfy(entry -> assertThat(entry.getOperationSummary())
                .doesNotContain("sha256", "fingerprint", "length=", "个人原因甲", "个人原因乙"));
    }

    @Test
    void restoreFailsClosedWhenTheLatestCancellationAuditIsMalformed()
    {
        HrOnboarding cancelled = row("CANCELLED", 4);
        when(accessService.lockScopedForUpdate(any(HrOnboardingQuery.class))).thenReturn(cancelled);
        HrOnboardingOperationLog older = new HrOnboardingOperationLog();
        older.setLogId(100L);
        older.setToStatus("CANCELLED");
        older.setOperationSummary("category=PERSONAL; auditRef=0123456789abcdef0123456789abcdef");
        HrOnboardingOperationLog latest = new HrOnboardingOperationLog();
        latest.setLogId(101L);
        latest.setToStatus("CANCELLED");
        latest.setOperationSummary("malformed latest cancellation audit");
        when(logMapper.selectByOnboardingId(1L)).thenReturn(Arrays.asList(older, latest));

        assertThatThrownBy(() -> service.restore(1L, 4, "operator"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("取消审计记录缺失");
        verify(mapper, never()).updateOnboardingStatusByVersion(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void staleOptimisticWriteReturnsStableVersionConflict()
    {
        HrOnboarding draft = row("DRAFT", 5);
        HrOnboardingUpdateRequest input = new HrOnboardingUpdateRequest();
        input.setVersion(5);
        input.setEmployeeName("新姓名");
        when(accessService.lockScopedForUpdate(any(HrOnboardingQuery.class))).thenReturn(draft);
        when(mapper.updateOnboardingByVersion(any())).thenReturn(0);

        assertThatThrownBy(() -> service.update(1L, input, "operator"))
                .isInstanceOf(HrOnboardingValidationException.class)
                .extracting("errorCode").isEqualTo("ONBOARDING_VERSION_CONFLICT");
    }

    @Test
    void detailOperationLogsExposeStructuredSafeFields()
    {
        HrOnboarding draft = row("DRAFT", 1);
        HrOnboardingOperationLog source = new HrOnboardingOperationLog();
        source.setOperationType("UPDATE");
        source.setOperatorName("管理员");
        source.setFromStatus("DRAFT");
        source.setToStatus("DRAFT");
        source.setChangedFieldKeys("targetStoreId,remark,,");
        source.setOperationSummary("更新字段: targetStoreId,remark");
        source.setOperationTime(Date.from(Instant.parse("2026-07-12T08:20:00Z")));
        when(accessService.findScoped(any())).thenReturn(draft);
        when(logMapper.selectByOnboardingId(1L)).thenReturn(Collections.singletonList(source));

        HrOnboardingDetailVo.OperationLogVo log = service.get(1L).getOperationLogs().get(0);

        assertThat(log.getFromStatus()).isEqualTo("DRAFT");
        assertThat(log.getToStatus()).isEqualTo("DRAFT");
        assertThat(log.getChangedFieldKeys()).containsExactly("targetStoreId", "remark");
        assertThat(log.getSummary()).doesNotContain("13800138000");
    }

    @Test
    void versionOnlyUpdateRevalidatesTargetsWithoutWritingWhenDerivedSnapshotIsUnchanged()
    {
        HrOnboarding draft = row("DRAFT", 1);
        HrOnboardingUpdateRequest input = new HrOnboardingUpdateRequest();
        input.setVersion(1);
        when(accessService.lockScopedForUpdate(any())).thenReturn(draft);
        when(accessService.findScoped(any())).thenReturn(draft);

        assertThat(service.update(1L, input, "管理员").getOnboardingId()).isEqualTo(1L);

        verify(mapper, never()).updateOnboardingByVersion(any());
        verify(logMapper, never()).insertOperationLog(any());
        verify(accessService).validateTargets(draft);
    }

    @Test
    void versionOnlyUpdatePersistsARefreshedDepartmentSupervisorSnapshot()
    {
        HrOnboarding draft = row("DRAFT", 1);
        draft.setDepartmentSupervisor(null);
        HrOnboardingUpdateRequest input = new HrOnboardingUpdateRequest();
        input.setVersion(1);
        when(accessService.lockScopedForUpdate(any())).thenReturn(draft);
        org.mockito.Mockito.doAnswer(invocation -> {
            ((HrOnboarding) invocation.getArgument(0)).setDepartmentSupervisor("新负责人");
            return null;
        }).when(accessService).validateTargets(draft);
        when(mapper.updateOnboardingByVersion(draft)).thenReturn(1);
        when(accessService.findScoped(any())).thenReturn(draft);

        HrOnboardingDetailVo detail = service.update(1L, input, "管理员");

        assertThat(detail.getOnboardingId()).isEqualTo(1L);
        assertThat(draft.getDepartmentSupervisor()).isEqualTo("新负责人");
        verify(mapper).updateOnboardingByVersion(draft);
        ArgumentCaptor<HrOnboardingOperationLog> log = ArgumentCaptor.forClass(HrOnboardingOperationLog.class);
        verify(logMapper).insertOperationLog(log.capture());
        assertThat(log.getValue().getChangedFieldKeys()).isEqualTo("departmentSupervisor");
        assertThat(log.getValue().getOperationSummary()).isEqualTo("更新字段: departmentSupervisor");
    }

    @Test
    void unchangedPatchComparesNormalizedValuesBeforeMutatingOrAuditing()
    {
        Date birthDate = new Date(946_684_800_000L);
        Date expectedEntryDate = new Date(1_752_192_000_000L);
        HrOnboarding draft = org.mockito.Mockito.spy(row("DRAFT", 7));
        draft.setTargetStoreId(null);
        draft.setDirectSupervisorUserId(21L);
        draft.setJobGrade(null);
        draft.setSex("1");
        draft.setBirthDate(birthDate);
        draft.setIdType("ID_CARD");
        draft.setIdNumber(null);
        draft.setRegisteredResidence(null);
        draft.setCurrentAddress(null);
        draft.setMaritalStatus("SINGLE");
        draft.setEthnicity(null);
        draft.setEmergencyContact(null);
        draft.setEmergencyContactRelation(null);
        draft.setEmergencyContactPhone(null);
        draft.setExpectedEntryDate(expectedEntryDate);
        draft.setWorkLocation(null);
        draft.setWorkCityLevel("T1");
        draft.setBankName(null);
        draft.setBankAccount(null);
        draft.setContractType("FIXED");
        draft.setSocialType(null);
        draft.setProbationPeriod("3_MONTHS");
        draft.setLegalEntity(null);
        draft.setRemark(null);
        org.mockito.Mockito.clearInvocations(draft);
        when(accessService.lockScopedForUpdate(any())).thenReturn(draft);
        when(accessService.findScoped(any())).thenReturn(draft);

        HrOnboardingUpdateRequest input = new HrOnboardingUpdateRequest();
        input.setVersion(7);
        input.setEmployeeName("  员工  ");
        input.setPhoneNumber(" 13800138000 ");
        input.setTargetDeptId(10L);
        input.setTargetStoreId(null);
        input.setTargetPostId(12L);
        input.setDirectSupervisorUserId(21L);
        input.setOwnerUserId(14L);
        input.setJobGrade(" \t ");
        input.setEmployeeCategory(" 正式 ");
        input.setSex(" 1 ");
        input.setBirthDate(new Date(birthDate.getTime()));
        input.setIdType(" ID_CARD ");
        input.setIdNumber("");
        input.setRegisteredResidence(" ");
        input.setCurrentAddress("\t");
        input.setMaritalStatus(" SINGLE ");
        input.setEthnicity("");
        input.setEmergencyContact(" ");
        input.setEmergencyContactRelation("\t");
        input.setEmergencyContactPhone("");
        input.setExpectedEntryDate(new Date(expectedEntryDate.getTime()));
        input.setWorkLocation(" ");
        input.setWorkCityLevel(" T1 ");
        input.setBankName("\t");
        input.setBankAccount("");
        input.setContractType(" FIXED ");
        input.setSocialType(" ");
        input.setProbationPeriod(" 3_MONTHS ");
        input.setLegalEntity("\t");
        input.setRemark(" ");

        HrOnboardingDetailVo detail = service.update(1L, input, "operator");

        assertThat(detail.getOnboardingId()).isEqualTo(1L);
        assertThat(draft.getEmployeeName()).isEqualTo("员工");
        assertThat(draft.getJobGrade()).isNull();
        assertThat(draft.getIdNumber()).isNull();
        assertThat(draft.getCurrentAddress()).isNull();
        assertThat(draft.getRemark()).isNull();
        assertThat(org.mockito.Mockito.mockingDetails(draft).getInvocations())
                .noneMatch(invocation -> invocation.getMethod().getName().startsWith("set"));
        verify(mapper, never()).updateOnboardingByVersion(any());
        verify(logMapper, never()).insertOperationLog(any());
        verify(accessService).validateTargets(draft);
    }

    @Test
    void realPatchWritesOnlyNormalizedDifferencesAndAuditsExactFieldKeys()
    {
        HrOnboarding draft = row("DRAFT", 3);
        draft.setCurrentAddress("旧地址");
        draft.setExpectedEntryDate(new Date(1_752_192_000_000L));
        when(accessService.lockScopedForUpdate(any())).thenReturn(draft);
        when(mapper.updateOnboardingByVersion(any())).thenReturn(1);
        when(accessService.findScoped(any())).thenReturn(draft);

        HrOnboardingUpdateRequest input = new HrOnboardingUpdateRequest();
        input.setVersion(3);
        input.setEmployeeName(" 新员工 ");
        input.setPhoneNumber(" 13800138000 ");
        input.setTargetDeptId(10L);
        input.setTargetPostId(12L);
        input.setOwnerUserId(15L);
        input.setEmployeeCategory(" 合同工 ");
        input.setCurrentAddress(" \t ");
        input.setExpectedEntryDate(new Date(1_752_278_400_000L));

        service.update(1L, input, "operator");

        assertThat(draft.getEmployeeName()).isEqualTo("新员工");
        assertThat(draft.getPhoneNumber()).isEqualTo("13800138000");
        assertThat(draft.getOwnerUserId()).isEqualTo(15L);
        assertThat(draft.getEmployeeCategory()).isEqualTo("合同工");
        assertThat(draft.getCurrentAddress()).isEmpty();
        assertThat(draft.getExpectedEntryDate()).isEqualTo(new Date(1_752_278_400_000L));
        verify(mapper).updateOnboardingByVersion(draft);
        verify(accessService).validateTargets(draft);
        ArgumentCaptor<HrOnboardingOperationLog> log = ArgumentCaptor.forClass(HrOnboardingOperationLog.class);
        verify(logMapper).insertOperationLog(log.capture());
        assertThat(log.getValue().getOperationType()).isEqualTo("UPDATE");
        assertThat(log.getValue().getChangedFieldKeys())
                .isEqualTo("employeeName,ownerUserId,employeeCategory,currentAddress,expectedEntryDate");
    }

    @Test
    void sensitivePatchDistinguishesOmittedAndExplicitClearAndRejectsMaskPlaceholder()
    {
        HrOnboarding draft = row("DRAFT", 1);
        draft.setPhoneNumber("13800138000");
        draft.setIdNumber("350000199001010000");
        when(accessService.lockScopedForUpdate(any(HrOnboardingQuery.class))).thenReturn(draft);
        when(mapper.updateOnboardingByVersion(any())).thenReturn(1);
        when(accessService.findScoped(any(HrOnboardingQuery.class))).thenReturn(draft);

        HrOnboardingUpdateRequest omitted = new HrOnboardingUpdateRequest();
        omitted.setVersion(1);
        service.update(1L, omitted, "operator");
        ArgumentCaptor<HrOnboarding> write = ArgumentCaptor.forClass(HrOnboarding.class);
        verify(mapper, never()).updateOnboardingByVersion(any());
        assertThat(draft.getIdNumber()).isEqualTo("350000199001010000");

        HrOnboardingUpdateRequest clear = new HrOnboardingUpdateRequest();
        clear.setVersion(1);
        clear.setIdNumber("");
        service.update(1L, clear, "operator");
        verify(mapper).updateOnboardingByVersion(write.capture());
        assertThat(write.getAllValues().get(write.getAllValues().size() - 1).getIdNumber()).isEmpty();

        HrOnboardingUpdateRequest masked = new HrOnboardingUpdateRequest();
        masked.setVersion(1);
        masked.setPhoneNumber("138****8000");
        assertThatThrownBy(() -> service.update(1L, masked, "operator"))
                .isInstanceOf(HrOnboardingValidationException.class)
                .extracting("errorCode").isEqualTo("MASKED_VALUE_NOT_ACCEPTED");
    }

    @Test
    void optionalEmergencyPhoneOnlyValidatesWhenAReplacementValueIsProvided()
    {
        HrOnboarding draft = row("DRAFT", 1);
        draft.setEmergencyContactPhone("13900139000");
        when(accessService.lockScopedForUpdate(any())).thenReturn(draft);

        HrOnboardingUpdateRequest unrelated = new HrOnboardingUpdateRequest();
        unrelated.setVersion(1);
        unrelated.setMaritalStatus("已婚");
        when(mapper.updateOnboardingByVersion(any())).thenReturn(1);
        when(accessService.findScoped(any())).thenReturn(draft);
        service.update(1L, unrelated, "operator");

        HrOnboardingUpdateRequest invalid = new HrOnboardingUpdateRequest();
        invalid.setVersion(1);
        invalid.setEmergencyContactPhone("123");
        assertThatThrownBy(() -> service.update(1L, invalid, "operator"))
                .isInstanceOf(HrOnboardingValidationException.class)
                .satisfies(error -> {
                    HrOnboardingValidationException validation = (HrOnboardingValidationException) error;
                    assertThat(validation.getErrorCode()).isEqualTo("ONBOARDING_VALIDATION_FAILED");
                    assertThat(validation.getFieldErrors())
                            .containsEntry("emergencyContactPhone", "紧急联系人电话格式不正确");
                });
        verify(mapper, times(1)).updateOnboardingByVersion(any());
    }

    @Test
    void sensitivePatchRejectsEveryMaskGlyphButAllowsMiddleDotAndEmptyClear()
    {
        HrOnboarding draft = row("DRAFT", 1);
        when(accessService.lockScopedForUpdate(any())).thenReturn(draft);
        when(mapper.updateOnboardingByVersion(any())).thenReturn(1);
        when(accessService.findScoped(any())).thenReturn(draft);
        for (String masked : Arrays.asList("*", "＊", "•", "●", "○", "◯", "◎", "◉", "◌", "◍", "◦", "∙", "上海*"))
        {
            HrOnboardingUpdateRequest request = new HrOnboardingUpdateRequest();
            request.setVersion(1);
            request.setCurrentAddress(masked);
            assertThatThrownBy(() -> service.update(1L, request, "operator"))
                    .as("mask glyph %s", masked)
                    .isInstanceOf(HrOnboardingValidationException.class)
                    .extracting("errorCode").isEqualTo("MASKED_VALUE_NOT_ACCEPTED");
        }

        HrOnboardingUpdateRequest allowed = new HrOnboardingUpdateRequest();
        allowed.setVersion(1);
        allowed.setCurrentAddress("张三·李四");
        service.update(1L, allowed, "operator");
        assertThat(draft.getCurrentAddress()).isEqualTo("张三·李四");

        HrOnboardingUpdateRequest clear = new HrOnboardingUpdateRequest();
        clear.setVersion(1);
        clear.setCurrentAddress("");
        service.update(1L, clear, "operator");
        assertThat(draft.getCurrentAddress()).isEmpty();
    }

    @Test
    void detailRoundTripsEveryEditableSafeFieldAndOnlyMaskedSensitiveValues()
    {
        HrOnboarding draft = row("DRAFT", 3);
        draft.setDirectSupervisorUserId(21L);
        draft.setDepartmentSupervisor("部门主管");
        draft.setJobGrade("P3");
        draft.setSex("1");
        Date birthDate = new Date(946_684_800_000L);
        draft.setBirthDate(birthDate);
        draft.setIdType("ID_CARD");
        draft.setMaritalStatus("MARRIED");
        draft.setEthnicity("HAN");
        draft.setEmergencyContact("家属");
        draft.setEmergencyContactRelation("配偶");
        draft.setEmergencyContactPhone("13900139000");
        draft.setWorkLocation("厦门");
        draft.setWorkCityLevel("T2");
        draft.setBankName("测试银行");
        draft.setContractType("FIXED");
        draft.setSocialType("LOCAL");
        draft.setProbationPeriod("3_MONTHS");
        draft.setLegalEntity("测试公司");
        draft.setRemark("入职备注");
        draft.setPhoneNumber("13800138000");
        draft.setIdNumber("350000199001010000");
        draft.setBankAccount("6222020200001234567");
        draft.setRegisteredResidence("福建省泉州市鲤城区");
        draft.setCurrentAddress("福建省厦门市思明区");
        draft.setPreferredConflictAction("BIND_EXISTING");
        draft.setPreferredBindUserId(99L);
        when(accessService.findScoped(any())).thenReturn(draft);
        when(logMapper.selectByOnboardingId(1L)).thenReturn(Collections.emptyList());

        HrOnboardingDetailVo detail = service.get(1L);

        assertThat(detail.getDirectSupervisorUserId()).isEqualTo(21L);
        assertThat(detail.getDepartmentSupervisor()).isEqualTo("部门主管");
        assertThat(detail.getJobGrade()).isEqualTo("P3");
        assertThat(detail.getSex()).isEqualTo("1");
        assertThat(detail.getBirthDate()).isEqualTo(birthDate);
        assertThat(detail.getIdType()).isEqualTo("ID_CARD");
        assertThat(detail.getMaritalStatus()).isEqualTo("MARRIED");
        assertThat(detail.getEthnicity()).isEqualTo("HAN");
        assertThat(detail.getEmergencyContact()).isEqualTo("家属");
        assertThat(detail.getEmergencyContactRelation()).isEqualTo("配偶");
        assertThat(detail.getWorkLocation()).isEqualTo("厦门");
        assertThat(detail.getWorkCityLevel()).isEqualTo("T2");
        assertThat(detail.getBankName()).isEqualTo("测试银行");
        assertThat(detail.getContractType()).isEqualTo("FIXED");
        assertThat(detail.getSocialType()).isEqualTo("LOCAL");
        assertThat(detail.getProbationPeriod()).isEqualTo("3_MONTHS");
        assertThat(detail.getLegalEntity()).isEqualTo("测试公司");
        assertThat(detail.getRemark()).isEqualTo("入职备注");
        assertThat(detail.getPhoneNumberMasked()).isEqualTo("138****8000");
        assertThat(detail.getIdNumberMasked()).isEqualTo("3500**********0000");
        assertThat(detail.getBankAccountMasked()).isEqualTo("6222***********4567");
        assertThat(detail.getRegisteredResidenceMasked()).isEqualTo("福建*******");
        assertThat(detail.getCurrentAddressMasked()).isEqualTo("福建*******");
        assertThat(detail.getEmergencyContactPhoneMasked()).isEqualTo("139****9000");
        assertThat(detail.getPreferredConflictAction()).isEqualTo("BIND_EXISTING");
        assertThat(detail.getPreferredBindUserId()).isEqualTo(99L);
    }

    @Test
    void nullablePatchPreservesOmittedValuesAndClearsExplicitNulls()
    {
        HrOnboarding draft = row("DRAFT", 1);
        Date birthDate = new Date(1_000L);
        draft.setTargetStoreId(11L);
        draft.setDirectSupervisorUserId(13L);
        draft.setBirthDate(birthDate);
        when(accessService.lockScopedForUpdate(any())).thenReturn(draft);
        when(mapper.updateOnboardingByVersion(any())).thenReturn(1);
        when(accessService.findScoped(any())).thenReturn(draft);

        HrOnboardingUpdateRequest omitted = new HrOnboardingUpdateRequest();
        omitted.setVersion(1);
        service.update(1L, omitted, "operator");
        assertThat(draft.getTargetStoreId()).isEqualTo(11L);
        assertThat(draft.getDirectSupervisorUserId()).isEqualTo(13L);
        assertThat(draft.getBirthDate()).isEqualTo(birthDate);

        HrOnboardingUpdateRequest clear = new HrOnboardingUpdateRequest();
        clear.setVersion(1);
        clear.setTargetStoreId(null);
        clear.setDirectSupervisorUserId(null);
        clear.setBirthDate(null);
        clear.setTargetDeptId(null);
        service.update(1L, clear, "operator");
        assertThat(draft.getTargetStoreId()).isNull();
        assertThat(draft.getDirectSupervisorUserId()).isNull();
        assertThat(draft.getBirthDate()).isNull();
        assertThat(draft.getTargetDeptId()).as("required source is not nullable-patchable").isEqualTo(10L);
    }

    @Test
    void nullListAndSummaryQueriesFailClosedBeforeMapperInvocation()
    {
        assertThatThrownBy(() -> service.list(null)).isInstanceOf(ServiceException.class)
                .hasMessageContaining("查询参数");
        assertThatThrownBy(() -> service.summary(null)).isInstanceOf(ServiceException.class)
                .hasMessageContaining("查询参数");
        verify(mapper, never()).selectOnboardingList(any());
    }

    @Test
    void statusWritesAndOperationLogsUseTheSuppliedCurrentUserId()
    {
        HrOnboarding draft = row("DRAFT", 3);
        HrOnboarding ready = row("READY", 4);
        ready.setOnboardingId(2L);
        when(accessService.lockScopedForUpdate(any())).thenReturn(draft, ready);
        when(mapper.updateOnboardingStatusByVersion(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(1);
        when(accessService.findScoped(any())).thenReturn(row("CANCELLED", 4), row("DRAFT", 5));

        service.cancel(1L, 3, "个人原因", "operator");
        service.returnToDraft(2L, 4, "operator");

        verify(mapper).updateOnboardingStatusByVersion(1L, "DRAFT", "CANCELLED", 3, 9L,
                "operator", "个人原因");
        verify(mapper).updateOnboardingStatusByVersion(2L, "READY", "DRAFT", 4, 9L,
                "operator", null);
        ArgumentCaptor<HrOnboardingOperationLog> log = ArgumentCaptor.forClass(HrOnboardingOperationLog.class);
        verify(logMapper, times(2)).insertOperationLog(log.capture());
        assertThat(log.getAllValues()).allSatisfy(entry -> assertThat(entry.getOperatorUserId()).isEqualTo(9L));
    }

    @Test
    void createRetriesOnlyNamedOnboardingNumberCollisionThreeTimes()
    {
        HrOnboardingCreateRequest input = quickCreate();
        when(noGenerator.next()).thenReturn("OB20260711AAAAAAAAAAAA", "OB20260711BBBBBBBBBBBB", "OB20260711CCCCCCCCCCCC");
        when(mapper.insertOnboarding(any()))
                .thenThrow(new DataIntegrityViolationException("Duplicate entry for key 'uk_hr_onboarding_no'"))
                .thenThrow(new DataIntegrityViolationException("Duplicate entry for key 'uk_hr_onboarding_no'"))
                .thenThrow(new DataIntegrityViolationException("Duplicate entry for key 'uk_hr_onboarding_no'"));

        assertThatThrownBy(() -> service.create(input, "operator"))
                .isInstanceOf(HrOnboardingValidationException.class)
                .extracting("errorCode").isEqualTo("ONBOARDING_NO_COLLISION");
        verify(mapper, times(3)).insertOnboarding(any());
    }

    @Test
    void listAndSummaryAreDataScopedAndReturnMaskedTaskData() throws Exception
    {
        for (String name : new String[] { "list", "summary" })
        {
            Method method = HrOnboardingServiceImpl.class.getMethod(name, HrOnboardingQuery.class);
            DataScope scope = method.getAnnotation(DataScope.class);
            assertThat(scope).isNotNull();
            assertThat(scope.deptAlias()).isEqualTo("d");
        }

        HrOnboarding todayDraft = row("DRAFT", 1);
        todayDraft.setExpectedEntryDate(Date.from(Instant.parse("2026-07-11T00:00:00Z")));
        todayDraft.setPhoneNumberMasked("138****8000");
        todayDraft.setReadinessMissingCount(6);
        todayDraft.setMarkReadyAllowed(false);
        HrOnboardingSummaryVo counts = new HrOnboardingSummaryVo();
        counts.setTodayArrivalCount(1);
        counts.setPendingConfirmCount(1);
        counts.setAccountConfigurationRiskCount(1);
        when(mapper.selectOnboardingSummary(any())).thenReturn(counts);
        when(mapper.selectTodayOnboardingTasks(any())).thenReturn(Collections.singletonList(todayDraft));

        HrOnboardingSummaryVo summary = service.summary(new HrOnboardingQuery());

        assertThat(summary.getTodayArrivalCount()).isEqualTo(1);
        assertThat(summary.getPendingConfirmCount()).isEqualTo(1);
        assertThat(summary.getAccountConfigurationRiskCount()).isEqualTo(1);
        assertThat(summary.getTodayTasks()).singleElement()
                .extracting("phoneNumberMasked").isEqualTo("138****8000");
        assertThat(summary.getTodayTasks().get(0).getAllowedActions()).containsExactly("EDIT", "CANCEL");
        assertThat(summary.getTodayTasks().get(0).getMissingCount()).isEqualTo(6);
        verify(mapper, never()).selectOnboardingList(any());
        ArgumentCaptor<HrOnboardingQuery> summaryQuery = ArgumentCaptor.forClass(HrOnboardingQuery.class);
        verify(mapper).selectOnboardingSummary(summaryQuery.capture());
        assertThat(summaryQuery.getValue().getSummaryDate()).isNotNull();
    }

    @Test
    void summaryUsesSafeDecisionProjectionForCompleteDraftWithoutEvaluatingPartialPiiRow()
    {
        HrOnboarding completeDraft = row("DRAFT", 1);
        completeDraft.setPhoneNumberMasked("138****8000");
        completeDraft.setReadinessMissingCount(0);
        completeDraft.setMarkReadyAllowed(true);
        when(mapper.selectOnboardingSummary(any())).thenReturn(new HrOnboardingSummaryVo());
        when(mapper.selectTodayOnboardingTasks(any())).thenReturn(Collections.singletonList(completeDraft));

        assertThat(service.summary(new HrOnboardingQuery()).getTodayTasks()).singleElement().satisfies(task -> {
            assertThat(task.getPhoneNumberMasked()).isEqualTo("138****8000");
            assertThat(task.getMissingCount()).isZero();
            assertThat(task.getAllowedActions()).containsExactly("EDIT", "MARK_READY", "CANCEL");
            assertThat(task.getCurrentAction()).isEqualTo("MARK_READY");
        });
    }

    @Test
    void safeTodayProjectionPreservesNonDraftStateActions()
    {
        HrOnboarding ready = row("READY", 1);
        HrOnboarding cancelled = row("CANCELLED", 1);
        HrOnboarding confirmed = row("CONFIRMED", 1);
        when(mapper.selectOnboardingSummary(any())).thenReturn(new HrOnboardingSummaryVo());
        when(mapper.selectTodayOnboardingTasks(any())).thenReturn(Arrays.asList(ready, cancelled, confirmed));

        assertThat(service.summary(new HrOnboardingQuery()).getTodayTasks()).satisfiesExactly(
                task -> {
                    assertThat(task.getAllowedActions()).containsExactly("RETURN_TO_DRAFT", "CONFIRM", "CANCEL");
                    assertThat(task.getCurrentAction()).isEqualTo("CONFIRM");
                },
                task -> {
                    assertThat(task.getAllowedActions()).containsExactly("RESTORE");
                    assertThat(task.getCurrentAction()).isEqualTo("RESTORE");
                },
                task -> {
                    assertThat(task.getAllowedActions()).isEmpty();
                    assertThat(task.getCurrentAction()).isNull();
                });
    }


    @Test
    void publicServiceCallRunsDataScopeAspectOnInjectedAccessProxyAndFailsClosed()
    {
        SysDeptMapper deptMapper = org.mockito.Mockito.mock(SysDeptMapper.class);
        SysPostMapper postMapper = org.mockito.Mockito.mock(SysPostMapper.class);
        SysUserMapper userMapper = org.mockito.Mockito.mock(SysUserMapper.class);
        SysUserProfileDerivationService derivation = org.mockito.Mockito.mock(SysUserProfileDerivationService.class);
        HrOnboardingAccessService target = new HrOnboardingAccessService(mapper, deptMapper, postMapper,
                userMapper, derivation);
        AspectJProxyFactory proxyFactory = new AspectJProxyFactory(target);
        proxyFactory.setProxyTargetClass(true);
        proxyFactory.addAspect(new DataScopeAspect());
        HrOnboardingAccessService accessProxy = proxyFactory.getProxy();
        HrOnboardingRuleService rules = new HrOnboardingRuleService(new HrOnboardingFieldRegistry(), null,
                Clock.fixed(Instant.parse("2026-07-11T00:00:00Z"), ZoneOffset.UTC));
        HrOnboardingServiceImpl publicService = new HrOnboardingServiceImpl(mapper, logMapper, accessProxy, rules,
                new HrSensitiveFieldMasker(), noGenerator,
                Clock.fixed(Instant.parse("2026-07-11T00:00:00Z"), ZoneOffset.UTC), () -> 9L);
        loginAsDepartmentScopedUser();
        when(mapper.selectOnboardingList(any())).thenReturn(Collections.emptyList());

        try
        {
            assertThatThrownBy(() -> publicService.get(99L))
                    .isInstanceOf(ServiceException.class)
                    .hasMessageContaining("无权");
            ArgumentCaptor<HrOnboardingQuery> query = ArgumentCaptor.forClass(HrOnboardingQuery.class);
            verify(mapper).selectOnboardingList(query.capture());
            assertThat(query.getValue().getParams().get("dataScope")).asString()
                    .contains("d.dept_id = 20");
        }
        finally
        {
            SecurityContextHolder.remove();
        }
    }

    @Test
    void summaryAcceptsJdbcDateRowsFromTheSafeTodayQuery()
    {
        HrOnboarding today = row("DRAFT", 1);
        today.setExpectedEntryDate(new java.sql.Date(Instant.parse("2026-07-11T00:00:00Z").toEpochMilli()));
        HrOnboardingSummaryVo counts = new HrOnboardingSummaryVo();
        counts.setTodayArrivalCount(1);
        when(mapper.selectOnboardingSummary(any())).thenReturn(counts);
        when(mapper.selectTodayOnboardingTasks(any())).thenReturn(Collections.singletonList(today));

        assertThat(service.summary(new HrOnboardingQuery()).getTodayArrivalCount()).isEqualTo(1);
        verify(mapper, never()).selectOnboardingList(any());
    }

    @Test
    void detailUsesStrictPercentNamesAndEvaluatesProjectedEmployeeProfile() throws Exception
    {
        HrOnboarding source = row("DRAFT", 1);
        source.setCompanyName("华东公司");
        source.setDeptLevel1Name("人力中心");
        source.setPositionName("招聘专员");
        when(accessService.findScoped(any(HrOnboardingQuery.class))).thenReturn(source);
        when(logMapper.selectByOnboardingId(1L)).thenReturn(Collections.emptyList());

        HrOnboardingDetailVo detail = service.get(1L);

        Map<String, Method> getters = Arrays.stream(HrOnboardingDetailVo.class.getMethods())
                .filter(method -> method.getName().startsWith("get"))
                .collect(java.util.stream.Collectors.toMap(Method::getName, method -> method, (left, right) -> left));
        assertThat(getters).containsKeys("getOnboardingCompletionPercent", "getProfileCompletionPercent",
                "getOnboardingCompletedFieldCount", "getOnboardingRequiredFieldCount",
                "getReadinessBlockingCodes");
        assertThat(getters).doesNotContainKeys("getOnboardingCompletionPercentage", "getProfileCompletionPercentage");
        assertThat(detail.getMissingProfileFields()).isNotEmpty();
        assertThat(detail.getOnboardingCompletedFieldCount() + detail.getMissingCount())
                .isEqualTo(detail.getOnboardingRequiredFieldCount());
        assertThat(detail.getReadinessBlockingCodes())
                .containsExactly(HrOnboardingRuleService.BLOCK_DERIVED_DEPARTMENT_SUPERVISOR_MISSING);
        assertThat((Integer) getters.get("getProfileCompletionPercent").invoke(detail)).isBetween(1, 99);
    }

    @Test
    void allWritesAreTransactionalAndRequestContractsAreWhitelistOnly() throws Exception
    {
        for (Method method : HrOnboardingServiceImpl.class.getMethods())
        {
            if (Arrays.asList("create", "update", "markReady", "returnToDraft", "cancel", "restore")
                    .contains(method.getName()))
            {
                Transactional tx = method.getAnnotation(Transactional.class);
                assertThat(tx).as(method.getName()).isNotNull();
                assertThat(tx.rollbackFor()).contains(Exception.class);
            }
        }

        assertThat(beanProperties(HrOnboardingCreateRequest.class)).containsExactlyInAnyOrder(
                "employeeName", "phoneNumber", "expectedEntryDate", "targetDeptId", "targetPostId",
                "employeeCategory", "ownerUserId");
        assertThat(beanProperties(HrOnboardingUpdateRequest.class)).doesNotContain(
                "employeeNo", "status", "onboardingNo", "sourceType", "preferredConflictAction", "preferredBindUserId",
                "linkedUserId", "confirmedBy", "confirmedTime", "createBy", "updateBy");
        assertThat(Arrays.stream(HrOnboardingUpdateRequest.class.getMethods()).map(Method::getName))
                .doesNotContain("getEmployeeNo", "setEmployeeNo", "setStatus", "setOnboardingNo",
                        "setSourceType", "setPreferredConflictAction", "setPreferredBindUserId",
                        "setLinkedUserId", "setConfirmedBy", "setConfirmedTime", "setCreateBy", "setUpdateBy");
        assertThat(beanProperties(HrOnboardingCancelRequest.class)).contains("version", "reason");
    }

    private static java.util.List<String> beanProperties(Class<?> type)
    {
        return Arrays.stream(type.getMethods())
                .map(Method::getName)
                .filter(name -> name.startsWith("get") && !name.equals("getClass"))
                .map(name -> Character.toLowerCase(name.charAt(3)) + name.substring(4))
                .collect(java.util.stream.Collectors.toList());
    }

    private static HrOnboardingCreateRequest quickCreate()
    {
        HrOnboardingCreateRequest input = new HrOnboardingCreateRequest();
        input.setEmployeeName("新员工");
        input.setPhoneNumber("13800138000");
        input.setExpectedEntryDate(new Date());
        input.setTargetDeptId(10L);
        input.setTargetPostId(12L);
        input.setEmployeeCategory("正式");
        input.setOwnerUserId(14L);
        return input;
    }

    private static HrOnboarding row(String status, int version)
    {
        HrOnboarding row = new HrOnboarding();
        row.setOnboardingId(1L);
        row.setStatus(status);
        row.setVersion(version);
        row.setEmployeeName("员工");
        row.setPhoneNumber("13800138000");
        row.setExpectedEntryDate(new Date());
        row.setTargetDeptId(10L);
        row.setTargetPostId(12L);
        row.setEmployeeCategory("正式");
        row.setOwnerUserId(14L);
        return row;
    }

    private static String[] pair(String from, String to) { return new String[] { from, to }; }

    private static Map<String, Object> option(Object label, Object value)
    {
        Map<String, Object> option = new LinkedHashMap<>();
        option.put("label", label);
        option.put("value", value);
        return option;
    }

    private static String auditRef(String summary)
    {
        Matcher matcher = Pattern.compile("auditRef=([0-9a-f]{32})").matcher(summary);
        assertThat(matcher.find()).as(summary).isTrue();
        return matcher.group(1);
    }

    private static void loginAsDepartmentScopedUser()
    {
        SysRole role = new SysRole();
        role.setRoleId(7L);
        role.setRoleKey("hr_user");
        role.setStatus(UserConstants.ROLE_NORMAL);
        role.setDataScope(Constants.Dept.DATA_SCOPE_DEPT);
        SysUser user = new SysUser();
        user.setUserId(200L);
        user.setDeptId(20L);
        user.setRoles(Collections.singletonList(role));
        LoginUser loginUser = new LoginUser();
        loginUser.setSysUser(user);
        loginUser.setRoles(Collections.singleton("hr_user"));
        SecurityContextHolder.setUserId("200");
        SecurityContextHolder.set(SecurityConstants.LOGIN_USER, loginUser);
    }

    private static boolean isAllowed(String from, String to)
    {
        return ("DRAFT".equals(from) && Arrays.asList("READY", "CANCELLED").contains(to))
                || ("READY".equals(from) && Arrays.asList("DRAFT", "CONFIRMED", "CANCELLED").contains(to))
                || ("CANCELLED".equals(from) && "DRAFT".equals(to));
    }
}
