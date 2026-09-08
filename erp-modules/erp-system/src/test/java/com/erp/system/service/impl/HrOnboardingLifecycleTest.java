package com.erp.system.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import jakarta.validation.constraints.Digits;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;
import com.erp.common.core.exception.ServiceException;
import com.erp.oa.api.domain.HrEmployeeSigningSnapshot;
import com.erp.oa.api.domain.HrSignBusinessEvent;
import com.erp.system.domain.SysHrLifecycleAction;
import com.erp.system.domain.SysHrSignEventOutbox;
import com.erp.system.domain.dto.HrLifecycleOnboardingConfirmRequest;
import com.erp.system.mapper.SysConfigMapper;
import com.erp.system.mapper.SysHrLifecycleActionMapper;
import com.erp.system.mapper.SysHrRenewalGuardMapper;
import com.erp.system.mapper.SysHrSignEventOutboxMapper;
import com.erp.system.mapper.SysPostMapper;
import com.erp.system.mapper.SysUserProfileMapper;
import com.erp.system.mapper.SysUserPostMapper;
import com.erp.system.service.ISysUserShopService;

@DisplayName("HR确认入职生命周期")
class HrOnboardingLifecycleTest
{
    private SysConfigMapper configMapper;
    private SysUserProfileMapper profileMapper;
    private SysHrLifecycleActionMapper actionMapper;
    private SysHrSignEventOutboxMapper outboxMapper;
    private ISysUserShopService userShopService;
    private ObjectMapper objectMapper;
    private HrLifecycleServiceImpl service;

    @BeforeEach
    void setUp()
    {
        configMapper = mock(SysConfigMapper.class);
        profileMapper = mock(SysUserProfileMapper.class);
        actionMapper = mock(SysHrLifecycleActionMapper.class);
        outboxMapper = mock(SysHrSignEventOutboxMapper.class);
        userShopService = mock(ISysUserShopService.class);
        objectMapper = JsonMapper.builder().findAndAddModules().build();
        service = new HrLifecycleServiceImpl(configMapper, profileMapper, actionMapper,
                outboxMapper, mock(SysHrRenewalGuardMapper.class), mock(SysPostMapper.class),
                mock(com.erp.system.mapper.SysDeptMapper.class),
                mock(com.erp.system.mapper.SysUserMapper.class),
                mock(SysUserPostMapper.class), userShopService,
                objectMapper);
        ReflectionTestUtils.setField(service, "clock", java.time.Clock.fixed(
                java.time.Instant.parse("2026-07-12T02:03:04Z"), java.time.ZoneOffset.UTC));
    }

    @Test
    @DisplayName("确认请求固定完整入职字段且服务声明整笔事务")
    void shouldDefineCompleteRequestAndTransactionBoundary() throws Exception
    {
        Class<?> requestType = Class.forName("com.erp.system.domain.dto.HrLifecycleOnboardingConfirmRequest");
        Set<String> fields = Arrays.stream(requestType.getDeclaredFields())
                .map(Field::getName)
                .collect(Collectors.toSet());

        assertThat(fields).contains(
                "requestId", "entryDate", "contractStartDate", "contractEndDate",
                "probationStartDate", "probationEndDate", "contractTypeCode",
                "contractTermCode", "socialTypeCode", "jobGradeCode",
                "legalEntityId", "legalEntityCode", "legalEntityName",
                "baseSalary", "postSalary", "fieldAllowance", "performanceSalary",
                "salaryTotal", "salaryVersion");
        assertThat(requestType.getDeclaredField("entryDate").getType()).isEqualTo(LocalDate.class);
        assertThat(requestType.getDeclaredField("baseSalary").getType()).isEqualTo(BigDecimal.class);

        Class<?> serviceType = Class.forName("com.erp.system.service.impl.HrLifecycleServiceImpl");
        Method confirm = Arrays.stream(serviceType.getDeclaredMethods())
                .filter(method -> method.getName().equals("confirmOnboarding"))
                .findFirst()
                .orElseThrow();
        Transactional transactional = confirm.getAnnotation(Transactional.class);
        assertThat(transactional).isNotNull();
        assertThat(transactional.rollbackFor()).contains(Exception.class);
    }

