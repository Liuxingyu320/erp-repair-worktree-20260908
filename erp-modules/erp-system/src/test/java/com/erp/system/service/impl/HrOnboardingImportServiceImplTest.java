package com.erp.system.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.erp.common.core.exception.ServiceException;
import com.erp.system.domain.HrOnboardingImportBatch;
import com.erp.system.domain.HrOnboardingImportRow;
import com.erp.system.domain.vo.HrOnboardingImportConfirmRequest;
import com.erp.system.domain.vo.HrOnboardingImportPreviewVo;
import com.erp.system.mapper.HrOnboardingImportMapper;
import com.erp.system.service.ISysConfigService;
import com.erp.system.support.HrSensitiveFieldMasker;
import com.erp.system.controller.HrOnboardingImportController;
import com.erp.common.log.annotation.Log;
import com.erp.common.log.enums.BusinessType;
import com.erp.common.security.annotation.RequiresPermissions;
import org.springframework.web.bind.annotation.RequestMapping;

@ExtendWith(MockitoExtension.class)
class HrOnboardingImportServiceImplTest
{
    @Mock private HrOnboardingImportMapper mapper;
    @Mock private HrOnboardingImportRowProcessor processor;
    @Mock private ISysConfigService configService;
    private HrOnboardingImportServiceImpl service;

    @BeforeEach
    void setUp()
    {
        org.mockito.Mockito.lenient().when(configService.selectConfigByKey("hr.onboarding.import_retention_days"))
                .thenReturn("30");
        org.mockito.Mockito.lenient().when(mapper.markRowFailure(any(),any(),any(),any(),any(),any())).thenReturn(1);
        org.mockito.Mockito.lenient().when(mapper.recordRowDecision(any(),any(),any(),any(),any())).thenReturn(1);
        org.mockito.Mockito.lenient().when(mapper.heartbeatProcessingBatch(any(),any(),any(),any())).thenReturn(1);
        org.mockito.Mockito.lenient().when(mapper.finishBatch(any(),any(),any(Integer.class),any(Integer.class),any(),any(),any())).thenReturn(1);
        service = new HrOnboardingImportServiceImpl(mapper, null, processor, configService,
                new HrSensitiveFieldMasker(), Clock.fixed(Instant.parse("2026-07-11T00:00:00Z"), ZoneOffset.UTC),
                () -> 7L, () -> false);
    }

    @Test
    void cleanupIsBoundedChildFirstAndOnlyTargetsExpiredUnconfirmedStatuses()
    {
        service.deleteExpiredUnconfirmedBatches(Date.from(Instant.parse("2026-06-11T00:00:00Z")), 1000);

        verify(mapper).deleteRowsForExpiredBatches(any(Date.class), eq(1000),
                eq(Arrays.asList("PREVIEWED", "FAILED")));
        verify(mapper).deleteExpiredBatches(any(Date.class), eq(1000),
                eq(Arrays.asList("PREVIEWED", "FAILED")));
    }

    @Test
    void previewRejectsOversizeUploadBeforeReadingOrParsing() throws Exception
    {
        org.springframework.web.multipart.MultipartFile file=
                org.mockito.Mockito.mock(org.springframework.web.multipart.MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);when(file.getSize()).thenReturn(10L*1024L*1024L+1L);

        assertThatThrownBy(()->service.preview(file,"operator"))
                .isInstanceOf(ServiceException.class).hasMessage("IMPORT_FILE_SIZE_LIMIT_EXCEEDED");

        verify(file,never()).getBytes();
    }

