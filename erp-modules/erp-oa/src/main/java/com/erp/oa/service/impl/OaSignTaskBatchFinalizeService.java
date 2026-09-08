package com.erp.oa.service.impl;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.core.utils.StringUtils;
import com.erp.common.security.shop.ShopScopeService;
import com.erp.common.security.utils.SecurityUtils;
import com.erp.oa.constant.OaSignPackageStatus;
import com.erp.oa.constant.OaSignSigningSequence;
import com.erp.oa.constant.OaSignTaskStatus;
import com.erp.oa.constant.OaSignTemplateType;
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
import com.erp.oa.domain.vo.OaSignTaskBatchFinalizeCompanyOption;
import com.erp.oa.domain.vo.OaSignTaskBatchFinalizeItem;
import com.erp.oa.domain.vo.OaSignTaskBatchFinalizePreviewItem;
import com.erp.oa.domain.vo.OaSignTaskBatchFinalizePreviewResult;
import com.erp.oa.domain.vo.OaSignTaskBatchFinalizeResult;
import com.erp.oa.domain.vo.OaSignTaskBatchFinalizeSealOption;
import com.erp.oa.mapper.OaSignPackageDocumentMapper;
import com.erp.oa.mapper.OaSignPackageMapper;
import com.erp.oa.mapper.OaSignTaskMapper;
import com.erp.oa.service.IOaSignPackageService;
import com.erp.system.api.domain.SysLegalEntity;

/**
 * Coordinates company-and-seal finalization for real {@code PENDING_COMPANY} tasks.
 *
 * <p>The coordinator deliberately has no outer transaction. Each mutation delegates to the
 * established transactional package finalizer, so one failed item cannot roll back another
 * employee's successfully generated final contract.</p>
 */
@Service
public class OaSignTaskBatchFinalizeService
{
    private static final int MAX_BATCH_SIZE = 20;
    private static final String HEX_64 = "(?i)[0-9a-f]{64}";

    private final OaSignHrAccessService hrAccessService;
    private final ShopScopeService shopScopeService;
    private final OaSignTaskMapper taskMapper;
    private final OaSignPackageMapper packageMapper;
    private final OaSignPackageDocumentMapper documentMapper;
    private final OaSignPlacementPolicyService placementPolicyService;
    private final OaSignCompanyService companyService;
    private final IOaSignPackageService packageService;

    public OaSignTaskBatchFinalizeService(OaSignHrAccessService hrAccessService,
            @Qualifier("oaSignScopeService") ShopScopeService shopScopeService,
            OaSignTaskMapper taskMapper, OaSignPackageMapper packageMapper,
            OaSignPackageDocumentMapper documentMapper,
            OaSignPlacementPolicyService placementPolicyService,
            OaSignCompanyService companyService, IOaSignPackageService packageService)
    {
        this.hrAccessService = hrAccessService;
        this.shopScopeService = shopScopeService;
        this.taskMapper = taskMapper;
        this.packageMapper = packageMapper;
        this.documentMapper = documentMapper;
        this.placementPolicyService = placementPolicyService;
        this.companyService = companyService;
        this.packageService = packageService;
    }

    public OaSignTaskBatchFinalizePreviewResult preview(
            OaSignTaskBatchFinalizePreviewRequest request, Long selectedShopDeptId)
    {
        hrAccessService.requireCurrentHr();
        List<Long> taskIds = normalizeTaskIds(request == null ? null : request.getTaskIds());
        List<Long> scopeDeptIds = shopScopeService.resolveScopeDeptIds(selectedShopDeptId);
        Map<Long, SealValidation> sealCache = new HashMap<>();

        List<OaSignTaskBatchFinalizePreviewItem> items = new ArrayList<>();
        for (Long taskId : taskIds)
        {
            items.add(previewItem(taskId, scopeDeptIds, sealCache));
        }
        OaSignTaskBatchFinalizePreviewResult result = new OaSignTaskBatchFinalizePreviewResult();
        result.setItems(items);
        result.setTotalCount(items.size());
        result.setReadyCount((int) items.stream()
                .filter(item -> Boolean.TRUE.equals(item.getReadyForExecution())).count());
        result.setBlockedCount(result.getTotalCount() - result.getReadyCount());
        return result;
    }

