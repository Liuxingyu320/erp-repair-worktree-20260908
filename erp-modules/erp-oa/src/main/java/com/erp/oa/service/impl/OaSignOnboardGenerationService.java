package com.erp.oa.service.impl;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.security.utils.SecurityUtils;
import com.erp.oa.api.domain.HrSignBusinessEvent;
import com.erp.oa.constant.OaOnboardSalaryVersionPolicy;
import com.erp.oa.constant.OaSignPackageStatus;
import com.erp.oa.constant.OaSignSigningSequence;
import com.erp.oa.domain.OaCompanySealConfig;
import com.erp.oa.domain.OaSignOnboardContractSnapshot;
import com.erp.oa.domain.OaSignOnboardDataRequest;
import com.erp.oa.domain.OaSignOnboardImportBatch;
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

/** Generates existing signing tasks without sending them. Each row is isolated. */
@Service
public class OaSignOnboardGenerationService
{
    private static final Logger log = LoggerFactory.getLogger(
            OaSignOnboardGenerationService.class);
    private static final long GENERATION_LEASE_MILLIS = 5L * 60L * 1000L;

    private final OaSignOnboardImportService importService;
    private final OaSignOnboardImportBatchMapper batchMapper;
    private final OaSignOnboardImportRowMapper rowMapper;
    private final OaSignTaskMapper taskMapper;
    private final OaSignPackageMapper packageMapper;
    private final OaSignPlanVersionMapper planVersionMapper;
    private final OaOnboardSignEventFactory eventFactory;
    private final OnboardSignScenarioRule onboardRule;
    private final OaSignTaskOrchestrator orchestrator;
    private final OaSignCompanyService companyService;
    private final OaSignOnboardDataRequestMapper dataRequestMapper;
    private final IOaSignPackageService packageService;
    private final OaSignSalarySourceService salarySources;

    public OaSignOnboardGenerationService(OaSignOnboardImportService importService,
            OaSignOnboardImportBatchMapper batchMapper,
            OaSignOnboardImportRowMapper rowMapper,
            OaSignTaskMapper taskMapper,
            OaSignPackageMapper packageMapper,
            OaSignPlanVersionMapper planVersionMapper,
            OaOnboardSignEventFactory eventFactory,
            OnboardSignScenarioRule onboardRule,
            OaSignTaskOrchestrator orchestrator,
            OaSignCompanyService companyService,
            OaSignOnboardDataRequestMapper dataRequestMapper,
            IOaSignPackageService packageService, OaSignSalarySourceService salarySources)
    {
        this.importService = importService;
        this.batchMapper = batchMapper;
        this.rowMapper = rowMapper;
        this.taskMapper = taskMapper;
        this.packageMapper = packageMapper;
        this.planVersionMapper = planVersionMapper;
        this.eventFactory = eventFactory;
        this.onboardRule = onboardRule;
        this.orchestrator = orchestrator;
        this.companyService = companyService;
        this.dataRequestMapper = dataRequestMapper;
        this.packageService = packageService;
        this.salarySources = salarySources;
    }

    public OaSignOnboardGenerateResult generate(Long batchId, OaSignOnboardGenerateRequest action,
            Long selectedShopDeptId)
    {
        importService.requireEnabled();
        OaSignOnboardImportBatch batch = importService.requireHrBatch(batchId, selectedShopDeptId);
        if (action == null || blank(action.getRequestId()) || action.getRequestId().length() > 64)
            throw new ServiceException("生成请求编号无效");
        if (action.getBatchVersion() == null || !Objects.equals(batch.getVersion(), action.getBatchVersion()))
            throw new ServiceException("导入批次已变化，请刷新后生成");
        Long initiatingOperatorUserId = SecurityUtils.getUserId();
        List<Long> rowIds = normalizeRowIds(action.getRowIds());
        List<OaSignOnboardImportRow> selected = rowMapper.selectForGeneration(batchId, rowIds);
        if (selected == null || selected.size() != rowIds.size())
            throw new ServiceException("生成行不存在或不属于当前批次");
        for (OaSignOnboardImportRow row : selected)
            importService.requireCurrentHrTaskOwnership(row, batch);
        Date staleBefore = new Date(System.currentTimeMillis() - GENERATION_LEASE_MILLIS);
        if (batchMapper.claimGeneration(batchId, batch.getVersion(), staleBefore) != 1)
            throw new ServiceException("导入批次正在被其他请求处理");
        batch = batchMapper.selectById(batchId);
        selected = rowMapper.selectForGeneration(batchId, rowIds);
        if (selected == null || selected.size() != rowIds.size())
            throw new ServiceException("生成行已变化，请刷新后重试");
        for (OaSignOnboardImportRow row : selected)
            importService.requireCurrentHrTaskOwnership(row, batch);

        OaSignOnboardGenerateResult result = new OaSignOnboardGenerateResult();
        List<OaSignOnboardGenerateResult.Item> items = new ArrayList<>();
        OaSignOnboardImportBatch generationBatch = batch;
        for (OaSignOnboardImportRow source : selected)
        {
            OaSignOnboardImportRow row = source;
            try
            {
                OaSignOnboardImportRow recoverySource = row;
                row = withCurrentOwnerTaskLock(generationBatch, recoverySource,
                        initiatingOperatorUserId, () -> recoverInterruptedGeneration(
                                generationBatch, recoverySource, staleBefore));
                if ("GENERATING".equals(row.getStatus()))
                {
                    OaSignOnboardGenerateResult.Item item = item(row);
                    item.setResult("BLOCKED");
                    item.setStatus("GENERATING");
                    item.setMessage("该行仍在生成租约内，本次不接管");
                    items.add(item);
                    continue;
                }
                if (row.getTaskId() == null || hasStagedSignatureFirstBinding(row))
                {
                    OaSignOnboardImportRow confirmationSource = row;
                    row = withCurrentOwnerTaskLock(generationBatch, confirmationSource,
                            initiatingOperatorUserId,
                            () -> applyConfirmations(confirmationSource, action));
                }
                items.add(generateOne(generationBatch, row, action.getRequestId(),
                        initiatingOperatorUserId));
            }
            catch (RuntimeException exception)
            {
                boolean existingClaim = "GENERATING".equals(row.getStatus());
                String claimedRequestId = existingClaim
                        ? row.getGenerationRequestId() : action.getRequestId();
                Long claimedVersion = row.getVersion() == null ? null
                        : existingClaim ? row.getVersion() : row.getVersion() + 1;
                try
                {
                    failWithCurrentOwner(generationBatch, row.getRowId(), claimedRequestId,
                            claimedVersion, failureCode(exception), initiatingOperatorUserId);
                }
                catch (ServiceException authorizationChanged)
                {
                    // A failed owner/source/shop lock means the initiating HR no longer has
                    // authority over this row. Leave the lease untouched for the new owner or
                    // the system recovery path; the old owner must not perform compensation.
                    throw authorizationChanged;
                }
                log.error("入职合同生成失败，batchId={}, rowId={}, requestId={}",
                        batchId, source == null ? null : source.getRowId(),
                        action.getRequestId(), exception);
                OaSignOnboardGenerateResult.Item item = item(source);
                item.setResult("FAILED");
                item.setStatus("GENERATE_FAILED");
                item.setMessage("生成失败，请刷新批次查看行错误后重试");
                items.add(item);
            }
        }
        result.setItems(items);
        summarize(result, items);
        importService.refreshSummary(batchId);
        return result;
    }