    @Test
    @DisplayName("五个入职金额字段声明DECIMAL 16,2输入契约")
    void shouldDeclareDatabaseCompatibleMoneyDigits() throws Exception
    {
        for (String fieldName : List.of(
                "baseSalary", "postSalary", "fieldAllowance", "performanceSalary", "salaryTotal"))
        {
            Digits digits = HrLifecycleOnboardingConfirmRequest.class.getDeclaredField(fieldName)
                    .getAnnotation(Digits.class);
            assertThat(digits).as(fieldName).isNotNull();
            assertThat(digits.integer()).as(fieldName + " integer").isEqualTo(14);
            assertThat(digits.fraction()).as(fieldName + " fraction").isEqualTo(2);
        }
    }

    @Test
    @DisplayName("稳定法律主体试用期薪资字段有可重复部署迁移和完整Mapper映射")
    void shouldPersistStableSigningSnapshotFields() throws Exception
    {
        Path sqlPath = repoFile("sql/erp_hr_onboarding_profile_20260712.sql");
        Path dockerPath = repoFile("docker/mysql/db/erp_hr_onboarding_profile_20260712.sql");
        assertThat(sqlPath).exists();
        assertThat(dockerPath).exists();
        String sql = Files.readString(sqlPath, StandardCharsets.UTF_8);
        assertThat(Files.readString(dockerPath, StandardCharsets.UTF_8)).isEqualTo(sql);
        assertThat(sql).contains(
                "FROM information_schema.TABLES",
                "legal_entity_id bigint", "legal_entity_code varchar(64)",
                "probation_start_date date", "probation_end_date date",
                "base_salary decimal(16,2)", "post_salary decimal(16,2)",
                "field_allowance decimal(16,2)", "performance_salary decimal(16,2)",
                "salary_total decimal(16,2)", "salary_version varchar(32)");

        Path profileSchemaPath = repoFile("sql/erp_user_employee_profile_20260706.sql");
        Path dockerProfileSchemaPath = repoFile(
                "docker/mysql/db/erp_user_employee_profile_20260706.sql");
        String profileSchema = Files.readString(profileSchemaPath, StandardCharsets.UTF_8);
        assertThat(Files.readString(dockerProfileSchemaPath, StandardCharsets.UTF_8))
                .isEqualTo(profileSchema);
        assertThat(profileSchema).contains(
                "legal_entity_id bigint", "legal_entity_code varchar(64)",
                "probation_start_date date", "probation_end_date date",
                "base_salary decimal(16,2)", "salary_total decimal(16,2)",
                "salary_version varchar(32)");

        Set<String> profileFields = Arrays.stream(
                Class.forName("com.erp.system.api.domain.SysUserProfile").getDeclaredFields())
                .map(Field::getName).collect(Collectors.toSet());
        assertThat(profileFields).contains(
                "legalEntityId", "legalEntityCode", "probationStartDate", "probationEndDate",
                "baseSalary", "postSalary", "fieldAllowance", "performanceSalary",
                "salaryTotal", "salaryVersion");

        String mapper = Files.readString(repoFile(
                "erp-modules/erp-system/src/main/resources/mapper/system/SysUserProfileMapper.xml"),
                StandardCharsets.UTF_8);
        assertThat(mapper)
                .contains("selectSigningSnapshotByUserIdForUpdate", "for update",
                        "updateLifecycleOnboardingProfile", "legal_entity_id = #{snapshot.legalEntityId}",
                        "p.base_salary as baseSalary", "p.post_salary as postSalary",
                        "p.field_allowance as fieldAllowance",
                        "p.performance_salary as performanceSalary",
                        "p.salary_total as salaryTotal", "p.salary_version as salaryVersion",
                        "base_salary = #{snapshot.baseSalary}",
                        "post_salary = #{snapshot.postSalary}",
                        "field_allowance = #{snapshot.fieldAllowance}",
                        "performance_salary = #{snapshot.performanceSalary}",
                        "salary_total = #{snapshot.salaryTotal}",
                        "salary_version = #{snapshot.salaryVersion}", "update_by = #{operatorName}")
                .doesNotContain("legal_entity_id = shop_dept_id");
        String userMapper = Files.readString(repoFile(
                "erp-modules/erp-system/src/main/resources/mapper/system/SysUserMapper.xml"),
                StandardCharsets.UTF_8);
        String generalProfileResult = userMapper.substring(
                userMapper.indexOf("id=\"UserProfileResult\""),
                userMapper.indexOf("id=\"SignCandidateUserResult\""));
        String signCandidateResult = userMapper.substring(
                userMapper.indexOf("id=\"SignCandidateUserResult\""),
                userMapper.indexOf("id=\"SysUserManageListResult\""));
        String signCandidateSelect = userMapper.substring(
                userMapper.indexOf("id=\"selectSignCandidateUsers\""),
                userMapper.indexOf("id=\"selectAllocatedList\""));
        assertThat(userMapper).contains(
                "property=\"legalEntityId\" column=\"legal_entity_id\"",
                "property=\"legalEntityCode\" column=\"legal_entity_code\"",
                "property=\"probationStartDate\"", "property=\"probationEndDate\"",
                "p.legal_entity_id", "p.probation_start_date", "p.probation_end_date");
        assertThat(generalProfileResult)
                .doesNotContain("property=\"salaryTotal\"", "column=\"salary_total\"");
        assertThat(signCandidateResult).contains(
                "property=\"baseSalary\"", "property=\"postSalary\"",
                "property=\"fieldAllowance\"", "property=\"performanceSalary\"",
                "property=\"salaryTotal\"", "property=\"salaryVersion\"");
        assertThat(signCandidateSelect).contains(
                "p.base_salary", "p.post_salary", "p.field_allowance",
                "p.performance_salary", "p.salary_total", "p.salary_version");
    }

