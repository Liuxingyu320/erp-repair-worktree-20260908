package com.erp.oa.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.erp.common.core.context.SecurityContextHolder;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.security.shop.ShopScopeService;
import com.erp.oa.constant.OaSignPackageStatus;
import com.erp.oa.constant.OaSignSigningSequence;
import com.erp.oa.constant.OaSignTaskStatus;
import com.erp.oa.domain.OaCompanySealConfig;
import com.erp.oa.domain.OaSignPackage;
import com.erp.oa.domain.OaSignPackageDocument;
import com.erp.oa.domain.OaSignTask;
import com.erp.oa.domain.dto.OaSignPackageFinalizeRequest;
import com.erp.oa.domain.dto.OaSignTaskBatchFinalizeAction;
import com.erp.oa.domain.dto.OaSignTaskBatchFinalizePreviewRequest;
import com.erp.oa.domain.dto.OaSignTaskBatchFinalizeRequest;
import com.erp.oa.domain.vo.OaLegalEntityCandidate;
import com.erp.oa.domain.vo.OaSignCompanyOptions;
import com.erp.oa.domain.vo.OaSignTaskBatchFinalizePreviewResult;
import com.erp.oa.domain.vo.OaSignTaskBatchFinalizeResult;
import com.erp.oa.mapper.OaSignPackageDocumentMapper;
import com.erp.oa.mapper.OaSignPackageMapper;
import com.erp.oa.mapper.OaSignTaskMapper;
import com.erp.oa.service.IOaSignPackageService;
import com.erp.system.api.domain.SysLegalEntity;

@DisplayName("签约任务批量选公司盖章")
class OaSignTaskBatchFinalizeServiceTest
{
    private OaSignHrAccessService hrAccessService;
    private ShopScopeService shopScopeService;
    private OaSignTaskMapper taskMapper;
    private OaSignPackageMapper packageMapper;
    private OaSignPackageDocumentMapper documentMapper;
    private OaSignPlacementPolicyService placementPolicyService;
    private OaSignCompanyService companyService;
    private IOaSignPackageService packageService;
    private OaSignTaskBatchFinalizeService service;

    @BeforeEach
    void setUp()
    {
        SecurityContextHolder.setUserId("101");
        SecurityContextHolder.setUserName("hr");
        hrAccessService = mock(OaSignHrAccessService.class);
        shopScopeService = mock(ShopScopeService.class);
        taskMapper = mock(OaSignTaskMapper.class);
        packageMapper = mock(OaSignPackageMapper.class);
        documentMapper = mock(OaSignPackageDocumentMapper.class);
        placementPolicyService = mock(OaSignPlacementPolicyService.class);
        companyService = mock(OaSignCompanyService.class);
        packageService = mock(IOaSignPackageService.class);
        service = new OaSignTaskBatchFinalizeService(hrAccessService, shopScopeService,
                taskMapper, packageMapper, documentMapper, placementPolicyService,
                companyService, packageService);
        when(shopScopeService.resolveScopeDeptIds(1171L)).thenReturn(List.of(1171L));
    }

    @AfterEach
    void tearDown()
    {
        SecurityContextHolder.remove();
    }