    public OaSignTaskBatchFinalizeResult execute(OaSignTaskBatchFinalizeRequest request,
            Long selectedShopDeptId)
    {
        hrAccessService.requireCurrentHr();
        if (request == null || StringUtils.isBlank(request.getRequestId()))
        {
            throw new ServiceException("请求编号不能为空");
        }
        String batchRequestId = request.getRequestId().trim();
        if (batchRequestId.length() > 64)
        {
            throw new ServiceException("请求编号不能超过64个字符");
        }
        List<OaSignTaskBatchFinalizeAction> actions = normalizeActions(request.getItems());
        List<Long> scopeDeptIds = shopScopeService.resolveScopeDeptIds(selectedShopDeptId);

        List<OaSignTaskBatchFinalizeItem> results = new ArrayList<>();
        for (OaSignTaskBatchFinalizeAction action : actions)
        {
            OaSignTaskBatchFinalizeItem item = new OaSignTaskBatchFinalizeItem();
            item.setTaskId(action.getTaskId());
            item.setPackageId(action.getPackageId());
            try
            {
                Inspection inspection = inspect(action.getTaskId(), action.getPackageId(),
                        scopeDeptIds);
                validateExpectedVersions(inspection, action);
                validateDecision(inspection, action);

                OaSignPackageFinalizeRequest finalizeRequest = new OaSignPackageFinalizeRequest();
                finalizeRequest.setLegalEntityId(action.getLegalEntityId());
                finalizeRequest.setSealId(action.getSealId());
                finalizeRequest.setCorrectionReason(action.getCorrectionReason());
                finalizeRequest.setRequestId(itemRequestId(batchRequestId, action.getTaskId()));
                finalizeRequest.setExpectedVersion(action.getExpectedPackageVersion());
                finalizeRequest.setExpectedTaskVersion(action.getExpectedTaskVersion());
                finalizeRequest.setSigningSequence(OaSignSigningSequence.SIGNATURE_FIRST);

                OaSignPackage finalized = packageService.finalizePackage(action.getPackageId(),
                        finalizeRequest, selectedShopDeptId);
                OaSignTask latestTask = findTaskForResult(action.getTaskId());
                OaSignPackage latestPackage = finalized == null
                        ? findPackageForResult(action.getPackageId()) : finalized;
                if (latestPackage == null
                        || !OaSignPackageStatus.PENDING_COMPANY.equals(latestPackage.getStatus())
                        || !"PREPARED_NOT_SENT".equals(
                                latestPackage.getFinalConfirmationStatus()))
                {
                    setResult(item, "FAILED", "FINALIZE_RESULT_UNCONFIRMED",
                            "最终合同生成结果未能确认，请打开任务查看", latestTask, latestPackage);
                }
                else
                {
                    setResult(item, "FINALIZED", "FINALIZED",
                            "最终合同已生成但尚未发送，请预览后再发送员工确认",
                            latestTask, latestPackage);
                }
            }
            catch (PreflightFailure failure)
            {
                boolean concealed = "OUT_OF_SCOPE".equals(failure.code())
                        || "HR_NOT_ASSIGNED".equals(failure.code());
                if (!concealed && classifyAlreadyFinalized(item, action, scopeDeptIds))
                {
                    // The task was finalized concurrently with the current authorized request.
                }
                else
                {
                    setResult(item, "BLOCKED", failure.code(), failure.getMessage(),
                            concealed ? null : findTaskForResult(action.getTaskId()),
                            concealed ? null : findPackageForResult(action.getPackageId()));
                }
            }
            catch (RuntimeException failure)
            {
                if (!classifyAlreadyFinalized(item, action, scopeDeptIds))
                {
                    setResult(item, "FAILED", "FINALIZE_FAILED", safeMessage(failure),
                            findTaskForResult(action.getTaskId()),
                            findPackageForResult(action.getPackageId()));
                }
            }
            results.add(item);
        }
        OaSignTaskBatchFinalizeResult result = new OaSignTaskBatchFinalizeResult();
        result.setItems(results);
        summarize(result, results);
        return result;
    }