    @Test
    void getRequiresCreatorOrAdministratorAndAlwaysMasksSensitiveStagingValues()
    {
        HrOnboardingImportBatch batch = batch("PREVIEWED", 0, 7L);
        HrOnboardingImportRow row = row(11L, "IMPORTABLE");
        row.getPayload().setPhoneNumber("13800138000");
        row.getPayload().setIdNumber("350203199001011234");
        row.getPayload().setBankAccount("6222020200001234567");
        row.getPayload().setRegisteredResidence("福建省厦门市思明区");
        row.setCandidateUserId(88L); row.setCandidateOnboardingId(77L);
        when(mapper.selectBatchById(1L)).thenReturn(batch);
        when(mapper.selectRowsByBatchId(1L)).thenReturn(Collections.singletonList(row));

        HrOnboardingImportPreviewVo preview = service.getBatch(1L);

        assertThat(preview.getRows()).singleElement().satisfies(safe -> {
            assertThat(safe.getPhoneNumberMasked()).isEqualTo("138****8000");
            assertThat(safe.getIdNumberMasked()).doesNotContain("350203199001011234");
            assertThat(safe.getBankAccountMasked()).doesNotContain("6222020200001234567");
            assertThat(safe.getRegisteredResidenceMasked()).doesNotContain("福建省厦门市思明区");
            assertThat(read(safe,"getCandidateUserId")).isEqualTo(88L);
            assertThat(read(safe,"getCandidateOnboardingId")).isEqualTo(77L);
        });

        batch.setCreatorUserId(99L);
        assertThatThrownBy(() -> service.getBatch(1L)).isInstanceOf(ServiceException.class);
    }

    @Test
    void confirmAtomicallyClaimsBatchContinuesMixedRowsAndReturnsMachineReadableTotals()
    {
        HrOnboardingImportBatch batch = batch("PREVIEWED", 3, 7L);
        HrOnboardingImportRow first = row(11L, "IMPORTABLE");
        HrOnboardingImportRow second = row(12L, "WARNING");
        when(mapper.selectBatchById(1L)).thenReturn(batch, batch("COMPLETED_WITH_ERRORS", 4, 7L));
        when(mapper.selectRowsByBatchId(1L)).thenReturn(Arrays.asList(first, second));
        when(mapper.claimPreviewedBatch(1L, 3, 7L, "operator")).thenReturn(1);
        when(processor.process(eq(11L), any(), eq("operator"),eq(4))).thenReturn(101L);
        when(processor.process(eq(12L), any(), eq("operator"),eq(4))).thenThrow(new ServiceException("STALE_CONFLICT"));

        HrOnboardingImportConfirmRequest request = new HrOnboardingImportConfirmRequest();
        request.setVersion(3);
        request.setRows(Arrays.asList(decision(11L, "IMPORT", null), decision(12L, "CONTINUE", null)));
        HrOnboardingImportPreviewVo result = service.confirmBatch(1L, request, "operator");

        verify(mapper).finishBatch(1L,4,1,1,"COMPLETED_WITH_ERRORS",7L,"operator");
        assertThat(org.mockito.Mockito.mockingDetails(mapper).getInvocations())
                .filteredOn(invocation -> invocation.getMethod().getName().equals("markRowFailure"))
                .singleElement().satisfies(invocation -> assertThat(invocation.getArguments())
                        .containsSequence(12L,1L,"STALE_CONFLICT"));
        assertThat(result.getSuccessRows()).isEqualTo(1);
        assertThat(result.getFailureRows()).isEqualTo(1);
        assertThat(result.getErrors()).singleElement().extracting("rowId").isEqualTo(12L);
        assertThat(result.getErrors()).singleElement().extracting("code").isEqualTo("STALE_CONFLICT");
    }

    @Test
    void foreignRowDecisionIsRejectedBeforeClaimWithoutPhantomFailure()
    {
        HrOnboardingImportBatch before = batch("PREVIEWED", 3, 7L);
        HrOnboardingImportRow owned = row(11L, "IMPORTABLE");
        when(mapper.selectBatchById(1L)).thenReturn(before);
        when(mapper.selectRowsByBatchId(1L)).thenReturn(Collections.singletonList(owned));
        HrOnboardingImportConfirmRequest request = new HrOnboardingImportConfirmRequest();
        request.setVersion(3); request.setRows(Collections.singletonList(decision(999L, "IMPORT", null)));

        assertThatThrownBy(()->service.confirmBatch(1L,request,"operator"))
                .isInstanceOf(ServiceException.class).hasMessage("ROW_NOT_IN_BATCH");

        verify(processor, never()).process(any(), any(), any(),any());
        verify(mapper,never()).claimPreviewedBatch(any(),any(),any(),any());
        verify(mapper,never()).markRowFailure(any(),any(),any(),any(),any(),any());
    }