    /**
     * Repairs the only durable ambiguity left by a process crash after a row was claimed.
     *
     * <p>An already completed draft is rebound only through an exact source-event and package
     * join in the mapper. Otherwise the row is returned to the retryable state while preserving
     * its frozen source event version. A live owner is never pre-empted: both batch and row must
     * be older than the lease cutoff before this path is allowed to take over.</p>
     */
    OaSignOnboardImportRow recoverInterruptedGeneration(OaSignOnboardImportBatch batch,
            OaSignOnboardImportRow row, Date staleBefore)
    {
        if (row == null || !"GENERATING".equals(row.getStatus())) return row;
        if (staleBefore == null || row.getUpdateTime() == null
                || row.getUpdateTime().after(staleBefore)) return row;
        OaSignTask sourceTask = selectExactSourceTask(row);
        if (sourceTask != null && "PENDING_COMPANY".equals(sourceTask.getStatus())
                && row.getTaskId() != null && row.getPackageId() != null)
        {
            OaSignPackage signPackage = sourceTask.getPackageId() == null ? null
                    : packageMapper.selectOaSignPackageById(sourceTask.getPackageId());
            if (signPackage != null && "PREPARED_NOT_SENT".equals(
                    signPackage.getFinalConfirmationStatus()))
            {
                if (!validStagedRecoveryBinding(batch, row, sourceTask, signPackage))
                {
                    return failInterruptedRecovery(row, "CONFLICT",
                            "GENERATION_RECOVERY_SOURCE_MISMATCH");
                }
                if (rowMapper.completeGeneration(row.getRowId(), sourceTask.getTaskId(),
                        sourceTask.getPackageId(), row.getGenerationRequestId(),
                        row.getVersion()) == 1)
                {
                    return rowMapper.selectById(row.getRowId());
                }
                OaSignOnboardImportRow latest = rowMapper.selectById(row.getRowId());
                if (latest != null && "GENERATED".equals(latest.getStatus())
                        && Objects.equals(latest.getTaskId(), sourceTask.getTaskId())
                        && Objects.equals(latest.getPackageId(), sourceTask.getPackageId()))
                {
                    return latest;
                }
                throw new ServiceException("崩溃恢复时导入行已变化，请刷新后重试");
            }
        }
        if (sourceTask != null && "READY_TO_SEND".equals(sourceTask.getStatus()))
        {
            OaSignPackage signPackage = sourceTask.getPackageId() == null ? null
                    : packageMapper.selectOaSignPackageById(sourceTask.getPackageId());
            if (!validRecoveryBinding(batch, row, sourceTask, signPackage))
            {
                return failInterruptedRecovery(row, "CONFLICT",
                        "GENERATION_RECOVERY_SOURCE_MISMATCH");
            }
            if (rowMapper.recoverCompletedGeneration(row.getRowId(), batch.getBatchId(),
                    sourceTask.getTaskId(), sourceTask.getPackageId(), batch.getShopDeptId(),
                    row.getGenerationRequestId(), row.getVersion()) == 1)
            {
                return rowMapper.selectById(row.getRowId());
            }
            OaSignOnboardImportRow latest = rowMapper.selectById(row.getRowId());
            if (latest != null && Objects.equals(latest.getTaskId(), sourceTask.getTaskId())
                    && Objects.equals(latest.getPackageId(), sourceTask.getPackageId()))
            {
                return latest;
            }
            throw new ServiceException("崩溃恢复时导入行已变化，请刷新后重试");
        }
        return failInterruptedRecovery(row, "GENERATE_FAILED", "GENERATE_FAILED");
    }

    private OaSignOnboardImportRow failInterruptedRecovery(OaSignOnboardImportRow row,
            String status, String code)
    {
        LinkedHashSet<String> errors = new LinkedHashSet<>(importService.list(row.getErrorCodesJson()));
        errors.add(code);
        if (rowMapper.failGeneration(row.getRowId(), status, importService.json(errors),
                row.getGenerationRequestId(), row.getVersion()) != 1)
        {
            OaSignOnboardImportRow latest = rowMapper.selectById(row.getRowId());
            if (latest != null && !"GENERATING".equals(latest.getStatus())) return latest;
            throw new ServiceException("崩溃恢复时导入行已变化，请刷新后重试");
        }
        return rowMapper.selectById(row.getRowId());
    }

    private boolean validRecoveryBinding(OaSignOnboardImportBatch batch,
            OaSignOnboardImportRow row, OaSignTask task, OaSignPackage signPackage)
    {
        return batch != null && exactSourceTask(task, row)
                && Objects.equals(task.getShopDeptId(), batch.getShopDeptId())
                && Objects.equals(task.getPlanVersionId(), row.getPlanVersionId())
                && task.getPackageId() != null && signPackage != null
                && Objects.equals(signPackage.getPackageId(), task.getPackageId())
                && Objects.equals(signPackage.getTaskId(), task.getTaskId())
                && Objects.equals(signPackage.getEmployeeId(), row.getEmployeeId())
                && Objects.equals(signPackage.getShopDeptId(), batch.getShopDeptId())
                && Objects.equals(signPackage.getPlanVersionId(), row.getPlanVersionId())
                && OaSignPackageStatus.DRAFT.equals(signPackage.getStatus());
    }

