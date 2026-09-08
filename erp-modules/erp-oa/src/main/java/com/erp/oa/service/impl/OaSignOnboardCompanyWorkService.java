package com.erp.oa.service.impl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.security.utils.SecurityUtils;
import com.erp.oa.constant.OaSignSigningSequence;
import com.erp.oa.domain.OaSignOnboardDataRequest;
import com.erp.oa.domain.OaSignOnboardImportBatch;
import com.erp.oa.domain.OaSignOnboardImportRow;
import com.erp.oa.domain.dto.OaSignOnboardCompanyWorkRequest;
import com.erp.oa.domain.dto.OaSignOnboardGenerateRequest;
import com.erp.oa.domain.dto.OaSignOnboardImportRowUpdateRequest;
import com.erp.oa.domain.vo.OaSignOnboardCompanyWorkResult;
import com.erp.oa.domain.vo.OaSignOnboardCompanyWorkView;
import com.erp.oa.domain.vo.OaSignOnboardGenerateResult;
import com.erp.oa.domain.vo.OaSignOnboardImportBatchView;
import com.erp.oa.domain.vo.OaSignOnboardImportRowView;
import com.erp.oa.mapper.OaSignOnboardDataRequestMapper;
import com.erp.oa.mapper.OaSignOnboardImportBatchMapper;
import com.erp.oa.mapper.OaSignOnboardImportRowMapper;
import com.erp.oa.mapper.OaDeptScopeMapper;
import com.erp.oa.domain.vo.OaSignCompanyOptions;

/**
 * Bridges completed employee fact/signature requests into the contract task centre.
 *
 * <p>The employee's real staged package already exists before company selection. Execution
 * reuses that same task/package and the existing versioned onboarding generation services;
 * company master, seal image, employee facts, plan files and source versions are revalidated at
 * the write edge.</p>
 */
@Service
public class OaSignOnboardCompanyWorkService
{
    private static final int MAX_ITEMS = 20;
    private static final int MAX_LIST_ITEMS = 100;
    private static final long GENERATION_LEASE_MILLIS = 5L * 60L * 1000L;

    private final OaSignOnboardImportService importService;
    private final OaSignOnboardGenerationService generationService;
    private final OaSignOnboardImportBatchMapper batchMapper;
    private final OaSignOnboardImportRowMapper rowMapper;
    private final OaSignOnboardDataRequestMapper dataRequestMapper;
    private final OaSignCompanyService companyService;
    private final OaDeptScopeMapper deptScopeMapper;
    private final OaSignScopeService signScopeService;
    private final OaSignHrAccessService hrAccessService;
    private final TransactionTemplate transactionTemplate;

