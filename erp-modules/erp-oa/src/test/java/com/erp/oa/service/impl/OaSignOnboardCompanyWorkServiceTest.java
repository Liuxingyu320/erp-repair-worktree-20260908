package com.erp.oa.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Date;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import com.erp.common.core.context.SecurityContextHolder;
import com.erp.common.core.exception.ServiceException;
import com.erp.oa.domain.OaCompanySealConfig;
import com.erp.oa.domain.OaSignOnboardDataRequest;
import com.erp.oa.domain.OaSignOnboardImportBatch;
import com.erp.oa.domain.OaSignOnboardImportRow;
import com.erp.oa.domain.dto.OaSignOnboardCompanyWorkRequest;
import com.erp.oa.domain.dto.OaSignOnboardGenerateRequest;
import com.erp.oa.domain.vo.OaSignCompanyOptions;
import com.erp.oa.domain.vo.OaSignOnboardCompanyWorkResult;
import com.erp.oa.domain.vo.OaSignOnboardCompanyWorkView;
import com.erp.oa.domain.vo.OaSignOnboardGenerateResult;
import com.erp.oa.domain.vo.OaSignOnboardImportBatchView;
import com.erp.oa.domain.vo.OaSignOnboardImportRowView;
import com.erp.oa.mapper.OaSignOnboardDataRequestMapper;
import com.erp.oa.mapper.OaDeptScopeMapper;
import com.erp.oa.mapper.OaSignOnboardImportBatchMapper;
import com.erp.oa.mapper.OaSignOnboardImportRowMapper;
import com.erp.system.api.domain.SysLegalEntity;
import com.fasterxml.jackson.databind.ObjectMapper;

@DisplayName("签名优先入职批量选公司盖章工作区")
class OaSignOnboardCompanyWorkServiceTest
{
    private static final Long SCOPE_ID = 1171L;
    private OaSignOnboardImportService importService;
    private OaSignOnboardGenerationService generationService;
    private OaSignOnboardImportBatchMapper batchMapper;
    private OaSignOnboardImportRowMapper rowMapper;
    private OaSignOnboardDataRequestMapper dataRequestMapper;
    private OaSignCompanyService companyService;
    private OaDeptScopeMapper deptScopeMapper;
    private OaSignHrAccessService hrAccessService;
    private OaSignScopeService signScopeService;
    private OaSignOnboardCompanyWorkService service;
    private OaSignOnboardGenerationRecoveryService recoveryService;

    @BeforeEach
    void setUp()
    {
        importService = mock(OaSignOnboardImportService.class);
        generationService = mock(OaSignOnboardGenerationService.class);
        when(generationService.withCurrentOwnerTaskLock(any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    java.util.function.Supplier<Object> work =
                            invocation.getArgument(3, java.util.function.Supplier.class);
                    return work.get();
                });
        batchMapper = mock(OaSignOnboardImportBatchMapper.class);
        rowMapper = mock(OaSignOnboardImportRowMapper.class);
        dataRequestMapper = mock(OaSignOnboardDataRequestMapper.class);
        companyService = mock(OaSignCompanyService.class);
        deptScopeMapper = mock(OaDeptScopeMapper.class);
        hrAccessService = mock(OaSignHrAccessService.class);
        signScopeService = mock(OaSignScopeService.class);
        service = new OaSignOnboardCompanyWorkService(importService,
                generationService,
                batchMapper, rowMapper, dataRequestMapper,
                companyService, deptScopeMapper,
                signScopeService, hrAccessService,
                mock(PlatformTransactionManager.class));
        recoveryService = new OaSignOnboardGenerationRecoveryService(importService,
                generationService, batchMapper, rowMapper);
        SecurityContextHolder.setUserId("101");
        when(hrAccessService.currentTaskOwnerFilter()).thenReturn(101L);
    }

    @AfterEach
    void tearDown()
    {
        SecurityContextHolder.remove();
    }

