package com.erp.approval.service;

import static com.erp.approval.constant.ApprovalRuntimeConstants.ACTION_REASSIGN;
import static com.erp.approval.constant.ApprovalRuntimeConstants.CANDIDATE_PENDING;
import static com.erp.approval.constant.ApprovalRuntimeConstants.INSTANCE_RUNNING;
import static com.erp.approval.constant.ApprovalRuntimeConstants.TASK_PENDING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import com.erp.approval.candidate.ApprovalDirectoryUser;
import com.erp.approval.domain.ApprovalActionLog;
import com.erp.approval.domain.ApprovalInstance;
import com.erp.approval.domain.ApprovalTask;
import com.erp.approval.domain.ApprovalTaskCandidate;
import com.erp.approval.domain.dto.ApprovalReassignRequest;
import com.erp.approval.guard.ApprovalBusinessActionGuardRegistry;
import com.erp.approval.mapper.ApprovalCandidateDirectoryMapper;
import com.erp.approval.mapper.ApprovalDefinitionMapper;
import com.erp.approval.mapper.ApprovalRuntimeMapper;

@DisplayName("审批动作并发幂等")
class ApprovalTaskServiceConcurrencyTest
{
    @Test
    @DisplayName("两个同requestId改派请求收敛到同一候选人")
    void identicalConcurrentRequestsShouldConverge() throws Exception
    {
        ApprovalRuntimeMapper runtimeMapper = mock(ApprovalRuntimeMapper.class);
        ApprovalCandidateDirectoryMapper directoryMapper =
                mock(ApprovalCandidateDirectoryMapper.class);
        ApprovalPermissionPolicy permissionPolicy =
                mock(ApprovalPermissionPolicy.class);
        ApprovalRuntimeService runtimeService = mock(ApprovalRuntimeService.class);
        ApprovalTaskService service = new ApprovalTaskService(runtimeMapper,
                mock(ApprovalDefinitionMapper.class), directoryMapper,
                permissionPolicy, runtimeService,
                new ApprovalBusinessActionGuardRegistry(List.of()));
        String actionKey = ApprovalRuntimeService.actionKey(88L,
                ACTION_REASSIGN, "request-1");
        CyclicBarrier bothObservedNoAction = new CyclicBarrier(2);
        CountDownLatch winnerPersisted = new CountDownLatch(1);
        AtomicInteger claims = new AtomicInteger();
        AtomicReference<ApprovalTaskCandidate> persistedTarget =
                new AtomicReference<>();

        when(runtimeMapper.selectTaskById(31L)).thenAnswer(ignored -> task());
        when(runtimeService.requireInstance(88L))
                .thenAnswer(ignored -> instance());
        when(runtimeMapper.selectActionByKey(actionKey)).thenAnswer(ignored ->
        {
            bothObservedNoAction.await(5, TimeUnit.SECONDS);
            return null;
        });
        when(runtimeMapper.selectCandidateById(45L))
                .thenAnswer(ignored -> sourceCandidate());
        when(runtimeMapper.selectCandidatesByTaskId(31L))
                .thenAnswer(ignored -> List.of(sourceCandidate()));
        when(permissionPolicy.requiredPermission("OA_PURCHASE"))
                .thenReturn("oa:todo:approve");
        when(directoryMapper.selectActiveUserById(77L, "oa:todo:approve"))
                .thenReturn(targetUser());
        doAnswer(ignored ->
        {
            if (claims.incrementAndGet() == 1)
            {
                return null;
            }
            if (!winnerPersisted.await(5, TimeUnit.SECONDS))
            {
                throw new AssertionError("获胜事务未在期限内持久化");
            }
            throw new DuplicateKeyException("模拟action_key唯一冲突");
        }).when(runtimeService).insertAction(any(ApprovalInstance.class),
                any(ApprovalTask.class), any(ApprovalTaskCandidate.class),
                anyString(), anyLong(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString());
        when(runtimeMapper.updateCandidateWithLock(
                any(ApprovalTaskCandidate.class), eq(0L),
                eq(CANDIDATE_PENDING))).thenReturn(1);
        when(runtimeMapper.insertCandidate(any(ApprovalTaskCandidate.class)))
                .thenAnswer(invocation ->
                {
                    ApprovalTaskCandidate target = invocation.getArgument(0);
                    target.setCandidateId(501L);
                    persistedTarget.set(target);
                    winnerPersisted.countDown();
                    return 1;
                });
        when(runtimeMapper.selectActionByKeyForShare(actionKey))
                .thenAnswer(ignored -> existingAction());
        when(runtimeMapper.selectReassignedCandidateForShare(31L, 45L, 77L))
                .thenAnswer(ignored -> persistedTarget.get());

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try
        {
            Future<ApprovalTaskCandidate> first = executor.submit(() ->
                    service.reassign(31L, request(), 9L, "alice"));
            Future<ApprovalTaskCandidate> second = executor.submit(() ->
                    service.reassign(31L, request(), 9L, "alice"));

            ApprovalTaskCandidate firstResult = first.get(10, TimeUnit.SECONDS);
            ApprovalTaskCandidate secondResult = second.get(10, TimeUnit.SECONDS);

            assertThat(firstResult.getCandidateId()).isEqualTo(501L);
            assertThat(secondResult.getCandidateId()).isEqualTo(501L);
            assertThat(firstResult.getUserId()).isEqualTo(77L);
            assertThat(secondResult.getUserId()).isEqualTo(77L);
            assertThat(claims).hasValue(2);
            verify(runtimeMapper, times(1)).insertCandidate(
                    any(ApprovalTaskCandidate.class));
            verify(runtimeMapper, times(1)).updateCandidateWithLock(
                    any(ApprovalTaskCandidate.class), eq(0L),
                    eq(CANDIDATE_PENDING));
            verify(runtimeMapper).selectActionByKeyForShare(actionKey);
        }
        finally
        {
            executor.shutdownNow();
        }
    }

    private static ApprovalReassignRequest request()
    {
        ApprovalReassignRequest request = new ApprovalReassignRequest();
        request.setFromCandidateId(45L);
        request.setToUserId(77L);
        request.setReason("原审批人请假");
        request.setRequestId("request-1");
        return request;
    }

    private static ApprovalTask task()
    {
        ApprovalTask task = new ApprovalTask();
        task.setTaskId(31L);
        task.setInstanceId(88L);
        task.setNodeId(13L);
        task.setTaskStatus(TASK_PENDING);
        return task;
    }

    private static ApprovalInstance instance()
    {
        ApprovalInstance instance = new ApprovalInstance();
        instance.setInstanceId(88L);
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
        candidate.setInstanceId(88L);
        candidate.setUserId(9L);
        candidate.setCandidateOrder(1);
        candidate.setCandidateStatus(CANDIDATE_PENDING);
        candidate.setLockVersion(0L);
        return candidate;
    }

    private static ApprovalDirectoryUser targetUser()
    {
        ApprovalDirectoryUser user = new ApprovalDirectoryUser();
        user.setUserId(77L);
        user.setUserName("bob");
        user.setDeptId(6L);
        user.setDeptName("区域经营部");
        return user;
    }

    private static ApprovalActionLog existingAction()
    {
        ApprovalActionLog action = new ApprovalActionLog();
        action.setTaskId(31L);
        action.setCandidateId(45L);
        action.setOperatorUserId(9L);
        action.setActionType(ACTION_REASSIGN);
        action.setActionReason("原审批人请假");
        return action;
    }
}