    @Test
    @DisplayName("只有配置HR本人可以确认且必须通过目标门店数据范围")
    void shouldRequireConfiguredHrAndTargetShopScope()
    {
        when(configMapper.selectConfiguredSignHrUserId()).thenReturn(88L);

        assertThatThrownBy(() -> confirm(validRequest(true), 77L, false))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("配置HR");
        assertThatThrownBy(() -> confirm(validRequest(true), 77L, true))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("配置HR");
        verify(profileMapper, never()).selectSigningSnapshotByUserIdForUpdate(anyLong());

        HrEmployeeSigningSnapshot snapshot = waitingSnapshot();
        when(profileMapper.selectSigningSnapshotByUserIdForUpdate(9L)).thenReturn(snapshot);
        doThrow(new ServiceException("当前用户无权选择该店铺或仓库"))
                .when(userShopService).checkUserShopScope(88L, 20L, false);

        assertThatThrownBy(() -> confirm(validRequest(true), 88L, true))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("无权");
        verify(userShopService).checkUserShopScope(88L, 20L, false);
        verify(actionMapper, never()).insertAction(any());
    }

    @Test
    @DisplayName("待入职且有有效试用期时同事务更新为试用并写入typed事件")
    void shouldConfirmWaitingEmployeeIntoProbationAndWriteTypedEvent() throws Exception
    {
        stubFreshConfirmation(waitingSnapshot(), 501L);
        HrLifecycleOnboardingConfirmRequest request = validRequest(true);

        Long actionId = confirm(request, 88L, false);

        assertThat(actionId).isEqualTo(501L);
        ArgumentCaptor<HrEmployeeSigningSnapshot> update = ArgumentCaptor.forClass(HrEmployeeSigningSnapshot.class);
        ArgumentCaptor<SysHrLifecycleAction> action = ArgumentCaptor.forClass(SysHrLifecycleAction.class);
        ArgumentCaptor<SysHrSignEventOutbox> outbox = ArgumentCaptor.forClass(SysHrSignEventOutbox.class);
        verify(profileMapper).updateLifecycleOnboardingProfile(update.capture(), org.mockito.ArgumentMatchers.eq("配置HR"));
        verify(actionMapper).insertAction(action.capture());
        verify(outboxMapper).insertOutbox(outbox.capture());

        assertThat(update.getValue().getEmployeeStatus()).isEqualTo("试用");
        assertThat(update.getValue().getLegalEntityId()).isEqualTo(300L);
        assertThat(update.getValue().getLegalEntityCode()).isEqualTo("LE-SH");
        assertThat(action.getValue().getActionType()).isEqualTo("ONBOARD_CONFIRMED");
        assertThat(action.getValue().getOperatorType()).isEqualTo("HUMAN");
        assertThat(action.getValue().getOperatorUserId()).isEqualTo(88L);
        assertThat(action.getValue().getOperatorIp()).isEqualTo("10.0.0.8");
        assertThat(action.getValue().getOperatorUserAgent()).isEqualTo("JUnit-UA");
        assertThat(action.getValue().getVersion()).isEqualTo(1L);
        assertThat(action.getValue().getBeforeSnapshotJson()).contains("待入职", "DB-ID-NUMBER");
        assertThat(action.getValue().getAfterSnapshotJson()).contains("试用", "\"legalEntityId\":300");

        HrSignBusinessEvent event = objectMapper.readValue(outbox.getValue().getPayloadJson(),
                HrSignBusinessEvent.class);
        assertThat(event.getEventId()).isNotBlank();
        assertThat(event.getScenario()).isEqualTo("ONBOARD");
        assertThat(event.getEmployeeId()).isEqualTo(9L);
        assertThat(event.getSourceType()).isEqualTo("HR_LIFECYCLE_ACTION");
        assertThat(event.getSourceBusinessId()).isEqualTo("501");
        assertThat(event.getSourceEventVersion()).isEqualTo(1L);
        assertThat(event.getOccurredTime()).isNotNull();
        assertThat(event.getOperatorUserId()).isEqualTo(88L);
        assertThat(event.getBeforeSnapshot().getEmployeeStatus()).isEqualTo("待入职");
        assertThat(event.getAfterSnapshot().getEmployeeStatus()).isEqualTo("试用");
        assertThat(event.getAfterSnapshot().getBaseSalary()).isEqualByComparingTo("5000.00");
        assertThat(event.getAfterSnapshot().getPostSalary()).isEqualByComparingTo("2000.00");
        assertThat(event.getAfterSnapshot().getFieldAllowance()).isEqualByComparingTo("300.00");
        assertThat(event.getAfterSnapshot().getPerformanceSalary()).isEqualByComparingTo("700.00");
        assertThat(event.getAfterSnapshot().getSalaryTotal()).isEqualByComparingTo("8000.00");
        assertThat(event.getAfterSnapshot().getSalaryVersion()).isEqualTo("2026-V1");
        assertThat(event.getAttributes()).containsEntry("actionType", "ONBOARD_CONFIRMED");
        assertThat(outbox.getValue().getActionId()).isEqualTo(501L);
        assertThat(outbox.getValue().getEventVersion()).isEqualTo(1L);
        assertThat(outbox.getValue().getStatus()).isEqualTo("PENDING");
        assertThat(outbox.getValue().getRetryCount()).isZero();
        assertThat(outbox.getValue().getVersion()).isZero();

        InOrder order = inOrder(profileMapper, actionMapper, outboxMapper);
        order.verify(profileMapper).selectSigningSnapshotByUserIdForUpdate(9L);
        order.verify(actionMapper).insertAction(any());
        order.verify(profileMapper).updateLifecycleOnboardingProfile(any(), any());
        order.verify(outboxMapper).insertOutbox(any());
    }