    @Test
    @DisplayName("多批次工作列表使用固定次数的批量读取并保持批次排序")
    void listBulkLoadsBatchesAndRowsWithoutPerBatchQueries()
    {
        OaSignOnboardImportBatch first = batch();
        first.setBatchNo("OSI-31");
        OaSignOnboardImportBatch second = batch();
        second.setBatchId(32L);
        second.setBatchNo("OSI-32");
        OaSignOnboardImportRow firstStored = readySignatureRow();
        OaSignOnboardImportRow secondStored = readySignatureRow();
        secondStored.setBatchId(32L);
        secondStored.setRowId(72L);
        OaSignOnboardImportRowView firstView = companyWorkRowView(71L);
        OaSignOnboardImportRowView secondView = companyWorkRowView(72L);
        List<Long> orderedBatchIds = List.of(32L, 31L);
        List<OaSignOnboardImportRow> storedRows = List.of(secondStored, firstStored);

        when(signScopeService.resolveScopeDeptIds(SCOPE_ID)).thenReturn(List.of(SCOPE_ID));
        when(rowMapper.selectSignatureFirstCompanyWorkRows(
                eq(101L), eq(List.of(SCOPE_ID)), eq(100)))
                .thenReturn(storedRows);
        when(batchMapper.selectByIds(orderedBatchIds)).thenReturn(List.of(first, second));
        when(importService.companyWorkRowViewsByBatch(storedRows,
                Map.of(31L, first, 32L, second))).thenReturn(
                Map.of(31L, List.of(firstView), 32L, List.of(secondView)));

        OaSignOnboardCompanyWorkView result = service.list(SCOPE_ID);

        assertThat(result.getTotalCount()).isEqualTo(2);
        assertThat(result.getItems()).extracting(OaSignOnboardCompanyWorkView.Item::getBatchId)
                .containsExactly(32L, 31L);
        verify(batchMapper).selectByIds(orderedBatchIds);
        verify(rowMapper).selectSignatureFirstCompanyWorkRows(
                eq(101L), eq(List.of(SCOPE_ID)), eq(100));
        verify(batchMapper, never()).selectById(any());
        verify(batchMapper, never()).claimGeneration(any(), any(), any());
        verify(rowMapper, never()).selectByBatchId(any());
        verify(importService, never()).detail(any(), any());
        verify(importService, never()).refreshSummary(any());
    }

    @Test
    @DisplayName("后台恢复会接管过期批次并修复过期行租约")
    void backgroundRecoveryTakesOverOnlyAnExpiredBatchLease()
    {
        Date staleBefore = new Date(2_000L);
        OaSignOnboardImportBatch staleBatch = batch();
        staleBatch.setStatus("GENERATING");
        staleBatch.setUpdateTime(new Date(1_000L));
        OaSignOnboardImportBatch claimedBatch = batch();
        claimedBatch.setStatus("GENERATING");
        claimedBatch.setVersion(11L);
        claimedBatch.setUpdateTime(new Date(2_100L));
        OaSignOnboardImportRow staleRow = readySignatureRow();
        staleRow.setStatus("GENERATING");
        staleRow.setUpdateTime(new Date(1_000L));
        OaSignOnboardImportRow recovered = readySignatureRow();
        recovered.setStatus("GENERATE_FAILED");
        recovered.setVersion(staleRow.getVersion() + 1L);
        when(batchMapper.selectById(31L)).thenReturn(staleBatch, claimedBatch);
        when(batchMapper.claimGeneration(31L, 10L, staleBefore)).thenReturn(1);
        when(rowMapper.selectByBatchId(31L)).thenReturn(List.of(staleRow));
        when(generationService.recoverInterruptedGeneration(
                claimedBatch, staleRow, staleBefore)).thenReturn(recovered);

        recoveryService.recoverOne(31L, staleBefore);

        verify(batchMapper).claimGeneration(31L, 10L, staleBefore);
        verify(generationService).recoverInterruptedGeneration(
                claimedBatch, staleRow, staleBefore);
        verify(importService).refreshSummary(31L);
    }

    @Test
    @DisplayName("批次中仍有活跃行租约时不释放恢复锁")
    void listRecoveryRetainsBatchLeaseWhileAnyRowClaimIsStillLive()
    {
        Date staleBefore = new Date(2_000L);
        OaSignOnboardImportBatch staleBatch = batch();
        staleBatch.setStatus("GENERATING");
        staleBatch.setUpdateTime(new Date(1_000L));
        OaSignOnboardImportBatch claimedBatch = batch();
        claimedBatch.setStatus("GENERATING");
        claimedBatch.setVersion(11L);
        claimedBatch.setUpdateTime(new Date(2_100L));
        OaSignOnboardImportRow liveRow = readySignatureRow();
        liveRow.setStatus("GENERATING");
        liveRow.setUpdateTime(new Date(2_100L));
        when(batchMapper.selectById(31L)).thenReturn(staleBatch, claimedBatch);
        when(batchMapper.claimGeneration(31L, 10L, staleBefore)).thenReturn(1);
        when(rowMapper.selectByBatchId(31L)).thenReturn(List.of(liveRow));

        recoveryService.recoverOne(31L, staleBefore);

        verify(generationService, never()).recoverInterruptedGeneration(any(), any(), any());
        verify(importService, never()).refreshSummary(31L);
    }