    private boolean validStagedRecoveryBinding(OaSignOnboardImportBatch batch,
            OaSignOnboardImportRow row, OaSignTask task, OaSignPackage signPackage)
    {
        if (batch == null || !exactSourceTask(task, row)
                || !Objects.equals(row.getTaskId(), task.getTaskId())
                || !Objects.equals(row.getPackageId(), task.getPackageId())
                || !Objects.equals(task.getShopDeptId(), batch.getShopDeptId())
                || !Objects.equals(task.getPlanVersionId(), row.getPlanVersionId())
                || !"PENDING_COMPANY".equals(task.getStatus())
                || signPackage == null
                || !Objects.equals(signPackage.getPackageId(), task.getPackageId())
                || !Objects.equals(signPackage.getTaskId(), task.getTaskId())
                || !Objects.equals(signPackage.getEmployeeId(), row.getEmployeeId())
                || !Objects.equals(signPackage.getShopDeptId(), batch.getShopDeptId())
                || !Objects.equals(signPackage.getPlanVersionId(), row.getPlanVersionId())
                || !OaSignPackageStatus.PENDING_COMPANY.equals(signPackage.getStatus())
                || !OaSignSigningSequence.SIGNATURE_FIRST.equals(
                        signPackage.getSigningSequence())
                || !"PREPARED_NOT_SENT".equals(signPackage.getFinalConfirmationStatus())
                || !Objects.equals(signPackage.getLegalEntityIdSnapshot(),
                        row.getMatchedLegalEntityId())
                || !Objects.equals(signPackage.getSealIdSnapshot(),
                        row.getRecommendedSealId())
                || signPackage.getInitialSignedTime() == null
                || blank(signPackage.getSignatureSampleFileUrl())
                || !sha256Hash(signPackage.getSignatureSampleHash())
                || signPackage.getSignatureSampleTime() == null
                || blank(signPackage.getFinalDocumentVersion())
                || !sha256Hash(signPackage.getFinalDocumentRootHash())
                || signPackage.getFinalGeneratedTime() == null)
        {
            return false;
        }
        if (row.getDataRequestId() == null) return false;
        OaSignOnboardDataRequest request = dataRequestMapper.selectById(row.getDataRequestId());
        byte[] bytes = request == null ? null : request.getSignatureSampleBytes();
        return request != null
                && Objects.equals(request.getRequestId(), row.getDataRequestId())
                && Objects.equals(request.getRowId(), row.getRowId())
                && Objects.equals(request.getEmployeeId(), row.getEmployeeId())
                && "COMPLETED".equals(request.getStatus())
                && OaSignSigningSequence.signatureFirst(request.getSigningSequence())
                && !blank(request.getSignatureRequestId())
                && bytes != null && bytes.length > 0
                && request.getSignatureSampleTime() != null
                && Objects.equals(request.getSignatureSampleTime(),
                        signPackage.getSignatureSampleTime())
                && !blank(request.getSignatureSampleHash())
                && request.getSignatureSampleHash().equalsIgnoreCase(sha256(bytes))
                && request.getSignatureSampleHash().equalsIgnoreCase(
                        signPackage.getSignatureSampleHash());
    }

    private boolean sha256Hash(String value)
    {
        if (value == null || value.length() != 64) return false;
        for (int index = 0; index < value.length(); index++)
        {
            char current = value.charAt(index);
            if (!(current >= '0' && current <= '9')
                    && !(current >= 'a' && current <= 'f')
                    && !(current >= 'A' && current <= 'F')) return false;
        }
        return true;
    }

    private OaSignOnboardImportRow applyConfirmations(OaSignOnboardImportRow source,
            OaSignOnboardGenerateRequest action)
    {
        List<String> existingErrors = new ArrayList<>(importService.list(source.getErrorCodesJson()));
        if ("GENERATE_FAILED".equals(source.getStatus()))
        {
            existingErrors.removeIf(code -> Set.of("GENERATE_FAILED",
                    "PACKAGE_PERSONAL_FACTS_NOT_FROZEN", "SEND_PREPARATION_FAILED").contains(code));
            source.setErrorCodesJson(importService.json(existingErrors));
        }
        boolean historical = isHistorical(importService.snapshot(source));
        List<String> warnings = importService.list(source.getWarningCodesJson());
        String historicalReason = normalize(action.getHistoricalReason());
        String warningReason = normalize(action.getWarningReason());
        Boolean noExternal = Boolean.TRUE.equals(action.getNoExternalContractConfirmed())
                && !Boolean.TRUE.equals(source.getNoExternalContractConfirmed())
                        ? Boolean.TRUE : null;
        Boolean historicalConfirmed = historical && !blank(historicalReason)
                && (!Boolean.TRUE.equals(source.getHistoricalSupplement())
                        || !Objects.equals(normalize(source.getHistoricalReason()), historicalReason))
                                ? Boolean.TRUE : null;
        Boolean warningConfirmed = !warnings.isEmpty() && !blank(warningReason)
                && (!Boolean.TRUE.equals(source.getWarningConfirmed())
                        || !Objects.equals(normalize(source.getWarningReason()), warningReason))
                                ? Boolean.TRUE : null;
        if (Boolean.TRUE.equals(noExternal) || historicalConfirmed != null || warningConfirmed != null)
        {
            historicalReason = text(historicalReason, 500);
            warningReason = text(warningReason, 500);
            if (rowMapper.applyGenerationConfirmations(source.getRowId(), noExternal,
                    historicalConfirmed, historicalReason,
                    warningConfirmed, warningReason, source.getVersion()) != 1)
                throw new ServiceException("导入行已变化");
            source = rowMapper.selectById(source.getRowId());
        }
        List<String> missing = new ArrayList<>(importService.list(source.getMissingFieldsJson()));
        if (Boolean.TRUE.equals(source.getNoExternalContractConfirmed()))
            missing.remove("noExternalContractConfirmation");
        if (!historical || Boolean.TRUE.equals(source.getHistoricalSupplement())
                && !blank(source.getHistoricalReason())) missing.remove("historicalSupplementReason");
        if (warnings.isEmpty() || Boolean.TRUE.equals(source.getWarningConfirmed())
                && !blank(source.getWarningReason())) missing.remove("warningConfirmationReason");
        source.setMissingFieldsJson(importService.json(missing));
        source.setStatus(existingErrors.isEmpty() && missing.isEmpty()
                ? "READY_TO_GENERATE" : source.getStatus());
        if (!Objects.equals(source.getMissingFieldsJson(), rowMapper.selectById(source.getRowId()).getMissingFieldsJson())
                || "READY_TO_GENERATE".equals(source.getStatus()))
        {
            if (rowMapper.updateEditableWithVersion(source) != 1)
                throw new ServiceException("导入行已变化");
            source = rowMapper.selectById(source.getRowId());
        }
        return source;
    }

