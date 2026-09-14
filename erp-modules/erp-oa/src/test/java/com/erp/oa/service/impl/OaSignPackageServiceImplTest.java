package com.erp.oa.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.nio.file.Paths;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.test.util.ReflectionTestUtils;
import com.erp.common.core.context.SecurityContextHolder;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.security.shop.ShopScopeService;
import com.erp.oa.constant.OaSignPackageStatus;
import com.erp.oa.constant.OaSignSigningSequence;
import com.erp.oa.constant.OaSignTaskStatus;
import com.erp.oa.constant.OaSignTemplateType;
import com.erp.oa.constant.OaSignFileEvidenceType;
import com.erp.oa.domain.OaSignEvent;
import com.erp.oa.domain.OaSignFileEvidence;
import com.erp.oa.domain.OaSignPackage;
import com.erp.oa.domain.OaSignPackageDocument;
import com.erp.oa.domain.OaSignPlan;
import com.erp.oa.domain.OaSignPlanVersionTemplate;
import com.erp.oa.domain.OaSignTemplate;
import com.erp.oa.domain.OaSignTask;
import com.erp.oa.domain.OaSignFinalConfirmation;
import com.erp.oa.domain.OaCompanySealConfig;
import com.erp.oa.domain.dto.OaSignDocumentHashRequest;
import com.erp.oa.domain.dto.OaSignDocumentReadRequest;
import com.erp.oa.domain.dto.OaSignFinalDocumentHashRequest;
import com.erp.oa.domain.dto.OaSignPackageFinalizeRequest;
import com.erp.oa.domain.dto.OaSignPackageSignRequest;
import com.erp.oa.domain.vo.OaSignPackageFile;
import com.erp.oa.domain.vo.OaSignCompanyOptions;
import com.erp.oa.domain.vo.SignedPdfResult;
import com.erp.oa.domain.vo.StagedSignFile;
import com.erp.oa.mapper.OaSignEventMapper;
import com.erp.oa.mapper.OaSignFileEvidenceMapper;
import com.erp.oa.mapper.OaSignPackageDocumentMapper;
import com.erp.oa.mapper.OaSignPackageMapper;
import com.erp.oa.mapper.OaSignPlanMapper;
import com.erp.oa.mapper.OaSignPlanVersionMapper;
import com.erp.oa.mapper.OaSignTemplateMapper;
import com.erp.oa.mapper.OaSignTaskMapper;
import com.erp.oa.mapper.OaSignFinalConfirmationMapper;
import com.erp.system.api.domain.SysLegalEntity;

@DisplayName("员工签约包服务")
class OaSignPackageServiceImplTest
{
    private static final byte[] TEST_TEMPLATE_SOURCE =
            "test-template-source".getBytes(StandardCharsets.UTF_8);
    private static final String TEST_TEMPLATE_SOURCE_HASH =
            "3040c6bde8d7e0c16e6672f02b3ed63287612d993f2cfee7d3be3547053040ae";

    @TempDir
    Path tempDir;

    @AfterEach
    void tearDown()
    {
        SecurityContextHolder.remove();
    }