    @Test
    @DisplayName("无试用期时直接转正式且非待入职不能用新requestId重复确认")
    void shouldConfirmWithoutProbationAndRejectNonWaitingStatus()
    {
        stubFreshConfirmation(waitingSnapshot(), 502L);
        HrLifecycleOnboardingConfirmRequest request = validRequest(false);

        assertThat(confirm(request, 88L, false)).isEqualTo(502L);
        ArgumentCaptor<HrEmployeeSigningSnapshot> updated = ArgumentCaptor.forClass(HrEmployeeSigningSnapshot.class);
        verify(profileMapper).updateLifecycleOnboardingProfile(updated.capture(), any());
        assertThat(updated.getValue().getEmployeeStatus()).isEqualTo("正式");

        setUp();
        HrEmployeeSigningSnapshot active = waitingSnapshot();
        active.setEmployeeStatus("正式");
        when(configMapper.selectConfiguredSignHrUserId()).thenReturn(88L);
        when(profileMapper.selectSigningSnapshotByUserIdForUpdate(9L)).thenReturn(active);
        assertThatThrownBy(() -> confirm(validRequest(false), 88L, false))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("待入职");
        verify(actionMapper, never()).insertAction(any());
    }

    @Test
    @DisplayName("同一requestId重放返回原action且不再锁档案或写outbox")
    void shouldReplaySameRequestIdIdempotently()
    {
        SysHrLifecycleAction existing = new SysHrLifecycleAction();
        existing.setActionId(601L);
        existing.setActionType("ONBOARD_CONFIRMED");
        existing.setEmployeeId(9L);
        when(configMapper.selectConfiguredSignHrUserId()).thenReturn(88L);
        when(profileMapper.selectSigningSnapshotByUserIdForUpdate(9L)).thenReturn(waitingSnapshot());
        when(actionMapper.selectByRequestIdForUpdate("req-onboard-1")).thenReturn(existing);

        assertThat(confirm(validRequest(true), 88L, false)).isEqualTo(601L);

        verify(userShopService).checkUserShopScope(88L, 20L, false);
        verify(profileMapper, never()).updateLifecycleOnboardingProfile(any(), any());
        verify(actionMapper, never()).insertAction(any());
        verify(outboxMapper, never()).insertOutbox(any());
    }

