package com.erp.oa.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import com.erp.common.core.context.SecurityContextHolder;
import com.erp.oa.api.domain.HrSignBusinessEvent;
import com.erp.oa.constant.OaSignPackageStatus;
import com.erp.oa.domain.OaCompanySealConfig;
import com.erp.oa.domain.OaSignOnboardImportBatch;
import com.erp.oa.domain.OaSignOnboardContractSnapshot;
import com.erp.oa.domain.OaSignOnboardDataRequest;
import com.erp.oa.domain.OaSignOnboardImportRow;
import com.erp.oa.domain.OaSignPackage;
import com.erp.oa.domain.OaSignPlanVersion;
import com.erp.oa.domain.OaSignTask;
import com.erp.oa.domain.dto.OaSignOnboardGenerateRequest;
import com.erp.oa.domain.vo.OaSignDraftDecision;
import com.erp.oa.domain.vo.OaSignOnboardGenerateResult;
import com.erp.oa.mapper.OaSignOnboardImportBatchMapper;
import com.erp.oa.mapper.OaSignOnboardDataRequestMapper;
import com.erp.oa.mapper.OaSignOnboardImportRowMapper;
import com.erp.oa.mapper.OaSignPackageMapper;
import com.erp.oa.mapper.OaSignPlanVersionMapper;
import com.erp.oa.mapper.OaSignTaskMapper;
import com.erp.oa.service.rule.OnboardSignScenarioRule;
import com.erp.oa.service.IOaSignPackageService;
import com.erp.system.api.domain.SignCandidateUser;
import com.erp.system.api.domain.SysLegalEntity;

class OaSignOnboardGenerationServiceTest
{
    private OaSignOnboardImportService importService;
    private OaSignOnboardImportBatchMapper batchMapper;
    private OaSignOnboardImportRowMapper rowMapper;
    private OaSignTaskMapper taskMapper;
    private OaSignPackageMapper packageMapper;
    private OaSignOnboardDataRequestMapper dataRequestMapper;
    private OaSignCompanyService companyService;
    private OaSignPlanVersionMapper planVersionMapper;
    private OaOnboardSignEventFactory eventFactory;
    private OnboardSignScenarioRule onboardRule;
    private OaSignTaskOrchestrator orchestrator;
    private IOaSignPackageService packageService;
    private OaSignOnboardGenerationService service;

    @BeforeEach
    void setUp()
    {
        SecurityContextHolder.setUserId("101");
        importService = mock(OaSignOnboardImportService.class);
        batchMapper = mock(OaSignOnboardImportBatchMapper.class);
        rowMapper = mock(OaSignOnboardImportRowMapper.class);
        taskMapper = mock(OaSignTaskMapper.class);
        packageMapper = mock(OaSignPackageMapper.class);
        dataRequestMapper = mock(OaSignOnboardDataRequestMapper.class);
        when(importService.list(any())).thenReturn(List.of());
        when(importService.json(any())).thenReturn("[\"GENERATE_FAILED\"]");
        companyService = mock(OaSignCompanyService.class);
        planVersionMapper = mock(OaSignPlanVersionMapper.class);
        eventFactory = mock(OaOnboardSignEventFactory.class);
        onboardRule = mock(OnboardSignScenarioRule.class);
        orchestrator = mock(OaSignTaskOrchestrator.class);
        when(orchestrator.withLockedExcelImportTask(any(), any(), anyLong(), anyLong(), any()))
                .thenAnswer(invocation -> {
                    Long taskId = invocation.getArgument(2, Long.class);
                    @SuppressWarnings("unchecked")
                    java.util.function.Function<OaSignTask, Object> work =
                            invocation.getArgument(4, java.util.function.Function.class);
                    return work.apply(taskMapper.selectOaSignTaskById(taskId));
                });
        packageService = mock(IOaSignPackageService.class);
        service = new OaSignOnboardGenerationService(importService,
                batchMapper, rowMapper, taskMapper,
                packageMapper, planVersionMapper, eventFactory, onboardRule,
                orchestrator, companyService, dataRequestMapper, packageService);
    }

    @AfterEach
    void clearSecurityContext()
    {
        SecurityContextHolder.remove();
    }

    @Test
    void interruptedRowRebindsOnlyAnExactReadyTaskWithItsFrozenPackage()
    {
        OaSignOnboardImportBatch batch = batch();
        OaSignOnboardImportRow interrupted = interruptedRow();
        OaSignTask task = exactTask("READY_TO_SEND", 55L, 77L);
        OaSignPackage signPackage = packageFor(task, 55L, 201L);
        OaSignOnboardImportRow recovered = interruptedRow();
        recovered.setStatus("GENERATED");
        recovered.setTaskId(66L);
        recovered.setPackageId(77L);
        recovered.setVersion(9L);

        when(taskMapper.selectCanonicalTaskBySourceEvent(any())).thenReturn(task);
        when(packageMapper.selectOaSignPackageById(77L)).thenReturn(signPackage);
        when(rowMapper.recoverCompletedGeneration(9L, 3L, 66L, 77L, 41L,
                "owner-old", 8L))
                .thenReturn(1);
        when(rowMapper.selectById(9L)).thenReturn(recovered);

        OaSignOnboardImportRow result = service.recoverInterruptedGeneration(
                batch, interrupted, new Date(2_000L));

        assertThat(result.getTaskId()).isEqualTo(66L);
        assertThat(result.getPackageId()).isEqualTo(77L);
        ArgumentCaptor<OaSignTask> query = ArgumentCaptor.forClass(OaSignTask.class);
        verify(taskMapper).selectCanonicalTaskBySourceEvent(query.capture());
        assertThat(query.getValue().getScenario()).isEqualTo("ONBOARD");
        assertThat(query.getValue().getEmployeeId()).isEqualTo(201L);
        assertThat(query.getValue().getSourceType())
                .isEqualTo(OaOnboardSignEventFactory.SOURCE_TYPE);
        assertThat(query.getValue().getSourceBusinessId()).isEqualTo("9");
        assertThat(query.getValue().getSourceEventVersion()).isEqualTo("7");
        verify(rowMapper, never()).failGeneration(anyLong(), any(), any(), any(), anyLong());
    }

    @Test
    void publicGenerationRecoversStaleTaskOnlyRowWithItsOriginalLease()
    {
        OaSignOnboardImportBatch batch = batch();
        batch.setVersion(4L);
        OaSignOnboardImportRow interrupted = interruptedRow();
        interrupted.setTaskId(66L);
        OaSignTask task = exactTask("READY_TO_SEND", 55L, 77L);
        task.setAssignedHrUserId(101L);
        OaSignPackage signPackage = packageFor(task, 55L, 201L);
        OaSignOnboardImportRow recovered = interruptedRow();
        recovered.setStatus("GENERATED");
        recovered.setTaskId(66L);
        recovered.setPackageId(77L);
        recovered.setVersion(9L);
        OaSignOnboardGenerateRequest action = new OaSignOnboardGenerateRequest();
        action.setRequestId("new-recovery-request");
        action.setBatchVersion(4L);
        action.setRowIds(List.of(9L));

        when(importService.requireHrBatch(3L, 41L)).thenReturn(batch);
        when(rowMapper.selectForGeneration(3L, List.of(9L)))
                .thenReturn(List.of(interrupted));
        when(batchMapper.claimGeneration(eq(3L), eq(4L), any(Date.class))).thenReturn(1);
        when(batchMapper.selectById(3L)).thenReturn(batch);
        when(taskMapper.selectCanonicalTaskBySourceEvent(any())).thenReturn(task);
        when(taskMapper.selectOaSignTaskById(66L)).thenReturn(task);
        when(packageMapper.selectOaSignPackageById(77L)).thenReturn(signPackage);
        when(rowMapper.recoverCompletedGeneration(9L, 3L, 66L, 77L, 41L,
                "owner-old", 8L)).thenReturn(1);
        when(rowMapper.selectById(9L)).thenReturn(recovered);

        OaSignOnboardGenerateResult result = service.generate(3L, action, 41L);

        assertThat(result.getReusedCount()).isEqualTo(1);
        assertThat(result.getItems()).singleElement().satisfies(item -> {
            assertThat(item.getResult()).isEqualTo("REUSED");
            assertThat(item.getTaskId()).isEqualTo(66L);
            assertThat(item.getPackageId()).isEqualTo(77L);
        });
        verify(rowMapper).recoverCompletedGeneration(9L, 3L, 66L, 77L, 41L,
                "owner-old", 8L);
        verify(rowMapper, never()).claimGeneration(anyLong(), any(), anyLong());
        verify(rowMapper, never()).claimTaskOnlyGeneration(
                anyLong(), anyLong(), any(), anyLong());
        verify(orchestrator, never()).orchestrate(any());
    }

    @Test
    void interruptedRowWithoutASourceTaskReturnsToRetryableState()
    {
        OaSignOnboardImportRow interrupted = interruptedRow();
        OaSignOnboardImportRow retryable = interruptedRow();
        retryable.setStatus("GENERATE_FAILED");
        retryable.setVersion(9L);
        when(taskMapper.selectCanonicalTaskBySourceEvent(any())).thenReturn(null);
        when(rowMapper.failGeneration(eq(9L), eq("GENERATE_FAILED"), any(),
                eq("owner-old"), eq(8L)))
                .thenReturn(1);
        when(rowMapper.selectById(9L)).thenReturn(retryable);

        OaSignOnboardImportRow result = service.recoverInterruptedGeneration(
                batch(), interrupted, new Date(2_000L));

        assertThat(result.getStatus()).isEqualTo("GENERATE_FAILED");
        assertThat(result.getSourceEventVersion()).isEqualTo(7L);
        verify(rowMapper, never()).recoverCompletedGeneration(anyLong(), anyLong(), anyLong(),
                anyLong(), anyLong(), any(), anyLong());
    }

