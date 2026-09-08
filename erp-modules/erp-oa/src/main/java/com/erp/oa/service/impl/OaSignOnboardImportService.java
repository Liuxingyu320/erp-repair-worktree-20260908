package com.erp.oa.service.impl;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.erp.common.core.constant.SecurityConstants;
import com.erp.common.core.domain.R;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.security.shop.ShopScopeService;
import com.erp.common.security.utils.SecurityUtils;
import com.erp.oa.api.domain.HrSignBusinessEvent;
import com.erp.oa.constant.OaSignSigningSequence;
import com.erp.oa.constant.OaSignTemplateType;
import com.erp.oa.domain.OaCompanySealConfig;
import com.erp.oa.domain.OaSignOnboardContractSnapshot;
import com.erp.oa.domain.OaSignOnboardDataRequest;
import com.erp.oa.domain.OaSignOnboardImportBatch;
import com.erp.oa.domain.OaSignOnboardImportRow;
import com.erp.oa.domain.OaSignOnboardSendRequest;
import com.erp.oa.domain.OaSignPackage;
import com.erp.oa.domain.OaSignPlanVersion;
import com.erp.oa.domain.OaSignPlanVersionTemplate;
import com.erp.oa.domain.OaSignTemplate;
import com.erp.oa.domain.OaSignTask;
import com.erp.oa.domain.dto.OaSignOnboardDataRequestSendRequest;
import com.erp.oa.domain.dto.OaSignOnboardImportRowUpdateRequest;
import com.erp.oa.domain.vo.OaSignDraftDecision;
import com.erp.oa.domain.vo.OaSignOnboardImportBatchView;
import com.erp.oa.domain.vo.OaSignOnboardImportRowView;
import com.erp.oa.mapper.OaSignOnboardDataRequestMapper;
import com.erp.oa.mapper.OaSignOnboardImportBatchMapper;
import com.erp.oa.mapper.OaSignOnboardImportRowMapper;
import com.erp.oa.mapper.OaSignOnboardSendRequestMapper;
import com.erp.oa.mapper.OaSignPlanVersionMapper;
import com.erp.oa.mapper.OaSignTaskMapper;
import com.erp.oa.service.rule.OnboardSignScenarioRule;
import com.erp.system.api.RemoteUserService;
import com.erp.system.api.constant.SigningProfileCodes;
import com.erp.system.api.domain.SignCandidateUser;
import com.erp.system.api.domain.SignCandidateUserQuery;
import com.erp.system.api.domain.SysLegalEntity;

/** Persistent preview, matching and HR row-edit workflow for onboarding Excel imports. */
@Service
public class OaSignOnboardImportService
{
    public static final String MATCH_MODE_MANUAL_SELECTED = "MANUAL_SELECTED";
    public static final String MATCH_MODE_EXCEL_PHONE_NAME = "EXCEL_PHONE_NAME";
    private static final int MAX_SELECTED = 100;
    private static final OaSignOnboardRowPolicy ROW_POLICY = new OaSignOnboardRowPolicy();
    static final String CONFIRMATION_SNAPSHOT_VERSION = "ONBOARD_CONFIRM_V1";
    /**
     * Stable V1 fact order used when the employee confirmation hash was first introduced.
     * MySQL JSON objects do not preserve insertion order, so a snapshot read back from the
     * database must be restored to this order before it is serialized for hashing. Keeping the
     * original business-field order also lets already-issued V1 snapshots continue to validate.
     */
    private static final List<String> CONFIRMATION_FACT_FIELDS = List.of(
            "employeeName", "idNumber", "phone", "currentAddress", "contractTypeCode",
            "socialTypeCode", "employeePost", "jobGradeCode", "workLocation", "cityLevel",
            "contractTermCode", "contractStartDate", "contractEndDate", "probationStartDate",
            "probationEndDate", "workSchedule", "salaryTotal", "baseSalary", "postSalary",
            "fieldAllowance", "performanceSalary", "salaryVersion", "servicePersonType",
            "insuranceType", "studentStatus", "schoolName", "retirementStatus",
            "incomeStartYearMonth");
    /**
     * These facts are intentionally finalized after the employee's task-scoped signature in
     * SIGNATURE_FIRST. They still block formal document generation, but they must not suppress
     * plan/document preview or the earlier employee fact-confirmation request.
     */
    private static final Set<String> COMPANY_FINALIZATION_ERRORS = Set.of(
            "COMPANY_SELECTION_INVALID", "COMPANY_MATCH_REQUIRES_HR",
            "COMPANY_MASTER_DATA_INCOMPLETE", "COMPANY_SEAL_REQUIRES_HR",
            "COMPANY_SEAL_INVALID");
    private static final Set<String> FINAL_GENERATED_ROW_STATUSES = Set.of(
            "GENERATED", "SENT", "PARTIAL_SENT");

    @Value("${oa.sign.excel-import.enabled:true}")
    private boolean enabled;

    private final OaSignOnboardExcelParser parser;
    private final OaSignOnboardImportBatchMapper batchMapper;
    private final OaSignOnboardImportRowMapper rowMapper;
    private final OaSignOnboardDataRequestMapper dataRequestMapper;
    private final OaSignHrAccessService hrAccessService;
    private final ShopScopeService shopScopeService;
    private final RemoteUserService remoteUserService;
    private final OnboardSignScenarioRule onboardRule;
    private final OaSignPlanVersionMapper planVersionMapper;
    private final OaSignTaskMapper taskMapper;
    private final OaOnboardSignEventFactory eventFactory;
    private final OaSignCompanyService companyService;
    private final ObjectMapper objectMapper;

    @Autowired
    private OaSignOnboardSendRequestMapper sendRequestMapper;

    @Autowired
    private OaSignTaskOrchestrator taskOrchestrator;

    public OaSignOnboardImportService(OaSignOnboardExcelParser parser,
            OaSignOnboardImportBatchMapper batchMapper,
            OaSignOnboardImportRowMapper rowMapper,
            OaSignOnboardDataRequestMapper dataRequestMapper,
            OaSignHrAccessService hrAccessService,
            @Qualifier("oaSignScopeService") ShopScopeService shopScopeService,
            RemoteUserService remoteUserService,
            OnboardSignScenarioRule onboardRule,
            OaSignPlanVersionMapper planVersionMapper,
            OaSignTaskMapper taskMapper,
            OaOnboardSignEventFactory eventFactory,
            OaSignCompanyService companyService,
            ObjectMapper objectMapper)
    {
        this.parser = parser;
        this.batchMapper = batchMapper;
        this.rowMapper = rowMapper;
        this.dataRequestMapper = dataRequestMapper;
        this.hrAccessService = hrAccessService;
        this.shopScopeService = shopScopeService;
        this.remoteUserService = remoteUserService;
        this.onboardRule = onboardRule;
        this.planVersionMapper = planVersionMapper;
        this.taskMapper = taskMapper;
        this.eventFactory = eventFactory;
        this.companyService = companyService;
        this.objectMapper = objectMapper;
    }

    public List<Long> parseEmployeeIds(String raw)
    {
        requireEnabled();
        if (blank(raw)) return Collections.emptyList();
        try
        {
            List<Long> values;
            if (raw.trim().startsWith("["))
            {
                values = objectMapper.readValue(raw, new TypeReference<List<Long>>() { });
            }
            else
            {
                values = new ArrayList<>();
                for (String value : raw.split(",")) values.add(Long.valueOf(value.trim()));
            }
            return normalizeIds(values);
        }
        catch (ServiceException exception) { throw exception; }
        catch (Exception exception) { throw new ServiceException("员工编号格式无效"); }
    }

    @Transactional(rollbackFor = Exception.class)
    public OaSignOnboardImportBatchView preview(MultipartFile file, List<Long> employeeIds,
            String requestedMatchMode, Long selectedShopDeptId)
    {
        requireEnabled();
        hrAccessService.requireCurrentHr();
        List<Long> selected = normalizeIds(employeeIds);
        String matchMode = normalizeMatchMode(requestedMatchMode, selected);
        boolean autoMatch = MATCH_MODE_EXCEL_PHONE_NAME.equals(matchMode);
        Long shopDeptId = shopScopeService.resolveRequiredShopDept(selectedShopDeptId);
        String shopDeptName = shopScopeService.resolveShopDeptName(shopDeptId);
        OaSignOnboardExcelParser.ParsedWorkbook workbook = parser.parse(file);
        if (autoMatch && workbook.getRows().size() > MAX_SELECTED)
            throw new ServiceException("未预选员工时，签约数据一次最多100行");
        String selectionHash = selectionHash(matchMode, selected);
        Map<Long, SignCandidateUser> candidates = autoMatch
                ? loadCandidatesByPhone(workbook.getRows(), shopDeptId)
                : loadCandidates(selected, shopDeptId);
        // Auto-match previews must be recalculated every time. A previously unmatched row can
        // become matchable after an employee profile is created or a phone number is corrected.
        OaSignOnboardImportBatch reusable = autoMatch ? null
                : batchMapper.selectReusable(shopDeptId, SecurityUtils.getUserId(), selectionHash,
                        workbook.getFileSha256(), new Date());
        if (reusable != null && reusableProfileFactsCurrent(reusable.getBatchId(), candidates))
            return detailInternal(reusable);
        OaSignOnboardImportBatch batch = new OaSignOnboardImportBatch();
        batch.setBatchNo("OSI-" + UUID.randomUUID().toString().replace("-", ""));
        batch.setShopDeptId(shopDeptId);
        batch.setShopDeptName(shopDeptName);
        batch.setSelectedEmployeeIdsJson(json(selected));
        batch.setSelectionHash(selectionHash);
        batch.setSelectedCount(selected.size());
        batch.setOriginalFileName(limit(file.getOriginalFilename(), 255));
        batch.setFileSize(file.getSize());
        batch.setFileSha256(workbook.getFileSha256());
        batch.setSheetName(OaSignOnboardExcelParser.SHEET_NAME);
        batch.setStatus("PREVIEW_READY");
        batch.setTotalRowCount(workbook.getRows().size());
        batch.setMatchedCount(0);
        batch.setExcludedCount(0);
        batch.setErrorCount(0);
        batch.setWarningCount(0);
        batch.setGeneratedCount(0);
        batch.setCreatedByUserId(SecurityUtils.getUserId());
        batch.setCreatedByName(limit(SecurityUtils.getUsername(), 64));
        batch.setExpiresTime(new Date(System.currentTimeMillis() + 24L * 60L * 60L * 1000L));
        batch.setVersion(0L);
        if (batchMapper.insertBatch(batch) != 1) throw new ServiceException("导入批次保存失败");

        List<MatchedSource> matched = autoMatch
                ? matchByPhoneAndName(workbook.getRows(), candidates)
                : match(workbook.getRows(), candidates);
        Map<Long, Long> counts = matched.stream().filter(v -> v.candidate != null)
                .collect(Collectors.groupingBy(v -> v.candidate.getUserId(), LinkedHashMap::new,
                        Collectors.counting()));
        List<OaSignOnboardImportRow> rows = new ArrayList<>();
        Set<Long> uniquelyMatched = new LinkedHashSet<>();
        int excluded = 0;
        for (MatchedSource source : matched)
        {
            if (source.candidate == null)
            {
                rows.add(unmatchedRow(batch, source));
                if (source.extra) excluded++;
            }
            else if (counts.getOrDefault(source.candidate.getUserId(), 0L) > 1)
            {
                source.errors.add("DUPLICATE_EXCEL_ROW");
                rows.add(conflictSourceRow(batch, source));
            }
            else
            {
                OaSignOnboardImportRow row = matchedRow(batch, source);
                rows.add(row);
                uniquelyMatched.add(source.candidate.getUserId());
            }
        }
        int syntheticRow = OaSignOnboardExcelParser.MAX_ROWS + 1;
        for (Long employeeId : selected)
        {
            SignCandidateUser candidate = candidates.get(employeeId);
            if (!uniquelyMatched.contains(employeeId))
            {
                String code = candidate == null ? "EMPLOYEE_NOT_AVAILABLE"
                        : counts.getOrDefault(employeeId, 0L) > 1 ? "DUPLICATE_EXCEL_ROW"
                        : "MISSING_EXCEL_ROW";
                rows.add(missingSelectedRow(batch, candidate, employeeId, syntheticRow++, code));
            }
        }
        if (!rows.isEmpty() && rowMapper.insertRows(rows) != rows.size())
            throw new ServiceException("导入行保存不完整");
        batchMapper.updateSummary(batch.getBatchId(), "PREVIEW_READY", uniquelyMatched.size(), excluded,
                (int) rows.stream().filter(this::hasErrors).count(),
                (int) rows.stream().filter(this::hasWarnings).count(), 0);
        return detailInternal(batchMapper.selectById(batch.getBatchId()));
    }

    public OaSignOnboardImportBatchView detail(Long batchId, Long selectedShopDeptId)
    {
        requireEnabled();
        return detailInternal(requireHrBatch(batchId, selectedShopDeptId));
    }

    @Transactional(rollbackFor = Exception.class)
    public OaSignOnboardImportBatchView updateRow(Long batchId, Long rowId,
            OaSignOnboardImportRowUpdateRequest request, Long selectedShopDeptId)
    {
        return updateRowInternal(batchId, rowId, request, selectedShopDeptId, false);
    }

    @Transactional(rollbackFor = Exception.class)
    public OaSignOnboardImportBatchView updateRowAfterProfileSync(Long batchId, Long rowId,
            OaSignOnboardImportRowUpdateRequest request, Long selectedShopDeptId)
    {
        return updateRowInternal(batchId, rowId, request, selectedShopDeptId, true);
    }