    @Test
    @DisplayName("绑定不一致的批次不得在后台恢复时先行占用")
    void staleRecoveryDoesNotClaimMixedOwnerBatch()
    {
        Date staleBefore = new Date(2_000L);
        OaSignOnboardImportBatch staleBatch = batch();
        staleBatch.setStatus("GENERATING");
        staleBatch.setUpdateTime(new Date(1_000L));
        OaSignOnboardImportRow mine = readySignatureRow();
        mine.setStatus("GENERATING");
        mine.setUpdateTime(new Date(1_000L));
        OaSignOnboardImportRow anotherOwner = readySignatureRow();
        anotherOwner.setRowId(72L);
        anotherOwner.setTaskId(802L);
        anotherOwner.setPackageId(902L);
        anotherOwner.setStatus("GENERATING");
        anotherOwner.setUpdateTime(new Date(1_000L));
        when(batchMapper.selectById(31L)).thenReturn(staleBatch);
        when(rowMapper.selectByBatchId(31L)).thenReturn(List.of(mine, anotherOwner));
        doThrow(new ServiceException("导入行与签约任务绑定不一致"))
                .when(importService).requireValidTaskBinding(anotherOwner);

        recoveryService.recoverOne(31L, staleBefore);

        verify(batchMapper, never()).claimGeneration(any(), any(), any());
        verify(generationService, never()).recoverInterruptedGeneration(any(), any(), any());
    }

    @Test
    @DisplayName("员工已留签名且仅剩公司类错误时预检可继续")
    void companyErrorsAreResolvedByExplicitSelection()
    {
        OaSignOnboardImportRow row = readySignatureRow();
        when(importService.requireHrBatch(31L, SCOPE_ID)).thenReturn(batch());
        when(importService.requireBatchRow(31L, 71L)).thenReturn(row);
        when(dataRequestMapper.selectById(501L)).thenReturn(completedSignatureRequest());
        when(importService.submittedEmployeeConfirmationValues(any())).thenReturn(Map.of());
        when(importService.list("company-errors")).thenReturn(List.of(
                "COMPANY_MASTER_DATA_INCOMPLETE", "COMPANY_SEAL_REQUIRES_HR"));
        when(importService.list("[]")).thenReturn(List.of());
        when(companyService.requireContractReadyEntity(4L)).thenReturn(new SysLegalEntity());
        when(companyService.requireContractReadySeal(8L, 4L))
                .thenReturn(new OaCompanySealConfig());

        OaSignOnboardCompanyWorkResult result = service.preview(
                request(Boolean.TRUE), SCOPE_ID);

        verify(hrAccessService).requireCurrentHr();
        assertThat(result.getReadyCount()).isEqualTo(1);
        assertThat(result.getBlockedCount()).isZero();
        assertThat(result.getItems()).singleElement().satisfies(item -> {
            assertThat(item.getResult()).isEqualTo("READY");
            assertThat(item.getBlockers()).isEmpty();
        });
    }

    @Test
    @DisplayName("员工唯一签名已绑定真实签约包后仍可进入选公司盖章预检")
    void stagedRealPackageRemainsEligibleForCompanyWork()
    {
        OaSignOnboardImportRow row = readySignatureRow();
        row.setTaskId(801L);
        row.setPackageId(901L);
        when(importService.requireHrBatch(31L, SCOPE_ID)).thenReturn(batch());
        when(importService.requireBatchRow(31L, 71L)).thenReturn(row);
        when(dataRequestMapper.selectById(501L)).thenReturn(completedSignatureRequest());
        when(importService.submittedEmployeeConfirmationValues(any())).thenReturn(Map.of());
        when(importService.list("company-errors")).thenReturn(List.of(
                "COMPANY_MATCH_REQUIRES_HR"));
        when(importService.list("[]")).thenReturn(List.of());
        when(companyService.requireContractReadyEntity(4L)).thenReturn(new SysLegalEntity());
        when(companyService.requireContractReadySeal(8L, 4L))
                .thenReturn(new OaCompanySealConfig());

        OaSignOnboardCompanyWorkResult result = service.preview(
                request(Boolean.TRUE), SCOPE_ID);

        assertThat(result.getReadyCount()).isEqualTo(1);
        assertThat(result.getItems()).singleElement().satisfies(item -> {
            assertThat(item.getResult()).isEqualTo("READY");
            assertThat(item.getTaskId()).isEqualTo(801L);
            assertThat(item.getPackageId()).isEqualTo(901L);
        });
    }

    @Test
    @DisplayName("公司工作区预检对非当前经办人任务关闭失败")
    void companyWorkPreflightFailsClosedForDifferentTaskOwner()
    {
        OaSignOnboardImportRow row = readySignatureRow();
        row.setTaskId(801L);
        row.setPackageId(901L);
        when(importService.requireHrBatch(31L, SCOPE_ID)).thenReturn(batch());
        when(importService.requireBatchRow(31L, 71L)).thenReturn(row);
        doThrow(new ServiceException("导入行签约任务未分配给当前合同经办人"))
                .when(importService).requireCurrentHrTaskOwnership(eq(row), any());

        OaSignOnboardCompanyWorkResult result = service.preview(
                request(Boolean.TRUE), SCOPE_ID);

        assertThat(result.getFailedCount()).isEqualTo(1);
        assertThat(result.getItems()).singleElement().satisfies(item ->
                assertThat(item.getMessage()).contains("未分配给当前"));
        verify(companyService, never()).requireContractReadyEntity(any());
    }