    public OaSignOnboardCompanyWorkService(OaSignOnboardImportService importService,
            OaSignOnboardGenerationService generationService,
            OaSignOnboardImportBatchMapper batchMapper,
            OaSignOnboardImportRowMapper rowMapper,
            OaSignOnboardDataRequestMapper dataRequestMapper,
            OaSignCompanyService companyService,
            OaDeptScopeMapper deptScopeMapper,
            OaSignScopeService signScopeService,
            OaSignHrAccessService hrAccessService,
            PlatformTransactionManager transactionManager)
    {
        this.importService = importService;
        this.generationService = generationService;
        this.batchMapper = batchMapper;
        this.rowMapper = rowMapper;
        this.dataRequestMapper = dataRequestMapper;
        this.companyService = companyService;
        this.deptScopeMapper = deptScopeMapper;
        this.signScopeService = signScopeService;
        this.hrAccessService = hrAccessService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public OaSignCompanyOptions options(Long legalEntityId)
    {
        importService.requireEnabled();
        hrAccessService.requireCurrentHr();
        OaSignCompanyOptions options = new OaSignCompanyOptions();
        List<com.erp.system.api.domain.SysLegalEntity> entities =
                deptScopeMapper.selectActiveLegalEntities();
        List<com.erp.system.api.domain.SysLegalEntity> readyEntities = entities == null
                ? List.of() : entities.stream()
                        .filter(Objects::nonNull)
                        .filter(entity -> companyService.contractMasterMissingFields(entity).isEmpty())
                        .toList();
        options.setLegalEntities(readyEntities);
        if (legalEntityId == null)
        {
            options.setSeals(List.of());
            return options;
        }
        companyService.requireContractReadyEntity(legalEntityId);
        OaSignCompanyService.SealRecommendation recommendation =
                companyService.recommendContractSeal(legalEntityId);
        List<com.erp.oa.domain.OaCompanySealConfig> readySeals = new ArrayList<>();
        for (com.erp.oa.domain.OaCompanySealConfig seal : recommendation.getCandidates())
        {
            try
            {
                readySeals.add(companyService.requireContractReadySeal(
                        seal.getSealId(), legalEntityId));
            }
            catch (ServiceException ignored)
            {
                // Options are executable choices, not a seal-maintenance diagnostic view.
            }
        }
        options.setSeals(readySeals);
        Long recommendedSealId = recommendation.getSelectedSeal() == null ? null
                : recommendation.getSelectedSeal().getSealId();
        options.setRecommendedSealId(readySeals.stream()
                .anyMatch(seal -> Objects.equals(seal.getSealId(), recommendedSealId))
                        ? recommendedSealId : null);
        options.setCompanySealRequired(Boolean.TRUE);
        return options;
    }

    public OaSignOnboardCompanyWorkView list(Long selectedShopDeptId)
    {
        importService.requireEnabled();
        hrAccessService.requireCurrentHr();
        List<Long> scopeDeptIds = signScopeService.resolveScopeDeptIds(selectedShopDeptId);
        Long assignedHrUserId = hrAccessService.currentTaskOwnerFilter();
        List<OaSignOnboardImportRow> storedRows =
                rowMapper.selectSignatureFirstCompanyWorkRows(
                        assignedHrUserId, scopeDeptIds, MAX_LIST_ITEMS);
        if (storedRows == null) storedRows = List.of();
        List<Long> orderedBatchIds = storedRows.stream()
                .filter(Objects::nonNull)
                .map(OaSignOnboardImportRow::getBatchId)
                .filter(Objects::nonNull).distinct().toList();
        List<OaSignOnboardCompanyWorkView.Item> items = new ArrayList<>();
        if (!orderedBatchIds.isEmpty())
        {
            Map<Long, OaSignOnboardImportBatch> batchesById =
                    loadCompanyWorkBatches(orderedBatchIds, scopeDeptIds);
            Map<Long, List<OaSignOnboardImportRowView>> viewsByBatch =
                    importService.companyWorkRowViewsByBatch(storedRows, batchesById);
            for (Long batchId : orderedBatchIds)
            {
                OaSignOnboardImportBatch batch = batchesById.get(batchId);
                for (OaSignOnboardImportRowView row :
                        viewsByBatch.getOrDefault(batchId, List.of()))
                {
                    if (!isCompanyWorkRow(row)) continue;
                    items.add(toWorkItem(batch, row));
                    if (items.size() >= MAX_LIST_ITEMS) break;
                }
                if (items.size() >= MAX_LIST_ITEMS) break;
            }
        }
        OaSignOnboardCompanyWorkView result = new OaSignOnboardCompanyWorkView();
        result.setItems(items);
        result.setTotalCount(items.size());
        return result;
    }

    private Map<Long, OaSignOnboardImportBatch> loadCompanyWorkBatches(
            List<Long> batchIds, List<Long> scopeDeptIds)
    {
        List<OaSignOnboardImportBatch> batches = batchMapper.selectByIds(batchIds);
        Map<Long, OaSignOnboardImportBatch> batchesById = batches == null
                ? new LinkedHashMap<>()
                : batches.stream()
                        .filter(Objects::nonNull)
                        .filter(batch -> batch.getBatchId() != null)
                        .collect(Collectors.toMap(OaSignOnboardImportBatch::getBatchId,
                                Function.identity(), (left, right) -> left,
                                LinkedHashMap::new));
        for (Long batchId : batchIds)
        {
            OaSignOnboardImportBatch batch = batchesById.get(batchId);
            if (batch == null || batch.getShopDeptId() == null
                    || scopeDeptIds != null && !scopeDeptIds.isEmpty()
                    && !scopeDeptIds.contains(batch.getShopDeptId()))
                throw new ServiceException("导入批次不存在或无权访问");
        }
        return batchesById;
    }

    public OaSignOnboardCompanyWorkResult preview(OaSignOnboardCompanyWorkRequest action,
            Long selectedShopDeptId)
    {
        requireAction(action);
        OaSignOnboardCompanyWorkResult result = new OaSignOnboardCompanyWorkResult();
        List<OaSignOnboardCompanyWorkResult.Item> items = new ArrayList<>();
        Set<Long> uniqueRows = new HashSet<>();
        for (OaSignOnboardCompanyWorkRequest.Item requested : action.getItems())
        {
            if (requested == null)
            {
                items.add(failed(null, "待处理记录不能为空"));
                continue;
            }
            if (!uniqueRows.add(requested.getRowId()))
            {
                items.add(failed(requested, "同一导入行不能重复选择"));
                continue;
            }
            try
            {
                items.add(preflight(action.getRequestId(), requested, selectedShopDeptId));
            }
            catch (ServiceException exception)
            {
                items.add(failed(requested, safeMessage(exception)));
            }
            catch (RuntimeException exception)
            {
                items.add(failed(requested, "预检失败，请刷新后重试"));
            }
        }
        summarize(result, items, false);
        return result;
    }

    public OaSignOnboardCompanyWorkResult execute(OaSignOnboardCompanyWorkRequest action,
            Long selectedShopDeptId)
    {
        requireAction(action);
        OaSignOnboardCompanyWorkResult result = new OaSignOnboardCompanyWorkResult();
        List<OaSignOnboardCompanyWorkResult.Item> items = new ArrayList<>();
        Set<Long> uniqueRows = new HashSet<>();
        for (OaSignOnboardCompanyWorkRequest.Item requested : action.getItems())
        {
            if (requested == null)
            {
                items.add(failed(null, "待处理记录不能为空"));
                continue;
            }
            if (!uniqueRows.add(requested.getRowId()))
            {
                items.add(failed(requested, "同一导入行不能重复处理"));
                continue;
            }
            try
            {
                items.add(executeOne(action.getRequestId(), requested, selectedShopDeptId));
            }
            catch (ServiceException exception)
            {
                items.add(failed(requested, safeMessage(exception)));
            }
            catch (RuntimeException exception)
            {
                items.add(failed(requested, "处理失败，请刷新后重试"));
            }
        }
        summarize(result, items, true);
        return result;
    }

    private OaSignOnboardCompanyWorkResult.Item executeOne(String requestId,
            OaSignOnboardCompanyWorkRequest.Item requested, Long selectedShopDeptId)
    {
        Long effectiveScope = effectiveScope(requested.getBatchId(), selectedShopDeptId);
        OaSignOnboardImportBatch batch = importService.requireHrBatch(
                requested.getBatchId(), effectiveScope);
        OaSignOnboardImportRow current = importService.requireBatchRow(
                requested.getBatchId(), requested.getRowId());
        importService.requireCurrentHrTaskOwnership(current, batch);
        Long initiatingOperatorUserId = SecurityUtils.getUserId();
        String itemRequestId = itemRequestId(requestId, requested.getRowId(),
                requested.getVersion());
        OaSignOnboardDataRequest stagedData = current.getDataRequestId() == null ? null
                : dataRequestMapper.selectById(current.getDataRequestId());
        boolean stagedPackage = validStagedPackageBinding(current, stagedData);
        if (current.getTaskId() != null && !stagedPackage)
        {
            if (Objects.equals(current.getGenerationRequestId(), itemRequestId)
                    && sameSelection(current, requested))
            {
                OaSignOnboardCompanyWorkResult.Item reused = base(requested, current);
                reused.setTaskId(current.getTaskId());
                reused.setPackageId(current.getPackageId());
                reused.setResult("REUSED");
                reused.setStatus(current.getStatus());
                reused.setMessage("同一请求已按相同公司与印章生成正式合同草稿");
                return reused;
            }
            return conflict(requested, current,
                    sameSelection(current, requested)
                            ? "ALREADY_GENERATED_BY_ANOTHER_REQUEST"
                            : "ALREADY_GENERATED_WITH_DIFFERENT_DECISION",
                    sameSelection(current, requested)
                            ? "该记录已由其他请求生成，请刷新查看结果"
                            : "该记录已按其他公司或印章生成，不能覆盖");
        }
        if (Objects.equals(current.getGenerationRequestId(), itemRequestId))
        {
            if (!sameSelection(current, requested))
                return conflict(requested, current, "COMPANY_WORK_SELECTION_CONFLICT",
                        "同一请求的公司或印章与已冻结选择不一致");
            if ("GENERATING".equals(current.getStatus()) && generationLeaseActive(current))
                return conflict(requested, current, "GENERATION_IN_PROGRESS",
                        "同一请求正在生成，请稍后刷新");
            if (!Set.of("READY_TO_GENERATE", "GENERATE_FAILED", "GENERATING")
                    .contains(current.getStatus()))
                return conflict(requested, current, "COMPANY_WORK_STATE_CHANGED",
                        "当前记录状态已变化，需要刷新后处理");
            return generateBound(batch.getBatchId(), batch.getVersion(), itemRequestId,
                    requested, current, effectiveScope);
        }

        OaSignOnboardCompanyWorkResult.Item checked = preflight(requestId, requested,
                effectiveScope);
        if (!"READY".equals(checked.getResult())) return checked;

        OaSignOnboardImportRowUpdateRequest update = new OaSignOnboardImportRowUpdateRequest();
        update.setVersion(requested.getVersion());
        update.setLegalEntityId(requested.getLegalEntityId());
        update.setSealId(requested.getSealId());
        update.setNoExternalContractConfirmed(requested.getNoExternalContractConfirmed());
        if (!blank(requested.getHistoricalReason()))
        {
            update.setHistoricalSupplement(Boolean.TRUE);
            update.setHistoricalReason(requested.getHistoricalReason().trim());
        }
        if (!blank(requested.getWarningReason()))
        {
            update.setWarningConfirmed(Boolean.TRUE);
            update.setWarningReason(requested.getWarningReason().trim());
        }
        // updateRow performs remote candidate refresh before taking its short canonical-task
        // write lock. Do not wrap that refresh in the package/task lock used by the local bind.
        OaSignOnboardImportBatchView updated = importService.updateRow(batch.getBatchId(),
                requested.getRowId(), update, effectiveScope);
        OaSignOnboardImportRowView refreshedRow = updated.getRows().stream()
                .filter(value -> Objects.equals(value.getRowId(), requested.getRowId()))
                .findFirst().orElseThrow(() -> new ServiceException(
                        "导入行已变化，请刷新后重试"));
        PreparedCompanyWork prepared;
        if (!"READY_TO_GENERATE".equals(refreshedRow.getStatus()))
        {
            prepared = new PreparedCompanyWork(updated, refreshedRow, null);
        }
        else
        {
            OaSignOnboardImportRow writeSource = rowMapper.selectById(requested.getRowId());
            if (writeSource == null
                    || !Objects.equals(writeSource.getBatchId(), batch.getBatchId()))
                throw new ServiceException("导入行已变化，请刷新后重试");
            prepared = generationService.withCurrentOwnerTaskLock(
                    batch, writeSource, initiatingOperatorUserId,
                    () -> transactionTemplate.execute(status -> {
                OaSignOnboardImportRow lockedRow = rowMapper.selectById(
                        requested.getRowId());
                importService.requireCurrentHrTaskOwnership(lockedRow, batch);
                if (lockedRow == null
                        || !Objects.equals(lockedRow.getVersion(), refreshedRow.getVersion()))
                    throw new ServiceException("导入行已变化，请刷新后重试");
                int boundCount = stagedPackage
                        ? rowMapper.bindStagedCompanyWorkGenerationRequest(
                                requested.getRowId(), lockedRow.getTaskId(),
                                lockedRow.getPackageId(), itemRequestId,
                                requested.getLegalEntityId(), requested.getSealId(),
                                lockedRow.getVersion())
                        : rowMapper.bindCompanyWorkGenerationRequest(requested.getRowId(),
                                itemRequestId, requested.getLegalEntityId(),
                                requested.getSealId(), lockedRow.getVersion());
                if (boundCount != 1)
                    throw new ServiceException("导入行已被其他请求处理，请刷新后重试");
                OaSignOnboardImportRow bound = rowMapper.selectById(requested.getRowId());
                if (bound == null
                        || !Objects.equals(bound.getGenerationRequestId(), itemRequestId))
                    throw new ServiceException("批量选公司盖章请求未能冻结");
                return new PreparedCompanyWork(updated, refreshedRow, bound);
            }));
        }
        if (prepared == null) throw new ServiceException("批量选公司盖章未完成");
        OaSignOnboardImportRowView updatedRow = prepared.row();
        if (!"READY_TO_GENERATE".equals(updatedRow.getStatus()))
        {
            OaSignOnboardCompanyWorkResult.Item blocked = base(requested, updatedRow);
            blocked.setResult("BLOCKED");
            blocked.setStatus(updatedRow.getStatus());
            blocked.setBlockers(blockers(updatedRow, requested));
            blocked.setMessage("公司与印章已保存，仍有资料或校验项未完成");
            return blocked;
        }
        return generateBound(batch.getBatchId(), prepared.batch().getVersion(), itemRequestId,
                requested, prepared.boundRow(), effectiveScope);
    }

    private OaSignOnboardCompanyWorkResult.Item generateBound(Long batchId, Long batchVersion,
            String itemRequestId, OaSignOnboardCompanyWorkRequest.Item requested,
            OaSignOnboardImportRow source, Long effectiveScope)
    {
        OaSignOnboardGenerateRequest generate = new OaSignOnboardGenerateRequest();
        generate.setRequestId(itemRequestId);
        generate.setBatchVersion(batchVersion);
        generate.setRowIds(List.of(requested.getRowId()));
        generate.setNoExternalContractConfirmed(requested.getNoExternalContractConfirmed());
        generate.setHistoricalReason(requested.getHistoricalReason());
        generate.setWarningReason(requested.getWarningReason());
        OaSignOnboardGenerateResult generated = generationService.generate(batchId,
                generate, effectiveScope);
        OaSignOnboardGenerateResult.Item generatedItem = generated.getItems().isEmpty() ? null
                : generated.getItems().get(0);
        if (generatedItem == null)
            throw new ServiceException("正式合同草稿未返回处理结果");
        OaSignOnboardCompanyWorkResult.Item item = base(requested, source);
        item.setResult(generatedItem.getResult());
        item.setStatus(generatedItem.getStatus());
        item.setTaskId(generatedItem.getTaskId());
        item.setPackageId(generatedItem.getPackageId());
        item.setMessage(("GENERATED".equals(generatedItem.getResult())
                || "REUSED".equals(generatedItem.getResult()))
                ? "公司与印章已冻结，正式合同草稿已生成，待预览后发送"
                : generatedItem.getMessage());
        return item;
    }

    private OaSignOnboardCompanyWorkResult.Item preflight(
            String requestId, OaSignOnboardCompanyWorkRequest.Item requested,
            Long selectedShopDeptId)
    {
        Long effectiveScope = effectiveScope(requested.getBatchId(), selectedShopDeptId);
        OaSignOnboardImportBatch batch = importService.requireHrBatch(
                requested.getBatchId(), effectiveScope);
        OaSignOnboardImportRow row = importService.requireBatchRow(
                requested.getBatchId(), requested.getRowId());
        importService.requireCurrentHrTaskOwnership(row, batch);
        OaSignOnboardCompanyWorkResult.Item result = base(requested, row);
        OaSignOnboardDataRequest data = row.getDataRequestId() == null ? null
                : dataRequestMapper.selectById(row.getDataRequestId());
        boolean stagedPackage = validStagedPackageBinding(row, data);
        if (row.getTaskId() != null && !stagedPackage)
        {
            result.setResult("BLOCKED");
            result.setStatus(row.getStatus());
            result.setTaskId(row.getTaskId());
            result.setPackageId(row.getPackageId());
            result.setBlockers(List.of("ALREADY_GENERATED"));
            result.setMessage("该记录已生成正式合同草稿，无需再次预检");
            return result;
        }
        LinkedHashSet<String> blockers = new LinkedHashSet<>();
        String expectedGenerationRequestId = itemRequestId(requestId, requested.getRowId(),
                requested.getVersion());
        boolean resumingSameRequest = Objects.equals(row.getGenerationRequestId(),
                expectedGenerationRequestId);
        boolean ownerLeaseActive = !blank(row.getGenerationRequestId())
                && generationLeaseActive(row);
        if (resumingSameRequest && !sameSelection(row, requested))
            blockers.add("COMPANY_WORK_SELECTION_CONFLICT");
        if (!resumingSameRequest && ownerLeaseActive)
            blockers.add("COMPANY_WORK_OWNED_BY_ANOTHER_REQUEST");
        if (!resumingSameRequest && !Objects.equals(row.getVersion(), requested.getVersion()))
            blockers.add("ROW_VERSION_CHANGED");
        if ("GENERATING".equals(row.getStatus()) && generationLeaseActive(row))
            blockers.add("GENERATION_IN_PROGRESS");
        if (!validSignatureFirstDataRequest(row, data))
        {
            blockers.add("SIGNATURE_CONFIRMATION_INCOMPLETE");
        }
        else
        {
            try
            {
                importService.requireEmployeeConfirmationCurrent(data, row,
                        importService.submittedEmployeeConfirmationValues(data));
            }
            catch (ServiceException exception)
            {
                blockers.add("EMPLOYEE_FACTS_CHANGED");
            }
        }
        try
        {
            companyService.requireContractReadyEntity(requested.getLegalEntityId());
        }
        catch (ServiceException exception)
        {
            blockers.add("COMPANY_NOT_CONTRACT_READY");
        }
        try
        {
            companyService.requireContractReadySeal(requested.getSealId(),
                    requested.getLegalEntityId());
        }
        catch (ServiceException exception)
        {
            blockers.add("SEAL_NOT_CONTRACT_READY");
        }
        boolean retryingFailedGeneration = "GENERATE_FAILED".equals(row.getStatus())
                && (resumingSameRequest || !ownerLeaseActive);
        blockers.addAll(nonCompanyBlockers(row, requested, retryingFailedGeneration));
        result.setBlockers(new ArrayList<>(blockers));
        result.setStatus(row.getStatus());
        if (blockers.isEmpty())
        {
            result.setResult("READY");
            result.setMessage("校验通过，可批量选择公司并盖章");
        }
        else
        {
            result.setResult("BLOCKED");
            result.setMessage("仍有资料、版本或主数据校验项未完成");
        }
        return result;
    }

    private List<String> nonCompanyBlockers(OaSignOnboardImportRow row,
            OaSignOnboardCompanyWorkRequest.Item requested,
            boolean retryingFailedGeneration)
    {
        LinkedHashSet<String> blockers = new LinkedHashSet<>();
        for (String code : importService.list(row.getErrorCodesJson()))
            if (code != null && !code.startsWith("COMPANY_")
                    && !(retryingFailedGeneration
                    && Set.of("GENERATE_FAILED", "PACKAGE_PERSONAL_FACTS_NOT_FROZEN",
                            "SEND_PREPARATION_FAILED").contains(code)))
                blockers.add(code);
        List<String> missing = new ArrayList<>(importService.list(row.getMissingFieldsJson()));
        if (Boolean.TRUE.equals(row.getNoExternalContractConfirmed())
                || Boolean.TRUE.equals(requested.getNoExternalContractConfirmed()))
            missing.remove("noExternalContractConfirmation");
        if (Boolean.TRUE.equals(row.getHistoricalSupplement()) && !blank(row.getHistoricalReason())
                || !blank(requested.getHistoricalReason()))
            missing.remove("historicalSupplementReason");
        if (Boolean.TRUE.equals(row.getWarningConfirmed()) && !blank(row.getWarningReason())
                || !blank(requested.getWarningReason()))
            missing.remove("warningConfirmationReason");
        blockers.addAll(missing);
        if (row.getPlanVersionId() == null) blockers.add("PLAN_MISSING");
        return new ArrayList<>(blockers);
    }

    private List<String> blockers(OaSignOnboardImportRowView row,
            OaSignOnboardCompanyWorkRequest.Item requested)
    {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String code : row.getErrorCodes())
            if (code != null && !code.startsWith("COMPANY_")) result.add(code);
        result.addAll(row.getMissingFields());
        return new ArrayList<>(result);
    }

