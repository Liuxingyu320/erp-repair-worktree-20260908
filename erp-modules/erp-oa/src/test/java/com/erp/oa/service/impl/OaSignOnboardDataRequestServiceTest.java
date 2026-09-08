package com.erp.oa.service.impl;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Base64;
import java.util.Date;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.security.MessageDigest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.test.util.ReflectionTestUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.erp.common.core.constant.SecurityConstants;
import com.erp.common.core.context.SecurityContextHolder;
import com.erp.common.core.domain.R;
import com.erp.common.core.exception.ServiceException;
import com.erp.oa.domain.OaSignOnboardContractSnapshot;
import com.erp.oa.domain.OaSignOnboardDataRequest;
import com.erp.oa.domain.OaSignOnboardImportBatch;
import com.erp.oa.domain.OaSignOnboardImportRow;
import com.erp.oa.domain.dto.OaSignOnboardDataReviewRequest;
import com.erp.oa.domain.dto.OaSignOnboardDataSubmitRequest;
import com.erp.oa.domain.dto.OaSignOnboardImportRowUpdateRequest;
import com.erp.oa.domain.vo.OaSignOnboardDataRequestView;
import com.erp.oa.mapper.OaSignOnboardDataRequestMapper;
import com.erp.oa.mapper.OaSignOnboardImportRowMapper;
import com.erp.oa.service.IOaSignPackageService;
import com.erp.system.api.RemoteUserService;
import com.erp.system.api.domain.ReviewedSignProfileSupplement;
import com.erp.system.api.domain.ReviewedSignProfileSupplementResult;

class OaSignOnboardDataRequestServiceTest
{
    @AfterEach
    void clearSecurityContext()
    {
        SecurityContextHolder.remove();
    }