    @Test
    @DisplayName("预览返回版本、Excel建议、可选公司和已验真的推荐合同章")
    void shouldPreviewSafeCompanyAndSealCandidates()
    {
        OaSignTask task = pendingTask(1L, 11L, 5L);
        OaSignPackage signPackage = pendingPackage(11L, 1L, 8L);
        signPackage.setRecommendedCompanySnapshot("测试合同公司");
        signPackage.setRecommendedLegalRepresentativeSnapshot("法人甲");
        signPackage.setRecommendedRegisteredAddressSnapshot("注册地址甲");
        OaSignPackageDocument document = signedDocument(21L);
        SysLegalEntity entity = contractReadyEntity(31L);
        OaCompanySealConfig seal = validSeal(41L, 31L);
        OaLegalEntityCandidate department = new OaLegalEntityCandidate();
        department.setLegalEntityId(31L);
        department.setLegalEntityName("测试合同公司");
        OaSignCompanyOptions options = new OaSignCompanyOptions();
        options.setAutomaticCandidate(department);
        options.setLegalEntities(List.of(entity));
        OaSignCompanyService.CompanyMatchCandidate ranked =
                new OaSignCompanyService.CompanyMatchCandidate(entity,
                        new BigDecimal("0.9000"), List.of());
        OaSignCompanyService.CompanyMatchResult match =
                new OaSignCompanyService.CompanyMatchResult(entity, List.of(ranked), department,
                        "EXCEL_AUTO", new BigDecimal("0.9000"), BigDecimal.ZERO,
                        new BigDecimal("0.6000"), new BigDecimal("0.0800"), false);

        when(taskMapper.selectOaSignTaskById(1L)).thenReturn(task);
        when(packageMapper.selectOaSignPackageById(11L)).thenReturn(signPackage);
        when(documentMapper.selectDocumentsByPackageId(11L)).thenReturn(List.of(document));
        when(placementPolicyService.requiresEmployeeSignature(document)).thenReturn(true);
        when(placementPolicyService.requiresCompanySeal(document)).thenReturn(true);
        when(companyService.options(signPackage)).thenReturn(options);
        when(companyService.matchExcelCompany(any(), any(), any(), eq(1171L))).thenReturn(match);
        when(companyService.contractMasterMissingFields(entity)).thenReturn(List.of());
        when(companyService.listSeals(31L, true)).thenReturn(List.of(seal));
        when(companyService.requireContractReadySeal(41L, 31L)).thenReturn(seal);

        OaSignTaskBatchFinalizePreviewRequest request =
                new OaSignTaskBatchFinalizePreviewRequest();
        request.setTaskIds(List.of(1L));
        OaSignTaskBatchFinalizePreviewResult result = service.preview(request, 1171L);

        assertThat(result.getTotalCount()).isEqualTo(1);
        assertThat(result.getReadyCount()).isEqualTo(1);
        assertThat(result.getItems().get(0).getTaskVersion()).isEqualTo(5L);
        assertThat(result.getItems().get(0).getPackageVersion()).isEqualTo(8L);
        assertThat(result.getItems().get(0).getExcelRecommendedCompany())
                .isEqualTo("测试合同公司");
        assertThat(result.getItems().get(0).getRecommendedLegalEntityId()).isEqualTo(31L);
        assertThat(result.getItems().get(0).getCompanyCandidates()).singleElement()
                .satisfies(candidate -> {
                    assertThat(candidate.getSelectable()).isTrue();
                    assertThat(candidate.getRecommendedSealId()).isEqualTo(41L);
                    assertThat(candidate.getAvailableContractSeals()).singleElement()
                            .satisfies(value -> assertThat(value.getSealName()).isEqualTo("合同章"));
                });
    }

    @Test
    @DisplayName("空组织范围失败关闭且不回显无权任务的签约包资料")
    void shouldFailClosedWithoutAuthorizedOrganizationScope()
    {
        when(shopScopeService.resolveScopeDeptIds(1171L)).thenReturn(List.of());
        when(taskMapper.selectOaSignTaskById(1L)).thenReturn(pendingTask(1L, 11L, 5L));
        OaSignTaskBatchFinalizePreviewRequest request =
                new OaSignTaskBatchFinalizePreviewRequest();
        request.setTaskIds(List.of(1L));

        OaSignTaskBatchFinalizePreviewResult result = service.preview(request, 1171L);

        assertThat(result.getBlockedCount()).isEqualTo(1);
        assertThat(result.getItems().get(0).getReadyForExecution()).isFalse();
        assertThat(result.getItems().get(0).getPackageId()).isNull();
        verify(packageMapper, never()).selectOaSignPackageById(any());
    }