    private boolean validSignatureFirstDataRequest(OaSignOnboardImportRow row,
            OaSignOnboardDataRequest request)
    {
        return request != null && Objects.equals(request.getRequestId(), row.getDataRequestId())
                && Objects.equals(request.getRowId(), row.getRowId())
                && Objects.equals(request.getEmployeeId(), row.getEmployeeId())
                && "COMPLETED".equals(request.getStatus())
                && OaSignSigningSequence.SIGNATURE_FIRST.equalsIgnoreCase(
                        request.getSigningSequence() == null ? ""
                                : request.getSigningSequence().trim())
                && !blank(request.getSignatureRequestId())
                && request.getSignatureSampleBytes() != null
                && request.getSignatureSampleBytes().length > 0
                && !blank(request.getSignatureSampleHash())
                && request.getSignatureSampleHash().equalsIgnoreCase(
                        OaSignOnboardExcelParser.sha256(request.getSignatureSampleBytes()))
                && request.getSignatureSampleTime() != null;
    }

    private boolean validStagedPackageBinding(OaSignOnboardImportRow row,
            OaSignOnboardDataRequest request)
    {
        return row != null && row.getTaskId() != null && row.getPackageId() != null
                && !Set.of("GENERATED", "SENT", "PARTIAL_SENT").contains(row.getStatus())
                && validSignatureFirstDataRequest(row, request);
    }

