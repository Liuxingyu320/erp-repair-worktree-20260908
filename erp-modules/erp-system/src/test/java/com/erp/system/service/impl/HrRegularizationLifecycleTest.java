package com.erp.system.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;
import com.erp.common.core.exception.ServiceException;
import com.erp.oa.api.domain.HrEmployeeSigningSnapshot;
import com.erp.oa.api.domain.HrSignBusinessEvent;
import com.erp.system.domain.SysHrLifecycleAction;
import com.erp.system.domain.SysHrSignEventOutbox;
import com.erp.system.domain.SysPost;
import com.erp.system.domain.SysUserPost;
import com.erp.system.domain.dto.HrRegularizationRequest;
import com.erp.system.mapper.SysConfigMapper;
import com.erp.system.mapper.SysHrLifecycleActionMapper;
import com.erp.system.mapper.SysHrRenewalGuardMapper;
import com.erp.system.mapper.SysHrSignEventOutboxMapper;
import com.erp.system.mapper.SysPostMapper;
import com.erp.system.mapper.SysUserProfileMapper;
import com.erp.system.mapper.SysUserPostMapper;
import com.erp.system.service.ISysUserShopService;

@DisplayName("HR转正生命周期")
class HrRegularizationLifecycleTest
{
    private SysConfigMapper configMapper;
    private SysUserProfileMapper profileMapper;
    private SysHrLifecycleActionMapper actionMapper;
    private SysHrSignEventOutboxMapper outboxMapper;
    private SysPostMapper postMapper;
    private SysUserPostMapper userPostMapper;
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
        postMapper = mock(SysPostMapper.class);
        userPostMapper = mock(SysUserPostMapper.class);
        userShopService = mock(ISysUserShopService.class);
        objectMapper = JsonMapper.builder().findAndAddModules().build();
        service = new HrLifecycleServiceImpl(configMapper, profileMapper, actionMapper,
                outboxMapper, mock(SysHrRenewalGuardMapper.class), postMapper,
                mock(com.erp.system.mapper.SysDeptMapper.class),
                mock(com.erp.system.mapper.SysUserMapper.class),
                userPostMapper, userShopService, objectMapper, org.mockito.Mockito.mock(com.erp.system.service.impl.HrSalarySourceService.class));
        ReflectionTestUtils.setField(service, "clock", java.time.Clock.fixed(
                java.time.Instant.parse("2026-07-12T02:03:04Z"),
                java.time.ZoneOffset.UTC));
    }

    @Test
    @DisplayName("转正确认暴露固定请求契约路由和单HR权限")
    void shouldExposeStableRequestRouteAndPermission() throws Exception
    {
        Path request = repoFile(
                "erp-modules/erp-system/src/main/java/com/erp/system/domain/dto/HrRegularizationRequest.java");
        assertThat(request).exists();
        String source = Files.readString(repoFile(
                "erp-modules/erp-system/src/main/java/com/erp/system/controller/HrEmployeeProfileController.java"),
                StandardCharsets.UTF_8);
        assertThat(source).contains(
                "@RequiresPermissions(\"hr:employee:regularize\")",
                "@PostMapping(\"/{userId}/regularize\")",
                "HrRegularizationRequest request",
                "hrLifecycleService.confirmRegularization");
    }

    @Test
    @DisplayName("生命周期服务显式注入权威岗位和员工岗位关联Mapper")
    void shouldInjectCanonicalPostAndAssociationMappers()
    {
        Set<Class<?>> constructorTypes = Arrays.stream(
                HrLifecycleServiceImpl.class.getConstructors())
                .flatMap(constructor -> Arrays.stream(constructor.getParameterTypes()))
                .collect(Collectors.toSet());

        assertThat(constructorTypes).contains(SysPostMapper.class, SysUserPostMapper.class);
    }

    @Test
    @DisplayName("权威岗位加锁且专用档案更新同步职位展示列")
    void shouldLockCanonicalPostAndPersistRegularizationOwnedFields() throws Exception
    {
        assertThat(Arrays.stream(SysPostMapper.class.getMethods())
                .map(method -> method.getName()))
                .contains("selectPostByIdForUpdate");
        assertThat(Arrays.stream(SysUserProfileMapper.class.getMethods())
                .map(method -> method.getName()))
                .contains("updateRegularizationProfile");

        String postMapper = Files.readString(repoFile(
                "erp-modules/erp-system/src/main/resources/mapper/system/SysPostMapper.xml"),
                StandardCharsets.UTF_8);
        assertThat(postMapper).contains(
                "<select id=\"selectPostByIdForUpdate\"",
                "where post_id = #{postId}",
                "for update");
        String profileMapper = Files.readString(repoFile(
                "erp-modules/erp-system/src/main/resources/mapper/system/SysUserProfileMapper.xml"),
                StandardCharsets.UTF_8);
        assertThat(profileMapper).contains(
                "<update id=\"updateRegularizationProfile\"",
                "employee_status = '正式'",
                "actual_regularization_date = #{snapshot.actualRegularizationDate}",
                "position_names = #{snapshot.postName}",
                "job_grade = #{snapshot.jobGradeCode}",
                "salary_version = #{snapshot.salaryVersion}",
                "and employee_status = '试用'");
    }

    @Test
    @DisplayName("确认转正以服务端快照和权威岗位原子写档案岗位动作及typed outbox")
    void shouldRegularizeWithCanonicalPostAndTransactionalWrites() throws Exception
    {
        stubFreshRegularization();
        HrRegularizationRequest request = validRequest();

        Long actionId = confirm(request, 88L, false);

        assertThat(actionId).isEqualTo(801L);
        ArgumentCaptor<HrEmployeeSigningSnapshot> profile =
                ArgumentCaptor.forClass(HrEmployeeSigningSnapshot.class);
        ArgumentCaptor<List<SysUserPost>> associations = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<SysHrLifecycleAction> action =
                ArgumentCaptor.forClass(SysHrLifecycleAction.class);
        ArgumentCaptor<SysHrSignEventOutbox> outbox =
                ArgumentCaptor.forClass(SysHrSignEventOutbox.class);
        verify(postMapper).selectPostByIdForUpdate(402L);
        verify(profileMapper).updateRegularizationProfile(profile.capture(),
                org.mockito.ArgumentMatchers.eq("配置HR"));
        verify(userPostMapper).deleteUserPostByUserId(9L);
        verify(userPostMapper).batchUserPost(associations.capture());
        verify(actionMapper).insertAction(action.capture());
        verify(outboxMapper).insertOutbox(outbox.capture());

        HrEmployeeSigningSnapshot after = profile.getValue();
        assertThat(after.getEmployeeStatus()).isEqualTo("正式");
        assertThat(after.getActualRegularizationDate())
                .isEqualTo(LocalDate.of(2026, 7, 10));
        assertThat(after.getPostId()).isEqualTo(402L);
        assertThat(after.getPostCode()).isEqualTo("SALESLEAD");
        assertThat(after.getPostName()).isEqualTo("销售组长");
        assertThat(after.getPositionNo()).isEqualTo("SALESLEAD-E000009");
        assertThat(after.getJobGradeCode()).isEqualTo("P4");
        assertThat(after.getJobGradeName()).isEqualTo("P4");
        assertThat(after.getSalaryTotal()).isEqualByComparingTo("9000.00");
        assertThat(associations.getValue()).singleElement().satisfies(link -> {
            assertThat(link.getUserId()).isEqualTo(9L);
            assertThat(link.getPostId()).isEqualTo(402L);
        });

        SysHrLifecycleAction recorded = action.getValue();
        assertThat(recorded.getActionType()).isEqualTo("REGULARIZATION_CONFIRMED");
        assertThat(recorded.getSourceType()).isEqualTo("HR_REGULARIZATION");
        assertThat(recorded.getSourceBusinessId()).isEqualTo("req-regularize-1");
        assertThat(recorded.getBusinessStatus()).isEqualTo("CONFIRMED");
        assertThat(recorded.getEffectiveDate()).isEqualTo(LocalDate.of(2026, 7, 10));
        assertThat(recorded.getBeforeSnapshotJson()).contains("\"employeeStatus\":\"试用\"");
        assertThat(recorded.getAfterSnapshotJson()).contains(
                "\"employeeStatus\":\"正式\"", "\"postCode\":\"SALESLEAD\"");

        HrSignBusinessEvent event = objectMapper.readValue(
                outbox.getValue().getPayloadJson(), HrSignBusinessEvent.class);
        assertThat(event.getScenario()).isEqualTo("REGULARIZE");
        assertThat(event.getSourceType()).isEqualTo("HR_LIFECYCLE_ACTION");
        assertThat(event.getSourceBusinessId()).isEqualTo("801");
        assertThat(event.getSourceEventVersion()).isEqualTo(1L);
        assertThat(event.getAttributes()).containsEntry(
                "actionType", "REGULARIZATION_CONFIRMED")
                .containsEntry("sourceActionId", 801)
                .containsEntry("sourceActionVersion", 1);
        assertThat(event.getBeforeSnapshot().getPostName()).isEqualTo("销售顾问");
        assertThat(event.getAfterSnapshot().getPostName()).isEqualTo("销售组长");
        assertThat(outbox.getValue().getPayloadJson())
                .doesNotContain("versionHash", "sourceFileHash");
    }

    @Test
    @DisplayName("只有当前配置HR本人且在目标门店范围内才可确认转正")
    void shouldRequireConfiguredHrAndShopScope()
    {
        when(configMapper.selectConfiguredSignHrUserId()).thenReturn(88L);
        assertThatThrownBy(() -> confirm(validRequest(), 77L, false))
                .isInstanceOf(ServiceException.class).hasMessageContaining("配置HR");
        assertThatThrownBy(() -> confirm(validRequest(), 77L, true))
                .isInstanceOf(ServiceException.class).hasMessageContaining("配置HR");
        verify(profileMapper, never()).selectSigningSnapshotByUserIdForUpdate(anyLong());

        when(profileMapper.selectSigningSnapshotByUserIdForUpdate(9L))
                .thenReturn(probationSnapshot());
        doThrow(new ServiceException("当前用户无权选择该店铺或仓库"))
                .when(userShopService).checkUserShopScope(88L, 20L, false);
        assertThatThrownBy(() -> confirm(validRequest(), 88L, false))
                .isInstanceOf(ServiceException.class).hasMessageContaining("无权");
        verify(postMapper, never()).selectPostByIdForUpdate(anyLong());

        setUp();
        when(configMapper.selectConfiguredSignHrUserId()).thenReturn(88L);
        when(profileMapper.selectSigningSnapshotByUserIdForUpdate(9L))
                .thenReturn(probationSnapshot());
        doThrow(new ServiceException("当前用户无权选择该店铺或仓库"))
                .when(userShopService).checkUserShopScope(88L, 20L, false);
        assertThatThrownBy(() -> confirm(validRequest(), 88L, true))
                .isInstanceOf(ServiceException.class).hasMessageContaining("无权");
        verify(userShopService).checkUserShopScope(88L, 20L, false);
        verify(postMapper, never()).selectPostByIdForUpdate(anyLong());
    }

    @Test
    @DisplayName("仅试用员工可转正且实际日期不得早于入职或试用开始")
    void shouldRequireProbationStatusAndValidEffectiveDate()
    {
        when(configMapper.selectConfiguredSignHrUserId()).thenReturn(88L);
        HrEmployeeSigningSnapshot formal = probationSnapshot();
        formal.setEmployeeStatus("正式");
        when(profileMapper.selectSigningSnapshotByUserIdForUpdate(9L)).thenReturn(formal);
        assertThatThrownBy(() -> confirm(validRequest(), 88L, false))
                .isInstanceOf(ServiceException.class).hasMessageContaining("仅试用");

        setUp();
        when(configMapper.selectConfiguredSignHrUserId()).thenReturn(88L);
        HrEmployeeSigningSnapshot probation = probationSnapshot();
        probation.setEntryDate(LocalDate.of(2026, 4, 1));
        when(profileMapper.selectSigningSnapshotByUserIdForUpdate(9L))
                .thenReturn(probation);
        HrRegularizationRequest tooEarly = validRequest();
        tooEarly.setActualRegularizationDate(LocalDate.of(2026, 4, 30));
        assertThatThrownBy(() -> confirm(tooEarly, 88L, false))
                .isInstanceOf(ServiceException.class).hasMessageContaining("试用期开始");
        verify(actionMapper, never()).insertAction(any());
    }

    @Test
    @DisplayName("实际转正日可等于上海业务当天但不能填写未来日期")
    void shouldRejectRegularizationDateAfterShanghaiBusinessDay()
    {
        ReflectionTestUtils.setField(service, "clock", java.time.Clock.fixed(
                java.time.Instant.parse("2026-07-11T16:30:00Z"),
                java.time.ZoneOffset.UTC));
        stubFreshRegularization();
        HrRegularizationRequest today = validRequest();
        today.setActualRegularizationDate(LocalDate.of(2026, 7, 12));

        assertThat(confirm(today, 88L, false)).isEqualTo(801L);

        setUp();
        ReflectionTestUtils.setField(service, "clock", java.time.Clock.fixed(
                java.time.Instant.parse("2026-07-11T16:30:00Z"),
                java.time.ZoneOffset.UTC));
        stubBeforePostValidation();
        HrRegularizationRequest tomorrow = validRequest();
        tomorrow.setActualRegularizationDate(LocalDate.of(2026, 7, 13));
        assertThatThrownBy(() -> confirm(tomorrow, 88L, false))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("业务当天");
        verify(postMapper, never()).selectPostByIdForUpdate(anyLong());
        verify(actionMapper, never()).insertAction(any());
    }

    @Test
    @DisplayName("岗位必须存在启用且请求代码名称与权威主数据一致")
    void shouldRejectMissingDisabledOrMismatchedPost()
    {
        stubBeforePostValidation();
        when(postMapper.selectPostByIdForUpdate(402L)).thenReturn(null);
        assertThatThrownBy(() -> confirm(validRequest(), 88L, false))
                .isInstanceOf(ServiceException.class).hasMessageContaining("岗位不存在");

        SysPost disabled = canonicalPost();
        disabled.setStatus("1");
        when(postMapper.selectPostByIdForUpdate(402L)).thenReturn(disabled);
        assertThatThrownBy(() -> confirm(validRequest(), 88L, false))
                .isInstanceOf(ServiceException.class).hasMessageContaining("停用");

        SysPost enabled = canonicalPost();
        when(postMapper.selectPostByIdForUpdate(402L)).thenReturn(enabled);
        HrRegularizationRequest mismatch = validRequest();
        mismatch.setPostName("客户端伪造名称");
        assertThatThrownBy(() -> confirm(mismatch, 88L, false))
                .isInstanceOf(ServiceException.class).hasMessageContaining("岗位代码或名称");
        verify(actionMapper, never()).insertAction(any());
    }

    @Test
    @DisplayName("职级受单字段schema约束必须代码名称一致且薪资精度不做舍入")
    void shouldValidateGradeAndExactMoneyPrecision()
    {
        when(configMapper.selectConfiguredSignHrUserId()).thenReturn(88L);
        HrRegularizationRequest grade = validRequest();
        grade.setJobGradeName("四级");
        assertThatThrownBy(() -> confirm(grade, 88L, false))
                .isInstanceOf(ServiceException.class).hasMessageContaining("职级代码和名称必须一致");

        HrRegularizationRequest scale = validRequest();
        scale.setBaseSalary(new BigDecimal("6000.001"));
        assertThatThrownBy(() -> confirm(scale, 88L, false))
                .isInstanceOf(ServiceException.class).hasMessageContaining("小数不能超过2位");

        HrRegularizationRequest total = validRequest();
        total.setSalaryTotal(new BigDecimal("9000.01"));
        assertThatThrownBy(() -> confirm(total, 88L, false))
                .isInstanceOf(ServiceException.class).hasMessageContaining("等于各薪资项之和");
        verify(profileMapper, never()).selectSigningSnapshotByUserIdForUpdate(anyLong());
    }

    @Test
    @DisplayName("同requestId仅精确payload和已落库状态可重放且不重复写")
    void shouldReplayOnlyExactPersistedPayload() throws Exception
    {
        when(configMapper.selectConfiguredSignHrUserId()).thenReturn(88L);
        HrEmployeeSigningSnapshot before = probationSnapshot();
        HrEmployeeSigningSnapshot after = regularizedSnapshot();
        when(profileMapper.selectSigningSnapshotByUserIdForUpdate(9L)).thenReturn(after);
        SysHrLifecycleAction existing = existingAction(before, after);
        when(actionMapper.selectByRequestIdForUpdate("req-regularize-1"))
                .thenReturn(existing);

        assertThat(confirm(validRequest(), 88L, false)).isEqualTo(801L);
        verify(postMapper, never()).selectPostByIdForUpdate(anyLong());
        verify(profileMapper, never()).updateRegularizationProfile(any(), any());
        verify(userPostMapper, never()).deleteUserPostByUserId(anyLong());
        verify(outboxMapper, never()).insertOutbox(any());

        HrRegularizationRequest changed = validRequest();
        changed.setSalaryVersion("2026-V3");
        assertThatThrownBy(() -> confirm(changed, 88L, false))
                .isInstanceOf(ServiceException.class).hasMessageContaining("payload不一致");
    }

    @Test
    @DisplayName("档案或岗位关联任一步写失败均抛错使整笔事务回滚且不写outbox")
    void shouldFailTransactionWhenProfileOrPostAssociationWriteFails()
    {
        stubFreshRegularization();
        when(profileMapper.updateRegularizationProfile(any(), any())).thenReturn(0);
        assertThatThrownBy(() -> confirm(validRequest(), 88L, false))
                .isInstanceOf(ServiceException.class).hasMessageContaining("档案更新失败");
        verify(userPostMapper, never()).deleteUserPostByUserId(anyLong());
        verify(outboxMapper, never()).insertOutbox(any());

        setUp();
        stubFreshRegularization();
        when(userPostMapper.deleteUserPostByUserId(9L)).thenReturn(0);
        assertThatThrownBy(() -> confirm(validRequest(), 88L, false))
                .isInstanceOf(ServiceException.class).hasMessageContaining("原岗位关联更新失败");
        verify(userPostMapper, never()).batchUserPost(any());
        verify(outboxMapper, never()).insertOutbox(any());

        setUp();
        stubFreshRegularization();
        when(userPostMapper.batchUserPost(any())).thenReturn(0);
        assertThatThrownBy(() -> confirm(validRequest(), 88L, false))
                .isInstanceOf(ServiceException.class).hasMessageContaining("新岗位关联写入失败");
        verify(outboxMapper, never()).insertOutbox(any());
    }

    @Test
    @DisplayName("动作或outbox写入失败均终止并依赖事务回滚全部变更")
    void shouldFailTransactionWhenActionOrOutboxWriteFails()
    {
        stubFreshRegularization();
        org.mockito.Mockito.doReturn(0).when(actionMapper).insertAction(any());
        assertThatThrownBy(() -> confirm(validRequest(), 88L, false))
                .isInstanceOf(ServiceException.class).hasMessageContaining("转正动作写入失败");
        verify(profileMapper, never()).updateRegularizationProfile(any(), any());
        verify(userPostMapper, never()).deleteUserPostByUserId(anyLong());
        verify(outboxMapper, never()).insertOutbox(any());

        setUp();
        stubFreshRegularization();
        when(outboxMapper.insertOutbox(any())).thenReturn(0);
        assertThatThrownBy(() -> confirm(validRequest(), 88L, false))
                .isInstanceOf(ServiceException.class).hasMessageContaining("发件箱写入失败");
    }

    @Test
    @DisplayName("requestId唯一键竞争仅对精确相同action快照收敛")
    void shouldConvergeDuplicateKeyOnlyForExactActionPayload() throws Exception
    {
        stubFreshRegularization();
        SysHrLifecycleAction concurrent = existingAction(
                probationSnapshot(), regularizedSnapshot());
        when(actionMapper.selectByRequestIdForUpdate("req-regularize-1"))
                .thenReturn(null, concurrent);
        doThrow(new DuplicateKeyException("concurrent regularization"))
                .when(actionMapper).insertAction(any());

        assertThat(confirm(validRequest(), 88L, false)).isEqualTo(801L);
        verify(profileMapper, never()).updateRegularizationProfile(any(), any());
        verify(userPostMapper, never()).deleteUserPostByUserId(anyLong());
        verify(outboxMapper, never()).insertOutbox(any());

        setUp();
        stubFreshRegularization();
        HrEmployeeSigningSnapshot mismatchedAfter = regularizedSnapshot();
        mismatchedAfter.setSalaryVersion("2026-V9");
        SysHrLifecycleAction mismatch = existingAction(
                probationSnapshot(), mismatchedAfter);
        when(actionMapper.selectByRequestIdForUpdate("req-regularize-1"))
                .thenReturn(null, mismatch);
        doThrow(new DuplicateKeyException("concurrent different payload"))
                .when(actionMapper).insertAction(any());
        assertThatThrownBy(() -> confirm(validRequest(), 88L, false))
                .isInstanceOf(ServiceException.class).hasMessageContaining("payload不一致");
    }

    @Test
    @DisplayName("转正服务边界声明异常整笔回滚")
    void shouldDeclareTransactionalRollbackBoundary() throws Exception
    {
        Method method = HrLifecycleServiceImpl.class.getMethod("confirmRegularization",
                Long.class, HrRegularizationRequest.class, Long.class, String.class,
                boolean.class, String.class, String.class);
        Transactional transactional = method.getAnnotation(Transactional.class);
        assertThat(transactional).isNotNull();
        assertThat(transactional.rollbackFor()).contains(Exception.class);
    }

    private Long confirm(HrRegularizationRequest request, Long operatorId, boolean admin)
    {
        try (org.mockito.MockedStatic<com.erp.common.security.auth.AuthUtil> authorization =
                org.mockito.Mockito.mockStatic(com.erp.common.security.auth.AuthUtil.class))
        {
            return service.confirmRegularization(9L, request, operatorId, "配置HR", admin,
                    "10.0.0.8", "JUnit-UA");
        }
    }

    private void stubBeforePostValidation()
    {
        when(configMapper.selectConfiguredSignHrUserId()).thenReturn(88L);
        when(profileMapper.selectSigningSnapshotByUserIdForUpdate(9L))
                .thenReturn(probationSnapshot());
    }

    private void stubFreshRegularization()
    {
        stubBeforePostValidation();
        when(postMapper.selectPostByIdForUpdate(402L)).thenReturn(canonicalPost());
        doAnswer(invocation -> {
            SysHrLifecycleAction action = invocation.getArgument(0);
            action.setActionId(801L);
            return 1;
        }).when(actionMapper).insertAction(any());
        when(profileMapper.updateRegularizationProfile(any(), any())).thenReturn(1);
        when(userPostMapper.deleteUserPostByUserId(9L)).thenReturn(1);
        when(userPostMapper.batchUserPost(any())).thenReturn(1);
        when(outboxMapper.insertOutbox(any())).thenReturn(1);
    }

    private HrRegularizationRequest validRequest()
    {
        HrRegularizationRequest request = new HrRegularizationRequest();
        request.setRequestId("req-regularize-1");
        request.setActualRegularizationDate(LocalDate.of(2026, 7, 10));
        request.setPostId(402L);
        request.setPostCode("SALESLEAD");
        request.setPostName("销售组长");
        request.setJobGradeCode("P4");
        request.setJobGradeName("P4");
        request.setBaseSalary(new BigDecimal("6000.00"));
        request.setPostSalary(new BigDecimal("2000.00"));
        request.setFieldAllowance(new BigDecimal("300.00"));
        request.setPerformanceSalary(new BigDecimal("700.00"));
        request.setSalaryTotal(new BigDecimal("9000.00"));
        request.setSalaryVersion("2026-V2");
        return request;
    }

    private HrEmployeeSigningSnapshot probationSnapshot()
    {
        HrEmployeeSigningSnapshot snapshot = new HrEmployeeSigningSnapshot();
        snapshot.setEmployeeId(9L);
        snapshot.setEmployeeNo("E000009");
        snapshot.setEmployeeName("张三");
        snapshot.setPhone("13800000000");
        snapshot.setIdType("CN_ID_CARD");
        snapshot.setIdNumber("310101199001011234");
        snapshot.setCurrentAddress("上海市徐汇区");
        snapshot.setEmployeeStatus("试用");
        snapshot.setEmployeeCategory("FULL_TIME");
        snapshot.setShopDeptId(20L);
        snapshot.setShopDeptName("徐汇门店");
        snapshot.setDeptId(20L);
        snapshot.setDeptName("徐汇门店");
        snapshot.setLegalEntityId(300L);
        snapshot.setLegalEntityCode("LE-SH");
        snapshot.setLegalEntityName("上海示例有限公司");
        snapshot.setPostId(401L);
        snapshot.setPostCode("SALES");
        snapshot.setPostName("销售顾问");
        snapshot.setJobGradeCode("P3");
        snapshot.setJobGradeName("P3");
        snapshot.setWorkLocation("上海");
        snapshot.setWorkCityLevel("一线");
        snapshot.setContractTypeCode("LABOR_CONTRACT");
        snapshot.setContractTermCode("FIXED_TERM");
        snapshot.setSocialTypeCode("SOCIAL_INSURED");
        snapshot.setRenewalCount(0);
        snapshot.setEntryDate(LocalDate.of(2026, 5, 1));
        snapshot.setContractStartDate(LocalDate.of(2026, 5, 1));
        snapshot.setContractEndDate(LocalDate.of(2029, 4, 30));
        snapshot.setProbationStartDate(LocalDate.of(2026, 5, 1));
        snapshot.setProbationEndDate(LocalDate.of(2026, 7, 31));
        snapshot.setBaseSalary(new BigDecimal("5000.00"));
        snapshot.setPostSalary(new BigDecimal("2000.00"));
        snapshot.setFieldAllowance(new BigDecimal("300.00"));
        snapshot.setPerformanceSalary(new BigDecimal("700.00"));
        snapshot.setSalaryTotal(new BigDecimal("8000.00"));
        snapshot.setSalaryVersion("2026-V1");
        return snapshot;
    }

    private HrEmployeeSigningSnapshot regularizedSnapshot()
    {
        HrEmployeeSigningSnapshot snapshot = objectMapper.convertValue(
                probationSnapshot(), HrEmployeeSigningSnapshot.class);
        snapshot.setEmployeeStatus("正式");
        snapshot.setActualRegularizationDate(LocalDate.of(2026, 7, 10));
        snapshot.setPostId(402L);
        snapshot.setPostCode("SALESLEAD");
        snapshot.setPostName("销售组长");
        snapshot.setJobGradeCode("P4");
        snapshot.setJobGradeName("P4");
        snapshot.setBaseSalary(new BigDecimal("6000.00"));
        snapshot.setSalaryTotal(new BigDecimal("9000.00"));
        snapshot.setSalaryVersion("2026-V2");
        return snapshot;
    }

    private SysPost canonicalPost()
    {
        SysPost post = new SysPost();
        post.setPostId(402L);
        post.setPostCode("SALESLEAD");
        post.setPostName("销售组长");
        post.setStatus("0");
        return post;
    }

    private SysHrLifecycleAction existingAction(HrEmployeeSigningSnapshot before,
            HrEmployeeSigningSnapshot after) throws Exception
    {
        SysHrLifecycleAction action = new SysHrLifecycleAction();
        action.setActionId(801L);
        action.setActionType("REGULARIZATION_CONFIRMED");
        action.setEmployeeId(9L);
        action.setSourceType("HR_REGULARIZATION");
        action.setSourceBusinessId("req-regularize-1");
        action.setBeforeSnapshotJson(objectMapper.writeValueAsString(before));
        action.setAfterSnapshotJson(objectMapper.writeValueAsString(after));
        action.setEffectiveDate(LocalDate.of(2026, 7, 10));
        action.setBusinessStatus("CONFIRMED");
        action.setRequestId("req-regularize-1");
        action.setVersion(1L);
        return action;
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
