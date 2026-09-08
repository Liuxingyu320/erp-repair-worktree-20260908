package com.erp.inventory.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import com.erp.common.core.context.SecurityContextHolder;
import com.erp.common.core.exception.ServiceException;
import com.erp.inventory.domain.InvStockCheck;
import com.erp.inventory.domain.InvStockCheckApprovalInstance;
import com.erp.inventory.domain.InvStockCheckApprovalTask;
import com.erp.inventory.domain.InvStockCheckDetail;
import com.erp.inventory.domain.dto.InvStockCheckApprovalRequest;
import com.erp.inventory.domain.vo.InvStockCheckAdjustmentResult;
import com.erp.inventory.domain.vo.InvStockCheckAdjustmentEntry;
import com.erp.inventory.domain.vo.InvStockCheckSnapshotChange;
import com.erp.inventory.mapper.InvDeptScopeMapper;
import com.erp.inventory.mapper.InvStockCheckApprovalCandidateMapper;
import com.erp.inventory.mapper.InvStockCheckApprovalInstanceMapper;
import com.erp.inventory.mapper.InvStockCheckApprovalTaskMapper;
import com.erp.inventory.mapper.InvStockCheckDetailMapper;
import com.erp.inventory.mapper.InvStockCheckMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

@DisplayName("盘点审批服务")
class InvStockCheckApprovalServiceImplTest
{
    @AfterEach
    void tearDown()
    {
        SecurityContextHolder.remove();
    }

    @Test
    @DisplayName("有差异提交时保存运营总监候选人和明细快照")
    void shouldCreatePendingApprovalWithCandidateSnapshot()
    {
        SecurityContextHolder.setUserId("7");
        SecurityContextHolder.setUserName("counter");
        InvStockCheckApprovalInstanceMapper instanceMapper = mock(InvStockCheckApprovalInstanceMapper.class);
        InvStockCheckApprovalTaskMapper taskMapper = mock(InvStockCheckApprovalTaskMapper.class);
        InvStockCheckApprovalCandidateMapper candidateMapper = mock(InvStockCheckApprovalCandidateMapper.class);
        InvStockCheckApprovalServiceImpl service = service(instanceMapper, taskMapper, candidateMapper);
        when(candidateMapper.selectOperationsDirectorCandidates(20L))
                .thenReturn(List.of(Map.of("userId", 9L, "userName", "ops")));
        doAnswer(invocation -> {
            InvStockCheckApprovalInstance instance = invocation.getArgument(0);
            instance.setInstanceId(41L);
            return 1;
        }).when(instanceMapper).insertInstance(any(InvStockCheckApprovalInstance.class));

        InvStockCheck check = check(1001L, 20L);
        check.setApprovalRound(2);
        InvStockCheckApprovalInstance instance = service.createPendingApproval(
                check, List.of(detail(501L, 101L, "测试茶", "10", "8")));

        assertThat(instance.getInstanceId()).isEqualTo(41L);
        assertThat(instance.getRoundNo()).isEqualTo(3);
        assertThat(instance.getStatus()).isEqualTo("running");
        assertThat(instance.getLossItemCount()).isEqualTo(1);
        assertThat(instance.getDetailSnapshot()).contains("测试茶", "actualQty", "8");
        ArgumentCaptor<InvStockCheckApprovalTask> taskCaptor =
                ArgumentCaptor.forClass(InvStockCheckApprovalTask.class);
        verify(taskMapper).insertTask(taskCaptor.capture());
        assertThat(taskCaptor.getValue().getCandidateUserIds()).isEqualTo("9");
        assertThat(taskCaptor.getValue().getCandidateUserNames()).isEqualTo("ops");
        assertThat(taskCaptor.getValue().getStatus()).isEqualTo("pending");
    }

