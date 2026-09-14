package com.erp.approval.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import com.erp.approval.candidate.ApprovalDirectoryUser;
import com.erp.approval.controller.ApprovalTaskController;
import com.erp.approval.domain.ApprovalInstance;
import com.erp.approval.domain.ApprovalTask;
import com.erp.approval.domain.ApprovalTaskCandidate;
import com.erp.approval.domain.dto.ApprovalReassignRequest;
import com.erp.approval.guard.ApprovalBusinessActionGuardRegistry;
import com.erp.approval.mapper.ApprovalCandidateDirectoryMapper;
import com.erp.approval.mapper.ApprovalDefinitionMapper;
import com.erp.approval.mapper.ApprovalRuntimeMapper;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.security.annotation.RequiresPermissions;
import com.erp.common.security.auth.AuthUtil;
import com.erp.common.security.utils.SecurityUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class ApprovalReassignOptionsTest
{
    private final ApprovalRuntimeMapper runtime = mock(ApprovalRuntimeMapper.class);
    private final ApprovalCandidateDirectoryMapper directory = mock(ApprovalCandidateDirectoryMapper.class);
    private final ApprovalPermissionPolicy permissions = mock(ApprovalPermissionPolicy.class);
    private final ApprovalRuntimeService runtimeService = mock(ApprovalRuntimeService.class);
    private final ApprovalTaskService service = new ApprovalTaskService(runtime,
            mock(ApprovalDefinitionMapper.class), directory, permissions,
            runtimeService, new ApprovalBusinessActionGuardRegistry(List.of()));
    private MockedStatic<AuthUtil> auth;
    private MockedStatic<SecurityUtils> security;
    private ApprovalTask task;
    private ApprovalInstance instance;
    private ApprovalTaskCandidate source;

    @BeforeEach
    void setUp()
    {
        auth = mockStatic(AuthUtil.class);
        security = mockStatic(SecurityUtils.class);
        task = new ApprovalTask(); task.setTaskId(31L); task.setInstanceId(88L);
        task.setNodeId(4L); task.setTaskStatus("PENDING");
        instance = new ApprovalInstance(); instance.setInstanceId(88L);
        instance.setCurrentNodeId(4L); instance.setStatus("RUNNING"); instance.setBusinessCode("OA_PURCHASE");
        source = new ApprovalTaskCandidate(); source.setCandidateId(45L); source.setTaskId(31L);
        source.setUserId(6L); source.setCandidateStatus("PENDING");
        when(runtime.selectTaskById(31L)).thenReturn(task);
        when(runtimeService.requireInstance(88L)).thenReturn(instance);
        when(runtime.countInstanceParticipant(88L, 10L)).thenReturn(1);
        when(runtime.selectCandidateById(45L)).thenReturn(source);
        when(permissions.requiredPermission("OA_PURCHASE")).thenReturn("oa:todo:approve");
    }

    @AfterEach
    void tearDown() { auth.close(); security.close(); }

    @Test
    void searchesOnlyTheServerResolvedBusinessPermissionAndReturnsBoundedPages()
    {
        List<ApprovalDirectoryUser> users = IntStream.range(0, 21).mapToObj(i -> person(100L + i)).toList();
        when(directory.selectReassignOptions(31L, "oa:todo:approve", "采购部", 20, 21)).thenReturn(users);
        Map<String, Object> result = service.reassignOptions(31L, 45L, " 采购部 ", 2, 10L);
        assertThat((List<?>) result.get("rows")).hasSize(20);
        assertThat(result).containsEntry("hasMore", true).containsEntry("taskId", "31")
                .containsEntry("instanceId", "88").containsEntry("fromCandidateId", "45");
        verify(directory).selectReassignOptions(31L, "oa:todo:approve", "采购部", 20, 21);
    }

    @Test
    void usesAllowedSourcePermissionRatherThanAcceptingClientSuppliedPermission()
    {
        source.setCandidateSourceCode("oa:purchase:approve");
        when(permissions.isCandidatePermissionAllowed("OA_PURCHASE", "oa:purchase:approve")).thenReturn(true);
        when(directory.selectReassignOptions(31L, "oa:purchase:approve", "", 0, 21)).thenReturn(List.of());
        assertThat(service.reassignOptions(31L, 45L, "", 1, 10L)).containsEntry("hasMore", false);
        verify(directory).selectReassignOptions(31L, "oa:purchase:approve", "", 0, 21);
    }

    @Test
    void rejectsSourceFromAnotherTaskOrAlreadyProcessedBeforeDirectoryRead()
    {
        source.setTaskId(32L);
        assertThatThrownBy(() -> service.reassignOptions(31L, 45L, "", 1, 10L)).hasMessageContaining("源候选人");
        source.setTaskId(31L); source.setCandidateStatus("REASSIGNED");
        assertThatThrownBy(() -> service.reassignOptions(31L, 45L, "", 1, 10L)).hasMessageContaining("源候选人");
        verifyNoInteractions(directory);
    }

    @Test
    void rejectsFinishedTaskOrDifferentCurrentNodeBeforeDirectoryRead()
    {
        task.setTaskStatus("APPROVED");
        assertThatThrownBy(() -> service.reassignOptions(31L, 45L, "", 1, 10L)).hasMessageContaining("活动节点");
        task.setTaskStatus("PENDING"); instance.setCurrentNodeId(5L);
        assertThatThrownBy(() -> service.reassignOptions(31L, 45L, "", 1, 10L)).hasMessageContaining("活动节点");
        verifyNoInteractions(directory);
    }

    @Test
    void preservesInstanceVisibilityAndReassignEndpointPermission() throws Exception
    {
        when(runtime.countInstanceParticipant(88L, 10L)).thenReturn(0);
        assertThatThrownBy(() -> service.reassignOptions(31L, 45L, "", 1, 10L)).hasMessageContaining("无权查看");
        verifyNoInteractions(directory);
        RequiresPermissions annotation = ApprovalTaskController.class
                .getMethod("reassignOptions", Long.class, Long.class, String.class, int.class)
                .getAnnotation(RequiresPermissions.class);
        assertThat(annotation.value()).containsExactly("approval:task:reassign");
        auth.when(() -> AuthUtil.hasPermi("approval:instance:query")).thenReturn(true);
        when(directory.selectReassignOptions(31L, "oa:todo:approve", "", 0, 21)).thenReturn(List.of());
        assertThat(service.reassignOptions(31L, 45L, "", 1, 10L)).containsEntry("hasMore", false);
    }

    @Test
    void rejectsUnboundedSearchAndMissingBusinessPermission()
    {
        assertThatThrownBy(() -> service.reassignOptions(31L, 45L, "x".repeat(65), 1, 10L)).hasMessageContaining("参数");
        assertThatThrownBy(() -> service.reassignOptions(31L, 45L, "", 0, 10L)).hasMessageContaining("参数");
        assertThatThrownBy(() -> service.reassignOptions(31L, 45L, "", 1001, 10L)).hasMessageContaining("参数");
        when(permissions.requiredPermission("OA_PURCHASE")).thenReturn("");
        assertThatThrownBy(() -> service.reassignOptions(31L, 45L, "", 1, 10L)).hasMessageContaining("未配置");
        verifyNoInteractions(directory);
    }

    @Test
    void selectionNeverBypassesWriteTimeEligibilityRecheck()
    {
        when(directory.selectReassignOptions(31L, "oa:todo:approve", "", 0, 21)).thenReturn(List.of(person(77L)));
        service.reassignOptions(31L, 45L, "", 1, 10L);
        when(runtime.selectCandidatesByTaskId(31L)).thenReturn(List.of(source));
        when(directory.selectActiveUserById(77L, "oa:todo:approve")).thenReturn(null);
        ApprovalReassignRequest request = new ApprovalReassignRequest();
        request.setFromCandidateId(45L); request.setToUserId(77L); request.setReason("原审批人请假"); request.setRequestId("selection-1");
        assertThatThrownBy(() -> service.reassign(31L, request, 10L, "admin"))
                .isInstanceOf(ServiceException.class).hasMessageContaining("无业务审批权限");
        verify(runtime, never()).insertCandidate(any());
    }

    @Test
    void candidateIdentityIsSerializedLosslesslyWithAccountAndDepartment() throws Exception
    {
        ApprovalDirectoryUser person = person(Long.MAX_VALUE);
        person.setUserName("张三"); person.setAccountName("zhangsan.a"); person.setDeptName("采购部");
        String json = new ObjectMapper().writeValueAsString(person);
        assertThat(json).contains("\"userId\":\"9223372036854775807\"", "zhangsan.a", "采购部");
    }

    private static ApprovalDirectoryUser person(Long id)
    {
        ApprovalDirectoryUser user = new ApprovalDirectoryUser(); user.setUserId(id); return user;
    }
}
