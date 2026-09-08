package com.erp.oa.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import com.erp.approval.api.RemoteApprovalService;
import com.erp.approval.api.domain.ApprovalBusinessCallbackRequest;
import com.erp.approval.api.domain.ApprovalBusinessCallbackResponse;
import com.erp.approval.api.domain.ApprovalStartRequest;
import com.erp.approval.api.domain.ApprovalWithdrawRequest;
import com.erp.common.core.constant.SecurityConstants;
import com.erp.common.core.domain.R;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.core.utils.StringUtils;
import com.erp.common.security.auth.AuthUtil;
import com.erp.common.security.shop.ShopScopeService;
import com.erp.common.security.utils.SecurityUtils;
import com.erp.oa.config.OaReimbursementProperties;
import com.erp.oa.domain.OaReimbursement;
import com.erp.oa.domain.OaReimbursementApprovalStartOutbox;
import com.erp.oa.domain.OaReimbursementInvoice;
import com.erp.oa.domain.OaReimbursementItem;
import com.erp.oa.domain.dto.OaInvoiceRecognitionUpdateRequest;
import com.erp.oa.mapper.OaDeptScopeMapper;
import com.erp.oa.mapper.OaReimbursementMapper;
import com.erp.oa.service.BusinessFeatureGate;
import com.erp.oa.service.IOaReimbursementService;
import com.erp.oa.service.invoice.OaInvoiceRecognitionCoordinator;
import com.erp.oa.service.invoice.OaInvoiceRecognitionResult;
import com.erp.system.api.domain.SysUser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class OaReimbursementServiceImpl
        implements IOaReimbursementService
{
    public static final String BUSINESS_CODE = "OA_REIMBURSEMENT";
    public static final String PERMISSION_FINANCE_LIST =
            "oa:reimbursement:finance:list";
    public static final String PERMISSION_FINANCE_EXPORT =
            "oa:reimbursement:finance:export";

    private static final String DRAFT = "draft";
    private static final String SUBMITTING = "submitting";
    private static final String PENDING = "pending";
    private static final String APPROVED = "approved";
    private static final String RETURNED = "returned";
    private static final String REJECTED = "rejected";
    private static final String WITHDRAWN = "withdrawn";
    private static final String TERMINATED = "terminated";
    private static final String EXPORT_NOT_EXPORTED = "not_exported";
    private static final Set<String> EDITABLE = Set.of(
            DRAFT, RETURNED, WITHDRAWN);

    private final OaReimbursementMapper mapper;
    private final OaDeptScopeMapper deptScopeMapper;
    private final ShopScopeService shopScopeService;
    private final RemoteApprovalService approvalService;
    private final OaReimbursementApprovalStartOutboxService
            approvalStartOutboxService;
    private final OaReimbursementApprovalStartAfterCommitTrigger
            approvalStartTrigger;
    private final ObjectMapper objectMapper;
    private final BusinessFeatureGate featureGate;
    private final OaReimbursementFileStorageService fileStorage;
    private final OaInvoiceRecognitionCoordinator recognitionCoordinator;
    private final int maxInvoicesPerClaim;

    public OaReimbursementServiceImpl(OaReimbursementMapper mapper,
            OaDeptScopeMapper deptScopeMapper,
            ShopScopeService shopScopeService,
            RemoteApprovalService approvalService,
            OaReimbursementApprovalStartOutboxService
                    approvalStartOutboxService,
            OaReimbursementApprovalStartAfterCommitTrigger
                    approvalStartTrigger,
            ObjectMapper objectMapper,
            BusinessFeatureGate featureGate,
            OaReimbursementFileStorageService fileStorage,
            OaInvoiceRecognitionCoordinator recognitionCoordinator,
            OaReimbursementProperties properties)
    {
        this.mapper = mapper;
        this.deptScopeMapper = deptScopeMapper;
        this.shopScopeService = shopScopeService;
        this.approvalService = approvalService;
        this.approvalStartOutboxService = approvalStartOutboxService;
        this.approvalStartTrigger = approvalStartTrigger;
        this.objectMapper = objectMapper;
        this.featureGate = featureGate;
        this.fileStorage = fileStorage;
        this.recognitionCoordinator = recognitionCoordinator;
        this.maxInvoicesPerClaim = properties.getMaxInvoicesPerClaim();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OaReimbursement saveDraft(OaReimbursement requested,
            Long selectedShopDeptId)
    {
        if (requested == null)
        {
            throw new ServiceException("报销申请不能为空");
        }
        requested.setTitle(optionalText(requested.getTitle(),
                "报销标题", 120));
        requested.setPurpose(optionalText(requested.getPurpose(),
                "报销事由", 500));
        List<OaReimbursementItem> items = normalizedItems(
                requested.getItems(), false);
        BigDecimal total = total(items);
        if (requested.getReimbursementId() == null)
        {
            Long shopDeptId = shopScopeService.resolveRequiredShopDept(
                    selectedShopDeptId);
            SysUser user = SecurityUtils.getLoginUser().getSysUser();
            requested.setReimbursementNo(newReimbursementNo());
            requested.setApplicantId(user.getUserId());
            requested.setApplicantName(user.getUserName());
            requested.setApplicantDeptId(user.getDeptId());
            requested.setShopDeptId(shopDeptId);
            requested.setTotalAmount(total);
            requested.setStatus(DRAFT);
            requested.setExportStatus(EXPORT_NOT_EXPORTED);
            requested.setApprovalInstanceId(null);
            requested.setApprovalRound(0);
            requested.setRowVersion(0L);
            requested.setLastApprovalEventKey(null);
            requested.setCreateBy(SecurityUtils.getUsername());
            if (mapper.insertReimbursement(requested) != 1)
            {
                throw new ServiceException("报销草稿保存失败");
            }
        }
        else
        {
            OaReimbursement current = requireOwnedForUpdate(
                    requested.getReimbursementId(), selectedShopDeptId);
            if (!EDITABLE.contains(current.getStatus()))
            {
                throw new ServiceException("当前状态不允许修改报销申请");
            }
            requireVersion(requested.getRowVersion(), current.getRowVersion());
            requested.setShopDeptId(current.getShopDeptId());
            requested.setTotalAmount(total);
            requested.setStatus(current.getStatus());
            requested.setExportStatus(current.getExportStatus());
            requested.setApprovalInstanceId(current.getApprovalInstanceId());
            requested.setApprovalRound(current.getApprovalRound());
            requested.setLastApprovalEventKey(
                    current.getLastApprovalEventKey());
            requested.setRowVersion(current.getRowVersion());
            requested.setUpdateBy(SecurityUtils.getUsername());
            validateItemSources(requested.getReimbursementId(), items);
            updateOrThrow(requested, "报销申请已变化，请刷新后重试");
        }
        replaceItems(requested.getReimbursementId(), items);
        return loadDetail(requested.getReimbursementId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OaReimbursement submit(OaReimbursement requested,
            Long selectedShopDeptId)
    {
        if (requested == null)
        {
            throw new ServiceException("报销申请不能为空");
        }
        if (requested.getReimbursementId() != null)
        {
            OaReimbursement current = requireOwnedForUpdate(
                    requested.getReimbursementId(), selectedShopDeptId);
            if (PENDING.equals(current.getStatus())
                    && current.getApprovalInstanceId() != null)
            {
                return loadDetail(current.getReimbursementId());
            }
            if (SUBMITTING.equals(current.getStatus()))
            {
                OaReimbursementApprovalStartOutbox existing =
                        approvalStartOutboxService
                                .selectByReimbursementRound(
                                        current.getReimbursementId(),
                                        current.getApprovalRound());
                if (existing == null)
                {
                    throw new ServiceException(
                            "报销申请处于提交中，但审批发起记录缺失");
                }
                approvalStartTrigger.trigger(existing.getOutboxId());
                return loadDetail(current.getReimbursementId());
            }
        }
        featureGate.requireEnabled(BusinessFeatureGate.REIMBURSEMENT);
        validateSubmissionRequest(requested);
        OaReimbursement saved = saveDraft(requested, selectedShopDeptId);
        validateSubmittable(saved);

        int round = (saved.getApprovalRound() == null
                ? 0 : saved.getApprovalRound()) + 1;
        Long version = saved.getRowVersion() == null ? 0L
                : saved.getRowVersion();
        ApprovalStartRequest command = buildApprovalStartRequest(saved,
                round);
        if (mapper.markApprovalSubmitting(saved.getReimbursementId(),
                saved.getStatus(), version, round,
                SecurityUtils.getUsername()) != 1)
        {
            throw new ServiceException("报销申请已变化，无法提交");
        }
        saved.setStatus(SUBMITTING);
        saved.setApprovalInstanceId(null);
        saved.setApprovalRound(round);
        saved.setLastApprovalEventKey(null);
        saved.setRowVersion(version + 1);
        OaReimbursementApprovalStartOutbox outbox =
                approvalStartOutboxService.enqueue(saved, command,
                        SecurityUtils.getUsername());
        approvalStartTrigger.trigger(outbox.getOutboxId());
        return loadDetail(saved.getReimbursementId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OaReimbursement withdraw(Long reimbursementId, String reason,
            Long selectedShopDeptId)
    {
        OaReimbursement current = requireOwnedForUpdate(reimbursementId,
                selectedShopDeptId);
        if (WITHDRAWN.equals(current.getStatus()))
        {
            return loadDetail(reimbursementId);
        }
        if (!PENDING.equals(current.getStatus())
                || current.getApprovalInstanceId() == null)
        {
            throw new ServiceException("只有审批中的报销申请可以撤回");
        }
        ApprovalWithdrawRequest command = new ApprovalWithdrawRequest();
        command.setInstanceId(current.getApprovalInstanceId());
        command.setApplicantId(SecurityUtils.getUserId());
        command.setReason(normalizeReason(reason, "申请人撤回报销申请"));
        R<Boolean> response = approvalService.withdraw(command,
                SecurityConstants.INNER);
        if (response == null || !R.isSuccess(response)
                || !Boolean.TRUE.equals(response.getData()))
        {
            throw new ServiceException(response == null
                    ? "审批中心暂时不可用"
                    : StringUtils.defaultIfEmpty(response.getMsg(),
                            "撤回报销审批失败"));
        }
        return loadDetail(reimbursementId);
    }

    @Override
    public boolean isSubmissionEnabled()
    {
        return featureGate.isEnabled(BusinessFeatureGate.REIMBURSEMENT);
    }

    @Override
    public Map<String, Object> recognitionAvailability()
    {
        return recognitionCoordinator.availability();
    }

    @Override
    public OaReimbursement detail(Long reimbursementId,
            Long selectedShopDeptId)
    {
        OaReimbursement value = mapper.selectById(reimbursementId);
        requireReadable(value, selectedShopDeptId);
        return attachChildren(value);
    }

    @Override
    public List<OaReimbursement> selectMyList(OaReimbursement filter,
            Long selectedShopDeptId)
    {
        OaReimbursement query = filter == null
                ? new OaReimbursement() : filter;
        query.setApplicantId(SecurityUtils.getUserId());
        shopScopeService.appendShopScope(query, selectedShopDeptId);
        return mapper.selectMyList(query);
    }

    @Override
    public List<OaReimbursement> selectFinanceList(OaReimbursement filter,
            Long selectedShopDeptId)
    {
        OaReimbursement query = filter == null
                ? new OaReimbursement() : filter;
        // The finance ledger is intentionally approval-complete only.
        // Ignore any client-supplied business status instead of composing a
        // contradictory second status predicate in the mapper.
        query.setStatus(null);
        shopScopeService.appendShopScope(query, selectedShopDeptId);
        return mapper.selectFinanceList(query);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OaReimbursementInvoice uploadInvoice(Long reimbursementId,
            MultipartFile file, Long selectedShopDeptId)
    {
        OaReimbursement current = requireOwnedForUpdate(reimbursementId,
                selectedShopDeptId);
        if (!EDITABLE.contains(current.getStatus()))
        {
            throw new ServiceException("提交审批后不能修改发票");
        }
        OaReimbursementFileStorageService.StoredInvoice stored =
                fileStorage.storeInvoice(reimbursementId, file);
        registerRollbackCleanup(stored.relativePath());
        OaReimbursementInvoice existing = mapper
                .selectInvoiceByReimbursementAndSha(reimbursementId,
                        stored.sha256());
        if (existing != null)
        {
            fileStorage.delete(stored.relativePath());
            existing.setIdempotentReplay(true);
            return existing;
        }
        int count = mapper.countInvoices(reimbursementId);
        if (count >= maxInvoicesPerClaim)
        {
            throw new ServiceException("每张报销单最多上传"
                    + maxInvoicesPerClaim + "个发票文件");
        }
        Long duplicateId = mapper.selectApprovedDuplicateReimbursement(
                stored.sha256(), reimbursementId);
        OaReimbursementInvoice invoice = new OaReimbursementInvoice();
        invoice.setReimbursementId(reimbursementId);
        invoice.setOriginalName(stored.originalName());
        invoice.setStoredName(stored.storedName());
        invoice.setStoragePath(stored.relativePath());
        invoice.setContentType(stored.contentType());
        invoice.setFileExtension(stored.extension());
        invoice.setFileSize(stored.size());
        invoice.setSha256(stored.sha256());
        invoice.setDuplicateStatus(duplicateId == null ? "none" : "warning");
        invoice.setDuplicateReimbursementId(duplicateId);
        invoice.setSortNo(count + 1);
        invoice.setUploadedBy(SecurityUtils.getUserId());
        invoice.setUploadedByName(SecurityUtils.getUsername());
        try
        {
            if (mapper.insertInvoice(invoice) != 1)
            {
                throw new ServiceException("保存发票记录失败");
            }
        }
        catch (DuplicateKeyException duplicate)
        {
            OaReimbursementInvoice replay = mapper
                    .selectInvoiceByReimbursementAndSha(reimbursementId,
                            stored.sha256());
            if (replay == null)
            {
                throw duplicate;
            }
            fileStorage.delete(stored.relativePath());
            replay.setIdempotentReplay(true);
            return replay;
        }
        recognitionCoordinator.apply(invoice,
                recognitionCoordinator.pending());
        mapper.upsertInvoiceRecognition(invoice);
        advanceRowVersion(current, "报销申请已变化，请刷新后重试");
        return mapper.selectInvoiceById(invoice.getInvoiceId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OaReimbursementInvoice recognizeInvoice(Long reimbursementId,
            Long invoiceId, String engine, Long selectedShopDeptId)
    {
        OaReimbursement current = requireOwnedForUpdate(reimbursementId,
                selectedShopDeptId);
        requireInvoiceEditable(current);
        OaReimbursementInvoice invoice = requireInvoice(reimbursementId,
                invoiceId);
        OaInvoiceRecognitionResult result =
                recognitionCoordinator.recognize(
                        fileStorage.resolve(invoice.getStoragePath()),
                        invoice.getFileExtension(), engine);
        recognitionCoordinator.apply(invoice, result);
        invoice.setCorrectedBy(null);
        invoice.setCorrectedTime(null);
        mapper.upsertInvoiceRecognition(invoice);
        updateDuplicateByRecognition(invoice);
        advanceRowVersion(current, "报销申请已变化，请刷新后重试");
        return mapper.selectInvoiceById(invoiceId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OaReimbursementInvoice updateInvoiceRecognition(
            Long reimbursementId, Long invoiceId,
            OaInvoiceRecognitionUpdateRequest request,
            Long selectedShopDeptId)
    {
        OaReimbursement current = requireOwnedForUpdate(reimbursementId,
                selectedShopDeptId);
        requireInvoiceEditable(current);
        if (request == null)
        {
            throw new ServiceException("发票识别结果不能为空");
        }
        OaReimbursementInvoice invoice = requireInvoice(reimbursementId,
                invoiceId);
        invoice.setInvoiceType(optionalText(request.getInvoiceType(), 80));
        invoice.setInvoiceCode(identifier(request.getInvoiceCode(), 32));
        invoice.setInvoiceNumber(identifier(request.getInvoiceNumber(), 40));
        invoice.setInvoiceDate(request.getInvoiceDate());
        invoice.setSellerName(optionalText(request.getSellerName(), 200));
        invoice.setSellerTaxNo(identifier(request.getSellerTaxNo(), 40));
        invoice.setPurchaserName(optionalText(
                request.getPurchaserName(), 200));
        invoice.setPurchaserTaxNo(identifier(
                request.getPurchaserTaxNo(), 40));
        invoice.setAmountWithoutTax(amount(request.getAmountWithoutTax()));
        invoice.setTaxAmount(amount(request.getTaxAmount()));
        invoice.setInvoiceTotalAmount(amount(
                request.getInvoiceTotalAmount()));
        invoice.setCheckCode(identifier(request.getCheckCode(), 40));
        invoice.setServiceType(optionalText(request.getServiceType(), 80));
        invoice.setCommoditySummary(optionalText(
                request.getCommoditySummary(), 500));
        if (StringUtils.isBlank(invoice.getInvoiceNumber())
                && invoice.getInvoiceDate() == null
                && StringUtils.isBlank(invoice.getSellerName())
                && invoice.getInvoiceTotalAmount() == null)
        {
            throw new ServiceException("请至少填写发票号码、日期、销售方或价税合计");
        }
        invoice.setRecognitionStatus("corrected");
        invoice.setRecognitionEngine("manual");
        invoice.setRecognitionProvider("user");
        invoice.setRecognitionMessage("识别结果已由申请人核对修正");
        invoice.setCorrectedBy(SecurityUtils.getUserId());
        invoice.setCorrectedTime(new Date());
        mapper.upsertInvoiceRecognition(invoice);
        updateDuplicateByRecognition(invoice);
        advanceRowVersion(current, "报销申请已变化，请刷新后重试");
        return mapper.selectInvoiceById(invoiceId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OaReimbursement deleteInvoice(Long reimbursementId, Long invoiceId,
            Long selectedShopDeptId, Long expectedRowVersion)
    {
        OaReimbursement current = requireOwnedForUpdate(reimbursementId,
                selectedShopDeptId);
        if (!EDITABLE.contains(current.getStatus()))
        {
            throw new ServiceException("提交审批后不能删除发票");
        }
        if (expectedRowVersion != null)
        {
            requireVersion(expectedRowVersion, current.getRowVersion());
        }
        OaReimbursementInvoice invoice = mapper.selectInvoiceById(invoiceId);
        if (invoice == null || !Objects.equals(reimbursementId,
                invoice.getReimbursementId()))
        {
            throw new ServiceException("发票不存在");
        }
        mapper.deleteInvoiceRecognition(invoiceId, reimbursementId);
        if (invoice.getItemId() != null
                && mapper.deleteItem(reimbursementId,
                        invoice.getItemId()) != 1)
        {
            throw new ServiceException("删除发票对应费用明细失败");
        }
        if (mapper.deleteInvoice(invoiceId, reimbursementId) != 1)
        {
            throw new ServiceException("删除发票记录失败");
        }
        OaReimbursement update = new OaReimbursement();
        update.setReimbursementId(reimbursementId);
        update.setTotalAmount(total(mapper.selectItemsByReimbursementId(
                reimbursementId)));
        update.setRowVersion(current.getRowVersion());
        update.setUpdateBy(SecurityUtils.getUsername());
        updateOrThrow(update, "报销申请已变化，请刷新后重试");
        OaReimbursement detail = loadDetail(reimbursementId);
        registerCommitCleanup(invoice.getStoragePath());
        return detail;
    }

    @Override
    public InvoiceContent invoiceContent(Long reimbursementId, Long invoiceId,
            String mode, Long selectedShopDeptId)
    {
        OaReimbursement value = mapper.selectById(reimbursementId);
        requireReadable(value, selectedShopDeptId);
        OaReimbursementInvoice invoice = mapper.selectInvoiceById(invoiceId);
        if (invoice == null || !Objects.equals(reimbursementId,
                invoice.getReimbursementId()))
        {
            throw new ServiceException("发票不存在");
        }
        boolean preview = "preview".equals(mode);
        if (!preview && !"download".equals(mode))
        {
            throw new ServiceException("不支持的发票访问方式");
        }
        if (preview && "application/ofd".equals(invoice.getContentType()))
        {
            throw new ServiceException("OFD 发票请下载后查看");
        }
        java.nio.file.Path path = fileStorage.resolve(invoice.getStoragePath());
        try
        {
            return new InvoiceContent(path, invoice.getOriginalName(),
                    invoice.getContentType(), Files.size(path), preview);
        }
        catch (java.io.IOException exception)
        {
            throw new ServiceException("读取发票文件失败").setDetailMessage(
                    exception.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApprovalBusinessCallbackResponse applyApprovalCallback(
            ApprovalBusinessCallbackRequest request)
    {
        ApprovalBusinessCallbackResponse invalid = validateCallback(request);
        if (invalid != null)
        {
            return invalid;
        }
        Long id;
        try
        {
            id = Long.valueOf(request.getBusinessId());
        }
        catch (NumberFormatException exception)
        {
            return retry("INVALID_BUSINESS_ID", "报销业务ID不是有效数字");
        }
        OaReimbursement current = mapper.selectByIdForUpdate(id);
        if (current == null)
        {
            return stale(request, "BUSINESS_NOT_FOUND", "报销申请不存在");
        }
        if (request.getEventKey().equals(
                current.getLastApprovalEventKey()))
        {
            return accepted("IDEMPOTENT", "审批事件已处理");
        }
        if (SUBMITTING.equals(current.getStatus())
                && current.getApprovalInstanceId() == null
                && Objects.equals(request.getBusinessRound(),
                        current.getApprovalRound()))
        {
            return retry("APPROVAL_LINK_PENDING",
                    "报销审批实例正在完成本地关联");
        }
        if (!Objects.equals(request.getInstanceId(),
                current.getApprovalInstanceId())
                || !Objects.equals(request.getBusinessRound(),
                        current.getApprovalRound()))
        {
            return stale(request, "STALE_APPROVAL_EVENT",
                    "审批实例或业务轮次已不是当前轮次");
        }
        CallbackTarget target = callbackTarget(request.getAction());
        String payloadTarget = payloadTargetStatus(request.getPayload());
        if (target == null || payloadTarget == null
                || !target.approvalStatus().equals(payloadTarget))
        {
            return retry("INVALID_APPROVAL_TARGET", "审批动作与目标状态不一致");
        }
        if (!PENDING.equals(current.getStatus()))
        {
            return stale(request, "BUSINESS_STATUS_CHANGED",
                    "报销申请已不处于审批中");
        }
        OaReimbursement update = new OaReimbursement();
        update.setReimbursementId(id);
        update.setStatus(target.businessStatus());
        update.setLastApprovalEventKey(request.getEventKey());
        update.setRowVersion(current.getRowVersion());
        update.setUpdateBy("approval");
        if (APPROVED.equals(target.businessStatus()))
        {
            update.setApprovedTime(new Date());
        }
        updateOrThrow(update, "报销审批回调并发冲突");
        return accepted("ACCEPTED", "报销审批状态已更新");
    }

    private void validateSubmittable(OaReimbursement value)
    {
        if (StringUtils.isBlank(value.getTitle()))
        {
            throw new ServiceException("报销标题不能为空");
        }
        if (StringUtils.isBlank(value.getPurpose()))
        {
            throw new ServiceException("报销事由不能为空");
        }
        if (value.getItems() == null || value.getItems().isEmpty())
        {
            throw new ServiceException("请至少填写一条费用明细");
        }
        normalizedItems(value.getItems(), true);
        if (mapper.countInvoices(value.getReimbursementId()) <= 0)
        {
            throw new ServiceException("请至少上传一个发票文件");
        }
        if (mapper.countInvoicesNotReady(value.getReimbursementId()) > 0)
        {
            throw new ServiceException(
                    "存在尚未识别或核对的发票，请先完成发票识别");
        }
    }

    private void validateSubmissionRequest(OaReimbursement value)
    {
        if (StringUtils.isBlank(value.getTitle()))
        {
            throw new ServiceException("报销标题不能为空");
        }
        if (StringUtils.isBlank(value.getPurpose()))
        {
            throw new ServiceException("报销事由不能为空");
        }
        normalizedItems(value.getItems(), true);
        if (value.getReimbursementId() == null)
        {
            throw new ServiceException("请先保存草稿并上传发票");
        }
        if (mapper.countInvoices(value.getReimbursementId()) <= 0)
        {
            throw new ServiceException("请至少上传一个发票文件");
        }
        if (mapper.countInvoicesNotReady(value.getReimbursementId()) > 0)
        {
            throw new ServiceException(
                    "存在尚未识别或核对的发票，请先完成发票识别");
        }
    }

    private ApprovalStartRequest buildApprovalStartRequest(
            OaReimbursement value, int round)
    {
        ApprovalStartRequest request = new ApprovalStartRequest();
        request.setBusinessCode(BUSINESS_CODE);
        request.setBusinessId(String.valueOf(value.getReimbursementId()));
        request.setBusinessRound(round);
        request.setApplicantId(value.getApplicantId());
        request.setApplicantName(value.getApplicantName());
        request.setApplicantDeptId(value.getApplicantDeptId());
        request.setApplicantDeptName(value.getApplicantDeptName());
        request.setAnchorDeptId(value.getApplicantDeptId() == null
                ? value.getShopDeptId() : value.getApplicantDeptId());
        request.setAnchorDeptName(StringUtils.isBlank(
                value.getApplicantDeptName())
                        ? value.getShopDeptName()
                        : value.getApplicantDeptName());
        request.setPreviousInstanceId(value.getApprovalInstanceId());
        request.setBusinessSubtype("EXPENSE");
        request.setIdempotencyKey(BUSINESS_CODE + ":"
                + value.getReimbursementId() + ":" + round);
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("reimbursementId", value.getReimbursementId());
        variables.put("title", value.getTitle());
        variables.put("amount", value.getTotalAmount());
        variables.put("category", category(value.getItems()));
        variables.put("shopDeptId", value.getShopDeptId());
        request.setVariables(variables);
        Map<String, String> route = new LinkedHashMap<>();
        route.put("businessId", String.valueOf(value.getReimbursementId()));
        route.put("reimbursementId",
                String.valueOf(value.getReimbursementId()));
        route.put("todoType", "OA_REIMBURSEMENT_APPROVAL");
        route.put("desktopPath", "/oa/reimbursement");
        route.put("mobilePath", "/mobile/reimbursement");
        request.setRouteSnapshot(route);
        return request;
    }

    private String category(List<OaReimbursementItem> items)
    {
        Set<String> categories = new LinkedHashSet<>();
        items.forEach(item -> categories.add(item.getExpenseType()));
        return categories.size() == 1 ? categories.iterator().next() : "MULTI";
    }

    private void requireReadable(OaReimbursement value,
            Long selectedShopDeptId)
    {
        if (value == null)
        {
            throw new ServiceException("报销申请不存在");
        }
        Long userId = SecurityUtils.getUserId();
        if (SecurityUtils.isAdmin()
                || Objects.equals(value.getApplicantId(), userId))
        {
            return;
        }
        if (value.getApprovalInstanceId() != null
                && isApprovalParticipant(value.getApprovalInstanceId(),
                        userId))
        {
            return;
        }
        if (AuthUtil.hasPermi(PERMISSION_FINANCE_LIST)
                || AuthUtil.hasPermi(PERMISSION_FINANCE_EXPORT))
        {
            requireShopScope(value, selectedShopDeptId);
            return;
        }
        throw new ServiceException("无权查看该报销申请");
    }

    private boolean isApprovalParticipant(Long instanceId, Long userId)
    {
        try
        {
            R<Boolean> response = approvalService.canAccessInstance(instanceId,
                    userId, SecurityConstants.INNER);
            return response != null && R.isSuccess(response)
                    && Boolean.TRUE.equals(response.getData());
        }
        catch (RuntimeException exception)
        {
            return false;
        }
    }

    private OaReimbursement requireOwnedForUpdate(Long reimbursementId,
            Long selectedShopDeptId)
    {
        OaReimbursement current = mapper.selectByIdForUpdate(
                reimbursementId);
        if (current == null)
        {
            throw new ServiceException("报销申请不存在");
        }
        requireShopScope(current, selectedShopDeptId);
        if (!Objects.equals(current.getApplicantId(),
                SecurityUtils.getUserId()))
        {
            throw new ServiceException("仅申请人可以修改报销申请");
        }
        return current;
    }

    private void requireInvoiceEditable(OaReimbursement reimbursement)
    {
        if (!EDITABLE.contains(reimbursement.getStatus()))
        {
            throw new ServiceException("提交审批后不能修改发票识别结果");
        }
    }

    private OaReimbursementInvoice requireInvoice(Long reimbursementId,
            Long invoiceId)
    {
        OaReimbursementInvoice invoice = mapper.selectInvoiceById(invoiceId);
        if (invoice == null || !Objects.equals(reimbursementId,
                invoice.getReimbursementId()))
        {
            throw new ServiceException("发票不存在");
        }
        return invoice;
    }

    private void updateDuplicateByRecognition(
            OaReimbursementInvoice invoice)
    {
        Long duplicateId = null;
        if (StringUtils.isNotBlank(invoice.getInvoiceNumber()))
        {
            duplicateId = mapper.selectApprovedDuplicateByNumber(
                    invoice.getInvoiceCode(), invoice.getInvoiceNumber(),
                    invoice.getReimbursementId());
        }
        if (duplicateId == null)
        {
            duplicateId = mapper.selectApprovedDuplicateReimbursement(
                    invoice.getSha256(), invoice.getReimbursementId());
        }
        invoice.setDuplicateStatus(duplicateId == null
                ? "none" : "warning");
        invoice.setDuplicateReimbursementId(duplicateId);
        mapper.updateInvoiceDuplicate(invoice.getInvoiceId(),
                invoice.getReimbursementId(),
                invoice.getDuplicateStatus(), duplicateId);
    }

    private String optionalText(String value, int maximum)
    {
        if (StringUtils.isBlank(value))
        {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maximum)
        {
            throw new ServiceException("发票识别字段长度超过系统限制");
        }
        return normalized;
    }

    private String identifier(String value, int maximum)
    {
        String normalized = optionalText(value, maximum);
        if (normalized == null)
        {
            return null;
        }
        normalized = normalized.replaceAll("\\s+", "")
                .toUpperCase(Locale.ROOT);
        if (normalized.length() > maximum)
        {
            throw new ServiceException("发票识别字段长度超过系统限制");
        }
        return normalized;
    }

    private BigDecimal amount(BigDecimal value)
    {
        if (value == null)
        {
            return null;
        }
        if (value.signum() < 0
                || value.compareTo(new BigDecimal("9999999999.99")) > 0)
        {
            throw new ServiceException("发票金额超出系统允许范围");
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private void requireShopScope(OaReimbursement value,
            Long selectedShopDeptId)
    {
        if (SecurityUtils.isAdmin())
        {
            return;
        }
        Long root = shopScopeService.resolveRequiredShopDept(
                selectedShopDeptId);
        if (deptScopeMapper.countDeptInScope(root,
                value.getShopDeptId()) <= 0)
        {
            throw new ServiceException("无权访问该店铺报销数据");
        }
    }

    private OaReimbursement loadDetail(Long id)
    {
        OaReimbursement value = mapper.selectById(id);
        if (value == null)
        {
            throw new ServiceException("报销申请不存在");
        }
        return attachChildren(value);
    }

    private OaReimbursement attachChildren(OaReimbursement value)
    {
        value.setItems(mapper.selectItemsByReimbursementId(
                value.getReimbursementId()));
        value.setInvoices(mapper.selectInvoicesByReimbursementId(
                value.getReimbursementId()));
        return value;
    }

    private void replaceItems(Long reimbursementId,
            List<OaReimbursementItem> items)
    {
        validateItemSources(reimbursementId, items);
        mapper.clearInvoiceItemLinks(reimbursementId);
        mapper.deleteItemsByReimbursementId(reimbursementId);
        int order = 0;
        for (OaReimbursementItem item : items)
        {
            item.setReimbursementId(reimbursementId);
            item.setSortNo(++order);
            if (mapper.insertItem(item) != 1)
            {
                throw new ServiceException("保存费用明细失败");
            }
            if (item.getSourceInvoiceId() != null
                    && mapper.bindInvoiceItem(item.getSourceInvoiceId(),
                            reimbursementId, item.getItemId()) != 1)
            {
                throw new ServiceException("绑定发票费用明细失败");
            }
        }
    }

    private void validateItemSources(Long reimbursementId,
            List<OaReimbursementItem> items)
    {
        Set<Long> sourceInvoiceIds = new LinkedHashSet<>();
        for (OaReimbursementItem item : items)
        {
            Long sourceInvoiceId = item.getSourceInvoiceId();
            if (sourceInvoiceId == null)
            {
                continue;
            }
            if (!sourceInvoiceIds.add(sourceInvoiceId))
            {
                throw new ServiceException("同一发票不能关联多条费用明细");
            }
            OaReimbursementInvoice invoice = mapper.selectInvoiceById(
                    sourceInvoiceId);
            if (invoice == null || !Objects.equals(reimbursementId,
                    invoice.getReimbursementId()))
            {
                throw new ServiceException("发票费用明细关联无效");
            }
        }
    }

    private List<OaReimbursementItem> normalizedItems(
            List<OaReimbursementItem> items, boolean required)
    {
        List<OaReimbursementItem> values = items == null
                ? new ArrayList<>() : new ArrayList<>(items);
        if (required && values.isEmpty())
        {
            throw new ServiceException("请至少填写一条费用明细");
        }
        if (values.size() > 100)
        {
            throw new ServiceException("单张报销单最多100条费用明细");
        }
        for (OaReimbursementItem item : values)
        {
            if (item == null)
            {
                throw new ServiceException("费用明细填写不完整");
            }
            String expenseType = optionalText(item.getExpenseType(),
                    "费用类型", 32);
            String description = optionalText(item.getDescription(),
                    "费用说明", 300);
            item.setExpenseType(expenseType.isEmpty() ? null : expenseType);
            item.setDescription(description.isEmpty() ? null : description);
            if (item.getMerchantName() != null)
            {
                String merchantName = optionalText(item.getMerchantName(),
                        "商户名称", 120);
                item.setMerchantName(merchantName.isEmpty()
                        ? null : merchantName);
            }
            if (item.getClaimedAmount() != null)
            {
                if (item.getClaimedAmount().compareTo(BigDecimal.ZERO) < 0)
                {
                    throw new ServiceException("报销金额不能小于0");
                }
                item.setClaimedAmount(item.getClaimedAmount().setScale(2,
                        RoundingMode.HALF_UP));
            }
            if (required && (StringUtils.isBlank(item.getExpenseType())
                    || item.getExpenseDate() == null
                    || StringUtils.isBlank(item.getDescription())
                    || item.getClaimedAmount() == null
                    || item.getClaimedAmount()
                            .compareTo(BigDecimal.ZERO) <= 0))
            {
                throw new ServiceException("费用明细填写不完整");
            }
        }
        return values;
    }

    private BigDecimal total(List<OaReimbursementItem> items)
    {
        BigDecimal result = items.stream()
                .map(OaReimbursementItem::getClaimedAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        if (result.compareTo(new BigDecimal("9999999999.99")) > 0)
        {
            throw new ServiceException("报销总金额超过系统上限");
        }
        return result;
    }

    private String optionalText(String value, String field, int maxLength)
    {
        if (StringUtils.isBlank(value))
        {
            return "";
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength)
        {
            throw new ServiceException(field + "不能超过"
                    + maxLength + "个字符");
        }
        return normalized;
    }

    private void requireVersion(Long requested, Long current)
    {
        if (requested == null || !Objects.equals(requested, current))
        {
            throw new ServiceException("报销申请已变化，请刷新后重试");
        }
    }

    private void updateOrThrow(OaReimbursement value, String message)
    {
        if (value.getRowVersion() == null
                || mapper.updateReimbursement(value) != 1)
        {
            throw new ServiceException(message);
        }
    }

    private void advanceRowVersion(OaReimbursement current, String message)
    {
        if (current == null || current.getRowVersion() == null)
        {
            throw new ServiceException(message);
        }
        OaReimbursement change = new OaReimbursement();
        change.setReimbursementId(current.getReimbursementId());
        change.setRowVersion(current.getRowVersion());
        change.setUpdateBy(SecurityUtils.getUsername());
        updateOrThrow(change, message);
        current.setRowVersion(current.getRowVersion() + 1);
    }

    private String newReimbursementNo()
    {
        String date = java.time.LocalDate.now().format(
                java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
        return "BX" + date + UUID.randomUUID().toString()
                .replace("-", "").substring(0, 10).toUpperCase(Locale.ROOT);
    }

    private String normalizeReason(String reason, String fallback)
    {
        String normalized = StringUtils.isBlank(reason)
                ? fallback : reason.trim();
        if (normalized.length() > 500)
        {
            throw new ServiceException("原因不能超过500个字符");
        }
        return normalized;
    }

    private void registerRollbackCleanup(String relativePath)
    {
        if (!TransactionSynchronizationManager.isSynchronizationActive())
        {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization()
                {
                    @Override
                    public void afterCompletion(int status)
                    {
                        if (status != TransactionSynchronization.STATUS_COMMITTED)
                        {
                            fileStorage.deleteQuietly(relativePath);
                        }
                    }
                });
    }

    private void registerCommitCleanup(String relativePath)
    {
        if (!TransactionSynchronizationManager.isSynchronizationActive())
        {
            fileStorage.delete(relativePath);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization()
                {
                    @Override
                    public void afterCommit()
                    {
                        fileStorage.deleteQuietly(relativePath);
                    }
                });
    }

    private ApprovalBusinessCallbackResponse validateCallback(
            ApprovalBusinessCallbackRequest request)
    {
        if (request == null || StringUtils.isBlank(request.getEventKey())
                || request.getInstanceId() == null
                || StringUtils.isBlank(request.getBusinessCode())
                || StringUtils.isBlank(request.getBusinessId())
                || request.getBusinessRound() == null
                || StringUtils.isBlank(request.getAction()))
        {
            return retry("INVALID_CALLBACK", "审批回调字段不完整");
        }
        if (!BUSINESS_CODE.equals(request.getBusinessCode()))
        {
            return retry("BUSINESS_CODE_MISMATCH", "OA 回调不支持该业务类型");
        }
        if (request.getEventKey().length() > 128)
        {
            return retry("EVENT_KEY_TOO_LONG", "审批事件键超过128个字符");
        }
        return null;
    }

    private CallbackTarget callbackTarget(String action)
    {
        return switch (action.trim().toUpperCase(Locale.ROOT))
        {
            case "APPROVE" -> new CallbackTarget("APPROVED", APPROVED);
            case "RETURN" -> new CallbackTarget("RETURNED", RETURNED);
            case "REJECT" -> new CallbackTarget("REJECTED", REJECTED);
            case "WITHDRAW" -> new CallbackTarget("WITHDRAWN", WITHDRAWN);
            case "TERMINATE" -> new CallbackTarget("TERMINATED", TERMINATED);
            default -> null;
        };
    }

    private String payloadTargetStatus(String payload)
    {
        if (StringUtils.isBlank(payload))
        {
            return null;
        }
        try
        {
            JsonNode value = objectMapper.readTree(payload)
                    .get("targetStatus");
            return value == null || !value.isTextual() ? null
                    : value.asText().trim().toUpperCase(Locale.ROOT);
        }
        catch (JsonProcessingException exception)
        {
            return null;
        }
    }

    private ApprovalBusinessCallbackResponse accepted(String code,
            String message)
    {
        return response(true, false, code, message);
    }

    private ApprovalBusinessCallbackResponse retry(String code,
            String message)
    {
        return response(false, false, code, message);
    }

    private ApprovalBusinessCallbackResponse stale(
            ApprovalBusinessCallbackRequest request, String code,
            String message)
    {
        return "APPROVE".equals(request.getAction().trim()
                .toUpperCase(Locale.ROOT))
                        ? response(false, true, code, message)
                        : accepted(code + "_IGNORED", message);
    }

    private ApprovalBusinessCallbackResponse response(boolean accepted,
            boolean invalidated, String code, String message)
    {
        ApprovalBusinessCallbackResponse value =
                new ApprovalBusinessCallbackResponse();
        value.setAccepted(accepted);
        value.setInvalidated(invalidated);
        value.setCode(code);
        value.setMessage(message);
        return value;
    }

    private record CallbackTarget(String approvalStatus,
            String businessStatus) { }
}