    @Test
    void interruptedStagedRowReconcilesPreparedFinalOutputWithoutRegeneration()
    {
        OaSignOnboardImportBatch batch = batch();
        OaSignOnboardImportRow interrupted = signatureFirstRow(
                "GENERATING", 8L, "owner-staged");
        interrupted.setTaskId(66L);
        interrupted.setPackageId(77L);
        interrupted.setUpdateTime(new Date(1_000L));
        byte[] signatureBytes = new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47, 0x01};
        String signatureHash = OaSignOnboardExcelParser.sha256(signatureBytes);
        Date signatureTime = new Date(900L);
        OaSignOnboardDataRequest data = new OaSignOnboardDataRequest();
        data.setRequestId(501L);
        data.setRowId(9L);
        data.setEmployeeId(201L);
        data.setStatus("COMPLETED");
        data.setSigningSequence("SIGNATURE_FIRST");
        data.setSignatureRequestId("signature-request-staged");
        data.setSignatureSampleBytes(signatureBytes);
        data.setSignatureSampleHash(signatureHash);
        data.setSignatureSampleTime(signatureTime);
        OaSignTask task = exactTask("PENDING_COMPANY", 55L, 77L);
        OaSignPackage prepared = frozenSignatureFirstPackage(
                task, signatureHash, signatureTime, true);
        prepared.setStatus(OaSignPackageStatus.PENDING_COMPANY);
        prepared.setInitialSignedTime(signatureTime);
        prepared.setFinalConfirmationStatus("PREPARED_NOT_SENT");
        OaSignOnboardImportRow recovered = signatureFirstRow(
                "GENERATED", 9L, "owner-staged");
        recovered.setTaskId(66L);
        recovered.setPackageId(77L);

        when(taskMapper.selectCanonicalTaskBySourceEvent(any())).thenReturn(task);
        when(packageMapper.selectOaSignPackageById(77L)).thenReturn(prepared);
        when(dataRequestMapper.selectById(501L)).thenReturn(data);
        when(rowMapper.completeGeneration(9L, 66L, 77L,
                "owner-staged", 8L)).thenReturn(1);
        when(rowMapper.selectById(9L)).thenReturn(recovered);

        OaSignOnboardImportRow result = service.recoverInterruptedGeneration(
                batch, interrupted, new Date(2_000L));

