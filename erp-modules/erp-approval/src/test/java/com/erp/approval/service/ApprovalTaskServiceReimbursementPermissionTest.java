package com.erp.approval.service;

import static com.erp.approval.constant.ApprovalRuntimeConstants.CANDIDATE_PENDING;
import static com.erp.approval.constant.ApprovalRuntimeConstants.ACTION_APPROVE;
import static com.erp.approval.constant.ApprovalRuntimeConstants.INSTANCE_RUNNING;
import static com.erp.approval.constant.ApprovalRuntimeConstants.TASK_PENDING;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import com.erp.approval.candidate.ApprovalDirectoryUser;
import com.erp.approval.constant.ApprovalBusinessCodes;
import com.erp.approval.domain.ApprovalInstance;
import com.erp.approval.domain.ApprovalTask;
import com.erp.approval.domain.ApprovalTaskCandidate;
import com.erp.approval.domain.dto.ApprovalTaskActionRequest;
import com.erp.approval.guard.ApprovalBusinessActionGuardRegistry;
import com.erp.approval.mapper.ApprovalCandidateDirectoryMapper;
import com.erp.approval.mapper.ApprovalDefinitionMapper;
import com.erp.approval.mapper.ApprovalRuntimeMapper;
import com.erp.common.core.constant.SecurityConstants;
import com.erp.common.core.context.SecurityContextHolder;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.core.exception.auth.NotPermissionException;
import com.erp.system.api.model.LoginUser;

@DisplayName("报销审批动作节点权限")
class ApprovalTaskServiceReimbursementPermissionTest
{
    private static final String BASE = "oa:reimbursement:approve";
    private static final String FINANCE =
            "oa:reimbursement:finance:approve";

    private ApprovalRuntimeMapper runtimeMapper;
    private ApprovalCandidateDirectoryMapper directoryMapper;
    private ApprovalRuntimeService runtimeService;
    private ApprovalBusinessActionGuardRegistry actionGuards;
    private ApprovalTaskService service;

    @BeforeEach
    void setUp()
    {
        runtimeMapper = mock(ApprovalRuntimeMapper.class);
        directoryMapper = mock(ApprovalCandidateDirectoryMapper.class);
        runtimeService = mock(ApprovalRuntimeService.class);
        actionGuards = mock(ApprovalBusinessActionGuardRegistry.class);
        service = new ApprovalTaskService(runtimeMapper,
                mock(ApprovalDefinitionMapper.class), directoryMapper,
                new ApprovalPermissionPolicy(), runtimeService,
                actionGuards);
        arrangeFinanceTask();
    }

    @AfterEach
    void tearDown()
    {
        SecurityContextHolder.remove();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    @DisplayName("只有基础审批权限不能处理财务节点")
    void shouldRejectBasePermissionAtFinanceNode()
    {
        loginWith(BASE);

        assertThatThrownBy(() -> service.approve(
                31L, request(), 9L, "approver"))
                .isInstanceOf(NotPermissionException.class)
                .hasMessageContaining(FINANCE);
        verify(directoryMapper, never()).selectActiveUserById(
                any(), any());
    }

    @Test
    @DisplayName("财务节点校验专用权限及实时有效人员目录")
    void shouldUseFinancePermissionForCurrentCandidate()
    {
        loginWith(FINANCE);
        ApprovalDirectoryUser active = new ApprovalDirectoryUser();
        active.setUserId(9L);
        when(directoryMapper.selectActiveUserById(9L, FINANCE))
                .thenReturn(active);
        doThrow(new ServiceException("permission-passed"))
                .when(runtimeService).insertAction(
                        any(), any(), any(), any(), any(), any(), any(),
                        any(), any(), any(), any());

        assertThatThrownBy(() -> service.approve(
                31L, request(), 9L, "approver"))
                .isInstanceOf(ServiceException.class)
                .hasMessage("permission-passed");
        verify(directoryMapper).selectActiveUserById(9L, FINANCE);
        verify(actionGuards).validate(any(ApprovalInstance.class),
                eq(ACTION_APPROVE), eq(9L));
    }

    @Test
    @DisplayName("业务证据守卫拒绝后不得写入审批动作")
    void shouldValidateBusinessEvidenceBeforePersistingApproveAction()
    {
        loginWith(FINANCE);
        ApprovalDirectoryUser active = new ApprovalDirectoryUser();
        active.setUserId(9L);
        when(directoryMapper.selectActiveUserById(9L, FINANCE))
                .thenReturn(active);
        doThrow(new ServiceException("evidence-blocked"))
                .when(actionGuards).validate(any(ApprovalInstance.class),
                        eq(ACTION_APPROVE), eq(9L));

        assertThatThrownBy(() -> service.approve(
                31L, request(), 9L, "approver"))
                .isInstanceOf(ServiceException.class)
                .hasMessage("evidence-blocked");
        verify(runtimeService, never()).insertAction(
                any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any());
    }

    private void arrangeFinanceTask()
    {
        ApprovalTask task = new ApprovalTask();
        task.setTaskId(31L);
        task.setInstanceId(88L);
        task.setNodeId(13L);
        task.setNodeOrder(2);
        task.setTaskStatus(TASK_PENDING);
        task.setApprovalMode("ANY_ONE");
        task.setRequiredCount(1);
        task.setCompletedCount(0);
        task.setLockVersion(0L);

        ApprovalInstance instance = new ApprovalInstance();
        instance.setInstanceId(88L);
        instance.setBusinessCode(
                ApprovalBusinessCodes.OA_REIMBURSEMENT);
        instance.setStatus(INSTANCE_RUNNING);
        instance.setCurrentNodeId(13L);
        instance.setLockVersion(0L);

        ApprovalTaskCandidate candidate =
                new ApprovalTaskCandidate();
        candidate.setCandidateId(45L);
        candidate.setTaskId(31L);
        candidate.setInstanceId(88L);
        candidate.setUserId(9L);
        candidate.setCandidateStatus(CANDIDATE_PENDING);
        candidate.setCandidateSourceCode(FINANCE);
        candidate.setLockVersion(0L);

        when(runtimeMapper.selectTaskById(31L)).thenReturn(task);
        when(runtimeService.requireInstance(88L)).thenReturn(instance);
        when(runtimeMapper.selectActionByKey(any())).thenReturn(null);
        when(runtimeMapper.selectPendingCandidate(31L, 9L))
                .thenReturn(candidate);
    }

    private ApprovalTaskActionRequest request()
    {
        ApprovalTaskActionRequest request =
                new ApprovalTaskActionRequest();
        request.setRequestId("request-finance-1");
        request.setReason("同意");
        return request;
    }

    private void loginWith(String permission)
    {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(SecurityConstants.AUTHORIZATION_HEADER,
                "Bearer test-token");
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(request));
        SecurityContextHolder.setUserId("9");
        LoginUser loginUser = new LoginUser();
        loginUser.setUserid(9L);
        loginUser.setUsername("approver");
        loginUser.setPermissions(Set.of(permission));
        loginUser.setRoles(Set.of());
        SecurityContextHolder.set(SecurityConstants.LOGIN_USER,
                loginUser);
    }
}