    private OaSignOnboardImportBatchView updateRowInternal(Long batchId, Long rowId,
            OaSignOnboardImportRowUpdateRequest request, Long selectedShopDeptId,
            boolean trustedProfileSync)
    {
        requireEnabled();
        OaSignOnboardImportBatch batch = requireHrBatch(batchId, selectedShopDeptId);
        OaSignOnboardImportRow row = requireBatchRow(batchId, rowId);
        requireCurrentHrTaskOwnership(row, batch);
        Long initiatingOperatorUserId = SecurityUtils.getUserId();
        if (request == null || !Objects.equals(row.getVersion(), request.getVersion()))
            throw new ServiceException("导入行已变化，请刷新后修改");
        if (row.getEmployeeId() == null) throw new ServiceException("未匹配员工的行不能修改");
        if (("WAITING_EMPLOYEE_DATA".equals(row.getStatus())
                || "PENDING_HR_REVIEW".equals(row.getStatus()))
                && ROW_POLICY.editsPersonalFacts(request))
            throw new ServiceException("员工补资料任务进行中，不能并发修改同一组个人事实");
        OaSignOnboardContractSnapshot snapshot = snapshot(row);
        SignCandidateUser candidate = requireCandidate(row.getEmployeeId(), batch.getShopDeptId());
        if (!identityFactsMatch(batch, snapshot, candidate))
            throw new ServiceException("员工身份资料已变化，请重新预览后修改");
        if (trustedProfileSync)
            snapshot.setProfileFactsHash(trim(candidate.getProfileFactsHash()));
        else if (!profileFactsMatch(snapshot, candidate))
            throw new ServiceException("员工档案已变化，请重新预览后修改");
        ROW_POLICY.applyUpdate(request, snapshot, row);
        evaluate(batch, row, snapshot, candidate, stickyErrors(row));
        lockCurrentHrTaskForRowWrite(row, batch, initiatingOperatorUserId);
        if (rowMapper.updateEditableWithVersion(row) != 1)
            throw new ServiceException("导入行已变化，请刷新后修改");
        refreshSummary(batchId);
        return detailInternal(batchMapper.selectById(batchId));
    }

    boolean profileFactsMatch(OaSignOnboardContractSnapshot snapshot, SignCandidateUser candidate)
    {
        return ROW_POLICY.profileFactsMatch(snapshot, candidate);
    }

    boolean identityFactsMatch(OaSignOnboardContractSnapshot snapshot, SignCandidateUser candidate)
    {
        return ROW_POLICY.identityFactsMatch(snapshot, candidate);
    }

    boolean identityFactsMatch(OaSignOnboardImportBatch batch,
            OaSignOnboardContractSnapshot snapshot, SignCandidateUser candidate)
    {
        return ROW_POLICY.identityFactsMatch(batch, snapshot, candidate);
    }

    private boolean reusableProfileFactsCurrent(Long batchId,
            Map<Long, SignCandidateUser> candidates)
    {
        for (OaSignOnboardImportRow row : rowMapper.selectByBatchId(batchId))
        {
            if (row.getEmployeeId() == null || row.getTaskId() != null) continue;
            OaSignOnboardContractSnapshot frozen = snapshot(row);
            SignCandidateUser candidate = candidates.get(row.getEmployeeId());
            if (!identityFactsMatch(frozen, candidate)
                    || !blank(frozen.getProfileFactsHash())
                    && !profileFactsMatch(frozen, candidate))
                return false;
        }
        return true;
    }

    @Transactional(rollbackFor = Exception.class)
    public OaSignOnboardImportBatchView sendDataRequests(Long batchId,
            OaSignOnboardDataRequestSendRequest request, Long selectedShopDeptId)
    {
        requireEnabled();
        OaSignOnboardImportBatch batch = requireHrBatch(batchId, selectedShopDeptId);
        String requestId = request == null ? null : trim(request.getRequestId());
        if (requestId == null || requestId.length() > 64)
            throw new ServiceException("补资料请求编号无效");
        if (request.getRowIds() == null || request.getRowIds().isEmpty()
                || request.getRowIds().size() > MAX_SELECTED)
            throw new ServiceException("请选择1至100行发送补资料任务");
        String signingSequence = OaSignSigningSequence.normalize(request.getSigningSequence());
        boolean signatureFirst = OaSignSigningSequence.signatureFirst(signingSequence);
        Set<Long> requestedIds = new LinkedHashSet<>();
        for (Long rowId : request.getRowIds())
        {
            if (rowId == null || rowId <= 0) throw new ServiceException("导入行编号无效");
            requestedIds.add(rowId);
        }
        if (claimDataRequestSend(requestId, batchId, signingSequence, requestedIds))
            return detailInternal(batchMapper.selectById(batchId));
        List<OaSignOnboardImportRow> rows = rowMapper.selectByBatchId(batchId);
        Map<Long, OaSignOnboardImportRow> rowById = rows.stream()
                .collect(Collectors.toMap(OaSignOnboardImportRow::getRowId, Function.identity()));
        if (!rowById.keySet().containsAll(requestedIds))
            throw new ServiceException("补资料行不存在或不属于当前批次");
        int handled = 0;
        for (Long requestedRowId : requestedIds)
        {
            OaSignOnboardImportRow row = rowById.get(requestedRowId);
            requireCurrentHrTaskOwnership(row, batch);
            if (row.getEmployeeId() == null || row.getTaskId() != null)
                throw new ServiceException("所选行不能发送补资料任务");
            OaSignOnboardContractSnapshot snapshot = snapshot(row);
            List<String> allowed = ROW_POLICY.employeeMissingFields(snapshot);
            if (allowed.isEmpty() && !signatureFirst)
                throw new ServiceException("所选行只缺HR专属字段，不能发送给员工填写");
            List<String> signatureBlockers = signatureRequestBlockers(row, snapshot);
            if (signatureFirst && !signatureBlockers.isEmpty())
                throw new ServiceException("当前行不能发起先留签名："
                        + String.join(",", signatureBlockers));
            EmployeeConfirmationSnapshot confirmation = employeeConfirmationSnapshot(row, snapshot);
            String factSnapshotJson = confirmation.json();
            OaSignOnboardDataRequest existing = dataRequestMapper.selectByRowId(row.getRowId());
            if (existing != null && !"COMPLETED".equals(existing.getStatus()))
            {
                boolean fieldsChanged = !new LinkedHashSet<>(allowed)
                        .equals(new LinkedHashSet<>(list(existing.getAllowedFieldsJson())));
                boolean requestFactsChanged = fieldsChanged
                        || !Objects.equals(signingSequence, existing.getSigningSequence())
                        || !Objects.equals(factSnapshotJson, existing.getFactSnapshotJson());
                if (requestFactsChanged)
                {
                    if (!List.of("PENDING_EMPLOYEE", "REJECTED").contains(existing.getStatus()))
                        throw new ServiceException("补资料任务已提交或正在审核，请先完成或驳回当前轮次再发送新字段");
                    if (blank(snapshot.getProfileFactsHash()))
                        throw new ServiceException("员工档案版本令牌缺失，请重新预览");
                    if (dataRequestMapper.refreshEmployeeFields(existing.getRequestId(), json(allowed),
                            signingSequence, factSnapshotJson,
                            confirmation.version(), confirmation.hash(),
                            "OPS:" + row.getRowId() + ":" + row.getVersion(),
                            snapshot.getProfileFactsHash(), existing.getVersion()) != 1)
                        throw new ServiceException("补资料任务状态已变化");
                }
                else if ("REJECTED".equals(existing.getStatus())
                        && dataRequestMapper.reopenRejected(existing.getRequestId(), existing.getVersion()) != 1)
                    throw new ServiceException("补资料任务状态已变化");
                boolean employeeActionable = requestFactsChanged
                        || List.of("PENDING_EMPLOYEE", "REJECTED").contains(existing.getStatus());
                boolean rowNeedsLink = !Objects.equals(row.getDataRequestId(), existing.getRequestId())
                        || !"WAITING_EMPLOYEE_DATA".equals(row.getStatus());
                if (employeeActionable && (rowNeedsLink
                        || signatureFirst && row.getTaskId() == null))
                {
                    if (signatureFirst)
                    {
                        OaSignPackage staged = stageSignatureFirstPackage(
                                batch, row, snapshot, requestId, signingSequence);
                        if (rowMapper.linkStagedPackage(row.getRowId(), existing.getRequestId(),
                                staged.getTaskId(), staged.getPackageId(),
                                row.getSourceEventVersion(),
                                "WAITING_EMPLOYEE_DATA", row.getVersion()) != 1)
                            throw new ServiceException("导入行已变化");
                    }
                    else if (rowMapper.linkDataRequest(row.getRowId(), existing.getRequestId(),
                            "WAITING_EMPLOYEE_DATA", row.getVersion()) != 1)
                        throw new ServiceException("导入行已变化");
                }
                handled++;
                continue;
            }
            // A completed request is an immutable approval/audit fact. If later HR edits
            // make another employee fact necessary, create a new request round and move
            // only the import row's current pointer; never recycle the old payload/hash.
            OaSignOnboardDataRequest data = new OaSignOnboardDataRequest();
            data.setRequestNo("ODR-" + UUID.randomUUID().toString().replace("-", ""));
            data.setBatchId(batchId);
            data.setRowId(row.getRowId());
            data.setEmployeeId(row.getEmployeeId());
            data.setAllowedFieldsJson(json(allowed));
            data.setSigningSequence(signingSequence);
            data.setFactSnapshotJson(factSnapshotJson);
            data.setConfirmationSnapshotVersion(confirmation.version());
            data.setConfirmationSnapshotHash(confirmation.hash());
            data.setStatus("PENDING_EMPLOYEE");
            data.setProfileSyncStatus("NOT_STARTED");
            data.setProfileSyncRequestId("OPS:" + row.getRowId() + ":" + row.getVersion());
            if (blank(snapshot.getProfileFactsHash()))
                throw new ServiceException("员工档案版本令牌缺失，请重新预览");
            data.setProfileBeforeHash(snapshot.getProfileFactsHash());
            data.setVersion(1L);
            if (dataRequestMapper.insertRequest(data) != 1)
                throw new ServiceException("补资料任务创建失败");
            if (signatureFirst)
            {
                OaSignPackage staged = stageSignatureFirstPackage(
                        batch, row, snapshot, requestId, signingSequence);
                if (rowMapper.linkStagedPackage(row.getRowId(), data.getRequestId(),
                        staged.getTaskId(), staged.getPackageId(),
                        row.getSourceEventVersion(),
                        "WAITING_EMPLOYEE_DATA", row.getVersion()) != 1)
                    throw new ServiceException("首阶段签约包与导入行关联失败");
            }
            else if (rowMapper.linkDataRequest(row.getRowId(), data.getRequestId(),
                    "WAITING_EMPLOYEE_DATA", row.getVersion()) != 1)
                throw new ServiceException("补资料任务创建失败");
            handled++;
        }
        if (handled != requestedIds.size())
            throw new ServiceException("当前没有可发送的员工补资料项");
        refreshSummary(batchId);
        return detailInternal(batchMapper.selectById(batchId));
    }

    private OaSignPackage stageSignatureFirstPackage(OaSignOnboardImportBatch batch,
            OaSignOnboardImportRow row, OaSignOnboardContractSnapshot snapshot,
            String sendRequestId, String signingSequence)
    {
        if (taskOrchestrator == null)
            throw new ServiceException("签约包编排服务未就绪");
        Long sourceEventVersion = row.getSourceEventVersion() == null
                ? row.getVersion() : row.getSourceEventVersion();
        if (sourceEventVersion == null || sourceEventVersion <= 0)
            throw new ServiceException("导入行签约事件版本不完整，请重新预览");
        // SIGNATURE_FIRST creates the real task/package before final-document generation.
        // Freeze the same authoritative optimistic row version that claimGeneration used
        // historically; linkStagedPackage persists and compare-and-sets this exact value.
        row.setSourceEventVersion(sourceEventVersion);
        SignCandidateUser candidate = requireCandidate(row.getEmployeeId(), batch.getShopDeptId());
        HrSignBusinessEvent event = eventFactory.create(batch, row, snapshot, candidate,
                SecurityUtils.getUserId(), "STAGE:" + sendRequestId + ":" + row.getRowId());
        Map<String, Object> attributes = event.getAttributes() == null
                ? new LinkedHashMap<>() : new LinkedHashMap<>(event.getAttributes());
        attributes.put("signingSequence", signingSequence);
        event.setAttributes(attributes);
        OaSignPackage staged = taskOrchestrator
                .stageSignatureFirstPackageInCurrentTransaction(event);
        if (staged == null || staged.getTaskId() == null || staged.getPackageId() == null
                || !Objects.equals(staged.getPlanVersionId(), row.getPlanVersionId()))
        {
            throw new ServiceException("首阶段签约包与导入行冻结方案不一致");
        }
        return staged;
    }

