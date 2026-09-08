package com.erp.system.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import com.erp.common.core.exception.ServiceException;
import com.erp.oa.api.domain.HrEmployeeSigningSnapshot;
import com.erp.oa.api.domain.HrSignBusinessEvent;
import com.erp.system.api.domain.SysDept;
import com.erp.system.api.domain.SysUser;
import com.erp.system.domain.dto.HrEmployeeTransferRequest;
import com.erp.system.domain.dto.HrTransferRiskConfirmation;
import com.erp.system.domain.SysHrLifecycleAction;
import com.erp.system.domain.SysHrSignEventOutbox;
import com.erp.system.domain.SysPost;
import com.erp.system.domain.SysUserPost;
import com.erp.system.mapper.SysConfigMapper;
import com.erp.system.mapper.SysDeptMapper;
import com.erp.system.mapper.SysHrLifecycleActionMapper;
import com.erp.system.mapper.SysHrRenewalGuardMapper;
import com.erp.system.mapper.SysHrSignEventOutboxMapper;
import com.erp.system.mapper.SysPostMapper;
import com.erp.system.mapper.SysUserPostMapper;
import com.erp.system.mapper.SysUserProfileMapper;
import com.erp.system.mapper.SysUserMapper;
import com.erp.system.service.ISysUserShopService;
import org.mockito.ArgumentCaptor;

@DisplayName("HR调岗生命周期")
class HrEmployeeTransferLifecycleTest
{
    private SysConfigMapper configMapper;
    private SysUserProfileMapper profileMapper;
    private SysHrLifecycleActionMapper actionMapper;
    private SysHrSignEventOutboxMapper outboxMapper;
    private SysPostMapper postMapper;
    private SysDeptMapper deptMapper;
    private SysUserMapper userMapper;
    private SysUserPostMapper userPostMapper;
    private ISysUserShopService userShopService;
    private HrLifecycleServiceImpl service;

    @BeforeEach
    void setUp()
    {
        configMapper = mock(SysConfigMapper.class);
        profileMapper = mock(SysUserProfileMapper.class);
        actionMapper = mock(SysHrLifecycleActionMapper.class);
        outboxMapper = mock(SysHrSignEventOutboxMapper.class);
        postMapper = mock(SysPostMapper.class);
        deptMapper = mock(SysDeptMapper.class);
        userMapper = mock(SysUserMapper.class);
        userPostMapper = mock(SysUserPostMapper.class);
        userShopService = mock(ISysUserShopService.class);
        service = new HrLifecycleServiceImpl(configMapper, profileMapper, actionMapper,
                outboxMapper, mock(SysHrRenewalGuardMapper.class), postMapper,
                deptMapper, userMapper, userPostMapper, userShopService,
                JsonMapper.builder().findAndAddModules().build());
        ReflectionTestUtils.setField(service, "clock", Clock.fixed(
                Instant.parse("2026-12-31T16:30:00Z"), ZoneId.of("UTC")));
    }

    @Test
    @DisplayName("调岗入口必须使用稳定请求契约和单HR权限")
    void shouldExposeStableTransferContract() throws Exception
    {
        Path request = repoFile(
                "erp-modules/erp-system/src/main/java/com/erp/system/domain/dto/HrEmployeeTransferRequest.java");
        assertThat(request).exists();

        String controller = Files.readString(repoFile(
                "erp-modules/erp-system/src/main/java/com/erp/system/controller/HrEmployeeProfileController.java"),
                StandardCharsets.UTF_8);
        assertThat(controller).contains(
                "@RequiresPermissions(\"hr:employee:transfer\")",
                "@PostMapping(\"/{userId}/transfer\")",
                "HrEmployeeTransferRequest request",
                "hrLifecycleService.confirmTransfer");

        String lifecycle = Files.readString(repoFile(
                "erp-modules/erp-system/src/main/java/com/erp/system/service/IHrLifecycleService.java"),
                StandardCharsets.UTF_8);
        assertThat(lifecycle).contains("Long confirmTransfer(");
    }