    private boolean isCompanyWorkRow(OaSignOnboardImportRowView row)
    {
        return row != null && (row.getTaskId() == null || row.getPackageId() != null)
                && row.getStatus() != null
                && !Set.of("GENERATING", "GENERATED", "SENT", "PARTIAL_SENT")
                        .contains(row.getStatus())
                && OaSignSigningSequence.SIGNATURE_FIRST.equals(
                        row.getDataRequestSigningSequence())
                && Boolean.TRUE.equals(row.getDataRequestSignatureCaptured())
                // A different import has already produced the employee's open onboarding
                // task. Keeping this row in the company-work queue invites an impossible
                // second submission and makes a successful first attempt look like a failure.
                && (row.getErrorCodes() == null
                        || !row.getErrorCodes().contains("EXISTING_OPEN_ONBOARD_TASK"));
    }

    private Long effectiveScope(Long batchId, Long selectedShopDeptId)
    {
        if (selectedShopDeptId != null && selectedShopDeptId > 0)
            return selectedShopDeptId;
        // An empty scope is the established admin/global representation. Re-enter the
        // existing batch authorization boundary with the batch's own active scope instead of
        // weakening requireHrBatch or duplicating its ownership checks.
        List<Long> scope = signScopeService.resolveScopeDeptIds(selectedShopDeptId);
        if (scope != null && !scope.isEmpty())
            throw new ServiceException("请先选择签约组织");
        OaSignOnboardImportBatch batch = batchMapper.selectById(batchId);
        if (batch == null || batch.getShopDeptId() == null)
            throw new ServiceException("导入批次不存在或无权访问");
        return batch.getShopDeptId();
    }

