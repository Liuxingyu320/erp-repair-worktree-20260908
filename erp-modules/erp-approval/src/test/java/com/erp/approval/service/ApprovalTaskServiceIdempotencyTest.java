package com.erp.approval.service;

import static com.erp.approval.constant.ApprovalRuntimeConstants.ACTION_APPROVE;
import static com.erp.approval.constant.ApprovalRuntimeConstants.ACTION_REASSIGN;
import static com.erp.approval.constant.ApprovalRuntimeConstants.CANDIDATE_PENDING;
import static com.erp.approval.constant.ApprovalRuntimeConstants.INSTANCE_RUNNING;
import static com.erp.approval.constant.ApprovalRuntimeConstants.TASK_PENDING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import com.erp.approval.guard.ApprovalBusinessActionGuardRegistry;
import com.erp.approval.candidate.ApprovalDirectoryUser;
import com.erp.approval.domain.ApprovalActionLog;
import com.erp.approval.domain.ApprovalInstance;
import com.erp.approval.domain.ApprovalTask;
import com.erp.approval.domain.ApprovalTaskCandidate;
import com.erp.approval.domain.dto.ApprovalReassignRequest;
import com.erp.approval.domain.dto.ApprovalTaskActionRequest;
import com.erp.approval.mapper.ApprovalCandidateDirectoryMapper;
import com.erp.approval.mapper.ApprovalDefinitionMapper;
import com.erp.approval.mapper.ApprovalRuntimeMapper;
import com.erp.common.core.exception.ServiceException;
import org.springframework.dao.DuplicateKeyException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("统一审批动作幂等语义")
class ApprovalTaskServiceIdempotencyTest
{
    private ApprovalRuntimeMapper runtimeMapper;
    private ApprovalCandidateDirectoryMapper directoryMapper;
    private ApprovalPermissionPolicy permissionPolicy;
    private ApprovalRuntimeService runtimeService;
    private ApprovalTaskService service;

    @BeforeEach
    void setUp()
    {
        runtimeMapper = mock(ApprovalRuntimeMapper.class);
        directoryMapper = mock(ApprovalCandidateDirectoryMapper.class);
        permissionPolicy = mock(ApprovalPermissionPolicy.class);
        runtimeService = mock(ApprovalRuntimeService.class);
        service = new ApprovalTaskService(runtimeMapper,
                mock(ApprovalDefinitionMapper.class), directoryMapper,
                permissionPolicy, runtimeService,
                new ApprovalBusinessActionGuardRegistry(List.of()));
    }

    @Test
    @DisplayName("同一请求ID与同一审批意见可安全重放")
    void shouldReplayOnlyWhenActionPayloadMatches()
    {
        ApprovalInstance instance = instance();
        ApprovalTask task = task();
        ApprovalActionLog existing = existingAction("同意入库");
        arrangeExisting(task, instance, existing);
        ApprovalTaskActionRequest request = request("  同意入库  ");

        ApprovalInstance result = service.approve(31L, request, 9L, "alice");

        assertThat(result).isSameAs(instance);
        assertThat(request.getReason()).isEqualTo("同意入库");
        verifyNoInteractions(directoryMapper);
    }