    @Test
    @DisplayName("同组织其他HR的任务失败关闭且不读取签约包")
    void shouldFailClosedForTaskAssignedToAnotherHr()
    {
        OaSignTask task = pendingTask(1L, 11L, 5L);
        when(taskMapper.selectOaSignTaskById(1L)).thenReturn(task);
        doThrow(new ServiceException("签约任务不存在或未分配给当前合同经办人"))
                .when(hrAccessService).requireTaskOwner(task);
        OaSignTaskBatchFinalizePreviewRequest request =
                new OaSignTaskBatchFinalizePreviewRequest();
        request.setTaskIds(List.of(1L));

        OaSignTaskBatchFinalizePreviewResult result = service.preview(request, 1171L);

        assertThat(result.getBlockedCount()).isEqualTo(1);
        assertThat(result.getItems().get(0).getPackageId()).isNull();
        assertThat(result.getItems().get(0).getBlockingReasons())
                .containsExactly("签约任务未分配给当前合同经办人");
        verify(packageMapper, never()).selectOaSignPackageById(any());
    }

    @Test
    @DisplayName("批量执行逐项调用单项事务且允许部分成功")
    void shouldFinalizeEachItemIndependentlyAndKeepPartialSuccess()
    {
        OaSignTask firstTask = pendingTask(1L, 11L, 5L);
        OaSignTask secondTask = pendingTask(2L, 12L, 6L);
        OaSignPackage firstPackage = pendingPackage(11L, 1L, 8L);
        OaSignPackage secondPackage = pendingPackage(12L, 2L, 9L);
        OaSignPackageDocument firstDocument = signedDocument(21L);
        OaSignPackageDocument secondDocument = signedDocument(22L);
        SysLegalEntity entity = contractReadyEntity(31L);
        OaCompanySealConfig seal = validSeal(41L, 31L);
        OaSignCompanyService.CompanyMatchResult noRecommendation = noRecommendation();

        when(taskMapper.selectOaSignTaskById(1L)).thenReturn(firstTask);
        when(taskMapper.selectOaSignTaskById(2L)).thenReturn(secondTask);
        when(packageMapper.selectOaSignPackageById(11L)).thenReturn(firstPackage);
        when(packageMapper.selectOaSignPackageById(12L)).thenReturn(secondPackage);
        when(documentMapper.selectDocumentsByPackageId(11L)).thenReturn(List.of(firstDocument));
        when(documentMapper.selectDocumentsByPackageId(12L)).thenReturn(List.of(secondDocument));
        when(placementPolicyService.requiresEmployeeSignature(any())).thenReturn(true);
        when(placementPolicyService.requiresCompanySeal(any())).thenReturn(true);
        when(companyService.requireContractReadyEntity(31L)).thenReturn(entity);
        when(companyService.requireContractReadySeal(41L, 31L)).thenReturn(seal);
        when(companyService.matchExcelCompany(any(), any(), any(), eq(1171L)))
                .thenReturn(noRecommendation);
        OaSignPackage finalized = pendingPackage(11L, 1L, 9L);
        finalized.setFinalConfirmationStatus("PREPARED_NOT_SENT");
        when(packageService.finalizePackage(eq(11L), any(OaSignPackageFinalizeRequest.class),
                eq(1171L))).thenReturn(finalized);
        when(packageService.finalizePackage(eq(12L), any(OaSignPackageFinalizeRequest.class),
                eq(1171L))).thenThrow(new ServiceException("PDF生成失败"));

        OaSignTaskBatchFinalizeRequest request = new OaSignTaskBatchFinalizeRequest();
        request.setRequestId("batch-finalize-1");
        request.setItems(List.of(action(1L, 11L, 5L, 8L),
                action(2L, 12L, 6L, 9L)));
        OaSignTaskBatchFinalizeResult result = service.execute(request, 1171L);

        assertThat(result.getTotalCount()).isEqualTo(2);
        assertThat(result.getFinalizedCount()).isEqualTo(1);
        assertThat(result.getFailedCount()).isEqualTo(1);
        assertThat(result.getItems()).extracting(value -> value.getResult())
                .containsExactly("FINALIZED", "FAILED");
        verify(packageService).finalizePackage(eq(11L), any(OaSignPackageFinalizeRequest.class),
                eq(1171L));
        verify(packageService).finalizePackage(eq(12L), any(OaSignPackageFinalizeRequest.class),
                eq(1171L));
    }

