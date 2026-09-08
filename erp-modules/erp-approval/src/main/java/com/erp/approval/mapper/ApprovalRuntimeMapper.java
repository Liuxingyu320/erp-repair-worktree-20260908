package com.erp.approval.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.erp.approval.domain.ApprovalActionLog;
import com.erp.approval.domain.ApprovalCallbackOutbox;
import com.erp.approval.domain.ApprovalInstance;
import com.erp.approval.domain.ApprovalTask;
import com.erp.approval.domain.ApprovalTaskCandidate;
import com.erp.approval.domain.vo.ApprovalTodoCountRow;
import com.erp.approval.domain.vo.ApprovalTodoAccessRule;
import com.erp.approval.domain.vo.ApprovalTodoRow;
import com.erp.common.core.domain.todo.TodoQuery;

public interface ApprovalRuntimeMapper
{
    ApprovalInstance selectInstanceById(Long instanceId);

    ApprovalInstance selectInstanceByIdForShare(Long instanceId);

    ApprovalInstance selectInstanceByIdempotencyKey(String idempotencyKey);

    ApprovalInstance selectInstanceByBusinessRound(
            @Param("businessCode") String businessCode,
            @Param("businessId") String businessId,
            @Param("businessRound") Integer businessRound);

    List<ApprovalInstance> selectInstanceList(ApprovalInstance filter);

    int countInstanceParticipant(@Param("instanceId") Long instanceId,
            @Param("userId") Long userId);

    ApprovalTask selectTaskById(Long taskId);

    ApprovalTask selectNextWaitingTask(@Param("instanceId") Long instanceId,
            @Param("afterOrder") Integer afterOrder);

    List<ApprovalTask> selectTasksByInstanceId(Long instanceId);

    List<ApprovalTaskCandidate> selectCandidatesByTaskId(Long taskId);

    List<ApprovalTaskCandidate> selectCandidatesByInstanceId(Long instanceId);

    ApprovalTaskCandidate selectPendingCandidate(
            @Param("taskId") Long taskId, @Param("userId") Long userId);

    ApprovalTaskCandidate selectCandidateById(Long candidateId);

    ApprovalTaskCandidate selectReassignedCandidateForShare(
            @Param("taskId") Long taskId,
            @Param("fromCandidateId") Long fromCandidateId,
            @Param("toUserId") Long toUserId);

    List<ApprovalActionLog> selectActionsByInstanceId(Long instanceId);

    ApprovalActionLog selectActionByKey(String actionKey);

    ApprovalActionLog selectActionByKeyForShare(String actionKey);

    List<ApprovalCallbackOutbox> selectCallbacksByInstanceId(Long instanceId);

    ApprovalCallbackOutbox selectCallbackById(Long outboxId);

    List<ApprovalCallbackOutbox> selectDispatchableCallbacks(
            @Param("limit") Integer limit);

    List<ApprovalTodoRow> selectTodoRows(@Param("userId") Long userId,
            @Param("query") TodoQuery query,
            @Param("accessRules") List<ApprovalTodoAccessRule> accessRules);

    List<ApprovalTodoCountRow> selectTodoTypeCounts(
            @Param("userId") Long userId,
            @Param("query") TodoQuery query,
            @Param("accessRules") List<ApprovalTodoAccessRule> accessRules);

    List<ApprovalTodoRow> selectRecentTodoRows(@Param("userId") Long userId,
            @Param("query") TodoQuery query,
            @Param("accessRules") List<ApprovalTodoAccessRule> accessRules);

    int insertInstance(ApprovalInstance instance);

    int insertTask(ApprovalTask task);

    int insertCandidate(ApprovalTaskCandidate candidate);

    int insertAction(ApprovalActionLog action);

    int insertCallback(ApprovalCallbackOutbox callback);

    int setRootInstanceIdIfNull(Long instanceId);

    int updateInstanceWithLock(@Param("instance") ApprovalInstance instance,
            @Param("expectedVersion") Long expectedVersion,
            @Param("expectedStatus") String expectedStatus);

    int updateTaskWithLock(@Param("task") ApprovalTask task,
            @Param("expectedVersion") Long expectedVersion,
            @Param("expectedStatus") String expectedStatus);

    int updateCandidateWithLock(
            @Param("candidate") ApprovalTaskCandidate candidate,
            @Param("expectedVersion") Long expectedVersion,
            @Param("expectedStatus") String expectedStatus);

    int cancelOtherCandidates(@Param("taskId") Long taskId,
            @Param("exceptCandidateId") Long exceptCandidateId,
            @Param("reason") String reason, @Param("updateBy") String updateBy);

    int cancelOpenCandidatesByInstance(@Param("instanceId") Long instanceId,
            @Param("reason") String reason, @Param("updateBy") String updateBy);

    int cancelOpenTasksByInstance(@Param("instanceId") Long instanceId,
            @Param("reason") String reason, @Param("updateBy") String updateBy);

    int claimCallback(@Param("outboxId") Long outboxId,
            @Param("expectedVersion") Long expectedVersion,
            @Param("worker") String worker,
            @Param("lockUntil") java.util.Date lockUntil);

    int markCallbackSucceeded(@Param("outboxId") Long outboxId,
            @Param("expectedVersion") Long expectedVersion,
            @Param("updateBy") String updateBy);

    int markCallbackFailure(@Param("outboxId") Long outboxId,
            @Param("expectedVersion") Long expectedVersion,
            @Param("status") String status,
            @Param("retryCount") Integer retryCount,
            @Param("nextRetryTime") java.util.Date nextRetryTime,
            @Param("errorCode") String errorCode,
            @Param("errorMessage") String errorMessage,
            @Param("updateBy") String updateBy);

    int replayCallback(@Param("outboxId") Long outboxId,
            @Param("expectedVersion") Long expectedVersion,
            @Param("updateBy") String updateBy);
}