    private OaSignOnboardGenerateResult.Item generateOne(OaSignOnboardImportBatch batch,
            OaSignOnboardImportRow row, String requestId, Long initiatingOperatorUserId)
    {
        importService.requireCurrentHrTaskOwnership(row, batch);
        OaSignOnboardGenerateResult.Item result = item(row);
        boolean stagedSignatureFirst = hasStagedSignatureFirstBinding(row);
        boolean taskOnlyBinding = hasTaskOnlyBinding(row);
        if (row.getTaskId() != null && !stagedSignatureFirst && !taskOnlyBinding)
        {
            OaSignTask boundTask = selectExactSourceTask(row);
            if (!sameTask(boundTask, row.getTaskId()) || row.getPackageId() == null
                    || !Objects.equals(row.getPackageId(), boundTask.getPackageId()))
            {
                throw new ServiceException("导入行与已生成签约任务绑定不完整");
            }
            String sourceId = salarySources.requireConfirmed(batch, row, importService.snapshot(row));
            salarySources.bind(packageMapper.selectOaSignPackageById(row.getPackageId()), sourceId);
            result.setTaskId(row.getTaskId()); result.setPackageId(row.getPackageId());
            result.setResult("REUSED"); result.setStatus("GENERATED"); result.setMessage("已复用生成结果");
            return result;
        }
        if (!"READY_TO_GENERATE".equals(row.getStatus()))
        {
            result.setResult("BLOCKED"); result.setStatus(row.getStatus());
            result.setMessage("资料、确认或警告处理尚未完成");
            return result;
        }
        OaSignOnboardContractSnapshot snapshot = importService.snapshot(row);
        Set<String> companyErrors = companyGenerationErrors(row, snapshot);
        if (!companyErrors.isEmpty())
            return blockReadyRow(batch, row, initiatingOperatorUserId,
                    "COMPANY_CHANGED_REPREVIEW", companyErrors,
                    "公司或印章未完成安全匹配，请重新预览");
        SignCandidateUser candidate = importService.requireCandidate(row.getEmployeeId(),
                batch.getShopDeptId());
        if (!importService.profileFactsMatch(snapshot, candidate)
                || !importService.identityFactsMatch(batch, snapshot, candidate))
            return blockReadyRow(batch, row, initiatingOperatorUserId,
                    "PROFILE_CHANGED_REPREVIEW",
                    Set.of("PROFILE_CHANGED_REPREVIEW"), "员工档案已变化，请重新预览");
        Set<String> templateErrors = importService.templateGateErrors(row.getPlanVersionId(), snapshot);
        if (!templateErrors.isEmpty())
            return blockReadyRow(batch, row, initiatingOperatorUserId,
                    "PLAN_CHANGED_REPREVIEW", templateErrors,
                    "签约套餐文件不完整，请重新预览");
        OaSignTask sourceTask = selectExactSourceTask(row);
        if (taskOnlyBinding && (!sameTask(sourceTask, row.getTaskId())
                || !recoverableTaskOnlyBinding(sourceTask)))
        {
            throw new ServiceException("迁移签约任务处于不可安全恢复的半绑定状态");
        }
        OaSignTask existingOpenTask = taskMapper.selectOpenOnboardTaskByEmployeeId(row.getEmployeeId());
        if (existingOpenTask != null && !sameTask(existingOpenTask, sourceTask))
            return blockForExistingTask(batch, row, existingOpenTask,
                    initiatingOperatorUserId);
        OaSignOnboardImportRow claimSource = row;
        boolean stagedClaim = stagedSignatureFirst;
        boolean taskOnlyClaim = taskOnlyBinding;
        row = withCurrentOwnerTaskLock(batch, claimSource, initiatingOperatorUserId, () -> {
            int claimed = stagedClaim
                    ? rowMapper.claimStagedGeneration(claimSource.getRowId(),
                            claimSource.getTaskId(), claimSource.getPackageId(),
                            requestId, claimSource.getVersion())
                    : taskOnlyClaim
                            ? rowMapper.claimTaskOnlyGeneration(claimSource.getRowId(),
                                    claimSource.getTaskId(), requestId,
                                    claimSource.getVersion())
                            : rowMapper.claimGeneration(claimSource.getRowId(), requestId,
                                    claimSource.getVersion());
            if (claimed != 1)
                throw new ServiceException("导入行正在被其他请求生成");
            return rowMapper.selectById(claimSource.getRowId());
        });
        importService.requireCurrentHrTaskOwnership(row, batch);
        stagedSignatureFirst = hasStagedSignatureFirstBinding(row);
        sourceTask = selectExactSourceTask(row);
        existingOpenTask = taskMapper.selectOpenOnboardTaskByEmployeeId(row.getEmployeeId());
        if (existingOpenTask != null && !sameTask(existingOpenTask, sourceTask))
            return blockClaimedRowForExistingTask(
                    batch, row, existingOpenTask, initiatingOperatorUserId);
        snapshot = importService.snapshot(row);
        companyErrors = companyGenerationErrors(row, snapshot);
        if (!companyErrors.isEmpty())
            return blockClaimedRow(batch, row, initiatingOperatorUserId,
                    "COMPANY_CHANGED_REPREVIEW", companyErrors,
                    "公司或印章未完成安全匹配，请重新预览");
        candidate = importService.requireCandidate(row.getEmployeeId(), batch.getShopDeptId());
        if (!importService.profileFactsMatch(snapshot, candidate)
                || !importService.identityFactsMatch(batch, snapshot, candidate))
            return blockClaimedRow(batch, row, initiatingOperatorUserId,
                    "PROFILE_CHANGED_REPREVIEW", Set.of("PROFILE_CHANGED_REPREVIEW"),
                    "员工档案已变化，请重新预览");
        templateErrors = importService.templateGateErrors(row.getPlanVersionId(), snapshot);
        if (!templateErrors.isEmpty())
            return blockClaimedRow(batch, row, initiatingOperatorUserId,
                    "PLAN_CHANGED_REPREVIEW", templateErrors,
                    "签约套餐文件不完整，请重新预览");
        String salarySourceId = salarySources.requireConfirmed(batch, row, snapshot);
        SigningContext signing = signingContext(row);
        HrSignBusinessEvent event = eventFactory.create(batch, row, snapshot, candidate,
                initiatingOperatorUserId, requestId);
        var eventAttributes = event.getAttributes() == null
                ? new java.util.LinkedHashMap<String, Object>()
                : new java.util.LinkedHashMap<>(event.getAttributes());
        eventAttributes.put("signingSequence", signing.sequence());
        if (signing.dataRequest() != null)
            eventAttributes.put("signatureDataRequestId", signing.dataRequest().getRequestId());
        event.setAttributes(eventAttributes);
        OaSignDraftDecision currentDecision = onboardRule.decide(event);
        if (currentDecision.getAction() != OaSignDraftDecision.Action.CREATE_DRAFT
                || !Objects.equals(currentDecision.getPlanVersionId(), row.getPlanVersionId()))
        {
            throw new GenerationFailureException(
                    "PLAN_CHANGED_REPREVIEW", "签约方案已变化，请重新预览");
        }
        OaSignPlanVersion version = planVersionMapper.selectPlanVersionById(row.getPlanVersionId());
        if (version == null || !Objects.equals(version.getVersionHash(), row.getPlanVersionHash()))
        {
            throw new GenerationFailureException(
                    "PLAN_CHANGED_REPREVIEW", "签约方案已变化，请重新预览");
        }

        Long taskId;
        if (stagedSignatureFirst)
        {
            taskId = row.getTaskId();
        }
        else
        {
            taskId = orchestrator.orchestrate(event);
        }
        return finishClaimedGeneration(batch, row, signing, snapshot, currentDecision,
                taskId, stagedSignatureFirst, initiatingOperatorUserId, salarySourceId);
    }

