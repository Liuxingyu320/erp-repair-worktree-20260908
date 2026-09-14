package com.erp.system.service.impl;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.security.auth.AuthUtil;
import com.erp.oa.api.domain.HrEmployeeSigningSnapshot;
import com.erp.oa.api.domain.HrRenewalGuard;
import com.erp.oa.api.domain.HrSignBusinessEvent;
import com.erp.system.api.constant.SigningProfileCodes;
import com.erp.system.api.domain.SysDept;
import com.erp.system.api.domain.SysUser;
import com.erp.system.domain.SysHrLifecycleAction;
import com.erp.system.domain.vo.HrEmployeeLifecycleContextVo;
import com.erp.system.domain.SysHrSignEventOutbox;
import com.erp.system.domain.SysPost;
import com.erp.system.domain.SysUserPost;
import com.erp.system.domain.dto.HrLifecycleOnboardingConfirmRequest;
import com.erp.system.domain.dto.HrRenewalDecisionRequest;
import com.erp.system.domain.dto.HrRegularizationRequest;
import com.erp.system.domain.dto.HrEmployeeTransferRequest;
import com.erp.system.domain.dto.HrOffboardingConfirmRequest;
import com.erp.system.mapper.SysConfigMapper;
import com.erp.system.mapper.SysDeptMapper;
import com.erp.system.mapper.SysHrLifecycleActionMapper;
import com.erp.system.mapper.SysHrRenewalGuardMapper;
import com.erp.system.mapper.SysHrSignEventOutboxMapper;
import com.erp.system.mapper.SysPostMapper;
import com.erp.system.mapper.SysUserMapper;
import com.erp.system.mapper.SysUserProfileMapper;
import com.erp.system.mapper.SysUserPostMapper;
import com.erp.system.service.IHrLifecycleService;
import com.erp.system.service.ISysUserShopService;

@Service
public class HrLifecycleServiceImpl implements IHrLifecycleService
{
    private static final String ACTION_TYPE = "ONBOARD_CONFIRMED";
    private static final String SCENARIO = "ONBOARD";
    private static final String SOURCE_TYPE = "HR_LIFECYCLE_ACTION";
    private static final String RENEWAL_SCENARIO = "RENEWAL";
    private static final String RENEWAL_SOURCE_TYPE = "HR_RENEWAL";
    private static final String RENEWAL_DECISION = "RENEWAL_DECISION";
    private static final String RENEWAL_CONFIRMED = "RENEWAL_CONFIRMED";
    private static final String RENEWAL_DECLINED = "RENEWAL_DECLINED";
    private static final String REGULARIZATION_SCENARIO = "REGULARIZE";
    private static final String REGULARIZATION_SOURCE_TYPE = "HR_REGULARIZATION";
    private static final String REGULARIZATION_CONFIRMED = "REGULARIZATION_CONFIRMED";
    private static final int MAX_MONEY_INTEGER_DIGITS = 14;
    private static final int MAX_MONEY_SCALE = 2;
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final HrOffboardingPolicy OFFBOARDING_POLICY =
            new HrOffboardingPolicy();
    private static final HrTransferPolicy TRANSFER_POLICY =
            new HrTransferPolicy();
    private static final HrRegularizationPolicy REGULARIZATION_POLICY =
            new HrRegularizationPolicy();
    private static final HrRenewalPolicy RENEWAL_POLICY =
            new HrRenewalPolicy();

    private final SysConfigMapper configMapper;
    private final SysUserProfileMapper profileMapper;
    private final SysHrLifecycleActionMapper actionMapper;
    private final SysHrSignEventOutboxMapper outboxMapper;
    private final SysHrRenewalGuardMapper renewalGuardMapper;
    private final SysPostMapper postMapper;
    private final SysDeptMapper deptMapper;
    private final SysUserMapper userMapper;
    private final SysUserPostMapper userPostMapper;
    private final ISysUserShopService userShopService;
    private final ObjectMapper objectMapper;
    private final HrSalarySourceService salarySources;
    private Clock clock = Clock.systemDefaultZone();

    public HrLifecycleServiceImpl(SysConfigMapper configMapper,
            SysUserProfileMapper profileMapper,
            SysHrLifecycleActionMapper actionMapper,
            SysHrSignEventOutboxMapper outboxMapper,
            SysHrRenewalGuardMapper renewalGuardMapper,
            SysPostMapper postMapper,
            SysDeptMapper deptMapper,
            SysUserMapper userMapper,
            SysUserPostMapper userPostMapper,
            ISysUserShopService userShopService,
            ObjectMapper objectMapper, HrSalarySourceService salarySources)
    {
        this.configMapper = configMapper;
        this.profileMapper = profileMapper;
        this.actionMapper = actionMapper;
        this.outboxMapper = outboxMapper;
        this.renewalGuardMapper = renewalGuardMapper;
        this.postMapper = postMapper;
        this.deptMapper = deptMapper;
        this.userMapper = userMapper;
        this.userPostMapper = userPostMapper;
        this.userShopService = userShopService;
        this.objectMapper = objectMapper;
        this.salarySources = salarySources;
    }