    @Test
    @DisplayName("共享HR角色的普通员工即使有签约包菜单也不能读取业务列表")
    void shouldRejectPackageListForSharedRoleEmployee()
    {
        OaSignPackageServiceImpl service = newPackageService();
        OaSignPackageMapper packageMapper = mock(OaSignPackageMapper.class);
        OaSignPackageDocumentMapper documentMapper = mock(OaSignPackageDocumentMapper.class);
        OaSignEventMapper eventMapper = mock(OaSignEventMapper.class);
        ShopScopeService shopScopeService = mock(ShopScopeService.class);
        ReflectionTestUtils.setField(service, "packageMapper", packageMapper);
        ReflectionTestUtils.setField(service, "documentMapper", documentMapper);
        ReflectionTestUtils.setField(service, "eventMapper", eventMapper);
        ReflectionTestUtils.setField(service, "shopScopeService", shopScopeService);
        OaSignHrAccessService accessService =
                (OaSignHrAccessService) ReflectionTestUtils.getField(service, "signHrAccessService");
        doThrow(new ServiceException("仅当前配置的合同经办人可以访问签约业务"))
                .when(accessService).requireCurrentHr();
        SecurityContextHolder.setUserId("102");
        when(packageMapper.selectOaSignPackageList(any())).thenReturn(List.of(new OaSignPackage()));

        assertThatThrownBy(() -> service.selectPackageList(new OaSignPackage(), 1171L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("配置的合同经办人");
        verify(packageMapper, never()).selectOaSignPackageList(any());
    }

    @Test
    @DisplayName("普通HR签约包列表强制使用当前负责人过滤")
    void shouldFilterTaskBoundPackageListByCurrentOwner()
    {
        OaSignPackageServiceImpl service = newPackageService();
        OaSignPackageMapper packageMapper = mock(OaSignPackageMapper.class);
        ShopScopeService shopScopeService = mock(ShopScopeService.class);
        ReflectionTestUtils.setField(service, "packageMapper", packageMapper);
        ReflectionTestUtils.setField(service, "shopScopeService", shopScopeService);
        OaSignHrAccessService accessService =
                (OaSignHrAccessService) ReflectionTestUtils.getField(service, "signHrAccessService");
        when(accessService.currentTaskOwnerFilter()).thenReturn(101L);
        when(packageMapper.selectOaSignPackageList(any())).thenReturn(List.of());

        service.selectPackageList(new OaSignPackage(), 1171L);

        ArgumentCaptor<OaSignPackage> captor = ArgumentCaptor.forClass(OaSignPackage.class);
        verify(packageMapper).selectOaSignPackageList(captor.capture());
        assertThat(captor.getValue().getParams())
                .containsEntry("assignedHrUserId", 101L);
    }

    @Test
    @DisplayName("同组织其他HR不能读取任务关联签约包")
    void shouldRejectTaskBoundPackageAssignedToAnotherHr()
    {
        OaSignPackageServiceImpl service = newPackageService();
        OaSignPackageMapper packageMapper = mock(OaSignPackageMapper.class);
        OaSignPackageDocumentMapper documentMapper = mock(OaSignPackageDocumentMapper.class);
        OaSignTaskMapper taskMapper = mock(OaSignTaskMapper.class);
        ShopScopeService shopScopeService = mock(ShopScopeService.class);
        ReflectionTestUtils.setField(service, "packageMapper", packageMapper);
        ReflectionTestUtils.setField(service, "documentMapper", documentMapper);
        ReflectionTestUtils.setField(service, "taskMapper", taskMapper);
        ReflectionTestUtils.setField(service, "shopScopeService", shopScopeService);
        OaSignHrAccessService accessService =
                (OaSignHrAccessService) ReflectionTestUtils.getField(service, "signHrAccessService");
        OaSignPackage signPackage = new OaSignPackage();
        signPackage.setPackageId(90L);
        signPackage.setTaskId(9L);
        signPackage.setShopDeptId(1171L);
        OaSignTask task = new OaSignTask();
        task.setTaskId(9L);
        task.setPackageId(90L);
        task.setAssignedHrUserId(202L);
        when(packageMapper.selectOaSignPackageById(90L)).thenReturn(signPackage);
        when(taskMapper.selectOaSignTaskById(9L)).thenReturn(task);
        when(shopScopeService.resolveRequiredShopDept(1171L)).thenReturn(1171L);
        when(shopScopeService.resolveScopeDeptIds(1171L)).thenReturn(List.of(1171L));
        doThrow(new ServiceException("签约任务不存在或未分配给当前合同经办人"))
                .when(accessService).requireTaskOwner(task);
        SecurityContextHolder.setUserId("101");

        assertThatThrownBy(() -> service.getPackageDetail(90L, 1171L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("未分配给当前合同经办人");
        assertThatThrownBy(() -> service.getPackageForVerification(90L, 1171L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("未分配给当前合同经办人");
        verify(documentMapper, never()).selectDocumentsByPackageId(any());
    }

    @Test
    @DisplayName("任务关联包禁止绕过任务中心直接撤回")
    void shouldRejectDirectVoidForTaskLinkedPackage()
    {
        OaSignPackageServiceImpl service = newPackageService();
        OaSignPackageMapper packageMapper = mock(OaSignPackageMapper.class);
        ReflectionTestUtils.setField(service, "packageMapper", packageMapper);
        OaSignPackage signPackage = new OaSignPackage();
        signPackage.setPackageId(90L);
        signPackage.setTaskId(9L);
        signPackage.setStatus(OaSignPackageStatus.PENDING_COMPANY);
        signPackage.setVersion(4L);
        when(packageMapper.selectOaSignPackageById(90L)).thenReturn(signPackage);
        SecurityContextHolder.setUserId("1");
        SecurityContextHolder.setUserName("admin");

        assertThatThrownBy(() -> service.voidPackage(90L, null, "直接撤回"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("合同签约中心取消");

        verify(packageMapper, never()).voidWithVersion(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("公司选项根据包内冻结文件明确返回是否需要印章")
    void shouldExposeFrozenCompanySealRequirementInCompanyOptions()
    {
        OaSignPackageServiceImpl service = newPackageService();
        OaSignPackageMapper packageMapper = mock(OaSignPackageMapper.class);
        OaSignPackageDocumentMapper documentMapper = mock(OaSignPackageDocumentMapper.class);
        OaSignCompanyService companyService = mock(OaSignCompanyService.class);
        ReflectionTestUtils.setField(service, "packageMapper", packageMapper);
        ReflectionTestUtils.setField(service, "documentMapper", documentMapper);
        ReflectionTestUtils.setField(service, "signCompanyService", companyService);
        OaSignPackage signPackage = new OaSignPackage();
        signPackage.setPackageId(90L);
        signPackage.setStatus(OaSignPackageStatus.PENDING_COMPANY);
        // Excel 只提供建议值，首次签名完成前不冻结法律主体。
        signPackage.setLegalEntityIdSnapshot(null);
        signPackage.setLegalEntityNameSnapshot(null);
        signPackage.setRecommendedCompanySnapshot("舟山茗汇文化传播有限公司");
        signPackage.setRecommendedLegalRepresentativeSnapshot("杜翠香");
        signPackage.setRecommendedRegisteredAddressSnapshot("浙江省舟山市嵊泗县");
        when(packageMapper.selectOaSignPackageById(90L)).thenReturn(signPackage);
        when(documentMapper.selectDocumentsByPackageId(90L))
                .thenReturn(List.of(policyDocument(51L, "Y", "N")))
                .thenReturn(List.of(policyDocument(51L, "Y", "Y")));
        OaSignCompanyOptions withoutSeal = new OaSignCompanyOptions();
        OaSignCompanyOptions withSeal = new OaSignCompanyOptions();
        when(companyService.options(signPackage)).thenReturn(withoutSeal, withSeal);
        SecurityContextHolder.setUserId("1");

        OaSignCompanyOptions firstOptions = service.getCompanyOptions(90L, null);
        assertThat(firstOptions.getCompanySealRequired()).isFalse();
        assertThat(firstOptions.getRecommendedLegalEntityName())
                .isEqualTo("舟山茗汇文化传播有限公司");
        assertThat(firstOptions.getRecommendedLegalRepresentative()).isEqualTo("杜翠香");
        assertThat(firstOptions.getRecommendedRegisteredAddress()).isEqualTo("浙江省舟山市嵊泗县");
        assertThat(signPackage.getLegalEntityIdSnapshot()).isNull();
        assertThat(signPackage.getLegalEntityNameSnapshot()).isNull();
        verify(companyService).options(signPackage);
        assertThat(service.getCompanyOptions(90L, null).getCompanySealRequired()).isTrue();
    }

    @Test
    @DisplayName("员工完成首次签名前不开放公司选择")
    void shouldDeferCompanySelectionUntilAfterInitialEmployeeSignature()
    {
        OaSignPackageServiceImpl service = newPackageService();
        OaSignPackageMapper packageMapper = mock(OaSignPackageMapper.class);
        OaSignPackageDocumentMapper documentMapper = mock(OaSignPackageDocumentMapper.class);
        OaSignCompanyService companyService = mock(OaSignCompanyService.class);
        ReflectionTestUtils.setField(service, "packageMapper", packageMapper);
        ReflectionTestUtils.setField(service, "documentMapper", documentMapper);
        ReflectionTestUtils.setField(service, "signCompanyService", companyService);
        OaSignPackage signPackage = new OaSignPackage();
        signPackage.setPackageId(91L);
        signPackage.setStatus(OaSignPackageStatus.PENDING_SIGN);
        signPackage.setRecommendedCompanySnapshot("舟山茗汇文化传播有限公司");
        when(packageMapper.selectOaSignPackageById(91L)).thenReturn(signPackage);
        SecurityContextHolder.setUserId("1");

        assertThatThrownBy(() -> service.getCompanyOptions(91L, null))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("完成首次签名后");

        verify(documentMapper, never()).selectDocumentsByPackageId(any());
        verify(companyService, never()).options(any());
        assertThat(signPackage.getLegalEntityIdSnapshot()).isNull();
        assertThat(signPackage.getRecommendedCompanySnapshot())
                .isEqualTo("舟山茗汇文化传播有限公司");
    }

    @Test
    @DisplayName("先留签名流程选公司时仍必须通过完整法定主体门禁")
    void shouldRejectSignatureFirstFinalizationWhenCompanyMasterIsIncomplete()
    {
        OaSignPackageServiceImpl service = newPackageService();
        OaSignPackageMapper packageMapper = mock(OaSignPackageMapper.class);
        OaSignCompanyService companyService = mock(OaSignCompanyService.class);
        ReflectionTestUtils.setField(service, "packageMapper", packageMapper);
        ReflectionTestUtils.setField(service, "signCompanyService", companyService);
        OaSignPackage signPackage = new OaSignPackage();
        signPackage.setPackageId(90L);
        signPackage.setStatus(OaSignPackageStatus.PENDING_COMPANY);
        signPackage.setSigningSequence(OaSignSigningSequence.SIGNATURE_FIRST);
        when(packageMapper.selectOaSignPackageById(90L)).thenReturn(signPackage);
        when(companyService.requireContractReadyEntity(1L))
                .thenThrow(new ServiceException("所选公司主数据不完整：统一社会信用代码、注册地址"));
        SecurityContextHolder.setUserId("1");
        SecurityContextHolder.setUserName("admin");
        OaSignPackageFinalizeRequest request = new OaSignPackageFinalizeRequest();
        request.setLegalEntityId(1L);

        assertThatThrownBy(() -> service.finalizePackage(90L, request, null))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("公司主数据不完整");

        verify(companyService).requireContractReadyEntity(1L);
        verify(companyService, never()).requireActiveEntity(any());
    }

    @Test
    @DisplayName("任务关联包终结只校验绑定和版本，不以历史分配人鉴权")
    void shouldValidateFinalizeForRoleAuthorizedOperatorRegardlessOfTaskAssignee()
    {
        OaSignPackageServiceImpl service = newPackageService();
        OaSignPackageMapper packageMapper = mock(OaSignPackageMapper.class);
        OaSignTaskMapper taskMapper = mock(OaSignTaskMapper.class);
        ShopScopeService shopScopeService = mock(ShopScopeService.class);
        ReflectionTestUtils.setField(service, "packageMapper", packageMapper);
        ReflectionTestUtils.setField(service, "taskMapper", taskMapper);
        ReflectionTestUtils.setField(service, "shopScopeService", shopScopeService);
        OaSignPackage signPackage = new OaSignPackage();
        signPackage.setPackageId(90L);
        signPackage.setTaskId(9L);
        signPackage.setEmployeeId(201L);
        signPackage.setShopDeptId(1171L);
        signPackage.setStatus(OaSignPackageStatus.PENDING_COMPANY);
        signPackage.setSigningSequence(OaSignSigningSequence.SIGNATURE_FIRST);
        signPackage.setVersion(7L);
        OaSignTask task = new OaSignTask();
        task.setTaskId(9L);
        task.setPackageId(90L);
        task.setEmployeeId(201L);
        task.setShopDeptId(1171L);
        task.setAssignedHrUserId(999L);
        task.setStatus(OaSignTaskStatus.PENDING_COMPANY.name());
        task.setVersion(11L);
        when(packageMapper.selectOaSignPackageById(90L)).thenReturn(signPackage);
        when(taskMapper.lockOaSignTaskById(9L)).thenReturn(task);
        when(shopScopeService.resolveRequiredShopDept(1171L)).thenReturn(1171L);
        when(shopScopeService.resolveScopeDeptIds(1171L)).thenReturn(List.of(1171L));
        SecurityContextHolder.setUserId("101");
        OaSignPackageFinalizeRequest request = new OaSignPackageFinalizeRequest();
        request.setLegalEntityId(1L);
        request.setExpectedVersion(7L);
        request.setExpectedTaskVersion(11L);

        OaSignTask validated = ReflectionTestUtils.invokeMethod(service,
                "validateFinalizeTask", 90L, signPackage, request,
                OaSignTaskStatus.PENDING_COMPANY, true);

        assertThat(validated).isSameAs(task);
    }

    @Test
    @DisplayName("最终合同幂等重放不受历史任务分配人影响")
    void shouldAllowFinalizationReplayAfterTaskAssigneeChanged()
    {
        OaSignPackageServiceImpl service = newPackageService();
        OaSignPackageMapper packageMapper = mock(OaSignPackageMapper.class);
        OaSignTaskMapper taskMapper = mock(OaSignTaskMapper.class);
        OaSignEventMapper eventMapper = mock(OaSignEventMapper.class);
        ShopScopeService shopScopeService = mock(ShopScopeService.class);
        ReflectionTestUtils.setField(service, "packageMapper", packageMapper);
        ReflectionTestUtils.setField(service, "taskMapper", taskMapper);
        ReflectionTestUtils.setField(service, "eventMapper", eventMapper);
        ReflectionTestUtils.setField(service, "shopScopeService", shopScopeService);
        OaSignPackage signPackage = finalizedTaskPackage();
        OaSignTask task = finalizedTask(999L);
        OaSignEvent repeated = new OaSignEvent();
        repeated.setPackageId(90L);
        repeated.setRequestId("finalize-replay-1");
        when(packageMapper.selectOaSignPackageById(90L)).thenReturn(signPackage);
        when(taskMapper.lockOaSignTaskById(9L)).thenReturn(task);
        when(eventMapper.selectEventByTypeAndRequestId(
                "FINAL_CONTRACT_GENERATED", "finalize-replay-1")).thenReturn(repeated);
        when(shopScopeService.resolveRequiredShopDept(1171L)).thenReturn(1171L);
        when(shopScopeService.resolveScopeDeptIds(1171L)).thenReturn(List.of(1171L));
        SecurityContextHolder.setUserId("101");
        OaSignPackageFinalizeRequest request = finalizedReplayRequest();
        request.setRequestId("finalize-replay-1");

        assertThatCode(() -> ReflectionTestUtils.invokeMethod(service,
                "assertExistingFinalizationReplay", 90L, signPackage, request))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("已生成最终合同拒绝改用其他冻结公司或印章")
    void shouldRejectPendingFinalReplayWithDifferentFrozenDecision()
    {
        OaSignPackageServiceImpl service = newPackageService();
        OaSignPackageMapper packageMapper = mock(OaSignPackageMapper.class);
        OaSignTaskMapper taskMapper = mock(OaSignTaskMapper.class);
        ShopScopeService shopScopeService = mock(ShopScopeService.class);
        ReflectionTestUtils.setField(service, "packageMapper", packageMapper);
        ReflectionTestUtils.setField(service, "taskMapper", taskMapper);
        ReflectionTestUtils.setField(service, "shopScopeService", shopScopeService);
        when(packageMapper.selectOaSignPackageById(90L)).thenReturn(finalizedTaskPackage());
        when(taskMapper.lockOaSignTaskById(9L)).thenReturn(finalizedTask(101L));
        when(shopScopeService.resolveRequiredShopDept(1171L)).thenReturn(1171L);
        when(shopScopeService.resolveScopeDeptIds(1171L)).thenReturn(List.of(1171L));
        SecurityContextHolder.setUserId("101");
        OaSignPackageFinalizeRequest request = finalizedReplayRequest();
        request.setSealId(42L);

        assertThatThrownBy(() -> service.finalizePackage(90L, request, 1171L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("其他公司或印章");
    }

    @Test
    @DisplayName("最终合同幂等重放拒绝签署顺序变化")
    void shouldRejectFinalizationReplayWhenSigningSequenceChanged()
    {
        OaSignPackageServiceImpl service = newPackageService();
        OaSignPackageMapper packageMapper = mock(OaSignPackageMapper.class);
        ShopScopeService shopScopeService = mock(ShopScopeService.class);
        ReflectionTestUtils.setField(service, "packageMapper", packageMapper);
        ReflectionTestUtils.setField(service, "shopScopeService", shopScopeService);
        OaSignPackage signPackage = finalizedTaskPackage();
        signPackage.setSigningSequence(OaSignSigningSequence.COMPANY_FIRST);
        when(packageMapper.selectOaSignPackageById(90L)).thenReturn(signPackage);
        when(shopScopeService.resolveRequiredShopDept(1171L)).thenReturn(1171L);
        when(shopScopeService.resolveScopeDeptIds(1171L)).thenReturn(List.of(1171L));
        SecurityContextHolder.setUserId("101");

        assertThatThrownBy(() -> service.finalizePackage(
                90L, finalizedReplayRequest(), 1171L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("签署顺序已变化");
    }

    @Test
    @DisplayName("先留签名候选重试同时绑定补资请求、签名请求和采集时间")
    void shouldBindRepeatedSignatureFirstCandidateToOriginalRequestIdentity() throws Exception
    {
        OaSignPackageServiceImpl service = newPackageService();
        OaSignPackageMapper packageMapper = mock(OaSignPackageMapper.class);
        OaSignTaskMapper taskMapper = mock(OaSignTaskMapper.class);
        OaSignFileEvidenceMapper evidenceMapper = mock(OaSignFileEvidenceMapper.class);
        OaSignEventMapper eventMapper = mock(OaSignEventMapper.class);
        OaSignDocumentService documentService = mock(OaSignDocumentService.class);
        ReflectionTestUtils.setField(service, "packageMapper", packageMapper);
        ReflectionTestUtils.setField(service, "taskMapper", taskMapper);
        ReflectionTestUtils.setField(service, "evidenceMapper", evidenceMapper);
        ReflectionTestUtils.setField(service, "eventMapper", eventMapper);
        ReflectionTestUtils.setField(service, "documentService", documentService);
        when(documentService.readConfiguredFileBytes(any()))
                .thenReturn(TEST_TEMPLATE_SOURCE);

        byte[] png = SignatureImageTestFixtures.signature(1);
        String sampleHash = sha256(png);
        String rootHash = "a".repeat(64);
        Date capturedTime = new Date(1_752_990_400_456L);
        Path samplePath = tempDir.resolve("task-signature-sample.png");
        Files.write(samplePath, png);

        OaSignPackage signPackage = new OaSignPackage();
        signPackage.setPackageId(90L);
        signPackage.setTaskId(9L);
        signPackage.setStatus(OaSignPackageStatus.DRAFT);
        signPackage.setSigningSequence(OaSignSigningSequence.SIGNATURE_FIRST);
        signPackage.setDocumentVersion("SP-90-V1");
        signPackage.setVersion(7L);
        signPackage.setFinalDocumentVersion("SP-90-F1");
        signPackage.setFinalDocumentRootHash(rootHash);
        signPackage.setFinalGeneratedTime(new Date(capturedTime.getTime() + 2_000L));
        signPackage.setSignatureSampleFileUrl("/profile/task-signature-sample.png");
        signPackage.setSignatureSampleHash(sampleHash);
        signPackage.setSignatureSampleTime(capturedTime);
        OaSignTask task = new OaSignTask();
        task.setTaskId(9L);
        task.setPackageId(90L);
        task.setAssignedHrUserId(101L);
        task.setStatus(OaSignTaskStatus.READY_TO_SEND.name());
        OaSignFileEvidence evidence = new OaSignFileEvidence();
        evidence.setPackageId(90L);
        evidence.setDocumentVersion("SAMPLE-500");
        evidence.setEvidenceType(OaSignFileEvidenceType.SIGNATURE_SAMPLE);
        evidence.setFileHash(sampleHash);
        evidence.setGeneratedTime(capturedTime);
        OaSignEvent prepared = new OaSignEvent();
        prepared.setPackageId(90L);
        prepared.setEventType("FINAL_CONTRACT_PREPARED");
        prepared.setOperatorRole("SYSTEM");
        prepared.setRequestId("signature-request-1");
        prepared.setDocumentHash(rootHash);
        when(packageMapper.selectOaSignPackageById(90L)).thenReturn(signPackage);
        when(taskMapper.selectOaSignTaskById(9L)).thenReturn(task);
        when(evidenceMapper.selectEvidenceByPackageId(90L)).thenReturn(List.of(evidence));
        when(eventMapper.selectEventsByPackageId(90L)).thenReturn(List.of(prepared));
        when(documentService.resolveGeneratedSignPackageFile(
                signPackage.getSignatureSampleFileUrl())).thenReturn(samplePath);

        assertThat(service.prepareSignatureFirstCandidateForSystem(
                90L, 9L, 101L, 500L, "signature-request-1", png, sampleHash,
                capturedTime, "SP-90-V1", 7L)).isSameAs(signPackage);

        assertThatThrownBy(() -> service.prepareSignatureFirstCandidateForSystem(
                90L, 9L, 101L, 501L, "signature-request-1", png, sampleHash,
                capturedTime, "SP-90-V1", 7L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("来源请求");
        assertThatThrownBy(() -> service.prepareSignatureFirstCandidateForSystem(
                90L, 9L, 101L, 500L, "signature-request-2", png, sampleHash,
                capturedTime, "SP-90-V1", 7L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("幂等请求");
        assertThatThrownBy(() -> service.prepareSignatureFirstCandidateForSystem(
                90L, 9L, 101L, 500L, "signature-request-1", png, sampleHash,
                new Date(capturedTime.getTime() + 1_000L), "SP-90-V1", 7L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("已冻结结果不一致");
    }

    @Test
    @DisplayName("普通唯一HR签约包API使用安全副本并裁剪文档和事件hash")
    void shouldRedactHashesFromBusinessPackageDetailWithoutMutatingPersistenceEntity()
    {
        OaSignPackageServiceImpl service = newPackageService();
        OaSignPackageMapper packageMapper = mock(OaSignPackageMapper.class);
        OaSignPackageDocumentMapper documentMapper = mock(OaSignPackageDocumentMapper.class);
        OaSignEventMapper eventMapper = mock(OaSignEventMapper.class);
        ShopScopeService shopScopeService = mock(ShopScopeService.class);
        ReflectionTestUtils.setField(service, "packageMapper", packageMapper);
        ReflectionTestUtils.setField(service, "documentMapper", documentMapper);
        ReflectionTestUtils.setField(service, "eventMapper", eventMapper);
        ReflectionTestUtils.setField(service, "shopScopeService", shopScopeService);

        OaSignPackage persisted = editableTaskPackage();
        OaSignPackageDocument persistedDocument = new OaSignPackageDocument();
        persistedDocument.setDocumentId(10L);
        persistedDocument.setFileHashBeforeSign("before-secret");
        persistedDocument.setFileHashAfterSign("after-secret");
        persistedDocument.setReviewPdfHash("review-secret");
        persistedDocument.setSignedPdfHash("signed-secret");
        persistedDocument.setSignatureHash("signature-secret");
        persistedDocument.setCertificateHash("certificate-secret");
        OaSignEvent persistedEvent = new OaSignEvent();
        persistedEvent.setEventHash("event-secret");
        persistedEvent.setPrevEventHash("prev-secret");
        persistedEvent.setDocumentHash("document-secret");
        persistedEvent.setEventPayload("reviewPdfHash=payload-secret");
        when(packageMapper.selectOaSignPackageById(90L)).thenReturn(persisted);
        when(shopScopeService.resolveRequiredShopDept(1171L)).thenReturn(1171L);
        when(documentMapper.selectDocumentsByPackageId(90L)).thenReturn(List.of(persistedDocument));
        when(eventMapper.selectEventsByPackageId(90L)).thenReturn(List.of(persistedEvent));

        OaSignPackage result = service.getPackageDetail(90L, 1171L);

        assertThat(result).isNotSameAs(persisted);
        assertThat(result.getDocuments()).singleElement().satisfies(document -> {
            assertThat(document).isNotSameAs(persistedDocument);
            assertThat(document.getFileHashBeforeSign()).isNull();
            assertThat(document.getFileHashAfterSign()).isNull();
            assertThat(document.getReviewPdfHash()).isNull();
            assertThat(document.getSignedPdfHash()).isNull();
            assertThat(document.getSignatureHash()).isNull();
            assertThat(document.getCertificateHash()).isNull();
        });
        assertThat(result.getEvents()).singleElement().satisfies(event -> {
            assertThat(event).isNotSameAs(persistedEvent);
            assertThat(event.getEventHash()).isNull();
            assertThat(event.getPrevEventHash()).isNull();
            assertThat(event.getDocumentHash()).isNull();
            assertThat(event.getEventPayload()).isNull();
        });
        assertThat(persistedDocument.getReviewPdfHash()).isEqualTo("review-secret");
        assertThat(persistedEvent.getEventHash()).isEqualTo("event-secret");
    }

    @Test
    @DisplayName("技术证据只读签约包详情保留原始hash")
    void shouldKeepHashesInTechnicalVerificationPackageDetail()
    {
        OaSignPackageServiceImpl service = newPackageService();
        OaSignPackageMapper packageMapper = mock(OaSignPackageMapper.class);
        OaSignPackageDocumentMapper documentMapper = mock(OaSignPackageDocumentMapper.class);
        OaSignEventMapper eventMapper = mock(OaSignEventMapper.class);
        ShopScopeService shopScopeService = mock(ShopScopeService.class);
        ReflectionTestUtils.setField(service, "packageMapper", packageMapper);
        ReflectionTestUtils.setField(service, "documentMapper", documentMapper);
        ReflectionTestUtils.setField(service, "eventMapper", eventMapper);
        ReflectionTestUtils.setField(service, "shopScopeService", shopScopeService);
        OaSignHrAccessService accessService =
                (OaSignHrAccessService) ReflectionTestUtils.getField(service, "signHrAccessService");
        when(accessService.isTechnicalEvidenceReader()).thenReturn(true);

        OaSignPackage persisted = editableTaskPackage();
        OaSignPackageDocument document = new OaSignPackageDocument();
        document.setDocumentId(10L);
        document.setReviewPdfHash("review-secret");
        OaSignEvent event = new OaSignEvent();
        event.setEventHash("event-secret");
        when(packageMapper.selectOaSignPackageById(90L)).thenReturn(persisted);
        when(shopScopeService.resolveRequiredShopDept(1171L)).thenReturn(1171L);
        when(documentMapper.selectDocumentsByPackageId(90L)).thenReturn(List.of(document));
        when(eventMapper.selectEventsByPackageId(90L)).thenReturn(List.of(event));

        OaSignPackage result = service.getPackageForVerification(90L, 1171L);

        assertThat(result).isSameAs(persisted);
        assertThat(result.getDocuments()).singleElement()
                .extracting(OaSignPackageDocument::getReviewPdfHash).isEqualTo("review-secret");
        assertThat(result.getEvents()).singleElement()
                .extracting(OaSignEvent::getEventHash).isEqualTo("event-secret");
    }

    @Test
    @DisplayName("非管理员按ID读取签约包时缺少门店上下文直接拒绝")
    void shouldRejectPackageReadWithoutShopContextForNonAdmin()
    {
        OaSignPackageServiceImpl service = newPackageService();
        OaSignPackageMapper packageMapper = mock(OaSignPackageMapper.class);
        OaSignPackageDocumentMapper documentMapper = mock(OaSignPackageDocumentMapper.class);
        OaSignEventMapper eventMapper = mock(OaSignEventMapper.class);
        ShopScopeService shopScopeService = mock(ShopScopeService.class);
        ReflectionTestUtils.setField(service, "packageMapper", packageMapper);
        ReflectionTestUtils.setField(service, "documentMapper", documentMapper);
        ReflectionTestUtils.setField(service, "eventMapper", eventMapper);
        ReflectionTestUtils.setField(service, "shopScopeService", shopScopeService);
        SecurityContextHolder.setUserId("2");
        OaSignPackage persisted = editableTaskPackage();
        when(packageMapper.selectOaSignPackageById(90L)).thenReturn(persisted);
        when(shopScopeService.resolveRequiredShopDept(null))
                .thenThrow(new ServiceException("请先选择店铺或仓库"));

        assertThatThrownBy(() -> service.getPackageDetail(90L, null))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("请先选择店铺或仓库");
    }

    @Test
    @DisplayName("非管理员可从授权父门店上下文读取子门店签约包")
    void shouldAllowPackageReadFromAuthorizedShopSubtree()
    {
        OaSignPackageServiceImpl service = newPackageService();
        OaSignPackageMapper packageMapper = mock(OaSignPackageMapper.class);
        OaSignPackageDocumentMapper documentMapper = mock(OaSignPackageDocumentMapper.class);
        OaSignEventMapper eventMapper = mock(OaSignEventMapper.class);
        ShopScopeService shopScopeService = mock(ShopScopeService.class);
        ReflectionTestUtils.setField(service, "packageMapper", packageMapper);
        ReflectionTestUtils.setField(service, "documentMapper", documentMapper);
        ReflectionTestUtils.setField(service, "eventMapper", eventMapper);
        ReflectionTestUtils.setField(service, "shopScopeService", shopScopeService);
        SecurityContextHolder.setUserId("2");
        OaSignPackage persisted = editableTaskPackage();
        persisted.setShopDeptId(1172L);
        when(packageMapper.selectOaSignPackageById(90L)).thenReturn(persisted);
        when(shopScopeService.resolveRequiredShopDept(1171L)).thenReturn(1171L);
        when(shopScopeService.resolveScopeDeptIds(1171L)).thenReturn(List.of(1171L, 1172L));
        when(documentMapper.selectDocumentsByPackageId(90L)).thenReturn(List.of());
        when(eventMapper.selectEventsByPackageId(90L)).thenReturn(List.of());

        assertThat(service.getPackageDetail(90L, 1171L).getPackageId()).isEqualTo(90L);
    }

    @Test
    @DisplayName("非管理员技术证据读取也不能跨越授权门店子树")
    void shouldRejectTechnicalVerificationOutsideAuthorizedShopSubtree()
    {
        OaSignPackageServiceImpl service = newPackageService();
        OaSignPackageMapper packageMapper = mock(OaSignPackageMapper.class);
        ShopScopeService shopScopeService = mock(ShopScopeService.class);
        ReflectionTestUtils.setField(service, "packageMapper", packageMapper);
        ReflectionTestUtils.setField(service, "shopScopeService", shopScopeService);
        SecurityContextHolder.setUserId("2");
        OaSignPackage persisted = editableTaskPackage();
        persisted.setShopDeptId(2200L);
        when(packageMapper.selectOaSignPackageById(90L)).thenReturn(persisted);
        when(shopScopeService.resolveRequiredShopDept(1171L)).thenReturn(1171L);
        when(shopScopeService.resolveScopeDeptIds(1171L)).thenReturn(List.of(1171L, 1172L));

        assertThatThrownBy(() -> service.getPackageForVerification(90L, 1171L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("无权访问该签约包");
    }

    @Test
    @DisplayName("管理员技术证据读取保留全局按ID查看能力")
    void shouldAllowAdminGlobalVerificationWithoutShopContext()
    {
        OaSignPackageServiceImpl service = newPackageService();
        OaSignPackageMapper packageMapper = mock(OaSignPackageMapper.class);
        OaSignPackageDocumentMapper documentMapper = mock(OaSignPackageDocumentMapper.class);
        OaSignEventMapper eventMapper = mock(OaSignEventMapper.class);
        ShopScopeService shopScopeService = mock(ShopScopeService.class);
        ReflectionTestUtils.setField(service, "packageMapper", packageMapper);
        ReflectionTestUtils.setField(service, "documentMapper", documentMapper);
        ReflectionTestUtils.setField(service, "eventMapper", eventMapper);
        ReflectionTestUtils.setField(service, "shopScopeService", shopScopeService);
        SecurityContextHolder.setUserId("1");
        OaSignPackage persisted = editableTaskPackage();
        persisted.setShopDeptId(9999L);
        when(packageMapper.selectOaSignPackageById(90L)).thenReturn(persisted);
        when(documentMapper.selectDocumentsByPackageId(90L)).thenReturn(List.of());
        when(eventMapper.selectEventsByPackageId(90L)).thenReturn(List.of());

        assertThat(service.getPackageForVerification(90L, null)).isSameAs(persisted);
        verify(shopScopeService, never()).resolveRequiredShopDept(any());
        verify(shopScopeService, never()).resolveScopeDeptIds(any());
    }

    @Test
    @DisplayName("待HR确认的任务绑定草稿不能通过通用编辑接口修改")
    void shouldRejectTaskDraftUpdateWhileWaitingHrConfirmation()
    {
        OaSignPackageServiceImpl service = newPackageService();
        OaSignPackageMapper packageMapper = mock(OaSignPackageMapper.class);
        OaSignTaskMapper taskMapper = mock(OaSignTaskMapper.class);
        ShopScopeService shopScopeService = mock(ShopScopeService.class);
        ReflectionTestUtils.setField(service, "packageMapper", packageMapper);
        ReflectionTestUtils.setField(service, "taskMapper", taskMapper);
        ReflectionTestUtils.setField(service, "shopScopeService", shopScopeService);
        SecurityContextHolder.setUserId("101");

        OaSignPackage existing = editableTaskPackage();
        OaSignTask task = editableTask(OaSignTaskStatus.WAITING_HR_CONFIRM);
        when(packageMapper.selectOaSignPackageById(90L)).thenReturn(existing);
        when(taskMapper.selectOaSignTaskById(9L)).thenReturn(task);
        when(shopScopeService.resolveScopeDeptIds(1171L)).thenReturn(List.of(1171L));

        assertThatThrownBy(() -> service.updateDraftPackage(90L, validDraftRequest("offboard"), 1171L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("当前任务状态不能编辑草稿");
        verify(packageMapper, never()).updateDraftOaSignPackage(any());
    }

    @Test
    @DisplayName("待补资料任务编辑后仍强制保留任务场景与五个绑定字段")
    void shouldKeepTaskBindingFieldsWhenUpdatingNeedsDataDraft()
    {
        OaSignPackageServiceImpl service = newPackageService();
        OaSignPackageMapper packageMapper = mock(OaSignPackageMapper.class);
        OaSignPackageDocumentMapper documentMapper = mock(OaSignPackageDocumentMapper.class);
        OaSignEventMapper eventMapper = mock(OaSignEventMapper.class);
        OaSignTaskMapper taskMapper = mock(OaSignTaskMapper.class);
        ShopScopeService shopScopeService = mock(ShopScopeService.class);
        ReflectionTestUtils.setField(service, "packageMapper", packageMapper);
        ReflectionTestUtils.setField(service, "documentMapper", documentMapper);
        ReflectionTestUtils.setField(service, "eventMapper", eventMapper);
        ReflectionTestUtils.setField(service, "taskMapper", taskMapper);
        ReflectionTestUtils.setField(service, "shopScopeService", shopScopeService);
        SecurityContextHolder.setUserId("101");
        SecurityContextHolder.setUserName("唯一HR");

        OaSignPackage existing = editableTaskPackage();
        existing.setSourcePlanId(20L);
        existing.setContractTermCodeSnapshot("OPEN_ENDED");
        OaSignTask task = editableTask(OaSignTaskStatus.NEEDS_DATA);
        when(packageMapper.selectOaSignPackageById(90L)).thenReturn(existing);
        when(taskMapper.selectOaSignTaskById(9L)).thenReturn(task);
        when(shopScopeService.resolveScopeDeptIds(1171L)).thenReturn(List.of(1171L));
        when(packageMapper.updateDraftOaSignPackage(any())).thenReturn(1);
        when(documentMapper.selectDocumentsByPackageId(90L)).thenReturn(List.of());
        when(eventMapper.selectEventsByPackageId(90L)).thenReturn(List.of());

        existing.setActualRegularizationDate("2026-07-10");
        OaSignPackage request = validDraftRequest("offboard");
        request.setContractTermCodeSnapshot("FIXED_TERM");
        request.setActualRegularizationDate("2026-07-11");
        service.updateDraftPackage(90L, request, 1171L);

        ArgumentCaptor<OaSignPackage> updateCaptor = ArgumentCaptor.forClass(OaSignPackage.class);
        verify(packageMapper).updateDraftOaSignPackage(updateCaptor.capture());
        OaSignPackage update = updateCaptor.getValue();
        assertThat(update.getEmployeeId()).isEqualTo(201L);
        assertThat(update.getScenario()).isEqualTo("onboard");
        assertThat(update.getShopDeptId()).isEqualTo(1171L);
        assertThat(update.getTaskId()).isEqualTo(9L);
        assertThat(update.getPlanVersionId()).isEqualTo(55L);
        assertThat(update.getSourcePlanId()).isEqualTo(55L);
        assertThat(update.getContractTermCodeSnapshot()).isEqualTo("OPEN_ENDED");
        assertThat(update.getParams()).containsEntry("assignedHrUserId", 101L);
        assertThat(update.getActualRegularizationDate()).isEqualTo("2026-07-11");
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = OaSignTaskStatus.class, names = { "NEW", "NEEDS_DATA", "FAILED" })
    @DisplayName("已绑定转正任务草稿编辑始终保留生命周期冻结的实际转正日")
    void shouldFreezeLifecycleRegularizationDateForEditableBoundTask(
            OaSignTaskStatus status)
    {
        OaSignPackageServiceImpl service = newPackageService();
        OaSignPackageMapper packageMapper = mock(OaSignPackageMapper.class);
        OaSignPackageDocumentMapper documentMapper = mock(OaSignPackageDocumentMapper.class);
        OaSignEventMapper eventMapper = mock(OaSignEventMapper.class);
        OaSignTaskMapper taskMapper = mock(OaSignTaskMapper.class);
        ShopScopeService shopScopeService = mock(ShopScopeService.class);
        ReflectionTestUtils.setField(service, "packageMapper", packageMapper);
        ReflectionTestUtils.setField(service, "documentMapper", documentMapper);
        ReflectionTestUtils.setField(service, "eventMapper", eventMapper);
        ReflectionTestUtils.setField(service, "taskMapper", taskMapper);
        ReflectionTestUtils.setField(service, "shopScopeService", shopScopeService);
        SecurityContextHolder.setUserId("101");
        SecurityContextHolder.setUserName("唯一HR");

        OaSignPackage existing = editableTaskPackage();
        existing.setScenario("onboard");
        existing.setActualRegularizationDate("2026-07-10");
        OaSignTask task = editableTask(status);
        task.setScenario("REGULARIZE");
        when(packageMapper.selectOaSignPackageById(90L)).thenReturn(existing);
        when(taskMapper.selectOaSignTaskById(9L)).thenReturn(task);
        when(shopScopeService.resolveScopeDeptIds(1171L)).thenReturn(List.of(1171L));
        when(packageMapper.updateDraftOaSignPackage(any())).thenReturn(1);
        when(documentMapper.selectDocumentsByPackageId(90L)).thenReturn(List.of());
        when(eventMapper.selectEventsByPackageId(90L)).thenReturn(List.of());
        OaSignPackage request = validDraftRequest("offboard");
        request.setActualRegularizationDate("2099-01-01");

        service.updateDraftPackage(90L, request, 1171L);

        ArgumentCaptor<OaSignPackage> updateCaptor =
                ArgumentCaptor.forClass(OaSignPackage.class);
        verify(packageMapper).updateDraftOaSignPackage(updateCaptor.capture());
        assertThat(updateCaptor.getValue().getScenario()).isEqualTo("regularize");
        assertThat(updateCaptor.getValue().getActualRegularizationDate())
                .isEqualTo("2026-07-10");
    }

    @Test
    @DisplayName("生成文件后编辑任务草稿在同一事务失效旧版本并在提交后安全删除归档文件")
    void shouldInvalidateGeneratedDocumentsWhenEditingTaskDraft()
    {
        OaSignPackageServiceImpl service = newPackageService();
        OaSignPackageMapper packageMapper = mock(OaSignPackageMapper.class);
        OaSignPackageDocumentMapper documentMapper = mock(OaSignPackageDocumentMapper.class);
        OaSignFileEvidenceMapper evidenceMapper = mock(OaSignFileEvidenceMapper.class);
        OaSignEventMapper eventMapper = mock(OaSignEventMapper.class);
        OaSignTaskMapper taskMapper = mock(OaSignTaskMapper.class);
        OaSignFileStorageService fileStorageService = mock(OaSignFileStorageService.class);
        ShopScopeService shopScopeService = mock(ShopScopeService.class);
        ReflectionTestUtils.setField(service, "packageMapper", packageMapper);
        ReflectionTestUtils.setField(service, "documentMapper", documentMapper);
        ReflectionTestUtils.setField(service, "evidenceMapper", evidenceMapper);
        ReflectionTestUtils.setField(service, "eventMapper", eventMapper);
        ReflectionTestUtils.setField(service, "taskMapper", taskMapper);
        ReflectionTestUtils.setField(service, "fileStorageService", fileStorageService);
        ReflectionTestUtils.setField(service, "shopScopeService", shopScopeService);
        SecurityContextHolder.setUserId("101");
        SecurityContextHolder.setUserName("唯一HR");

        OaSignPackage existing = editableTaskPackage();
        existing.setSourcePlanId(55L);
        existing.setDocumentVersion("SP-90-V1");
        existing.setConfirmStatus(OaSignTaskStatus.NEEDS_DATA.name());
        OaSignTask task = editableTask(OaSignTaskStatus.NEEDS_DATA);
        OaSignFileEvidence evidence = new OaSignFileEvidence();
        evidence.setPackageId(90L);
        evidence.setDocumentVersion("SP-90-V1");
        evidence.setFileUrl("task-none/package-90/SP-90-V1/review.pdf");
        evidence.setFileHash("review-hash-secret");
        when(packageMapper.selectOaSignPackageById(90L)).thenReturn(existing);
        when(taskMapper.selectOaSignTaskById(9L)).thenReturn(task);
        when(shopScopeService.resolveScopeDeptIds(1171L)).thenReturn(List.of(1171L));
        when(evidenceMapper.selectEvidenceByPackageId(90L)).thenReturn(List.of(evidence));
        when(packageMapper.updateDraftOaSignPackage(any())).thenAnswer(invocation -> {
            existing.setDocumentVersion(null);
            existing.setConfirmStatus(OaSignTaskStatus.NEEDS_DATA.name());
            return 1;
        });
        when(documentMapper.selectDocumentsByPackageId(90L)).thenReturn(List.of());
        when(eventMapper.selectEventsByPackageId(90L)).thenReturn(List.of());

        TransactionSynchronizationManager.initSynchronization();
        try
        {
            OaSignPackage detail = service.updateDraftPackage(90L, validDraftRequest(), 1171L);

            assertThat(detail.getDocumentVersion()).isNull();
            assertThat(detail.getDocuments()).isEmpty();
            verify(evidenceMapper).deleteEvidenceByPackageIdAndDocumentVersion(90L, "SP-90-V1");
            verify(documentMapper).deleteDocumentsByPackageId(90L);
            verify(fileStorageService, never()).discardArchivedEvidence(any(), any());

            for (TransactionSynchronization synchronization
                    : TransactionSynchronizationManager.getSynchronizations())
            {
                synchronization.afterCommit();
            }
            verify(fileStorageService).discardArchivedEvidence(
                    "task-none/package-90/SP-90-V1/review.pdf", "review-hash-secret");
        }
        finally
        {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("任务并发迁移导致草稿原子更新为零行时不删除数据库记录或物理文件")
    void shouldNotDeleteGeneratedDocumentsWhenAtomicDraftUpdateLosesHrAssignment()
    {
        OaSignPackageServiceImpl service = newPackageService();
        OaSignPackageMapper packageMapper = mock(OaSignPackageMapper.class);
        OaSignPackageDocumentMapper documentMapper = mock(OaSignPackageDocumentMapper.class);
        OaSignFileEvidenceMapper evidenceMapper = mock(OaSignFileEvidenceMapper.class);
        OaSignTaskMapper taskMapper = mock(OaSignTaskMapper.class);
        OaSignFileStorageService fileStorageService = mock(OaSignFileStorageService.class);
        ShopScopeService shopScopeService = mock(ShopScopeService.class);
        ReflectionTestUtils.setField(service, "packageMapper", packageMapper);
        ReflectionTestUtils.setField(service, "documentMapper", documentMapper);
        ReflectionTestUtils.setField(service, "evidenceMapper", evidenceMapper);
        ReflectionTestUtils.setField(service, "taskMapper", taskMapper);
        ReflectionTestUtils.setField(service, "fileStorageService", fileStorageService);
        ReflectionTestUtils.setField(service, "shopScopeService", shopScopeService);
        SecurityContextHolder.setUserId("101");

        OaSignPackage existing = editableTaskPackage();
        existing.setDocumentVersion("SP-90-V1");
        when(packageMapper.selectOaSignPackageById(90L)).thenReturn(existing);
        when(taskMapper.selectOaSignTaskById(9L)).thenReturn(editableTask(OaSignTaskStatus.NEEDS_DATA));
        when(shopScopeService.resolveScopeDeptIds(1171L)).thenReturn(List.of(1171L));
        when(evidenceMapper.selectEvidenceByPackageId(90L)).thenReturn(List.of(new OaSignFileEvidence()));
        when(packageMapper.updateDraftOaSignPackage(any())).thenReturn(0);

        assertThatThrownBy(() -> service.updateDraftPackage(90L, validDraftRequest(), 1171L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("刷新后重试");
        verify(evidenceMapper, never()).deleteEvidenceByPackageIdAndDocumentVersion(any(), any());
        verify(documentMapper, never()).deleteDocumentsByPackageId(any());
        verify(fileStorageService, never()).discardArchivedEvidence(any(), any());
    }

    @Test
    @DisplayName("草稿编辑事务回滚时不删除旧物理文件")
    void shouldKeepGeneratedFilesWhenDraftEditTransactionRollsBack()
    {
        OaSignPackageServiceImpl service = newPackageService();
        OaSignPackageMapper packageMapper = mock(OaSignPackageMapper.class);
        OaSignPackageDocumentMapper documentMapper = mock(OaSignPackageDocumentMapper.class);
        OaSignFileEvidenceMapper evidenceMapper = mock(OaSignFileEvidenceMapper.class);
        OaSignEventMapper eventMapper = mock(OaSignEventMapper.class);
        OaSignTaskMapper taskMapper = mock(OaSignTaskMapper.class);
        OaSignFileStorageService fileStorageService = mock(OaSignFileStorageService.class);
        ShopScopeService shopScopeService = mock(ShopScopeService.class);
        ReflectionTestUtils.setField(service, "packageMapper", packageMapper);
        ReflectionTestUtils.setField(service, "documentMapper", documentMapper);
        ReflectionTestUtils.setField(service, "evidenceMapper", evidenceMapper);
        ReflectionTestUtils.setField(service, "eventMapper", eventMapper);
        ReflectionTestUtils.setField(service, "taskMapper", taskMapper);
        ReflectionTestUtils.setField(service, "fileStorageService", fileStorageService);
        ReflectionTestUtils.setField(service, "shopScopeService", shopScopeService);
        SecurityContextHolder.setUserId("101");

        OaSignPackage existing = editableTaskPackage();
        existing.setDocumentVersion("SP-90-V1");
        OaSignFileEvidence evidence = new OaSignFileEvidence();
        evidence.setDocumentVersion("SP-90-V1");
        evidence.setFileUrl("task-none/package-90/SP-90-V1/review.pdf");
        evidence.setFileHash("review-hash-secret");
        when(packageMapper.selectOaSignPackageById(90L)).thenReturn(existing);
        when(taskMapper.selectOaSignTaskById(9L)).thenReturn(editableTask(OaSignTaskStatus.NEEDS_DATA));
        when(shopScopeService.resolveScopeDeptIds(1171L)).thenReturn(List.of(1171L));
        when(evidenceMapper.selectEvidenceByPackageId(90L)).thenReturn(List.of(evidence));
        when(packageMapper.updateDraftOaSignPackage(any())).thenAnswer(invocation -> {
            existing.setDocumentVersion(null);
            return 1;
        });
        when(documentMapper.selectDocumentsByPackageId(90L)).thenReturn(List.of());
        when(eventMapper.selectEventsByPackageId(90L)).thenReturn(List.of());

        TransactionSynchronizationManager.initSynchronization();
        try
        {
            service.updateDraftPackage(90L, validDraftRequest(), 1171L);
            for (TransactionSynchronization synchronization
                    : TransactionSynchronizationManager.getSynchronizations())
            {
                synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
            }
            verify(fileStorageService, never()).discardArchivedEvidence(any(), any());
        }
        finally
        {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private OaSignPackage editableTaskPackage()
    {
        OaSignPackage value = new OaSignPackage();
        value.setPackageId(90L);
        value.setTaskId(9L);
        value.setEmployeeId(201L);
        value.setScenario("onboard");
        value.setSocialType("有社保");
        value.setEmploymentType("劳动合同");
        value.setShopDeptId(1171L);
        value.setPlanVersionId(55L);
        value.setStatus(OaSignPackageStatus.DRAFT);
        return value;
    }

    private OaSignTask editableTask(OaSignTaskStatus status)
    {
        OaSignTask value = new OaSignTask();
        value.setTaskId(9L);
        value.setPackageId(90L);
        value.setEmployeeId(201L);
        value.setScenario("onboard");
        value.setShopDeptId(1171L);
        value.setAssignedHrUserId(101L);
        value.setPlanVersionId(55L);
        value.setStatus(status.name());
        return value;
    }

    private OaSignPackage validDraftRequest(String scenario)
    {
        OaSignPackage value = new OaSignPackage();
        value.setEmployeeId(999L);
        value.setTaskId(999L);
        value.setPlanVersionId(999L);
        value.setShopDeptId(999L);
        value.setEmployeeNameSnapshot("员工甲");
        value.setEmployeePhoneSnapshot("13800000000");
        value.setEmployeeIdCardSnapshot("TEST-ID-****-0011");
        value.setScenario(scenario);
        return value;
    }

    private OaSignPackageServiceImpl newPackageService()
    {
        OaSignPackageServiceImpl service = new OaSignPackageServiceImpl();
        ReflectionTestUtils.setField(service, "salarySources", org.mockito.Mockito.mock(OaSignSalarySourceService.class));
        OaSignTaskMapper taskMapper = mock(OaSignTaskMapper.class);
        OaSignTaskEventService taskEventService = mock(OaSignTaskEventService.class);
        OaSignNotificationOutboxService outboxService = mock(OaSignNotificationOutboxService.class);
        when(taskMapper.insertOaSignTask(any())).thenAnswer(invocation -> {
            OaSignTask task = invocation.getArgument(0);
            task.setTaskId(909L);
            return 1;
        });
        when(taskMapper.updateSentLifecycle(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(1);
        when(taskEventService.transition(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    OaSignTask task = invocation.getArgument(0);
                    OaSignTaskStatus target = invocation.getArgument(1);
                    task.setStatus(target.name());
                    task.setVersion(task.getVersion() + 1);
                    return task;
                });
        ReflectionTestUtils.setField(service, "signHrAccessService", mock(OaSignHrAccessService.class));
        ReflectionTestUtils.setField(service, "taskMapper", taskMapper);
        ReflectionTestUtils.setField(service, "taskEventService", taskEventService);
        ReflectionTestUtils.setField(service, "notificationOutboxService", outboxService);
        ReflectionTestUtils.setField(service, "placementPolicyService", new OaSignPlacementPolicyService());
        return service;
    }

    private OaSignPackage finalizedTaskPackage()
    {
        OaSignPackage value = new OaSignPackage();
        value.setPackageId(90L);
        value.setTaskId(9L);
        value.setEmployeeId(201L);
        value.setShopDeptId(1171L);
        value.setStatus(OaSignPackageStatus.PENDING_FINAL_CONFIRM);
        value.setSigningSequence(OaSignSigningSequence.SIGNATURE_FIRST);
        value.setLegalEntityIdSnapshot(31L);
        value.setSealIdSnapshot(41L);
        value.setVersion(8L);
        return value;
    }

    private OaSignTask finalizedTask(Long assignedHrUserId)
    {
        OaSignTask value = new OaSignTask();
        value.setTaskId(9L);
        value.setPackageId(90L);
        value.setEmployeeId(201L);
        value.setShopDeptId(1171L);
        value.setAssignedHrUserId(assignedHrUserId);
        value.setStatus(OaSignTaskStatus.PENDING_FINAL_CONFIRM.name());
        value.setVersion(12L);
        return value;
    }

    private OaSignPackageFinalizeRequest finalizedReplayRequest()
    {
        OaSignPackageFinalizeRequest value = new OaSignPackageFinalizeRequest();
        value.setLegalEntityId(31L);
        value.setSealId(41L);
        value.setSigningSequence(OaSignSigningSequence.SIGNATURE_FIRST);
        value.setExpectedVersion(7L);
        value.setExpectedTaskVersion(11L);
        return value;
    }

    private GeneratedRollbackFixture generatedRollbackFixture(List<OaSignTemplate> templates)
    {
        OaSignPackageServiceImpl service = newPackageService();
        OaSignPackageMapper packageMapper = mock(OaSignPackageMapper.class);
        OaSignTemplateMapper templateMapper = mock(OaSignTemplateMapper.class);
        OaSignPlanMapper planMapper = mock(OaSignPlanMapper.class);
        OaSignPackageDocumentMapper documentMapper = mock(OaSignPackageDocumentMapper.class);
        OaSignEventMapper eventMapper = mock(OaSignEventMapper.class);
        OaSignFileEvidenceMapper evidenceMapper = mock(OaSignFileEvidenceMapper.class);
        ShopScopeService shopScopeService = mock(ShopScopeService.class);
        OaSignDocumentService documentService = mock(OaSignDocumentService.class);
        ReflectionTestUtils.setField(service, "packageMapper", packageMapper);
        ReflectionTestUtils.setField(service, "templateMapper", templateMapper);
        ReflectionTestUtils.setField(service, "planMapper", planMapper);
        ReflectionTestUtils.setField(service, "documentMapper", documentMapper);
        ReflectionTestUtils.setField(service, "eventMapper", eventMapper);
        ReflectionTestUtils.setField(service, "evidenceMapper", evidenceMapper);
        ReflectionTestUtils.setField(service, "shopScopeService", shopScopeService);
        ReflectionTestUtils.setField(service, "documentService", documentService);
        when(documentService.readConfiguredFileBytes(any()))
                .thenReturn(TEST_TEMPLATE_SOURCE);
        SecurityContextHolder.setUserId("1");
        SecurityContextHolder.setUserName("admin");
        OaSignPackage signPackage = new OaSignPackage();
        signPackage.setPackageId(1L);
        signPackage.setEmployeeId(201L);
        signPackage.setStatus(OaSignPackageStatus.DRAFT);
        signPackage.setScenario("onboard");
        signPackage.setSocialType("有社保");
        signPackage.setEmploymentType("劳动合同");
        signPackage.setShopDeptId(1171L);
        signPackage.setSourcePlanId(20L);
        when(packageMapper.selectOaSignPackageById(1L)).thenReturn(signPackage);
        when(planMapper.selectActiveTemplatesByPlanId(20L)).thenReturn(templates);
        when(planMapper.selectOaSignPlanById(20L)).thenReturn(activeDeadlinePlan(20L));
        when(packageMapper.markSentWithVersion(any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any())).thenReturn(1);
        return new GeneratedRollbackFixture(service, packageMapper, documentMapper, eventMapper,
                documentService, signPackage);
    }

    private OaSignPlan activeDeadlinePlan(Long planId)
    {
        OaSignPlan plan = new OaSignPlan();
        plan.setPlanId(planId);
        plan.setStatus("0");
        plan.setSignDeadlineDays(7);
        return plan;
    }

    private GeneratedSignDocument generatedDocument(String name)
    {
        return new GeneratedSignDocument("/" + name + ".docx", name + "-source-hash",
                "/" + name + ".pdf", name + "-review-hash", "SP-1-V1",
                "source/" + name + ".docx", 10L, "review/" + name + ".pdf", 20L);
    }

    private record GeneratedRollbackFixture(OaSignPackageServiceImpl service,
            OaSignPackageMapper packageMapper, OaSignPackageDocumentMapper documentMapper,
            OaSignEventMapper eventMapper, OaSignDocumentService documentService,
            OaSignPackage signPackage)
    {
    }

    @Test
    @DisplayName("手工创建签约包不能伪造不可变方案版本引用")
    void shouldClearClientPlanVersionWhenCreatingManualPackage()
    {
        OaSignPackageServiceImpl service = newPackageService();
        OaSignPackageMapper packageMapper = mock(OaSignPackageMapper.class);
        OaSignPackageDocumentMapper documentMapper = mock(OaSignPackageDocumentMapper.class);
        OaSignEventMapper eventMapper = mock(OaSignEventMapper.class);
        ShopScopeService shopScopeService = mock(ShopScopeService.class);
        ReflectionTestUtils.setField(service, "packageMapper", packageMapper);
        ReflectionTestUtils.setField(service, "documentMapper", documentMapper);
        ReflectionTestUtils.setField(service, "eventMapper", eventMapper);
        ReflectionTestUtils.setField(service, "shopScopeService", shopScopeService);
        SecurityContextHolder.setUserId("101");
        SecurityContextHolder.setUserName("唯一HR");
        when(shopScopeService.resolveRequiredShopDept(1171L)).thenReturn(1171L);
        when(shopScopeService.resolveShopDeptName(1171L)).thenReturn("测试门店");

        OaSignPackage draft = new OaSignPackage();
        draft.setEmployeeId(201L);
        draft.setShopDeptId(1171L);
        draft.setSourcePlanId(77L);
        draft.setPlanVersionId(999999999L);
        draft.setEmployeeNameSnapshot("员工甲");
        draft.setEmployeePhoneSnapshot("13800000000");
        draft.setEmployeeIdCardSnapshot("TEST-ID-****-0011");
        draft.setScenario("renew");
        when(packageMapper.insertOaSignPackage(draft)).thenAnswer(invocation -> {
            draft.setPackageId(90L);
            return 1;
        });
        when(packageMapper.selectOaSignPackageById(90L)).thenReturn(draft);
        when(documentMapper.selectDocumentsByPackageId(90L)).thenReturn(List.of());
        when(eventMapper.selectEventsByPackageId(90L)).thenReturn(List.of());

        OaSignPackage created = service.createPackage(draft, 1171L);

        assertThat(created.getPlanVersionId()).isNull();
        assertThat(created.getSourcePlanId()).isEqualTo(77L);
        assertThat(created.getScenario()).isEqualTo("renewal");
        assertThat(created.getShopDeptName()).isEqualTo("测试门店");
    }

    @Test
    @DisplayName("待补资料任务可创建绑定草稿且关键归属以任务为准")
    void shouldCreateAndLinkDraftForAssignedHrTask()
    {
        OaSignPackageServiceImpl service = newPackageService();
        OaSignPackageMapper packageMapper = mock(OaSignPackageMapper.class);
        OaSignPackageDocumentMapper documentMapper = mock(OaSignPackageDocumentMapper.class);
        OaSignEventMapper eventMapper = mock(OaSignEventMapper.class);
        OaSignTaskMapper taskMapper = mock(OaSignTaskMapper.class);
        ShopScopeService shopScopeService = mock(ShopScopeService.class);
        ReflectionTestUtils.setField(service, "packageMapper", packageMapper);
        ReflectionTestUtils.setField(service, "documentMapper", documentMapper);
        ReflectionTestUtils.setField(service, "eventMapper", eventMapper);
        ReflectionTestUtils.setField(service, "taskMapper", taskMapper);
        ReflectionTestUtils.setField(service, "shopScopeService", shopScopeService);
        SecurityContextHolder.setUserId("101");
        SecurityContextHolder.setUserName("唯一HR");

        OaSignTask task = new OaSignTask();
        task.setTaskId(9L);
        task.setEmployeeId(201L);
        task.setScenario("onboard");
        task.setShopDeptId(1171L);
        task.setAssignedHrUserId(101L);
        task.setPlanVersionId(55L);
        task.setStatus(OaSignTaskStatus.NEEDS_DATA.name());
        when(taskMapper.selectOaSignTaskById(9L)).thenReturn(task);
        when(taskMapper.updatePackageLink(9L, 90L, 55L, 101L)).thenReturn(1);
        when(shopScopeService.resolveRequiredShopDept(1171L)).thenReturn(1171L);
        when(shopScopeService.resolveScopeDeptIds(1171L)).thenReturn(List.of(1171L));

        OaSignPackage draft = new OaSignPackage();
        draft.setTaskId(9L);
        draft.setEmployeeId(999L);
        draft.setScenario("offboard");
        draft.setShopDeptId(999L);
        draft.setSourcePlanId(999L);
        draft.setEmployeeNameSnapshot("员工甲");
        draft.setEmployeePhoneSnapshot("13800000000");
        draft.setEmployeeIdCardSnapshot("TEST-ID-****-0011");
        when(packageMapper.insertOaSignPackage(draft)).thenAnswer(invocation -> {
            draft.setPackageId(90L);
            return 1;
        });
        when(packageMapper.selectOaSignPackageById(90L)).thenReturn(draft);
        when(documentMapper.selectDocumentsByPackageId(90L)).thenReturn(List.of());
        when(eventMapper.selectEventsByPackageId(90L)).thenReturn(List.of());

        OaSignPackage created = service.createPackage(draft, 1171L);

        assertThat(created.getEmployeeId()).isEqualTo(201L);
        assertThat(created.getScenario()).isEqualTo("onboard");
        assertThat(created.getShopDeptId()).isEqualTo(1171L);
        assertThat(created.getPlanVersionId()).isEqualTo(55L);
        assertThat(created.getSourcePlanId()).isEqualTo(55L);
        assertThat(created.getConfirmStatus()).isEqualTo(OaSignTaskStatus.NEEDS_DATA.name());
        verify(taskMapper).updatePackageLink(9L, 90L, 55L, 101L);
    }

    @Test
    @DisplayName("任务草稿生成文件时强制使用任务不可变方案版本而非签约包传入值")
    void shouldDeriveTemplatesFromTaskPlanVersionWhenPreparingDocuments()
    {
        OaSignPackageServiceImpl service = newPackageService();
        OaSignPackageMapper packageMapper = mock(OaSignPackageMapper.class);
        OaSignPlanMapper planMapper = mock(OaSignPlanMapper.class);
        OaSignPlanVersionMapper planVersionMapper = mock(OaSignPlanVersionMapper.class);
        OaSignTemplateMapper templateMapper = mock(OaSignTemplateMapper.class);
        OaSignPackageDocumentMapper documentMapper = mock(OaSignPackageDocumentMapper.class);
        OaSignEventMapper eventMapper = mock(OaSignEventMapper.class);
        OaSignFileEvidenceMapper evidenceMapper = mock(OaSignFileEvidenceMapper.class);
        OaSignTaskMapper taskMapper = mock(OaSignTaskMapper.class);
        ShopScopeService shopScopeService = mock(ShopScopeService.class);
        OaSignDocumentService documentService = mock(OaSignDocumentService.class);
        ReflectionTestUtils.setField(service, "packageMapper", packageMapper);
        ReflectionTestUtils.setField(service, "planMapper", planMapper);
        ReflectionTestUtils.setField(service, "planVersionMapper", planVersionMapper);
        ReflectionTestUtils.setField(service, "templateMapper", templateMapper);
        ReflectionTestUtils.setField(service, "documentMapper", documentMapper);
        ReflectionTestUtils.setField(service, "eventMapper", eventMapper);
        ReflectionTestUtils.setField(service, "evidenceMapper", evidenceMapper);
        ReflectionTestUtils.setField(service, "taskMapper", taskMapper);
        ReflectionTestUtils.setField(service, "shopScopeService", shopScopeService);
        ReflectionTestUtils.setField(service, "documentService", documentService);
        when(documentService.readConfiguredFileBytes(any()))
                .thenReturn(TEST_TEMPLATE_SOURCE);
        SecurityContextHolder.setUserId("101");
        SecurityContextHolder.setUserName("唯一HR");

        OaSignPackage signPackage = editableTaskPackage();
        signPackage.setSourcePlanId(999L);
        signPackage.setPlanVersionId(999L);
        OaSignTask task = editableTask(OaSignTaskStatus.VALIDATING);
        OaSignTemplate boundTemplate = template(20L, "任务版本合同",
                OaSignTemplateType.ONBOARD_POST_DUTY, null);
        when(packageMapper.selectOaSignPackageById(90L)).thenReturn(signPackage);
        when(taskMapper.selectOaSignTaskById(9L)).thenReturn(task);
        when(shopScopeService.resolveRequiredShopDept(1171L)).thenReturn(1171L);
        when(planVersionMapper.selectTemplatesByVersionId(55L))
                .thenReturn(List.of(versionSnapshot(boundTemplate)));
        when(documentService.renderPackageDocument(eq(signPackage), any(OaSignTemplate.class), any()))
                .thenReturn(new GeneratedSignDocument("/profile/sign-package/90/task.docx", "source-hash",
                        "/profile/sign-package/90/task.pdf", "review-hash", "SP-90-V1"));
        when(packageMapper.updateOaSignPackage(any())).thenReturn(1);
        when(documentMapper.selectDocumentsByPackageId(90L)).thenReturn(List.of());
        when(eventMapper.selectEventsByPackageId(90L)).thenReturn(List.of());

        service.preparePackageDocuments(90L, 1171L);

        verify(planVersionMapper).selectTemplatesByVersionId(55L);
        verify(planVersionMapper, never()).selectTemplatesByVersionId(999L);
        verify(planMapper, never()).selectActiveTemplatesByPlanId(any());
        verify(templateMapper, never()).selectMatchedActiveTemplates(any());
        ArgumentCaptor<OaSignTemplate> templateCaptor = ArgumentCaptor.forClass(OaSignTemplate.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> templateTypesCaptor = ArgumentCaptor.forClass(List.class);
        verify(documentService).renderPackageDocument(eq(signPackage), templateCaptor.capture(),
                templateTypesCaptor.capture());
        assertThat(templateCaptor.getValue().getTemplateId()).isEqualTo(boundTemplate.getTemplateId());
        assertThat(templateCaptor.getValue().getFileHash()).isEqualTo(TEST_TEMPLATE_SOURCE_HASH);
        assertThat(templateTypesCaptor.getValue())
                .containsExactly(OaSignTemplateType.ONBOARD_POST_DUTY);
        ArgumentCaptor<OaSignPackage> updateCaptor = ArgumentCaptor.forClass(OaSignPackage.class);
        verify(packageMapper).updateOaSignPackage(updateCaptor.capture());
        assertThat(updateCaptor.getValue().getSourcePlanId()).isEqualTo(55L);
        assertThat(updateCaptor.getValue().getPlanVersionId()).isEqualTo(55L);
    }

    @Test
    @DisplayName("重复生成前同事务清除全部旧证据并仅在提交后按hash删除旧文件")
    void shouldInvalidateAllOldEvidenceBeforeRegeneratingDocuments()
    {
        OaSignPackageServiceImpl service = newPackageService();
        OaSignPackageMapper packageMapper = mock(OaSignPackageMapper.class);
        OaSignPlanMapper planMapper = mock(OaSignPlanMapper.class);
        OaSignPlanVersionMapper planVersionMapper = mock(OaSignPlanVersionMapper.class);
        OaSignPackageDocumentMapper documentMapper = mock(OaSignPackageDocumentMapper.class);
        OaSignEventMapper eventMapper = mock(OaSignEventMapper.class);
        OaSignFileEvidenceMapper evidenceMapper = mock(OaSignFileEvidenceMapper.class);
        OaSignTaskMapper taskMapper = mock(OaSignTaskMapper.class);
        ShopScopeService shopScopeService = mock(ShopScopeService.class);
        OaSignDocumentService documentService = mock(OaSignDocumentService.class);
        OaSignFileStorageService fileStorageService = mock(OaSignFileStorageService.class);
        ReflectionTestUtils.setField(service, "packageMapper", packageMapper);
        ReflectionTestUtils.setField(service, "planMapper", planMapper);
        ReflectionTestUtils.setField(service, "planVersionMapper", planVersionMapper);
        ReflectionTestUtils.setField(service, "documentMapper", documentMapper);
        ReflectionTestUtils.setField(service, "eventMapper", eventMapper);
        ReflectionTestUtils.setField(service, "evidenceMapper", evidenceMapper);
        ReflectionTestUtils.setField(service, "taskMapper", taskMapper);
        ReflectionTestUtils.setField(service, "shopScopeService", shopScopeService);
        ReflectionTestUtils.setField(service, "documentService", documentService);
        ReflectionTestUtils.setField(service, "fileStorageService", fileStorageService);
        when(documentService.readConfiguredFileBytes(any()))
                .thenReturn(TEST_TEMPLATE_SOURCE);
        SecurityContextHolder.setUserId("101");
        SecurityContextHolder.setUserName("唯一HR");

        OaSignPackage signPackage = editableTaskPackage();
        signPackage.setSourcePlanId(55L);
        signPackage.setDocumentVersion("SP-90-V2");
        OaSignTask task = editableTask(OaSignTaskStatus.VALIDATING);
        OaSignTemplate boundTemplate = template(20L, "任务版本合同",
                OaSignTemplateType.ONBOARD_POST_DUTY, null);
        OaSignFileEvidence versionOne = evidence("SP-90-V1", "old/v1.pdf", "old-v1-hash");
        OaSignFileEvidence versionTwo = evidence("SP-90-V2", "old/v2.pdf", "old-v2-hash");
        when(packageMapper.selectOaSignPackageById(90L)).thenReturn(signPackage);
        when(taskMapper.selectOaSignTaskById(9L)).thenReturn(task);
        when(shopScopeService.resolveRequiredShopDept(1171L)).thenReturn(1171L);
        when(planVersionMapper.selectTemplatesByVersionId(55L))
                .thenReturn(List.of(versionSnapshot(boundTemplate)));
        when(evidenceMapper.selectEvidenceByPackageId(90L)).thenReturn(List.of(versionOne, versionTwo));
        when(documentService.renderPackageDocument(eq(signPackage), any(OaSignTemplate.class), any()))
                .thenReturn(new GeneratedSignDocument("/profile/sign-package/90/task.docx", "new-source-hash",
                        "/profile/sign-package/90/task.pdf", "new-review-hash", "SP-90-V3"));
        when(packageMapper.updateOaSignPackage(any())).thenReturn(1);
        when(documentMapper.selectDocumentsByPackageId(90L)).thenReturn(List.of());
        when(eventMapper.selectEventsByPackageId(90L)).thenReturn(List.of());

        TransactionSynchronizationManager.initSynchronization();
        try
        {
            service.preparePackageDocuments(90L, 1171L);

            assertThat(org.mockito.Mockito.mockingDetails(evidenceMapper).getInvocations())
                    .anySatisfy(invocation -> {
                        assertThat(invocation.getMethod().getName()).isEqualTo("deleteEvidenceByPackageId");
                        assertThat((Object) invocation.getArgument(0)).isEqualTo(90L);
                    });
            verify(fileStorageService, never()).discardArchivedEvidence(any(), any());
            for (TransactionSynchronization synchronization
                    : TransactionSynchronizationManager.getSynchronizations())
            {
                synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
            }
            verify(fileStorageService, never()).discardArchivedEvidence(any(), any());
            for (TransactionSynchronization synchronization
                    : TransactionSynchronizationManager.getSynchronizations())
            {
                synchronization.afterCommit();
            }
            verify(fileStorageService).discardArchivedEvidence("old/v1.pdf", "old-v1-hash");
            verify(fileStorageService).discardArchivedEvidence("old/v2.pdf", "old-v2-hash");
        }
        finally
        {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("第二个模板生成失败时事务回滚清理第一个模板的源文件和阅读PDF")
    void shouldCleanupFirstGeneratedDocumentWhenSecondTemplateFails()
    {
        OaSignTemplate first = template(31L, "第一份", OaSignTemplateType.ONBOARD_COMMITMENT, null);
        OaSignTemplate second = template(32L, "第二份", OaSignTemplateType.ONBOARD_ARCHIVE_CATALOG, null);
        GeneratedRollbackFixture fixture = generatedRollbackFixture(List.of(first, second));
        GeneratedSignDocument generated = generatedDocument("first");
        List<String> actualTypes = List.of(first.getTemplateType(), second.getTemplateType());
        when(fixture.documentService.renderPackageDocument(fixture.signPackage, first, actualTypes))
                .thenReturn(generated);
        when(fixture.documentService.renderPackageDocument(fixture.signPackage, second, actualTypes))
                .thenThrow(new ServiceException("第二模板失败"));

        TransactionSynchronizationManager.initSynchronization();
        try
        {
            assertThatThrownBy(() -> fixture.service.sendPackage(1L, 1171L))
                    .isInstanceOf(ServiceException.class)
                    .hasMessageContaining("第二模板失败");
            for (TransactionSynchronization synchronization
                    : TransactionSynchronizationManager.getSynchronizations())
            {
                synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
            }
            verify(fixture.documentService).discardUncommitted(generated);
        }
        finally
        {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("生成文件后的数据库失败由事务完成回调清理新归档文件")
    void shouldCleanupGeneratedDocumentWhenDatabaseWriteFails()
    {
        OaSignTemplate template = template(31L, "合同", OaSignTemplateType.ONBOARD_COMMITMENT, null);
        GeneratedRollbackFixture fixture = generatedRollbackFixture(List.of(template));
        GeneratedSignDocument generated = generatedDocument("db-failure");
        when(fixture.documentService.renderPackageDocument(fixture.signPackage, template,
                List.of(template.getTemplateType()))).thenReturn(generated);
        when(fixture.documentMapper.insertOaSignPackageDocument(any()))
                .thenThrow(new ServiceException("文档DB失败"));

        TransactionSynchronizationManager.initSynchronization();
        try
        {
            assertThatThrownBy(() -> fixture.service.sendPackage(1L, 1171L))
                    .isInstanceOf(ServiceException.class)
                    .hasMessageContaining("文档DB失败");
            for (TransactionSynchronization synchronization
                    : TransactionSynchronizationManager.getSynchronizations())
            {
                synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
            }
            verify(fixture.documentService).discardUncommitted(generated);
        }
        finally
        {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("生成文件事务提交成功后回调保留全部新归档文件")
    void shouldKeepGeneratedDocumentAfterCommit()
    {
        OaSignTemplate template = template(31L, "合同", OaSignTemplateType.ONBOARD_COMMITMENT, null);
        GeneratedRollbackFixture fixture = generatedRollbackFixture(List.of(template));
        GeneratedSignDocument generated = generatedDocument("committed");
        when(fixture.documentService.renderPackageDocument(fixture.signPackage, template,
                List.of(template.getTemplateType()))).thenReturn(generated);
        when(fixture.packageMapper.updateOaSignPackage(any())).thenReturn(1);
        when(fixture.documentMapper.selectDocumentsByPackageId(1L)).thenReturn(List.of());
        when(fixture.eventMapper.selectEventsByPackageId(1L)).thenReturn(List.of());

        TransactionSynchronizationManager.initSynchronization();
        try
        {
            fixture.service.sendPackage(1L, 1171L);

            assertThat(TransactionSynchronizationManager.getSynchronizations()).isNotEmpty();
            for (TransactionSynchronization synchronization
                    : TransactionSynchronizationManager.getSynchronizations())
            {
                synchronization.afterCompletion(TransactionSynchronization.STATUS_COMMITTED);
            }
            verify(fixture.documentService, never()).discardUncommitted(any());
        }
        finally
        {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("发送签约包时按岗位等级范围过滤匹配模板")
    void shouldFilterMatchedTemplatesByPostLevelWhenSendingPackage() throws Exception
    {
        OaSignPackageServiceImpl service = newPackageService();
        OaSignPackageMapper packageMapper = mock(OaSignPackageMapper.class);
        OaSignTemplateMapper templateMapper = mock(OaSignTemplateMapper.class);
        OaSignPlanMapper planMapper = mock(OaSignPlanMapper.class);
        OaSignPackageDocumentMapper documentMapper = mock(OaSignPackageDocumentMapper.class);
        OaSignEventMapper eventMapper = mock(OaSignEventMapper.class);
        OaSignFileEvidenceMapper evidenceMapper = mock(OaSignFileEvidenceMapper.class);
        ShopScopeService shopScopeService = mock(ShopScopeService.class);
        OaSignDocumentService documentService = mock(OaSignDocumentService.class);
        ReflectionTestUtils.setField(service, "packageMapper", packageMapper);
        ReflectionTestUtils.setField(service, "templateMapper", templateMapper);
        ReflectionTestUtils.setField(service, "planMapper", planMapper);
        ReflectionTestUtils.setField(service, "documentMapper", documentMapper);
        ReflectionTestUtils.setField(service, "eventMapper", eventMapper);
        ReflectionTestUtils.setField(service, "evidenceMapper", evidenceMapper);
        ReflectionTestUtils.setField(service, "shopScopeService", shopScopeService);
        ReflectionTestUtils.setField(service, "documentService", documentService);
        when(documentService.readConfiguredFileBytes(any()))
                .thenReturn(TEST_TEMPLATE_SOURCE);
        SecurityContextHolder.setUserId("1");
        SecurityContextHolder.setUserName("admin");

        OaSignPackage signPackage = new OaSignPackage();
        signPackage.setPackageId(1L);
        signPackage.setEmployeeId(201L);
        signPackage.setStatus("draft");
        signPackage.setScenario("onboard");
        signPackage.setSocialType("有社保");
        signPackage.setEmploymentType("劳动合同");
        signPackage.setShopDeptId(1171L);
        signPackage.setSourcePlanId(20L);
        signPackage.setPostLevelSnapshot("7级");
        when(packageMapper.selectOaSignPackageById(1L)).thenReturn(signPackage);
        when(shopScopeService.resolveRequiredShopDept(1171L)).thenReturn(1171L);
        when(planMapper.selectOaSignPlanById(20L)).thenReturn(activeDeadlinePlan(20L));
        when(planMapper.selectActiveTemplatesByPlanId(20L)).thenReturn(Arrays.asList(
                template(10L, "员工档案目录", OaSignTemplateType.ONBOARD_ARCHIVE_CATALOG, null),
                template(11L, "岗位职责确认书（2-4级）", OaSignTemplateType.ONBOARD_POST_DUTY, "2-4"),
                template(12L, "岗位职责确认书（7-8级）", OaSignTemplateType.ONBOARD_POST_DUTY, "7-8")));
        when(documentService.renderPackageDocument(any(), any(), any()))
                .thenReturn(new GeneratedSignDocument("/profile/sign-package/1/document.docx", "source-hash",
                        "/profile/sign-package/1/document.pdf", "review-hash", "SP-1-V1"));
        when(eventMapper.selectLatestEventHashByPackageId(eq(1L))).thenReturn(null);
        when(packageMapper.updateOaSignPackage(any())).thenReturn(1);
        when(packageMapper.markSentWithVersion(any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any())).thenReturn(1);

        service.sendPackage(1L, 1171L);

        ArgumentCaptor<OaSignTemplate> templateCaptor = ArgumentCaptor.forClass(OaSignTemplate.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> templateTypesCaptor = ArgumentCaptor.forClass(List.class);
        org.mockito.Mockito.verify(documentService, org.mockito.Mockito.times(2))
                .renderPackageDocument(eq(signPackage), templateCaptor.capture(),
                        templateTypesCaptor.capture());
        assertThat(templateCaptor.getAllValues()).extracting(OaSignTemplate::getTemplateName)
                .containsExactly("员工档案目录", "岗位职责确认书（7-8级）");
        assertThat(templateTypesCaptor.getAllValues()).allSatisfy(actualTypes ->
                assertThat(actualTypes).containsExactly(
                        OaSignTemplateType.ONBOARD_ARCHIVE_CATALOG,
                        OaSignTemplateType.ONBOARD_POST_DUTY));
        ArgumentCaptor<OaSignPackageDocument> documentCaptor = ArgumentCaptor.forClass(OaSignPackageDocument.class);
        verify(documentMapper, times(2)).insertOaSignPackageDocument(documentCaptor.capture());
        assertThat(documentCaptor.getAllValues()).allSatisfy(document -> {
            assertThat(document.getReviewPdfUrl()).isEqualTo("/profile/sign-package/1/document.pdf");
            assertThat(document.getReviewPdfHash()).isEqualTo("review-hash");
            assertThat(document.getDocumentVersion()).isEqualTo("SP-1-V1");
        });
        verify(evidenceMapper, org.mockito.Mockito.atLeast(4)).insertOaSignFileEvidence(any());
    }

    @Test
    @DisplayName("任务关联签约包禁止绕过任务中心直接发送")
    void shouldRejectLegacySendForTaskLinkedPackage()
    {
        OaSignPackageServiceImpl service = newPackageService();
        OaSignPackageMapper packageMapper = mock(OaSignPackageMapper.class);
        OaSignTemplateMapper templateMapper = mock(OaSignTemplateMapper.class);
        ShopScopeService shopScopeService = mock(ShopScopeService.class);
        ReflectionTestUtils.setField(service, "packageMapper", packageMapper);
        ReflectionTestUtils.setField(service, "templateMapper", templateMapper);
        ReflectionTestUtils.setField(service, "shopScopeService", shopScopeService);
        OaSignPackage signPackage = new OaSignPackage();
        signPackage.setPackageId(90L);
        signPackage.setTaskId(9L);
        signPackage.setShopDeptId(1171L);
        signPackage.setStatus(OaSignPackageStatus.DRAFT);
        when(packageMapper.selectOaSignPackageById(90L)).thenReturn(signPackage);
        when(shopScopeService.resolveRequiredShopDept(1171L)).thenReturn(1171L);

        assertThatThrownBy(() -> service.sendPackage(90L, 1171L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("合同签约中心");
        verify(templateMapper, never()).selectMatchedActiveTemplates(any());
    }

    @Test
    @DisplayName("续签应急手工包不得绕过续签生命周期门闩")
    void shouldRejectManualRenewalBeforeGeneratingOrCreatingTask()
    {
        OaSignPackageServiceImpl service = newPackageService();
        OaSignPackageMapper packageMapper = mock(OaSignPackageMapper.class);
        OaSignTemplateMapper templateMapper = mock(OaSignTemplateMapper.class);
        ShopScopeService shopScopeService = mock(ShopScopeService.class);
        ReflectionTestUtils.setField(service, "packageMapper", packageMapper);
        ReflectionTestUtils.setField(service, "templateMapper", templateMapper);
        ReflectionTestUtils.setField(service, "shopScopeService", shopScopeService);
        SecurityContextHolder.setUserId("101");
        SecurityContextHolder.setUserName("唯一HR");
        OaSignPackage signPackage = new OaSignPackage();
        signPackage.setPackageId(91L);
        signPackage.setEmployeeId(201L);
        signPackage.setShopDeptId(1171L);
        signPackage.setScenario("renewal");
        signPackage.setStatus(OaSignPackageStatus.DRAFT);
        when(packageMapper.selectOaSignPackageById(91L)).thenReturn(signPackage);
        when(shopScopeService.resolveRequiredShopDept(1171L)).thenReturn(1171L);

        assertThatThrownBy(() -> service.sendPackage(91L, 1171L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("生命周期任务");

        verify(templateMapper, never()).selectMatchedActiveTemplates(any());
        OaSignTaskMapper managedTaskMapper = (OaSignTaskMapper) ReflectionTestUtils.getField(
                service, "taskMapper");
        verify(managedTaskMapper, never()).insertOaSignTask(any());
    }

    @Test
    @DisplayName("来源方案签约包发送时优先使用方案绑定模板")
    void shouldUsePlanBoundTemplatesWhenSendingPackageFromPlan() throws Exception
    {
        OaSignPackageServiceImpl service = newPackageService();
        OaSignPackageMapper packageMapper = mock(OaSignPackageMapper.class);
        OaSignTemplateMapper templateMapper = mock(OaSignTemplateMapper.class);
        OaSignPlanMapper planMapper = mock(OaSignPlanMapper.class);
        OaSignPackageDocumentMapper documentMapper = mock(OaSignPackageDocumentMapper.class);
        OaSignEventMapper eventMapper = mock(OaSignEventMapper.class);
        OaSignFileEvidenceMapper evidenceMapper = mock(OaSignFileEvidenceMapper.class);
        ShopScopeService shopScopeService = mock(ShopScopeService.class);
        OaSignDocumentService documentService = mock(OaSignDocumentService.class);
        ReflectionTestUtils.setField(service, "packageMapper", packageMapper);
        ReflectionTestUtils.setField(service, "templateMapper", templateMapper);
        ReflectionTestUtils.setField(service, "planMapper", planMapper);
        ReflectionTestUtils.setField(service, "documentMapper", documentMapper);
        ReflectionTestUtils.setField(service, "eventMapper", eventMapper);
        ReflectionTestUtils.setField(service, "evidenceMapper", evidenceMapper);
        ReflectionTestUtils.setField(service, "shopScopeService", shopScopeService);
        ReflectionTestUtils.setField(service, "documentService", documentService);
        when(documentService.readConfiguredFileBytes(any()))
                .thenReturn(TEST_TEMPLATE_SOURCE);
        SecurityContextHolder.setUserId("1");
        SecurityContextHolder.setUserName("admin");

        OaSignPackage signPackage = new OaSignPackage();
        signPackage.setPackageId(1L);
        signPackage.setEmployeeId(201L);
        signPackage.setStatus(OaSignPackageStatus.DRAFT);
        signPackage.setScenario("onboard");
        signPackage.setSocialType("有社保");
        signPackage.setEmploymentType("劳动合同");
        signPackage.setShopDeptId(1171L);
        signPackage.setSourcePlanId(20L);
        signPackage.setPostLevelSnapshot("7级");
        OaSignTemplate planTemplate = template(20L, "项目总监合同", OaSignTemplateType.ONBOARD_POST_DUTY, "7-8");
        when(packageMapper.selectOaSignPackageById(1L)).thenReturn(signPackage);
        when(shopScopeService.resolveRequiredShopDept(1171L)).thenReturn(1171L);
        when(planMapper.selectOaSignPlanById(20L)).thenReturn(activeDeadlinePlan(20L));
        when(planMapper.selectActiveTemplatesByPlanId(20L)).thenReturn(Arrays.asList(planTemplate));
        when(documentService.renderPackageDocument(any(), any(), any()))
                .thenReturn(new GeneratedSignDocument("/profile/sign-package/1/director.docx", "source-hash",
                        "/profile/sign-package/1/director.pdf", "review-hash", "SP-1-V1"));
        when(eventMapper.selectLatestEventHashByPackageId(eq(1L))).thenReturn(null);
        when(packageMapper.updateOaSignPackage(any())).thenReturn(1);
        when(packageMapper.markSentWithVersion(any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any())).thenReturn(1);

        service.sendPackage(1L, 1171L);

        org.mockito.Mockito.verify(templateMapper, org.mockito.Mockito.never()).selectMatchedActiveTemplates(any());
        org.mockito.Mockito.verify(documentService).renderPackageDocument(
                eq(signPackage), eq(planTemplate),
                eq(List.of(OaSignTemplateType.ONBOARD_POST_DUTY)));
        verify(packageMapper).markSentWithVersion(eq(1L),
                eq(909L), eq(OaSignPackageStatus.DRAFT), eq(0L),
                eq("SP-1-V1"), any(Date.class), any(Date.class), eq("MANUAL_PLAN_SNAPSHOT"),
                eq(7), eq("NOT_REQUIRED"), eq("admin"));
        OaSignTaskMapper managedTaskMapper = (OaSignTaskMapper) ReflectionTestUtils.getField(
                service, "taskMapper");
        ArgumentCaptor<OaSignTask> taskCaptor = ArgumentCaptor.forClass(OaSignTask.class);
        verify(managedTaskMapper).insertOaSignTask(taskCaptor.capture());
        assertThat(taskCaptor.getValue().getSourceType()).isEqualTo("MANUAL_PACKAGE");
        assertThat(taskCaptor.getValue().getSourceBusinessId()).isEqualTo("1");
        assertThat(taskCaptor.getValue().getAssignedHrUserId()).isEqualTo(1L);
        assertThat(taskCaptor.getValue().getPackageId()).isEqualTo(1L);
        assertThat(taskCaptor.getValue().getStatus()).isEqualTo(OaSignTaskStatus.PENDING_SIGN.name());
        verify(managedTaskMapper).updateSentLifecycle(eq(909L),
                eq(OaSignTaskStatus.PENDING_SIGN.name()), eq(1L), eq(1L),
                any(Date.class), any(Date.class), eq("MANUAL_PLAN_SNAPSHOT"), eq(7));
    }

    @Test
    @DisplayName("生成包文档时冻结员工签名与企业章追加页策略")
    void shouldRequireReadConfirmationWhenTemplateOverridesEmployeeSignRequired() throws Exception
    {
        OaSignPackageServiceImpl service = newPackageService();
        OaSignPackageDocumentMapper documentMapper = mock(OaSignPackageDocumentMapper.class);
        OaSignEventMapper eventMapper = mock(OaSignEventMapper.class);
        OaSignFileEvidenceMapper evidenceMapper = mock(OaSignFileEvidenceMapper.class);
        OaSignDocumentService documentService = mock(OaSignDocumentService.class);
        ReflectionTestUtils.setField(service, "documentMapper", documentMapper);
        ReflectionTestUtils.setField(service, "eventMapper", eventMapper);
        ReflectionTestUtils.setField(service, "evidenceMapper", evidenceMapper);
        ReflectionTestUtils.setField(service, "documentService", documentService);
        when(documentService.readConfiguredFileBytes(any()))
                .thenReturn(TEST_TEMPLATE_SOURCE);
        when(documentService.renderPackageDocument(any(), any(), any()))
                .thenReturn(new GeneratedSignDocument("/profile/sign-package/1/leave.docx", "source-hash",
                        "/profile/sign-package/1/leave.pdf", "review-hash", "SP-1-V1"));
        when(eventMapper.selectLatestEventHashByPackageId(eq(1L))).thenReturn(null);

        OaSignPackage signPackage = new OaSignPackage();
        signPackage.setPackageId(1L);
        OaSignTemplate template = new OaSignTemplate();
        template.setTemplateId(10L);
        template.setTemplateType(OaSignTemplateType.OFFBOARD_LEAVE_CERTIFICATE);
        template.setTemplateName("离职证明签收");
        template.setFileUrl("/immutable/test-template-source.docx");
        template.setFileHash(TEST_TEMPLATE_SOURCE_HASH);
        template.setEmployeeSignRequired("Y");
        template.setCompanySealRequired("Y");
        template.setCompanySealPositionJson(
                OaSignPlacementPolicyService.APPENDED_CONFIRMATION_PAGE);

        ReflectionTestUtils.invokeMethod(service, "createPackageDocument", signPackage, template, 1,
                List.of(template.getTemplateType()));

        ArgumentCaptor<OaSignPackageDocument> documentCaptor = ArgumentCaptor.forClass(OaSignPackageDocument.class);
        org.mockito.Mockito.verify(documentMapper).insertOaSignPackageDocument(documentCaptor.capture());
        assertThat(documentCaptor.getValue().getEmployeeVisible()).isEqualTo("Y");
        assertThat(documentCaptor.getValue().getReadConfirmationRequired()).isEqualTo("Y");
        assertThat(documentCaptor.getValue().getEmployeeSignRequired()).isEqualTo("Y");
        assertThat(documentCaptor.getValue().getSignaturePositionJson())
                .isEqualTo(OaSignPlacementPolicyService.APPENDED_CONFIRMATION_PAGE);
        assertThat(documentCaptor.getValue().getCompanySealRequired()).isEqualTo("Y");
        assertThat(documentCaptor.getValue().getCompanySealPositionJson())
                .isEqualTo(OaSignPlacementPolicyService.APPENDED_CONFIRMATION_PAGE);
        assertThat(documentCaptor.getValue().getDocumentPolicyMode())
                .isEqualTo(OaSignPlacementPolicyService.SNAPSHOT_V1);
        assertThat(documentCaptor.getValue().getReadConfirmed()).isEqualTo("N");
        assertThat(documentCaptor.getValue().getReviewPdfHash()).isEqualTo("review-hash");
        assertThat(documentCaptor.getValue().getDocumentVersion()).isEqualTo("SP-1-V1");

        ArgumentCaptor<OaSignEvent> eventCaptor = ArgumentCaptor.forClass(OaSignEvent.class);
        org.mockito.Mockito.verify(eventMapper).insertOaSignEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getEventType()).isEqualTo("DOCUMENT_GENERATED");
    }

    @Test
    @DisplayName("新劳动合同持久化经正式校验边界返回的顺序绑定四签一章快照")
    void shouldFreezeNewLaborDocumentWithHashBoundMultiPlacement() throws Exception
    {
        OaSignPackageServiceImpl service = newPackageService();
        OaSignPackageDocumentMapper documentMapper = mock(OaSignPackageDocumentMapper.class);
        OaSignEventMapper eventMapper = mock(OaSignEventMapper.class);
        OaSignFileEvidenceMapper evidenceMapper = mock(OaSignFileEvidenceMapper.class);
        OaSignDocumentService documentService = mock(OaSignDocumentService.class);
        OaSignPlacementPolicyService placementPolicy = mock(OaSignPlacementPolicyService.class);
        ReflectionTestUtils.setField(service, "documentMapper", documentMapper);
        ReflectionTestUtils.setField(service, "eventMapper", eventMapper);
        ReflectionTestUtils.setField(service, "evidenceMapper", evidenceMapper);
        ReflectionTestUtils.setField(service, "documentService", documentService);
        ReflectionTestUtils.setField(service, "placementPolicyService", placementPolicy);
        byte[] exactV7Source;
        try (java.io.InputStream input = getClass().getResourceAsStream(
                "/oa/sign/templates/onboard-20260721-v7/09_ONBOARD_LABOR_CONTRACT.docx"))
        {
            assertThat(input).isNotNull();
            exactV7Source = input.readAllBytes();
        }
        String reviewPdfHash =
                "552e2b6a17e6206ea4c12c761d02abf698db3281ecf1861a858ce0024b7ce7d4";
        String signaturePolicy = laborMultiPolicy(reviewPdfHash, true,
                "SIGNATURE_FIRST_BODY_PLACEMENT");
        String sealPolicy = laborMultiPolicy(reviewPdfHash, false,
                "SIGNATURE_FIRST_BODY_PLACEMENT");
        when(documentService.readConfiguredFileBytes("/immutable/exact-v7.docx"))
                .thenReturn(exactV7Source);
        when(documentService.renderPackageDocument(any(), any(), any()))
                .thenReturn(new GeneratedSignDocument(
                        "/profile/sign-package/1/labor.docx", "source-hash",
                        "/profile/sign-package/1/labor.pdf", reviewPdfHash, "SP-1-V1"));
        when(documentService.resolveGeneratedSignPackageFile(
                "/profile/sign-package/1/labor.pdf"))
                .thenReturn(tempDir.resolve("verified-review.pdf"));
        when(placementPolicy.normalizeCompanySealRequired("Y", true)).thenReturn("Y");
        when(placementPolicy.validateAndFreezeGeneratedPositions(any(), any(), any(), any(),
                any(), any(), any(), anyBoolean(), anyBoolean(), any(), anyBoolean(), any()))
                .thenReturn(new OaSignPlacementPolicyService.GeneratedPlacementPolicies(
                        signaturePolicy, sealPolicy, "exact-v7-dynamic"));
        when(eventMapper.selectLatestEventHashByPackageId(1L)).thenReturn(null);

        OaSignPackage signPackage = new OaSignPackage();
        signPackage.setPackageId(1L);
        signPackage.setSigningSequence(OaSignSigningSequence.SIGNATURE_FIRST);
        signPackage.setLegalRepresentativeSnapshot("测试代表");
        OaSignTemplate template = template(12L, "劳动合同",
                OaSignTemplateType.ONBOARD_LABOR_CONTRACT, null);
        template.setTemplateVersion("20260721-v7");
        template.setFileUrl("/immutable/exact-v7.docx");
        template.setFileHash(
                "1226728ec0e703d513efda83dc5578ee4cdd65cb3e7d0e7cb5afee813f466558");
        template.setEmployeeSignRequired("Y");
        template.setCompanySealRequired("Y");
        template.setSignaturePositionJson(
                OaSignPlacementPolicyService.APPENDED_CONFIRMATION_PAGE);
        template.setCompanySealPositionJson(
                OaSignPlacementPolicyService.APPENDED_CONFIRMATION_PAGE);

        ReflectionTestUtils.invokeMethod(service, "createPackageDocument", signPackage,
                template, 1, List.of(template.getTemplateType()));

        ArgumentCaptor<OaSignPackageDocument> captor =
                ArgumentCaptor.forClass(OaSignPackageDocument.class);
        verify(documentMapper).insertOaSignPackageDocument(captor.capture());
        OaSignPackageDocument frozen = captor.getValue();
        frozen.setDocumentId(1201L);
        assertThat(frozen.getSignaturePositionJson())
                .contains("\"mode\":\"PLACED_MULTI\"", reviewPdfHash,
                        "SIGNATURE_FIRST_BODY_PLACEMENT")
                .doesNotContain("textOverlays");
        assertThat(frozen.getCompanySealPositionJson())
                .contains("\"mode\":\"PLACED_MULTI\"", reviewPdfHash,
                        "SIGNATURE_FIRST_BODY_PLACEMENT")
                .doesNotContain("textOverlays");
        OaSignPlacementPolicyService realPlacementPolicy =
                new OaSignPlacementPolicyService();
        realPlacementPolicy.assertSigningSequenceMatches(signPackage, frozen);
        assertThat(realPlacementPolicy.resolveSignaturePlacement(frozen).expanded()).hasSize(4);
        assertThat(realPlacementPolicy.resolveCompanySealPlacement(frozen).expanded()).hasSize(1);
        assertThat(realPlacementPolicy.resolveDisplayTextPlacements(frozen)).isEmpty();
        assertThat(realPlacementPolicy.resolveFinalExportExpectedBodyPageCount(frozen))
                .isEqualTo(18);
        verify(placementPolicy).validateAndFreezeGeneratedPositions(
                eq(OaSignTemplateType.ONBOARD_LABOR_CONTRACT), eq("20260721-v7"),
                eq(template.getFileHash()), any(), any(), any(), eq(reviewPdfHash),
                eq(true), eq(true), eq("测试代表"), eq(false),
                eq(OaSignSigningSequence.SIGNATURE_FIRST));
    }

    @Test
    @DisplayName("员工只能下载本人签约包内的生成文件")
    void shouldResolveMyGeneratedDocumentFile() throws Exception
    {
        OaSignPackageServiceImpl service = newPackageService();
        OaSignPackageMapper packageMapper = mock(OaSignPackageMapper.class);
        OaSignPackageDocumentMapper documentMapper = mock(OaSignPackageDocumentMapper.class);
        OaSignDocumentService documentService = mock(OaSignDocumentService.class);
        ReflectionTestUtils.setField(service, "packageMapper", packageMapper);
        ReflectionTestUtils.setField(service, "documentMapper", documentMapper);
        ReflectionTestUtils.setField(service, "documentService", documentService);
        SecurityContextHolder.setUserId("960");

        OaSignPackage signPackage = new OaSignPackage();
        signPackage.setPackageId(1L);
        signPackage.setEmployeeId(960L);
        signPackage.setStatus(OaSignPackageStatus.SIGNED);
        when(packageMapper.selectOaSignPackageById(1L)).thenReturn(signPackage);
        OaSignPackageDocument document = new OaSignPackageDocument();
        document.setDocumentId(10L);
        document.setPackageId(1L);
        document.setDocumentName("入职承诺书");
        document.setEmployeeVisible("Y");
        document.setGeneratedFileUrl("/profile/sign-package/1/commitment.docx");
        document.setGeneratedPdfUrl("/profile/sign-package/1/commitment.pdf");
        when(documentMapper.selectOaSignPackageDocumentById(10L)).thenReturn(document);
        when(documentService.resolveGeneratedSignPackageFile("/profile/sign-package/1/commitment.pdf"))
                .thenReturn(Paths.get("/tmp/commitment.pdf"));

        OaSignPackageFile file = service.resolveMyDocumentFile(1L, 10L);

        assertThat(file.getPath()).isEqualTo(Paths.get("/tmp/commitment.pdf"));
        assertThat(file.getFileName()).isEqualTo("入职承诺书.pdf");
        assertThat(file.getContentType()).isEqualTo("application/pdf");
    }

    @Test
    @DisplayName("签约完成前原始文件禁止下载但认证图片预览仍可取源")
    void shouldRejectPendingRawDocumentDownloadButAllowServerPreviewSource()
    {
        OaSignPackageServiceImpl service = newPackageService();
        OaSignPackageMapper packageMapper = mock(OaSignPackageMapper.class);
        OaSignPackageDocumentMapper documentMapper = mock(OaSignPackageDocumentMapper.class);
        OaSignDocumentService documentService = mock(OaSignDocumentService.class);
        ReflectionTestUtils.setField(service, "packageMapper", packageMapper);
        ReflectionTestUtils.setField(service, "documentMapper", documentMapper);
        ReflectionTestUtils.setField(service, "documentService", documentService);
        SecurityContextHolder.setUserId("960");

        OaSignPackage signPackage = new OaSignPackage();
        signPackage.setPackageId(1L);
        signPackage.setEmployeeId(960L);
        signPackage.setStatus(OaSignPackageStatus.PENDING_SIGN);
        when(packageMapper.selectOaSignPackageById(1L)).thenReturn(signPackage);
        OaSignPackageDocument document = new OaSignPackageDocument();
        document.setDocumentId(10L);
        document.setPackageId(1L);
        document.setDocumentName("劳动合同");
        document.setEmployeeVisible("Y");
        document.setGeneratedPdfUrl("/profile/private/sign-package/review.pdf");
        when(documentMapper.selectOaSignPackageDocumentById(10L)).thenReturn(document);
        when(documentService.resolveGeneratedSignPackageFile(document.getGeneratedPdfUrl()))
                .thenReturn(Paths.get("/tmp/review.pdf"));

        assertThatThrownBy(() -> service.resolveMyDocumentFile(1L, 10L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("仅支持在线预览")
                .hasMessageContaining("不能下载原始文件");

        OaSignPackageFile previewSource = service.resolveMyDocumentPreviewFile(1L, 10L);
        assertThat(previewSource.getPath()).isEqualTo(Paths.get("/tmp/review.pdf"));
        assertThat(previewSource.getFileName()).isEqualTo("劳动合同.pdf");
    }

    @Test
    @DisplayName("签名先行最终候选显式发送前拒绝所有员工文件解析且发送后恢复")
    void shouldHidePreparedSignatureFirstFilesUntilExplicitSend()
    {
        OaSignPackageServiceImpl service = newPackageService();
        OaSignPackageMapper packageMapper = mock(OaSignPackageMapper.class);
        OaSignPackageDocumentMapper documentMapper = mock(OaSignPackageDocumentMapper.class);
        OaSignDocumentService documentService = mock(OaSignDocumentService.class);
        ReflectionTestUtils.setField(service, "packageMapper", packageMapper);
        ReflectionTestUtils.setField(service, "documentMapper", documentMapper);
        ReflectionTestUtils.setField(service, "documentService", documentService);
        SecurityContextHolder.setUserId("960");

        OaSignPackage signPackage = new OaSignPackage();
        signPackage.setPackageId(1L);
        signPackage.setEmployeeId(960L);
        signPackage.setStatus(OaSignPackageStatus.PENDING_COMPANY);
        signPackage.setSigningSequence(OaSignSigningSequence.SIGNATURE_FIRST);
        signPackage.setFinalConfirmationStatus("PREPARED_NOT_SENT");
        when(packageMapper.selectOaSignPackageById(1L)).thenReturn(signPackage);

        assertThatThrownBy(() -> service.resolveMyDocumentPreviewFile(1L, 10L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("尚未发送");
        assertThatThrownBy(() -> service.resolveMyFinalDocumentPreviewFile(1L, 10L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("尚未发送");
        assertThatThrownBy(() -> service.resolveMyDocumentFile(1L, 10L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("尚未发送");
        assertThatThrownBy(() -> service.resolveMyFinalDocumentFile(1L, 10L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("尚未发送");
        assertThatThrownBy(() -> service.resolveMySignedDocumentFile(1L, 10L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("尚未发送");
        assertThatThrownBy(() -> service.resolveMyCertificateFile(1L, 10L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("尚未发送");
        verify(documentMapper, never()).selectOaSignPackageDocumentById(any());

        signPackage.setStatus(OaSignPackageStatus.PENDING_FINAL_CONFIRM);
        signPackage.setFinalConfirmationStatus("PENDING");
        OaSignPackageDocument document = new OaSignPackageDocument();
        document.setDocumentId(10L);
        document.setPackageId(1L);
        document.setDocumentName("劳动合同");
        document.setEmployeeVisible("Y");
        document.setGeneratedPdfUrl("/profile/private/sign-package/sent-review.pdf");
        when(documentMapper.selectOaSignPackageDocumentById(10L)).thenReturn(document);
        when(documentService.resolveGeneratedSignPackageFile(document.getGeneratedPdfUrl()))
                .thenReturn(Paths.get("/tmp/sent-review.pdf"));

        OaSignPackageFile sentPreview = service.resolveMyDocumentPreviewFile(1L, 10L);
        assertThat(sentPreview.getPath()).isEqualTo(Paths.get("/tmp/sent-review.pdf"));
    }

    @Test
    @DisplayName("员工即使知道文件ID也不能下载HR内部材料")
    void shouldRejectInternalDocumentFileForEmployee()
    {
        OaSignPackageServiceImpl service = newPackageService();
        OaSignPackageMapper packageMapper = mock(OaSignPackageMapper.class);
        OaSignPackageDocumentMapper documentMapper = mock(OaSignPackageDocumentMapper.class);
        OaSignDocumentService documentService = mock(OaSignDocumentService.class);
        ReflectionTestUtils.setField(service, "packageMapper", packageMapper);
        ReflectionTestUtils.setField(service, "documentMapper", documentMapper);
        ReflectionTestUtils.setField(service, "documentService", documentService);
        SecurityContextHolder.setUserId("960");

        OaSignPackage signPackage = new OaSignPackage();
        signPackage.setPackageId(1L);
        signPackage.setEmployeeId(960L);
        when(packageMapper.selectOaSignPackageById(1L)).thenReturn(signPackage);
        OaSignPackageDocument document = new OaSignPackageDocument();
        document.setDocumentId(10L);
        document.setPackageId(1L);
        document.setTemplateType(OaSignTemplateType.ONBOARD_ARCHIVE_CATALOG);
        document.setEmployeeVisible("N");
        when(documentMapper.selectOaSignPackageDocumentById(10L)).thenReturn(document);

        assertThatThrownBy(() -> service.resolveMyDocumentFile(1L, 10L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("文件不存在");
        verify(documentService, never()).resolveGeneratedSignPackageFile(any());
    }

    @Test
    @DisplayName("员工详情只返回可见文件且不泄露内部事件")
    void shouldFilterInternalDocumentsFromEmployeeDetail()
    {
        OaSignPackageServiceImpl service = newPackageService();
        OaSignPackageMapper packageMapper = mock(OaSignPackageMapper.class);
        OaSignPackageDocumentMapper documentMapper = mock(OaSignPackageDocumentMapper.class);
        OaSignEventMapper eventMapper = mock(OaSignEventMapper.class);
        ReflectionTestUtils.setField(service, "packageMapper", packageMapper);
        ReflectionTestUtils.setField(service, "documentMapper", documentMapper);
        ReflectionTestUtils.setField(service, "eventMapper", eventMapper);
        SecurityContextHolder.setUserId("960");

        OaSignPackage signPackage = new OaSignPackage();
        signPackage.setPackageId(1L);
        signPackage.setEmployeeId(960L);
        when(packageMapper.selectOaSignPackageById(1L)).thenReturn(signPackage);
        OaSignPackageDocument visible = new OaSignPackageDocument();
        visible.setDocumentId(10L);
        visible.setPackageId(1L);
        visible.setEmployeeVisible("Y");
        OaSignPackageDocument internal = new OaSignPackageDocument();
        internal.setDocumentId(11L);
        internal.setPackageId(1L);
        internal.setEmployeeVisible("N");
        when(documentMapper.selectDocumentsByPackageId(1L)).thenReturn(List.of(visible, internal));
        OaSignEvent packageEvent = new OaSignEvent();
        packageEvent.setEventType("PACKAGE_SENT");
        OaSignEvent visibleEvent = new OaSignEvent();
        visibleEvent.setDocumentId(10L);
        OaSignEvent internalEvent = new OaSignEvent();
        internalEvent.setDocumentId(11L);
        when(eventMapper.selectEventsByPackageId(1L))
                .thenReturn(List.of(packageEvent, visibleEvent, internalEvent));

        OaSignPackage detail = service.getMyPackageDetail(1L);

        assertThat(detail.getDocuments()).extracting(OaSignPackageDocument::getDocumentId)
                .containsExactly(10L);
        assertThat(detail.getDocuments().get(0)).isNotSameAs(visible);
        assertThat(detail.getEvents()).isNull();
    }

    @Test
    @DisplayName("员工已签文件下载必须解析独立signedPdf而不是阅读PDF")
    void shouldResolveIndependentSignedDocumentFile() throws Exception
    {
        OaSignPackageServiceImpl service = newPackageService();
        OaSignPackageMapper packageMapper = mock(OaSignPackageMapper.class);
        OaSignPackageDocumentMapper documentMapper = mock(OaSignPackageDocumentMapper.class);
        OaSignDocumentService documentService = mock(OaSignDocumentService.class);
        ReflectionTestUtils.setField(service, "packageMapper", packageMapper);
        ReflectionTestUtils.setField(service, "documentMapper", documentMapper);
        ReflectionTestUtils.setField(service, "documentService", documentService);
        SecurityContextHolder.setUserId("960");

        OaSignPackage signPackage = new OaSignPackage();
        signPackage.setPackageId(1L);
        signPackage.setEmployeeId(960L);
        when(packageMapper.selectOaSignPackageById(1L)).thenReturn(signPackage);
        OaSignPackageDocument document = new OaSignPackageDocument();
        document.setDocumentId(10L);
        document.setPackageId(1L);
        document.setDocumentName("劳动合同");
        document.setEmployeeVisible("Y");
        document.setSigned("Y");
        document.setReviewPdfUrl("/profile/private/sign-package/review.pdf");
        document.setSignedPdfUrl("/profile/private/sign-package/signed.pdf");
        when(documentMapper.selectOaSignPackageDocumentById(10L)).thenReturn(document);
        when(documentService.resolveGeneratedSignPackageFile(document.getSignedPdfUrl()))
                .thenReturn(Paths.get("/tmp/signed.pdf"));

        OaSignPackageFile file = service.resolveMySignedDocumentFile(1L, 10L);

        assertThat(file.getPath()).isEqualTo(Paths.get("/tmp/signed.pdf"));
        assertThat(file.getFileName()).isEqualTo("劳动合同-已签.pdf");
    }

    @Test
    @DisplayName("员工不能下载非本人签约包文件")
    void shouldRejectOtherEmployeeDocumentFile() throws Exception
    {
        OaSignPackageServiceImpl service = newPackageService();
        OaSignPackageMapper packageMapper = mock(OaSignPackageMapper.class);
        ReflectionTestUtils.setField(service, "packageMapper", packageMapper);
        SecurityContextHolder.setUserId("960");

        OaSignPackage signPackage = new OaSignPackage();
        signPackage.setPackageId(1L);
        signPackage.setEmployeeId(961L);
        when(packageMapper.selectOaSignPackageById(1L)).thenReturn(signPackage);

        assertThatThrownBy(() -> service.resolveMyDocumentFile(1L, 10L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("只能查看和签署本人的签约包");
    }

    @Test
    @DisplayName("签署证明仅在文件已签署且证明生成后可下载")
    void shouldResolveCertificateOnlyAfterSigned() throws Exception
    {
        OaSignPackageServiceImpl service = newPackageService();
        OaSignPackageMapper packageMapper = mock(OaSignPackageMapper.class);
        OaSignPackageDocumentMapper documentMapper = mock(OaSignPackageDocumentMapper.class);
        OaSignDocumentService documentService = mock(OaSignDocumentService.class);
        ReflectionTestUtils.setField(service, "packageMapper", packageMapper);
        ReflectionTestUtils.setField(service, "documentMapper", documentMapper);
        ReflectionTestUtils.setField(service, "documentService", documentService);
        SecurityContextHolder.setUserId("960");

        OaSignPackage signPackage = new OaSignPackage();
        signPackage.setPackageId(1L);
        signPackage.setEmployeeId(960L);
        when(packageMapper.selectOaSignPackageById(1L)).thenReturn(signPackage);
        OaSignPackageDocument document = new OaSignPackageDocument();
        document.setDocumentId(10L);
        document.setPackageId(1L);
        document.setDocumentName("入职承诺书");
        document.setEmployeeVisible("Y");
        document.setSigned("Y");
        document.setCertificateFileUrl("/profile/sign-package/1/certificate.pdf");
        when(documentMapper.selectOaSignPackageDocumentById(10L)).thenReturn(document);
        when(documentService.resolveGeneratedSignPackageFile("/profile/sign-package/1/certificate.pdf"))
                .thenReturn(Paths.get("/tmp/certificate.pdf"));

        OaSignPackageFile file = service.resolveMyCertificateFile(1L, 10L);

        assertThat(file.getPath()).isEqualTo(Paths.get("/tmp/certificate.pdf"));
        assertThat(file.getFileName()).isEqualTo("入职承诺书-签署证明.pdf");
    }

    @Test
    @DisplayName("草稿签约包允许更新快照字段")
    void shouldUpdateDraftPackageSnapshotFields() throws Exception
    {
        OaSignPackageServiceImpl service = newPackageService();
        OaSignPackageMapper packageMapper = mock(OaSignPackageMapper.class);
        OaSignPackageDocumentMapper documentMapper = mock(OaSignPackageDocumentMapper.class);
        OaSignEventMapper eventMapper = mock(OaSignEventMapper.class);
        ShopScopeService shopScopeService = mock(ShopScopeService.class);
        ReflectionTestUtils.setField(service, "packageMapper", packageMapper);
        ReflectionTestUtils.setField(service, "documentMapper", documentMapper);
        ReflectionTestUtils.setField(service, "eventMapper", eventMapper);
        ReflectionTestUtils.setField(service, "shopScopeService", shopScopeService);
        SecurityContextHolder.setUserId("1");
        SecurityContextHolder.setUserName("admin");

        OaSignPackage existing = new OaSignPackage();
        existing.setPackageId(100L);
        existing.setStatus(OaSignPackageStatus.DRAFT);
        existing.setEmployeeId(960L);
        existing.setShopDeptId(1171L);
        existing.setSourcePlanId(20L);
        existing.setSourcePlanName("项目总监方案");
        when(packageMapper.selectOaSignPackageById(100L)).thenReturn(existing);
        when(shopScopeService.resolveRequiredShopDept(1171L)).thenReturn(1171L);
        when(packageMapper.updateDraftOaSignPackage(any())).thenReturn(1);
        when(documentMapper.selectDocumentsByPackageId(100L)).thenReturn(Arrays.asList());
        when(eventMapper.selectEventsByPackageId(100L)).thenReturn(Arrays.asList());

        OaSignPackage request = new OaSignPackage();
        request.setPackageId(999L);
        request.setStatus(OaSignPackageStatus.SIGNED);
        request.setEmployeeId(961L);
        request.setShopDeptId(1188L);
        request.setSourcePlanId(21L);
        request.setSourcePlanName("错误方案");
        request.setEmployeeNameSnapshot("张三");
        request.setEmployeePhoneSnapshot("13800000000");
        request.setEmployeeIdCardSnapshot("TEST-ID-****-1234");
        request.setEmployeeAddressSnapshot(null);
        request.setDeptIdSnapshot(300L);
        request.setDeptNameSnapshot("项目部");
        request.setScenario("onboarding");
        request.setPostNameSnapshot("项目总监");
        request.setPostLevelSnapshot("7级");
        request.setEmploymentType("全职");
        request.setContractTermCodeSnapshot("FIXED_TERM");
        request.setSocialType("北京");
        request.setServicePersonType("普通");
        request.setInsuranceType("五险一金");
        request.setSalaryVersion("2026A");
        request.setEntryDate("2026-07-10");
        request.setContractStartDate("2026-07-10");
        request.setContractEndDate("2029-07-09");
        request.setProbationStartDate("2026-07-10");
        request.setProbationEndDate("2026-10-09");
        request.setActualRegularizationDate("2026-10-10");
        request.setBaseSalary(new BigDecimal("10000.00"));
        request.setPostSalary(new BigDecimal("5000.00"));
        request.setFieldAllowance(new BigDecimal("800.00"));
        request.setSalaryTotal(new BigDecimal("15800.00"));
        request.setRemark("确认无误");

        service.updateDraftPackage(100L, request, 1171L);

        ArgumentCaptor<OaSignPackage> packageCaptor = ArgumentCaptor.forClass(OaSignPackage.class);
        org.mockito.Mockito.verify(packageMapper).updateDraftOaSignPackage(packageCaptor.capture());
        OaSignPackage update = packageCaptor.getValue();
        assertThat(update.getPackageId()).isEqualTo(100L);
        assertThat(update.getEmployeeId()).isEqualTo(960L);
        assertThat(update.getShopDeptId()).isEqualTo(1171L);
        assertThat(update.getSourcePlanId()).isEqualTo(20L);
        assertThat(update.getSourcePlanName()).isEqualTo("项目总监方案");
        assertThat(update.getStatus()).isEqualTo(OaSignPackageStatus.DRAFT);
        assertThat(update.getEmployeeNameSnapshot()).isEqualTo("张三");
        assertThat(update.getEmployeePhoneSnapshot()).isEqualTo("13800000000");
        assertThat(update.getEmployeeIdCardSnapshot()).isEqualTo("TEST-ID-****-1234");
        assertThat(update.getEmployeeAddressSnapshot()).isNull();
        assertThat(update.getScenario()).isEqualTo("onboarding");
        assertThat(update.getPostNameSnapshot()).isEqualTo("项目总监");
        assertThat(update.getPostLevelSnapshot()).isEqualTo("7级");
        assertThat(update.getContractTermCodeSnapshot()).isEqualTo("FIXED_TERM");
        assertThat(update.getActualRegularizationDate()).isEqualTo("2026-10-10");
        assertThat(update.getBaseSalary()).isEqualByComparingTo("10000.00");
        assertThat(update.getPostSalary()).isEqualByComparingTo("5000.00");
        assertThat(update.getFieldAllowance()).isEqualByComparingTo("800.00");
        assertThat(update.getSalaryTotal()).isEqualByComparingTo("15800.00");
        assertThat(update.getRemark()).isEqualTo("确认无误");
        assertThat(update.getUpdateBy()).isEqualTo("admin");
    }

    @Test
    @DisplayName("应急自由建包的审计来源在后续草稿编辑省略备注时依然保留")
    void shouldPreserveEmergencySourceReasonWhenDraftUpdateOmitsRemark()
    {
        OaSignPackageServiceImpl service = newPackageService();
        OaSignPackageMapper packageMapper = mock(OaSignPackageMapper.class);
        OaSignPackageDocumentMapper documentMapper = mock(OaSignPackageDocumentMapper.class);
        OaSignEventMapper eventMapper = mock(OaSignEventMapper.class);
        ShopScopeService shopScopeService = mock(ShopScopeService.class);
        ReflectionTestUtils.setField(service, "packageMapper", packageMapper);
        ReflectionTestUtils.setField(service, "documentMapper", documentMapper);
        ReflectionTestUtils.setField(service, "eventMapper", eventMapper);
        ReflectionTestUtils.setField(service, "shopScopeService", shopScopeService);
        SecurityContextHolder.setUserId("101");
        SecurityContextHolder.setUserName("签约HR");

        OaSignPackage existing = new OaSignPackage();
        existing.setPackageId(100L);
        existing.setStatus(OaSignPackageStatus.DRAFT);
        existing.setEmployeeId(960L);
        existing.setShopDeptId(1171L);
        existing.setRemark("非生命周期任务来源：纸质审批单已授权补录");
        when(packageMapper.selectOaSignPackageById(100L)).thenReturn(existing);
        when(shopScopeService.resolveRequiredShopDept(1171L)).thenReturn(1171L);
        when(packageMapper.updateDraftOaSignPackage(any())).thenReturn(1);
        when(documentMapper.selectDocumentsByPackageId(100L)).thenReturn(List.of());
        when(eventMapper.selectEventsByPackageId(100L)).thenReturn(List.of());
        OaSignPackage request = validDraftRequest();
        request.setRemark(null);

        service.updateDraftPackage(100L, request, 1171L);

        ArgumentCaptor<OaSignPackage> updateCaptor =
                ArgumentCaptor.forClass(OaSignPackage.class);
        verify(packageMapper).updateDraftOaSignPackage(updateCaptor.capture());
        assertThat(updateCaptor.getValue().getRemark())
                .isEqualTo("非生命周期任务来源：纸质审批单已授权补录");
    }

    @Test
    @DisplayName("应急自由建包的审计来源不能被去前缀或改写")
    void shouldRejectEmergencySourceReasonTamperingDuringDraftUpdate()
    {
        OaSignPackageServiceImpl service = newPackageService();
        OaSignPackageMapper packageMapper = mock(OaSignPackageMapper.class);
        ShopScopeService shopScopeService = mock(ShopScopeService.class);
        ReflectionTestUtils.setField(service, "packageMapper", packageMapper);
        ReflectionTestUtils.setField(service, "shopScopeService", shopScopeService);
        SecurityContextHolder.setUserId("101");

        OaSignPackage existing = new OaSignPackage();
        existing.setPackageId(100L);
        existing.setStatus(OaSignPackageStatus.DRAFT);
        existing.setEmployeeId(960L);
        existing.setShopDeptId(1171L);
        existing.setRemark("非生命周期任务来源：纸质审批单已授权补录");
        when(packageMapper.selectOaSignPackageById(100L)).thenReturn(existing);
        when(shopScopeService.resolveRequiredShopDept(1171L)).thenReturn(1171L);
        OaSignPackage request = validDraftRequest();
        request.setRemark("纸质审批单已授权补录");

        assertThatThrownBy(() -> service.updateDraftPackage(100L, request, 1171L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("来源标识和原因不可删除");
        verify(packageMapper, never()).updateDraftOaSignPackage(any());

        request.setRemark("非生命周期任务来源：改成其他原因");
        assertThatThrownBy(() -> service.updateDraftPackage(100L, request, 1171L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("来源原因不可修改");
        verify(packageMapper, never()).updateDraftOaSignPackage(any());
    }

    @Test
    @DisplayName("生命周期任务包不误用应急自由建包备注保护")
    void shouldNotApplyEmergencyRemarkProtectionToLifecycleTaskPackage()
    {
        OaSignPackageServiceImpl service = newPackageService();
        OaSignPackageMapper packageMapper = mock(OaSignPackageMapper.class);
        OaSignPackageDocumentMapper documentMapper = mock(OaSignPackageDocumentMapper.class);
        OaSignEventMapper eventMapper = mock(OaSignEventMapper.class);
        OaSignTaskMapper taskMapper = mock(OaSignTaskMapper.class);
        ShopScopeService shopScopeService = mock(ShopScopeService.class);
        ReflectionTestUtils.setField(service, "packageMapper", packageMapper);
        ReflectionTestUtils.setField(service, "documentMapper", documentMapper);
        ReflectionTestUtils.setField(service, "eventMapper", eventMapper);
        ReflectionTestUtils.setField(service, "taskMapper", taskMapper);
        ReflectionTestUtils.setField(service, "shopScopeService", shopScopeService);
        SecurityContextHolder.setUserId("101");
        SecurityContextHolder.setUserName("签约HR");

        OaSignPackage existing = editableTaskPackage();
        existing.setRemark("非生命周期任务来源：仅为普通任务备注文字");
        when(packageMapper.selectOaSignPackageById(90L)).thenReturn(existing);
        when(taskMapper.selectOaSignTaskById(9L))
                .thenReturn(editableTask(OaSignTaskStatus.NEEDS_DATA));
        when(shopScopeService.resolveScopeDeptIds(1171L)).thenReturn(List.of(1171L));
        when(packageMapper.updateDraftOaSignPackage(any())).thenReturn(1);
        when(documentMapper.selectDocumentsByPackageId(90L)).thenReturn(List.of());
        when(eventMapper.selectEventsByPackageId(90L)).thenReturn(List.of());
        OaSignPackage request = validDraftRequest("onboard");
        request.setRemark("生命周期任务业务备注");

        service.updateDraftPackage(90L, request, 1171L);

        ArgumentCaptor<OaSignPackage> updateCaptor =
                ArgumentCaptor.forClass(OaSignPackage.class);
        verify(packageMapper).updateDraftOaSignPackage(updateCaptor.capture());
        assertThat(updateCaptor.getValue().getRemark()).isEqualTo("生命周期任务业务备注");
    }

    @Test
    @DisplayName("普通用户缺少门店头时不允许编辑草稿签约包")
    void shouldRejectDraftUpdateWithoutShopContextForNonAdmin() throws Exception
    {
        OaSignPackageServiceImpl service = newPackageService();
        OaSignPackageMapper packageMapper = mock(OaSignPackageMapper.class);
        ShopScopeService shopScopeService = mock(ShopScopeService.class);
        ReflectionTestUtils.setField(service, "packageMapper", packageMapper);
        ReflectionTestUtils.setField(service, "shopScopeService", shopScopeService);
        SecurityContextHolder.setUserId("2");

        OaSignPackage existing = new OaSignPackage();
        existing.setPackageId(100L);
        existing.setStatus(OaSignPackageStatus.DRAFT);
        existing.setShopDeptId(1171L);
        when(packageMapper.selectOaSignPackageById(100L)).thenReturn(existing);
        when(shopScopeService.resolveRequiredShopDept(null))
                .thenThrow(new ServiceException("请先选择店铺或仓库"));

        assertThatThrownBy(() -> service.updateDraftPackage(100L, validDraftRequest(), null))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("请先选择店铺或仓库");
        org.mockito.Mockito.verify(shopScopeService).resolveRequiredShopDept(null);
        org.mockito.Mockito.verify(packageMapper, org.mockito.Mockito.never()).updateDraftOaSignPackage(any());
    }

    @Test
    @DisplayName("非草稿签约包不允许编辑")
    void shouldRejectUpdatingNonDraftPackage() throws Exception
    {
        OaSignPackageServiceImpl service = newPackageService();
        OaSignPackageMapper packageMapper = mock(OaSignPackageMapper.class);
        ShopScopeService shopScopeService = mock(ShopScopeService.class);
        ReflectionTestUtils.setField(service, "packageMapper", packageMapper);
        ReflectionTestUtils.setField(service, "shopScopeService", shopScopeService);

        OaSignPackage existing = new OaSignPackage();
        existing.setPackageId(100L);
        existing.setStatus(OaSignPackageStatus.PENDING_SIGN);
        existing.setShopDeptId(1171L);
        when(packageMapper.selectOaSignPackageById(100L)).thenReturn(existing);
        when(shopScopeService.resolveRequiredShopDept(1171L)).thenReturn(1171L);

        assertThatThrownBy(() -> service.updateDraftPackage(100L, new OaSignPackage(), 1171L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("仅草稿签约包可以编辑");
    }

    @Test
    @DisplayName("草稿签约包编辑请求不能为空")
    void shouldRejectNullDraftUpdateRequest() throws Exception
    {
        OaSignPackageServiceImpl service = newPackageService();

        assertThatThrownBy(() -> service.updateDraftPackage(100L, null, 1171L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("签约包信息不能为空");
    }

    @Test
    @DisplayName("草稿签约包必填快照字段不能为空")
    void shouldRejectBlankRequiredDraftSnapshotFields() throws Exception
    {
        OaSignPackageServiceImpl service = newPackageService();
        OaSignPackageMapper packageMapper = mock(OaSignPackageMapper.class);
        ShopScopeService shopScopeService = mock(ShopScopeService.class);
        ReflectionTestUtils.setField(service, "packageMapper", packageMapper);
        ReflectionTestUtils.setField(service, "shopScopeService", shopScopeService);

        OaSignPackage existing = new OaSignPackage();
        existing.setPackageId(100L);
        existing.setStatus(OaSignPackageStatus.DRAFT);
        existing.setShopDeptId(1171L);
        when(packageMapper.selectOaSignPackageById(100L)).thenReturn(existing);
        when(shopScopeService.resolveRequiredShopDept(1171L)).thenReturn(1171L);

        OaSignPackage request = validDraftRequest();
        request.setEmployeeNameSnapshot(" ");

        assertThatThrownBy(() -> service.updateDraftPackage(100L, request, 1171L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("员工姓名不能为空");
        org.mockito.Mockito.verify(packageMapper, org.mockito.Mockito.never()).updateDraftOaSignPackage(any());
    }

    @Test
    @DisplayName("草稿签约包更新失败时提示刷新重试")
    void shouldRejectWhenDraftPackageUpdateAffectsNoRows() throws Exception
    {
        OaSignPackageServiceImpl service = newPackageService();
        OaSignPackageMapper packageMapper = mock(OaSignPackageMapper.class);
        ShopScopeService shopScopeService = mock(ShopScopeService.class);
        ReflectionTestUtils.setField(service, "packageMapper", packageMapper);
        ReflectionTestUtils.setField(service, "shopScopeService", shopScopeService);
        SecurityContextHolder.setUserName("admin");

        OaSignPackage existing = new OaSignPackage();
        existing.setPackageId(100L);
        existing.setStatus(OaSignPackageStatus.DRAFT);
        existing.setEmployeeId(960L);
        existing.setShopDeptId(1171L);
        existing.setSourcePlanId(20L);
        existing.setSourcePlanName("项目总监方案");
        when(packageMapper.selectOaSignPackageById(100L)).thenReturn(existing);
        when(shopScopeService.resolveRequiredShopDept(1171L)).thenReturn(1171L);
        when(packageMapper.updateDraftOaSignPackage(any())).thenReturn(0);

        assertThatThrownBy(() -> service.updateDraftPackage(100L, validDraftRequest(), 1171L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("签约包保存失败");
    }

    @Test
    @DisplayName("签署按文档ID匹配无序hash集合并生成独立已签PDF证据")
    void shouldBindSignatureToFrozenReviewPdfSetRegardlessOfRequestOrder() throws Exception
    {
        SigningFixture fixture = signingFixture();

        OaSignPackage result = fixture.service.signPackage(500L,
                signRequest("SP-500-V1", "request-1", fixture.second, fixture.first));

        assertThat(result).isNotSameAs(fixture.signPackage);
        assertThat(result.getPackageId()).isEqualTo(fixture.signPackage.getPackageId());
        assertThat(result.getEmployeeId()).isNull();
        verify(fixture.signedPdfService, times(2)).generateSignedPdf(any(), eq(fixture.signPackage),
                any(), any(), any(), eq(null), any(), eq("本人确认签署本签约包"), eq(null), eq(null));
        verify(fixture.packageMapper).updateStatusWithVersion(eq(500L),
                eq(OaSignPackageStatus.PART_VIEWED), eq(OaSignPackageStatus.PENDING_COMPANY),
                eq(7L), any(Date.class), eq("employee"));

        ArgumentCaptor<OaSignPackageDocument> documentCaptor =
                ArgumentCaptor.forClass(OaSignPackageDocument.class);
        verify(fixture.documentMapper, times(2)).updateOaSignPackageDocument(documentCaptor.capture());
        assertThat(documentCaptor.getAllValues())
                .allSatisfy(update -> {
                    assertThat(update.getSigned()).isEqualTo("Y");
                    assertThat(update.getSignedPdfUrl()).contains("signed-");
                    assertThat(update.getSignedPdfHash()).startsWith("signed-hash-");
                    assertThat(update.getSignedPdfUrl()).isNotEqualTo(update.getReviewPdfUrl());
                    assertThat(update.getFileHashAfterSign()).isEqualTo(update.getSignedPdfHash());
                });

        ArgumentCaptor<com.erp.oa.domain.OaSignFileEvidence> evidenceCaptor =
                ArgumentCaptor.forClass(com.erp.oa.domain.OaSignFileEvidence.class);
        verify(fixture.evidenceMapper, org.mockito.Mockito.atLeast(4))
                .insertOaSignFileEvidence(evidenceCaptor.capture());
        assertThat(evidenceCaptor.getAllValues()).extracting(com.erp.oa.domain.OaSignFileEvidence::getEvidenceType)
                .contains(OaSignFileEvidenceType.SIGNATURE_IMAGE, OaSignFileEvidenceType.SIGNED_PDF);

        ArgumentCaptor<OaSignEvent> eventCaptor = ArgumentCaptor.forClass(OaSignEvent.class);
        verify(fixture.eventMapper, org.mockito.Mockito.atLeast(2)).insertOaSignEvent(eventCaptor.capture());
        assertThat(eventCaptor.getAllValues()).anySatisfy(event -> {
            assertThat(event.getEventType()).isEqualTo("PACKAGE_SIGN_REQUESTED");
            assertThat(event.getRequestId()).isEqualTo("request-1");
            assertThat(event.getOperatorRole()).isEqualTo("EMPLOYEE");
        });
    }

    @Test
    @DisplayName("Excel分阶段先签名包禁止绕过事实确认入口调用通用签名")
    void shouldRejectGenericSigningForStagedExcelSignatureFirstPackage() throws Exception
    {
        SigningFixture fixture = signingFixture();
        fixture.signPackage.setTaskId(9L);
        fixture.signPackage.setSigningSequence(OaSignSigningSequence.SIGNATURE_FIRST);
        OaSignTaskMapper taskMapper = (OaSignTaskMapper) ReflectionTestUtils.getField(
                fixture.service, "taskMapper");
        OaSignTask task = new OaSignTask();
        task.setTaskId(9L);
        task.setPackageId(500L);
        task.setEmployeeId(960L);
        task.setSourceType(OaOnboardSignEventFactory.SOURCE_TYPE);
        task.setStatus(OaSignTaskStatus.VIEWED.name());
        when(taskMapper.selectOaSignTaskById(9L)).thenReturn(task);

        assertThatThrownBy(() -> fixture.service.signPackage(500L,
                signRequest("SP-500-V1", "excel-generic-sign", fixture.first,
                        fixture.second)))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("合同资料补全入口")
                .hasMessageContaining("唯一一次签名");

        verify(fixture.signedPdfService, never()).generateSignedPdf(any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any());
        verify(fixture.packageMapper, never()).updateStatusWithVersion(any(), any(), any(),
                any(), any(), any());
        verify(fixture.eventMapper, never()).insertOaSignEvent(any());
    }

    @Test
    @DisplayName("既有exact-v7 APPENDED公司先行包无需改快照即可继续签署确认归档")
    void shouldCompleteCompanyFirstSigningWithoutASecondEmployeeConfirmation() throws Exception
    {
        SigningFixture fixture = signingFixture();
        fixture.signPackage.setTaskId(9L);
        fixture.signPackage.setSigningSequence(OaSignSigningSequence.COMPANY_FIRST);
        fixture.signPackage.setScenario("onboard");
        fixture.signPackage.setLegalEntityIdSnapshot(31L);
        fixture.signPackage.setLegalEntityCodeSnapshot("LEGAL-31");
        fixture.signPackage.setLegalEntityNameSnapshot("示例文化有限公司");
        fixture.signPackage.setLegalEntityCreditCodeSnapshot("91330000TEST31");
        fixture.signPackage.setLegalEntityAddressSnapshot("杭州市测试路31号");
        fixture.signPackage.setLegalRepresentativeSnapshot("测试法人");
        byte[] sealBytes = jpegSealBytes();
        String sealHash = sha256(sealBytes);
        fixture.signPackage.setSealIdSnapshot(41L);
        fixture.signPackage.setSealNameSnapshot("测试合同章");
        fixture.signPackage.setSealImageUrlSnapshot("/seal/41.jpeg");
        fixture.signPackage.setSealImageHashSnapshot(sealHash);
        fixture.signPackage.setCompanyFrozenTime(new Date());
        fixture.signPackage.setFinalDocumentVersion("SP-500-V2");
        fixture.signPackage.setFinalGeneratedTime(new Date());
        fixture.signPackage.setFinalConfirmationStatus("PENDING");

        OaSignCompanyService companyService = mock(OaSignCompanyService.class);
        OaSignFileStorageService fileStorageService = mock(OaSignFileStorageService.class);
        OaSignFinalConfirmationMapper confirmationMapper =
                mock(OaSignFinalConfirmationMapper.class);
        ReflectionTestUtils.setField(fixture.service, "signCompanyService", companyService);
        ReflectionTestUtils.setField(fixture.service, "fileStorageService", fileStorageService);
        ReflectionTestUtils.setField(fixture.service, "finalConfirmationMapper",
                confirmationMapper);
        SysLegalEntity entity = new SysLegalEntity();
        entity.setLegalEntityId(31L);
        entity.setLegalEntityCode("LEGAL-31");
        entity.setLegalEntityName("示例文化有限公司");
        entity.setUnifiedSocialCreditCode("91330000TEST31");
        entity.setRegisteredAddress("杭州市测试路31号");
        entity.setLegalRepresentative("测试法人");
        when(companyService.requireContractReadyEntity(31L)).thenReturn(entity);
        OaCompanySealConfig seal = new OaCompanySealConfig();
        seal.setSealId(41L);
        seal.setLegalEntityId(31L);
        seal.setSealName("测试合同章");
        seal.setSealImageUrl("/seal/41.jpeg");
        seal.setSealImageHash(sealHash);
        when(companyService.requireContractReadySeal(41L, 31L)).thenReturn(seal);
        when(fixture.documentService.readConfiguredFileBytes("/seal/41.jpeg"))
                .thenReturn(sealBytes);

        Path signaturePath = tempDir.resolve("company-first-signature.png");
        byte[] signatureBytes = "signature".getBytes(StandardCharsets.UTF_8);
        Files.write(signaturePath, signatureBytes);
        String signatureHash = sha256(signatureBytes);
        for (OaSignPackageDocument document : fixture.documents)
        {
            document.setTemplateId(document.getDocumentId());
            document.setTemplateVersionSnapshot("v1");
            document.setSourceFileUrlSnapshot("/immutable/" + document.getDocumentId()
                    + ".docx");
        }
        OaSignPackageDocument legacyLabor = fixture.documents.get(0);
        legacyLabor.setTemplateType(OaSignTemplateType.ONBOARD_LABOR_CONTRACT);
        legacyLabor.setTemplateVersionSnapshot("20260721-v7");
        legacyLabor.setSourceFileUrlSnapshot(
                "/oa/sign/templates/onboard-20260721-v7/09_ONBOARD_LABOR_CONTRACT.docx");
        legacyLabor.setSignaturePositionJson(
                OaSignPlacementPolicyService.APPENDED_CONFIRMATION_PAGE);
        legacyLabor.setCompanySealRequired("Y");
        legacyLabor.setCompanySealPositionJson(
                OaSignPlacementPolicyService.APPENDED_CONFIRMATION_PAGE);

        Path pending51 = tempDir.resolve("pending-final-51.pdf");
        Path pending52 = tempDir.resolve("pending-final-52.pdf");
        Files.writeString(pending51, "%PDF-1.4 pending 51",
                StandardCharsets.ISO_8859_1);
        Files.writeString(pending52, "%PDF-1.4 pending 52",
                StandardCharsets.ISO_8859_1);
        when(fixture.documentService.resolveGeneratedSignPackageFile(
                "/generated/signature.png")).thenReturn(signaturePath);
        when(fixture.documentService.resolveGeneratedSignPackageFile(
                "/generated/pending-final-51.pdf")).thenReturn(pending51);
        when(fixture.documentService.resolveGeneratedSignPackageFile(
                "/generated/pending-final-52.pdf")).thenReturn(pending52);

        String contentHash51 = sha256("stable-body-51".getBytes(StandardCharsets.UTF_8));
        String contentHash52 = sha256("stable-body-52".getBytes(StandardCharsets.UTF_8));
        fixture.documents.get(0).setFinalPdfUrl("/generated/pending-final-51.pdf");
        fixture.documents.get(0).setFinalPdfHash(sha256(Files.readAllBytes(pending51)));
        fixture.documents.get(0).setFinalContentHash(contentHash51);
        fixture.documents.get(0).setFinalDocumentVersion("SP-500-V2");
        fixture.documents.get(0).setFinalReadConfirmed("Y");
        fixture.documents.get(0).setFinalReadConfirmedTime(new Date());
        fixture.documents.get(1).setFinalPdfUrl("/generated/pending-final-52.pdf");
        fixture.documents.get(1).setFinalPdfHash(sha256(Files.readAllBytes(pending52)));
        fixture.documents.get(1).setFinalContentHash(contentHash52);
        fixture.documents.get(1).setFinalDocumentVersion("SP-500-V2");
        fixture.documents.get(1).setFinalReadConfirmed("Y");
        fixture.documents.get(1).setFinalReadConfirmedTime(new Date());
        String rootHash = sha256(("51:" + contentHash51 + "\n52:" + contentHash52 + "\n")
                .getBytes(StandardCharsets.UTF_8));
        fixture.signPackage.setFinalDocumentRootHash(rootHash);
        when(fixture.eventMapper.selectEventsByPackageId(500L)).thenReturn(List.of(
                finalReadEvent(fixture.signPackage, fixture.documents.get(0)),
                finalReadEvent(fixture.signPackage, fixture.documents.get(1))));

        when(fixture.signedPdfService.generateSignedPdf(any(), eq(fixture.signPackage),
                any(), any(), any(), eq(null), any(), any(), eq(null), eq(null)))
                .thenAnswer(invocation -> {
                    OaSignPackageDocument document = invocation.getArgument(2);
                    return new SignedPdfResult("signed-" + document.getDocumentId() + ".pdf",
                            "/generated/signed-" + document.getDocumentId() + ".pdf",
                            "signed-hash-" + document.getDocumentId(), 100L,
                            "signature.png", "/generated/signature.png", signatureHash,
                            null, 2);
                });
        when(fixture.signedPdfService.archiveConfirmedFinalPdf(any(),
                eq(fixture.signPackage), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any())).thenAnswer(invocation -> {
                    OaSignPackageDocument document = invocation.getArgument(2);
                    return new SignedPdfResult(
                            "archive-" + document.getDocumentId() + ".pdf",
                            "/generated/archive-" + document.getDocumentId() + ".pdf",
                            "archive-hash-" + document.getDocumentId(), 140L,
                            null, null, null, null, 2);
                });

        when(fixture.packageMapper.updateFinalizedPackage(any(), any(), any()))
                .thenReturn(1);
        when(fixture.packageMapper.updateFinalConfirmed(any(), any(), any(), any(),
                any(), any(), any())).thenReturn(1);
        when(confirmationMapper.insertConfirmation(any())).thenAnswer(invocation -> {
            invocation.getArgument(0, OaSignFinalConfirmation.class)
                    .setConfirmationId(701L);
            return 1;
        });
        when(confirmationMapper.insertConfirmationDocument(any())).thenReturn(1);
        org.mockito.Mockito.doAnswer(invocation -> {
            OaSignPackageDocument update = invocation.getArgument(0);
            OaSignPackageDocument target = fixture.documents.stream()
                    .filter(item -> Objects.equals(item.getDocumentId(), update.getDocumentId()))
                    .findFirst().orElseThrow();
            if (update.getSignatureFileUrl() != null)
            {
                target.setSignatureFileUrl(update.getSignatureFileUrl());
                target.setSignatureHash(update.getSignatureHash());
            }
            if (update.getFinalPdfUrl() != null)
            {
                target.setFinalPdfUrl(update.getFinalPdfUrl());
                target.setFinalPdfHash(update.getFinalPdfHash());
                target.setFinalContentHash(update.getFinalContentHash());
                target.setFinalDocumentVersion(update.getFinalDocumentVersion());
            }
            if (update.getFinalArchivePdfUrl() != null)
            {
                target.setFinalArchivePdfUrl(update.getFinalArchivePdfUrl());
                target.setFinalArchivePdfHash(update.getFinalArchivePdfHash());
            }
            return 1;
        }).when(fixture.documentMapper).updateOaSignPackageDocument(any());

        OaSignTaskMapper taskMapper = (OaSignTaskMapper) ReflectionTestUtils.getField(
                fixture.service, "taskMapper");
        OaSignTask task = new OaSignTask();
        task.setTaskId(9L);
        task.setPackageId(500L);
        task.setEmployeeId(960L);
        task.setStatus(OaSignTaskStatus.VIEWED.name());
        task.setVersion(3L);
        when(taskMapper.selectOaSignTaskById(9L)).thenReturn(task);
        OaSignNotificationOutboxService outbox =
                (OaSignNotificationOutboxService) ReflectionTestUtils.getField(
                        fixture.service, "notificationOutboxService");

        OaSignPackage result = fixture.service.signPackage(500L,
                companyFirstSignRequest(fixture.signPackage, "company-first-1",
                        fixture.documents));

        assertThat(result.getStatus()).isEqualTo(OaSignPackageStatus.SIGNED);
        assertThat(task.getStatus()).isEqualTo(OaSignTaskStatus.SIGNED.name());
        verify(fixture.packageMapper).updateFinalizedPackage(any(),
                eq(OaSignPackageStatus.PART_VIEWED), eq(7L));
        verify(fixture.packageMapper).updateFinalConfirmed(eq(500L),
                eq(OaSignPackageStatus.PENDING_FINAL_CONFIRM), eq(8L), any(), any(),
                any(), eq("employee"));
        verify(confirmationMapper).insertConfirmation(any());
        verify(confirmationMapper, times(2)).insertConfirmationDocument(any());
        verify(outbox, never()).enqueueFinalReady(any(), any());
        verify(outbox).enqueueCompleted(eq(task), eq(fixture.signPackage));
        OaSignTaskEventService taskEventService = (OaSignTaskEventService)
                ReflectionTestUtils.getField(fixture.service, "taskEventService");
        ArgumentCaptor<String> taskRequestIdCaptor = ArgumentCaptor.forClass(String.class);
        verify(taskEventService, times(2)).transition(any(), any(), any(), any(), any(),
                any(), taskRequestIdCaptor.capture(), any(), any());
        assertThat(taskRequestIdCaptor.getAllValues()).hasSize(2)
                .allSatisfy(value -> assertThat(value).hasSize(64));
        assertThat(taskRequestIdCaptor.getAllValues().stream().distinct().count())
                .isEqualTo(2L);
        ArgumentCaptor<OaSignEvent> eventCaptor = ArgumentCaptor.forClass(OaSignEvent.class);
        verify(fixture.eventMapper, org.mockito.Mockito.atLeast(3))
                .insertOaSignEvent(eventCaptor.capture());
        assertThat(eventCaptor.getAllValues()).extracting(OaSignEvent::getEventType)
                .contains("FINAL_CONTRACT_CONFIRMED")
                .doesNotContain("FINAL_CONTRACT_GENERATED");
        assertThat(legacyLabor.getSignaturePositionJson())
                .isEqualTo(OaSignPlacementPolicyService.APPENDED_CONFIRMATION_PAGE);
        assertThat(legacyLabor.getCompanySealPositionJson())
                .isEqualTo(OaSignPlacementPolicyService.APPENDED_CONFIRMATION_PAGE);
        verify(fixture.documentMapper, never()).insertOaSignPackageDocument(any());
    }

    @Test
    @DisplayName("公司先行在发送前生成带冻结印章且不含员工签名的稳定候选")
    void shouldPrepareCompanyFirstCandidateBeforeSending() throws Exception
    {
        OaSignPackageServiceImpl service = newPackageService();
        OaSignPackageDocumentMapper documentMapper = mock(OaSignPackageDocumentMapper.class);
        OaSignEventMapper eventMapper = mock(OaSignEventMapper.class);
        OaSignFileEvidenceMapper evidenceMapper = mock(OaSignFileEvidenceMapper.class);
        OaSignDocumentService documentService = mock(OaSignDocumentService.class);
        OaSignedPdfService pdfService = mock(OaSignedPdfService.class);
        OaSignCompanyService companyService = mock(OaSignCompanyService.class);
        OaSignFileStorageService storageService = mock(OaSignFileStorageService.class);
        ReflectionTestUtils.setField(service, "documentMapper", documentMapper);
        ReflectionTestUtils.setField(service, "eventMapper", eventMapper);
        ReflectionTestUtils.setField(service, "evidenceMapper", evidenceMapper);
        ReflectionTestUtils.setField(service, "documentService", documentService);
        ReflectionTestUtils.setField(service, "signedPdfService", pdfService);
        ReflectionTestUtils.setField(service, "signCompanyService", companyService);
        ReflectionTestUtils.setField(service, "fileStorageService", storageService);

        byte[] sealBytes = jpegSealBytes();
        String sealHash = sha256(sealBytes);
        OaSignPackage signPackage = new OaSignPackage();
        signPackage.setPackageId(500L);
        signPackage.setTaskId(9L);
        signPackage.setStatus(OaSignPackageStatus.DRAFT);
        signPackage.setSigningSequence(OaSignSigningSequence.COMPANY_FIRST);
        signPackage.setDocumentVersion("SP-500-V1");
        signPackage.setLegalEntityIdSnapshot(31L);
        signPackage.setLegalEntityCodeSnapshot("LEGAL-31");
        signPackage.setLegalEntityNameSnapshot("示例文化有限公司");
        signPackage.setLegalEntityCreditCodeSnapshot("91330000TEST31");
        signPackage.setLegalEntityAddressSnapshot("杭州市测试路31号");
        signPackage.setLegalRepresentativeSnapshot("测试法人");
        signPackage.setSealIdSnapshot(41L);
        signPackage.setSealNameSnapshot("合同专用章");
        signPackage.setSealImageUrlSnapshot("/seal/41.jpeg");
        signPackage.setSealImageHashSnapshot(sealHash);
        signPackage.setCompanyFrozenTime(new Date());

        Path reviewPath = tempDir.resolve("company-first-review.pdf");
        Files.writeString(reviewPath, "%PDF-1.4 company-first",
                StandardCharsets.ISO_8859_1);
        OaSignPackageDocument document = signingDocument(51L, reviewPath);
        document.setCompanySealRequired("Y");
        document.setCompanySealPositionJson(
                "{\"mode\":\"PLACED\",\"pageNumber\":1,\"x\":400,\"y\":40,"
                        + "\"width\":120,\"height\":120}");
        when(documentMapper.selectDocumentsByPackageId(500L)).thenReturn(List.of(document));
        when(documentService.resolveGeneratedSignPackageFile(document.getReviewPdfUrl()))
                .thenReturn(reviewPath);

        SysLegalEntity entity = new SysLegalEntity();
        entity.setLegalEntityId(31L);
        entity.setLegalEntityCode("LEGAL-31");
        entity.setLegalEntityName("示例文化有限公司");
        entity.setUnifiedSocialCreditCode("91330000TEST31");
        entity.setRegisteredAddress("杭州市测试路31号");
        entity.setLegalRepresentative("测试法人");
        OaCompanySealConfig seal = new OaCompanySealConfig();
        seal.setSealId(41L);
        seal.setLegalEntityId(31L);
        seal.setSealName("合同专用章");
        seal.setSealImageUrl("/seal/41.jpeg");
        seal.setSealImageHash(sealHash);
        when(companyService.requireContractReadyEntity(31L)).thenReturn(entity);
        when(companyService.requireContractReadySeal(41L, 31L)).thenReturn(seal);
        when(documentService.readConfiguredFileBytes("/seal/41.jpeg")).thenReturn(sealBytes);

        StagedSignFile stagedSeal = new StagedSignFile(tempDir, tempDir.resolve("seal.tmp"),
                "archive/seal.jpg", "seal.jpg", sealHash, sealBytes.length);
        stagedSeal.markPromoted(tempDir.resolve("seal.jpg"), "/profile/seal.jpg");
        when(storageService.stage(any(), eq(500L), eq("SP-500-V2"),
                eq("company-seal-41.jpg"), eq(sealBytes))).thenReturn(stagedSeal);
        when(storageService.promote(stagedSeal)).thenReturn(stagedSeal);

        OaSignFileEvidence rendered = sourceEvidence(document,
                OaSignFileEvidenceType.RENDERED_SOURCE, "source.docx", "a".repeat(64));
        OaSignFileEvidence review = sourceEvidence(document,
                OaSignFileEvidenceType.REVIEW_PDF, "review.pdf", document.getReviewPdfHash());
        when(evidenceMapper.selectEvidenceByType(51L, "SP-500-V1",
                OaSignFileEvidenceType.RENDERED_SOURCE)).thenReturn(rendered);
        when(evidenceMapper.selectEvidenceByType(51L, "SP-500-V1",
                OaSignFileEvidenceType.REVIEW_PDF)).thenReturn(review);
        String pendingHash = "b".repeat(64);
        String contentHash = "c".repeat(64);
        when(pdfService.generatePendingFinalPdf(any(), eq(signPackage), any(), eq(reviewPath),
                eq(null), eq(sealBytes), eq(null), any(Date.class), any(), eq(null), any()))
                .thenReturn(new SignedPdfResult("pending.pdf", "/pending.pdf", pendingHash,
                        200L, null, null, null, 0L, sealHash, 2, contentHash));

        ReflectionTestUtils.invokeMethod(service, "prepareCompanyFirstFinalCandidate",
                signPackage);

        assertThat(signPackage.getFinalDocumentVersion()).isEqualTo("SP-500-V2");
        assertThat(signPackage.getFinalDocumentRootHash()).isEqualTo(
                sha256(("51:" + contentHash + "\n").getBytes(StandardCharsets.UTF_8)));
        assertThat(signPackage.getFinalConfirmationStatus()).isEqualTo("PREPARED_NOT_SENT");
        verify(pdfService).generatePendingFinalPdf(any(), eq(signPackage), any(), eq(reviewPath),
                eq(null), eq(sealBytes), eq(null), any(Date.class), any(), eq(null), any());
        verify(pdfService, never()).generateSignedPdf(any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any());
        ArgumentCaptor<OaSignFileEvidence> evidenceCaptor =
                ArgumentCaptor.forClass(OaSignFileEvidence.class);
        verify(evidenceMapper, org.mockito.Mockito.atLeast(4))
                .insertOaSignFileEvidence(evidenceCaptor.capture());
        assertThat(evidenceCaptor.getAllValues())
                .extracting(OaSignFileEvidence::getEvidenceType)
                .contains(OaSignFileEvidenceType.FINAL_PENDING_PDF,
                        OaSignFileEvidenceType.COMPANY_SEAL)
                .doesNotContain(OaSignFileEvidenceType.SIGNATURE_IMAGE);
    }

    @Test
    @DisplayName("公司先行拒绝会在员工确认后改变正文的签名定位")
    void shouldRejectCompanyFirstSignaturePlacementThatChangesStableBody()
    {
        OaSignPackageServiceImpl service = newPackageService();
        OaSignPackageDocument document = policyDocument(51L, "Y", "N");
        document.setSignaturePositionJson(
                "{\"mode\":\"PLACED\",\"pageNumber\":1,\"x\":20,\"y\":20,"
                        + "\"width\":120,\"height\":50}");

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(service,
                "assertCompanyFirstSignaturePreservesStableBody", List.of(document)))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("不得在员工确认后改变合同正文");
    }

    @Test
    @DisplayName("最终化按文件快照分别绑定员工签名时间与公司盖章时间")
    void shouldApplyOnlyFrozenMarksToEachFinalDocument()
    {
        OaSignPackageServiceImpl service = newPackageService();
        OaSignedPdfService pdfService = mock(OaSignedPdfService.class);
        ReflectionTestUtils.setField(service, "signedPdfService", pdfService);
        OaSignPackage signPackage = new OaSignPackage();
        signPackage.setPackageId(500L);
        signPackage.setDocumentVersion("SP-500-V2");
        Date employeeSignedTime = Date.from(Instant.parse("2026-07-17T02:03:04Z"));
        signPackage.setInitialSignedTime(employeeSignedTime);
        OaSignPackageDocument finalDocument = new OaSignPackageDocument();
        finalDocument.setDocumentId(51L);
        finalDocument.setPackageId(500L);
        finalDocument.setDocumentVersion("SP-500-V2");
        Path finalReview = Path.of("/tmp/final-review.pdf");
        byte[] signature = { 1, 2, 3 };
        byte[] seal = { 4, 5, 6 };
        Date finalGeneratedTime = Date.from(Instant.parse("2026-07-17T06:07:08Z"));
        SignedPdfResult sealed = new SignedPdfResult("sealed.pdf", "/sealed.pdf",
                "sealed-hash", 10L, null, null, null, "seal-hash", 1);
        when(pdfService.generatePendingFinalPdf(any(), eq(signPackage), eq(finalDocument),
                eq(finalReview), org.mockito.ArgumentMatchers.same(signature),
                org.mockito.ArgumentMatchers.same(seal), eq(employeeSignedTime),
                eq(finalGeneratedTime), any(),
                org.mockito.ArgumentMatchers.isNull(), any())).thenReturn(sealed);

        OaSignPackageDocument signedAndSealed = policyDocument(51L, "Y", "Y");
        SignedPdfResult actual = ReflectionTestUtils.invokeMethod(service,
                "generateFinalDocumentPdf", signPackage, signedAndSealed, finalDocument,
                finalReview, signature, seal, finalGeneratedTime);

        assertThat(actual).isSameAs(sealed);
        verify(pdfService).generatePendingFinalPdf(any(), eq(signPackage), eq(finalDocument),
                eq(finalReview), org.mockito.ArgumentMatchers.same(signature),
                org.mockito.ArgumentMatchers.same(seal), eq(employeeSignedTime),
                eq(finalGeneratedTime), any(),
                org.mockito.ArgumentMatchers.isNull(), any());

        OaSignPackageDocument readOnly = policyDocument(52L, "N", "N");
        OaSignPackageDocument readOnlyFinal = new OaSignPackageDocument();
        readOnlyFinal.setDocumentId(52L);
        readOnlyFinal.setPackageId(500L);
        readOnlyFinal.setDocumentVersion("SP-500-V2");
        SignedPdfResult unmarked = new SignedPdfResult("plain.pdf", "/plain.pdf",
                "plain-hash", 10L, null, null, null, null, 1);
        when(pdfService.generatePendingFinalPdfWithoutMarks(
                null, signPackage, readOnlyFinal, finalReview))
                .thenReturn(unmarked);

        assertThat((SignedPdfResult) ReflectionTestUtils.invokeMethod(service,
                "generateFinalDocumentPdf", signPackage, readOnly, readOnlyFinal,
                finalReview, signature, seal, finalGeneratedTime)).isSameAs(unmarked);
        verify(pdfService).generatePendingFinalPdfWithoutMarks(
                null, signPackage, readOnlyFinal, finalReview);
    }

    @Test
    @DisplayName("公司处理期间暂停员工签署时钟")
    void shouldResumeEmployeeDeadlineAfterCompanyStage()
    {
        OaSignPackageServiceImpl service = newPackageService();
        OaSignPackage signPackage = new OaSignPackage();
        signPackage.setInitialSignedTime(Date.from(Instant.parse("2026-07-17T02:00:00Z")));
        signPackage.setSignDeadline(Date.from(Instant.parse("2026-07-24T15:59:59Z")));
        signPackage.setDeadlinePolicySource("PLAN_VERSION");
        signPackage.setDeadlineDaysSnapshot(7);

        Date resumed = ReflectionTestUtils.invokeMethod(service,
                "resumeEmployeeDeadlineAfterCompanyStage", signPackage,
                Date.from(Instant.parse("2026-07-17T05:00:00Z")));

        assertThat(resumed).isEqualTo(Date.from(Instant.parse("2026-07-24T18:59:59Z")));
    }

    @Test
    @DisplayName("HR延后审核按员工已冻结签名时间校验截止时间")
    void shouldValidateStagedSignatureDeadlineByCapturedTimeInsteadOfReviewTime()
    {
        OaSignPackageServiceImpl service = newPackageService();
        OaSignPackage signPackage = new OaSignPackage();
        Date deadline = Date.from(Instant.parse("2026-07-17T02:03:04Z"));
        signPackage.setSignDeadline(deadline);
        signPackage.setDeadlinePolicySource("PLAN_VERSION");
        signPackage.setDeadlineDaysSnapshot(7);

        // The deadline is historical relative to the test run, but an employee capture exactly
        // at the frozen deadline remains valid when HR reviews it later.
        ReflectionTestUtils.invokeMethod(service,
                "assertStagedSignatureCapturedByDeadline", signPackage, deadline);

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(service,
                "assertStagedSignatureCapturedByDeadline", signPackage,
                Date.from(deadline.toInstant().plusSeconds(1))))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("截止时间前提交");
    }

    @Test
    @DisplayName("恢复员工签署时钟对缺失期限策略失败关闭")
    void shouldRejectDeadlineResumeWithoutCompletePolicy()
    {
        OaSignPackageServiceImpl service = newPackageService();
        OaSignPackage signPackage = new OaSignPackage();
        signPackage.setInitialSignedTime(Date.from(Instant.parse("2026-07-17T02:00:00Z")));
        signPackage.setSignDeadline(Date.from(Instant.parse("2026-07-24T15:59:59Z")));

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(service,
                "resumeEmployeeDeadlineAfterCompanyStage", signPackage,
                Date.from(Instant.parse("2026-07-17T05:00:00Z"))))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("签署期限策略缺失");
    }

    @Test
    @DisplayName("签署事务正常提交后保留签署证明和已签文件")
    void shouldKeepCertificateAndSignedFilesAfterCommit() throws Exception
    {
        SigningFixture fixture = signingFixture();
        TransactionSynchronizationManager.initSynchronization();
        try
        {
            fixture.service.signPackage(500L,
                    signRequest("SP-500-V1", "request-commit", fixture.first, fixture.second));

            assertThat(TransactionSynchronizationManager.getSynchronizations()).isNotEmpty();
            for (TransactionSynchronization synchronization
                    : TransactionSynchronizationManager.getSynchronizations())
            {
                synchronization.afterCompletion(TransactionSynchronization.STATUS_COMMITTED);
            }
            verify(fixture.documentService, never()).discardUncommitted(any());
            verify(fixture.signedPdfService, never()).discardUncommitted(any());
        }
        finally
        {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("确认短语必须完全一致")
    void shouldRequireExactConfirmationPhrase() throws Exception
    {
        SigningFixture fixture = signingFixture();
        OaSignPackageSignRequest request = signRequest("SP-500-V1", "request-exact",
                fixture.first, fixture.second);
        request.setSignConfirmText("我已阅读，本人确认签署本签约包");

        assertThatThrownBy(() -> fixture.service.signPackage(500L, request))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("请确认输入");
        verify(fixture.signedPdfService, never()).generateSignedPdf(any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("旧文档版本、漏文件和请求hash不一致均拒绝签署")
    void shouldRejectStaleVersionMissingDocumentAndHashMismatch() throws Exception
    {
        SigningFixture fixture = signingFixture();

        assertThatThrownBy(() -> fixture.service.signPackage(500L,
                signRequest("SP-500-V0", "request-old", fixture.first, fixture.second)))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("文档版本已更新");
        assertThatThrownBy(() -> fixture.service.signPackage(500L,
                signRequest("SP-500-V1", "request-missing", fixture.first)))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("必签文件集合不完整");

        OaSignDocumentHashRequest mismatch = hashRequest(fixture.second.getDocumentId(), "wrong-hash");
        assertThatThrownBy(() -> fixture.service.signPackage(500L,
                signRequest("SP-500-V1", "request-mismatch", fixture.first, mismatch)))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("文件校验不一致");
    }

    @Test
    @DisplayName("未阅读或缺少对应版本阅读事件时拒绝签署")
    void shouldRequireReadFlagAndMatchingReadEvent() throws Exception
    {
        SigningFixture fixture = signingFixture();
        fixture.documents.get(0).setReadConfirmed("N");
        assertThatThrownBy(() -> fixture.service.signPackage(500L,
                signRequest("SP-500-V1", "request-unread", fixture.first, fixture.second)))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("阅读确认");

        fixture.documents.get(0).setReadConfirmed("Y");
        when(fixture.eventMapper.selectEventsByPackageId(500L)).thenReturn(List.of(
                readEvent(fixture.documents.get(1))));
        assertThatThrownBy(() -> fixture.service.signPackage(500L,
                signRequest("SP-500-V1", "request-event", fixture.first, fixture.second)))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("阅读证据不完整");
    }

    @Test
    @DisplayName("相同requestId返回首次结果且已签包不重复生成")
    void shouldReturnExistingResultForIdempotentOrCompletedSigning() throws Exception
    {
        SigningFixture fixture = signingFixture();
        OaSignPackageSignRequest repeatedRequest = signRequest(
                "SP-500-V1", "request-repeat", fixture.first, fixture.second);
        OaSignEvent existing = new OaSignEvent();
        existing.setPackageId(500L);
        existing.setEventType("PACKAGE_SIGN_REQUESTED");
        existing.setRequestId("request-repeat");
        existing.setDocumentHash(signRequestPayloadHash(repeatedRequest));
        when(fixture.eventMapper.selectEventByTypeAndRequestId("PACKAGE_SIGN_REQUESTED", "request-repeat"))
                .thenReturn(existing);

        fixture.service.signPackage(500L, repeatedRequest);
        verify(fixture.signedPdfService, never()).generateSignedPdf(any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any());

        fixture.signPackage.setStatus(OaSignPackageStatus.SIGNED);
        fixture.service.signPackage(500L,
                signRequest("SP-500-V1", "request-new", fixture.first, fixture.second));
        verify(fixture.signedPdfService, never()).generateSignedPdf(any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("并发插入相同requestId时返回已经完成的首次结果")
    void shouldRecoverFromConcurrentDuplicateRequestId() throws Exception
    {
        SigningFixture fixture = signingFixture();
        OaSignPackageSignRequest racingRequest = signRequest(
                "SP-500-V1", "request-race", fixture.first, fixture.second);
        OaSignEvent existing = new OaSignEvent();
        existing.setPackageId(500L);
        existing.setEventType("PACKAGE_SIGN_REQUESTED");
        existing.setRequestId("request-race");
        existing.setDocumentHash(signRequestPayloadHash(racingRequest));
        when(fixture.eventMapper.selectEventByTypeAndRequestId("PACKAGE_SIGN_REQUESTED", "request-race"))
                .thenReturn(null, existing);
        org.mockito.Mockito.doAnswer(invocation -> {
            OaSignEvent event = invocation.getArgument(0);
            if ("PACKAGE_SIGN_REQUESTED".equals(event.getEventType()))
            {
                throw new org.springframework.dao.DuplicateKeyException("duplicate request");
            }
            return 1;
        }).when(fixture.eventMapper).insertOaSignEvent(any());

        fixture.service.signPackage(500L, racingRequest);

        verify(fixture.signedPdfService, never()).generateSignedPdf(any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("相同requestId携带不同签名载荷时拒绝回放")
    void shouldRejectIdempotencyKeyReuseWithDifferentSigningPayload() throws Exception
    {
        SigningFixture fixture = signingFixture();
        OaSignPackageSignRequest original = signRequest(
                "SP-500-V1", "request-conflict", fixture.first, fixture.second);
        OaSignEvent existing = new OaSignEvent();
        existing.setPackageId(500L);
        existing.setEventType("PACKAGE_SIGN_REQUESTED");
        existing.setRequestId("request-conflict");
        existing.setDocumentHash(signRequestPayloadHash(original));
        when(fixture.eventMapper.selectEventByTypeAndRequestId(
                "PACKAGE_SIGN_REQUESTED", "request-conflict")).thenReturn(existing);
        OaSignPackageSignRequest changed = signRequest(
                "SP-500-V1", "request-conflict", fixture.first, fixture.second);
        changed.setSignatureDataUrl("data:image/png;base64,"
                + Base64.getEncoder().encodeToString(
                        SignatureImageTestFixtures.signature(2)));

        assertThatThrownBy(() -> fixture.service.signPackage(500L, changed))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("同一签署请求编号不能提交不同载荷");
        verify(fixture.signedPdfService, never()).generateSignedPdf(any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("并发状态更新失败时不把文档和签约包标记为已签")
    void shouldRejectConcurrentSigningBeforePersistingSignedDocuments() throws Exception
    {
        SigningFixture fixture = signingFixture();
        when(fixture.packageMapper.updateStatusWithVersion(any(), any(), any(), any(), any(), any()))
                .thenReturn(0);

        assertThatThrownBy(() -> fixture.service.signPackage(500L,
                signRequest("SP-500-V1", "request-concurrent", fixture.first, fixture.second)))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("签署状态已变化");
        verify(fixture.documentMapper, never()).updateOaSignPackageDocument(any());
        verify(fixture.evidenceMapper, never()).insertOaSignFileEvidence(any());
        verify(fixture.signedPdfService, times(2)).discardUncommitted(any());
    }

    @Test
    @DisplayName("非本人或错误状态的签约包不能签署")
    void shouldRejectOtherEmployeeAndWrongPackageStatus() throws Exception
    {
        SigningFixture fixture = signingFixture();
        fixture.signPackage.setEmployeeId(961L);
        assertThatThrownBy(() -> fixture.service.signPackage(500L,
                signRequest("SP-500-V1", "request-other", fixture.first, fixture.second)))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("本人的签约包");

        fixture.signPackage.setEmployeeId(960L);
        fixture.signPackage.setStatus(OaSignPackageStatus.DRAFT);
        assertThatThrownBy(() -> fixture.service.signPackage(500L,
                signRequest("SP-500-V1", "request-state", fixture.first, fixture.second)))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("当前签约包状态不能签署");
    }

    @Test
    @DisplayName("阅读确认事件保存当前文档版本、阅读PDF hash和员工角色")
    void shouldRecordFrozenReviewEvidenceWhenConfirmingRead() throws Exception
    {
        SigningFixture fixture = signingFixture();
        fixture.documents.get(0).setReadConfirmed("N");
        OaSignDocumentReadRequest request = readRequest(fixture.documents.get(0));

        fixture.service.confirmDocumentRead(500L, 51L, request);

        ArgumentCaptor<OaSignEvent> eventCaptor = ArgumentCaptor.forClass(OaSignEvent.class);
        verify(fixture.eventMapper).insertOaSignEvent(eventCaptor.capture());
        OaSignEvent event = eventCaptor.getValue();
        assertThat(event.getEventType()).isEqualTo("DOCUMENT_READ_CONFIRMED");
        assertThat(event.getDocumentHash()).isEqualTo(fixture.documents.get(0).getReviewPdfHash());
        assertThat(event.getEventPayload()).contains("SP-500-V1", fixture.documents.get(0).getReviewPdfHash());
        assertThat(event.getOperatorRole()).isEqualTo("EMPLOYEE");
    }

    @Test
    @DisplayName("员工阅读和签署同步任务状态事实源")
    void shouldSynchronizeTaskStatusWhenEmployeeReadsAndSigns() throws Exception
    {
        SigningFixture fixture = signingFixture();
        OaSignTaskMapper taskMapper = mock(OaSignTaskMapper.class);
        OaSignTaskEventService taskEventService = mock(OaSignTaskEventService.class);
        OaSignNotificationOutboxService notificationOutboxService = mock(OaSignNotificationOutboxService.class);
        ReflectionTestUtils.setField(fixture.service, "taskMapper", taskMapper);
        ReflectionTestUtils.setField(fixture.service, "taskEventService", taskEventService);
        ReflectionTestUtils.setField(fixture.service, "notificationOutboxService", notificationOutboxService);
        fixture.signPackage.setTaskId(9L);
        fixture.signPackage.setSigningSequence(OaSignSigningSequence.SIGNATURE_FIRST);
        OaSignTask task = new OaSignTask();
        task.setTaskId(9L);
        task.setSourceType("HR_LIFECYCLE_ACTION");
        task.setAssignedHrUserId(101L);
        task.setShopDeptId(1171L);
        task.setStatus("PENDING_SIGN");
        task.setVersion(5L);
        when(taskMapper.selectOaSignTaskById(9L)).thenReturn(task);
        when(taskEventService.transition(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    OaSignTaskStatus target = invocation.getArgument(1);
                    task.setStatus(target.name());
                    task.setVersion(task.getVersion() + 1);
                    return task;
                });

        fixture.documents.get(0).setReadConfirmed("N");
        fixture.service.confirmDocumentRead(500L, 51L, readRequest(fixture.documents.get(0)));
        fixture.documents.get(0).setReadConfirmed("Y");
        fixture.service.signPackage(500L,
                signRequest("SP-500-V1", "task-sign-9", fixture.first, fixture.second));

        verify(taskEventService).transition(eq(task), eq(OaSignTaskStatus.VIEWED), any(), any(),
                any(), any(), any(), any(), any());
        verify(taskEventService).transition(eq(task), eq(OaSignTaskStatus.PENDING_COMPANY), any(), any(),
                any(), any(), any(), any(), any());
        verify(notificationOutboxService).enqueueInitialSigned(task, fixture.signPackage);
        verify(notificationOutboxService, never()).enqueueCompleted(task, fixture.signPackage);
    }

    @Test
    @DisplayName("最终合同生成和最终完成在各自状态事务写入通知Outbox")
    void shouldEnqueueFinalReadyAndCompletionFromTaskTransitions()
    {
        OaSignPackageServiceImpl service = new OaSignPackageServiceImpl();
        ReflectionTestUtils.setField(service, "salarySources", org.mockito.Mockito.mock(OaSignSalarySourceService.class));
        OaSignTaskMapper taskMapper = mock(OaSignTaskMapper.class);
        OaSignTaskEventService taskEventService = mock(OaSignTaskEventService.class);
        OaSignNotificationOutboxService outboxService = mock(OaSignNotificationOutboxService.class);
        ReflectionTestUtils.setField(service, "taskMapper", taskMapper);
        ReflectionTestUtils.setField(service, "taskEventService", taskEventService);
        ReflectionTestUtils.setField(service, "notificationOutboxService", outboxService);
        OaSignPackage signPackage = new OaSignPackage();
        signPackage.setPackageId(90L);
        signPackage.setTaskId(9L);
        signPackage.setEmployeeId(201L);
        signPackage.setDocumentVersion("SP-90-V1");
        signPackage.setFinalDocumentVersion("SP-90-F1");
        OaSignTask task = new OaSignTask();
        task.setTaskId(9L);
        task.setEmployeeId(201L);
        task.setAssignedHrUserId(101L);
        task.setStatus(OaSignTaskStatus.PENDING_COMPANY.name());
        task.setVersion(3L);
        when(taskMapper.selectOaSignTaskById(9L)).thenReturn(task);
        when(taskEventService.transition(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    OaSignTaskStatus target = invocation.getArgument(1);
                    task.setStatus(target.name());
                    task.setVersion(task.getVersion() + 1);
                    return task;
                });

        ReflectionTestUtils.invokeMethod(service, "syncTaskStatus", signPackage,
                OaSignTaskStatus.PENDING_FINAL_CONFIRM, "FINAL_CONTRACT_GENERATED",
                null, null, null);
        ReflectionTestUtils.invokeMethod(service, "syncTaskStatus", signPackage,
                OaSignTaskStatus.SIGNED, "FINAL_CONTRACT_CONFIRMED",
                "final-request-9", null, null);

        verify(outboxService).enqueueFinalReady(task, signPackage);
        verify(outboxService).enqueueCompleted(task, signPackage);
        verify(taskMapper).updateCompletedTime(eq(9L), any(Date.class));
    }

    @Test
    @DisplayName("阅读确认拒绝客户端旧版本、错误hash或磁盘已篡改文件")
    void shouldRejectStaleOrTamperedReadEvidence() throws Exception
    {
        SigningFixture fixture = signingFixture();
        OaSignPackageDocument document = fixture.documents.get(0);
        OaSignDocumentReadRequest stale = readRequest(document);
        stale.setDocumentVersion("SP-500-V0");
        assertThatThrownBy(() -> fixture.service.confirmDocumentRead(500L, 51L, stale))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("文档版本已更新");

        OaSignDocumentReadRequest wrongHash = readRequest(document);
        wrongHash.setReviewPdfHash("0".repeat(64));
        assertThatThrownBy(() -> fixture.service.confirmDocumentRead(500L, 51L, wrongHash))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("文件校验不一致");

        Files.writeString(fixture.firstPath, "tampered", StandardCharsets.UTF_8);
        OaSignDocumentReadRequest tampered = readRequest(document);
        assertThatThrownBy(() -> fixture.service.confirmDocumentRead(500L, 51L, tampered))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("文件校验不一致");
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = { false, true })
    void secondDocumentFailureTracksAndCleansFirstResult(boolean transactionCallbacks) throws Exception
    {
        SigningFixture fixture = signingFixture();
        SignedPdfResult firstResult = new SignedPdfResult("owned/first.pdf", "/private/first.pdf",
                "first-hash", 100L, null, null, null, null, 2);
        if (transactionCallbacks)
            org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization();
        try
        {
            when(fixture.signedPdfService.generateSignedPdf(any(), eq(fixture.signPackage), any(), any(),
                    any(), eq(null), any(), any(), eq(null), eq(null))).thenAnswer(call -> {
                OaSignPackageDocument document = call.getArgument(2);
                if (document.getDocumentId().equals(52L))
                {
                    if (transactionCallbacks)
                        assertThat(org.springframework.transaction.support.TransactionSynchronizationManager.getSynchronizations()).hasSize(1);
                    throw new ServiceException("second render failed");
                }
                return firstResult;
            });
            assertThatThrownBy(() -> fixture.service.signPackage(500L,
                    signRequest("SP-500-V1", "second-render-failure", fixture.first, fixture.second)))
                    .hasMessageContaining("second render failed");
            verify(fixture.signedPdfService).discardUncommitted(firstResult);
            verify(fixture.documentMapper, never()).updateOaSignPackageDocument(any());
            verify(fixture.evidenceMapper, never()).insertOaSignFileEvidence(any());
            if (transactionCallbacks)
            {
                for (var callback : org.springframework.transaction.support.TransactionSynchronizationManager.getSynchronizations())
                    callback.afterCompletion(org.springframework.transaction.support.TransactionSynchronization.STATUS_ROLLED_BACK);
                verify(fixture.signedPdfService, times(2)).discardUncommitted(firstResult);
            }
        }
        finally
        {
            if (transactionCallbacks)
                org.springframework.transaction.support.TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private SigningFixture signingFixture() throws Exception
    {
        OaSignPackageServiceImpl service = newPackageService();
        SigningFixture fixture = new SigningFixture();
        fixture.service = service;
        fixture.packageMapper = mock(OaSignPackageMapper.class);
        fixture.documentMapper = mock(OaSignPackageDocumentMapper.class);
        fixture.eventMapper = mock(OaSignEventMapper.class);
        fixture.evidenceMapper = mock(OaSignFileEvidenceMapper.class);
        fixture.documentService = mock(OaSignDocumentService.class);
        fixture.signedPdfService = mock(OaSignedPdfService.class);
        ReflectionTestUtils.setField(service, "packageMapper", fixture.packageMapper);
        ReflectionTestUtils.setField(service, "documentMapper", fixture.documentMapper);
        ReflectionTestUtils.setField(service, "eventMapper", fixture.eventMapper);
        ReflectionTestUtils.setField(service, "evidenceMapper", fixture.evidenceMapper);
        ReflectionTestUtils.setField(service, "documentService", fixture.documentService);
        ReflectionTestUtils.setField(service, "signedPdfService", fixture.signedPdfService);
        SecurityContextHolder.setUserId("960");
        SecurityContextHolder.setUserName("employee");

        fixture.signPackage = new OaSignPackage();
        fixture.signPackage.setPackageId(500L);
        fixture.signPackage.setPackageNo("SP202607110500");
        fixture.signPackage.setEmployeeId(960L);
        fixture.signPackage.setEmployeeNameSnapshot("张三");
        fixture.signPackage.setEmployeeIdCardSnapshot("TEST-ID-****-1234");
        fixture.signPackage.setDeptNameSnapshot("杭州示例公司");
        fixture.signPackage.setStatus(OaSignPackageStatus.PART_VIEWED);
        fixture.signPackage.setDocumentVersion("SP-500-V1");
        fixture.signPackage.setVersion(7L);
        fixture.signPackage.setSignDeadline(Date.from(Instant.parse("2099-12-31T15:59:59Z")));
        fixture.signPackage.setDeadlinePolicySource("PLAN_VERSION");
        fixture.signPackage.setDeadlineDaysSnapshot(7);

        Path firstPath = tempDir.resolve("review-first.pdf");
        Path secondPath = tempDir.resolve("review-second.pdf");
        fixture.firstPath = firstPath;
        Files.writeString(firstPath, "%PDF-1.4 first", StandardCharsets.ISO_8859_1);
        Files.writeString(secondPath, "%PDF-1.4 second", StandardCharsets.ISO_8859_1);
        OaSignPackageDocument first = signingDocument(51L, firstPath);
        OaSignPackageDocument second = signingDocument(52L, secondPath);
        fixture.documents = Arrays.asList(first, second);
        fixture.first = hashRequest(51L, first.getReviewPdfHash());
        fixture.second = hashRequest(52L, second.getReviewPdfHash());

        when(fixture.packageMapper.selectOaSignPackageById(500L)).thenReturn(fixture.signPackage);
        when(fixture.documentMapper.selectDocumentsByPackageId(500L)).thenReturn(fixture.documents);
        when(fixture.documentMapper.selectOaSignPackageDocumentById(51L)).thenReturn(first);
        when(fixture.documentMapper.selectOaSignPackageDocumentById(52L)).thenReturn(second);
        when(fixture.documentService.resolveGeneratedSignPackageFile(first.getReviewPdfUrl())).thenReturn(firstPath);
        when(fixture.documentService.resolveGeneratedSignPackageFile(second.getReviewPdfUrl())).thenReturn(secondPath);
        when(fixture.eventMapper.selectEventsByPackageId(500L))
                .thenReturn(Arrays.asList(readEvent(first), readEvent(second)));
        when(fixture.packageMapper.updateStatusWithVersion(any(), any(), any(), any(), any(), any()))
                .thenReturn(1);
        when(fixture.packageMapper.markViewedWithVersion(any(), any(), any(), any(), any())).thenReturn(1);
        when(fixture.documentService.generateSignCertificate(eq(null), eq(fixture.signPackage), any(),
                eq(fixture.documents), any(), any(), any(), any()))
                .thenReturn(new GeneratedSignDocument("/profile/sign-package/500/certificate.pdf", null,
                        "certificate-hash"));
        when(fixture.signedPdfService.generateSignedPdf(any(), eq(fixture.signPackage), any(), any(),
                any(), eq(null), any(), any(), eq(null), eq(null)))
                .thenAnswer(invocation -> {
                    OaSignPackageDocument document = invocation.getArgument(2);
                    String suffix = String.valueOf(document.getDocumentId());
                    return new SignedPdfResult("task-none/package-500/SP-500-V1/signed-" + suffix + ".pdf",
                            "/profile/private/sign-package/task-none/package-500/SP-500-V1/signed-" + suffix + ".pdf",
                            "signed-hash-" + suffix, 100L,
                            "task-none/package-500/SP-500-V1/signature-" + suffix + ".png",
                            "/profile/private/sign-package/task-none/package-500/SP-500-V1/signature-" + suffix + ".png",
                            "signature-hash-" + suffix, null, 2);
                });
        return fixture;
    }

    @Test
    @DisplayName("任务确认后的发送只使用冻结文件不重新生成")
    void shouldSendPreparedTaskPackageWithoutRegeneratingDocuments()
    {
        OaSignPackageServiceImpl service = newPackageService();
        OaSignPackageMapper packageMapper = mock(OaSignPackageMapper.class);
        OaSignPackageDocumentMapper documentMapper = mock(OaSignPackageDocumentMapper.class);
        OaSignEventMapper eventMapper = mock(OaSignEventMapper.class);
        ShopScopeService shopScopeService = mock(ShopScopeService.class);
        OaSignDocumentService documentService = mock(OaSignDocumentService.class);
        ReflectionTestUtils.setField(service, "packageMapper", packageMapper);
        ReflectionTestUtils.setField(service, "documentMapper", documentMapper);
        ReflectionTestUtils.setField(service, "eventMapper", eventMapper);
        ReflectionTestUtils.setField(service, "shopScopeService", shopScopeService);
        ReflectionTestUtils.setField(service, "documentService", documentService);
        SecurityContextHolder.setUserId("101");
        SecurityContextHolder.setUserName("唯一HR");

        OaSignPackage signPackage = new OaSignPackage();
        signPackage.setPackageId(90L);
        signPackage.setTaskId(9L);
        signPackage.setShopDeptId(1171L);
        signPackage.setStatus(OaSignPackageStatus.DRAFT);
        signPackage.setVersion(0L);
        signPackage.setDocumentVersion("SP-90-V1");
        OaSignPackageDocument document = new OaSignPackageDocument();
        document.setDocumentId(1L);
        document.setPackageId(90L);
        document.setDocumentVersion("SP-90-V1");
        document.setReviewPdfUrl("/profile/private/sign-package/review.pdf");
        document.setReviewPdfHash("hash-a");
        when(packageMapper.selectOaSignPackageById(90L)).thenReturn(signPackage);
        when(documentMapper.selectDocumentsByPackageId(90L)).thenReturn(List.of(document));
        when(shopScopeService.resolveRequiredShopDept(1171L)).thenReturn(1171L);
        when(eventMapper.selectLatestEventHashByPackageId(90L)).thenReturn(null);
        when(packageMapper.markSentWithVersion(any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any())).thenReturn(1);
        Date sentTime = Date.from(Instant.parse("2026-07-17T02:03:04Z"));
        Date signDeadline = Date.from(Instant.parse("2026-07-24T15:59:59Z"));

        service.sendPreparedPackage(90L, 9L, "SP-90-V1", 1171L,
                sentTime, signDeadline, "PLAN_VERSION", 7);

        verify(documentService, never()).renderPackageDocument(any(), any(), any());
        verify(packageMapper).markSentWithVersion(90L, 9L, OaSignPackageStatus.DRAFT, 0L,
                "SP-90-V1", sentTime, signDeadline, "PLAN_VERSION", 7,
                "NOT_REQUIRED", "唯一HR");
    }

    private OaSignPackageDocument signingDocument(Long documentId, Path reviewPdf) throws Exception
    {
        OaSignPackageDocument document = new OaSignPackageDocument();
        document.setDocumentId(documentId);
        document.setPackageId(500L);
        document.setDocumentName("文档" + documentId);
        document.setTemplateType(OaSignTemplateType.ONBOARD_COMMITMENT);
        document.setEmployeeVisible("Y");
        document.setReadConfirmationRequired("Y");
        document.setEmployeeSignRequired("Y");
        document.setSignaturePositionJson(
                OaSignPlacementPolicyService.APPENDED_CONFIRMATION_PAGE);
        document.setCompanySealRequired("N");
        document.setDocumentPolicyMode(OaSignPlacementPolicyService.SNAPSHOT_V1);
        document.setReadConfirmed("Y");
        document.setReviewPdfUrl("/profile/private/sign-package/review-" + documentId + ".pdf");
        document.setReviewPdfHash(sha256(Files.readAllBytes(reviewPdf)));
        document.setFileHashBeforeSign(document.getReviewPdfHash());
        document.setDocumentVersion("SP-500-V1");
        return document;
    }

    private OaSignPackageDocument policyDocument(Long documentId,
            String employeeSignRequired, String companySealRequired)
    {
        OaSignPackageDocument document = new OaSignPackageDocument();
        document.setDocumentId(documentId);
        document.setPackageId(500L);
        document.setEmployeeVisible("Y");
        document.setEmployeeSignRequired(employeeSignRequired);
        document.setSignaturePositionJson("Y".equals(employeeSignRequired)
                ? OaSignPlacementPolicyService.APPENDED_CONFIRMATION_PAGE : null);
        document.setCompanySealRequired(companySealRequired);
        document.setCompanySealPositionJson("Y".equals(companySealRequired)
                ? "{\"mode\":\"PLACED\",\"pageNumber\":1,\"x\":400,\"y\":40,"
                        + "\"width\":120,\"height\":120}"
                : null);
        document.setDocumentPolicyMode(OaSignPlacementPolicyService.SNAPSHOT_V1);
        return document;
    }

    private static OaSignEvent readEvent(OaSignPackageDocument document)
    {
        OaSignEvent event = new OaSignEvent();
        event.setPackageId(document.getPackageId());
        event.setDocumentId(document.getDocumentId());
        event.setEventType("DOCUMENT_READ_CONFIRMED");
        event.setDocumentHash(document.getReviewPdfHash());
        event.setEventPayload("documentVersion=" + document.getDocumentVersion()
                + ";reviewPdfHash=" + document.getReviewPdfHash());
        return event;
    }

    private static OaSignEvent finalReadEvent(OaSignPackage signPackage,
            OaSignPackageDocument document)
    {
        OaSignEvent event = new OaSignEvent();
        event.setPackageId(document.getPackageId());
        event.setDocumentId(document.getDocumentId());
        event.setEventType("FINAL_DOCUMENT_OPENED");
        event.setDocumentHash(document.getFinalPdfHash());
        event.setEventPayload("version=" + signPackage.getFinalDocumentVersion()
                + ";hash=" + document.getFinalPdfHash());
        return event;
    }

    private OaSignPackageSignRequest signRequest(String version, String requestId,
            OaSignDocumentHashRequest... hashes)
    {
        OaSignPackageSignRequest request = new OaSignPackageSignRequest();
        request.setDocumentVersion(version);
        request.setRequestId(requestId);
        request.setDocumentHashes(Arrays.asList(hashes));
        request.setSignConfirmText("本人确认签署本签约包");
        request.setSignatureDataUrl("data:image/png;base64,"
                + Base64.getEncoder().encodeToString(SignatureImageTestFixtures.signature(1)));
        return request;
    }

    private OaSignPackageSignRequest companyFirstSignRequest(OaSignPackage signPackage,
            String requestId, List<OaSignPackageDocument> documents)
    {
        OaSignPackageSignRequest request = signRequest(signPackage.getDocumentVersion(),
                requestId);
        request.setDocumentHashes(null);
        request.setFinalDocumentVersion(signPackage.getFinalDocumentVersion());
        request.setFinalDocumentRootHash(signPackage.getFinalDocumentRootHash());
        request.setFinalDocumentHashes(documents.stream().map(document -> {
            OaSignFinalDocumentHashRequest hash = new OaSignFinalDocumentHashRequest();
            hash.setDocumentId(document.getDocumentId());
            hash.setFinalPdfHash(document.getFinalPdfHash());
            return hash;
        }).toList());
        return request;
    }

    private String signRequestPayloadHash(OaSignPackageSignRequest request)
    {
        OaSignPackageSigningRequestPolicy policy =
                new OaSignPackageSigningRequestPolicy();
        byte[] signatureBytes = policy.decodeSignaturePng(
                request.getSignatureDataUrl());
        return policy.payloadHash(request, signatureBytes);
    }

    private static OaSignDocumentHashRequest hashRequest(Long documentId, String hash)
    {
        OaSignDocumentHashRequest request = new OaSignDocumentHashRequest();
        request.setDocumentId(documentId);
        request.setReviewPdfHash(hash);
        return request;
    }

    private static OaSignDocumentReadRequest readRequest(OaSignPackageDocument document)
    {
        OaSignDocumentReadRequest request = new OaSignDocumentReadRequest();
        request.setRequestId("read-" + document.getDocumentId() + "-"
                + document.getDocumentVersion());
        request.setExpectedVersion(7L);
        request.setDocumentVersion(document.getDocumentVersion());
        request.setReviewPdfHash(document.getReviewPdfHash());
        return request;
    }

    private static String sha256(byte[] bytes) throws Exception
    {
        return java.util.HexFormat.of().formatHex(
                java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static byte[] jpegSealBytes() throws Exception
    {
        BufferedImage image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < image.getWidth(); x++)
        {
            for (int y = 0; y < image.getHeight(); y++)
            {
                image.setRGB(x, y, 0x00FF0000);
            }
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        assertThat(ImageIO.write(image, "jpeg", output)).isTrue();
        return output.toByteArray();
    }

    private static final class SigningFixture
    {
        private OaSignPackageServiceImpl service;
        private OaSignPackageMapper packageMapper;
        private OaSignPackageDocumentMapper documentMapper;
        private OaSignEventMapper eventMapper;
        private OaSignFileEvidenceMapper evidenceMapper;
        private OaSignDocumentService documentService;
        private OaSignedPdfService signedPdfService;
        private OaSignPackage signPackage;
        private List<OaSignPackageDocument> documents;
        private Path firstPath;
        private OaSignDocumentHashRequest first;
        private OaSignDocumentHashRequest second;
    }

    private OaSignPackage validDraftRequest()
    {
        OaSignPackage request = new OaSignPackage();
        request.setEmployeeNameSnapshot("张三");
        request.setEmployeePhoneSnapshot("13800000000");
        request.setEmployeeIdCardSnapshot("TEST-ID-****-1234");
        request.setScenario("onboarding");
        return request;
    }

    private OaSignTemplate template(Long templateId, String templateName, String templateType, String postLevelScope)
    {
        OaSignTemplate template = new OaSignTemplate();
        OaSignTemplateType.Option option = OaSignTemplateType.require(templateType);
        template.setTemplateId(templateId);
        template.setTemplateName(templateName);
        template.setTemplateType(templateType);
        template.setTemplateVersion("test");
        template.setFileUrl("/immutable/test-template-source.docx");
        template.setFileHash(TEST_TEMPLATE_SOURCE_HASH);
        template.setPostLevelScope(postLevelScope);
        template.setScenario(option.getScenario());
        template.setEmployeeVisible(option.isEmployeeVisible() ? "Y" : "N");
        template.setReadConfirmationRequired(option.isReadConfirmationRequired() ? "Y" : "N");
        template.setEmployeeSignRequired(option.isEmployeeSignRequired() ? "Y" : "N");
        template.setCompanySealRequired("N");
        template.setSortOrder(templateId.intValue());
        return template;
    }

    private OaSignPlanVersionTemplate versionSnapshot(OaSignTemplate source)
    {
        OaSignPlanVersionTemplate snapshot = new OaSignPlanVersionTemplate();
        snapshot.setPlanVersionId(55L);
        snapshot.setTemplateId(source.getTemplateId());
        snapshot.setTemplateName(source.getTemplateName());
        snapshot.setTemplateType(source.getTemplateType());
        snapshot.setTemplateVersion(source.getTemplateVersion());
        snapshot.setSourceFileUrl(source.getFileUrl());
        snapshot.setSourceFileHash(source.getFileHash());
        snapshot.setRequiredPlaceholders(source.getRequiredPlaceholders());
        snapshot.setEmployeeSignRequired(source.getEmployeeSignRequired());
        snapshot.setCompanySealRequired(source.getCompanySealRequired());
        snapshot.setSignaturePositionJson(source.getSignaturePositionJson());
        snapshot.setCompanySealPositionJson(source.getCompanySealPositionJson());
        snapshot.setSortOrder(source.getSortOrder());
        snapshot.setMatchConditionJson("{}");
        return snapshot;
    }

    private static String laborMultiPolicy(String reviewPdfHash, boolean signature,
            String signingSequencePolicy)
    {
        String placements = signature
                ? "[{\"pageNumber\":14,\"x\":318,\"y\":698,\"width\":90,\"height\":42},"
                        + "{\"pageNumber\":14,\"x\":320,\"y\":480,\"width\":90,\"height\":42},"
                        + "{\"pageNumber\":16,\"x\":320,\"y\":520,\"width\":90,\"height\":42},"
                        + "{\"pageNumber\":18,\"x\":315,\"y\":420,\"width\":90,\"height\":42}]"
                : "[{\"pageNumber\":14,\"x\":180,\"y\":688,\"width\":62,\"height\":61}]";
        return "{\"mode\":\"PLACED_MULTI\","
                + "\"templateType\":\"ONBOARD_LABOR_CONTRACT\","
                + "\"templateVersion\":\"20260721-v7\","
                + "\"reviewPdfHash\":\"" + reviewPdfHash + "\","
                + "\"placementConfigVersion\":\"20260809-rev06-v1\","
                + "\"profileId\":\"exact-v7-dynamic\","
                + "\"expectedBodyPageCount\":18,"
                + "\"signingSequencePolicy\":\"" + signingSequencePolicy + "\","
                + "\"placements\":" + placements + "}";
    }

    private OaSignFileEvidence evidence(String version, String fileUrl, String fileHash)
    {
        OaSignFileEvidence evidence = new OaSignFileEvidence();
        evidence.setPackageId(90L);
        evidence.setDocumentVersion(version);
        evidence.setFileUrl(fileUrl);
        evidence.setFileHash(fileHash);
        return evidence;
    }

    private OaSignFileEvidence sourceEvidence(OaSignPackageDocument document,
            OaSignFileEvidenceType type, String fileUrl, String fileHash)
    {
        OaSignFileEvidence evidence = new OaSignFileEvidence();
        evidence.setPackageId(document.getPackageId());
        evidence.setDocumentId(document.getDocumentId());
        evidence.setDocumentVersion(document.getDocumentVersion());
        evidence.setEvidenceType(type);
        evidence.setFileUrl(fileUrl);
        evidence.setFileHash(fileHash);
        evidence.setFileSize(100L);
        evidence.setGeneratedTime(new Date());
        return evidence;
    }
}