    private OaSignOnboardGenerateResult.Item finishClaimedGeneration(
            OaSignOnboardImportBatch batch, OaSignOnboardImportRow row,
            SigningContext signing, OaSignOnboardContractSnapshot snapshot,
            OaSignDraftDecision currentDecision, Long taskId, boolean stagedSignatureFirst,
            Long initiatingOperatorUserId, String salarySourceId)
    {
        OaSignOnboardGenerateResult.Item result = item(row);
        return orchestrator.withLockedExcelImportTask(row, batch, taskId,
                initiatingOperatorUserId, task -> {
            if (!exactSourceTask(task, row))
            {
                throw new ServiceException("Excel导入签约任务来源已变化");
            }
            String expectedTaskStatus = stagedSignatureFirst
                    ? "PENDING_COMPANY" : "READY_TO_SEND";
            if (task.getPackageId() == null
                    || !Objects.equals(task.getPlanVersionId(), row.getPlanVersionId())
                    || !Objects.equals(row.getPackageId(), stagedSignatureFirst
                            ? task.getPackageId() : row.getPackageId())
                    || !expectedTaskStatus.equals(task.getStatus()))
            {
                throw new GenerationFailureException(
                        blank(task.getFailureCode())
                                ? "GENERATE_FAILED" : task.getFailureCode(),
                        stagedSignatureFirst
                                ? "签约任务不在等待HR选择公司阶段"
                                : "签约任务未生成到待发送状态");
            }
            OaSignPackage frozenPackage = packageMapper.selectOaSignPackageById(
                    task.getPackageId());
            if (OaSignSigningSequence.signatureFirst(signing.sequence()))
            {
                if (frozenPackage == null)
                    throw new ServiceException("签约包草稿不存在");
                OaSignOnboardDataRequest data = signing.dataRequest();
                if (stagedSignatureFirst)
                {
                    packageService.prepareStagedSignatureFirstCandidateForSystem(
                            frozenPackage.getPackageId(), task.getTaskId(),
                            initiatingOperatorUserId, currentDecision.getDraftPackage(),
                            data.getRequestId(), data.getSignatureRequestId(),
                            data.getSignatureSampleBytes(), data.getSignatureSampleHash(),
                            data.getSignatureSampleTime(), frozenPackage.getDocumentVersion(),
                            frozenPackage.getVersion());
                }
                else
                {
                    packageService.prepareSignatureFirstCandidateForSystem(
                            frozenPackage.getPackageId(), task.getTaskId(),
                            initiatingOperatorUserId, data.getRequestId(),
                            data.getSignatureRequestId(), data.getSignatureSampleBytes(),
                            data.getSignatureSampleHash(), data.getSignatureSampleTime(),
                            frozenPackage.getDocumentVersion(), frozenPackage.getVersion());
                }
                frozenPackage = packageMapper.selectOaSignPackageById(task.getPackageId());
            }
            if (frozenPackage == null
                    || !Objects.equals(frozenPackage.getTaskId(), task.getTaskId())
                    || !Objects.equals(frozenPackage.getEmployeeId(), row.getEmployeeId())
                    || !Objects.equals(frozenPackage.getShopDeptId(), batch.getShopDeptId())
                    || !Objects.equals(frozenPackage.getPlanVersionId(), row.getPlanVersionId())
                    || !Objects.equals(signing.sequence(), frozenPackage.getSigningSequence())
                    || !Objects.equals(frozenPackage.getLegalEntityIdSnapshot(),
                            snapshot.getMatchedLegalEntityId())
                    || !Objects.equals(frozenPackage.getSealIdSnapshot(),
                            snapshot.getMatchedSealId())
                    || !Objects.equals(normalize(frozenPackage.getLegalEntityNameSnapshot()),
                            normalize(snapshot.getMatchedLegalEntityName()))
                    || !Objects.equals(normalize(frozenPackage.getLegalEntityAddressSnapshot()),
                            normalize(snapshot.getMatchedRegisteredAddress()))
                    || !Objects.equals(normalize(frozenPackage.getSealImageHashSnapshot()),
                            normalize(snapshot.getMatchedSealImageHash()))
                    || !Objects.equals(normalize(snapshot.getStudentStatus()),
                            normalize(frozenPackage.getStudentStatusSnapshot()))
                    || !Objects.equals(normalize(snapshot.getRetirementStatus()),
                            normalize(frozenPackage.getRetirementStatusSnapshot()))
                    || !Objects.equals(normalize(snapshot.getIncomeStartYearMonth()),
                            normalize(frozenPackage.getIncomeStartYearMonth())))
            {
                throw new GenerationFailureException(
                        "PACKAGE_PERSONAL_FACTS_NOT_FROZEN", "签约包个人事实冻结失败");
            }
            if (OaSignSigningSequence.signatureFirst(signing.sequence())
                    && (!Objects.equals(signing.dataRequest().getSignatureSampleHash(),
                            frozenPackage.getSignatureSampleHash())
                    || frozenPackage.getSignatureSampleTime() == null
                    || blank(frozenPackage.getSignatureSampleFileUrl())
                    || blank(frozenPackage.getFinalDocumentVersion())
                    || blank(frozenPackage.getFinalDocumentRootHash())
                    || frozenPackage.getFinalGeneratedTime() == null))
            {
                throw new GenerationFailureException("PACKAGE_SIGNATURE_SAMPLE_NOT_FROZEN",
                        "任务级手写签名样本或待确认候选未冻结");
            }
            if (stagedSignatureFirst
                    && (!OaSignPackageStatus.PENDING_COMPANY.equals(frozenPackage.getStatus())
                    || !"PREPARED_NOT_SENT".equals(
                            frozenPackage.getFinalConfirmationStatus())))
            {
                throw new GenerationFailureException(
                        "PACKAGE_FINAL_NOT_PREPARED", "最终合同生成后状态未正确冻结");
            }
            if (!stagedSignatureFirst
                    && !OaSignPackageStatus.DRAFT.equals(frozenPackage.getStatus()))
            {
                throw new GenerationFailureException(
                        "PACKAGE_DRAFT_STATE_CHANGED", "签约包草稿状态已变化");
            }
            salarySources.bind(frozenPackage, salarySourceId);
            if (rowMapper.completeGeneration(row.getRowId(), taskId, task.getPackageId(),
                    row.getGenerationRequestId(), row.getVersion()) != 1)
            {
                throw new ServiceException("生成结果保存失败");
            }
            result.setTaskId(taskId);
            result.setPackageId(task.getPackageId());
            result.setResult("GENERATED");
            result.setStatus("GENERATED");
            result.setMessage("合同已生成，尚未发送");
            return result;
        });
    }

