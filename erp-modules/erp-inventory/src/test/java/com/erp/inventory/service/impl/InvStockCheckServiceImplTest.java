package com.erp.inventory.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import com.erp.approval.api.domain.ApprovalStartRequest;
import com.erp.common.core.context.SecurityContextHolder;
import com.erp.common.core.exception.ServiceException;
import com.erp.inventory.constant.InvStatusConstants;
import com.erp.inventory.controller.InvStockCheckController;
import com.erp.inventory.domain.InvStock;
import com.erp.inventory.domain.InvStockCheck;
import com.erp.inventory.domain.InvStockCheckApprovalStartOutbox;
import com.erp.inventory.domain.InvStockCheckApprovalInstance;
import com.erp.inventory.domain.InvStockCheckDetail;
import com.erp.inventory.domain.dto.InvStockCheckAssignmentRequest;
import com.erp.inventory.domain.vo.InvStockCheckAdjustmentResult;
import com.erp.inventory.domain.vo.InvStockCheckCounterCandidate;
import com.erp.inventory.domain.vo.InvStockCheckSnapshotChange;
import com.erp.inventory.mapper.InvDeptScopeMapper;
import com.erp.inventory.mapper.InvNumberSequenceMapper;
import com.erp.inventory.mapper.InvStockCheckDetailMapper;
import com.erp.inventory.mapper.InvStockCheckMapper;
import com.erp.inventory.mapper.InvStockMapper;
import com.erp.inventory.service.IInvStockCheckApprovalService;
import com.erp.inventory.service.IInvStockCheckService;
import com.erp.common.security.annotation.IdempotentSubmit;
import com.erp.common.security.annotation.Logical;
import com.erp.common.security.annotation.RequiresPermissions;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.test.util.ReflectionTestUtils;

@DisplayName("库存盘点服务")
class InvStockCheckServiceImplTest
{
    @AfterEach
    void tearDown()
    {
        SecurityContextHolder.remove();
    }

    @Test
    @DisplayName("根据差异数量记录盘盈盘亏类型")
    void shouldResolveDifferenceTypeByDiffQuantity()
    {
        assertThat(InvStockCheckServiceImpl.resolveDiffType(new BigDecimal("2.00"))).isEqualTo("profit");
        assertThat(InvStockCheckServiceImpl.resolveDiffType(new BigDecimal("-1.00"))).isEqualTo("loss");
        assertThat(InvStockCheckServiceImpl.resolveDiffType(BigDecimal.ZERO)).isEqualTo("none");
        assertThat(InvStockCheckServiceImpl.resolveDiffType(null)).isEqualTo("none");
    }

    @Test
    @DisplayName("差异达到复盘阈值时必须复盘")
    void shouldRequireRecountWhenAbsoluteDifferenceReachesThreshold()
    {
        assertThat(InvStockCheckServiceImpl.requiresRecount(
                new BigDecimal("10"), new BigDecimal("8"), new BigDecimal("2"))).isTrue();
        assertThat(InvStockCheckServiceImpl.requiresRecount(
                new BigDecimal("10"), new BigDecimal("11"), new BigDecimal("2"))).isFalse();
        assertThat(InvStockCheckServiceImpl.requiresRecount(
                new BigDecimal("10"), new BigDecimal("8"), BigDecimal.ZERO)).isFalse();
        assertThat(InvStockCheckServiceImpl.requiresRecount(
                new BigDecimal("10"), null, new BigDecimal("2"))).isFalse();
    }

    @Test
    @DisplayName("盘点审批状态常量和可编辑状态符合契约")
    void shouldExposeStockCheckApprovalStates()
    {
        assertThat(InvStatusConstants.PENDING_APPROVAL).isEqualTo("pending_approval");
        assertThat(InvStatusConstants.INVALIDATED).isEqualTo("invalidated");
        assertThat(InvStateGuard.isStockCheckEditable(InvStatusConstants.DRAFT)).isTrue();
        assertThat(InvStateGuard.isStockCheckEditable(InvStatusConstants.REJECTED)).isTrue();
        assertThat(InvStateGuard.isStockCheckEditable(InvStatusConstants.PENDING_APPROVAL)).isFalse();
    }

    @Test
    @DisplayName("录入实盘只保存且驳回状态仍可修改")
    void shouldSaveRejectedInputWithoutSubmitting()
    {
        SecurityContextHolder.setUserId("7");
        SecurityContextHolder.setUserName("counter");
        InvStockCheckServiceImpl service = new InvStockCheckServiceImpl();
        InvStockCheckMapper checkMapper = mock(InvStockCheckMapper.class);
        InvStockCheckDetailMapper detailMapper = mock(InvStockCheckDetailMapper.class);
        IInvStockCheckApprovalService approvalService = mock(IInvStockCheckApprovalService.class);
        InvStockCheck check = stockCheck(1001L, 20L, InvStatusConstants.REJECTED);
        InvStockCheckDetail dbDetail = stockCheckDetail(501L, 1001L, 101L,
                "测试商品", "10", "8", "-2");
        InvStockCheck input = new InvStockCheck();
        input.setDetails(List.of(dbDetail));
        when(checkMapper.selectInvStockCheckById(1001L)).thenReturn(check);
        when(checkMapper.selectInvStockCheckByIdForUpdate(1001L)).thenReturn(check);
        when(detailMapper.selectInvStockCheckDetailByCheckId(1001L)).thenReturn(List.of(dbDetail));
        when(detailMapper.selectInvStockCheckDetailByCheckIdForUpdate(1001L)).thenReturn(List.of(dbDetail));
        service.deptScopeMapper = deptScopeMapper(Map.of(20L, "WAREHOUSE"));
        ReflectionTestUtils.setField(service, "checkMapper", checkMapper);
        ReflectionTestUtils.setField(service, "checkDetailMapper", detailMapper);
        ReflectionTestUtils.setField(service, "approvalService", approvalService);

        service.inputActualQty(1001L, input, 20L);

        ArgumentCaptor<InvStockCheck> captor = ArgumentCaptor.forClass(InvStockCheck.class);
        verify(checkMapper).updateInvStockCheck(captor.capture());
        assertThat(captor.getValue().getStatus()).isNull();
        assertThat(captor.getValue().getCounterUserId()).isNull();
        assertThat(captor.getValue().getCounterName()).isNull();
        assertThat(captor.getValue().getDeadline()).isNull();
        verify(approvalService, never()).createPendingApproval(any(), any());
    }

    @Test
    void shouldPreserveRecountAndAuditWhenActualUnchangedAndRecountOmitted() throws Exception
    {
        InvStockCheckDetail saved = saveRecountInput("{\"detailId\":501,\"actualQty\":8.00}");
        assertThat(saved.getRecountQty()).isEqualByComparingTo("7");
        assertThat(saved.getRecountBy()).isEqualTo("original-counter");
        assertThat(saved.getRecountTime()).isEqualTo(new Date(123L));
        assertThat(saved.getDiffQty()).isEqualByComparingTo("-3");
        assertThat(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(saved))
                .doesNotContain("recountQtySpecified");
    }

    @Test
    void shouldClearRecountAndAuditWhenExplicitNullIsProvided() throws Exception
    {
        InvStockCheckDetail saved = saveRecountInput("{\"detailId\":501,\"actualQty\":8,\"recountQty\":null}");
        assertThat(saved.getRecountQty()).isNull();
        assertThat(saved.getRecountBy()).isNull();
        assertThat(saved.getRecountTime()).isNull();
        assertThat(saved.getDiffQty()).isEqualByComparingTo("-2");
    }

