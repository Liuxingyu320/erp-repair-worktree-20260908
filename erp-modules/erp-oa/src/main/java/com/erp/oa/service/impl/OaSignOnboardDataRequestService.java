package com.erp.oa.service.impl;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.erp.common.core.constant.SecurityConstants;
import com.erp.common.core.domain.R;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.security.utils.SecurityUtils;
import com.erp.oa.domain.OaSignOnboardDataRequest;
import com.erp.oa.domain.OaSignOnboardImportBatch;
import com.erp.oa.domain.OaSignOnboardImportRow;
import com.erp.oa.domain.dto.OaSignOnboardDataReviewRequest;
import com.erp.oa.domain.dto.OaSignOnboardDataSubmitRequest;
import com.erp.oa.domain.dto.OaSignOnboardImportRowUpdateRequest;
import com.erp.oa.domain.vo.OaSignOnboardDataRequestView;
import com.erp.oa.constant.OaSignSigningSequence;
import com.erp.oa.mapper.OaSignOnboardDataRequestMapper;
import com.erp.oa.mapper.OaSignOnboardImportRowMapper;
import com.erp.oa.service.IOaSignPackageService;
import com.erp.system.api.RemoteUserService;
import com.erp.system.api.domain.ReviewedSignProfileSupplement;
import com.erp.system.api.domain.ReviewedSignProfileSupplementResult;