    @Test
    @DisplayName("并发同requestId撞唯一键后收敛到已提交action且不做后续写入")
    void shouldConvergeConcurrentSameRequestId()
    {
        SysHrLifecycleAction concurrent = new SysHrLifecycleAction();
        concurrent.setActionId(602L);
        concurrent.setActionType("ONBOARD_CONFIRMED");
        concurrent.setEmployeeId(9L);
        when(configMapper.selectConfiguredSignHrUserId()).thenReturn(88L);
        when(profileMapper.selectSigningSnapshotByUserIdForUpdate(9L)).thenReturn(waitingSnapshot());
        when(actionMapper.selectByRequestIdForUpdate("req-onboard-1"))
                .thenReturn(null, concurrent);
        doThrow(new DuplicateKeyException("concurrent requestId"))
                .when(actionMapper).insertAction(any());

        assertThat(confirm(validRequest(true), 88L, false)).isEqualTo(602L);

        verify(profileMapper, never()).updateLifecycleOnboardingProfile(any(), any());
        verify(outboxMapper, never()).insertOutbox(any());
    }

    @Test
    @DisplayName("非法日期代码薪资在任何业务写入前明确拒绝")
    void shouldValidateDatesCodesAndSalaryBeforeWriting()
    {
        when(configMapper.selectConfiguredSignHrUserId()).thenReturn(88L);
        HrLifecycleOnboardingConfirmRequest sameDayContract = validRequest(false);
        sameDayContract.setContractEndDate(sameDayContract.getContractStartDate());
        assertThatThrownBy(() -> confirm(sameDayContract, 88L, false))
                .isInstanceOf(ServiceException.class).hasMessageContaining("合同");

        HrLifecycleOnboardingConfirmRequest invalidDates = validRequest(true);
        invalidDates.setContractEndDate(invalidDates.getContractStartDate().minusDays(1));
        assertThatThrownBy(() -> confirm(invalidDates, 88L, false))
                .isInstanceOf(ServiceException.class).hasMessageContaining("合同");

        HrLifecycleOnboardingConfirmRequest invalidCode = validRequest(true);
        invalidCode.setSocialTypeCode("UNKNOWN");
        assertThatThrownBy(() -> confirm(invalidCode, 88L, false))
                .isInstanceOf(ServiceException.class).hasMessageContaining("社保");

        HrLifecycleOnboardingConfirmRequest invalidSalary = validRequest(true);
        invalidSalary.setSalaryTotal(new BigDecimal("9999.00"));
        assertThatThrownBy(() -> confirm(invalidSalary, 88L, false))
                .isInstanceOf(ServiceException.class).hasMessageContaining("薪资合计");
        verify(profileMapper, never()).selectSigningSnapshotByUserIdForUpdate(anyLong());
        verify(actionMapper, never()).insertAction(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "baseSalary", "postSalary", "fieldAllowance", "performanceSalary", "salaryTotal"
    })
    @DisplayName("service直调拒绝任一三位小数金额且不写业务数据")
    void shouldRejectThreeDecimalMoneyBeforeWriting(String field)
    {
        stubFreshConfirmation(waitingSnapshot(), 801L);
        HrLifecycleOnboardingConfirmRequest request = validRequest(true);
        if ("salaryTotal".equals(field))
        {
            request.setSalaryTotal(new BigDecimal("8000.000"));
        }
        else
        {
            request.setBaseSalary(BigDecimal.ZERO);
            request.setPostSalary(BigDecimal.ZERO);
            request.setFieldAllowance(BigDecimal.ZERO);
            request.setPerformanceSalary(BigDecimal.ZERO);
            BigDecimal threeDecimals = new BigDecimal("0.005");
            switch (field)
            {
                case "baseSalary" -> request.setBaseSalary(threeDecimals);
                case "postSalary" -> request.setPostSalary(threeDecimals);
                case "fieldAllowance" -> request.setFieldAllowance(threeDecimals);
                case "performanceSalary" -> request.setPerformanceSalary(threeDecimals);
                default -> throw new IllegalArgumentException(field);
            }
            request.setSalaryTotal(threeDecimals);
        }

        assertThatThrownBy(() -> confirm(request, 88L, false))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("小数");
        verify(actionMapper, never()).insertAction(any());
        verify(profileMapper, never()).updateLifecycleOnboardingProfile(any(), any());
        verify(outboxMapper, never()).insertOutbox(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "baseSalary", "postSalary", "fieldAllowance", "performanceSalary", "salaryTotal"
    })
    @DisplayName("service直调拒绝任一超过十四位整数的金额且不写业务数据")
    void shouldRejectMoreThanFourteenIntegerDigitsBeforeWriting(String field)
    {
        stubFreshConfirmation(waitingSnapshot(), 802L);
        HrLifecycleOnboardingConfirmRequest request = validRequest(true);
        BigDecimal tooLarge = new BigDecimal("100000000000000.00");
        request.setBaseSalary(BigDecimal.ZERO);
        request.setPostSalary(BigDecimal.ZERO);
        request.setFieldAllowance(BigDecimal.ZERO);
        request.setPerformanceSalary(BigDecimal.ZERO);
        if ("salaryTotal".equals(field))
        {
            request.setBaseSalary(new BigDecimal("99999999999999.99"));
            request.setPostSalary(new BigDecimal("0.01"));
        }
        else
        {
            switch (field)
            {
                case "baseSalary" -> request.setBaseSalary(tooLarge);
                case "postSalary" -> request.setPostSalary(tooLarge);
                case "fieldAllowance" -> request.setFieldAllowance(tooLarge);
                case "performanceSalary" -> request.setPerformanceSalary(tooLarge);
                default -> throw new IllegalArgumentException(field);
            }
        }
        request.setSalaryTotal(tooLarge);

        assertThatThrownBy(() -> confirm(request, 88L, false))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("整数");
        verify(actionMapper, never()).insertAction(any());
        verify(profileMapper, never()).updateLifecycleOnboardingProfile(any(), any());
        verify(outboxMapper, never()).insertOutbox(any());
    }