    private OaSignTaskBatchFinalizePreviewItem previewItem(Long taskId,
            List<Long> scopeDeptIds, Map<Long, SealValidation> sealCache)
    {
        OaSignTaskBatchFinalizePreviewItem item = new OaSignTaskBatchFinalizePreviewItem();
        item.setTaskId(taskId);
        List<String> blockers = new ArrayList<>();
        Inspection inspection;
        try
        {
            inspection = inspect(taskId, null, scopeDeptIds);
        }
        catch (PreflightFailure failure)
        {
            blockers.add(failure.getMessage());
            // Do not echo task/package details when the current HR is not authorized to see them.
            if (!"OUT_OF_SCOPE".equals(failure.code())
                    && !"HR_NOT_ASSIGNED".equals(failure.code()))
            {
                populateKnownPreviewFields(item, taskMapper.selectOaSignTaskById(taskId), null);
            }
            item.setBlockingReasons(blockers);
            item.setReadyForExecution(false);
            return item;
        }

        OaSignTask task = inspection.task();
        OaSignPackage signPackage = inspection.signPackage();
        populateKnownPreviewFields(item, task, signPackage);
        item.setCompanySealRequired(inspection.companySealRequired());
        item.setExcelRecommendedCompany(signPackage.getRecommendedCompanySnapshot());
        item.setExcelRecommendedLegalRepresentative(
                signPackage.getRecommendedLegalRepresentativeSnapshot());
        item.setExcelRecommendedRegisteredAddress(
                signPackage.getRecommendedRegisteredAddressSnapshot());

        try
        {
            OaSignCompanyOptions baseOptions = companyService.options(signPackage);
            OaSignCompanyService.CompanyMatchResult excelMatch = companyService.matchExcelCompany(
                    signPackage.getRecommendedCompanySnapshot(),
                    signPackage.getRecommendedLegalRepresentativeSnapshot(),
                    signPackage.getRecommendedRegisteredAddressSnapshot(),
                    signPackage.getDeptIdSnapshot());
            item.setExcelMatchMode(excelMatch.getMode());
            OaLegalEntityCandidate department = excelMatch.getDepartmentCandidate() == null
                    ? baseOptions.getAutomaticCandidate() : excelMatch.getDepartmentCandidate();
            Long departmentId = department == null ? null : department.getLegalEntityId();
            Long excelId = excelMatch.getSelectedEntity() == null ? null
                    : excelMatch.getSelectedEntity().getLegalEntityId();
            Long recommendedId = excelId == null ? departmentId : excelId;
            item.setRecommendedLegalEntityId(recommendedId);
            item.setRecommendedLegalEntitySource(excelId == null
                    ? departmentId == null ? "HR_REQUIRED" : "DEPARTMENT" : "EXCEL_MATCH");

            Map<Long, BigDecimal> excelScores = new LinkedHashMap<>();
            for (OaSignCompanyService.CompanyMatchCandidate candidate : excelMatch.getCandidates())
            {
                if (candidate.getEntity() != null && candidate.getEntity().getLegalEntityId() != null)
                {
                    excelScores.put(candidate.getEntity().getLegalEntityId(), candidate.getScore());
                }
            }
            List<OaSignTaskBatchFinalizeCompanyOption> candidates = new ArrayList<>();
            List<SysLegalEntity> entities = baseOptions.getLegalEntities() == null
                    ? List.of() : baseOptions.getLegalEntities();
            for (SysLegalEntity entity : entities)
            {
                if (entity == null || entity.getLegalEntityId() == null)
                {
                    continue;
                }
                candidates.add(toCompanyOption(entity, departmentId, excelId,
                        excelScores.get(entity.getLegalEntityId()),
                        inspection.companySealRequired(), sealCache));
            }
            item.setCompanyCandidates(candidates);
            if (candidates.stream().noneMatch(value -> Boolean.TRUE.equals(value.getSelectable())))
            {
                blockers.add(inspection.companySealRequired()
                        ? "没有同时满足公司主数据与有效合同章要求的候选公司"
                        : "没有主数据完整的可用合同公司");
            }
        }
        catch (RuntimeException failure)
        {
            blockers.add("公司与印章候选加载失败：" + safeMessage(failure));
        }
        item.setBlockingReasons(blockers);
        item.setReadyForExecution(blockers.isEmpty());
        return item;
    }

