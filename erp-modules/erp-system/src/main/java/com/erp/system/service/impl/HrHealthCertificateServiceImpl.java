package com.erp.system.service.impl;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.core.constant.SecurityConstants;
import com.erp.common.core.domain.R;
import com.erp.common.core.utils.StringUtils;
import com.erp.common.security.auth.AuthUtil;
import com.erp.approval.api.RemoteApprovalService;
import com.erp.approval.api.domain.ApprovalBusinessCallbackRequest;
import com.erp.approval.api.domain.ApprovalBusinessCallbackResponse;
import com.erp.approval.api.domain.ApprovalInstanceSnapshot;
import com.erp.approval.api.domain.ApprovalStartRequest;
import com.erp.approval.api.domain.ApprovalWithdrawRequest;
import com.erp.system.api.RemoteFileService;
import com.erp.system.api.domain.DriveBusinessFile;
import com.erp.system.api.domain.SysUser;
import com.erp.system.domain.HrHealthCertificate;
import com.erp.system.domain.HrHealthCertificateApprovalStartOutbox;
import com.erp.system.domain.dto.HrHealthCertificateReviewRequest;
import com.erp.system.domain.dto.HrHealthCertificateSubmitRequest;
import com.erp.system.domain.vo.HrEmployeeQuery;
import com.erp.system.domain.vo.HrHealthCertificateVo;
import com.erp.system.domain.vo.HrHealthCertificateOpsSummaryVo;
import com.erp.system.mapper.HrHealthCertificateMapper;
import com.erp.system.metric.HrBusinessMetrics;
import com.erp.system.service.IHrHealthCertificateService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class HrHealthCertificateServiceImpl
        implements IHrHealthCertificateService
{
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final String BUSINESS_CODE = "HR_HEALTH_CERTIFICATE";
    private final HrHealthCertificateMapper mapper;
    private final HrEmployeeAccessService employeeAccessService;
    private final HrHealthCertificateAccessService accessService;
    private final RemoteFileService remoteFileService;
    private final HrHealthCertificateFeatureService featureService;
    private final HrBusinessMetrics businessMetrics;
    private final HrHealthCertificateApprovalStartOutboxService outboxService;
    private final HrHealthCertificateApprovalStartAfterCommitTrigger afterCommitTrigger;
    private final Clock clock;

    @Autowired
    private RemoteApprovalService approvalService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    public HrHealthCertificateServiceImpl(HrHealthCertificateMapper mapper,
            HrEmployeeAccessService employeeAccessService,
            HrHealthCertificateAccessService accessService,
            RemoteFileService remoteFileService,
            HrHealthCertificateFeatureService featureService,
            HrBusinessMetrics businessMetrics,
            HrHealthCertificateApprovalStartOutboxService outboxService,
            HrHealthCertificateApprovalStartAfterCommitTrigger afterCommitTrigger)
    {
        this(mapper, employeeAccessService, accessService,
                remoteFileService, featureService, businessMetrics,
                outboxService, afterCommitTrigger,
                Clock.system(BUSINESS_ZONE));
    }

    HrHealthCertificateServiceImpl(HrHealthCertificateMapper mapper,
            HrEmployeeAccessService employeeAccessService,
            HrHealthCertificateAccessService accessService,
            RemoteFileService remoteFileService,
            HrHealthCertificateFeatureService featureService, Clock clock)
    {
        this(mapper, employeeAccessService, accessService,
                remoteFileService, featureService, null, null, null, clock);
    }

    HrHealthCertificateServiceImpl(HrHealthCertificateMapper mapper,
            HrEmployeeAccessService employeeAccessService,
            HrHealthCertificateAccessService accessService,
            RemoteFileService remoteFileService,
            HrHealthCertificateFeatureService featureService,
            HrBusinessMetrics businessMetrics, Clock clock)
    {
        this(mapper, employeeAccessService, accessService,
                remoteFileService, featureService, businessMetrics, null,
                null, clock);
    }

    HrHealthCertificateServiceImpl(HrHealthCertificateMapper mapper,
            HrEmployeeAccessService employeeAccessService,
            HrHealthCertificateAccessService accessService,
            RemoteFileService remoteFileService,
            HrHealthCertificateFeatureService featureService,
            HrHealthCertificateApprovalStartOutboxService outboxService,
            HrHealthCertificateApprovalStartAfterCommitTrigger afterCommitTrigger,
            Clock clock)
    {
        this(mapper, employeeAccessService, accessService,
                remoteFileService, featureService, null, outboxService,
                afterCommitTrigger, clock);
    }

    HrHealthCertificateServiceImpl(HrHealthCertificateMapper mapper,
            HrEmployeeAccessService employeeAccessService,
            HrHealthCertificateAccessService accessService,
            RemoteFileService remoteFileService,
            HrHealthCertificateFeatureService featureService,
            HrBusinessMetrics businessMetrics,
            HrHealthCertificateApprovalStartOutboxService outboxService,
            HrHealthCertificateApprovalStartAfterCommitTrigger afterCommitTrigger,
            Clock clock)
    {
        this.mapper = mapper;
        this.employeeAccessService = employeeAccessService;
        this.accessService = accessService;
        this.remoteFileService = remoteFileService;
        this.featureService = featureService;
        this.businessMetrics = businessMetrics;
        this.outboxService = outboxService;
        this.afterCommitTrigger = afterCommitTrigger;
        this.clock = clock;
    }

    @Override
    public List<HrHealthCertificateVo> selectMine(Long userId)
    {
        requireUser(userId);
        return decorate(mapper.selectByUserId(userId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public HrHealthCertificateVo saveMyDraft(Long userId,
            HrHealthCertificate certificate, String operator)
    {
        requireUser(userId);
        featureService.requireIntakeEnabled();
        SysUser user = requireIntakeEmployee(userId);
        validateCertificate(certificate);
        validateAttachmentBinding(certificate.getAttachmentNodeId());
        certificate.setUserId(userId);
        certificate.setDeptIdSnapshot(user.getDeptId());
        certificate.setReviewStatus("DRAFT");
        if (certificate.getCertificateId() == null)
        {
            certificate.setCreateBy(operator);
            if (mapper.insertCertificate(certificate) != 1)
            {
                throw new ServiceException("健康证草稿保存失败");
            }
        }
        else
        {
            if (certificate.getVersion() == null)
            {
                throw new ServiceException("健康证版本不能为空");
            }
            certificate.setUpdateBy(operator);
            if (mapper.updateDraft(certificate) != 1)
            {
                throw new ServiceException("健康证草稿已变化或不可编辑，请刷新后重试");
            }
        }
        return decorate(mapper.selectById(certificate.getCertificateId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public HrHealthCertificateVo submitMine(Long userId,
            HrHealthCertificateSubmitRequest request, String operator)
    {
        requireUser(userId);
        // New intake is fail-closed before any certificate lookup. Besides
        // avoiding reads while the capability is disabled, this keeps the
        // response independent of whether a caller guessed an existing id.
        featureService.requireIntakeEnabled();
        if (request == null || request.getCertificateId() == null
                || request.getVersion() == null)
        {
            throw new ServiceException("健康证记录和版本不能为空");
        }
        requireApprovalStartSupport();
        HrHealthCertificateVo current = mapper.selectByIdForUpdate(
                request.getCertificateId());
        if (current == null || !userId.equals(current.getUserId()))
        {
            throw new ServiceException("健康证不存在或无权访问");
        }
        if ("APPROVAL_PENDING".equals(current.getReviewStatus())
                && current.getApprovalInstanceId() != null)
        {
            return decorate(current);
        }
        if ("APPROVAL_SUBMITTING".equals(current.getReviewStatus())
                && current.getApprovalRound() != null)
        {
            HrHealthCertificateApprovalStartOutbox existing = outboxService
                    .selectByCertificateRound(current.getCertificateId(),
                            current.getApprovalRound());
            if (existing == null)
            {
                throw new ServiceException("健康证审批发起记录缺失，请联系管理员恢复");
            }
            afterCommitTrigger.trigger(existing.getOutboxId());
            return decorate(current);
        }
        SysUser employee = requireIntakeEmployee(userId);
        validateCertificate(current);
        int nextRound = (current.getApprovalRound() == null
                ? 0 : current.getApprovalRound()) + 1;
        Long previousInstanceId = current.getApprovalInstanceId();
        ApprovalStartRequest approvalRequest = buildApprovalStartRequest(
                current, employee, nextRound, previousInstanceId);
        if (mapper.markApprovalSubmitting(request.getCertificateId(), userId,
                request.getVersion(), nextRound, operator) != 1)
        {
            throw new ServiceException("健康证已变化或不可提交，请刷新后重试");
        }
        current.setReviewStatus("APPROVAL_SUBMITTING");
        current.setApprovalInstanceId(null);
        current.setApprovalRound(nextRound);
        current.setLastApprovalEventKey(null);
        current.setVersion(request.getVersion() + 1);
        HrHealthCertificateApprovalStartOutbox outbox = outboxService.enqueue(
                current, approvalRequest, operator);
        afterCommitTrigger.trigger(outbox.getOutboxId());
        return decorate(mapper.selectById(request.getCertificateId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String withdrawMine(Long userId, Long certificateId,
            String operator)
    {
        requireUser(userId);
        if (certificateId == null)
        {
            throw new ServiceException("健康证记录不能为空");
        }
        HrHealthCertificateVo current = mapper.selectByIdForUpdate(certificateId);
        if (current == null || !userId.equals(current.getUserId()))
        {
            throw new ServiceException("健康证不存在或无权访问");
        }
        if (!"APPROVAL_PENDING".equals(current.getReviewStatus())
                || current.getApprovalInstanceId() == null
                || current.getApprovalRound() == null
                || current.getApprovalRound() <= 0)
        {
            throw new ServiceException("只有统一审批中的健康证可以撤回");
        }

        R<ApprovalInstanceSnapshot> snapshotResult = approvalService.getInstance(
                current.getApprovalInstanceId(), SecurityConstants.INNER);
        ApprovalInstanceSnapshot snapshot = snapshotResult == null
                ? null : snapshotResult.getData();
        if (snapshotResult == null || !R.isSuccess(snapshotResult)
                || snapshot == null)
        {
            throw new ServiceException(snapshotResult == null
                    ? "审批中心暂时不可用，无法核对撤回申请"
                    : StringUtils.defaultIfEmpty(snapshotResult.getMsg(),
                            "无法核对统一审批实例"));
        }
        if (!BUSINESS_CODE.equals(snapshot.getBusinessCode())
                || !String.valueOf(certificateId).equals(
                        snapshot.getBusinessId())
                || !java.util.Objects.equals(current.getApprovalRound(),
                        snapshot.getBusinessRound())
                || !userId.equals(snapshot.getApplicantUserId()))
        {
            throw new ServiceException("统一审批实例与当前健康证申请不匹配");
        }
        String approvalStatus = snapshot.getStatus() == null ? ""
                : snapshot.getStatus().trim().toUpperCase(Locale.ROOT);
        if (!List.of("RUNNING", "WITHDRAWING").contains(approvalStatus))
        {
            throw new ServiceException("当前审批状态不允许撤回");
        }

        ApprovalWithdrawRequest request = new ApprovalWithdrawRequest();
        request.setInstanceId(current.getApprovalInstanceId());
        request.setApplicantId(userId);
        request.setReason("申请人撤回健康证审批");
        R<Boolean> result = approvalService.withdraw(request,
                SecurityConstants.INNER);
        if (result == null || !R.isSuccess(result)
                || !Boolean.TRUE.equals(result.getData()))
        {
            throw new ServiceException(result == null
                    ? "审批中心暂时不可用，撤回请求未提交"
                    : StringUtils.defaultIfEmpty(result.getMsg(),
                            "撤回健康证审批失败"));
        }
        return "撤回请求已提交，审批结果同步后健康证将变为已撤回";
    }

    @Override
    public List<HrHealthCertificateVo> selectList(
            HrHealthCertificateVo query)
    {
        return decorate(accessService.selectScopedList(query));
    }

    @Override
    public HrHealthCertificateOpsSummaryVo selectOpsSummary(
            HrHealthCertificateVo query)
    {
        HrHealthCertificateOpsSummaryVo summary =
                accessService.selectOpsSummary(query);
        if (summary == null)
        {
            summary = new HrHealthCertificateOpsSummaryVo();
        }
        if (businessMetrics != null)
        {
            summary.setReminderFailureCount(
                    businessMetrics.getReminderFailureCount());
        }
        return summary;
    }

    @Override
    public List<HrHealthCertificateVo> selectEmployee(Long userId)
    {
        authorizeEmployee(userId);
        return decorate(mapper.selectByUserId(userId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public HrHealthCertificateVo review(Long certificateId,
            HrHealthCertificateReviewRequest request, Long reviewerUserId,
            String reviewerName)
    {
        if (certificateId == null || request == null
                || request.getVersion() == null)
        {
            throw new ServiceException("审核记录和版本不能为空");
        }
        HrHealthCertificateVo certificate = mapper.selectById(certificateId);
        if (certificate == null)
        {
            throw new ServiceException("健康证不存在");
        }
        authorizeEmployee(certificate.getUserId());
        String decision = request.getDecision() == null ? ""
                : request.getDecision().trim().toUpperCase(Locale.ROOT);
        if (!"APPROVED".equals(decision) && !"REJECTED".equals(decision))
        {
            throw new ServiceException("审核决定只支持APPROVED或REJECTED");
        }
        if ("REJECTED".equals(decision)
                && StringUtils.isBlank(request.getRejectionReason()))
        {
            throw new ServiceException("驳回原因不能为空");
        }
        String rejectionReason = "REJECTED".equals(decision)
                ? request.getRejectionReason().trim() : null;
        if (rejectionReason != null && rejectionReason.length() > 300)
        {
            throw new ServiceException("驳回原因不能超过300个字符");
        }
        if (mapper.lockEmployeeProfile(certificate.getUserId()) == null)
        {
            throw new ServiceException("员工档案不存在");
        }
        if ("APPROVED".equals(decision))
        {
            mapper.clearCurrentByUserId(certificate.getUserId(), reviewerName);
        }
        int affected = mapper.reviewCertificate(certificateId,
                request.getVersion(), decision,
                "APPROVED".equals(decision) ? "Y" : null,
                reviewerUserId, reviewerName,
                rejectionReason);
        if (affected != 1)
        {
            throw new ServiceException("健康证已被其他人审核，请刷新后重试");
        }
        return decorate(mapper.selectById(certificateId));
    }

    @Override
    public Map<Long, HrHealthCertificateVo> selectCurrentProjection(
            List<Long> userIds)
    {
        if (userIds == null || userIds.isEmpty())
        {
            return Collections.emptyMap();
        }
        List<Long> unique = userIds.stream().filter(id -> id != null && id > 0)
                .distinct().toList();
        if (unique.isEmpty())
        {
            return Collections.emptyMap();
        }
        Map<Long, HrHealthCertificateVo> values = new LinkedHashMap<>();
        for (HrHealthCertificateVo row : decorate(
                mapper.selectCurrentByUserIds(unique)))
        {
            values.put(row.getUserId(), row);
        }
        return values;
    }

    @Override
    public Long resolveAttachmentNode(Long certificateId,
            Long requesterUserId)
    {
        requireUser(requesterUserId);
        HrHealthCertificateVo certificate = mapper.selectById(certificateId);
        if (certificate == null || certificate.getAttachmentNodeId() == null)
        {
            throw new ServiceException("健康证附件不存在");
        }
        if (!requesterUserId.equals(certificate.getUserId()))
        {
            if (!AuthUtil.hasPermi("hr:healthCertificate:query")
                    && !AuthUtil.hasPermi("hr:healthCertificate:review"))
            {
                throw new ServiceException("健康证附件不存在或无权访问");
            }
            authorizeEmployee(certificate.getUserId());
        }
        return certificate.getAttachmentNodeId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApprovalBusinessCallbackResponse applyApprovalCallback(
            ApprovalBusinessCallbackRequest request)
    {
        ApprovalBusinessCallbackResponse envelopeError = validateCallback(request);
        if (envelopeError != null)
        {
            return envelopeError;
        }
        Long certificateId;
        try
        {
            certificateId = Long.valueOf(request.getBusinessId());
        }
        catch (NumberFormatException exception)
        {
            return retry("INVALID_BUSINESS_ID", "健康证业务ID不是有效数字");
        }
        HrHealthCertificateVo certificate = mapper.selectByIdForUpdate(
                certificateId);
        if (certificate == null)
        {
            return stale(request, "BUSINESS_NOT_FOUND", "健康证记录不存在");
        }
        if (request.getEventKey().equals(
                certificate.getLastApprovalEventKey()))
        {
            return accepted("IDEMPOTENT", "回调事件已处理");
        }
        if ("APPROVAL_SUBMITTING".equals(certificate.getReviewStatus())
                && java.util.Objects.equals(request.getBusinessRound(),
                        certificate.getApprovalRound()))
        {
            return retry("APPROVAL_LINK_PENDING",
                    "审批实例正在关联健康证，请稍后重试回调");
        }
        if (!java.util.Objects.equals(request.getInstanceId(),
                certificate.getApprovalInstanceId())
                || !java.util.Objects.equals(request.getBusinessRound(),
                        certificate.getApprovalRound()))
        {
            return stale(request, "STALE_APPROVAL_EVENT",
                    "审批实例或业务轮次已不是当前轮次");
        }
        CallbackPayload payload = callbackPayload(request.getPayload());
        if (payload == null)
        {
            return retry("INVALID_PAYLOAD", "审批回调载荷无效");
        }
        String expected = expectedTargetStatus(request.getAction());
        if (expected == null)
        {
            return retry("UNSUPPORTED_ACTION", "不支持的健康证审批动作");
        }
        if (!expected.equals(payload.targetStatus()))
        {
            return retry("TARGET_STATUS_MISMATCH", "审批动作与目标状态不一致");
        }
        if (!"APPROVAL_PENDING".equals(certificate.getReviewStatus()))
        {
            return stale(request, "BUSINESS_STATUS_CHANGED",
                    "健康证记录已不处于统一审批状态");
        }

        String operatorName = StringUtils.isBlank(payload.operatorName())
                ? "approval" : payload.operatorName().trim();
        String reason = "APPROVED".equals(expected) ? null
                : StringUtils.isBlank(payload.reason())
                        ? "审批已" + statusDescription(expected)
                        : payload.reason().trim();
        if (reason != null && reason.length() > 300)
        {
            reason = reason.substring(0, 300);
        }
        if (mapper.lockEmployeeProfile(certificate.getUserId()) == null)
        {
            return retry("EMPLOYEE_PROFILE_MISSING", "员工档案不存在");
        }
        if ("APPROVED".equals(expected))
        {
            mapper.clearCurrentByUserId(certificate.getUserId(), operatorName);
        }
        int affected = mapper.applyApprovalResult(certificateId,
                certificate.getVersion(), request.getInstanceId(),
                request.getBusinessRound(), request.getEventKey(), expected,
                "APPROVED".equals(expected) ? "Y" : null,
                payload.operatorId(), operatorName, reason);
        if (affected != 1)
        {
            throw new ServiceException("健康证审批回调并发冲突");
        }
        return accepted("ACCEPTED", "健康证审批状态已更新");
    }

    private ApprovalStartRequest buildApprovalStartRequest(
            HrHealthCertificateVo certificate, SysUser employee,
            int round, Long previousInstanceId)
    {
        ApprovalStartRequest command = new ApprovalStartRequest();
        command.setBusinessCode(BUSINESS_CODE);
        command.setBusinessId(String.valueOf(certificate.getCertificateId()));
        command.setBusinessRound(round);
        command.setApplicantId(certificate.getUserId());
        command.setApplicantName(StringUtils.defaultIfEmpty(
                certificate.getEmployeeName(), certificate.getCreateBy()));
        command.setApplicantDeptId(employee.getDeptId());
        command.setApplicantDeptName(certificate.getCurrentDeptName());
        command.setAnchorDeptId(employee.getDeptId());
        command.setAnchorDeptName(certificate.getCurrentDeptName());
        command.setPreviousInstanceId(previousInstanceId);
        command.setBusinessSubtype("ALL");
        command.setIdempotencyKey(BUSINESS_CODE + ":"
                + certificate.getCertificateId() + ":" + round);

        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("certificateId", certificate.getCertificateId());
        variables.put("certificateNo", certificate.getCertificateNo());
        variables.put("issuedDate", certificate.getIssuedDate());
        variables.put("expiresOn", certificate.getExpiresOn());
        variables.put("employeeUserId", certificate.getUserId());
        command.setVariables(variables);

        Map<String, String> route = new LinkedHashMap<>();
        route.put("businessId", String.valueOf(certificate.getCertificateId()));
        route.put("certificateId", String.valueOf(certificate.getCertificateId()));
        route.put("todoType", "HR_HEALTH_CERT_REVIEW");
        route.put("desktopPath", "/hr/healthCertificate");
        route.put("mobilePath", "/mobile/hr/health-certificate");
        command.setRouteSnapshot(route);
        return command;
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
            return retry("BUSINESS_CODE_MISMATCH",
                    "System 回调入口不支持该业务类型");
        }
        if (request.getEventKey().length() > 128)
        {
            return retry("EVENT_KEY_TOO_LONG", "审批事件键超过128个字符");
        }
        return null;
    }

    private CallbackPayload callbackPayload(String json)
    {
        if (StringUtils.isBlank(json))
        {
            return null;
        }
        try
        {
            JsonNode root = objectMapper.readTree(json);
            JsonNode target = root.get("targetStatus");
            if (target == null || !target.isTextual())
            {
                return null;
            }
            JsonNode operatorId = root.get("operatorId");
            return new CallbackPayload(
                    target.asText().trim().toUpperCase(Locale.ROOT),
                    textValue(root.get("reason")),
                    operatorId == null || !operatorId.canConvertToLong()
                            ? null : operatorId.longValue(),
                    textValue(root.get("operatorName")));
        }
        catch (JsonProcessingException exception)
        {
            return null;
        }
    }

    private String expectedTargetStatus(String action)
    {
        return switch (action.trim().toUpperCase(Locale.ROOT))
        {
            case "APPROVE" -> "APPROVED";
            case "RETURN" -> "RETURNED";
            case "REJECT" -> "REJECTED";
            case "WITHDRAW" -> "WITHDRAWN";
            case "TERMINATE" -> "TERMINATED";
            default -> null;
        };
    }

    private String statusDescription(String status)
    {
        return switch (status)
        {
            case "RETURNED" -> "退回，请修改后重新提交";
            case "REJECTED" -> "拒绝";
            case "WITHDRAWN" -> "撤回";
            case "TERMINATED" -> "终止";
            default -> "处理";
        };
    }

    private String textValue(JsonNode value)
    {
        return value == null || value.isNull() ? null : value.asText();
    }

    private ApprovalBusinessCallbackResponse accepted(String code,
            String message)
    {
        return callbackResponse(true, false, code, message);
    }

    private ApprovalBusinessCallbackResponse retry(String code,
            String message)
    {
        return callbackResponse(false, false, code, message);
    }

    private ApprovalBusinessCallbackResponse stale(
            ApprovalBusinessCallbackRequest request, String code,
            String message)
    {
        return "APPROVE".equals(request.getAction().trim()
                .toUpperCase(Locale.ROOT))
                        ? invalidated(code, message)
                        : accepted(code + "_IGNORED", message);
    }

    private ApprovalBusinessCallbackResponse invalidated(String code,
            String message)
    {
        return callbackResponse(false, true, code, message);
    }

    private ApprovalBusinessCallbackResponse callbackResponse(boolean accepted,
            boolean invalidated, String code, String message)
    {
        ApprovalBusinessCallbackResponse response =
                new ApprovalBusinessCallbackResponse();
        response.setAccepted(accepted);
        response.setInvalidated(invalidated);
        response.setCode(code);
        response.setMessage(message);
        return response;
    }

    private record CallbackPayload(String targetStatus, String reason,
            Long operatorId, String operatorName) { }

    private List<HrHealthCertificateVo> decorate(
            List<HrHealthCertificateVo> rows)
    {
        if (rows == null)
        {
            return Collections.emptyList();
        }
        rows.forEach(this::decorate);
        return rows;
    }

    private HrHealthCertificateVo decorate(HrHealthCertificateVo row)
    {
        if (row == null)
        {
            return null;
        }
        row.setAttachmentPresent(row.getAttachmentNodeId() != null);
        if (!"APPROVED".equals(row.getReviewStatus())
                || !"Y".equals(row.getCurrentFlag()))
        {
            row.setHealthCertificateStatus(row.getReviewStatus());
            row.setDaysRemaining(null);
            return row;
        }
        long days = ChronoUnit.DAYS.between(LocalDate.now(clock),
                row.getExpiresOn());
        row.setDaysRemaining(days);
        row.setHealthCertificateStatus(days < 0 ? "EXPIRED"
                : days <= 30 ? "EXPIRING" : "VALID");
        return row;
    }

    private void validateCertificate(HrHealthCertificate certificate)
    {
        if (certificate == null || certificate.getIssuedDate() == null
                || certificate.getExpiresOn() == null)
        {
            throw new ServiceException("办理日期和到期日期不能为空");
        }
        LocalDate effectiveFrom = certificate.getValidFrom() == null
                ? certificate.getIssuedDate() : certificate.getValidFrom();
        certificate.setValidFrom(effectiveFrom);
        if (certificate.getExpiresOn().isBefore(effectiveFrom)
                || certificate.getIssuedDate().isAfter(
                        certificate.getExpiresOn()))
        {
            throw new ServiceException("健康证到期日期不能早于办理或生效日期");
        }
        if (certificate.getCertificateNo() != null
                && certificate.getCertificateNo().length() > 100)
        {
            throw new ServiceException("健康证编号不能超过100个字符");
        }
        if (certificate.getIssuerName() != null
                && certificate.getIssuerName().length() > 128)
        {
            throw new ServiceException("发证机构不能超过128个字符");
        }
    }

    private void validateAttachmentBinding(Long attachmentNodeId)
    {
        if (attachmentNodeId == null)
        {
            return;
        }
        R<DriveBusinessFile> result = remoteFileService
                .validateDriveBusinessFile(attachmentNodeId,
                        "HEALTH_CERTIFICATE", SecurityConstants.INNER);
        if (result == null || R.isError(result) || result.getData() == null
                || !attachmentNodeId.equals(result.getData().getNodeId()))
        {
            throw new ServiceException("健康证附件不可用或无权绑定");
        }
    }

    private void authorizeEmployee(Long userId)
    {
        HrEmployeeQuery query = new HrEmployeeQuery();
        query.setUserId(userId);
        employeeAccessService.findScoped(query);
    }

    private SysUser requireIntakeEmployee(Long userId)
    {
        HrEmployeeQuery query=new HrEmployeeQuery();
        query.setUserId(userId);
        SysUser employee;
        try
        {
            employee=employeeAccessService.findActiveScoped(query);
        }
        catch(ServiceException denied)
        {
            throw new ServiceException("仅在职员工可新建或提交健康证");
        }
        if(employee==null||!userId.equals(employee.getUserId())||!"0".equals(employee.getStatus()))
            throw new ServiceException("仅启用的在职员工可新建或提交健康证");
        return employee;
    }

    private void requireApprovalStartSupport()
    {
        if (outboxService == null || afterCommitTrigger == null)
        {
            throw new ServiceException("健康证审批发起恢复组件未就绪");
        }
    }

    private void requireUser(Long userId)
    {
        if (userId == null || userId <= 0)
        {
            throw new ServiceException("无法识别当前员工");
        }
    }
}