    @Test
    @DisplayName("陈旧任务版本在生成PDF前被逐项阻断")
    void shouldBlockStaleTaskVersionBeforeCallingFinalizer()
    {
        OaSignTask task = pendingTask(1L, 11L, 6L);
        OaSignPackage signPackage = pendingPackage(11L, 1L, 8L);
        OaSignPackageDocument document = signedDocument(21L);
        when(taskMapper.selectOaSignTaskById(1L)).thenReturn(task);
        when(packageMapper.selectOaSignPackageById(11L)).thenReturn(signPackage);
        when(documentMapper.selectDocumentsByPackageId(11L)).thenReturn(List.of(document));
        when(placementPolicyService.requiresEmployeeSignature(document)).thenReturn(true);
        when(placementPolicyService.requiresCompanySeal(document)).thenReturn(true);

        OaSignTaskBatchFinalizeRequest request = new OaSignTaskBatchFinalizeRequest();
        request.setRequestId("stale-batch");
        request.setItems(List.of(action(1L, 11L, 5L, 8L)));
        OaSignTaskBatchFinalizeResult result = service.execute(request, 1171L);

        assertThat(result.getBlockedCount()).isEqualTo(1);
        assertThat(result.getItems().get(0).getCode()).isEqualTo("TASK_VERSION_CHANGED");
        verify(packageService, never()).finalizePackage(any(), any(), any());
    }

    @Test
    @DisplayName("批量重试只把冻结决策相同项计为已完成并阻断公司印章冲突")
    void shouldClassifyOnlyMatchingFrozenDecisionAsAlreadyFinalized()
    {
        OaSignTask sameTask = pendingTask(1L, 11L, 6L);
        sameTask.setStatus(OaSignTaskStatus.PENDING_FINAL_CONFIRM.name());
        OaSignTask conflictTask = pendingTask(2L, 12L, 7L);
        conflictTask.setStatus(OaSignTaskStatus.PENDING_FINAL_CONFIRM.name());
        OaSignPackage samePackage = pendingPackage(11L, 1L, 9L);
        samePackage.setStatus(OaSignPackageStatus.PENDING_FINAL_CONFIRM);
        samePackage.setLegalEntityIdSnapshot(31L);
        samePackage.setSealIdSnapshot(41L);
        OaSignPackage conflictPackage = pendingPackage(12L, 2L, 10L);
        conflictPackage.setStatus(OaSignPackageStatus.PENDING_FINAL_CONFIRM);
        conflictPackage.setLegalEntityIdSnapshot(32L);
        conflictPackage.setSealIdSnapshot(42L);
        when(taskMapper.selectOaSignTaskById(1L)).thenReturn(sameTask);
        when(taskMapper.selectOaSignTaskById(2L)).thenReturn(conflictTask);
        when(packageMapper.selectOaSignPackageById(11L)).thenReturn(samePackage);
        when(packageMapper.selectOaSignPackageById(12L)).thenReturn(conflictPackage);

        OaSignTaskBatchFinalizeRequest request = new OaSignTaskBatchFinalizeRequest();
        request.setRequestId("batch-replay");
        request.setItems(List.of(action(1L, 11L, 5L, 8L),
                action(2L, 12L, 6L, 9L)));
        OaSignTaskBatchFinalizeResult result = service.execute(request, 1171L);

        assertThat(result.getAlreadyFinalizedCount()).isEqualTo(1);
        assertThat(result.getBlockedCount()).isEqualTo(1);
        assertThat(result.getItems()).extracting(value -> value.getResult())
                .containsExactly("ALREADY_FINALIZED", "BLOCKED");
        assertThat(result.getItems().get(1).getCode())
                .isEqualTo("FINALIZED_DECISION_CONFLICT");
        verify(packageService, never()).finalizePackage(any(), any(), any());
    }

    @Test
    @DisplayName("已生成但未发送的最终合同重放按相同公司印章计为已完成")
    void shouldClassifyMatchingPreparedCandidateAsAlreadyFinalized()
    {
        OaSignTask task = pendingTask(1L, 11L, 6L);
        OaSignPackage signPackage = pendingPackage(11L, 1L, 9L);
        signPackage.setFinalConfirmationStatus("PREPARED_NOT_SENT");
        signPackage.setLegalEntityIdSnapshot(31L);
        signPackage.setSealIdSnapshot(41L);
        when(taskMapper.selectOaSignTaskById(1L)).thenReturn(task);
        when(packageMapper.selectOaSignPackageById(11L)).thenReturn(signPackage);

        OaSignTaskBatchFinalizeRequest request = new OaSignTaskBatchFinalizeRequest();
        request.setRequestId("batch-prepared-replay");
        request.setItems(List.of(action(1L, 11L, 5L, 8L)));

        OaSignTaskBatchFinalizeResult result = service.execute(request, 1171L);

        assertThat(result.getAlreadyFinalizedCount()).isEqualTo(1);
        assertThat(result.getItems().get(0).getMessage()).contains("尚未发送");
        verify(packageService, never()).finalizePackage(any(), any(), any());
    }