        assertThat(result.getStatus()).isEqualTo("GENERATED");
        assertThat(result.getTaskId()).isEqualTo(66L);
        assertThat(result.getPackageId()).isEqualTo(77L);
        verify(rowMapper).completeGeneration(9L, 66L, 77L,
                "owner-staged", 8L);
        verify(packageService, never()).prepareStagedSignatureFirstCandidateForSystem(
                anyLong(), anyLong(), anyLong(), any(), anyLong(), any(), any(), any(), any(),
                any(), anyLong());
        verify(orchestrator, never()).orchestrate(any());
    }

    @Test
    void interruptedStagedRowRejectsPreparedOutputWithMismatchedSignatureEvidence()
    {
        OaSignOnboardImportRow interrupted = signatureFirstRow(
                "GENERATING", 8L, "owner-staged");
        interrupted.setTaskId(66L);
        interrupted.setPackageId(77L);
        interrupted.setUpdateTime(new Date(1_000L));
        byte[] signatureBytes = new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47, 0x02};
        String signatureHash = OaSignOnboardExcelParser.sha256(signatureBytes);
        Date signatureTime = new Date(900L);
        OaSignOnboardDataRequest data = new OaSignOnboardDataRequest();
        data.setRequestId(501L);
        data.setRowId(9L);
        data.setEmployeeId(201L);
        data.setStatus("COMPLETED");
        data.setSigningSequence("SIGNATURE_FIRST");
        data.setSignatureRequestId("signature-request-staged");
        data.setSignatureSampleBytes(signatureBytes);
        data.setSignatureSampleHash(signatureHash);
        data.setSignatureSampleTime(signatureTime);
        OaSignTask task = exactTask("PENDING_COMPANY", 55L, 77L);
        OaSignPackage prepared = frozenSignatureFirstPackage(
                task, "a".repeat(64), signatureTime, true);
        prepared.setStatus(OaSignPackageStatus.PENDING_COMPANY);
        prepared.setInitialSignedTime(signatureTime);
        prepared.setFinalConfirmationStatus("PREPARED_NOT_SENT");
        OaSignOnboardImportRow conflict = signatureFirstRow(
                "CONFLICT", 9L, "owner-staged");
        conflict.setTaskId(66L);
        conflict.setPackageId(77L);

        when(taskMapper.selectCanonicalTaskBySourceEvent(any())).thenReturn(task);
        when(packageMapper.selectOaSignPackageById(77L)).thenReturn(prepared);
        when(dataRequestMapper.selectById(501L)).thenReturn(data);
        when(rowMapper.failGeneration(eq(9L), eq("CONFLICT"), any(),
                eq("owner-staged"), eq(8L))).thenReturn(1);
        when(rowMapper.selectById(9L)).thenReturn(conflict);

        OaSignOnboardImportRow result = service.recoverInterruptedGeneration(
                batch(), interrupted, new Date(2_000L));

        assertThat(result.getStatus()).isEqualTo("CONFLICT");
        verify(rowMapper, never()).completeGeneration(anyLong(), anyLong(), anyLong(),
                any(), anyLong());
        verify(rowMapper).failGeneration(eq(9L), eq("CONFLICT"), any(),
                eq("owner-staged"), eq(8L));
    }

    @Test
    void interruptedStagedRowWithoutPreparedFinalOutputReturnsToRetryableState()
    {
        OaSignOnboardImportRow interrupted = signatureFirstRow(
                "GENERATING", 8L, "owner-staged");
        interrupted.setTaskId(66L);
        interrupted.setPackageId(77L);
        interrupted.setUpdateTime(new Date(1_000L));
        OaSignTask task = exactTask("PENDING_COMPANY", 55L, 77L);
        OaSignPackage waitingCompany = packageFor(task, 55L, 201L);
        waitingCompany.setStatus(OaSignPackageStatus.PENDING_COMPANY);
        waitingCompany.setSigningSequence("SIGNATURE_FIRST");
        waitingCompany.setFinalConfirmationStatus("WAITING_COMPANY");
        OaSignOnboardImportRow retryable = signatureFirstRow(
                "GENERATE_FAILED", 9L, "owner-staged");
        retryable.setTaskId(66L);
        retryable.setPackageId(77L);

        when(taskMapper.selectCanonicalTaskBySourceEvent(any())).thenReturn(task);
        when(packageMapper.selectOaSignPackageById(77L)).thenReturn(waitingCompany);
        when(rowMapper.failGeneration(eq(9L), eq("GENERATE_FAILED"), any(),
                eq("owner-staged"), eq(8L))).thenReturn(1);
        when(rowMapper.selectById(9L)).thenReturn(retryable);

        OaSignOnboardImportRow result = service.recoverInterruptedGeneration(
                batch(), interrupted, new Date(2_000L));

        assertThat(result.getStatus()).isEqualTo("GENERATE_FAILED");
        assertThat(result.getTaskId()).isEqualTo(66L);
        assertThat(result.getPackageId()).isEqualTo(77L);
        verify(rowMapper, never()).completeGeneration(anyLong(), anyLong(), anyLong(),
                any(), anyLong());
    }

    @Test
    void readyTaskWithMismatchedPlanIsNeverRebound()
    {
        OaSignOnboardImportRow interrupted = interruptedRow();
        OaSignTask wrongPlan = exactTask("READY_TO_SEND", 999L, 77L);
        OaSignPackage signPackage = packageFor(wrongPlan, 999L, 201L);
        OaSignOnboardImportRow conflict = interruptedRow();
        conflict.setStatus("CONFLICT");
        conflict.setVersion(9L);
        when(taskMapper.selectCanonicalTaskBySourceEvent(any())).thenReturn(wrongPlan);
        when(packageMapper.selectOaSignPackageById(77L)).thenReturn(signPackage);
        when(rowMapper.failGeneration(eq(9L), eq("CONFLICT"), any(),
                eq("owner-old"), eq(8L)))
                .thenReturn(1);
        when(rowMapper.selectById(9L)).thenReturn(conflict);

        OaSignOnboardImportRow result = service.recoverInterruptedGeneration(
                batch(), interrupted, new Date(2_000L));

        assertThat(result.getStatus()).isEqualTo("CONFLICT");
        assertThat(result.getTaskId()).isNull();
        verify(rowMapper, never()).recoverCompletedGeneration(anyLong(), anyLong(), anyLong(),
                anyLong(), anyLong(), any(), anyLong());
    }

    @Test
    void rowStillInsideItsLeaseIsNotTakenOver()
    {
        OaSignOnboardImportRow live = interruptedRow();
        live.setUpdateTime(new Date(3_000L));

        OaSignOnboardImportRow result = service.recoverInterruptedGeneration(
                batch(), live, new Date(2_000L));

        assertThat(result).isSameAs(live);
        verify(taskMapper, never()).selectCanonicalTaskBySourceEvent(any());
        verify(rowMapper, never()).failGeneration(anyLong(), any(), any(), any(), anyLong());
    }

    @Test
    void oldOwnerCannotFailTheRowAfterANewOwnerHasClaimedIt()
    {
        OaSignOnboardImportRow oldOwner = interruptedRow();
        OaSignOnboardImportRow newOwner = interruptedRow();
        newOwner.setGenerationRequestId("owner-new");
        newOwner.setVersion(10L);
        when(rowMapper.selectById(9L)).thenReturn(newOwner);

        service.fail(oldOwner, "GENERATE_FAILED");

        verify(rowMapper, never()).failGeneration(anyLong(), any(), any(), any(), anyLong());
    }

    @Test
    void knownFailureImmediatelyReleasesTheCurrentClaimOwner()
    {
        OaSignOnboardImportRow current = interruptedRow();
        when(rowMapper.selectById(9L)).thenReturn(current);
        when(rowMapper.failGeneration(eq(9L), eq("GENERATE_FAILED"), any(),
                eq("owner-old"), eq(8L))).thenReturn(1);

        service.fail(9L, "owner-old", 8L, "GENERATE_FAILED");

        verify(rowMapper).failGeneration(eq(9L), eq("GENERATE_FAILED"), any(),
                eq("owner-old"), eq(8L));
    }

    @Test
    void signatureFirstGenerationRejectsChangedFactsBeforeUsingStoredSignature()
    {
        OaSignOnboardImportRow row = interruptedRow();
        row.setDataRequestId(501L);
        OaSignOnboardDataRequest request = new OaSignOnboardDataRequest();
        request.setRequestId(501L);
        request.setRowId(row.getRowId());
        request.setEmployeeId(row.getEmployeeId());
        request.setStatus("COMPLETED");
        request.setSigningSequence("SIGNATURE_FIRST");
        when(dataRequestMapper.selectById(501L)).thenReturn(request);
        when(importService.submittedEmployeeConfirmationValues(request))
                .thenReturn(Map.of("currentAddress", "\u676d\u5dde\u5e02"));
        doThrow(new com.erp.common.core.exception.ServiceException(
                "\u5408\u540c\u4e8b\u5b9e\u5df2\u53d8\u5316，需要HR重新发起并由员工重新签名"))
                .when(importService).requireEmployeeConfirmationCurrent(
                        eq(request), eq(row), anyMap());

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                ReflectionTestUtils.invokeMethod(service, "signingContext", row))
                .isInstanceOf(com.erp.common.core.exception.ServiceException.class)
                .hasMessageContaining("\u5408\u540c\u4e8b\u5b9e\u5df2\u53d8\u5316");
        verify(importService).requireEmployeeConfirmationCurrent(
                eq(request), eq(row), anyMap());
    }

    @Test
    void directGenerationFailsClosedBeforeClaimWhenBoundTaskOwnerDoesNotMatch()
    {
        OaSignOnboardImportBatch batch = batch();
        batch.setVersion(4L);
        OaSignOnboardImportRow row = signatureFirstRow("READY_TO_GENERATE", 3L, null);
        row.setTaskId(801L);
        row.setPackageId(901L);
        OaSignOnboardGenerateRequest action = new OaSignOnboardGenerateRequest();
        action.setRequestId("owner-check-generate");
        action.setBatchVersion(4L);
        action.setRowIds(List.of(row.getRowId()));
        when(importService.requireHrBatch(3L, 41L)).thenReturn(batch);
        when(rowMapper.selectForGeneration(3L, List.of(row.getRowId())))
                .thenReturn(List.of(row));
        doThrow(new com.erp.common.core.exception.ServiceException(
                "导入行签约任务未分配给当前合同经办人"))
                .when(importService).requireCurrentHrTaskOwnership(eq(row), any());

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                service.generate(3L, action, 41L))
                .isInstanceOf(com.erp.common.core.exception.ServiceException.class)
                .hasMessageContaining("未分配给当前");
        verify(batchMapper, never()).claimGeneration(anyLong(), anyLong(), any());
        verify(rowMapper, never()).claimStagedGeneration(anyLong(), anyLong(), anyLong(),
                any(), anyLong());
    }

    @Test
    void directGenerationRejectsDuplicateExactSourceTasksBeforeClaim()
    {
        OaSignOnboardImportBatch batch = batch();
        batch.setVersion(4L);
        OaSignOnboardImportRow row = signatureFirstRow("READY_TO_GENERATE", 3L, null);
        row.setTaskId(801L);
        row.setPackageId(901L);
        OaSignOnboardGenerateRequest action = new OaSignOnboardGenerateRequest();
        action.setRequestId("duplicate-source-generate");
        action.setBatchVersion(4L);
        action.setRowIds(List.of(row.getRowId()));
        when(importService.requireHrBatch(3L, 41L)).thenReturn(batch);
        when(rowMapper.selectForGeneration(3L, List.of(row.getRowId())))
                .thenReturn(List.of(row));
        doThrow(new com.erp.common.core.exception.ServiceException(
                "同一Excel来源事件存在多个签约任务"))
                .when(importService).requireCurrentHrTaskOwnership(eq(row), any());

        assertThatThrownBy(() -> service.generate(3L, action, 41L))
                .isInstanceOf(com.erp.common.core.exception.ServiceException.class)
                .hasMessageContaining("多个签约任务");
        verify(batchMapper, never()).claimGeneration(anyLong(), anyLong(), any());
        verify(rowMapper, never()).claimGeneration(anyLong(), any(), anyLong());
        verify(rowMapper, never()).claimTaskOnlyGeneration(
                anyLong(), anyLong(), any(), anyLong());
    }

    @Test
    void reassignmentBeforeFinalWriteDoesNotMutatePackageCompleteOrFailTheRow()
    {
        OaSignOnboardImportBatch batch = batch();
        OaSignOnboardImportRow claimed = canonicalRecoveryRow(
                "GENERATING", 6L, "owner-race-request");
        OaSignTask exact = new OaSignTask();
        exact.setTaskId(198L);
        exact.setScenario("ONBOARD");
        exact.setEmployeeId(957L);
        exact.setShopDeptId(41L);
        exact.setSourceType(OaOnboardSignEventFactory.SOURCE_TYPE);
        exact.setSourceBusinessId("1876");
        exact.setSourceEventVersion("3");
        exact.setAssignedHrUserId(202L);
        when(rowMapper.selectById(1876L)).thenReturn(claimed);
        when(taskMapper.selectCanonicalTaskBySourceEvent(any())).thenReturn(exact);
        org.mockito.Mockito.reset(orchestrator);
        when(orchestrator.withLockedExcelImportTask(any(), any(), anyLong(), anyLong(), any()))
                .thenThrow(new com.erp.common.core.exception.ServiceException(
                        "Excel导入签约任务已改派，原经办人不能继续生成"));
        OaSignDraftDecision decision = new OaSignDraftDecision();
        OaSignOnboardContractSnapshot snapshot = signatureFirstSnapshot();
        Object signing = ReflectionTestUtils.invokeMethod(service, "signingContext", claimed);

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(service,
                "finishClaimedGeneration", batch, claimed, signing, snapshot,
                decision, 198L, false, 101L))
                .isInstanceOf(com.erp.common.core.exception.ServiceException.class)
                .hasMessageContaining("已改派");
        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(service,
                "failWithCurrentOwner", batch, 1876L, "owner-race-request",
                6L, "GENERATE_FAILED", 101L))
                .isInstanceOf(com.erp.common.core.exception.ServiceException.class)
                .hasMessageContaining("已改派");

        verify(packageService, never()).prepareSignatureFirstCandidateForSystem(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        verify(packageService, never()).prepareStagedSignatureFirstCandidateForSystem(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        verify(rowMapper, never()).completeGeneration(any(), any(), any(), any(), any());
        verify(rowMapper, never()).failGeneration(any(), any(), any(), any(), any());
    }

    @Test
    void generateCatchDoesNotCompensateClaimAfterOwnerReassignment()
    {
        OaSignOnboardImportBatch batch = new OaSignOnboardImportBatch();
        batch.setBatchId(134L);
        batch.setShopDeptId(101L);
        batch.setVersion(7L);
        OaSignOnboardImportRow ready = canonicalRecoveryRow(
                "READY_TO_GENERATE", 5L, null);
        ready.setTaskId(198L);
        OaSignOnboardImportRow claimed = canonicalRecoveryRow(
                "GENERATING", 6L, "owner-race-full-generate");
        claimed.setTaskId(198L);
        OaSignTask task = new OaSignTask();
        task.setTaskId(198L);
        task.setScenario("ONBOARD");
        task.setEmployeeId(957L);
        task.setShopDeptId(101L);
        task.setSourceType(OaOnboardSignEventFactory.SOURCE_TYPE);
        task.setSourceBusinessId("1876");
        task.setSourceEventVersion("3");
        task.setStatus("NEEDS_DATA");
        task.setAssignedHrUserId(101L);
        OaSignOnboardContractSnapshot snapshot = signatureFirstSnapshot();
        snapshot.setStudentStatus("NON_STUDENT");
        snapshot.setRetirementStatus("NOT_RETIRED");
        snapshot.setIncomeStartYearMonth("2026-08");
        SignCandidateUser candidate = new SignCandidateUser();
        candidate.setUserId(957L);
        SysLegalEntity entity = new SysLegalEntity();
        entity.setLegalEntityId(4L);
        entity.setVersion(2L);
        entity.setLegalEntityCode("LEGAL-4");
        entity.setLegalEntityName("示例文化有限公司");
        entity.setRegisteredAddress("杭州市测试路4号");
        entity.setUnifiedSocialCreditCode("91330000TEST04");
        entity.setLegalRepresentative("测试法人");
        OaCompanySealConfig seal = new OaCompanySealConfig();
        seal.setSealId(8L);
        seal.setLegalEntityId(4L);
        seal.setSealName("公章");
        seal.setSealImageUrl("/seal/8.png");
        seal.setSealImageHash("b".repeat(64));
        OaSignOnboardGenerateRequest action = new OaSignOnboardGenerateRequest();
        action.setRequestId("owner-race-full-generate");
        action.setBatchVersion(7L);
        action.setRowIds(List.of(1876L));

        when(importService.requireHrBatch(134L, 101L)).thenReturn(batch);
        when(rowMapper.selectForGeneration(134L, List.of(1876L)))
                .thenReturn(List.of(ready));
        when(batchMapper.claimGeneration(eq(134L), eq(7L), any(Date.class))).thenReturn(1);
        when(batchMapper.selectById(134L)).thenReturn(batch);
        when(importService.snapshot(any(OaSignOnboardImportRow.class))).thenReturn(snapshot);
        when(importService.requireCandidate(957L, 101L)).thenReturn(candidate);
        when(importService.profileFactsMatch(snapshot, candidate)).thenReturn(true);
        when(importService.identityFactsMatch(batch, snapshot, candidate)).thenReturn(true);
        when(importService.templateGateErrors(1000000014L, snapshot)).thenReturn(Set.of());
        when(companyService.currentMatchPolicyVersion()).thenReturn("COMPANY_MATCH_V1");
        when(companyService.requireContractReadyEntity(4L)).thenReturn(entity);
        when(companyService.requireContractReadySeal(8L, 4L)).thenReturn(seal);
        when(taskMapper.selectCanonicalTaskBySourceEvent(any())).thenReturn(task);
        when(taskMapper.selectOpenOnboardTaskByEmployeeId(957L)).thenReturn(task);
        when(taskMapper.selectOaSignTaskById(198L)).thenReturn(task);
        when(rowMapper.claimTaskOnlyGeneration(1876L, 198L,
                "owner-race-full-generate", 5L)).thenReturn(1);
        when(rowMapper.selectById(1876L)).thenReturn(claimed);
        int[] ownershipChecks = {0};
        org.mockito.Mockito.doAnswer(invocation -> {
            if (++ownershipChecks[0] == 4)
                throw new com.erp.common.core.exception.ServiceException(
                        "Excel导入签约任务已改派，原经办人不能继续生成");
            return null;
        }).when(importService).requireCurrentHrTaskOwnership(eq(ready), eq(batch));
        org.mockito.Mockito.doAnswer(invocation -> {
            if (++ownershipChecks[0] == 4)
                throw new com.erp.common.core.exception.ServiceException(
                        "Excel导入签约任务已改派，原经办人不能继续生成");
            return null;
        }).when(importService).requireCurrentHrTaskOwnership(eq(claimed), eq(batch));
        org.mockito.Mockito.reset(orchestrator);
        int[] taskLocks = {0};
        when(orchestrator.withLockedExcelImportTask(any(), any(), anyLong(), anyLong(), any()))
                .thenAnswer(invocation -> {
                    if (++taskLocks[0] >= 3)
                        throw new com.erp.common.core.exception.ServiceException(
                                "Excel导入签约任务已改派，原经办人不能继续生成");
                    @SuppressWarnings("unchecked")
                    java.util.function.Function<OaSignTask, Object> work =
                            invocation.getArgument(4, java.util.function.Function.class);
                    return work.apply(task);
                });

        assertThatThrownBy(() -> service.generate(134L, action, 101L))
                .isInstanceOf(com.erp.common.core.exception.ServiceException.class)
                .hasMessageContaining("已改派");

        verify(rowMapper).claimTaskOnlyGeneration(1876L, 198L,
                "owner-race-full-generate", 5L);
        verify(rowMapper, never()).failGeneration(any(), any(), any(), any(), any());
        verify(rowMapper, never()).completeGeneration(any(), any(), any(), any(), any());
    }

    @Test
    void signatureFirstGenerationCreatesDraftAndBindsOriginalSignatureSample()
    {
        OaSignOnboardImportBatch batch = batch();
        OaSignOnboardImportRow ready = signatureFirstRow("READY_TO_GENERATE", 3L, null);
        OaSignOnboardImportRow claimed = signatureFirstRow("GENERATING", 4L,
                "generation-request-1");
        OaSignOnboardContractSnapshot snapshot = signatureFirstSnapshot();
        SignCandidateUser candidate = new SignCandidateUser();
        candidate.setUserId(201L);

        byte[] signaturePng = new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47,
                0x0d, 0x0a, 0x1a, 0x0a, 0x01};
        String signatureHash = OaSignOnboardExcelParser.sha256(signaturePng);
        Date signatureTime = new Date(1_752_990_400_456L);
        OaSignOnboardDataRequest dataRequest = new OaSignOnboardDataRequest();
        dataRequest.setRequestId(501L);
        dataRequest.setRowId(9L);
        dataRequest.setEmployeeId(201L);
        dataRequest.setStatus("COMPLETED");
        dataRequest.setSigningSequence("SIGNATURE_FIRST");
        dataRequest.setSignatureRequestId("signature-request-501");
        dataRequest.setSignatureSampleBytes(signaturePng);
        dataRequest.setSignatureSampleHash(signatureHash);
        dataRequest.setSignatureSampleTime(signatureTime);

        SysLegalEntity entity = new SysLegalEntity();
        entity.setLegalEntityId(4L);
        entity.setVersion(2L);
        entity.setLegalEntityCode("LEGAL-4");
        entity.setLegalEntityName("示例文化有限公司");
        entity.setUnifiedSocialCreditCode("91330000TEST04");
        entity.setRegisteredAddress("杭州市测试路4号");
        entity.setLegalRepresentative("测试法人");
        OaCompanySealConfig seal = new OaCompanySealConfig();
        seal.setSealId(8L);
        seal.setLegalEntityId(4L);
        seal.setSealName("公章");
        seal.setSealImageUrl("/seal/8.png");
        seal.setSealImageHash("b".repeat(64));

        OaSignTask task = exactTask("READY_TO_SEND", 55L, 77L);
        task.setAssignedHrUserId(101L);
        OaSignPackage draft = frozenSignatureFirstPackage(task, signatureHash, signatureTime,
                false);
        OaSignPackage prepared = frozenSignatureFirstPackage(task, signatureHash, signatureTime,
                true);
        OaSignPlanVersion planVersion = new OaSignPlanVersion();
        planVersion.setVersionId(55L);
        planVersion.setVersionHash("plan-hash-55");
        HrSignBusinessEvent event = new HrSignBusinessEvent();
        OaSignDraftDecision decision = new OaSignDraftDecision();
        decision.setAction(OaSignDraftDecision.Action.CREATE_DRAFT);
        decision.setPlanVersionId(55L);

        when(importService.snapshot(any(OaSignOnboardImportRow.class))).thenReturn(snapshot);
        when(importService.requireCandidate(201L, 41L)).thenReturn(candidate);
        when(importService.profileFactsMatch(snapshot, candidate)).thenReturn(true);
        when(importService.identityFactsMatch(batch, snapshot, candidate)).thenReturn(true);
        when(importService.templateGateErrors(55L, snapshot)).thenReturn(Set.of());
        when(importService.submittedEmployeeConfirmationValues(dataRequest))
                .thenReturn(Map.of("currentAddress", "杭州市测试路1号"));
        when(companyService.currentMatchPolicyVersion()).thenReturn("COMPANY_MATCH_V1");
        when(companyService.requireContractReadyEntity(4L)).thenReturn(entity);
        when(companyService.requireContractReadySeal(8L, 4L)).thenReturn(seal);
        when(dataRequestMapper.selectById(501L)).thenReturn(dataRequest);
        when(rowMapper.claimGeneration(9L, "generation-request-1", 3L)).thenReturn(1);
        when(rowMapper.selectById(9L)).thenReturn(claimed);
        when(eventFactory.create(eq(batch), eq(claimed), eq(snapshot), eq(candidate),
                any(), eq("generation-request-1"))).thenReturn(event);
        when(onboardRule.decide(event)).thenReturn(decision);
        when(planVersionMapper.selectPlanVersionById(55L)).thenReturn(planVersion);
        when(orchestrator.orchestrate(event)).thenReturn(66L);
        when(taskMapper.selectOaSignTaskById(66L)).thenReturn(task);
        when(packageMapper.selectOaSignPackageById(77L)).thenReturn(draft, prepared);
        when(rowMapper.completeGeneration(9L, 66L, 77L,
                "generation-request-1", 4L)).thenReturn(1);

        OaSignOnboardGenerateResult.Item result = ReflectionTestUtils.invokeMethod(
                service, "generateOne", batch, ready, "generation-request-1", 101L);

        assertThat(result.getResult()).isEqualTo("GENERATED");
        assertThat(result.getTaskId()).isEqualTo(66L);
        assertThat(result.getPackageId()).isEqualTo(77L);
        assertThat(event.getAttributes())
                .containsEntry("signingSequence", "SIGNATURE_FIRST")
                .containsEntry("signatureDataRequestId", 501L);
        ArgumentCaptor<Long> dataRequestId = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<String> signatureRequestId = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<byte[]> sampleBytes = ArgumentCaptor.forClass(byte[].class);
        ArgumentCaptor<String> sampleHash = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Date> sampleTime = ArgumentCaptor.forClass(Date.class);
        verify(packageService).prepareSignatureFirstCandidateForSystem(
                eq(77L), eq(66L), eq(101L), dataRequestId.capture(),
                signatureRequestId.capture(), sampleBytes.capture(), sampleHash.capture(),
                sampleTime.capture(), eq("SP-77-V1"), eq(5L));
        assertThat(dataRequestId.getValue()).isEqualTo(501L);
        assertThat(signatureRequestId.getValue()).isEqualTo("signature-request-501");
        assertThat(sampleBytes.getValue()).containsExactly(signaturePng);
        assertThat(sampleHash.getValue()).isEqualTo(signatureHash);
        assertThat(sampleTime.getValue()).isEqualTo(signatureTime);
        verify(rowMapper).completeGeneration(9L, 66L, 77L,
                "generation-request-1", 4L);
    }

    @Test
    void stagedSignatureFirstGenerationReusesSamePackageAndOnlyPreparesFinalCandidate()
    {
        OaSignOnboardImportBatch batch = batch();
        OaSignOnboardImportRow ready = signatureFirstRow("READY_TO_GENERATE", 8L, null);
        ready.setTaskId(66L);
        ready.setPackageId(77L);
        OaSignOnboardImportRow claimed = signatureFirstRow("GENERATING", 9L,
                "generation-request-staged");
        claimed.setTaskId(66L); claimed.setPackageId(77L);
        OaSignOnboardContractSnapshot snapshot = signatureFirstSnapshot();
        SignCandidateUser candidate = new SignCandidateUser();
        candidate.setUserId(201L);
        byte[] signaturePng = new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47,
                0x0d, 0x0a, 0x1a, 0x0a, 0x02};
        String signatureHash = OaSignOnboardExcelParser.sha256(signaturePng);
        Date signatureTime = new Date(1_752_990_500_000L);
        OaSignOnboardDataRequest dataRequest = new OaSignOnboardDataRequest();
        dataRequest.setRequestId(501L); dataRequest.setRowId(9L);
        dataRequest.setEmployeeId(201L); dataRequest.setStatus("COMPLETED");
        dataRequest.setSigningSequence("SIGNATURE_FIRST");
        dataRequest.setSignatureRequestId("signature-request-staged");
        dataRequest.setSignatureSampleBytes(signaturePng);
        dataRequest.setSignatureSampleHash(signatureHash);
        dataRequest.setSignatureSampleTime(signatureTime);
        SysLegalEntity entity = new SysLegalEntity();
        entity.setLegalEntityId(4L); entity.setVersion(2L);
        entity.setLegalEntityCode("LEGAL-4");
        entity.setLegalEntityName("示例文化有限公司");
        entity.setUnifiedSocialCreditCode("91330000TEST04");
        entity.setRegisteredAddress("杭州市测试路4号");
        entity.setLegalRepresentative("测试法人");
        OaCompanySealConfig seal = new OaCompanySealConfig();
        seal.setSealId(8L); seal.setLegalEntityId(4L); seal.setSealName("公章");
        seal.setSealImageUrl("/seal/8.png"); seal.setSealImageHash("b".repeat(64));
        OaSignTask task = exactTask("PENDING_COMPANY", 55L, 77L);
        task.setAssignedHrUserId(101L);
        OaSignPackage staged = frozenSignatureFirstPackage(task, signatureHash,
                signatureTime, false);
        staged.setStatus(OaSignPackageStatus.PENDING_COMPANY);
        staged.setSignedTime(signatureTime);
        staged.setInitialSignedTime(signatureTime);
        staged.setSignatureSampleFileUrl("/signature/501.png");
        staged.setSignatureSampleHash(signatureHash);
        staged.setSignatureSampleTime(signatureTime);
        staged.setFinalConfirmationStatus("WAITING_COMPANY");
        OaSignPackage prepared = frozenSignatureFirstPackage(task, signatureHash,
                signatureTime, true);
        prepared.setStatus(OaSignPackageStatus.PENDING_COMPANY);
        prepared.setSignedTime(signatureTime);
        prepared.setInitialSignedTime(signatureTime);
        prepared.setFinalConfirmationStatus("PREPARED_NOT_SENT");
        OaSignPlanVersion planVersion = new OaSignPlanVersion();
        planVersion.setVersionId(55L); planVersion.setVersionHash("plan-hash-55");
        HrSignBusinessEvent event = new HrSignBusinessEvent();
        OaSignDraftDecision decision = new OaSignDraftDecision();
        decision.setAction(OaSignDraftDecision.Action.CREATE_DRAFT);
        decision.setPlanVersionId(55L);
        decision.setDraftPackage(prepared);

        when(importService.snapshot(any(OaSignOnboardImportRow.class))).thenReturn(snapshot);
        when(importService.requireCandidate(201L, 41L)).thenReturn(candidate);
        when(importService.profileFactsMatch(snapshot, candidate)).thenReturn(true);
        when(importService.identityFactsMatch(batch, snapshot, candidate)).thenReturn(true);
        when(importService.templateGateErrors(55L, snapshot)).thenReturn(Set.of());
        when(importService.submittedEmployeeConfirmationValues(dataRequest)).thenReturn(Map.of());
        when(companyService.currentMatchPolicyVersion()).thenReturn("COMPANY_MATCH_V1");
        when(companyService.requireContractReadyEntity(4L)).thenReturn(entity);
        when(companyService.requireContractReadySeal(8L, 4L)).thenReturn(seal);
        when(dataRequestMapper.selectById(501L)).thenReturn(dataRequest);
        when(taskMapper.selectCanonicalTaskBySourceEvent(any())).thenReturn(task);
        when(taskMapper.selectOaSignTaskById(66L)).thenReturn(task);
        when(packageMapper.selectOaSignPackageById(77L))
                .thenReturn(staged, staged, staged, prepared);
        when(rowMapper.claimStagedGeneration(9L, 66L, 77L,
                "generation-request-staged", 8L)).thenReturn(1);
        when(rowMapper.selectById(9L)).thenReturn(claimed);
        when(eventFactory.create(eq(batch), eq(claimed), eq(snapshot), eq(candidate),
                any(), eq("generation-request-staged"))).thenReturn(event);
        when(onboardRule.decide(event)).thenReturn(decision);
        when(planVersionMapper.selectPlanVersionById(55L)).thenReturn(planVersion);
        when(rowMapper.completeGeneration(9L, 66L, 77L,
                "generation-request-staged", 9L)).thenReturn(1);

        OaSignOnboardGenerateResult.Item result = ReflectionTestUtils.invokeMethod(
                service, "generateOne", batch, ready, "generation-request-staged", 101L);

        assertThat(result.getResult()).isEqualTo("GENERATED");
        verify(orchestrator, never()).orchestrate(any());
        verify(packageService).prepareStagedSignatureFirstCandidateForSystem(
                eq(77L), eq(66L), eq(101L), eq(prepared), eq(501L),
                eq("signature-request-staged"), eq(signaturePng), eq(signatureHash),
                eq(signatureTime), eq("SP-77-V1"), eq(5L));
        verify(rowMapper).completeGeneration(9L, 66L, 77L,
                "generation-request-staged", 9L);
    }

    @Test
    void publicStagedGenerationDoesNotRewriteAlreadyFrozenConfirmations()
    {
        OaSignOnboardImportBatch batch = batch();
        batch.setVersion(7L);
        OaSignOnboardImportBatch claimedBatch = batch();
        claimedBatch.setVersion(8L);
        OaSignOnboardImportRow ready = signatureFirstRow("READY_TO_GENERATE", 8L,
                "generation-request-staged");
        ready.setTaskId(66L); ready.setPackageId(77L);
        ready.setNoExternalContractConfirmed(Boolean.TRUE);
        OaSignOnboardImportRow refreshed = signatureFirstRow("READY_TO_GENERATE", 9L,
                "generation-request-staged");
        refreshed.setTaskId(66L); refreshed.setPackageId(77L);
        refreshed.setNoExternalContractConfirmed(Boolean.TRUE);
        OaSignOnboardImportRow claimed = signatureFirstRow("GENERATING", 10L,
                "generation-request-staged");
        claimed.setTaskId(66L); claimed.setPackageId(77L);
        claimed.setNoExternalContractConfirmed(Boolean.TRUE);
        OaSignOnboardContractSnapshot snapshot = signatureFirstSnapshot();
        SignCandidateUser candidate = new SignCandidateUser();
        candidate.setUserId(201L);
        byte[] signaturePng = new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47,
                0x0d, 0x0a, 0x1a, 0x0a, 0x03};
        String signatureHash = OaSignOnboardExcelParser.sha256(signaturePng);
        Date signatureTime = new Date(1_752_990_600_000L);
        OaSignOnboardDataRequest dataRequest = new OaSignOnboardDataRequest();
        dataRequest.setRequestId(501L); dataRequest.setRowId(9L);
        dataRequest.setEmployeeId(201L); dataRequest.setStatus("COMPLETED");
        dataRequest.setSigningSequence("SIGNATURE_FIRST");
        dataRequest.setSignatureRequestId("signature-request-staged");
        dataRequest.setSignatureSampleBytes(signaturePng);
        dataRequest.setSignatureSampleHash(signatureHash);
        dataRequest.setSignatureSampleTime(signatureTime);
        SysLegalEntity entity = new SysLegalEntity();
        entity.setLegalEntityId(4L); entity.setVersion(2L);
        entity.setLegalEntityCode("LEGAL-4");
        entity.setLegalEntityName("示例文化有限公司");
        entity.setUnifiedSocialCreditCode("91330000TEST04");
        entity.setRegisteredAddress("杭州市测试路4号");
        entity.setLegalRepresentative("测试法人");
        OaCompanySealConfig seal = new OaCompanySealConfig();
        seal.setSealId(8L); seal.setLegalEntityId(4L); seal.setSealName("公章");
        seal.setSealImageUrl("/seal/8.png"); seal.setSealImageHash("b".repeat(64));
        OaSignTask task = exactTask("PENDING_COMPANY", 55L, 77L);
        task.setAssignedHrUserId(101L);
        OaSignPackage staged = frozenSignatureFirstPackage(task, signatureHash,
                signatureTime, false);
        staged.setStatus(OaSignPackageStatus.PENDING_COMPANY);
        staged.setSignedTime(signatureTime);
        staged.setInitialSignedTime(signatureTime);
        staged.setSignatureSampleFileUrl("/signature/501.png");
        staged.setSignatureSampleHash(signatureHash);
        staged.setSignatureSampleTime(signatureTime);
        staged.setFinalConfirmationStatus("WAITING_COMPANY");
        OaSignPackage prepared = frozenSignatureFirstPackage(task, signatureHash,
                signatureTime, true);
        prepared.setStatus(OaSignPackageStatus.PENDING_COMPANY);
        prepared.setSignedTime(signatureTime);
        prepared.setInitialSignedTime(signatureTime);
        prepared.setFinalConfirmationStatus("PREPARED_NOT_SENT");
        OaSignPlanVersion planVersion = new OaSignPlanVersion();
        planVersion.setVersionId(55L); planVersion.setVersionHash("plan-hash-55");
        HrSignBusinessEvent event = new HrSignBusinessEvent();
        OaSignDraftDecision decision = new OaSignDraftDecision();
        decision.setAction(OaSignDraftDecision.Action.CREATE_DRAFT);
        decision.setPlanVersionId(55L);
        decision.setDraftPackage(prepared);

        when(importService.requireHrBatch(3L, 41L)).thenReturn(batch);
        when(rowMapper.selectForGeneration(3L, List.of(9L)))
                .thenReturn(List.of(ready), List.of(ready));
        when(batchMapper.claimGeneration(eq(3L), eq(7L), any(Date.class))).thenReturn(1);
        when(batchMapper.selectById(3L)).thenReturn(claimedBatch);
        when(importService.snapshot(any(OaSignOnboardImportRow.class))).thenReturn(snapshot);
        when(importService.json(any())).thenReturn("[]");
        when(importService.requireCandidate(201L, 41L)).thenReturn(candidate);
        when(importService.profileFactsMatch(snapshot, candidate)).thenReturn(true);
        when(importService.identityFactsMatch(claimedBatch, snapshot, candidate)).thenReturn(true);
        when(importService.templateGateErrors(55L, snapshot)).thenReturn(Set.of());
        when(importService.submittedEmployeeConfirmationValues(dataRequest)).thenReturn(Map.of());
        when(companyService.currentMatchPolicyVersion()).thenReturn("COMPANY_MATCH_V1");
        when(companyService.requireContractReadyEntity(4L)).thenReturn(entity);
        when(companyService.requireContractReadySeal(8L, 4L)).thenReturn(seal);
        when(dataRequestMapper.selectById(501L)).thenReturn(dataRequest);
        when(taskMapper.selectCanonicalTaskBySourceEvent(any())).thenReturn(task);
        when(taskMapper.selectOaSignTaskById(66L)).thenReturn(task);
        when(packageMapper.selectOaSignPackageById(77L))
                .thenReturn(staged, staged, staged, staged, prepared);
        when(rowMapper.selectById(9L)).thenReturn(ready, refreshed, claimed);
        when(rowMapper.updateEditableWithVersion(any())).thenReturn(1);
        when(rowMapper.claimStagedGeneration(9L, 66L, 77L,
                "generation-request-staged", 9L)).thenReturn(1);
        when(eventFactory.create(eq(claimedBatch), eq(claimed), eq(snapshot), eq(candidate),
                any(), eq("generation-request-staged"))).thenReturn(event);
        when(onboardRule.decide(event)).thenReturn(decision);
        when(planVersionMapper.selectPlanVersionById(55L)).thenReturn(planVersion);
        when(rowMapper.completeGeneration(9L, 66L, 77L,
                "generation-request-staged", 10L)).thenReturn(1);

        OaSignOnboardGenerateRequest action = new OaSignOnboardGenerateRequest();
        action.setRequestId("generation-request-staged");
        action.setBatchVersion(7L);
        action.setRowIds(List.of(9L));
        action.setNoExternalContractConfirmed(Boolean.TRUE);
        OaSignOnboardGenerateResult result = service.generate(3L, action, 41L);

        assertThat(result.getGeneratedCount()).isEqualTo(1);
        assertThat(result.getItems()).singleElement().satisfies(item -> {
            assertThat(item.getResult()).isEqualTo("GENERATED");
            assertThat(item.getTaskId()).isEqualTo(66L);
            assertThat(item.getPackageId()).isEqualTo(77L);
        });
        verify(rowMapper, never()).applyGenerationConfirmations(anyLong(), any(), any(),
                any(), any(), any(), anyLong());
        verify(rowMapper).claimStagedGeneration(9L, 66L, 77L,
                "generation-request-staged", 9L);
        verify(packageService).prepareStagedSignatureFirstCandidateForSystem(
                eq(77L), eq(66L), eq(101L), eq(prepared), eq(501L),
                eq("signature-request-staged"), eq(signaturePng), eq(signatureHash),
                eq(signatureTime), eq("SP-77-V1"), eq(5L));
        verify(orchestrator, never()).orchestrate(any());
    }

    @Test
    void publicStagedGenerationPersistsNewConfirmationBeforeClaimingUpdatedVersion()
    {
        OaSignOnboardImportBatch batch = batch();
        batch.setVersion(7L);
        OaSignOnboardImportBatch claimedBatch = batch();
        claimedBatch.setVersion(8L);

        OaSignOnboardImportRow ready = signatureFirstRow("READY_TO_GENERATE", 8L,
                "generation-request-staged-confirmation");
        ready.setTaskId(66L); ready.setPackageId(77L);
        ready.setNoExternalContractConfirmed(Boolean.FALSE);
        OaSignOnboardImportRow confirmed = signatureFirstRow("READY_TO_GENERATE", 9L,
                "generation-request-staged-confirmation");
        confirmed.setTaskId(66L);
        confirmed.setPackageId(77L);
        confirmed.setNoExternalContractConfirmed(Boolean.TRUE);
        OaSignOnboardImportRow refreshed = signatureFirstRow("READY_TO_GENERATE", 10L,
                "generation-request-staged-confirmation");
        refreshed.setTaskId(66L);
        refreshed.setPackageId(77L);
        refreshed.setNoExternalContractConfirmed(Boolean.TRUE);
        OaSignOnboardImportRow claimed = signatureFirstRow("GENERATING", 11L,
                "generation-request-staged-confirmation");
        claimed.setTaskId(66L);
        claimed.setPackageId(77L);
        claimed.setNoExternalContractConfirmed(Boolean.TRUE);

        OaSignOnboardContractSnapshot snapshot = signatureFirstSnapshot();
        SignCandidateUser candidate = new SignCandidateUser();
        candidate.setUserId(201L);
        byte[] signaturePng = new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47,
                0x0d, 0x0a, 0x1a, 0x0a, 0x04};
        String signatureHash = OaSignOnboardExcelParser.sha256(signaturePng);
        Date signatureTime = new Date(1_752_990_700_000L);
        OaSignOnboardDataRequest dataRequest = new OaSignOnboardDataRequest();
        dataRequest.setRequestId(501L);
        dataRequest.setRowId(9L);
        dataRequest.setEmployeeId(201L);
        dataRequest.setStatus("COMPLETED");
        dataRequest.setSigningSequence("SIGNATURE_FIRST");
        dataRequest.setSignatureRequestId("signature-request-staged-confirmation");
        dataRequest.setSignatureSampleBytes(signaturePng);
        dataRequest.setSignatureSampleHash(signatureHash);
        dataRequest.setSignatureSampleTime(signatureTime);

        SysLegalEntity entity = new SysLegalEntity();
        entity.setLegalEntityId(4L);
        entity.setVersion(2L);
        entity.setLegalEntityCode("LEGAL-4");
        entity.setLegalEntityName("示例文化有限公司");
        entity.setUnifiedSocialCreditCode("91330000TEST04");
        entity.setRegisteredAddress("杭州市测试路4号");
        entity.setLegalRepresentative("测试法人");
        OaCompanySealConfig seal = new OaCompanySealConfig();
        seal.setSealId(8L);
        seal.setLegalEntityId(4L);
        seal.setSealName("公章");
        seal.setSealImageUrl("/seal/8.png");
        seal.setSealImageHash("b".repeat(64));

        OaSignTask task = exactTask("PENDING_COMPANY", 55L, 77L);
        task.setAssignedHrUserId(101L);
        OaSignPackage staged = frozenSignatureFirstPackage(task, signatureHash,
                signatureTime, false);
        staged.setStatus(OaSignPackageStatus.PENDING_COMPANY);
        staged.setSignedTime(signatureTime);
        staged.setInitialSignedTime(signatureTime);
        staged.setSignatureSampleFileUrl("/signature/501.png");
        staged.setSignatureSampleHash(signatureHash);
        staged.setSignatureSampleTime(signatureTime);
        staged.setFinalConfirmationStatus("WAITING_COMPANY");
        OaSignPackage prepared = frozenSignatureFirstPackage(task, signatureHash,
                signatureTime, true);
        prepared.setStatus(OaSignPackageStatus.PENDING_COMPANY);
        prepared.setSignedTime(signatureTime);
        prepared.setInitialSignedTime(signatureTime);
        prepared.setFinalConfirmationStatus("PREPARED_NOT_SENT");
        OaSignPlanVersion planVersion = new OaSignPlanVersion();
        planVersion.setVersionId(55L); planVersion.setVersionHash("plan-hash-55");
        HrSignBusinessEvent event = new HrSignBusinessEvent();
        OaSignDraftDecision decision = new OaSignDraftDecision();
        decision.setAction(OaSignDraftDecision.Action.CREATE_DRAFT);
        decision.setPlanVersionId(55L);
        decision.setDraftPackage(prepared);

        when(importService.requireHrBatch(3L, 41L)).thenReturn(batch);
        when(rowMapper.selectForGeneration(3L, List.of(9L)))
                .thenReturn(List.of(ready), List.of(ready));
        when(batchMapper.claimGeneration(eq(3L), eq(7L), any(Date.class))).thenReturn(1);
        when(batchMapper.selectById(3L)).thenReturn(claimedBatch);
        when(importService.snapshot(any(OaSignOnboardImportRow.class))).thenReturn(snapshot);
        when(importService.json(any())).thenReturn("[]");
        when(importService.requireCandidate(201L, 41L)).thenReturn(candidate);
        when(importService.profileFactsMatch(snapshot, candidate)).thenReturn(true);
        when(importService.identityFactsMatch(claimedBatch, snapshot, candidate)).thenReturn(true);
        when(importService.templateGateErrors(55L, snapshot)).thenReturn(Set.of());
        when(importService.submittedEmployeeConfirmationValues(dataRequest)).thenReturn(Map.of());
        when(companyService.currentMatchPolicyVersion()).thenReturn("COMPANY_MATCH_V1");
        when(companyService.requireContractReadyEntity(4L)).thenReturn(entity);
        when(companyService.requireContractReadySeal(8L, 4L)).thenReturn(seal);
        when(dataRequestMapper.selectById(501L)).thenReturn(dataRequest);
        when(taskMapper.selectCanonicalTaskBySourceEvent(any())).thenReturn(task);
        when(taskMapper.selectOaSignTaskById(66L)).thenReturn(task);
        when(packageMapper.selectOaSignPackageById(77L))
                .thenReturn(staged, staged, staged, staged, prepared);
        when(rowMapper.applyGenerationConfirmations(9L, Boolean.TRUE,
                null, null, null, null, 8L)).thenReturn(1);
        when(rowMapper.selectById(9L)).thenReturn(confirmed, confirmed, refreshed, claimed);
        when(rowMapper.updateEditableWithVersion(any())).thenReturn(1);
        when(rowMapper.claimStagedGeneration(9L, 66L, 77L,
                "generation-request-staged-confirmation", 10L)).thenReturn(1);
        when(eventFactory.create(eq(claimedBatch), eq(claimed), eq(snapshot), eq(candidate),
                any(), eq("generation-request-staged-confirmation"))).thenReturn(event);
        when(onboardRule.decide(event)).thenReturn(decision);
        when(planVersionMapper.selectPlanVersionById(55L)).thenReturn(planVersion);
        when(rowMapper.completeGeneration(9L, 66L, 77L,
                "generation-request-staged-confirmation", 11L)).thenReturn(1);

        OaSignOnboardGenerateRequest action = new OaSignOnboardGenerateRequest();
        action.setRequestId("generation-request-staged-confirmation");
        action.setBatchVersion(7L);
        action.setRowIds(List.of(9L));
        action.setNoExternalContractConfirmed(Boolean.TRUE);

        OaSignOnboardGenerateResult result = service.generate(3L, action, 41L);

        assertThat(result.getGeneratedCount()).isEqualTo(1);
        assertThat(result.getItems()).singleElement().satisfies(item -> {
            assertThat(item.getResult()).isEqualTo("GENERATED");
            assertThat(item.getTaskId()).isEqualTo(66L);
            assertThat(item.getPackageId()).isEqualTo(77L);
        });
        verify(rowMapper).applyGenerationConfirmations(9L, Boolean.TRUE,
                null, null, null, null, 8L);
        ArgumentCaptor<OaSignOnboardImportRow> editable =
                ArgumentCaptor.forClass(OaSignOnboardImportRow.class);
        verify(rowMapper).updateEditableWithVersion(editable.capture());
        assertThat(editable.getValue().getVersion()).isEqualTo(9L);
        assertThat(editable.getValue().getNoExternalContractConfirmed()).isTrue();
        verify(rowMapper).claimStagedGeneration(9L, 66L, 77L,
                "generation-request-staged-confirmation", 10L);
        verify(rowMapper).completeGeneration(9L, 66L, 77L,
                "generation-request-staged-confirmation", 11L);
        verify(orchestrator, never()).orchestrate(any());
    }

    @Test
    void unboundReadyRowContinuesCanonicalTask198WithoutCreatingAnotherBinding()
    {
        assertCanonicalTask198Recovery(false, false);
    }

    @Test
    void migratedTaskOnlyNeedsDataRowContinuesCanonicalTask198()
    {
        assertCanonicalTask198Recovery(true, false);
    }

    @Test
    void migratedTaskOnlyRowBackfillsPackageFromReadyCanonicalTask198()
    {
        assertCanonicalTask198Recovery(true, true);
    }

    @Test
    void taskOnlyPackageBackfillRejectsPlanOrNonDraftPackageDrift()
    {
        OaSignOnboardImportBatch batch = new OaSignOnboardImportBatch();
        batch.setBatchId(134L);
        batch.setShopDeptId(101L);
        OaSignOnboardImportRow claimed = canonicalRecoveryRow(
                "GENERATING", 6L, "repair-employee-957");
        claimed.setTaskId(198L);
        OaSignTask task = new OaSignTask();
        task.setTaskId(198L);
        task.setScenario("ONBOARD");
        task.setEmployeeId(957L);
        task.setShopDeptId(101L);
        task.setSourceType(OaOnboardSignEventFactory.SOURCE_TYPE);
        task.setSourceBusinessId("1876");
        task.setSourceEventVersion("3");
        task.setStatus("READY_TO_SEND");
        task.setAssignedHrUserId(101L);
        task.setPlanVersionId(999L);
        task.setPackageId(7001L);
        when(taskMapper.selectOaSignTaskById(198L)).thenReturn(task);
        OaSignOnboardContractSnapshot snapshot = signatureFirstSnapshot();
        Object signing = ReflectionTestUtils.invokeMethod(service, "signingContext", claimed);

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(service,
                "finishClaimedGeneration", batch, claimed, signing, snapshot,
                new OaSignDraftDecision(), 198L, false, 101L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("未生成到待发送状态");

        task.setPlanVersionId(1000000014L);
        OaSignPackage nonDraft = new OaSignPackage();
        nonDraft.setPackageId(7001L);
        nonDraft.setTaskId(198L);
        nonDraft.setEmployeeId(957L);
        nonDraft.setShopDeptId(101L);
        nonDraft.setPlanVersionId(1000000014L);
        nonDraft.setStatus(OaSignPackageStatus.PENDING_SIGN);
        nonDraft.setSigningSequence("COMPANY_FIRST");
        nonDraft.setLegalEntityIdSnapshot(4L);
        nonDraft.setLegalEntityNameSnapshot("示例文化有限公司");
        nonDraft.setLegalEntityAddressSnapshot("杭州市测试路4号");
        nonDraft.setSealIdSnapshot(8L);
        nonDraft.setSealImageHashSnapshot("b".repeat(64));
        when(packageMapper.selectOaSignPackageById(7001L)).thenReturn(nonDraft);

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(service,
                "finishClaimedGeneration", batch, claimed, signing, snapshot,
                new OaSignDraftDecision(), 198L, false, 101L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("草稿状态已变化");
        verify(rowMapper, never()).completeGeneration(any(), any(), any(), any(), any());
    }

    private void assertCanonicalTask198Recovery(boolean taskOnlyBinding,
            boolean alreadyPackaged)
    {
        OaSignOnboardImportBatch batch = new OaSignOnboardImportBatch();
        batch.setBatchId(134L);
        batch.setShopDeptId(101L);
        OaSignOnboardImportRow ready = canonicalRecoveryRow("READY_TO_GENERATE", 5L, null);
        OaSignOnboardImportRow claimed = canonicalRecoveryRow("GENERATING", 6L,
                "repair-employee-957");
        if (taskOnlyBinding)
        {
            ready.setTaskId(198L);
            claimed.setTaskId(198L);
        }
        OaSignOnboardContractSnapshot snapshot = signatureFirstSnapshot();
        snapshot.setStudentStatus("NON_STUDENT");
        snapshot.setRetirementStatus("NOT_RETIRED");
        snapshot.setIncomeStartYearMonth("2026-08");
        SignCandidateUser candidate = new SignCandidateUser();
        candidate.setUserId(957L);

        SysLegalEntity entity = new SysLegalEntity();
        entity.setLegalEntityId(4L);
        entity.setVersion(2L);
        entity.setLegalEntityCode("LEGAL-4");
        entity.setLegalEntityName("示例文化有限公司");
        entity.setUnifiedSocialCreditCode("91330000TEST04");
        entity.setRegisteredAddress("杭州市测试路4号");
        entity.setLegalRepresentative("测试法人");
        OaCompanySealConfig seal = new OaCompanySealConfig();
        seal.setSealId(8L);
        seal.setLegalEntityId(4L);
        seal.setSealName("公章");
        seal.setSealImageUrl("/seal/8.png");
        seal.setSealImageHash("b".repeat(64));

        OaSignTask task198 = new OaSignTask();
        task198.setTaskId(198L);
        task198.setScenario("ONBOARD");
        task198.setEmployeeId(957L);
        task198.setShopDeptId(101L);
        task198.setSourceType(OaOnboardSignEventFactory.SOURCE_TYPE);
        task198.setSourceBusinessId("1876");
        task198.setSourceEventVersion("3");
        task198.setStatus(alreadyPackaged ? "READY_TO_SEND" : "NEEDS_DATA");
        task198.setAssignedHrUserId(101L);
        if (alreadyPackaged) task198.setPackageId(7001L);
        OaSignPackage frozen = new OaSignPackage();
        frozen.setPackageId(7001L);
        frozen.setTaskId(198L);
        frozen.setEmployeeId(957L);
        frozen.setShopDeptId(101L);
        frozen.setPlanVersionId(1000000014L);
        frozen.setStatus(OaSignPackageStatus.DRAFT);
        frozen.setSigningSequence("COMPANY_FIRST");
        frozen.setLegalEntityIdSnapshot(4L);
        frozen.setLegalEntityNameSnapshot("示例文化有限公司");
        frozen.setLegalEntityAddressSnapshot("杭州市测试路4号");
        frozen.setSealIdSnapshot(8L);
        frozen.setSealImageHashSnapshot("b".repeat(64));
        frozen.setStudentStatusSnapshot("NON_STUDENT");
        frozen.setRetirementStatusSnapshot("NOT_RETIRED");
        frozen.setIncomeStartYearMonth("2026-08");

        OaSignPlanVersion version = new OaSignPlanVersion();
        version.setVersionId(1000000014L);
        version.setVersionHash("plan-hash-1000000014");
        HrSignBusinessEvent event = new HrSignBusinessEvent();
        OaSignDraftDecision decision = new OaSignDraftDecision();
        decision.setAction(OaSignDraftDecision.Action.CREATE_DRAFT);
        decision.setPlanVersionId(1000000014L);

        when(importService.snapshot(any(OaSignOnboardImportRow.class))).thenReturn(snapshot);
        when(importService.requireCandidate(957L, 101L)).thenReturn(candidate);
        when(importService.profileFactsMatch(snapshot, candidate)).thenReturn(true);
        when(importService.identityFactsMatch(batch, snapshot, candidate)).thenReturn(true);
        when(importService.templateGateErrors(1000000014L, snapshot)).thenReturn(Set.of());
        when(companyService.currentMatchPolicyVersion()).thenReturn("COMPANY_MATCH_V1");
        when(companyService.requireContractReadyEntity(4L)).thenReturn(entity);
        when(companyService.requireContractReadySeal(8L, 4L)).thenReturn(seal);
        when(taskMapper.selectCanonicalTaskBySourceEvent(any())).thenReturn(task198);
        when(taskMapper.selectOpenOnboardTaskByEmployeeId(957L)).thenReturn(task198);
        if (taskOnlyBinding)
        {
            when(rowMapper.claimTaskOnlyGeneration(1876L, 198L,
                    "repair-employee-957", 5L)).thenReturn(1);
        }
        else
        {
            when(rowMapper.claimGeneration(1876L, "repair-employee-957", 5L)).thenReturn(1);
        }
        when(rowMapper.selectById(1876L)).thenReturn(claimed);
        when(eventFactory.create(eq(batch), eq(claimed), eq(snapshot), eq(candidate),
                any(), eq("repair-employee-957"))).thenReturn(event);
        when(onboardRule.decide(event)).thenReturn(decision);
        when(planVersionMapper.selectPlanVersionById(1000000014L)).thenReturn(version);
        when(orchestrator.orchestrate(event)).thenAnswer(invocation -> {
            task198.setStatus("READY_TO_SEND");
            task198.setPlanVersionId(1000000014L);
            task198.setPackageId(7001L);
            return 198L;
        });
        when(taskMapper.selectOaSignTaskById(198L)).thenReturn(task198);
        when(packageMapper.selectOaSignPackageById(7001L)).thenReturn(frozen);
        when(rowMapper.completeGeneration(1876L, 198L, 7001L,
                "repair-employee-957", 6L)).thenReturn(1);

        OaSignOnboardGenerateResult.Item result = ReflectionTestUtils.invokeMethod(
                service, "generateOne", batch, ready, "repair-employee-957", 101L);

        assertThat(result.getResult()).isEqualTo("GENERATED");
        assertThat(result.getTaskId()).isEqualTo(198L);
        assertThat(result.getPackageId()).isEqualTo(7001L);
        verify(orchestrator).orchestrate(event);
        verify(rowMapper).completeGeneration(1876L, 198L, 7001L,
                "repair-employee-957", 6L);
        if (taskOnlyBinding)
        {
            verify(rowMapper).claimTaskOnlyGeneration(1876L, 198L,
                    "repair-employee-957", 5L);
            verify(rowMapper, never()).claimGeneration(anyLong(), any(), anyLong());
        }
        else
        {
            verify(rowMapper).claimGeneration(1876L, "repair-employee-957", 5L);
            verify(rowMapper, never()).claimTaskOnlyGeneration(
                    anyLong(), anyLong(), any(), anyLong());
        }
        verify(rowMapper, never()).updateEditableWithVersion(any());
        ArgumentCaptor<OaSignTask> canonicalQuery =
                ArgumentCaptor.forClass(OaSignTask.class);
        verify(taskMapper, times(3)).selectCanonicalTaskBySourceEvent(
                canonicalQuery.capture());
        assertThat(canonicalQuery.getAllValues()).allSatisfy(query -> {
            assertThat(query.getScenario()).isEqualTo("ONBOARD");
            assertThat(query.getEmployeeId()).isEqualTo(957L);
            assertThat(query.getSourceType()).isEqualTo(OaOnboardSignEventFactory.SOURCE_TYPE);
            assertThat(query.getSourceBusinessId()).isEqualTo("1876");
            assertThat(query.getSourceEventVersion()).isEqualTo("3");
        });
    }

    @Test
    void changedCompanyMatchingPolicyForcesRepreviewBeforeGeneration()
    {
        OaSignOnboardImportRow row = interruptedRow();
        row.setCompanyMatchPolicyVersion("COMPANY_MATCH_V1_old");
        OaSignOnboardContractSnapshot snapshot = new OaSignOnboardContractSnapshot();
        snapshot.setSocialTypeCode("SOCIAL_INSURED");
        snapshot.setSalaryVersion("B");
        when(companyService.currentMatchPolicyVersion())
                .thenReturn("COMPANY_MATCH_V1_current");

        Set<String> errors = ReflectionTestUtils.invokeMethod(service,
                "companyGenerationErrors", row, snapshot);

        assertThat(errors).containsExactly("COMPANY_MATCH_POLICY_CHANGED_REPREVIEW");
        verify(companyService, never()).requireContractReadyEntity(anyLong());
    }

    private OaSignOnboardImportBatch batch()
    {
        OaSignOnboardImportBatch batch = new OaSignOnboardImportBatch();
        batch.setBatchId(3L);
        batch.setShopDeptId(41L);
        return batch;
    }

    private OaSignOnboardImportRow interruptedRow()
    {
        OaSignOnboardImportRow row = new OaSignOnboardImportRow();
        row.setRowId(9L);
        row.setBatchId(3L);
        row.setEmployeeId(201L);
        row.setPlanVersionId(55L);
        row.setStatus("GENERATING");
        row.setErrorCodesJson("[]");
        row.setGenerationRequestId("owner-old");
        row.setSourceEventVersion(7L);
        row.setVersion(8L);
        row.setUpdateTime(new Date(1_000L));
        return row;
    }

    private OaSignTask exactTask(String status, Long planVersionId, Long packageId)
    {
        OaSignTask task = new OaSignTask();
        task.setTaskId(66L);
        task.setScenario("ONBOARD");
        task.setEmployeeId(201L);
        task.setShopDeptId(41L);
        task.setSourceType(OaOnboardSignEventFactory.SOURCE_TYPE);
        task.setSourceBusinessId("9");
        task.setSourceEventVersion("7");
        task.setStatus(status);
        task.setPlanVersionId(planVersionId);
        task.setPackageId(packageId);
        return task;
    }

    private OaSignPackage packageFor(OaSignTask task, Long planVersionId, Long employeeId)
    {
        OaSignPackage signPackage = new OaSignPackage();
        signPackage.setPackageId(task.getPackageId());
        signPackage.setTaskId(task.getTaskId());
        signPackage.setEmployeeId(employeeId);
        signPackage.setShopDeptId(41L);
        signPackage.setPlanVersionId(planVersionId);
        signPackage.setStatus(OaSignPackageStatus.DRAFT);
        return signPackage;
    }

    private OaSignOnboardImportRow signatureFirstRow(String status, Long version,
            String generationRequestId)
    {
        OaSignOnboardImportRow row = new OaSignOnboardImportRow();
        row.setRowId(9L);
        row.setBatchId(3L);
        row.setEmployeeId(201L);
        row.setPlanVersionId(55L);
        row.setPlanVersionHash("plan-hash-55");
        row.setDataRequestId(501L);
        row.setStatus(status);
        row.setErrorCodesJson("[]");
        row.setMissingFieldsJson("[]");
        row.setSourceEventVersion(7L);
        row.setCompanyMatchPolicyVersion("COMPANY_MATCH_V1");
        row.setMatchedLegalEntityId(4L);
        row.setCompanyMasterVersion(2L);
        row.setRecommendedSealId(8L);
        row.setGenerationRequestId(generationRequestId);
        row.setVersion(version);
        return row;
    }

    private OaSignOnboardImportRow canonicalRecoveryRow(String status, Long version,
            String generationRequestId)
    {
        OaSignOnboardImportRow row = new OaSignOnboardImportRow();
        row.setRowId(1876L);
        row.setBatchId(134L);
        row.setEmployeeId(957L);
        row.setPlanVersionId(1000000014L);
        row.setPlanVersionHash("plan-hash-1000000014");
        row.setStatus(status);
        row.setErrorCodesJson("[]");
        row.setMissingFieldsJson("[]");
        row.setSourceEventVersion(3L);
        row.setCompanyMatchPolicyVersion("COMPANY_MATCH_V1");
        row.setMatchedLegalEntityId(4L);
        row.setCompanyMasterVersion(2L);
        row.setRecommendedSealId(8L);
        row.setGenerationRequestId(generationRequestId);
        row.setVersion(version);
        return row;
    }

    private OaSignOnboardContractSnapshot signatureFirstSnapshot()
    {
        OaSignOnboardContractSnapshot snapshot = new OaSignOnboardContractSnapshot();
        snapshot.setCurrentAddress("杭州市测试路1号");
        snapshot.setSocialTypeCode("SOCIAL_INSURED");
        snapshot.setSalaryVersion("B");
        snapshot.setMatchedLegalEntityId(4L);
        snapshot.setMatchedLegalEntityCode("LEGAL-4");
        snapshot.setMatchedLegalEntityName("示例文化有限公司");
        snapshot.setMatchedUnifiedSocialCreditCode("91330000TEST04");
        snapshot.setMatchedRegisteredAddress("杭州市测试路4号");
        snapshot.setMatchedLegalRepresentative("测试法人");
        snapshot.setMatchedSealId(8L);
        snapshot.setMatchedSealName("公章");
        snapshot.setMatchedSealImageUrl("/seal/8.png");
        snapshot.setMatchedSealImageHash("b".repeat(64));
        return snapshot;
    }

    private OaSignPackage frozenSignatureFirstPackage(OaSignTask task,
            String signatureHash, Date signatureTime, boolean prepared)
    {
        OaSignPackage signPackage = packageFor(task, 55L, 201L);
        signPackage.setSigningSequence("SIGNATURE_FIRST");
        signPackage.setLegalEntityIdSnapshot(4L);
        signPackage.setLegalEntityNameSnapshot("示例文化有限公司");
        signPackage.setLegalEntityAddressSnapshot("杭州市测试路4号");
        signPackage.setSealIdSnapshot(8L);
        signPackage.setSealImageHashSnapshot("b".repeat(64));
        signPackage.setDocumentVersion("SP-77-V1");
        signPackage.setVersion(5L);
        if (prepared)
        {
            signPackage.setSignatureSampleFileUrl("/signature/501.png");
            signPackage.setSignatureSampleHash(signatureHash);
            signPackage.setSignatureSampleTime(signatureTime);
            signPackage.setFinalDocumentVersion("SP-77-F1");
            signPackage.setFinalDocumentRootHash("c".repeat(64));
            signPackage.setFinalGeneratedTime(new Date(signatureTime.getTime() + 1_000L));
        }
        return signPackage;
    }
}