    @Test
    @DisplayName("公司工作区直达入口拒绝同一来源事件的重复签约任务")
    void companyWorkDirectEntryRejectsDuplicateExactSourceTasks()
    {
        OaSignOnboardImportRow row = readySignatureRow();
        row.setTaskId(801L);
        row.setPackageId(901L);
        when(importService.requireHrBatch(31L, SCOPE_ID)).thenReturn(batch());
        when(importService.requireBatchRow(31L, 71L)).thenReturn(row);
        doThrow(new ServiceException("同一Excel来源事件存在多个签约任务"))
                .when(importService).requireCurrentHrTaskOwnership(eq(row), any());

        OaSignOnboardCompanyWorkResult result = service.preview(
                request(Boolean.TRUE), SCOPE_ID);

        assertThat(result.getFailedCount()).isEqualTo(1);
        assertThat(result.getItems()).singleElement().satisfies(item ->
                assertThat(item.getMessage()).contains("多个签约任务"));
        verify(companyService, never()).requireContractReadyEntity(any());
        verify(generationService, never()).generate(any(), any(), any());
    }

    @Test
    @DisplayName("非公司资料缺失仍失败关闭且不会被批量盖章掩盖")
    void nonCompanyMissingFactStillBlocks()
    {
        OaSignOnboardImportRow row = readySignatureRow();
        row.setMissingFieldsJson("missing");
        when(importService.requireHrBatch(31L, SCOPE_ID)).thenReturn(batch());
        when(importService.requireBatchRow(31L, 71L)).thenReturn(row);
        when(dataRequestMapper.selectById(501L)).thenReturn(completedSignatureRequest());
        when(importService.submittedEmployeeConfirmationValues(any())).thenReturn(Map.of());
        when(importService.list("company-errors"))
                .thenReturn(List.of("COMPANY_MATCH_REQUIRES_HR"));
        when(importService.list("missing"))
                .thenReturn(List.of("servicePersonType", "noExternalContractConfirmation"));
        when(companyService.requireContractReadyEntity(4L)).thenReturn(new SysLegalEntity());
        when(companyService.requireContractReadySeal(8L, 4L))
                .thenReturn(new OaCompanySealConfig());

        OaSignOnboardCompanyWorkResult result = service.preview(
                request(Boolean.TRUE), SCOPE_ID);

        assertThat(result.getReadyCount()).isZero();
        assertThat(result.getBlockedCount()).isEqualTo(1);
        assertThat(result.getItems()).singleElement().satisfies(item -> {
            assertThat(item.getResult()).isEqualTo("BLOCKED");
            assertThat(item.getBlockers()).containsExactly("servicePersonType");
        });
    }

    @Test
    @DisplayName("签名样本内容与登记SHA-256不一致时预检阻断")
    void signatureHashMismatchBlocksPreflight()
    {
        OaSignOnboardImportRow row = readySignatureRow();
        OaSignOnboardDataRequest data = completedSignatureRequest();
        data.setSignatureSampleHash("0".repeat(64));
        when(importService.requireHrBatch(31L, SCOPE_ID)).thenReturn(batch());
        when(importService.requireBatchRow(31L, 71L)).thenReturn(row);
        when(dataRequestMapper.selectById(501L)).thenReturn(data);
        when(importService.list("company-errors")).thenReturn(List.of());
        when(importService.list("[]")).thenReturn(List.of());
        when(companyService.requireContractReadyEntity(4L)).thenReturn(new SysLegalEntity());
        when(companyService.requireContractReadySeal(8L, 4L))
                .thenReturn(new OaCompanySealConfig());

        OaSignOnboardCompanyWorkResult result = service.preview(
                request(Boolean.TRUE), SCOPE_ID);

        assertThat(result.getBlockedCount()).isEqualTo(1);
        assertThat(result.getItems()).singleElement().satisfies(item -> {
            assertThat(item.getResult()).isEqualTo("BLOCKED");
            assertThat(item.getBlockers()).contains("SIGNATURE_CONFIRMATION_INCOMPLETE");
        });
        verify(importService, never()).requireEmployeeConfirmationCurrent(any(), any(), any());
    }