    @Test
    @DisplayName("未来日期调岗必须按上海自然日立即拒绝且没有任何业务副作用")
    void shouldRejectFutureTransferWithoutSideEffects()
    {
        when(configMapper.selectConfiguredSignHrUserId()).thenReturn(88L);
        HrEmployeeTransferRequest request = validRequest();
        request.setEffectiveDate(LocalDate.of(2027, 1, 2));

        assertThatThrownBy(() -> confirm(request, 88L))
                .isInstanceOf(ServiceException.class)
                .hasMessage("未来日期的调岗暂不能确认，请在生效当天操作");

        verify(profileMapper, never()).selectSigningSnapshotByUserIdForUpdate(9L);
        verify(actionMapper, never()).insertAction(org.mockito.ArgumentMatchers.any());
        verify(outboxMapper, never()).insertOutbox(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("调岗服务必须使用权威组织岗位和专用条件更新Mapper")
    void shouldUseCanonicalTransferMappers() throws Exception
    {
        Set<Class<?>> constructorTypes = Arrays.stream(
                HrLifecycleServiceImpl.class.getConstructors())
                .flatMap(constructor -> Arrays.stream(constructor.getParameterTypes()))
                .collect(Collectors.toSet());
        assertThat(constructorTypes).contains(SysDeptMapper.class, SysUserMapper.class);
        assertThat(Arrays.stream(SysUserProfileMapper.class.getMethods())
                .map(method -> method.getName()))
                .contains("lockSigningProfileByUserId", "updateTransferProfile");
        assertThat(Arrays.stream(SysUserMapper.class.getMethods())
                .map(method -> method.getName()))
                .contains("updateTransferDept");
        assertThat(Arrays.stream(SysHrLifecycleActionMapper.class.getMethods())
                .map(method -> method.getName()))
                .contains("selectTransferActionsForUpdate");
        String snapshotMapper = Files.readString(repoFile(
                "erp-modules/erp-system/src/main/resources/mapper/system/SysUserProfileMapper.xml"),
                StandardCharsets.UTF_8);
        assertThat(snapshotMapper).contains(
                "p.direct_supervisor_user_id as directSupervisorId");
    }

    @Test
    @DisplayName("调岗审计必须分离业务日期且签约权限兼容钩子不再调用旧存储过程")
    void shouldDefineTransferAuditPersistence() throws Exception
    {
        assertThat(Arrays.stream(SysHrLifecycleAction.class.getMethods())
                .map(method -> method.getName()))
                .contains("getActualConfirmTime", "getRiskConfirmationJson",
                        "getHistoricalReason");

        Path rootSql = repoFile("sql/erp_hr_transfer_effective_date_20260713.sql");
        Path dockerSql = repoFile("docker/mysql/db/erp_hr_transfer_effective_date_20260713.sql");
        assertThat(rootSql).exists();
        assertThat(dockerSql).exists();
        String sql = Files.readString(rootSql, StandardCharsets.UTF_8);
        assertThat(sql).isEqualTo(Files.readString(dockerSql, StandardCharsets.UTF_8));
        assertThat(sql).contains("actual_confirm_time", "risk_confirmation_json",
                "historical_reason", "hr:employee:transfer",
                "sync_sign_hr_permissions_with_transfer");

        String configMapper = Files.readString(repoFile(
                "erp-modules/erp-system/src/main/resources/mapper/system/SysConfigMapper.xml"),
                StandardCharsets.UTF_8);
        assertThat(configMapper)
                .contains("<update id=\"syncSignHrPermissions\">",
                        "update sys_role",
                        "set role_id = role_id",
                        "where 1 = 0")
                .doesNotContain("{CALL sync_sign_hr_permissions_with_offboarding()}",
                        "{CALL sync_sign_hr_permissions_with_transfer()}");
    }

    @Test
    @DisplayName("上海业务日必须正确跨越闰日、月末和年末且不依赖服务器默认时区")
    void shouldResolveShanghaiBusinessDateAtCalendarBoundaries()
    {
        assertBusinessDate("2024-02-28T15:59:59Z", LocalDate.of(2024, 2, 28));
        assertBusinessDate("2024-02-28T16:00:00Z", LocalDate.of(2024, 2, 29));
        assertBusinessDate("2026-04-30T15:59:59Z", LocalDate.of(2026, 4, 30));
        assertBusinessDate("2026-04-30T16:00:00Z", LocalDate.of(2026, 5, 1));
        assertBusinessDate("2026-12-31T16:00:00Z", LocalDate.of(2027, 1, 1));
    }

    @Test
    @DisplayName("当天调岗应原子写当前档案、前后快照、生命周期动作和typed outbox")
    void shouldConfirmTodayTransferAndPublishFrozenSnapshots() throws Exception
    {
        stubFreshTransfer();

        Long actionId = confirm(validRequest(), 88L);

        assertThat(actionId).isEqualTo(801L);
        ArgumentCaptor<HrEmployeeSigningSnapshot> profile =
                ArgumentCaptor.forClass(HrEmployeeSigningSnapshot.class);
        ArgumentCaptor<SysHrLifecycleAction> action =
                ArgumentCaptor.forClass(SysHrLifecycleAction.class);
        ArgumentCaptor<SysHrSignEventOutbox> outbox =
                ArgumentCaptor.forClass(SysHrSignEventOutbox.class);
        ArgumentCaptor<List<SysUserPost>> posts = ArgumentCaptor.forClass(List.class);
        verify(userMapper).updateTransferDept(9L, 30L, 20L, "配置HR");
        verify(profileMapper).updateTransferProfile(profile.capture(), eq("配置HR"));
        verify(userPostMapper).deleteUserPostByUserId(9L);
        verify(userPostMapper).batchUserPost(posts.capture());
        verify(actionMapper).insertAction(action.capture());
        verify(outboxMapper).insertOutbox(outbox.capture());
        verify(userShopService).checkUserShopScope(88L, 20L, false);
        verify(userShopService).checkUserShopScope(88L, 30L, false);

        HrEmployeeSigningSnapshot after = profile.getValue();
        assertThat(after.getShopDeptId()).isEqualTo(30L);
        assertThat(after.getShopDeptName()).isEqualTo("上海二店");
        assertThat(after.getDeptId()).isEqualTo(30L);
        assertThat(after.getPostId()).isEqualTo(402L);
        assertThat(after.getPostName()).isEqualTo("店长");
        assertThat(after.getPositionNo()).isEqualTo("STOREMANAGER-E000009");
        assertThat(after.getTransferEffectiveDate()).isEqualTo(LocalDate.of(2027, 1, 1));
        assertThat(after.getDirectSupervisorId()).isEqualTo(66L);
        assertThat(after.getDirectSupervisorName()).isEqualTo("区域经理");
        assertThat(after.getSalaryTotal()).isEqualByComparingTo("10000.00");
        assertThat(posts.getValue()).singleElement().satisfies(link -> {
            assertThat(link.getUserId()).isEqualTo(9L);
            assertThat(link.getPostId()).isEqualTo(402L);
        });

        SysHrLifecycleAction recorded = action.getValue();
        assertThat(recorded.getActionType()).isEqualTo("TRANSFER_CONFIRMED");
        assertThat(recorded.getSourceType()).isEqualTo("HR_TRANSFER");
        assertThat(recorded.getEffectiveDate()).isEqualTo(LocalDate.of(2027, 1, 1));
        assertThat(recorded.getActualConfirmTime())
                .isEqualTo(Date.from(Instant.parse("2026-12-31T16:30:00Z")));
        assertThat(recorded.getRiskLevel()).isEqualTo("LOW");
        assertThat(recorded.getRiskConfirmationJson()).isNull();
        assertThat(recorded.getHistoricalReason()).isNull();
        assertThat(recorded.getBeforeSnapshotJson()).contains(
                "\"shopDeptId\":20", "\"postId\":401");
        assertThat(recorded.getAfterSnapshotJson()).contains(
                "\"shopDeptId\":30", "\"postId\":402",
                "\"transferEffectiveDate\":\"2027-01-01\"");

        HrSignBusinessEvent event = JsonMapper.builder().findAndAddModules().build()
                .readValue(outbox.getValue().getPayloadJson(), HrSignBusinessEvent.class);
        assertThat(event.getScenario()).isEqualTo("TRANSFER");
        assertThat(event.getBeforeSnapshot().getPostName()).isEqualTo("销售顾问");
        assertThat(event.getAfterSnapshot().getPostName()).isEqualTo("店长");
        assertThat(event.getAttributes())
                .containsEntry("effectiveDate", "2027-01-01")
                .containsEntry("historicalSupplement", false)
                .containsEntry("sourceActionId", 801)
                .containsEntry("sourceActionVersion", 1);
    }

    @Test
    @DisplayName("过去日期未提交同一HR结构化二次确认时必须拒绝")
    void shouldRejectHistoricalTransferWithoutRiskConfirmation()
    {
        when(configMapper.selectConfiguredSignHrUserId()).thenReturn(88L);
        when(profileMapper.selectSigningSnapshotByUserIdForUpdate(9L))
                .thenReturn(currentSnapshot());
        HrEmployeeTransferRequest request = validRequest();
        request.setEffectiveDate(LocalDate.of(2026, 12, 31));

        assertThatThrownBy(() -> confirm(request, 88L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("高风险补录")
                .hasMessageContaining("二次确认");
        verify(actionMapper, never()).insertAction(any());
        verify(outboxMapper, never()).insertOutbox(any());
        verify(userMapper, never()).updateTransferDept(any(), any(), any(), any());
    }

    @Test
    @DisplayName("有效历史补录确认必须标记HIGH并保存原因、确认内容和真实操作时间")
    void shouldConfirmHistoricalTransferWithHighRiskAudit()
    {
        stubFreshTransfer();
        HrEmployeeTransferRequest request = validRequest();
        request.setEffectiveDate(LocalDate.of(2026, 12, 31));
        request.setRiskConfirmation(validRiskConfirmation(request));

        assertThat(confirm(request, 88L)).isEqualTo(801L);

        ArgumentCaptor<SysHrLifecycleAction> action =
                ArgumentCaptor.forClass(SysHrLifecycleAction.class);
        ArgumentCaptor<SysHrSignEventOutbox> outbox =
                ArgumentCaptor.forClass(SysHrSignEventOutbox.class);
        verify(actionMapper).insertAction(action.capture());
        verify(outboxMapper).insertOutbox(outbox.capture());
        assertThat(action.getValue().getRiskLevel()).isEqualTo("HIGH");
        assertThat(action.getValue().getRiskCodesJson()).contains("HISTORICAL_BACKFILL");
        assertThat(action.getValue().getRiskConfirmationJson()).contains(
                "\"confirmed\":true", "\"operationDate\":\"2027-01-01\"",
                "\"riskStatement\":\"该操作将按历史日期补录并立即修改当前员工档案\"");
        assertThat(action.getValue().getHistoricalReason()).isEqualTo("补录纸质调岗单");
        assertThat(action.getValue().getEffectiveDate()).isEqualTo(LocalDate.of(2026, 12, 31));
        assertThat(action.getValue().getActualConfirmTime())
                .isEqualTo(Date.from(Instant.parse("2026-12-31T16:30:00Z")));
        assertThat(outbox.getValue().getPayloadJson()).contains(
                "\"effectiveDate\":\"2026-12-31\"",
                "\"historicalSupplement\":true");
    }

    @Test
    @DisplayName("历史确认内容与服务端员工、原目标组织岗位或日期任一不符都必须拒绝")
    void shouldRejectMismatchedHistoricalRiskConfirmation()
    {
        stubTransferBeforeWrites();
        HrEmployeeTransferRequest request = validRequest();
        request.setEffectiveDate(LocalDate.of(2026, 12, 31));
        HrTransferRiskConfirmation confirmation = validRiskConfirmation(request);
        confirmation.setBeforePostName("客户端伪造原岗位");
        request.setRiskConfirmation(confirmation);

        assertThatThrownBy(() -> confirm(request, 88L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("二次确认内容与当前调岗信息不一致");
        verify(actionMapper, never()).insertAction(any());
        verify(outboxMapper, never()).insertOutbox(any());
    }

    @Test
    @DisplayName("非唯一HR即使是管理员也不能确认调岗")
    void shouldRequireConfiguredHr()
    {
        when(configMapper.selectConfiguredSignHrUserId()).thenReturn(88L);
        assertThatThrownBy(() -> confirm(validRequest(), 77L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("配置HR");
        verify(profileMapper, never()).selectSigningSnapshotByUserIdForUpdate(any());
    }

    @Test
    @DisplayName("前后组织岗位职级地点薪资法律主体和汇报关系均无变化时不创建动作")
    void shouldRejectTransferWithoutBusinessChanges()
    {
        HrEmployeeSigningSnapshot current = currentSnapshot();
        current.setShopDeptId(30L);
        current.setShopDeptName("上海二店");
        current.setDeptId(30L);
        current.setDeptName("上海二店");
        current.setPostId(402L);
        current.setPostCode("STOREMANAGER");
        current.setPostName("店长");
        current.setJobGradeCode("P4");
        current.setJobGradeName("P4");
        current.setWorkLocation("上海市浦东新区");
        current.setDepartmentSupervisorName("新部门主管");
        current.setBaseSalary(new BigDecimal("6000.00"));
        current.setPostSalary(new BigDecimal("2000.00"));
        current.setFieldAllowance(new BigDecimal("500.00"));
        current.setPerformanceSalary(new BigDecimal("1500.00"));
        current.setSalaryTotal(new BigDecimal("10000.00"));
        current.setSalaryVersion("TRANSFER-2027-01");
        current.setDirectSupervisorId(66L);
        current.setDirectSupervisorName("区域经理");
        when(configMapper.selectConfiguredSignHrUserId()).thenReturn(88L);
        when(profileMapper.selectSigningSnapshotByUserIdForUpdate(9L)).thenReturn(current);
        when(deptMapper.selectDeptByIdForUpdate(30L)).thenReturn(canonicalDept());
        when(postMapper.selectPostByIdForUpdate(402L)).thenReturn(canonicalPost());
        when(userMapper.selectUserById(66L)).thenReturn(canonicalSupervisor());

        assertThatThrownBy(() -> confirm(validRequest(), 88L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("调岗前后没有业务变化");
        verify(actionMapper, never()).insertAction(any());
    }

    @Test
    @DisplayName("相同requestId和完全相同payload重试必须只返回原动作且不重复写业务数据")
    void shouldReplayIdenticalTransferRequestWithoutDuplicateWrites() throws Exception
    {
        HrEmployeeTransferRequest request = validRequest();
        when(configMapper.selectConfiguredSignHrUserId()).thenReturn(88L);
        when(profileMapper.selectSigningSnapshotByUserIdForUpdate(9L))
                .thenReturn(transferredSnapshot(request));
        when(actionMapper.selectByRequestIdForUpdate(request.getRequestId()))
                .thenReturn(completedTransferAction(request));

        assertThat(confirm(request, 88L)).isEqualTo(801L);

        verify(actionMapper, never()).insertAction(any());
        verify(userMapper, never()).updateTransferDept(any(), any(), any(), any());
        verify(profileMapper, never()).updateTransferProfile(any(), any());
        verify(userPostMapper, never()).deleteUserPostByUserId(any());
        verify(outboxMapper, never()).insertOutbox(any());
    }

    @Test
    @DisplayName("历史补录的相同请求跨自然日重试必须按原确认日幂等返回")
    void shouldReplayHistoricalTransferAgainstOriginalConfirmationDate() throws Exception
    {
        HrEmployeeTransferRequest request = validRequest();
        request.setEffectiveDate(LocalDate.of(2026, 12, 31));
        request.setRiskConfirmation(validRiskConfirmation(request));
        SysHrLifecycleAction existing = completedTransferAction(request);
        existing.setActualConfirmTime(
                Date.from(Instant.parse("2026-12-31T16:30:00Z")));
        existing.setHistoricalReason("补录纸质调岗单");
        ReflectionTestUtils.setField(service, "clock", Clock.fixed(
                Instant.parse("2027-01-01T16:30:00Z"), ZoneId.of("UTC")));
        when(configMapper.selectConfiguredSignHrUserId()).thenReturn(88L);
        when(profileMapper.selectSigningSnapshotByUserIdForUpdate(9L))
                .thenReturn(transferredSnapshot(request));
        when(actionMapper.selectByRequestIdForUpdate(request.getRequestId()))
                .thenReturn(existing);

        assertThat(confirm(request, 88L)).isEqualTo(801L);

        verify(actionMapper, never()).insertAction(any());
        verify(userMapper, never()).updateTransferDept(any(), any(), any(), any());
        verify(outboxMapper, never()).insertOutbox(any());
    }

    @Test
    @DisplayName("相同requestId但payload不同必须拒绝且不产生任何重复写入")
    void shouldRejectReusedRequestIdWithDifferentPayload() throws Exception
    {
        HrEmployeeTransferRequest original = validRequest();
        HrEmployeeTransferRequest changed = validRequest();
        changed.setPostSalary(new BigDecimal("2100.00"));
        changed.setSalaryTotal(new BigDecimal("10100.00"));
        when(configMapper.selectConfiguredSignHrUserId()).thenReturn(88L);
        when(profileMapper.selectSigningSnapshotByUserIdForUpdate(9L))
                .thenReturn(transferredSnapshot(original));
        when(actionMapper.selectByRequestIdForUpdate(original.getRequestId()))
                .thenReturn(completedTransferAction(original));

        assertThatThrownBy(() -> confirm(changed, 88L))
                .isInstanceOf(ServiceException.class)
                .hasMessage("requestId对应的调岗payload不一致");

        verify(actionMapper, never()).insertAction(any());
        verify(userMapper, never()).updateTransferDept(any(), any(), any(), any());
        verify(outboxMapper, never()).insertOutbox(any());
    }

    private void stubFreshTransfer()
    {
        stubTransferBeforeWrites();
        when(actionMapper.selectTransferActionsForUpdate(9L, validRequest().getEffectiveDate()))
                .thenReturn(List.of());
        doAnswer(invocation -> {
            SysHrLifecycleAction action = invocation.getArgument(0);
            action.setActionId(801L);
            return 1;
        }).when(actionMapper).insertAction(any(SysHrLifecycleAction.class));
        when(userMapper.updateTransferDept(9L, 30L, 20L, "配置HR")).thenReturn(1);
        when(profileMapper.updateTransferProfile(any(), eq("配置HR"))).thenReturn(1);
        when(userPostMapper.deleteUserPostByUserId(9L)).thenReturn(1);
        when(userPostMapper.batchUserPost(any())).thenReturn(1);
        when(outboxMapper.insertOutbox(any())).thenReturn(1);
    }

    private void stubTransferBeforeWrites()
    {
        when(configMapper.selectConfiguredSignHrUserId()).thenReturn(88L);
        when(profileMapper.selectSigningSnapshotByUserIdForUpdate(9L))
                .thenReturn(currentSnapshot());
        when(actionMapper.selectByRequestIdForUpdate("transfer-request-1")).thenReturn(null);
        when(actionMapper.selectTransferActionsForUpdate(9L, LocalDate.of(2027, 1, 1)))
                .thenReturn(List.of());
        when(actionMapper.selectTransferActionsForUpdate(9L, LocalDate.of(2026, 12, 31)))
                .thenReturn(List.of());
        when(deptMapper.selectDeptByIdForUpdate(30L)).thenReturn(canonicalDept());
        when(postMapper.selectPostByIdForUpdate(402L)).thenReturn(canonicalPost());
        when(userMapper.selectUserById(66L)).thenReturn(canonicalSupervisor());
    }

    private HrEmployeeSigningSnapshot currentSnapshot()
    {
        HrEmployeeSigningSnapshot snapshot = new HrEmployeeSigningSnapshot();
        snapshot.setEmployeeId(9L);
        snapshot.setEmployeeNo("E000009");
        snapshot.setEmployeeName("张三");
        snapshot.setPhone("13800000009");
        snapshot.setIdType("身份证");
        snapshot.setIdNumber("310101199001010019");
        snapshot.setCurrentAddress("上海市黄浦区");
        snapshot.setEmployeeStatus("正式");
        snapshot.setEmployeeCategory("全职");
        snapshot.setShopDeptId(20L);
        snapshot.setShopDeptName("上海一店");
        snapshot.setDeptId(20L);
        snapshot.setDeptName("上海一店");
        snapshot.setPostId(401L);
        snapshot.setPostCode("SALES");
        snapshot.setPostName("销售顾问");
        snapshot.setJobGradeCode("P3");
        snapshot.setJobGradeName("P3");
        snapshot.setDirectSupervisorId(55L);
        snapshot.setDirectSupervisorName("原店长");
        snapshot.setDepartmentSupervisorName("原部门主管");
        snapshot.setWorkLocation("上海市黄浦区");
        snapshot.setWorkCityLevel("一线");
        snapshot.setLegalEntityId(301L);
        snapshot.setLegalEntityCode("SH-COMPANY");
        snapshot.setLegalEntityName("上海公司");
        snapshot.setContractTypeCode("LABOR_CONTRACT");
        snapshot.setContractTermCode("THREE_YEAR");
        snapshot.setSocialTypeCode("SOCIAL_INSURED");
        snapshot.setRenewalCount(0);
        snapshot.setEntryDate(LocalDate.of(2024, 1, 1));
        snapshot.setContractStartDate(LocalDate.of(2024, 1, 1));
        snapshot.setContractEndDate(LocalDate.of(2027, 12, 31));
        snapshot.setBaseSalary(new BigDecimal("5500.00"));
        snapshot.setPostSalary(new BigDecimal("1800.00"));
        snapshot.setFieldAllowance(new BigDecimal("400.00"));
        snapshot.setPerformanceSalary(new BigDecimal("1300.00"));
        snapshot.setSalaryTotal(new BigDecimal("9000.00"));
        snapshot.setSalaryVersion("CURRENT-2026");
        return snapshot;
    }

    private SysDept canonicalDept()
    {
        SysDept dept = new SysDept();
        dept.setDeptId(30L);
        dept.setDeptName("上海二店");
        dept.setStatus("0");
        dept.setDelFlag("0");
        dept.setLeader("新部门主管");
        dept.setDeptType("STORE");
        return dept;
    }

    private SysPost canonicalPost()
    {
        SysPost post = new SysPost();
        post.setPostId(402L);
        post.setPostCode("STOREMANAGER");
        post.setPostName("店长");
        post.setStatus("0");
        return post;
    }

    private SysUser canonicalSupervisor()
    {
        SysUser user = new SysUser();
        user.setUserId(66L);
        user.setNickName("区域经理");
        user.setStatus("0");
        user.setDelFlag("0");
        return user;
    }

    private HrTransferRiskConfirmation validRiskConfirmation(
            HrEmployeeTransferRequest request)
    {
        HrTransferRiskConfirmation confirmation = new HrTransferRiskConfirmation();
        confirmation.setConfirmed(true);
        confirmation.setEmployeeId(9L);
        confirmation.setEmployeeName("张三");
        confirmation.setBeforeDeptId(20L);
        confirmation.setBeforeDeptName("上海一店");
        confirmation.setBeforePostId(401L);
        confirmation.setBeforePostName("销售顾问");
        confirmation.setAfterDeptId(30L);
        confirmation.setAfterDeptName("上海二店");
        confirmation.setAfterPostId(402L);
        confirmation.setAfterPostName("店长");
        confirmation.setEffectiveDate(request.getEffectiveDate());
        confirmation.setOperationDate(LocalDate.of(2027, 1, 1));
        confirmation.setRiskStatement("该操作将按历史日期补录并立即修改当前员工档案");
        confirmation.setReason("补录纸质调岗单");
        return confirmation;
    }

    private SysHrLifecycleAction completedTransferAction(
            HrEmployeeTransferRequest request) throws Exception
    {
        JsonMapper mapper = JsonMapper.builder().findAndAddModules().build();
        SysHrLifecycleAction action = new SysHrLifecycleAction();
        action.setActionId(801L);
        action.setActionType("TRANSFER_CONFIRMED");
        action.setSourceType("HR_TRANSFER");
        action.setSourceBusinessId(request.getRequestId());
        action.setRequestId(request.getRequestId());
        action.setEmployeeId(9L);
        action.setEffectiveDate(request.getEffectiveDate());
        action.setVersion(1L);
        action.setBeforeSnapshotJson(mapper.writeValueAsString(currentSnapshot()));
        action.setAfterSnapshotJson(mapper.writeValueAsString(transferredSnapshot(request)));
        return action;
    }

    private HrEmployeeSigningSnapshot transferredSnapshot(
            HrEmployeeTransferRequest request)
    {
        HrEmployeeSigningSnapshot snapshot = currentSnapshot();
        snapshot.setShopDeptId(request.getTargetDeptId());
        snapshot.setShopDeptName(request.getTargetDeptName());
        snapshot.setDeptId(request.getTargetDeptId());
        snapshot.setDeptName(request.getTargetDeptName());
        snapshot.setPostId(request.getPostId());
        snapshot.setPostCode(request.getPostCode());
        snapshot.setPostName(request.getPostName());
        snapshot.setJobGradeCode(request.getJobGradeCode());
        snapshot.setJobGradeName(request.getJobGradeName());
        snapshot.setWorkLocation(request.getWorkLocation());
        snapshot.setWorkCityLevel(request.getWorkCityLevel());
        snapshot.setDirectSupervisorId(request.getDirectSupervisorId());
        snapshot.setDirectSupervisorName(request.getDirectSupervisorName());
        snapshot.setDepartmentSupervisorName("新部门主管");
        snapshot.setLegalEntityId(request.getLegalEntityId());
        snapshot.setLegalEntityCode(request.getLegalEntityCode());
        snapshot.setLegalEntityName(request.getLegalEntityName());
        snapshot.setBaseSalary(request.getBaseSalary());
        snapshot.setPostSalary(request.getPostSalary());
        snapshot.setFieldAllowance(request.getFieldAllowance());
        snapshot.setPerformanceSalary(request.getPerformanceSalary());
        snapshot.setSalaryTotal(request.getSalaryTotal());
        snapshot.setSalaryVersion(request.getSalaryVersion());
        snapshot.setTransferEffectiveDate(request.getEffectiveDate());
        return snapshot;
    }

    private Long confirm(HrEmployeeTransferRequest request, Long operatorUserId)
    {
        return service.confirmTransfer(9L, request, operatorUserId, "配置HR", false,
                "10.0.0.8", "transfer-test-agent");
    }

    private void assertBusinessDate(String instant, LocalDate expected)
    {
        ReflectionTestUtils.setField(service, "clock",
                Clock.fixed(Instant.parse(instant), ZoneId.of("America/Los_Angeles")));
        assertThat(service.transferBusinessDate()).isEqualTo(expected);
    }

    private HrEmployeeTransferRequest validRequest()
    {
        HrEmployeeTransferRequest request = new HrEmployeeTransferRequest();
        request.setRequestId("transfer-request-1");
        request.setEffectiveDate(LocalDate.of(2027, 1, 1));
        request.setTargetDeptId(30L);
        request.setTargetDeptName("上海二店");
        request.setPostId(402L);
        request.setPostCode("STOREMANAGER");
        request.setPostName("店长");
        request.setJobGradeCode("P4");
        request.setJobGradeName("P4");
        request.setWorkLocation("上海市浦东新区");
        request.setWorkCityLevel("一线");
        request.setDirectSupervisorId(66L);
        request.setDirectSupervisorName("区域经理");
        request.setLegalEntityId(301L);
        request.setLegalEntityCode("SH-COMPANY");
        request.setLegalEntityName("上海公司");
        request.setBaseSalary(new BigDecimal("6000.00"));
        request.setPostSalary(new BigDecimal("2000.00"));
        request.setFieldAllowance(new BigDecimal("500.00"));
        request.setPerformanceSalary(new BigDecimal("1500.00"));
        request.setSalaryTotal(new BigDecimal("10000.00"));
        request.setSalaryVersion("TRANSFER-2027-01");
        return request;
    }

    private static Path repoFile(String relativePath)
    {
        Path current = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
        for (int i = 0; i < 6 && current != null; i++, current = current.getParent())
        {
            Path candidate = current.resolve(relativePath);
            if (Files.exists(candidate)
                    || (Files.isDirectory(current.resolve("erp-modules"))
                        && Files.isDirectory(current.resolve("erp-ui"))))
            {
                return candidate;
            }
        }
        return Paths.get(relativePath).toAbsolutePath().normalize();
    }
}
