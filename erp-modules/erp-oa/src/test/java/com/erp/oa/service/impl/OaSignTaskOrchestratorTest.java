package com.erp.oa.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;
import org.apache.ibatis.io.Resources;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import com.erp.oa.api.domain.HrEmployeeSigningSnapshot;
import com.erp.oa.api.domain.HrRenewalGuard;
import com.erp.oa.api.domain.HrSignBusinessEvent;
import com.erp.oa.constant.OaSignOperatorType;
import com.erp.oa.constant.OaSignPackageStatus;
import com.erp.oa.constant.OaSignSigningSequence;
import com.erp.oa.constant.OaSignTaskStatus;
import com.erp.oa.domain.OaSignPackage;
import com.erp.oa.domain.OaSignPlanVersion;
import com.erp.oa.domain.OaSignTask;
import com.erp.oa.domain.OaSignTaskEvent;
import com.erp.oa.domain.OaSignOnboardImportBatch;
import com.erp.oa.domain.OaSignOnboardImportRow;
import com.erp.oa.domain.vo.OaSignDraftDecision;
import com.erp.oa.mapper.OaSignPackageMapper;
import com.erp.oa.mapper.OaSignPlanVersionMapper;
import com.erp.oa.mapper.OaHrRenewalGuardMapper;
import com.erp.oa.mapper.OaSignTaskMapper;
import com.erp.oa.mapper.OaSignTaskEventMapper;
import com.erp.oa.service.IOaSignPackageService;
import com.erp.oa.service.IOaSignTaskService;
import com.erp.oa.service.OaSignTaskStateMachine;
import com.erp.oa.service.rule.OaSignScenarioRule;
import com.erp.oa.service.rule.RegularizeSignScenarioRule;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

@DisplayName("人事签约事件编排器")
class OaSignTaskOrchestratorTest
{
    private IOaSignTaskService taskService;
    private OaSignTaskMapper taskMapper;
    private OaSignTaskEventService eventService;
    private OaSignPlanVersionMapper versionMapper;
    private OaHrRenewalGuardMapper renewalGuardMapper;
    private OaSignPackageMapper packageMapper;
    private IOaSignPackageService packageService;
    private OaSignNotificationOutboxService notificationOutboxService;
    private PlatformTransactionManager transactionManager;
    private TransactionStatus transactionStatus;
    private List<OaSignTaskStatus> transitions;
    private OaSignTask task;

    @BeforeEach
    void setUp()
    {
        taskService = mock(IOaSignTaskService.class);
        taskMapper = mock(OaSignTaskMapper.class);
        eventService = mock(OaSignTaskEventService.class);
        versionMapper = mock(OaSignPlanVersionMapper.class);
        renewalGuardMapper = mock(OaHrRenewalGuardMapper.class);
        packageMapper = mock(OaSignPackageMapper.class);
        packageService = mock(IOaSignPackageService.class);
        notificationOutboxService = mock(OaSignNotificationOutboxService.class);
        transactionManager = mock(PlatformTransactionManager.class);
        transactionStatus = mock(TransactionStatus.class);
        when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
        transitions = new ArrayList<>();
        task = new OaSignTask();
        task.setTaskId(9L);
        task.setScenario("ONBOARD");
        task.setEmployeeId(201L);
        task.setShopDeptId(1171L);
        task.setLegalEntityId(301L);
        task.setAssignedHrUserId(101L);
        task.setVersion(0L);
        task.setStatus(OaSignTaskStatus.NEW.name());
        when(taskService.createTask(any())).thenAnswer(invocation -> {
            OaSignTask candidate = invocation.getArgument(0);
            candidate.setTaskId(task.getTaskId());
            candidate.setAssignedHrUserId(task.getAssignedHrUserId());
            candidate.setStatus(task.getStatus());
            candidate.setVersion(task.getVersion());
            task = candidate;
            return candidate;
        });
        when(taskMapper.selectOaSignTaskById(9L)).thenAnswer(invocation -> task);
        when(taskMapper.lockOaSignTaskById(any())).thenAnswer(invocation -> task);
        when(packageMapper.selectOaSignPackageById(90L)).thenAnswer(invocation -> taskPackage());
        when(taskMapper.updateRiskLevelForValidation(anyLong(), any(), anyLong(), anyLong()))
                .thenReturn(1);
        when(eventService.transition(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    OaSignTask current = invocation.getArgument(0);
                    OaSignTaskStatus target = invocation.getArgument(1);
                    transitions.add(target);
                    current.setStatus(target.name());
                    current.setVersion(current.getVersion() + 1);
                    return current;
        });
    }

    @Test
    @DisplayName("调岗任务创建时冻结前后快照、业务生效日和历史补录标记")
    void shouldFreezeTransferEventContextOnTaskCreation() throws Exception
    {
        OaSignScenarioRule transferRule = mock(OaSignScenarioRule.class);
        when(transferRule.supports("TRANSFER")).thenReturn(true);
        when(transferRule.dedupeKey(any())).thenReturn("TRANSFER:201:801:1");
        OaSignDraftDecision decision = new OaSignDraftDecision();
        decision.setAction(OaSignDraftDecision.Action.NEEDS_DATA);
        decision.setRiskLevel("HIGH");
        decision.setReasonCodes(List.of("PLAN_NOT_FOUND", "HISTORICAL_BACKFILL"));
        when(transferRule.decide(any())).thenReturn(decision);

        Long taskId = orchestrator(List.of(transferRule)).orchestrate(transferEvent());

        assertThat(taskId).isEqualTo(9L);
        assertThat(task.getBusinessEffectiveDate()).isEqualTo(LocalDate.of(2026, 12, 31));
        assertThat(task.getHistoricalSupplement()).isTrue();
        assertThat(task.getBeforeSnapshotJson()).contains("\"postName\":\"销售顾问\"");
        assertThat(task.getAfterSnapshotJson()).contains("\"postName\":\"店长\"");
        ObjectMapper mapper = JsonMapper.builder().findAndAddModules().build();
        assertThat(mapper.readValue(task.getAfterSnapshotJson(),
                HrEmployeeSigningSnapshot.class).getTransferEffectiveDate())
                .isEqualTo(LocalDate.of(2026, 12, 31));
        verify(notificationOutboxService).enqueueNeedsData(task);
    }

    @Test
    @DisplayName("离职任务创建时冻结最后工作日历史标记和离职快照")
    void shouldFreezeOffboardingEventContextOnTaskCreation() throws Exception
    {
        OaSignScenarioRule offboardRule = mock(OaSignScenarioRule.class);
        when(offboardRule.supports("OFFBOARD")).thenReturn(true);
        when(offboardRule.dedupeKey(any())).thenReturn("OFFBOARD:201:901:1");
        OaSignDraftDecision decision = new OaSignDraftDecision();
        decision.setAction(OaSignDraftDecision.Action.NEEDS_DATA);
        decision.setRiskLevel("HIGH");
        decision.setReasonCodes(List.of("PLAN_NOT_FOUND", "HISTORICAL_OFFBOARDING"));
        when(offboardRule.decide(any())).thenReturn(decision);

        Long taskId = orchestrator(List.of(offboardRule)).orchestrate(offboardEvent());

        assertThat(taskId).isEqualTo(9L);
        assertThat(task.getBusinessEffectiveDate()).isEqualTo(LocalDate.of(2026, 12, 31));
        assertThat(task.getHistoricalSupplement()).isTrue();
        assertThat(task.getBeforeSnapshotJson()).contains(
                "\"employeeStatus\":\"正式\"", "\"accountStatus\":\"0\"");
        assertThat(task.getAfterSnapshotJson()).contains(
                "\"employeeStatus\":\"离职\"", "\"accountStatus\":\"1\"",
                "\"leaveDate\":\"2026-12-31\"",
                "\"offboardingType\":\"VOLUNTARY_EXPECTED\"");
    }

    @Test
    @DisplayName("NO_ACTION决策元数据使用任务版本状态和HR归属原子保护")
    void shouldDefineGuardedNoActionDecisionMetadataWrite() throws Exception
    {
        assertThat(Arrays.stream(OaSignTaskMapper.class.getMethods())
                .map(method -> method.getName()))
                .contains("bindNoActionDecision");
        String mapper = new String(Resources.getResourceAsStream(
                "mapper/oa/OaSignTaskMapper.xml").readAllBytes(), StandardCharsets.UTF_8);
        assertThat(mapper).contains(
                "<update id=\"bindNoActionDecision\"",
                "plan_version_id = #{planVersionId}",
                "risk_level = #{riskLevel}",
                "version = version + 1",
                "status = 'VALIDATING'",
                "version = #{version}",
                "assigned_hr_user_id = #{assignedHrUserId}",
                "package_id is null");
    }

    @Test
    @DisplayName("待补资料风险更新使用状态版本和HR归属保护且不单独递增版本")
    void shouldDefineGuardedNeedsDataRiskWrite() throws Exception
    {
        assertThat(Arrays.stream(OaSignTaskMapper.class.getMethods())
                .map(method -> method.getName()))
                .contains("updateRiskLevelForValidation");
        String mapper = new String(Resources.getResourceAsStream(
                "mapper/oa/OaSignTaskMapper.xml").readAllBytes(), StandardCharsets.UTF_8);
        assertThat(mapper).contains(
                "<update id=\"updateRiskLevelForValidation\"",
                "risk_level = #{riskLevel}",
                "status = 'VALIDATING'",
                "version = #{version}",
                "assigned_hr_user_id = #{assignedHrUserId}",
                "package_id is null",
                "confirmed_by is null")
                .doesNotContain("<update id=\"updateRiskLevelForValidation\">\n"
                        + "        update oa_sign_task\n"
                        + "        set risk_level = #{riskLevel},\n"
                        + "            version = version + 1");
    }