    @Test
    @DisplayName("同一请求携带原始行版本可从已冻结的生成失败状态继续")
    void sameRequestAndOriginalVersionResumeFailedGeneration()
    {
        OaSignOnboardImportBatch batch = batch();
        batch.setVersion(12L);
        OaSignOnboardImportRow row = readySignatureRow();
        row.setVersion(7L);
        row.setStatus("GENERATE_FAILED");
        row.setMatchedLegalEntityId(4L);
        row.setRecommendedSealId(8L);
        row.setGenerationRequestId(OaSignOnboardCompanyWorkService.itemRequestId(
                "company-work-test", 71L, 3L));
        row.setUpdateTime(new Date());
        when(importService.requireHrBatch(31L, SCOPE_ID)).thenReturn(batch);
        when(importService.requireBatchRow(31L, 71L)).thenReturn(row);
        when(generationService.generate(eq(31L), any(), eq(SCOPE_ID)))
                .thenReturn(generatedResult());

        OaSignOnboardCompanyWorkResult result = service.execute(
                request(Boolean.TRUE), SCOPE_ID);

        assertThat(result.getSucceededCount()).isEqualTo(1);
        assertThat(result.getItems()).singleElement().satisfies(item -> {
            assertThat(item.getResult()).isEqualTo("GENERATED");
            assertThat(item.getTaskId()).isEqualTo(801L);
            assertThat(item.getPackageId()).isEqualTo(901L);
        });
        ArgumentCaptor<OaSignOnboardGenerateRequest> generateRequest =
                ArgumentCaptor.forClass(OaSignOnboardGenerateRequest.class);
        verify(generationService).generate(eq(31L), generateRequest.capture(), eq(SCOPE_ID));
        assertThat(generateRequest.getValue().getRequestId()).isEqualTo(
                OaSignOnboardCompanyWorkService.itemRequestId(
                        "company-work-test", 71L, 3L));
        assertThat(generateRequest.getValue().getBatchVersion()).isEqualTo(12L);
        assertThat(generateRequest.getValue().getRowIds()).containsExactly(71L);
    }

    @Test
    @DisplayName("公司资料刷新完成后才持任务锁冻结本地生成请求")
    void companyUpdateCompletesBeforeShortTaskLockedBind()
    {
        OaSignOnboardImportBatch batch = batch();
        OaSignOnboardImportRow current = readySignatureRow();
        current.setTaskId(801L);
        current.setPackageId(901L);
        OaSignOnboardImportRow updatedStored = readySignatureRow();
        updatedStored.setTaskId(801L);
        updatedStored.setPackageId(901L);
        updatedStored.setVersion(4L);
        OaSignOnboardImportRow bound = readySignatureRow();
        bound.setTaskId(801L);
        bound.setPackageId(901L);
        bound.setVersion(5L);
        bound.setGenerationRequestId(OaSignOnboardCompanyWorkService.itemRequestId(
                "company-work-test", 71L, 3L));
        OaSignOnboardImportRowView updatedView = companyWorkRowView(71L);
        updatedView.setVersion(4L);
        OaSignOnboardImportBatchView updatedBatch = new OaSignOnboardImportBatchView();
        updatedBatch.setBatchId(31L);
        updatedBatch.setVersion(10L);
        updatedBatch.setRows(List.of(updatedView));

        when(importService.requireHrBatch(31L, SCOPE_ID)).thenReturn(batch);
        when(importService.requireBatchRow(31L, 71L)).thenReturn(current);
        when(dataRequestMapper.selectById(501L)).thenReturn(completedSignatureRequest());
        when(importService.submittedEmployeeConfirmationValues(any())).thenReturn(Map.of());
        when(importService.list("company-errors")).thenReturn(List.of());
        when(importService.list("[]")).thenReturn(List.of());
        when(companyService.requireContractReadyEntity(4L)).thenReturn(new SysLegalEntity());
        when(companyService.requireContractReadySeal(8L, 4L))
                .thenReturn(new OaCompanySealConfig());
        when(importService.updateRow(eq(31L), eq(71L), any(), eq(SCOPE_ID)))
                .thenReturn(updatedBatch);
        when(rowMapper.selectById(71L)).thenReturn(updatedStored, updatedStored, bound);
        when(rowMapper.bindStagedCompanyWorkGenerationRequest(eq(71L), eq(801L),
                eq(901L), any(), eq(4L), eq(8L), eq(4L))).thenReturn(1);
        when(generationService.generate(eq(31L), any(), eq(SCOPE_ID)))
                .thenReturn(generatedResult());

        OaSignOnboardCompanyWorkResult result = service.execute(
                request(Boolean.TRUE), SCOPE_ID);

        assertThat(result.getSucceededCount()).isEqualTo(1);
        org.mockito.InOrder order = org.mockito.Mockito.inOrder(
                importService, generationService);
        order.verify(importService).updateRow(eq(31L), eq(71L), any(), eq(SCOPE_ID));
        order.verify(generationService).withCurrentOwnerTaskLock(
                eq(batch), eq(updatedStored), eq(101L), any());
    }