    private OaSignTaskBatchFinalizeCompanyOption toCompanyOption(SysLegalEntity entity,
            Long departmentId, Long excelId, BigDecimal excelScore, boolean sealRequired,
            Map<Long, SealValidation> sealCache)
    {
        OaSignTaskBatchFinalizeCompanyOption option = new OaSignTaskBatchFinalizeCompanyOption();
        option.setLegalEntityId(entity.getLegalEntityId());
        option.setLegalEntityCode(entity.getLegalEntityCode());
        option.setLegalEntityName(entity.getLegalEntityName());
        option.setUnifiedSocialCreditCode(entity.getUnifiedSocialCreditCode());
        option.setRegisteredAddress(entity.getRegisteredAddress());
        option.setLegalRepresentative(entity.getLegalRepresentative());
        option.setDepartmentCandidate(Objects.equals(entity.getLegalEntityId(), departmentId));
        option.setExcelCandidate(Objects.equals(entity.getLegalEntityId(), excelId));
        option.setExcelMatchScore(excelScore);
        List<String> missing = companyService.contractMasterMissingFields(entity);
        option.setMissingMasterFields(missing);
        option.setContractReady(missing.isEmpty());

        SealValidation seals = sealCache.computeIfAbsent(entity.getLegalEntityId(),
                this::validateSeals);
        option.setAvailableContractSeals(seals.seals());
        option.setRecommendedSealId(seals.recommendedSealId());
        option.setSealRecommendationMode(seals.recommendationMode());
        option.setSealValidationMessages(seals.validationMessages());
        option.setSelectable(missing.isEmpty() && (!sealRequired || !seals.seals().isEmpty()));
        return option;
    }

    private SealValidation validateSeals(Long legalEntityId)
    {
        List<OaCompanySealConfig> configured;
        try
        {
            configured = companyService.listSeals(legalEntityId, true);
        }
        catch (RuntimeException failure)
        {
            return new SealValidation(List.of(), null, "LOAD_FAILED",
                    List.of(safeMessage(failure)));
        }
        if (configured == null)
        {
            configured = List.of();
        }
        List<OaSignTaskBatchFinalizeSealOption> valid = new ArrayList<>();
        LinkedHashSet<String> validationMessages = new LinkedHashSet<>();
        for (OaCompanySealConfig candidate : configured)
        {
            if (candidate == null || candidate.getSealId() == null)
            {
                continue;
            }
            try
            {
                OaCompanySealConfig seal = companyService.requireContractReadySeal(
                        candidate.getSealId(), legalEntityId);
                valid.add(toSealOption(seal));
            }
            catch (RuntimeException failure)
            {
                validationMessages.add(safeMessage(failure));
            }
        }
        List<OaSignTaskBatchFinalizeSealOption> defaults = valid.stream()
                .filter(value -> Boolean.TRUE.equals(value.getDefaultSeal())).toList();
        Long recommended = null;
        String mode;
        if (valid.isEmpty())
        {
            mode = "HR_REQUIRED_NO_VALID_SEAL";
        }
        else if (defaults.size() == 1)
        {
            recommended = defaults.get(0).getSealId();
            mode = "UNIQUE_DEFAULT";
        }
        else if (defaults.size() > 1)
        {
            mode = "HR_REQUIRED_MULTIPLE_DEFAULT_SEALS";
        }
        else if (valid.size() == 1)
        {
            recommended = valid.get(0).getSealId();
            mode = "UNIQUE_ACTIVE";
        }
        else
        {
            mode = "HR_REQUIRED_MULTIPLE_ACTIVE_SEALS";
        }
        return new SealValidation(List.copyOf(valid), recommended, mode,
                List.copyOf(validationMessages));
    }

    private OaSignTaskBatchFinalizeSealOption toSealOption(OaCompanySealConfig seal)
    {
        OaSignTaskBatchFinalizeSealOption option = new OaSignTaskBatchFinalizeSealOption();
        option.setSealId(seal.getSealId());
        option.setSealCode(seal.getSealCode());
        option.setSealName(seal.getSealName());
        option.setDefaultSeal("Y".equalsIgnoreCase(seal.getIsDefault()));
        option.setValidFrom(seal.getValidFrom());
        option.setValidTo(seal.getValidTo());
        return option;
    }