    @Test
    void shouldDiscardOldRecountWhenActualChangedWithoutNewRecount() throws Exception
    {
        InvStockCheckDetail saved = saveRecountInput("{\"detailId\":501,\"actualQty\":6}");
        assertThat(saved.getRecountQty()).isNull();
        assertThat(saved.getRecountBy()).isNull();
        assertThat(saved.getRecountTime()).isNull();
        assertThat(saved.getDiffQty()).isEqualByComparingTo("-4");
    }

    @Test
    void shouldRecordNewRecountIncludingZeroWithCurrentOperator() throws Exception
    {
        InvStockCheckDetail saved = saveRecountInput("{\"detailId\":501,\"actualQty\":6,\"recountQty\":0}");
        assertThat(saved.getRecountQty()).isZero();
        assertThat(saved.getRecountBy()).isEqualTo("current-counter");
        assertThat(saved.getRecountTime()).isAfter(new Date(123L));
        assertThat(saved.getDiffQty()).isEqualByComparingTo("-10");
    }

    private InvStockCheckDetail saveRecountInput(String json) throws Exception
    {
        SecurityContextHolder.setUserId("7");
        SecurityContextHolder.setUserName("current-counter");
        InvStockCheckServiceImpl service = new InvStockCheckServiceImpl();
        InvStockCheckMapper checks = mock(InvStockCheckMapper.class);
        InvStockCheckDetailMapper details = mock(InvStockCheckDetailMapper.class);
        InvStockCheck check = stockCheck(1001L, 20L, InvStatusConstants.DRAFT);
        check.setRecountThreshold(BigDecimal.ONE);
        InvStockCheckDetail persisted = stockCheckDetail(501L, 1001L, 101L, "测试商品", "10", "8", "-3");
        persisted.setRecountRequired("1"); persisted.setRecountQty(new BigDecimal("7"));
        persisted.setRecountBy("original-counter"); persisted.setRecountTime(new Date(123L));
        when(checks.selectInvStockCheckById(1001L)).thenReturn(check);
        when(checks.selectInvStockCheckByIdForUpdate(1001L)).thenReturn(check);
        when(details.selectInvStockCheckDetailByCheckId(1001L)).thenReturn(List.of(persisted));
        when(details.selectInvStockCheckDetailByCheckIdForUpdate(1001L)).thenReturn(List.of(persisted));
        service.deptScopeMapper = deptScopeMapper(Map.of(20L, "WAREHOUSE"));
        ReflectionTestUtils.setField(service, "checkMapper", checks);
        ReflectionTestUtils.setField(service, "checkDetailMapper", details);
        InvStockCheck input = new InvStockCheck();
        input.setDetails(List.of(new com.fasterxml.jackson.databind.ObjectMapper().readValue(json, InvStockCheckDetail.class)));
        service.inputActualQty(1001L, input, 20L);
        verify(details).updateInvStockCheckDetail(persisted);
        return persisted;
    }