    @Override
    @Transactional(readOnly = true)
    public HrEmployeeLifecycleContextVo lifecycleContext(Long employeeId, String scenario, Long operatorUserId)
    {
        requireConfiguredHr(operatorUserId);
        if (employeeId == null || employeeId <= 0 || scenario == null || !List.of("REGULARIZE", "RENEWAL").contains(scenario))
            throw new ServiceException("办理上下文无效");
        HrEmployeeLifecycleContextVo context = actionMapper.selectLifecycleContext(employeeId);
        if (context == null) throw new ServiceException("员工档案不存在");
        userShopService.checkUserShopScope(operatorUserId, context.scopeDeptId, false);
        context.scenario = scenario;
        context.businessDate = LocalDate.ofInstant(clock.instant(), BUSINESS_ZONE);
        context.history = actionMapper.selectLifecycleHistory(employeeId, scenario);
        if (context.history == null) context.history = List.of();
        if ("REGULARIZE".equals(scenario))
        {
            if (!"试用".equals(trim(context.employeeStatus))) context.blockedReason = "仅试用员工可以办理转正";
            else if (!"0".equals(context.accountStatus)) context.blockedReason = "员工账号已停用";
            else if (context.postId == null) context.blockedReason = "员工原岗位未配置，请先完善档案";
        }
        else
        {
            try
            {
                if ("离职".equals(trim(context.employeeStatus))) throw new ServiceException("离职员工不能办理续签");
                HrEmployeeSigningSnapshot before = new HrEmployeeSigningSnapshot();
                before.setContractStartDate(context.contractStartDate); before.setContractEndDate(context.contractEndDate);
                before.setContractTypeCode(context.contractTypeCode); before.setContractTermCode(context.contractTermCode);
                before.setLegalEntityId(context.legalEntityId == null ? null : Long.valueOf(context.legalEntityId));
                before.setLegalEntityCode(context.legalEntityCode); before.setLegalEntityName(context.legalEntityName);
                before.setRenewalCount(context.renewalCount);
                RENEWAL_POLICY.validateBeforeSnapshot(before);
                context.cycleKey = RENEWAL_POLICY.cycleKey(employeeId, before.getContractEndDate(), RENEWAL_POLICY.renewalCount(before));
                List<String> types = actionMapper.selectRenewalCycleTypes(employeeId, context.cycleKey);
                if (types == null || !types.contains(RENEWAL_DECISION)) throw new ServiceException("当前合同尚无待处理续签决策");
                if (types.contains(RENEWAL_CONFIRMED) || types.contains(RENEWAL_DECLINED)) throw new ServiceException("当前合同续签决定已处理");
                HrRenewalGuard guard = renewalGuardMapper.selectCurrent(employeeId, RENEWAL_SCENARIO);
                if ((guard != null && (!"IDLE".equals(guard.getStatus()) || guard.getActionId() != null || guard.getTaskId() != null || guard.getVersion() == null))
                        || actionMapper.countUnfinishedRenewal(employeeId) > 0)
                    throw new ServiceException("该员工已有未完成续签任务，请先处理已有任务");
            }
            catch (ServiceException invalid) { context.blockedReason = invalid.getMessage(); }
            catch (NumberFormatException invalid) { context.blockedReason = "员工旧法律主体信息无效"; }
        }
        context.eligible = context.blockedReason == null;
        return context;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long confirmOnboarding(Long employeeId, HrLifecycleOnboardingConfirmRequest request,
            Long operatorUserId, String operatorName, boolean operatorAdmin,
            String operatorIp, String operatorUserAgent)
    {
        requireConfiguredHr(operatorUserId);
        normalizeAndValidateRequest(employeeId, request);

        HrEmployeeSigningSnapshot before = profileMapper.selectSigningSnapshotByUserIdForUpdate(employeeId);
        if (before == null)
        {
            throw new ServiceException("员工档案不存在");
        }
        userShopService.checkUserShopScope(operatorUserId, before.getShopDeptId(), false);

        SysHrLifecycleAction existing = actionMapper.selectByRequestIdForUpdate(request.getRequestId());
        if (existing != null)
        {
            return replay(existing, employeeId);
        }
        if (!"待入职".equals(trim(before.getEmployeeStatus())))
        {
            throw new ServiceException("仅待入职员工可以确认入职");
        }
        validateDatabaseSnapshot(before);

        HrEmployeeSigningSnapshot after = objectMapper.convertValue(before, HrEmployeeSigningSnapshot.class);
        applyRequest(after, request);
        salarySources.requireOnboardingSalary(employeeId, before, after);

        Instant now = clock.instant();
        SysHrLifecycleAction action = buildAction(before, after, request, operatorUserId,
                operatorName, operatorIp, operatorUserAgent);
        try
        {
            if (actionMapper.insertAction(action) != 1 || action.getActionId() == null)
            {
                throw new ServiceException("入职动作写入失败");
            }
        }
        catch (DuplicateKeyException duplicate)
        {
            SysHrLifecycleAction concurrent = actionMapper.selectByRequestIdForUpdate(request.getRequestId());
            if (concurrent != null)
            {
                return replay(concurrent, employeeId);
            }
            throw duplicate;
        }

        if (profileMapper.updateLifecycleOnboardingProfile(after, limit(trim(operatorName), 64)) != 1)
        {
            throw new ServiceException("员工档案更新失败，请刷新后重试");
        }

        HrSignBusinessEvent event = buildEvent(action, before, after, operatorUserId, now);
        SysHrSignEventOutbox outbox = new SysHrSignEventOutbox();
        outbox.setActionId(action.getActionId());
        outbox.setEventVersion(action.getVersion());
        outbox.setPayloadJson(writeJson(event, "签约事件序列化失败"));
        outbox.setStatus("PENDING");
        outbox.setRetryCount(0);
        outbox.setVersion(0L);
        if (outboxMapper.insertOutbox(outbox) != 1)
        {
            throw new ServiceException("签约事件发件箱写入失败");
        }
        return action.getActionId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createRenewalDecision(Long employeeId, LocalDate windowStart, LocalDate windowEnd)
    {
        RENEWAL_POLICY.validateScanParameters(
                employeeId, windowStart, windowEnd);
        HrEmployeeSigningSnapshot snapshot =
                profileMapper.selectSigningSnapshotByUserIdForUpdate(employeeId);
        if (!RENEWAL_POLICY.eligibleForScan(
                snapshot, windowStart, windowEnd))
        {
            return null;
        }
        int renewalCount = RENEWAL_POLICY.renewalCount(snapshot);
        snapshot.setRenewalCount(renewalCount);
        String cycleKey = RENEWAL_POLICY.cycleKey(
                employeeId, snapshot.getContractEndDate(), renewalCount);
        List<SysHrLifecycleAction> history =
                actionMapper.selectRenewalCycleActionsForUpdate(employeeId, cycleKey);
        SysHrLifecycleAction existingDecision = findAction(history, RENEWAL_DECISION);
        if (existingDecision != null)
        {
            return existingDecision.getActionId();
        }
        if (history != null && !history.isEmpty())
        {
            return history.get(0).getActionId();
        }

        SysHrLifecycleAction action = new SysHrLifecycleAction();
        action.setActionType(RENEWAL_DECISION);
        action.setEmployeeId(employeeId);
        action.setSourceType(RENEWAL_SOURCE_TYPE);
        action.setSourceBusinessId(cycleKey);
        String snapshotJson = writeJson(snapshot, "续签决策快照序列化失败");
        action.setBeforeSnapshotJson(snapshotJson);
        action.setAfterSnapshotJson(snapshotJson);
        action.setEffectiveDate(snapshot.getContractEndDate());
        action.setBusinessStatus("PENDING");
        action.setRiskLevel("LOW");
        action.setRiskCodesJson("[]");
        action.setRequestId(RENEWAL_DECISION + ":" + cycleKey);
        action.setVersion(1L);
        action.setOperatorType("SYSTEM");
        action.setOperatorUserId(null);
        action.setOperatorName("SYSTEM");
        action.setCreateBy("SYSTEM");
        try
        {
            if (actionMapper.insertAction(action) != 1 || action.getActionId() == null)
            {
                throw new ServiceException("续签决策动作写入失败");
            }
        }
        catch (DuplicateKeyException duplicate)
        {
            SysHrLifecycleAction concurrent =
                    actionMapper.selectByRequestIdForUpdate(action.getRequestId());
            if (concurrent != null && RENEWAL_DECISION.equals(concurrent.getActionType())
                    && employeeId.equals(concurrent.getEmployeeId()))
            {
                return concurrent.getActionId();
            }
            throw duplicate;
        }
        return action.getActionId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long confirmRenewal(Long employeeId, HrRenewalDecisionRequest request,
            Long operatorUserId, String operatorName, boolean operatorAdmin,
            String operatorIp, String operatorUserAgent)
    {
        requireConfiguredHr(operatorUserId);
        RENEWAL_POLICY.normalizeAndValidate(employeeId, request);

        HrEmployeeSigningSnapshot before =
                profileMapper.selectSigningSnapshotByUserIdForUpdate(employeeId);
        if (before == null)
        {
            throw new ServiceException("员工档案不存在");
        }
        userShopService.checkUserShopScope(
                operatorUserId, before.getShopDeptId(), false);
        SysHrLifecycleAction replay =
                actionMapper.selectByRequestIdForUpdate(request.getRequestId());
        if (replay != null)
        {
            return replayRenewal(replay, employeeId, request, before);
        }
        if ("离职".equals(trim(before.getEmployeeStatus())))
        {
            throw new ServiceException("离职员工不能处理续签");
        }
        RENEWAL_POLICY.validateBeforeSnapshot(before);
        int oldRenewalCount = RENEWAL_POLICY.renewalCount(before);
        before.setRenewalCount(oldRenewalCount);
        if (request.getDecision()
                == HrRenewalDecisionRequest.Decision.RENEW)
        {
            RENEWAL_POLICY.validateTransition(before, request);
        }

        String cycleKey = RENEWAL_POLICY.cycleKey(employeeId,
                before.getContractEndDate(), oldRenewalCount);
        if (request.getExpectedCycleKey() != null)
        {
            if (!cycleKey.equals(request.getExpectedCycleKey()))
                throw new ServiceException("旧合同周期已变化，请刷新后重新办理");
            if (!Objects.equals(before.getLegalEntityId(), request.getLegalEntityId())
                    || !Objects.equals(code(before.getLegalEntityCode()), request.getLegalEntityCode())
                    || !Objects.equals(trim(before.getLegalEntityName()), request.getLegalEntityName())
                    || !Objects.equals(code(before.getContractTypeCode()), request.getContractTypeCode())
                    || !Objects.equals(code(before.getContractTermCode()), request.getContractTermCode()))
                throw new ServiceException("续签合同类型或法律主体已变化，请刷新后重新办理");
        }
        List<SysHrLifecycleAction> history =
                actionMapper.selectRenewalCycleActionsForUpdate(employeeId, cycleKey);
        if (findAction(history, RENEWAL_CONFIRMED) != null
                || findAction(history, RENEWAL_DECLINED) != null)
        {
            throw new ServiceException("该旧合同续签决定已处理");
        }
        if (findAction(history, RENEWAL_DECISION) == null)
        {
            throw new ServiceException("该旧合同没有待处理续签决策");
        }
        renewalGuardMapper.insertIdle(employeeId, RENEWAL_SCENARIO);
        HrRenewalGuard guard = renewalGuardMapper.selectForUpdate(
                employeeId, RENEWAL_SCENARIO);
        if (guard == null)
        {
            throw new ServiceException("续签任务门闩初始化失败");
        }
        if (!"IDLE".equals(guard.getStatus()) || guard.getActionId() != null
                || guard.getTaskId() != null || guard.getVersion() == null)
        {
            throw new ServiceException("该员工已有未完成续签任务");
        }

        HrEmployeeSigningSnapshot after =
                objectMapper.convertValue(before, HrEmployeeSigningSnapshot.class);
        boolean renewing = request.getDecision() == HrRenewalDecisionRequest.Decision.RENEW;
        if (renewing)
        {
            RENEWAL_POLICY.apply(
                    after, request, oldRenewalCount + 1);
        }

        String actionType = renewing ? RENEWAL_CONFIRMED : RENEWAL_DECLINED;
        SysHrLifecycleAction action = buildRenewalAction(before, after, request, actionType,
                cycleKey, operatorUserId, operatorName, operatorIp, operatorUserAgent);
        try
        {
            if (actionMapper.insertAction(action) != 1 || action.getActionId() == null)
            {
                throw new ServiceException("续签决定动作写入失败");
            }
        }
        catch (DuplicateKeyException duplicate)
        {
            SysHrLifecycleAction concurrent =
                    actionMapper.selectByRequestIdForUpdate(request.getRequestId());
            if (concurrent != null)
            {
                return replayRenewal(concurrent, employeeId, request, before);
            }
            throw duplicate;
        }

        if (renewalGuardMapper.reserve(employeeId, RENEWAL_SCENARIO,
                action.getActionId(), guard.getVersion()) != 1)
        {
            throw new ServiceException("续签任务门闩预留失败，请重试");
        }

        if (renewing && profileMapper.updateRenewalProfile(after,
                before.getContractEndDate(), oldRenewalCount,
                limit(trim(operatorName), 64)) != 1)
        {
            throw new ServiceException("员工续签档案更新失败，请刷新后重试");
        }

        HrSignBusinessEvent event = buildRenewalEvent(action, before, after,
                request.getDecision(), operatorUserId, clock.instant());
        SysHrSignEventOutbox outbox = new SysHrSignEventOutbox();
        outbox.setActionId(action.getActionId());
        outbox.setEventVersion(action.getVersion());
        outbox.setPayloadJson(writeJson(event, "续签事件序列化失败"));
        outbox.setStatus("PENDING");
        outbox.setRetryCount(0);
        outbox.setVersion(0L);
        if (outboxMapper.insertOutbox(outbox) != 1)
        {
            throw new ServiceException("续签事件发件箱写入失败");
        }
        return action.getActionId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long confirmRegularization(Long employeeId, HrRegularizationRequest request,
            Long operatorUserId, String operatorName, boolean operatorAdmin,
            String operatorIp, String operatorUserAgent)
    {
        requireConfiguredHr(operatorUserId);
        REGULARIZATION_POLICY.normalizeAndValidate(employeeId, request);

        HrEmployeeSigningSnapshot before =
                profileMapper.selectSigningSnapshotByUserIdForUpdate(employeeId);
        if (before == null)
        {
            throw new ServiceException("员工档案不存在");
        }
        userShopService.checkUserShopScope(
                operatorUserId, before.getShopDeptId(), false);

        SysHrLifecycleAction existing =
                actionMapper.selectByRequestIdForUpdate(request.getRequestId());
        if (existing != null)
        {
            return replayRegularization(existing, employeeId, request, before, true);
        }
        if (!"试用".equals(trim(before.getEmployeeStatus())))
        {
            throw new ServiceException("仅试用员工可以确认转正");
        }
        REGULARIZATION_POLICY.validateEffectiveDate(
                before, request.getActualRegularizationDate(),
                LocalDate.ofInstant(clock.instant(), BUSINESS_ZONE));
        if (before.getPostId() == null)
        {
            throw new ServiceException("员工原岗位未配置");
        }

        if (request.isPreservePositionSalary() && !"0".equals(before.getAccountStatus()))
            throw new ServiceException("员工账号已停用，不能办理转正");
        SysPost canonicalPost = null;
        if (!request.isPreservePositionSalary())
        {
            canonicalPost = postMapper.selectPostByIdForUpdate(request.getPostId());
            REGULARIZATION_POLICY.validateCanonicalPost(canonicalPost, request);
        }
        HrEmployeeSigningSnapshot after =
                objectMapper.convertValue(before, HrEmployeeSigningSnapshot.class);
        REGULARIZATION_POLICY.apply(after, request, canonicalPost);
        if (REGULARIZATION_POLICY.salaryChanged(before, after)) AuthUtil.checkPermi("hr:employee:salary:edit");

        SysHrLifecycleAction action = buildRegularizationAction(before, after, request,
                operatorUserId, operatorName, operatorIp, operatorUserAgent);
        try
        {
            if (actionMapper.insertAction(action) != 1 || action.getActionId() == null)
            {
                throw new ServiceException("转正动作写入失败");
            }
        }
        catch (DuplicateKeyException duplicate)
        {
            SysHrLifecycleAction concurrent =
                    actionMapper.selectByRequestIdForUpdate(request.getRequestId());
            if (concurrent != null)
            {
                return replayRegularization(concurrent, employeeId, request, before, false);
            }
            throw duplicate;
        }

        String auditName = limit(trim(operatorName), 64);
        if (request.isPreservePositionSalary())
        {
            if (profileMapper.updateRegularizationDateOnly(employeeId, request.getActualRegularizationDate(), auditName) != 1)
                throw new ServiceException("员工转正状态已变化，请刷新后重试");
        }
        else
        {
            if (profileMapper.updateRegularizationProfile(after, auditName) != 1)
                throw new ServiceException("员工转正档案更新失败，请刷新后重试");
            if (REGULARIZATION_POLICY.salaryChanged(before, after))
                salarySources.recordChange(employeeId, "REGULARIZATION", action.getActionId(), before, after,
                        request.getActualRegularizationDate(), operatorUserId, auditName);
            if (userPostMapper.deleteUserPostByUserId(employeeId) < 1)
                throw new ServiceException("员工原岗位关联更新失败，请刷新后重试");
            SysUserPost userPost = new SysUserPost();
            userPost.setUserId(employeeId);
            userPost.setPostId(canonicalPost.getPostId());
            if (userPostMapper.batchUserPost(List.of(userPost)) != 1)
                throw new ServiceException("员工新岗位关联写入失败");
        }

        HrSignBusinessEvent event = buildRegularizationEvent(action, before, after,
                operatorUserId, clock.instant());
        SysHrSignEventOutbox outbox = new SysHrSignEventOutbox();
        outbox.setActionId(action.getActionId());
        outbox.setEventVersion(action.getVersion());
        outbox.setPayloadJson(writeJson(event, "转正事件序列化失败"));
        outbox.setStatus("PENDING");
        outbox.setRetryCount(0);
        outbox.setVersion(0L);
        if (outboxMapper.insertOutbox(outbox) != 1)
        {
            throw new ServiceException("转正事件发件箱写入失败");
        }
        return action.getActionId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public Long confirmOffboarding(Long employeeId, HrOffboardingConfirmRequest request,
            Long operatorUserId, String operatorName, boolean operatorAdmin,
            String operatorIp, String operatorUserAgent)
    {
        requireConfiguredHr(operatorUserId);
        OFFBOARDING_POLICY.normalizeAndValidate(employeeId, request);
        LocalDate operationDate = offboardingBusinessDate();
        if (request.getLastWorkingDate().isAfter(operationDate))
        {
            throw new ServiceException("未来最后工作日的离职暂不能确认，请在最后工作日操作");
        }

        profileMapper.lockSigningProfileByUserId(employeeId);
        HrEmployeeSigningSnapshot before =
                profileMapper.selectSigningSnapshotByUserIdForUpdate(employeeId);
        if (before == null)
        {
            throw new ServiceException("员工档案不存在");
        }
        userShopService.checkUserShopScope(operatorUserId, before.getShopDeptId(), false);

        SysHrLifecycleAction existing =
                actionMapper.selectByRequestIdForUpdate(request.getRequestId());
        if (existing != null)
        {
            return replayOffboarding(existing, employeeId, request, before, operationDate);
        }

        String oldEmployeeStatus = trim(before.getEmployeeStatus());
        String oldAccountStatus = trim(before.getAccountStatus());
        if (oldEmployeeStatus == null || "待入职".equals(oldEmployeeStatus)
                || "离职".equals(oldEmployeeStatus))
        {
            throw new ServiceException("当前员工状态不能确认离职");
        }
        if (!"0".equals(oldAccountStatus))
        {
            throw new ServiceException("员工账号已停用，不能确认离职");
        }
        if (before.getLeaveDate() != null
                && !Objects.equals(before.getLeaveDate(), request.getLastWorkingDate()))
        {
            throw new ServiceException("员工已有不同的计划离职日期");
        }

        boolean historical = request.getLastWorkingDate().isBefore(operationDate);
        HrEmployeeSigningSnapshot after =
                objectMapper.convertValue(before, HrEmployeeSigningSnapshot.class);
        OFFBOARDING_POLICY.apply(after, request);
        List<String> riskCodes =
                OFFBOARDING_POLICY.riskCodes(request, historical);
        if (!riskCodes.isEmpty())
        {
            OFFBOARDING_POLICY.validateRiskConfirmation(
                    request.getRiskConfirmation(), before,
                    request, operationDate);
        }

        List<SysHrLifecycleAction> sameDateActions =
                actionMapper.selectOffboardingActionsForUpdate(
                        employeeId, request.getLastWorkingDate());
        if (sameDateActions != null && !sameDateActions.isEmpty())
        {
            throw new ServiceException("该员工相同最后工作日的离职已确认");
        }

        Instant actualConfirmTime = clock.instant();
        SysHrLifecycleAction action = buildOffboardingAction(before, after, request,
                historical, riskCodes, actualConfirmTime, operatorUserId, operatorName,
                operatorIp, operatorUserAgent);
        try
        {
            if (actionMapper.insertAction(action) != 1 || action.getActionId() == null)
            {
                throw new ServiceException("离职动作写入失败");
            }
        }
        catch (DuplicateKeyException duplicate)
        {
            SysHrLifecycleAction concurrent =
                    actionMapper.selectByRequestIdForUpdate(request.getRequestId());
            if (concurrent != null)
            {
                return replayOffboarding(concurrent, employeeId, request, before,
                        operationDate);
            }
            throw duplicate;
        }

        String auditName = limit(trim(operatorName), 64);
        if (profileMapper.updateOffboardingProfile(after, oldEmployeeStatus,
                before.getLeaveDate(), auditName) != 1)
        {
            throw new ServiceException("员工离职档案更新失败，请刷新后重试");
        }
        if (userMapper.disableUserForOffboarding(employeeId, oldAccountStatus,
                auditName) != 1)
        {
            throw new ServiceException("员工账号停用失败，请刷新后重试");
        }

        HrSignBusinessEvent event = buildOffboardingEvent(action, before, after,
                historical, operatorUserId, actualConfirmTime);
        SysHrSignEventOutbox outbox = new SysHrSignEventOutbox();
        outbox.setActionId(action.getActionId());
        outbox.setEventVersion(action.getVersion());
        outbox.setPayloadJson(writeTransferJson(event, "离职签约事件序列化失败"));
        outbox.setStatus("PENDING");
        outbox.setRetryCount(0);
        outbox.setVersion(0L);
        if (outboxMapper.insertOutbox(outbox) != 1)
        {
            throw new ServiceException("离职签约事件发件箱写入失败");
        }
        return action.getActionId();
    }

    @Override
    public LocalDate offboardingBusinessDate()
    {
        return LocalDate.ofInstant(clock.instant(), BUSINESS_ZONE);
    }

    @Override
    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public Long confirmTransfer(Long employeeId, HrEmployeeTransferRequest request,
            Long operatorUserId, String operatorName, boolean operatorAdmin,
        String operatorIp, String operatorUserAgent)
    {
        requireConfiguredHr(operatorUserId);
        TRANSFER_POLICY.normalizeAndValidate(employeeId, request);
        if (request.isAdjustSalary()) AuthUtil.checkPermi("hr:employee:salary:edit");
        LocalDate businessToday = transferBusinessDate();
        if (request.getEffectiveDate().isAfter(businessToday))
        {
            throw new ServiceException("未来日期的调岗暂不能确认，请在生效当天操作");
        }

        profileMapper.lockSigningProfileByUserId(employeeId);
        HrEmployeeSigningSnapshot before =
                profileMapper.selectSigningSnapshotByUserIdForUpdate(employeeId);
        if (before == null)
        {
            throw new ServiceException("员工档案不存在");
        }
        if ("离职".equals(trim(before.getEmployeeStatus()))
                || "待入职".equals(trim(before.getEmployeeStatus())))
        {
            throw new ServiceException("当前员工状态不能确认调岗");
        }
        userShopService.checkUserShopScope(operatorUserId, before.getShopDeptId(), false);

        SysHrLifecycleAction existing =
                actionMapper.selectByRequestIdForUpdate(request.getRequestId());
        if (existing != null)
        {
            return replayTransfer(existing, employeeId, request, before, businessToday);
        }

        boolean historical = request.getEffectiveDate().isBefore(businessToday);
        if (historical && request.getRiskConfirmation() == null)
        {
            throw new ServiceException("过去日期调岗属于高风险补录，必须完成同一HR二次确认");
        }

        SysDept targetDept = deptMapper.selectDeptByIdForUpdate(request.getTargetDeptId());
        TRANSFER_POLICY.validateDept(targetDept, request);
        userShopService.checkUserShopScope(operatorUserId, targetDept.getDeptId(), false);
        SysPost targetPost = postMapper.selectPostByIdForUpdate(request.getPostId());
        TRANSFER_POLICY.validatePost(targetPost, request);
        SysUser supervisor = request.getDirectSupervisorId() == null
                ? null
                : userMapper.selectUserById(request.getDirectSupervisorId());
        TRANSFER_POLICY.validateSupervisor(supervisor, request);

        HrEmployeeSigningSnapshot after =
                objectMapper.convertValue(before, HrEmployeeSigningSnapshot.class);
        TRANSFER_POLICY.apply(after, request, targetDept, targetPost, supervisor);
        List<String> riskCodes =
                TRANSFER_POLICY.riskCodes(before, after, historical);
        if (!TRANSFER_POLICY.changed(before, after))
        {
            throw new ServiceException("调岗前后没有业务变化");
        }
        if (historical)
        {
            TRANSFER_POLICY.validateHistoricalConfirmation(
                    request.getRiskConfirmation(), before, after,
                    request.getEffectiveDate(), businessToday);
        }

        List<SysHrLifecycleAction> sameDateActions =
                actionMapper.selectTransferActionsForUpdate(employeeId, request.getEffectiveDate());
        if (sameDateActions != null && !sameDateActions.isEmpty())
        {
            throw new ServiceException("该员工相同生效日期的调岗已确认");
        }

        Instant actualConfirmTime = clock.instant();
        SysHrLifecycleAction action = buildTransferAction(before, after, request,
                historical, riskCodes, actualConfirmTime, operatorUserId, operatorName,
                operatorIp, operatorUserAgent);
        try
        {
            if (actionMapper.insertAction(action) != 1 || action.getActionId() == null)
            {
                throw new ServiceException("调岗动作写入失败");
            }
        }
        catch (DuplicateKeyException duplicate)
        {
            SysHrLifecycleAction concurrent =
                    actionMapper.selectByRequestIdForUpdate(request.getRequestId());
            if (concurrent != null)
            {
                return replayTransfer(concurrent, employeeId, request, before, businessToday);
            }
            throw duplicate;
        }

        String auditName = limit(trim(operatorName), 64);
        if (userMapper.updateTransferDept(employeeId, targetDept.getDeptId(),
                before.getShopDeptId(), auditName) != 1)
        {
            throw new ServiceException("员工当前组织更新失败，请刷新后重试");
        }
        if (profileMapper.updateTransferProfile(after, auditName) != 1)
        {
            throw new ServiceException("员工调岗档案更新失败，请刷新后重试");
        }
        if (TRANSFER_POLICY.salaryChanged(before, after))
        {
            salarySources.recordChange(employeeId, "TRANSFER", action.getActionId(), before, after,
                    request.getEffectiveDate(), operatorUserId, auditName);
        }
        if (userPostMapper.deleteUserPostByUserId(employeeId) < 1)
        {
            throw new ServiceException("员工原岗位关联更新失败，请刷新后重试");
        }
        SysUserPost userPost = new SysUserPost();
        userPost.setUserId(employeeId);
        userPost.setPostId(targetPost.getPostId());
        if (userPostMapper.batchUserPost(List.of(userPost)) != 1)
        {
            throw new ServiceException("员工新岗位关联写入失败");
        }

        HrSignBusinessEvent event = buildTransferEvent(action, before, after,
                historical, operatorUserId, actualConfirmTime);
        SysHrSignEventOutbox outbox = new SysHrSignEventOutbox();
        outbox.setActionId(action.getActionId());
        outbox.setEventVersion(action.getVersion());
        outbox.setPayloadJson(writeTransferJson(event, "调岗签约事件序列化失败"));
        outbox.setStatus("PENDING");
        outbox.setRetryCount(0);
        outbox.setVersion(0L);
        if (outboxMapper.insertOutbox(outbox) != 1)
        {
            throw new ServiceException("调岗签约事件发件箱写入失败");
        }
        return action.getActionId();
    }

    @Override
    public LocalDate transferBusinessDate()
    {
        return LocalDate.ofInstant(clock.instant(), BUSINESS_ZONE);
    }

    private SysHrLifecycleAction buildOffboardingAction(
            HrEmployeeSigningSnapshot before, HrEmployeeSigningSnapshot after,
            HrOffboardingConfirmRequest request, boolean historical,
            List<String> riskCodes, Instant actualConfirmTime, Long operatorUserId,
            String operatorName, String operatorIp, String operatorUserAgent)
    {
        SysHrLifecycleAction action = new SysHrLifecycleAction();
        action.setActionType("OFFBOARD_CONFIRMED");
        action.setEmployeeId(after.getEmployeeId());
        action.setSourceType("HR_OFFBOARDING");
        action.setSourceBusinessId(request.getRequestId());
        action.setBeforeSnapshotJson(writeTransferJson(before,
                "离职动作前快照序列化失败"));
        action.setAfterSnapshotJson(writeTransferJson(after,
                "离职动作后快照序列化失败"));
        action.setEffectiveDate(request.getLastWorkingDate());
        action.setActualConfirmTime(Date.from(actualConfirmTime));
        action.setBusinessStatus("CONFIRMED");
        action.setRiskLevel(riskCodes.isEmpty() ? "LOW" : "HIGH");
        action.setRiskCodesJson(writeJson(riskCodes, "离职风险代码序列化失败"));
        action.setRiskDetail(riskCodes.isEmpty() ? null : String.join(",", riskCodes));
        if (!riskCodes.isEmpty())
        {
            action.setRiskConfirmationJson(writeTransferJson(
                    request.getRiskConfirmation(), "离职二次确认序列化失败"));
            if (historical)
                action.setHistoricalReason(request.getRiskConfirmation().getReason());
        }
        action.setRequestId(request.getRequestId());
        action.setVersion(1L);
        action.setOperatorType("HUMAN");
        action.setOperatorUserId(operatorUserId);
        action.setOperatorName(limit(trim(operatorName), 64));
        action.setOperatorIp(limit(trim(operatorIp), 64));
        action.setOperatorUserAgent(limit(trim(operatorUserAgent), 500));
        action.setCreateBy(limit(trim(operatorName), 64));
        return action;
    }

    private HrSignBusinessEvent buildOffboardingEvent(SysHrLifecycleAction action,
            HrEmployeeSigningSnapshot before, HrEmployeeSigningSnapshot after,
            boolean historical, Long operatorUserId, Instant actualConfirmTime)
    {
        HrSignBusinessEvent event = new HrSignBusinessEvent();
        event.setEventId(UUID.randomUUID().toString());
        event.setScenario("OFFBOARD");
        event.setEmployeeId(after.getEmployeeId());
        event.setSourceType(SOURCE_TYPE);
        event.setSourceBusinessId(String.valueOf(action.getActionId()));
        event.setSourceEventVersion(action.getVersion());
        event.setOccurredTime(Date.from(actualConfirmTime));
        event.setOperatorUserId(operatorUserId);
        event.setBeforeSnapshot(before);
        event.setAfterSnapshot(after);
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("actionType", action.getActionType());
        attributes.put("sourceActionId", action.getActionId());
        attributes.put("sourceActionVersion", action.getVersion());
        attributes.put("effectiveDate", action.getEffectiveDate().toString());
        attributes.put("actualConfirmTime", action.getActualConfirmTime());
        attributes.put("historicalSupplement", historical);
        attributes.put("riskLevel", action.getRiskLevel());
        if (historical)
            attributes.put("historicalReason", action.getHistoricalReason());
        event.setAttributes(attributes);
        return event;
    }

    private Long replayOffboarding(SysHrLifecycleAction existing, Long employeeId,
            HrOffboardingConfirmRequest request, HrEmployeeSigningSnapshot current,
            LocalDate operationDate)
    {
        if (!"OFFBOARD_CONFIRMED".equals(existing.getActionType())
                || !"HR_OFFBOARDING".equals(existing.getSourceType())
                || !employeeId.equals(existing.getEmployeeId())
                || !Objects.equals(request.getRequestId(), existing.getRequestId())
                || !Objects.equals(request.getRequestId(), existing.getSourceBusinessId())
                || !Objects.equals(request.getLastWorkingDate(), existing.getEffectiveDate())
                || !Long.valueOf(1L).equals(existing.getVersion()))
        {
            throw new ServiceException("requestId对应的离职payload不一致");
        }
        HrEmployeeSigningSnapshot after = readOffboardingSnapshot(
                existing.getAfterSnapshotJson(), "after");
        if (!OFFBOARDING_POLICY.matchesRequest(after, request)
                || !OFFBOARDING_POLICY.sameState(current, after))
        {
            throw new ServiceException("requestId对应的离职payload不一致");
        }
        LocalDate originalOperationDate = existing.getActualConfirmTime() == null
                ? operationDate
                : LocalDate.ofInstant(existing.getActualConfirmTime().toInstant(),
                        BUSINESS_ZONE);
        List<String> riskCodes = OFFBOARDING_POLICY.riskCodes(request,
                request.getLastWorkingDate().isBefore(originalOperationDate));
        if (!riskCodes.isEmpty())
        {
            HrEmployeeSigningSnapshot before = readOffboardingSnapshot(
                    existing.getBeforeSnapshotJson(), "before");
            OFFBOARDING_POLICY.validateRiskConfirmation(
                    request.getRiskConfirmation(), before,
                    request, originalOperationDate);
            if (request.getLastWorkingDate().isBefore(originalOperationDate)
                    && !Objects.equals(existing.getHistoricalReason(),
                            request.getRiskConfirmation().getReason()))
            {
                throw new ServiceException("requestId对应的离职payload不一致");
            }
        }
        return existing.getActionId();
    }

    private HrEmployeeSigningSnapshot readOffboardingSnapshot(String json, String label)
    {
        try
        {
            HrEmployeeSigningSnapshot snapshot = objectMapper.readValue(
                    json, HrEmployeeSigningSnapshot.class);
            if (snapshot == null)
                throw new ServiceException("requestId离职" + label + "快照无效");
            return snapshot;
        }
        catch (JsonProcessingException exception)
        {
            throw new ServiceException("requestId离职" + label + "快照无效");
        }
    }

    private SysHrLifecycleAction buildTransferAction(
            HrEmployeeSigningSnapshot before, HrEmployeeSigningSnapshot after,
            HrEmployeeTransferRequest request, boolean historical, List<String> riskCodes,
            Instant actualConfirmTime, Long operatorUserId, String operatorName,
            String operatorIp, String operatorUserAgent)
    {
        SysHrLifecycleAction action = new SysHrLifecycleAction();
        action.setActionType("TRANSFER_CONFIRMED");
        action.setEmployeeId(after.getEmployeeId());
        action.setSourceType("HR_TRANSFER");
        action.setSourceBusinessId(request.getRequestId());
        action.setBeforeSnapshotJson(writeTransferJson(before, "调岗动作前快照序列化失败"));
        action.setAfterSnapshotJson(writeTransferJson(after, "调岗动作后快照序列化失败"));
        action.setEffectiveDate(request.getEffectiveDate());
        action.setActualConfirmTime(Date.from(actualConfirmTime));
        action.setBusinessStatus("CONFIRMED");
        action.setRiskLevel(riskCodes.isEmpty() ? "LOW" : "HIGH");
        action.setRiskCodesJson(writeJson(riskCodes, "调岗风险代码序列化失败"));
        action.setRiskDetail(riskCodes.isEmpty() ? null : String.join(",", riskCodes));
        if (historical)
        {
            action.setRiskConfirmationJson(writeTransferJson(request.getRiskConfirmation(),
                    "历史调岗二次确认序列化失败"));
            action.setHistoricalReason(request.getRiskConfirmation().getReason());
        }
        action.setRequestId(request.getRequestId());
        action.setVersion(1L);
        action.setOperatorType("HUMAN");
        action.setOperatorUserId(operatorUserId);
        action.setOperatorName(limit(trim(operatorName), 64));
        action.setOperatorIp(limit(trim(operatorIp), 64));
        action.setOperatorUserAgent(limit(trim(operatorUserAgent), 500));
        action.setCreateBy(limit(trim(operatorName), 64));
        return action;
    }

    private HrSignBusinessEvent buildTransferEvent(SysHrLifecycleAction action,
            HrEmployeeSigningSnapshot before, HrEmployeeSigningSnapshot after,
            boolean historical, Long operatorUserId, Instant actualConfirmTime)
    {
        HrSignBusinessEvent event = new HrSignBusinessEvent();
        event.setEventId(UUID.randomUUID().toString());
        event.setScenario("TRANSFER");
        event.setEmployeeId(after.getEmployeeId());
        event.setSourceType(SOURCE_TYPE);
        event.setSourceBusinessId(String.valueOf(action.getActionId()));
        event.setSourceEventVersion(action.getVersion());
        event.setOccurredTime(Date.from(actualConfirmTime));
        event.setOperatorUserId(operatorUserId);
        event.setBeforeSnapshot(before);
        event.setAfterSnapshot(after);
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("actionType", "TRANSFER_CONFIRMED");
        attributes.put("sourceActionId", action.getActionId());
        attributes.put("sourceActionVersion", action.getVersion());
        attributes.put("effectiveDate", action.getEffectiveDate().toString());
        attributes.put("actualConfirmTime", action.getActualConfirmTime());
        attributes.put("historicalSupplement", historical);
        attributes.put("riskLevel", action.getRiskLevel());
        if (historical) attributes.put("historicalReason", action.getHistoricalReason());
        event.setAttributes(attributes);
        return event;
    }

    private Long replayTransfer(SysHrLifecycleAction existing, Long employeeId,
            HrEmployeeTransferRequest request, HrEmployeeSigningSnapshot current,
            LocalDate operationDate)
    {
        if (!"TRANSFER_CONFIRMED".equals(existing.getActionType())
                || !"HR_TRANSFER".equals(existing.getSourceType())
                || !employeeId.equals(existing.getEmployeeId())
                || !Objects.equals(request.getRequestId(), existing.getRequestId())
                || !Objects.equals(request.getEffectiveDate(), existing.getEffectiveDate())
                || !Long.valueOf(1L).equals(existing.getVersion()))
        {
            throw new ServiceException("requestId对应的调岗payload不一致");
        }
        HrEmployeeSigningSnapshot before = readTransferSnapshot(
                existing.getBeforeSnapshotJson(), "before");
        HrEmployeeSigningSnapshot after = readTransferSnapshot(
                existing.getAfterSnapshotJson(), "after");
        if ((!request.isAdjustSalary() && TRANSFER_POLICY.salaryChanged(before, after))
                || !TRANSFER_POLICY.matchesRequest(after, request)
                || !TRANSFER_POLICY.sameState(current, after))
        {
            throw new ServiceException("requestId对应的调岗payload不一致")
                    .setDetailMessage(TRANSFER_POLICY.replayMismatchDetail(
                            current, after, request));
        }
        LocalDate originalOperationDate = existing.getActualConfirmTime() == null
                ? operationDate
                : LocalDate.ofInstant(existing.getActualConfirmTime().toInstant(), BUSINESS_ZONE);
        if (request.getEffectiveDate().isAfter(originalOperationDate))
        {
            throw new ServiceException("requestId对应的调岗payload不一致");
        }
        if (request.getEffectiveDate().isBefore(originalOperationDate))
        {
            TRANSFER_POLICY.validateHistoricalConfirmation(
                    request.getRiskConfirmation(), before, after,
                    request.getEffectiveDate(), originalOperationDate);
            if (!Objects.equals(existing.getHistoricalReason(),
                    request.getRiskConfirmation().getReason()))
            {
                throw new ServiceException("requestId对应的调岗payload不一致");
            }
        }
        return existing.getActionId();
    }

    private HrEmployeeSigningSnapshot readTransferSnapshot(String json, String label)
    {
        try
        {
            HrEmployeeSigningSnapshot snapshot = objectMapper.readValue(
                    json, HrEmployeeSigningSnapshot.class);
            if (snapshot == null) throw new ServiceException("requestId调岗" + label + "快照无效");
            return snapshot;
        }
        catch (JsonProcessingException exception)
        {
            throw new ServiceException("requestId调岗" + label + "快照无效");
        }
    }

    private SysHrLifecycleAction buildRegularizationAction(
            HrEmployeeSigningSnapshot before, HrEmployeeSigningSnapshot after,
            HrRegularizationRequest request, Long operatorUserId, String operatorName,
            String operatorIp, String operatorUserAgent)
    {
        boolean salaryChanged =
                REGULARIZATION_POLICY.salaryChanged(before, after);
        SysHrLifecycleAction action = new SysHrLifecycleAction();
        action.setActionType(REGULARIZATION_CONFIRMED);
        action.setEmployeeId(after.getEmployeeId());
        action.setSourceType(REGULARIZATION_SOURCE_TYPE);
        action.setSourceBusinessId(request.getRequestId());
        action.setBeforeSnapshotJson(writeJson(before, "转正动作前快照序列化失败"));
        action.setAfterSnapshotJson(writeJson(after, "转正动作后快照序列化失败"));
        action.setEffectiveDate(request.getActualRegularizationDate());
        action.setBusinessStatus("CONFIRMED");
        action.setRiskLevel(salaryChanged ? "REVIEW_REQUIRED" : "LOW");
        action.setRiskCodesJson(salaryChanged ? "[\"SALARY_CHANGED\"]" : "[]");
        action.setRequestId(request.getRequestId());
        action.setVersion(1L);
        action.setOperatorType("HUMAN");
        action.setOperatorUserId(operatorUserId);
        action.setOperatorName(limit(trim(operatorName), 64));
        action.setOperatorIp(limit(trim(operatorIp), 64));
        action.setOperatorUserAgent(limit(trim(operatorUserAgent), 500));
        action.setCreateBy(limit(trim(operatorName), 64));
        return action;
    }

    private HrSignBusinessEvent buildRegularizationEvent(SysHrLifecycleAction action,
            HrEmployeeSigningSnapshot before, HrEmployeeSigningSnapshot after,
            Long operatorUserId, Instant now)
    {
        HrSignBusinessEvent event = new HrSignBusinessEvent();
        event.setEventId(UUID.randomUUID().toString());
        event.setScenario(REGULARIZATION_SCENARIO);
        event.setEmployeeId(after.getEmployeeId());
        event.setSourceType(SOURCE_TYPE);
        event.setSourceBusinessId(String.valueOf(action.getActionId()));
        event.setSourceEventVersion(action.getVersion());
        event.setOccurredTime(Date.from(now));
        event.setOperatorUserId(operatorUserId);
        event.setBeforeSnapshot(before);
        event.setAfterSnapshot(after);
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("actionType", REGULARIZATION_CONFIRMED);
        attributes.put("sourceActionId", action.getActionId());
        attributes.put("sourceActionVersion", action.getVersion());
        event.setAttributes(attributes);
        return event;
    }

    private Long replayRegularization(SysHrLifecycleAction existing, Long employeeId,
            HrRegularizationRequest request, HrEmployeeSigningSnapshot current,
            boolean requireCurrentState)
    {
        if (!REGULARIZATION_CONFIRMED.equals(existing.getActionType())
                || !employeeId.equals(existing.getEmployeeId())
                || !REGULARIZATION_SOURCE_TYPE.equals(existing.getSourceType())
                || !Objects.equals(request.getRequestId(), existing.getRequestId())
                || !Objects.equals(request.getRequestId(), existing.getSourceBusinessId())
                || !Long.valueOf(1L).equals(existing.getVersion()))
        {
            throw new ServiceException("requestId对应的转正payload不一致");
        }
        HrEmployeeSigningSnapshot before = readRegularizationSnapshot(
                existing.getBeforeSnapshotJson(), "before");
        HrEmployeeSigningSnapshot after = readRegularizationSnapshot(
                existing.getAfterSnapshotJson(), "after");
        HrEmployeeSigningSnapshot preserved = objectMapper.convertValue(before, HrEmployeeSigningSnapshot.class);
        if (request.isPreservePositionSalary()) REGULARIZATION_POLICY.apply(preserved, request, null);
        if (!request.isPreservePositionSalary() && REGULARIZATION_POLICY.salaryChanged(before, after))
            AuthUtil.checkPermi("hr:employee:salary:edit");
        if (!employeeId.equals(before.getEmployeeId())
                || !employeeId.equals(after.getEmployeeId())
                || !"试用".equals(trim(before.getEmployeeStatus()))
                || !"正式".equals(trim(after.getEmployeeStatus()))
                || !Objects.equals(existing.getEffectiveDate(),
                        request.getActualRegularizationDate())
                || (request.isPreservePositionSalary() ? !REGULARIZATION_POLICY.sameState(preserved, after)
                        : !REGULARIZATION_POLICY.matchesRequest(after, request))
                || (requireCurrentState
                        && !REGULARIZATION_POLICY.sameState(current, after)))
        {
            throw new ServiceException("requestId对应的转正payload不一致");
        }
        return existing.getActionId();
    }

    private HrEmployeeSigningSnapshot readRegularizationSnapshot(String json, String label)
    {
        try
        {
            if (trim(json) == null)
            {
                throw new ServiceException("requestId转正" + label + "快照缺失");
            }
            HrEmployeeSigningSnapshot snapshot = objectMapper.readValue(
                    json, HrEmployeeSigningSnapshot.class);
            if (snapshot == null)
            {
                throw new ServiceException("requestId转正" + label + "快照无效");
            }
            return snapshot;
        }
        catch (JsonProcessingException exception)
        {
            throw new ServiceException("requestId转正" + label + "快照无效");
        }
    }

    private void requireConfiguredHr(Long operatorUserId)
    {
        Long configuredHrUserId = configMapper.selectConfiguredSignHrUserId();
        if (operatorUserId == null || configuredHrUserId == null || !configuredHrUserId.equals(operatorUserId))
        {
            throw new ServiceException("仅当前配置HR本人可以执行此操作");
        }
    }

    private SysHrLifecycleAction buildRenewalAction(HrEmployeeSigningSnapshot before,
            HrEmployeeSigningSnapshot after, HrRenewalDecisionRequest request,
            String actionType, String cycleKey, Long operatorUserId, String operatorName,
            String operatorIp, String operatorUserAgent)
    {
        SysHrLifecycleAction action = new SysHrLifecycleAction();
        action.setActionType(actionType);
        action.setEmployeeId(after.getEmployeeId());
        action.setSourceType(RENEWAL_SOURCE_TYPE);
        action.setSourceBusinessId(cycleKey);
        action.setBeforeSnapshotJson(writeJson(before, "续签动作前快照序列化失败"));
        action.setAfterSnapshotJson(writeJson(after, "续签动作后快照序列化失败"));
        action.setEffectiveDate(request.getDecision() == HrRenewalDecisionRequest.Decision.RENEW
                ? request.getContractStartDate() : before.getContractEndDate());
        action.setBusinessStatus(request.getDecision() == HrRenewalDecisionRequest.Decision.RENEW
                ? "CONFIRMED" : "DECLINED");
        action.setRiskLevel("LOW");
        action.setRiskCodesJson("[]");
        action.setRequestId(request.getRequestId());
        action.setVersion(1L);
        action.setOperatorType("HUMAN");
        action.setOperatorUserId(operatorUserId);
        action.setOperatorName(limit(trim(operatorName), 64));
        action.setOperatorIp(limit(trim(operatorIp), 64));
        action.setOperatorUserAgent(limit(trim(operatorUserAgent), 500));
        action.setCreateBy(limit(trim(operatorName), 64));
        return action;
    }

    private HrSignBusinessEvent buildRenewalEvent(SysHrLifecycleAction action,
            HrEmployeeSigningSnapshot before, HrEmployeeSigningSnapshot after,
            HrRenewalDecisionRequest.Decision decision, Long operatorUserId, Instant now)
    {
        HrSignBusinessEvent event = new HrSignBusinessEvent();
        event.setEventId(UUID.randomUUID().toString());
        event.setScenario(RENEWAL_SCENARIO);
        event.setEmployeeId(after.getEmployeeId());
        event.setSourceType(SOURCE_TYPE);
        event.setSourceBusinessId(String.valueOf(action.getActionId()));
        event.setSourceEventVersion(action.getVersion());
        event.setOccurredTime(Date.from(now));
        event.setOperatorUserId(operatorUserId);
        event.setBeforeSnapshot(before);
        event.setAfterSnapshot(after);
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("actionType", action.getActionType());
        attributes.put("decision", decision.name());
        attributes.put("oldContractEndDate", before.getContractEndDate().toString());
        attributes.put("oldRenewalCount", before.getRenewalCount());
        attributes.put("newRenewalCount", after.getRenewalCount());
        event.setAttributes(attributes);
        return event;
    }

    private SysHrLifecycleAction findAction(List<SysHrLifecycleAction> history,
            String actionType)
    {
        if (history == null)
        {
            return null;
        }
        for (SysHrLifecycleAction action : history)
        {
            if (action != null && actionType.equals(action.getActionType()))
            {
                return action;
            }
        }
        return null;
    }

    private Long replayRenewal(SysHrLifecycleAction existing, Long employeeId,
            HrRenewalDecisionRequest request, HrEmployeeSigningSnapshot current)
    {
        if (request.getExpectedCycleKey() != null && !Objects.equals(request.getExpectedCycleKey(), existing.getSourceBusinessId()))
            throw new ServiceException("requestId对应的旧合同周期不一致");

        String expectedActionType = request.getDecision()
                == HrRenewalDecisionRequest.Decision.RENEW
                ? RENEWAL_CONFIRMED : RENEWAL_DECLINED;
        if (!expectedActionType.equals(existing.getActionType())
                || !employeeId.equals(existing.getEmployeeId())
                || !RENEWAL_SOURCE_TYPE.equals(existing.getSourceType()))
        {
            throw new ServiceException("requestId对应的续签payload不一致");
        }
        HrEmployeeSigningSnapshot before = readRenewalSnapshot(
                existing.getBeforeSnapshotJson(), "before");
        HrEmployeeSigningSnapshot after = readRenewalSnapshot(
                existing.getAfterSnapshotJson(), "after");
        if (!employeeId.equals(before.getEmployeeId()) || !employeeId.equals(after.getEmployeeId()))
        {
            throw new ServiceException("requestId已被其他业务使用");
        }
        int oldCount = RENEWAL_POLICY.renewalCount(before);
        String expectedCycle = before.getContractEndDate() == null ? null
                : RENEWAL_POLICY.cycleKey(
                        employeeId, before.getContractEndDate(), oldCount);
        if (!Objects.equals(expectedCycle, existing.getSourceBusinessId()))
        {
            throw new ServiceException("requestId对应的旧合同周期不一致");
        }
        if (request.getDecision() == HrRenewalDecisionRequest.Decision.RENEW)
        {
            if (!RENEWAL_POLICY.matchesRequest(after, request)
                    || after.getRenewalCount() == null
                    || after.getRenewalCount() != oldCount + 1)
            {
                throw new ServiceException("requestId对应的续签payload不一致");
            }
            if (!RENEWAL_POLICY.sameState(current, after))
            {
                throw new ServiceException("requestId对应的旧合同周期不一致");
            }
        }
        else
        {
            if (!RENEWAL_POLICY.sameState(before, after)
                    || !RENEWAL_POLICY.sameState(current, before))
            {
                throw new ServiceException("requestId对应的旧合同周期不一致");
            }
        }
        return existing.getActionId();
    }

    private HrEmployeeSigningSnapshot readRenewalSnapshot(String json, String label)
    {
        try
        {
            if (trim(json) == null)
            {
                throw new ServiceException("requestId续签" + label + "快照缺失");
            }
            HrEmployeeSigningSnapshot snapshot = objectMapper.readValue(
                    json, HrEmployeeSigningSnapshot.class);
            if (snapshot == null)
            {
                throw new ServiceException("requestId续签" + label + "快照无效");
            }
            return snapshot;
        }
        catch (JsonProcessingException ex)
        {
            throw new ServiceException("requestId续签" + label + "快照无效");
        }
    }

    private void normalizeAndValidateRequest(Long employeeId, HrLifecycleOnboardingConfirmRequest request)
    {
        if (employeeId == null || employeeId <= 0)
        {
            throw new ServiceException("员工ID不能为空");
        }
        if (request == null)
        {
            throw new ServiceException("确认入职请求不能为空");
        }
        request.setRequestId(trim(request.getRequestId()));
        request.setContractTypeCode(code(request.getContractTypeCode()));
        request.setContractTermCode(code(request.getContractTermCode()));
        request.setSocialTypeCode(code(request.getSocialTypeCode()));
        request.setJobGradeCode(code(request.getJobGradeCode()));
        request.setLegalEntityCode(code(request.getLegalEntityCode()));
        request.setLegalEntityName(trim(request.getLegalEntityName()));
        request.setSalaryVersion(trim(request.getSalaryVersion()));

        requireText(request.getRequestId(), "requestId不能为空");
        if (request.getRequestId().length() > 64)
        {
            throw new ServiceException("requestId长度不能超过64个字符");
        }
        requireDate(request.getEntryDate(), "入职日期不能为空");
        requireDate(request.getContractStartDate(), "合同开始日期不能为空");
        requireDate(request.getContractEndDate(), "合同结束日期不能为空");
        if (request.getContractStartDate().isBefore(request.getEntryDate()))
        {
            throw new ServiceException("合同开始日期不能早于入职日期");
        }
        if (!request.getContractEndDate().isAfter(request.getContractStartDate()))
        {
            throw new ServiceException("合同结束日期必须晚于合同开始日期");
        }
        validateProbation(request);

        if (!SigningProfileCodes.isKnownContractType(request.getContractTypeCode()))
        {
            throw new ServiceException("合同类型代码不受支持");
        }
        if (!SigningProfileCodes.isKnownContractTerm(request.getContractTermCode()))
        {
            throw new ServiceException("合同期限代码不受支持");
        }
        if (!SigningProfileCodes.isKnownSocialType(request.getSocialTypeCode()))
        {
            throw new ServiceException("社保类型代码不受支持");
        }
        requireText(request.getJobGradeCode(), "职级代码不能为空");
        if (request.getLegalEntityId() == null || request.getLegalEntityId() <= 0)
        {
            throw new ServiceException("法律主体ID必须为正数");
        }
        requireText(request.getLegalEntityCode(), "法律主体代码不能为空");
        requireText(request.getLegalEntityName(), "法律主体名称不能为空");
        requireText(request.getSalaryVersion(), "薪资版本不能为空");
        validateSalary(request);
    }

    private void validateProbation(HrLifecycleOnboardingConfirmRequest request)
    {
        LocalDate start = request.getProbationStartDate();
        LocalDate end = request.getProbationEndDate();
        if ((start == null) != (end == null))
        {
            throw new ServiceException("试用期开始和结束日期必须同时填写");
        }
        if (start == null)
        {
            return;
        }
        if (start.isBefore(request.getEntryDate()))
        {
            throw new ServiceException("试用期开始日期不能早于入职日期");
        }
        if (end.isBefore(start))
        {
            throw new ServiceException("试用期结束日期不能早于开始日期");
        }
        if (end.isAfter(request.getContractEndDate()))
        {
            throw new ServiceException("试用期结束日期不能晚于合同结束日期");
        }
    }

    private void validateSalary(HrLifecycleOnboardingConfirmRequest request)
    {
        BigDecimal base = nonNegative(request.getBaseSalary(), "基本工资");
        BigDecimal post = nonNegative(request.getPostSalary(), "岗位工资");
        BigDecimal field = nonNegative(request.getFieldAllowance(), "外勤补贴");
        BigDecimal performance = nonNegative(request.getPerformanceSalary(), "绩效工资");
        BigDecimal total = nonNegative(request.getSalaryTotal(), "薪资合计");
        if (total.signum() <= 0)
        {
            throw new ServiceException("薪资合计必须大于0");
        }
        if (base.add(post).add(field).add(performance).compareTo(total) != 0)
        {
            throw new ServiceException("薪资合计必须等于各薪资项之和");
        }
    }

    private BigDecimal nonNegative(BigDecimal value, String label)
    {
        if (value == null || value.signum() < 0)
        {
            throw new ServiceException(label + "不能为空且不能为负数");
        }
        if (value.scale() > MAX_MONEY_SCALE)
        {
            throw new ServiceException(label + "小数不能超过2位");
        }
        long integerDigits = value.signum() == 0
                ? 0L : Math.max((long) value.precision() - value.scale(), 0L);
        if (integerDigits > MAX_MONEY_INTEGER_DIGITS)
        {
            throw new ServiceException(label + "整数不能超过14位");
        }
        return value;
    }

    private void validateDatabaseSnapshot(HrEmployeeSigningSnapshot snapshot)
    {
        requireText(snapshot.getEmployeeName(), "员工姓名未填写");
        requireText(snapshot.getPhone(), "员工手机号未填写");
        requireText(snapshot.getIdType(), "证件类型未填写");
        requireText(snapshot.getIdNumber(), "证件号码未填写");
        requireText(snapshot.getCurrentAddress(), "现住址未填写");
        if (snapshot.getShopDeptId() == null || snapshot.getDeptId() == null)
        {
            throw new ServiceException("员工组织或门店未配置");
        }
        if (snapshot.getPostId() == null || trim(snapshot.getPostCode()) == null
                || trim(snapshot.getPostName()) == null)
        {
            throw new ServiceException("员工岗位未配置");
        }
    }

    private void applyRequest(HrEmployeeSigningSnapshot snapshot, HrLifecycleOnboardingConfirmRequest request)
    {
        snapshot.setEmployeeStatus(request.getProbationStartDate() == null ? "正式" : "试用");
        snapshot.setEntryDate(request.getEntryDate());
        snapshot.setContractStartDate(request.getContractStartDate());
        snapshot.setContractEndDate(request.getContractEndDate());
        snapshot.setProbationStartDate(request.getProbationStartDate());
        snapshot.setProbationEndDate(request.getProbationEndDate());
        snapshot.setContractTypeCode(request.getContractTypeCode());
        snapshot.setContractTermCode(request.getContractTermCode());
        snapshot.setSocialTypeCode(request.getSocialTypeCode());
        snapshot.setJobGradeCode(request.getJobGradeCode());
        snapshot.setJobGradeName(request.getJobGradeCode());
        snapshot.setLegalEntityId(request.getLegalEntityId());
        snapshot.setLegalEntityCode(request.getLegalEntityCode());
        snapshot.setLegalEntityName(request.getLegalEntityName());
        snapshot.setBaseSalary(request.getBaseSalary());
        snapshot.setPostSalary(request.getPostSalary());
        snapshot.setFieldAllowance(request.getFieldAllowance());
        snapshot.setPerformanceSalary(request.getPerformanceSalary());
        snapshot.setSalaryTotal(request.getSalaryTotal());
        snapshot.setSalaryVersion(request.getSalaryVersion());
    }

    private SysHrLifecycleAction buildAction(HrEmployeeSigningSnapshot before,
            HrEmployeeSigningSnapshot after, HrLifecycleOnboardingConfirmRequest request,
            Long operatorUserId, String operatorName, String operatorIp, String operatorUserAgent)
    {
        SysHrLifecycleAction action = new SysHrLifecycleAction();
        action.setActionType(ACTION_TYPE);
        action.setEmployeeId(after.getEmployeeId());
        action.setSourceType("HR_ONBOARDING");
        action.setSourceBusinessId(request.getRequestId());
        action.setBeforeSnapshotJson(writeJson(before, "动作前快照序列化失败"));
        action.setAfterSnapshotJson(writeJson(after, "动作后快照序列化失败"));
        action.setEffectiveDate(request.getEntryDate());
        action.setBusinessStatus("CONFIRMED");
        action.setRiskLevel("LOW");
        action.setRiskCodesJson("[]");
        action.setRequestId(request.getRequestId());
        action.setVersion(1L);
        action.setOperatorType("HUMAN");
        action.setOperatorUserId(operatorUserId);
        action.setOperatorName(limit(trim(operatorName), 64));
        action.setOperatorIp(limit(trim(operatorIp), 64));
        action.setOperatorUserAgent(limit(trim(operatorUserAgent), 500));
        action.setCreateBy(limit(trim(operatorName), 64));
        return action;
    }

    private HrSignBusinessEvent buildEvent(SysHrLifecycleAction action,
            HrEmployeeSigningSnapshot before, HrEmployeeSigningSnapshot after,
            Long operatorUserId, Instant now)
    {
        HrSignBusinessEvent event = new HrSignBusinessEvent();
        event.setEventId(UUID.randomUUID().toString());
        event.setScenario(SCENARIO);
        event.setEmployeeId(after.getEmployeeId());
        event.setSourceType(SOURCE_TYPE);
        event.setSourceBusinessId(String.valueOf(action.getActionId()));
        event.setSourceEventVersion(action.getVersion());
        event.setOccurredTime(Date.from(now));
        event.setOperatorUserId(operatorUserId);
        event.setBeforeSnapshot(before);
        event.setAfterSnapshot(after);
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("actionType", ACTION_TYPE);
        event.setAttributes(attributes);
        return event;
    }

    private Long replay(SysHrLifecycleAction existing, Long employeeId)
    {
        if (!ACTION_TYPE.equals(existing.getActionType()) || !employeeId.equals(existing.getEmployeeId()))
        {
            throw new ServiceException("requestId已被其他业务使用");
        }
        return existing.getActionId();
    }

    private String writeJson(Object value, String message)
    {
        try
        {
            return objectMapper.writeValueAsString(value);
        }
        catch (JsonProcessingException exception)
        {
            throw new ServiceException(message).setDetailMessage(exception.getMessage());
        }
    }

    private String writeTransferJson(Object value, String message)
    {
        try
        {
            return objectMapper.writer()
                    .without(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                    .writeValueAsString(value);
        }
        catch (JsonProcessingException exception)
        {
            throw new ServiceException(message).setDetailMessage(exception.getMessage());
        }
    }

    private void requireText(String value, String message)
    {
        if (trim(value) == null)
        {
            throw new ServiceException(message);
        }
    }

    private void requireMaxLength(String value, int maxLength, String message)
    {
        if (value != null && value.length() > maxLength)
        {
            throw new ServiceException(message);
        }
    }

    private void requireDate(LocalDate value, String message)
    {
        if (value == null)
        {
            throw new ServiceException(message);
        }
    }

    private String code(String value)
    {
        String normalized = trim(value);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    private String trim(String value)
    {
        if (value == null)
        {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String limit(String value, int maxLength)
    {
        if (value == null || value.length() <= maxLength)
        {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