    private Inspection inspect(Long taskId, Long expectedPackageId, List<Long> scopeDeptIds)
    {
        OaSignTask task = taskId == null ? null : taskMapper.selectOaSignTaskById(taskId);
        if (task == null)
        {
            throw blocked("TASK_NOT_FOUND", "签约任务不存在");
        }
        if (!inScope(task.getShopDeptId(), scopeDeptIds))
        {
            throw blocked("OUT_OF_SCOPE", "签约任务不在当前签约组织权限范围内");
        }
        try
        {
            hrAccessService.requireTaskOwner(task);
        }
        catch (ServiceException unauthorized)
        {
            throw blocked("HR_NOT_ASSIGNED", "签约任务未分配给当前合同经办人");
        }
        if (task.getPackageId() == null
                || expectedPackageId != null && !Objects.equals(expectedPackageId, task.getPackageId()))
        {
            throw blocked("TASK_PACKAGE_MISMATCH", "签约任务与签约包绑定已变化");
        }
        OaSignPackage signPackage = packageMapper.selectOaSignPackageById(task.getPackageId());
        if (signPackage == null || !Objects.equals(signPackage.getTaskId(), task.getTaskId())
                || !Objects.equals(signPackage.getPackageId(), task.getPackageId())
                || !Objects.equals(signPackage.getEmployeeId(), task.getEmployeeId())
                || !Objects.equals(signPackage.getShopDeptId(), task.getShopDeptId()))
        {
            throw blocked("TASK_PACKAGE_MISMATCH", "签约任务与签约包绑定不一致");
        }
        if (!OaSignTaskStatus.PENDING_COMPANY.name().equals(task.getStatus())
                || !OaSignPackageStatus.PENDING_COMPANY.equals(signPackage.getStatus()))
        {
            throw blocked("STATUS_NOT_PENDING_COMPANY", "仅待选公司盖章任务可以批量处理");
        }
        if (!OaSignSigningSequence.SIGNATURE_FIRST.equals(signPackage.getSigningSequence()))
        {
            throw blocked("SIGNING_SEQUENCE_MISMATCH", "仅先确认事实并留签名的任务可以在此处理");
        }
        if (task.getVersion() == null || signPackage.getVersion() == null)
        {
            throw blocked("VERSION_MISSING", "任务或签约包缺少乐观锁版本");
        }
        if (StringUtils.isBlank(signPackage.getDocumentVersion()))
        {
            throw blocked("DOCUMENT_VERSION_MISSING", "签约包文档版本缺失");
        }
        List<OaSignPackageDocument> documents = documentMapper
                .selectDocumentsByPackageId(signPackage.getPackageId());
        List<OaSignPackageDocument> visible = documents == null ? List.of()
                : documents.stream().filter(this::isEmployeeVisibleDocument).toList();
        if (visible.isEmpty())
        {
            throw blocked("NO_EMPLOYEE_DOCUMENT", "签约包没有员工可见文件");
        }
        boolean signatureRequired;
        boolean sealRequired;
        try
        {
            signatureRequired = visible.stream()
                    .anyMatch(placementPolicyService::requiresEmployeeSignature);
            sealRequired = visible.stream()
                    .anyMatch(placementPolicyService::requiresCompanySeal);
        }
        catch (RuntimeException failure)
        {
            throw blocked("DOCUMENT_POLICY_INVALID", safeMessage(failure));
        }
        if (signatureRequired)
        {
            boolean hasSignature = visible.stream()
                    .filter(placementPolicyService::requiresEmployeeSignature)
                    .anyMatch(document -> "Y".equalsIgnoreCase(document.getSigned())
                            && StringUtils.isNotBlank(document.getSignatureFileUrl())
                            && StringUtils.isNotBlank(document.getSignatureHash())
                            && document.getSignatureHash().trim().matches(HEX_64));
            if (!hasSignature || employeeSignatureTimeMissing(signPackage))
            {
                throw blocked("EMPLOYEE_SIGNATURE_INCOMPLETE", "员工首次签名证据不完整");
            }
        }
        return new Inspection(task, signPackage, visible, sealRequired);
    }