    private OaSignOnboardGenerateResult.Item blockForExistingTask(
            OaSignOnboardImportBatch batch, OaSignOnboardImportRow row,
            OaSignTask existingTask, Long initiatingOperatorUserId)
    {
        LinkedHashSet<String> errors = new LinkedHashSet<>(importService.list(row.getErrorCodesJson()));
        errors.add("EXISTING_OPEN_ONBOARD_TASK");
        row.setErrorCodesJson(importService.json(errors));
        row.setStatus("CONFLICT");
        withCurrentOwnerTaskLock(batch, row, initiatingOperatorUserId, () -> {
            if (rowMapper.updateEditableWithVersion(row) != 1)
                throw new ServiceException("导入行已变化，请刷新后重试");
            return null;
        });
        return existingTaskBlockedItem(row, existingTask);
    }

    private OaSignOnboardGenerateResult.Item blockClaimedRowForExistingTask(
            OaSignOnboardImportBatch batch, OaSignOnboardImportRow row,
            OaSignTask existingTask, Long initiatingOperatorUserId)
    {
        LinkedHashSet<String> errors = new LinkedHashSet<>(importService.list(row.getErrorCodesJson()));
        errors.add("EXISTING_OPEN_ONBOARD_TASK");
        writeClaimedFailure(batch, row, initiatingOperatorUserId,
                "CONFLICT", importService.json(errors));
        return existingTaskBlockedItem(row, existingTask);
    }

    private OaSignOnboardGenerateResult.Item existingTaskBlockedItem(OaSignOnboardImportRow row,
            OaSignTask existingTask)
    {
        OaSignOnboardGenerateResult.Item result = item(row);
        result.setResult("BLOCKED");
        result.setStatus("CONFLICT");
        result.setMessage("员工已有开放的入职签约任务（" + existingTask.getStatus()
                + "），本次未生成");
        return result;
    }

    private OaSignOnboardGenerateResult.Item blockReadyRow(
            OaSignOnboardImportBatch batch, OaSignOnboardImportRow row,
            Long initiatingOperatorUserId, String status, Set<String> codes, String message)
    {
        LinkedHashSet<String> errors = new LinkedHashSet<>(importService.list(row.getErrorCodesJson()));
        errors.addAll(codes);
        row.setErrorCodesJson(importService.json(errors));
        row.setStatus(status);
        withCurrentOwnerTaskLock(batch, row, initiatingOperatorUserId, () -> {
            if (rowMapper.updateEditableWithVersion(row) != 1)
                throw new ServiceException("导入行已变化，请刷新后重试");
            return null;
        });
        OaSignOnboardGenerateResult.Item result = item(row);
        result.setResult("BLOCKED"); result.setStatus(status); result.setMessage(message);
        return result;
    }

    private OaSignOnboardGenerateResult.Item blockClaimedRow(
            OaSignOnboardImportBatch batch, OaSignOnboardImportRow row,
            Long initiatingOperatorUserId, String status, Set<String> codes, String message)
    {
        LinkedHashSet<String> errors = new LinkedHashSet<>(importService.list(row.getErrorCodesJson()));
        errors.addAll(codes);
        writeClaimedFailure(batch, row, initiatingOperatorUserId,
                status, importService.json(errors));
        OaSignOnboardGenerateResult.Item result = item(row);
        result.setResult("BLOCKED"); result.setStatus(status); result.setMessage(message);
        return result;
    }

    private void failWithCurrentOwner(OaSignOnboardImportBatch batch, Long rowId,
            String expectedRequestId, Long expectedVersion, String code,
            Long initiatingOperatorUserId)
    {
        if (rowId == null || expectedRequestId == null || expectedVersion == null) return;
        OaSignOnboardImportRow latest = rowMapper.selectById(rowId);
        if (latest == null || !"GENERATING".equals(latest.getStatus())
                || !Objects.equals(latest.getGenerationRequestId(), expectedRequestId)
                || !Objects.equals(latest.getVersion(), expectedVersion))
        {
            return;
        }
        LinkedHashSet<String> errors = new LinkedHashSet<>(
                importService.list(latest.getErrorCodesJson()));
        errors.add(code);
        writeClaimedFailure(batch, latest, initiatingOperatorUserId,
                failureStatus(code), importService.json(errors));
    }

    private void writeClaimedFailure(OaSignOnboardImportBatch batch,
            OaSignOnboardImportRow row, Long initiatingOperatorUserId,
            String status, String errorsJson)
    {
        OaSignTask exactTask = selectExactSourceTask(row);
        if (exactTask == null)
        {
            requireClaimedFailureWrite(row, status, errorsJson);
            return;
        }
        orchestrator.withLockedExcelImportTask(row, batch, exactTask.getTaskId(),
                initiatingOperatorUserId, locked -> {
                    requireClaimedFailureWrite(row, status, errorsJson);
                    return null;
                });
    }

    private void requireClaimedFailureWrite(OaSignOnboardImportRow row,
            String status, String errorsJson)
    {
        if (rowMapper.failGeneration(row.getRowId(), status, errorsJson,
                row.getGenerationRequestId(), row.getVersion()) != 1)
        {
            throw new ServiceException("导入行已变化，请刷新后重试");
        }
    }

    <T> T withCurrentOwnerTaskLock(OaSignOnboardImportBatch batch,
            OaSignOnboardImportRow row, Long initiatingOperatorUserId, Supplier<T> work)
    {
        if (work == null) throw new ServiceException("Excel导入写入操作不能为空");
        OaSignTask exactTask = selectExactSourceTask(row);
        if (exactTask == null) return work.get();
        return orchestrator.withLockedExcelImportTask(row, batch, exactTask.getTaskId(),
                initiatingOperatorUserId, locked -> work.get());
    }