    private OaSignOnboardCompanyWorkView.Item toWorkItem(
            OaSignOnboardImportBatchView batch, OaSignOnboardImportRowView row)
    {
        OaSignOnboardCompanyWorkView.Item item = new OaSignOnboardCompanyWorkView.Item();
        item.setBatchId(batch.getBatchId());
        item.setBatchNo(batch.getBatchNo());
        item.setRowId(row.getRowId());
        item.setTaskId(row.getTaskId());
        item.setPackageId(row.getPackageId());
        item.setSourceRowNumber(row.getSourceRowNumber());
        item.setVersion(row.getVersion());
        item.setEmployeeId(row.getEmployeeId());
        item.setEmployeeName(row.getEmployeeName());
        item.setShopDeptId(batch.getShopDeptId());
        item.setShopDeptName(batch.getShopDeptName());
        item.setStatus(row.getStatus());
        item.setPlanName(row.getPlanName());
        item.setTemplateNames(row.getTemplateNames());
        item.setErrorCodes(row.getErrorCodes());
        item.setWarningCodes(row.getWarningCodes());
        item.setMissingFields(row.getMissingFields());
        item.setMatchedLegalEntityId(row.getMatchedLegalEntityId());
        item.setMatchedLegalEntityName(row.getMatchedLegalEntityName());
        item.setRecommendedSealId(row.getRecommendedSealId());
        item.setCompanyCandidates(row.getCompanyCandidates());
        item.setSealCandidates(row.getSealCandidates());
        item.setSignatureCaptured(row.getDataRequestSignatureCaptured());
        item.setHistoricalSupplement(row.getHistoricalSupplement());
        item.setHistoricalReason(row.getHistoricalReason());
        item.setNoExternalContractConfirmed(row.getNoExternalContractConfirmed());
        item.setWarningConfirmed(row.getWarningConfirmed());
        item.setWarningReason(row.getWarningReason());
        return item;
    }