    @Test
    @DisplayName("非指定盘点人且无管理权限时不能代录")
    void shouldRejectInputByUnassignedUserWithoutManagePermission()
    {
        SecurityContextHolder.setUserId("9");
        SecurityContextHolder.setUserName("other");
        InvStockCheckServiceImpl service = new PermissionAwareStockCheckService(false);
        InvStockCheckMapper checkMapper = mock(InvStockCheckMapper.class);
        InvStockCheck check = stockCheck(1001L, 20L, InvStatusConstants.DRAFT);
        check.setCounterUserId(7L);
        when(checkMapper.selectInvStockCheckById(1001L)).thenReturn(check);
        when(checkMapper.selectInvStockCheckByIdForUpdate(1001L)).thenReturn(check);
        service.deptScopeMapper = deptScopeMapper(Map.of(20L, "WAREHOUSE"));
        ReflectionTestUtils.setField(service, "checkMapper", checkMapper);

        assertThatThrownBy(() -> service.inputActualQty(1001L, new InvStockCheck(), 20L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("仅指定盘点人");

        verify(checkMapper, never()).updateInvStockCheck(any());
    }

    @Test
    @DisplayName("具备盘点修改权限的主管可以明确代办")
    void shouldAllowManagedInputForUnassignedUser()
    {
        SecurityContextHolder.setUserId("9");
        SecurityContextHolder.setUserName("manager");
        InvStockCheckServiceImpl service = new PermissionAwareStockCheckService(true);
        InvStockCheckMapper checkMapper = mock(InvStockCheckMapper.class);
        InvStockCheckDetailMapper detailMapper = mock(InvStockCheckDetailMapper.class);
        InvStockCheck check = stockCheck(1001L, 20L, InvStatusConstants.DRAFT);
        check.setCounterUserId(7L);
        when(checkMapper.selectInvStockCheckById(1001L)).thenReturn(check);
        when(checkMapper.selectInvStockCheckByIdForUpdate(1001L)).thenReturn(check);
        when(detailMapper.selectInvStockCheckDetailByCheckId(1001L))
                .thenReturn(Collections.emptyList());
        when(detailMapper.selectInvStockCheckDetailByCheckIdForUpdate(1001L))
                .thenReturn(Collections.emptyList());
        service.deptScopeMapper = deptScopeMapper(Map.of(20L, "WAREHOUSE"));
        ReflectionTestUtils.setField(service, "checkMapper", checkMapper);
        ReflectionTestUtils.setField(service, "checkDetailMapper", detailMapper);

        service.inputActualQty(1001L, new InvStockCheck(), 20L);

        verify(checkMapper).updateInvStockCheck(any());
    }

    @Test
    @DisplayName("盘点指派只接受当前组织中有提交权限的有效用户并由后端写姓名")
    void shouldAssignValidatedCounterAndDeriveDisplayName()
    {
        SecurityContextHolder.setUserId("1");
        SecurityContextHolder.setUserName("admin");
        InvStockCheckServiceImpl service = new InvStockCheckServiceImpl();
        InvStockCheckMapper checkMapper = mock(InvStockCheckMapper.class);
        InvStockCheckDetailMapper detailMapper = mock(InvStockCheckDetailMapper.class);
        InvStockCheck check = stockCheck(1001L, 20L, InvStatusConstants.REJECTED);
        InvStockCheckCounterCandidate candidate = counterCandidate(8L, "zhangsan", "张三");
        InvStockCheckAssignmentRequest assignment = new InvStockCheckAssignmentRequest();
        assignment.setCounterUserId(8L);
        assignment.setDeadline(futureDeadline());
        when(checkMapper.selectInvStockCheckById(1001L)).thenReturn(check);
        when(checkMapper.selectInvStockCheckByIdForUpdate(1001L)).thenReturn(check);
        when(checkMapper.selectCounterCandidate(20L, 8L)).thenReturn(candidate);
        when(detailMapper.selectInvStockCheckDetailByCheckId(1001L))
                .thenReturn(Collections.emptyList());
        when(detailMapper.selectInvStockCheckDetailByCheckIdForUpdate(1001L))
                .thenReturn(Collections.emptyList());
        service.deptScopeMapper = deptScopeMapper(Map.of(20L, "WAREHOUSE"));
        ReflectionTestUtils.setField(service, "checkMapper", checkMapper);
        ReflectionTestUtils.setField(service, "checkDetailMapper", detailMapper);

        service.assignCounter(1001L, assignment, 20L);

        ArgumentCaptor<InvStockCheck> captor = ArgumentCaptor.forClass(InvStockCheck.class);
        verify(checkMapper).updateInvStockCheck(captor.capture());
        assertThat(captor.getValue().getCounterUserId()).isEqualTo(8L);
        assertThat(captor.getValue().getCounterName()).isEqualTo("张三");
        assertThat(captor.getValue().getDeadline()).isEqualTo(assignment.getDeadline());
    }

    @Test
    @DisplayName("无差异盘点提交后自动完成且不创建审批")
    void shouldCompleteZeroDifferenceCheckWithoutApproval()
    {
        SecurityContextHolder.setUserId("7");
        SecurityContextHolder.setUserName("counter");
        InvStockCheckServiceImpl service = new InvStockCheckServiceImpl();
        InvStockCheckMapper checkMapper = mock(InvStockCheckMapper.class);
        InvStockCheckDetailMapper detailMapper = mock(InvStockCheckDetailMapper.class);
        InvStockCheckAdjustmentService adjustmentService = mock(InvStockCheckAdjustmentService.class);
        IInvStockCheckApprovalService approvalService = mock(IInvStockCheckApprovalService.class);
        InvStockCheck check = stockCheck(1001L, 20L, InvStatusConstants.DRAFT);
        InvStockCheckDetail detail = stockCheckDetail(501L, 1001L, 101L,
                "测试商品", "10", "10", "0");
        List<InvStockCheckDetail> details = List.of(detail);
        when(checkMapper.selectInvStockCheckById(1001L)).thenReturn(check);
        when(checkMapper.selectInvStockCheckByIdForUpdate(1001L)).thenReturn(check);
        when(detailMapper.selectInvStockCheckDetailByCheckIdForUpdate(1001L)).thenReturn(details);
        when(adjustmentService.evaluate(check, details, false))
                .thenReturn(new InvStockCheckAdjustmentResult());
        configureApprovalFlow(service, checkMapper, detailMapper, adjustmentService, approvalService);

        service.submitCheck(1001L, null, 20L);

        ArgumentCaptor<InvStockCheck> captor = ArgumentCaptor.forClass(InvStockCheck.class);
        verify(checkMapper).updateInvStockCheck(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(InvStatusConstants.COMPLETED);
        assertThat(captor.getValue().getSubmittedUserId()).isEqualTo(7L);
        verify(approvalService, never()).createPendingApproval(any(), any());
    }

    @Test
    @DisplayName("有差异盘点提交后创建下一轮审批")
    void shouldCreateApprovalForDifferenceCheck()
    {
        SecurityContextHolder.setUserId("7");
        SecurityContextHolder.setUserName("counter");
        InvStockCheckServiceImpl service = new InvStockCheckServiceImpl();
        InvStockCheckMapper checkMapper = mock(InvStockCheckMapper.class);
        InvStockCheckDetailMapper detailMapper = mock(InvStockCheckDetailMapper.class);
        InvStockCheckAdjustmentService adjustmentService = mock(InvStockCheckAdjustmentService.class);
        IInvStockCheckApprovalService approvalService = mock(IInvStockCheckApprovalService.class);
        InvStockCheck check = stockCheck(1001L, 20L, InvStatusConstants.REJECTED);
        check.setApprovalRound(1);
        InvStockCheckDetail detail = stockCheckDetail(501L, 1001L, 101L,
                "测试商品", "10", "8", "-2");
        List<InvStockCheckDetail> details = List.of(detail);
        InvStockCheckApprovalInstance instance = new InvStockCheckApprovalInstance();
        instance.setInstanceId(41L);
        instance.setRoundNo(2);
        when(checkMapper.selectInvStockCheckById(1001L)).thenReturn(check);
        when(checkMapper.selectInvStockCheckByIdForUpdate(1001L)).thenReturn(check);
        when(detailMapper.selectInvStockCheckDetailByCheckIdForUpdate(1001L)).thenReturn(details);
        when(adjustmentService.evaluate(check, details, false))
                .thenReturn(new InvStockCheckAdjustmentResult());
        when(approvalService.createPendingApproval(check, details)).thenReturn(instance);
        configureApprovalFlow(service, checkMapper, detailMapper, adjustmentService, approvalService);

        service.submitCheck(1001L, null, 20L);

        ArgumentCaptor<InvStockCheck> captor = ArgumentCaptor.forClass(InvStockCheck.class);
        verify(checkMapper).updateInvStockCheck(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(InvStatusConstants.PENDING_APPROVAL);
        assertThat(captor.getValue().getApprovalInstanceId()).isEqualTo(41L);
        assertThat(captor.getValue().getApprovalRound()).isEqualTo(2);
    }

    @Test
    @DisplayName("原生审批驳回后重新提交会创建新轮次并保留前序实例")
    void shouldEnqueueNativeApprovalWithoutCallingRemoteInsideTransaction()
    {
        SecurityContextHolder.setUserId("7");
        SecurityContextHolder.setUserName("counter");
        InvStockCheckServiceImpl service = new InvStockCheckServiceImpl();
        InvStockCheckMapper checkMapper = mock(InvStockCheckMapper.class);
        InvStockCheckDetailMapper detailMapper = mock(
                InvStockCheckDetailMapper.class);
        InvStockCheckAdjustmentService adjustmentService = mock(
                InvStockCheckAdjustmentService.class);
        IInvStockCheckApprovalService approvalService = mock(
                IInvStockCheckApprovalService.class);
        InventoryUnifiedApprovalService unifiedService = mock(
                InventoryUnifiedApprovalService.class);
        InvStockCheckApprovalStartOutboxService outboxService = mock(
                InvStockCheckApprovalStartOutboxService.class);
        InvStockCheckApprovalStartAfterCommitTrigger trigger = mock(
                InvStockCheckApprovalStartAfterCommitTrigger.class);
        InvStockCheck check = stockCheck(1001L, 20L,
                InvStatusConstants.REJECTED);
        check.setApprovalRound(1);
        check.setApprovalEngine(
                InventoryUnifiedApprovalService.ENGINE_NATIVE);
        check.setApprovalInstanceId(7001L);
        InvStockCheck submitted = stockCheck(1001L, 20L,
                InvStatusConstants.PENDING_APPROVAL);
        submitted.setApprovalRound(2);
        submitted.setApprovalEngine(
                InventoryUnifiedApprovalService.ENGINE_NATIVE);
        submitted.setRowVersion(4L);
        InvStockCheckDetail detail = stockCheckDetail(501L, 1001L, 101L,
                "测试商品", "10", "8", "-2");
        List<InvStockCheckDetail> details = List.of(detail);
        ApprovalStartRequest request = new ApprovalStartRequest();
        request.setBusinessCode(
                InventoryUnifiedApprovalService.STOCK_CHECK);
        request.setBusinessId("1001");
        request.setBusinessRound(2);
        request.setApplicantId(7L);
        request.setIdempotencyKey("INV_STOCK_CHECK:1001:2");
        InvStockCheckApprovalStartOutbox outbox =
                new InvStockCheckApprovalStartOutbox();
        outbox.setOutboxId(10L);

        when(checkMapper.selectInvStockCheckById(1001L)).thenReturn(check);
        when(checkMapper.selectInvStockCheckByIdForUpdate(1001L))
                .thenReturn(check, submitted);
        when(detailMapper.selectInvStockCheckDetailByCheckIdForUpdate(1001L))
                .thenReturn(details);
        when(adjustmentService.evaluate(check, details, false))
                .thenReturn(new InvStockCheckAdjustmentResult());
        when(unifiedService.useNativeStockCheck(check)).thenReturn(true);
        when(unifiedService.buildStockCheckStartRequest(check, details, 2))
                .thenReturn(request);
        when(outboxService.enqueue(submitted, request, "counter"))
                .thenReturn(outbox);
        service.deptScopeMapper = deptScopeMapper(Map.of(20L, "WAREHOUSE"));
        ReflectionTestUtils.setField(service, "checkMapper", checkMapper);
        ReflectionTestUtils.setField(service, "checkDetailMapper",
                detailMapper);
        ReflectionTestUtils.setField(service, "adjustmentService",
                adjustmentService);
        ReflectionTestUtils.setField(service, "approvalService",
                approvalService);
        ReflectionTestUtils.setField(service, "unifiedApprovalService",
                unifiedService);
        ReflectionTestUtils.setField(service, "approvalStartOutboxService",
                outboxService);
        ReflectionTestUtils.setField(service,
                "approvalStartAfterCommitTrigger", trigger);

        service.submitCheck(1001L, null, 20L);

        ArgumentCaptor<InvStockCheck> update = ArgumentCaptor
                .forClass(InvStockCheck.class);
        verify(checkMapper).updateInvStockCheck(update.capture());
        assertThat(update.getValue().getStatus())
                .isEqualTo(InvStatusConstants.PENDING_APPROVAL);
        assertThat(update.getValue().getApprovalRound()).isEqualTo(2);
        assertThat(update.getValue().getApprovalEngine())
                .isEqualTo(InventoryUnifiedApprovalService.ENGINE_NATIVE);
        assertThat(update.getValue().getApprovalInstanceId()).isNull();
        verify(outboxService).enqueue(submitted, request, "counter");
        verify(trigger).trigger(10L);
        verify(approvalService, never()).createPendingApproval(any(), any());
    }

    @Test
    @DisplayName("达到复盘阈值但未录入复盘数量时禁止提交")
    void shouldRejectSubmitWhenRequiredRecountIsMissing()
    {
        SecurityContextHolder.setUserId("7");
        SecurityContextHolder.setUserName("counter");
        InvStockCheckServiceImpl service = new InvStockCheckServiceImpl();
        InvStockCheckMapper checkMapper = mock(InvStockCheckMapper.class);
        InvStockCheckDetailMapper detailMapper = mock(InvStockCheckDetailMapper.class);
        InvStockCheckAdjustmentService adjustmentService = mock(InvStockCheckAdjustmentService.class);
        IInvStockCheckApprovalService approvalService = mock(IInvStockCheckApprovalService.class);
        InvStockCheck check = stockCheck(1001L, 20L, InvStatusConstants.DRAFT);
        InvStockCheckDetail detail = stockCheckDetail(501L, 1001L, 101L,
                "测试商品", "10", "8", "-2");
        detail.setRecountRequired("1");
        when(checkMapper.selectInvStockCheckById(1001L)).thenReturn(check);
        when(checkMapper.selectInvStockCheckByIdForUpdate(1001L)).thenReturn(check);
        when(detailMapper.selectInvStockCheckDetailByCheckIdForUpdate(1001L))
                .thenReturn(List.of(detail));
        configureApprovalFlow(service, checkMapper, detailMapper, adjustmentService, approvalService);

        assertThatThrownBy(() -> service.submitCheck(1001L, null, 20L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("达到复盘阈值");

        verify(adjustmentService, never()).evaluate(any(), any(), eq(false));
        verify(approvalService, never()).createPendingApproval(any(), any());
    }

    @Test
    @DisplayName("提交时库存快照变化则不创建审批并标记需重盘")
    void shouldInvalidateStaleCheckOnSubmit()
    {
        SecurityContextHolder.setUserId("7");
        SecurityContextHolder.setUserName("counter");
        InvStockCheckServiceImpl service = new InvStockCheckServiceImpl();
        InvStockCheckMapper checkMapper = mock(InvStockCheckMapper.class);
        InvStockCheckDetailMapper detailMapper = mock(InvStockCheckDetailMapper.class);
        InvStockCheckAdjustmentService adjustmentService = mock(InvStockCheckAdjustmentService.class);
        IInvStockCheckApprovalService approvalService = mock(IInvStockCheckApprovalService.class);
        InvStockCheck check = stockCheck(1001L, 20L, InvStatusConstants.DRAFT);
        InvStockCheckDetail detail = stockCheckDetail(501L, 1001L, 101L,
                "测试商品", "10", "8", "-2");
        List<InvStockCheckDetail> details = List.of(detail);
        InvStockCheckAdjustmentResult result = staleResult(detail);
        when(checkMapper.selectInvStockCheckById(1001L)).thenReturn(check);
        when(checkMapper.selectInvStockCheckByIdForUpdate(1001L)).thenReturn(check);
        when(detailMapper.selectInvStockCheckDetailByCheckIdForUpdate(1001L)).thenReturn(details);
        when(adjustmentService.evaluate(check, details, false)).thenReturn(result);
        configureApprovalFlow(service, checkMapper, detailMapper, adjustmentService, approvalService);

        service.submitCheck(1001L, null, 20L);

        ArgumentCaptor<InvStockCheck> captor = ArgumentCaptor.forClass(InvStockCheck.class);
        verify(checkMapper).updateInvStockCheck(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(InvStatusConstants.INVALIDATED);
        assertThat(captor.getValue().getLastInvalidReason()).contains("库存快照");
        assertThat(captor.getValue().getLastInvalidDetailSnapshot()).contains("测试商品");
        verify(approvalService, never()).createPendingApproval(any(), any());
    }

    @Test
    @DisplayName("库存失效后重新盘点会刷新账面快照并清空实盘")
    void shouldRestartInvalidatedCheckWithFreshSnapshot()
    {
        SecurityContextHolder.setUserId("7");
        SecurityContextHolder.setUserName("counter");
        InvStockCheckServiceImpl service = new InvStockCheckServiceImpl();
        InvStockCheckMapper checkMapper = mock(InvStockCheckMapper.class);
        InvStockCheckDetailMapper detailMapper = mock(InvStockCheckDetailMapper.class);
        InvStockMapper stockMapper = mock(InvStockMapper.class);
        InvStockCheck check = stockCheck(1001L, 20L, InvStatusConstants.INVALIDATED);
        InvStockCheckDetail detail = stockCheckDetail(501L, 1001L, 101L,
                "测试商品", "10", "8", "-2");
        detail.setItemType("gift");
        detail.setItemId(101L);
        detail.setProductId(null);
        InvStock current = stock(9001L, 101L, 20L, "9", "9");
        when(checkMapper.selectInvStockCheckById(1001L)).thenReturn(check);
        when(checkMapper.selectInvStockCheckByIdForUpdate(1001L)).thenReturn(check);
        when(detailMapper.selectInvStockCheckDetailByCheckIdForUpdate(1001L))
                .thenReturn(List.of(detail));
        when(stockMapper.selectInvStockByItemShopWarehouseForUpdate("gift", 101L, 20L, 20L))
                .thenReturn(current);
        service.deptScopeMapper = deptScopeMapper(Map.of(20L, "WAREHOUSE"));
        ReflectionTestUtils.setField(service, "checkMapper", checkMapper);
        ReflectionTestUtils.setField(service, "checkDetailMapper", detailMapper);
        ReflectionTestUtils.setField(service, "stockMapper", stockMapper);

        service.restartCheck(1001L, 20L);

        verify(detailMapper).resetSnapshot(501L, new BigDecimal("9"), BigDecimal.ONE);
        ArgumentCaptor<InvStockCheck> captor = ArgumentCaptor.forClass(InvStockCheck.class);
        verify(checkMapper).updateInvStockCheck(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(InvStatusConstants.DRAFT);
    }

    @Test
    @DisplayName("取消待审批盘点会同步关闭运行中的审批")
    void shouldCancelPendingCheckAndApproval()
    {
        SecurityContextHolder.setUserId("7");
        SecurityContextHolder.setUserName("counter");
        InvStockCheckServiceImpl service = new InvStockCheckServiceImpl();
        InvStockCheckMapper checkMapper = mock(InvStockCheckMapper.class);
        IInvStockCheckApprovalService approvalService = mock(IInvStockCheckApprovalService.class);
        InvStockCheck check = stockCheck(1001L, 20L, InvStatusConstants.PENDING_APPROVAL);
        when(checkMapper.selectInvStockCheckById(1001L)).thenReturn(check);
        when(checkMapper.selectInvStockCheckByIdForUpdate(1001L)).thenReturn(check);
        service.deptScopeMapper = deptScopeMapper(Map.of(20L, "WAREHOUSE"));
        ReflectionTestUtils.setField(service, "checkMapper", checkMapper);
        ReflectionTestUtils.setField(service, "approvalService", approvalService);

        service.cancelCheck(1001L, 20L);

        verify(approvalService).cancelRunning(1001L);
        ArgumentCaptor<InvStockCheck> captor = ArgumentCaptor.forClass(InvStockCheck.class);
        verify(checkMapper).updateInvStockCheck(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(InvStatusConstants.CANCELLED);
    }

    @Test
    @DisplayName("待我审批列表同时限定当前审批人和组织范围")
    void shouldScopeApprovalTodoListByUserAndShop()
    {
        SecurityContextHolder.setUserId("9");
        SecurityContextHolder.setUserName("ops");
        InvStockCheckServiceImpl service = new InvStockCheckServiceImpl();
        InvStockCheckMapper checkMapper = mock(InvStockCheckMapper.class);
        InvStockCheck query = new InvStockCheck();
        InvStockCheck todo = stockCheck(1001L, 20L, InvStatusConstants.PENDING_APPROVAL);
        service.deptScopeMapper = deptScopeMapper(Map.of(20L, "WAREHOUSE"));
        ReflectionTestUtils.setField(service, "checkMapper", checkMapper);
        when(checkMapper.selectInvStockCheckApprovalTodoList(query)).thenReturn(List.of(todo));

        List<InvStockCheck> result = service.selectApprovalTodoList(query, 20L);

        assertThat(result).containsExactly(todo);
        assertThat(query.getParams()).containsEntry("approvalUserId", 9L);
        assertThat(query.getParams()).containsEntry("scopeDeptIds", List.of(20L));
    }

    @Test
    @DisplayName("已有审批历史的草稿不能物理删除")
    void shouldRejectPhysicalDeleteWhenApprovalHistoryExists()
    {
        InvStockCheckServiceImpl service = new VisibleStockCheckService();
        InvStockCheckMapper checkMapper = mock(InvStockCheckMapper.class);
        InvStockCheckDetailMapper detailMapper = mock(InvStockCheckDetailMapper.class);
        InvStockCheck check = stockCheck(1001L, 201L, InvStatusConstants.DRAFT);
        check.setApprovalRound(1);
        when(checkMapper.selectInvStockCheckById(1001L)).thenReturn(check);
        when(checkMapper.selectInvStockCheckByIdForUpdate(1001L)).thenReturn(check);
        ReflectionTestUtils.setField(service, "checkMapper", checkMapper);
        ReflectionTestUtils.setField(service, "checkDetailMapper", detailMapper);

        assertThatThrownBy(() -> service.deleteCheck(new Long[] { 1001L }, 201L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("已有审批记录");
        verify(detailMapper, never()).deleteInvStockCheckDetailByCheckId(anyLong());
    }

    @Test
    @DisplayName("库存盘点控制器只暴露审批制接口并使用正确权限")
    void shouldExposeApprovalControllerContract()
    {
        Method[] methods = InvStockCheckController.class.getDeclaredMethods();

        assertThat(hasPostMapping(methods, "/input/{checkId}")).isTrue();
        assertThat(hasPostMapping(methods, "/edit")).isFalse();
        assertThat(hasPostMapping(methods, "/submit/{checkId}")).isTrue();
        assertThat(hasPostMapping(methods, "/restart/{checkId}")).isTrue();
        assertThat(hasPostMapping(methods, "/approval/{checkId}/approve")).isTrue();
        assertThat(hasPostMapping(methods, "/approval/{checkId}/reject")).isTrue();
        assertThat(hasGetMapping(methods, "/approval/todo")).isTrue();
        assertThat(hasGetMapping(methods, "/counter-candidates")).isTrue();
        assertThat(hasPutMapping(methods, "/{checkId}/assignment")).isTrue();
        assertThat(hasGetMapping(methods, "/{checkId}/approval-track")).isTrue();
        assertThat(hasPostMapping(methods, "/confirm/{checkId}")).isFalse();

        assertPermission(findPostMethod(methods, "/input/{checkId}"), "inv:stockCheck:submit");
        assertPermission(findPostMethod(methods, "/submit/{checkId}"), "inv:stockCheck:submit");
        assertPermission(findPostMethod(methods, "/restart/{checkId}"), "inv:stockCheck:submit");
        assertPermission(findGetMethod(methods, "/approval/todo"), "inv:stockCheck:approve");
        assertPermission(findPostMethod(methods, "/approval/{checkId}/approve"),
                "inv:stockCheck:approve");
        assertPermission(findPostMethod(methods, "/approval/{checkId}/reject"),
                "inv:stockCheck:approve");
        RequiresPermissions approvalTrackPermission = findGetMethod(
                methods, "/{checkId}/approval-track")
                .getAnnotation(RequiresPermissions.class);
        assertThat(approvalTrackPermission.value()).containsExactly(
                "inv:stockCheck:query", "inv:stockCheck:approve",
                "inv:stockCheck:submit");
        assertThat(approvalTrackPermission.logical()).isEqualTo(Logical.OR);
        assertPermission(findPutMethod(methods, "/{checkId}/assignment"),
                "inv:stockCheck:edit");
        RequiresPermissions candidatesPermission = findGetMethod(methods, "/counter-candidates")
                .getAnnotation(RequiresPermissions.class);
        assertThat(candidatesPermission.value())
                .containsExactly("inv:stockCheck:add", "inv:stockCheck:edit");
        assertThat(candidatesPermission.logical()).isEqualTo(Logical.OR);
        assertIdempotent(findPostMethod(methods, "/submit/{checkId}"));
        assertIdempotent(findPostMethod(methods, "/restart/{checkId}"));
        assertIdempotent(findPutMethod(methods, "/{checkId}/assignment"));
        assertIdempotent(findPostMethod(methods, "/approval/{checkId}/approve"));
        assertIdempotent(findPostMethod(methods, "/approval/{checkId}/reject"));

        Method deleteMethod = Arrays.stream(methods)
                .filter(method -> {
                    DeleteMapping mapping = method.getAnnotation(DeleteMapping.class);
                    return mapping != null && Arrays.asList(mapping.value()).contains("/{checkIds}");
                })
                .findFirst()
                .orElseThrow(() -> new AssertionError("缺少 DELETE /stockCheck/{checkIds}"));

        assertThat(Arrays.stream(deleteMethod.getParameters())
                .anyMatch(parameter -> parameter.getType().equals(Long[].class)
                        && parameter.isAnnotationPresent(PathVariable.class)
                        && "checkIds".equals(parameter.getAnnotation(PathVariable.class).value())))
                .isTrue();
    }

    @Test
    @DisplayName("库存盘点控制器暴露取消端点并保留删除草稿端点")
    void shouldExposeCancelAndDeleteDraftContracts() throws Exception
    {
        Method cancelMethod = InvStockCheckController.class.getDeclaredMethod(
                "cancel", Long.class, jakarta.servlet.http.HttpServletRequest.class,
                jakarta.servlet.http.HttpServletResponse.class);
        PostMapping cancelMapping = cancelMethod.getAnnotation(PostMapping.class);
        assertThat(cancelMapping).isNotNull();
        assertThat(Arrays.asList(cancelMapping.value())).contains("/cancel/{checkId}");

        Method deleteMethod = Arrays.stream(InvStockCheckController.class.getDeclaredMethods())
                .filter(method -> {
                    DeleteMapping mapping = method.getAnnotation(DeleteMapping.class);
                    return mapping != null && Arrays.asList(mapping.value()).contains("/{checkIds}");
                })
                .findFirst()
                .orElseThrow(() -> new AssertionError("缺少 DELETE /stockCheck/{checkIds}"));

        assertThat(deleteMethod.getName()).isEqualTo("delete");
    }

    @Test
    @DisplayName("盘点中状态不能被物理删除")
    void shouldRejectPhysicalDeleteForCheckingStockCheck()
    {
        InvStockCheckServiceImpl service = new VisibleStockCheckService();
        ReflectionTestUtils.setField(service, "checkMapper", stockCheckMapperReturning(InvStatusConstants.CHECKING));
        ReflectionTestUtils.setField(service, "checkDetailMapper", failOnPhysicalDeleteDetailMapper());

        assertThatThrownBy(() -> service.deleteCheck(new Long[] { 1001L }, 201L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("当前状态不允许删除草稿");
    }

    @Test
    @DisplayName("仓库上下文不能创建门店库存盘点")
    void shouldRejectWarehouseCreatingStoreStockCheck()
    {
        SecurityContextHolder.setUserId("1");
        InvStockCheckServiceImpl service = new InvStockCheckServiceImpl();
        service.deptScopeMapper = deptScopeMapper(Map.of(20L, "WAREHOUSE", 10L, "STORE"));
        InvStockCheck check = new InvStockCheck();
        check.setWarehouseId(10L);

        assertThatThrownBy(() -> service.createCheck(check, 20L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("只能盘点当前组织库存");
    }

    @Test
    @DisplayName("盘点服务不再暴露直接确认能力")
    void shouldRemoveDirectStockCheckConfirmation()
    {
        assertThat(Arrays.stream(IInvStockCheckService.class.getDeclaredMethods())
                .noneMatch(method -> "confirmCheck".equals(method.getName()))).isTrue();
    }

    @Test
    @DisplayName("创建指定商品盘点时只生成所选商品快照")
    void shouldCreateStockCheckSnapshotForSelectedProductsOnly()
    {
        SecurityContextHolder.setUserId("1");
        SecurityContextHolder.setUserName("admin");
        InvStockCheckServiceImpl service = new InvStockCheckServiceImpl();
        InvStockCheckMapper checkMapper = mock(InvStockCheckMapper.class);
        InvStockCheckDetailMapper detailMapper = mock(InvStockCheckDetailMapper.class);
        InvNumberSequenceMapper numberSequenceMapper = mock(InvNumberSequenceMapper.class);
        InvStockCheck check = new InvStockCheck();
        check.setWarehouseId(20L);
        check.setCounterUserId(7L);
        check.setCounterName("客户端伪造姓名");
        check.setDeadline(futureDeadline());
        check.setDetails(List.of(requestedStockCheckProduct(101L), requestedStockCheckProduct(303L)));

        service.deptScopeMapper = deptScopeMapper(Map.of(20L, "WAREHOUSE"));
        ReflectionTestUtils.setField(service, "checkMapper", checkMapper);
        ReflectionTestUtils.setField(service, "checkDetailMapper", detailMapper);
        ReflectionTestUtils.setField(service, "numberSequenceMapper", numberSequenceMapper);

        doAnswer(invocation -> {
            InvStockCheck inserted = invocation.getArgument(0);
            inserted.setCheckId(1001L);
            return 1;
        }).when(checkMapper).insertInvStockCheck(any(InvStockCheck.class));
        when(numberSequenceMapper.insertOrUpdateSequence(any(), any(), eq(0), any())).thenReturn(1);
        when(numberSequenceMapper.incrementAndGetSequence(any(), any())).thenReturn(1);
        when(numberSequenceMapper.selectLastInsertId()).thenReturn(1L);
        when(checkMapper.selectInvStockCheckById(1001L)).thenReturn(check);
        when(checkMapper.selectCounterCandidate(20L, 7L))
                .thenReturn(counterCandidate(7L, "counter", "真实盘点人"));
        when(detailMapper.selectInvStockCheckDetailByCheckId(1001L)).thenReturn(Collections.emptyList());
        when(detailMapper.selectInvStockCheckDetailByCheckIdForUpdate(1001L)).thenReturn(Collections.emptyList());
        when(detailMapper.selectStockForCheck(20L, 20L)).thenReturn(List.of(
                stockSnapshotRow(101L, "明前龙井", "8.00"),
                stockSnapshotRow(202L, "清香铁观音", "12.00")));

        service.createCheck(check, 20L);

        @SuppressWarnings({ "unchecked", "rawtypes" })
        ArgumentCaptor<List<InvStockCheckDetail>> detailCaptor = ArgumentCaptor.forClass((Class) List.class);
        verify(detailMapper).batchInsertInvStockCheckDetail(detailCaptor.capture());
        List<InvStockCheckDetail> createdDetails = detailCaptor.getValue();

        assertThat(createdDetails).hasSize(1);
        assertThat(createdDetails.get(0).getProductId()).isEqualTo(101L);
        assertThat(createdDetails.get(0).getProductName()).isEqualTo("明前龙井");
        assertThat(createdDetails.get(0).getBookQty()).isEqualByComparingTo("8.00");
        assertThat(check.getCounterName()).isEqualTo("真实盘点人");
    }

    @Test
    @DisplayName("创建指定商品盘点时只生成所选商品快照")
    void shouldKeepAllThreeMaterialIdentitiesInFullStockCheck()
    {
        SecurityContextHolder.setUserId("1");
        SecurityContextHolder.setUserName("admin");
        InvStockCheckServiceImpl service = new InvStockCheckServiceImpl();
        InvStockCheckMapper checkMapper = mock(InvStockCheckMapper.class);
        InvStockCheckDetailMapper detailMapper = mock(InvStockCheckDetailMapper.class);
        InvNumberSequenceMapper numberSequenceMapper = mock(InvNumberSequenceMapper.class);
        InvStockCheck check = new InvStockCheck();
        check.setWarehouseId(20L);
        check.setCounterUserId(7L);
        check.setCounterName("客户端伪造姓名");
        check.setDeadline(futureDeadline());
        check.setCheckScope("all");

        service.deptScopeMapper = deptScopeMapper(Map.of(20L, "WAREHOUSE"));
        ReflectionTestUtils.setField(service, "checkMapper", checkMapper);
        ReflectionTestUtils.setField(service, "checkDetailMapper", detailMapper);
        ReflectionTestUtils.setField(service, "numberSequenceMapper", numberSequenceMapper);

        doAnswer(invocation -> {
            InvStockCheck inserted = invocation.getArgument(0);
            inserted.setCheckId(1001L);
            return 1;
        }).when(checkMapper).insertInvStockCheck(any(InvStockCheck.class));
        when(numberSequenceMapper.insertOrUpdateSequence(any(), any(), eq(0), any())).thenReturn(1);
        when(numberSequenceMapper.incrementAndGetSequence(any(), any())).thenReturn(1);
        when(numberSequenceMapper.selectLastInsertId()).thenReturn(1L);
        when(checkMapper.selectInvStockCheckById(1001L)).thenReturn(check);
        when(checkMapper.selectCounterCandidate(20L, 7L))
                .thenReturn(counterCandidate(7L, "counter", "真实盘点人"));
        when(detailMapper.selectInvStockCheckDetailByCheckId(1001L)).thenReturn(Collections.emptyList());
        when(detailMapper.selectInvStockCheckDetailByCheckIdForUpdate(1001L)).thenReturn(Collections.emptyList());
        Map<String, Object> oe = stockSnapshotRow(101L, "OE物料", "12.00");
        oe.remove("productId"); oe.put("itemType", "oe"); oe.put("itemId", 101L);
        Map<String, Object> gift = stockSnapshotRow(101L, "礼盒", "15.00");
        gift.remove("productId"); gift.put("itemType", "gift"); gift.put("itemId", 101L);
        when(detailMapper.selectStockForCheck(20L, 20L)).thenReturn(List.of(
                stockSnapshotRow(101L, "明前龙井", "8.00"), oe, gift));

        service.createCheck(check, 20L);

        @SuppressWarnings({ "unchecked", "rawtypes" })
        ArgumentCaptor<List<InvStockCheckDetail>> detailCaptor = ArgumentCaptor.forClass((Class) List.class);
        verify(detailMapper).batchInsertInvStockCheckDetail(detailCaptor.capture());
        List<InvStockCheckDetail> createdDetails = detailCaptor.getValue();

        assertThat(createdDetails).hasSize(3);
        assertThat(createdDetails).extracting(row -> row.getItemType() + ":" + row.getItemId())
                .containsExactly("product:101", "oe:101", "gift:101");
        assertThat(createdDetails.get(1).getProductId()).isNull();
        assertThat(createdDetails.get(2).getProductId()).isNull();
        assertThat(createdDetails.get(0).getProductId()).isEqualTo(101L);
        assertThat(createdDetails.get(0).getProductName()).isEqualTo("明前龙井");
        assertThat(createdDetails.get(0).getBookQty()).isEqualByComparingTo("8.00");
        assertThat(check.getCounterName()).isEqualTo("真实盘点人");
    }

    private boolean hasPostMapping(Method[] methods, String path)
    {
        return Arrays.stream(methods)
                .map(method -> method.getAnnotation(PostMapping.class))
                .filter(mapping -> mapping != null)
                .anyMatch(mapping -> Arrays.asList(mapping.value()).contains(path));
    }

    private boolean hasGetMapping(Method[] methods, String path)
    {
        return Arrays.stream(methods)
                .map(method -> method.getAnnotation(GetMapping.class))
                .filter(mapping -> mapping != null)
                .anyMatch(mapping -> Arrays.asList(mapping.value()).contains(path));
    }

    private boolean hasPutMapping(Method[] methods, String path)
    {
        return Arrays.stream(methods)
                .map(method -> method.getAnnotation(PutMapping.class))
                .filter(mapping -> mapping != null)
                .anyMatch(mapping -> Arrays.asList(mapping.value()).contains(path));
    }

    private Method findPostMethod(Method[] methods, String path)
    {
        return Arrays.stream(methods)
                .filter(method -> {
                    PostMapping mapping = method.getAnnotation(PostMapping.class);
                    return mapping != null && Arrays.asList(mapping.value()).contains(path);
                })
                .findFirst()
                .orElseThrow(() -> new AssertionError("缺少 POST " + path));
    }

    private Method findGetMethod(Method[] methods, String path)
    {
        return Arrays.stream(methods)
                .filter(method -> {
                    GetMapping mapping = method.getAnnotation(GetMapping.class);
                    return mapping != null && Arrays.asList(mapping.value()).contains(path);
                })
                .findFirst()
                .orElseThrow(() -> new AssertionError("缺少 GET " + path));
    }

    private Method findPutMethod(Method[] methods, String path)
    {
        return Arrays.stream(methods)
                .filter(method -> {
                    PutMapping mapping = method.getAnnotation(PutMapping.class);
                    return mapping != null && Arrays.asList(mapping.value()).contains(path);
                })
                .findFirst()
                .orElseThrow(() -> new AssertionError("缺少 PUT " + path));
    }

    private void assertPermission(Method method, String permission)
    {
        RequiresPermissions annotation = method.getAnnotation(RequiresPermissions.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).containsExactly(permission);
    }

    private void assertIdempotent(Method method)
    {
        IdempotentSubmit annotation = method.getAnnotation(IdempotentSubmit.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.timeout()).isEqualTo(30);
    }

    private static InvStockCheck stockCheck(Long checkId, Long shopDeptId, String status)
    {
        InvStockCheck check = new InvStockCheck();
        check.setCheckId(checkId);
        check.setCheckNo("SC202606100001");
        check.setShopDeptId(shopDeptId);
        check.setWarehouseId(shopDeptId);
        check.setStatus(status);
        check.setCounterUserId(7L);
        return check;
    }

    private static InvStockCheckCounterCandidate counterCandidate(Long userId,
            String userName, String displayName)
    {
        InvStockCheckCounterCandidate candidate = new InvStockCheckCounterCandidate();
        candidate.setUserId(userId);
        candidate.setUserName(userName);
        candidate.setDisplayName(displayName);
        return candidate;
    }

    private static Date futureDeadline()
    {
        return new Date(System.currentTimeMillis() + 86_400_000L);
    }

    private static InvStockCheckDetail stockCheckDetail(Long detailId, Long checkId, Long productId,
            String productName, String bookQty, String actualQty, String diffQty)
    {
        InvStockCheckDetail detail = new InvStockCheckDetail();
        detail.setDetailId(detailId);
        detail.setCheckId(checkId);
        detail.setProductId(productId);
        detail.setProductName(productName);
        detail.setBookQty(new BigDecimal(bookQty));
        detail.setActualQty(new BigDecimal(actualQty));
        detail.setDiffQty(new BigDecimal(diffQty));
        detail.setDiffType(InvStockCheckServiceImpl.resolveDiffType(detail.getDiffQty()));
        detail.setCostPrice(BigDecimal.ONE);
        return detail;
    }

    private static InvStockCheckDetail requestedStockCheckProduct(Long productId)
    {
        InvStockCheckDetail detail = new InvStockCheckDetail();
        detail.setProductId(productId);
        return detail;
    }

    private static Map<String, Object> stockSnapshotRow(Long productId, String productName, String currentQuantity)
    {
        Map<String, Object> row = new HashMap<>();
        row.put("productId", productId);
        row.put("productName", productName);
        row.put("productCode", "P-" + productId);
        row.put("unit", "斤");
        row.put("spec", "500g");
        row.put("currentQuantity", new BigDecimal(currentQuantity));
        row.put("costPrice", BigDecimal.ONE);
        return row;
    }

    private static InvStock stock(Long stockId, Long productId, Long shopDeptId, String currentQty, String availableQty)
    {
        InvStock stock = new InvStock();
        stock.setStockId(stockId);
        stock.setProductId(productId);
        stock.setShopDeptId(shopDeptId);
        stock.setWarehouseId(shopDeptId);
        stock.setCurrentQuantity(new BigDecimal(currentQty));
        stock.setAvailableQuantity(new BigDecimal(availableQty));
        stock.setCostPrice(BigDecimal.ONE);
        return stock;
    }

    private static InvStockCheckAdjustmentResult staleResult(InvStockCheckDetail detail)
    {
        InvStockCheckSnapshotChange change = new InvStockCheckSnapshotChange();
        change.setDetailId(detail.getDetailId());
        change.setProductId(detail.getProductId());
        change.setProductName(detail.getProductName());
        change.setBookQuantity(detail.getBookQty());
        change.setCurrentQuantity(new BigDecimal("9"));
        InvStockCheckAdjustmentResult result = new InvStockCheckAdjustmentResult();
        result.getSnapshotChanges().add(change);
        return result;
    }

    private static void configureApprovalFlow(InvStockCheckServiceImpl service,
            InvStockCheckMapper checkMapper, InvStockCheckDetailMapper detailMapper,
            InvStockCheckAdjustmentService adjustmentService,
            IInvStockCheckApprovalService approvalService)
    {
        service.deptScopeMapper = deptScopeMapper(Map.of(20L, "WAREHOUSE"));
        ReflectionTestUtils.setField(service, "checkMapper", checkMapper);
        ReflectionTestUtils.setField(service, "checkDetailMapper", detailMapper);
        ReflectionTestUtils.setField(service, "adjustmentService", adjustmentService);
        ReflectionTestUtils.setField(service, "approvalService", approvalService);
        ReflectionTestUtils.setField(service, "unifiedApprovalService",
                mock(InventoryUnifiedApprovalService.class));
    }

    private static InvStockCheckMapper stockCheckMapperReturning(String status)
    {
        return (InvStockCheckMapper) Proxy.newProxyInstance(
                InvStockCheckMapper.class.getClassLoader(),
                new Class<?>[] { InvStockCheckMapper.class },
                (proxy, method, args) -> {
                    if ("selectInvStockCheckById".equals(method.getName())
                            || "selectInvStockCheckByIdForUpdate".equals(method.getName()))
                    {
                        InvStockCheck check = new InvStockCheck();
                        check.setCheckId((Long) args[0]);
                        check.setShopDeptId(201L);
                        check.setStatus(status);
                        return check;
                    }
                    if ("deleteInvStockCheckByIds".equals(method.getName()))
                    {
                        throw new AssertionError("checking stock check must not be physically deleted");
                    }
                    return defaultValue(method.getReturnType());
                });
    }

    private static InvStockCheckMapper stockCheckMapperReturningDept(String status, Long shopDeptId)
    {
        return (InvStockCheckMapper) Proxy.newProxyInstance(
                InvStockCheckMapper.class.getClassLoader(),
                new Class<?>[] { InvStockCheckMapper.class },
                (proxy, method, args) -> {
                    if ("selectInvStockCheckById".equals(method.getName())
                            || "selectInvStockCheckByIdForUpdate".equals(method.getName()))
                    {
                        InvStockCheck check = new InvStockCheck();
                        check.setCheckId((Long) args[0]);
                        check.setShopDeptId(shopDeptId);
                        check.setWarehouseId(shopDeptId);
                        check.setStatus(status);
                        return check;
                    }
                    return defaultValue(method.getReturnType());
                });
    }

    private static InvDeptScopeMapper deptScopeMapper(Map<Long, String> deptTypes)
    {
        return new InvDeptScopeMapper()
        {
            @Override public List<Long> selectUserAuthorizedInventoryDeptIds(Long userId) { return java.util.Collections.emptyList(); }
            @Override public List<Long> selectAllActiveInventoryDeptIds() { return java.util.Collections.emptyList(); }
            private final Map<Long, String> types = new HashMap<>(deptTypes);

            @Override
            public List<Long> selectSubDeptIds(Long deptId)
            {
                return Collections.singletonList(deptId);
            }

            @Override
            public List<Long> selectRelatedDeptIds(Long deptId)
            {
                return Collections.singletonList(deptId);
            }

            @Override
            public List<Long> selectActiveRelatedDeptIdsForReplenishment(
                    Long deptId)
            {
                return selectRelatedDeptIds(deptId);
            }

            @Override
            public List<Long> selectAncestorDeptIds(Long deptId)
            {
                return Collections.emptyList();
            }

            @Override
            public Long selectRawBusinessRootDeptId(Long deptId)
            {
                return deptId;
            }

            @Override
            public List<Long> selectUserStoreScopeDeptIds(Long userId)
            {
                return Collections.emptyList();
            }

            @Override
            public List<Long> selectAllStoreDeptIds()
            {
                return Collections.emptyList();
            }

            @Override
            public int countDeptInScope(Long scopeDeptId, Long targetDeptId)
            {
                return 1;
            }

            @Override
            public int countUserShopScope(Long userId, Long deptId)
            {
                return 1;
            }

            @Override
            public String selectDeptNameById(Long deptId)
            {
                return "测试组织";
            }

            @Override
            public String selectDeptTypeById(Long deptId)
            {
                return types.get(deptId);
            }
        };
    }

    private static InvStockCheckDetailMapper failOnPhysicalDeleteDetailMapper()
    {
        return (InvStockCheckDetailMapper) Proxy.newProxyInstance(
                InvStockCheckDetailMapper.class.getClassLoader(),
                new Class<?>[] { InvStockCheckDetailMapper.class },
                (proxy, method, args) -> {
                    if (method.getName().startsWith("delete"))
                    {
                        throw new AssertionError("checking stock check detail must not be physically deleted");
                    }
                    return defaultValue(method.getReturnType());
                });
    }

    private static Object defaultValue(Class<?> returnType)
    {
        if (!returnType.isPrimitive())
        {
            return null;
        }
        if (returnType.equals(boolean.class))
        {
            return false;
        }
        if (returnType.equals(void.class))
        {
            return null;
        }
        return 0;
    }

    private static class VisibleStockCheckService extends InvStockCheckServiceImpl
    {
        @Override
        protected void assertShopVisible(Long shopDeptId, Long selectedShopDeptId, String errorMsg)
        {
            // Test scope is status transition protection, not shop visibility.
        }
    }

    private static class PermissionAwareStockCheckService extends InvStockCheckServiceImpl
    {
        private final boolean manage;

        private PermissionAwareStockCheckService(boolean manage)
        {
            this.manage = manage;
        }

        @Override
        protected boolean hasPermission(String permission)
        {
            return manage && "inv:stockCheck:edit".equals(permission);
        }
    }
}