    private String failureCode(RuntimeException exception)
    {
        return exception instanceof GenerationFailureException generationFailure
                ? generationFailure.code : "GENERATE_FAILED";
    }

    private String failureStatus(String code)
    {
        return "PLAN_CHANGED_REPREVIEW".equals(code) ? "PLAN_CHANGED_REPREVIEW"
                : "PROFILE_CHANGED_REPREVIEW".equals(code)
                        ? "PROFILE_CHANGED_REPREVIEW" : "GENERATE_FAILED";
    }

    void fail(OaSignOnboardImportRow row, String code)
    {
        if (row == null || row.getRowId() == null) return;
        fail(row.getRowId(), row.getGenerationRequestId(), row.getVersion(), code);
    }

    void fail(Long rowId, String expectedOwner, Long expectedVersion, String code)
    {
        if (rowId == null || expectedOwner == null || expectedVersion == null) return;
        OaSignOnboardImportRow latest = rowMapper.selectById(rowId);
        if (latest != null && "GENERATING".equals(latest.getStatus())
                && Objects.equals(latest.getGenerationRequestId(), expectedOwner)
                && Objects.equals(latest.getVersion(), expectedVersion))
        {
            LinkedHashSet<String> errors = new LinkedHashSet<>(
                    importService.list(latest.getErrorCodesJson()));
            errors.add(code);
            rowMapper.failGeneration(latest.getRowId(), "PLAN_CHANGED_REPREVIEW".equals(code)
                            ? "PLAN_CHANGED_REPREVIEW"
                            : "PROFILE_CHANGED_REPREVIEW".equals(code)
                            ? "PROFILE_CHANGED_REPREVIEW" : "GENERATE_FAILED",
                    importService.json(errors), latest.getGenerationRequestId(), latest.getVersion());
        }
    }

    private OaSignTask selectExactSourceTask(OaSignOnboardImportRow row)
    {
        if (row == null || row.getRowId() == null || row.getEmployeeId() == null
                || row.getSourceEventVersion() == null) return null;
        OaSignTask query = new OaSignTask();
        query.setScenario("ONBOARD");
        query.setEmployeeId(row.getEmployeeId());
        query.setSourceType(OaOnboardSignEventFactory.SOURCE_TYPE);
        query.setSourceBusinessId(String.valueOf(row.getRowId()));
        query.setSourceEventVersion(String.valueOf(row.getSourceEventVersion()));
        List<OaSignTask> exactTasks = taskMapper.selectExactTasksBySourceEvent(query);
        if (exactTasks != null && exactTasks.size() > 1)
            throw new ServiceException("同一Excel来源事件存在多个签约任务");
        OaSignTask task = taskMapper.selectCanonicalTaskBySourceEvent(query);
        if (exactTasks != null && exactTasks.size() == 1
                && !sameTask(exactTasks.get(0), task))
            throw new ServiceException("Excel导入签约任务来源数据不规范");
        if (task != null && !exactSourceTask(task, row))
            throw new ServiceException("Excel导入签约任务来源数据不规范");
        return task;
    }

    private boolean exactSourceTask(OaSignTask task, OaSignOnboardImportRow row)
    {
        return task != null && row != null
                && "ONBOARD".equalsIgnoreCase(task.getScenario())
                && Objects.equals(task.getEmployeeId(), row.getEmployeeId())
                && OaOnboardSignEventFactory.SOURCE_TYPE.equalsIgnoreCase(task.getSourceType())
                && Objects.equals(task.getSourceBusinessId(), String.valueOf(row.getRowId()))
                && Objects.equals(task.getSourceEventVersion(),
                        String.valueOf(row.getSourceEventVersion()));
    }

    private boolean sameTask(OaSignTask left, OaSignTask right)
    {
        return left != null && right != null && left.getTaskId() != null
                && Objects.equals(left.getTaskId(), right.getTaskId());
    }

    private boolean sameTask(OaSignTask task, Long taskId)
    {
        return task != null && taskId != null && Objects.equals(task.getTaskId(), taskId);
    }

    private boolean hasTaskOnlyBinding(OaSignOnboardImportRow row)
    {
        return row != null && row.getTaskId() != null && row.getPackageId() == null;
    }

    private boolean recoverableTaskOnlyBinding(OaSignTask task)
    {
        if (task == null || blank(task.getStatus())) return false;
        if (Set.of("NEW", "NEEDS_DATA", "FAILED").contains(task.getStatus()))
            return task.getPackageId() == null;
        return Set.of("DRAFT_CREATED", "READY_TO_SEND").contains(task.getStatus())
                && task.getPackageId() != null;
    }

    private OaSignOnboardGenerateResult.Item item(OaSignOnboardImportRow row)
    {
        OaSignOnboardGenerateResult.Item item = new OaSignOnboardGenerateResult.Item();
        item.setRowId(row.getRowId()); item.setEmployeeId(row.getEmployeeId());
        item.setTaskId(row.getTaskId()); item.setPackageId(row.getPackageId());
        return item;
    }

    private void summarize(OaSignOnboardGenerateResult result,
            List<OaSignOnboardGenerateResult.Item> items)
    {
        result.setTotalCount(items.size());
        for (OaSignOnboardGenerateResult.Item item : items)
        {
            switch (item.getResult())
            {
                case "GENERATED" -> result.setGeneratedCount(result.getGeneratedCount() + 1);
                case "REUSED" -> result.setReusedCount(result.getReusedCount() + 1);
                case "BLOCKED" -> result.setBlockedCount(result.getBlockedCount() + 1);
                default -> result.setFailedCount(result.getFailedCount() + 1);
            }
        }
    }

    private List<Long> normalizeRowIds(List<Long> rowIds)
    {
        if (rowIds == null || rowIds.isEmpty()) throw new ServiceException("请选择要生成的导入行");
        if (rowIds.size() > 100) throw new ServiceException("一次最多生成100行");
        LinkedHashSet<Long> values = new LinkedHashSet<>();
        for (Long rowId : rowIds)
        {
            if (rowId == null || rowId <= 0) throw new ServiceException("导入行编号无效");
            values.add(rowId);
        }
        return new ArrayList<>(values);
    }