    private boolean employeeSignatureTimeMissing(OaSignPackage signPackage)
    {
        return signPackage.getSignatureSampleTime() == null
                && signPackage.getInitialSignedTime() == null
                && signPackage.getSignedTime() == null;
    }

    private void validateExpectedVersions(Inspection inspection,
            OaSignTaskBatchFinalizeAction action)
    {
        if (!Objects.equals(inspection.task().getVersion(), action.getExpectedTaskVersion()))
        {
            throw blocked("TASK_VERSION_CHANGED", "任务版本已变化，请重新预览");
        }
        if (!Objects.equals(inspection.signPackage().getVersion(),
                action.getExpectedPackageVersion()))
        {
            throw blocked("PACKAGE_VERSION_CHANGED", "签约包版本已变化，请重新预览");
        }
    }

    private void validateDecision(Inspection inspection, OaSignTaskBatchFinalizeAction action)
    {
        SysLegalEntity selected;
        try
        {
            selected = companyService.requireContractReadyEntity(action.getLegalEntityId());
        }
        catch (RuntimeException failure)
        {
            throw blocked("COMPANY_NOT_READY", safeMessage(failure));
        }
        Long recommendedId = recommendedLegalEntityId(inspection.signPackage());
        if (recommendedId != null && !recommendedId.equals(action.getLegalEntityId())
                && StringUtils.isBlank(action.getCorrectionReason()))
        {
            throw blocked("CORRECTION_REASON_REQUIRED", "改选非推荐合同公司时必须填写原因");
        }
        if (inspection.companySealRequired())
        {
            if (action.getSealId() == null)
            {
                throw blocked("SEAL_REQUIRED", "请选择所选公司的有效合同印章");
            }
            try
            {
                companyService.requireContractReadySeal(action.getSealId(),
                        selected.getLegalEntityId());
            }
            catch (RuntimeException failure)
            {
                throw blocked("SEAL_NOT_READY", safeMessage(failure));
            }
        }
        else if (action.getSealId() != null)
        {
            throw blocked("SEAL_NOT_REQUIRED", "当前签约包文件策略不需要企业章");
        }
    }

    private Long recommendedLegalEntityId(OaSignPackage signPackage)
    {
        OaSignCompanyService.CompanyMatchResult match = companyService.matchExcelCompany(
                signPackage.getRecommendedCompanySnapshot(),
                signPackage.getRecommendedLegalRepresentativeSnapshot(),
                signPackage.getRecommendedRegisteredAddressSnapshot(),
                signPackage.getDeptIdSnapshot());
        if (match.getSelectedEntity() != null)
        {
            return match.getSelectedEntity().getLegalEntityId();
        }
        OaLegalEntityCandidate department = match.getDepartmentCandidate() == null
                ? companyService.resolveCandidate(signPackage) : match.getDepartmentCandidate();
        return department == null ? null : department.getLegalEntityId();
    }