    @Test
    @DisplayName("已生成任务只允许相同请求和相同公司印章复用")
    void generatedTaskRequiresSameRequestAndDecisionForReuse()
    {
        OaSignOnboardImportRow row = readySignatureRow();
        row.setStatus("GENERATED");
        row.setTaskId(801L);
        row.setPackageId(901L);
        row.setMatchedLegalEntityId(4L);
        row.setRecommendedSealId(8L);
        row.setGenerationRequestId(OaSignOnboardCompanyWorkService.itemRequestId(
                "company-work-test", 71L, 3L));
        when(importService.requireHrBatch(31L, SCOPE_ID)).thenReturn(batch());
        when(importService.requireBatchRow(31L, 71L)).thenReturn(row);

        OaSignOnboardCompanyWorkResult reused = service.execute(
                request(Boolean.TRUE), SCOPE_ID);
        OaSignOnboardCompanyWorkRequest otherRequest = request(Boolean.TRUE);
        otherRequest.setRequestId("company-work-other");
        OaSignOnboardCompanyWorkResult requestConflict = service.execute(
                otherRequest, SCOPE_ID);
        OaSignOnboardCompanyWorkRequest otherDecision = request(Boolean.TRUE);
        otherDecision.getItems().get(0).setSealId(9L);
        OaSignOnboardCompanyWorkResult decisionConflict = service.execute(
                otherDecision, SCOPE_ID);

        assertThat(reused.getItems()).singleElement()
                .extracting(OaSignOnboardCompanyWorkResult.Item::getResult)
                .isEqualTo("REUSED");
        assertThat(requestConflict.getItems()).singleElement().satisfies(item -> {
            assertThat(item.getResult()).isEqualTo("BLOCKED");
            assertThat(item.getBlockers())
                    .containsExactly("ALREADY_GENERATED_BY_ANOTHER_REQUEST");
        });
        assertThat(decisionConflict.getItems()).singleElement().satisfies(item -> {
            assertThat(item.getResult()).isEqualTo("BLOCKED");
            assertThat(item.getBlockers())
                    .containsExactly("ALREADY_GENERATED_WITH_DIFFERENT_DECISION");
        });
        verify(generationService, never()).generate(any(), any(), any());
    }

    @Test
    @DisplayName("其他请求在冻结租约内不能覆盖待生成的公司印章选择")
    void liveGenerationOwnerBlocksAnotherRequestBeforeClaim()
    {
        OaSignOnboardImportRow row = readySignatureRow();
        row.setVersion(4L);
        row.setMatchedLegalEntityId(4L);
        row.setRecommendedSealId(8L);
        row.setGenerationRequestId(OaSignOnboardCompanyWorkService.itemRequestId(
                "company-work-first", 71L, 3L));
        row.setUpdateTime(new Date());
        OaSignOnboardCompanyWorkRequest another = request(Boolean.TRUE);
        another.setRequestId("company-work-second");
        another.getItems().get(0).setVersion(4L);
        when(importService.requireHrBatch(31L, SCOPE_ID)).thenReturn(batch());
        when(importService.requireBatchRow(31L, 71L)).thenReturn(row);
        when(dataRequestMapper.selectById(501L)).thenReturn(completedSignatureRequest());
        when(importService.submittedEmployeeConfirmationValues(any())).thenReturn(Map.of());
        when(importService.list("company-errors")).thenReturn(List.of());
        when(importService.list("[]")).thenReturn(List.of());
        when(companyService.requireContractReadyEntity(4L)).thenReturn(new SysLegalEntity());
        when(companyService.requireContractReadySeal(8L, 4L))
                .thenReturn(new OaCompanySealConfig());

        OaSignOnboardCompanyWorkResult result = service.preview(another, SCOPE_ID);

        assertThat(result.getItems()).singleElement().satisfies(item -> {
            assertThat(item.getResult()).isEqualTo("BLOCKED");
            assertThat(item.getBlockers())
                    .contains("COMPANY_WORK_OWNED_BY_ANOTHER_REQUEST");
        });
    }