    /**
     * Claims the client request in the same transaction as every data-request mutation.
     * The mapper's upsert serializes concurrent callers; a committed existing token is a
     * completed replay because an uncommitted claim cannot escape this transaction.
     */
    private boolean claimDataRequestSend(String requestId, Long batchId,
            String signingSequence, Set<Long> requestedIds)
    {
        List<Long> canonicalRowIds = requestedIds.stream().sorted().toList();
        StringBuilder payload = new StringBuilder(128);
        payload.append("operation=ONBOARD_DATA_REQUEST_SEND\n")
                .append("batchId=").append(batchId).append('\n')
                .append("signingSequence=").append(signingSequence).append('\n');
        for (Long rowId : canonicalRowIds)
            payload.append("rowId=").append(rowId).append('\n');
        String payloadHash = OaSignOnboardExcelParser.sha256(
                payload.toString().getBytes(StandardCharsets.UTF_8));
        String claimToken = UUID.randomUUID().toString();
        OaSignOnboardSendRequest claim = new OaSignOnboardSendRequest();
        claim.setRequestId(requestId);
        claim.setBatchId(batchId);
        claim.setOperatorUserId(SecurityUtils.getUserId());
        claim.setPayloadHash(payloadHash);
        claim.setClaimToken(claimToken);
        if (sendRequestMapper.claim(claim) <= 0)
            throw new ServiceException("补资料发送请求登记失败");
        OaSignOnboardSendRequest persisted = sendRequestMapper.selectByRequestId(requestId);
        if (persisted == null)
            throw new ServiceException("补资料发送请求登记失败");
        if (!Objects.equals(persisted.getBatchId(), batchId)
                || !Objects.equals(persisted.getOperatorUserId(), SecurityUtils.getUserId())
                || !Objects.equals(persisted.getPayloadHash(), payloadHash))
            throw new ServiceException("补资料请求编号已用于不同内容");
        return !Objects.equals(persisted.getClaimToken(), claimToken);
    }

    OaSignOnboardImportBatch requireHrBatch(Long batchId, Long selectedShopDeptId)
    {
        hrAccessService.requireCurrentHr();
        if (batchId == null || batchId <= 0) throw new ServiceException("导入批次编号无效");
        OaSignOnboardImportBatch batch = batchMapper.selectById(batchId);
        if (batch == null)
            throw new ServiceException("导入批次不存在或无权访问");
        List<Long> scope = shopScopeService.resolveScopeDeptIds(selectedShopDeptId);
        if (scope == null || !scope.contains(batch.getShopDeptId()))
            throw new ServiceException("导入批次不在当前签约范围");
        return batch;
    }

    /** Validate the immutable row/task/package binding before role-authorized operations. */
    void requireCurrentHrTaskOwnership(OaSignOnboardImportRow row,
            OaSignOnboardImportBatch batch)
    {
        if (row == null || batch == null
                || !Objects.equals(row.getBatchId(), batch.getBatchId()))
            throw new ServiceException("导入行与批次绑定不一致");
        OaSignTask sourceCandidate = selectSourceTaskCandidate(row);
        OaSignTask exactTask = exactSourceTask(sourceCandidate, row)
                ? sourceCandidate : null;
        if (sourceCandidate != null && exactTask == null)
            throw new ServiceException("导入行签约任务来源数据不规范");
        OaSignTask task = row.getTaskId() == null
                ? exactTask : taskMapper.selectOaSignTaskById(row.getTaskId());
        if (task != null && !Objects.equals(task.getShopDeptId(), batch.getShopDeptId()))
            throw new ServiceException("导入行签约任务不在当前批次门店");
        if (row.getTaskId() == null)
        {
            if (task != null && !OaSignHrAccessService.isCurrentUserTaskOwner(task))
                throw new ServiceException("导入行签约任务未分配给当前合同经办人");
            return;
        }
        if (!hasValidTaskBinding(row, task) || !sameTask(task, exactTask))
            throw new ServiceException("导入行与签约任务绑定不一致");
        if (!OaSignHrAccessService.isCurrentUserTaskOwner(task))
            throw new ServiceException("导入行签约任务未分配给当前合同经办人");
    }

    /**
     * Rechecks the initiating HR under the canonical task row lock immediately before an
     * editable import-row write. Remote employee lookups happen before this method, so the lock
     * is held only for the local compare-and-set and the surrounding transaction commit.
     */
    private void lockCurrentHrTaskForRowWrite(OaSignOnboardImportRow row,
            OaSignOnboardImportBatch batch, Long initiatingOperatorUserId)
    {
        if (row == null || batch == null
                || !Objects.equals(row.getBatchId(), batch.getBatchId()))
            throw new ServiceException("导入行与批次绑定不一致");
        OaSignTask canonical = selectSourceTaskCandidate(row);
        if (canonical == null)
        {
            if (row.getTaskId() != null)
                throw new ServiceException("导入行与签约任务绑定不一致");
            return;
        }
        if (!exactSourceTask(canonical, row))
            throw new ServiceException("导入行签约任务来源数据不规范");
        Long taskId = row.getTaskId() == null ? canonical.getTaskId() : row.getTaskId();
        if (taskId == null || !Objects.equals(taskId, canonical.getTaskId()))
            throw new ServiceException("导入行与签约任务绑定不一致");
        OaSignTask locked = taskMapper.lockOaSignTaskById(taskId);
        if (!exactSourceTask(locked, row)
                || !Objects.equals(locked.getShopDeptId(), batch.getShopDeptId()))
            throw new ServiceException("导入行签约任务来源或门店已变化");
        if (initiatingOperatorUserId == null || initiatingOperatorUserId <= 0
                || !Objects.equals(initiatingOperatorUserId, locked.getAssignedHrUserId()))
            throw new ServiceException("导入行签约任务已改派，原经办人不能继续修改");
        if (row.getTaskId() != null && !hasValidTaskBinding(row, locked))
            throw new ServiceException("导入行与签约任务绑定不一致");
    }

    /** System maintenance validates immutable evidence without impersonating an HR operator. */
    void requireValidTaskBinding(OaSignOnboardImportRow row)
    {
        if (row == null || row.getTaskId() == null) return;
        OaSignTask task = taskMapper.selectOaSignTaskById(row.getTaskId());
        OaSignTask sourceCandidate = selectSourceTaskCandidate(row);
        if (!hasValidTaskBinding(row, task)
                || !exactSourceTask(sourceCandidate, row)
                || !sameTask(task, sourceCandidate))
            throw new ServiceException("导入行与签约任务绑定不一致");
    }

    private boolean currentHrOwnsBoundTask(OaSignOnboardImportRow row, OaSignTask task)
    {
        return hasValidTaskBinding(row, task)
                && OaSignHrAccessService.isCurrentUserTaskOwner(task);
    }

    private boolean hasValidTaskBinding(OaSignOnboardImportRow row, OaSignTask task)
    {
        return row != null && row.getTaskId() != null && task != null
                && Objects.equals(row.getTaskId(), task.getTaskId())
                && exactSourceTask(task, row)
                && (row.getPackageId() == null
                        || Objects.equals(row.getPackageId(), task.getPackageId()));
    }

    private OaSignTask selectExactSourceTask(OaSignOnboardImportRow row)
    {
        OaSignTask task = selectSourceTaskCandidate(row);
        return exactSourceTask(task, row) ? task : null;
    }

    private OaSignTask selectSourceTaskCandidate(OaSignOnboardImportRow row)
    {
        if (row == null || row.getRowId() == null || row.getEmployeeId() == null
                || row.getSourceEventVersion() == null) return null;
        OaSignTask query = new OaSignTask();
        query.setScenario("ONBOARD");
        query.setEmployeeId(row.getEmployeeId());
        query.setSourceType(OaOnboardSignEventFactory.SOURCE_TYPE);
        query.setSourceBusinessId(String.valueOf(row.getRowId()));
        query.setSourceEventVersion(String.valueOf(row.getSourceEventVersion()));
        List<OaSignTask> exactTasks = safe(taskMapper.selectExactTasksBySourceEvent(query));
        if (exactTasks.size() > 1)
            throw new ServiceException("同一Excel来源事件存在多个签约任务");
        OaSignTask canonical = taskMapper.selectCanonicalTaskBySourceEvent(query);
        if (exactTasks.size() == 1 && !sameTask(exactTasks.get(0), canonical))
            throw new ServiceException("Excel导入签约任务来源数据不规范");
        return canonical;
    }