    @Test
    void repeatedConfirmationReturnsStoredResultWithoutProcessingAgain()
    {
        HrOnboardingImportBatch completed = batch("COMPLETED", 4, 7L);
        completed.setSuccessRows(2); completed.setFailureRows(0);
        when(mapper.selectBatchById(1L)).thenReturn(completed);
        when(mapper.selectRowsByBatchId(1L)).thenReturn(Collections.emptyList());

        HrOnboardingImportConfirmRequest request = new HrOnboardingImportConfirmRequest();
        request.setVersion(3);
        HrOnboardingImportPreviewVo result = service.confirmBatch(1L, request, "operator");

        assertThat(result.getSuccessRows()).isEqualTo(2);
        verify(mapper, never()).claimPreviewedBatch(any(), any(), any(), any());
        verify(processor, never()).process(any(), any(), any(),any());
    }

    @Test
    void replayReconstructsStructuredErrorsFromPersistedFailedRows()
    {
        HrOnboardingImportBatch completed=batch("COMPLETED_WITH_ERRORS",4,7L);
        completed.setSuccessRows(0);completed.setFailureRows(1);
        HrOnboardingImportRow failed=row(11L,"WARNING");failed.setRowStatus("FAILED");
        failed.setResultCode("STALE_CONFLICT");failed.setResultMessage("冲突信息已变化");
        when(mapper.selectBatchById(1L)).thenReturn(completed);
        when(mapper.selectRowsByBatchId(1L)).thenReturn(Collections.singletonList(failed));

        HrOnboardingImportPreviewVo result=service.getBatch(1L);

        assertThat(result.getErrors()).singleElement().satisfies(error -> assertThat(error)
                .containsEntry("rowId",11L).containsEntry("sourceRowNumber",11)
                .containsEntry("code","STALE_CONFLICT").containsEntry("message","冲突信息已变化"));
    }

    @Test
    void recentProcessingLeaseReturnsSnapshotWithoutStealingOrProcessing()
    {
        HrOnboardingImportBatch processing=batch("PROCESSING",4,7L);
        processing.setUpdateTime(Date.from(Instant.parse("2026-07-10T23:59:30Z")));
        when(mapper.selectBatchById(1L)).thenReturn(processing);
        when(mapper.selectRowsByBatchId(1L)).thenReturn(Collections.emptyList());

        HrOnboardingImportPreviewVo result=service.confirmBatch(1L,null,"operator");

        assertThat(result.getStatus()).isEqualTo("PROCESSING");
        verify(mapper,never()).claimStaleProcessingBatch(any(),any(),any(),any(),any());
        verify(processor,never()).process(any(),any(),any(),any());
    }

    @Test
    void concurrentClaimRaceReturnsTheRecentProcessingSnapshot()
    {
        HrOnboardingImportBatch previewed=batch("PREVIEWED",3,7L);
        HrOnboardingImportBatch processing=batch("PROCESSING",4,7L);
        processing.setUpdateTime(Date.from(Instant.parse("2026-07-10T23:59:30Z")));
        when(mapper.selectBatchById(1L)).thenReturn(previewed,processing,processing);
        when(mapper.selectRowsByBatchId(1L)).thenReturn(Collections.emptyList());
        when(mapper.claimPreviewedBatch(1L,3,7L,"operator")).thenReturn(0);
        HrOnboardingImportConfirmRequest request=new HrOnboardingImportConfirmRequest();
        request.setVersion(3);request.setRows(Collections.emptyList());

        HrOnboardingImportPreviewVo result=service.confirmBatch(1L,request,"operator");

        assertThat(result.getStatus()).isEqualTo("PROCESSING");
        verify(processor,never()).process(any(),any(),any(),any());
    }