    private Set<String> companyGenerationErrors(OaSignOnboardImportRow row,
            OaSignOnboardContractSnapshot snapshot)
    {
        LinkedHashSet<String> errors = new LinkedHashSet<>();
        String requiredSalaryVersion = OaOnboardSalaryVersionPolicy.requiredSalaryVersion(
                snapshot.getSocialTypeCode());
        if (requiredSalaryVersion == null
                || !requiredSalaryVersion.equals(
                        OaOnboardSalaryVersionPolicy.normalizeSalaryVersion(
                                snapshot.getSalaryVersion())))
            errors.add("SALARY_SOCIAL_MAPPING_CHANGED");
        if (!Objects.equals(row.getCompanyMatchPolicyVersion(),
                companyService.currentMatchPolicyVersion()))
        {
            errors.add("COMPANY_MATCH_POLICY_CHANGED_REPREVIEW");
            return errors;
        }
        if (row.getMatchedLegalEntityId() == null
                || !Objects.equals(row.getMatchedLegalEntityId(),
                        snapshot.getMatchedLegalEntityId()))
        {
            errors.add("COMPANY_MATCH_REQUIRES_HR");
            return errors;
        }
        SysLegalEntity entity;
        try
        {
            entity = companyService.requireContractReadyEntity(row.getMatchedLegalEntityId());
        }
        catch (ServiceException exception)
        {
            errors.add("COMPANY_MASTER_DATA_INCOMPLETE");
            return errors;
        }
        if (!Objects.equals(row.getCompanyMasterVersion(), entity.getVersion())
                || !same(snapshot.getMatchedLegalEntityCode(), entity.getLegalEntityCode())
                || !same(snapshot.getMatchedLegalEntityName(), entity.getLegalEntityName())
                || !same(snapshot.getMatchedUnifiedSocialCreditCode(),
                        entity.getUnifiedSocialCreditCode())
                || !same(snapshot.getMatchedRegisteredAddress(), entity.getRegisteredAddress())
                || !same(snapshot.getMatchedLegalRepresentative(),
                        entity.getLegalRepresentative()))
            errors.add("COMPANY_MASTER_CHANGED_REPREVIEW");
        if (row.getRecommendedSealId() == null)
        {
            errors.add("COMPANY_SEAL_REQUIRES_HR");
            return errors;
        }
        try
        {
            OaCompanySealConfig seal = companyService.requireContractReadySeal(
                    row.getRecommendedSealId(), row.getMatchedLegalEntityId());
            if (!Objects.equals(snapshot.getMatchedSealId(), seal.getSealId())
                    || !same(snapshot.getMatchedSealName(), seal.getSealName())
                    || !same(snapshot.getMatchedSealImageUrl(), seal.getSealImageUrl())
                    || !same(snapshot.getMatchedSealImageHash(), seal.getSealImageHash()))
                errors.add("COMPANY_SEAL_CHANGED_REPREVIEW");
        }
        catch (ServiceException exception)
        {
            errors.add("COMPANY_SEAL_CHANGED_REPREVIEW");
        }
        return errors;
    }

    private SigningContext signingContext(OaSignOnboardImportRow row)
    {
        if (row == null || row.getDataRequestId() == null)
            return new SigningContext(OaSignSigningSequence.COMPANY_FIRST, null);
        OaSignOnboardDataRequest request = dataRequestMapper.selectById(row.getDataRequestId());
        if (request == null || !Objects.equals(request.getRowId(), row.getRowId())
                || !Objects.equals(request.getEmployeeId(), row.getEmployeeId())
                || !"COMPLETED".equals(request.getStatus()))
            throw new ServiceException("员工事实确认任务尚未完成");
        String sequence = OaSignSigningSequence.normalize(request.getSigningSequence());
        if (OaSignSigningSequence.signatureFirst(sequence))
        {
            importService.requireEmployeeConfirmationCurrent(request, row,
                    importService.submittedEmployeeConfirmationValues(request));
            byte[] bytes = request.getSignatureSampleBytes();
            if (bytes == null || bytes.length == 0 || blank(request.getSignatureSampleHash())
                    || request.getSignatureSampleTime() == null
                    || blank(request.getSignatureRequestId())
                    || !request.getSignatureSampleHash().equalsIgnoreCase(sha256(bytes)))
                throw new ServiceException("任务级手写签名样本不完整或校验不一致");
        }
        return new SigningContext(sequence, request);
    }

    private boolean hasStagedSignatureFirstBinding(OaSignOnboardImportRow row)
    {
        if (row == null || row.getTaskId() == null || row.getPackageId() == null
                || row.getDataRequestId() == null
                || Set.of("GENERATED", "SENT", "PARTIAL_SENT").contains(row.getStatus()))
        {
            return false;
        }
        OaSignOnboardDataRequest request = dataRequestMapper.selectById(row.getDataRequestId());
        if (request == null || !Objects.equals(request.getRowId(), row.getRowId())
                || !Objects.equals(request.getEmployeeId(), row.getEmployeeId())
                || !OaSignSigningSequence.signatureFirst(request.getSigningSequence()))
        {
            return false;
        }
        OaSignTask task = taskMapper.selectOaSignTaskById(row.getTaskId());
        OaSignPackage signPackage = packageMapper.selectOaSignPackageById(row.getPackageId());
        return task != null && signPackage != null && exactSourceTask(task, row)
                && Objects.equals(task.getPackageId(), row.getPackageId())
                && Objects.equals(signPackage.getTaskId(), row.getTaskId())
                && Objects.equals(signPackage.getEmployeeId(), row.getEmployeeId())
                && OaSignSigningSequence.SIGNATURE_FIRST.equals(
                        signPackage.getSigningSequence())
                && Set.of("PENDING_SIGN", "VIEWED", "PENDING_COMPANY")
                        .contains(task.getStatus())
                && Set.of(OaSignPackageStatus.PENDING_SIGN,
                        OaSignPackageStatus.PART_VIEWED,
                        OaSignPackageStatus.PENDING_COMPANY)
                        .contains(signPackage.getStatus());
    }

    private String sha256(byte[] bytes)
    {
        try
        {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        }
        catch (NoSuchAlgorithmException exception)
        {
            throw new IllegalStateException("签名校验算法不可用", exception);
        }
    }

    private record SigningContext(String sequence, OaSignOnboardDataRequest dataRequest) {}

    private static final class GenerationFailureException extends RuntimeException
    {
        private static final long serialVersionUID = 1L;
        private final String code;

        private GenerationFailureException(String code, String message)
        {
            super(message);
            this.code = code;
        }
    }

    private boolean same(String left, String right)
    {
        return Objects.equals(normalize(left), normalize(right));
    }

    private boolean isHistorical(OaSignOnboardContractSnapshot snapshot)
    {
        return snapshot.getContractStartDate() != null
                && snapshot.getContractStartDate().isBefore(LocalDate.now());
    }
    private String normalize(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private String text(String value, int max) { value = value == null ? null : value.trim(); if (value != null && value.length() > max) throw new ServiceException("确认原因不能超过" + max + "个字符"); return blank(value) ? null : value; }
    private boolean blank(String value) { return value == null || value.isBlank(); }
}