    @Test
    @DisplayName("其他请求的冻结租约过期后可重试生成失败行")
    void expiredOwnerAllowsRetryOfGenerationFailure()
    {
        OaSignOnboardImportRow row = readySignatureRow();
        row.setVersion(4L);
        row.setStatus("GENERATE_FAILED");
        row.setErrorCodesJson("generate-errors");
        row.setMatchedLegalEntityId(4L);
        row.setRecommendedSealId(8L);
        row.setGenerationRequestId(OaSignOnboardCompanyWorkService.itemRequestId(
                "company-work-first", 71L, 3L));
        row.setUpdateTime(new Date(System.currentTimeMillis() - 6L * 60L * 1000L));
        OaSignOnboardCompanyWorkRequest another = request(Boolean.TRUE);
        another.setRequestId("company-work-second");
        another.getItems().get(0).setVersion(4L);
        when(importService.requireHrBatch(31L, SCOPE_ID)).thenReturn(batch());
        when(importService.requireBatchRow(31L, 71L)).thenReturn(row);
        when(dataRequestMapper.selectById(501L)).thenReturn(completedSignatureRequest());
        when(importService.submittedEmployeeConfirmationValues(any())).thenReturn(Map.of());
        when(importService.list("generate-errors")).thenReturn(List.of(
                "GENERATE_FAILED", "SEND_PREPARATION_FAILED"));
        when(importService.list("[]")).thenReturn(List.of());
        when(companyService.requireContractReadyEntity(4L)).thenReturn(new SysLegalEntity());
        when(companyService.requireContractReadySeal(8L, 4L))
                .thenReturn(new OaCompanySealConfig());

        OaSignOnboardCompanyWorkResult result = service.preview(another, SCOPE_ID);

        assertThat(result.getReadyCount()).isEqualTo(1);
        assertThat(result.getItems()).singleElement().satisfies(item -> {
            assertThat(item.getResult()).isEqualTo("READY");
            assertThat(item.getBlockers()).isEmpty();
        });
    }

    @Test
    @DisplayName("公司和印章选项只暴露可直接执行的主数据")
    void optionsExcludeIncompleteCompanyAndInvalidSeal()
    {
        SysLegalEntity readyCompany = legalEntity(4L, "完整公司");
        SysLegalEntity incompleteCompany = legalEntity(5L, "缺资料公司");
        OaCompanySealConfig readySeal = seal(8L, 4L);
        OaCompanySealConfig invalidSeal = seal(9L, 4L);
        when(deptScopeMapper.selectActiveLegalEntities())
                .thenReturn(List.of(readyCompany, incompleteCompany));
        when(companyService.contractMasterMissingFields(readyCompany)).thenReturn(List.of());
        when(companyService.contractMasterMissingFields(incompleteCompany))
                .thenReturn(List.of("统一社会信用代码"));
        when(companyService.requireContractReadyEntity(4L)).thenReturn(readyCompany);
        when(companyService.recommendContractSeal(4L)).thenReturn(
                new OaSignCompanyService.SealRecommendation(readySeal,
                        List.of(readySeal, invalidSeal), "UNIQUE_DEFAULT"));
        when(companyService.requireContractReadySeal(8L, 4L)).thenReturn(readySeal);
        when(companyService.requireContractReadySeal(9L, 4L))
                .thenThrow(new ServiceException("印章无效"));

        OaSignCompanyOptions options = service.options(4L);

        assertThat(options.getLegalEntities()).containsExactly(readyCompany);
        assertThat(options.getSeals()).containsExactly(readySeal);
        assertThat(options.getRecommendedSealId()).isEqualTo(8L);
        assertThat(options.getCompanySealRequired()).isTrue();
    }

    @Test
    @DisplayName("已有开放入职任务的冲突行不再进入待选公司队列")
    void existingOpenTaskConflictIsNotOfferedForDuplicateGeneration()
            throws Exception
    {
        OaSignOnboardImportRowView row = new OaSignOnboardImportRowView();
        row.setDataRequestSigningSequence("SIGNATURE_FIRST");
        row.setDataRequestSignatureCaptured(Boolean.TRUE);
        row.setErrorCodes(List.of("EXISTING_OPEN_ONBOARD_TASK"));
        var method = OaSignOnboardCompanyWorkService.class
                .getDeclaredMethod("isCompanyWorkRow", OaSignOnboardImportRowView.class);
        method.setAccessible(true);

        assertThat(method.invoke(service, row)).isEqualTo(Boolean.FALSE);
    }

    @Test
    @DisplayName("待选公司工作项保留真实任务和签约包标识并按字符串输出")
    void companyWorkItemExposesExactStringSerializedTaskAndPackageIds()
            throws Exception
    {
        long taskId = 9_999_999_999_999_991L;
        long packageId = 9_999_999_999_999_992L;
        OaSignOnboardImportBatchView batch = new OaSignOnboardImportBatchView();
        batch.setBatchId(31L);
        batch.setBatchNo("OSI-test");
        batch.setShopDeptId(SCOPE_ID);
        OaSignOnboardImportRowView row = new OaSignOnboardImportRowView();
        row.setRowId(71L);
        row.setTaskId(taskId);
        row.setPackageId(packageId);

        var method = OaSignOnboardCompanyWorkService.class.getDeclaredMethod(
                "toWorkItem", OaSignOnboardImportBatchView.class,
                OaSignOnboardImportRowView.class);
        method.setAccessible(true);
        OaSignOnboardCompanyWorkView.Item item =
                (OaSignOnboardCompanyWorkView.Item) method.invoke(service, batch, row);

        assertThat(item.getTaskId()).isEqualTo(taskId);
        assertThat(item.getPackageId()).isEqualTo(packageId);
        assertThat(new ObjectMapper().writeValueAsString(item))
                .contains("\"taskId\":\"9999999999999991\"")
                .contains("\"packageId\":\"9999999999999992\"");
    }