    @Test
    void staleProcessingLeaseResumesOnlyPersistedPendingDecisionsAndRecomputesTotals()
    {
        HrOnboardingImportBatch processing=batch("PROCESSING",4,7L);
        processing.setUpdateTime(Date.from(Instant.parse("2026-07-10T23:50:00Z")));
        HrOnboardingImportBatch completed=batch("COMPLETED_WITH_ERRORS",5,7L);
        HrOnboardingImportRow prior=row(10L,"IMPORTABLE");prior.setRowStatus("SUCCESS");
        HrOnboardingImportRow pending=row(11L,"WARNING");pending.setDecision("CONTINUE");
        when(mapper.selectBatchById(1L)).thenReturn(processing,completed);
        when(mapper.selectRowsByBatchId(1L)).thenReturn(Arrays.asList(prior,pending));
        when(mapper.claimStaleProcessingBatch(eq(1L),eq(4),eq(7L),any(Date.class),eq("operator"))).thenReturn(1);
        when(processor.process(eq(11L),any(),eq("operator"),eq(5))).thenThrow(new ServiceException("STALE_CONFLICT"));

        service.confirmBatch(1L,null,"operator");

        verify(processor,never()).process(eq(10L),any(),any(),any());
        verify(processor).process(eq(11L),any(),eq("operator"),eq(5));
        verify(mapper).finishBatch(1L,5,1,1,"COMPLETED_WITH_ERRORS",7L,"operator");
    }

    @Test
    void staleResumeIgnoresRetryRequestAndUsesPersistedDecisionAuthoritatively()
    {
        HrOnboardingImportBatch processing=batch("PROCESSING",4,7L);
        processing.setUpdateTime(Date.from(Instant.parse("2026-07-10T23:50:00Z")));
        HrOnboardingImportBatch completed=batch("COMPLETED",5,7L);
        HrOnboardingImportRow pending=row(11L,"BINDABLE_ACCOUNT");pending.setDecision("BIND_EXISTING");
        pending.setBindUserId(88L);
        when(mapper.selectBatchById(1L)).thenReturn(processing,completed);
        when(mapper.selectRowsByBatchId(1L)).thenReturn(Collections.singletonList(pending));
        when(mapper.claimStaleProcessingBatch(eq(1L),eq(4),eq(7L),any(Date.class),eq("operator"))).thenReturn(1);
        when(processor.process(eq(11L),any(),eq("operator"),eq(5))).thenReturn(101L);
        HrOnboardingImportConfirmRequest retry=new HrOnboardingImportConfirmRequest();retry.setVersion(4);
        retry.setRows(Collections.singletonList(decision(11L,"IMPORT",999L)));

        service.confirmBatch(1L,retry,"operator");

        org.mockito.ArgumentCaptor<HrOnboardingImportConfirmRequest.RowDecision> used=
                org.mockito.ArgumentCaptor.forClass(HrOnboardingImportConfirmRequest.RowDecision.class);
        verify(processor).process(eq(11L),used.capture(),eq("operator"),eq(5));
        assertThat(used.getValue().getDecision()).isEqualTo("BIND_EXISTING");
        assertThat(used.getValue().getBindUserId()).isEqualTo(88L);
    }

    @Test
    void lostRowLeaseAbortsAttemptWithoutMarkingFailureOrFinishingBatch()
    {
        HrOnboardingImportBatch before=batch("PREVIEWED",3,7L);HrOnboardingImportRow pending=row(11L,"IMPORTABLE");
        when(mapper.selectBatchById(1L)).thenReturn(before);when(mapper.selectRowsByBatchId(1L))
                .thenReturn(Collections.singletonList(pending));
        when(mapper.claimPreviewedBatch(1L,3,7L,"operator")).thenReturn(1);
        when(processor.process(eq(11L),any(),eq("operator"),eq(4)))
                .thenThrow(new ServiceException("IMPORT_PROCESSING_LEASE_LOST"));
        HrOnboardingImportConfirmRequest request=new HrOnboardingImportConfirmRequest();request.setVersion(3);
        request.setRows(Collections.singletonList(decision(11L,"IMPORT",null)));

        assertThatThrownBy(()->service.confirmBatch(1L,request,"operator"))
                .isInstanceOf(ServiceException.class).hasMessage("IMPORT_PROCESSING_LEASE_LOST");

        verify(mapper,never()).markRowFailure(any(),any(),any(),any(),any(),any());
        verify(mapper,never()).finishBatch(any(),any(),any(Integer.class),any(Integer.class),any(),any(),any());
    }