    @Test
    @DisplayName("薪资变化待补资料在终态迁移前持久化REVIEW_REQUIRED风险")
    void shouldPersistReviewRiskBeforeSalaryNeedsData()
    {
        OaSignScenarioRule rule = matchingRule();
        when(rule.dedupeKey(any())).thenReturn("ONBOARD:201:record-9:1");
        OaSignDraftDecision decision = new OaSignDraftDecision();
        decision.setAction(OaSignDraftDecision.Action.NEEDS_DATA);
        decision.setRiskLevel("REVIEW_REQUIRED");
        decision.setReasonCodes(List.of("PLAN_NOT_FOUND", "SALARY_CHANGED"));
        when(rule.decide(any())).thenReturn(decision);

        Long taskId = orchestrator(List.of(rule)).orchestrate(event(1L));

        assertThat(taskId).isEqualTo(9L);
        assertThat(task.getStatus()).isEqualTo(OaSignTaskStatus.NEEDS_DATA.name());
        assertThat(task.getRiskLevel()).isEqualTo("REVIEW_REQUIRED");
        assertThat(task.getFailureCode()).isEqualTo("PLAN_NOT_FOUND");
        assertThat(task.getFailureDetail()).contains("SALARY_CHANGED");
        verify(taskMapper).updateRiskLevelForValidation(
                9L, "REVIEW_REQUIRED", 1L, 101L);
        InOrder order = inOrder(taskMapper, eventService);
        order.verify(eventService).transition(any(),
                org.mockito.ArgumentMatchers.eq(OaSignTaskStatus.VALIDATING),
                any(), any(), any(), any(), any(), any(), any());
        order.verify(taskMapper).updateRiskLevelForValidation(
                9L, "REVIEW_REQUIRED", 1L, 101L);
        order.verify(eventService).transition(any(),
                org.mockito.ArgumentMatchers.eq(OaSignTaskStatus.NEEDS_DATA),
                any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("待补资料终态迁移失败回滚同事务风险更新")
    void shouldRollbackNeedsDataRiskWhenTerminalTransitionFails()
    {
        List<TransactionStatus> transactionStatuses = new ArrayList<>();
        when(transactionManager.getTransaction(any())).thenAnswer(invocation -> {
            TransactionStatus status = mock(TransactionStatus.class);
            transactionStatuses.add(status);
            return status;
        });
        OaSignScenarioRule rule = matchingRule();
        when(rule.dedupeKey(any())).thenReturn("ONBOARD:201:record-9:1");
        OaSignDraftDecision decision = new OaSignDraftDecision();
        decision.setAction(OaSignDraftDecision.Action.NEEDS_DATA);
        decision.setRiskLevel("REVIEW_REQUIRED");
        decision.setReasonCodes(List.of("PLAN_NOT_FOUND", "SALARY_CHANGED"));
        when(rule.decide(any())).thenReturn(decision);
        doAnswer(invocation -> {
            OaSignTask current = invocation.getArgument(0);
            OaSignTaskStatus target = invocation.getArgument(1);
            if (target == OaSignTaskStatus.NEEDS_DATA)
            {
                throw new com.erp.common.core.exception.ServiceException(
                        "simulated needs data transition failure");
            }
            current.setStatus(target.name());
            current.setVersion(current.getVersion() + 1);
            return current;
        }).when(eventService).transition(any(), any(), any(), any(), any(), any(), any(), any(), any());

        assertThatThrownBy(() -> orchestrator(List.of(rule)).orchestrate(event(1L)))
                .isInstanceOf(com.erp.common.core.exception.ServiceException.class)
                .hasMessageContaining("simulated needs data transition failure");

        assertThat(transactionStatuses).hasSizeGreaterThanOrEqualTo(3);
        TransactionStatus riskAndTerminal = transactionStatuses.get(2);
        verify(taskMapper).updateRiskLevelForValidation(
                9L, "REVIEW_REQUIRED", 1L, 101L);
        verify(transactionManager).rollback(riskAndTerminal);
        verify(transactionManager, never()).commit(riskAndTerminal);
    }

    @Test
    @DisplayName("并发已完成待补资料但风险不一致时拒绝静默收敛")
    void shouldRejectConcurrentNeedsDataWithDifferentRisk()
    {
        OaSignScenarioRule rule = matchingRule();
        when(rule.dedupeKey(any())).thenReturn("ONBOARD:201:record-9:1");
        OaSignDraftDecision decision = new OaSignDraftDecision();
        decision.setAction(OaSignDraftDecision.Action.NEEDS_DATA);
        decision.setRiskLevel("REVIEW_REQUIRED");
        decision.setReasonCodes(List.of("PLAN_NOT_FOUND", "SALARY_CHANGED"));
        when(rule.decide(any())).thenReturn(decision);
        when(taskMapper.lockOaSignTaskById(9L)).thenAnswer(invocation -> {
            task.setStatus(OaSignTaskStatus.NEEDS_DATA.name());
            task.setRiskLevel("LOW");
            task.setVersion(2L);
            return task;
        });

        assertThatThrownBy(() -> orchestrator(List.of(rule)).orchestrate(event(1L)))
                .isInstanceOf(com.erp.common.core.exception.ServiceException.class)
                .hasMessageContaining("风险决定不一致");

        assertThat(task.getStatus()).isEqualTo(OaSignTaskStatus.NEEDS_DATA.name());
        assertThat(task.getRiskLevel()).isEqualTo("LOW");
        assertThat(task.getFailureCode()).isNull();
        verify(taskMapper, never()).updateRiskLevelForValidation(
                anyLong(), any(), anyLong(), anyLong());
        verify(eventService, never()).transition(any(),
                org.mockito.ArgumentMatchers.eq(OaSignTaskStatus.FAILED),
                any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("薪资变化NO_ACTION在终态前冻结方案版本和REVIEW_REQUIRED风险")
    void shouldPersistPlanVersionAndReviewRiskBeforeSalaryNoAction()
    {
        OaSignScenarioRule rule = matchingRule();
        when(rule.dedupeKey(any())).thenReturn("ONBOARD:201:record-9:1");
        OaSignDraftDecision decision = new OaSignDraftDecision();
        decision.setAction(OaSignDraftDecision.Action.NO_ACTION);
        decision.setPlanVersionId(55L);
        decision.setRiskLevel("REVIEW_REQUIRED");
        decision.setReasonCodes(List.of(
                "NO_EMPLOYEE_CONFIRMATION_DOCUMENT", "SALARY_CHANGED"));
        when(rule.decide(any())).thenReturn(decision);
        when(versionMapper.lockPlanVersionById(55L)).thenReturn(publishedVersion());
        when(taskMapper.bindNoActionDecision(
                9L, 55L, "REVIEW_REQUIRED", 1L, 101L)).thenReturn(1);

        Long taskId = orchestrator(List.of(rule)).orchestrate(event(1L));

        assertThat(taskId).isEqualTo(9L);
        assertThat(task.getStatus()).isEqualTo(OaSignTaskStatus.NO_ACTION.name());
        assertThat(task.getPlanVersionId()).isEqualTo(55L);
        assertThat(task.getRiskLevel()).isEqualTo("REVIEW_REQUIRED");
        assertThat(task.getPackageId()).isNull();
        verify(taskMapper).bindNoActionDecision(
                9L, 55L, "REVIEW_REQUIRED", 1L, 101L);
        verify(eventService).transition(any(),
                org.mockito.ArgumentMatchers.eq(OaSignTaskStatus.NO_ACTION),
                org.mockito.ArgumentMatchers.eq(OaSignOperatorType.SYSTEM),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.eq("NO_EMPLOYEE_CONFIRMATION_DOCUMENT"),
                org.mockito.ArgumentMatchers.contains("SALARY_CHANGED"),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull());
        verify(packageMapper, never()).insertOaSignPackage(any());
    }

    @Test
    @DisplayName("NO_ACTION终态迁移失败回滚同一事务内的方案和风险绑定")
    void shouldRollbackNoActionMetadataWhenTerminalTransitionFails()
    {
        List<TransactionStatus> transactionStatuses = new ArrayList<>();
        when(transactionManager.getTransaction(any())).thenAnswer(invocation -> {
            TransactionStatus status = mock(TransactionStatus.class);
            transactionStatuses.add(status);
            return status;
        });
        OaSignScenarioRule rule = matchingRule();
        when(rule.dedupeKey(any())).thenReturn("ONBOARD:201:record-9:1");
        OaSignDraftDecision decision = new OaSignDraftDecision();
        decision.setAction(OaSignDraftDecision.Action.NO_ACTION);
        decision.setPlanVersionId(55L);
        decision.setRiskLevel("REVIEW_REQUIRED");
        decision.setReasonCodes(List.of(
                "NO_EMPLOYEE_CONFIRMATION_DOCUMENT", "SALARY_CHANGED"));
        when(rule.decide(any())).thenReturn(decision);
        when(versionMapper.lockPlanVersionById(55L)).thenReturn(publishedVersion());
        when(taskMapper.bindNoActionDecision(
                9L, 55L, "REVIEW_REQUIRED", 1L, 101L)).thenReturn(1);
        doAnswer(invocation -> {
            OaSignTask current = invocation.getArgument(0);
            OaSignTaskStatus target = invocation.getArgument(1);
            if (target == OaSignTaskStatus.NO_ACTION)
            {
                throw new com.erp.common.core.exception.ServiceException(
                        "simulated no action transition failure");
            }
            current.setStatus(target.name());
            current.setVersion(current.getVersion() + 1);
            return current;
        }).when(eventService).transition(any(), any(), any(), any(), any(), any(), any(), any(), any());

        assertThatThrownBy(() -> orchestrator(List.of(rule)).orchestrate(event(1L)))
                .isInstanceOf(com.erp.common.core.exception.ServiceException.class)
                .hasMessageContaining("simulated no action transition failure");

        assertThat(transactionStatuses).hasSizeGreaterThanOrEqualTo(3);
        TransactionStatus metadataAndTerminal = transactionStatuses.get(2);
        verify(transactionManager).rollback(metadataAndTerminal);
        verify(transactionManager, never()).commit(metadataAndTerminal);
    }

    @Test
    @DisplayName("并发处理器已完成相同NO_ACTION决定时幂等返回")
    void shouldTreatConcurrentSameNoActionDecisionAsIdempotent()
    {
        OaSignScenarioRule rule = matchingRule();
        when(rule.dedupeKey(any())).thenReturn("ONBOARD:201:record-9:1");
        OaSignDraftDecision decision = new OaSignDraftDecision();
        decision.setAction(OaSignDraftDecision.Action.NO_ACTION);
        decision.setPlanVersionId(55L);
        decision.setRiskLevel("REVIEW_REQUIRED");
        decision.setReasonCodes(List.of(
                "NO_EMPLOYEE_CONFIRMATION_DOCUMENT", "SALARY_CHANGED"));
        when(rule.decide(any())).thenReturn(decision);
        when(taskMapper.lockOaSignTaskById(9L)).thenAnswer(invocation -> {
            task.setStatus(OaSignTaskStatus.NO_ACTION.name());
            task.setPlanVersionId(55L);
            task.setRiskLevel("REVIEW_REQUIRED");
            task.setVersion(2L);
            return task;
        });

        assertThat(orchestrator(List.of(rule)).orchestrate(event(1L))).isEqualTo(9L);

        assertThat(task.getStatus()).isEqualTo(OaSignTaskStatus.NO_ACTION.name());
        assertThat(task.getPlanVersionId()).isEqualTo(55L);
        assertThat(task.getRiskLevel()).isEqualTo("REVIEW_REQUIRED");
        verify(taskMapper, never()).bindNoActionDecision(anyLong(), anyLong(), any(), anyLong(), anyLong());
    }

    @Test
    @DisplayName("没有场景规则时保留任务并以PLAN_NOT_FOUND进入待补资料")
    void shouldKeepTaskForMissingScenarioRule()
    {
        OaSignTaskOrchestrator orchestrator = orchestrator(List.of());

        Long taskId = orchestrator.orchestrate(event(1L));

        assertThat(taskId).isEqualTo(9L);
        assertThat(task.getDedupeKey()).isEqualTo("EVENT:event-9:1");
        assertThat(task.getFailureCode()).isEqualTo("PLAN_NOT_FOUND");
        assertThat(transitions).containsExactly(
                OaSignTaskStatus.VALIDATING, OaSignTaskStatus.NEEDS_DATA);
        verify(packageMapper, never()).insertOaSignPackage(any());
    }

    @Test
    @DisplayName("唯一规则先计算去重键再创建版本化草稿并直接进入待发送")
    void shouldCreateVersionedDraftReadyToSendWithoutApproval()
    {
        OaSignScenarioRule rule = mock(OaSignScenarioRule.class);
        when(rule.supports("ONBOARD")).thenReturn(true);
        when(rule.dedupeKey(any())).thenReturn("ONBOARD:201:record-9:1");
        OaSignPackage draft = new OaSignPackage();
        OaSignDraftDecision decision = new OaSignDraftDecision();
        decision.setAction(OaSignDraftDecision.Action.CREATE_DRAFT);
        decision.setPlanVersionId(55L);
        decision.setRiskLevel("LOW");
        decision.setReasonCodes(List.of());
        decision.setDraftPackage(draft);
        when(rule.decide(any())).thenReturn(decision);
        OaSignPlanVersion version = publishedVersion();
        when(versionMapper.lockPlanVersionById(55L)).thenReturn(version);
        when(packageMapper.insertOaSignPackage(any())).thenAnswer(invocation -> {
            invocation.getArgument(0, OaSignPackage.class).setPackageId(90L);
            return 1;
        });
        when(taskMapper.bindVersionedPackage(9L, 90L, 55L, "LOW", 1L, 101L)).thenReturn(1);
        when(packageService.preparePackageDocumentsForSystem(90L, 9L, 101L)).thenReturn(draft);

        HrSignBusinessEvent onboardEvent = event(1L);
        onboardEvent.getAfterSnapshot().setContractTermCode("OPEN_ENDED");

        Long taskId = orchestrator(List.of(rule)).orchestrate(onboardEvent);

        assertThat(taskId).isEqualTo(9L);
        assertThat(task.getDedupeKey()).isEqualTo("ONBOARD:201:record-9:1");
        assertThat(task.getPlanVersionId()).isEqualTo(55L);
        assertThat(task.getPackageId()).isEqualTo(90L);
        assertThat(task.getRiskLevel()).isEqualTo("LOW");
        assertThat(draft.getContractTermCodeSnapshot()).isEqualTo("OPEN_ENDED");
        assertThat(transitions).containsExactly(
                OaSignTaskStatus.VALIDATING,
                OaSignTaskStatus.DRAFT_CREATED,
                OaSignTaskStatus.READY_TO_SEND);
        assertThat(transitions).doesNotContain(OaSignTaskStatus.WAITING_HR_CONFIRM);
        verify(notificationOutboxService).enqueueWaitingHr(any(), any());
        InOrder versionedWriteOrder = inOrder(taskMapper, versionMapper, packageMapper, packageService);
        versionedWriteOrder.verify(taskMapper).lockOaSignTaskById(9L);
        versionedWriteOrder.verify(versionMapper).lockPlanVersionById(55L);
        versionedWriteOrder.verify(packageMapper).insertOaSignPackage(draft);
        versionedWriteOrder.verify(taskMapper).bindVersionedPackage(9L, 90L, 55L, "LOW", 1L, 101L);
        versionedWriteOrder.verify(packageService).preparePackageDocumentsForSystem(90L, 9L, 101L);
    }

    @Test
    @DisplayName("先签名首次发送在当前事务内创建真实包并冻结期限")
    void shouldStageAndSendRealSignatureFirstPackageInCallerTransaction()
    {
        OaSignScenarioRule rule = mock(OaSignScenarioRule.class);
        when(rule.supports("ONBOARD")).thenReturn(true);
        when(rule.dedupeKey(any())).thenReturn("ONBOARD:201:record-9:1");
        OaSignPackage draft = new OaSignPackage();
        draft.setLegalEntityIdSnapshot(301L);
        draft.setLegalEntityNameSnapshot("候选公司");
        draft.setSealIdSnapshot(401L);
        draft.setSealNameSnapshot("候选印章");
        OaSignDraftDecision decision = new OaSignDraftDecision();
        decision.setAction(OaSignDraftDecision.Action.CREATE_DRAFT);
        decision.setPlanVersionId(55L);
        decision.setRiskLevel("LOW");
        decision.setDraftPackage(draft);
        when(rule.decide(any())).thenReturn(decision);
        OaSignPlanVersion version = publishedVersion();
        version.setSignDeadlineDays(7);
        when(versionMapper.lockPlanVersionById(55L)).thenReturn(version);
        when(packageMapper.insertOaSignPackage(draft)).thenAnswer(invocation -> {
            draft.setPackageId(90L);
            return 1;
        });
        when(taskMapper.bindVersionedPackage(9L, 90L, 55L, "LOW", 1L, 101L))
                .thenReturn(1);
        when(packageMapper.sendStagedShell(draft, 0L)).thenReturn(1);
        when(taskMapper.updateSentLifecycle(eq(9L), eq(OaSignTaskStatus.PENDING_SIGN.name()),
                eq(5L), eq(101L), any(), any(), eq("PLAN_VERSION"), eq(7)))
                .thenReturn(1);
        HrSignBusinessEvent stagedEvent = event(1L);
        stagedEvent.setOperatorUserId(101L);
        stagedEvent.setAttributes(new HashMap<>(Map.of(
                "signingSequence", OaSignSigningSequence.SIGNATURE_FIRST)));

        OaSignPackage result = orchestrator(List.of(rule))
                .stageSignatureFirstPackageInCurrentTransaction(stagedEvent);

        assertThat(result).isSameAs(draft);
        assertThat(result.getStatus()).isEqualTo(OaSignPackageStatus.PENDING_SIGN);
        assertThat(result.getSigningSequence()).isEqualTo(OaSignSigningSequence.SIGNATURE_FIRST);
        assertThat(result.getDocumentVersion()).isEqualTo("SP-90-V1");
        assertThat(result.getSignDeadline()).isAfter(result.getSentTime());
        assertThat(result.getDeadlineDaysSnapshot()).isEqualTo(7);
        assertThat(result.getLegalEntityIdSnapshot()).isNull();
        assertThat(result.getLegalEntityNameSnapshot()).isNull();
        assertThat(result.getSealIdSnapshot()).isNull();
        assertThat(result.getSealNameSnapshot()).isNull();
        assertThat(task.getLegalEntityId()).isNull();
        assertThat(transitions).containsExactly(OaSignTaskStatus.VALIDATING,
                OaSignTaskStatus.DRAFT_CREATED, OaSignTaskStatus.READY_TO_SEND,
                OaSignTaskStatus.SENDING, OaSignTaskStatus.PENDING_SIGN);
        verify(packageService, never()).preparePackageDocumentsForSystem(anyLong(), anyLong(), anyLong());
        verify(notificationOutboxService).enqueueSent(task, draft);
        verify(notificationOutboxService, never()).enqueueWaitingHr(any(), any());
    }

    @Test
    @DisplayName("同源首阶段包重放直接复用且不重复通知")
    void shouldReuseCommittedStagedPackageWithoutDuplicateSideEffects()
    {
        task.setSourceType("HR_ONBOARD");
        task.setSourceBusinessId("record-9");
        task.setSourceEventVersion("1");
        task.setPlanVersionId(55L);
        task.setPackageId(90L);
        task.setStatus(OaSignTaskStatus.PENDING_SIGN.name());
        OaSignPackage existing = taskPackage();
        existing.setStatus(OaSignPackageStatus.PENDING_SIGN);
        existing.setSigningSequence(OaSignSigningSequence.SIGNATURE_FIRST);
        when(taskMapper.selectCanonicalTaskBySourceEvent(any())).thenReturn(task);
        when(packageMapper.selectOaSignPackageById(90L)).thenReturn(existing);
        HrSignBusinessEvent replay = event(1L);
        replay.setOperatorUserId(101L);
        replay.setAttributes(new HashMap<>(Map.of(
                "signingSequence", OaSignSigningSequence.SIGNATURE_FIRST)));

        OaSignPackage result = orchestrator(List.of())
                .stageSignatureFirstPackageInCurrentTransaction(replay);

        assertThat(result).isSameAs(existing);
        verify(taskService, never()).createTask(any());
        verify(packageMapper, never()).insertOaSignPackage(any());
        verify(notificationOutboxService, never()).enqueueSent(any(), any());
        assertThat(transitions).isEmpty();
    }

    @Test
    @DisplayName("多个场景规则不能任选其一并以PLAN_CONFLICT进入待补资料")
    void shouldKeepTaskForConflictingScenarioRules()
    {
        OaSignScenarioRule first = matchingRule();
        OaSignScenarioRule second = matchingRule();

        Long taskId = orchestrator(List.of(first, second)).orchestrate(event(1L));

        assertThat(taskId).isEqualTo(9L);
        assertThat(task.getFailureCode()).isEqualTo("PLAN_CONFLICT");
        assertThat(transitions).containsExactly(
                OaSignTaskStatus.VALIDATING, OaSignTaskStatus.NEEDS_DATA);
        verify(first, never()).dedupeKey(any());
        verify(second, never()).dedupeKey(any());
        verify(first, never()).decide(any());
        verify(second, never()).decide(any());
    }

    @Test
    @DisplayName("同一eventId和eventVersion重投返回同一任务且不重复运行规则")
    void shouldReturnSameTaskForSameEventVersion()
    {
        OaSignScenarioRule rule = createDraftRule();
        when(versionMapper.lockPlanVersionById(55L)).thenReturn(publishedVersion());
        when(packageMapper.insertOaSignPackage(any())).thenAnswer(invocation -> {
            invocation.getArgument(0, OaSignPackage.class).setPackageId(90L);
            return 1;
        });
        when(taskMapper.bindVersionedPackage(9L, 90L, 55L, "LOW", 1L, 101L)).thenReturn(1);
        when(packageService.preparePackageDocumentsForSystem(90L, 9L, 101L))
                .thenAnswer(invocation -> taskPackage());
        OaSignTaskOrchestrator orchestrator = orchestrator(List.of(rule));
        Long first = orchestrator.orchestrate(event(1L));
        OaSignTask persisted = task;
        persisted.setSourceType("HR_ONBOARD");
        persisted.setSourceBusinessId("record-9");
        persisted.setSourceEventVersion("1");
        when(taskMapper.selectCanonicalTaskBySourceEvent(any())).thenReturn(persisted);
        doReturn(persisted).when(taskService).createTask(any());

        Long repeated = orchestrator.orchestrate(event(1L));

        assertThat(repeated).isEqualTo(first).isEqualTo(9L);
        assertThat(task.getStatus()).isEqualTo(OaSignTaskStatus.READY_TO_SEND.name());
        verify(rule, times(1)).supports("ONBOARD");
        verify(rule, times(1)).dedupeKey(any());
        verify(rule, times(1)).decide(any());
        verify(taskService, times(1)).createTask(any());
        verify(packageMapper, times(1)).insertOaSignPackage(any());
    }

    @Test
    @DisplayName("更高sourceEventVersion使用不同去重键并创建不同任务")
    void shouldCreateDifferentTaskForHigherEventVersion()
    {
        OaSignScenarioRule rule = mock(OaSignScenarioRule.class);
        when(rule.supports("ONBOARD")).thenReturn(true);
        when(rule.dedupeKey(any())).thenAnswer(invocation -> {
            HrSignBusinessEvent value = invocation.getArgument(0);
            return "ONBOARD:201:record-9:" + value.getSourceEventVersion();
        });
        OaSignDraftDecision needsData = new OaSignDraftDecision();
        needsData.setAction(OaSignDraftDecision.Action.NEEDS_DATA);
        needsData.setReasonCodes(List.of("PROFILE_INCOMPLETE"));
        when(rule.decide(any())).thenReturn(needsData);
        Map<Long, OaSignTask> tasks = new HashMap<>();
        AtomicLong ids = new AtomicLong(8L);
        doAnswer(invocation -> {
            OaSignTask candidate = invocation.getArgument(0);
            candidate.setTaskId(ids.incrementAndGet());
            candidate.setAssignedHrUserId(101L);
            candidate.setStatus(OaSignTaskStatus.NEW.name());
            candidate.setVersion(0L);
            tasks.put(candidate.getTaskId(), candidate);
            task = candidate;
            return candidate;
        }).when(taskService).createTask(any());
        when(taskMapper.selectOaSignTaskById(any())).thenAnswer(invocation ->
                tasks.get(invocation.getArgument(0, Long.class)));
        OaSignTaskOrchestrator orchestrator = orchestrator(List.of(rule));

        Long first = orchestrator.orchestrate(event(1L));
        Long second = orchestrator.orchestrate(event(2L));

        assertThat(first).isEqualTo(9L);
        assertThat(second).isEqualTo(10L);
        assertThat(tasks.get(first).getDedupeKey()).endsWith(":1");
        assertThat(tasks.get(second).getDedupeKey()).endsWith(":2");
    }

    @Test
    @DisplayName("规则业务校验失败持久化并返回任务ID")
    void shouldPersistBusinessValidationFailureAndReturnTaskId()
    {
        OaSignScenarioRule rule = matchingRule();
        when(rule.dedupeKey(any())).thenReturn("ONBOARD:201:record-9:1");
        when(rule.decide(any())).thenThrow(new com.erp.common.core.exception.ServiceException("手机号缺失"));

        Long taskId = orchestrator(List.of(rule)).orchestrate(event(1L));

        assertThat(taskId).isEqualTo(9L);
        assertThat(task.getStatus()).isEqualTo(OaSignTaskStatus.NEEDS_DATA.name());
        assertThat(task.getFailureCode()).isEqualTo("VALIDATION_FAILED");
        assertThat(task.getFailureDetail()).isEqualTo("手机号缺失");
    }

    @Test
    @DisplayName("草稿文件业务校验失败保留待补资料任务并返回taskId")
    void shouldPersistDraftPreparationValidationFailureAndReturnTaskId()
    {
        OaSignScenarioRule rule = createDraftRule();
        when(versionMapper.lockPlanVersionById(55L)).thenReturn(publishedVersion());
        when(packageMapper.insertOaSignPackage(any())).thenAnswer(invocation -> {
            invocation.getArgument(0, OaSignPackage.class).setPackageId(90L);
            return 1;
        });
        when(taskMapper.bindVersionedPackage(9L, 90L, 55L, "LOW", 1L, 101L)).thenReturn(1);
        when(packageService.preparePackageDocumentsForSystem(90L, 9L, 101L))
                .thenThrow(new IOaSignPackageService.DraftValidationException(
                        "DRAFT_PREPARATION_FAILED", "模板文件不存在"));

        Long taskId = orchestrator(List.of(rule)).orchestrate(event(1L));

        assertThat(taskId).isEqualTo(9L);
        assertThat(task.getStatus()).isEqualTo(OaSignTaskStatus.NEEDS_DATA.name());
        assertThat(task.getFailureCode()).isEqualTo("DRAFT_PREPARATION_FAILED");
        assertThat(task.getFailureDetail()).contains("模板文件不存在");
    }

    @Test
    @DisplayName("薪资变化草稿文件校验失败仍原子持久化复核风险和原因")
    void shouldPersistSalaryReviewContextWhenDraftPreparationFails()
    {
        OaSignScenarioRule rule = salaryReviewDraftRule();
        OaSignPlanVersion version = publishedVersion();
        version.setScenario("REGULARIZE");
        task.setScenario("REGULARIZE");
        when(versionMapper.lockPlanVersionById(55L)).thenReturn(version);
        when(packageMapper.insertOaSignPackage(any())).thenAnswer(invocation -> {
            invocation.getArgument(0, OaSignPackage.class).setPackageId(90L);
            return 1;
        });
        when(taskMapper.bindVersionedPackage(
                9L, 90L, 55L, "REVIEW_REQUIRED", 1L, 101L)).thenReturn(1);
        when(packageService.preparePackageDocumentsForSystem(90L, 9L, 101L))
                .thenAnswer(invocation -> {
                    task.setPackageId(null);
                    task.setPlanVersionId(null);
                    task.setRiskLevel("NORMAL");
                    throw new IOaSignPackageService.DraftValidationException(
                            "TEMPLATE_FILE_INVALID", "模板文件hash不一致");
                });

        Long taskId = orchestrator(List.of(rule)).orchestrate(regularizeEvent());

        assertThat(taskId).isEqualTo(9L);
        assertThat(task.getStatus()).isEqualTo(OaSignTaskStatus.NEEDS_DATA.name());
        assertThat(task.getRiskLevel()).isEqualTo("REVIEW_REQUIRED");
        assertThat(task.getFailureCode()).isEqualTo("TEMPLATE_FILE_INVALID");
        assertThat(task.getFailureDetail()).contains("模板文件hash不一致", "SALARY_CHANGED");
        verify(taskMapper).updateRiskLevelForValidation(
                9L, "REVIEW_REQUIRED", 1L, 101L);
    }

    @Test
    @DisplayName("草稿持久化ServiceException按基础设施失败保留FAILED并抛出")
    void shouldRethrowDraftPersistenceServiceExceptionForOutboxRetry()
    {
        OaSignScenarioRule rule = createDraftRule();
        when(versionMapper.lockPlanVersionById(55L)).thenReturn(publishedVersion());
        when(packageMapper.insertOaSignPackage(any())).thenAnswer(invocation -> {
            invocation.getArgument(0, OaSignPackage.class).setPackageId(90L);
            return 1;
        });
        when(taskMapper.bindVersionedPackage(9L, 90L, 55L, "LOW", 1L, 101L)).thenReturn(1);
        when(packageService.preparePackageDocumentsForSystem(90L, 9L, 101L))
                .thenThrow(new com.erp.common.core.exception.ServiceException("签约文件准备更新失败"));

        assertThatThrownBy(() -> orchestrator(List.of(rule)).orchestrate(event(1L)))
                .isInstanceOf(com.erp.common.core.exception.ServiceException.class)
                .hasMessageContaining("准备更新失败");
        assertThat(task.getStatus()).isEqualTo(OaSignTaskStatus.FAILED.name());
        assertThat(task.getFailureCode()).isEqualTo("INFRASTRUCTURE_FAILURE");
    }

    @Test
    @DisplayName("并发后到事务锁定任务后复用已绑定草稿而不重复插包")
    void shouldReusePackageFoundAfterTaskRowLock()
    {
        OaSignScenarioRule rule = createDraftRule();
        task.setStatus(OaSignTaskStatus.VALIDATING.name());
        task.setVersion(1L);
        task.setPlanVersionId(55L);
        task.setPackageId(90L);
        task.setSourceType("HR_ONBOARD");
        task.setSourceBusinessId("record-9");
        task.setSourceEventVersion("1");
        doReturn(task).when(taskService).createTask(any());
        OaSignPackage existing = taskPackage();
        when(packageMapper.selectOaSignPackageById(90L)).thenReturn(existing);
        when(versionMapper.lockPlanVersionById(55L)).thenReturn(publishedVersion());
        when(packageService.preparePackageDocumentsForSystem(90L, 9L, 101L)).thenReturn(existing);

        Long taskId = orchestrator(List.of(rule)).orchestrate(event(1L));

        assertThat(taskId).isEqualTo(9L);
        assertThat(task.getStatus()).isEqualTo(OaSignTaskStatus.READY_TO_SEND.name());
        verify(taskMapper).lockOaSignTaskById(9L);
        verify(packageMapper, never()).insertOaSignPackage(any());
        verify(taskMapper, never()).bindVersionedPackage(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("重投已绑定草稿沿历史版本恢复且不受新规则或停配状态影响")
    void shouldResumeBoundPackageFromHistoricalVersion()
    {
        OaSignScenarioRule rule = matchingRule();
        when(rule.dedupeKey(any())).thenReturn("ONBOARD:201:record-9:1");
        OaSignDraftDecision recalculated = new OaSignDraftDecision();
        recalculated.setAction(OaSignDraftDecision.Action.CREATE_DRAFT);
        recalculated.setPlanVersionId(77L);
        recalculated.setRiskLevel("HIGH");
        recalculated.setReasonCodes(List.of());
        recalculated.setDraftPackage(new OaSignPackage());
        when(rule.decide(any())).thenReturn(recalculated);
        task.setStatus(OaSignTaskStatus.VALIDATING.name());
        task.setVersion(1L);
        task.setPlanVersionId(55L);
        task.setPackageId(90L);
        task.setRiskLevel("LOW");
        task.setSourceType("HR_ONBOARD");
        task.setSourceBusinessId("record-9");
        task.setSourceEventVersion("1");
        doReturn(task).when(taskService).createTask(any());
        OaSignPackage existing = taskPackage();
        when(packageMapper.selectOaSignPackageById(90L)).thenReturn(existing);
        OaSignPlanVersion historical = publishedVersion();
        historical.setMatchingStatus("DISABLED");
        when(versionMapper.lockPlanVersionById(55L)).thenReturn(historical);
        when(packageService.preparePackageDocumentsForSystem(90L, 9L, 101L)).thenReturn(existing);

        Long taskId = orchestrator(List.of(rule)).orchestrate(event(1L));

        assertThat(taskId).isEqualTo(9L);
        assertThat(task.getStatus()).isEqualTo(OaSignTaskStatus.READY_TO_SEND.name());
        assertThat(task.getPlanVersionId()).isEqualTo(55L);
        assertThat(task.getRiskLevel()).isEqualTo("LOW");
        verify(versionMapper).lockPlanVersionById(55L);
        verify(versionMapper, never()).lockPlanVersionById(77L);
        verify(packageMapper, never()).insertOaSignPackage(any());
        verify(taskMapper, never()).bindVersionedPackage(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("重投已绑定失败任务时先按来源事件恢复且不再依赖当前规则")
    void shouldResumeBoundFailedTaskByStableSourceIdentityBeforeRuleSelection()
    {
        task.setStatus(OaSignTaskStatus.FAILED.name());
        task.setVersion(2L);
        task.setSourceType("HR_ONBOARD");
        task.setSourceBusinessId("record-9");
        task.setSourceEventVersion("1");
        task.setPlanVersionId(55L);
        task.setPackageId(90L);
        when(taskMapper.selectCanonicalTaskBySourceEvent(any())).thenAnswer(invocation -> {
            OaSignTask query = invocation.getArgument(0);
            assertThat(query.getScenario()).isEqualTo("ONBOARD");
            assertThat(query.getEmployeeId()).isEqualTo(201L);
            assertThat(query.getSourceType()).isEqualTo("HR_ONBOARD");
            assertThat(query.getSourceBusinessId()).isEqualTo("record-9");
            assertThat(query.getSourceEventVersion()).isEqualTo("1");
            return task;
        });
        OaSignPackage existing = taskPackage();
        when(packageMapper.selectOaSignPackageById(90L)).thenReturn(existing);
        OaSignPlanVersion historical = publishedVersion();
        historical.setMatchingStatus("DISABLED");
        when(versionMapper.lockPlanVersionById(55L)).thenReturn(historical);
        when(packageService.preparePackageDocumentsForSystem(90L, 9L, 101L)).thenReturn(existing);

        Long taskId = orchestrator(List.of()).orchestrate(event(1L));

        assertThat(taskId).isEqualTo(9L);
        assertThat(task.getStatus()).isEqualTo(OaSignTaskStatus.READY_TO_SEND.name());
        assertThat(task.getPackageId()).isEqualTo(90L);
        verify(taskService, never()).createTask(any());
        verify(versionMapper).lockPlanVersionById(55L);
    }

    @Test
    @DisplayName("已绑定草稿恢复业务校验失败保留待补资料并返回同一taskId")
    void shouldPersistBoundDraftValidationFailureAndReturnSameTaskId()
    {
        task.setStatus(OaSignTaskStatus.FAILED.name());
        task.setVersion(2L);
        task.setSourceType("HR_ONBOARD");
        task.setSourceBusinessId("record-9");
        task.setSourceEventVersion("1");
        task.setPlanVersionId(55L);
        task.setPackageId(90L);
        when(taskMapper.selectCanonicalTaskBySourceEvent(any())).thenReturn(task);
        OaSignPackage existing = taskPackage();
        when(packageMapper.selectOaSignPackageById(90L)).thenReturn(existing);
        when(versionMapper.lockPlanVersionById(55L)).thenReturn(publishedVersion());
        when(packageService.preparePackageDocumentsForSystem(90L, 9L, 101L))
                .thenThrow(new IOaSignPackageService.DraftValidationException(
                        "DRAFT_PREPARATION_FAILED", "模板文件不存在"));

        Long taskId = orchestrator(List.of()).orchestrate(event(1L));

        assertThat(taskId).isEqualTo(9L);
        assertThat(task.getStatus()).isEqualTo(OaSignTaskStatus.NEEDS_DATA.name());
        assertThat(task.getFailureCode()).isEqualTo("DRAFT_PREPARATION_FAILED");
        assertThat(task.getFailureDetail()).contains("模板文件不存在");
        verify(taskService, never()).createTask(any());
    }

    @Test
    @DisplayName("已绑定转正薪资复核任务重投文件失败不回退风险和原因")
    void shouldKeepSalaryReviewContextOnBoundRegularizationRedelivery()
    {
        task.setScenario("REGULARIZE");
        task.setStatus(OaSignTaskStatus.FAILED.name());
        task.setVersion(2L);
        task.setRiskLevel("REVIEW_REQUIRED");
        task.setSourceType("HR_LIFECYCLE_ACTION");
        task.setSourceBusinessId("701");
        task.setSourceEventVersion("1");
        task.setPlanVersionId(55L);
        task.setPackageId(90L);
        when(taskMapper.selectCanonicalTaskBySourceEvent(any())).thenReturn(task);
        when(packageMapper.selectOaSignPackageById(90L)).thenReturn(taskPackage());
        OaSignPlanVersion version = publishedVersion();
        version.setScenario("REGULARIZE");
        when(versionMapper.lockPlanVersionById(55L)).thenReturn(version);
        when(packageService.preparePackageDocumentsForSystem(90L, 9L, 101L))
                .thenThrow(new IOaSignPackageService.DraftValidationException(
                        "TEMPLATE_FILE_INVALID", "模板文件不存在"));

        OaSignTaskOrchestrator orchestrator = orchestrator(List.of());
        assertThat(orchestrator.orchestrate(regularizeEvent())).isEqualTo(9L);
        assertThat(orchestrator.orchestrate(regularizeEvent())).isEqualTo(9L);

        assertThat(task.getStatus()).isEqualTo(OaSignTaskStatus.NEEDS_DATA.name());
        assertThat(task.getRiskLevel()).isEqualTo("REVIEW_REQUIRED");
        assertThat(task.getFailureCode()).isEqualTo("TEMPLATE_FILE_INVALID");
        assertThat(task.getFailureDetail()).contains("模板文件不存在", "SALARY_CHANGED");
        verify(taskMapper, never()).updateRiskLevelForValidation(
                anyLong(), any(), anyLong(), anyLong());
        verify(packageService, times(2))
                .preparePackageDocumentsForSystem(90L, 9L, 101L);
    }

    @Test
    @DisplayName("已绑定校验中任务忽略已变化去重键和NO_ACTION决策")
    void shouldNotRecalculateChangedRuleForBoundValidatingTask()
    {
        OaSignScenarioRule rule = matchingRule();
        when(rule.dedupeKey(any())).thenReturn("CHANGED:DEDUPE:KEY");
        OaSignDraftDecision noAction = new OaSignDraftDecision();
        noAction.setAction(OaSignDraftDecision.Action.NO_ACTION);
        when(rule.decide(any())).thenReturn(noAction);
        task.setStatus(OaSignTaskStatus.VALIDATING.name());
        task.setSourceType("HR_ONBOARD");
        task.setSourceBusinessId("record-9");
        task.setSourceEventVersion("1");
        task.setPlanVersionId(55L);
        task.setPackageId(90L);
        when(taskMapper.selectCanonicalTaskBySourceEvent(any())).thenReturn(task);
        OaSignPackage existing = taskPackage();
        when(packageMapper.selectOaSignPackageById(90L)).thenReturn(existing);
        when(versionMapper.lockPlanVersionById(55L)).thenReturn(publishedVersion());
        when(packageService.preparePackageDocumentsForSystem(90L, 9L, 101L)).thenReturn(existing);

        Long taskId = orchestrator(List.of(rule)).orchestrate(event(1L));

        assertThat(taskId).isEqualTo(9L);
        assertThat(task.getStatus()).isEqualTo(OaSignTaskStatus.READY_TO_SEND.name());
        verify(rule, never()).supports(any());
        verify(rule, never()).dedupeKey(any());
        verify(rule, never()).decide(any());
        verify(taskService, never()).createTask(any());
    }

    @Test
    @DisplayName("已绑定失败任务忽略当前规则decide业务异常并恢复历史草稿")
    void shouldNotInvokeThrowingRuleForBoundFailedTask()
    {
        OaSignScenarioRule rule = matchingRule();
        when(rule.dedupeKey(any())).thenReturn("ONBOARD:201:record-9:changed");
        when(rule.decide(any())).thenThrow(new com.erp.common.core.exception.ServiceException(
                "rule validation failed"));
        task.setStatus(OaSignTaskStatus.FAILED.name());
        task.setSourceType("HR_ONBOARD");
        task.setSourceBusinessId("record-9");
        task.setSourceEventVersion("1");
        task.setPlanVersionId(55L);
        task.setPackageId(90L);
        when(taskMapper.selectCanonicalTaskBySourceEvent(any())).thenReturn(task);
        OaSignPackage existing = taskPackage();
        when(packageMapper.selectOaSignPackageById(90L)).thenReturn(existing);
        when(versionMapper.lockPlanVersionById(55L)).thenReturn(publishedVersion());
        when(packageService.preparePackageDocumentsForSystem(90L, 9L, 101L)).thenReturn(existing);

        Long taskId = orchestrator(List.of(rule)).orchestrate(event(1L));

        assertThat(taskId).isEqualTo(9L);
        assertThat(task.getStatus()).isEqualTo(OaSignTaskStatus.READY_TO_SEND.name());
        verify(rule, never()).supports(any());
        verify(rule, never()).dedupeKey(any());
        verify(rule, never()).decide(any());
    }

    @Test
    @DisplayName("未绑定待补资料任务在规则恢复后沿原task创建草稿")
    void shouldContinueUnboundNeedsDataTaskWithoutCreatingAnotherTask()
    {
        OaSignScenarioRule rule = createDraftRule();
        when(rule.dedupeKey(any())).thenReturn("ONBOARD:957:1876:3");
        task.setTaskId(198L);
        task.setEmployeeId(957L);
        task.setStatus(OaSignTaskStatus.NEEDS_DATA.name());
        task.setVersion(2L);
        task.setSourceType(OaOnboardSignEventFactory.SOURCE_TYPE);
        task.setSourceBusinessId("1876");
        task.setSourceEventVersion("3");
        when(taskMapper.selectCanonicalTaskBySourceEvent(any())).thenReturn(task);
        when(taskMapper.selectOaSignTaskById(198L)).thenAnswer(invocation -> task);
        when(versionMapper.lockPlanVersionById(55L)).thenReturn(publishedVersion());
        when(packageMapper.insertOaSignPackage(any())).thenAnswer(invocation -> {
            invocation.getArgument(0, OaSignPackage.class).setPackageId(90L);
            return 1;
        });
        when(taskMapper.bindVersionedPackage(any(), any(), any(), any(), any(), any()))
                .thenReturn(1);
        when(packageService.preparePackageDocumentsForSystem(90L, 198L, 101L))
                .thenReturn(new OaSignPackage());

        HrSignBusinessEvent excelEvent = event(3L);
        excelEvent.setEventId("OA-ONBOARD-EXCEL:1876:3");
        excelEvent.setEmployeeId(957L);
        excelEvent.setSourceType(OaOnboardSignEventFactory.SOURCE_TYPE);
        excelEvent.setSourceBusinessId("1876");
        excelEvent.setOperatorUserId(101L);
        excelEvent.getAfterSnapshot().setEmployeeId(957L);
        excelEvent.setAttributes(Map.of("generationRequestId", "task198-recovery"));

        Long taskId = orchestrator(List.of(rule)).orchestrate(excelEvent);

        assertThat(taskId).isEqualTo(198L);
        assertThat(task.getStatus()).isEqualTo(OaSignTaskStatus.READY_TO_SEND.name());
        assertThat(task.getPlanVersionId()).isEqualTo(55L);
        assertThat(task.getPackageId()).isEqualTo(90L);
        verify(taskService, never()).createTask(any());
        verify(rule).supports("ONBOARD");
        verify(rule).dedupeKey(any());
        verify(rule).decide(any());
        verify(packageMapper, times(1)).insertOaSignPackage(any());
    }

    @Test
    @DisplayName("不同生成请求使用不同审计请求号并经真实事件历史恢复待补资料任务")
    void shouldResumeNeedsDataWithDifferentGenerationRequestUsingRealEventHistory()
    {
        OaSignScenarioRule rule = matchingRule();
        when(rule.dedupeKey(any())).thenReturn("ONBOARD:201:1876:3");
        OaSignDraftDecision needsData = new OaSignDraftDecision();
        needsData.setAction(OaSignDraftDecision.Action.NEEDS_DATA);
        needsData.setRiskLevel("LOW");
        needsData.setReasonCodes(List.of("TEMPLATE_FILE_MISSING"));
        OaSignDraftDecision recovered = new OaSignDraftDecision();
        recovered.setAction(OaSignDraftDecision.Action.CREATE_DRAFT);
        recovered.setPlanVersionId(55L);
        recovered.setRiskLevel("LOW");
        recovered.setReasonCodes(List.of());
        recovered.setDraftPackage(new OaSignPackage());
        when(rule.decide(any())).thenReturn(needsData, recovered);

        task.setSourceType(OaOnboardSignEventFactory.SOURCE_TYPE);
        task.setSourceBusinessId("1876");
        task.setSourceEventVersion("3");
        when(taskMapper.selectCanonicalTaskBySourceEvent(any())).thenReturn(task);
        when(taskMapper.updateStatusWithVersion(anyLong(), any(), any(), anyLong(),
                any(), any(), any(), anyLong())).thenReturn(1);
        when(versionMapper.lockPlanVersionById(55L)).thenReturn(publishedVersion());
        when(packageMapper.insertOaSignPackage(any())).thenAnswer(invocation -> {
            invocation.getArgument(0, OaSignPackage.class).setPackageId(90L);
            return 1;
        });
        when(taskMapper.bindVersionedPackage(any(), any(), any(), any(), any(), any()))
                .thenReturn(1);
        when(packageService.preparePackageDocumentsForSystem(90L, 9L, 101L))
                .thenReturn(taskPackage());

        OaSignTaskEventMapper eventMapper = mock(OaSignTaskEventMapper.class);
        List<OaSignTaskEvent> history = new ArrayList<>();
        Map<String, OaSignTaskEvent> requests = new HashMap<>();
        when(eventMapper.selectEventByRequestId(any())).thenAnswer(invocation ->
                requests.get(invocation.getArgument(0, String.class)));
        when(eventMapper.selectLastTaskEvent(9L)).thenAnswer(invocation ->
                history.isEmpty() ? null : history.get(history.size() - 1));
        when(eventMapper.insertOaSignTaskEvent(any())).thenAnswer(invocation -> {
            OaSignTaskEvent persisted = invocation.getArgument(0, OaSignTaskEvent.class);
            history.add(persisted);
            if (persisted.getRequestId() != null)
                requests.put(persisted.getRequestId(), persisted);
            return 1;
        });
        eventService = new OaSignTaskEventService(taskMapper, eventMapper,
                renewalGuardMapper, new OaSignTaskStateMachine());
        OaSignTaskOrchestrator realHistoryOrchestrator = orchestrator(List.of(rule));

        HrSignBusinessEvent first = excelEvent("generation-request-A");
        HrSignBusinessEvent second = excelEvent("generation-request-B");
        assertThat(realHistoryOrchestrator.orchestrate(first)).isEqualTo(9L);
        assertThat(task.getStatus()).isEqualTo(OaSignTaskStatus.NEEDS_DATA.name());
        assertThat(realHistoryOrchestrator.orchestrate(second)).isEqualTo(9L);

        assertThat(task.getStatus()).isEqualTo(OaSignTaskStatus.READY_TO_SEND.name());
        assertThat(requests.keySet()).hasSize(2)
                .allSatisfy(requestId -> assertThat(requestId)
                        .startsWith("EXCEL:").hasSizeLessThanOrEqualTo(64));
        verify(rule, times(2)).decide(any());
        verify(taskService, never()).createTask(any());
    }

    @Test
    @DisplayName("Excel既有任务前置读取后被改派时锁内拒绝原经办人继续生成")
    void shouldRejectExcelTaskReassignedAfterInitialLookup()
    {
        OaSignScenarioRule rule = createDraftRule();
        task.setTaskId(198L);
        task.setEmployeeId(957L);
        task.setStatus(OaSignTaskStatus.NEEDS_DATA.name());
        task.setVersion(2L);
        task.setSourceType(OaOnboardSignEventFactory.SOURCE_TYPE);
        task.setSourceBusinessId("1876");
        task.setSourceEventVersion("3");
        task.setAssignedHrUserId(101L);
        when(taskMapper.selectCanonicalTaskBySourceEvent(any())).thenReturn(task);
        when(taskMapper.selectExactTasksBySourceEvent(any())).thenReturn(List.of(task));
        when(taskMapper.selectOaSignTaskById(198L)).thenReturn(task);
        OaSignTask reassigned = new OaSignTask();
        reassigned.setTaskId(198L);
        reassigned.setScenario("ONBOARD");
        reassigned.setEmployeeId(957L);
        reassigned.setShopDeptId(1171L);
        reassigned.setStatus(OaSignTaskStatus.NEEDS_DATA.name());
        reassigned.setVersion(3L);
        reassigned.setSourceType(OaOnboardSignEventFactory.SOURCE_TYPE);
        reassigned.setSourceBusinessId("1876");
        reassigned.setSourceEventVersion("3");
        reassigned.setAssignedHrUserId(202L);
        when(taskMapper.lockOaSignTaskById(198L)).thenReturn(reassigned);
        HrSignBusinessEvent excelEvent = event(3L);
        excelEvent.setEventId("OA-ONBOARD-EXCEL:1876:3");
        excelEvent.setEmployeeId(957L);
        excelEvent.setSourceType(OaOnboardSignEventFactory.SOURCE_TYPE);
        excelEvent.setSourceBusinessId("1876");
        excelEvent.setOperatorUserId(101L);
        excelEvent.getAfterSnapshot().setEmployeeId(957L);
        excelEvent.setAttributes(Map.of("generationRequestId", "task198-reassigned"));

        assertThatThrownBy(() -> orchestrator(List.of(rule)).orchestrate(excelEvent))
                .isInstanceOf(com.erp.common.core.exception.ServiceException.class)
                .hasMessageContaining("已改派");

        verify(taskMapper, times(3)).lockOaSignTaskById(198L);
        verify(eventService, never()).transition(any(), any(), any(), any(), any(), any(),
                any(), any(), any());
        verify(packageMapper, never()).insertOaSignPackage(any());
        verify(taskMapper, never()).bindVersionedPackage(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("生成收尾锁看到改派时不执行包或导入行写入")
    void shouldNotRunExcelGenerationWriteAfterTaskReassignment()
    {
        OaSignOnboardImportBatch batch = new OaSignOnboardImportBatch();
        batch.setBatchId(134L);
        batch.setShopDeptId(1171L);
        OaSignOnboardImportRow row = new OaSignOnboardImportRow();
        row.setRowId(1876L);
        row.setBatchId(134L);
        row.setEmployeeId(957L);
        row.setSourceEventVersion(3L);
        task.setTaskId(198L);
        task.setEmployeeId(957L);
        task.setSourceType(OaOnboardSignEventFactory.SOURCE_TYPE);
        task.setSourceBusinessId("1876");
        task.setSourceEventVersion("3");
        task.setAssignedHrUserId(101L);
        when(taskMapper.selectCanonicalTaskBySourceEvent(any())).thenReturn(task);
        when(taskMapper.selectExactTasksBySourceEvent(any())).thenReturn(List.of(task));
        OaSignTask reassigned = new OaSignTask();
        reassigned.setTaskId(198L);
        reassigned.setScenario("ONBOARD");
        reassigned.setEmployeeId(957L);
        reassigned.setShopDeptId(1171L);
        reassigned.setSourceType(OaOnboardSignEventFactory.SOURCE_TYPE);
        reassigned.setSourceBusinessId("1876");
        reassigned.setSourceEventVersion("3");
        reassigned.setAssignedHrUserId(202L);
        when(taskMapper.lockOaSignTaskById(198L)).thenReturn(reassigned);
        AtomicBoolean wrote = new AtomicBoolean();

        assertThatThrownBy(() -> orchestrator(List.of()).withLockedExcelImportTask(
                row, batch, 198L, 101L, locked -> {
                    wrote.set(true);
                    return null;
                }))
                .isInstanceOf(com.erp.common.core.exception.ServiceException.class)
                .hasMessageContaining("已改派");

        assertThat(wrote).isFalse();
    }

    @Test
    @DisplayName("生成收尾锁拒绝同一Excel来源事件的重复任务")
    void shouldRejectDuplicateExcelSourceTasksBeforeGenerationWrite()
    {
        OaSignOnboardImportBatch batch = new OaSignOnboardImportBatch();
        batch.setBatchId(134L);
        batch.setShopDeptId(1171L);
        OaSignOnboardImportRow row = new OaSignOnboardImportRow();
        row.setRowId(1876L);
        row.setBatchId(134L);
        row.setEmployeeId(957L);
        row.setSourceEventVersion(3L);
        task.setTaskId(198L);
        task.setEmployeeId(957L);
        task.setSourceType(OaOnboardSignEventFactory.SOURCE_TYPE);
        task.setSourceBusinessId("1876");
        task.setSourceEventVersion("3");
        task.setAssignedHrUserId(101L);
        OaSignTask duplicate = new OaSignTask();
        duplicate.setTaskId(199L);
        duplicate.setScenario("ONBOARD");
        duplicate.setEmployeeId(957L);
        duplicate.setShopDeptId(1171L);
        duplicate.setSourceType(OaOnboardSignEventFactory.SOURCE_TYPE);
        duplicate.setSourceBusinessId("1876");
        duplicate.setSourceEventVersion("3");
        duplicate.setAssignedHrUserId(101L);
        when(taskMapper.selectCanonicalTaskBySourceEvent(any())).thenReturn(task);
        when(taskMapper.selectExactTasksBySourceEvent(any()))
                .thenReturn(List.of(task, duplicate));
        AtomicBoolean wrote = new AtomicBoolean();

        assertThatThrownBy(() -> orchestrator(List.of()).withLockedExcelImportTask(
                row, batch, 198L, 101L, locked -> {
                    wrote.set(true);
                    return null;
                }))
                .isInstanceOf(com.erp.common.core.exception.ServiceException.class)
                .hasMessageContaining("规范任务");

        assertThat(wrote).isFalse();
        verify(taskMapper, never()).lockOaSignTaskById(198L);
    }

    @Test
    @DisplayName("未发布或停止匹配的真实版本不能绑定并降级到待补资料")
    void shouldRejectUnpublishedOrDisabledLockedVersion()
    {
        OaSignScenarioRule rule = createDraftRule();
        OaSignPlanVersion invalid = publishedVersion();
        invalid.setMatchingStatus("DISABLED");
        when(versionMapper.lockPlanVersionById(55L)).thenReturn(invalid);

        Long taskId = orchestrator(List.of(rule)).orchestrate(event(1L));

        assertThat(taskId).isEqualTo(9L);
        assertThat(task.getStatus()).isEqualTo(OaSignTaskStatus.NEEDS_DATA.name());
        assertThat(task.getFailureCode()).isEqualTo("PLAN_NOT_FOUND");
        verify(packageMapper, never()).insertOaSignPackage(any());
        verify(taskMapper, never()).bindVersionedPackage(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("版本范围与任务不一致时不绑定并记录PLAN_CONFLICT")
    void shouldRejectLockedVersionOutsideTaskScope()
    {
        OaSignScenarioRule rule = createDraftRule();
        OaSignPlanVersion invalid = publishedVersion();
        invalid.setShopDeptId(999L);
        when(versionMapper.lockPlanVersionById(55L)).thenReturn(invalid);

        Long taskId = orchestrator(List.of(rule)).orchestrate(event(1L));

        assertThat(taskId).isEqualTo(9L);
        assertThat(task.getFailureCode()).isEqualTo("PLAN_CONFLICT");
        verify(packageMapper, never()).insertOaSignPackage(any());
    }

    @Test
    @DisplayName("基础设施失败保留FAILED任务并重新抛出供system outbox重试")
    void shouldKeepFailedTaskAndRethrowInfrastructureFailure()
    {
        OaSignScenarioRule rule = matchingRule();
        when(rule.dedupeKey(any())).thenReturn("ONBOARD:201:record-9:1");
        when(rule.decide(any())).thenThrow(new IllegalStateException("object storage unavailable"));

        assertThatThrownBy(() -> orchestrator(List.of(rule)).orchestrate(event(1L)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("object storage unavailable");
        assertThat(task.getTaskId()).isEqualTo(9L);
        assertThat(task.getStatus()).isEqualTo(OaSignTaskStatus.FAILED.name());
        assertThat(task.getFailureCode()).isEqualTo("INFRASTRUCTURE_FAILURE");
        assertThat(task.getFailureDetail()).contains("object storage unavailable");
    }

    @Test
    @DisplayName("规则去重键计算异常时使用事件兜底键保留FAILED任务并抛出重试")
    void shouldKeepTaskWhenRuleDedupeCalculationFails()
    {
        OaSignScenarioRule rule = matchingRule();
        when(rule.dedupeKey(any())).thenThrow(new IllegalStateException("dedupe dependency unavailable"));

        assertThatThrownBy(() -> orchestrator(List.of(rule)).orchestrate(event(1L)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("dedupe dependency unavailable");
        assertThat(task.getTaskId()).isEqualTo(9L);
        assertThat(task.getDedupeKey()).isEqualTo("EVENT:event-9:1");
        assertThat(task.getStatus()).isEqualTo(OaSignTaskStatus.FAILED.name());
        assertThat(task.getFailureCode()).isEqualTo("INFRASTRUCTURE_FAILURE");
    }

    @Test
    @DisplayName("转正去重键业务失败仍从规则安全提取薪资复核上下文")
    void shouldPreserveSalaryReviewContextWhenRegularizeDedupeKeyIsInvalid()
    {
        OaSignScenarioRule rule = new RegularizeSignScenarioRule(
                versionMapper, new ObjectMapper());
        HrSignBusinessEvent event = regularizeSalaryChangeEventWithoutOperator();

        Long taskId = orchestrator(List.of(rule)).orchestrate(event);

        assertThat(taskId).isEqualTo(9L);
        assertThat(task.getStatus()).isEqualTo(OaSignTaskStatus.NEEDS_DATA.name());
        assertThat(task.getFailureCode()).isEqualTo("DEDUPE_KEY_INVALID");
        assertThat(task.getRiskLevel()).isEqualTo("REVIEW_REQUIRED");
        assertThat(task.getFailureDetail())
                .contains("INVALID_OPERATOR_USER_ID", "SALARY_CHANGED");
        verify(taskMapper).updateRiskLevelForValidation(
                9L, "REVIEW_REQUIRED", 1L, 101L);
        verifyNoInteractions(versionMapper);
    }

    @Test
    @DisplayName("去重键业务失败后的安全决策提取异常不覆盖原业务失败")
    void shouldKeepOriginalDedupeFailureWhenRiskContextEvaluationThrows()
    {
        OaSignScenarioRule rule = mock(OaSignScenarioRule.class);
        when(rule.supports("REGULARIZE")).thenReturn(true);
        when(rule.dedupeKey(any())).thenThrow(
                new com.erp.common.core.exception.ServiceException("invalid dedupe source"));
        when(rule.decide(any())).thenThrow(new IllegalStateException("plan store unavailable"));

        Long taskId = orchestrator(List.of(rule)).orchestrate(regularizeEvent());

        assertThat(taskId).isEqualTo(9L);
        assertThat(task.getStatus()).isEqualTo(OaSignTaskStatus.NEEDS_DATA.name());
        assertThat(task.getFailureCode()).isEqualTo("DEDUPE_KEY_INVALID");
        assertThat(task.getFailureDetail()).contains("invalid dedupe source")
                .doesNotContain("plan store unavailable");
        assertThat(transitions).doesNotContain(OaSignTaskStatus.FAILED);
        verify(rule).decide(any());
    }

    @Test
    @DisplayName("基础设施失败事件重投恢复同一任务而不丢失或新建任务")
    void shouldRecoverSameFailedTaskOnRedelivery()
    {
        OaSignScenarioRule rule = matchingRule();
        when(rule.dedupeKey(any())).thenReturn("ONBOARD:201:record-9:1");
        OaSignDraftDecision recoveredDecision = new OaSignDraftDecision();
        recoveredDecision.setAction(OaSignDraftDecision.Action.NEEDS_DATA);
        recoveredDecision.setReasonCodes(List.of("PROFILE_INCOMPLETE"));
        when(rule.decide(any()))
                .thenThrow(new IllegalStateException("temporary outage"))
                .thenReturn(recoveredDecision);
        OaSignTaskOrchestrator orchestrator = orchestrator(List.of(rule));
        assertThatThrownBy(() -> orchestrator.orchestrate(event(1L)))
                .isInstanceOf(IllegalStateException.class);
        OaSignTask persistedFailure = task;
        doReturn(persistedFailure).when(taskService).createTask(any());

        Long retried = orchestrator.orchestrate(event(1L));

        assertThat(retried).isEqualTo(9L);
        assertThat(task).isSameAs(persistedFailure);
        assertThat(task.getStatus()).isEqualTo(OaSignTaskStatus.NEEDS_DATA.name());
        assertThat(task.getFailureCode()).isEqualTo("PROFILE_INCOMPLETE");
        verify(taskService, times(2)).createTask(any());
    }

    @Test
    @DisplayName("并发重投已由另一处理器推进到等待确认时幂等返回而不误标失败")
    void shouldTreatConcurrentAdvancedTaskAsIdempotentSuccess()
    {
        OaSignScenarioRule rule = createDraftRule();
        when(versionMapper.lockPlanVersionById(55L)).thenReturn(publishedVersion());
        when(packageMapper.insertOaSignPackage(any())).thenAnswer(invocation -> {
            invocation.getArgument(0, OaSignPackage.class).setPackageId(90L);
            return 1;
        });
        when(taskMapper.bindVersionedPackage(9L, 90L, 55L, "LOW", 1L, 101L)).thenReturn(1);
        when(packageService.preparePackageDocumentsForSystem(90L, 9L, 101L))
                .thenAnswer(invocation -> taskPackage());
        AtomicBoolean raced = new AtomicBoolean();
        doAnswer(invocation -> {
            OaSignTask current = invocation.getArgument(0);
            OaSignTaskStatus target = invocation.getArgument(1);
            if (target == OaSignTaskStatus.DRAFT_CREATED && raced.compareAndSet(false, true))
            {
                current.setStatus(OaSignTaskStatus.READY_TO_SEND.name());
                current.setVersion(current.getVersion() + 2);
                throw new com.erp.common.core.exception.ServiceException("任务已被其他操作更新");
            }
            current.setStatus(target.name());
            current.setVersion(current.getVersion() + 1);
            return current;
        }).when(eventService).transition(any(), any(), any(), any(), any(), any(), any(), any(), any());

        Long taskId = orchestrator(List.of(rule)).orchestrate(event(1L));

        assertThat(taskId).isEqualTo(9L);
        assertThat(task.getStatus()).isEqualTo(OaSignTaskStatus.READY_TO_SEND.name());
        assertThat(task.getFailureCode()).isNull();
    }

    @ParameterizedTest(name = "{0}草稿无需审批即可发送")
    @ValueSource(strings = { "ONBOARD", "REGULARIZE", "TRANSFER", "OFFBOARD", "RENEWAL" })
    @DisplayName("五类场景全部跳过审批并直接进入待发送")
    void shouldMakeEveryScenarioReadyToSendWithoutApproval(String scenario)
    {
        HrSignBusinessEvent event = event(1L);
        event.setScenario(scenario);
        if ("RENEWAL".equals(scenario))
        {
            event.setSourceType("HR_LIFECYCLE_ACTION");
            event.setSourceBusinessId("701");
            when(renewalGuardMapper.selectForUpdate(201L, "RENEWAL"))
                    .thenReturn(guard("RESERVED", 701L, null, 0L));
            when(renewalGuardMapper.activate(201L, "RENEWAL", 701L, 9L, 0L))
                    .thenReturn(1);
        }
        OaSignScenarioRule rule = createDraftRule();
        when(rule.supports(scenario)).thenReturn(true);
        when(rule.dedupeKey(any())).thenReturn(scenario + ":201:record-9:1");
        OaSignPlanVersion version = publishedVersion();
        version.setScenario(scenario);
        when(versionMapper.lockPlanVersionById(55L)).thenReturn(version);
        when(packageMapper.insertOaSignPackage(any())).thenAnswer(invocation -> {
            invocation.getArgument(0, OaSignPackage.class).setPackageId(90L);
            return 1;
        });
        when(taskMapper.bindVersionedPackage(9L, 90L, 55L, "LOW", 1L, 101L)).thenReturn(1);
        when(packageService.preparePackageDocumentsForSystem(90L, 9L, 101L))
                .thenAnswer(invocation -> taskPackage());

        orchestrator(List.of(rule)).orchestrate(event);

        assertThat(task.getStatus()).isEqualTo(OaSignTaskStatus.READY_TO_SEND.name());
        assertThat(task.getAutomationLevel()).isEqualTo("MANUAL");
        assertThat(transitions).doesNotContain(OaSignTaskStatus.WAITING_HR_CONFIRM);
    }

    @Test
    @DisplayName("续签消费先锁权威门闩再检查有效任务并原子激活")
    void shouldLockAndActivateReservedRenewalGuardBeforeProcessing()
    {
        OaSignScenarioRule rule = renewalNeedsDataRule();
        when(renewalGuardMapper.selectForUpdate(201L, "RENEWAL"))
                .thenReturn(guard("RESERVED", 701L, null, 2L));
        when(renewalGuardMapper.activate(201L, "RENEWAL", 701L, 9L, 2L))
                .thenReturn(1);

        assertThat(orchestrator(List.of(rule)).orchestrate(renewalEvent(701L)))
                .isEqualTo(9L);

        InOrder order = inOrder(renewalGuardMapper, taskMapper, taskService);
        order.verify(renewalGuardMapper).selectForUpdate(201L, "RENEWAL");
        order.verify(taskMapper).selectOpenRenewalTaskForUpdate(201L);
        order.verify(taskService).createTask(any());
        order.verify(renewalGuardMapper).activate(201L, "RENEWAL", 701L, 9L, 2L);
    }

    @ParameterizedTest(name = "已有{0}续签任务时阻断新事件")
    @ValueSource(strings = { "READY_TO_SEND", "PENDING_SIGN" })
    @DisplayName("OA消费侧在建第二个任务和包前拒绝已有非终态续签")
    void shouldBlockAnotherRenewalWhenOpenTaskExists(String status)
    {
        when(renewalGuardMapper.selectForUpdate(201L, "RENEWAL"))
                .thenReturn(guard("RESERVED", 702L, null, 4L));
        OaSignTask open = renewalTask(9L, 701L, OaSignTaskStatus.valueOf(status));
        when(taskMapper.selectOpenRenewalTaskForUpdate(201L)).thenReturn(open);

        assertThatThrownBy(() -> orchestrator(List.of(renewalNeedsDataRule()))
                .orchestrate(renewalEvent(702L)))
                .isInstanceOf(com.erp.common.core.exception.ServiceException.class)
                .hasMessageContaining("未完成续签任务");
        verify(taskService, never()).createTask(any());
        verify(packageMapper, never()).insertOaSignPackage(any());
    }

    @Test
    @DisplayName("并发同源事件看到已激活门闩时复用原任务而不自阻断")
    void shouldReuseSameSourceTaskAfterConcurrentGuardActivation()
    {
        task = renewalTask(9L, 701L, OaSignTaskStatus.READY_TO_SEND);
        task.setPackageId(90L);
        task.setPlanVersionId(55L);
        when(renewalGuardMapper.selectForUpdate(201L, "RENEWAL"))
                .thenReturn(guard("ACTIVE", 701L, 9L, 3L));

        assertThat(orchestrator(List.of(renewalNeedsDataRule()))
                .orchestrate(renewalEvent(701L))).isEqualTo(9L);

        verify(taskService, never()).createTask(any());
        verify(renewalGuardMapper, never()).activate(anyLong(), any(), anyLong(), anyLong(), anyLong());
        verify(notificationOutboxService).enqueueWaitingHr(any(), any());
    }

    @ParameterizedTest(name = "{0}门闩下create竞态返回终态任务")
    @ValueSource(strings = { "RESERVED", "IDLE" })
    @DisplayName("初查为空但create返回并发终态同源任务时原事务释放门闩而不激活")
    void shouldReconcileTerminalTaskReturnedByConcurrentCreate(String guardStatus)
    {
        when(taskMapper.selectCanonicalTaskBySourceEvent(any())).thenReturn(null);
        HrRenewalGuard guard = "IDLE".equals(guardStatus)
                ? guard("IDLE", null, null, 2L)
                : guard("RESERVED", 701L, null, 2L);
        when(renewalGuardMapper.selectForUpdate(201L, "RENEWAL")).thenReturn(guard);
        if ("IDLE".equals(guardStatus))
        {
            when(renewalGuardMapper.reserveIdleForEarliestRecoverableAction(
                    201L, "RENEWAL", 701L, 2L)).thenReturn(1);
        }
        task = renewalTask(9L, 701L, OaSignTaskStatus.NO_ACTION);
        doReturn(task).when(taskService).createTask(any());

        assertThat(orchestrator(List.of(renewalNeedsDataRule()))
                .orchestrate(renewalEvent(701L))).isEqualTo(9L);

        InOrder order = inOrder(taskMapper, renewalGuardMapper, taskService, eventService);
        order.verify(taskMapper).selectCanonicalTaskBySourceEvent(any());
        order.verify(renewalGuardMapper).selectForUpdate(201L, "RENEWAL");
        if ("IDLE".equals(guardStatus))
        {
            order.verify(renewalGuardMapper).reserveIdleForEarliestRecoverableAction(
                    201L, "RENEWAL", 701L, 2L);
        }
        order.verify(taskMapper).selectOpenRenewalTaskForUpdate(201L);
        order.verify(taskService).createTask(any());
        order.verify(eventService).reconcileTerminalRenewalGuard(task);
        verify(transactionManager).commit(transactionStatus);
        verify(renewalGuardMapper, never()).activate(anyLong(), any(), anyLong(), anyLong(), anyLong());
    }

    @Test
    @DisplayName("初查为空但ACTIVE同源任务已终态时原事务修复门闩")
    void shouldReconcileTerminalTaskFoundBehindActiveGuard()
    {
        when(taskMapper.selectCanonicalTaskBySourceEvent(any())).thenReturn(null);
        when(renewalGuardMapper.selectForUpdate(201L, "RENEWAL"))
                .thenReturn(guard("ACTIVE", 701L, 9L, 3L));
        task = renewalTask(9L, 701L, OaSignTaskStatus.SIGNED);

        assertThat(orchestrator(List.of(renewalNeedsDataRule()))
                .orchestrate(renewalEvent(701L))).isEqualTo(9L);

        InOrder order = inOrder(taskMapper, renewalGuardMapper, eventService);
        order.verify(taskMapper).selectCanonicalTaskBySourceEvent(any());
        order.verify(renewalGuardMapper).selectForUpdate(201L, "RENEWAL");
        order.verify(taskMapper).lockOaSignTaskById(9L);
        order.verify(eventService).reconcileTerminalRenewalGuard(task);
        verify(transactionManager).commit(transactionStatus);
        verify(taskService, never()).createTask(any());
        verify(renewalGuardMapper, never()).activate(anyLong(), any(), anyLong(), anyLong(), anyLong());
    }

    @ParameterizedTest(name = "终态{0}重投必须修复续签门闩")
    @ValueSource(strings = { "SIGNED", "REFUSED", "EXPIRED", "CANCELLED", "NO_ACTION" })
    @DisplayName("同source续签终态重投在独立事务中修复门闩后幂等返回")
    void shouldReconcileGuardWhenExistingRenewalTaskIsTerminal(String status)
    {
        task = renewalTask(9L, 701L, OaSignTaskStatus.valueOf(status));
        when(taskMapper.selectCanonicalTaskBySourceEvent(any())).thenReturn(task);

        assertThat(orchestrator(List.of()).orchestrate(renewalEvent(701L)))
                .isEqualTo(9L);

        verify(eventService).reconcileTerminalRenewalGuard(task);
        verify(transactionManager).commit(transactionStatus);
        verify(taskService, never()).createTask(any());
    }

    @Test
    @DisplayName("迁移重跑仍留IDLE时A2仅凭最早未结action证明自愈并激活")
    void shouldRecoverA2FromIdleGuardLeftByRepeatedMigration()
    {
        when(renewalGuardMapper.selectForUpdate(201L, "RENEWAL"))
                .thenReturn(guard("IDLE", null, null, 4L));
        when(renewalGuardMapper.reserveIdleForEarliestRecoverableAction(
                201L, "RENEWAL", 702L, 4L)).thenReturn(1);
        when(renewalGuardMapper.activate(201L, "RENEWAL", 702L, 9L, 5L))
                .thenReturn(1);

        assertThat(orchestrator(List.of(renewalNeedsDataRule()))
                .orchestrate(renewalEvent(702L))).isEqualTo(9L);

        InOrder order = inOrder(renewalGuardMapper, taskMapper, taskService);
        order.verify(renewalGuardMapper).selectForUpdate(201L, "RENEWAL");
        order.verify(renewalGuardMapper).reserveIdleForEarliestRecoverableAction(
                201L, "RENEWAL", 702L, 4L);
        order.verify(taskMapper).selectOpenRenewalTaskForUpdate(201L);
        order.verify(taskService).createTask(any());
        order.verify(renewalGuardMapper).activate(201L, "RENEWAL", 702L, 9L, 5L);
    }

    @Test
    @DisplayName("ONBOARD不同来源并发命中开放任务门闩时只复用不推进")
    void shouldNotAdvanceCrossSourceOnboardTaskReturnedByCreationGuard()
    {
        OaSignScenarioRule rule = matchingRule();
        when(rule.dedupeKey(any())).thenReturn("ONBOARD:PROFILE:201:20260717");
        OaSignTask canonical = new OaSignTask();
        canonical.setTaskId(88L);
        canonical.setScenario("ONBOARD");
        canonical.setEmployeeId(201L);
        canonical.setSourceType("HR_LIFECYCLE_ACTION");
        canonical.setSourceBusinessId("991");
        canonical.setSourceEventVersion("1");
        canonical.setDedupeKey("ONBOARD:LIFECYCLE:991:1");
        canonical.setStatus(OaSignTaskStatus.NEW.name());
        canonical.setVersion(0L);
        doReturn(canonical).when(taskService).createTask(any());

        Long taskId = orchestrator(List.of(rule)).orchestrate(event(1L));

        assertThat(taskId).isEqualTo(88L);
        assertThat(canonical.getStatus()).isEqualTo(OaSignTaskStatus.NEW.name());
        verify(rule, never()).decide(any());
        verify(eventService, never()).transition(any(), any(), any(), any(), any(), any(),
                any(), any(), any());
    }

    @Test
    @DisplayName("IDLE认领无法证明当前action最早未结时不创建任务")
    void shouldRejectIdleClaimWithoutEarliestRecoverableActionProof()
    {
        when(renewalGuardMapper.selectForUpdate(201L, "RENEWAL"))
                .thenReturn(guard("IDLE", null, null, 4L));

        assertThatThrownBy(() -> orchestrator(List.of(renewalNeedsDataRule()))
                .orchestrate(renewalEvent(702L)))
                .isInstanceOf(com.erp.common.core.exception.ServiceException.class)
                .hasMessageContaining("最早未结业务动作");

        verify(taskService, never()).createTask(any());
        verify(renewalGuardMapper, never()).activate(anyLong(), any(), anyLong(), anyLong(), anyLong());
    }

    @Test
    @DisplayName("RESERVED门闩属于其他action时不得被消费或覆盖")
    void shouldRejectReservedGuardOwnedByAnotherAction()
    {
        when(renewalGuardMapper.selectForUpdate(201L, "RENEWAL"))
                .thenReturn(guard("RESERVED", 701L, null, 4L));

        assertThatThrownBy(() -> orchestrator(List.of(renewalNeedsDataRule()))
                .orchestrate(renewalEvent(702L)))
                .isInstanceOf(com.erp.common.core.exception.ServiceException.class)
                .hasMessageContaining("当前业务动作预留");

        verify(renewalGuardMapper, never()).reserveIdleForEarliestRecoverableAction(
                anyLong(), any(), anyLong(), anyLong());
        verify(taskService, never()).createTask(any());
        verify(renewalGuardMapper, never()).activate(anyLong(), any(), anyLong(), anyLong(), anyLong());
    }

    @Test
    @DisplayName("ACTIVE门闩绑定其他action时拒绝绕过")
    void shouldRejectActiveGuardOwnedByAnotherAction()
    {
        when(renewalGuardMapper.selectForUpdate(201L, "RENEWAL"))
                .thenReturn(guard("ACTIVE", 701L, 9L, 3L));

        assertThatThrownBy(() -> orchestrator(List.of(renewalNeedsDataRule()))
                .orchestrate(renewalEvent(702L)))
                .isInstanceOf(com.erp.common.core.exception.ServiceException.class)
                .hasMessageContaining("未完成续签任务");
        verify(taskService, never()).createTask(any());
    }

    private OaSignTaskOrchestrator orchestrator(List<OaSignScenarioRule> rules)
    {
        return new OaSignTaskOrchestrator(rules, taskService, taskMapper, eventService,
                versionMapper, packageMapper, packageService, renewalGuardMapper,
                notificationOutboxService,
                transactionManager, JsonMapper.builder().findAndAddModules().build());
    }

    private HrSignBusinessEvent transferEvent()
    {
        HrEmployeeSigningSnapshot before = new HrEmployeeSigningSnapshot();
        before.setEmployeeId(201L);
        before.setShopDeptId(1171L);
        before.setLegalEntityId(301L);
        before.setPostName("销售顾问");
        HrEmployeeSigningSnapshot after = new HrEmployeeSigningSnapshot();
        after.setEmployeeId(201L);
        after.setShopDeptId(1171L);
        after.setLegalEntityId(301L);
        after.setPostName("店长");
        after.setTransferEffectiveDate(LocalDate.of(2026, 12, 31));
        HrSignBusinessEvent event = new HrSignBusinessEvent();
        event.setEventId("transfer-event-801");
        event.setScenario("TRANSFER");
        event.setEmployeeId(201L);
        event.setSourceType("HR_LIFECYCLE_ACTION");
        event.setSourceBusinessId("801");
        event.setSourceEventVersion(1L);
        event.setOccurredTime(Date.from(Instant.parse("2026-12-31T16:30:00Z")));
        event.setOperatorUserId(101L);
        event.setBeforeSnapshot(before);
        event.setAfterSnapshot(after);
        event.setAttributes(new HashMap<>(Map.of(
                "actionType", "TRANSFER_CONFIRMED",
                "sourceActionId", 801L,
                "sourceActionVersion", 1L,
                "effectiveDate", "2026-12-31",
                "historicalSupplement", true,
                "historicalReason", "补录纸质调岗单")));
        return event;
    }

    private HrSignBusinessEvent offboardEvent()
    {
        HrEmployeeSigningSnapshot before = new HrEmployeeSigningSnapshot();
        before.setEmployeeId(201L);
        before.setShopDeptId(1171L);
        before.setLegalEntityId(301L);
        before.setEmployeeStatus("正式");
        before.setAccountStatus("0");
        HrEmployeeSigningSnapshot after = new HrEmployeeSigningSnapshot();
        after.setEmployeeId(201L);
        after.setShopDeptId(1171L);
        after.setLegalEntityId(301L);
        after.setEmployeeStatus("离职");
        after.setAccountStatus("1");
        after.setLeaveDate(LocalDate.of(2026, 12, 31));
        after.setOffboardingType("VOLUNTARY_EXPECTED");
        HrSignBusinessEvent event = new HrSignBusinessEvent();
        event.setEventId("offboard-event-901");
        event.setScenario("OFFBOARD");
        event.setEmployeeId(201L);
        event.setSourceType("HR_LIFECYCLE_ACTION");
        event.setSourceBusinessId("901");
        event.setSourceEventVersion(1L);
        event.setOccurredTime(Date.from(Instant.parse("2026-12-31T16:30:00Z")));
        event.setOperatorUserId(101L);
        event.setBeforeSnapshot(before);
        event.setAfterSnapshot(after);
        event.setAttributes(new HashMap<>(Map.of(
                "actionType", "OFFBOARD_CONFIRMED",
                "sourceActionId", 901L,
                "sourceActionVersion", 1L,
                "effectiveDate", "2026-12-31",
                "historicalSupplement", true,
                "riskLevel", "HIGH")));
        return event;
    }

    private OaSignScenarioRule matchingRule()
    {
        OaSignScenarioRule rule = mock(OaSignScenarioRule.class);
        when(rule.supports("ONBOARD")).thenReturn(true);
        return rule;
    }

    private OaSignScenarioRule createDraftRule()
    {
        OaSignScenarioRule rule = matchingRule();
        when(rule.dedupeKey(any())).thenReturn("ONBOARD:201:record-9:1");
        OaSignDraftDecision decision = new OaSignDraftDecision();
        decision.setAction(OaSignDraftDecision.Action.CREATE_DRAFT);
        decision.setPlanVersionId(55L);
        decision.setRiskLevel("LOW");
        decision.setReasonCodes(List.of());
        decision.setDraftPackage(new OaSignPackage());
        when(rule.decide(any())).thenReturn(decision);
        return rule;
    }

    private OaSignScenarioRule salaryReviewDraftRule()
    {
        OaSignScenarioRule rule = mock(OaSignScenarioRule.class);
        when(rule.supports("REGULARIZE")).thenReturn(true);
        when(rule.dedupeKey(any())).thenReturn("REGULARIZE:201:701:1");
        OaSignDraftDecision decision = new OaSignDraftDecision();
        decision.setAction(OaSignDraftDecision.Action.CREATE_DRAFT);
        decision.setPlanVersionId(55L);
        decision.setRiskLevel("REVIEW_REQUIRED");
        decision.setReasonCodes(List.of("SALARY_CHANGED"));
        decision.setDraftPackage(new OaSignPackage());
        when(rule.decide(any())).thenReturn(decision);
        return rule;
    }

    private HrSignBusinessEvent regularizeEvent()
    {
        HrSignBusinessEvent event = event(1L);
        event.setScenario("REGULARIZE");
        event.setSourceType("HR_LIFECYCLE_ACTION");
        event.setSourceBusinessId("701");
        return event;
    }

    private HrSignBusinessEvent regularizeSalaryChangeEventWithoutOperator()
    {
        HrEmployeeSigningSnapshot before = new HrEmployeeSigningSnapshot();
        before.setEmployeeId(201L);
        before.setBaseSalary(new BigDecimal("5000.00"));
        before.setSalaryVersion("2026-V1");
        HrEmployeeSigningSnapshot after = new HrEmployeeSigningSnapshot();
        after.setEmployeeId(201L);
        after.setBaseSalary(new BigDecimal("6000.00"));
        after.setSalaryVersion("2026-V2");

        HrSignBusinessEvent event = regularizeEvent();
        event.setOccurredTime(Date.from(Instant.parse("2026-07-12T02:03:04Z")));
        event.setBeforeSnapshot(before);
        event.setAfterSnapshot(after);
        event.setAttributes(Map.of(
                "actionType", "REGULARIZATION_CONFIRMED",
                "sourceActionId", 701L,
                "sourceActionVersion", 1L));
        event.setOperatorUserId(null);
        return event;
    }

    private OaSignScenarioRule renewalNeedsDataRule()
    {
        OaSignScenarioRule rule = mock(OaSignScenarioRule.class);
        when(rule.supports("RENEWAL")).thenReturn(true);
        when(rule.dedupeKey(any())).thenAnswer(invocation -> {
            HrSignBusinessEvent event = invocation.getArgument(0);
            return "RENEWAL:201:2026-08-01:" + event.getSourceBusinessId();
        });
        OaSignDraftDecision decision = new OaSignDraftDecision();
        decision.setAction(OaSignDraftDecision.Action.NEEDS_DATA);
        decision.setRiskLevel("HIGH");
        decision.setReasonCodes(List.of("TEST_NEEDS_DATA"));
        when(rule.decide(any())).thenReturn(decision);
        return rule;
    }

    private HrSignBusinessEvent renewalEvent(Long actionId)
    {
        HrSignBusinessEvent event = event(1L);
        event.setScenario("RENEWAL");
        event.setSourceType("HR_LIFECYCLE_ACTION");
        event.setSourceBusinessId(String.valueOf(actionId));
        return event;
    }

    private OaSignTask renewalTask(Long taskId, Long actionId, OaSignTaskStatus status)
    {
        OaSignTask value = new OaSignTask();
        value.setTaskId(taskId);
        value.setScenario("RENEWAL");
        value.setEmployeeId(201L);
        value.setSourceType("HR_LIFECYCLE_ACTION");
        value.setSourceBusinessId(String.valueOf(actionId));
        value.setSourceEventVersion("1");
        value.setDedupeKey("RENEWAL:201:2026-08-01:" + actionId);
        value.setAssignedHrUserId(101L);
        value.setStatus(status.name());
        value.setVersion(2L);
        return value;
    }

    private HrRenewalGuard guard(String status, Long actionId, Long taskId, Long version)
    {
        HrRenewalGuard value = new HrRenewalGuard();
        value.setEmployeeId(201L);
        value.setScenario("RENEWAL");
        value.setStatus(status);
        value.setActionId(actionId);
        value.setTaskId(taskId);
        value.setVersion(version);
        return value;
    }

    private OaSignPackage taskPackage()
    {
        OaSignPackage signPackage = new OaSignPackage();
        signPackage.setPackageId(90L);
        signPackage.setTaskId(9L);
        signPackage.setPlanVersionId(55L);
        signPackage.setEmployeeId(201L);
        signPackage.setShopDeptId(1171L);
        signPackage.setDocumentVersion("SP-90-V1");
        return signPackage;
    }

    private HrSignBusinessEvent event(long eventVersion)
    {
        HrEmployeeSigningSnapshot snapshot = new HrEmployeeSigningSnapshot();
        snapshot.setEmployeeId(201L);
        snapshot.setEmployeeName("员工甲");
        snapshot.setPhone("13800000000");
        snapshot.setIdNumber("110101199001011234");
        snapshot.setShopDeptId(1171L);
        snapshot.setLegalEntityId(301L);
        HrSignBusinessEvent event = new HrSignBusinessEvent();
        event.setEventId("event-9");
        event.setScenario("ONBOARD");
        event.setEmployeeId(201L);
        event.setSourceType("HR_ONBOARD");
        event.setSourceBusinessId("record-9");
        event.setSourceEventVersion(eventVersion);
        event.setAfterSnapshot(snapshot);
        return event;
    }

    private HrSignBusinessEvent excelEvent(String generationRequestId)
    {
        HrSignBusinessEvent excel = event(3L);
        excel.setEventId("OA-ONBOARD-EXCEL:1876:3");
        excel.setSourceType(OaOnboardSignEventFactory.SOURCE_TYPE);
        excel.setSourceBusinessId("1876");
        excel.setOperatorUserId(101L);
        excel.setAttributes(new HashMap<>(Map.of(
                "importBatchId", 134L,
                "importRowId", 1876L,
                "generationRequestId", generationRequestId)));
        return excel;
    }

    private OaSignPlanVersion publishedVersion()
    {
        OaSignPlanVersion version = new OaSignPlanVersion();
        version.setVersionId(55L);
        version.setPlanId(5L);
        version.setPlanName("标准入职方案");
        version.setScenario("ONBOARD");
        version.setShopDeptId(1171L);
        version.setLegalEntityId(301L);
        version.setPublishStatus("PUBLISHED");
        version.setMatchingStatus("ENABLED");
        version.setAutoSendConditionJson("{\"enabled\":true}");
        return version;
    }
}
