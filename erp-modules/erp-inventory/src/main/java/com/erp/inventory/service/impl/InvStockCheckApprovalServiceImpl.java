package com.erp.inventory.service.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.alibaba.fastjson2.JSON;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.security.utils.SecurityUtils;
import com.erp.inventory.domain.InvStockCheck;
import com.erp.inventory.domain.InvStockCheckApprovalInstance;
import com.erp.inventory.domain.InvStockCheckApprovalTask;
import com.erp.inventory.domain.InvStockCheckDetail;
import com.erp.inventory.domain.dto.InvStockCheckApprovalRequest;
import com.erp.inventory.domain.vo.InvStockCheckAdjustmentResult;
import com.erp.inventory.mapper.InvStockCheckApprovalCandidateMapper;
import com.erp.inventory.mapper.InvStockCheckApprovalInstanceMapper;
import com.erp.inventory.mapper.InvStockCheckApprovalTaskMapper;
import com.erp.inventory.mapper.InvStockCheckDetailMapper;
import com.erp.inventory.mapper.InvStockCheckMapper;
import com.erp.inventory.service.IInvStockCheckApprovalService;

@Service
public class InvStockCheckApprovalServiceImpl extends InvBaseService
        implements IInvStockCheckApprovalService
{
    private static final String STATUS_RUNNING = "running";
    private static final String STATUS_PENDING = "pending";
    private static final String STATUS_PENDING_APPROVAL = "pending_approval";
    private static final String STATUS_APPROVED = "approved";
    private static final String STATUS_REJECTED = "rejected";
    private static final String STATUS_INVALIDATED = "invalidated";
    private static final String STATUS_CANCELLED = "cancelled";
    private static final String STATUS_COMPLETED = "completed";
    private static final String SNAPSHOT_INVALID_REASON = "库存快照已变化，需要重新盘点";

    @Autowired
    private InvStockCheckApprovalInstanceMapper instanceMapper;

    @Autowired
    private InvStockCheckApprovalTaskMapper taskMapper;

    @Autowired
    private InvStockCheckApprovalCandidateMapper candidateMapper;

    @Autowired
    private InvStockCheckMapper checkMapper;

    @Autowired
    private InvStockCheckDetailMapper detailMapper;

    @Autowired
    private InvStockCheckAdjustmentService adjustmentService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InvStockCheckApprovalInstance createPendingApproval(InvStockCheck check,
            List<InvStockCheckDetail> details)
    {
        if (check == null || check.getCheckId() == null)
        {
            throw new ServiceException("盘点单不能为空");
        }
        List<Candidate> candidates = candidates(check.getShopDeptId());
        if (candidates.isEmpty())
        {
            throw new ServiceException("当前组织未配置具有盘点审批权限的运营总监，请先配置岗位、角色和组织授权");
        }
        InvStockCheckApprovalInstance instance = new InvStockCheckApprovalInstance();
        instance.setCheckId(check.getCheckId());
        instance.setRoundNo((check.getApprovalRound() == null ? 0 : check.getApprovalRound()) + 1);
        instance.setStatus(STATUS_RUNNING);
        instance.setShopDeptId(check.getShopDeptId());
        instance.setCheckNo(check.getCheckNo());
        instance.setProfitItemCount(countBySign(details, 1));
        instance.setLossItemCount(countBySign(details, -1));
        instance.setTotalAbsDiffQuantity(totalAbsoluteDifference(details));
        instance.setDetailSnapshot(JSON.toJSONString(details == null ? List.of() : details));
        instance.setSubmittedUserId(SecurityUtils.getUserId());
        instance.setSubmittedBy(SecurityUtils.getUsername());
        instance.setSubmittedTime(new Date());
        instance.setCreateBy(SecurityUtils.getUsername());
        instanceMapper.insertInstance(instance);

        InvStockCheckApprovalTask task = new InvStockCheckApprovalTask();
        task.setInstanceId(instance.getInstanceId());
        task.setCheckId(check.getCheckId());
        task.setRoundNo(instance.getRoundNo());
        task.setCandidateUserIds(candidates.stream()
                .map(candidate -> String.valueOf(candidate.userId)).collect(Collectors.joining(",")));
        task.setCandidateUserNames(candidates.stream()
                .map(candidate -> candidate.userName).collect(Collectors.joining(",")));
        task.setStatus(STATUS_PENDING);
        task.setSelfApproved("0");
        task.setCreateBy(SecurityUtils.getUsername());
        taskMapper.insertTask(task);
        instance.setTasks(List.of(task));
        return instance;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approve(Long checkId, InvStockCheckApprovalRequest request,
            Long selectedShopDeptId)
    {
        if (checkId == null || request == null || request.getInstanceId() == null)
        {
            throw new ServiceException("审批参数不能为空");
        }
        InvStockCheck check = checkMapper.selectInvStockCheckByIdForUpdate(checkId);
        if (check == null)
        {
            throw new ServiceException("盘点单不存在");
        }
        requireLegacyEngine(check);
        assertShopVisible(check.getShopDeptId(), selectedShopDeptId, "无权审批该盘点单");
        if (!STATUS_PENDING_APPROVAL.equals(check.getStatus()))
        {
            throw new ServiceException("当前盘点单不在待审批状态");
        }

        InvStockCheckApprovalInstance instance =
                instanceMapper.selectByIdForUpdate(request.getInstanceId());
        if (instance == null || !Objects.equals(checkId, instance.getCheckId())
                || !STATUS_RUNNING.equals(instance.getStatus()))
        {
            throw new ServiceException("当前审批已处理或审批记录无效");
        }
        assertCurrentInstance(check, instance);
        InvStockCheckApprovalTask task = taskMapper.selectByInstanceIdForUpdate(instance.getInstanceId());
        if (task == null || !STATUS_PENDING.equals(task.getStatus()))
        {
            throw new ServiceException("当前审批任务已处理");
        }

        Long userId = SecurityUtils.getUserId();
        List<Candidate> currentCandidates = candidates(check.getShopDeptId());
        if (currentCandidates.stream().noneMatch(candidate -> Objects.equals(userId, candidate.userId)))
        {
            throw new ServiceException("当前用户不是该盘点单的有效审批人");
        }

        List<InvStockCheckDetail> details =
                detailMapper.selectInvStockCheckDetailByCheckIdForUpdate(checkId);
        InvStockCheckAdjustmentResult result = adjustmentService.evaluate(check, details, true);
        if (result.hasSnapshotChanges())
        {
            invalidate(check, instance, task, result, request.getComment());
            return;
        }

        Date now = new Date();
        String username = SecurityUtils.getUsername();
        task.setStatus(STATUS_APPROVED);
        task.setAction("approve");
        task.setApproverUserId(userId);
        task.setApproverName(username);
        task.setApprovalComment(request.getComment());
        task.setApprovalTime(now);
        task.setSelfApproved(Objects.equals(userId, instance.getSubmittedUserId()) ? "1" : "0");
        task.setUpdateBy(username);
        taskMapper.updateTask(task);

        instance.setStatus(STATUS_APPROVED);
        instance.setAdjustmentResultSnapshot(JSON.toJSONString(result.getAdjustments()));
        instance.setFinishedUserId(userId);
        instance.setFinishedBy(username);
        instance.setFinishedTime(now);
        instance.setFinishComment(request.getComment());
        instance.setUpdateBy(username);
        instanceMapper.updateInstance(instance);

        check.setStatus(STATUS_COMPLETED);
        if (check.getApprovalInstanceId() == null)
        {
            check.setApprovalInstanceId(instance.getInstanceId());
            check.setApprovalEngine(InventoryUnifiedApprovalService.ENGINE_LEGACY);
        }
        check.setApprovedUserId(userId);
        check.setApprovedBy(username);
        check.setApprovedTime(now);
        check.setUpdateBy(username);
        checkMapper.updateInvStockCheck(check);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reject(Long checkId, InvStockCheckApprovalRequest request,
            Long selectedShopDeptId)
    {
        if (request == null || request.getComment() == null
                || request.getComment().trim().isEmpty())
        {
            throw new ServiceException("驳回原因不能为空");
        }
        if (checkId == null || request.getInstanceId() == null)
        {
            throw new ServiceException("审批参数不能为空");
        }
        InvStockCheck check = checkMapper.selectInvStockCheckByIdForUpdate(checkId);
        if (check == null)
        {
            throw new ServiceException("盘点单不存在");
        }
        requireLegacyEngine(check);
        assertShopVisible(check.getShopDeptId(), selectedShopDeptId, "无权审批该盘点单");
        if (!STATUS_PENDING_APPROVAL.equals(check.getStatus()))
        {
            throw new ServiceException("当前盘点单不在待审批状态");
        }
        InvStockCheckApprovalInstance instance =
                instanceMapper.selectByIdForUpdate(request.getInstanceId());
        if (instance == null || !Objects.equals(checkId, instance.getCheckId())
                || !STATUS_RUNNING.equals(instance.getStatus()))
        {
            throw new ServiceException("当前审批已处理或审批记录无效");
        }
        assertCurrentInstance(check, instance);
        InvStockCheckApprovalTask task = taskMapper.selectByInstanceIdForUpdate(instance.getInstanceId());
        if (task == null || !STATUS_PENDING.equals(task.getStatus()))
        {
            throw new ServiceException("当前审批任务已处理");
        }

        Long userId = SecurityUtils.getUserId();
        assertCurrentCandidate(check.getShopDeptId(), userId);
        Date now = new Date();
        String username = SecurityUtils.getUsername();
        String reason = request.getComment().trim();

        finishTask(task, STATUS_REJECTED, "reject", userId, username, reason, now,
                Objects.equals(userId, instance.getSubmittedUserId()));
        taskMapper.updateTask(task);
        finishInstance(instance, STATUS_REJECTED, userId, username, reason, now);
        instanceMapper.updateInstance(instance);

        check.setStatus(STATUS_REJECTED);
        if (check.getApprovalInstanceId() == null)
        {
            check.setApprovalInstanceId(instance.getInstanceId());
            check.setApprovalEngine(InventoryUnifiedApprovalService.ENGINE_LEGACY);
        }
        check.setLastRejectReason(reason);
        check.setLastRejectedUserId(userId);
        check.setLastRejectedBy(username);
        check.setLastRejectedTime(now);
        check.setUpdateBy(username);
        checkMapper.updateInvStockCheck(check);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelRunning(Long checkId)
    {
        if (checkId == null)
        {
            return;
        }
        InvStockCheckApprovalInstance instance =
                instanceMapper.selectRunningByCheckIdForUpdate(checkId);
        if (instance == null)
        {
            return;
        }
        Long userId = SecurityUtils.getUserId();
        String username = SecurityUtils.getUsername();
        Date now = new Date();
        InvStockCheckApprovalTask task = taskMapper.selectByInstanceIdForUpdate(instance.getInstanceId());
        if (task != null && STATUS_PENDING.equals(task.getStatus()))
        {
            finishTask(task, STATUS_CANCELLED, "cancel", userId, username, "盘点已取消", now,
                    Objects.equals(userId, instance.getSubmittedUserId()));
            taskMapper.updateTask(task);
        }
        finishInstance(instance, STATUS_CANCELLED, userId, username, "盘点已取消", now);
        instanceMapper.updateInstance(instance);
    }

    @Override
    public List<InvStockCheckApprovalInstance> selectTrack(Long checkId, Long selectedShopDeptId)
    {
        InvStockCheck check = checkMapper.selectInvStockCheckById(checkId);
        if (check == null)
        {
            throw new ServiceException("盘点单不存在");
        }
        requireLegacyEngine(check);
        assertShopVisible(check.getShopDeptId(), selectedShopDeptId, "无权查看该盘点单审批轨迹");
        List<InvStockCheckApprovalInstance> instances = instanceMapper.selectByCheckId(checkId);
        if (instances == null)
        {
            return List.of();
        }
        for (InvStockCheckApprovalInstance instance : instances)
        {
            List<InvStockCheckApprovalTask> tasks = taskMapper.selectByInstanceId(instance.getInstanceId());
            instance.setTasks(tasks == null ? List.of() : tasks);
        }
        return instances;
    }

    private void invalidate(InvStockCheck check, InvStockCheckApprovalInstance instance,
            InvStockCheckApprovalTask task, InvStockCheckAdjustmentResult result, String comment)
    {
        Long userId = SecurityUtils.getUserId();
        String username = SecurityUtils.getUsername();
        Date now = new Date();
        String detailSnapshot = JSON.toJSONString(result.getSnapshotChanges());
        finishTask(task, STATUS_INVALIDATED, "invalidate", userId, username,
                SNAPSHOT_INVALID_REASON, now, Objects.equals(userId, instance.getSubmittedUserId()));
        taskMapper.updateTask(task);

        finishInstance(instance, STATUS_INVALIDATED, userId, username, comment, now);
        instance.setInvalidReason(SNAPSHOT_INVALID_REASON);
        instance.setInvalidDetailSnapshot(detailSnapshot);
        instanceMapper.updateInstance(instance);

        check.setStatus(STATUS_INVALIDATED);
        check.setLastInvalidReason(SNAPSHOT_INVALID_REASON);
        check.setLastInvalidDetailSnapshot(detailSnapshot);
        check.setLastInvalidatedTime(now);
        check.setUpdateBy(username);
        checkMapper.updateInvStockCheck(check);
    }

    private void assertCurrentCandidate(Long shopDeptId, Long userId)
    {
        if (candidates(shopDeptId).stream()
                .noneMatch(candidate -> Objects.equals(userId, candidate.userId)))
        {
            throw new ServiceException("当前用户不是该盘点单的有效审批人");
        }
    }

    private static void requireLegacyEngine(InvStockCheck check)
    {
        if (InventoryUnifiedApprovalService.ENGINE_NATIVE.equalsIgnoreCase(
                check.getApprovalEngine() == null ? ""
                        : check.getApprovalEngine()))
        {
            throw new ServiceException("该盘点单已使用统一审批，请在工作台处理或查看轨迹");
        }
    }

    private void assertCurrentInstance(InvStockCheck check,
            InvStockCheckApprovalInstance instance)
    {
        Long currentInstanceId = check.getApprovalInstanceId();
        if (currentInstanceId == null)
        {
            InvStockCheckApprovalInstance running =
                    instanceMapper.selectRunningByCheckIdForUpdate(check.getCheckId());
            currentInstanceId = running == null ? null : running.getInstanceId();
        }
        if (!Objects.equals(currentInstanceId, instance.getInstanceId()))
        {
            throw new ServiceException("该审批记录不是当前审批实例，请刷新后重试");
        }
    }

    private static void finishTask(InvStockCheckApprovalTask task, String status, String action,
            Long userId, String username, String comment, Date now, boolean selfApproved)
    {
        task.setStatus(status);
        task.setAction(action);
        task.setApproverUserId(userId);
        task.setApproverName(username);
        task.setApprovalComment(comment);
        task.setApprovalTime(now);
        task.setSelfApproved(selfApproved ? "1" : "0");
        task.setUpdateBy(username);
    }

    private static void finishInstance(InvStockCheckApprovalInstance instance, String status,
            Long userId, String username, String comment, Date now)
    {
        instance.setStatus(status);
        instance.setFinishedUserId(userId);
        instance.setFinishedBy(username);
        instance.setFinishedTime(now);
        instance.setFinishComment(comment);
        instance.setUpdateBy(username);
    }

    private List<Candidate> candidates(Long deptId)
    {
        List<Map<String, Object>> rows = candidateMapper.selectOperationsDirectorCandidates(deptId);
        List<Candidate> candidates = new ArrayList<>();
        if (rows == null)
        {
            return candidates;
        }
        for (Map<String, Object> row : rows)
        {
            Long userId = asLong(first(row, "userId", "user_id", "USERID", "USER_ID"));
            if (userId != null)
            {
                Object name = first(row, "userName", "user_name", "USERNAME", "USER_NAME");
                candidates.add(new Candidate(userId, name == null ? "" : String.valueOf(name)));
            }
        }
        return candidates;
    }

    private static int countBySign(List<InvStockCheckDetail> details, int sign)
    {
        if (details == null)
        {
            return 0;
        }
        int count = 0;
        for (InvStockCheckDetail detail : details)
        {
            if (safe(detail.getDiffQty()).signum() == sign)
            {
                count++;
            }
        }
        return count;
    }

    private static BigDecimal totalAbsoluteDifference(List<InvStockCheckDetail> details)
    {
        BigDecimal total = BigDecimal.ZERO;
        if (details != null)
        {
            for (InvStockCheckDetail detail : details)
            {
                total = total.add(safe(detail.getDiffQty()).abs());
            }
        }
        return total;
    }

    private static BigDecimal safe(BigDecimal value)
    {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static Object first(Map<String, Object> row, String... keys)
    {
        if (row == null)
        {
            return null;
        }
        for (String key : keys)
        {
            if (row.containsKey(key))
            {
                return row.get(key);
            }
        }
        return null;
    }

    private static Long asLong(Object value)
    {
        if (value instanceof Number)
        {
            return ((Number) value).longValue();
        }
        try
        {
            return value == null ? null : Long.valueOf(String.valueOf(value));
        }
        catch (NumberFormatException e)
        {
            return null;
        }
    }

    private static final class Candidate
    {
        private final Long userId;
        private final String userName;

        private Candidate(Long userId, String userName)
        {
            this.userId = userId;
            this.userName = userName;
        }
    }
}