    @Test
    void administratorCanClaimAndConfirmAnotherCreatorsBatch()
    {
        service = new HrOnboardingImportServiceImpl(mapper, null, processor, configService,
                new HrSensitiveFieldMasker(), Clock.fixed(Instant.parse("2026-07-11T00:00:00Z"), ZoneOffset.UTC),
                () -> 7L, () -> true);
        HrOnboardingImportBatch batch = batch("PREVIEWED", 2, 99L);
        when(mapper.selectBatchById(1L)).thenReturn(batch, batch("COMPLETED", 3, 99L));
        when(mapper.selectRowsByBatchId(1L)).thenReturn(Collections.emptyList());
        when(mapper.claimPreviewedBatch(1L, 2, 99L, "admin")).thenReturn(1);
        HrOnboardingImportConfirmRequest request = new HrOnboardingImportConfirmRequest();
        request.setVersion(2); request.setRows(Collections.emptyList());

        service.confirmBatch(1L, request, "admin");

        verify(mapper).claimPreviewedBatch(1L, 2, 99L, "admin");
    }

    @Test
    void duplicateRowDecisionsAreRejectedBeforeTheBatchIsClaimed()
    {
        HrOnboardingImportBatch batch = batch("PREVIEWED", 2, 7L);
        when(mapper.selectBatchById(1L)).thenReturn(batch);
        HrOnboardingImportConfirmRequest request = new HrOnboardingImportConfirmRequest();
        request.setVersion(2);
        request.setRows(Arrays.asList(decision(11L, "IMPORT", null), decision(11L, "IMPORT", null)));

        assertThatThrownBy(() -> service.confirmBatch(1L, request, "operator"))
                .isInstanceOf(ServiceException.class).hasMessageContaining("DUPLICATE_ROW_DECISION");

        verify(mapper, never()).claimPreviewedBatch(any(), any(), any(), any());
    }

    @Test
    void controllerExposesOnlyFiveSafeEndpointsWithExactPermissionsAndPiiSafeLogs() throws Exception
    {
        RequestMapping root = HrOnboardingImportController.class.getAnnotation(RequestMapping.class);
        assertThat(root.value()).containsExactly("/hr/onboarding/import");
        assertEndpoint("preview", "hr:onboarding:import:preview", BusinessType.IMPORT, true);
        assertEndpoint("get", "hr:onboarding:import:preview", null, false);
        assertEndpoint("confirm", "hr:onboarding:import:confirm", BusinessType.IMPORT, true);
        assertEndpoint("template", "hr:onboarding:import:template", BusinessType.EXPORT, true);
        assertEndpoint("errors", "hr:onboarding:import:preview", BusinessType.EXPORT, true);
        assertThat(HrOnboardingImportController.class.getDeclaredMethods()).hasSize(5);
    }

    private void assertEndpoint(String methodName, String permission, BusinessType businessType, boolean logged)
            throws Exception
    {
        java.lang.reflect.Method method = Arrays.stream(HrOnboardingImportController.class.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(methodName)).findFirst().orElseThrow();
        assertThat(method.getAnnotation(RequiresPermissions.class).value()).containsExactly(permission);
        Log log = method.getAnnotation(Log.class);
        if (!logged) { assertThat(log).isNull(); return; }
        assertThat(log.businessType()).isEqualTo(businessType);
        assertThat(log.isSaveRequestData()).isFalse();
        assertThat(log.isSaveResponseData()).isFalse();
    }

    private static HrOnboardingImportConfirmRequest.RowDecision decision(Long id, String value, Long bind)
    {
        HrOnboardingImportConfirmRequest.RowDecision row = new HrOnboardingImportConfirmRequest.RowDecision();
        row.setRowId(id); row.setDecision(value); row.setBindUserId(bind); return row;
    }

    private static HrOnboardingImportBatch batch(String status, int version, Long creator)
    {
        HrOnboardingImportBatch batch = new HrOnboardingImportBatch();
        batch.setBatchId(1L); batch.setStatus(status); batch.setVersion(version); batch.setCreatorUserId(creator);
        return batch;
    }

    private static HrOnboardingImportRow row(Long id, String category)
    {
        HrOnboardingImportRow row = new HrOnboardingImportRow();
        row.setRowId(id); row.setBatchId(1L); row.setSourceRowNumber(id.intValue()); row.setCategory(category);
        row.setRowStatus("PREVIEWED");
        row.setPayload(new com.erp.system.domain.HrOnboarding()); return row;
    }

    private static Object read(Object target,String method)
    {
        try { return target.getClass().getMethod(method).invoke(target); }
        catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
}