    private OaSignOnboardCompanyWorkView.Item toWorkItem(
            OaSignOnboardImportBatch batch, OaSignOnboardImportRowView row)
    {
        OaSignOnboardImportBatchView view = new OaSignOnboardImportBatchView();
        view.setBatchId(batch.getBatchId());
        view.setBatchNo(batch.getBatchNo());
        view.setShopDeptId(batch.getShopDeptId());
        view.setShopDeptName(batch.getShopDeptName());
        return toWorkItem(view, row);
    }

    private OaSignOnboardCompanyWorkResult.Item base(
            OaSignOnboardCompanyWorkRequest.Item requested,
            OaSignOnboardImportRow row)
    {
        OaSignOnboardCompanyWorkResult.Item item = new OaSignOnboardCompanyWorkResult.Item();
        if (requested != null)
        {
            item.setBatchId(requested.getBatchId());
            item.setRowId(requested.getRowId());
        }
        if (row != null)
        {
            item.setEmployeeId(row.getEmployeeId());
            item.setEmployeeName(row.getEmployeeNameMasked());
            item.setTaskId(row.getTaskId());
            item.setPackageId(row.getPackageId());
        }
        return item;
    }

    private OaSignOnboardCompanyWorkResult.Item base(
            OaSignOnboardCompanyWorkRequest.Item requested,
            OaSignOnboardImportRowView row)
    {
        OaSignOnboardCompanyWorkResult.Item item = new OaSignOnboardCompanyWorkResult.Item();
        item.setBatchId(requested.getBatchId());
        item.setRowId(requested.getRowId());
        item.setEmployeeId(row.getEmployeeId());
        item.setEmployeeName(row.getEmployeeName());
        return item;
    }