    private boolean classifyAlreadyFinalized(OaSignTaskBatchFinalizeItem item,
            OaSignTaskBatchFinalizeAction action, List<Long> scopeDeptIds)
    {
        OaSignTask task = findTaskForResult(action.getTaskId());
        OaSignPackage signPackage = findPackageForResult(action.getPackageId());
        boolean prepared = task != null && signPackage != null
                && OaSignTaskStatus.PENDING_COMPANY.name().equals(task.getStatus())
                && OaSignPackageStatus.PENDING_COMPANY.equals(signPackage.getStatus())
                && "PREPARED_NOT_SENT".equals(signPackage.getFinalConfirmationStatus());
        boolean sent = task != null && signPackage != null
                && OaSignTaskStatus.PENDING_FINAL_CONFIRM.name().equals(task.getStatus())
                && OaSignPackageStatus.PENDING_FINAL_CONFIRM.equals(signPackage.getStatus());
        if (task == null || signPackage == null
                || !inScope(task.getShopDeptId(), scopeDeptIds)
                || !Objects.equals(task.getPackageId(), signPackage.getPackageId())
                || !Objects.equals(signPackage.getTaskId(), task.getTaskId())
                || !Objects.equals(task.getEmployeeId(), signPackage.getEmployeeId())
                || !Objects.equals(task.getShopDeptId(), signPackage.getShopDeptId())
                || !OaSignSigningSequence.SIGNATURE_FIRST.equals(
                        signPackage.getSigningSequence())
                || !prepared && !sent)
        {
            return false;
        }
        try
        {
            hrAccessService.requireTaskOwner(task);
        }
        catch (ServiceException unauthorized)
        {
            return false;
        }
        if (!Objects.equals(action.getLegalEntityId(), signPackage.getLegalEntityIdSnapshot())
                || !Objects.equals(action.getSealId(), signPackage.getSealIdSnapshot()))
        {
            setResult(item, "BLOCKED", "FINALIZED_DECISION_CONFLICT",
                    "最终合同已按其他公司或印章生成，请刷新后核对", task, signPackage);
            return true;
        }
        setResult(item, "ALREADY_FINALIZED", "ALREADY_FINALIZED",
                prepared ? "最终合同已生成但尚未发送，无需重复生成"
                        : "最终合同已生成并发送，无需重复处理",
                task, signPackage);
        return true;
    }

    private OaSignTask findTaskForResult(Long taskId)
    {
        try
        {
            return taskId == null ? null : taskMapper.selectOaSignTaskById(taskId);
        }
        catch (RuntimeException ignored)
        {
            return null;
        }
    }

    private OaSignPackage findPackageForResult(Long packageId)
    {
        try
        {
            return packageId == null ? null : packageMapper.selectOaSignPackageById(packageId);
        }
        catch (RuntimeException ignored)
        {
            return null;
        }
    }

    private void populateKnownPreviewFields(OaSignTaskBatchFinalizePreviewItem item,
            OaSignTask task, OaSignPackage signPackage)
    {
        if (task != null)
        {
            item.setTaskId(task.getTaskId());
            item.setTaskNo(task.getTaskNo());
            item.setTaskStatus(task.getStatus());
            item.setTaskVersion(task.getVersion());
            item.setPackageId(task.getPackageId());
            item.setEmployeeId(task.getEmployeeId());
        }
        OaSignPackage resolved = signPackage;
        if (resolved == null && task != null && task.getPackageId() != null)
        {
            resolved = packageMapper.selectOaSignPackageById(task.getPackageId());
        }
        if (resolved != null)
        {
            item.setPackageId(resolved.getPackageId());
            item.setPackageNo(resolved.getPackageNo());
            item.setPackageStatus(resolved.getStatus());
            item.setPackageVersion(resolved.getVersion());
            item.setSigningSequence(resolved.getSigningSequence());
            item.setEmployeeId(resolved.getEmployeeId());
            item.setEmployeeName(resolved.getEmployeeNameSnapshot());
        }
    }

    private boolean isEmployeeVisibleDocument(OaSignPackageDocument document)
    {
        if (document == null || "N".equalsIgnoreCase(document.getEmployeeVisible()))
        {
            return false;
        }
        if ("Y".equalsIgnoreCase(document.getEmployeeVisible()))
        {
            return true;
        }
        try
        {
            return OaSignTemplateType.require(document.getTemplateType()).isEmployeeVisible();
        }
        catch (RuntimeException ignored)
        {
            return false;
        }
    }

    private List<Long> normalizeTaskIds(List<Long> taskIds)
    {
        if (taskIds == null || taskIds.isEmpty())
        {
            throw new ServiceException("请选择要处理的签约任务");
        }
        if (taskIds.size() > MAX_BATCH_SIZE)
        {
            throw new ServiceException("单次最多处理" + MAX_BATCH_SIZE + "个签约任务");
        }
        LinkedHashSet<Long> unique = new LinkedHashSet<>();
        for (Long taskId : taskIds)
        {
            if (taskId == null || taskId <= 0)
            {
                throw new ServiceException("任务编号无效");
            }
            if (!unique.add(taskId))
            {
                throw new ServiceException("任务编号不能重复");
            }
        }
        return new ArrayList<>(unique);
    }