/** Employee submission and HR-reviewed, compare-and-set System profile synchronization. */
@Service
public class OaSignOnboardDataRequestService
{
    private static final String SIGNATURE_CONFIRMATION_TEXT =
            "本人已核对本入职签约包的合同生成信息，并完成本合同包唯一一次手写签名";
    private static final String PNG_PREFIX = "data:image/png;base64,";
    private static final int MAX_SIGNATURE_BYTES = 5 * 1024 * 1024;
    private static final byte[] PNG_MAGIC = new byte[] {
            (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a };
    /** Employee-visible contract facts. Internal company/seal IDs, URLs and hashes stay server-side. */
    private static final List<String> EMPLOYEE_VISIBLE_FACT_FIELDS = List.of(
            "employeeName", "idNumber", "phone", "currentAddress",
            "contractTypeCode", "socialTypeCode", "employeePost", "jobGradeCode",
            "workLocation", "cityLevel", "contractTermCode",
            "contractStartDate", "contractEndDate", "probationStartDate",
            "probationEndDate", "workSchedule", "salaryTotal", "baseSalary",
            "postSalary", "fieldAllowance", "performanceSalary", "salaryVersion",
            "servicePersonType", "insuranceType",
            "studentStatus", "schoolName", "retirementStatus", "incomeStartYearMonth");
    private final OaSignOnboardDataRequestMapper requestMapper;
    private final OaSignOnboardImportRowMapper rowMapper;
    private final OaSignOnboardImportService importService;
    private final OaSignHrAccessService hrAccessService;
    private final RemoteUserService remoteUserService;
    private final ObjectMapper objectMapper;
    private final IOaSignPackageService packageService;
    private final OaSignOnboardGenerationService generationService;
    private final TransactionOperations localTransaction;

    @Autowired
    public OaSignOnboardDataRequestService(OaSignOnboardDataRequestMapper requestMapper,
            OaSignOnboardImportRowMapper rowMapper,
            OaSignOnboardImportService importService,
            OaSignHrAccessService hrAccessService,
            RemoteUserService remoteUserService,
            ObjectMapper objectMapper,
            IOaSignPackageService packageService,
            OaSignOnboardGenerationService generationService,
            PlatformTransactionManager transactionManager)
    {
        this(requestMapper, rowMapper, importService, hrAccessService, remoteUserService,
                objectMapper, packageService, generationService,
                new TransactionTemplate(transactionManager));
    }

    OaSignOnboardDataRequestService(OaSignOnboardDataRequestMapper requestMapper,
            OaSignOnboardImportRowMapper rowMapper,
            OaSignOnboardImportService importService,
            OaSignHrAccessService hrAccessService,
            RemoteUserService remoteUserService,
            ObjectMapper objectMapper,
            IOaSignPackageService packageService)
    {
        this(requestMapper, rowMapper, importService, hrAccessService, remoteUserService,
                objectMapper, packageService, null,
                directTransactions());
    }

    OaSignOnboardDataRequestService(OaSignOnboardDataRequestMapper requestMapper,
            OaSignOnboardImportRowMapper rowMapper,
            OaSignOnboardImportService importService,
            OaSignHrAccessService hrAccessService,
            RemoteUserService remoteUserService,
            ObjectMapper objectMapper,
            IOaSignPackageService packageService,
            OaSignOnboardGenerationService generationService)
    {
        this(requestMapper, rowMapper, importService, hrAccessService, remoteUserService,
                objectMapper, packageService, generationService, directTransactions());
    }

    OaSignOnboardDataRequestService(OaSignOnboardDataRequestMapper requestMapper,
            OaSignOnboardImportRowMapper rowMapper,
            OaSignOnboardImportService importService,
            OaSignHrAccessService hrAccessService,
            RemoteUserService remoteUserService,
            ObjectMapper objectMapper,
            IOaSignPackageService packageService,
            PlatformTransactionManager transactionManager)
    {
        this(requestMapper, rowMapper, importService, hrAccessService, remoteUserService,
                objectMapper, packageService, null,
                new TransactionTemplate(transactionManager));
    }

    OaSignOnboardDataRequestService(OaSignOnboardDataRequestMapper requestMapper,
            OaSignOnboardImportRowMapper rowMapper,
            OaSignOnboardImportService importService,
            OaSignHrAccessService hrAccessService,
            RemoteUserService remoteUserService,
            ObjectMapper objectMapper,
            IOaSignPackageService packageService,
            TransactionOperations localTransaction)
    {
        this(requestMapper, rowMapper, importService, hrAccessService, remoteUserService,
                objectMapper, packageService, null, localTransaction);
    }

    OaSignOnboardDataRequestService(OaSignOnboardDataRequestMapper requestMapper,
            OaSignOnboardImportRowMapper rowMapper,
            OaSignOnboardImportService importService,
            OaSignHrAccessService hrAccessService,
            RemoteUserService remoteUserService,
            ObjectMapper objectMapper,
            IOaSignPackageService packageService,
            OaSignOnboardGenerationService generationService,
            TransactionOperations localTransaction)
    {
        this.requestMapper = requestMapper;
        this.rowMapper = rowMapper;
        this.importService = importService;
        this.hrAccessService = hrAccessService;
        this.remoteUserService = remoteUserService;
        this.objectMapper = objectMapper;
        this.packageService = packageService;
        this.generationService = generationService;
        this.localTransaction = Objects.requireNonNull(localTransaction, "localTransaction");
    }

    private static TransactionOperations directTransactions()
    {
        return new TransactionOperations()
        {
            @Override
            public <T> T execute(TransactionCallback<T> callback)
            {
                return callback.doInTransaction(null);
            }
        };
    }

    public List<OaSignOnboardDataRequestView> mine()
    {
        importService.requireEnabled();
        List<OaSignOnboardDataRequestView> result = new ArrayList<>();
        List<OaSignOnboardDataRequest> rows = requestMapper.selectMine(SecurityUtils.getUserId());
        if (rows != null) for (OaSignOnboardDataRequest request : rows)
        {
            requireDataRequestRowBinding(request, rowMapper.selectById(request.getRowId()));
            result.add(view(request));
        }
        return result;
    }

    public OaSignOnboardDataRequestView detail(Long requestId, Long selectedShopDeptId)
    {
        importService.requireEnabled();
        OaSignOnboardDataRequest request = require(requestId);
        OaSignOnboardImportRow row = rowMapper.selectById(request.getRowId());
        requireDataRequestRowBinding(request, row);
        if (!Objects.equals(request.getEmployeeId(), SecurityUtils.getUserId()))
        {
            if (!hrAccessService.isCurrentHr())
                throw new ServiceException("补资料任务不存在或无权访问");
            OaSignOnboardImportBatch batch = importService.requireHrBatch(
                    request.getBatchId(), selectedShopDeptId);
            importService.requireCurrentHrTaskOwnership(row, batch);
        }
        return view(request);
    }

    @Transactional(rollbackFor = Exception.class)
    public OaSignOnboardDataRequestView submit(Long requestId,
            OaSignOnboardDataSubmitRequest action)
    {
        importService.requireEnabled();
        OaSignOnboardDataRequest request = require(requestId);
        Long employeeId = SecurityUtils.getUserId();
        if (!Objects.equals(request.getEmployeeId(), employeeId))
            throw new ServiceException("补资料任务不存在或无权访问");
        OaSignOnboardImportRow row = rowMapper.selectById(request.getRowId());
        requireDataRequestRowBinding(request, row);
        if (action == null)
            throw new ServiceException("补资料任务已变化，请刷新后提交");
        List<String> allowed = stringList(request.getAllowedFieldsJson());
        Map<String, Object> values = employeeValues(action, allowed);
        String signingSequence = OaSignSigningSequence.normalize(request.getSigningSequence());
        boolean signatureFirst = OaSignSigningSequence.signatureFirst(signingSequence);
        boolean editable = List.of("PENDING_EMPLOYEE", "REJECTED")
                .contains(request.getStatus());
        boolean signatureCaptured = signatureFirst && hasCapturedSignature(request);
        if (signatureFirst && hasAnySignatureEvidence(request) && !signatureCaptured)
        {
            throw new ServiceException("已留存的唯一手写签名证据不完整，请联系HR处理");
        }
        SignatureCapture signature = null;
        String signaturePayloadHash = null;
        boolean incomingSignature = hasIncomingSignature(action);
        if (signatureCaptured && !incomingSignature)
        {
            signature = persistedSignature(request);
            signaturePayloadHash = signaturePayloadHash(values, signature);
            if (!editable)
            {
                if (!Objects.equals(request.getSignaturePayloadHash(), signaturePayloadHash))
                    throw new ServiceException("相同唯一签名的事实更正内容不一致");
                bindCompletedStagedSignature(request);
                return view(request);
            }
        }
        else
        {
            if (signatureCaptured && editable) rejectCapturedSignatureOverwrite(action);
            signature = captureSignature(signingSequence, action);
            signaturePayloadHash = signaturePayloadHash(values, signature);
            if (signatureFirst && request.getSignatureRequestId() != null)
            {
                if (!Objects.equals(request.getSignatureRequestId(), signature.requestId()))
                    throw new ServiceException("当前补资料任务已使用其他签名请求提交");
                if (!Objects.equals(request.getSignaturePayloadHash(), signaturePayloadHash))
                    throw new ServiceException("相同签名请求编号的提交内容不一致");
                bindCompletedStagedSignature(request);
                return view(request);
            }
        }
        if (!Objects.equals(request.getVersion(), action.getVersion()))
            throw new ServiceException("补资料任务已变化，请刷新后提交");
        if (!editable)
            throw new ServiceException("当前补资料任务不能提交");
        if (signatureFirst)
            importService.requireEmployeeConfirmationCurrent(request, row, Map.of());
        boolean signatureOnly = values.isEmpty()
                && signatureFirst;
        String requestTarget = signatureOnly ? "COMPLETED" : "SUBMITTED";
        String profileSyncStatus = signatureOnly ? "COMPLETED" : "NOT_STARTED";
        String rowTarget = signatureOnly ? "READY_TO_GENERATE" : "PENDING_HR_REVIEW";
        int requestUpdated = signatureCaptured
                ? requestMapper.resubmitPreservingSignature(requestId, employeeId,
                        json(values), signaturePayloadHash, request.getSignaturePayloadHash(),
                        requestTarget, profileSyncStatus, request.getVersion())
                : requestMapper.submit(requestId, employeeId, json(values),
                        signature.confirmationText(), signature.requestId(),
                        signaturePayloadHash, signature.bytes(), signature.hash(),
                        signature.capturedTime(), requestTarget, profileSyncStatus,
                        request.getVersion());
        if (requestUpdated != 1
                || rowMapper.updateStatus(row.getRowId(), rowTarget, row.getVersion()) != 1)
            throw new ServiceException("补资料任务已变化，请刷新后提交");
        OaSignOnboardDataRequest submitted = requestMapper.selectById(requestId);
        if (signatureOnly) bindCompletedStagedSignature(submitted);
        return view(submitted);
    }

    public OaSignOnboardDataRequestView review(Long requestId,
            OaSignOnboardDataReviewRequest action, Long selectedShopDeptId)
    {
        importService.requireEnabled();
        hrAccessService.requireCurrentHr();
        OaSignOnboardDataRequest request = require(requestId);
        OaSignOnboardImportBatch batch = importService.requireHrBatch(
                request.getBatchId(), selectedShopDeptId);
        OaSignOnboardImportRow row = importService.requireBatchRow(
                request.getBatchId(), request.getRowId());
        requireDataRequestRowBinding(request, row);
        importService.requireCurrentHrTaskOwnership(row, batch);
        Long initiatingOperatorUserId = SecurityUtils.getUserId();
        if (action == null || !Objects.equals(request.getVersion(), action.getVersion()))
            throw new ServiceException("补资料任务已变化，请刷新后审核");
        String decision = upper(action.getAction());
        if ("REJECT".equals(decision))
        {
            return withCurrentOwnerTaskLock(batch, row, initiatingOperatorUserId,
                    () -> rejectOwned(request.getRequestId(), action, batch));
        }
        if (!"APPROVE".equals(decision)) throw new ServiceException("审核动作无效");
        return approve(request, action, selectedShopDeptId, batch, row,
                initiatingOperatorUserId);
    }

    private <T> T withCurrentOwnerTaskLock(OaSignOnboardImportBatch batch,
            OaSignOnboardImportRow row, Long initiatingOperatorUserId, Supplier<T> work)
    {
        // Package-private constructors keep pure unit tests lightweight. The Spring constructor
        // always supplies the generation service, whose helper performs the canonical task lock.
        if (generationService == null) return work.get();
        return generationService.withCurrentOwnerTaskLock(
                batch, row, initiatingOperatorUserId, work);
    }

    private OaSignOnboardDataRequestView rejectOwned(Long requestId,
            OaSignOnboardDataReviewRequest action, OaSignOnboardImportBatch batch)
    {
        if (blank(action.getReason())) throw new ServiceException("驳回时必须填写原因");
        OaSignOnboardDataRequest rejected = localTransaction.execute(status -> {
            OaSignOnboardDataRequest request = require(requestId);
            if (!Objects.equals(request.getVersion(), action.getVersion()))
                throw new ServiceException("补资料任务已变化，请刷新后审核");
            OaSignOnboardImportRow row = rowMapper.selectById(request.getRowId());
            requireDataRequestRowBinding(request, row);
            importService.requireCurrentHrTaskOwnership(row, batch);
            String from = request.getStatus();
            if ("PROFILE_SYNC_FAILED".equals(from))
                throw new ServiceException("档案同步结果未知时不能改值驳回，请使用原请求重试审核");
            if (!"SUBMITTED".equals(from))
                throw new ServiceException("当前补资料任务不能驳回");
            if (requestMapper.review(requestId, from, "REJECTED", SecurityUtils.getUserId(),
                    limit(action.getReason(), 500), null, "NOT_STARTED",
                    request.getProfileSyncRequestId(), request.getVersion()) != 1
                    || rowMapper.updateStatus(row.getRowId(), "WAITING_EMPLOYEE_DATA",
                            row.getVersion()) != 1)
            {
                throw new ServiceException("补资料任务或导入行状态已变化，请刷新后审核");
            }
            OaSignOnboardDataRequest current = requestMapper.selectById(requestId);
            if (current == null) throw new ServiceException("补资料驳回结果保存失败");
            return current;
        });
        if (rejected == null) throw new ServiceException("补资料驳回结果保存失败");
        return view(rejected);
    }

    private OaSignOnboardDataRequestView approve(OaSignOnboardDataRequest request,
            OaSignOnboardDataReviewRequest action, Long selectedShopDeptId,
            OaSignOnboardImportBatch batch, OaSignOnboardImportRow row,
            Long initiatingOperatorUserId)
    {
        if ("COMPLETED".equals(request.getStatus())
                && OaSignSigningSequence.signatureFirst(request.getSigningSequence()))
        {
            // Repairs an older partial completion and makes an HR retry idempotently finish the
            // staged package binding without replaying the already-applied remote profile write.
            return withCurrentOwnerTaskLock(batch, row, initiatingOperatorUserId, () -> {
                OaSignOnboardDataRequest current = require(request.getRequestId());
                OaSignOnboardImportRow currentRow = rowMapper.selectById(current.getRowId());
                requireDataRequestRowBinding(current, currentRow);
                importService.requireCurrentHrTaskOwnership(currentRow, batch);
                return completeProfileSync(current.getRequestId(), null);
            });
        }
        ApprovalPreparation prepared = withCurrentOwnerTaskLock(
                batch, row, initiatingOperatorUserId,
                () -> localTransaction.execute(status -> prepareApproval(
                        request.getRequestId(), action, batch)));
        if (prepared == null) throw new ServiceException("补资料审核准备未完成");

        ReviewedSignProfileSupplementResult sync;
        try
        {
            R<ReviewedSignProfileSupplementResult> response = remoteUserService.supplementSigningProfile(
                    prepared.supplement(), SecurityConstants.INNER);
            sync = response == null || R.isError(response) ? null : response.getData();
            if (sync == null || !sync.isApplied() && !sync.isReplayed())
                throw new ServiceException("档案同步结果无法确认");
        }
        catch (RuntimeException exception)
        {
            markSyncFailedWithCurrentOwner(batch, prepared.request().getRequestId(),
                    prepared.row().getRowId(), initiatingOperatorUserId);
            throw new ServiceException("员工档案已变化或同步失败，请重新预览后审核");
        }

        try
        {
            return finishApproval(prepared, sync, selectedShopDeptId, batch,
                    initiatingOperatorUserId);
        }
        catch (RuntimeException exception)
        {
            markSyncFailedWithCurrentOwner(batch, prepared.request().getRequestId(),
                    prepared.row().getRowId(), initiatingOperatorUserId);
            throw exception;
        }
    }

    private ApprovalPreparation prepareApproval(Long requestId,
            OaSignOnboardDataReviewRequest action, OaSignOnboardImportBatch batch)
    {
        OaSignOnboardDataRequest request = require(requestId);
        if (!Objects.equals(request.getVersion(), action.getVersion()))
            throw new ServiceException("补资料任务已变化，请刷新后审核");
        OaSignOnboardImportRow row = rowMapper.selectById(request.getRowId());
        requireDataRequestRowBinding(request, row);
        importService.requireCurrentHrTaskOwnership(row, batch);
        Map<String, Object> submitted = objectMap(request.getSubmittedValuesJson());
        if (submitted.isEmpty()) throw new ServiceException("员工尚未提交补充资料");
        var snapshot = importService.snapshot(row);
        boolean serviceContract = "SERVICE_CONTRACT".equals(snapshot.getContractTypeCode());
        Map<String, Object> approvedHrValues;
        if ("SUBMITTED".equals(request.getStatus()))
        {
            approvedHrValues = freezeApprovedHrValues(action, serviceContract);
            if (requestMapper.review(requestId, "SUBMITTED", "APPROVED",
                    SecurityUtils.getUserId(), limit(action.getReason(), 500),
                    json(approvedHrValues), "PENDING",
                    request.getProfileSyncRequestId(), request.getVersion()) != 1)
            {
                throw new ServiceException("补资料任务已变化，请刷新后审核");
            }
            request = require(requestId);
        }
        else if (List.of("APPROVED", "PROFILE_SYNC_FAILED").contains(request.getStatus()))
        {
            // A retry always reuses the HR facts and System idempotency key frozen by the first
            // approval; a changed UI payload cannot alter the remote operation after a crash.
            approvedHrValues = requireApprovedHrValues(request, serviceContract);
        }
        else
        {
            throw new ServiceException("当前补资料任务不能审核通过");
        }
        if (List.of("PENDING_HR_REVIEW", "PROFILE_SYNC_FAILED")
                .contains(row.getStatus()))
        {
            if (rowMapper.updateStatus(row.getRowId(), "PROFILE_SYNC_APPLYING",
                    row.getVersion()) != 1)
                throw new ServiceException("导入行已变化");
            row = rowMapper.selectById(row.getRowId());
        }
        else if (!List.of("PROFILE_SYNC_APPLYING", "READY_TO_GENERATE")
                .contains(row.getStatus()))
        {
            throw new ServiceException("导入行已变化，无法恢复补资料审核");
        }
        requireDataRequestRowBinding(request, row);
        if (!List.of("PROFILE_SYNC_APPLYING", "READY_TO_GENERATE")
                .contains(row.getStatus()))
            throw new ServiceException("导入行同步状态保存失败");
        ReviewedSignProfileSupplement supplement = supplement(request, submitted);
        return new ApprovalPreparation(request, row, submitted,
                approvedHrValues, supplement);
    }

    private OaSignOnboardDataRequestView finishApproval(ApprovalPreparation prepared,
            ReviewedSignProfileSupplementResult sync, Long selectedShopDeptId,
            OaSignOnboardImportBatch batch, Long initiatingOperatorUserId)
    {
        OaSignOnboardDataRequest request = require(prepared.request().getRequestId());
        OaSignOnboardImportRow row = rowMapper.selectById(prepared.row().getRowId());
        requireDataRequestRowBinding(request, row);
        importService.requireCurrentHrTaskOwnership(row, batch);
        if (profileApplyAlreadyReflected(row, prepared.submitted(),
                prepared.approvedHrValues(), sync))
            return completeProfileSyncWithCurrentOwner(batch, row,
                    initiatingOperatorUserId, request.getRequestId(), sync);
        if (!"PROFILE_SYNC_APPLYING".equals(row.getStatus()))
            throw new ServiceException("导入行已变化，无法恢复补资料审核");
        OaSignOnboardImportRowUpdateRequest patch = new OaSignOnboardImportRowUpdateRequest();
        patch.setVersion(row.getVersion());
        patch.setCurrentAddress(prepared.supplement().getCurrentAddress());
        patch.setStudentStatus(prepared.supplement().getStudentStatus());
        patch.setSchoolName(prepared.supplement().getSchoolName());
        patch.setRetirementStatus(prepared.supplement().getRetirementStatus());
        patch.setIncomeStartYearMonth(prepared.supplement().getIncomeStartYearMonth());
        patch.setServicePersonType(text(prepared.approvedHrValues(), "servicePersonType"));
        patch.setInsuranceType(text(prepared.approvedHrValues(), "insuranceType"));
        importService.updateRowAfterProfileSync(request.getBatchId(), row.getRowId(), patch,
                selectedShopDeptId);
        request = require(request.getRequestId());
        row = rowMapper.selectById(row.getRowId());
        requireDataRequestRowBinding(request, row);
        importService.requireCurrentHrTaskOwnership(row, batch);
        return completeProfileSyncWithCurrentOwner(batch, row,
                initiatingOperatorUserId, request.getRequestId(), sync);
    }

    private OaSignOnboardDataRequestView completeProfileSyncWithCurrentOwner(
            OaSignOnboardImportBatch batch, OaSignOnboardImportRow row,
            Long initiatingOperatorUserId, Long requestId,
            ReviewedSignProfileSupplementResult sync)
    {
        return withCurrentOwnerTaskLock(batch, row, initiatingOperatorUserId, () -> {
            OaSignOnboardDataRequest current = require(requestId);
            OaSignOnboardImportRow currentRow = rowMapper.selectById(current.getRowId());
            requireDataRequestRowBinding(current, currentRow);
            importService.requireCurrentHrTaskOwnership(currentRow, batch);
            return completeProfileSync(requestId, sync);
        });
    }

    private ReviewedSignProfileSupplement supplement(OaSignOnboardDataRequest request,
            Map<String, Object> submitted)
    {
        ReviewedSignProfileSupplement supplement = new ReviewedSignProfileSupplement();
        supplement.setRequestId(request.getProfileSyncRequestId());
        supplement.setEmployeeId(request.getEmployeeId());
        supplement.setExpectedProfileHash(request.getProfileBeforeHash());
        supplement.setCurrentAddress(text(submitted, "currentAddress"));
        supplement.setStudentStatus(upper(text(submitted, "studentStatus")));
        supplement.setSchoolName(text(submitted, "schoolName"));
        supplement.setRetirementStatus(upper(text(submitted, "retirementStatus")));
        supplement.setIncomeStartYearMonth(text(submitted, "incomeStartYearMonth"));
        return supplement;
    }

    private void markSyncFailedWithCurrentOwner(OaSignOnboardImportBatch batch,
            Long requestId, Long rowId, Long initiatingOperatorUserId)
    {
        OaSignOnboardImportRow source = rowMapper.selectById(rowId);
        if (source == null) throw new ServiceException("导入行不存在");
        withCurrentOwnerTaskLock(batch, source, initiatingOperatorUserId, () -> {
            OaSignOnboardDataRequest request = require(requestId);
            OaSignOnboardImportRow row = rowMapper.selectById(rowId);
            requireDataRequestRowBinding(request, row);
            importService.requireCurrentHrTaskOwnership(row, batch);
            markSyncFailed(request, row);
            return null;
        });
    }

    private void requireDataRequestRowBinding(OaSignOnboardDataRequest request,
            OaSignOnboardImportRow row)
    {
        if (request == null || row == null
                || !Objects.equals(request.getBatchId(), row.getBatchId())
                || !Objects.equals(request.getRowId(), row.getRowId())
                || !Objects.equals(request.getEmployeeId(), row.getEmployeeId())
                || !Objects.equals(request.getRequestId(), row.getDataRequestId())
                || row.getTaskId() == null != (row.getPackageId() == null))
        {
            throw new ServiceException("补资料任务与导入行绑定不一致");
        }
    }

    private void markSyncFailed(OaSignOnboardDataRequest request, OaSignOnboardImportRow row)
    {
        if (request != null && List.of("APPROVED", "PROFILE_SYNC_FAILED").contains(request.getStatus()))
            requestMapper.updateProfileSync(request.getRequestId(), "PROFILE_SYNC_FAILED",
                    request.getProfileBeforeHash(), request.getProfileAfterHash(), request.getVersion());
        if (row != null && List.of("PENDING_HR_REVIEW", "PROFILE_SYNC_APPLYING",
                "PROFILE_SYNC_FAILED").contains(row.getStatus()))
            rowMapper.updateStatus(row.getRowId(), "PROFILE_SYNC_FAILED", row.getVersion());
    }

    private record ApprovalPreparation(OaSignOnboardDataRequest request,
            OaSignOnboardImportRow row, Map<String, Object> submitted,
            Map<String, Object> approvedHrValues,
            ReviewedSignProfileSupplement supplement) {}

    private Map<String, Object> freezeApprovedHrValues(OaSignOnboardDataReviewRequest action,
            boolean serviceContract)
    {
        Map<String, Object> values = new LinkedHashMap<>();
        if (!serviceContract) return values;
        String servicePersonType = upper(action.getServicePersonType());
        String insuranceType = upper(action.getInsuranceType());
        if (blank(servicePersonType) || blank(insuranceType))
            throw new ServiceException("劳务员工必须由HR确认劳务人员类型和保险类型");
        values.put("servicePersonType", servicePersonType);
        values.put("insuranceType", insuranceType);
        return values;
    }

    private Map<String, Object> requireApprovedHrValues(OaSignOnboardDataRequest request,
            boolean serviceContract)
    {
        if (blank(request.getApprovedHrValuesJson()))
            throw new ServiceException("补资料HR审批快照缺失，无法安全重试");
        Map<String, Object> values = objectMap(request.getApprovedHrValuesJson());
        if (!values.keySet().stream().allMatch(List.of("servicePersonType", "insuranceType")::contains))
            throw new ServiceException("补资料HR审批快照损坏");
        if (!serviceContract && !values.isEmpty())
            throw new ServiceException("补资料HR审批快照与合同类型不一致");
        if (serviceContract && (blank(text(values, "servicePersonType"))
                || blank(text(values, "insuranceType"))))
            throw new ServiceException("补资料HR审批快照不完整，无法安全重试");
        return values;
    }

    private boolean profileApplyAlreadyReflected(OaSignOnboardImportRow row,
            Map<String, Object> submitted, Map<String, Object> approvedHrValues,
            ReviewedSignProfileSupplementResult sync)
    {
        if (row == null || sync == null || blank(sync.getAfterHash())) return false;
        var snapshot = importService.snapshot(row);
        if (!Objects.equals(trim(snapshot.getProfileFactsHash()), trim(sync.getAfterHash()))) return false;
        if (!sameSubmitted(submitted, "currentAddress", snapshot.getCurrentAddress(), false)
                || !sameSubmitted(submitted, "studentStatus", snapshot.getStudentStatus(), true)
                || !sameSubmitted(submitted, "schoolName", snapshot.getSchoolName(), false)
                || !sameSubmitted(submitted, "retirementStatus", snapshot.getRetirementStatus(), true)
                || !sameSubmitted(submitted, "incomeStartYearMonth", snapshot.getIncomeStartYearMonth(), false))
            return false;
        return !approvedHrValues.containsKey("servicePersonType")
                || Objects.equals(upper(text(approvedHrValues, "servicePersonType")),
                        upper(snapshot.getServicePersonType()))
                && Objects.equals(upper(text(approvedHrValues, "insuranceType")),
                        upper(snapshot.getInsuranceType()));
    }

    private boolean sameSubmitted(Map<String, Object> submitted, String field,
            String currentValue, boolean uppercase)
    {
        if (!submitted.containsKey(field)) return true;
        String submittedValue = text(submitted, field);
        return uppercase ? Objects.equals(upper(submittedValue), upper(currentValue))
                : Objects.equals(trim(submittedValue), trim(currentValue));
    }

    private OaSignOnboardDataRequestView completeProfileSync(Long requestId,
            ReviewedSignProfileSupplementResult sync)
    {
        OaSignOnboardDataRequest completed = localTransaction.execute(status -> {
            OaSignOnboardDataRequest current = require(requestId);
            if (!"COMPLETED".equals(current.getStatus()))
            {
                if (sync == null || blank(sync.getBeforeHash()) || blank(sync.getAfterHash()))
                    throw new ServiceException("补资料档案同步证据缺失");
                if (requestMapper.updateProfileSync(current.getRequestId(), "COMPLETED",
                        sync.getBeforeHash(), sync.getAfterHash(), current.getVersion()) != 1)
                {
                    current = require(requestId);
                    if (!"COMPLETED".equals(current.getStatus()))
                        throw new ServiceException("补资料审核结果保存失败");
                }
                else
                {
                    current = require(requestId);
                }
            }
            // This must stay in the same local transaction as COMPLETED. The remote System
            // profile write happened before this boundary and is deliberately not rolled back;
            // any local failure leaves the request retryable instead of permanently half-bound.
            bindCompletedStagedSignature(current);
            return current;
        });
        if (completed == null) throw new ServiceException("补资料审核结果保存失败");
        return view(completed);
    }

    private boolean unexpectedTaskBinding(OaSignOnboardImportRow row,
            OaSignOnboardDataRequest request)
    {
        if (row == null || row.getTaskId() == null) return false;
        return row.getPackageId() == null || request == null
                || !Objects.equals(row.getDataRequestId(), request.getRequestId())
                || !OaSignSigningSequence.signatureFirst(request.getSigningSequence());
    }

    private void bindCompletedStagedSignature(OaSignOnboardDataRequest request)
    {
        if (request == null || !"COMPLETED".equals(request.getStatus())
                || !OaSignSigningSequence.signatureFirst(request.getSigningSequence()))
        {
            return;
        }
        OaSignOnboardImportRow row = rowMapper.selectById(request.getRowId());
        // packageId == null identifies a deployment-compatible legacy data request. It keeps
        // the old generate-then-send route; only newly staged requests use the unified package.
        if (row == null || row.getTaskId() == null || row.getPackageId() == null)
        {
            return;
        }
        if (!Objects.equals(row.getDataRequestId(), request.getRequestId())
                || !Objects.equals(row.getEmployeeId(), request.getEmployeeId()))
        {
            throw new ServiceException("补资料任务与入职签约包关联不一致");
        }
        byte[] sample = request.getSignatureSampleBytes();
        if (sample == null || sample.length == 0
                || blank(request.getSignatureRequestId())
                || blank(request.getSignatureSampleHash())
                || request.getSignatureSampleTime() == null
                || !request.getSignatureSampleHash().equalsIgnoreCase(sha256(sample)))
        {
            throw new ServiceException("入职签约包唯一手写签名不完整或校验不一致");
        }
        packageService.recordStagedSignatureFirstSampleForSystem(
                row.getPackageId(), row.getTaskId(), request.getRequestId(),
                request.getSignatureRequestId(), sample, request.getSignatureSampleHash(),
                request.getSignatureSampleTime());
    }

    private Map<String, Object> employeeValues(OaSignOnboardDataSubmitRequest action,
            List<String> allowed)
    {
        Map<String, Object> candidates = new LinkedHashMap<>();
        candidates.put("currentAddress", trim(action.getCurrentAddress()));
        candidates.put("studentStatus", upper(action.getStudentStatus()));
        candidates.put("schoolName", trim(action.getSchoolName()));
        candidates.put("retirementStatus", upper(action.getRetirementStatus()));
        candidates.put("incomeStartYearMonth", trim(action.getIncomeStartYearMonth()));
        Map<String, Object> result = new LinkedHashMap<>();
        for (String field : allowed)
        {
            if (!candidates.containsKey(field)) throw new ServiceException("补资料字段白名单无效");
            Object value = candidates.get(field);
            boolean conditionalOnStudentChoice = allowed.contains("studentStatus")
                    && List.of("schoolName", "incomeStartYearMonth").contains(field);
            if (value == null && !conditionalOnStudentChoice)
                throw new ServiceException("请完整填写需要补充的个人资料");
            result.put(field, value);
        }
        String student = (String) result.get("studentStatus");
        if (student != null && !List.of("STUDENT", "NON_STUDENT").contains(student))
            throw new ServiceException("在校状态无效");
        if ("STUDENT".equals(student) && blank((String) candidates.get("schoolName")))
            throw new ServiceException("在校员工必须填写学校信息");
        if ("STUDENT".equals(student))
        {
            result.put("schoolName", candidates.get("schoolName"));
            result.remove("incomeStartYearMonth");
        }
        if ("NON_STUDENT".equals(student))
        {
            result.remove("schoolName");
            if (allowed.contains("incomeStartYearMonth")
                    && blank((String) candidates.get("incomeStartYearMonth")))
                throw new ServiceException("未成年非在校员工必须填写个人劳动收入起始年月");
        }
        String retirement = (String) result.get("retirementStatus");
        if (retirement != null && !List.of("RETIRED", "NOT_RETIRED").contains(retirement))
            throw new ServiceException("退休状态无效");
        String month = (String) result.get("incomeStartYearMonth");
        if (month != null && !month.matches("[0-9]{4}-(0[1-9]|1[0-2])"))
            throw new ServiceException("个人劳动收入起始年月格式必须为yyyy-MM");
        String address = (String) result.get("currentAddress");
        if (address != null && address.length() > 255) throw new ServiceException("现住址不能超过255个字符");
        return result;
    }

    private OaSignOnboardDataRequest require(Long requestId)
    {
        if (requestId == null || requestId <= 0) throw new ServiceException("补资料任务编号无效");
        OaSignOnboardDataRequest request = requestMapper.selectById(requestId);
        if (request == null) throw new ServiceException("补资料任务不存在");
        return request;
    }

    private OaSignOnboardDataRequestView view(OaSignOnboardDataRequest request)
    {
        OaSignOnboardDataRequestView view = new OaSignOnboardDataRequestView();
        view.setRequestId(request.getRequestId()); view.setRowId(request.getRowId());
        view.setRequestNo(request.getRequestNo()); view.setStatus(request.getStatus());
        view.setSigningSequence(OaSignSigningSequence.normalize(request.getSigningSequence()));
        view.setAllowedFields(stringList(request.getAllowedFieldsJson()));
        OaSignOnboardImportRow sourceRow = rowMapper.selectById(request.getRowId());
        if (sourceRow != null)
        {
            view.setTaskId(sourceRow.getTaskId());
            view.setPackageId(sourceRow.getPackageId());
        }
        if (OaSignSigningSequence.signatureFirst(request.getSigningSequence()))
        {
            OaSignOnboardImportService.EmployeeConfirmationSnapshot frozen =
                    importService.requireFrozenEmployeeConfirmation(request);
            if (List.of("PENDING_EMPLOYEE", "REJECTED").contains(request.getStatus()))
                importService.requireEmployeeConfirmationCurrent(request, sourceRow, Map.of());
            view.setFactSnapshot(employeeVisibleFacts(frozen.facts()));
            view.setPlannedDocumentNames(frozen.plannedDocumentNames());
        }
        else
        {
            view.setFactSnapshot(employeeVisibleFactSnapshot(request.getFactSnapshotJson()));
            view.setPlannedDocumentNames(frozenDocumentNames(request.getFactSnapshotJson()));
        }
        view.setSubmittedValues(objectMap(request.getSubmittedValuesJson()));
        view.setReviewReason(request.getReviewReason()); view.setProfileSyncStatus(request.getProfileSyncStatus());
        view.setSubmittedTime(request.getSubmittedTime()); view.setVersion(request.getVersion());
        view.setSignatureCaptured(!blank(request.getSignatureSampleHash())
                && request.getSignatureSampleTime() != null);
        view.setSignatureSampleTime(request.getSignatureSampleTime());
        if (OaSignSigningSequence.signatureFirst(request.getSigningSequence()))
            view.setSignatureConfirmationPrompt(SIGNATURE_CONFIRMATION_TEXT);
        return view;
    }

    private SignatureCapture captureSignature(String signingSequence,
            OaSignOnboardDataSubmitRequest action)
    {
        if (!OaSignSigningSequence.signatureFirst(signingSequence))
        {
            if (!blank(action.getSignatureDataUrl()) || !blank(action.getSignatureRequestId())
                    || !blank(action.getFactConfirmationText()))
                throw new ServiceException("当前流程无需提前留存手写签名");
            return new SignatureCapture(null, null, null, null, null);
        }
        String confirmationText = trim(action.getFactConfirmationText());
        String requestId = trim(action.getSignatureRequestId());
        if (!SIGNATURE_CONFIRMATION_TEXT.equals(confirmationText))
            throw new ServiceException("请确认输入：" + SIGNATURE_CONFIRMATION_TEXT);
        if (blank(requestId) || requestId.length() > 64)
            throw new ServiceException("签名请求编号无效");
        String dataUrl = trim(action.getSignatureDataUrl());
        if (dataUrl == null || !dataUrl.startsWith(PNG_PREFIX))
            throw new ServiceException("请使用PNG格式留存手写签名");
        byte[] bytes;
        try
        {
            bytes = Base64.getDecoder().decode(dataUrl.substring(PNG_PREFIX.length()));
        }
        catch (IllegalArgumentException exception)
        {
            throw new ServiceException("手写签名图片编码无效");
        }
        if (bytes.length < PNG_MAGIC.length || bytes.length > MAX_SIGNATURE_BYTES)
            throw new ServiceException("手写签名图片大小无效");
        for (int index = 0; index < PNG_MAGIC.length; index++)
            if (bytes[index] != PNG_MAGIC[index])
                throw new ServiceException("手写签名图片不是有效PNG");
        return new SignatureCapture(confirmationText, requestId, bytes,
                sha256(bytes), new Date());
    }

    private boolean hasCapturedSignature(OaSignOnboardDataRequest request)
    {
        byte[] sample = request.getSignatureSampleBytes();
        if (!SIGNATURE_CONFIRMATION_TEXT.equals(trim(request.getFactConfirmationText()))
                || blank(request.getSignatureRequestId())
                || !isSha256(request.getSignaturePayloadHash())
                || sample == null || sample.length < PNG_MAGIC.length
                || sample.length > MAX_SIGNATURE_BYTES
                || !isSha256(request.getSignatureSampleHash())
                || request.getSignatureSampleTime() == null)
        {
            return false;
        }
        for (int index = 0; index < PNG_MAGIC.length; index++)
        {
            if (sample[index] != PNG_MAGIC[index]) return false;
        }
        return request.getSignatureSampleHash().equalsIgnoreCase(sha256(sample));
    }

    private boolean hasAnySignatureEvidence(OaSignOnboardDataRequest request)
    {
        byte[] sample = request.getSignatureSampleBytes();
        return !blank(request.getFactConfirmationText())
                || !blank(request.getSignatureRequestId())
                || !blank(request.getSignaturePayloadHash())
                || sample != null
                || !blank(request.getSignatureSampleHash())
                || request.getSignatureSampleTime() != null;
    }

    private void rejectCapturedSignatureOverwrite(OaSignOnboardDataSubmitRequest action)
    {
        if (!blank(action.getSignatureDataUrl())
                || !blank(action.getSignatureRequestId())
                || !blank(action.getFactConfirmationText()))
        {
            throw new ServiceException("本签约包已留存唯一手写签名，不得再次上传或覆盖");
        }
    }

    private boolean hasIncomingSignature(OaSignOnboardDataSubmitRequest action)
    {
        return !blank(action.getSignatureDataUrl())
                || !blank(action.getSignatureRequestId())
                || !blank(action.getFactConfirmationText());
    }

    private SignatureCapture persistedSignature(OaSignOnboardDataRequest request)
    {
        return new SignatureCapture(request.getFactConfirmationText(),
                request.getSignatureRequestId(), request.getSignatureSampleBytes(),
                request.getSignatureSampleHash(), request.getSignatureSampleTime());
    }

    private boolean isSha256(String value)
    {
        return value != null && value.matches("(?i)^[0-9a-f]{64}$");
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

    private record SignatureCapture(String confirmationText, String requestId,
            byte[] bytes, String hash, Date capturedTime) {}

    private List<String> stringList(String json)
    {
        try { return blank(json) ? new ArrayList<>() : objectMapper.readValue(json, new TypeReference<List<String>>() { }); }
        catch (Exception exception) { throw new ServiceException("补资料字段白名单损坏"); }
    }
    private Map<String, Object> objectMap(String json)
    {
        try { return blank(json) ? new LinkedHashMap<>() : objectMapper.readValue(json, new TypeReference<Map<String, Object>>() { }); }
        catch (Exception exception) { throw new ServiceException("员工补资料快照损坏"); }
    }
    private Map<String, Object> employeeVisibleFactSnapshot(String json)
    {
        Map<String, Object> source = objectMap(json);
        if (source.get("facts") instanceof Map<?, ?> nested)
        {
            source = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : nested.entrySet())
                source.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return employeeVisibleFacts(source);
    }
    private Map<String, Object> employeeVisibleFacts(Map<String, Object> source)
    {
        Map<String, Object> visible = new LinkedHashMap<>();
        for (String field : EMPLOYEE_VISIBLE_FACT_FIELDS)
        {
            if (source.containsKey(field)) visible.put(field, source.get(field));
        }
        return visible;
    }
    private List<String> frozenDocumentNames(String json)
    {
        Object value = objectMap(json).get("plannedDocumentNames");
        if (!(value instanceof List<?> documents)) return new ArrayList<>();
        return documents.stream().map(String::valueOf).toList();
    }
    private String signaturePayloadHash(Map<String, Object> values, SignatureCapture signature)
    {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("submittedValues", values == null ? Map.of() : values);
        payload.put("factConfirmationText", signature.confirmationText());
        payload.put("signatureRequestId", signature.requestId());
        payload.put("signatureSampleHash", signature.hash());
        return sha256(json(payload).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    private String json(Object value)
    {
        try { return objectMapper.writeValueAsString(value); }
        catch (Exception exception) { throw new ServiceException("员工补资料快照保存失败"); }
    }
    private String text(Map<String, Object> values, String name) { Object value = values.get(name); return value == null ? null : trim(String.valueOf(value)); }
    private String limit(String value, int max) { value = trim(value); return value == null || value.length() <= max ? value : value.substring(0, max); }
    private String upper(String value) { value = trim(value); return value == null ? null : value.toUpperCase(Locale.ROOT); }
    private String trim(String value) { if (value == null) return null; String result = value.trim(); return result.isEmpty() ? null : result; }
    private boolean blank(String value) { return trim(value) == null; }
}
