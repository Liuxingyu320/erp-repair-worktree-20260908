package com.erp.approval.service;

import java.util.List;
import org.springframework.stereotype.Service;
import com.erp.approval.domain.ApprovalInstance;
import com.erp.approval.domain.ApprovalTask;
import com.erp.approval.domain.dto.ApprovalInstanceDetail;
import com.erp.approval.mapper.ApprovalRuntimeMapper;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.security.auth.AuthUtil;
import com.erp.common.security.utils.SecurityUtils;

@Service
public class ApprovalMonitorService
{
    private final ApprovalRuntimeMapper runtimeMapper;

    public ApprovalMonitorService(ApprovalRuntimeMapper runtimeMapper)
    {
        this.runtimeMapper = runtimeMapper;
    }

    public List<ApprovalInstance> listInstances(ApprovalInstance filter)
    {
        return runtimeMapper.selectInstanceList(filter == null
                ? new ApprovalInstance() : filter);
    }

    public ApprovalInstanceDetail getInstanceDetail(Long instanceId,
            Long currentUserId)
    {
        ApprovalInstance instance = runtimeMapper.selectInstanceById(instanceId);
        if (instance == null)
        {
            throw new ServiceException("审批实例不存在");
        }
        boolean viewAll = SecurityUtils.isAdmin()
                || AuthUtil.hasPermi("approval:instance:query");
        if (!viewAll && (currentUserId == null
                || runtimeMapper.countInstanceParticipant(instanceId,
                        currentUserId) == 0))
        {
            throw new ServiceException("无权查看该审批实例");
        }
        ApprovalInstanceDetail detail = new ApprovalInstanceDetail();
        detail.setInstance(instance);
        detail.setTasks(runtimeMapper.selectTasksByInstanceId(instanceId));
        detail.setCandidates(runtimeMapper.selectCandidatesByInstanceId(instanceId));
        detail.setActions(runtimeMapper.selectActionsByInstanceId(instanceId));
        detail.setCallbacks(runtimeMapper.selectCallbacksByInstanceId(instanceId));
        return detail;
    }

    public boolean canAccessInstance(Long instanceId, Long userId)
    {
        return instanceId != null && userId != null
                && runtimeMapper.countInstanceParticipant(instanceId,
                        userId) > 0;
    }

    public String currentNodeName(Long instanceId)
    {
        return runtimeMapper.selectTasksByInstanceId(instanceId).stream()
                .filter(item -> "PENDING".equals(item.getTaskStatus()))
                .map(ApprovalTask::getNodeName).findFirst().orElse(null);
    }
}