    private OaSignOnboardCompanyWorkResult.Item failed(
            OaSignOnboardCompanyWorkRequest.Item requested, String message)
    {
        OaSignOnboardCompanyWorkResult.Item item = base(requested,
                (OaSignOnboardImportRow) null);
        item.setResult("FAILED");
        item.setStatus("FAILED");
        item.setMessage(message);
        return item;
    }

    private void requireAction(OaSignOnboardCompanyWorkRequest action)
    {
        importService.requireEnabled();
        hrAccessService.requireCurrentHr();
        if (action == null || blank(action.getRequestId())
                || action.getRequestId().trim().length() > 64)
            throw new ServiceException("批量选公司盖章请求编号无效");
        List<OaSignOnboardCompanyWorkRequest.Item> items = action.getItems();
        if (items == null || items.isEmpty() || items.size() > MAX_ITEMS)
            throw new ServiceException("请选择1至20条待选公司盖章记录");
    }

    private void summarize(OaSignOnboardCompanyWorkResult result,
            List<OaSignOnboardCompanyWorkResult.Item> items, boolean executed)
    {
        int ready = 0;
        int succeeded = 0;
        int blocked = 0;
        int failed = 0;
        for (OaSignOnboardCompanyWorkResult.Item item : items)
        {
            if ("READY".equals(item.getResult())) ready++;
            else if ("GENERATED".equals(item.getResult()) || "REUSED".equals(item.getResult()))
                succeeded++;
            else if ("BLOCKED".equals(item.getResult())) blocked++;
            else failed++;
        }
        result.setItems(items);
        result.setTotalCount(items.size());
        result.setReadyCount(ready);
        result.setSucceededCount(executed ? succeeded : 0);
        result.setBlockedCount(blocked);
        result.setFailedCount(failed);
    }