    @ParameterizedTest
    @CsvSource({
            "99999999999999.99, 0.00, 99999999999999.99",
            "0.01, 0.00, 0.01",
            "1E+13, 0.00, 1E+13",
            "0E+20, 0.01, 0.01"
    })
    @DisplayName("十四位两位边界小数和科学计数金额原值通过")
    void shouldAcceptPersistableMoneyWithoutRounding(String baseText, String postText,
            String totalText)
    {
        stubFreshConfirmation(waitingSnapshot(), 803L);
        HrLifecycleOnboardingConfirmRequest request = validRequest(true);
        request.setBaseSalary(new BigDecimal(baseText));
        request.setPostSalary(new BigDecimal(postText));
        request.setFieldAllowance(BigDecimal.ZERO);
        request.setPerformanceSalary(BigDecimal.ZERO);
        request.setSalaryTotal(new BigDecimal(totalText));

        assertThat(confirm(request, 88L, false)).isEqualTo(803L);
        ArgumentCaptor<HrEmployeeSigningSnapshot> update =
                ArgumentCaptor.forClass(HrEmployeeSigningSnapshot.class);
        verify(profileMapper).updateLifecycleOnboardingProfile(update.capture(), any());
        assertThat(update.getValue().getBaseSalary()).isSameAs(request.getBaseSalary());
        assertThat(update.getValue().getPostSalary()).isSameAs(request.getPostSalary());
        assertThat(update.getValue().getSalaryTotal()).isSameAs(request.getSalaryTotal());
        verify(actionMapper).insertAction(any());
        verify(outboxMapper).insertOutbox(any());
    }