    private boolean exactSourceTask(OaSignTask task, OaSignOnboardImportRow row)
    {
        return task != null && row != null && row.getRowId() != null
                && row.getSourceEventVersion() != null
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

    OaSignOnboardImportRow requireBatchRow(Long batchId, Long rowId)
    {
        OaSignOnboardImportRow row = rowMapper.selectById(rowId);
        if (row == null || !Objects.equals(batchId, row.getBatchId()))
            throw new ServiceException("导入行不存在");
        return row;
    }

    SignCandidateUser requireCandidate(Long employeeId, Long shopDeptId)
    {
        SignCandidateUser candidate = loadCandidates(List.of(employeeId), shopDeptId).get(employeeId);
        if (candidate == null) throw new ServiceException("员工档案已变化，请重新预览");
        return candidate;
    }

    OaSignOnboardContractSnapshot snapshot(OaSignOnboardImportRow row)
    {
        try
        {
            return blank(row.getSnapshotJson()) ? new OaSignOnboardContractSnapshot()
                    : objectMapper.readValue(row.getSnapshotJson(), OaSignOnboardContractSnapshot.class);
        }
        catch (Exception exception) { throw new ServiceException("导入行快照损坏，请重新预览"); }
    }

    List<String> list(String json)
    {
        try
        {
            return blank(json) ? new ArrayList<>()
                    : objectMapper.readValue(json, new TypeReference<List<String>>() { });
        }
        catch (Exception exception) { return new ArrayList<>(List.of("SNAPSHOT_JSON_INVALID")); }
    }

    String json(Object value)
    {
        try { return objectMapper.writeValueAsString(value); }
        catch (Exception exception) { throw new ServiceException("签约快照序列化失败"); }
    }

    void requireEnabled()
    {
        if (!enabled) throw new ServiceException("入职签约Excel导入功能未启用");
    }

    private OaSignOnboardImportRow matchedRow(OaSignOnboardImportBatch batch, MatchedSource source)
    {
        OaSignOnboardImportRow row = baseSourceRow(batch, source);
        row.setEmployeeId(source.candidate.getUserId());
        row.setMatchType(source.matchType);
        evaluate(batch, row, source.parsed.getSnapshot(), source.candidate, source.errors);
        return row;
    }

    private OaSignOnboardImportRow conflictSourceRow(OaSignOnboardImportBatch batch, MatchedSource source)
    {
        OaSignOnboardImportRow row = baseSourceRow(batch, source);
        row.setEmployeeId(null);
        row.setMatchType("CONFLICT");
        row.setStatus("CONFLICT");
        row.setErrorCodesJson(json(source.errors));
        row.setWarningCodesJson("[]");
        row.setMissingFieldsJson("[]");
        return row;
    }

    private OaSignOnboardImportRow unmatchedRow(OaSignOnboardImportBatch batch, MatchedSource source)
    {
        OaSignOnboardImportRow row = baseSourceRow(batch, source);
        row.setEmployeeId(null);
        row.setMatchType(source.extra ? "EXTRA" : "CONFLICT");
        row.setStatus(source.extra ? "EXCLUDED" : "CONFLICT");
        row.setErrorCodesJson(json(source.errors));
        row.setWarningCodesJson(json(source.extra ? List.of("EXTRA_NOT_SELECTED") : List.of()));
        row.setMissingFieldsJson("[]");
        return row;
    }

    private OaSignOnboardImportRow baseSourceRow(OaSignOnboardImportBatch batch, MatchedSource source)
    {
        OaSignOnboardImportRow row = new OaSignOnboardImportRow();
        row.setBatchId(batch.getBatchId());
        row.setSourceRowNumber(source.parsed.getSourceRowNumber());
        row.setRowHash(source.parsed.getRowHash());
        OaSignOnboardContractSnapshot snapshot = source.parsed.getSnapshot();
        row.setEmployeeNameMasked(maskName(snapshot.getEmployeeName()));
        row.setPhoneMasked(maskPhone(snapshot.getPhone()));
        row.setIdNumberMasked(maskId(snapshot.getIdNumber()));
        row.setAddressMasked(maskAddress(snapshot.getCurrentAddress()));
        row.setSnapshotJson(json(snapshot));
        row.setWarningConfirmed(false);
        row.setHistoricalSupplement(false);
        row.setNoExternalContractConfirmed(false);
        row.setVersion(1L);
        return row;
    }

    private OaSignOnboardImportRow missingSelectedRow(OaSignOnboardImportBatch batch,
            SignCandidateUser candidate, Long employeeId, int sourceRow, String code)
    {
        OaSignOnboardImportRow row = new OaSignOnboardImportRow();
        row.setBatchId(batch.getBatchId());
        row.setSourceRowNumber(sourceRow);
        row.setRowHash(OaSignOnboardExcelParser.sha256((employeeId + ":" + code)
                .getBytes(StandardCharsets.UTF_8)));
        row.setEmployeeId(employeeId);
        row.setMatchType("MISSING");
        row.setEmployeeNameMasked(maskName(displayName(candidate)));
        row.setPhoneMasked(maskPhone(candidate == null ? null : candidate.getPhonenumber()));
        row.setIdNumberMasked(maskId(candidate == null ? null : candidate.getIdNumber()));
        row.setSnapshotJson("{}");
        row.setStatus("CONFLICT");
        row.setErrorCodesJson(json(List.of(code)));
        row.setWarningCodesJson("[]");
        row.setMissingFieldsJson("[]");
        row.setWarningConfirmed(false);
        row.setHistoricalSupplement(false);
        row.setNoExternalContractConfirmed(false);
        row.setVersion(1L);
        return row;
    }

    private void evaluate(OaSignOnboardImportBatch batch, OaSignOnboardImportRow row,
            OaSignOnboardContractSnapshot snapshot, SignCandidateUser candidate,
            List<String> originalErrors)
    {
        LinkedHashSet<String> errors = new LinkedHashSet<>(originalErrors);
        LinkedHashSet<String> warnings = new LinkedHashSet<>();
        LinkedHashSet<String> missing = new LinkedHashSet<>();
        ROW_POLICY.applyAuthoritativeIdentity(snapshot, candidate);
        if (blank(snapshot.getCurrentAddress()))
        {
            snapshot.setCurrentAddress(trim(candidate.getCurrentAddress()));
            snapshot.setAddressSource(blank(snapshot.getCurrentAddress()) ? null : "ERP_PROFILE");
        }
        if (blank(snapshot.getStudentStatus())) snapshot.setStudentStatus(trim(candidate.getStudentStatus()));
        if (blank(snapshot.getSchoolName())) snapshot.setSchoolName(trim(candidate.getSchoolName()));
        if (blank(snapshot.getRetirementStatus())) snapshot.setRetirementStatus(trim(candidate.getRetirementStatus()));
        if (blank(snapshot.getIncomeStartYearMonth())) snapshot.setIncomeStartYearMonth(trim(candidate.getIncomeStartYearMonth()));
        if (blank(snapshot.getProfileFactsHash())) snapshot.setProfileFactsHash(trim(candidate.getProfileFactsHash()));
        evaluateCompany(row, snapshot, candidate, errors);
        ROW_POLICY.validateEditable(snapshot, errors);
        if (blank(snapshot.getCurrentAddress())) missing.add("currentAddress");
        boolean service = SigningProfileCodes.SERVICE_CONTRACT.equals(snapshot.getContractTypeCode());
        if (service)
        {
            if (blank(snapshot.getServicePersonType())) missing.add("servicePersonType");
            if (blank(snapshot.getInsuranceType())) missing.add("insuranceType");
            if (blank(snapshot.getStudentStatus())) missing.add("studentStatus");
            if (blank(snapshot.getRetirementStatus())) missing.add("retirementStatus");
        }
        int age = ROW_POLICY.ageAt(snapshot.getIdNumber(), snapshot.getContractStartDate());
        if (age >= 16 && age < 18)
        {
            if (blank(snapshot.getStudentStatus())) missing.add("studentStatus");
            if ("NON_STUDENT".equalsIgnoreCase(snapshot.getStudentStatus())
                    && blank(snapshot.getIncomeStartYearMonth())) missing.add("incomeStartYearMonth");
        }
        if (snapshot.getContractStartDate() != null
                && snapshot.getContractStartDate().isBefore(LocalDate.now()))
        {
            if (!Boolean.TRUE.equals(row.getHistoricalSupplement()) || blank(row.getHistoricalReason()))
                missing.add("historicalSupplementReason");
        }
        if (!Boolean.TRUE.equals(row.getNoExternalContractConfirmed()))
            missing.add("noExternalContractConfirmation");
        OaSignTask existingOpenTask = taskMapper.selectOpenOnboardTaskByEmployeeId(candidate.getUserId());
        if (existingOpenTask != null
                && !Objects.equals(existingOpenTask.getTaskId(), row.getTaskId())
                && !sameTask(existingOpenTask, selectExactSourceTask(row)))
            errors.add("EXISTING_OPEN_ONBOARD_TASK");

        row.setRouteCode(route(snapshot));
        row.setPlanVersionId(null);
        row.setPlanVersionHash(null);
        if (errors.stream().allMatch(COMPANY_FINALIZATION_ERRORS::contains))
        {
            boolean temporaryRowId = row.getRowId() == null;
            boolean temporarySourceVersion = row.getSourceEventVersion() == null;
            try
            {
                if (temporarySourceVersion) row.setSourceEventVersion(1L);
                if (temporaryRowId) row.setRowId((long) row.getSourceRowNumber());
                HrSignBusinessEvent event = eventFactory.create(batch, row, snapshot, candidate,
                        SecurityUtils.getUserId(), "PREVIEW");
                OaSignDraftDecision decision = onboardRule.decide(event);
                if (decision.getAction() == OaSignDraftDecision.Action.CREATE_DRAFT)
                {
                    row.setPlanVersionId(decision.getPlanVersionId());
                    OaSignPlanVersion version = planVersionMapper.selectPlanVersionById(decision.getPlanVersionId());
                    row.setPlanVersionHash(version == null ? null : version.getVersionHash());
                    snapshot.setSalaryVersion(decision.getDraftPackage() == null ? null
                            : trim(decision.getDraftPackage().getSalaryVersion()));
                    salaryWarning(snapshot, version, warnings);
                    errors.addAll(templateGateErrors(decision.getPlanVersionId(), snapshot));
                    if (service && decision.getDraftPackage() != null
                            && (!blank(snapshot.getServicePersonType())
                            && !Objects.equals(upper(snapshot.getServicePersonType()),
                                    upper(decision.getDraftPackage().getServicePersonType()))
                            || !blank(snapshot.getInsuranceType())
                            && !Objects.equals(upper(snapshot.getInsuranceType()),
                                    upper(decision.getDraftPackage().getInsuranceType()))))
                        errors.add("PLAN_SERVICE_FACT_MISMATCH");
                }
                else
                {
                    applyPlanDecisionReasons(decision.getReasonCodes(), missing, errors);
                }
            }
            catch (RuntimeException exception) { errors.add("PLAN_PREVIEW_FAILED"); }
            finally
            {
                if (temporaryRowId) row.setRowId(null);
                if (temporarySourceVersion) row.setSourceEventVersion(null);
            }
        }
        if (!warnings.isEmpty() && (!Boolean.TRUE.equals(row.getWarningConfirmed())
                || blank(row.getWarningReason()))) missing.add("warningConfirmationReason");
        row.setSnapshotJson(json(snapshot));
        row.setAddressMasked(maskAddress(snapshot.getCurrentAddress()));
        row.setErrorCodesJson(json(errors));
        row.setWarningCodesJson(json(warnings));
        row.setMissingFieldsJson(json(missing));
        row.setStatus(!errors.isEmpty() ? "CONFLICT"
                : !missing.isEmpty() ? "NEEDS_HR_DATA" : "READY_TO_GENERATE");
    }

    void applyPlanDecisionReasons(List<String> reasons, Set<String> missing, Set<String> errors)
    {
        if (reasons == null) return;
        for (String reason : reasons)
        {
            if (reason == null) continue;
            switch (reason)
            {
                case "MISSING_CURRENT_ADDRESS" -> missing.add("currentAddress");
                case "MISSING_JOB_GRADE" -> missing.add("jobGradeCode");
                case "MISSING_CONTRACT_TYPE" -> missing.add("contractTypeCode");
                case "MISSING_CONTRACT_TERM" -> missing.add("contractTermCode");
                case "MISSING_SOCIAL_TYPE" -> missing.add("socialTypeCode");
                case "MISSING_CONTRACT_DATES" -> {
                    missing.add("contractStartDate");
                    missing.add("contractEndDate");
                }
                default -> errors.add(reason);
            }
        }
    }

    private void evaluateCompany(OaSignOnboardImportRow row,
            OaSignOnboardContractSnapshot snapshot, SignCandidateUser employee,
            Set<String> errors)
    {
        OaSignCompanyService.CompanyMatchResult matched = companyService.matchExcelCompany(
                snapshot.getRecommendedCompany(), snapshot.getLegalRepresentative(),
                snapshot.getRegisteredAddress(), employee.getDeptId());
        row.setCompanyMatchPolicyVersion(companyService.currentMatchPolicyVersion());
        row.setCompanyMatchScore(matched.getFirstScore());
        row.setCompanySecondScore(matched.getSecondScore());
        row.setDeptLegalEntityId(matched.getDepartmentCandidate() == null ? null
                : matched.getDepartmentCandidate().getLegalEntityId());
        row.setCompanyCandidatesJson(json(companyCandidates(matched.getCandidates())));

        boolean manuallyConfirmed = "HR_CONFIRMED".equals(row.getCompanyMatchMode())
                && row.getMatchedLegalEntityId() != null;
        SysLegalEntity selected = null;
        if (manuallyConfirmed)
        {
            try
            {
                selected = companyService.requireActiveEntity(row.getMatchedLegalEntityId());
            }
            catch (ServiceException exception)
            {
                errors.add("COMPANY_SELECTION_INVALID");
            }
        }
        else
        {
            selected = matched.getSelectedEntity();
            row.setMatchedLegalEntityId(selected == null ? null : selected.getLegalEntityId());
            row.setCompanyMatchMode(matched.getMode());
        }
        row.setCompanyDeptConflict(selected != null && row.getDeptLegalEntityId() != null
                && !Objects.equals(selected.getLegalEntityId(), row.getDeptLegalEntityId()));
        if (selected == null)
        {
            clearMatchedCompany(snapshot, row);
            errors.add("COMPANY_MATCH_REQUIRES_HR");
            return;
        }

        row.setCompanyMasterVersion(selected.getVersion());
        freezeMatchedCompany(snapshot, selected);
        if (!companyService.contractMasterMissingFields(selected).isEmpty())
            errors.add("COMPANY_MASTER_DATA_INCOMPLETE");

        OaSignCompanyService.SealRecommendation recommendation;
        try
        {
            recommendation = companyService.recommendContractSeal(selected.getLegalEntityId());
        }
        catch (ServiceException exception)
        {
            row.setRecommendedSealId(null);
            row.setSealRecommendationMode("HR_REQUIRED_COMPANY_INVALID");
            row.setSealCandidatesJson("[]");
            errors.add("COMPANY_SEAL_REQUIRES_HR");
            return;
        }
        row.setSealCandidatesJson(json(sealCandidates(recommendation.getCandidates())));
        boolean manuallySelectedSeal = "HR_SELECTED".equals(row.getSealRecommendationMode())
                && row.getRecommendedSealId() != null;
        OaCompanySealConfig selectedSeal = null;
        if (manuallySelectedSeal)
        {
            try
            {
                selectedSeal = companyService.requireContractReadySeal(
                        row.getRecommendedSealId(), selected.getLegalEntityId());
            }
            catch (ServiceException exception)
            {
                selectedSeal = null;
                errors.add("COMPANY_SEAL_INVALID");
            }
        }
        else
        {
            selectedSeal = recommendation.getSelectedSeal();
            row.setRecommendedSealId(selectedSeal == null ? null : selectedSeal.getSealId());
            row.setSealRecommendationMode(recommendation.getMode());
            if (selectedSeal != null)
            {
                try
                {
                    selectedSeal = companyService.requireContractReadySeal(selectedSeal.getSealId(),
                            selected.getLegalEntityId());
                }
                catch (ServiceException exception)
                {
                    selectedSeal = null;
                    errors.add("COMPANY_SEAL_INVALID");
                }
            }
        }
        if (selectedSeal == null)
        {
            clearMatchedSeal(snapshot);
            errors.add("COMPANY_SEAL_REQUIRES_HR");
        }
        else
        {
            freezeMatchedSeal(snapshot, selectedSeal);
        }
    }

    private List<OaSignOnboardImportRowView.CompanyCandidate> companyCandidates(
            List<OaSignCompanyService.CompanyMatchCandidate> candidates)
    {
        List<OaSignOnboardImportRowView.CompanyCandidate> result = new ArrayList<>();
        for (OaSignCompanyService.CompanyMatchCandidate candidate : candidates)
        {
            SysLegalEntity entity = candidate.getEntity();
            OaSignOnboardImportRowView.CompanyCandidate view =
                    new OaSignOnboardImportRowView.CompanyCandidate();
            view.setLegalEntityId(entity.getLegalEntityId());
            view.setLegalEntityCode(entity.getLegalEntityCode());
            view.setLegalEntityName(entity.getLegalEntityName());
            view.setUnifiedSocialCreditCode(entity.getUnifiedSocialCreditCode());
            view.setRegisteredAddress(entity.getRegisteredAddress());
            view.setLegalRepresentative(entity.getLegalRepresentative());
            view.setMasterVersion(entity.getVersion());
            view.setScore(candidate.getScore());
            view.setMissingMasterFields(candidate.getMissingMasterFields());
            result.add(view);
        }
        return result;
    }

    private List<OaSignOnboardImportRowView.SealCandidate> sealCandidates(
            List<OaCompanySealConfig> candidates)
    {
        List<OaSignOnboardImportRowView.SealCandidate> result = new ArrayList<>();
        for (OaCompanySealConfig candidate : candidates)
        {
            OaSignOnboardImportRowView.SealCandidate view =
                    new OaSignOnboardImportRowView.SealCandidate();
            view.setSealId(candidate.getSealId());
            view.setSealName(candidate.getSealName());
            view.setSealCode(candidate.getSealCode());
            view.setDefaultSeal("Y".equalsIgnoreCase(candidate.getIsDefault()));
            result.add(view);
        }
        return result;
    }

    private void freezeMatchedCompany(OaSignOnboardContractSnapshot snapshot,
            SysLegalEntity entity)
    {
        snapshot.setMatchedLegalEntityId(entity.getLegalEntityId());
        snapshot.setMatchedLegalEntityCode(trim(entity.getLegalEntityCode()));
        snapshot.setMatchedLegalEntityName(trim(entity.getLegalEntityName()));
        snapshot.setMatchedUnifiedSocialCreditCode(trim(entity.getUnifiedSocialCreditCode()));
        snapshot.setMatchedRegisteredAddress(trim(entity.getRegisteredAddress()));
        snapshot.setMatchedLegalRepresentative(trim(entity.getLegalRepresentative()));
        snapshot.setMatchedCompanyPhone(trim(entity.getContactPhone()));
    }

    private void clearMatchedCompany(OaSignOnboardContractSnapshot snapshot,
            OaSignOnboardImportRow row)
    {
        snapshot.setMatchedLegalEntityId(null);
        snapshot.setMatchedLegalEntityCode(null);
        snapshot.setMatchedLegalEntityName(null);
        snapshot.setMatchedUnifiedSocialCreditCode(null);
        snapshot.setMatchedRegisteredAddress(null);
        snapshot.setMatchedLegalRepresentative(null);
        snapshot.setMatchedCompanyPhone(null);
        row.setCompanyMasterVersion(null);
        row.setRecommendedSealId(null);
        row.setSealRecommendationMode(null);
        row.setSealCandidatesJson("[]");
        clearMatchedSeal(snapshot);
    }

    private void freezeMatchedSeal(OaSignOnboardContractSnapshot snapshot,
            OaCompanySealConfig seal)
    {
        snapshot.setMatchedSealId(seal.getSealId());
        snapshot.setMatchedSealName(trim(seal.getSealName()));
        snapshot.setMatchedSealImageUrl(trim(seal.getSealImageUrl()));
        snapshot.setMatchedSealImageHash(trim(seal.getSealImageHash()));
    }

    private void clearMatchedSeal(OaSignOnboardContractSnapshot snapshot)
    {
        snapshot.setMatchedSealId(null);
        snapshot.setMatchedSealName(null);
        snapshot.setMatchedSealImageUrl(null);
        snapshot.setMatchedSealImageHash(null);
    }

    private void salaryWarning(OaSignOnboardContractSnapshot snapshot, OaSignPlanVersion version,
            Set<String> warnings)
    {
        if (version == null) return;
        BigDecimal min = decimal(version.getRuleJson(), version.getDefaultValuesJson(),
                "referenceSalaryMin", "salaryMin");
        BigDecimal max = decimal(version.getRuleJson(), version.getDefaultValuesJson(),
                "referenceSalaryMax", "salaryMax");
        snapshot.setReferenceSalaryMin(min);
        snapshot.setReferenceSalaryMax(max);
        if (snapshot.getSalaryTotal() != null && (min != null && snapshot.getSalaryTotal().compareTo(min) < 0
                || max != null && snapshot.getSalaryTotal().compareTo(max) > 0))
            warnings.add("SALARY_OUTSIDE_REFERENCE_RANGE");
    }

    private BigDecimal decimal(String firstJson, String secondJson, String... names)
    {
        for (String json : List.of(firstJson == null ? "" : firstJson,
                secondJson == null ? "" : secondJson))
        {
            try
            {
                JsonNode root = objectMapper.readTree(json);
                for (String name : names)
                {
                    JsonNode node = root == null ? null : root.get(name);
                    if (node != null && node.isNumber()) return node.decimalValue();
                    if (node != null && node.isTextual()) return new BigDecimal(node.asText().trim());
                }
            }
            catch (Exception ignored) { }
        }
        return null;
    }

    private List<String> stickyErrors(OaSignOnboardImportRow row)
    {
        return list(row.getErrorCodesJson()).stream().filter(code -> code.startsWith("EMPLOYEE_")
                || code.startsWith("DUPLICATE_") || code.startsWith("MISSING_EXCEL")
                || code.startsWith("PHONE_NAME_")).toList();
    }

    private Map<Long, SignCandidateUser> loadCandidates(List<Long> employeeIds, Long shopDeptId)
    {
        SignCandidateUserQuery query = new SignCandidateUserQuery();
        query.setDeptId(shopDeptId);
        query.setUserIds(employeeIds.toArray(Long[]::new));
        query.setLimit(employeeIds.size());
        Map<Long, SignCandidateUser> candidates = loadCandidates(query);
        candidates.keySet().retainAll(new LinkedHashSet<>(employeeIds));
        return candidates;
    }

    private Map<Long, SignCandidateUser> loadCandidatesByPhone(
            List<OaSignOnboardExcelParser.ParsedRow> rows, Long shopDeptId)
    {
        LinkedHashSet<String> phones = rows.stream()
                .map(OaSignOnboardExcelParser.ParsedRow::getSnapshot)
                .filter(Objects::nonNull)
                .map(OaSignOnboardContractSnapshot::getPhone)
                .map(this::trim)
                .filter(value -> value != null && value.matches("1[3-9][0-9]{9}"))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (phones.isEmpty()) return Collections.emptyMap();
        SignCandidateUserQuery query = new SignCandidateUserQuery();
        query.setDeptId(shopDeptId);
        query.setPhoneNumbers(phones.toArray(String[]::new));
        query.setIncludeInactiveEmployees(true);
        // Direct imports are capped at 100 rows. The wider result cap keeps duplicate
        // profile phone numbers visible so ambiguous identities fail closed.
        query.setLimit(500);
        Map<Long, SignCandidateUser> candidates = loadCandidates(query, true);
        candidates.entrySet().removeIf(entry -> !phones.contains(trim(entry.getValue().getPhonenumber())));
        return candidates;
    }

    private Map<Long, SignCandidateUser> loadCandidates(SignCandidateUserQuery query)
    {
        return loadCandidates(query, false);
    }

    private Map<Long, SignCandidateUser> loadCandidates(SignCandidateUserQuery query,
            boolean failWhenResultLimitReached)
    {
        R<List<SignCandidateUser>> response = remoteUserService.listSignCandidates(query, SecurityConstants.INNER);
        if (response == null || R.isError(response)) throw new ServiceException("获取员工签约档案失败");
        if (failWhenResultLimitReached && response.getData() != null
                && query.getLimit() != null && response.getData().size() >= query.getLimit())
            throw new ServiceException("手机号匹配候选过多，无法安全确认唯一员工，请先清理重复档案");
        Map<Long, SignCandidateUser> result = new LinkedHashMap<>();
        if (response.getData() != null)
            for (SignCandidateUser value : response.getData())
                if (value != null && value.getUserId() != null)
                    result.putIfAbsent(value.getUserId(), value);
        return result;
    }

    List<MatchedSource> matchByPhoneAndName(List<OaSignOnboardExcelParser.ParsedRow> rows,
            Map<Long, SignCandidateUser> candidates)
    {
        Map<String, List<SignCandidateUser>> byPhone = index(candidates.values(),
                value -> trim(value.getPhonenumber()));
        List<MatchedSource> result = new ArrayList<>();
        for (OaSignOnboardExcelParser.ParsedRow row : rows)
        {
            OaSignOnboardContractSnapshot snapshot = row.getSnapshot();
            List<String> errors = new ArrayList<>(row.getErrors());
            List<SignCandidateUser> phones = byPhone.get(trim(snapshot.getPhone()));
            SignCandidateUser matched = null;
            if (phones == null || phones.isEmpty())
            {
                errors.add("EMPLOYEE_PHONE_NOT_FOUND");
            }
            else if (phones.size() > 1)
            {
                errors.add("DUPLICATE_PROFILE_PHONE");
            }
            else if (!sameName(snapshot.getEmployeeName(), displayName(phones.get(0))))
            {
                errors.add("PHONE_NAME_MISMATCH");
            }
            else
            {
                matched = phones.get(0);
                if (!Objects.equals(normalizeId(snapshot.getIdNumber()),
                        normalizeId(matched.getIdNumber())))
                    errors.add("EMPLOYEE_IDENTITY_MISMATCH");
                else if ("离职".equals(trim(matched.getEmployeeStatus())))
                {
                    errors.add("EMPLOYEE_REHIRE_REQUIRED");
                    matched = null;
                }
                else if (trim(matched.getAccountStatus()) != null
                        && !"0".equals(trim(matched.getAccountStatus())))
                {
                    errors.add("EMPLOYEE_ACCOUNT_DISABLED");
                    matched = null;
                }
            }
            result.add(new MatchedSource(row, matched, "PHONE_AND_NAME", errors, false));
        }
        return result;
    }

    List<MatchedSource> match(List<OaSignOnboardExcelParser.ParsedRow> rows,
            Map<Long, SignCandidateUser> candidates)
    {
        Map<String, List<SignCandidateUser>> byId = index(candidates.values(),
                value -> normalizeId(value.getIdNumber()));
        Map<String, List<SignCandidateUser>> byPhone = index(candidates.values(),
                value -> trim(value.getPhonenumber()));
        List<MatchedSource> result = new ArrayList<>();
        for (OaSignOnboardExcelParser.ParsedRow row : rows)
        {
            OaSignOnboardContractSnapshot snapshot = row.getSnapshot();
            List<String> errors = new ArrayList<>(row.getErrors());
            List<SignCandidateUser> identityMatches = byId.get(normalizeId(snapshot.getIdNumber()));
            boolean duplicateIdentity = identityMatches != null && identityMatches.size() > 1;
            SignCandidateUser matched = unique(identityMatches);
            String matchType = "ID_NUMBER";
            if (duplicateIdentity) errors.add("DUPLICATE_PROFILE_IDENTITY");
            if (matched != null && !sameName(snapshot.getEmployeeName(), displayName(matched)))
                errors.add("ID_MATCH_NAME_MISMATCH");
            if (matched == null && !duplicateIdentity)
            {
                List<SignCandidateUser> phones = byPhone.get(trim(snapshot.getPhone()));
                if (phones != null && phones.size() == 1
                        && sameName(snapshot.getEmployeeName(), displayName(phones.get(0))))
                {
                    matched = phones.get(0);
                    matchType = "PHONE_AND_NAME";
                }
                else if (phones != null && !phones.isEmpty()) errors.add("PHONE_NAME_MISMATCH");
            }
            boolean extra = matched == null && !duplicateIdentity
                    && errors.stream().noneMatch(v -> v.contains("MISMATCH"));
            result.add(new MatchedSource(row, matched, matchType, errors, extra));
        }
        return result;
    }

    void applyAuthoritativeIdentity(OaSignOnboardContractSnapshot snapshot,
            SignCandidateUser candidate)
    {
        ROW_POLICY.applyAuthoritativeIdentity(snapshot, candidate);
    }

    private Map<String, List<SignCandidateUser>> index(Iterable<SignCandidateUser> values,
            Function<SignCandidateUser, String> key)
    {
        Map<String, List<SignCandidateUser>> result = new HashMap<>();
        for (SignCandidateUser value : values)
        {
            String index = key.apply(value);
            if (index != null) result.computeIfAbsent(index, ignored -> new ArrayList<>()).add(value);
        }
        return result;
    }

    private SignCandidateUser unique(List<SignCandidateUser> values)
    {
        return values != null && values.size() == 1 ? values.get(0) : null;
    }

    /**
     * Builds the company-work list from already bulk-loaded import rows. Only fields consumed by
     * that list are projected, and all related requests, task bindings, plans and templates are
     * fetched in bounded set queries instead of once per row.
     */
    Map<Long, List<OaSignOnboardImportRowView>> companyWorkRowViewsByBatch(
            List<OaSignOnboardImportRow> storedRows,
            Map<Long, OaSignOnboardImportBatch> batchesById)
    {
        if (storedRows == null || storedRows.isEmpty()) return Collections.emptyMap();
        List<OaSignOnboardImportRow> rows = storedRows.stream()
                .filter(Objects::nonNull)
                .filter(row -> row.getBatchId() != null)
                .toList();
        if (rows.isEmpty()) return Collections.emptyMap();

        List<Long> requestIds = rows.stream()
                .map(OaSignOnboardImportRow::getDataRequestId)
                .filter(Objects::nonNull).distinct().toList();
        Map<Long, OaSignOnboardDataRequest> requestsById = requestIds.isEmpty()
                ? Collections.emptyMap()
                : safe(dataRequestMapper.selectSummariesByIds(requestIds)).stream()
                        .filter(request -> request.getRequestId() != null)
                        .collect(Collectors.toMap(OaSignOnboardDataRequest::getRequestId,
                                Function.identity(), (left, right) -> left,
                                LinkedHashMap::new));

        List<Long> taskIds = rows.stream().map(OaSignOnboardImportRow::getTaskId)
                .filter(Objects::nonNull).distinct().toList();
        Map<Long, OaSignTask> tasksById = taskIds.isEmpty()
                ? Collections.emptyMap()
                : safe(taskMapper.selectOaSignTasksByIds(taskIds)).stream()
                        .filter(task -> task.getTaskId() != null)
                        .collect(Collectors.toMap(OaSignTask::getTaskId, Function.identity(),
                                (left, right) -> left, LinkedHashMap::new));
        ExactSourceTaskIndex exactTasksBySource = exactSourceTasks(rows);

        List<Long> versionIds = rows.stream()
                .map(OaSignOnboardImportRow::getPlanVersionId)
                .filter(Objects::nonNull).distinct().toList();
        Map<Long, OaSignPlanVersion> plansById = versionIds.isEmpty()
                ? Collections.emptyMap()
                : safe(planVersionMapper.selectPlanVersionsByIds(versionIds)).stream()
                        .filter(plan -> plan.getVersionId() != null)
                        .collect(Collectors.toMap(OaSignPlanVersion::getVersionId,
                                Function.identity(), (left, right) -> left,
                                LinkedHashMap::new));
        Map<Long, List<OaSignPlanVersionTemplate>> templatesByVersionId =
                versionIds.isEmpty() ? Collections.emptyMap()
                        : safe(planVersionMapper.selectTemplatesByVersionIds(versionIds)).stream()
                                .filter(template -> template.getPlanVersionId() != null)
                                .collect(Collectors.groupingBy(
                                        OaSignPlanVersionTemplate::getPlanVersionId,
                                        LinkedHashMap::new, Collectors.toList()));

        Map<Long, List<OaSignOnboardImportRowView>> result = new LinkedHashMap<>();
        for (OaSignOnboardImportRow row : rows)
        {
            OaSignOnboardImportBatch batch = batchesById == null
                    ? null : batchesById.get(row.getBatchId());
            if (!currentHrCanViewRow(row, batch, tasksById, exactTasksBySource))
                continue;
            result.computeIfAbsent(row.getBatchId(), ignored -> new ArrayList<>())
                    .add(toCompanyWorkView(row, requestsById.get(row.getDataRequestId()),
                            plansById, templatesByVersionId));
        }
        return result;
    }

    private OaSignOnboardImportRowView toCompanyWorkView(OaSignOnboardImportRow row,
            OaSignOnboardDataRequest request, Map<Long, OaSignPlanVersion> plansById,
            Map<Long, List<OaSignPlanVersionTemplate>> templatesByVersionId)
    {
        OaSignOnboardContractSnapshot snapshot = snapshot(row);
        OaSignOnboardImportRowView view = new OaSignOnboardImportRowView();
        view.setRowId(row.getRowId());
        view.setSourceRowNumber(row.getSourceRowNumber());
        view.setEmployeeId(row.getEmployeeId());
        view.setEmployeeName(blank(snapshot.getEmployeeName())
                ? row.getEmployeeNameMasked() : snapshot.getEmployeeName());
        view.setStatus(row.getStatus());
        view.setErrorCodes(list(row.getErrorCodesJson()));
        view.setWarningCodes(list(row.getWarningCodesJson()));
        view.setMissingFields(list(row.getMissingFieldsJson()));
        view.setWarningConfirmed(row.getWarningConfirmed());
        view.setWarningReason(row.getWarningReason());
        view.setMatchedLegalEntityId(row.getMatchedLegalEntityId());
        view.setMatchedLegalEntityName(snapshot.getMatchedLegalEntityName());
        view.setCompanyCandidates(companyCandidateViews(row.getCompanyCandidatesJson()));
        view.setRecommendedSealId(row.getRecommendedSealId());
        view.setSealCandidates(sealCandidateViews(row.getSealCandidatesJson()));
        view.setDataRequestId(row.getDataRequestId());
        populateDataRequestSigningFacts(view, row, request);
        view.setTaskId(row.getTaskId());
        view.setPackageId(row.getPackageId());
        view.setHistoricalSupplement(row.getHistoricalSupplement());
        view.setHistoricalReason(row.getHistoricalReason());
        view.setNoExternalContractConfirmed(row.getNoExternalContractConfirmed());
        view.setVersion(row.getVersion());
        OaSignPlanVersion plan = plansById.get(row.getPlanVersionId());
        view.setPlanName(plan == null ? null : plan.getPlanName());
        view.setTemplateNames(applicableTemplates(
                templatesByVersionId.get(row.getPlanVersionId()), snapshot).stream()
                        .map(OaSignPlanVersionTemplate::getTemplateName)
                        .filter(Objects::nonNull).toList());
        return view;
    }

    private <T> List<T> safe(List<T> values)
    {
        return values == null ? Collections.emptyList() : values;
    }

    private OaSignOnboardImportBatchView detailInternal(OaSignOnboardImportBatch batch)
    {
        if (batch == null) throw new ServiceException("导入批次不存在");
        OaSignOnboardImportBatchView view = new OaSignOnboardImportBatchView();
        view.setBatchId(batch.getBatchId()); view.setBatchNo(batch.getBatchNo());
        view.setShopDeptId(batch.getShopDeptId()); view.setShopDeptName(batch.getShopDeptName());
        view.setOriginalFileName(batch.getOriginalFileName()); view.setFileSha256(batch.getFileSha256());
        view.setSheetName(batch.getSheetName()); view.setStatus(batch.getStatus());
        view.setSelectedCount(batch.getSelectedCount()); view.setTotalRowCount(batch.getTotalRowCount());
        view.setMatchedCount(batch.getMatchedCount()); view.setExcludedCount(batch.getExcludedCount());
        view.setErrorCount(batch.getErrorCount()); view.setWarningCount(batch.getWarningCount());
        view.setGeneratedCount(batch.getGeneratedCount()); view.setVersion(batch.getVersion());
        view.setExpiresTime(batch.getExpiresTime());
        Map<String, PlanView> plans = new HashMap<>();
        List<OaSignOnboardImportRow> storedRows = rowMapper.selectByBatchId(batch.getBatchId());
        List<Long> taskIds = safe(storedRows).stream().filter(Objects::nonNull)
                .map(OaSignOnboardImportRow::getTaskId).filter(Objects::nonNull)
                .distinct().toList();
        Map<Long, OaSignTask> tasksById = taskIds.isEmpty()
                ? Collections.emptyMap()
                : safe(taskMapper.selectOaSignTasksByIds(taskIds)).stream()
                        .filter(task -> task.getTaskId() != null)
                        .collect(Collectors.toMap(OaSignTask::getTaskId, Function.identity(),
                                (left, right) -> left, LinkedHashMap::new));
        ExactSourceTaskIndex exactTasksBySource = exactSourceTasks(storedRows);
        storedRows = safe(storedRows).stream()
                .filter(row -> currentHrCanViewRow(row, batch, tasksById, exactTasksBySource))
                .toList();
        LinkedHashSet<Long> employeeIds = storedRows.stream()
                .map(OaSignOnboardImportRow::getEmployeeId).filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, OaSignTask> latestTasks = latestTaskFacts(new ArrayList<>(employeeIds));
        Map<Long, OaSignTask> openTasks = openTaskFacts(employeeIds);
        List<OaSignOnboardImportRowView> rows = new ArrayList<>();
        for (OaSignOnboardImportRow row : storedRows)
            rows.add(toView(row, plans,
                    visibleTaskMetadata(latestTasks.get(row.getEmployeeId()), batch),
                    visibleTaskMetadata(openTasks.get(row.getEmployeeId()), batch)));
        view.setRows(rows);
        return view;
    }

    private ExactSourceTaskIndex exactSourceTasks(List<OaSignOnboardImportRow> rows)
    {
        List<OaSignTask> sourceEvents = safe(rows).stream()
                .filter(Objects::nonNull)
                .filter(row -> row.getTaskId() == null)
                .map(this::exactSourceQuery)
                .filter(Objects::nonNull)
                .toList();
        if (sourceEvents.isEmpty())
            return new ExactSourceTaskIndex(Collections.emptyMap(), Collections.emptySet());
        Set<String> requestedKeys = sourceEvents.stream()
                .map(this::exactSourceKey).filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<String, OaSignTask> result = new LinkedHashMap<>();
        Set<String> deniedKeys = new LinkedHashSet<>();
        for (OaSignTask task : safe(taskMapper.selectOaSignTasksBySourceEvents(sourceEvents)))
        {
            String key = exactSourceKey(task);
            if (key != null && requestedKeys.contains(key))
            {
                if (deniedKeys.contains(key) || result.putIfAbsent(key, task) != null)
                {
                    result.remove(key);
                    deniedKeys.add(key);
                }
                continue;
            }
            String looseKey = looseSourceKey(task);
            if (looseKey != null && requestedKeys.contains(looseKey))
            {
                result.remove(looseKey);
                deniedKeys.add(looseKey);
            }
        }
        return new ExactSourceTaskIndex(result, deniedKeys);
    }

    private OaSignTask exactSourceQuery(OaSignOnboardImportRow row)
    {
        if (row == null || row.getRowId() == null || row.getEmployeeId() == null
                || row.getSourceEventVersion() == null) return null;
        OaSignTask query = new OaSignTask();
        query.setScenario("ONBOARD");
        query.setEmployeeId(row.getEmployeeId());
        query.setSourceType(OaOnboardSignEventFactory.SOURCE_TYPE);
        query.setSourceBusinessId(String.valueOf(row.getRowId()));
        query.setSourceEventVersion(String.valueOf(row.getSourceEventVersion()));
        return query;
    }

    private String exactSourceKey(OaSignOnboardImportRow row)
    {
        return exactSourceKey(exactSourceQuery(row));
    }

    private String exactSourceKey(OaSignTask task)
    {
        if (task == null || task.getEmployeeId() == null
                || blank(task.getScenario())
                || blank(task.getSourceType())
                || blank(task.getSourceBusinessId())
                || blank(task.getSourceEventVersion())) return null;
        return task.getScenario().trim().toUpperCase(Locale.ROOT) + "\u0000"
                + task.getEmployeeId() + "\u0000"
                + task.getSourceType().trim().toUpperCase(Locale.ROOT) + "\u0000"
                + task.getSourceBusinessId() + "\u0000" + task.getSourceEventVersion();
    }

    private String looseSourceKey(OaSignTask task)
    {
        if (task == null || task.getEmployeeId() == null
                || blank(task.getScenario()) || blank(task.getSourceType())
                || blank(task.getSourceBusinessId()) || blank(task.getSourceEventVersion()))
        {
            return null;
        }
        return task.getScenario().trim().toUpperCase(Locale.ROOT) + "\u0000"
                + task.getEmployeeId() + "\u0000"
                + task.getSourceType().trim().toUpperCase(Locale.ROOT) + "\u0000"
                + task.getSourceBusinessId().trim() + "\u0000"
                + task.getSourceEventVersion().trim();
    }

    private boolean currentHrCanViewRow(OaSignOnboardImportRow row,
            OaSignOnboardImportBatch batch, Map<Long, OaSignTask> tasksById,
            ExactSourceTaskIndex exactTasksBySource)
    {
        if (row == null || batch == null
                || !Objects.equals(row.getBatchId(), batch.getBatchId())) return false;
        OaSignTask task;
        if (row.getTaskId() != null)
        {
            task = tasksById.get(row.getTaskId());
            if (!currentHrOwnsBoundTask(row, task)) return false;
        }
        else
        {
            String sourceKey = exactSourceKey(row);
            if (exactTasksBySource.deniedKeys().contains(sourceKey)) return false;
            task = exactTasksBySource.tasks().get(sourceKey);
            if (task == null) return true;
            if (!exactSourceTask(task, row)
                    || !OaSignHrAccessService.isCurrentUserTaskOwner(task)) return false;
        }
        return Objects.equals(task.getShopDeptId(), batch.getShopDeptId());
    }

    private record ExactSourceTaskIndex(Map<String, OaSignTask> tasks,
            Set<String> deniedKeys) {}

    private OaSignTask visibleTaskMetadata(OaSignTask task,
            OaSignOnboardImportBatch batch)
    {
        return task != null && batch != null
                && Objects.equals(task.getShopDeptId(), batch.getShopDeptId())
                && OaSignHrAccessService.isCurrentUserTaskOwner(task) ? task : null;
    }

    private OaSignOnboardImportRowView toView(OaSignOnboardImportRow row, Map<String, PlanView> plans,
            OaSignTask latestTask, OaSignTask openTask)
    {
        OaSignOnboardImportRowView view = new OaSignOnboardImportRowView();
        view.setRowId(row.getRowId()); view.setSourceRowNumber(row.getSourceRowNumber());
        view.setEmployeeId(row.getEmployeeId()); view.setMatchType(row.getMatchType());
        OaSignOnboardContractSnapshot snapshot = snapshot(row);
        view.setEmployeeName(blank(snapshot.getEmployeeName())
                ? row.getEmployeeNameMasked() : snapshot.getEmployeeName());
        view.setPhone(blank(snapshot.getPhone()) ? row.getPhoneMasked() : snapshot.getPhone());
        view.setIdNumber(row.getIdNumberMasked()); view.setCurrentAddress(row.getAddressMasked());
        view.setStatus(row.getStatus()); view.setErrorCodes(list(row.getErrorCodesJson()));
        view.setWarningCodes(list(row.getWarningCodesJson())); view.setMissingFields(list(row.getMissingFieldsJson()));
        view.setWarningConfirmed(row.getWarningConfirmed()); view.setRouteCode(row.getRouteCode());
        view.setPlanVersionId(row.getPlanVersionId()); view.setPlanVersionHash(row.getPlanVersionHash());
        view.setMatchedLegalEntityId(row.getMatchedLegalEntityId());
        view.setCompanyMatchMode(row.getCompanyMatchMode());
        view.setCompanyMatchScore(row.getCompanyMatchScore());
        view.setCompanySecondScore(row.getCompanySecondScore());
        view.setCompanyMatchPolicyVersion(row.getCompanyMatchPolicyVersion());
        view.setCompanyMasterVersion(row.getCompanyMasterVersion());
        view.setDeptLegalEntityId(row.getDeptLegalEntityId());
        view.setCompanyDeptConflict(row.getCompanyDeptConflict());
        view.setCompanyCandidates(companyCandidateViews(row.getCompanyCandidatesJson()));
        view.setRecommendedSealId(row.getRecommendedSealId());
        view.setSealRecommendationMode(row.getSealRecommendationMode());
        view.setSealCandidates(sealCandidateViews(row.getSealCandidatesJson()));
        view.setDataRequestId(row.getDataRequestId());
        populateDataRequestSigningFacts(view, row);
        view.setTaskId(row.getTaskId());
        view.setPackageId(row.getPackageId()); view.setHistoricalSupplement(row.getHistoricalSupplement());
        view.setNoExternalContractConfirmed(row.getNoExternalContractConfirmed()); view.setVersion(row.getVersion());
        if (openTask != null && !Objects.equals(openTask.getTaskId(), row.getTaskId()))
        {
            view.setExistingTaskId(openTask.getTaskId());
            view.setExistingTaskStatus(openTask.getStatus());
            view.setExistingTaskSourceType(openTask.getSourceType());
        }
        if (latestTask != null)
        {
            view.setLatestTaskId(latestTask.getTaskId());
            view.setLatestTaskStatus(latestTask.getStatus());
            view.setLatestTaskSourceType(latestTask.getSourceType());
        }
        view.setContractTypeCode(snapshot.getContractTypeCode()); view.setSocialTypeCode(snapshot.getSocialTypeCode());
        view.setContractTermCode(snapshot.getContractTermCode()); view.setEmployeePost(snapshot.getEmployeePost());
        view.setWorkLocation(snapshot.getWorkLocation()); view.setCityLevel(snapshot.getCityLevel());
        view.setContractStartDate(snapshot.getContractStartDate()); view.setContractEndDate(snapshot.getContractEndDate());
        view.setProbationStartDate(snapshot.getProbationStartDate()); view.setProbationEndDate(snapshot.getProbationEndDate());
        view.setSalaryTotal(snapshot.getSalaryTotal()); view.setBaseSalary(snapshot.getBaseSalary());
        view.setPostSalary(snapshot.getPostSalary()); view.setFieldAllowance(snapshot.getFieldAllowance());
        view.setPerformanceSalary(snapshot.getPerformanceSalary()); view.setWarningReason(row.getWarningReason());
        view.setHistoricalReason(row.getHistoricalReason());
        view.setJobGradeCode(snapshot.getJobGradeCode()); view.setServicePersonType(snapshot.getServicePersonType());
        view.setInsuranceType(snapshot.getInsuranceType()); view.setStudentStatus(snapshot.getStudentStatus());
        view.setRecommendedCompany(snapshot.getRecommendedCompany());
        view.setRecommendedLegalRepresentative(snapshot.getLegalRepresentative());
        view.setRecommendedRegisteredAddress(snapshot.getRegisteredAddress());
        view.setMatchedLegalEntityCode(snapshot.getMatchedLegalEntityCode());
        view.setMatchedLegalEntityName(snapshot.getMatchedLegalEntityName());
        view.setMatchedUnifiedSocialCreditCode(snapshot.getMatchedUnifiedSocialCreditCode());
        view.setMatchedRegisteredAddress(snapshot.getMatchedRegisteredAddress());
        view.setMatchedLegalRepresentative(snapshot.getMatchedLegalRepresentative());
        view.setSalaryVersion(snapshot.getSalaryVersion());
        view.setSchoolName(snapshot.getSchoolName()); view.setRetirementStatus(snapshot.getRetirementStatus());
        view.setIncomeStartYearMonth(snapshot.getIncomeStartYearMonth());
        List<String> signatureBlockers = signatureRequestBlockers(row, snapshot);
        view.setSignatureRequestBlockers(signatureBlockers);
        view.setSignatureRequestable(signatureBlockers.isEmpty());
        if (row.getPlanVersionId() != null)
        {
            String conditionKey = row.getPlanVersionId() + ":" + snapshot.getJobGradeCode() + ":"
                    + snapshot.getIdNumber() + ":" + snapshot.getContractStartDate() + ":"
                    + snapshot.getStudentStatus();
            PlanView plan = plans.computeIfAbsent(conditionKey,
                    ignored -> planView(row.getPlanVersionId(), snapshot));
            view.setPlanName(plan.name); view.setTemplateNames(plan.templates);
        }
        return view;
    }

    private void populateDataRequestSigningFacts(OaSignOnboardImportRowView view,
            OaSignOnboardImportRow row)
    {
        populateDataRequestSigningFacts(view, row,
                row == null || row.getDataRequestId() == null ? null
                        : dataRequestMapper.selectById(row.getDataRequestId()));
    }

    private void populateDataRequestSigningFacts(OaSignOnboardImportRowView view,
            OaSignOnboardImportRow row, OaSignOnboardDataRequest request)
    {
        view.setDataRequestSignatureCaptured(Boolean.FALSE);
        if (row == null || row.getDataRequestId() == null)
        {
            return;
        }
        if (request == null || !Objects.equals(request.getRowId(), row.getRowId())
                || !Objects.equals(request.getEmployeeId(), row.getEmployeeId()))
        {
            return;
        }
        view.setDataRequestSigningSequence(
                OaSignSigningSequence.normalize(request.getSigningSequence()));
        view.setDataRequestSignatureCaptured("COMPLETED".equals(request.getStatus())
                && !blank(request.getSignatureSampleHash())
                && request.getSignatureSampleTime() != null);
    }

    private List<OaSignOnboardImportRowView.CompanyCandidate> companyCandidateViews(String value)
    {
        try
        {
            return blank(value) ? new ArrayList<>() : objectMapper.readValue(value,
                    new TypeReference<List<OaSignOnboardImportRowView.CompanyCandidate>>() { });
        }
        catch (Exception ignored) { return new ArrayList<>(); }
    }

    private List<OaSignOnboardImportRowView.SealCandidate> sealCandidateViews(String value)
    {
        try
        {
            return blank(value) ? new ArrayList<>() : objectMapper.readValue(value,
                    new TypeReference<List<OaSignOnboardImportRowView.SealCandidate>>() { });
        }
        catch (Exception ignored) { return new ArrayList<>(); }
    }

    private Map<Long, OaSignTask> latestTaskFacts(List<Long> employeeIds)
    {
        if (employeeIds.isEmpty()) return Collections.emptyMap();
        List<OaSignTask> tasks = taskMapper.selectLatestOnboardTasksByEmployeeIds(employeeIds);
        if (tasks == null) return Collections.emptyMap();
        return tasks.stream().filter(task -> task.getEmployeeId() != null)
                .collect(Collectors.toMap(OaSignTask::getEmployeeId, Function.identity(),
                        (left, right) -> left, LinkedHashMap::new));
    }

    private Map<Long, OaSignTask> openTaskFacts(Iterable<Long> employeeIds)
    {
        Map<Long, OaSignTask> result = new LinkedHashMap<>();
        for (Long employeeId : employeeIds)
        {
            OaSignTask task = taskMapper.selectOpenOnboardTaskByEmployeeId(employeeId);
            if (task != null) result.put(employeeId, task);
        }
        return result;
    }

    private PlanView planView(Long versionId, OaSignOnboardContractSnapshot snapshot)
    {
        OaSignPlanVersion version = planVersionMapper.selectPlanVersionById(versionId);
        List<String> names = new ArrayList<>();
        for (OaSignPlanVersionTemplate item : applicableTemplates(versionId, snapshot))
            names.add(item.getTemplateName());
        return new PlanView(version == null ? null : version.getPlanName(), names);
    }

    /** Read-only, server-derived document list used by the employee fact-confirmation page. */
    List<String> plannedDocumentNames(OaSignOnboardImportRow row)
    {
        if (row == null || row.getPlanVersionId() == null)
            return Collections.emptyList();
        OaSignOnboardContractSnapshot snapshot = snapshot(row);
        return applicableTemplates(row.getPlanVersionId(), snapshot).stream()
                .map(OaSignPlanVersionTemplate::getTemplateName)
                .filter(Objects::nonNull).map(String::trim).filter(name -> !name.isEmpty())
                .toList();
    }

    List<String> signatureRequestBlockers(OaSignOnboardImportRow row,
            OaSignOnboardContractSnapshot snapshot)
    {
        LinkedHashSet<String> blockers = new LinkedHashSet<>();
        if (row != null)
        {
            list(row.getErrorCodesJson()).stream()
                    .filter(code -> !COMPANY_FINALIZATION_ERRORS.contains(code))
                    .forEach(blockers::add);
        }
        if (row == null || row.getPlanVersionId() == null)
        {
            blockers.add("PLAN_MISSING");
            return new ArrayList<>(blockers);
        }
        List<String> documents = plannedDocumentNames(row);
        if (documents.isEmpty()) blockers.add("PLAN_DOCUMENTS_EMPTY");
        blockers.addAll(templateGateErrors(row.getPlanVersionId(), snapshot));
        requireConfirmationFact(snapshot.getEmployeeName(), "EMPLOYEE_NAME_MISSING", blockers);
        requireConfirmationFact(snapshot.getIdNumber(), "ID_NUMBER_MISSING", blockers);
        requireConfirmationFact(snapshot.getPhone(), "PHONE_MISSING", blockers);
        requireConfirmationFact(snapshot.getContractTypeCode(), "CONTRACT_TYPE_MISSING", blockers);
        requireConfirmationFact(snapshot.getSocialTypeCode(), "SOCIAL_TYPE_MISSING", blockers);
        requireConfirmationFact(snapshot.getEmployeePost(), "EMPLOYEE_POST_MISSING", blockers);
        requireConfirmationFact(snapshot.getJobGradeCode(), "JOB_GRADE_MISSING", blockers);
        requireConfirmationFact(snapshot.getWorkLocation(), "WORK_LOCATION_MISSING", blockers);
        requireConfirmationFact(snapshot.getCityLevel(), "CITY_LEVEL_MISSING", blockers);
        requireConfirmationFact(snapshot.getContractTermCode(), "CONTRACT_TERM_MISSING", blockers);
        requireConfirmationFact(snapshot.getWorkSchedule(), "WORK_SCHEDULE_MISSING", blockers);
        requireConfirmationFact(snapshot.getSalaryVersion(), "SALARY_VERSION_MISSING", blockers);
        if (snapshot.getContractStartDate() == null || snapshot.getContractEndDate() == null)
            blockers.add("CONTRACT_DATES_MISSING");
        if (!ROW_POLICY.validSalary(snapshot)) blockers.add("SALARY_FACTS_INVALID");
        if (SigningProfileCodes.SERVICE_CONTRACT.equals(snapshot.getContractTypeCode()))
        {
            requireConfirmationFact(snapshot.getServicePersonType(),
                    "SERVICE_PERSON_TYPE_MISSING", blockers);
            requireConfirmationFact(snapshot.getInsuranceType(),
                    "INSURANCE_TYPE_MISSING", blockers);
        }
        return new ArrayList<>(blockers);
    }

    private void requireConfirmationFact(String value, String code, Set<String> blockers)
    {
        if (blank(value)) blockers.add(code);
    }

    EmployeeConfirmationSnapshot employeeConfirmationSnapshot(OaSignOnboardImportRow row)
    {
        return employeeConfirmationSnapshot(row, snapshot(row));
    }

    EmployeeConfirmationSnapshot employeeConfirmationSnapshot(OaSignOnboardImportRow row,
            OaSignOnboardContractSnapshot snapshot)
    {
        Map<String, Object> facts = new LinkedHashMap<>();
        putFact(facts, "employeeName", snapshot.getEmployeeName());
        putFact(facts, "idNumber", snapshot.getIdNumber());
        putFact(facts, "phone", snapshot.getPhone());
        putFact(facts, "currentAddress", snapshot.getCurrentAddress());
        putFact(facts, "contractTypeCode", snapshot.getContractTypeCode());
        putFact(facts, "socialTypeCode", snapshot.getSocialTypeCode());
        putFact(facts, "employeePost", snapshot.getEmployeePost());
        putFact(facts, "jobGradeCode", snapshot.getJobGradeCode());
        putFact(facts, "workLocation", snapshot.getWorkLocation());
        putFact(facts, "cityLevel", snapshot.getCityLevel());
        putFact(facts, "contractTermCode", snapshot.getContractTermCode());
        putFact(facts, "contractStartDate", snapshot.getContractStartDate());
        putFact(facts, "contractEndDate", snapshot.getContractEndDate());
        putFact(facts, "probationStartDate", snapshot.getProbationStartDate());
        putFact(facts, "probationEndDate", snapshot.getProbationEndDate());
        putFact(facts, "workSchedule", snapshot.getWorkSchedule());
        putFact(facts, "salaryTotal", snapshot.getSalaryTotal());
        putFact(facts, "baseSalary", snapshot.getBaseSalary());
        putFact(facts, "postSalary", snapshot.getPostSalary());
        putFact(facts, "fieldAllowance", snapshot.getFieldAllowance());
        putFact(facts, "performanceSalary", snapshot.getPerformanceSalary());
        putFact(facts, "salaryVersion", snapshot.getSalaryVersion());
        putFact(facts, "servicePersonType", snapshot.getServicePersonType());
        putFact(facts, "insuranceType", snapshot.getInsuranceType());
        putFact(facts, "studentStatus", snapshot.getStudentStatus());
        putFact(facts, "schoolName", snapshot.getSchoolName());
        putFact(facts, "retirementStatus", snapshot.getRetirementStatus());
        putFact(facts, "incomeStartYearMonth", snapshot.getIncomeStartYearMonth());
        return buildEmployeeConfirmationSnapshot(facts, plannedDocumentNames(row));
    }

    EmployeeConfirmationSnapshot requireFrozenEmployeeConfirmation(
            OaSignOnboardDataRequest request)
    {
        if (request == null || blank(request.getFactSnapshotJson()))
            throw new ServiceException("员工确认快照缺失，请HR重新发起");
        try
        {
            Map<String, Object> root = objectMapper.readValue(request.getFactSnapshotJson(),
                    new TypeReference<LinkedHashMap<String, Object>>() { });
            String version = root.get("snapshotVersion") == null ? null
                    : String.valueOf(root.get("snapshotVersion"));
            if (!CONFIRMATION_SNAPSHOT_VERSION.equals(version)
                    || !(root.get("facts") instanceof Map<?, ?> rawFacts)
                    || !(root.get("plannedDocumentNames") instanceof List<?> rawDocuments))
                throw new ServiceException("员工确认快照版本无效，请HR重新发起");
            Map<String, Object> facts = new LinkedHashMap<>();
            rawFacts.forEach((key, value) -> facts.put(String.valueOf(key), value));
            List<String> documents = rawDocuments.stream().map(String::valueOf).toList();
            EmployeeConfirmationSnapshot frozen = buildEmployeeConfirmationSnapshot(facts, documents);
            if (!Objects.equals(request.getConfirmationSnapshotVersion(), frozen.version())
                    || !Objects.equals(request.getConfirmationSnapshotHash(), frozen.hash()))
                throw new ServiceException("员工确认快照校验不一致，请HR重新发起");
            return frozen;
        }
        catch (ServiceException exception)
        {
            throw exception;
        }
        catch (Exception exception)
        {
            throw new ServiceException("员工确认快照损坏，请HR重新发起");
        }
    }

    void requireEmployeeConfirmationCurrent(OaSignOnboardDataRequest request,
            OaSignOnboardImportRow row, Map<String, Object> submittedValues)
    {
        if (row == null)
            throw new ServiceException("导入行不存在，需要HR重新发起并由员工重新签名");
        EmployeeConfirmationSnapshot frozen = requireFrozenEmployeeConfirmation(request);
        Map<String, Object> expectedFacts = new LinkedHashMap<>(frozen.facts());
        if (submittedValues != null)
            submittedValues.forEach((key, value) -> {
                if (expectedFacts.containsKey(key)) expectedFacts.put(key, normalizeFact(value));
            });
        EmployeeConfirmationSnapshot current = employeeConfirmationSnapshot(row);
        if (!expectedFacts.equals(current.facts()))
            throw new ServiceException("合同事实已变化，需要HR重新发起并由员工重新签名");
        if (!frozen.plannedDocumentNames().equals(current.plannedDocumentNames()))
            throw new ServiceException("计划文件已变化，需要HR重新发起并由员工重新签名");
    }

    Map<String, Object> submittedEmployeeConfirmationValues(OaSignOnboardDataRequest request)
    {
        if (request == null || blank(request.getSubmittedValuesJson()))
            return Collections.emptyMap();
        try
        {
            return objectMapper.readValue(request.getSubmittedValuesJson(),
                    new TypeReference<LinkedHashMap<String, Object>>() { });
        }
        catch (Exception exception)
        {
            throw new ServiceException("员工提交事实快照损坏，请重新发起");
        }
    }

    private EmployeeConfirmationSnapshot buildEmployeeConfirmationSnapshot(
            Map<String, Object> facts, List<String> plannedDocuments)
    {
        Map<String, Object> normalizedFacts = new LinkedHashMap<>();
        CONFIRMATION_FACT_FIELDS.forEach(key -> {
            if (facts.containsKey(key)) normalizedFacts.put(key, normalizeFact(facts.get(key)));
        });
        facts.entrySet().stream()
                .filter(entry -> !CONFIRMATION_FACT_FIELDS.contains(entry.getKey()))
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> normalizedFacts.put(entry.getKey(),
                        normalizeFact(entry.getValue())));
        List<String> documents = plannedDocuments == null ? List.of()
                : plannedDocuments.stream().map(this::trim).filter(Objects::nonNull).toList();
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("snapshotVersion", CONFIRMATION_SNAPSHOT_VERSION);
        root.put("facts", normalizedFacts);
        root.put("plannedDocumentNames", documents);
        String canonicalJson = json(root);
        String hash = OaSignOnboardExcelParser.sha256(
                canonicalJson.getBytes(StandardCharsets.UTF_8));
        return new EmployeeConfirmationSnapshot(CONFIRMATION_SNAPSHOT_VERSION,
                Collections.unmodifiableMap(normalizedFacts), List.copyOf(documents),
                canonicalJson, hash);
    }