    private OaSignOnboardCompanyWorkResult.Item conflict(
            OaSignOnboardCompanyWorkRequest.Item requested,
            OaSignOnboardImportRow current, String blocker, String message)
    {
        OaSignOnboardCompanyWorkResult.Item item = base(requested, current);
        item.setTaskId(current == null ? null : current.getTaskId());
        item.setPackageId(current == null ? null : current.getPackageId());
        item.setResult("BLOCKED");
        item.setStatus(current == null ? "BLOCKED" : current.getStatus());
        item.setBlockers(List.of(blocker));
        item.setMessage(message);
        return item;
    }

    private boolean sameSelection(OaSignOnboardImportRow current,
            OaSignOnboardCompanyWorkRequest.Item requested)
    {
        return current != null && requested != null
                && Objects.equals(current.getMatchedLegalEntityId(), requested.getLegalEntityId())
                && Objects.equals(current.getRecommendedSealId(), requested.getSealId());
    }

    private boolean generationLeaseActive(OaSignOnboardImportRow row)
    {
        return row != null && row.getUpdateTime() != null
                && row.getUpdateTime().getTime()
                        > System.currentTimeMillis() - GENERATION_LEASE_MILLIS;
    }

    static String itemRequestId(String requestId, Long rowId, Long rowVersion)
    {
        try
        {
            String source = requestId.trim() + ":" + rowVersion;
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(source.getBytes(StandardCharsets.UTF_8));
            return "OCW:" + HexFormat.of().formatHex(hash, 0, 12) + ":" + rowId;
        }
        catch (NoSuchAlgorithmException exception)
        {
            throw new IllegalStateException("SHA-256不可用", exception);
        }
    }

    private record PreparedCompanyWork(OaSignOnboardImportBatchView batch,
            OaSignOnboardImportRowView row, OaSignOnboardImportRow boundRow) { }

    private String safeMessage(ServiceException exception)
    {
        return exception == null || blank(exception.getMessage())
                ? "处理失败，请刷新后重试" : exception.getMessage();
    }

    private boolean blank(String value)
    {
        return value == null || value.trim().isEmpty();
    }
}