    @Test
    @DisplayName("action档案更新或outbox任一步失败都向事务边界抛出")
    void shouldPropagateEveryWriteFailureForRollback()
    {
        stubFreshConfirmation(waitingSnapshot(), 701L);
        doThrow(new IllegalStateException("action failed")).when(actionMapper).insertAction(any());
        assertThatThrownBy(() -> confirm(validRequest(true), 88L, false))
                .isInstanceOf(IllegalStateException.class).hasMessage("action failed");
        verify(profileMapper, never()).updateLifecycleOnboardingProfile(any(), any());

        setUp();
        stubFreshConfirmation(waitingSnapshot(), 702L);
        when(profileMapper.updateLifecycleOnboardingProfile(any(), any())).thenReturn(0);
        assertThatThrownBy(() -> confirm(validRequest(true), 88L, false))
                .isInstanceOf(ServiceException.class).hasMessageContaining("档案");
        verify(outboxMapper, never()).insertOutbox(any());

        setUp();
        stubFreshConfirmation(waitingSnapshot(), 703L);
        doThrow(new IllegalStateException("outbox failed")).when(outboxMapper).insertOutbox(any());
        assertThatThrownBy(() -> confirm(validRequest(true), 88L, false))
                .isInstanceOf(IllegalStateException.class).hasMessage("outbox failed");
    }