    private void putFact(Map<String, Object> facts, String name, Object value)
    {
        facts.put(name, normalizeFact(value));
    }

    private Object normalizeFact(Object value)
    {
        if (value == null) return null;
        if (value instanceof BigDecimal decimal)
            return decimal.stripTrailingZeros().toPlainString();
        if (value instanceof LocalDate date) return date.toString();
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    Set<String> templateGateErrors(Long versionId, OaSignOnboardContractSnapshot snapshot)
    {
        Set<String> types = applicableTemplates(versionId, snapshot).stream()
                .map(OaSignPlanVersionTemplate::getTemplateType)
                .filter(Objects::nonNull).map(this::upper)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        LinkedHashSet<String> errors = new LinkedHashSet<>();
        boolean service = SigningProfileCodes.SERVICE_CONTRACT.equals(snapshot.getContractTypeCode());
        List<String> required = service
                ? List.of(OaSignTemplateType.ONBOARD_COMMITMENT,
                        OaSignTemplateType.ONBOARD_SERVICE_CONTRACT,
                        OaSignTemplateType.ONBOARD_SERVICE_RECEIPT)
                : List.of(OaSignTemplateType.ONBOARD_COMMITMENT,
                        OaSignTemplateType.ONBOARD_LABOR_CONTRACT,
                        OaSignTemplateType.ONBOARD_HANDBOOK_RECEIPT,
                        OaSignTemplateType.ONBOARD_SALARY_CONFIRM);
        for (String type : required)
            if (!types.contains(type)) errors.add("PLAN_MISSING_" + type);
        try
        {
            if (Integer.parseInt(snapshot.getJobGradeCode()) >= 7
                    && !types.contains(OaSignTemplateType.ONBOARD_CONFIDENTIAL_NONCOMPETE))
                errors.add("PLAN_MISSING_" + OaSignTemplateType.ONBOARD_CONFIDENTIAL_NONCOMPETE);
        }
        catch (RuntimeException ignored) { }
        int age = ROW_POLICY.ageAt(snapshot.getIdNumber(), snapshot.getContractStartDate());
        if (age >= 16 && age < 18 && "NON_STUDENT".equals(upper(snapshot.getStudentStatus()))
                && !types.contains(OaSignTemplateType.ONBOARD_MINOR_NONSTUDENT_DECLARATION))
            errors.add("PLAN_MISSING_" + OaSignTemplateType.ONBOARD_MINOR_NONSTUDENT_DECLARATION);
        return errors;
    }

    private List<OaSignPlanVersionTemplate> applicableTemplates(Long versionId,
            OaSignOnboardContractSnapshot snapshot)
    {
        return applicableTemplates(planVersionMapper.selectTemplatesByVersionId(versionId),
                snapshot);
    }

    private List<OaSignPlanVersionTemplate> applicableTemplates(
            List<OaSignPlanVersionTemplate> templates,
            OaSignOnboardContractSnapshot snapshot)
    {
        if (templates == null || templates.isEmpty()) return Collections.emptyList();
        OaSignPackage packageFact = new OaSignPackage();
        packageFact.setEmploymentType(snapshot.getContractTypeCode());
        packageFact.setSocialType(snapshot.getSocialTypeCode());
        packageFact.setSalaryVersion(snapshot.getSalaryVersion());
        packageFact.setPostLevelSnapshot(snapshot.getJobGradeCode());
        packageFact.setEmployeeIdCardSnapshot(snapshot.getIdNumber());
        packageFact.setContractStartDate(snapshot.getContractStartDate() == null ? null
                : snapshot.getContractStartDate().toString());
        packageFact.setStudentStatusSnapshot(snapshot.getStudentStatus());
        List<OaSignPlanVersionTemplate> result = new ArrayList<>();
        for (OaSignPlanVersionTemplate item : templates)
        {
            OaSignTemplate template =
                    OaSignTemplateApplicabilityPolicy.fromVersionSnapshot(item);
            if (template != null
                    && OaSignTemplateApplicabilityPolicy.shouldIncludeTemplate(
                            template, packageFact))
                result.add(item);
        }
        return result;
    }

    void refreshSummary(Long batchId)
    {
        List<OaSignOnboardImportRow> rows = rowMapper.selectByBatchId(batchId);
        // SIGNATURE_FIRST deliberately creates the real task/package before the employee
        // supplies facts or HR selects a company. A task id therefore proves only that the first
        // package was staged; final generation is represented by the import row's terminal
        // generation/send state.
        int generated = (int) rows.stream()
                .filter(v -> FINAL_GENERATED_ROW_STATUSES.contains(v.getStatus())).count();
        int matched = (int) rows.stream().filter(v -> v.getEmployeeId() != null
                && !"MISSING".equals(v.getMatchType())).count();
        int excluded = (int) rows.stream().filter(v -> "EXCLUDED".equals(v.getStatus())).count();
        int errors = (int) rows.stream().filter(this::hasErrors).count();
        int warnings = (int) rows.stream().filter(this::hasWarnings).count();
        long generatable = rows.stream().filter(v -> v.getEmployeeId() != null).count();
        String status = generated == 0 ? "PREVIEW_READY"
                : generated >= generatable ? "GENERATED" : "PARTIAL_GENERATED";
        batchMapper.updateSummary(batchId, status, matched, excluded, errors, warnings, generated);
    }

    private boolean hasErrors(OaSignOnboardImportRow row) { return !list(row.getErrorCodesJson()).isEmpty(); }
    private boolean hasWarnings(OaSignOnboardImportRow row) { return !list(row.getWarningCodesJson()).isEmpty(); }

    private List<Long> normalizeIds(List<Long> values)
    {
        if (values == null || values.isEmpty()) return Collections.emptyList();
        if (values.size() > MAX_SELECTED) throw new ServiceException("一次最多选择100名员工");
        LinkedHashSet<Long> result = new LinkedHashSet<>();
        for (Long value : values)
        {
            if (value == null || value <= 0) throw new ServiceException("员工编号无效");
            result.add(value);
        }
        return new ArrayList<>(result);
    }

    private String normalizeMatchMode(String value, List<Long> selected)
    {
        String mode = upper(value);
        if (!MATCH_MODE_MANUAL_SELECTED.equals(mode)
                && !MATCH_MODE_EXCEL_PHONE_NAME.equals(mode))
            throw new ServiceException("Excel员工匹配模式无效");
        if (MATCH_MODE_MANUAL_SELECTED.equals(mode) && selected.isEmpty())
            throw new ServiceException("手工选择模式请先勾选员工");
        if (MATCH_MODE_EXCEL_PHONE_NAME.equals(mode) && !selected.isEmpty())
            throw new ServiceException("Excel自动匹配模式不能同时限定已选员工");
        return mode;
    }

    private String selectionHash(String matchMode, List<Long> ids)
    {
        List<Long> sorted = new ArrayList<>(ids); Collections.sort(sorted);
        return OaSignOnboardExcelParser.sha256((matchMode + ":" + sorted)
                .getBytes(StandardCharsets.UTF_8));
    }

    private String route(OaSignOnboardContractSnapshot value)
    {
        int grade;
        try { grade = Integer.parseInt(value.getJobGradeCode()); }
        catch (Exception exception) { return null; }
        int band = grade <= 4 ? 1 : grade <= 6 ? 2 : 3;
        if (SigningProfileCodes.LABOR_CONTRACT.equals(value.getContractTypeCode()))
            return (SigningProfileCodes.SOCIAL_INSURED.equals(value.getSocialTypeCode()) ? "A" : "A")
                    + (SigningProfileCodes.SOCIAL_INSURED.equals(value.getSocialTypeCode()) ? band : band + 3);
        if (SigningProfileCodes.SERVICE_CONTRACT.equals(value.getContractTypeCode())
                && SigningProfileCodes.SOCIAL_UNINSURED.equals(value.getSocialTypeCode())) return "B" + band;
        return null;
    }

    private boolean sameName(String first, String second) { return Objects.equals(trim(first), trim(second)); }
    private String normalizeId(String value) { return value == null ? null : value.replace(" ", "").toUpperCase(Locale.ROOT); }
    private String displayName(SignCandidateUser value) { return value == null ? null : blank(value.getNickName()) ? trim(value.getUserName()) : trim(value.getNickName()); }
    private String maskName(String value) { value = trim(value); return value == null ? null : value.length() == 1 ? "*" : value.charAt(0) + "*".repeat(Math.min(3, value.length() - 1)); }
    private String maskPhone(String value) { value = trim(value); return value == null ? null : value.length() >= 7 ? value.substring(0, 3) + "****" + value.substring(value.length() - 4) : "****"; }
    private String maskId(String value) { value = trim(value); return value == null ? null : value.length() >= 8 ? value.substring(0, 4) + "**********" + value.substring(value.length() - 4) : "********"; }
    private String maskAddress(String value) { value = trim(value); return value == null ? null : value.substring(0, Math.min(6, value.length())) + "***"; }
    private String text(String value, int max) { String result = trim(value); if (result != null && result.length() > max) throw new ServiceException("填写内容不能超过" + max + "个字符"); return result; }
    private String limit(String value, int max) { return value == null ? null : value.length() <= max ? value : value.substring(0, max); }
    private String upper(String value) { value = trim(value); return value == null ? null : value.toUpperCase(Locale.ROOT); }
    private String trim(String value) { if (value == null) return null; String result = value.trim(); return result.isEmpty() ? null : result; }
    private boolean blank(String value) { return trim(value) == null; }

    record MatchedSource(OaSignOnboardExcelParser.ParsedRow parsed,
            SignCandidateUser candidate, String matchType, List<String> errors, boolean extra) { }
    record EmployeeConfirmationSnapshot(String version, Map<String, Object> facts,
            List<String> plannedDocumentNames, String json, String hash) { }
    private record PlanView(String name, List<String> templates) { }
}
