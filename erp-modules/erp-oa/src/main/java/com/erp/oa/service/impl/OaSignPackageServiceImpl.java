package com.erp.oa.service.impl;

import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.core.utils.StringUtils;
import com.erp.common.security.shop.ShopScopeService;
import com.erp.common.security.utils.SecurityUtils;
import com.erp.oa.constant.OaSignPackageStatus;
import com.erp.oa.constant.OaSignScenarioCodes;
import com.erp.oa.constant.OaSignTemplateType;
import com.erp.oa.constant.OaSignOperatorType;
import com.erp.oa.constant.OaSignTaskStatus;
import com.erp.oa.constant.OaSignSigningSequence;
import com.erp.oa.domain.OaSignEvent;
import com.erp.oa.domain.OaSignFileEvidence;
import com.erp.oa.domain.OaSignPackage;
import com.erp.oa.domain.OaSignPackageDocument;
import com.erp.oa.domain.OaSignPlan;
import com.erp.oa.domain.OaSignPlanVersion;
import com.erp.oa.domain.OaSignPlanVersionTemplate;
import com.erp.oa.domain.OaSignTemplate;
import com.erp.oa.domain.OaSignTask;
import com.erp.oa.domain.OaCompanySealConfig;
import com.erp.oa.domain.OaSignFinalConfirmation;
import com.erp.oa.domain.OaSignFinalConfirmationDocument;
import com.erp.oa.domain.dto.OaSignDocumentReadRequest;
import com.erp.oa.domain.dto.OaSignPackageSignRequest;
import com.erp.oa.domain.dto.OaSignPackageFinalizeRequest;
import com.erp.oa.domain.dto.OaSignFinalConfirmRequest;
import com.erp.oa.domain.dto.OaSignFinalDocumentReadRequest;
import com.erp.oa.domain.vo.OaSignPackageFile;
import com.erp.oa.domain.vo.OaSignCompanyOptions;
import com.erp.oa.domain.vo.OaLegalEntityCandidate;
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
import com.erp.oa.service.IOaSignPackageService;
import com.erp.system.api.domain.SysLegalEntity;

@Service
public class OaSignPackageServiceImpl implements IOaSignPackageService
{
    private static final String YES = "Y";
    private static final String NO = "N";
    private static final String FINAL_GENERATION_TEXT = "沿用员工首次签名并补充公司及印章";
    private static final String FINAL_EXPORT_EVIDENCE_FAILURE =
            "该历史合同缺少签章位置或证据，暂不能恢复";
    private static final String FINALIZE_REQUEST_EVENT = "FINAL_CONTRACT_FINALIZE_REQUESTED";
    private static final EnumSet<OaSignTaskStatus> TASK_DRAFT_STATUSES = EnumSet.of(
            OaSignTaskStatus.NEW, OaSignTaskStatus.NEEDS_DATA, OaSignTaskStatus.FAILED);
    private static final OaSignPackageDraftPolicy DRAFT_POLICY =
            new OaSignPackageDraftPolicy();
    private static final OaSignPackageSigningRequestPolicy SIGNING_REQUEST_POLICY =
            new OaSignPackageSigningRequestPolicy();
    private static final OaSignPackageReadPolicy READ_POLICY =
            new OaSignPackageReadPolicy();
    private static final OaSignPackageFinalizationPolicy FINALIZATION_POLICY =
            new OaSignPackageFinalizationPolicy();
    private static final OaSignPackageStagedSnapshotPolicy STAGED_SNAPSHOT_POLICY =
            new OaSignPackageStagedSnapshotPolicy();
    private static final OaSignPackageStagedEvidencePolicy STAGED_EVIDENCE_POLICY =
            new OaSignPackageStagedEvidencePolicy();
    private static final OaSignPackageDeadlinePolicy DEADLINE_POLICY =
            new OaSignPackageDeadlinePolicy();
    private static final OaSignPackageDocumentDeliveryPolicy DOCUMENT_DELIVERY_POLICY =
            new OaSignPackageDocumentDeliveryPolicy();

    @Autowired
    private OaSignPackageMapper packageMapper;

    @Autowired
    private OaSignTemplateMapper templateMapper;

    @Autowired
    private OaSignPlanMapper planMapper;

    @Autowired
    private OaSignPlanVersionMapper planVersionMapper;

    @Autowired
    private OaSignPackageDocumentMapper documentMapper;

    @Autowired
    private OaSignEventMapper eventMapper;

    @Autowired
    private OaSignFileEvidenceMapper evidenceMapper;

    @Autowired
    private OaSignTaskMapper taskMapper;

    @Autowired
    private OaSignTaskEventService taskEventService;

    @Autowired
    @Qualifier("oaSignScopeService")
    private ShopScopeService shopScopeService;

    @Autowired
    private OaSignDocumentService documentService;

    @Autowired
    private OaSignedPdfService signedPdfService;

    @Autowired
    private OaSignPlacementPolicyService placementPolicyService;

    @Autowired
    private OaSignNotificationOutboxService notificationOutboxService;

    @Autowired
    private OaSignHrAccessService signHrAccessService;

    @Autowired
    private OaSignFileStorageService fileStorageService;

    @Autowired
    private OaSignCompanyService signCompanyService;

    @Autowired
    private OaSignFinalConfirmationMapper finalConfirmationMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OaSignPackage createPackage(OaSignPackage signPackage, Long selectedShopDeptId)
    {
        signHrAccessService.requireCurrentHr();
        if (signPackage == null)
        {
            throw new ServiceException("签约包信息不能为空");
        }
        OaSignTask task = signPackage.getTaskId() == null ? null
                : prepareTaskDraft(signPackage.getTaskId(), selectedShopDeptId);
        if (task != null && task.getPackageId() != null)
        {
            return getPackageDetail(task.getPackageId(), selectedShopDeptId);
        }
        Long shopDeptId = resolveDraftShopDept(task, signPackage, selectedShopDeptId);
        if (task != null)
        {
            bindDraftToTask(signPackage, task, shopDeptId);
        }
        else
        {
            // Immutable plan-version references are server-owned. Manual packages may keep the
            // legacy sourcePlanId selection, but cannot manufacture a Phase 3 version binding.
            signPackage.setPlanVersionId(null);
            if (StringUtils.isNotBlank(signPackage.getScenario()))
            {
                signPackage.setScenario(OaSignScenarioCodes.normalizePackageScenario(signPackage.getScenario()));
            }
        }
        boolean preserveServerCompany = task != null
                && OaOnboardSignEventFactory.SOURCE_TYPE.equals(task.getSourceType())
                && Objects.equals(task.getLegalEntityId(), signPackage.getLegalEntityIdSnapshot());
        clearServerOwnedLifecycle(signPackage, preserveServerCompany);
        signPackage.setShopDeptId(shopDeptId);
        signPackage.setShopDeptName(shopScopeService.resolveShopDeptName(shopDeptId));
        if (StringUtils.isBlank(signPackage.getPackageNo()))
        {
            signPackage.setPackageNo(generatePackageNo());
        }
        if (StringUtils.isBlank(signPackage.getStatus()))
        {
            signPackage.setStatus(OaSignPackageStatus.DRAFT);
        }
        if (!OaSignPackageStatus.DRAFT.equals(signPackage.getStatus()))
        {
            throw new ServiceException("新建签约包只能保存为草稿");
        }
        signPackage.setCreateBy(SecurityUtils.getUsername());
        if (packageMapper.insertOaSignPackage(signPackage) != 1)
        {
            throw new ServiceException("签约包草稿创建失败");
        }
        recordEvent(signPackage.getPackageId(), null, "PACKAGE_CREATED", "创建员工签约包草稿",
                null, null, null, "HR", null);
        if (task != null && taskMapper.updatePackageLink(task.getTaskId(), signPackage.getPackageId(),
                task.getPlanVersionId(), task.getAssignedHrUserId()) != 1)
        {
            throw new ServiceException("签约任务草稿关联失败，请刷新后重试");
        }
        return getPackageDetail(signPackage.getPackageId(), selectedShopDeptId);
    }

    private OaSignTask prepareTaskDraft(Long taskId, Long selectedShopDeptId)
    {
        OaSignTask task = taskMapper.selectOaSignTaskById(taskId);
        if (task == null)
        {
            throw new ServiceException("签约任务不存在");
        }
        signHrAccessService.requireTaskOwner(task);
        if (task.getShopDeptId() != null)
        {
            List<Long> scopeDeptIds = shopScopeService.resolveScopeDeptIds(selectedShopDeptId);
            if (scopeDeptIds == null || !scopeDeptIds.contains(task.getShopDeptId()))
            {
                throw new ServiceException("无权访问该门店的签约任务");
            }
        }
        if (task.getPackageId() == null)
        {
            OaSignTaskStatus taskStatus;
            try
            {
                taskStatus = OaSignTaskStatus.valueOf(task.getStatus());
            }
            catch (RuntimeException ex)
            {
                throw new ServiceException("签约任务状态不正确");
            }
            if (!TASK_DRAFT_STATUSES.contains(taskStatus) || task.getConfirmedSnapshotHash() != null)
            {
                throw new ServiceException("当前任务状态不能创建草稿");
            }
        }
        return task;
    }

    private Long resolveDraftShopDept(OaSignTask task, OaSignPackage signPackage, Long selectedShopDeptId)
    {
        if (task != null && task.getShopDeptId() != null)
        {
            return task.getShopDeptId();
        }
        return shopScopeService.resolveRequiredShopDept(
                selectedShopDeptId != null && selectedShopDeptId > 0 ? selectedShopDeptId : signPackage.getShopDeptId());
    }

    private void bindDraftToTask(OaSignPackage signPackage, OaSignTask task, Long shopDeptId)
    {
        Long taskPlanReference = taskPlanReference(task);
        signPackage.setPackageId(null);
        signPackage.setPackageNo(null);
        signPackage.setEmployeeId(task.getEmployeeId());
        signPackage.setScenario(OaSignScenarioCodes.normalizePackageScenario(task.getScenario()));
        signPackage.setShopDeptId(shopDeptId);
        signPackage.setTaskId(task.getTaskId());
        signPackage.setPlanVersionId(taskPlanReference);
        signPackage.setSourcePlanId(taskPlanReference);
        signPackage.setSourcePlanName(null);
        signPackage.setConfirmStatus(OaSignTaskStatus.NEEDS_DATA.name());
        signPackage.setStatus(OaSignPackageStatus.DRAFT);
        signPackage.setDocumentVersion(null);
        signPackage.setSentTime(null);
        signPackage.setViewedTime(null);
        signPackage.setSignedTime(null);
        signPackage.setVersion(0L);
        signPackage.setDocuments(null);
        signPackage.setEvents(null);
        if (task.getSignDeadline() != null)
        {
            signPackage.setSignDeadline(task.getSignDeadline());
        }
    }

    @Override
    public List<OaSignPackage> selectPackageList(OaSignPackage signPackage, Long selectedShopDeptId)
    {
        signHrAccessService.requireCurrentHr();
        Long assignedHrUserId = signHrAccessService.currentTaskOwnerFilter();
        signPackage.getParams().remove("assignedHrUserId");
        if (assignedHrUserId != null)
        {
            signPackage.getParams().put("assignedHrUserId", assignedHrUserId);
        }
        if (StringUtils.isNotBlank(signPackage.getScenario()))
        {
            signPackage.setScenario(OaSignScenarioCodes.normalizePackageScenario(signPackage.getScenario()));
        }
        shopScopeService.appendShopScope(signPackage, selectedShopDeptId);
        List<OaSignPackage> list = packageMapper.selectOaSignPackageList(signPackage);
        for (OaSignPackage row : list)
        {
            sanitizeListRow(row);
        }
        return list;
    }

    @Override
    public OaSignPackage getPackageDetail(Long packageId, Long selectedShopDeptId)
    {
        signHrAccessService.requireCurrentHr();
        OaSignPackage signPackage = packageAccessPolicy().hrScopedPackage(
                packageId, selectedShopDeptId);
        attachChildren(signPackage);
        return OaSignResponseSanitizer.businessPackage(signPackage);
    }