    private List<OaSignTaskBatchFinalizeAction> normalizeActions(
            List<OaSignTaskBatchFinalizeAction> actions)
    {
        if (actions == null || actions.isEmpty())
        {
            throw new ServiceException("请选择要处理的签约任务");
        }
        if (actions.size() > MAX_BATCH_SIZE)
        {
            throw new ServiceException("单次最多处理" + MAX_BATCH_SIZE + "个签约任务");
        }
        Set<Long> taskIds = new LinkedHashSet<>();
        for (OaSignTaskBatchFinalizeAction action : actions)
        {
            if (action == null || action.getTaskId() == null || action.getTaskId() <= 0
                    || action.getPackageId() == null || action.getPackageId() <= 0
                    || action.getLegalEntityId() == null || action.getLegalEntityId() <= 0
                    || action.getExpectedTaskVersion() == null
                    || action.getExpectedTaskVersion() < 0
                    || action.getExpectedPackageVersion() == null
                    || action.getExpectedPackageVersion() < 0)
            {
                throw new ServiceException("批量处理项不完整或编号无效");
            }
            if (!taskIds.add(action.getTaskId()))
            {
                throw new ServiceException("任务编号不能重复");
            }
        }
        return new ArrayList<>(actions);
    }

    static String itemRequestId(String batchRequestId, Long taskId)
    {
        try
        {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(batchRequestId.trim().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(24);
            for (int index = 0; index < 12; index++)
            {
                hex.append(String.format(Locale.ROOT, "%02x", hash[index]));
            }
            return "BF:" + hex + ":" + taskId;
        }
        catch (NoSuchAlgorithmException exception)
        {
            throw new IllegalStateException("SHA-256不可用", exception);
        }
    }

    private boolean inScope(Long shopDeptId, List<Long> scopeDeptIds)
    {
        return shopDeptId != null && scopeDeptIds != null && scopeDeptIds.contains(shopDeptId);
    }

    private PreflightFailure blocked(String code, String message)
    {
        return new PreflightFailure(code, message);
    }

    private String safeMessage(Throwable failure)
    {
        return failure == null || StringUtils.isBlank(failure.getMessage())
                ? "处理失败，请刷新后重试" : failure.getMessage();
    }

    private void setResult(OaSignTaskBatchFinalizeItem item, String result, String code,
            String message, OaSignTask task, OaSignPackage signPackage)
    {
        item.setResult(result);
        item.setCode(code);
        item.setMessage(message);
        if (task != null)
        {
            item.setTaskId(task.getTaskId());
            item.setTaskStatus(task.getStatus());
            item.setTaskVersion(task.getVersion());
            if (item.getPackageId() == null) item.setPackageId(task.getPackageId());
        }
        if (signPackage != null)
        {
            item.setPackageId(signPackage.getPackageId());
            item.setPackageStatus(signPackage.getStatus());
            item.setPackageVersion(signPackage.getVersion());
        }
    }

    private void summarize(OaSignTaskBatchFinalizeResult result,
            List<OaSignTaskBatchFinalizeItem> items)
    {
        result.setTotalCount(items.size());
        for (OaSignTaskBatchFinalizeItem item : items)
        {
            switch (item.getResult())
            {
                case "FINALIZED" -> result.setFinalizedCount(result.getFinalizedCount() + 1);
                case "ALREADY_FINALIZED" -> result.setAlreadyFinalizedCount(
                        result.getAlreadyFinalizedCount() + 1);
                case "BLOCKED" -> result.setBlockedCount(result.getBlockedCount() + 1);
                default -> result.setFailedCount(result.getFailedCount() + 1);
            }
        }
    }

    private record Inspection(OaSignTask task, OaSignPackage signPackage,
            List<OaSignPackageDocument> visibleDocuments, boolean companySealRequired) {}

    private record SealValidation(List<OaSignTaskBatchFinalizeSealOption> seals,
            Long recommendedSealId, String recommendationMode,
            List<String> validationMessages) {}

    private static final class PreflightFailure extends RuntimeException
    {
        private static final long serialVersionUID = 1L;
        private final String code;

        PreflightFailure(String code, String message)
        {
            super(message);
            this.code = code;
        }

        String code()
        {
            return code;
        }
    }
}