    @Test
    void hrDetailMustPassCurrentHrAndSelectedSigningScopeAuthorization()
    {
        OaSignOnboardDataRequestMapper requestMapper = mock(OaSignOnboardDataRequestMapper.class);
        OaSignOnboardImportRowMapper rowMapper = mock(OaSignOnboardImportRowMapper.class);
        OaSignOnboardImportService importService = mock(OaSignOnboardImportService.class);
        OaSignHrAccessService hrAccessService = mock(OaSignHrAccessService.class);
        OaSignOnboardDataRequestService service = new OaSignOnboardDataRequestService(
                requestMapper, rowMapper, importService,
                hrAccessService, mock(RemoteUserService.class), new ObjectMapper(),
                mock(IOaSignPackageService.class));
        OaSignOnboardDataRequest dataRequest = new OaSignOnboardDataRequest();
        dataRequest.setRequestId(91L);
        dataRequest.setBatchId(31L);
        dataRequest.setRowId(71L);
        dataRequest.setEmployeeId(42L);
        OaSignOnboardImportRow row = new OaSignOnboardImportRow();
        row.setRowId(71L);
        row.setBatchId(31L);
        row.setEmployeeId(42L);
        row.setDataRequestId(91L);
        when(requestMapper.selectById(91L)).thenReturn(dataRequest);
        when(rowMapper.selectById(71L)).thenReturn(row);
        when(hrAccessService.isCurrentHr()).thenReturn(true);
        doThrow(new ServiceException("导入批次不在当前签约范围"))
                .when(importService).requireHrBatch(31L, 1171L);
        SecurityContextHolder.setUserId("101");

        assertThatThrownBy(() -> service.detail(91L, 1171L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("不在当前签约范围");
        verify(importService).requireHrBatch(31L, 1171L);
    }

    @Test
    void hrDetailRejectsDataRequestBoundToAnotherTaskOwner()
    {
        OaSignOnboardDataRequestMapper requestMapper = mock(OaSignOnboardDataRequestMapper.class);
        OaSignOnboardImportRowMapper rowMapper = mock(OaSignOnboardImportRowMapper.class);
        OaSignOnboardImportService importService = mock(OaSignOnboardImportService.class);
        OaSignHrAccessService hrAccessService = mock(OaSignHrAccessService.class);
        OaSignOnboardDataRequestService service = new OaSignOnboardDataRequestService(
                requestMapper, rowMapper, importService,
                hrAccessService, mock(RemoteUserService.class), new ObjectMapper(),
                mock(IOaSignPackageService.class));
        OaSignOnboardDataRequest dataRequest = new OaSignOnboardDataRequest();
        dataRequest.setRequestId(91L);
        dataRequest.setBatchId(31L);
        dataRequest.setRowId(71L);
        dataRequest.setEmployeeId(42L);
        OaSignOnboardImportRow row = new OaSignOnboardImportRow();
        row.setRowId(71L);
        row.setBatchId(31L);
        row.setEmployeeId(42L);
        row.setDataRequestId(91L);
        row.setTaskId(801L);
        row.setPackageId(901L);
        when(requestMapper.selectById(91L)).thenReturn(dataRequest);
        when(rowMapper.selectById(71L)).thenReturn(row);
        when(hrAccessService.isCurrentHr()).thenReturn(true);
        doThrow(new ServiceException("导入行签约任务未分配给当前合同经办人"))
                .when(importService).requireCurrentHrTaskOwnership(eq(row), any());
        SecurityContextHolder.setUserId("101");

        assertThatThrownBy(() -> service.detail(91L, 1171L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("未分配给当前");
        verify(importService).requireHrBatch(31L, 1171L);
    }

    @Test
    void employeeDetailFailsClosedBeforeExposingTaskMetadataForMigratedWrongLink()
    {
        OaSignOnboardDataRequestMapper requestMapper =
                mock(OaSignOnboardDataRequestMapper.class);
        OaSignOnboardImportRowMapper rowMapper = mock(OaSignOnboardImportRowMapper.class);
        OaSignOnboardDataRequestService service = new OaSignOnboardDataRequestService(
                requestMapper, rowMapper, mock(OaSignOnboardImportService.class),
                mock(OaSignHrAccessService.class), mock(RemoteUserService.class),
                new ObjectMapper(), mock(IOaSignPackageService.class));
        OaSignOnboardDataRequest request = new OaSignOnboardDataRequest();
        request.setRequestId(91L);
        request.setBatchId(31L);
        request.setRowId(71L);
        request.setEmployeeId(42L);
        OaSignOnboardImportRow wrongEmployeeRow = new OaSignOnboardImportRow();
        wrongEmployeeRow.setRowId(71L);
        wrongEmployeeRow.setBatchId(31L);
        wrongEmployeeRow.setEmployeeId(43L);
        wrongEmployeeRow.setDataRequestId(91L);
        wrongEmployeeRow.setTaskId(801L);
        wrongEmployeeRow.setPackageId(901L);
        when(requestMapper.selectById(91L)).thenReturn(request);
        when(rowMapper.selectById(71L)).thenReturn(wrongEmployeeRow);
        SecurityContextHolder.setUserId("42");

        assertThatThrownBy(() -> service.detail(91L, null))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("绑定不一致");
    }

    @Test
    void hrReviewReassignmentAfterInitialCheckCannotWrite()
    {
        SecurityContextHolder.setUserId("101");
        OaSignOnboardDataRequestMapper requestMapper =
                mock(OaSignOnboardDataRequestMapper.class);
        OaSignOnboardImportRowMapper rowMapper = mock(OaSignOnboardImportRowMapper.class);
        OaSignOnboardImportService importService = mock(OaSignOnboardImportService.class);
        OaSignOnboardGenerationService generationService =
                mock(OaSignOnboardGenerationService.class);
        OaSignOnboardDataRequestService service = new OaSignOnboardDataRequestService(
                requestMapper, rowMapper, importService,
                mock(OaSignHrAccessService.class), mock(RemoteUserService.class),
                new ObjectMapper(), mock(IOaSignPackageService.class), generationService);
        OaSignOnboardDataRequest request = new OaSignOnboardDataRequest();
        request.setRequestId(91L);
        request.setBatchId(31L);
        request.setRowId(71L);
        request.setEmployeeId(42L);
        request.setStatus("SUBMITTED");
        request.setVersion(5L);
        OaSignOnboardImportBatch batch = new OaSignOnboardImportBatch();
        batch.setBatchId(31L);
        batch.setShopDeptId(1171L);
        OaSignOnboardImportRow row = new OaSignOnboardImportRow();
        row.setBatchId(31L);
        row.setRowId(71L);
        row.setEmployeeId(42L);
        row.setTaskId(801L);
        row.setPackageId(901L);
        row.setDataRequestId(91L);
        when(requestMapper.selectById(91L)).thenReturn(request);
        when(importService.requireHrBatch(31L, 1171L)).thenReturn(batch);
        when(importService.requireBatchRow(31L, 71L)).thenReturn(row);
        doThrow(new ServiceException("Excel导入签约任务已改派"))
                .when(generationService).withCurrentOwnerTaskLock(
                        eq(batch), eq(row), eq(101L), any());
        OaSignOnboardDataReviewRequest action = new OaSignOnboardDataReviewRequest();
        action.setAction("REJECT");
        action.setReason("请更正");
        action.setVersion(5L);

        assertThatThrownBy(() -> service.review(91L, action, 1171L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("已改派");

        verify(requestMapper, never()).review(any(), any(), any(), any(), any(), any(),
                any(), any(), any());
        verify(rowMapper, never()).updateStatus(any(), any(), any());
    }

    @Test
    void unknownProfileSyncResultCannotBeRejectedAndResubmittedWithChangedValues()
    {
        OaSignOnboardDataRequestMapper requestMapper = mock(OaSignOnboardDataRequestMapper.class);
        OaSignOnboardImportRowMapper rowMapper = mock(OaSignOnboardImportRowMapper.class);
        OaSignOnboardImportService importService = mock(OaSignOnboardImportService.class);
        OaSignHrAccessService hrAccessService = mock(OaSignHrAccessService.class);
        OaSignOnboardDataRequestService service = new OaSignOnboardDataRequestService(
                requestMapper, rowMapper, importService,
                hrAccessService, mock(RemoteUserService.class), new ObjectMapper(),
                mock(IOaSignPackageService.class));
        OaSignOnboardDataRequest dataRequest = new OaSignOnboardDataRequest();
        dataRequest.setRequestId(92L);
        dataRequest.setBatchId(32L);
        dataRequest.setRowId(72L);
        dataRequest.setEmployeeId(42L);
        dataRequest.setStatus("PROFILE_SYNC_FAILED");
        dataRequest.setVersion(5L);
        dataRequest.setProfileSyncRequestId("OPS:12:4");
        OaSignOnboardImportRow row = new OaSignOnboardImportRow();
        row.setBatchId(32L);
        row.setRowId(72L);
        row.setEmployeeId(42L);
        row.setDataRequestId(92L);
        when(requestMapper.selectById(92L)).thenReturn(dataRequest);
        when(importService.requireBatchRow(32L, 72L)).thenReturn(row);
        when(rowMapper.selectById(72L)).thenReturn(row);
        OaSignOnboardDataReviewRequest action = new OaSignOnboardDataReviewRequest();
        action.setAction("REJECT");
        action.setReason("请重新填写");
        action.setVersion(5L);

        assertThatThrownBy(() -> service.review(92L, action, 1171L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("同步结果未知时不能改值");
    }

    @Test
    void rejectedRequestAndImportRowTransitionShareOneRollbackBoundary()
    {
        OaSignOnboardDataRequestMapper requestMapper = mock(OaSignOnboardDataRequestMapper.class);
        OaSignOnboardImportRowMapper rowMapper = mock(OaSignOnboardImportRowMapper.class);
        OaSignOnboardImportService importService = mock(OaSignOnboardImportService.class);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        TransactionStatus transactionStatus = mock(TransactionStatus.class);
        when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenReturn(transactionStatus);
        OaSignOnboardDataRequestService service = new OaSignOnboardDataRequestService(
                requestMapper, rowMapper, importService, mock(OaSignHrAccessService.class),
                mock(RemoteUserService.class), new ObjectMapper(),
                mock(IOaSignPackageService.class), transactionManager);
        OaSignOnboardDataRequest request = recoveryRequest("SUBMITTED");
        request.setRequestId(94L);
        request.setVersion(5L);
        OaSignOnboardImportRow row = new OaSignOnboardImportRow();
        row.setBatchId(31L); row.setRowId(71L);
        row.setEmployeeId(42L); row.setDataRequestId(94L);
        row.setVersion(9L); row.setStatus("PENDING_HR_REVIEW");
        when(requestMapper.selectById(94L)).thenReturn(request);
        when(importService.requireBatchRow(31L, 71L)).thenReturn(row);
        when(requestMapper.review(eq(94L), eq("SUBMITTED"), eq("REJECTED"),
                eq(101L), eq("请更正现住址"), any(), eq("NOT_STARTED"),
                eq("OPS:71:9"), eq(5L))).thenReturn(1);
        when(rowMapper.selectById(71L)).thenReturn(row);
        when(rowMapper.updateStatus(71L, "WAITING_EMPLOYEE_DATA", 9L))
                .thenReturn(0);
        SecurityContextHolder.setUserId("101");
        OaSignOnboardDataReviewRequest action = new OaSignOnboardDataReviewRequest();
        action.setAction("REJECT");
        action.setReason("请更正现住址");
        action.setVersion(5L);

        assertThatThrownBy(() -> service.review(94L, action, 1171L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("导入行状态已变化");

        verify(transactionManager).rollback(transactionStatus);
        verify(transactionManager, never()).commit(transactionStatus);
    }

    @Test
    void approvedCrashRetryUsesFrozenHrFactsAndSameSystemIdempotencyRequest()
    {
        RecoveryFixture fixture = new RecoveryFixture("PENDING_HR_REVIEW", false);
        fixture.stubRequestLifecycle();
        OaSignOnboardDataReviewRequest retry = fixture.action();
        retry.setServicePersonType("CHANGED_AFTER_CRASH");
        retry.setInsuranceType("CHANGED_AFTER_CRASH");

        OaSignOnboardDataRequestView result = fixture.service.review(93L, retry, 1171L);

        assertThat(result.getStatus()).isEqualTo("COMPLETED");
        ArgumentCaptor<ReviewedSignProfileSupplement> syncRequest =
                ArgumentCaptor.forClass(ReviewedSignProfileSupplement.class);
        verify(fixture.remoteUserService).supplementSigningProfile(syncRequest.capture(),
                eq(SecurityConstants.INNER));
        assertThat(syncRequest.getValue().getRequestId()).isEqualTo("OPS:71:9");
        ArgumentCaptor<OaSignOnboardImportRowUpdateRequest> patch =
                ArgumentCaptor.forClass(OaSignOnboardImportRowUpdateRequest.class);
        verify(fixture.importService).updateRowAfterProfileSync(eq(31L), eq(71L),
                patch.capture(), eq(1171L));
        assertThat(patch.getValue().getServicePersonType()).isEqualTo("INTERN");
        assertThat(patch.getValue().getInsuranceType()).isEqualTo("COMMERCIAL");
        verify(fixture.requestMapper, never()).review(any(), any(), any(), any(), any(),
                any(), any(), any(), any());
    }

    @Test
    void profileSyncApplyingCrashRetryContinuesWithoutRepeatingRowTransition()
    {
        RecoveryFixture fixture = new RecoveryFixture("PROFILE_SYNC_APPLYING", false);
        fixture.stubRequestLifecycle();

        OaSignOnboardDataRequestView result = fixture.service.review(
                93L, fixture.action(), 1171L);

        assertThat(result.getStatus()).isEqualTo("COMPLETED");
        verify(fixture.rowMapper, never()).updateStatus(71L, "PROFILE_SYNC_APPLYING", 10L);
        verify(fixture.importService).updateRowAfterProfileSync(eq(31L), eq(71L),
                any(OaSignOnboardImportRowUpdateRequest.class), eq(1171L));
    }

    @Test
    void approvalDoesNotHoldTaskLockAcrossRemoteOrCandidateRefresh()
    {
        RecoveryFixture fixture = new RecoveryFixture("PROFILE_SYNC_APPLYING", false);
        fixture.stubRequestLifecycle();

        fixture.service.review(93L, fixture.action(), 1171L);

        org.mockito.InOrder order = org.mockito.Mockito.inOrder(
                fixture.generationService, fixture.remoteUserService,
                fixture.importService);
        order.verify(fixture.generationService).withCurrentOwnerTaskLock(
                any(), any(), eq(101L), any());
        order.verify(fixture.remoteUserService).supplementSigningProfile(
                any(ReviewedSignProfileSupplement.class), eq(SecurityConstants.INNER));
        order.verify(fixture.importService).updateRowAfterProfileSync(
                eq(31L), eq(71L), any(OaSignOnboardImportRowUpdateRequest.class),
                eq(1171L));
        order.verify(fixture.generationService).withCurrentOwnerTaskLock(
                any(), any(), eq(101L), any());
    }

    @Test
    void replayAfterRowWasAlreadyRecalculatedOnlyCompletesRequest()
    {
        RecoveryFixture fixture = new RecoveryFixture("READY_TO_GENERATE", true);
        fixture.stubRequestLifecycle();

        OaSignOnboardDataRequestView result = fixture.service.review(
                93L, fixture.action(), 1171L);

        assertThat(result.getStatus()).isEqualTo("COMPLETED");
        verify(fixture.rowMapper, never()).updateStatus(any(), any(), any());
        verify(fixture.importService, never()).updateRowAfterProfileSync(any(), any(), any(), any());
        verify(fixture.requestMapper).updateProfileSync(93L, "COMPLETED",
                "profile-before", "profile-after", 6L);
    }

    @Test
    void completedHrRetryIdempotentlyBindsStagedSignatureWithoutReplayingRemoteSync()
            throws Exception
    {
        OaSignOnboardDataRequestMapper requestMapper = mock(OaSignOnboardDataRequestMapper.class);
        OaSignOnboardImportRowMapper rowMapper = mock(OaSignOnboardImportRowMapper.class);
        OaSignOnboardImportService importService = mock(OaSignOnboardImportService.class);
        RemoteUserService remoteUserService = mock(RemoteUserService.class);
        IOaSignPackageService packageService = mock(IOaSignPackageService.class);
        OaSignOnboardDataRequestService service = new OaSignOnboardDataRequestService(
                requestMapper, rowMapper, importService, mock(OaSignHrAccessService.class),
                remoteUserService, new ObjectMapper(), packageService);
        OaSignOnboardDataRequest completed = completedStagedRequest();
        OaSignOnboardImportRow row = stagedRow();
        when(requestMapper.selectById(93L)).thenReturn(completed);
        when(rowMapper.selectById(71L)).thenReturn(row);
        when(importService.requireBatchRow(31L, 71L)).thenReturn(row);
        when(importService.requireFrozenEmployeeConfirmation(completed)).thenReturn(
                frozenConfirmation());
        OaSignOnboardDataReviewRequest retry = new OaSignOnboardDataReviewRequest();
        retry.setAction("APPROVE");
        retry.setVersion(6L);

        OaSignOnboardDataRequestView result = service.review(93L, retry, 1171L);

        assertThat(result.getStatus()).isEqualTo("COMPLETED");
        verify(packageService).recordStagedSignatureFirstSampleForSystem(
                eq(901L), eq(801L), eq(93L), eq("sig-recovery"), any(byte[].class),
                eq(completed.getSignatureSampleHash()), eq(completed.getSignatureSampleTime()));
        verify(remoteUserService, never()).supplementSigningProfile(any(), any());
        verify(requestMapper, never()).updateProfileSync(any(), any(), any(), any(), any());
    }

    @Test
    void profileCompletionAndStagedBindingShareOneRollbackBoundary() throws Exception
    {
        OaSignOnboardDataRequestMapper requestMapper = mock(OaSignOnboardDataRequestMapper.class);
        OaSignOnboardImportRowMapper rowMapper = mock(OaSignOnboardImportRowMapper.class);
        IOaSignPackageService packageService = mock(IOaSignPackageService.class);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        TransactionStatus transactionStatus = mock(TransactionStatus.class);
        when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenReturn(transactionStatus);
        OaSignOnboardDataRequestService service = new OaSignOnboardDataRequestService(
                requestMapper, rowMapper, mock(OaSignOnboardImportService.class),
                mock(OaSignHrAccessService.class), mock(RemoteUserService.class),
                new ObjectMapper(), packageService, transactionManager);
        OaSignOnboardDataRequest approved = completedStagedRequest();
        approved.setStatus("APPROVED");
        OaSignOnboardDataRequest completed = completedStagedRequest();
        when(requestMapper.selectById(93L)).thenReturn(approved, completed);
        when(requestMapper.updateProfileSync(93L, "COMPLETED",
                "profile-before", "profile-after", 6L)).thenReturn(1);
        when(rowMapper.selectById(71L)).thenReturn(stagedRow());
        doThrow(new ServiceException("签名绑定失败")).when(packageService)
                .recordStagedSignatureFirstSampleForSystem(any(), any(), any(), any(),
                        any(), any(), any());
        ReviewedSignProfileSupplementResult sync = new ReviewedSignProfileSupplementResult(
                false, true, "profile-before", "profile-after", "currentAddress");

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(
                service, "completeProfileSync", 93L, sync))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("签名绑定失败");

        verify(requestMapper).updateProfileSync(93L, "COMPLETED",
                "profile-before", "profile-after", 6L);
        verify(transactionManager).rollback(transactionStatus);
        verify(transactionManager, never()).commit(transactionStatus);
    }

    @Test
    void concurrentCompletionReplayStillRunsIdempotentStagedBinding() throws Exception
    {
        OaSignOnboardDataRequestMapper requestMapper = mock(OaSignOnboardDataRequestMapper.class);
        OaSignOnboardImportRowMapper rowMapper = mock(OaSignOnboardImportRowMapper.class);
        OaSignOnboardImportService importService = mock(OaSignOnboardImportService.class);
        IOaSignPackageService packageService = mock(IOaSignPackageService.class);
        OaSignOnboardDataRequestService service = new OaSignOnboardDataRequestService(
                requestMapper, rowMapper, importService, mock(OaSignHrAccessService.class),
                mock(RemoteUserService.class), new ObjectMapper(), packageService);
        OaSignOnboardDataRequest approved = completedStagedRequest();
        approved.setStatus("APPROVED");
        OaSignOnboardDataRequest completed = completedStagedRequest();
        when(requestMapper.selectById(93L)).thenReturn(approved, completed);
        when(requestMapper.updateProfileSync(93L, "COMPLETED",
                "profile-before", "profile-after", 6L)).thenReturn(0);
        when(rowMapper.selectById(71L)).thenReturn(stagedRow());
        when(importService.requireFrozenEmployeeConfirmation(completed)).thenReturn(
                frozenConfirmation());
        ReviewedSignProfileSupplementResult sync = new ReviewedSignProfileSupplementResult(
                false, true, "profile-before", "profile-after", "currentAddress");

        OaSignOnboardDataRequestView result = ReflectionTestUtils.invokeMethod(
                service, "completeProfileSync", 93L, sync);

        assertThat(result.getStatus()).isEqualTo("COMPLETED");
        verify(packageService).recordStagedSignatureFirstSampleForSystem(
                eq(901L), eq(801L), eq(93L), eq("sig-recovery"), any(byte[].class),
                eq(completed.getSignatureSampleHash()), eq(completed.getSignatureSampleTime()));
    }

    @Test
    void employeeDetailAndMineNeverReturnFrozenHrApprovalSnapshot() throws Exception
    {
        OaSignOnboardDataRequestMapper requestMapper = mock(OaSignOnboardDataRequestMapper.class);
        OaSignOnboardImportService importService = mock(OaSignOnboardImportService.class);
        OaSignOnboardImportRowMapper rowMapper = mock(OaSignOnboardImportRowMapper.class);
        OaSignOnboardDataRequestService service = new OaSignOnboardDataRequestService(
                requestMapper, rowMapper, importService,
                mock(OaSignHrAccessService.class), mock(RemoteUserService.class), new ObjectMapper(),
                mock(IOaSignPackageService.class));
        OaSignOnboardDataRequest request = recoveryRequest("PENDING_EMPLOYEE");
        request.setFactSnapshotJson("{\"employeeName\":\"段继康\","
                + "\"profileFactsHash\":\"secret-profile-hash\","
                + "\"matchedLegalEntityId\":99,\"matchedSealId\":88,"
                + "\"matchedSealImageUrl\":\"/private/seal.png\","
                + "\"matchedSealImageHash\":\"secret-seal-hash\"}");
        when(requestMapper.selectById(93L)).thenReturn(request);
        when(requestMapper.selectMine(42L)).thenReturn(List.of(request));
        OaSignOnboardImportRow sourceRow = new OaSignOnboardImportRow();
        sourceRow.setRowId(71L);
        sourceRow.setBatchId(31L);
        sourceRow.setEmployeeId(42L);
        sourceRow.setDataRequestId(93L);
        when(rowMapper.selectById(71L)).thenReturn(sourceRow);
        SecurityContextHolder.setUserId("42");

        ObjectMapper json = new ObjectMapper();
        String detailJson = json.writeValueAsString(service.detail(93L, null));
        String mineJson = json.writeValueAsString(service.mine());

        assertThat(detailJson).doesNotContain("approvedHrValues", "INTERN", "COMMERCIAL");
        assertThat(mineJson).doesNotContain("approvedHrValues", "INTERN", "COMMERCIAL");
        assertThat(detailJson).contains("段继康").doesNotContain("profileFactsHash",
                "secret-profile-hash", "matchedLegalEntityId", "matchedSealId",
                "matchedSealImageUrl", "secret-seal-hash");
        assertThat(mineJson).doesNotContain("profileFactsHash", "matchedSealImageUrl",
                "secret-seal-hash");
    }

    @Test
    void signatureFirstViewUsesFrozenPlannedDocumentsAndNeverRecomputesCurrentPlan()
    {
        OaSignOnboardDataRequestMapper requestMapper = mock(OaSignOnboardDataRequestMapper.class);
        OaSignOnboardImportRowMapper rowMapper = mock(OaSignOnboardImportRowMapper.class);
        OaSignOnboardImportService importService = mock(OaSignOnboardImportService.class);
        IOaSignPackageService packageService = mock(IOaSignPackageService.class);
        OaSignOnboardDataRequestService service = new OaSignOnboardDataRequestService(
                requestMapper, rowMapper, importService, mock(OaSignHrAccessService.class),
                mock(RemoteUserService.class), new ObjectMapper(),
                packageService);
        OaSignOnboardDataRequest request = recoveryRequest("COMPLETED");
        request.setSigningSequence("SIGNATURE_FIRST");
        request.setSignatureSampleHash("a".repeat(64));
        request.setSignatureSampleTime(new Date());
        Map<String, Object> facts = new LinkedHashMap<>();
        facts.put("employeeName", "段继康");
        facts.put("salaryTotal", "9000");
        OaSignOnboardImportService.EmployeeConfirmationSnapshot frozen =
                new OaSignOnboardImportService.EmployeeConfirmationSnapshot(
                        OaSignOnboardImportService.CONFIRMATION_SNAPSHOT_VERSION, facts,
                        List.of("劳动合同", "薪酬结构确认书（B版）"), "{}", "h");
        when(requestMapper.selectById(93L)).thenReturn(request);
        when(importService.requireFrozenEmployeeConfirmation(request)).thenReturn(frozen);
        OaSignOnboardImportRow sourceRow = new OaSignOnboardImportRow();
        sourceRow.setRowId(71L);
        sourceRow.setBatchId(31L);
        sourceRow.setEmployeeId(42L);
        sourceRow.setDataRequestId(93L);
        when(rowMapper.selectById(71L)).thenReturn(sourceRow);
        SecurityContextHolder.setUserId("42");

        OaSignOnboardDataRequestView view = service.detail(93L, null);

        assertThat(view.getPlannedDocumentNames())
                .containsExactly("劳动合同", "薪酬结构确认书（B版）");
        assertThat(view.getFactSnapshot()).containsEntry("salaryTotal", "9000");
        verify(importService, never()).plannedDocumentNames(any());
    }

    @Test
    void signatureFirstSubmitReplaysSameRequestAndRejectsDifferentPayload()
    {
        OaSignOnboardDataRequestMapper requestMapper = mock(OaSignOnboardDataRequestMapper.class);
        OaSignOnboardImportRowMapper rowMapper = mock(OaSignOnboardImportRowMapper.class);
        OaSignOnboardImportService importService = mock(OaSignOnboardImportService.class);
        IOaSignPackageService packageService = mock(IOaSignPackageService.class);
        OaSignOnboardDataRequestService service = new OaSignOnboardDataRequestService(
                requestMapper, rowMapper, importService, mock(OaSignHrAccessService.class),
                mock(RemoteUserService.class), new ObjectMapper(),
                packageService);
        OaSignOnboardDataRequest request = recoveryRequest("PENDING_EMPLOYEE");
        request.setSigningSequence("SIGNATURE_FIRST");
        request.setAllowedFieldsJson("[]");
        request.setVersion(1L);
        OaSignOnboardImportRow row = new OaSignOnboardImportRow();
        row.setRowId(71L); row.setBatchId(31L); row.setEmployeeId(42L); row.setVersion(3L);
        row.setTaskId(801L); row.setPackageId(901L); row.setDataRequestId(93L);
        when(requestMapper.selectById(93L)).thenReturn(request);
        when(rowMapper.selectById(71L)).thenReturn(row);
        when(rowMapper.updateStatus(71L, "READY_TO_GENERATE", 3L)).thenReturn(1);
        when(requestMapper.submit(eq(93L), eq(42L), eq("{}"), any(), eq("sig-1"),
                any(), any(), any(), any(), eq("COMPLETED"), eq("COMPLETED"), eq(1L)))
                .thenAnswer(invocation -> {
                    request.setFactConfirmationText(invocation.getArgument(3));
                    request.setSignatureRequestId(invocation.getArgument(4));
                    request.setSignaturePayloadHash(invocation.getArgument(5));
                    request.setSignatureSampleBytes(invocation.getArgument(6));
                    request.setSignatureSampleHash(invocation.getArgument(7));
                    request.setSignatureSampleTime(invocation.getArgument(8));
                    request.setStatus("COMPLETED"); request.setVersion(2L);
                    return 1;
                });
        Map<String, Object> facts = Map.of("employeeName", "段继康");
        OaSignOnboardImportService.EmployeeConfirmationSnapshot frozen =
                new OaSignOnboardImportService.EmployeeConfirmationSnapshot(
                        OaSignOnboardImportService.CONFIRMATION_SNAPSHOT_VERSION, facts,
                        List.of("劳动合同"), "{}", "h");
        when(importService.requireFrozenEmployeeConfirmation(request)).thenReturn(frozen);
        SecurityContextHolder.setUserId("42");
        OaSignOnboardDataSubmitRequest action = signatureAction("sig-1", (byte) 1);

        OaSignOnboardDataRequestView first = service.submit(93L, action);
        OaSignOnboardDataRequestView replay = service.submit(93L, action);

        assertThat(first.getStatus()).isEqualTo("COMPLETED");
        assertThat(first.getTaskId()).isEqualTo(801L);
        assertThat(first.getPackageId()).isEqualTo(901L);
        assertThat(replay.getStatus()).isEqualTo("COMPLETED");
        row.setDataRequestId(94L);
        assertThatThrownBy(() -> service.submit(93L, action))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("绑定不一致");
        row.setDataRequestId(93L);
        row.setBatchId(32L);
        assertThatThrownBy(() -> service.submit(93L, action))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("绑定不一致");
        row.setBatchId(31L);
        verify(requestMapper).submit(eq(93L), eq(42L), eq("{}"), any(), eq("sig-1"),
                any(), any(), any(), any(), eq("COMPLETED"), eq("COMPLETED"), eq(1L));
        verify(rowMapper).updateStatus(71L, "READY_TO_GENERATE", 3L);
        verify(packageService, times(2)).recordStagedSignatureFirstSampleForSystem(
                eq(901L), eq(801L), eq(93L), eq("sig-1"), any(byte[].class),
                any(String.class), any(Date.class));
        assertThatThrownBy(() -> service.submit(93L, signatureAction("sig-1", (byte) 2)))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("提交内容不一致");
    }

    @Test
    void rejectedSignatureFirstRequestPreservesCapturedSampleAndOnlyResubmitsFacts()
            throws Exception
    {
        OaSignOnboardDataRequestMapper requestMapper = mock(OaSignOnboardDataRequestMapper.class);
        OaSignOnboardImportRowMapper rowMapper = mock(OaSignOnboardImportRowMapper.class);
        OaSignOnboardImportService importService = mock(OaSignOnboardImportService.class);
        OaSignOnboardDataRequestService service = new OaSignOnboardDataRequestService(
                requestMapper, rowMapper, importService, mock(OaSignHrAccessService.class),
                mock(RemoteUserService.class), new ObjectMapper(),
                mock(IOaSignPackageService.class));
        OaSignOnboardDataRequest request = recoveryRequest("REJECTED");
        request.setSigningSequence("SIGNATURE_FIRST");
        request.setFactConfirmationText(
                "本人已核对本入职签约包的合同生成信息，并完成本合同包唯一一次手写签名");
        request.setSignatureRequestId("sig-original");
        request.setSignaturePayloadHash("c".repeat(64));
        byte[] originalSample = SignatureImageTestFixtures.signature(7);
        request.setSignatureSampleBytes(originalSample);
        request.setSignatureSampleHash(HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(originalSample)));
        request.setSignatureSampleTime(new Date(2_000L));
        OaSignOnboardImportRow row = new OaSignOnboardImportRow();
        row.setRowId(71L); row.setBatchId(31L); row.setEmployeeId(42L); row.setVersion(3L);
        row.setDataRequestId(93L);
        when(requestMapper.selectById(93L)).thenReturn(request);
        when(rowMapper.selectById(71L)).thenReturn(row);
        when(rowMapper.updateStatus(71L, "PENDING_HR_REVIEW", 3L)).thenReturn(1);
        when(requestMapper.resubmitPreservingSignature(eq(93L), eq(42L),
                eq("{\"currentAddress\":\"浙江省舟山市新城路2号\"}"),
                any(String.class), eq("c".repeat(64)), eq("SUBMITTED"),
                eq("NOT_STARTED"), eq(6L))).thenAnswer(invocation -> {
                    request.setSubmittedValuesJson(invocation.getArgument(2));
                    request.setSignaturePayloadHash(invocation.getArgument(3));
                    request.setStatus("SUBMITTED");
                    request.setVersion(7L);
                    return 1;
                });
        when(importService.requireFrozenEmployeeConfirmation(request)).thenReturn(
                frozenConfirmation());
        SecurityContextHolder.setUserId("42");

        OaSignOnboardDataSubmitRequest overwrite = signatureAction("sig-new", (byte) 9);
        overwrite.setVersion(6L);
        overwrite.setCurrentAddress("浙江省舟山市新城路2号");
        assertThatThrownBy(() -> service.submit(93L, overwrite))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("不得再次上传或覆盖");

        OaSignOnboardDataSubmitRequest correction = new OaSignOnboardDataSubmitRequest();
        correction.setVersion(6L);
        correction.setCurrentAddress("浙江省舟山市新城路2号");
        OaSignOnboardDataRequestView result = service.submit(93L, correction);
        OaSignOnboardDataRequestView replay = service.submit(93L, correction);

        assertThat(result.getStatus()).isEqualTo("SUBMITTED");
        assertThat(replay.getStatus()).isEqualTo("SUBMITTED");
        assertThat(result.isSignatureCaptured()).isTrue();
        assertThat(request.getSignatureRequestId()).isEqualTo("sig-original");
        assertThat(request.getSignatureSampleBytes()).containsExactly(originalSample);
        assertThat(request.getSignaturePayloadHash()).hasSize(64)
                .isNotEqualTo("c".repeat(64));
        verify(requestMapper, times(1)).resubmitPreservingSignature(eq(93L), eq(42L),
                eq("{\"currentAddress\":\"浙江省舟山市新城路2号\"}"),
                eq(request.getSignaturePayloadHash()), eq("c".repeat(64)),
                eq("SUBMITTED"), eq("NOT_STARTED"), eq(6L));
        verify(requestMapper, never()).submit(any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void firstSignatureFirstSubmissionStillRequiresTheOnlySignature()
    {
        OaSignOnboardDataRequestMapper requestMapper = mock(OaSignOnboardDataRequestMapper.class);
        OaSignOnboardImportRowMapper rowMapper = mock(OaSignOnboardImportRowMapper.class);
        OaSignOnboardDataRequestService service = new OaSignOnboardDataRequestService(
                requestMapper, rowMapper,
                mock(OaSignOnboardImportService.class), mock(OaSignHrAccessService.class),
                mock(RemoteUserService.class), new ObjectMapper(),
                mock(IOaSignPackageService.class));
        OaSignOnboardDataRequest request = recoveryRequest("PENDING_EMPLOYEE");
        request.setSigningSequence("SIGNATURE_FIRST");
        request.setAllowedFieldsJson("[]");
        when(requestMapper.selectById(93L)).thenReturn(request);
        OaSignOnboardImportRow row = new OaSignOnboardImportRow();
        row.setRowId(71L);
        row.setBatchId(31L);
        row.setEmployeeId(42L);
        row.setDataRequestId(93L);
        when(rowMapper.selectById(71L)).thenReturn(row);
        SecurityContextHolder.setUserId("42");
        OaSignOnboardDataSubmitRequest action = new OaSignOnboardDataSubmitRequest();
        action.setVersion(6L);

        assertThatThrownBy(() -> service.submit(93L, action))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("请确认输入");
        verify(requestMapper, never()).resubmitPreservingSignature(
                any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void invalidFirstSignatureIsRejectedBeforeAnySubmissionOrPackageWrite()
    {
        OaSignOnboardDataRequestMapper requestMapper = mock(OaSignOnboardDataRequestMapper.class);
        OaSignOnboardImportRowMapper rowMapper = mock(OaSignOnboardImportRowMapper.class);
        IOaSignPackageService packages = mock(IOaSignPackageService.class);
        OaSignOnboardDataRequestService service = new OaSignOnboardDataRequestService(
                requestMapper, rowMapper, mock(OaSignOnboardImportService.class),
                mock(OaSignHrAccessService.class), mock(RemoteUserService.class), new ObjectMapper(), packages);
        OaSignOnboardDataRequest request = recoveryRequest("PENDING_EMPLOYEE");
        request.setSigningSequence("SIGNATURE_FIRST");
        request.setAllowedFieldsJson("[]");
        OaSignOnboardImportRow row = new OaSignOnboardImportRow();
        row.setRowId(71L); row.setBatchId(31L); row.setEmployeeId(42L); row.setDataRequestId(93L);
        when(requestMapper.selectById(93L)).thenReturn(request);
        when(rowMapper.selectById(71L)).thenReturn(row);
        SecurityContextHolder.setUserId("42");
        byte[] header = java.util.Arrays.copyOf(SignatureImageTestFixtures.signature(1), 8);
        byte[] transparent = SignatureImageTestFixtures.png(
                new java.awt.image.BufferedImage(320, 120, java.awt.image.BufferedImage.TYPE_INT_ARGB));
        for (byte[] invalid : new byte[][] {header, transparent})
        {
            OaSignOnboardDataSubmitRequest action = signatureAction("sig-1", (byte) 1);
            action.setSignatureDataUrl("data:image/png;base64," + Base64.getEncoder().encodeToString(invalid));
            assertThatThrownBy(() -> service.submit(93L, action)).isInstanceOf(ServiceException.class);
        }
        assertThat(request.getStatus()).isEqualTo("PENDING_EMPLOYEE");
        verify(requestMapper, never()).submit(any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any());
        org.mockito.Mockito.verifyNoInteractions(packages);
    }

    @Test
    void damagedHistoricalSignatureCannotBeSilentlyOverwritten() throws Exception
    {
        OaSignOnboardDataRequestMapper requestMapper = mock(OaSignOnboardDataRequestMapper.class);
        OaSignOnboardImportRowMapper rowMapper = mock(OaSignOnboardImportRowMapper.class);
        OaSignOnboardDataRequestService service = new OaSignOnboardDataRequestService(
                requestMapper, rowMapper, mock(OaSignOnboardImportService.class),
                mock(OaSignHrAccessService.class), mock(RemoteUserService.class),
                new ObjectMapper(), mock(IOaSignPackageService.class));
        OaSignOnboardDataRequest request = recoveryRequest("REJECTED");
        request.setSigningSequence("SIGNATURE_FIRST");
        request.setAllowedFieldsJson("[]");
        OaSignOnboardDataSubmitRequest action = signatureAction("new-signature", (byte) 2);
        byte[] header = java.util.Arrays.copyOf(SignatureImageTestFixtures.signature(1), 8);
        request.setFactConfirmationText(action.getFactConfirmationText());
        request.setSignatureRequestId("old-signature");
        request.setSignatureSampleBytes(header);
        request.setSignatureSampleHash(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(header)));
        request.setSignaturePayloadHash("c".repeat(64));
        request.setSignatureSampleTime(new Date(2_000L));
        OaSignOnboardImportRow row = new OaSignOnboardImportRow();
        row.setRowId(71L); row.setBatchId(31L); row.setEmployeeId(42L); row.setDataRequestId(93L);
        when(requestMapper.selectById(93L)).thenReturn(request);
        when(rowMapper.selectById(71L)).thenReturn(row);
        SecurityContextHolder.setUserId("42");
        assertThatThrownBy(() -> service.submit(93L, action)).hasMessageContaining("请联系HR处理");
        assertThat(request.getSignatureSampleBytes()).containsExactly(header);
        verify(requestMapper, never()).submit(any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any());
        verify(requestMapper, never()).resubmitPreservingSignature(
                any(), any(), any(), any(), any(), any(), any(), any());
    }

    private static OaSignOnboardDataSubmitRequest signatureAction(String requestId, byte tail)
    {
        byte[] png = SignatureImageTestFixtures.signature(tail);
        OaSignOnboardDataSubmitRequest action = new OaSignOnboardDataSubmitRequest();
        action.setVersion(1L);
        action.setFactConfirmationText(
                "本人已核对本入职签约包的合同生成信息，并完成本合同包唯一一次手写签名");
        action.setSignatureRequestId(requestId);
        action.setSignatureDataUrl("data:image/png;base64,"
                + Base64.getEncoder().encodeToString(png));
        return action;
    }

    @Test
    void studentAndNonStudentConditionalFactsMatchServerContract()
    {
        OaSignOnboardDataRequestService service = new OaSignOnboardDataRequestService(
                mock(OaSignOnboardDataRequestMapper.class),
                mock(OaSignOnboardImportRowMapper.class),
                mock(OaSignOnboardImportService.class), mock(OaSignHrAccessService.class),
                mock(RemoteUserService.class), new ObjectMapper(),
                mock(IOaSignPackageService.class));
        List<String> allowed = List.of("studentStatus", "schoolName", "incomeStartYearMonth");
        OaSignOnboardDataSubmitRequest student = new OaSignOnboardDataSubmitRequest();
        student.setStudentStatus("STUDENT"); student.setSchoolName("浙江大学");
        @SuppressWarnings("unchecked")
        Map<String, Object> studentValues = ReflectionTestUtils.invokeMethod(
                service, "employeeValues", student, allowed);
        assertThat(studentValues).containsEntry("schoolName", "浙江大学")
                .doesNotContainKey("incomeStartYearMonth");

        OaSignOnboardDataSubmitRequest nonStudent = new OaSignOnboardDataSubmitRequest();
        nonStudent.setStudentStatus("NON_STUDENT");
        nonStudent.setIncomeStartYearMonth("2026-07");
        @SuppressWarnings("unchecked")
        Map<String, Object> nonStudentValues = ReflectionTestUtils.invokeMethod(
                service, "employeeValues", nonStudent, allowed);
        assertThat(nonStudentValues).containsEntry("incomeStartYearMonth", "2026-07")
                .doesNotContainKey("schoolName");
    }

    private static OaSignOnboardDataRequest recoveryRequest(String status)
    {
        OaSignOnboardDataRequest request = new OaSignOnboardDataRequest();
        request.setRequestId(93L);
        request.setRequestNo("ODR-93");
        request.setBatchId(31L);
        request.setRowId(71L);
        request.setEmployeeId(42L);
        request.setAllowedFieldsJson("[\"currentAddress\"]");
        request.setSubmittedValuesJson("{\"currentAddress\":\"浙江省舟山市新城路1号\"}");
        request.setApprovedHrValuesJson(
                "{\"servicePersonType\":\"INTERN\",\"insuranceType\":\"COMMERCIAL\"}");
        request.setStatus(status);
        request.setProfileSyncRequestId("OPS:71:9");
        request.setProfileBeforeHash("profile-before");
        request.setVersion(6L);
        return request;
    }

    private static OaSignOnboardDataRequest completedStagedRequest() throws Exception
    {
        byte[] sample = SignatureImageTestFixtures.signature(1);
        OaSignOnboardDataRequest request = recoveryRequest("COMPLETED");
        request.setSigningSequence("SIGNATURE_FIRST");
        request.setSignatureRequestId("sig-recovery");
        request.setSignatureSampleBytes(sample);
        request.setSignatureSampleHash(HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(sample)));
        request.setSignatureSampleTime(new Date(1_000L));
        request.setFactSnapshotJson("{}");
        request.setConfirmationSnapshotVersion(
                OaSignOnboardImportService.CONFIRMATION_SNAPSHOT_VERSION);
        request.setConfirmationSnapshotHash("frozen-hash");
        return request;
    }

    private static OaSignOnboardImportRow stagedRow()
    {
        OaSignOnboardImportRow row = new OaSignOnboardImportRow();
        row.setRowId(71L);
        row.setBatchId(31L);
        row.setEmployeeId(42L);
        row.setDataRequestId(93L);
        row.setTaskId(801L);
        row.setPackageId(901L);
        row.setStatus("READY_TO_GENERATE");
        row.setVersion(9L);
        return row;
    }

    private static OaSignOnboardImportService.EmployeeConfirmationSnapshot frozenConfirmation()
    {
        return new OaSignOnboardImportService.EmployeeConfirmationSnapshot(
                OaSignOnboardImportService.CONFIRMATION_SNAPSHOT_VERSION,
                Map.of("employeeName", "测试员工"), List.of("劳动合同"), "{}",
                "frozen-hash");
    }

    private final class RecoveryFixture
    {
        private final OaSignOnboardDataRequestMapper requestMapper =
                mock(OaSignOnboardDataRequestMapper.class);
        private final OaSignOnboardImportRowMapper rowMapper =
                mock(OaSignOnboardImportRowMapper.class);
        private final OaSignOnboardImportService importService =
                mock(OaSignOnboardImportService.class);
        private final RemoteUserService remoteUserService = mock(RemoteUserService.class);
        private final OaSignOnboardGenerationService generationService =
                mock(OaSignOnboardGenerationService.class);
        private final OaSignOnboardDataRequestService service;
        private final OaSignOnboardImportBatch batch = new OaSignOnboardImportBatch();
        private final OaSignOnboardDataRequest approved = recoveryRequest("APPROVED");
        private final OaSignOnboardDataRequest completed = recoveryRequest("COMPLETED");
        private final OaSignOnboardImportRow firstRow = row("PENDING_HR_REVIEW", 9L);
        private final OaSignOnboardImportRow recoveryRow;
        private final boolean alreadyApplied;
        private final AtomicBoolean profileCompleted = new AtomicBoolean();

        private RecoveryFixture(String recoveryStatus, boolean alreadyApplied)
        {
            this.alreadyApplied = alreadyApplied;
            this.recoveryRow = row(recoveryStatus, "PROFILE_SYNC_APPLYING".equals(recoveryStatus) ? 10L : 9L);
            OaSignHrAccessService hrAccessService = mock(OaSignHrAccessService.class);
            service = new OaSignOnboardDataRequestService(requestMapper, rowMapper, importService,
                    hrAccessService, remoteUserService, new ObjectMapper(),
                    mock(IOaSignPackageService.class), generationService);
            SecurityContextHolder.setUserId("101");
            batch.setBatchId(31L);
            batch.setShopDeptId(1171L);
            when(importService.requireHrBatch(31L, 1171L)).thenReturn(batch);
            when(importService.requireBatchRow(31L, 71L)).thenReturn(recoveryRow);
            when(generationService.withCurrentOwnerTaskLock(any(), any(), any(), any()))
                    .thenAnswer(invocation -> {
                        @SuppressWarnings("unchecked")
                        java.util.function.Supplier<Object> work = invocation.getArgument(
                                3, java.util.function.Supplier.class);
                        return work.get();
                    });
            when(requestMapper.updateProfileSync(93L, "COMPLETED",
                    "profile-before", "profile-after", 6L)).thenAnswer(invocation -> {
                profileCompleted.set(true);
                return 1;
            });
            when(remoteUserService.supplementSigningProfile(any(ReviewedSignProfileSupplement.class),
                    eq(SecurityConstants.INNER))).thenReturn(R.ok(
                            new ReviewedSignProfileSupplementResult(false, true,
                                    "profile-before", "profile-after", "currentAddress")));
        }

        private void stubRequestLifecycle()
        {
            when(requestMapper.selectById(93L)).thenAnswer(invocation ->
                    profileCompleted.get() ? completed : approved);
            if (alreadyApplied)
            {
                when(rowMapper.selectById(71L)).thenReturn(recoveryRow, recoveryRow);
                when(importService.snapshot(recoveryRow)).thenReturn(snapshot(true));
            }
            else if ("PROFILE_SYNC_APPLYING".equals(recoveryRow.getStatus()))
            {
                when(rowMapper.selectById(71L)).thenReturn(recoveryRow, recoveryRow);
                when(importService.snapshot(recoveryRow)).thenReturn(snapshot(false));
            }
            else
            {
                OaSignOnboardImportRow applying = row("PROFILE_SYNC_APPLYING", 10L);
                when(rowMapper.selectById(71L)).thenReturn(firstRow, applying);
                when(importService.snapshot(any(OaSignOnboardImportRow.class)))
                        .thenReturn(snapshot(false));
                when(rowMapper.updateStatus(71L, "PROFILE_SYNC_APPLYING", 9L)).thenReturn(1);
            }
        }

        private OaSignOnboardDataReviewRequest action()
        {
            OaSignOnboardDataReviewRequest action = new OaSignOnboardDataReviewRequest();
            action.setAction("APPROVE");
            action.setVersion(6L);
            action.setServicePersonType("INTERN");
            action.setInsuranceType("COMMERCIAL");
            return action;
        }

        private OaSignOnboardImportRow row(String status, Long version)
        {
            OaSignOnboardImportRow row = new OaSignOnboardImportRow();
            row.setRowId(71L);
            row.setBatchId(31L);
            row.setEmployeeId(42L);
            row.setDataRequestId(93L);
            row.setStatus(status);
            row.setVersion(version);
            return row;
        }

        private OaSignOnboardContractSnapshot snapshot(boolean applied)
        {
            OaSignOnboardContractSnapshot snapshot = new OaSignOnboardContractSnapshot();
            snapshot.setContractTypeCode("SERVICE_CONTRACT");
            snapshot.setProfileFactsHash(applied ? "profile-after" : "profile-before");
            if (applied)
            {
                snapshot.setCurrentAddress("浙江省舟山市新城路1号");
                snapshot.setServicePersonType("INTERN");
                snapshot.setInsuranceType("COMMERCIAL");
            }
            return snapshot;
        }
    }
}