    private Long confirm(HrLifecycleOnboardingConfirmRequest request, Long operatorId, boolean admin)
    {
        return service.confirmOnboarding(9L, request, operatorId, "配置HR", admin,
                "10.0.0.8", "JUnit-UA");
    }

    private void stubFreshConfirmation(HrEmployeeSigningSnapshot snapshot, Long actionId)
    {
        when(configMapper.selectConfiguredSignHrUserId()).thenReturn(88L);
        when(profileMapper.selectSigningSnapshotByUserIdForUpdate(9L)).thenReturn(snapshot);
        when(profileMapper.updateLifecycleOnboardingProfile(any(), any())).thenReturn(1);
        when(actionMapper.selectByRequestId("req-onboard-1")).thenReturn(null);
        when(actionMapper.selectByRequestIdForUpdate("req-onboard-1")).thenReturn(null);
        doAnswer(invocation -> {
            SysHrLifecycleAction action = invocation.getArgument(0);
            action.setActionId(actionId);
            return 1;
        }).when(actionMapper).insertAction(any());
        when(outboxMapper.insertOutbox(any())).thenReturn(1);
    }

    private HrEmployeeSigningSnapshot waitingSnapshot()
    {
        HrEmployeeSigningSnapshot snapshot = new HrEmployeeSigningSnapshot();
        snapshot.setEmployeeId(9L);
        snapshot.setEmployeeNo("E0009");
        snapshot.setEmployeeName("张三");
        snapshot.setPhone("13800000000");
        snapshot.setIdType("CN_ID_CARD");
        snapshot.setIdNumber("DB-ID-NUMBER");
        snapshot.setCurrentAddress("上海市徐汇区");
        snapshot.setEmployeeStatus("待入职");
        snapshot.setEmployeeCategory("FULL_TIME");
        snapshot.setShopDeptId(20L);
        snapshot.setShopDeptName("徐汇门店");
        snapshot.setDeptId(20L);
        snapshot.setDeptName("徐汇门店");
        snapshot.setPostId(30L);
        snapshot.setPostCode("SALES");
        snapshot.setPostName("销售顾问");
        snapshot.setWorkLocation("上海");
        return snapshot;
    }

    private HrLifecycleOnboardingConfirmRequest validRequest(boolean probation)
    {
        HrLifecycleOnboardingConfirmRequest request = new HrLifecycleOnboardingConfirmRequest();
        request.setRequestId("req-onboard-1");
        request.setEntryDate(LocalDate.of(2026, 7, 15));
        request.setContractStartDate(LocalDate.of(2026, 7, 15));
        request.setContractEndDate(LocalDate.of(2029, 7, 14));
        if (probation)
        {
            request.setProbationStartDate(LocalDate.of(2026, 7, 15));
            request.setProbationEndDate(LocalDate.of(2026, 10, 14));
        }
        request.setContractTypeCode("LABOR_CONTRACT");
        request.setContractTermCode("FIXED_TERM");
        request.setSocialTypeCode("SOCIAL_INSURED");
        request.setJobGradeCode("P3");
        request.setLegalEntityId(300L);
        request.setLegalEntityCode("LE-SH");
        request.setLegalEntityName("上海示例有限公司");
        request.setBaseSalary(new BigDecimal("5000.00"));
        request.setPostSalary(new BigDecimal("2000.00"));
        request.setFieldAllowance(new BigDecimal("300.00"));
        request.setPerformanceSalary(new BigDecimal("700.00"));
        request.setSalaryTotal(new BigDecimal("8000.00"));
        request.setSalaryVersion("2026-V1");
        return request;
    }

    private static Path repoFile(String relativePath)
    {
        Path base = Paths.get(System.getProperty("user.dir"));
        Path path = base.resolve(relativePath);
        if (!Files.exists(path))
        {
            path = base.resolve("../..").resolve(relativePath).normalize();
        }
        return path;
    }
}