    private OaSignTask pendingTask(Long taskId, Long packageId, Long version)
    {
        OaSignTask task = new OaSignTask();
        task.setTaskId(taskId);
        task.setTaskNo("OST-" + taskId);
        task.setEmployeeId(201L + taskId);
        task.setShopDeptId(1171L);
        task.setAssignedHrUserId(101L);
        task.setPackageId(packageId);
        task.setStatus(OaSignTaskStatus.PENDING_COMPANY.name());
        task.setVersion(version);
        return task;
    }

    private OaSignPackage pendingPackage(Long packageId, Long taskId, Long version)
    {
        OaSignPackage value = new OaSignPackage();
        value.setPackageId(packageId);
        value.setPackageNo("OSP-" + packageId);
        value.setTaskId(taskId);
        value.setEmployeeId(201L + taskId);
        value.setEmployeeNameSnapshot("员工" + taskId);
        value.setDeptIdSnapshot(1171L);
        value.setShopDeptId(1171L);
        value.setStatus(OaSignPackageStatus.PENDING_COMPANY);
        value.setSigningSequence(OaSignSigningSequence.SIGNATURE_FIRST);
        value.setDocumentVersion("SP-" + packageId + "-V1");
        value.setInitialSignedTime(new Date());
        value.setVersion(version);
        return value;
    }

    private OaSignPackageDocument signedDocument(Long documentId)
    {
        OaSignPackageDocument value = new OaSignPackageDocument();
        value.setDocumentId(documentId);
        value.setEmployeeVisible("Y");
        value.setEmployeeSignRequired("Y");
        value.setCompanySealRequired("Y");
        value.setSigned("Y");
        value.setSignatureFileUrl("/profile/signature-" + documentId + ".png");
        value.setSignatureHash("a".repeat(64));
        return value;
    }

    private SysLegalEntity contractReadyEntity(Long legalEntityId)
    {
        SysLegalEntity value = new SysLegalEntity();
        value.setLegalEntityId(legalEntityId);
        value.setLegalEntityCode("COMPANY-" + legalEntityId);
        value.setLegalEntityName("测试合同公司");
        value.setUnifiedSocialCreditCode("91330000TEST000001");
        value.setRegisteredAddress("注册地址甲");
        value.setLegalRepresentative("法人甲");
        return value;
    }

    private OaCompanySealConfig validSeal(Long sealId, Long legalEntityId)
    {
        OaCompanySealConfig value = new OaCompanySealConfig();
        value.setSealId(sealId);
        value.setLegalEntityId(legalEntityId);
        value.setSealCode("SEAL-" + sealId);
        value.setSealName("合同章");
        value.setSealType("CONTRACT");
        value.setIsDefault("Y");
        value.setStatus("0");
        return value;
    }

    private OaSignTaskBatchFinalizeAction action(Long taskId, Long packageId,
            Long taskVersion, Long packageVersion)
    {
        OaSignTaskBatchFinalizeAction value = new OaSignTaskBatchFinalizeAction();
        value.setTaskId(taskId);
        value.setPackageId(packageId);
        value.setLegalEntityId(31L);
        value.setSealId(41L);
        value.setExpectedTaskVersion(taskVersion);
        value.setExpectedPackageVersion(packageVersion);
        return value;
    }

    private OaSignCompanyService.CompanyMatchResult noRecommendation()
    {
        return new OaSignCompanyService.CompanyMatchResult(null, List.of(), null,
                "HR_REQUIRED_NO_INPUT", BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("0.6000"), new BigDecimal("0.0800"), false);
    }
}