    @Override
    public OaSignPackage getPackageForVerification(Long packageId, Long selectedShopDeptId)
    {
        signHrAccessService.requireCurrentHrOrTechnicalEvidenceReader();
        OaSignPackage signPackage = packageAccessPolicy().scopedPackage(
                packageId, selectedShopDeptId);
        if (!signHrAccessService.isTechnicalEvidenceReader())
        {
            packageAccessPolicy().requireBoundTaskOwner(signPackage);
        }
        attachChildren(signPackage);
        return signPackage;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OaSignPackage updateDraftPackage(Long packageId, OaSignPackage signPackage, Long selectedShopDeptId)
    {
        signHrAccessService.requireCurrentHr();
        if (signPackage == null)
        {
            throw new ServiceException("签约包信息不能为空");
        }
        OaSignPackage existing = packageAccessPolicy().hrScopedPackage(
                packageId, selectedShopDeptId);
        if (!OaSignPackageStatus.DRAFT.equals(existing.getStatus()))
        {
            throw new ServiceException("仅草稿签约包可以编辑");
        }
        OaSignTask task = requireEditableTaskBinding(existing, selectedShopDeptId);
        OaSignPackage update = DRAFT_POLICY.buildDraftPackageUpdate(
                packageId, signPackage, existing, task, SecurityUtils.getUserId());
        DRAFT_POLICY.validateDraftPackage(update);
        String obsoleteDocumentVersion = existing.getDocumentVersion();
        List<OaSignFileEvidence> obsoleteEvidence = selectObsoleteEvidence(
                existing.getPackageId(), obsoleteDocumentVersion);
        update.setUpdateBy(SecurityUtils.getUsername());
        if (packageMapper.updateDraftOaSignPackage(update) != 1)
        {
            throw new ServiceException("签约包保存失败，请刷新后重试");
        }
        invalidateObsoleteDocuments(existing.getPackageId(), obsoleteDocumentVersion, obsoleteEvidence);
        OaSignPackage detail = packageAccessPolicy().hrScopedPackage(
                packageId, selectedShopDeptId);
        attachChildren(detail);
        return OaSignResponseSanitizer.businessPackage(detail);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OaSignPackage sendPackage(Long packageId, Long selectedShopDeptId)
    {
        signHrAccessService.requireCurrentHr();
        OaSignPackage signPackage = packageAccessPolicy().hrScopedPackage(
                packageId, selectedShopDeptId);
        if (signPackage.getTaskId() != null)
        {
            throw new ServiceException("任务签约包请在合同签约中心直接发送");
        }
        if (OaSignScenarioCodes.isRenewal(signPackage.getScenario()))
        {
            throw new ServiceException("续签必须由合同签约中心的生命周期任务发送");
        }
        generatePreparedDocuments(signPackage);
        prepareCompanyFirstFinalCandidate(signPackage);
        persistPreparedCompanyFirstSnapshot(signPackage, SecurityUtils.getUsername());
        OaSignPackageDeadlinePolicy.DeadlineSnapshot deadline =
                requireManualDeadlineSnapshot(signPackage);
        OaSignTask managedTask = createManagedTaskForManualPackage(signPackage);
        OaSignPackage sent = markCompanyFirstPackageSent(signPackage, selectedShopDeptId,
                deadline.sentTime(),
                deadline.signDeadline(), deadline.policySource(), deadline.days());
        taskEventService.transition(managedTask, OaSignTaskStatus.PENDING_SIGN,
                OaSignOperatorType.HR, SecurityUtils.getUserId(), "MANUAL_PACKAGE_SENT",
                "应急手工包已纳入统一任务闭环", null, null, null);
        if (taskMapper.updateSentLifecycle(managedTask.getTaskId(),
                OaSignTaskStatus.PENDING_SIGN.name(), managedTask.getVersion(),
                managedTask.getAssignedHrUserId(), deadline.sentTime(), deadline.signDeadline(),
                deadline.policySource(), deadline.days()) != 1)
        {
            throw new ServiceException("手工签约任务截止时间保存失败");
        }
        managedTask.setSentTime(deadline.sentTime());
        managedTask.setSignDeadline(deadline.signDeadline());
        managedTask.setDeadlinePolicySource(deadline.policySource());
        managedTask.setDeadlineDaysSnapshot(deadline.days());
        notificationOutboxService.enqueueSent(managedTask, sent == null ? signPackage : sent);
        return sent;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OaSignPackage preparePackageDocuments(Long packageId, Long selectedShopDeptId)
    {
        signHrAccessService.requireCurrentHr();
        OaSignPackage signPackage = packageAccessPolicy().hrScopedPackage(
                packageId, selectedShopDeptId);
        bindTaskPlanForDocumentPreparation(signPackage);
        generatePreparedDocuments(signPackage);
        prepareCompanyFirstFinalCandidate(signPackage);
        OaSignPackage update = new OaSignPackage();
        update.setVersion(null);
        update.setPackageId(packageId);
        update.setDocumentVersion(signPackage.getDocumentVersion());
        update.setSigningSequence(signPackage.getSigningSequence());
        update.setCompanyFrozenTime(signPackage.getCompanyFrozenTime());
        update.setFinalDocumentVersion(signPackage.getFinalDocumentVersion());
        update.setFinalDocumentRootHash(signPackage.getFinalDocumentRootHash());
        update.setFinalGeneratedTime(signPackage.getFinalGeneratedTime());
        update.setFinalConfirmationStatus(signPackage.getFinalConfirmationStatus());
        update.setConfirmStatus("NOT_REQUIRED");
        if (signPackage.getTaskId() != null)
        {
            update.setSourcePlanId(signPackage.getSourcePlanId());
            update.setPlanVersionId(signPackage.getPlanVersionId());
        }
        update.setUpdateBy(SecurityUtils.getUsername());
        if (packageMapper.updateOaSignPackage(update) != 1)
        {
            throw new ServiceException("签约文件准备失败，请刷新后重试");
        }
        recordEvent(packageId, null, "PACKAGE_PREPARED", "生成待发送合同文件，无需审核", null,
                null, null, "HR", null);
        return getPackageDetail(packageId, selectedShopDeptId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OaSignPackage preparePackageDocumentsForSystem(Long packageId, Long taskId,
            Long assignedHrUserId)
    {
        OaSignPackage signPackage = packageId == null ? null
                : packageMapper.selectOaSignPackageById(packageId);
        OaSignTask task = taskId == null ? null : taskMapper.selectOaSignTaskById(taskId);
        if (signPackage == null || task == null
                || !Objects.equals(signPackage.getTaskId(), task.getTaskId())
                || !Objects.equals(task.getPackageId(), signPackage.getPackageId())
                || !Objects.equals(task.getPlanVersionId(), signPackage.getPlanVersionId()))
        {
            throw new ServiceException("签约任务草稿关联不一致");
        }
        if (assignedHrUserId == null
                || !Objects.equals(assignedHrUserId, task.getAssignedHrUserId()))
        {
            throw new ServiceException("签约任务经办人分配已变化");
        }
        if (!OaSignTaskStatus.VALIDATING.name().equals(task.getStatus())
                && !OaSignTaskStatus.DRAFT_CREATED.name().equals(task.getStatus()))
        {
            throw new ServiceException("当前任务状态不能准备签约文件");
        }
        if ("NOT_REQUIRED".equals(signPackage.getConfirmStatus())
                && StringUtils.isNotBlank(signPackage.getDocumentVersion()))
        {
            return signPackage;
        }
        if (signPackage.getPlanVersionId() == null)
        {
            throw new ServiceException("签约任务尚未绑定不可变方案版本");
        }
        validateVersionedDraftTemplates(signPackage.getPlanVersionId());
        generatePreparedDocuments(signPackage);
        prepareCompanyFirstFinalCandidate(signPackage);
        OaSignPackage update = new OaSignPackage();
        update.setPackageId(packageId);
        update.setDocumentVersion(signPackage.getDocumentVersion());
        update.setSigningSequence(signPackage.getSigningSequence());
        update.setCompanyFrozenTime(signPackage.getCompanyFrozenTime());
        update.setFinalDocumentVersion(signPackage.getFinalDocumentVersion());
        update.setFinalDocumentRootHash(signPackage.getFinalDocumentRootHash());
        update.setFinalGeneratedTime(signPackage.getFinalGeneratedTime());
        update.setFinalConfirmationStatus(signPackage.getFinalConfirmationStatus());
        update.setConfirmStatus("NOT_REQUIRED");
        update.setSourcePlanId(signPackage.getSourcePlanId());
        update.setPlanVersionId(signPackage.getPlanVersionId());
        update.setUpdateBy("system");
        if (packageMapper.updateOaSignPackage(update) != 1)
        {
            throw new ServiceException("签约文件准备失败，请重试");
        }
        signPackage.setConfirmStatus("NOT_REQUIRED");
        recordSystemEvent(packageId, "PACKAGE_PREPARED", "系统已生成待发送文件，无需审核");
        return signPackage;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OaSignPackage prepareSignatureFirstCandidateForSystem(Long packageId, Long taskId,
            Long assignedHrUserId, Long dataRequestId, String signatureRequestId,
            byte[] signatureSampleBytes, String signatureSampleHash, Date signatureSampleTime,
            String expectedDocumentVersion, Long expectedVersion)
    {
        OaSignPackage signPackage = packageId == null ? null
                : packageMapper.selectOaSignPackageById(packageId);
        OaSignTask task = taskId == null ? null : taskMapper.selectOaSignTaskById(taskId);
        if (signPackage == null || task == null
                || !Objects.equals(signPackage.getTaskId(), taskId)
                || !Objects.equals(task.getPackageId(), packageId)
                || !Objects.equals(task.getAssignedHrUserId(), assignedHrUserId)
                || !OaSignTaskStatus.READY_TO_SEND.name().equals(task.getStatus()))
        {
            throw new ServiceException("签约任务与草稿关联已变化");
        }
        if (!OaSignSigningSequence.SIGNATURE_FIRST.equals(signPackage.getSigningSequence()))
        {
            throw new ServiceException("当前签约包不是先留签名流程");
        }
        STAGED_EVIDENCE_POLICY.validateCandidateSample(
                dataRequestId, signatureRequestId, signatureSampleBytes,
                signatureSampleHash, signatureSampleTime);
        if (!Objects.equals(signPackage.getDocumentVersion(), expectedDocumentVersion)
                || !Objects.equals(signPackage.getVersion(), expectedVersion))
        {
            throw new ServiceException("签约包草稿版本已变化");
        }
        if (StringUtils.isNotBlank(signPackage.getFinalDocumentVersion()))
        {
            validateRepeatedSignatureFirstCandidate(signPackage, dataRequestId,
                    signatureRequestId, signatureSampleHash, signatureSampleTime);
            return signPackage;
        }
        if (!OaSignPackageStatus.DRAFT.equals(signPackage.getStatus())
                || StringUtils.isBlank(signPackage.getDocumentVersion())
                || signPackage.getCompanyFrozenTime() == null)
        {
            throw new ServiceException("公司、印章或待发送文件尚未冻结");
        }
        List<OaSignPackageDocument> visibleDocuments = documentMapper
                .selectDocumentsByPackageId(packageId).stream()
                .filter(this::isEmployeeVisibleDocument).toList();
        StagedSignFile sample = null;
        try
        {
            sample = fileStorageService.promote(fileStorageService.stage(taskId, packageId,
                    "SAMPLE-" + dataRequestId, "signature-sample.png", signatureSampleBytes));
            fileLifecycle().registerArchivedEvidenceRollbackCleanup(sample);
            signPackage.setSignatureSampleFileUrl(sample.getPublicUrl());
            signPackage.setSignatureSampleHash(signatureSampleHash.toLowerCase(Locale.ROOT));
            signPackage.setSignatureSampleTime(signatureSampleTime);
            PreparedFinalCandidate candidate = generateAndPersistFinalCandidate(
                    signPackage, visibleDocuments, signatureSampleBytes, new Date());
            signPackage.setFinalDocumentVersion(candidate.finalVersion());
            signPackage.setFinalDocumentRootHash(candidate.rootHash());
            signPackage.setFinalGeneratedTime(candidate.generatedTime());
            signPackage.setFinalConfirmationStatus("PREPARED_NOT_SENT");
            signPackage.setUpdateBy("system");
            if (packageMapper.updatePreparedFinalCandidate(signPackage,
                    expectedDocumentVersion, expectedVersion) != 1)
            {
                throw new ServiceException("签约包草稿已变化，请重新生成");
            }
            evidenceStore().recordTaskSignatureSample(
                    signPackage, sample, dataRequestId, signatureSampleTime);
            recordSystemEvent(packageId, "SIGNATURE_SAMPLE_BOUND",
                    "已绑定本任务手写签名样本，不写入员工全局档案");
            recordEvent(packageId, null, "FINAL_CONTRACT_PREPARED",
                    "已生成先留签名流程的待确认候选",
                    candidate.rootHash(), null, null, "SYSTEM", signatureRequestId);
            return packageMapper.selectOaSignPackageById(packageId);
        }
        finally
        {
            fileStorageService.cleanup(sample);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OaSignPackage recordStagedSignatureFirstSampleForSystem(Long packageId, Long taskId,
            Long dataRequestId, String signatureRequestId, byte[] signatureSampleBytes,
            String signatureSampleHash, Date signatureSampleTime)
    {
        OaSignPackage signPackage = packageId == null ? null
                : packageMapper.selectOaSignPackageById(packageId);
        OaSignTask task = taskId == null ? null : taskMapper.selectOaSignTaskById(taskId);
        requireStagedSignatureBinding(signPackage, task, dataRequestId, signatureRequestId,
                signatureSampleBytes, signatureSampleHash, signatureSampleTime);
        if (OaSignPackageStatus.PENDING_COMPANY.equals(signPackage.getStatus())
                || OaSignPackageStatus.PENDING_FINAL_CONFIRM.equals(signPackage.getStatus())
                || OaSignPackageStatus.SIGNED.equals(signPackage.getStatus()))
        {
            validatePersistedStagedSignatureSample(signPackage, dataRequestId,
                    signatureSampleHash, signatureSampleTime);
            return signPackage;
        }
        if (!List.of(OaSignPackageStatus.PENDING_SIGN, OaSignPackageStatus.PART_VIEWED)
                .contains(signPackage.getStatus())
                || !List.of(OaSignTaskStatus.PENDING_SIGN.name(), OaSignTaskStatus.VIEWED.name())
                        .contains(task.getStatus()))
        {
            throw new ServiceException("当前签约包不能记录员工首次签名");
        }
        assertStagedSignatureCapturedByDeadline(signPackage, signatureSampleTime);
        Long expectedVersion = signPackage.getVersion() == null ? 0L : signPackage.getVersion();
        String currentStatus = signPackage.getStatus();
        StagedSignFile sample = null;
        try
        {
            sample = fileStorageService.promote(fileStorageService.stage(taskId, packageId,
                    "SAMPLE-" + dataRequestId, "signature-sample.png", signatureSampleBytes));
            fileLifecycle().registerArchivedEvidenceRollbackCleanup(sample);
            signPackage.setSignatureSampleFileUrl(sample.getPublicUrl());
            signPackage.setSignatureSampleHash(signatureSampleHash.toLowerCase(Locale.ROOT));
            signPackage.setSignatureSampleTime(signatureSampleTime);
            signPackage.setSignedTime(signatureSampleTime);
            signPackage.setInitialSignedTime(signatureSampleTime);
            signPackage.setFinalConfirmationStatus("WAITING_COMPANY");
            signPackage.setUpdateBy("employee:" + task.getEmployeeId());
            if (packageMapper.recordStagedEmployeeSignature(signPackage, currentStatus,
                    expectedVersion) != 1)
            {
                OaSignPackage latest = packageMapper.selectOaSignPackageById(packageId);
                if (latest != null && OaSignPackageStatus.PENDING_COMPANY.equals(latest.getStatus()))
                {
                    validatePersistedStagedSignatureSample(latest, dataRequestId,
                            signatureSampleHash, signatureSampleTime);
                    return latest;
                }
                throw new ServiceException("员工签名状态已变化，请刷新后重试");
            }
            evidenceStore().recordTaskSignatureSample(
                    signPackage, sample, dataRequestId, signatureSampleTime);
            recordSystemEvent(packageId, "SIGNATURE_SAMPLE_BOUND",
                    "已绑定本签约包唯一一次员工手写签名，不写入员工全局档案");
            recordEvent(packageId, null, "EMPLOYEE_INITIAL_SIGNED",
                    "员工已确认合同生成事实并完成唯一一次手写签名，等待HR选择公司与印章",
                    signatureSampleHash, null, null, "EMPLOYEE", signatureRequestId);
            signPackage.setStatus(OaSignPackageStatus.PENDING_COMPANY);
            signPackage.setVersion(expectedVersion + 1);
            syncTaskStatus(signPackage, OaSignTaskStatus.PENDING_COMPANY,
                    "EMPLOYEE_INITIAL_SIGNED", signatureRequestId, null, null);
            return packageMapper.selectOaSignPackageById(packageId);
        }
        finally
        {
            fileStorageService.cleanup(sample);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OaSignPackage prepareStagedSignatureFirstCandidateForSystem(Long packageId,
            Long taskId, Long assignedHrUserId, OaSignPackage finalizedSnapshot,
            Long dataRequestId, String signatureRequestId, byte[] signatureSampleBytes,
            String signatureSampleHash, Date signatureSampleTime,
            String expectedDocumentVersion, Long expectedVersion)
    {
        OaSignPackage signPackage = packageId == null ? null
                : packageMapper.selectOaSignPackageById(packageId);
        OaSignTask task = taskId == null ? null : taskMapper.selectOaSignTaskById(taskId);
        requireStagedSignatureBinding(signPackage, task, dataRequestId, signatureRequestId,
                signatureSampleBytes, signatureSampleHash, signatureSampleTime);
        if (!Objects.equals(task.getAssignedHrUserId(), assignedHrUserId)
                || !OaSignTaskStatus.PENDING_COMPANY.name().equals(task.getStatus()))
        {
            throw new ServiceException("签约任务经办人或状态已变化");
        }
        validatePersistedStagedSignatureSample(signPackage, dataRequestId,
                signatureSampleHash, signatureSampleTime);
        if ("PREPARED_NOT_SENT".equals(signPackage.getFinalConfirmationStatus())
                && StringUtils.isNotBlank(signPackage.getFinalDocumentVersion()))
        {
            STAGED_SNAPSHOT_POLICY.requireSameStagedCompanyDecision(
                    signPackage, finalizedSnapshot);
            return signPackage;
        }
        if (!OaSignPackageStatus.PENDING_COMPANY.equals(signPackage.getStatus())
                || !"WAITING_COMPANY".equals(signPackage.getFinalConfirmationStatus())
                || !Objects.equals(signPackage.getDocumentVersion(), expectedDocumentVersion)
                || !Objects.equals(signPackage.getVersion(), expectedVersion))
        {
            throw new ServiceException("签约包公司处理阶段已变化，请刷新后重试");
        }
        STAGED_SNAPSHOT_POLICY.requireSameStagedPackageIdentity(
                signPackage, finalizedSnapshot);
        STAGED_SNAPSHOT_POLICY.applyStagedFinalizedSnapshot(
                signPackage, finalizedSnapshot);
        String currentStatus = signPackage.getStatus();
        signPackage.setStatus(OaSignPackageStatus.DRAFT);
        generatePreparedDocuments(signPackage, true);
        signPackage.setStatus(currentStatus);
        List<OaSignPackageDocument> visibleDocuments = documentMapper
                .selectDocumentsByPackageId(packageId).stream()
                .filter(this::isEmployeeVisibleDocument).toList();
        PreparedFinalCandidate candidate = generateAndPersistFinalCandidate(
                signPackage, visibleDocuments, signatureSampleBytes, new Date());
        signPackage.setFinalDocumentVersion(candidate.finalVersion());
        signPackage.setFinalDocumentRootHash(candidate.rootHash());
        signPackage.setFinalGeneratedTime(candidate.generatedTime());
        signPackage.setFinalConfirmationStatus("PREPARED_NOT_SENT");
        signPackage.setUpdateBy("system");
        if (packageMapper.prepareStagedFinalCandidate(signPackage,
                expectedDocumentVersion, expectedVersion) != 1)
        {
            throw new ServiceException("签约包已变化，请重新生成最终合同");
        }
        recordSystemEvent(packageId, "PACKAGE_PREPARED",
                "已在原签约包生成公司与印章冻结后的最终合同，尚未发送");
        recordEvent(packageId, null, "FINAL_CONTRACT_PREPARED",
                "HR已生成最终合同，等待显式发送给员工确认",
                candidate.rootHash(), null, null, "SYSTEM", signatureRequestId);
        return packageMapper.selectOaSignPackageById(packageId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OaSignPackage sendStagedSignatureFirstFinalForSystem(Long packageId, Long taskId,
            Long assignedHrUserId, String requestId)
    {
        if (StringUtils.isBlank(requestId) || requestId.trim().length() > 64)
        {
            throw new ServiceException("最终合同发送请求编号无效");
        }
        OaSignPackage signPackage = packageId == null ? null
                : packageMapper.selectOaSignPackageById(packageId);
        OaSignTask task = taskId == null ? null : taskMapper.selectOaSignTaskById(taskId);
        if (signPackage == null || task == null
                || !Objects.equals(signPackage.getTaskId(), taskId)
                || !Objects.equals(task.getPackageId(), packageId)
                || !Objects.equals(task.getAssignedHrUserId(), assignedHrUserId))
        {
            throw new ServiceException("签约任务与签约包关联已变化");
        }
        if (OaSignPackageStatus.PENDING_FINAL_CONFIRM.equals(signPackage.getStatus())
                || OaSignPackageStatus.SIGNED.equals(signPackage.getStatus()))
        {
            return signPackage;
        }
        if (!OaSignPackageStatus.PENDING_COMPANY.equals(signPackage.getStatus())
                || !OaSignTaskStatus.PENDING_COMPANY.name().equals(task.getStatus())
                || !OaSignSigningSequence.SIGNATURE_FIRST.equals(
                        signPackage.getSigningSequence())
                || !"PREPARED_NOT_SENT".equals(signPackage.getFinalConfirmationStatus())
                || StringUtils.isBlank(signPackage.getFinalDocumentVersion())
                || StringUtils.isBlank(signPackage.getFinalDocumentRootHash())
                || signPackage.getFinalGeneratedTime() == null)
        {
            throw new ServiceException("最终合同尚未生成或已经发送");
        }
        List<OaSignPackageDocument> visibleDocuments = documentMapper
                .selectDocumentsByPackageId(packageId).stream()
                .filter(this::isEmployeeVisibleDocument).toList();
        Map<Long, String> hashes = fileIntegrity().validateFinalDocuments(
                signPackage, visibleDocuments);
        String actualRoot = fileIntegrity().finalDocumentRootHashFromHashes(hashes);
        if (!sameHash(actualRoot, signPackage.getFinalDocumentRootHash()))
        {
            throw new ServiceException("最终合同文件集合校验不一致，请重新生成");
        }
        Long expectedVersion = signPackage.getVersion() == null ? 0L : signPackage.getVersion();
        signPackage.setSignDeadline(resumeEmployeeDeadlineAfterCompanyStage(
                signPackage, new Date()));
        signPackage.setConfirmStatus("NOT_REQUIRED");
        signPackage.setUpdateBy(String.valueOf(assignedHrUserId));
        if (packageMapper.sendStagedFinalCandidate(signPackage, expectedVersion) != 1)
        {
            OaSignPackage latest = packageMapper.selectOaSignPackageById(packageId);
            if (latest != null && OaSignPackageStatus.PENDING_FINAL_CONFIRM
                    .equals(latest.getStatus())) return latest;
            throw new ServiceException("最终合同发送状态已变化，请刷新后重试");
        }
        signPackage.setStatus(OaSignPackageStatus.PENDING_FINAL_CONFIRM);
        signPackage.setFinalConfirmationStatus("PENDING");
        signPackage.setVersion(expectedVersion + 1);
        recordEvent(packageId, null, "FINAL_CANDIDATE_SENT",
                "HR已发送最终合同；员工仅确认文件，不再签名",
                actualRoot, null, null, "HR", requestId);
        syncTaskStatus(signPackage, OaSignTaskStatus.PENDING_FINAL_CONFIRM,
                "FINAL_CANDIDATE_SENT", requestId, null, null);
        return packageMapper.selectOaSignPackageById(packageId);
    }

    private void requireStagedSignatureBinding(OaSignPackage signPackage, OaSignTask task,
            Long dataRequestId, String signatureRequestId, byte[] signatureSampleBytes,
            String signatureSampleHash, Date signatureSampleTime)
    {
        STAGED_EVIDENCE_POLICY.requireStagedSignatureBinding(
                signPackage, task, dataRequestId, signatureRequestId,
                signatureSampleBytes, signatureSampleHash, signatureSampleTime);
    }

    private void validatePersistedStagedSignatureSample(OaSignPackage signPackage,
            Long dataRequestId, String expectedHash, Date expectedTime)
    {
        STAGED_EVIDENCE_POLICY.validatePersistedStagedSignatureSnapshot(
                signPackage, expectedHash, expectedTime);
        List<OaSignFileEvidence> evidenceRows = evidenceMapper
                .selectEvidenceByPackageId(signPackage.getPackageId());
        STAGED_EVIDENCE_POLICY.validatePersistedStagedSignatureEvidence(
                dataRequestId, expectedHash, expectedTime, evidenceRows);
        fileIntegrity().readAndValidateManagedFile(
                signPackage.getSignatureSampleFileUrl(), expectedHash,
                "签约包唯一签名文件校验不一致",
                "读取签约包唯一签名失败");
    }

    private void validateRepeatedSignatureFirstCandidate(OaSignPackage signPackage,
            Long expectedDataRequestId, String expectedSignatureRequestId,
            String expectedSampleHash, Date expectedSampleTime)
    {
        OaSignPackageStagedEvidencePolicy.FrozenSignatureFirstEvidence frozen =
                validateFrozenSignatureFirstCandidate(signPackage);
        STAGED_EVIDENCE_POLICY.validateRepeatedSignatureFirstCandidate(
                signPackage, frozen, expectedDataRequestId,
                expectedSignatureRequestId, expectedSampleHash, expectedSampleTime);
    }

    private OaSignPackageStagedEvidencePolicy.FrozenSignatureFirstEvidence
            validateFrozenSignatureFirstCandidate(OaSignPackage signPackage)
    {
        STAGED_EVIDENCE_POLICY.validateFrozenSignatureFirstPackage(signPackage);
        List<OaSignFileEvidence> persistedEvidence = evidenceMapper
                .selectEvidenceByPackageId(signPackage.getPackageId());
        OaSignFileEvidence sample = STAGED_EVIDENCE_POLICY
                .validateFrozenSignatureSample(signPackage, persistedEvidence);
        List<OaSignEvent> persistedEvents = eventMapper
                .selectEventsByPackageId(signPackage.getPackageId());
        OaSignEvent prepared = STAGED_EVIDENCE_POLICY
                .validateFrozenPreparedEvent(signPackage, persistedEvents);
        fileIntegrity().readAndValidateManagedFile(
                signPackage.getSignatureSampleFileUrl(),
                signPackage.getSignatureSampleHash(),
                "任务级手写签名样本校验不一致",
                "读取任务级手写签名样本失败");
        return new OaSignPackageStagedEvidencePolicy.FrozenSignatureFirstEvidence(
                sample, prepared);
    }

    private void validateVersionedDraftTemplates(Long planVersionId)
    {
        List<OaSignPlanVersionTemplate> templates =
                planVersionMapper.selectTemplatesByVersionId(planVersionId);
        if (templates == null || templates.isEmpty())
        {
            throw new IOaSignPackageService.DraftValidationException(
                    "TEMPLATE_NOT_FOUND", "签约方案版本没有可用模板");
        }
        for (OaSignPlanVersionTemplate template : templates)
        {
            if (template == null || StringUtils.isBlank(template.getSourceFileUrl())
                    || StringUtils.isBlank(template.getSourceFileHash()))
            {
                throw new IOaSignPackageService.DraftValidationException(
                        "TEMPLATE_FILE_INVALID", "签约方案版本模板快照不完整");
            }
            String actualHash = documentService.calculateFileUrlSha256(template.getSourceFileUrl());
            if (StringUtils.isBlank(actualHash))
            {
                throw new IOaSignPackageService.DraftValidationException(
                        "TEMPLATE_FILE_MISSING", "模板文件不存在");
            }
            if (!actualHash.equalsIgnoreCase(template.getSourceFileHash()))
            {
                throw new IOaSignPackageService.DraftValidationException(
                        "TEMPLATE_HASH_MISMATCH", "模板文件校验值与发布版本不一致");
            }
        }
    }

    private void bindTaskPlanForDocumentPreparation(OaSignPackage signPackage)
    {
        if (signPackage.getTaskId() == null)
        {
            return;
        }
        OaSignTask task = taskMapper.selectOaSignTaskById(signPackage.getTaskId());
        if (task == null || !Objects.equals(task.getPackageId(), signPackage.getPackageId()))
        {
            throw new ServiceException("任务签约包关联已变化，请刷新后重试");
        }
        Long taskPlanReference = taskPlanReference(task);
        if (taskPlanReference == null)
        {
            throw new ServiceException("签约任务尚未绑定不可变方案版本");
        }
        signPackage.setEmployeeId(task.getEmployeeId());
        signPackage.setShopDeptId(task.getShopDeptId());
        signPackage.setScenario(OaSignScenarioCodes.normalizePackageScenario(task.getScenario()));
        signPackage.setPlanVersionId(taskPlanReference);
        OaSignPlanVersion planVersion = planVersionMapper.selectPlanVersionById(taskPlanReference);
        signPackage.setSourcePlanId(planVersion == null ? taskPlanReference : planVersion.getPlanId());
        if (planVersion != null)
        {
            signPackage.setSourcePlanName(planVersion.getPlanName());
        }
    }

    private List<OaSignFileEvidence> selectObsoleteEvidence(Long packageId, String documentVersion)
    {
        if (StringUtils.isBlank(documentVersion))
        {
            return List.of();
        }
        List<OaSignFileEvidence> evidence = evidenceMapper.selectEvidenceByPackageId(packageId);
        if (evidence == null || evidence.isEmpty())
        {
            return List.of();
        }
        List<OaSignFileEvidence> obsolete = new ArrayList<>();
        for (OaSignFileEvidence row : evidence)
        {
            if (row != null && Objects.equals(documentVersion, row.getDocumentVersion()))
            {
                obsolete.add(row);
            }
        }
        return obsolete;
    }

    private void invalidateObsoleteDocuments(Long packageId, String documentVersion,
            List<OaSignFileEvidence> obsoleteEvidence)
    {
        if (StringUtils.isBlank(documentVersion))
        {
            return;
        }
        evidenceMapper.deleteEvidenceByPackageIdAndDocumentVersion(
                packageId, documentVersion);
        documentMapper.deleteDocumentsByPackageId(packageId);
        fileLifecycle().scheduleObsoleteEvidenceDeletion(obsoleteEvidence);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OaSignPackage sendPreparedPackage(Long packageId, Long taskId,
            String documentVersion, Long selectedShopDeptId,
            Date sentTime, Date signDeadline, String deadlinePolicySource,
            Integer deadlineDaysSnapshot)
    {
        signHrAccessService.requireCurrentHr();
        OaSignPackage signPackage = packageAccessPolicy().hrScopedPackage(
                packageId, selectedShopDeptId);
        if (!OaSignPackageStatus.DRAFT.equals(signPackage.getStatus()))
        {
            if (Objects.equals(signPackage.getTaskId(), taskId)
                    && (OaSignPackageStatus.PENDING_SIGN.equals(signPackage.getStatus())
                    || OaSignPackageStatus.PART_VIEWED.equals(signPackage.getStatus())
                    || OaSignPackageStatus.PENDING_FINAL_CONFIRM.equals(signPackage.getStatus())
                    || OaSignPackageStatus.SIGNED.equals(signPackage.getStatus())))
            {
                return getPackageDetail(packageId, selectedShopDeptId);
            }
            throw new ServiceException("仅草稿签约包可以发送");
        }
        if (!Objects.equals(signPackage.getTaskId(), taskId)
                || StringUtils.isBlank(documentVersion)
                || !Objects.equals(signPackage.getDocumentVersion(), documentVersion))
        {
            throw new ServiceException("任务确认的文档版本已变化，请重新确认");
        }
        List<OaSignPackageDocument> documents = documentMapper.selectDocumentsByPackageId(packageId);
        if (documents == null || documents.isEmpty()
                || documents.stream().anyMatch(document -> StringUtils.isBlank(document.getReviewPdfUrl())
                || StringUtils.isBlank(document.getReviewPdfHash())
                || !Objects.equals(documentVersion, document.getDocumentVersion())))
        {
            throw new ServiceException("签约文件尚未准备完成");
        }
        if (OaSignSigningSequence.SIGNATURE_FIRST.equals(signPackage.getSigningSequence()))
        {
            return markSignatureFirstPackageSent(signPackage, documents, selectedShopDeptId,
                    sentTime, signDeadline, deadlinePolicySource, deadlineDaysSnapshot);
        }
        return markCompanyFirstPackageSent(signPackage, selectedShopDeptId, sentTime, signDeadline,
                deadlinePolicySource, deadlineDaysSnapshot);
    }

    @Override
    public void markTaskConfirmation(Long packageId, Long taskId, String confirmStatus, Long planVersionId)
    {
        signHrAccessService.requireCurrentHr();
        if (packageMapper.updateTaskConfirmation(packageId, taskId, confirmStatus, planVersionId) != 1)
        {
            throw new ServiceException("签约包确认状态保存失败");
        }
    }

    private void generatePreparedDocuments(OaSignPackage signPackage)
    {
        generatePreparedDocuments(signPackage, false);
    }

    private void generatePreparedDocuments(OaSignPackage signPackage,
            boolean preserveTaskSignatureSample)
    {
        if (!OaSignPackageStatus.DRAFT.equals(signPackage.getStatus()))
        {
            throw new ServiceException("仅草稿签约包可以生成文件");
        }
        List<OaSignTemplate> candidates = selectTemplatesForSending(signPackage);
        List<OaSignTemplate> matchedTemplates = filterTemplatesForPackage(candidates, signPackage);
        if (matchedTemplates == null || matchedTemplates.isEmpty())
        {
            throw new ServiceException("请先配置启用状态的签约包模板");
        }
        OaSignPackagePreflightValidator.validate(signPackage, matchedTemplates);
        freezeAndValidateExplicitSequenceCompany(signPackage, matchedTemplates);
        for (OaSignTemplate template : matchedTemplates)
        {
            documentService.assertTemplateLegalEntityCompatible(
                    template.getTemplateType(), template.getFileUrl(),
                    signPackage.getLegalEntityNameSnapshot());
        }
        String nextDocumentVersion = nextDocumentVersion(signPackage);
        List<OaSignFileEvidence> obsoleteEvidence = evidenceMapper.selectEvidenceByPackageId(
                signPackage.getPackageId());
        if (preserveTaskSignatureSample)
        {
            String obsoleteDocumentVersion = signPackage.getDocumentVersion();
            obsoleteEvidence = obsoleteEvidence == null ? List.of()
                    : obsoleteEvidence.stream()
                            .filter(Objects::nonNull)
                            .filter(value -> Objects.equals(obsoleteDocumentVersion,
                                    value.getDocumentVersion()))
                            .toList();
            evidenceMapper.deleteEvidenceByPackageIdAndDocumentVersion(
                    signPackage.getPackageId(), obsoleteDocumentVersion);
        }
        else
        {
            evidenceMapper.deleteEvidenceByPackageId(signPackage.getPackageId());
        }
        documentMapper.deleteDocumentsByPackageId(signPackage.getPackageId());
        fileLifecycle().scheduleObsoleteEvidenceDeletion(obsoleteEvidence);
        signPackage.setDocumentVersion(nextDocumentVersion);
        List<String> actualPackageTemplateTypes = matchedTemplates.stream()
                .map(OaSignTemplate::getTemplateType)
                .filter(StringUtils::isNotBlank)
                .toList();
        int sortOrder = 1;
        for (OaSignTemplate template : matchedTemplates)
        {
            createPackageDocument(signPackage, template, sortOrder++, actualPackageTemplateTypes);
        }
    }

    private void prepareCompanyFirstFinalCandidate(OaSignPackage signPackage)
    {
        if (!OaSignSigningSequence.COMPANY_FIRST.equals(signPackage.getSigningSequence()))
        {
            return;
        }
        List<OaSignPackageDocument> packageDocuments = documentMapper
                .selectDocumentsByPackageId(signPackage.getPackageId());
        List<OaSignPackageDocument> visibleDocuments = (packageDocuments == null
                ? List.<OaSignPackageDocument>of() : packageDocuments).stream()
                .filter(this::isEmployeeVisibleDocument).toList();
        assertSigningSequencePolicyMatches(signPackage, visibleDocuments);
        assertCompanyFirstSignaturePreservesStableBody(visibleDocuments);
        PreparedFinalCandidate candidate = generateAndPersistCompanyFirstCandidate(
                signPackage, visibleDocuments, new Date());
        signPackage.setFinalDocumentVersion(candidate.finalVersion());
        signPackage.setFinalDocumentRootHash(candidate.rootHash());
        signPackage.setFinalGeneratedTime(candidate.generatedTime());
        signPackage.setFinalConfirmationStatus("PREPARED_NOT_SENT");
        recordEvent(signPackage.getPackageId(), null, "FINAL_CONTRACT_PREPARED",
                "已生成冻结公司与印章的先选公司待签完整文件",
                candidate.rootHash(), null, null, "SYSTEM", null);
    }

    private void persistPreparedCompanyFirstSnapshot(OaSignPackage signPackage, String updateBy)
    {
        if (!OaSignSigningSequence.COMPANY_FIRST.equals(signPackage.getSigningSequence()))
        {
            return;
        }
        OaSignPackage update = new OaSignPackage();
        update.setPackageId(signPackage.getPackageId());
        update.setDocumentVersion(signPackage.getDocumentVersion());
        update.setSigningSequence(signPackage.getSigningSequence());
        update.setCompanyFrozenTime(signPackage.getCompanyFrozenTime());
        update.setFinalDocumentVersion(signPackage.getFinalDocumentVersion());
        update.setFinalDocumentRootHash(signPackage.getFinalDocumentRootHash());
        update.setFinalGeneratedTime(signPackage.getFinalGeneratedTime());
        update.setFinalConfirmationStatus(signPackage.getFinalConfirmationStatus());
        update.setUpdateBy(StringUtils.isBlank(updateBy) ? "system" : updateBy);
        if (packageMapper.updateOaSignPackage(update) != 1)
        {
            throw new ServiceException("先选公司待签完整文件快照保存失败");
        }
    }

    private void assertCompanyFirstSignaturePreservesStableBody(
            List<OaSignPackageDocument> visibleDocuments)
    {
        if (visibleDocuments == null || visibleDocuments.isEmpty())
        {
            throw new ServiceException("签约包没有员工可见文件");
        }
        for (OaSignPackageDocument document : visibleDocuments)
        {
            if (placementPolicyService.requiresEmployeeSignature(document)
                    && placementPolicyService.resolveSignaturePlacement(document) != null)
            {
                throw new ServiceException("先选公司流程的员工签名必须使用追加确认页，"
                        + "不得在员工确认后改变合同正文");
            }
        }
    }

    private void assertSigningSequencePolicyMatches(OaSignPackage signPackage,
            List<OaSignPackageDocument> documents)
    {
        if (documents == null)
        {
            return;
        }
        for (OaSignPackageDocument document : documents)
        {
            placementPolicyService.assertSigningSequenceMatches(signPackage, document);
        }
    }

    private void freezeAndValidateExplicitSequenceCompany(OaSignPackage signPackage,
            List<OaSignTemplate> templates)
    {
        if (signPackage == null || StringUtils.isBlank(signPackage.getSigningSequence()))
        {
            return;
        }
        signPackage.setSigningSequence(OaSignSigningSequence.normalize(
                signPackage.getSigningSequence()));
        boolean sealRequired = templates != null && templates.stream()
                .anyMatch(template -> YES.equals(placementPolicyService
                        .normalizeCompanySealRequired(template.getCompanySealRequired(),
                                OaSignTemplateType.require(template.getTemplateType())
                                        .isEmployeeVisible())));
        requireFrozenCompanyContext(signPackage, sealRequired);
        if (signPackage.getCompanyFrozenTime() == null)
        {
            signPackage.setCompanyFrozenTime(new Date());
        }
    }

    private void clearPendingCompanySnapshot(OaSignPackage signPackage)
    {
        signPackage.setLegalEntityIdSnapshot(null);
        signPackage.setLegalEntityCodeSnapshot(null);
        signPackage.setLegalEntityNameSnapshot(null);
        signPackage.setLegalEntitySourceDeptId(null);
        signPackage.setLegalEntityResolveMode(null);
        signPackage.setLegalEntityCreditCodeSnapshot(null);
        signPackage.setLegalEntityAddressSnapshot(null);
        signPackage.setLegalRepresentativeSnapshot(null);
        signPackage.setLegalEntityPhoneSnapshot(null);
        signPackage.setLegalEntityOverrideReason(null);
        signPackage.setSealIdSnapshot(null);
        signPackage.setSealNameSnapshot(null);
        signPackage.setSealImageUrlSnapshot(null);
        signPackage.setSealImageHashSnapshot(null);
        clearFinalDocumentSnapshot(signPackage);
    }

    private void clearFinalDocumentSnapshot(OaSignPackage signPackage)
    {
        signPackage.setFinalDocumentVersion(null);
        signPackage.setFinalDocumentRootHash(null);
        signPackage.setFinalGeneratedTime(null);
        signPackage.setFinalConfirmedTime(null);
        signPackage.setFinalConfirmationStatus(null);
    }

    private OaSignPackage markPackageSent(OaSignPackage signPackage, Long selectedShopDeptId,
            Date sentTime, Date signDeadline, String deadlinePolicySource,
            Integer deadlineDaysSnapshot)
    {
        DEADLINE_POLICY.validateSendDeadline(
                sentTime, signDeadline, deadlinePolicySource, deadlineDaysSnapshot);
        Long expectedVersion = signPackage.getVersion() == null ? 0L : signPackage.getVersion();
        String confirmStatus = signPackage.getTaskId() == null
                ? signPackage.getConfirmStatus() : "NOT_REQUIRED";
        if (packageMapper.markSentWithVersion(signPackage.getPackageId(), signPackage.getTaskId(),
                OaSignPackageStatus.DRAFT, expectedVersion, signPackage.getDocumentVersion(),
                sentTime, signDeadline, deadlinePolicySource, deadlineDaysSnapshot,
                confirmStatus, SecurityUtils.getUsername()) != 1)
        {
            throw new ServiceException("签约包发送失败，请刷新后重试");
        }
        recordEvent(signPackage.getPackageId(), null, "PACKAGE_SENT", "发送员工签约包",
                null, null, null, "HR", null);
        return getPackageDetail(signPackage.getPackageId(), selectedShopDeptId);
    }

    private OaSignPackage markCompanyFirstPackageSent(OaSignPackage signPackage,
            Long selectedShopDeptId, Date sentTime, Date signDeadline,
            String deadlinePolicySource, Integer deadlineDaysSnapshot)
    {
        // Legacy/manual drafts created before sequence snapshots remain readable and sendable;
        // all new lifecycle tasks carry an explicit sequence and enter the strict branch below.
        if (StringUtils.isBlank(signPackage.getSigningSequence()))
        {
            return markPackageSent(signPackage, selectedShopDeptId, sentTime, signDeadline,
                    deadlinePolicySource, deadlineDaysSnapshot);
        }
        if (!OaSignSigningSequence.COMPANY_FIRST.equals(signPackage.getSigningSequence()))
        {
            throw new ServiceException("先选公司流程顺序快照不一致");
        }
        if (signPackage.getCompanyFrozenTime() == null
                || StringUtils.isBlank(signPackage.getFinalDocumentVersion())
                || StringUtils.isBlank(signPackage.getFinalDocumentRootHash())
                || signPackage.getFinalGeneratedTime() == null)
        {
            throw new ServiceException("先选公司流程的完整待签文件尚未准备完成");
        }
        List<OaSignPackageDocument> visibleDocuments = documentMapper
                .selectDocumentsByPackageId(signPackage.getPackageId()).stream()
                .filter(this::isEmployeeVisibleDocument).toList();
        assertSigningSequencePolicyMatches(signPackage, visibleDocuments);
        requireFrozenCompanyContext(signPackage, visibleDocuments.stream()
                .anyMatch(placementPolicyService::requiresCompanySeal));
        assertCompanyFirstSignaturePreservesStableBody(visibleDocuments);
        Map<Long, String> hashes = fileIntegrity().validateFinalDocuments(
                signPackage, visibleDocuments);
        String actualRoot = fileIntegrity().finalDocumentRootHashFromHashes(hashes);
        if (!sameHash(actualRoot, signPackage.getFinalDocumentRootHash()))
        {
            throw new ServiceException("待签完整文件集合校验不一致，请重新生成");
        }
        OaSignPackage sent = markPackageSent(signPackage, selectedShopDeptId, sentTime,
                signDeadline, deadlinePolicySource, deadlineDaysSnapshot);
        recordEvent(signPackage.getPackageId(), null, "FINAL_CANDIDATE_SENT",
                "HR已发送冻结公司与印章的完整待签文件",
                actualRoot, null, null, "HR", null);
        return sent;
    }

    private OaSignPackage markSignatureFirstPackageSent(OaSignPackage signPackage,
            List<OaSignPackageDocument> documents, Long selectedShopDeptId,
            Date sentTime, Date signDeadline, String deadlinePolicySource,
            Integer deadlineDaysSnapshot)
    {
        DEADLINE_POLICY.validateSendDeadline(
                sentTime, signDeadline, deadlinePolicySource, deadlineDaysSnapshot);
        if (signPackage.getCompanyFrozenTime() == null
                || StringUtils.isBlank(signPackage.getFinalDocumentVersion())
                || StringUtils.isBlank(signPackage.getFinalDocumentRootHash())
                || signPackage.getFinalGeneratedTime() == null
                || StringUtils.isBlank(signPackage.getSignatureSampleFileUrl())
                || StringUtils.isBlank(signPackage.getSignatureSampleHash())
                || signPackage.getSignatureSampleTime() == null)
        {
            throw new ServiceException("先留签名流程的完整候选尚未准备完成");
        }
        List<OaSignPackageDocument> visible = documents.stream()
                .filter(this::isEmployeeVisibleDocument).toList();
        assertSigningSequencePolicyMatches(signPackage, visible);
        requireFrozenCompanyContext(signPackage,
                visible.stream().anyMatch(placementPolicyService::requiresCompanySeal));
        validateFrozenSignatureFirstCandidate(signPackage);
        Map<Long, String> hashes = fileIntegrity().validateFinalDocuments(
                signPackage, visible);
        String actualRoot = fileIntegrity().finalDocumentRootHashFromHashes(hashes);
        if (!sameHash(actualRoot, signPackage.getFinalDocumentRootHash()))
        {
            throw new ServiceException("待确认文件集合校验不一致，请重新生成");
        }
        Long expectedVersion = signPackage.getVersion() == null ? 0L : signPackage.getVersion();
        String confirmStatus = signPackage.getTaskId() == null
                ? signPackage.getConfirmStatus() : "NOT_REQUIRED";
        if (packageMapper.markSignatureFirstSentWithVersion(signPackage.getPackageId(),
                signPackage.getTaskId(), expectedVersion, signPackage.getDocumentVersion(),
                signPackage.getFinalDocumentVersion(), actualRoot, sentTime, signDeadline,
                deadlinePolicySource, deadlineDaysSnapshot, confirmStatus,
                SecurityUtils.getUsername()) != 1)
        {
            throw new ServiceException("签约包发送失败，请刷新后重试");
        }
        recordEvent(signPackage.getPackageId(), null, "FINAL_CANDIDATE_SENT",
                "HR已显式发送先留签名的待确认文件",
                actualRoot, null, null, "HR", null);
        return getPackageDetail(signPackage.getPackageId(), selectedShopDeptId);
    }

    private OaSignPackageDeadlinePolicy.DeadlineSnapshot
            requireManualDeadlineSnapshot(OaSignPackage signPackage)
    {
        if (signPackage.getSourcePlanId() == null)
        {
            throw new ServiceException("应急手工签约包必须选择含签署期限的服务端方案");
        }
        OaSignPlan plan = planMapper.selectOaSignPlanById(signPackage.getSourcePlanId());
        return DEADLINE_POLICY.manualDeadlineSnapshot(plan, Instant.now());
    }

    private OaSignTask createManagedTaskForManualPackage(OaSignPackage signPackage)
    {
        if (signPackage.getPackageId() == null || signPackage.getEmployeeId() == null
                || signPackage.getShopDeptId() == null || StringUtils.isBlank(signPackage.getScenario()))
        {
            throw new ServiceException("应急手工签约包缺少任务绑定信息");
        }
        Long hrUserId = SecurityUtils.getUserId();
        if (hrUserId == null)
        {
            throw new ServiceException("无法确定应急手工包的合同经办人");
        }
        String identity = "MANUAL_PACKAGE:" + signPackage.getPackageId();
        String dedupeKey = "SIGN_MANUAL_PACKAGE:" + signPackage.getPackageId();
        OaSignTask existing = taskMapper.selectOaSignTaskByDedupeKey(dedupeKey);
        if (existing != null)
        {
            assertManagedManualTask(existing, signPackage, hrUserId);
            signPackage.setTaskId(existing.getTaskId());
            return existing;
        }

        OaSignTask task = new OaSignTask();
        task.setTaskNo("STM" + System.currentTimeMillis()
                + UUID.randomUUID().toString().substring(0, 6).toUpperCase());
        String taskScenario = OaSignScenarioCodes.normalizeTaskScenario(signPackage.getScenario());
        if (OaSignScenarioCodes.RENEWAL.equals(taskScenario))
        {
            throw new ServiceException("续签手工包不得绕过续签生命周期门闩");
        }
        task.setScenario(taskScenario);
        task.setEmployeeId(signPackage.getEmployeeId());
        task.setShopDeptId(signPackage.getShopDeptId());
        task.setLegalEntityId(signPackage.getLegalEntityIdSnapshot());
        task.setAssignedHrUserId(hrUserId);
        task.setSourceType("MANUAL_PACKAGE");
        task.setSourceBusinessId(String.valueOf(signPackage.getPackageId()));
        task.setSourceEventVersion(identity);
        task.setDedupeKey(dedupeKey);
        task.setStatus(OaSignTaskStatus.SENDING.name());
        task.setAutomationLevel("MANUAL");
        task.setRiskLevel("NORMAL");
        task.setPackageId(signPackage.getPackageId());
        task.setRetryCount(0);
        task.setVersion(0L);
        try
        {
            if (taskMapper.insertOaSignTask(task) != 1)
            {
                throw new ServiceException("应急手工签约任务创建失败");
            }
        }
        catch (DuplicateKeyException duplicate)
        {
            OaSignTask concurrent = taskMapper.selectOaSignTaskByDedupeKey(dedupeKey);
            if (concurrent == null)
            {
                throw duplicate;
            }
            assertManagedManualTask(concurrent, signPackage, hrUserId);
            task = concurrent;
        }
        signPackage.setTaskId(task.getTaskId());
        return task;
    }

    private void assertManagedManualTask(OaSignTask task, OaSignPackage signPackage, Long hrUserId)
    {
        if (task.getTaskId() == null || !Objects.equals(task.getPackageId(), signPackage.getPackageId())
                || !Objects.equals(task.getEmployeeId(), signPackage.getEmployeeId())
                || !Objects.equals(task.getShopDeptId(), signPackage.getShopDeptId())
                || !"MANUAL_PACKAGE".equals(task.getSourceType())
                || !OaSignTaskStatus.SENDING.name().equals(task.getStatus()))
        {
            throw new ServiceException("应急手工签约包的受控任务关联不一致");
        }
    }

    private void clearServerOwnedLifecycle(OaSignPackage signPackage,
            boolean preserveServerCompany)
    {
        signPackage.setVoidReason(null);
        signPackage.setSentTime(null);
        signPackage.setViewedTime(null);
        signPackage.setSignedTime(null);
        signPackage.setInitialSignedTime(null);
        signPackage.setDocumentVersion(null);
        signPackage.setSignDeadline(null);
        signPackage.setDeadlinePolicySource(null);
        signPackage.setDeadlineDaysSnapshot(null);
        signPackage.setTerminalTime(null);
        signPackage.setTerminalReasonCode(null);
        signPackage.setTerminalReasonDetail(null);
        signPackage.setResolutionStatus(null);
        signPackage.setResolvedBy(null);
        signPackage.setResolvedTime(null);
        signPackage.setResolutionReasonCode(null);
        signPackage.setResolutionReasonDetail(null);
        signPackage.setReissueOfPackageId(null);
        signPackage.setReissuedToPackageId(null);
        if (preserveServerCompany) clearFinalDocumentSnapshot(signPackage);
        else clearPendingCompanySnapshot(signPackage);
        signPackage.setVersion(0L);
        signPackage.setDocuments(null);
        signPackage.setEvents(null);
    }

    private String nextDocumentVersion(OaSignPackage signPackage)
    {
        String prefix = "SP-" + signPackage.getPackageId() + "-V";
        if (StringUtils.isBlank(signPackage.getDocumentVersion()))
        {
            return prefix + "1";
        }
        if (!signPackage.getDocumentVersion().startsWith(prefix))
        {
            throw new ServiceException("文档版本格式不合法");
        }
        String sequence = signPackage.getDocumentVersion().substring(prefix.length());
        if (!sequence.matches("[1-9][0-9]*"))
        {
            throw new ServiceException("文档版本格式不合法");
        }
        return prefix + (Long.parseLong(sequence) + 1);
    }

    private List<OaSignTemplate> selectTemplatesForSending(OaSignPackage signPackage)
    {
        if (signPackage.getPlanVersionId() != null)
        {
            List<OaSignPlanVersionTemplate> snapshots =
                    planVersionMapper.selectTemplatesByVersionId(signPackage.getPlanVersionId());
            if (snapshots == null)
            {
                return List.of();
            }
            List<OaSignTemplate> templates = new ArrayList<>();
            for (OaSignPlanVersionTemplate snapshot : snapshots)
            {
                OaSignTemplate template =
                        OaSignTemplateApplicabilityPolicy.fromVersionSnapshot(snapshot);
                if (template == null)
                {
                    continue;
                }
                templates.add(template);
            }
            return templates;
        }
        if (signPackage.getSourcePlanId() != null)
        {
            return planMapper.selectActiveTemplatesByPlanId(signPackage.getSourcePlanId());
        }
        return templateMapper.selectMatchedActiveTemplates(signPackage);
    }

    private List<OaSignTemplate> filterTemplatesForPackage(List<OaSignTemplate> candidates,
            OaSignPackage signPackage)
    {
        if (candidates == null || candidates.isEmpty())
        {
            return candidates;
        }
        List<OaSignTemplate> matched = new ArrayList<>();
        for (OaSignTemplate template : candidates)
        {
            if (OaSignTemplateApplicabilityPolicy.shouldIncludeTemplate(template, signPackage))
            {
                matched.add(template);
            }
        }
        return matched;
    }

    private OaSignTask requireEditableTaskBinding(OaSignPackage existing, Long selectedShopDeptId)
    {
        if (existing.getTaskId() == null)
        {
            return null;
        }
        OaSignTask task = taskMapper.selectOaSignTaskById(existing.getTaskId());
        if (task == null || !Objects.equals(existing.getPackageId(), task.getPackageId()))
        {
            throw new ServiceException("任务签约包关联已变化，请刷新后重试");
        }
        if (task.getShopDeptId() != null)
        {
            List<Long> scopeDeptIds = shopScopeService.resolveScopeDeptIds(selectedShopDeptId);
            if (scopeDeptIds == null || !scopeDeptIds.contains(task.getShopDeptId()))
            {
                throw new ServiceException("无权访问该门店的签约任务");
            }
        }
        OaSignTaskStatus taskStatus;
        try
        {
            taskStatus = OaSignTaskStatus.valueOf(task.getStatus());
        }
        catch (RuntimeException ex)
        {
            throw new ServiceException("签约任务状态不正确");
        }
        if (!TASK_DRAFT_STATUSES.contains(taskStatus) || task.getConfirmedSnapshotHash() != null)
        {
            throw new ServiceException("当前任务状态不能编辑草稿");
        }
        return task;
    }

    /**
     * Phase 2 has one server-owned plan reference on the task. Until the immutable plan-version
     * tables land in phase 3, the legacy source_plan_id template lookup uses that same reference.
     * Keeping the conversion here makes the phase-3 mapping explicit and prevents request values
     * from influencing template selection.
     */
    private Long taskPlanReference(OaSignTask task)
    {
        return task == null ? null : task.getPlanVersionId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OaSignPackage voidPackage(Long packageId, Long selectedShopDeptId, String voidReason)
    {
        signHrAccessService.requireCurrentHr();
        OaSignPackage signPackage = packageAccessPolicy().hrScopedPackage(
                packageId, selectedShopDeptId);
        if (signPackage.getTaskId() != null)
        {
            throw new ServiceException("任务签约包请在合同签约中心取消，避免任务与签约包状态不一致");
        }
        return voidScopedPackage(signPackage, selectedShopDeptId, voidReason);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OaSignPackage voidPackageForTask(Long packageId, Long taskId,
            Long selectedShopDeptId, String voidReason)
    {
        signHrAccessService.requireCurrentHr();
        OaSignPackage signPackage = packageAccessPolicy().hrScopedPackage(
                packageId, selectedShopDeptId);
        if (taskId == null || !Objects.equals(signPackage.getTaskId(), taskId))
        {
            throw new ServiceException("签约任务与签约包关联已变化");
        }
        return voidScopedPackage(signPackage, selectedShopDeptId, voidReason);
    }

    private OaSignPackage voidScopedPackage(OaSignPackage signPackage,
            Long selectedShopDeptId, String voidReason)
    {
        if (OaSignPackageStatus.SIGNED.equals(signPackage.getStatus())
                || OaSignPackageStatus.REFUSED.equals(signPackage.getStatus())
                || OaSignPackageStatus.EXPIRED.equals(signPackage.getStatus())
                || OaSignPackageStatus.VOIDED.equals(signPackage.getStatus()))
        {
            throw new ServiceException("终态签约包不能撤回或修改");
        }
        Long expectedVersion = signPackage.getVersion() == null ? 0L : signPackage.getVersion();
        if (packageMapper.voidWithVersion(signPackage.getPackageId(), signPackage.getStatus(), expectedVersion,
                voidReason, SecurityUtils.getUsername()) != 1)
        {
            throw new ServiceException("签约包状态已变化，请刷新后重试");
        }
        recordEvent(signPackage.getPackageId(), null, "PACKAGE_VOIDED", "撤回员工签约包",
                null, null, null, "HR", null);
        return getPackageDetail(signPackage.getPackageId(), selectedShopDeptId);
    }

    @Override
    public List<OaSignPackage> selectMyPackages(OaSignPackage signPackage)
    {
        signPackage.setEmployeeId(SecurityUtils.getUserId());
        List<OaSignPackage> list = packageMapper.selectMyOaSignPackageList(signPackage);
        return OaSignResponseSanitizer.employeePackages(list);
    }

    @Override
    public OaSignPackage getMyPackageDetail(Long packageId)
    {
        OaSignPackage signPackage = packageAccessPolicy().employeePackage(packageId);
        attachEmployeeChildren(signPackage);
        return OaSignResponseSanitizer.employeePackage(signPackage);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OaSignPackage confirmDocumentRead(Long packageId, Long documentId,
            OaSignDocumentReadRequest readRequest)
    {
        OaSignPackage signPackage = packageAccessPolicy().employeePackage(packageId);
        READ_POLICY.validateInitialRequest(readRequest);
        String requestId = StringUtils.trim(readRequest.getRequestId());
        String requestPayload = READ_POLICY.initialEventPayload(readRequest);
        OaSignEvent existingRequest = eventMapper.selectEventByTypeAndRequestId(
                "DOCUMENT_READ_CONFIRMED", requestId);
        if (existingRequest != null)
        {
            READ_POLICY.assertSameInitialReplay(existingRequest, signPackage,
                    documentId, readRequest, SecurityUtils.getUserId());
            return getMyPackageDetail(packageId);
        }
        if (!OaSignPackageStatus.PENDING_SIGN.equals(signPackage.getStatus())
                && !OaSignPackageStatus.PART_VIEWED.equals(signPackage.getStatus()))
        {
            throw new ServiceException("当前签约包状态不能确认阅读");
        }
        assertSigningDeadlineOpen(signPackage);
        OaSignPackageDocument document = assertPackageDocument(packageId, documentId);
        assertEmployeeVisibleDocument(document);
        if (!requiresReadConfirmation(document))
        {
            throw new ServiceException("该文件无需确认阅读");
        }
        READ_POLICY.validateInitialEvidence(signPackage, document, readRequest);
        Path reviewPdfPath = documentService.resolveGeneratedSignPackageFile(
                document.getReviewPdfUrl());
        if (!sameHash(document.getReviewPdfHash(), sha256(reviewPdfPath)))
        {
            throw new ServiceException("阅读文件校验不一致，请刷新后重试");
        }
        if (isYes(document.getReadConfirmed()))
        {
            throw new ServiceException("当前版本文件已确认阅读，请刷新后查看");
        }
        try
        {
            recordEvent(packageId, documentId, "DOCUMENT_READ_CONFIRMED", requestPayload,
                    document.getReviewPdfHash(), null, null, "EMPLOYEE", requestId);
        }
        catch (DuplicateKeyException duplicate)
        {
            OaSignEvent concurrentRequest = eventMapper.selectEventByTypeAndRequestId(
                    "DOCUMENT_READ_CONFIRMED", requestId);
            if (concurrentRequest != null)
            {
                READ_POLICY.assertSameInitialReplay(concurrentRequest, signPackage,
                        documentId, readRequest, SecurityUtils.getUserId());
                return getMyPackageDetail(packageId);
            }
            throw duplicate;
        }
        OaSignPackageDocument updateDocument = new OaSignPackageDocument();
        updateDocument.setDocumentId(documentId);
        updateDocument.setReadConfirmed(YES);
        documentMapper.updateOaSignPackageDocument(updateDocument);
        Date viewedTime = new Date();
        Long expectedVersion = readRequest.getExpectedVersion();
        if (packageMapper.markViewedWithVersion(packageId, signPackage.getStatus(), expectedVersion,
                viewedTime, SecurityUtils.getUsername()) != 1)
        {
            throw new ServiceException("签约包状态已变化，请刷新后重试");
        }
        signPackage.setStatus(OaSignPackageStatus.PART_VIEWED);
        signPackage.setVersion(expectedVersion + 1);
        if (signPackage.getViewedTime() == null)
        {
            signPackage.setViewedTime(viewedTime);
        }
        syncTaskStatus(signPackage, OaSignTaskStatus.VIEWED, "EMPLOYEE_VIEWED",
                requestId, null, null);
        return getMyPackageDetail(packageId);
    }

    private void assertSigningDeadlineOpen(OaSignPackage signPackage)
    {
        DEADLINE_POLICY.assertSigningDeadlineOpen(signPackage, new Date());
    }

    private void assertStagedSignatureCapturedByDeadline(OaSignPackage signPackage,
            Date signatureCapturedTime)
    {
        DEADLINE_POLICY.assertStagedSignatureCapturedByDeadline(
                signPackage, signatureCapturedTime);
    }

    private Date resumeEmployeeDeadlineAfterCompanyStage(OaSignPackage signPackage,
            Date resumedTime)
    {
        return DEADLINE_POLICY.resumeEmployeeDeadlineAfterCompanyStage(
                signPackage, resumedTime);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OaSignPackage signPackage(Long packageId, OaSignPackageSignRequest signRequest,
            String signerIp, String signerUserAgent)
    {
        OaSignPackage signPackage = packageAccessPolicy().employeePackage(packageId);
        assertGenericEmployeeSigningAllowed(signPackage);
        SIGNING_REQUEST_POLICY.validate(signPackage, signRequest);
        byte[] signaturePng = SIGNING_REQUEST_POLICY.decodeSignaturePng(
                signRequest.getSignatureDataUrl());
        String requestPayloadHash = SIGNING_REQUEST_POLICY.payloadHash(
                signRequest, signaturePng);
        OaSignEvent existingRequest = eventMapper.selectEventByTypeAndRequestId(
                "PACKAGE_SIGN_REQUESTED", signRequest.getRequestId());
        if (existingRequest != null)
        {
            SIGNING_REQUEST_POLICY.assertSameReplay(
                    existingRequest, packageId, requestPayloadHash);
            return getMyPackageDetail(packageId);
        }
        if (OaSignPackageStatus.PENDING_COMPANY.equals(signPackage.getStatus())
                || OaSignPackageStatus.PENDING_FINAL_CONFIRM.equals(signPackage.getStatus())
                || OaSignPackageStatus.SIGNED.equals(signPackage.getStatus()))
        {
            return getMyPackageDetail(packageId);
        }
        if (!OaSignPackageStatus.PENDING_SIGN.equals(signPackage.getStatus())
                && !OaSignPackageStatus.PART_VIEWED.equals(signPackage.getStatus()))
        {
            throw new ServiceException("当前签约包状态不能签署");
        }
        assertSigningDeadlineOpen(signPackage);
        if (!Objects.equals(signPackage.getDocumentVersion(), signRequest.getDocumentVersion()))
        {
            throw new ServiceException("文档版本已更新，请重新阅读后签署");
        }

        List<OaSignPackageDocument> documents = documentMapper.selectDocumentsByPackageId(packageId);
        List<OaSignPackageDocument> employeeDocuments = documents.stream()
                .filter(this::isEmployeeVisibleDocument).toList();
        List<OaSignPackageDocument> requiredDocuments = employeeDocuments.stream()
                .filter(document -> isYes(document.getEmployeeSignRequired())).toList();
        assertSigningSequencePolicyMatches(signPackage, employeeDocuments);
        List<OaSignEvent> events = eventMapper.selectEventsByPackageId(packageId);
        boolean companyFirst = OaSignSigningSequence.COMPANY_FIRST.equals(
                signPackage.getSigningSequence());
        Map<Long, Path> reviewPdfPaths;
        if (companyFirst)
        {
            assertCompanyFirstSignaturePreservesStableBody(employeeDocuments);
            validateCompanyFirstSignCandidate(signPackage, employeeDocuments,
                    signRequest, events);
            reviewPdfPaths = validateFrozenReviewPdfsForSignatureEvidence(
                    signPackage, requiredDocuments);
        }
        else
        {
            Map<Long, String> requestedHashes = SIGNING_REQUEST_POLICY.requestedReviewHashes(
                    signRequest.getDocumentHashes(), requiredDocuments.size());
            validateRequiredReadConfirmations(signPackage, employeeDocuments, events);
            reviewPdfPaths = validateFrozenReviewPdfs(signPackage, requiredDocuments,
                    requestedHashes, events);
        }

        try
        {
            recordEvent(packageId, null, "PACKAGE_SIGN_REQUESTED",
                    "documentVersion=" + signPackage.getDocumentVersion(), requestPayloadHash,
                    signerIp, signerUserAgent, "EMPLOYEE", signRequest.getRequestId());
        }
        catch (DuplicateKeyException duplicate)
        {
            OaSignEvent concurrentRequest = eventMapper.selectEventByTypeAndRequestId(
                    "PACKAGE_SIGN_REQUESTED", signRequest.getRequestId());
            if (concurrentRequest != null)
            {
                SIGNING_REQUEST_POLICY.assertSameReplay(
                        concurrentRequest, packageId, requestPayloadHash);
                return getMyPackageDetail(packageId);
            }
            throw duplicate;
        }

        Date signedTime = new Date();
        Map<Long, SignedPdfResult> signedResults = new LinkedHashMap<>();
        for (OaSignPackageDocument document : requiredDocuments)
        {
            OaSignedPdfService.PdfImagePlacement signaturePlacement =
                    placementPolicyService.resolveSignaturePlacement(document);
            SignedPdfResult result = signedPdfService.generateSignedPdf(null, signPackage, document,
                    reviewPdfPaths.get(document.getDocumentId()), signaturePng, null, signedTime,
                    signRequest.getSignConfirmText(), signaturePlacement, null);
            signedResults.put(document.getDocumentId(), result);
        }
        fileLifecycle().registerSignedPdfRollbackCleanup(signedResults.values());
        GeneratedSignDocument certificate = null;
        try
        {
            applySignedResultSnapshot(requiredDocuments, signedResults);
            signPackage.setSignedTime(signedTime);
            String eventRootHash = eventMapper.selectLatestEventHashByPackageId(packageId);
            certificate = documentService.generateSignCertificate(null, signPackage, signRequest,
                    employeeDocuments, signerIp, signerUserAgent, resolveHrConfirmer(signPackage), eventRootHash);
            fileLifecycle().registerCertificateRollbackCleanup(certificate);

            Long expectedVersion = signPackage.getVersion() == null ? 0L : signPackage.getVersion();
            String employeeSignSourceStatus = signPackage.getStatus();
            if (OaSignSigningSequence.COMPANY_FIRST.equals(signPackage.getSigningSequence()))
            {
                signPackage.setInitialSignedTime(signedTime);
                for (OaSignPackageDocument document : requiredDocuments)
                {
                    SignedPdfResult result = signedResults.get(document.getDocumentId());
                    persistSignedDocument(document, result, certificate);
                    evidenceStore().recordSigned(
                            signPackage, document, result, certificate, signedTime);
                }
                signPackage.setFinalConfirmationStatus("PENDING");
                signPackage.setStatus(OaSignPackageStatus.PENDING_FINAL_CONFIRM);
                signPackage.setUpdateBy(SecurityUtils.getUsername());
                if (packageMapper.updateFinalizedPackage(signPackage,
                        employeeSignSourceStatus, expectedVersion) != 1)
                {
                    throw new ServiceException("签约状态已变化，请刷新后查看结果");
                }
                signPackage.setVersion(expectedVersion + 1);
                recordEvent(packageId, null, "EMPLOYEE_INITIAL_SIGNED",
                        "员工已对发送前冻结的当前最终合同版本完成签名",
                        certificate.getSha256(), signerIp, signerUserAgent,
                        "EMPLOYEE", signRequest.getRequestId());
                List<OaSignPackageDocument> finalDocuments = documentMapper
                        .selectDocumentsByPackageId(packageId).stream()
                        .filter(this::isEmployeeVisibleDocument).toList();
                Map<Long, String> verifiedHashes =
                        fileIntegrity().validateFinalDocuments(
                                signPackage, finalDocuments);
                validateFinalDocumentsOpened(signPackage, finalDocuments,
                        eventMapper.selectEventsByPackageId(packageId));
                String actualRootHash =
                        fileIntegrity().finalDocumentRootHashFromHashes(
                                verifiedHashes);
                if (!sameHash(actualRootHash, signPackage.getFinalDocumentRootHash())
                        || !sameHash(actualRootHash,
                                signRequest.getFinalDocumentRootHash()))
                {
                    throw new ServiceException("最终合同文件集合校验不一致，请联系经办人");
                }
                lockRenewalGuardBeforePackageTerminal(signPackage,
                        OaSignTaskStatus.SIGNED);
                // 先在同一事务内经过合法的任务状态边，再立即完成确认；不向员工
                // 发送一个已经无需处理的“待最终确认”通知。
                syncTaskStatus(signPackage, OaSignTaskStatus.PENDING_FINAL_CONFIRM,
                        "EMPLOYEE_SIGNED_FROZEN_COMPANY_CONTRACT",
                        taskStepRequestId(signRequest.getRequestId(),
                                OaSignTaskStatus.PENDING_FINAL_CONFIRM),
                        signerIp, signerUserAgent, false);
                persistFinalConfirmation(signPackage, finalDocuments, actualRootHash,
                        signRequest.getSignConfirmText(), signRequest.getRequestId(),
                        signerIp, signerUserAgent, expectedVersion + 1,
                        "员工已一次完成手写签名和最终合同确认");
                syncTaskStatus(signPackage, OaSignTaskStatus.SIGNED,
                        "FINAL_CONTRACT_CONFIRMED",
                        taskStepRequestId(signRequest.getRequestId(),
                                OaSignTaskStatus.SIGNED),
                        signerIp, signerUserAgent);
                return getMyPackageDetail(packageId);
            }
            if (packageMapper.updateStatusWithVersion(packageId, signPackage.getStatus(),
                    OaSignPackageStatus.PENDING_COMPANY,
                    expectedVersion, signedTime, SecurityUtils.getUsername()) != 1)
            {
                throw new ServiceException("签署状态已变化，请刷新后查看结果");
            }
            OaSignPackage signedTimeUpdate = new OaSignPackage();
            signedTimeUpdate.setPackageId(packageId);
            signedTimeUpdate.setSignedTime(signedTime);
            signedTimeUpdate.setInitialSignedTime(signedTime);
            signedTimeUpdate.setFinalConfirmationStatus("WAITING_COMPANY");
            signedTimeUpdate.setUpdateBy(SecurityUtils.getUsername());
            packageMapper.updateOaSignPackage(signedTimeUpdate);

            for (OaSignPackageDocument document : requiredDocuments)
            {
                SignedPdfResult result = signedResults.get(document.getDocumentId());
                persistSignedDocument(document, result, certificate);
                evidenceStore().recordSigned(
                        signPackage, document, result, certificate, signedTime);
            }
            recordEvent(packageId, null, "EMPLOYEE_INITIAL_SIGNED", "员工已完成首次签名，等待补充公司与印章",
                    certificate.getSha256(), signerIp, signerUserAgent, "EMPLOYEE", signRequest.getRequestId());
            syncTaskStatus(signPackage, OaSignTaskStatus.PENDING_COMPANY, "EMPLOYEE_INITIAL_SIGNED",
                    signRequest.getRequestId(), signerIp, signerUserAgent);
            return getMyPackageDetail(packageId);
        }
        catch (RuntimeException | Error failure)
        {
            fileLifecycle().discardGeneratedDocument(certificate, failure);
            fileLifecycle().discardSignedPdfResults(signedResults.values(), failure);
            throw failure;
        }
    }

    private void assertGenericEmployeeSigningAllowed(OaSignPackage signPackage)
    {
        if (signPackage == null
                || !OaSignSigningSequence.SIGNATURE_FIRST.equals(
                        signPackage.getSigningSequence())
                || signPackage.getTaskId() == null)
        {
            return;
        }
        OaSignTask task = taskMapper.selectOaSignTaskById(signPackage.getTaskId());
        if (task != null && OaOnboardSignEventFactory.SOURCE_TYPE
                .equalsIgnoreCase(task.getSourceType()))
        {
            throw new ServiceException(
                    "Excel入职签约包请在合同资料补全入口完成事实确认与唯一一次签名");
        }
    }

    @Override
    public OaSignCompanyOptions getCompanyOptions(Long packageId, Long selectedShopDeptId)
    {
        signHrAccessService.requireCurrentHr();
        OaSignPackage signPackage = packageAccessPolicy().hrScopedPackage(
                packageId, selectedShopDeptId);
        if (OaSignSigningSequence.COMPANY_FIRST.equals(signPackage.getSigningSequence()))
        {
            throw new ServiceException("公司先行流程已冻结公司与印章，不得重新选择");
        }
        if (!OaSignPackageStatus.PENDING_COMPANY.equals(signPackage.getStatus())
                && !OaSignPackageStatus.PENDING_FINAL_CONFIRM.equals(signPackage.getStatus()))
        {
            throw new ServiceException("员工完成首次签名后才能选择公司和印章");
        }
        List<OaSignPackageDocument> documents = documentMapper.selectDocumentsByPackageId(packageId);
        List<OaSignPackageDocument> visibleDocuments = documents == null ? List.of()
                : documents.stream().filter(this::isEmployeeVisibleDocument).toList();
        if (visibleDocuments.isEmpty())
        {
            throw new ServiceException("签约包没有员工可见文件");
        }
        OaSignCompanyOptions options = signCompanyService.options(signPackage);
        options.setCompanySealRequired(visibleDocuments.stream()
                .anyMatch(placementPolicyService::requiresCompanySeal));
        options.setRecommendedLegalEntityName(signPackage.getRecommendedCompanySnapshot());
        options.setRecommendedLegalRepresentative(
                signPackage.getRecommendedLegalRepresentativeSnapshot());
        options.setRecommendedRegisteredAddress(
                signPackage.getRecommendedRegisteredAddressSnapshot());
        return options;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OaSignPackage finalizePackage(Long packageId, OaSignPackageFinalizeRequest request,
            Long selectedShopDeptId)
    {
        signHrAccessService.requireCurrentHr();
        if (request == null)
        {
            throw new ServiceException("公司与印章信息不能为空");
        }
        OaSignPackage signPackage = packageAccessPolicy().hrScopedPackage(
                packageId, selectedShopDeptId);
        validateFinalizeSigningSequence(signPackage, request);
        String requestId = StringUtils.trim(request.getRequestId());
        String requestPayloadHash = null;
        if (StringUtils.isNotBlank(requestId))
        {
            validateFinalizeWriteRequest(request, requestId);
            requestPayloadHash = finalizeRequestPayloadHash(packageId, request);
            OaSignEvent requestClaim = eventMapper.selectEventByTypeAndRequestId(
                    FINALIZE_REQUEST_EVENT, requestId);
            if (requestClaim != null)
            {
                assertSameFinalizeRequestReplay(requestClaim, packageId, requestPayloadHash);
                assertExistingFinalizationReplay(packageId, signPackage, request);
                return getPackageDetail(packageId, selectedShopDeptId);
            }
            // Compatibility for requests completed immediately before the durable request
            // claim event was introduced. New executions always persist FINALIZE_REQUEST_EVENT.
            OaSignEvent repeated = eventMapper.selectEventByTypeAndRequestId(
                    "FINAL_CONTRACT_GENERATED", requestId);
            if (repeated != null)
            {
                if (!Objects.equals(repeated.getPackageId(), packageId))
                {
                    throw new ServiceException("最终合同生成请求编号已被其他签约包使用");
                }
                assertExistingFinalizationReplay(packageId, signPackage, request);
                return getPackageDetail(packageId, selectedShopDeptId);
            }
        }
        if (OaSignPackageStatus.PENDING_FINAL_CONFIRM.equals(signPackage.getStatus()))
        {
            assertExistingFinalizationReplay(packageId, signPackage, request);
            validateFinalizeWriteRequest(request, requestId);
            throw new ServiceException("最终合同已由其他请求生成，不能复用当前请求编号");
        }
        if (isPreparedFinalCandidate(signPackage))
        {
            assertExistingFinalizationReplay(packageId, signPackage, request);
            validateFinalizeWriteRequest(request, requestId);
            throw new ServiceException("最终合同已生成但尚未发送，不能重复生成");
        }
        if (!OaSignPackageStatus.PENDING_COMPANY.equals(signPackage.getStatus()))
        {
            throw new ServiceException("当前签约包不能补充公司与印章");
        }
        if (request.getExpectedVersion() != null
                && !Objects.equals(request.getExpectedVersion(), signPackage.getVersion()))
        {
            throw new ServiceException("签约包版本已变化，请重新预览");
        }
        validateFinalizeTask(packageId, signPackage, request,
                OaSignTaskStatus.PENDING_COMPANY, true);
        OaLegalEntityCandidate candidate = signCompanyService.resolveCandidate(signPackage);
        Long legalEntityId = request.getLegalEntityId() != null ? request.getLegalEntityId()
                : candidate == null ? null : candidate.getLegalEntityId();
        if (legalEntityId == null)
        {
            throw new ServiceException("签约部门尚未绑定公司，请先维护部门公司或手工选择公司");
        }
        boolean manuallyChanged = candidate == null || !legalEntityId.equals(candidate.getLegalEntityId());
        String correctionReason = StringUtils.trim(request.getCorrectionReason());
        if (manuallyChanged && StringUtils.isBlank(correctionReason))
        {
            correctionReason = candidate == null ? "签约部门尚未配置公司，人工选择"
                    : "人工更正部门自动识别结果";
        }
        SysLegalEntity legalEntity = signCompanyService.requireContractReadyEntity(legalEntityId);
        validateFinalizeWriteRequest(request, requestId);
        requestPayloadHash = finalizeRequestPayloadHash(packageId, request);
        recordEvent(packageId, null, FINALIZE_REQUEST_EVENT,
                "已受理补充公司与印章的最终合同生成请求",
                requestPayloadHash, null, null, "HR", requestId);
        List<OaSignPackageDocument> documents = documentMapper.selectDocumentsByPackageId(packageId);
        List<OaSignPackageDocument> visibleDocuments = documents == null ? List.of()
                : documents.stream().filter(this::isEmployeeVisibleDocument).toList();
        if (visibleDocuments.isEmpty())
        {
            throw new ServiceException("签约包没有员工可见文件");
        }
        boolean anySignatureRequired = visibleDocuments.stream()
                .anyMatch(placementPolicyService::requiresEmployeeSignature);
        boolean anySealRequired = visibleDocuments.stream()
                .anyMatch(placementPolicyService::requiresCompanySeal);
        byte[] signatureBytes = null;
        if (anySignatureRequired)
        {
            OaSignPackageDocument signatureSource = visibleDocuments.stream()
                    .filter(placementPolicyService::requiresEmployeeSignature)
                    .filter(document -> isYes(document.getSigned()))
                    .filter(document -> StringUtils.isNotBlank(document.getSignatureFileUrl()))
                    .findFirst().orElseThrow(() -> new ServiceException("员工首次签名记录不完整"));
            signatureBytes =
                    fileIntegrity().readAndValidateInitialSignature(signatureSource);
        }
        OaCompanySealConfig seal = null;
        byte[] sealBytes = null;
        if (anySealRequired)
        {
            seal = signCompanyService.requireActiveSeal(request.getSealId(), legalEntityId);
            sealBytes = documentService.readConfiguredFileBytes(seal.getSealImageUrl());
        }
        else if (request.getSealId() != null)
        {
            throw new ServiceException("当前包文件策略不需要企业章");
        }

        applyCompanySnapshot(signPackage, legalEntity, candidate, seal, manuallyChanged, correctionReason);
        String initialVersion = signPackage.getDocumentVersion();
        String finalVersion = nextDocumentVersion(signPackage);
        signPackage.setDocumentVersion(finalVersion);
        Date generatedTime = new Date();
        Map<Long, GeneratedSignDocument> generatedDocuments = new LinkedHashMap<>();
        Map<Long, SignedPdfResult> finalResults = new LinkedHashMap<>();
        List<String> actualPackageTemplateTypes = visibleDocuments.stream()
                .map(OaSignPackageDocument::getTemplateType)
                .filter(StringUtils::isNotBlank)
                .toList();
        StagedSignFile sealArchive = null;
        try
        {
            if (seal != null)
            {
                sealArchive = fileStorageService.promote(fileStorageService.stage(signPackage.getTaskId(),
                        signPackage.getPackageId(), finalVersion,
                        OaSignImageValidator.companySealArchiveFilename(
                                seal.getSealId(), sealBytes), sealBytes));
                fileLifecycle().registerArchivedEvidenceRollbackCleanup(sealArchive);
            }
            for (OaSignPackageDocument document : visibleDocuments)
            {
                OaSignTemplate template = snapshotTemplate(document);
                documentService.assertTemplateLegalEntityCompatible(template.getTemplateType(),
                        template.getFileUrl(), legalEntity.getLegalEntityName());
                GeneratedSignDocument generated = documentService.renderPackageDocument(
                        signPackage, template, actualPackageTemplateTypes);
                fileLifecycle().registerGeneratedDocumentRollbackCleanup(generated);
                generatedDocuments.put(document.getDocumentId(), generated);

                OaSignPackageDocument finalDocument = finalDocumentSnapshot(document, generated, finalVersion);
                Path finalReviewPdf = documentService.resolveGeneratedSignPackageFile(
                        generated.getReviewPdfUrl());
                SignedPdfResult result = generateFinalDocumentPdf(signPackage, document,
                        finalDocument, finalReviewPdf, signatureBytes, sealBytes, generatedTime);
                finalResults.put(document.getDocumentId(), result);
            }
            fileLifecycle().registerSignedPdfRollbackCleanup(finalResults.values());
            for (OaSignPackageDocument document : visibleDocuments)
            {
                GeneratedSignDocument generated = generatedDocuments.get(document.getDocumentId());
                SignedPdfResult result = finalResults.get(document.getDocumentId());
                OaSignPackageDocument updateDocument = new OaSignPackageDocument();
                updateDocument.setDocumentId(document.getDocumentId());
                updateDocument.setFinalPdfUrl(result.getSignedPdfUrl());
                updateDocument.setFinalPdfHash(result.getSignedPdfHash());
                updateDocument.setFinalContentHash(result.getContentPdfHash());
                updateDocument.setFinalDocumentVersion(finalVersion);
                updateDocument.setFinalReadConfirmed(NO);
                documentMapper.updateOaSignPackageDocument(updateDocument);
                evidenceStore().recordFinalCandidate(
                        signPackage, document, generated, result,
                        sealArchive, generatedTime);
            }
            String rootHash = fileIntegrity().finalDocumentRootHash(finalResults);
            signPackage.setDocumentVersion(initialVersion);
            signPackage.setFinalDocumentVersion(finalVersion);
            signPackage.setFinalDocumentRootHash(rootHash);
            signPackage.setFinalGeneratedTime(generatedTime);
            signPackage.setFinalConfirmationStatus("PREPARED_NOT_SENT");
            signPackage.setCompanyFrozenTime(generatedTime);
            signPackage.setStatus(OaSignPackageStatus.PENDING_COMPANY);
            signPackage.setUpdateBy(SecurityUtils.getUsername());
            Long expectedVersion = signPackage.getVersion() == null ? 0L : signPackage.getVersion();
            if (packageMapper.updateFinalizedPackage(signPackage, OaSignPackageStatus.PENDING_COMPANY,
                    expectedVersion) != 1)
            {
                throw new ServiceException("签约包状态已变化，请刷新后重试");
            }
            recordEvent(packageId, null, "FINAL_CONTRACT_PREPARED",
                    "已补充公司与印章并生成最终合同，尚未发送员工确认", rootHash,
                    null, null, "HR", requestId);
            return getPackageDetail(packageId, selectedShopDeptId);
        }
        catch (RuntimeException | Error failure)
        {
            fileLifecycle().discardSignedPdfResults(finalResults.values(), failure);
            throw failure;
        }
        finally
        {
            fileStorageService.cleanup(sealArchive);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OaSignPackage confirmFinalPackage(Long packageId, OaSignFinalConfirmRequest request,
            String confirmerIp, String confirmerUserAgent)
    {
        OaSignPackage signPackage = packageAccessPolicy().employeePackage(packageId);
        validateFinalConfirmationRequest(request);
        OaSignFinalConfirmation repeated = finalConfirmationMapper.selectByRequestId(request.getRequestId());
        if (repeated != null)
        {
            assertSameFinalConfirmationReplay(repeated, signPackage, request);
            return getMyPackageDetail(packageId);
        }
        if (OaSignPackageStatus.SIGNED.equals(signPackage.getStatus()))
        {
            return getMyPackageDetail(packageId);
        }
        if (!OaSignPackageStatus.PENDING_FINAL_CONFIRM.equals(signPackage.getStatus()))
        {
            throw new ServiceException("最终合同尚未生成，暂不能确认");
        }
        assertSigningDeadlineOpen(signPackage);
        if (!Objects.equals(signPackage.getFinalDocumentVersion(), request.getFinalDocumentVersion())
                || !sameHash(signPackage.getFinalDocumentRootHash(), request.getDocumentRootHash()))
        {
            throw new ServiceException("最终合同版本已变化，请重新打开并确认");
        }
        lockRenewalGuardBeforePackageTerminal(signPackage, OaSignTaskStatus.SIGNED);
        List<OaSignPackageDocument> documents = documentMapper.selectDocumentsByPackageId(packageId).stream()
                .filter(this::isEmployeeVisibleDocument).toList();
        Map<Long, String> verifiedHashes =
                fileIntegrity().validateFinalDocuments(signPackage, documents);
        validateFinalDocumentsOpened(signPackage, documents,
                eventMapper.selectEventsByPackageId(packageId));
        String actualRootHash =
                fileIntegrity().finalDocumentRootHashFromHashes(verifiedHashes);
        if (!sameHash(actualRootHash, signPackage.getFinalDocumentRootHash()))
        {
            throw new ServiceException("最终合同文件集合校验不一致，请联系经办人");
        }
        Long expectedVersion = signPackage.getVersion() == null ? 0L : signPackage.getVersion();
        persistFinalConfirmation(signPackage, documents, actualRootHash,
                request.getConfirmationText(), request.getRequestId(), confirmerIp,
                confirmerUserAgent, expectedVersion,
                "员工已阅读并确认最终合同，无需再次签名");
        syncTaskStatus(signPackage, OaSignTaskStatus.SIGNED, "FINAL_CONTRACT_CONFIRMED",
                request.getRequestId(), confirmerIp, confirmerUserAgent);
        return getMyPackageDetail(packageId);
    }

    private void persistFinalConfirmation(OaSignPackage signPackage,
            List<OaSignPackageDocument> documents, String actualRootHash,
            String confirmationText, String requestId, String confirmerIp,
            String confirmerUserAgent, Long expectedVersion, String eventPayload)
    {
        Long packageId = signPackage.getPackageId();
        // MySQL DATETIME has second precision in this schema.  Truncate once so
        // the persisted confirmation, PDF evidence text and event payload use
        // the exact same audit timestamp instead of JDBC rounding milliseconds.
        Date confirmedTime = new Date((System.currentTimeMillis() / 1000L) * 1000L);
        Map<Long, SignedPdfResult> archiveResults = generateFinalArchives(signPackage,
                documents, confirmedTime, actualRootHash);
        fileLifecycle().registerSignedPdfRollbackCleanup(archiveResults.values());
        String archiveRootHash =
                fileIntegrity().archiveDocumentRootHash(archiveResults);
        OaSignFinalConfirmation confirmation = new OaSignFinalConfirmation();
        confirmation.setPackageId(packageId);
        confirmation.setEmployeeId(SecurityUtils.getUserId());
        confirmation.setFinalDocumentVersion(signPackage.getFinalDocumentVersion());
        confirmation.setDocumentRootHash(actualRootHash);
        confirmation.setConfirmationText(confirmationText);
        confirmation.setIdentityMethod("LOGIN_TOKEN");
        confirmation.setRequestId(requestId);
        confirmation.setIpAddress(confirmerIp);
        confirmation.setUserAgent(confirmerUserAgent);
        confirmation.setConfirmedTime(confirmedTime);
        if (finalConfirmationMapper.insertConfirmation(confirmation) != 1)
        {
            throw new ServiceException("最终合同确认保存失败");
        }
        for (OaSignPackageDocument document : documents)
        {
            OaSignFinalConfirmationDocument confirmationDocument =
                    new OaSignFinalConfirmationDocument();
            confirmationDocument.setConfirmationId(confirmation.getConfirmationId());
            confirmationDocument.setPackageId(packageId);
            confirmationDocument.setDocumentId(document.getDocumentId());
            confirmationDocument.setFinalDocumentVersion(
                    signPackage.getFinalDocumentVersion());
            confirmationDocument.setFinalPdfHash(document.getFinalPdfHash());
            if (finalConfirmationMapper.insertConfirmationDocument(
                    confirmationDocument) != 1)
            {
                throw new ServiceException("最终合同逐文件确认保存失败");
            }
            SignedPdfResult archive = archiveResults.get(document.getDocumentId());
            OaSignPackageDocument updateDocument = new OaSignPackageDocument();
            updateDocument.setDocumentId(document.getDocumentId());
            updateDocument.setFinalArchivePdfUrl(archive.getSignedPdfUrl());
            updateDocument.setFinalArchivePdfHash(archive.getSignedPdfHash());
            updateDocument.setSigned(YES);
            updateDocument.setStatus(OaSignPackageStatus.SIGNED);
            if (documentMapper.updateOaSignPackageDocument(updateDocument) != 1)
            {
                throw new ServiceException("最终归档文档状态保存失败");
            }
            evidenceStore().recordFinalArchive(
                    signPackage, document, archive, confirmedTime);
        }
        if (packageMapper.updateFinalConfirmed(packageId,
                OaSignPackageStatus.PENDING_FINAL_CONFIRM, expectedVersion,
                confirmedTime, archiveRootHash, confirmedTime,
                SecurityUtils.getUsername()) != 1)
        {
            throw new ServiceException("最终合同确认状态已变化，请刷新后查看");
        }
        signPackage.setStatus(OaSignPackageStatus.SIGNED);
        signPackage.setFinalConfirmationStatus("CONFIRMED");
        signPackage.setFinalConfirmedTime(confirmedTime);
        signPackage.setFinalArchiveRootHash(archiveRootHash);
        signPackage.setFinalEvidenceGeneratedTime(confirmedTime);
        signPackage.setVersion(expectedVersion + 1);
        recordEvent(packageId, null, "FINAL_CONTRACT_CONFIRMED", eventPayload,
                actualRootHash, confirmerIp, confirmerUserAgent, "EMPLOYEE", requestId,
                confirmedTime);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OaSignPackage confirmFinalDocumentRead(Long packageId, Long documentId,
            OaSignFinalDocumentReadRequest request, String confirmerIp,
            String confirmerUserAgent)
    {
        OaSignPackage signPackage = packageAccessPolicy().employeePackage(packageId);
        READ_POLICY.validateFinalRequest(request);
        OaSignEvent repeated = eventMapper.selectEventByTypeAndRequestId(
                "FINAL_DOCUMENT_OPENED", request.getRequestId());
        if (repeated != null)
        {
            READ_POLICY.assertSameFinalReplay(
                    repeated, signPackage, documentId, request);
            return getMyPackageDetail(packageId);
        }
        boolean companyFirstPendingSign = OaSignSigningSequence.COMPANY_FIRST.equals(
                signPackage.getSigningSequence())
                && (OaSignPackageStatus.PENDING_SIGN.equals(signPackage.getStatus())
                        || OaSignPackageStatus.PART_VIEWED.equals(signPackage.getStatus()));
        if (!OaSignPackageStatus.PENDING_FINAL_CONFIRM.equals(signPackage.getStatus())
                && !companyFirstPendingSign)
        {
            if (OaSignPackageStatus.SIGNED.equals(signPackage.getStatus()))
            {
                return getMyPackageDetail(packageId);
            }
            throw new ServiceException("当前签约包无需确认最终合同");
        }
        assertSigningDeadlineOpen(signPackage);
        OaSignPackageDocument document = assertPackageDocument(packageId, documentId);
        assertEmployeeVisibleDocument(document);
        READ_POLICY.validateFinalEvidence(signPackage, document, request);
        Path path = documentService.resolveGeneratedSignPackageFile(
                document.getFinalPdfUrl());
        if (!sameHash(document.getFinalPdfHash(), sha256(path)))
        {
            throw new ServiceException("最终合同文件校验不一致，请联系经办人");
        }
        if (isYes(document.getFinalReadConfirmed())
                && document.getFinalReadConfirmedTime() != null)
        {
            return getMyPackageDetail(packageId);
        }
        Date openedTime = new Date();
        try
        {
            recordEvent(packageId, documentId, "FINAL_DOCUMENT_OPENED",
                    READ_POLICY.finalEventPayload(signPackage, document),
                    document.getFinalPdfHash(),
                    confirmerIp, confirmerUserAgent, "EMPLOYEE", request.getRequestId());
        }
        catch (DuplicateKeyException duplicate)
        {
            OaSignEvent concurrent = eventMapper.selectEventByTypeAndRequestId(
                    "FINAL_DOCUMENT_OPENED", request.getRequestId());
            if (concurrent != null)
            {
                READ_POLICY.assertSameFinalReplay(
                        concurrent, signPackage, documentId, request);
                return getMyPackageDetail(packageId);
            }
            throw duplicate;
        }
        if (documentMapper.markFinalReadConfirmed(documentId, packageId,
                signPackage.getFinalDocumentVersion(), document.getFinalPdfHash(), openedTime) != 1)
        {
            throw new ServiceException("最终合同版本已变化，请重新打开");
        }
        return getMyPackageDetail(packageId);
    }

    private void validateFinalDocumentsOpened(OaSignPackage signPackage,
            List<OaSignPackageDocument> documents, List<OaSignEvent> events)
    {
        for (OaSignPackageDocument document : documents)
        {
            if (!isYes(document.getFinalReadConfirmed())
                    || document.getFinalReadConfirmedTime() == null
                    || !READ_POLICY.hasFinalReadEvent(
                            signPackage, document, events))
            {
                throw new ServiceException("请先逐份打开并确认当前版本的最终合同");
            }
        }
    }

    private void assertSameFinalConfirmationReplay(OaSignFinalConfirmation existing,
            OaSignPackage signPackage, OaSignFinalConfirmRequest request)
    {
        FINALIZATION_POLICY.assertSameFinalConfirmationReplay(
                existing, signPackage, request);
    }

    private void validateFinalConfirmationRequest(OaSignFinalConfirmRequest request)
    {
        FINALIZATION_POLICY.validateFinalConfirmationRequest(request);
    }

    private void validateFinalizeSigningSequence(OaSignPackage signPackage,
            OaSignPackageFinalizeRequest request)
    {
        FINALIZATION_POLICY.validateFinalizeSigningSequence(signPackage, request);
    }

    private void validateFinalizeWriteRequest(OaSignPackageFinalizeRequest request,
            String requestId)
    {
        FINALIZATION_POLICY.validateFinalizeWriteRequest(request, requestId);
    }

    private String finalizeRequestPayloadHash(Long packageId,
            OaSignPackageFinalizeRequest request)
    {
        return FINALIZATION_POLICY.finalizeRequestPayloadHash(packageId, request);
    }

    private void assertSameFinalizeRequestReplay(OaSignEvent existingRequest,
            Long packageId, String requestPayloadHash)
    {
        FINALIZATION_POLICY.assertSameFinalizeRequestReplay(existingRequest,
                packageId, requestPayloadHash, SecurityUtils.getUserId());
    }

    /**
     * Replays may return the frozen result only after re-checking authorization, task binding,
     * signing sequence and the exact company/seal decision. Expected versions intentionally are
     * not re-checked here because a successful first execution advances both versions.
     */
    private void assertExistingFinalizationReplay(Long packageId, OaSignPackage signPackage,
            OaSignPackageFinalizeRequest request)
    {
        OaSignTaskStatus expectedTaskStatus =
                FINALIZATION_POLICY.existingReplayTaskStatus(signPackage);
        validateFinalizeTask(packageId, signPackage, request, expectedTaskStatus, false);
        FINALIZATION_POLICY.validateExistingReplayDecision(signPackage, request);
    }

    private boolean isPreparedFinalCandidate(OaSignPackage signPackage)
    {
        return FINALIZATION_POLICY.isPreparedFinalCandidate(signPackage);
    }

    private OaSignTask validateFinalizeTask(Long packageId, OaSignPackage signPackage,
            OaSignPackageFinalizeRequest request, OaSignTaskStatus expectedStatus,
            boolean validateExpectedVersion)
    {
        if (signPackage.getTaskId() == null && request.getExpectedTaskVersion() == null)
        {
            return null;
        }
        OaSignTask task = signPackage.getTaskId() == null ? null
                : taskMapper.lockOaSignTaskById(signPackage.getTaskId());
        if (task == null || !Objects.equals(task.getPackageId(), packageId)
                || !Objects.equals(task.getTaskId(), signPackage.getTaskId())
                || !Objects.equals(task.getEmployeeId(), signPackage.getEmployeeId())
                || !Objects.equals(task.getShopDeptId(), signPackage.getShopDeptId()))
        {
            throw new ServiceException("签约任务与签约包绑定不一致");
        }
        if (!expectedStatus.name().equals(task.getStatus()))
        {
            throw new ServiceException("签约任务状态已变化，请重新预览");
        }
        if (validateExpectedVersion
                && !Objects.equals(request.getExpectedTaskVersion(), task.getVersion()))
        {
            throw new ServiceException("签约任务版本已变化，请重新预览");
        }
        return task;
    }

    private void applyCompanySnapshot(OaSignPackage signPackage, SysLegalEntity legalEntity,
            OaLegalEntityCandidate candidate, OaCompanySealConfig seal,
            boolean manuallyChanged, String correctionReason)
    {
        signPackage.setLegalEntityIdSnapshot(legalEntity.getLegalEntityId());
        signPackage.setLegalEntityCodeSnapshot(legalEntity.getLegalEntityCode());
        signPackage.setLegalEntityNameSnapshot(legalEntity.getLegalEntityName());
        signPackage.setLegalEntityCreditCodeSnapshot(legalEntity.getUnifiedSocialCreditCode());
        signPackage.setLegalEntityAddressSnapshot(legalEntity.getRegisteredAddress());
        signPackage.setLegalRepresentativeSnapshot(legalEntity.getLegalRepresentative());
        signPackage.setLegalEntityPhoneSnapshot(legalEntity.getContactPhone());
        signPackage.setLegalEntitySourceDeptId(candidate == null ? signPackage.getDeptIdSnapshot()
                : candidate.getSourceDeptId());
        signPackage.setLegalEntityResolveMode(manuallyChanged ? "MANUAL" : "AUTO");
        signPackage.setLegalEntityOverrideReason(manuallyChanged ? correctionReason : null);
        signPackage.setSealIdSnapshot(seal == null ? null : seal.getSealId());
        signPackage.setSealNameSnapshot(seal == null ? null : seal.getSealName());
        signPackage.setSealImageUrlSnapshot(seal == null ? null : seal.getSealImageUrl());
        signPackage.setSealImageHashSnapshot(seal == null ? null : seal.getSealImageHash());
    }

    private OaSignTemplate snapshotTemplate(OaSignPackageDocument document)
    {
        if (StringUtils.isBlank(document.getSourceFileUrlSnapshot()))
        {
            throw new ServiceException("模板快照文件不存在，不能生成最终合同");
        }
        OaSignTemplate template = new OaSignTemplate();
        template.setTemplateId(document.getTemplateId());
        template.setTemplateType(document.getTemplateType());
        template.setTemplateName(document.getDocumentName());
        template.setTemplateVersion(document.getTemplateVersionSnapshot());
        template.setFileUrl(document.getSourceFileUrlSnapshot());
        template.setEmployeeVisible(document.getEmployeeVisible());
        template.setReadConfirmationRequired(document.getReadConfirmationRequired());
        template.setEmployeeSignRequired(document.getEmployeeSignRequired());
        template.setSignaturePositionJson(document.getSignaturePositionJson());
        template.setCompanySealPositionJson(document.getCompanySealPositionJson());
        template.setCompanySealRequired(document.getCompanySealRequired());
        return template;
    }

    private OaSignPackageDocument finalDocumentSnapshot(OaSignPackageDocument source,
            GeneratedSignDocument generated, String finalVersion)
    {
        OaSignPackageDocument document = new OaSignPackageDocument();
        document.setDocumentId(source.getDocumentId());
        document.setPackageId(source.getPackageId());
        document.setDocumentName(source.getDocumentName());
        document.setReviewPdfUrl(generated.getReviewPdfUrl());
        document.setReviewPdfHash(generated.getReviewPdfHash());
        document.setDocumentVersion(finalVersion);
        document.setEmployeeVisible(source.getEmployeeVisible());
        document.setReadConfirmationRequired(source.getReadConfirmationRequired());
        document.setEmployeeSignRequired(source.getEmployeeSignRequired());
        document.setSignaturePositionJson(source.getSignaturePositionJson());
        document.setCompanySealPositionJson(source.getCompanySealPositionJson());
        document.setCompanySealRequired(source.getCompanySealRequired());
        document.setDocumentPolicyMode(source.getDocumentPolicyMode());
        return document;
    }

    private String finalGenerationText(boolean signatureRequired, boolean sealRequired)
    {
        if (signatureRequired && sealRequired)
        {
            return FINAL_GENERATION_TEXT;
        }
        return signatureRequired ? "沿用员工首次签名生成最终合同"
                : "按冻结策略加盖公司印章生成最终合同";
    }

    private SignedPdfResult generateFinalDocumentPdf(OaSignPackage signPackage,
            OaSignPackageDocument sourceDocument, OaSignPackageDocument finalDocument,
            Path finalReviewPdf, byte[] signatureBytes, byte[] sealBytes, Date finalGeneratedTime)
    {
        placementPolicyService.assertSigningSequenceMatches(signPackage, sourceDocument);
        boolean signatureRequired = placementPolicyService.requiresEmployeeSignature(sourceDocument);
        boolean sealRequired = placementPolicyService.requiresCompanySeal(sourceDocument);
        if (!signatureRequired && !sealRequired)
        {
            return signedPdfService.generatePendingFinalPdfWithoutMarks(null, signPackage,
                    finalDocument, finalReviewPdf);
        }
        if (signatureRequired && (signatureBytes == null || signatureBytes.length == 0))
        {
            throw new ServiceException("最终合同缺少员工签名证据");
        }
        if (sealRequired && (sealBytes == null || sealBytes.length == 0))
        {
            throw new ServiceException("最终合同缺少企业章证据");
        }
        Date employeeSignedTime = employeeSignatureTime(signPackage);
        if (signatureRequired && employeeSignedTime == null)
        {
            throw new ServiceException("最终合同缺少员工签名时间");
        }
        if (sealRequired && finalGeneratedTime == null)
        {
            throw new ServiceException("最终合同缺少公司盖章时间");
        }
        return signedPdfService.generatePendingFinalPdf(null, signPackage, finalDocument,
                finalReviewPdf, signatureRequired ? signatureBytes : null,
                sealRequired ? sealBytes : null,
                signatureRequired ? employeeSignedTime : null,
                sealRequired ? finalGeneratedTime : null,
                finalGenerationText(signatureRequired, sealRequired),
                signatureRequired
                        ? placementPolicyService.resolveSignaturePlacement(sourceDocument) : null,
                sealRequired
                        ? placementPolicyService.resolveCompanySealPlacement(sourceDocument) : null);
    }

    private Date employeeSignatureTime(OaSignPackage signPackage)
    {
        if (signPackage.getSignatureSampleTime() != null)
        {
            return signPackage.getSignatureSampleTime();
        }
        return signPackage.getInitialSignedTime() == null
                ? signPackage.getSignedTime() : signPackage.getInitialSignedTime();
    }

    private PreparedFinalCandidate generateAndPersistFinalCandidate(
            OaSignPackage signPackage, List<OaSignPackageDocument> visibleDocuments,
            byte[] signatureBytes, Date generatedTime)
    {
        if (visibleDocuments == null || visibleDocuments.isEmpty())
        {
            throw new ServiceException("签约包没有员工可见文件");
        }
        boolean sealRequired = visibleDocuments.stream()
                .anyMatch(placementPolicyService::requiresCompanySeal);
        FrozenCompanyContext company = requireFrozenCompanyContext(signPackage, sealRequired);
        String initialVersion = signPackage.getDocumentVersion();
        String finalVersion = nextDocumentVersion(signPackage);
        signPackage.setDocumentVersion(finalVersion);
        Map<Long, GeneratedSignDocument> generatedDocuments = new LinkedHashMap<>();
        Map<Long, SignedPdfResult> finalResults = new LinkedHashMap<>();
        List<String> actualPackageTemplateTypes = visibleDocuments.stream()
                .map(OaSignPackageDocument::getTemplateType)
                .filter(StringUtils::isNotBlank)
                .toList();
        StagedSignFile sealArchive = null;
        try
        {
            if (company.seal() != null && sealRequired)
            {
                sealArchive = fileStorageService.promote(fileStorageService.stage(
                        signPackage.getTaskId(), signPackage.getPackageId(), finalVersion,
                        OaSignImageValidator.companySealArchiveFilename(
                                company.seal().getSealId(), company.sealBytes()),
                        company.sealBytes()));
                fileLifecycle().registerArchivedEvidenceRollbackCleanup(sealArchive);
            }
            for (OaSignPackageDocument document : visibleDocuments)
            {
                OaSignTemplate template = snapshotTemplate(document);
                documentService.assertTemplateLegalEntityCompatible(template.getTemplateType(),
                        template.getFileUrl(), company.entity().getLegalEntityName());
                GeneratedSignDocument generated = documentService.renderPackageDocument(
                        signPackage, template, actualPackageTemplateTypes);
                fileLifecycle().registerGeneratedDocumentRollbackCleanup(generated);
                generatedDocuments.put(document.getDocumentId(), generated);

                OaSignPackageDocument finalDocument = finalDocumentSnapshot(
                        document, generated, finalVersion);
                Path finalReviewPdf = documentService.resolveGeneratedSignPackageFile(
                        generated.getReviewPdfUrl());
                SignedPdfResult result = generateFinalDocumentPdf(signPackage, document,
                        finalDocument, finalReviewPdf, signatureBytes,
                        sealRequired ? company.sealBytes() : null, generatedTime);
                finalResults.put(document.getDocumentId(), result);
            }
            fileLifecycle().registerSignedPdfRollbackCleanup(finalResults.values());
            for (OaSignPackageDocument document : visibleDocuments)
            {
                GeneratedSignDocument generated = generatedDocuments.get(document.getDocumentId());
                SignedPdfResult result = finalResults.get(document.getDocumentId());
                OaSignPackageDocument updateDocument = new OaSignPackageDocument();
                updateDocument.setDocumentId(document.getDocumentId());
                updateDocument.setFinalPdfUrl(result.getSignedPdfUrl());
                updateDocument.setFinalPdfHash(result.getSignedPdfHash());
                updateDocument.setFinalContentHash(result.getContentPdfHash());
                updateDocument.setFinalDocumentVersion(finalVersion);
                updateDocument.setFinalReadConfirmed(NO);
                documentMapper.updateOaSignPackageDocument(updateDocument);
                evidenceStore().recordFinalCandidate(
                        signPackage, document, generated, result,
                        sealArchive, generatedTime);
            }
            return new PreparedFinalCandidate(finalVersion,
                    fileIntegrity().finalDocumentRootHash(finalResults),
                    generatedTime);
        }
        catch (RuntimeException | Error failure)
        {
            fileLifecycle().discardSignedPdfResults(finalResults.values(), failure);
            throw failure;
        }
        finally
        {
            signPackage.setDocumentVersion(initialVersion);
            fileStorageService.cleanup(sealArchive);
        }
    }

    /**
     * Builds the company-first candidate before HR sends it.  Employee signatures are
     * deliberately absent here and may only be added to the replaceable evidence page later;
     * the stable body hash therefore cannot change after the employee opens the candidate.
     */
    private PreparedFinalCandidate generateAndPersistCompanyFirstCandidate(
            OaSignPackage signPackage, List<OaSignPackageDocument> visibleDocuments,
            Date generatedTime)
    {
        if (visibleDocuments == null || visibleDocuments.isEmpty())
        {
            throw new ServiceException("签约包没有员工可见文件");
        }
        boolean sealRequired = visibleDocuments.stream()
                .anyMatch(placementPolicyService::requiresCompanySeal);
        FrozenCompanyContext company = requireFrozenCompanyContext(signPackage, sealRequired);
        String initialVersion = signPackage.getDocumentVersion();
        String finalVersion = nextDocumentVersion(signPackage);
        signPackage.setDocumentVersion(finalVersion);
        Map<Long, SignedPdfResult> finalResults = new LinkedHashMap<>();
        StagedSignFile sealArchive = null;
        try
        {
            if (company.seal() != null && sealRequired)
            {
                sealArchive = fileStorageService.promote(fileStorageService.stage(
                        signPackage.getTaskId(), signPackage.getPackageId(), finalVersion,
                        OaSignImageValidator.companySealArchiveFilename(
                                company.seal().getSealId(), company.sealBytes()),
                        company.sealBytes()));
                fileLifecycle().registerArchivedEvidenceRollbackCleanup(sealArchive);
            }
            for (OaSignPackageDocument document : visibleDocuments)
            {
                OaSignPackageDocument finalDocument = finalDocumentSnapshotFromFrozenReview(
                        document, finalVersion);
                Path frozenReviewPdf = documentService.resolveGeneratedSignPackageFile(
                        document.getReviewPdfUrl());
                boolean documentSealRequired = placementPolicyService
                        .requiresCompanySeal(document);
                SignedPdfResult result = documentSealRequired
                        ? signedPdfService.generatePendingFinalPdf(null, signPackage,
                                finalDocument, frozenReviewPdf, null, company.sealBytes(),
                                null, generatedTime, "冻结公司与印章的待签完整合同",
                                null, placementPolicyService
                                        .resolveCompanySealPlacement(document))
                        : signedPdfService.generatePendingFinalPdfWithoutMarks(null,
                                signPackage, finalDocument, frozenReviewPdf);
                finalResults.put(document.getDocumentId(), result);
            }
            fileLifecycle().registerSignedPdfRollbackCleanup(finalResults.values());
            for (OaSignPackageDocument document : visibleDocuments)
            {
                SignedPdfResult result = finalResults.get(document.getDocumentId());
                OaSignPackageDocument updateDocument = new OaSignPackageDocument();
                updateDocument.setDocumentId(document.getDocumentId());
                updateDocument.setFinalPdfUrl(result.getSignedPdfUrl());
                updateDocument.setFinalPdfHash(result.getSignedPdfHash());
                updateDocument.setFinalContentHash(result.getContentPdfHash());
                updateDocument.setFinalDocumentVersion(finalVersion);
                updateDocument.setFinalReadConfirmed(NO);
                documentMapper.updateOaSignPackageDocument(updateDocument);
                evidenceStore().recordCompanyFirstFinalCandidate(
                        signPackage, document, finalVersion, result,
                        sealArchive, generatedTime);
            }
            return new PreparedFinalCandidate(finalVersion,
                    fileIntegrity().finalDocumentRootHash(finalResults),
                    generatedTime);
        }
        catch (RuntimeException | Error failure)
        {
            fileLifecycle().discardSignedPdfResults(finalResults.values(), failure);
            throw failure;
        }
        finally
        {
            signPackage.setDocumentVersion(initialVersion);
            fileStorageService.cleanup(sealArchive);
        }
    }

    private OaSignPackageDocument finalDocumentSnapshotFromFrozenReview(
            OaSignPackageDocument source, String finalVersion)
    {
        if (StringUtils.isBlank(source.getReviewPdfUrl())
                || StringUtils.isBlank(source.getReviewPdfHash()))
        {
            throw new ServiceException("冻结待发文件不完整");
        }
        OaSignPackageDocument document = new OaSignPackageDocument();
        document.setDocumentId(source.getDocumentId());
        document.setPackageId(source.getPackageId());
        document.setDocumentName(source.getDocumentName());
        document.setReviewPdfUrl(source.getReviewPdfUrl());
        document.setReviewPdfHash(source.getReviewPdfHash());
        document.setDocumentVersion(finalVersion);
        document.setEmployeeVisible(source.getEmployeeVisible());
        document.setReadConfirmationRequired(source.getReadConfirmationRequired());
        document.setEmployeeSignRequired(source.getEmployeeSignRequired());
        document.setSignaturePositionJson(source.getSignaturePositionJson());
        document.setCompanySealPositionJson(source.getCompanySealPositionJson());
        document.setCompanySealRequired(source.getCompanySealRequired());
        document.setDocumentPolicyMode(source.getDocumentPolicyMode());
        return document;
    }

    private FrozenCompanyContext requireFrozenCompanyContext(OaSignPackage signPackage,
            boolean sealRequired)
    {
        if (signPackage == null || signPackage.getLegalEntityIdSnapshot() == null)
        {
            throw new ServiceException("签约包尚未冻结公司主数据");
        }
        SysLegalEntity entity = signCompanyService.requireContractReadyEntity(
                signPackage.getLegalEntityIdSnapshot());
        if (!sameText(signPackage.getLegalEntityCodeSnapshot(), entity.getLegalEntityCode())
                || !sameText(signPackage.getLegalEntityNameSnapshot(), entity.getLegalEntityName())
                || !sameText(signPackage.getLegalEntityCreditCodeSnapshot(),
                        entity.getUnifiedSocialCreditCode())
                || !sameText(signPackage.getLegalEntityAddressSnapshot(),
                        entity.getRegisteredAddress())
                || !sameText(signPackage.getLegalRepresentativeSnapshot(),
                        entity.getLegalRepresentative()))
        {
            throw new ServiceException("公司主数据已变化，请重新预览后生成");
        }
        OaCompanySealConfig seal = null;
        byte[] sealBytes = null;
        if (sealRequired || signPackage.getSealIdSnapshot() != null)
        {
            seal = signCompanyService.requireContractReadySeal(
                    signPackage.getSealIdSnapshot(), entity.getLegalEntityId());
            if (!sameText(signPackage.getSealNameSnapshot(), seal.getSealName())
                    || !Objects.equals(signPackage.getSealImageUrlSnapshot(),
                            seal.getSealImageUrl())
                    || !sameHash(signPackage.getSealImageHashSnapshot(),
                            seal.getSealImageHash()))
            {
                throw new ServiceException("公司印章快照已变化，请重新预览后生成");
            }
            sealBytes = documentService.readConfiguredFileBytes(seal.getSealImageUrl());
            if (!sameHash(signPackage.getSealImageHashSnapshot(), sha256(sealBytes)))
            {
                throw new ServiceException("公司印章文件校验不一致");
            }
        }
        if (sealRequired && seal == null)
        {
            throw new ServiceException("当前签约包缺少有效合同章");
        }
        return new FrozenCompanyContext(entity, seal, sealBytes);
    }

    private boolean sameText(String left, String right)
    {
        return Objects.equals(StringUtils.trim(left), StringUtils.trim(right));
    }

    private record FrozenCompanyContext(SysLegalEntity entity,
            OaCompanySealConfig seal, byte[] sealBytes) {}

    private record PreparedFinalCandidate(String finalVersion,
            String rootHash, Date generatedTime) {}

    private Map<Long, SignedPdfResult> generateFinalArchives(OaSignPackage signPackage,
            List<OaSignPackageDocument> documents, Date confirmedTime, String finalRootHash)
    {
        assertSigningSequencePolicyMatches(signPackage, documents);
        Map<Long, SignedPdfResult> archives = new LinkedHashMap<>();
        byte[] sealBytes = null;
        if (documents.stream().anyMatch(placementPolicyService::requiresCompanySeal))
        {
            if (StringUtils.isBlank(signPackage.getSealImageUrlSnapshot()))
            {
                throw new ServiceException("最终归档缺少公司印章快照");
            }
            sealBytes = documentService.readConfiguredFileBytes(
                    signPackage.getSealImageUrlSnapshot());
            if (!sameHash(signPackage.getSealImageHashSnapshot(), sha256(sealBytes)))
            {
                throw new ServiceException("公司印章快照校验不一致");
            }
        }
        for (OaSignPackageDocument document : documents)
        {
            boolean signatureRequired = placementPolicyService.requiresEmployeeSignature(document);
            boolean sealRequired = placementPolicyService.requiresCompanySeal(document);
            byte[] signatureBytes = signatureRequired
                    ? fileIntegrity().readFinalSignatureBytes(
                            signPackage, document) : null;
            SignedPdfResult archive = signedPdfService.archiveConfirmedFinalPdf(
                    signPackage.getTaskId(), signPackage, document,
                    documentService.resolveGeneratedSignPackageFile(document.getFinalPdfUrl()),
                    document.getFinalPdfHash(), document.getFinalContentHash(), finalRootHash,
                    signatureBytes, sealRequired ? sealBytes : null,
                    signatureRequired ? employeeSignatureTime(signPackage) : null,
                    sealRequired ? signPackage.getFinalGeneratedTime() : null,
                    confirmedTime,
                    signatureRequired
                            ? placementPolicyService.resolveSignaturePlacement(document) : null,
                    sealRequired
                            ? placementPolicyService.resolveCompanySealPlacement(document) : null);
            archives.put(document.getDocumentId(), archive);
        }
        return archives;
    }

    private String sha256(byte[] bytes)
    {
        return fileIntegrity().sha256(bytes);
    }

    /**
     * A company-first employee action crosses two explicit task-state edges in one
     * transaction. Task-event request IDs are globally unique, so each durable edge
     * gets a deterministic child ID while the package/final-confirm records retain
     * the original API request ID.
     */
    private String taskStepRequestId(String requestId, OaSignTaskStatus target)
    {
        if (StringUtils.isBlank(requestId) || target == null)
        {
            throw new ServiceException("签约任务幂等请求信息不完整");
        }
        return sha256((requestId.trim() + "|TASK|" + target.name())
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private void applySignedResultSnapshot(List<OaSignPackageDocument> documents,
            Map<Long, SignedPdfResult> signedResults)
    {
        for (OaSignPackageDocument document : documents)
        {
            SignedPdfResult result = signedResults.get(document.getDocumentId());
            if (result != null)
            {
                document.setSigned("Y");
                document.setSignedPdfUrl(result.getSignedPdfUrl());
                document.setSignedPdfHash(result.getSignedPdfHash());
                document.setSignatureFileUrl(result.getSignatureFileUrl());
                document.setSignatureHash(result.getSignatureHash());
            }
        }
    }

    private void syncTaskStatus(OaSignPackage signPackage, OaSignTaskStatus target,
            String reasonCode, String requestId, String ipAddress, String userAgent)
    {
        syncTaskStatus(signPackage, target, reasonCode, requestId, ipAddress,
                userAgent, true);
    }

    private void syncTaskStatus(OaSignPackage signPackage, OaSignTaskStatus target,
            String reasonCode, String requestId, String ipAddress, String userAgent,
            boolean enqueueFinalReady)
    {
        if (signPackage.getTaskId() == null || taskMapper == null || taskEventService == null)
        {
            return;
        }
        OaSignTask task = taskMapper.selectOaSignTaskById(signPackage.getTaskId());
        if (task == null || target.name().equals(task.getStatus()))
        {
            return;
        }
        boolean allowedSource = switch (target)
        {
            case VIEWED -> OaSignTaskStatus.PENDING_SIGN.name().equals(task.getStatus());
            case PENDING_COMPANY -> OaSignTaskStatus.PENDING_SIGN.name().equals(task.getStatus())
                    || OaSignTaskStatus.VIEWED.name().equals(task.getStatus());
            case PENDING_FINAL_CONFIRM -> OaSignTaskStatus.PENDING_COMPANY.name().equals(task.getStatus())
                    || OaSignTaskStatus.PENDING_SIGN.name().equals(task.getStatus())
                    || OaSignTaskStatus.VIEWED.name().equals(task.getStatus());
            case SIGNED -> OaSignTaskStatus.PENDING_FINAL_CONFIRM.name().equals(task.getStatus());
            default -> false;
        };
        if (!allowedSource)
        {
            throw new ServiceException("签约包与任务状态不一致，请联系人力资源经办人处理");
        }
        if (target == OaSignTaskStatus.PENDING_FINAL_CONFIRM
                && OaSignTaskStatus.PENDING_COMPANY.name().equals(task.getStatus())
                && !Objects.equals(task.getSignDeadline(), signPackage.getSignDeadline()))
        {
            if (task.getSignDeadline() == null || signPackage.getSignDeadline() == null
                    || !signPackage.getSignDeadline().after(task.getSignDeadline())
                    || taskMapper.resumeEmployeeDeadlineAfterCompanyStage(task.getTaskId(),
                            OaSignTaskStatus.PENDING_COMPANY.name(), task.getVersion(),
                            task.getAssignedHrUserId(), task.getSignDeadline(),
                            signPackage.getSignDeadline()) != 1)
            {
                throw new ServiceException("公司处理阶段后恢复员工签署期限失败");
            }
            task.setSignDeadline(signPackage.getSignDeadline());
            task.setVersion(task.getVersion() + 1);
        }
        OaSignOperatorType operatorType = target == OaSignTaskStatus.PENDING_FINAL_CONFIRM
                && OaSignTaskStatus.PENDING_COMPANY.name().equals(task.getStatus())
                        ? OaSignOperatorType.HR : OaSignOperatorType.EMPLOYEE;
        taskEventService.transition(task, target, operatorType,
                SecurityUtils.getUserId(), reasonCode, null, requestId, ipAddress, userAgent);
        if (notificationOutboxService != null && target == OaSignTaskStatus.PENDING_COMPANY)
        {
            notificationOutboxService.enqueueInitialSigned(task, signPackage);
        }
        if (notificationOutboxService != null
                && target == OaSignTaskStatus.PENDING_FINAL_CONFIRM
                && enqueueFinalReady)
        {
            notificationOutboxService.enqueueFinalReady(task, signPackage);
        }
        if (target == OaSignTaskStatus.SIGNED)
        {
            Date completedTime = new Date();
            taskMapper.updateCompletedTime(task.getTaskId(), completedTime);
            task.setCompletedTime(completedTime);
            if (notificationOutboxService != null)
            {
                notificationOutboxService.enqueueCompleted(task, signPackage);
            }
        }
    }

    private void lockRenewalGuardBeforePackageTerminal(OaSignPackage signPackage,
            OaSignTaskStatus target)
    {
        if (signPackage.getTaskId() == null || taskMapper == null || taskEventService == null)
        {
            return;
        }
        OaSignTask task = taskMapper.selectOaSignTaskById(signPackage.getTaskId());
        if (task == null || !Objects.equals(task.getPackageId(), signPackage.getPackageId())
                || !Objects.equals(task.getEmployeeId(), signPackage.getEmployeeId()))
        {
            throw new ServiceException("签约包与任务状态不一致，请联系人力资源经办人处理");
        }
        taskEventService.lockRenewalGuardBeforeTerminal(task, target);
    }

    private String resolveHrConfirmer(OaSignPackage signPackage)
    {
        return StringUtils.isNotBlank(signPackage.getUpdateBy())
                ? signPackage.getUpdateBy() : signPackage.getCreateBy();
    }

    private void validateCompanyFirstSignCandidate(OaSignPackage signPackage,
            List<OaSignPackageDocument> employeeDocuments,
            OaSignPackageSignRequest signRequest, List<OaSignEvent> events)
    {
        if (!Objects.equals(signPackage.getFinalDocumentVersion(),
                signRequest.getFinalDocumentVersion())
                || !sameHash(signPackage.getFinalDocumentRootHash(),
                        signRequest.getFinalDocumentRootHash()))
        {
            throw new ServiceException("最终合同版本已变化，请重新打开并签署");
        }
        Map<Long, String> requestedHashes = SIGNING_REQUEST_POLICY.requestedFinalHashes(
                signRequest.getFinalDocumentHashes(), employeeDocuments.size());
        Map<Long, String> stableBodyHashes =
                fileIntegrity().validateFinalDocuments(
                        signPackage, employeeDocuments);
        for (OaSignPackageDocument document : employeeDocuments)
        {
            String requestedHash = requestedHashes.get(document.getDocumentId());
            if (!sameHash(document.getFinalPdfHash(), requestedHash))
            {
                throw new ServiceException("最终合同文件已变化，请重新打开并签署");
            }
        }
        if (requestedHashes.size() != employeeDocuments.size())
        {
            throw new ServiceException("最终合同文件集合不完整，请刷新后逐份重新打开");
        }
        validateFinalDocumentsOpened(signPackage, employeeDocuments, events);
        String actualRoot =
                fileIntegrity().finalDocumentRootHashFromHashes(
                        stableBodyHashes);
        if (!sameHash(actualRoot, signPackage.getFinalDocumentRootHash())
                || !sameHash(actualRoot, signRequest.getFinalDocumentRootHash()))
        {
            throw new ServiceException("最终合同正文集合已变化，请联系经办人重新生成");
        }
    }

    private Map<Long, Path> validateFrozenReviewPdfsForSignatureEvidence(
            OaSignPackage signPackage, List<OaSignPackageDocument> requiredDocuments)
    {
        if (requiredDocuments.isEmpty())
        {
            throw new ServiceException("签约包没有需要员工签署的文件");
        }
        Map<Long, Path> paths = new LinkedHashMap<>();
        for (OaSignPackageDocument document : requiredDocuments)
        {
            if (!Objects.equals(signPackage.getDocumentVersion(),
                    document.getDocumentVersion())
                    || StringUtils.isBlank(document.getReviewPdfUrl())
                    || StringUtils.isBlank(document.getReviewPdfHash()))
            {
                throw new ServiceException("冻结待发文件证据不完整");
            }
            Path path = documentService.resolveGeneratedSignPackageFile(
                    document.getReviewPdfUrl());
            if (!sameHash(document.getReviewPdfHash(), sha256(path)))
            {
                throw new ServiceException("冻结待发文件校验不一致");
            }
            paths.put(document.getDocumentId(), path);
        }
        return paths;
    }

    private Map<Long, Path> validateFrozenReviewPdfs(OaSignPackage signPackage,
            List<OaSignPackageDocument> requiredDocuments, Map<Long, String> requestedHashes,
            List<OaSignEvent> events)
    {
        if (requiredDocuments.isEmpty())
        {
            throw new ServiceException("签约包没有需要员工签署的文件");
        }
        Map<Long, Path> paths = new LinkedHashMap<>();
        for (OaSignPackageDocument document : requiredDocuments)
        {
            if (!Objects.equals(signPackage.getDocumentVersion(), document.getDocumentVersion()))
            {
                throw new ServiceException("文档版本已更新，请重新阅读后签署");
            }
            if (!isYes(document.getReadConfirmed()))
            {
                throw new ServiceException("全部必签文件阅读确认后才能签署");
            }
            String requestHash = requestedHashes.get(document.getDocumentId());
            if (requestHash == null || StringUtils.isBlank(document.getReviewPdfUrl())
                    || StringUtils.isBlank(document.getReviewPdfHash()))
            {
                throw new ServiceException("必签文件集合不完整，请刷新后重新阅读");
            }
            Path reviewPdfPath = documentService.resolveGeneratedSignPackageFile(document.getReviewPdfUrl());
            String diskHash = sha256(reviewPdfPath);
            if (!sameHash(document.getReviewPdfHash(), requestHash)
                    || !sameHash(document.getReviewPdfHash(), diskHash))
            {
                throw new ServiceException("签约文件校验不一致，请刷新后重新阅读");
            }
            if (!READ_POLICY.hasInitialReadEvent(events, document))
            {
                throw new ServiceException("阅读证据不完整，请重新打开文件并确认阅读");
            }
            paths.put(document.getDocumentId(), reviewPdfPath);
        }
        if (paths.size() != requestedHashes.size())
        {
            throw new ServiceException("必签文件集合不完整，请刷新后重新阅读");
        }
        return paths;
    }

    private void validateRequiredReadConfirmations(OaSignPackage signPackage,
            List<OaSignPackageDocument> employeeDocuments, List<OaSignEvent> events)
    {
        for (OaSignPackageDocument document : employeeDocuments)
        {
            if (!requiresReadConfirmation(document))
            {
                continue;
            }
            if (!Objects.equals(signPackage.getDocumentVersion(), document.getDocumentVersion()))
            {
                throw new ServiceException("文档版本已更新，请重新阅读后签署");
            }
            if (!isYes(document.getReadConfirmed()))
            {
                throw new ServiceException("全部需确认文件阅读确认后才能签署");
            }
            if (StringUtils.isBlank(document.getReviewPdfUrl())
                    || StringUtils.isBlank(document.getReviewPdfHash()))
            {
                throw new ServiceException("阅读文件证据不完整，请重新打开文件并确认阅读");
            }
            Path reviewPdfPath = documentService.resolveGeneratedSignPackageFile(document.getReviewPdfUrl());
            if (!sameHash(document.getReviewPdfHash(), sha256(reviewPdfPath))
                    || !READ_POLICY.hasInitialReadEvent(events, document))
            {
                throw new ServiceException("阅读证据不完整，请重新打开文件并确认阅读");
            }
        }
    }

    private void persistSignedDocument(OaSignPackageDocument document, SignedPdfResult result,
            GeneratedSignDocument certificate)
    {
        OaSignPackageDocument updateDocument = new OaSignPackageDocument();
        updateDocument.setDocumentId(document.getDocumentId());
        updateDocument.setSigned(YES);
        updateDocument.setStatus(OaSignPackageStatus.SIGNED);
        updateDocument.setReviewPdfUrl(document.getReviewPdfUrl());
        updateDocument.setReviewPdfHash(document.getReviewPdfHash());
        updateDocument.setSignedFileUrl(result.getSignedPdfUrl());
        updateDocument.setSignedPdfUrl(result.getSignedPdfUrl());
        updateDocument.setSignedPdfHash(result.getSignedPdfHash());
        updateDocument.setFileHashAfterSign(result.getSignedPdfHash());
        updateDocument.setSignatureFileUrl(result.getSignatureFileUrl());
        updateDocument.setSignatureHash(result.getSignatureHash());
        updateDocument.setCertificateFileUrl(certificate.getFileUrl());
        updateDocument.setCertificateHash(certificate.getSha256());
        updateDocument.setDocumentVersion(document.getDocumentVersion());
        documentMapper.updateOaSignPackageDocument(updateDocument);
    }

    private boolean sameHash(String left, String right)
    {
        return fileIntegrity().sameHash(left, right);
    }

    private String sha256(Path path)
    {
        return fileIntegrity().sha256(path);
    }

    @Override
    public OaSignPackageFile resolveMyDocumentFile(Long packageId, Long documentId)
    {
        OaSignPackage signPackage = packageAccessPolicy().employeePackage(packageId);
        assertEmployeeFileStageVisible(signPackage);
        OaSignPackageDocument document = assertPackageDocument(packageId, documentId);
        assertEmployeeVisibleDocument(document);
        if (!OaSignPackageStatus.SIGNED.equals(signPackage.getStatus()))
        {
            throw new ServiceException("签约完成前仅支持在线预览，不能下载原始文件");
        }
        return packageFileResolver().source(document);
    }

    @Override
    public OaSignPackageFile resolveMyDocumentPreviewFile(Long packageId, Long documentId)
    {
        OaSignPackage signPackage = packageAccessPolicy().employeePackage(packageId);
        assertEmployeeFileStageVisible(signPackage);
        OaSignPackageDocument document = assertPackageDocument(packageId, documentId);
        assertEmployeeVisibleDocument(document);
        return packageFileResolver().source(document);
    }

    @Override
    public OaSignPackageFile resolveMySignedDocumentFile(Long packageId, Long documentId)
    {
        OaSignPackage signPackage = packageAccessPolicy().employeePackage(packageId);
        assertEmployeeFileStageVisible(signPackage);
        OaSignPackageDocument document = assertPackageDocument(packageId, documentId);
        assertEmployeeVisibleDocument(document);
        return packageFileResolver().signed(document);
    }

    @Override
    public OaSignPackageFile resolveMyFinalDocumentFile(Long packageId, Long documentId)
    {
        OaSignPackage signPackage = packageAccessPolicy().employeePackage(packageId);
        assertEmployeeFileStageVisible(signPackage);
        if (!OaSignPackageStatus.SIGNED.equals(signPackage.getStatus()))
        {
            throw new ServiceException("待最终确认版仅支持在线预览，确认后可下载归档文件");
        }
        OaSignPackageDocument document = assertPackageDocument(packageId, documentId);
        assertEmployeeVisibleDocument(document);
        return packageFileResolver().finalArchive(document);
    }

    @Override
    public OaSignPackageFile resolveMyFinalDocumentExportFile(Long packageId, Long documentId)
    {
        OaSignPackage signPackage = packageAccessPolicy().employeePackage(packageId);
        assertEmployeeFileStageVisible(signPackage);
        OaSignPackageDocument document = assertPackageDocument(packageId, documentId);
        assertEmployeeVisibleDocument(document);
        return resolveConfirmedFinalDocumentExport(signPackage, document);
    }

    @Override
    public OaSignPackageFile resolveMyFinalDocumentPreviewFile(Long packageId, Long documentId)
    {
        OaSignPackage signPackage = packageAccessPolicy().employeePackage(packageId);
        assertEmployeeFileStageVisible(signPackage);
        boolean companyFirstPendingSign = OaSignSigningSequence.COMPANY_FIRST.equals(
                signPackage.getSigningSequence())
                && (OaSignPackageStatus.PENDING_SIGN.equals(signPackage.getStatus())
                        || OaSignPackageStatus.PART_VIEWED.equals(signPackage.getStatus()));
        if (!companyFirstPendingSign
                && !OaSignPackageStatus.PENDING_FINAL_CONFIRM.equals(signPackage.getStatus())
                && !OaSignPackageStatus.SIGNED.equals(signPackage.getStatus()))
        {
            throw new ServiceException("最终合同尚未生成");
        }
        OaSignPackageDocument document = assertPackageDocument(packageId, documentId);
        assertEmployeeVisibleDocument(document);
        return OaSignPackageStatus.SIGNED.equals(signPackage.getStatus())
                ? packageFileResolver().finalArchive(document)
                : packageFileResolver().finalCandidate(document);
    }

    @Override
    public OaSignPackageFile resolveMyCertificateFile(Long packageId, Long documentId)
    {
        OaSignPackage signPackage = packageAccessPolicy().employeePackage(packageId);
        assertEmployeeFileStageVisible(signPackage);
        OaSignPackageDocument document = assertPackageDocument(packageId, documentId);
        assertEmployeeVisibleDocument(document);
        return packageFileResolver().certificate(document);
    }

    private void assertEmployeeFileStageVisible(OaSignPackage signPackage)
    {
        if (OaSignResponseSanitizer.isSignatureFirstPreparedNotSent(signPackage))
        {
            throw new ServiceException("最终合同尚未发送，请等待经办人发送后查看");
        }
    }

    @Override
    public OaSignPackageFile resolveScopedDocumentFile(Long packageId, Long documentId, Long selectedShopDeptId)
    {
        signHrAccessService.requireCurrentHr();
        packageAccessPolicy().hrScopedPackage(packageId, selectedShopDeptId);
        OaSignPackageDocument document = assertPackageDocument(packageId, documentId);
        return packageFileResolver().source(document);
    }

    @Override
    public OaSignPackageFile resolveScopedSignedDocumentFile(Long packageId, Long documentId,
            Long selectedShopDeptId)
    {
        signHrAccessService.requireCurrentHr();
        packageAccessPolicy().hrScopedPackage(packageId, selectedShopDeptId);
        return packageFileResolver().signed(assertPackageDocument(packageId, documentId));
    }

    @Override
    public OaSignPackageFile resolveScopedFinalDocumentFile(Long packageId, Long documentId,
            Long selectedShopDeptId)
    {
        signHrAccessService.requireCurrentHr();
        OaSignPackage signPackage = packageAccessPolicy().hrScopedPackage(
                packageId, selectedShopDeptId);
        boolean preparedCandidate = OaSignPackageStatus.PENDING_COMPANY.equals(signPackage.getStatus())
                && "PREPARED_NOT_SENT".equals(signPackage.getFinalConfirmationStatus());
        boolean sentCandidate = OaSignPackageStatus.PENDING_FINAL_CONFIRM.equals(signPackage.getStatus())
                && "PENDING".equals(signPackage.getFinalConfirmationStatus());
        boolean confirmedArchive = OaSignPackageStatus.SIGNED.equals(signPackage.getStatus())
                && "CONFIRMED".equals(signPackage.getFinalConfirmationStatus());
        if ((!preparedCandidate && !sentCandidate && !confirmedArchive)
                || StringUtils.isBlank(signPackage.getFinalDocumentVersion())
                || !SIGNING_REQUEST_POLICY.isSha256Hash(
                        signPackage.getFinalDocumentRootHash()))
        {
            throw new ServiceException("当前签约包不存在可预览的最终合同");
        }
        List<OaSignPackageDocument> visibleDocuments = documentMapper
                .selectDocumentsByPackageId(packageId).stream()
                .filter(this::isEmployeeVisibleDocument).toList();
        Map<Long, String> verifiedHashes =
                fileIntegrity().validateFinalDocuments(
                        signPackage, visibleDocuments);
        String actualRootHash =
                fileIntegrity().finalDocumentRootHashFromHashes(verifiedHashes);
        if (!sameHash(actualRootHash, signPackage.getFinalDocumentRootHash()))
        {
            throw new ServiceException("最终合同文件集合校验不一致");
        }
        OaSignPackageDocument document = assertPackageDocument(packageId, documentId);
        if (!Objects.equals(signPackage.getFinalDocumentVersion(),
                document.getFinalDocumentVersion())
                || !SIGNING_REQUEST_POLICY.isSha256Hash(document.getFinalPdfHash()))
        {
            throw new ServiceException("最终合同文档版本或文件证据不完整");
        }
        return confirmedArchive ? packageFileResolver().finalArchive(document)
                : packageFileResolver().finalCandidate(document);
    }

    @Override
    public OaSignPackageFile resolveScopedFinalDocumentExportFile(Long packageId, Long documentId,
            Long selectedShopDeptId)
    {
        signHrAccessService.requireCurrentHr();
        OaSignPackage signPackage = packageAccessPolicy().hrScopedPackage(
                packageId, selectedShopDeptId);
        OaSignPackageDocument document = assertPackageDocument(packageId, documentId);
        if (!isEmployeeVisibleDocument(document))
        {
            throw new ServiceException(FINAL_EXPORT_EVIDENCE_FAILURE);
        }
        return resolveConfirmedFinalDocumentExport(signPackage, document);
    }

    /**
     * Resolves and verifies only a confirmed immutable archive.  This path is deliberately
     * separate from preview: a pending candidate, a legacy candidate fallback, or a newly
     * generated replacement can never be presented as the formal final export.
     */
    private OaSignPackageFile resolveConfirmedFinalDocumentExport(OaSignPackage signPackage,
            OaSignPackageDocument targetDocument)
    {
        if (signPackage == null
                || !OaSignPackageStatus.SIGNED.equals(signPackage.getStatus())
                || !"CONFIRMED".equalsIgnoreCase(signPackage.getFinalConfirmationStatus())
                || StringUtils.isBlank(signPackage.getFinalDocumentVersion())
                || signPackage.getFinalConfirmedTime() == null
                || signPackage.getFinalEvidenceGeneratedTime() == null
                || !SIGNING_REQUEST_POLICY.isSha256Hash(signPackage.getFinalDocumentRootHash())
                || !SIGNING_REQUEST_POLICY.isSha256Hash(signPackage.getFinalArchiveRootHash()))
        {
            throw new ServiceException("当前签约包不存在可导出的最终归档");
        }
        List<OaSignPackageDocument> visibleDocuments = documentMapper
                .selectDocumentsByPackageId(signPackage.getPackageId()).stream()
                .filter(this::isEmployeeVisibleDocument).toList();
        if (visibleDocuments.isEmpty() || visibleDocuments.stream()
                .noneMatch(document -> Objects.equals(document.getDocumentId(),
                        targetDocument.getDocumentId())))
        {
            throw new ServiceException(FINAL_EXPORT_EVIDENCE_FAILURE);
        }
        assertReviewedExactV7VisibleDocumentSet(targetDocument, visibleDocuments);
        assertSigningSequencePolicyMatches(signPackage, visibleDocuments);
        if (OaSignSigningSequence.COMPANY_FIRST.equals(signPackage.getSigningSequence()))
        {
            // APPENDED legacy snapshots and the explicit exact-v7 company-first MULTI policy
            // both resolve to no employee-signature body placement during signing. The latter
            // retains audited coordinates solely for this read-only display export.
            assertCompanyFirstSignaturePreservesStableBody(visibleDocuments);
        }

        OaSignFinalConfirmation confirmation = finalConfirmationMapper
                .selectByPackageAndVersion(signPackage.getPackageId(),
                        signPackage.getFinalDocumentVersion());
        if (confirmation == null
                || !Objects.equals(confirmation.getPackageId(), signPackage.getPackageId())
                || !Objects.equals(confirmation.getEmployeeId(), signPackage.getEmployeeId())
                || !Objects.equals(confirmation.getFinalDocumentVersion(),
                        signPackage.getFinalDocumentVersion())
                || !sameHash(confirmation.getDocumentRootHash(),
                        signPackage.getFinalDocumentRootHash())
                || !sameAuditSecond(confirmation.getConfirmedTime(),
                        signPackage.getFinalConfirmedTime()))
        {
            throw new ServiceException(FINAL_EXPORT_EVIDENCE_FAILURE);
        }
        assertFinalConfirmationDocumentEvidence(confirmation, visibleDocuments);

        Map<Long, String> stableBodyHashes = new LinkedHashMap<>();
        for (OaSignPackageDocument document : visibleDocuments)
        {
            if (!Objects.equals(signPackage.getFinalDocumentVersion(),
                    document.getFinalDocumentVersion())
                    || !SIGNING_REQUEST_POLICY.isSha256Hash(document.getFinalPdfHash())
                    || !SIGNING_REQUEST_POLICY.isSha256Hash(document.getFinalContentHash()))
            {
                throw new ServiceException(FINAL_EXPORT_EVIDENCE_FAILURE);
            }
            stableBodyHashes.put(document.getDocumentId(), document.getFinalContentHash());
        }
        if (!sameHash(fileIntegrity().finalDocumentRootHashFromHashes(stableBodyHashes),
                signPackage.getFinalDocumentRootHash()))
        {
            throw new ServiceException("最终合同文件集合校验不一致");
        }

        Map<Long, String> archiveHashes = new LinkedHashMap<>();
        ExportInputs targetInputs = null;
        for (OaSignPackageDocument document : visibleDocuments)
        {
            OaSignPackageFile archive = packageFileResolver().finalArchiveOnly(document);
            if (!SIGNING_REQUEST_POLICY.isSha256Hash(archive.getFileHash()))
            {
                throw new ServiceException(FINAL_EXPORT_EVIDENCE_FAILURE);
            }
            ExportMaterial signature = resolveExportSignatureMaterial(signPackage, document);
            ExportMaterial seal = resolveExportSealMaterial(signPackage, document);
            OaSignPlacementPolicyService.HistoricalExportPolicy historicalPolicy = null;
            if (placementPolicyService.requiresHistoricalExportPolicy(document))
            {
                if (StringUtils.isBlank(document.getSourceFileUrlSnapshot())
                        || StringUtils.isBlank(document.getReviewPdfUrl()))
                {
                    throw new ServiceException(
                            "该历史合同缺少源模板或review PDF证据，暂不能恢复");
                }
                String templateSourceHash = fileIntegrity().sha256(
                        documentService.readConfiguredFileBytes(
                                document.getSourceFileUrlSnapshot()));
                Path reviewPdfPath = documentService.resolveGeneratedSignPackageFile(
                        document.getReviewPdfUrl());
                historicalPolicy = placementPolicyService.resolveHistoricalExportPolicy(
                        document, signPackage, templateSourceHash,
                        reviewPdfPath, archive.getPath(), archive.getFileHash());
            }
            OaSignedPdfService.PdfImagePlacement signaturePlacement =
                    placementPolicyService.requiresEmployeeSignature(document)
                            ? placementPolicyService.resolveFinalExportSignaturePlacement(
                                    document, historicalPolicy)
                            : null;
            OaSignedPdfService.PdfImagePlacement sealPlacement =
                    placementPolicyService.requiresCompanySeal(document)
                            ? placementPolicyService.resolveFinalExportCompanySealPlacement(
                                    document, historicalPolicy)
                            : null;
            DisplayRepairInputs displayRepair = buildDisplayTextOverlays(
                    signPackage, document, visibleDocuments, historicalPolicy);
            Integer expectedBodyPageCount =
                    placementPolicyService.resolveFinalExportExpectedBodyPageCount(
                            document, historicalPolicy);
            if (Objects.equals(document.getDocumentId(), targetDocument.getDocumentId()))
            {
                targetInputs = new ExportInputs(archive, signature, seal,
                        signaturePlacement, sealPlacement, displayRepair.overlays(),
                        expectedBodyPageCount, displayRepair.auditMetadata(),
                        displayRepair.expectedArchiveFinalRootHash());
            }
            else
            {
                signedPdfService.validateFinalArchiveEvidence(archive.getPath(),
                        document.getFinalContentHash(), signPackage.getFinalDocumentRootHash(),
                        signature == null ? null : signature.hash(),
                        seal == null ? null : seal.hash());
            }
            archiveHashes.put(document.getDocumentId(), archive.getFileHash());
        }
        if (!sameHash(fileIntegrity().finalDocumentRootHashFromHashes(archiveHashes),
                signPackage.getFinalArchiveRootHash()) || targetInputs == null)
        {
            throw new ServiceException(FINAL_EXPORT_EVIDENCE_FAILURE);
        }
        OaSignedPdfService.FinalExportPreparation preparation = signedPdfService.prepareFinalExport(
                targetInputs.archive().getPath(), targetInputs.archive().getFileHash(),
                targetDocument.getFinalContentHash(),
                StringUtils.isNotBlank(targetInputs.expectedArchiveFinalRootHash())
                        ? targetInputs.expectedArchiveFinalRootHash()
                        : signPackage.getFinalDocumentRootHash(),
                targetInputs.signature() == null ? null : targetInputs.signature().hash(),
                targetInputs.seal() == null ? null : targetInputs.seal().hash(),
                targetInputs.signaturePlacement(), targetInputs.sealPlacement(),
                targetInputs.signature() == null ? null : targetInputs.signature().bytes(),
                targetInputs.seal() == null ? null : targetInputs.seal().bytes(),
                targetInputs.textOverlays(), targetInputs.expectedBodyPageCount(),
                targetInputs.auditMetadata());
        return new OaSignPackageFile(preparation.path(),
                finalExportFileName(signPackage, targetDocument),
                "application/pdf", null, preparation.derived());
    }

    private void assertFinalConfirmationDocumentEvidence(
            OaSignFinalConfirmation confirmation,
            List<OaSignPackageDocument> visibleDocuments)
    {
        List<OaSignFinalConfirmationDocument> evidence = finalConfirmationMapper
                .selectDocumentsByPackageAndVersion(confirmation.getPackageId(),
                        confirmation.getFinalDocumentVersion());
        if (evidence == null || evidence.size() != visibleDocuments.size())
        {
            throw new ServiceException(FINAL_EXPORT_EVIDENCE_FAILURE);
        }
        Map<Long, OaSignFinalConfirmationDocument> byDocumentId = new LinkedHashMap<>();
        for (OaSignFinalConfirmationDocument item : evidence)
        {
            if (item == null || item.getDocumentId() == null
                    || byDocumentId.putIfAbsent(item.getDocumentId(), item) != null)
            {
                throw new ServiceException(FINAL_EXPORT_EVIDENCE_FAILURE);
            }
        }
        for (OaSignPackageDocument document : visibleDocuments)
        {
            OaSignFinalConfirmationDocument item = byDocumentId.get(document.getDocumentId());
            if (item == null
                    || !Objects.equals(item.getConfirmationId(),
                            confirmation.getConfirmationId())
                    || !Objects.equals(item.getPackageId(), confirmation.getPackageId())
                    || !Objects.equals(item.getFinalDocumentVersion(),
                            confirmation.getFinalDocumentVersion())
                    || !sameHash(item.getFinalPdfHash(), document.getFinalPdfHash()))
            {
                throw new ServiceException(FINAL_EXPORT_EVIDENCE_FAILURE);
            }
        }
    }

    private void assertReviewedExactV7VisibleDocumentSet(
            OaSignPackageDocument targetDocument,
            List<OaSignPackageDocument> visibleDocuments)
    {
        if (targetDocument == null
                || !OaSignTemplateType.ONBOARD_LABOR_CONTRACT.equalsIgnoreCase(
                        StringUtils.trim(targetDocument.getTemplateType()))
                || !"20260721-v7".equals(StringUtils.trim(
                        targetDocument.getTemplateVersionSnapshot())))
        {
            return;
        }
        Map<String, Long> counts = visibleDocuments.stream()
                .map(OaSignPackageDocument::getTemplateType)
                .filter(StringUtils::isNotBlank)
                .map(value -> value.trim().toUpperCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.groupingBy(
                        value -> value, LinkedHashMap::new,
                        java.util.stream.Collectors.counting()));
        Set<String> allowed = Set.of(
                OaSignTemplateType.ONBOARD_COMMITMENT,
                OaSignTemplateType.ONBOARD_LABOR_CONTRACT,
                OaSignTemplateType.ONBOARD_HANDBOOK,
                OaSignTemplateType.ONBOARD_HANDBOOK_RECEIPT,
                OaSignTemplateType.ONBOARD_SALARY_CONFIRM,
                OaSignTemplateType.ONBOARD_CONFIDENTIAL_NONCOMPETE);
        long handbookCount = counts.getOrDefault(OaSignTemplateType.ONBOARD_HANDBOOK, 0L)
                + counts.getOrDefault(OaSignTemplateType.ONBOARD_HANDBOOK_RECEIPT, 0L);
        boolean complete = counts.keySet().stream().allMatch(allowed::contains)
                && counts.getOrDefault(OaSignTemplateType.ONBOARD_COMMITMENT, 0L) == 1L
                && counts.getOrDefault(OaSignTemplateType.ONBOARD_LABOR_CONTRACT, 0L) == 1L
                && handbookCount == 1L
                && counts.getOrDefault(OaSignTemplateType.ONBOARD_SALARY_CONFIRM, 0L) == 1L
                && counts.getOrDefault(
                        OaSignTemplateType.ONBOARD_CONFIDENTIAL_NONCOMPETE, 0L) <= 1L;
        if (!complete)
        {
            throw new ServiceException("劳动合同同包员工可见文档集合与审定方案不一致");
        }
    }

    /**
     * Read-only release gate for an already loaded immutable package snapshot.
     *
     * <p>The database verifier is in this package and deliberately calls the exact formal
     * export implementation instead of duplicating its policy checks.  It supplies read-only
     * mapper results, deletes only a returned disposable derivative, and never exposes this
     * boundary through an API.</p>
     */
    OaSignPackageFile dryRunConfirmedFinalDocumentExport(OaSignPackage signPackage,
            OaSignPackageDocument targetDocument)
    {
        if (targetDocument != null
                && OaSignTemplateType.ONBOARD_LABOR_CONTRACT.equalsIgnoreCase(
                        StringUtils.trim(targetDocument.getTemplateType()))
                && !placementPolicyService.requiresHistoricalExportPolicy(targetDocument))
        {
            List<OaSignPackageDocument> visibleDocuments = documentMapper
                    .selectDocumentsByPackageId(signPackage.getPackageId()).stream()
                    .filter(this::isEmployeeVisibleDocument).toList();
            Set<String> visibleTypes = visibleDocuments.stream()
                    .map(OaSignPackageDocument::getTemplateType)
                    .filter(StringUtils::isNotBlank)
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
            boolean handbookIncluded = visibleTypes.contains(OaSignTemplateType.ONBOARD_HANDBOOK)
                    || visibleTypes.contains(OaSignTemplateType.ONBOARD_HANDBOOK_RECEIPT);
            byte[] sourceBytes = documentService.readConfiguredFileBytes(
                    targetDocument.getSourceFileUrlSnapshot());
            Path reviewPdf = documentService.resolveGeneratedSignPackageFile(
                    targetDocument.getReviewPdfUrl());
            OaSignPlacementPolicyService.GeneratedPlacementPolicies revalidated =
                    placementPolicyService.validateAndFreezeGeneratedPositions(
                            targetDocument.getTemplateType(),
                            targetDocument.getTemplateVersionSnapshot(),
                            fileIntegrity().sha256(sourceBytes),
                            targetDocument.getSignaturePositionJson(),
                            targetDocument.getCompanySealPositionJson(), reviewPdf,
                            targetDocument.getReviewPdfHash(),
                            placementPolicyService.requiresEmployeeSignature(targetDocument),
                            placementPolicyService.requiresCompanySeal(targetDocument),
                            signPackage.getLegalRepresentativeSnapshot(), handbookIncluded,
                            signPackage.getSigningSequence());
            if (!Objects.equals(revalidated.signaturePositionJson(),
                    targetDocument.getSignaturePositionJson())
                    || !Objects.equals(revalidated.companySealPositionJson(),
                            targetDocument.getCompanySealPositionJson()))
            {
                throw new ServiceException("劳动合同冻结签章策略与实际源PDF锚点不一致");
            }
        }
        return resolveConfirmedFinalDocumentExport(signPackage, targetDocument);
    }

    private DisplayRepairInputs buildDisplayTextOverlays(
            OaSignPackage signPackage, OaSignPackageDocument document,
            List<OaSignPackageDocument> visibleDocuments,
            OaSignPlacementPolicyService.HistoricalExportPolicy historicalPolicy)
    {
        List<OaSignedPdfService.PdfTextPlacement> placements =
                placementPolicyService.resolveFinalExportDisplayTextPlacements(
                        document, historicalPolicy);
        if (placements.isEmpty())
        {
            if (historicalPolicy != null && !historicalPolicy.repairFields().isEmpty())
            {
                throw new ServiceException("历史合同修复字段声明与实际定位不一致");
            }
            return new DisplayRepairInputs(List.of(), null, null);
        }
        Set<String> visibleTypes = visibleDocuments.stream()
                .map(OaSignPackageDocument::getTemplateType)
                .filter(StringUtils::isNotBlank)
                .map(value -> value.trim().toUpperCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (historicalPolicy != null
                && !visibleTypes.containsAll(historicalPolicy.requiredVisibleDocumentTypes()))
        {
            throw new ServiceException("历史合同同包可见文档清单与修复证据不一致");
        }
        boolean handbookIncluded = visibleTypes.contains(OaSignTemplateType.ONBOARD_HANDBOOK)
                || visibleTypes.contains(OaSignTemplateType.ONBOARD_HANDBOOK_RECEIPT);
        String legalRepresentative = StringUtils.trim(
                signPackage.getLegalRepresentativeSnapshot());
        String representativeSource = "FROZEN_PACKAGE_SNAPSHOT";
        String representativeEvidence = "oa_sign_package.legal_representative_snapshot";
        if (StringUtils.isBlank(legalRepresentative) && historicalPolicy != null
                && StringUtils.isNotBlank(historicalPolicy.legalRepresentativeFallback()))
        {
            legalRepresentative = historicalPolicy.legalRepresentativeFallback();
            representativeSource = "VERSIONED_HISTORICAL_REPAIR";
            representativeEvidence = historicalPolicy.legalRepresentativeEvidence();
        }
        List<OaSignedPdfService.PdfTextOverlay> overlays = new ArrayList<>();
        Set<String> actualRepairFields = new java.util.TreeSet<>();
        if (historicalPolicy != null)
        {
            actualRepairFields.add("employeeSignaturePositions");
            actualRepairFields.add("companySealPosition");
        }
        for (OaSignedPdfService.PdfTextPlacement placement : placements)
        {
            String value;
            if ("companyLegalRepresentative".equals(placement.field()))
            {
                if (StringUtils.isBlank(legalRepresentative))
                {
                    throw new ServiceException("历史合同缺少冻结甲方代表，暂不能恢复");
                }
                value = "甲方代表：" + legalRepresentative;
            }
            else if ("attachmentHandbookMark".equals(placement.field()))
            {
                value = (handbookIncluded ? "☑" : "□") + " 3《员工手册》";
            }
            else if ("archiveEvidenceNotice".equals(placement.field()))
            {
                value = "签署校验证据请查看原最终归档及签署凭证。";
            }
            else
            {
                throw new ServiceException("签章展示文本字段不支持：" + placement.field());
            }
            overlays.add(new OaSignedPdfService.PdfTextOverlay(
                    placement.field(), value, placement));
            actualRepairFields.add(placement.field());
        }
        if (historicalPolicy != null)
        {
            Set<String> expectedRepairFields = historicalPolicy.repairFields().stream()
                    .filter(StringUtils::isNotBlank)
                    .collect(java.util.stream.Collectors.toCollection(java.util.TreeSet::new));
            if (!expectedRepairFields.equals(actualRepairFields))
            {
                throw new ServiceException("历史合同修复字段声明与实际导出不一致");
            }
        }
        OaSignedPdfService.FinalExportAuditMetadata audit = historicalPolicy == null ? null
                : new OaSignedPdfService.FinalExportAuditMetadata(
                        historicalPolicy.configVersion(), historicalPolicy.profileId(),
                        historicalPolicy.repairId(), representativeSource,
                        representativeEvidence,
                        List.copyOf(actualRepairFields));
        return new DisplayRepairInputs(List.copyOf(overlays), audit,
                historicalPolicy == null ? null
                        : historicalPolicy.expectedArchiveFinalRootHash());
    }

    private ExportMaterial resolveExportSignatureMaterial(OaSignPackage signPackage,
            OaSignPackageDocument document)
    {
        if (!placementPolicyService.requiresEmployeeSignature(document))
        {
            return null;
        }
        if (employeeSignatureTime(signPackage) == null)
        {
            throw new ServiceException(FINAL_EXPORT_EVIDENCE_FAILURE);
        }
        String expectedHash = StringUtils.isNotBlank(document.getSignatureFileUrl())
                ? document.getSignatureHash() : signPackage.getSignatureSampleHash();
        if (!SIGNING_REQUEST_POLICY.isSha256Hash(expectedHash))
        {
            throw new ServiceException(FINAL_EXPORT_EVIDENCE_FAILURE);
        }
        byte[] signatureBytes = fileIntegrity().readFinalSignatureBytes(signPackage, document);
        String actualHash = fileIntegrity().sha256(signatureBytes);
        if (!sameHash(expectedHash, actualHash))
        {
            throw new ServiceException(FINAL_EXPORT_EVIDENCE_FAILURE);
        }
        return new ExportMaterial(actualHash, signatureBytes);
    }

    private ExportMaterial resolveExportSealMaterial(OaSignPackage signPackage,
            OaSignPackageDocument document)
    {
        if (!placementPolicyService.requiresCompanySeal(document))
        {
            return null;
        }
        if (StringUtils.isBlank(signPackage.getSealImageUrlSnapshot())
                || !SIGNING_REQUEST_POLICY.isSha256Hash(signPackage.getSealImageHashSnapshot())
                || signPackage.getFinalGeneratedTime() == null)
        {
            throw new ServiceException(FINAL_EXPORT_EVIDENCE_FAILURE);
        }
        byte[] sealBytes = documentService.readConfiguredFileBytes(
                signPackage.getSealImageUrlSnapshot());
        String actualHash = fileIntegrity().sha256(sealBytes);
        if (!sameHash(signPackage.getSealImageHashSnapshot(), actualHash))
        {
            throw new ServiceException(FINAL_EXPORT_EVIDENCE_FAILURE);
        }
        return new ExportMaterial(actualHash, sealBytes);
    }

    private record ExportMaterial(String hash, byte[] bytes) {}

    private record ExportInputs(OaSignPackageFile archive, ExportMaterial signature,
            ExportMaterial seal, OaSignedPdfService.PdfImagePlacement signaturePlacement,
            OaSignedPdfService.PdfImagePlacement sealPlacement,
            List<OaSignedPdfService.PdfTextOverlay> textOverlays,
            Integer expectedBodyPageCount,
            OaSignedPdfService.FinalExportAuditMetadata auditMetadata,
            String expectedArchiveFinalRootHash) {}

    private record DisplayRepairInputs(List<OaSignedPdfService.PdfTextOverlay> overlays,
            OaSignedPdfService.FinalExportAuditMetadata auditMetadata,
            String expectedArchiveFinalRootHash) {}

    private boolean sameAuditSecond(Date left, Date right)
    {
        return left != null && right != null && left.getTime() / 1000L == right.getTime() / 1000L;
    }

    private String finalExportFileName(OaSignPackage signPackage,
            OaSignPackageDocument document)
    {
        return safeFinalExportPart(signPackage.getPackageNo(), "签约包") + "-"
                + safeFinalExportPart(signPackage.getEmployeeNameSnapshot(), "员工") + "-"
                + safeFinalExportPart(document.getDocumentName(), "合同") + "-签章展示版.pdf";
    }

    private String safeFinalExportPart(String value, String fallback)
    {
        if (StringUtils.isBlank(value))
        {
            return fallback;
        }
        String cleaned = value.trim().replaceAll("[\\\\/:*?\"<>|\\r\\n\\p{Cntrl}]+", "_");
        return StringUtils.isBlank(cleaned) || ".".equals(cleaned) || "..".equals(cleaned)
                ? fallback : cleaned;
    }

    @Override
    public OaSignPackageFile resolveScopedCertificateFile(Long packageId, Long documentId, Long selectedShopDeptId)
    {
        signHrAccessService.requireCurrentHr();
        packageAccessPolicy().hrScopedPackage(packageId, selectedShopDeptId);
        OaSignPackageDocument document = assertPackageDocument(packageId, documentId);
        return packageFileResolver().certificate(document);
    }

    private void createPackageDocument(OaSignPackage signPackage, OaSignTemplate template, int sortOrder,
            Collection<String> actualPackageTemplateTypes)
    {
        OaSignPackageDocumentDeliveryPolicy.DeliveryRequirements delivery =
                DOCUMENT_DELIVERY_POLICY.deliveryRequirements(template);
        String companySealRequired = placementPolicyService.normalizeCompanySealRequired(
                template.getCompanySealRequired(), YES.equals(delivery.employeeVisible()));
        if (StringUtils.isBlank(template.getFileUrl())
                || !SIGNING_REQUEST_POLICY.isSha256Hash(template.getFileHash()))
        {
            throw new ServiceException("签约模板源文件证据不完整");
        }
        byte[] templateSourceBytes = documentService.readConfiguredFileBytes(
                template.getFileUrl());
        String actualTemplateSourceHash = fileIntegrity().sha256(templateSourceBytes);
        if (!sameHash(template.getFileHash(), actualTemplateSourceHash))
        {
            throw new ServiceException("签约模板源文件指纹与冻结配置不一致");
        }
        placementPolicyService.assertTemplateGenerationAllowed(template.getTemplateType(),
                template.getTemplateVersion(), actualTemplateSourceHash);
        GeneratedSignDocument generated = documentService.renderPackageDocument(
                signPackage, template, actualPackageTemplateTypes);
        OaSignPlacementPolicyService.GeneratedPlacementPolicies generatedPolicies;
        try
        {
            Path generatedReviewPdf = documentService.resolveGeneratedSignPackageFile(
                    generated.getReviewPdfUrl());
            boolean handbookIncluded = actualPackageTemplateTypes != null
                    && actualPackageTemplateTypes.stream().anyMatch(value ->
                            OaSignTemplateType.ONBOARD_HANDBOOK.equalsIgnoreCase(value)
                                    || OaSignTemplateType.ONBOARD_HANDBOOK_RECEIPT
                                            .equalsIgnoreCase(value));
            generatedPolicies = placementPolicyService.validateAndFreezeGeneratedPositions(
                    template.getTemplateType(), template.getTemplateVersion(),
                    actualTemplateSourceHash, template.getSignaturePositionJson(),
                    template.getCompanySealPositionJson(), generatedReviewPdf,
                    generated.getReviewPdfHash(), YES.equals(delivery.employeeSignRequired()),
                    YES.equals(companySealRequired),
                    signPackage.getLegalRepresentativeSnapshot(), handbookIncluded,
                    signPackage.getSigningSequence());
        }
        catch (RuntimeException | Error failure)
        {
            try
            {
                documentService.discardUncommitted(generated);
            }
            catch (RuntimeException cleanupFailure)
            {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
        fileLifecycle().registerGeneratedDocumentRollbackCleanup(generated);
        if (StringUtils.isNotBlank(generated.getDocumentVersion()))
        {
            if (StringUtils.isNotBlank(signPackage.getDocumentVersion())
                    && !signPackage.getDocumentVersion().equals(generated.getDocumentVersion()))
            {
                throw new ServiceException("同一签约包生成了不一致的文档版本");
            }
            signPackage.setDocumentVersion(generated.getDocumentVersion());
        }
        OaSignPackageDocument document = new OaSignPackageDocument();
        document.setPackageId(signPackage.getPackageId());
        document.setTemplateId(template.getTemplateId());
        document.setTemplateType(template.getTemplateType());
        document.setDocumentName(template.getTemplateName());
        document.setTemplateVersionSnapshot(template.getTemplateVersion());
        document.setSourceFileUrlSnapshot(template.getFileUrl());
        document.setGeneratedFileUrl(generated.getSourceFileUrl());
        document.setGeneratedPdfUrl(generated.getReviewPdfUrl());
        document.setFileHashBeforeSign(generated.getReviewPdfHash());
        document.setReviewPdfUrl(generated.getReviewPdfUrl());
        document.setReviewPdfHash(generated.getReviewPdfHash());
        document.setDocumentVersion(generated.getDocumentVersion());
        document.setEmployeeVisible(delivery.employeeVisible());
        document.setReadConfirmationRequired(
                delivery.readConfirmationRequired());
        document.setEmployeeSignRequired(delivery.employeeSignRequired());
        document.setSignaturePositionJson(generatedPolicies.signaturePositionJson());
        document.setCompanySealPositionJson(generatedPolicies.companySealPositionJson());
        document.setCompanySealRequired(companySealRequired);
        document.setDocumentPolicyMode(OaSignPlacementPolicyService.SNAPSHOT_V1);
        document.setReadConfirmed(
                isYes(delivery.readConfirmationRequired()) ? NO : YES);
        document.setSigned(NO);
        document.setSortOrder(template.getSortOrder() != null ? template.getSortOrder() : sortOrder);
        document.setStatus(OaSignPackageStatus.PENDING_SIGN);
        placementPolicyService.assertSigningSequenceMatches(signPackage, document);
        documentMapper.insertOaSignPackageDocument(document);
        evidenceStore().recordGeneratedDocument(
                signPackage, document, generated, new Date());
        recordEvent(signPackage.getPackageId(), document.getDocumentId(), "DOCUMENT_GENERATED", "生成签约包文件",
                generated.getReviewPdfHash(), null, null, "SYSTEM", null);
    }

    private OaSignPackageFileResolver packageFileResolver()
    {
        return new OaSignPackageFileResolver(documentService);
    }

    private OaSignPackageFileLifecycle fileLifecycle()
    {
        return new OaSignPackageFileLifecycle(
                fileStorageService, documentService, signedPdfService);
    }

    private OaSignPackageEvidenceStore evidenceStore()
    {
        return new OaSignPackageEvidenceStore(
                evidenceMapper, placementPolicyService);
    }

    private OaSignPackageFileIntegrity fileIntegrity()
    {
        return new OaSignPackageFileIntegrity(
                documentService, signedPdfService);
    }

    private OaSignPackageAccessPolicy packageAccessPolicy()
    {
        return new OaSignPackageAccessPolicy(
                packageMapper, taskMapper, shopScopeService, signHrAccessService);
    }

    private OaSignPackageDocument assertPackageDocument(Long packageId, Long documentId)
    {
        OaSignPackageDocument document = documentMapper.selectOaSignPackageDocumentById(documentId);
        if (document == null || !Objects.equals(document.getPackageId(), packageId))
        {
            throw new ServiceException("签约包文件不存在");
        }
        return document;
    }

    private void attachChildren(OaSignPackage signPackage)
    {
        signPackage.setDocuments(documentMapper.selectDocumentsByPackageId(signPackage.getPackageId()));
        signPackage.setEvents(eventMapper.selectEventsByPackageId(signPackage.getPackageId()));
    }

    private void attachEmployeeChildren(OaSignPackage signPackage)
    {
        List<OaSignPackageDocument> documents = documentMapper.selectDocumentsByPackageId(signPackage.getPackageId());
        List<OaSignPackageDocument> visibleDocuments =
                DOCUMENT_DELIVERY_POLICY.employeeVisibleDocuments(documents);
        List<OaSignEvent> events = eventMapper.selectEventsByPackageId(signPackage.getPackageId());
        List<OaSignEvent> visibleEvents =
                DOCUMENT_DELIVERY_POLICY.employeeVisibleEvents(
                        events, visibleDocuments);
        signPackage.setDocuments(visibleDocuments);
        signPackage.setEvents(visibleEvents);
    }

    private void assertEmployeeVisibleDocument(OaSignPackageDocument document)
    {
        if (!isEmployeeVisibleDocument(document))
        {
            throw new ServiceException("签约包文件不存在");
        }
    }

    private boolean isEmployeeVisibleDocument(OaSignPackageDocument document)
    {
        return DOCUMENT_DELIVERY_POLICY.isEmployeeVisible(document);
    }

    private boolean requiresReadConfirmation(OaSignPackageDocument document)
    {
        return DOCUMENT_DELIVERY_POLICY.requiresReadConfirmation(document);
    }

    private void sanitizeListRow(OaSignPackage row)
    {
        row.setEmployeePhoneSnapshot(null);
        row.setEmployeeIdCardSnapshot(null);
        row.setDocuments(null);
        row.setEvents(null);
    }

    private void recordEvent(Long packageId, Long documentId, String eventType, String payload,
            String documentHash, String ipAddress, String userAgent, String operatorRole, String requestId)
    {
        recordEvent(packageId, documentId, eventType, payload, documentHash, ipAddress,
                userAgent, operatorRole, requestId, null);
    }

    private void recordEvent(Long packageId, Long documentId, String eventType, String payload,
            String documentHash, String ipAddress, String userAgent, String operatorRole,
            String requestId, Date factTime)
    {
        OaSignEvent event = new OaSignEvent();
        event.setPackageId(packageId);
        event.setDocumentId(documentId);
        event.setEventType(eventType);
        event.setEventPayload(payload);
        event.setOperatorUserId(SecurityUtils.getUserId());
        event.setOperatorName(SecurityUtils.getUsername());
        event.setOperatorRole(operatorRole);
        event.setIpAddress(ipAddress);
        event.setUserAgent(userAgent);
        event.setDocumentHash(documentHash);
        event.setRequestId(requestId);
        event.setPrevEventHash(eventMapper.selectLatestEventHashByPackageId(packageId));
        event.setCreateBy(SecurityUtils.getUsername());
        if (factTime == null)
        {
            long now = System.currentTimeMillis();
            event.setCreateTime(new Date(now - now % 1000));
        }
        else
        {
            event.setCreateTime(factTime);
        }
        event.setEventHash(hashEvent(event));
        eventMapper.insertOaSignEvent(event);
    }

    private void recordSystemEvent(Long packageId, String eventType, String payload)
    {
        OaSignEvent event = new OaSignEvent();
        event.setPackageId(packageId);
        event.setEventType(eventType);
        event.setEventPayload(payload);
        event.setOperatorName("system");
        event.setOperatorRole("SYSTEM");
        event.setPrevEventHash(eventMapper.selectLatestEventHashByPackageId(packageId));
        event.setCreateBy("system");
        long now = System.currentTimeMillis();
        event.setCreateTime(new Date(now - now % 1000));
        event.setEventHash(hashEvent(event));
        if (eventMapper.insertOaSignEvent(event) != 1)
        {
            throw new ServiceException("签约包事件记录失败");
        }
    }

    private String hashEvent(OaSignEvent event)
    {
        return OaSignVerificationService.calculateEventHash(event);
    }

    private boolean isYes(String value)
    {
        return YES.equalsIgnoreCase(value);
    }

    private String generatePackageNo()
    {
        return "SP" + new SimpleDateFormat("yyyyMMddHHmmss").format(new Date())
                + UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
    }
}