    @Test
    @DisplayName("没有有效运营总监时阻止差异盘点提交")
    void shouldRejectSubmitWithoutOperationsDirector()
    {
        SecurityContextHolder.setUserId("7");
        SecurityContextHolder.setUserName("counter");
        InvStockCheckApprovalInstanceMapper instanceMapper = mock(InvStockCheckApprovalInstanceMapper.class);
        InvStockCheckApprovalTaskMapper taskMapper = mock(InvStockCheckApprovalTaskMapper.class);
        InvStockCheckApprovalCandidateMapper candidateMapper = mock(InvStockCheckApprovalCandidateMapper.class);
        InvStockCheckApprovalServiceImpl service = service(instanceMapper, taskMapper, candidateMapper);
        when(candidateMapper.selectOperationsDirectorCandidates(20L)).thenReturn(List.of());

        assertThatThrownBy(() -> service.createPendingApproval(
                check(1001L, 20L), List.of(detail(501L, 101L, "测试茶", "10", "8"))))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("未配置具有盘点审批权限的运营总监");
        verify(instanceMapper, never()).insertInstance(any());
        verify(taskMapper, never()).insertTask(any());
    }

    @Test
    @DisplayName("运营总监审批本人提交的盘点时记录自审并完成库存调整")
    void shouldRecordSelfApprovalAndCompleteAdjustment()
    {
        SecurityContextHolder.setUserId("9");
        SecurityContextHolder.setUserName("ops");
        InvStockCheckApprovalInstanceMapper instanceMapper = mock(InvStockCheckApprovalInstanceMapper.class);
        InvStockCheckApprovalTaskMapper taskMapper = mock(InvStockCheckApprovalTaskMapper.class);
        InvStockCheckApprovalCandidateMapper candidateMapper = mock(InvStockCheckApprovalCandidateMapper.class);
        InvStockCheckMapper checkMapper = mock(InvStockCheckMapper.class);
        InvStockCheckDetailMapper detailMapper = mock(InvStockCheckDetailMapper.class);
        InvStockCheckAdjustmentService adjustmentService = mock(InvStockCheckAdjustmentService.class);
        InvDeptScopeMapper deptScopeMapper = mock(InvDeptScopeMapper.class);
        InvStockCheckApprovalServiceImpl service = service(instanceMapper, taskMapper, candidateMapper);
        ReflectionTestUtils.setField(service, "checkMapper", checkMapper);
        ReflectionTestUtils.setField(service, "detailMapper", detailMapper);
        ReflectionTestUtils.setField(service, "adjustmentService", adjustmentService);
        ReflectionTestUtils.setField(service, "deptScopeMapper", deptScopeMapper);

        InvStockCheck check = check(1001L, 20L);
        check.setStatus("pending_approval");
        InvStockCheckApprovalInstance instance = new InvStockCheckApprovalInstance();
        instance.setInstanceId(41L);
        instance.setCheckId(1001L);
        instance.setStatus("running");
        instance.setSubmittedUserId(9L);
        InvStockCheckApprovalTask task = new InvStockCheckApprovalTask();
        task.setTaskId(51L);
        task.setInstanceId(41L);
        task.setCheckId(1001L);
        task.setStatus("pending");
        task.setCandidateUserIds("9");
        List<InvStockCheckDetail> details = List.of(detail(501L, 101L, "测试茶", "10", "8"));

        when(checkMapper.selectInvStockCheckByIdForUpdate(1001L)).thenReturn(check);
        when(instanceMapper.selectByIdForUpdate(41L)).thenReturn(instance);
        when(taskMapper.selectByInstanceIdForUpdate(41L)).thenReturn(task);
        when(candidateMapper.selectOperationsDirectorCandidates(20L))
                .thenReturn(List.of(Map.of("userId", 9L, "userName", "ops")));
        when(detailMapper.selectInvStockCheckDetailByCheckIdForUpdate(1001L)).thenReturn(details);
        InvStockCheckAdjustmentResult adjustmentResult = new InvStockCheckAdjustmentResult();
        InvStockCheckAdjustmentEntry adjustment = new InvStockCheckAdjustmentEntry();
        adjustment.setProductName("测试茶");
        adjustment.setBeforeQuantity(new BigDecimal("10"));
        adjustment.setAfterQuantity(new BigDecimal("8"));
        adjustmentResult.getAdjustments().add(adjustment);
        when(adjustmentService.evaluate(check, details, true)).thenReturn(adjustmentResult);
        when(deptScopeMapper.countUserShopScope(9L, 20L)).thenReturn(1);
        when(deptScopeMapper.countDeptInScope(20L, 20L)).thenReturn(1);

        InvStockCheckApprovalRequest request = new InvStockCheckApprovalRequest();
        request.setInstanceId(41L);
        request.setComment("本人复核差异无误");
        service.approve(1001L, request, 20L);

        ArgumentCaptor<InvStockCheckApprovalTask> taskCaptor =
                ArgumentCaptor.forClass(InvStockCheckApprovalTask.class);
        verify(taskMapper).updateTask(taskCaptor.capture());
        assertThat(taskCaptor.getValue().getStatus()).isEqualTo("approved");
        assertThat(taskCaptor.getValue().getSelfApproved()).isEqualTo("1");
        ArgumentCaptor<InvStockCheckApprovalInstance> instanceCaptor =
                ArgumentCaptor.forClass(InvStockCheckApprovalInstance.class);
        verify(instanceMapper).updateInstance(instanceCaptor.capture());
        assertThat(instanceCaptor.getValue().getAdjustmentResultSnapshot())
                .contains("测试茶", "beforeQuantity", "10", "afterQuantity", "8");
        ArgumentCaptor<InvStockCheck> checkCaptor = ArgumentCaptor.forClass(InvStockCheck.class);
        verify(checkMapper).updateInvStockCheck(checkCaptor.capture());
        assertThat(checkCaptor.getValue().getStatus()).isEqualTo("completed");
        assertThat(checkCaptor.getValue().getApprovedUserId()).isEqualTo(9L);
        assertThat(checkCaptor.getValue().getApprovedBy()).isEqualTo("ops");
        assertThat(checkCaptor.getValue().getApprovedTime()).isNotNull();
    }