    @Test
    @DisplayName("同一请求ID不得用于不同审批意见")
    void shouldRejectReplayWhenReasonDiffers()
    {
        ApprovalInstance instance = instance();
        ApprovalTask task = task();
        arrangeExisting(task, instance, existingAction("同意入库"));

        assertThatThrownBy(() -> service.approve(31L,
                request("同意，但请补发票"), 9L, "alice"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("请求ID")
                .hasMessageContaining("其他审批内容");
        verifyNoInteractions(directoryMapper);
    }

    @Test
    @DisplayName("改派重放也必须匹配原审计意见")
    void shouldRejectReassignReplayWhenReasonDiffers()
    {
        ApprovalInstance instance = instance();
        ApprovalTask task = task();
        ApprovalActionLog existing = existingAction("原审批人请假");
        existing.setActionType(ACTION_REASSIGN);
        existing.setCandidateId(45L);
        when(runtimeMapper.selectTaskById(31L)).thenReturn(task);
        when(runtimeService.requireInstance(88L)).thenReturn(instance);
        when(runtimeMapper.selectActionByKey(
                ApprovalRuntimeService.actionKey(88L, ACTION_REASSIGN,
                        "request-1")))
                .thenReturn(existing);
        ApprovalReassignRequest request = new ApprovalReassignRequest();
        request.setFromCandidateId(45L);
        request.setToUserId(77L);
        request.setRequestId("request-1");
        request.setReason("原审批人已离职");

        assertThatThrownBy(() -> service.reassign(31L, request, 9L, "alice"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("请求ID")
                .hasMessageContaining("其他改派内容");
        verifyNoInteractions(directoryMapper);
    }

    @Test
    @DisplayName("并发唯一冲突重读后仍拒绝不同改派原因")
    void shouldRejectDifferentPayloadAfterConcurrentClaimConflict()
    {
        ApprovalTask task = activeTask();
        ApprovalInstance instance = activeInstance();
        ApprovalTaskCandidate source = sourceCandidate();
        ApprovalDirectoryUser target = new ApprovalDirectoryUser();
        target.setUserId(77L);
        when(runtimeMapper.selectTaskById(31L)).thenReturn(task);
        when(runtimeService.requireInstance(88L)).thenReturn(instance);
        when(runtimeMapper.selectActionByKey(
                ApprovalRuntimeService.actionKey(88L, ACTION_REASSIGN,
                        "request-1"))).thenReturn(null);
        when(runtimeMapper.selectCandidateById(45L)).thenReturn(source);
        when(runtimeMapper.selectCandidatesByTaskId(31L))
                .thenReturn(List.of(source));
        when(permissionPolicy.requiredPermission("OA_PURCHASE"))
                .thenReturn("oa:todo:approve");
        when(directoryMapper.selectActiveUserById(77L, "oa:todo:approve"))
                .thenReturn(target);
        doThrow(new DuplicateKeyException("并发唯一冲突"))
                .when(runtimeService).insertAction(any(), any(), any(),
                        any(), any(), any(), any(), any(), any(), any(), any());
        ApprovalActionLog winner = existingAction("与当前请求不同的原因");
        winner.setActionType(ACTION_REASSIGN);
        winner.setCandidateId(45L);
        when(runtimeMapper.selectActionByKeyForShare(
                ApprovalRuntimeService.actionKey(88L, ACTION_REASSIGN,
                        "request-1"))).thenReturn(winner);
        ApprovalReassignRequest request = new ApprovalReassignRequest();
        request.setFromCandidateId(45L);
        request.setToUserId(77L);
        request.setRequestId("request-1");
        request.setReason("当前请求原因");

        assertThatThrownBy(() -> service.reassign(31L, request, 9L, "alice"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("请求ID")
                .hasMessageContaining("其他改派内容");
        verify(runtimeMapper, never()).updateCandidateWithLock(
                any(), any(), any());
    }

    private void arrangeExisting(ApprovalTask task, ApprovalInstance instance,
            ApprovalActionLog existing)
    {
        when(runtimeMapper.selectTaskById(31L)).thenReturn(task);
        when(runtimeService.requireInstance(88L)).thenReturn(instance);
        when(runtimeMapper.selectActionByKey(
                ApprovalRuntimeService.actionKey(88L, ACTION_APPROVE, "request-1")))
                .thenReturn(existing);
    }

    private static ApprovalTaskActionRequest request(String reason)
    {
        ApprovalTaskActionRequest request = new ApprovalTaskActionRequest();
        request.setRequestId("request-1");
        request.setReason(reason);
        return request;
    }

    private static ApprovalTask task()
    {
        ApprovalTask task = new ApprovalTask();
        task.setTaskId(31L);
        task.setInstanceId(88L);
        return task;
    }

    private static ApprovalTask activeTask()
    {
        ApprovalTask task = task();
        task.setNodeId(13L);
        task.setTaskStatus(TASK_PENDING);
        return task;
    }

    private static ApprovalInstance instance()
    {
        ApprovalInstance instance = new ApprovalInstance();
        instance.setInstanceId(88L);
        return instance;
    }

    private static ApprovalInstance activeInstance()
    {
        ApprovalInstance instance = instance();
        instance.setBusinessCode("OA_PURCHASE");
        instance.setStatus(INSTANCE_RUNNING);
        instance.setCurrentNodeId(13L);
        return instance;
    }

    private static ApprovalTaskCandidate sourceCandidate()
    {
        ApprovalTaskCandidate candidate = new ApprovalTaskCandidate();
        candidate.setCandidateId(45L);
        candidate.setTaskId(31L);
        candidate.setUserId(9L);
        candidate.setCandidateStatus(CANDIDATE_PENDING);
        candidate.setLockVersion(0L);
        return candidate;
    }

    private static ApprovalActionLog existingAction(String reason)
    {
        ApprovalActionLog action = new ApprovalActionLog();
        action.setTaskId(31L);
        action.setOperatorUserId(9L);
        action.setActionType(ACTION_APPROVE);
        action.setActionReason(reason);
        return action;
    }
}