    @Test
    @DisplayName("子请求编号对长批次请求号与原始行版本都稳定区分")
    void itemRequestIdIsCollisionResistantAndVersionBound()
    {
        String commonPrefix = "x".repeat(60);
        String first = OaSignOnboardCompanyWorkService.itemRequestId(
                commonPrefix + "-a", 71L, 3L);
        String second = OaSignOnboardCompanyWorkService.itemRequestId(
                commonPrefix + "-b", 71L, 3L);
        String nextVersion = OaSignOnboardCompanyWorkService.itemRequestId(
                commonPrefix + "-a", 71L, 4L);

        assertThat(first).isEqualTo(OaSignOnboardCompanyWorkService.itemRequestId(
                commonPrefix + "-a", 71L, 3L));
        assertThat(first).isNotEqualTo(second).isNotEqualTo(nextVersion);
        assertThat(first.length()).isLessThanOrEqualTo(64);
    }

    private OaSignOnboardCompanyWorkRequest request(Boolean noExternal)
    {
        OaSignOnboardCompanyWorkRequest.Item item =
                new OaSignOnboardCompanyWorkRequest.Item();
        item.setBatchId(31L);
        item.setRowId(71L);
        item.setVersion(3L);
        item.setLegalEntityId(4L);
        item.setSealId(8L);
        item.setNoExternalContractConfirmed(noExternal);
        OaSignOnboardCompanyWorkRequest request = new OaSignOnboardCompanyWorkRequest();
        request.setRequestId("company-work-test");
        request.setItems(List.of(item));
        return request;
    }

    private OaSignOnboardImportBatch batch()
    {
        OaSignOnboardImportBatch batch = new OaSignOnboardImportBatch();
        batch.setBatchId(31L);
        batch.setShopDeptId(SCOPE_ID);
        batch.setVersion(10L);
        return batch;
    }

    private OaSignOnboardImportRowView companyWorkRowView(Long rowId)
    {
        OaSignOnboardImportRowView row = new OaSignOnboardImportRowView();
        row.setRowId(rowId);
        row.setEmployeeId(200L + rowId);
        row.setStatus("READY_TO_GENERATE");
        row.setDataRequestSigningSequence("SIGNATURE_FIRST");
        row.setDataRequestSignatureCaptured(Boolean.TRUE);
        row.setErrorCodes(List.of());
        row.setWarningCodes(List.of());
        row.setMissingFields(List.of());
        row.setVersion(3L);
        return row;
    }

    private OaSignOnboardGenerateResult generatedResult()
    {
        OaSignOnboardGenerateResult.Item item = new OaSignOnboardGenerateResult.Item();
        item.setRowId(71L);
        item.setEmployeeId(201L);
        item.setTaskId(801L);
        item.setPackageId(901L);
        item.setResult("GENERATED");
        item.setStatus("READY_TO_SEND");
        OaSignOnboardGenerateResult result = new OaSignOnboardGenerateResult();
        result.setItems(List.of(item));
        return result;
    }

    private SysLegalEntity legalEntity(Long id, String name)
    {
        SysLegalEntity entity = new SysLegalEntity();
        entity.setLegalEntityId(id);
        entity.setLegalEntityName(name);
        return entity;
    }

    private OaCompanySealConfig seal(Long id, Long legalEntityId)
    {
        OaCompanySealConfig seal = new OaCompanySealConfig();
        seal.setSealId(id);
        seal.setLegalEntityId(legalEntityId);
        return seal;
    }

    private OaSignOnboardImportRow readySignatureRow()
    {
        OaSignOnboardImportRow row = new OaSignOnboardImportRow();
        row.setRowId(71L);
        row.setBatchId(31L);
        row.setEmployeeId(201L);
        row.setEmployeeNameMasked("张**");
        row.setVersion(3L);
        row.setPlanVersionId(91L);
        row.setDataRequestId(501L);
        row.setStatus("READY_TO_GENERATE");
        row.setErrorCodesJson("company-errors");
        row.setMissingFieldsJson("[]");
        return row;
    }

    private OaSignOnboardDataRequest completedSignatureRequest()
    {
        OaSignOnboardDataRequest request = new OaSignOnboardDataRequest();
        request.setRequestId(501L);
        request.setRowId(71L);
        request.setEmployeeId(201L);
        request.setSigningSequence("SIGNATURE_FIRST");
        request.setStatus("COMPLETED");
        request.setSignatureRequestId("signature-request");
        request.setSignatureSampleBytes(new byte[] { 1, 2, 3 });
        request.setSignatureSampleHash(OaSignOnboardExcelParser.sha256(
                request.getSignatureSampleBytes()));
        request.setSignatureSampleTime(new Date());
        return request;
    }
}