    @Test
    @DisplayName("当前用户已不在有效运营总监候选人中时禁止审批")
    void shouldRejectApprovalByNonCandidate()
    {
        SecurityContextHolder.setUserId("8");
        SecurityContextHolder.setUserName("former-ops");
        InvStockCheckApprovalInstanceMapper instanceMapper = mock(InvStockCheckApprovalInstanceMapper.class);
        InvStockCheckApprovalTaskMapper taskMapper = mock(InvStockCheckApprovalTaskMapper.class);
        InvStockCheckApprovalCandidateMapper candidateMapper = mock(InvStockCheckApprovalCandidateMapper.class);
        InvStockCheckMapper checkMapper = mock(InvStockCheckMapper.class);
        InvStockCheckDetailMapper detailMapper = mock(InvStockCheckDetailMapper.class);
        InvStockCheckAdjustmentService adjustmentService = mock(InvStockCheckAdjustmentService.class);
        InvDeptScopeMapper deptScopeMapper = mock(InvDeptScopeMapper.class);
        InvStockCheckApprovalServiceImpl service = service(instanceMapper, taskMapper, candidateMapper);
        ReflectionTestUtils.setField(service, "checkMapper", checkMapper);
        ReflectionTestUtils.setField(service, "detailMapper", detailMapper);
        ReflectionTestUtils.setField(service, "adjustmentService", adjustmentService);
        ReflectionTestUtils.setField(service, "deptScopeMapper", deptScopeMapper);

        InvStockCheck check = check(1001L, 20L);
        check.setStatus("pending_approval");
        InvStockCheckApprovalInstance instance = runningInstance(41L, 1001L, 7L);
        InvStockCheckApprovalTask task = pendingTask(51L, 41L, 1001L, "9");
        when(checkMapper.selectInvStockCheckByIdForUpdate(1001L)).thenReturn(check);
        when(instanceMapper.selectByIdForUpdate(41L)).thenReturn(instance);
        when(taskMapper.selectByInstanceIdForUpdate(41L)).thenReturn(task);
        when(candidateMapper.selectOperationsDirectorCandidates(20L))
                .thenReturn(List.of(Map.of("userId", 9L, "userName", "ops")));
        when(deptScopeMapper.countUserShopScope(8L, 20L)).thenReturn(1);
        when(deptScopeMapper.countDeptInScope(20L, 20L)).thenReturn(1);

        assertThatThrownBy(() -> service.approve(1001L, request(41L, "同意"), 20L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("不是该盘点单的有效审批人");
        verify(adjustmentService, never()).evaluate(any(), any(), anyBoolean());
    }

    @Test
    @DisplayName("驳回原因必填")
    void shouldRequireRejectReason()
    {
        InvStockCheckApprovalServiceImpl service = service(
                mock(InvStockCheckApprovalInstanceMapper.class),
                mock(InvStockCheckApprovalTaskMapper.class),
                mock(InvStockCheckApprovalCandidateMapper.class));

        assertThatThrownBy(() -> service.reject(1001L, request(41L, "  "), 20L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("驳回原因不能为空");
    }

    @Test
    @DisplayName("驳回后记录原因并将盘点单置为可修改")
    void shouldRejectAndRecordVisibleReason()
    {
        SecurityContextHolder.setUserId("9");
        SecurityContextHolder.setUserName("ops");
        InvStockCheckApprovalInstanceMapper instanceMapper = mock(InvStockCheckApprovalInstanceMapper.class);
        InvStockCheckApprovalTaskMapper taskMapper = mock(InvStockCheckApprovalTaskMapper.class);
        InvStockCheckApprovalCandidateMapper candidateMapper = mock(InvStockCheckApprovalCandidateMapper.class);
        InvStockCheckMapper checkMapper = mock(InvStockCheckMapper.class);
        InvDeptScopeMapper deptScopeMapper = mock(InvDeptScopeMapper.class);
        InvStockCheckApprovalServiceImpl service = service(instanceMapper, taskMapper, candidateMapper);
        ReflectionTestUtils.setField(service, "checkMapper", checkMapper);
        ReflectionTestUtils.setField(service, "deptScopeMapper", deptScopeMapper);

        InvStockCheck check = check(1001L, 20L);
        check.setStatus("pending_approval");
        when(checkMapper.selectInvStockCheckByIdForUpdate(1001L)).thenReturn(check);
        when(instanceMapper.selectByIdForUpdate(41L)).thenReturn(runningInstance(41L, 1001L, 7L));
        when(taskMapper.selectByInstanceIdForUpdate(41L))
                .thenReturn(pendingTask(51L, 41L, 1001L, "9"));
        when(candidateMapper.selectOperationsDirectorCandidates(20L))
                .thenReturn(List.of(Map.of("userId", 9L, "userName", "ops")));
        when(deptScopeMapper.countUserShopScope(9L, 20L)).thenReturn(1);
        when(deptScopeMapper.countDeptInScope(20L, 20L)).thenReturn(1);

        service.reject(1001L, request(41L, "账实差异说明不完整"), 20L);

        ArgumentCaptor<InvStockCheckApprovalTask> taskCaptor =
                ArgumentCaptor.forClass(InvStockCheckApprovalTask.class);
        verify(taskMapper).updateTask(taskCaptor.capture());
        assertThat(taskCaptor.getValue().getStatus()).isEqualTo("rejected");
        assertThat(taskCaptor.getValue().getApprovalComment()).isEqualTo("账实差异说明不完整");
        ArgumentCaptor<InvStockCheck> checkCaptor = ArgumentCaptor.forClass(InvStockCheck.class);
        verify(checkMapper).updateInvStockCheck(checkCaptor.capture());
        assertThat(checkCaptor.getValue().getStatus()).isEqualTo("rejected");
        assertThat(checkCaptor.getValue().getLastRejectReason()).isEqualTo("账实差异说明不完整");
        assertThat(checkCaptor.getValue().getLastRejectedBy()).isEqualTo("ops");
        assertThat(checkCaptor.getValue().getLastRejectedTime()).isNotNull();
    }

    @Test
    @DisplayName("审批时库存快照变化则结束本轮并标记需重盘")
    void shouldInvalidateApprovalWhenSnapshotChanged()
    {
        SecurityContextHolder.setUserId("9");
        SecurityContextHolder.setUserName("ops");
        InvStockCheckApprovalInstanceMapper instanceMapper = mock(InvStockCheckApprovalInstanceMapper.class);
        InvStockCheckApprovalTaskMapper taskMapper = mock(InvStockCheckApprovalTaskMapper.class);
        InvStockCheckApprovalCandidateMapper candidateMapper = mock(InvStockCheckApprovalCandidateMapper.class);
        InvStockCheckMapper checkMapper = mock(InvStockCheckMapper.class);
        InvStockCheckDetailMapper detailMapper = mock(InvStockCheckDetailMapper.class);
        InvStockCheckAdjustmentService adjustmentService = mock(InvStockCheckAdjustmentService.class);
        InvDeptScopeMapper deptScopeMapper = mock(InvDeptScopeMapper.class);
        InvStockCheckApprovalServiceImpl service = service(instanceMapper, taskMapper, candidateMapper);
        ReflectionTestUtils.setField(service, "checkMapper", checkMapper);
        ReflectionTestUtils.setField(service, "detailMapper", detailMapper);
        ReflectionTestUtils.setField(service, "adjustmentService", adjustmentService);
        ReflectionTestUtils.setField(service, "deptScopeMapper", deptScopeMapper);

        InvStockCheck check = check(1001L, 20L);
        check.setStatus("pending_approval");
        List<InvStockCheckDetail> details = List.of(detail(501L, 101L, "测试茶", "10", "8"));
        InvStockCheckAdjustmentResult result = new InvStockCheckAdjustmentResult();
        InvStockCheckSnapshotChange change = new InvStockCheckSnapshotChange();
        change.setDetailId(501L);
        change.setProductId(101L);
        change.setProductName("测试茶");
        change.setBookQuantity(new BigDecimal("10"));
        change.setCurrentQuantity(new BigDecimal("9"));
        result.getSnapshotChanges().add(change);
        when(checkMapper.selectInvStockCheckByIdForUpdate(1001L)).thenReturn(check);
        when(instanceMapper.selectByIdForUpdate(41L)).thenReturn(runningInstance(41L, 1001L, 7L));
        when(taskMapper.selectByInstanceIdForUpdate(41L))
                .thenReturn(pendingTask(51L, 41L, 1001L, "9"));
        when(candidateMapper.selectOperationsDirectorCandidates(20L))
                .thenReturn(List.of(Map.of("userId", 9L, "userName", "ops")));
        when(detailMapper.selectInvStockCheckDetailByCheckIdForUpdate(1001L)).thenReturn(details);
        when(adjustmentService.evaluate(check, details, true)).thenReturn(result);
        when(deptScopeMapper.countUserShopScope(9L, 20L)).thenReturn(1);
        when(deptScopeMapper.countDeptInScope(20L, 20L)).thenReturn(1);

        service.approve(1001L, request(41L, "同意"), 20L);

        ArgumentCaptor<InvStockCheckApprovalInstance> instanceCaptor =
                ArgumentCaptor.forClass(InvStockCheckApprovalInstance.class);
        verify(instanceMapper).updateInstance(instanceCaptor.capture());
        assertThat(instanceCaptor.getValue().getStatus()).isEqualTo("invalidated");
        assertThat(instanceCaptor.getValue().getInvalidDetailSnapshot()).contains("测试茶", "currentQuantity", "9");
        ArgumentCaptor<InvStockCheck> checkCaptor = ArgumentCaptor.forClass(InvStockCheck.class);
        verify(checkMapper).updateInvStockCheck(checkCaptor.capture());
        assertThat(checkCaptor.getValue().getStatus()).isEqualTo("invalidated");
        assertThat(checkCaptor.getValue().getLastInvalidReason()).contains("库存快照");
        assertThat(checkCaptor.getValue().getLastInvalidDetailSnapshot()).contains("测试茶");
    }

    @Test
    @DisplayName("已结束审批再次操作时明确拒绝且不重复调库存")
    void shouldRejectRepeatedApproval()
    {
        SecurityContextHolder.setUserId("9");
        SecurityContextHolder.setUserName("ops");
        InvStockCheckApprovalInstanceMapper instanceMapper = mock(InvStockCheckApprovalInstanceMapper.class);
        InvStockCheckApprovalTaskMapper taskMapper = mock(InvStockCheckApprovalTaskMapper.class);
        InvStockCheckApprovalCandidateMapper candidateMapper = mock(InvStockCheckApprovalCandidateMapper.class);
        InvStockCheckMapper checkMapper = mock(InvStockCheckMapper.class);
        InvDeptScopeMapper deptScopeMapper = mock(InvDeptScopeMapper.class);
        InvStockCheckAdjustmentService adjustmentService = mock(InvStockCheckAdjustmentService.class);
        InvStockCheckApprovalServiceImpl service = service(instanceMapper, taskMapper, candidateMapper);
        ReflectionTestUtils.setField(service, "checkMapper", checkMapper);
        ReflectionTestUtils.setField(service, "deptScopeMapper", deptScopeMapper);
        ReflectionTestUtils.setField(service, "adjustmentService", adjustmentService);

        InvStockCheck check = check(1001L, 20L);
        check.setStatus("pending_approval");
        InvStockCheckApprovalInstance finished = runningInstance(41L, 1001L, 7L);
        finished.setStatus("approved");
        when(checkMapper.selectInvStockCheckByIdForUpdate(1001L)).thenReturn(check);
        when(instanceMapper.selectByIdForUpdate(41L)).thenReturn(finished);
        when(deptScopeMapper.countUserShopScope(9L, 20L)).thenReturn(1);
        when(deptScopeMapper.countDeptInScope(20L, 20L)).thenReturn(1);

        assertThatThrownBy(() -> service.approve(1001L, request(41L, "再次审批"), 20L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("已处理");
        verify(adjustmentService, never()).evaluate(any(), any(), anyBoolean());
    }

    @Test
    @DisplayName("旧页面携带的非当前审批实例不能审批")
    void shouldRejectStaleRunningApprovalInstance()
    {
        SecurityContextHolder.setUserId("9");
        SecurityContextHolder.setUserName("ops");
        InvStockCheckApprovalInstanceMapper instanceMapper = mock(InvStockCheckApprovalInstanceMapper.class);
        InvStockCheckApprovalTaskMapper taskMapper = mock(InvStockCheckApprovalTaskMapper.class);
        InvStockCheckApprovalCandidateMapper candidateMapper = mock(InvStockCheckApprovalCandidateMapper.class);
        InvStockCheckMapper checkMapper = mock(InvStockCheckMapper.class);
        InvDeptScopeMapper deptScopeMapper = mock(InvDeptScopeMapper.class);
        InvStockCheckAdjustmentService adjustmentService = mock(InvStockCheckAdjustmentService.class);
        InvStockCheckApprovalServiceImpl service = service(instanceMapper, taskMapper, candidateMapper);
        ReflectionTestUtils.setField(service, "checkMapper", checkMapper);
        ReflectionTestUtils.setField(service, "deptScopeMapper", deptScopeMapper);
        ReflectionTestUtils.setField(service, "adjustmentService", adjustmentService);

        InvStockCheck check = check(1001L, 20L);
        check.setStatus("pending_approval");
        check.setApprovalInstanceId(42L);
        when(checkMapper.selectInvStockCheckByIdForUpdate(1001L)).thenReturn(check);
        when(instanceMapper.selectByIdForUpdate(41L))
                .thenReturn(runningInstance(41L, 1001L, 7L));
        when(deptScopeMapper.countUserShopScope(9L, 20L)).thenReturn(1);
        when(deptScopeMapper.countDeptInScope(20L, 20L)).thenReturn(1);

        assertThatThrownBy(() -> service.approve(1001L, request(41L, "同意"), 20L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("不是当前审批实例");
        verify(taskMapper, never()).selectByInstanceIdForUpdate(any());
        verify(adjustmentService, never()).evaluate(any(), any(), anyBoolean());
    }

    @Test
    @DisplayName("审批轨迹按轮次返回并附带任务记录")
    void shouldAttachTasksToApprovalTrack()
    {
        SecurityContextHolder.setUserId("7");
        SecurityContextHolder.setUserName("counter");
        InvStockCheckApprovalInstanceMapper instanceMapper = mock(InvStockCheckApprovalInstanceMapper.class);
        InvStockCheckApprovalTaskMapper taskMapper = mock(InvStockCheckApprovalTaskMapper.class);
        InvStockCheckApprovalCandidateMapper candidateMapper = mock(InvStockCheckApprovalCandidateMapper.class);
        InvStockCheckMapper checkMapper = mock(InvStockCheckMapper.class);
        InvDeptScopeMapper deptScopeMapper = mock(InvDeptScopeMapper.class);
        InvStockCheckApprovalServiceImpl service = service(instanceMapper, taskMapper, candidateMapper);
        ReflectionTestUtils.setField(service, "checkMapper", checkMapper);
        ReflectionTestUtils.setField(service, "deptScopeMapper", deptScopeMapper);
        InvStockCheckApprovalInstance instance = runningInstance(41L, 1001L, 7L);
        InvStockCheckApprovalTask task = pendingTask(51L, 41L, 1001L, "9");
        when(checkMapper.selectInvStockCheckById(1001L)).thenReturn(check(1001L, 20L));
        when(instanceMapper.selectByCheckId(1001L)).thenReturn(List.of(instance));
        when(taskMapper.selectByInstanceId(41L)).thenReturn(List.of(task));
        when(deptScopeMapper.countUserShopScope(7L, 20L)).thenReturn(1);
        when(deptScopeMapper.countDeptInScope(20L, 20L)).thenReturn(1);

        List<InvStockCheckApprovalInstance> track = service.selectTrack(1001L, 20L);

        assertThat(track).hasSize(1);
        assertThat(track.get(0).getTasks()).containsExactly(task);
    }

    private static InvStockCheckApprovalServiceImpl service(
            InvStockCheckApprovalInstanceMapper instanceMapper,
            InvStockCheckApprovalTaskMapper taskMapper,
            InvStockCheckApprovalCandidateMapper candidateMapper)
    {
        InvStockCheckApprovalServiceImpl service = new InvStockCheckApprovalServiceImpl();
        ReflectionTestUtils.setField(service, "instanceMapper", instanceMapper);
        ReflectionTestUtils.setField(service, "taskMapper", taskMapper);
        ReflectionTestUtils.setField(service, "candidateMapper", candidateMapper);
        return service;
    }

    private static InvStockCheck check(Long checkId, Long deptId)
    {
        InvStockCheck check = new InvStockCheck();
        check.setCheckId(checkId);
        check.setCheckNo("SC202607100001");
        check.setShopDeptId(deptId);
        check.setWarehouseId(deptId);
        check.setApprovalInstanceId(41L);
        return check;
    }

    private static InvStockCheckDetail detail(Long detailId, Long productId, String name,
            String bookQty, String actualQty)
    {
        InvStockCheckDetail detail = new InvStockCheckDetail();
        detail.setDetailId(detailId);
        detail.setCheckId(1001L);
        detail.setProductId(productId);
        detail.setProductName(name);
        detail.setBookQty(new BigDecimal(bookQty));
        detail.setActualQty(new BigDecimal(actualQty));
        detail.setDiffQty(new BigDecimal(actualQty).subtract(new BigDecimal(bookQty)));
        detail.setDiffType(detail.getDiffQty().signum() > 0 ? "profit" : "loss");
        detail.setCostPrice(BigDecimal.ONE);
        return detail;
    }

    private static InvStockCheckApprovalInstance runningInstance(Long instanceId, Long checkId,
            Long submittedUserId)
    {
        InvStockCheckApprovalInstance instance = new InvStockCheckApprovalInstance();
        instance.setInstanceId(instanceId);
        instance.setCheckId(checkId);
        instance.setStatus("running");
        instance.setSubmittedUserId(submittedUserId);
        return instance;
    }

    private static InvStockCheckApprovalTask pendingTask(Long taskId, Long instanceId,
            Long checkId, String candidateUserIds)
    {
        InvStockCheckApprovalTask task = new InvStockCheckApprovalTask();
        task.setTaskId(taskId);
        task.setInstanceId(instanceId);
        task.setCheckId(checkId);
        task.setStatus("pending");
        task.setCandidateUserIds(candidateUserIds);
        return task;
    }

    private static InvStockCheckApprovalRequest request(Long instanceId, String comment)
    {
        InvStockCheckApprovalRequest request = new InvStockCheckApprovalRequest();
        request.setInstanceId(instanceId);
        request.setComment(comment);
        return request;
    }
}
