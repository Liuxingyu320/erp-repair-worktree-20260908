package com.erp.inventory.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import com.erp.common.core.exception.ServiceException;
import com.erp.approval.api.domain.ApprovalStartRequest;
import com.erp.inventory.domain.InvTransferOrder;
import org.springframework.test.util.ReflectionTestUtils;
import com.erp.common.core.context.SecurityContextHolder;
import com.erp.common.core.constant.SecurityConstants;
import com.erp.system.api.domain.SysUser;
import com.erp.system.api.model.LoginUser;
import com.erp.inventory.constant.InvStatusConstants;
import com.erp.inventory.constant.InvTransferSourceConfirmStatus;
import com.erp.inventory.constant.InvTransferTypes;
import com.erp.inventory.domain.InvStock;
import com.erp.inventory.domain.InvStockLog;
import com.erp.inventory.domain.InvTransferApprovalInstance;
import com.erp.inventory.domain.InvTransferApprovalStartOutbox;
import com.erp.inventory.domain.InvTransferApprovalTask;
import com.erp.inventory.domain.InvTransferDetail;
import com.erp.inventory.domain.InvTransferDiscrepancy;
import com.erp.inventory.domain.InvTransferDiscrepancyDetail;
import com.erp.inventory.domain.InvTransferDiscrepancyDisposition;
import com.erp.inventory.domain.InvTransferShipment;
import com.erp.inventory.domain.InvTransferShipmentDetail;
import com.erp.inventory.domain.InvTransferStatusLog;
import com.erp.inventory.domain.dto.InvDeliverItem;
import com.erp.inventory.domain.dto.InvDeliverRequest;
import com.erp.inventory.domain.dto.InvReceiveRequest;
import com.erp.inventory.domain.dto.InvTransferSourceConfirmItem;
import com.erp.inventory.domain.dto.InvTransferSourceConfirmRequest;
import com.erp.inventory.mapper.InvDeptScopeMapper;
import com.erp.inventory.mapper.InvNumberSequenceMapper;
import com.erp.inventory.mapper.InvStockLogMapper;
import com.erp.inventory.mapper.InvStockMapper;
import com.erp.inventory.mapper.InvTransferDetailMapper;
import com.erp.inventory.mapper.InvTransferDiscrepancyDispositionMapper;
import com.erp.inventory.mapper.InvTransferDiscrepancyMapper;
import com.erp.inventory.mapper.InvTransferOrderMapper;
import com.erp.inventory.mapper.InvTransferReservationMapper;
import com.erp.inventory.mapper.InvTransferShipmentDetailMapper;
import com.erp.inventory.mapper.InvTransferShipmentMapper;
import com.erp.inventory.mapper.InvTransferStatusLogMapper;
import com.erp.inventory.service.IInvTransferApprovalService;
import com.erp.inventory.domain.vo.InvTransferApprovalSummary;
import com.erp.inventory.domain.vo.InvTransferApprovalTrack;
import com.erp.inventory.domain.vo.InvTransferApprovalPreview;
import com.erp.inventory.domain.vo.InvTransferSourceConfirmResult;
import com.erp.inventory.domain.vo.InvStockSummary;
import com.erp.inventory.domain.vo.InventoryItemSnapshot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("调拨服务")
class InvTransferServiceImplTest
{
    @AfterEach
    void tearDown()
    {
        SecurityContextHolder.remove();
    }

    @Test
    @DisplayName("提交调拨单时创建审批快照并写状态日志")
    void shouldCreateApprovalSnapshotWhenTransferSubmitted()
    {
        SecurityContextHolder.setUserId("1");
        SecurityContextHolder.setUserName("admin");
        FakeTransferOrderMapper orderMapper = new FakeTransferOrderMapper();
        FakeTransferDetailMapper detailMapper = new FakeTransferDetailMapper();
        FakeApprovalService approvalService = new FakeApprovalService();
        FakeStatusLogMapper statusLogMapper = new FakeStatusLogMapper();
        InvTransferServiceImpl service = new InvTransferServiceImpl();
        ReflectionTestUtils.setField(service, "transferOrderMapper", orderMapper);
        ReflectionTestUtils.setField(service, "transferDetailMapper", detailMapper);
        ReflectionTestUtils.setField(service, "numberSequenceMapper", new FakeNumberSequenceMapper());
        ReflectionTestUtils.setField(service, "deptScopeMapper", new FakeDeptScopeMapper());
        ReflectionTestUtils.setField(service, "transferApprovalService", approvalService);
        configureLegacyUnifiedApproval(service);
        ReflectionTestUtils.setField(service, "statusLogMapper", statusLogMapper);
        ReflectionTestUtils.setField(service, "itemResolver", itemResolver());
        InvTransferReservationService reservationService =
                mock(InvTransferReservationService.class);
        ReflectionTestUtils.setField(service, "transferReservationService",
                reservationService);
        InvTransferRevisionService revisionService =
                mock(InvTransferRevisionService.class);
        ReflectionTestUtils.setField(service, "transferRevisionService",
                revisionService);
        InvTransferOrder order = transferOrder();

        InvTransferOrder submitted = service.submitTransfer(order, Collections.singletonList(detail()), 202L);

        assertThat(approvalService.submittedTransfer.getTransferId()).isEqualTo(900L);
        assertThat(orderMapper.updates).hasSize(2);
        InvTransferOrder submitUpdate = orderMapper.updates.get(1);
        assertThat(submitUpdate.getStatus()).isEqualTo(InvStatusConstants.SUBMITTED);
        assertThat(submitUpdate.getApprovalInstanceId()).isEqualTo(700L);
        assertThat(submitUpdate.getSubmittedTime()).isNotNull();
        assertThat(statusLogMapper.logs).hasSize(1);
        InvTransferStatusLog log = statusLogMapper.logs.get(0);
        assertThat(log.getTransferId()).isEqualTo(900L);
        assertThat(log.getFromStatus()).isEqualTo(InvStatusConstants.DRAFT);
        assertThat(log.getToStatus()).isEqualTo(InvStatusConstants.SUBMITTED);
        assertThat(log.getAction()).isEqualTo("submit");
        assertThat(log.getOperatorId()).isEqualTo(1L);
        assertThat(log.getOperatorName()).isEqualTo("admin");
        assertThat(submitted.getStatus()).isEqualTo(InvStatusConstants.SUBMITTED);
        assertThat(submitted.getApprovalInstanceId()).isEqualTo(700L);
        org.mockito.Mockito.verify(reservationService)
                .reserveForSubmission(
                        org.mockito.ArgumentMatchers.any(
                                InvTransferOrder.class),
                        org.mockito.ArgumentMatchers.anyList(),
                        org.mockito.ArgumentMatchers.eq("admin"));
        org.mockito.Mockito.verify(revisionService).syncDraft(
                org.mockito.ArgumentMatchers.any(InvTransferOrder.class),
                org.mockito.ArgumentMatchers.anyList(),
                org.mockito.ArgumentMatchers.eq("admin"));
        org.mockito.Mockito.verify(revisionService).sealForSubmission(
                org.mockito.ArgumentMatchers.any(InvTransferOrder.class),
                org.mockito.ArgumentMatchers.anyList(),
                org.mockito.ArgumentMatchers.eq(1),
                org.mockito.ArgumentMatchers.eq(700L),
                org.mockito.ArgumentMatchers.eq(false),
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.eq("admin"));
    }

    @Test
    @DisplayName("普通调拨忽略客户端成本并以商品档案金额发起审批")
    void shouldIgnoreClientCostAndUseMasterCostForApproval()
    {
        SecurityContextHolder.setUserId("1");
        SecurityContextHolder.setUserName("admin");
        FakeTransferOrderMapper orderMapper = new FakeTransferOrderMapper();
        FakeTransferDetailMapper detailMapper = new FakeTransferDetailMapper();
        FakeApprovalService approvalService = new FakeApprovalService();
        InvTransferServiceImpl service = new InvTransferServiceImpl();
        ReflectionTestUtils.setField(service, "transferOrderMapper", orderMapper);
        ReflectionTestUtils.setField(service, "transferDetailMapper", detailMapper);
        ReflectionTestUtils.setField(service, "numberSequenceMapper",
                new FakeNumberSequenceMapper());
        ReflectionTestUtils.setField(service, "deptScopeMapper",
                new FakeDeptScopeMapper());
        ReflectionTestUtils.setField(service, "transferApprovalService",
                approvalService);
        configureLegacyUnifiedApproval(service);
        ReflectionTestUtils.setField(service, "statusLogMapper",
                new FakeStatusLogMapper());
        ReflectionTestUtils.setField(service, "itemResolver",
                itemResolver());
        ReflectionTestUtils.setField(service, "transferReservationService",
                new FakeTransferReservationService(new FakeStockMapper()));
        ReflectionTestUtils.setField(service, "transferRevisionService",
                mock(InvTransferRevisionService.class));
        InvTransferDetail malicious = detail();
        malicious.setQuantity(new BigDecimal("3"));
        malicious.setCostPrice(new BigDecimal("0.01"));
        malicious.setAmount(new BigDecimal("0.03"));

        InvTransferOrder submitted = service.submitTransfer(transferOrder(),
                Collections.singletonList(malicious), 202L);

        assertThat(detailMapper.details.get(0).getCostPrice())
                .isEqualByComparingTo("1.00");
        assertThat(detailMapper.details.get(0).getAmount())
                .isEqualByComparingTo("3.00");
        assertThat(submitted.getTotalAmount()).isEqualByComparingTo("3.00");
        assertThat(approvalService.submittedTransfer.getTotalAmount())
                .isEqualByComparingTo("3.00");
    }

    @Test
    @DisplayName("原生审批提交仅在本地事务入队并注册提交后派发")
    void shouldEnqueueNativeApprovalWithoutCallingRemoteInTransaction()
    {
        SecurityContextHolder.setUserId("1");
        SecurityContextHolder.setUserName("admin");
        FakeTransferOrderMapper orderMapper = new FakeTransferOrderMapper();
        FakeTransferDetailMapper detailMapper = new FakeTransferDetailMapper();
        FakeStatusLogMapper statusLogMapper = new FakeStatusLogMapper();
        InvTransferServiceImpl service = new InvTransferServiceImpl();
        ReflectionTestUtils.setField(service, "transferOrderMapper",
                orderMapper);
        ReflectionTestUtils.setField(service, "transferDetailMapper",
                detailMapper);
        ReflectionTestUtils.setField(service, "numberSequenceMapper",
                new FakeNumberSequenceMapper());
        ReflectionTestUtils.setField(service, "deptScopeMapper",
                new FakeDeptScopeMapper());
        ReflectionTestUtils.setField(service, "transferApprovalService",
                mock(IInvTransferApprovalService.class));
        InventoryUnifiedApprovalService unified = mock(
                InventoryUnifiedApprovalService.class);
        org.mockito.Mockito.when(unified.useNativeTransfer(
                org.mockito.ArgumentMatchers.any())).thenReturn(true);
        ApprovalStartRequest request = new ApprovalStartRequest();
        request.setBusinessCode(InventoryUnifiedApprovalService.TRANSFER);
        request.setBusinessId("900");
        request.setBusinessRound(1);
        request.setApplicantId(1L);
        request.setIdempotencyKey("INV_TRANSFER:900:1");
        org.mockito.Mockito.when(unified.buildTransferStartRequest(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(1))).thenReturn(request);
        ReflectionTestUtils.setField(service, "unifiedApprovalService",
                unified);
        InvTransferApprovalStartOutboxService outboxService = mock(
                InvTransferApprovalStartOutboxService.class);
        InvTransferApprovalStartOutbox outbox =
                new InvTransferApprovalStartOutbox();
        outbox.setOutboxId(77L);
        org.mockito.Mockito.when(outboxService.enqueue(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.same(request),
                org.mockito.ArgumentMatchers.eq("admin"))).thenReturn(outbox);
        ReflectionTestUtils.setField(service, "approvalStartOutboxService",
                outboxService);
        InvTransferApprovalStartAfterCommitTrigger trigger = mock(
                InvTransferApprovalStartAfterCommitTrigger.class);
        ReflectionTestUtils.setField(service,
                "approvalStartAfterCommitTrigger", trigger);
        ReflectionTestUtils.setField(service, "statusLogMapper",
                statusLogMapper);
        ReflectionTestUtils.setField(service, "itemResolver",
                itemResolver());
        ReflectionTestUtils.setField(service, "transferReservationService",
                new FakeTransferReservationService(new FakeStockMapper()));
        ReflectionTestUtils.setField(service, "transferRevisionService",
                mock(InvTransferRevisionService.class));

        InvTransferOrder submitted = service.submitTransfer(transferOrder(),
                Collections.singletonList(detail()), 202L);

        InvTransferOrder submitUpdate = orderMapper.updates.get(1);
        assertThat(submitUpdate.getStatus())
                .isEqualTo(InvStatusConstants.SUBMITTED);
        assertThat(submitUpdate.getApprovalEngine())
                .isEqualTo(InventoryUnifiedApprovalService.ENGINE_NATIVE);
        assertThat(submitUpdate.getApprovalRound()).isEqualTo(1);
        assertThat(submitUpdate.getApprovalInstanceId()).isNull();
        org.mockito.Mockito.verify(outboxService).enqueue(
                org.mockito.ArgumentMatchers.same(submitted),
                org.mockito.ArgumentMatchers.same(request),
                org.mockito.ArgumentMatchers.eq("admin"));
        org.mockito.Mockito.verify(trigger).trigger(77L);
    }

    @Test
    @DisplayName("提交调拨单时返回动态审批提醒并写入状态日志")
    void shouldReturnApprovalWarningsAndPersistThemInStatusLog()
    {
        SecurityContextHolder.setUserId("1");
        SecurityContextHolder.setUserName("admin");
        String warning = "未找到四级门店最高领导，本次审批从三级部门开始";
        FakeTransferOrderMapper orderMapper = new FakeTransferOrderMapper();
        FakeTransferDetailMapper detailMapper = new FakeTransferDetailMapper();
        FakeApprovalService approvalService = new FakeApprovalService();
        approvalService.approvalWarnings = Collections.singletonList(warning);
        FakeStatusLogMapper statusLogMapper = new FakeStatusLogMapper();
        InvTransferServiceImpl service = new InvTransferServiceImpl();
        ReflectionTestUtils.setField(service, "transferOrderMapper", orderMapper);
        ReflectionTestUtils.setField(service, "transferDetailMapper", detailMapper);
        ReflectionTestUtils.setField(service, "numberSequenceMapper", new FakeNumberSequenceMapper());
        ReflectionTestUtils.setField(service, "deptScopeMapper", new FakeDeptScopeMapper());
        ReflectionTestUtils.setField(service, "transferApprovalService", approvalService);
        configureLegacyUnifiedApproval(service);
        ReflectionTestUtils.setField(service, "statusLogMapper", statusLogMapper);
        ReflectionTestUtils.setField(service, "itemResolver", itemResolver());
        ReflectionTestUtils.setField(service, "transferReservationService",
                new FakeTransferReservationService(new FakeStockMapper()));
        ReflectionTestUtils.setField(service, "transferRevisionService",
                mock(InvTransferRevisionService.class));

        InvTransferOrder submitted = service.submitTransfer(transferOrder(), Collections.singletonList(detail()), 202L);

        assertThat(statusLogMapper.logs.get(0).getReason()).contains(warning);
        assertThat(ReflectionTestUtils.getField(submitted, "approvalWarnings"))
                .isEqualTo(Collections.singletonList(warning));
    }

    @Test
    @DisplayName("审批实例自动通过时提交调拨单直接进入待发货")
    void shouldApproveTransferImmediatelyWhenApprovalInstanceAutoApproved()
    {
        SecurityContextHolder.setUserId("301");
        SecurityContextHolder.setUserName("operationDirector");
        FakeTransferOrderMapper orderMapper = new FakeTransferOrderMapper();
        FakeTransferDetailMapper detailMapper = new FakeTransferDetailMapper();
        FakeApprovalService approvalService = new FakeApprovalService();
        approvalService.instanceStatus = "approved";
        FakeStatusLogMapper statusLogMapper = new FakeStatusLogMapper();
        InvTransferServiceImpl service = new InvTransferServiceImpl();
        ReflectionTestUtils.setField(service, "transferOrderMapper", orderMapper);
        ReflectionTestUtils.setField(service, "transferDetailMapper", detailMapper);
        ReflectionTestUtils.setField(service, "numberSequenceMapper", new FakeNumberSequenceMapper());
        ReflectionTestUtils.setField(service, "deptScopeMapper", new FakeDeptScopeMapper());
        ReflectionTestUtils.setField(service, "transferApprovalService", approvalService);
        configureLegacyUnifiedApproval(service);
        ReflectionTestUtils.setField(service, "statusLogMapper", statusLogMapper);
        ReflectionTestUtils.setField(service, "itemResolver", itemResolver());
        ReflectionTestUtils.setField(service, "transferReservationService",
                new FakeTransferReservationService(new FakeStockMapper()));
        ReflectionTestUtils.setField(service, "transferRevisionService",
                mock(InvTransferRevisionService.class));

        InvTransferOrder submitted = service.submitTransfer(transferOrder(), Collections.singletonList(detail()), 202L);

        InvTransferOrder submitUpdate = orderMapper.updates.get(1);
        assertThat(submitUpdate.getStatus()).isEqualTo(InvStatusConstants.APPROVED);
        assertThat(submitUpdate.getApprovalInstanceId()).isEqualTo(700L);
        assertThat(submitUpdate.getSubmittedTime()).isNotNull();
        assertThat(submitUpdate.getApprovedTime()).isNotNull();
        assertThat(statusLogMapper.logs).hasSize(1);
        assertThat(statusLogMapper.logs.get(0).getToStatus()).isEqualTo(InvStatusConstants.APPROVED);
        assertThat(statusLogMapper.logs.get(0).getAction()).isEqualTo("auto_approve");
        assertThat(submitted.getStatus()).isEqualTo(InvStatusConstants.APPROVED);
    }

    @Test
    @DisplayName("调拨管理不能直接保存OE器皿")
    void shouldRejectManualOeTransfer()
    {
        SecurityContextHolder.setUserId("1");
        SecurityContextHolder.setUserName("admin");
        FakeTransferOrderMapper orderMapper = new FakeTransferOrderMapper();
        InvTransferServiceImpl service = transferService(orderMapper,
                new FakeTransferDetailMapper());
        InvTransferOrder order = warehouseReplenishmentOrder(202L);
        InvTransferDetail detail = new InvTransferDetail();
        detail.setItemType("oe");
        detail.setItemId(31L);
        detail.setQuantity(BigDecimal.ONE);

        assertThatThrownBy(() -> service.saveDraft(order,
                Collections.singletonList(detail), 301L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("OE器皿固定资产仅通过固定资产上报");
        assertThat(orderMapper.stored).isNull();
    }

    @Test
    @DisplayName("公共调拨入口不能伪造维修上报内部来源")
    void shouldRejectForgedRepairSourceWithOtherwiseAllowedItem()
    {
        SecurityContextHolder.setUserId("1");
        SecurityContextHolder.setUserName("admin");
        FakeTransferOrderMapper orderMapper = new FakeTransferOrderMapper();
        InvTransferServiceImpl service = transferService(orderMapper,
                new FakeTransferDetailMapper());
        InvTransferOrder order = warehouseReplenishmentOrder(202L);
        order.setSourceBusinessType("oe_replenishment");
        order.setSourceBusinessId(88L);

        assertThatThrownBy(() -> service.saveDraft(order,
                Collections.singletonList(detail()), 301L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("独立OE补货链路已退役");
        assertThat(orderMapper.stored).isNull();
    }

    @Test
    @DisplayName("历史OE草稿不能从调拨管理直接提交")
    void shouldRejectSubmittingExistingManualOeDraft()
    {
        SecurityContextHolder.setUserId("1");
        SecurityContextHolder.setUserName("admin");
        FakeTransferOrderMapper orderMapper = new FakeTransferOrderMapper();
        FakeTransferDetailMapper detailMapper = new FakeTransferDetailMapper();
        InvTransferServiceImpl service = transferService(orderMapper,
                detailMapper);
        orderMapper.stored = persistedWarehouseTransfer(
                InvStatusConstants.DRAFT);
        orderMapper.stored.setVersion(0L);
        InvTransferDetail detail = persistedDetail();
        detail.setItemType("oe");
        detail.setItemId(31L);
        detail.setProductId(null);
        detailMapper.details.add(detail);
        InvTransferOrder edit = warehouseReplenishmentOrder(202L);
        edit.setTransferId(900L);
        edit.setVersion(0L);

        assertThatThrownBy(() -> service.submitTransfer(edit, null, 301L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("OE器皿固定资产仅通过固定资产上报");
    }

    @Test
    @DisplayName("公共调拨入口不能改写已有维修上报补货草稿")
    void shouldRejectEditingExistingRepairOeReplenishmentDraft()
    {
        SecurityContextHolder.setUserId("1");
        SecurityContextHolder.setUserName("admin");
        FakeTransferOrderMapper orderMapper = new FakeTransferOrderMapper();
        InvTransferServiceImpl service = transferService(orderMapper,
                new FakeTransferDetailMapper());
        orderMapper.stored = persistedWarehouseTransfer(
                InvStatusConstants.DRAFT);
        orderMapper.stored.setVersion(0L);
        orderMapper.stored.setSourceBusinessType("oe_replenishment");
        orderMapper.stored.setSourceBusinessId(88L);
        InvTransferOrder edit = warehouseReplenishmentOrder(202L);
        edit.setTransferId(900L);
        edit.setVersion(0L);

        assertThatThrownBy(() -> service.saveDraft(edit,
                Collections.singletonList(detail()), 301L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("独立OE补货链路已退役");
    }

    @Test
    @DisplayName("超级管理员查看调拨列表也按当前组织过滤")
    void shouldScopeTransferListBySelectedDeptEvenForAdmin()
    {
        SecurityContextHolder.setUserId("1");
        SecurityContextHolder.setUserName("admin");
        FakeTransferOrderMapper orderMapper = new FakeTransferOrderMapper();
        InvTransferServiceImpl service = transferService(orderMapper, new FakeTransferDetailMapper());

        service.selectTransferList(new InvTransferOrder(), 202L);

        assertThat(orderMapper.lastListQuery.getParams().get("scopeDeptIds"))
                .isEqualTo(Collections.singletonList(202L));
    }

    @Test
    @DisplayName("仓库调拨列表只隐藏门店草稿")
    void shouldHideDraftTransfersFromWarehouseList()
    {
        SecurityContextHolder.setUserId("1");
        SecurityContextHolder.setUserName("admin");
        FakeTransferOrderMapper orderMapper = new FakeTransferOrderMapper();
        InvTransferServiceImpl service = transferService(
                orderMapper, new FakeTransferDetailMapper());

        service.selectTransferList(new InvTransferOrder(), 301L);

        assertThat(orderMapper.lastListQuery.getParams()
                .get("hideDraftTransfers")).isEqualTo(true);
    }

    @Test
    @DisplayName("门店调拨列表保留自己的草稿和审批中申请")
    void shouldKeepPreApprovalTransfersInStoreList()
    {
        SecurityContextHolder.setUserId("1");
        SecurityContextHolder.setUserName("admin");
        FakeTransferOrderMapper orderMapper = new FakeTransferOrderMapper();
        InvTransferServiceImpl service = transferService(
                orderMapper, new FakeTransferDetailMapper());

        service.selectTransferList(new InvTransferOrder(), 201L);

        assertThat(orderMapper.lastListQuery.getParams())
                .doesNotContainKey("hideDraftTransfers");
    }

    @Test
    @DisplayName("调拨运营汇总也按当前组织过滤")
    void shouldScopeTransferOpsSummaryBySelectedDept()
    {
        SecurityContextHolder.setUserId("1");
        SecurityContextHolder.setUserName("admin");
        FakeTransferOrderMapper orderMapper = new FakeTransferOrderMapper();
        InvTransferServiceImpl service = transferService(
                orderMapper, new FakeTransferDetailMapper());

        com.erp.inventory.domain.vo.InvTransferOpsSummaryVo summary =
                service.selectOpsSummary(202L);

        assertThat(orderMapper.lastOpsQuery.getParams().get("scopeDeptIds"))
                .isEqualTo(Collections.singletonList(202L));
        assertThat(summary.getOrganizationId()).isEqualTo(202L);
    }

    @Test
    @DisplayName("仓库运营汇总只不统计门店草稿")
    void shouldHideDraftTransfersFromWarehouseOpsSummary()
    {
        SecurityContextHolder.setUserId("1");
        SecurityContextHolder.setUserName("admin");
        FakeTransferOrderMapper orderMapper = new FakeTransferOrderMapper();
        InvTransferServiceImpl service = transferService(
                orderMapper, new FakeTransferDetailMapper());

        service.selectOpsSummary(301L);

        assertThat(orderMapper.lastOpsQuery.getParams()
                .get("hideDraftTransfers")).isEqualTo(true);
    }

    @Test
    @DisplayName("仓库店铺筛选只校验STORE类型并与仓库范围求交集")
    void shouldApplyWarehouseStoreFilterWithoutDirectStoreScope()
    {
        SecurityContextHolder.setUserId("9");
        SecurityContextHolder.setUserName("account");
        FakeTransferOrderMapper orderMapper = new FakeTransferOrderMapper();
        FakeDeptScopeMapper deptScopeMapper = new FakeDeptScopeMapper();
        // 202 是当前仓库 301 的合法授权店铺，但不是仓库的子节点。
        // 仓库员工没有该店铺的直接选店权限；若错误复用
        // assertShopVisible/countDeptInScope，这个 pair 仍会被判定为不可见。
        deptScopeMapper.deniedDeptScopePairs.add("301:202");
        deptScopeMapper.deniedUserScopeDeptIds.add(202L);
        InvTransferServiceImpl service = transferService(
                orderMapper, new FakeTransferDetailMapper(), deptScopeMapper);
        InvTransferOrder query = new InvTransferOrder();
        query.setStoreDeptId(202L);

        service.selectTransferList(query, 301L);

        assertThat(orderMapper.lastListQuery.getParams().get("storeDeptId"))
                .isEqualTo(202L);
        assertThat(orderMapper.lastListQuery.getParams())
                .containsEntry("hideDraftTransfers", true)
                .containsEntry("currentScopeDeptId", 301L);

        InvTransferOrder invalid = new InvTransferOrder();
        invalid.setStoreDeptId(301L);
        assertThatThrownBy(() -> service.selectTransferList(invalid, 301L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("店铺筛选无效");
    }

    @Test
    @DisplayName("创建和提交调拨单使用登录昵称快照而不是账号")
    void shouldPersistCreatorAndSubmitterNickNameSnapshots()
    {
        SecurityContextHolder.setUserId("1");
        SecurityContextHolder.setUserName("account");
        LoginUser loginUser = new LoginUser();
        SysUser user = new SysUser();
        user.setUserId(42L);
        user.setUserName("account");
        user.setNickName("张三");
        loginUser.setSysUser(user);
        SecurityContextHolder.set(SecurityConstants.LOGIN_USER, loginUser);

        FakeTransferOrderMapper orderMapper = new FakeTransferOrderMapper();
        InvTransferServiceImpl service = transferService(
                orderMapper, new FakeTransferDetailMapper());
        InvTransferOrder order = transferOrder();
        order.setCreatedByUserId(999L);
        order.setCreatedByName("伪造创建人");
        order.setSubmittedByUserId(998L);
        order.setSubmittedByName("伪造提交人");
        order.setSubmittedTime(new java.util.Date());

        service.saveDraft(order, Collections.singletonList(detail()), 202L);

        assertThat(orderMapper.insertedOrders.get(0).getCreatedByUserId())
                .isEqualTo(42L);
        assertThat(orderMapper.insertedOrders.get(0).getCreatedByName())
                .isEqualTo("张三");
        assertThat(orderMapper.insertedOrders.get(0).getCreatedByName())
                .isNotEqualTo(orderMapper.insertedOrders.get(0).getCreateBy());
        assertThat(orderMapper.insertedOrders.get(0).getSubmittedByUserId())
                .isNull();
        assertThat(orderMapper.insertedOrders.get(0).getSubmittedByName())
                .isNull();
        assertThat(orderMapper.insertedOrders.get(0).getSubmittedTime())
                .isNull();

        service.submitTransfer(order, Collections.singletonList(detail()), 202L);

        InvTransferOrder submitUpdate = orderMapper.updates.get(
                orderMapper.updates.size() - 1);
        assertThat(submitUpdate.getSubmittedByUserId()).isEqualTo(42L);
        assertThat(submitUpdate.getSubmittedByName()).isEqualTo("张三");
        assertThat(orderMapper.stored.getSubmittedByName()).isEqualTo("张三");
        assertThat(orderMapper.stored.getCreatedByUserId()).isEqualTo(42L);
        assertThat(orderMapper.stored.getCreatedByName()).isEqualTo("张三");

        user.setNickName(" ");
        FakeTransferOrderMapper noNameOrderMapper = new FakeTransferOrderMapper();
        InvTransferServiceImpl noNameService = transferService(
                noNameOrderMapper, new FakeTransferDetailMapper());
        noNameService.saveDraft(transferOrder(),
                Collections.singletonList(detail()), 202L);
        assertThat(noNameOrderMapper.insertedOrders.get(0).getCreateBy())
                .isEqualTo("account");
        assertThat(noNameOrderMapper.insertedOrders.get(0).getCreatedByName())
                .isNull();
    }

    @Test
    @DisplayName("编辑已有草稿不接受客户端覆盖创建人快照")
    void shouldKeepCreatorSnapshotWhenEditingExistingDraft()
    {
        SecurityContextHolder.setUserId("1");
        SecurityContextHolder.setUserName("account");
        LoginUser loginUser = new LoginUser();
        SysUser user = new SysUser();
        user.setUserId(42L);
        user.setUserName("account");
        user.setNickName("张三");
        loginUser.setSysUser(user);
        SecurityContextHolder.set(SecurityConstants.LOGIN_USER, loginUser);

        FakeTransferOrderMapper orderMapper = new FakeTransferOrderMapper();
        InvTransferOrder stored = persistedTransfer(InvStatusConstants.DRAFT);
        stored.setVersion(0L);
        stored.setCreatedByUserId(42L);
        stored.setCreatedByName("张三");
        orderMapper.stored = stored;
        InvTransferServiceImpl service = transferService(
                orderMapper, new FakeTransferDetailMapper());
        InvTransferOrder edit = draftEdit(0L, "恶意覆盖创建人");
        edit.setCreatedByUserId(999L);
        edit.setCreatedByName("伪造创建人");
        edit.setSubmittedByUserId(998L);
        edit.setSubmittedByName("伪造提交人");
        edit.setSubmittedTime(new java.util.Date());

        service.saveDraft(edit, Collections.singletonList(detail()), 202L);

        assertThat(orderMapper.stored.getCreatedByUserId()).isEqualTo(42L);
        assertThat(orderMapper.stored.getCreatedByName()).isEqualTo("张三");
        assertThat(orderMapper.stored.getSubmittedByUserId()).isNull();
        assertThat(orderMapper.stored.getSubmittedByName()).isNull();
        assertThat(orderMapper.stored.getSubmittedTime()).isNull();
    }

    @Test
    @DisplayName("来源重选自动生成的草稿继承创建人快照")
    void shouldInheritCreatorSnapshotForSourceReselectionDraft()
    {
        SecurityContextHolder.setUserId("1");
        SecurityContextHolder.setUserName("account");
        FakeTransferOrderMapper orderMapper = new FakeTransferOrderMapper();
        FakeTransferDetailMapper detailMapper = new FakeTransferDetailMapper();
        InvTransferServiceImpl service = transferService(orderMapper,
                detailMapper);
        InvTransferOrder parent = transferOrder();
        parent.setTransferId(900L);
        parent.setCreatedByUserId(42L);
        parent.setCreatedByName("张三");

        InvTransferOrder draft = ReflectionTestUtils.invokeMethod(service,
                "createSourceReselectionDraft", parent,
                new ArrayList<>(Collections.singletonList(detail())),
                new BigDecimal("1"), new BigDecimal("2"), "库存不足");

        assertThat(draft.getCreatedByUserId()).isEqualTo(42L);
        assertThat(draft.getCreatedByName()).isEqualTo("张三");
    }

    @Test
    @DisplayName("调拨详情先校验所选组织是否属于当前用户")
    void shouldRejectForgedSelectedDeptBeforeLoadingTransferDetail()
    {
        SecurityContextHolder.setUserId("2");
        SecurityContextHolder.setUserName("storeUser");
        FakeTransferOrderMapper orderMapper = new FakeTransferOrderMapper();
        orderMapper.stored = persistedTransfer(InvStatusConstants.SUBMITTED);
        FakeDeptScopeMapper deptScopeMapper = new FakeDeptScopeMapper();
        deptScopeMapper.deniedUserScopeDeptIds.add(999L);
        InvTransferServiceImpl service = transferService(
                orderMapper, new FakeTransferDetailMapper(), deptScopeMapper);

        assertThatThrownBy(() -> service.getTransferDetail(900L, 999L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("无权选择该店铺");
    }

    @Test
    @DisplayName("仓库不能通过详情直链查看门店调拨草稿")
    void shouldHideDraftTransferDetailFromWarehouse()
    {
        SecurityContextHolder.setUserId("1");
        SecurityContextHolder.setUserName("admin");
        FakeTransferOrderMapper orderMapper = new FakeTransferOrderMapper();
        orderMapper.stored = persistedWarehouseTransfer(
                InvStatusConstants.DRAFT);
        InvTransferServiceImpl service = transferService(
                orderMapper, new FakeTransferDetailMapper());

        assertThatThrownBy(() -> service.getTransferDetail(900L, 301L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("无权访问该调拨单");
    }

    @Test
    @DisplayName("发起门店仍可查看自己的调拨草稿")
    void shouldKeepDraftTransferDetailVisibleToRequestingStore()
    {
        SecurityContextHolder.setUserId("1");
        SecurityContextHolder.setUserName("admin");
        FakeTransferOrderMapper orderMapper = new FakeTransferOrderMapper();
        orderMapper.stored = persistedWarehouseTransfer(
                InvStatusConstants.DRAFT);
        InvTransferServiceImpl service = transferService(
                orderMapper, new FakeTransferDetailMapper());

        InvTransferOrder detail = service.getTransferDetail(900L, 202L);

        assertThat(detail).isSameAs(orderMapper.stored);
    }

    @Test
    @DisplayName("审批通过后仓库可查看带审批摘要的调拨详情")
    void shouldExposeApprovedTransferDetailWithApprovalSummaryToWarehouse()
    {
        SecurityContextHolder.setUserId("1");
        SecurityContextHolder.setUserName("admin");
        FakeTransferOrderMapper orderMapper = new FakeTransferOrderMapper();
        orderMapper.stored = persistedWarehouseTransfer(
                InvStatusConstants.APPROVED);
        FakeApprovalService approvalService = new FakeApprovalService();
        InvTransferApprovalSummary summary = new InvTransferApprovalSummary();
        summary.setTransferId(900L);
        summary.setState("approved");
        approvalService.summaries = Collections.singletonMap(900L, summary);
        InvTransferServiceImpl service = transferService(
                orderMapper, new FakeTransferDetailMapper());
        ReflectionTestUtils.setField(service, "transferApprovalService", approvalService);

        InvTransferOrder detail = service.getTransferDetail(900L, 301L);

        assertThat(detail).isSameAs(orderMapper.stored);
        assertThat(detail.getApprovalSummary()).isSameAs(summary);
        assertThat(approvalService.summaryRequests)
                .containsExactly(Collections.singletonList(900L));
    }

    @Test
    @DisplayName("调拨列表一次批量挂载审批摘要")
    void shouldAttachApprovalSummariesToTransferListInOneBatch()
    {
        SecurityContextHolder.setUserId("1");
        SecurityContextHolder.setUserName("admin");
        FakeTransferOrderMapper orderMapper = new FakeTransferOrderMapper();
        orderMapper.stored = persistedTransfer(InvStatusConstants.SUBMITTED);
        FakeApprovalService approvalService = new FakeApprovalService();
        InvTransferApprovalSummary summary = new InvTransferApprovalSummary();
        summary.setTransferId(900L);
        summary.setState("in_progress");
        summary.setSummaryText("等待店长审批");
        approvalService.summaries = Collections.singletonMap(900L, summary);
        InvTransferServiceImpl service = transferService(orderMapper, new FakeTransferDetailMapper());
        ReflectionTestUtils.setField(service, "transferApprovalService", approvalService);

        List<InvTransferOrder> rows = service.selectTransferList(new InvTransferOrder(), 201L);

        assertThat(rows).singleElement().satisfies(row ->
                assertThat(row.getApprovalSummary()).isSameAs(summary));
        assertThat(approvalService.summaryRequests).containsExactly(Collections.singletonList(900L));
    }

    @Test
    @DisplayName("列表不返回身份快照但详情保留真实昵称")
    void shouldHideAuditIdentityFromListButKeepItInDetail()
    {
        SecurityContextHolder.setUserId("1");
        SecurityContextHolder.setUserName("admin");
        FakeTransferOrderMapper orderMapper = new FakeTransferOrderMapper();
        InvTransferOrder detailRow = persistedTransfer(InvStatusConstants.SUBMITTED);
        detailRow.setCreatedByUserId(42L);
        detailRow.setCreatedByName("张三");
        detailRow.setSubmittedByUserId(43L);
        detailRow.setSubmittedByName("李四");
        detailRow.setCreateBy("account");
        detailRow.setUpdateBy("operator-account");
        orderMapper.stored = detailRow;

        InvTransferOrder listRow = persistedTransfer(InvStatusConstants.SUBMITTED);
        listRow.setTransferId(901L);
        listRow.setCreatedByUserId(42L);
    listRow.setCreatedByName("张三");
    listRow.setSubmittedByUserId(43L);
    listRow.setSubmittedByName("李四");
    listRow.setToDeptHierarchy("金英灵域 / 浙江区域");
        orderMapper.listRows = Collections.singletonList(listRow);

        InvTransferServiceImpl service = transferService(
                orderMapper, new FakeTransferDetailMapper());

        List<InvTransferOrder> rows = service.selectTransferList(
                new InvTransferOrder(), 301L);

        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row.getCreatedByUserId()).isNull();
            assertThat(row.getCreatedByName()).isNull();
        assertThat(row.getSubmittedByUserId()).isNull();
        assertThat(row.getSubmittedByName()).isNull();
        assertThat(row.getToDeptHierarchy()).isEqualTo("金英灵域 / 浙江区域");
        });

        InvTransferOrder detail = service.getTransferDetail(900L, 301L);

        assertThat(detail.getCreatedByUserId()).isEqualTo(42L);
        assertThat(detail.getCreatedByName()).isEqualTo("张三");
        assertThat(detail.getSubmittedByUserId()).isEqualTo(43L);
        assertThat(detail.getSubmittedByName()).isEqualTo("李四");
        assertThat(detail.getCreateBy()).isEqualTo("account");
        assertThat(detail.getUpdateBy()).isEqualTo("operator-account");
    }

    @Test
    @DisplayName("审批轨迹复用调拨详情组织范围并委托给审批服务")
    void shouldLoadApprovalTrackWithinTransferScope()
    {
        SecurityContextHolder.setUserId("1");
        SecurityContextHolder.setUserName("admin");
        FakeTransferOrderMapper orderMapper = new FakeTransferOrderMapper();
        orderMapper.stored = persistedTransfer(InvStatusConstants.SUBMITTED);
        FakeApprovalService approvalService = new FakeApprovalService();
        approvalService.track.setTransferId(900L);
        InvTransferServiceImpl service = transferService(orderMapper, new FakeTransferDetailMapper());
        ReflectionTestUtils.setField(service, "transferApprovalService", approvalService);

        InvTransferApprovalTrack track = service.getApprovalTrack(900L, 201L);

        assertThat(track.getTransferId()).isEqualTo(900L);
        assertThat(approvalService.trackTransfer).isSameAs(orderMapper.stored);
    }

    @Test
    @DisplayName("已提交调拨单不能直接出库")
    void shouldRejectSubmittedTransferDelivery()
    {
        SecurityContextHolder.setUserId("1");
        SecurityContextHolder.setUserName("admin");
        FakeTransferOrderMapper orderMapper = new FakeTransferOrderMapper();
        FakeTransferDetailMapper detailMapper = new FakeTransferDetailMapper();
        InvTransferServiceImpl service = transferService(orderMapper, detailMapper);
        orderMapper.stored = persistedTransfer(InvStatusConstants.SUBMITTED);
        detailMapper.details.add(persistedDetail());

        assertThatThrownBy(() -> service.deliverTransfer(900L, 201L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("当前状态不允许发货");
    }

    @Test
    @DisplayName("跨店发货生成调拨单后直接进入待收货且不重复扣库存")
    void shouldCreatePendingReceiptTransferForDeliveredCrossStoreShipment()
    {
        SecurityContextHolder.setUserId("1");
        SecurityContextHolder.setUserName("admin");
        FakeTransferOrderMapper orderMapper = new FakeTransferOrderMapper();
        FakeTransferDetailMapper detailMapper = new FakeTransferDetailMapper();
        FakeTransferShipmentMapper shipmentMapper = new FakeTransferShipmentMapper();
        FakeTransferShipmentDetailMapper shipmentDetailMapper = new FakeTransferShipmentDetailMapper();
        FakeStockMapper stockMapper = new FakeStockMapper();
        FakeStatusLogMapper statusLogMapper = new FakeStatusLogMapper();
        InvTransferServiceImpl service = transferService(orderMapper, detailMapper,
                statusLogMapper, shipmentMapper, shipmentDetailMapper, stockMapper);

        InvTransferOrder salesDeliveryTransfer = salesDeliveryTransfer();

        InvTransferOrder delivered = service.createDeliveredCrossStoreTransfer(
                salesDeliveryTransfer,
                Collections.singletonList(frozenDetail("2", "3.33", "6.66")),
                301L);

        assertThat(delivered.getStatus()).isEqualTo(InvStatusConstants.DELIVERED);
        assertThat(delivered.getDeliveredTime()).isNotNull();
        assertThat(delivered.getFromDeptId()).isEqualTo(201L);
        assertThat(delivered.getFromWarehouseId()).isEqualTo(301L);
        assertThat(delivered.getToDeptId()).isEqualTo(202L);
        assertThat(delivered.getPurchaseId()).isNull();
        assertThat(delivered.getSourceBusinessType()).isEqualTo("sales_delivery_notice");
        assertThat(delivered.getSourceBusinessId()).isEqualTo(101L);
        assertThat(delivered.getTotalAmount()).isEqualByComparingTo("6.66");
        assertThat(detailMapper.details)
                .extracting(InvTransferDetail::getDeliveredQuantity)
                .containsExactly(new BigDecimal("2"));
        assertThat(shipmentMapper.shipments).hasSize(1);
        assertThat(shipmentMapper.shipments.get(0).getStatus()).isEqualTo(InvStatusConstants.PENDING_RECEIVE);
        assertThat(shipmentDetailMapper.details).hasSize(1);
        assertThat(shipmentDetailMapper.details.get(0).getTransferDetailId()).isEqualTo(501L);
        assertThat(shipmentDetailMapper.details.get(0).getShippedQuantity()).isEqualByComparingTo("2");
        assertThat(shipmentDetailMapper.details.get(0).getReceivedQuantity()).isEqualByComparingTo("0");
        assertThat(stockMapper.sourceStock.getCurrentQuantity()).isEqualByComparingTo("10");
        assertThat(statusLogMapper.logs).hasSize(1);
        assertThat(statusLogMapper.logs.get(0).getFromStatus()).isEqualTo(InvStatusConstants.DRAFT);
        assertThat(statusLogMapper.logs.get(0).getToStatus()).isEqualTo(InvStatusConstants.DELIVERED);
        assertThat(statusLogMapper.logs.get(0).getAction()).isEqualTo("deliver");
    }

    @Test
    @DisplayName("销售通知调拨重试返回原单且不重复创建shipment")
    void shouldReturnExistingSalesDeliveryTransferOnIdempotentRetry()
    {
        SecurityContextHolder.setUserId("1");
        SecurityContextHolder.setUserName("admin");
        FakeTransferOrderMapper orderMapper = new FakeTransferOrderMapper();
        FakeTransferDetailMapper detailMapper = new FakeTransferDetailMapper();
        FakeTransferShipmentMapper shipmentMapper = new FakeTransferShipmentMapper();
        FakeTransferShipmentDetailMapper shipmentDetailMapper = new FakeTransferShipmentDetailMapper();
        FakeStatusLogMapper statusLogMapper = new FakeStatusLogMapper();
        InvTransferServiceImpl service = transferService(orderMapper, detailMapper,
                statusLogMapper, shipmentMapper, shipmentDetailMapper,
                new FakeStockMapper());

        InvTransferOrder first = service.createDeliveredCrossStoreTransfer(
                salesDeliveryTransfer(),
                List.of(frozenDetail("3.00", "3.33", "10.00")), 301L);
        InvTransferOrder retried = service.createDeliveredCrossStoreTransfer(
                salesDeliveryTransfer(),
                List.of(frozenDetail("3.00", "3.33", "10.00")), 301L);

        assertThat(retried.getTransferId()).isEqualTo(first.getTransferId());
        assertThat(orderMapper.insertedOrders).hasSize(1);
        assertThat(shipmentMapper.shipments).hasSize(1);
        assertThat(shipmentDetailMapper.details).hasSize(1);
        assertThat(statusLogMapper.logs).hasSize(1);
        assertThat(detailMapper.details).singleElement().satisfies(detail ->
        {
            assertThat(detail.getCostPrice()).isEqualByComparingTo("3.33");
            assertThat(detail.getAmount()).isEqualByComparingTo("10.00");
        });
    }

    @Test
    @DisplayName("销售通知来源相同但冻结明细不同时报来源冲突")
    void shouldRejectConflictingSalesDeliveryRetry()
    {
        SecurityContextHolder.setUserId("1");
        SecurityContextHolder.setUserName("admin");
        FakeTransferOrderMapper orderMapper = new FakeTransferOrderMapper();
        FakeTransferDetailMapper detailMapper = new FakeTransferDetailMapper();
        FakeTransferShipmentMapper shipmentMapper = new FakeTransferShipmentMapper();
        InvTransferServiceImpl service = transferService(orderMapper, detailMapper,
                new FakeStatusLogMapper(), shipmentMapper,
                new FakeTransferShipmentDetailMapper(), new FakeStockMapper());

        service.createDeliveredCrossStoreTransfer(salesDeliveryTransfer(),
                List.of(frozenDetail("3.00", "3.33", "10.00")), 301L);

        assertThatThrownBy(() -> service.createDeliveredCrossStoreTransfer(
                salesDeliveryTransfer(),
                List.of(frozenDetail("3.00", "3.34", "10.02")), 301L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("来源冲突");
        assertThat(orderMapper.insertedOrders).hasSize(1);
        assertThat(shipmentMapper.shipments).hasSize(1);
    }

    @Test
    @DisplayName("并发唯一键冲突后查询并校验已创建的销售通知调拨")
    void shouldRecoverFromConcurrentSalesDeliveryUniqueKeyConflict()
    {
        SecurityContextHolder.setUserId("1");
        SecurityContextHolder.setUserName("admin");
        FakeTransferOrderMapper orderMapper = new FakeTransferOrderMapper();
        FakeTransferDetailMapper detailMapper = new FakeTransferDetailMapper();
        InvTransferOrder concurrent = salesDeliveryTransfer();
        concurrent.setTransferId(901L);
        concurrent.setOrderNo("TF-CONCURRENT");
        concurrent.setStatus(InvStatusConstants.DELIVERED);
        concurrent.setFromDeptName("门店201");
        concurrent.setToDeptName("门店202");
        concurrent.setTotalQuantity(new BigDecimal("3.00"));
        concurrent.setTotalAmount(new BigDecimal("10.00"));
        orderMapper.concurrentSalesDelivery = concurrent;
        InvTransferDetail persisted = frozenDetail("3.00", "3.33", "10.00");
        persisted.setTransferId(901L);
        persisted.setDetailId(501L);
        detailMapper.details.add(persisted);
        FakeTransferShipmentMapper shipmentMapper = new FakeTransferShipmentMapper();
        InvTransferServiceImpl service = transferService(orderMapper, detailMapper,
                new FakeStatusLogMapper(), shipmentMapper,
                new FakeTransferShipmentDetailMapper(), new FakeStockMapper());

        InvTransferOrder result = service.createDeliveredCrossStoreTransfer(
                salesDeliveryTransfer(),
                List.of(frozenDetail("3.00", "3.33", "10.00")), 301L);

        assertThat(result.getTransferId()).isEqualTo(901L);
        assertThat(orderMapper.insertedOrders).isEmpty();
        assertThat(orderMapper.sourceForUpdateQueries).isEqualTo(1);
        assertThat(shipmentMapper.shipments).isEmpty();
    }

    @Test
    @DisplayName("人工异店调货必须由调入门店发起")
    void shouldRejectManualCrossStoreTransferFromSourceStoreContext()
    {
        SecurityContextHolder.setUserId("1");
        SecurityContextHolder.setUserName("admin");
        FakeTransferOrderMapper orderMapper = new FakeTransferOrderMapper();
        InvTransferServiceImpl service = transferService(orderMapper,
                new FakeTransferDetailMapper());

        assertThatThrownBy(() -> service.saveDraft(transferOrder(),
                Collections.singletonList(detail()), 201L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("异店调货目标必须为当前门店");
        assertThat(orderMapper.stored).isNull();
    }

    @Test
    @DisplayName("调入店用户必须同时拥有所选调出店权限")
    void shouldRequireUserScopeForSelectedCrossStoreSource()
    {
        SecurityContextHolder.setUserId("9");
        SecurityContextHolder.setUserName("storeUser");
        FakeTransferOrderMapper orderMapper = new FakeTransferOrderMapper();
        FakeDeptScopeMapper deptScopeMapper = new FakeDeptScopeMapper();
        deptScopeMapper.deniedUserScopeDeptIds.add(201L);
        InvTransferServiceImpl service = transferService(orderMapper,
                new FakeTransferDetailMapper(), deptScopeMapper);

        assertThatThrownBy(() -> service.saveDraft(transferOrder(),
                Collections.singletonList(detail()), 202L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("当前用户无权选择该店铺");
        assertThat(orderMapper.stored).isNull();
    }

    @Test
    @DisplayName("人工异店调货不能选择同一门店作为调出和调入")
    void shouldRejectManualCrossStoreTransferWithinSameStore()
    {
        SecurityContextHolder.setUserId("1");
        SecurityContextHolder.setUserName("admin");
        FakeTransferOrderMapper orderMapper = new FakeTransferOrderMapper();
        InvTransferServiceImpl service = transferService(orderMapper,
                new FakeTransferDetailMapper());
        InvTransferOrder order = transferOrder();
        order.setFromDeptId(202L);

        assertThatThrownBy(() -> service.saveDraft(order,
                Collections.singletonList(detail()), 202L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("异店调货来源和目标不能相同");
        assertThat(orderMapper.stored).isNull();
    }

    @Test
    @DisplayName("公共调拨入口不能伪造销售发货在途来源")
    void shouldRejectForgedSalesDeliverySource()
    {
        SecurityContextHolder.setUserId("1");
        SecurityContextHolder.setUserName("admin");
        FakeTransferOrderMapper orderMapper = new FakeTransferOrderMapper();
        InvTransferServiceImpl service = transferService(orderMapper,
                new FakeTransferDetailMapper());
        InvTransferOrder order = transferOrder();
        order.setSourceBusinessType("sales_delivery");
        order.setSourceBusinessId(101L);

        assertThatThrownBy(() -> service.saveDraft(order,
                Collections.singletonList(detail()), 301L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("销售发货在途调拨只能由销售发货生成");
        assertThat(orderMapper.stored).isNull();
    }

    @Test
    @DisplayName("公共调拨入口不能伪造销售发货通知来源")
    void shouldRejectForgedSalesDeliveryNoticeSource()
    {
        SecurityContextHolder.setUserId("1");
        SecurityContextHolder.setUserName("admin");
        FakeTransferOrderMapper orderMapper = new FakeTransferOrderMapper();
        InvTransferServiceImpl service = transferService(orderMapper,
                new FakeTransferDetailMapper());
        InvTransferOrder order = salesDeliveryTransfer();

        assertThatThrownBy(() -> service.saveDraft(order,
                Collections.singletonList(detail()), 202L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("只能由发货通知生成");
        assertThat(orderMapper.stored).isNull();
    }

    @Test
    @DisplayName("销售发货通知调拨拒绝零值来源ID")
    void shouldRejectZeroSalesDeliveryNoticeSourceId()
    {
        SecurityContextHolder.setUserId("1");
        SecurityContextHolder.setUserName("admin");
        FakeTransferOrderMapper orderMapper = new FakeTransferOrderMapper();
        InvTransferServiceImpl service = transferService(orderMapper,
                new FakeTransferDetailMapper());
        InvTransferOrder order = salesDeliveryTransfer();
        order.setSourceBusinessId(0L);

        assertThatThrownBy(() -> service.createDeliveredCrossStoreTransfer(
                order, List.of(frozenDetail("1.00", "1.00", "1.00")),
                301L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("来源无效");
        assertThat(orderMapper.stored).isNull();
    }

    @Test
    @DisplayName("调拨草稿创建后不能改成其他调拨类型")
    void shouldRejectChangingTransferTypeOnDraftEdit()
    {
        SecurityContextHolder.setUserId("1");
        SecurityContextHolder.setUserName("admin");
        FakeTransferOrderMapper orderMapper = new FakeTransferOrderMapper();
        orderMapper.stored = persistedTransfer(InvStatusConstants.DRAFT);
        orderMapper.stored.setVersion(0L);
        InvTransferServiceImpl service = transferService(orderMapper,
                new FakeTransferDetailMapper());
        InvTransferOrder edit = warehouseReplenishmentOrder(202L);
        edit.setTransferId(900L);
        edit.setVersion(0L);

        assertThatThrownBy(() -> service.saveDraft(edit,
                Collections.singletonList(detail()), 202L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("调拨类型创建后不能修改");
    }

    @Test
    @DisplayName("仓库上下文不能创建门店补货调拨")
    void shouldRejectWarehouseContextCreatingStoreReplenishmentTransfer()
    {
        SecurityContextHolder.setUserId("1");
        SecurityContextHolder.setUserName("admin");
        FakeTransferOrderMapper orderMapper = new FakeTransferOrderMapper();
        FakeTransferDetailMapper detailMapper = new FakeTransferDetailMapper();
        InvTransferServiceImpl service = transferService(orderMapper, detailMapper);

        assertThatThrownBy(() -> service.saveDraft(warehouseReplenishmentOrder(202L), Collections.singletonList(detail()), 301L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("门店才能发起补货申请");
    }

    @Test
    @DisplayName("门店补货目标必须是当前门店")
    void shouldRejectWarehouseReplenishmentTargetDifferentFromCurrentStore()
    {
        SecurityContextHolder.setUserId("1");
        SecurityContextHolder.setUserName("admin");
        FakeTransferOrderMapper orderMapper = new FakeTransferOrderMapper();
        FakeTransferDetailMapper detailMapper = new FakeTransferDetailMapper();
        InvTransferServiceImpl service = transferService(orderMapper, detailMapper);

        assertThatThrownBy(() -> service.saveDraft(warehouseReplenishmentOrder(203L), Collections.singletonList(detail()), 202L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("补货目标必须为当前门店");
    }

    @Test
    @DisplayName("门店补货的业务来源必须与实际发货仓库一致")
    void shouldRejectWarehouseReplenishmentWithMismatchedSourceLocation()
    {
        SecurityContextHolder.setUserId("1");
        SecurityContextHolder.setUserName("admin");
        FakeTransferOrderMapper orderMapper = new FakeTransferOrderMapper();
        InvTransferServiceImpl service = transferService(orderMapper,
                new FakeTransferDetailMapper());
        InvTransferOrder order = warehouseReplenishmentOrder(202L);
        order.setFromDeptId(201L);

        assertThatThrownBy(() -> service.saveDraft(order,
                Collections.singletonList(detail()), 202L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("补货业务来源必须为仓库");
        assertThat(orderMapper.stored).isNull();
    }

    @Test
    @DisplayName("门店返仓的业务目标必须与实际收货仓库一致")
    void shouldRejectStoreReturnWithMismatchedTargetLocation()
    {
        SecurityContextHolder.setUserId("1");
        SecurityContextHolder.setUserName("admin");
        FakeTransferOrderMapper orderMapper = new FakeTransferOrderMapper();
        InvTransferServiceImpl service = transferService(orderMapper,
                new FakeTransferDetailMapper());
        InvTransferOrder order = new InvTransferOrder();
        order.setFromDeptId(201L);
        order.setFromWarehouseId(201L);
        order.setToDeptId(301L);
        order.setToWarehouseId(302L);
        order.setTransferType(InvTransferTypes.STORE_RETURN);
        order.setReturnReasonCode("OVERSTOCK");

        assertThatThrownBy(() -> service.saveDraft(order,
                Collections.singletonList(detail()), 201L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("返仓业务目标与实际收货仓库不一致");
        assertThat(orderMapper.stored).isNull();
    }

    @Test
    @DisplayName("门店返仓保存拒绝伪造未授权的目标仓库")
    void shouldRejectUnauthorizedStoreReturnTargetWarehouse()
    {
        SecurityContextHolder.setUserId("9");
        SecurityContextHolder.setUserName("storeUser");
        FakeTransferOrderMapper orderMapper = new FakeTransferOrderMapper();
        FakeTransferDetailMapper detailMapper = new FakeTransferDetailMapper();
        FakeDeptScopeMapper deptScopeMapper = new FakeDeptScopeMapper();
        deptScopeMapper.deniedUserScopeDeptIds.add(301L);
        InvTransferServiceImpl service = transferService(orderMapper,
                detailMapper, deptScopeMapper);
        InvTransferOrder order = new InvTransferOrder();
        order.setFromDeptId(202L);
        order.setFromWarehouseId(202L);
        order.setToDeptId(301L);
        order.setToWarehouseId(301L);
        order.setTransferType(InvTransferTypes.STORE_RETURN);
        order.setReturnReasonCode("OVERSTOCK");

        assertThatThrownBy(() -> service.saveDraft(order,
                Collections.singletonList(detail()), 202L))
                .isInstanceOf(ServiceException.class)
                .hasMessage("无权选择返仓目标仓库");

        assertThat(orderMapper.stored).isNull();
        assertThat(detailMapper.details).isEmpty();
    }

    @Test
    @DisplayName("门店补货保存不要求用户绑定来源仓库")
    void shouldSaveReplenishmentTransferWithoutUserSourceWarehouseScope()
    {
        SecurityContextHolder.setUserId("9");
        SecurityContextHolder.setUserName("storeUser");
        FakeTransferOrderMapper orderMapper = new FakeTransferOrderMapper();
        FakeTransferDetailMapper detailMapper = new FakeTransferDetailMapper();
        FakeDeptScopeMapper deptScopeMapper = new FakeDeptScopeMapper();
        deptScopeMapper.deniedUserScopeDeptIds.add(301L);
        InvTransferServiceImpl service = transferService(orderMapper, detailMapper, deptScopeMapper);

        InvTransferOrder saved = service.saveDraft(
                warehouseReplenishmentOrder(202L),
                Collections.singletonList(detail()), 202L);

        assertThat(saved.getStatus()).isEqualTo(InvStatusConstants.DRAFT);
        assertThat(saved.getFromWarehouseId()).isEqualTo(301L);
        assertThat(saved.getToDeptId()).isEqualTo(202L);
        assertThat(detailMapper.details).hasSize(1);
    }

    @Test
    @DisplayName("门店补货保存拒绝其他业务根的来源仓库")
    void shouldRejectReplenishmentWarehouseOutsideStoreBusinessRoot()
    {
        SecurityContextHolder.setUserId("9");
        SecurityContextHolder.setUserName("storeUser");
        FakeTransferOrderMapper orderMapper = new FakeTransferOrderMapper();
        FakeTransferDetailMapper detailMapper = new FakeTransferDetailMapper();
        FakeDeptScopeMapper deptScopeMapper = new FakeDeptScopeMapper();
        deptScopeMapper.businessRootDeptIds.put(301L, 100L);
        deptScopeMapper.businessRootDeptIds.put(202L, 200L);
        InvTransferServiceImpl service = transferService(orderMapper,
                detailMapper, deptScopeMapper);

        assertThatThrownBy(() -> service.saveDraft(
                warehouseReplenishmentOrder(202L),
                Collections.singletonList(detail()), 202L))
                .isInstanceOf(ServiceException.class)
                .hasMessage("当前门店不允许向该仓库要货");

        assertThat(orderMapper.stored).isNull();
        assertThat(detailMapper.details).isEmpty();
    }

    @Test
    @DisplayName("门店补货提交不要求用户绑定来源仓库")
    void shouldSubmitReplenishmentTransferWithoutUserSourceWarehouseScope()
    {
        SecurityContextHolder.setUserId("9");
        SecurityContextHolder.setUserName("storeUser");
        FakeTransferOrderMapper orderMapper = new FakeTransferOrderMapper();
        FakeTransferDetailMapper detailMapper = new FakeTransferDetailMapper();
        FakeDeptScopeMapper deptScopeMapper = new FakeDeptScopeMapper();
        deptScopeMapper.deniedUserScopeDeptIds.add(301L);
        InvTransferServiceImpl service = transferService(orderMapper,
                detailMapper, deptScopeMapper);

        InvTransferOrder submitted = service.submitTransfer(
                warehouseReplenishmentOrder(202L),
                Collections.singletonList(detail()), 202L);

        assertThat(submitted.getStatus())
                .isEqualTo(InvStatusConstants.SUBMITTED);
        assertThat(submitted.getFromWarehouseId()).isEqualTo(301L);
        assertThat(submitted.getToDeptId()).isEqualTo(202L);
        assertThat(detailMapper.details).hasSize(1);
    }

    @Test
    @DisplayName("门店补货保存拒绝未授权归属组织的商品")
    void shouldRejectReplenishmentItemOwnedByUnauthorizedOrganization()
    {
        SecurityContextHolder.setUserId("9");
        SecurityContextHolder.setUserName("storeUser");
        FakeTransferOrderMapper orderMapper = new FakeTransferOrderMapper();
        FakeTransferDetailMapper detailMapper = new FakeTransferDetailMapper();
        InvTransferServiceImpl service = transferService(orderMapper,
                detailMapper);
        ReflectionTestUtils.setField(service, "itemResolver",
                itemResolver(999L));

        assertThatThrownBy(() -> service.saveDraft(
                warehouseReplenishmentOrder(202L),
                Collections.singletonList(detail()), 202L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("无权调拨该物料");

        assertThat(orderMapper.stored).isNull();
        assertThat(detailMapper.details).isEmpty();
    }

    @Test
    @DisplayName("编辑补货来源仓库时重验未随请求提交的持久化明细")
    void shouldRevalidatePersistedDetailsWhenEditingSourceWarehouse()
    {
        SecurityContextHolder.setUserId("9");
        SecurityContextHolder.setUserName("storeUser");
        FakeTransferOrderMapper orderMapper = new FakeTransferOrderMapper();
        orderMapper.stored = persistedWarehouseTransfer(
                InvStatusConstants.DRAFT);
        orderMapper.stored.setVersion(0L);
        FakeTransferDetailMapper detailMapper = new FakeTransferDetailMapper();
        detailMapper.details.add(persistedDetail());
        InvTransferServiceImpl service = transferService(orderMapper,
                detailMapper);
        ReflectionTestUtils.setField(service, "itemResolver",
                itemResolver(301L));
        InvTransferOrder edit = warehouseReplenishmentOrder(202L);
        edit.setTransferId(900L);
        edit.setVersion(0L);
        edit.setFromDeptId(302L);
        edit.setFromWarehouseId(302L);

        assertThatThrownBy(() -> service.saveDraft(edit, null, 202L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("无权调拨该物料");

        assertThat(orderMapper.updates).isEmpty();
        assertThat(detailMapper.deleteCalls).isZero();
    }

    @Test
    @DisplayName("提交前对已保存明细执行最终来源资格重验")
    void shouldRevalidatePersistedDetailsImmediatelyBeforeSubmission()
    {
        SecurityContextHolder.setUserId("9");
        SecurityContextHolder.setUserName("storeUser");
        FakeTransferOrderMapper orderMapper = new FakeTransferOrderMapper();
        FakeTransferDetailMapper detailMapper = new FakeTransferDetailMapper();
        InvTransferServiceImpl service = transferService(orderMapper,
                detailMapper);
        ReflectionTestUtils.setField(service, "itemResolver",
                itemResolverSequence(301L, 999L));
        InvTransferReservationService reservationService =
                mock(InvTransferReservationService.class);
        ReflectionTestUtils.setField(service, "transferReservationService",
                reservationService);

        assertThatThrownBy(() -> service.submitTransfer(
                warehouseReplenishmentOrder(202L),
                Collections.singletonList(detail()), 202L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("无权调拨该物料");

        org.mockito.Mockito.verify(reservationService,
                org.mockito.Mockito.never()).reserveForSubmission(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.anyList(),
                        org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("门店补货保存草稿时以服务端商品成本生成金额快照")
    void shouldSnapshotServerReferenceAmountsWhenSavingReplenishmentDraft()
    {
        SecurityContextHolder.setUserId("9");
        SecurityContextHolder.setUserName("storeUser");
        FakeTransferOrderMapper orderMapper = new FakeTransferOrderMapper();
        FakeTransferDetailMapper detailMapper = new FakeTransferDetailMapper();
        InvTransferServiceImpl service = transferService(orderMapper, detailMapper);
        InvTransferDetail first = detail();
        first.setQuantity(new BigDecimal("2"));
        first.setCostPrice(new BigDecimal("12.35"));
        InvTransferDetail second = detail();
        second.setProductId(1002L);
        second.setProductName("第二个商品");
        second.setQuantity(new BigDecimal("3"));
        second.setCostPrice(new BigDecimal("1.50"));

        InvTransferOrder saved = service.saveDraft(
                warehouseReplenishmentOrder(202L), List.of(first, second), 202L);

        assertThat(saved.getTotalQuantity()).isEqualByComparingTo("5");
        assertThat(saved.getTotalAmount()).isEqualByComparingTo("5.00");
        assertThat(detailMapper.details).hasSize(2);
        assertThat(detailMapper.details.get(0).getCostPrice()).isEqualByComparingTo("1.00");
        assertThat(detailMapper.details.get(0).getAmount()).isEqualByComparingTo("2.00");
        assertThat(detailMapper.details.get(1).getCostPrice()).isEqualByComparingTo("1.00");
        assertThat(detailMapper.details.get(1).getAmount()).isEqualByComparingTo("3.00");
    }

    @Test
    @DisplayName("陈旧版本不能覆盖最新调拨草稿或替换明细")
    void shouldRejectStaleDraftSaveBeforeReplacingDetails()
    {
        SecurityContextHolder.setUserId("9");
        SecurityContextHolder.setUserName("storeUser");
        FakeTransferOrderMapper orderMapper = new FakeTransferOrderMapper();
        orderMapper.stored = persistedTransfer(InvStatusConstants.DRAFT);
        orderMapper.stored.setVersion(5L);
        FakeTransferDetailMapper detailMapper = new FakeTransferDetailMapper();
        InvTransferDetail originalDetail = persistedDetail();
        detailMapper.details.add(originalDetail);
        InvTransferServiceImpl service = transferService(orderMapper, detailMapper);
        InvTransferOrder stale = draftEdit(4L, "陈旧保存");

        assertThatThrownBy(() -> service.saveDraft(
                stale, Collections.singletonList(detail()), 202L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("已被其他人修改或状态已变化");

        assertThat(orderMapper.stored.getVersion()).isEqualTo(5L);
        assertThat(orderMapper.stored.getStatus()).isEqualTo(InvStatusConstants.DRAFT);
        assertThat(detailMapper.deleteCalls).isZero();
        assertThat(detailMapper.details).containsExactly(originalDetail);
    }

    @Test
    @DisplayName("并发提交先完成时草稿保存不能把状态覆盖回草稿")
    void shouldRejectDraftSaveWhenConcurrentSubmitWinsCas()
    {
        SecurityContextHolder.setUserId("9");
        SecurityContextHolder.setUserName("storeUser");
        FakeTransferOrderMapper orderMapper = new FakeTransferOrderMapper();
        orderMapper.stored = persistedTransfer(InvStatusConstants.DRAFT);
        orderMapper.stored.setVersion(5L);
        orderMapper.beforeDraftCas = () -> {
            orderMapper.stored.setStatus(InvStatusConstants.SUBMITTED);
            orderMapper.stored.setVersion(6L);
        };
        FakeTransferDetailMapper detailMapper = new FakeTransferDetailMapper();
        detailMapper.details.add(persistedDetail());
        InvTransferServiceImpl service = transferService(orderMapper, detailMapper);

        assertThatThrownBy(() -> service.saveDraft(
                draftEdit(5L, "落后的草稿保存"),
                Collections.singletonList(detail()), 202L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("已被其他人修改或状态已变化");

        assertThat(orderMapper.stored.getStatus()).isEqualTo(InvStatusConstants.SUBMITTED);
        assertThat(orderMapper.stored.getVersion()).isEqualTo(6L);
        assertThat(detailMapper.deleteCalls).isZero();
    }

    @Test
    @DisplayName("同一版本的两个并发草稿保存仅允许一个成功")
    void shouldAllowOnlyOneConcurrentDraftSaveForSameVersion() throws Exception
    {
        FakeTransferOrderMapper orderMapper = new FakeTransferOrderMapper();
        orderMapper.stored = persistedTransfer(InvStatusConstants.DRAFT);
        orderMapper.stored.setVersion(7L);
        orderMapper.draftCasBarrier = new CyclicBarrier(2);
        FakeTransferDetailMapper detailMapper = new FakeTransferDetailMapper();
        InvTransferServiceImpl firstService = transferService(orderMapper, detailMapper);
        InvTransferServiceImpl secondService = transferService(orderMapper, detailMapper);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try
        {
            Future<Object> first = executor.submit(() -> saveDraftInThread(
                    firstService, draftEdit(7L, "并发保存一")));
            Future<Object> second = executor.submit(() -> saveDraftInThread(
                    secondService, draftEdit(7L, "并发保存二")));
            List<Object> outcomes = List.of(
                    first.get(10, TimeUnit.SECONDS),
                    second.get(10, TimeUnit.SECONDS));

            assertThat(outcomes.stream()
                    .filter(InvTransferOrder.class::isInstance).count())
                    .isEqualTo(1);
            assertThat(outcomes.stream()
                    .filter(ServiceException.class::isInstance).count())
                    .isEqualTo(1);
            ServiceException conflict = (ServiceException) outcomes.stream()
                    .filter(ServiceException.class::isInstance)
                    .findFirst().orElseThrow();
            assertThat(conflict).hasMessageContaining("已被其他人修改或状态已变化");
            assertThat(orderMapper.stored.getVersion()).isEqualTo(8L);
            assertThat(orderMapper.stored.getStatus()).isEqualTo(InvStatusConstants.DRAFT);
        }
        finally
        {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("旧调用未传版本时使用本次服务端读取版本参与CAS")
    void shouldUseCurrentServerVersionForLegacyDraftSave()
    {
        SecurityContextHolder.setUserId("9");
        SecurityContextHolder.setUserName("storeUser");
        FakeTransferOrderMapper orderMapper = new FakeTransferOrderMapper();
        orderMapper.stored = persistedTransfer(InvStatusConstants.DRAFT);
        orderMapper.stored.setVersion(9L);
        InvTransferServiceImpl service = transferService(
                orderMapper, new FakeTransferDetailMapper());

        InvTransferOrder saved = service.saveDraft(
                draftEdit(null, "旧调用保存"), null, 202L);

        assertThat(orderMapper.lastDraftExpectedVersion).isEqualTo(9L);
        assertThat(saved.getVersion()).isEqualTo(10L);
        assertThat(saved.getStatus()).isEqualTo(InvStatusConstants.DRAFT);
    }

    @Test
    @DisplayName("门店补货提交时来源仓库无库存必须整单拒绝")
    void shouldRejectWarehouseReplenishmentSubmissionWhenSourceStockIsMissing()
    {
        SecurityContextHolder.setUserId("1");
        SecurityContextHolder.setUserName("admin");
        FakeTransferOrderMapper orderMapper = new FakeTransferOrderMapper();
        FakeTransferDetailMapper detailMapper = new FakeTransferDetailMapper();
        FakeStockMapper stockMapper = new FakeStockMapper();
        stockMapper.sourceStock = null;
        InvTransferServiceImpl service = transferService(orderMapper, detailMapper,
                new FakeStatusLogMapper(), new FakeTransferShipmentMapper(), new FakeTransferShipmentDetailMapper(), stockMapper);

        assertThatThrownBy(() -> service.submitTransfer(
                warehouseReplenishmentOrder(202L),
                Collections.singletonList(detail()), 202L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("可用库存不足");

        assertThat(orderMapper.stored.getStatus())
                .isEqualTo(InvStatusConstants.DRAFT);
        assertThat(detailMapper.details).hasSize(1);
        assertThat(detailMapper.details.get(0).getProductId()).isEqualTo(1001L);
    }

    @Test
    @DisplayName("来源仓库库存不足时补货调拨不能发货")
    void shouldRejectWarehouseReplenishmentDeliveryWhenSourceStockIsMissing()
    {
        SecurityContextHolder.setUserId("1");
        SecurityContextHolder.setUserName("admin");
        FakeTransferOrderMapper orderMapper = new FakeTransferOrderMapper();
        FakeTransferDetailMapper detailMapper = new FakeTransferDetailMapper();
        FakeTransferShipmentMapper shipmentMapper = new FakeTransferShipmentMapper();
        FakeTransferShipmentDetailMapper shipmentDetailMapper = new FakeTransferShipmentDetailMapper();
        FakeStockMapper stockMapper = new FakeStockMapper();
        InvTransferServiceImpl service = transferService(orderMapper, detailMapper,
                new FakeStatusLogMapper(), shipmentMapper, shipmentDetailMapper, stockMapper);
        orderMapper.stored = persistedWarehouseTransfer(InvStatusConstants.APPROVED);
        detailMapper.details.add(persistedDetail());
        stockMapper.sourceStock = null;

        assertThatThrownBy(() -> service.deliverTransfer(900L, 301L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("库存不足");
        assertThat(orderMapper.stored.getStatus()).isEqualTo(InvStatusConstants.APPROVED);
        assertThat(detailMapper.details.get(0).getDeliveredQuantity()).isEqualByComparingTo("0");
        assertThat(shipmentDetailMapper.details).isEmpty();
    }

    @Test
    @DisplayName("目标门店不能执行仓库补货发货")
    void shouldRejectShipmentByTargetStoreForWarehouseReplenishment()
    {
        SecurityContextHolder.setUserId("1");
        SecurityContextHolder.setUserName("admin");
        FakeTransferOrderMapper orderMapper = new FakeTransferOrderMapper();
        FakeTransferDetailMapper detailMapper = new FakeTransferDetailMapper();
        InvTransferServiceImpl service = transferService(orderMapper, detailMapper);
        orderMapper.stored = persistedWarehouseTransfer(InvStatusConstants.APPROVED);
        detailMapper.details.add(persistedDetail());

        assertThatThrownBy(() -> service.deliverTransfer(900L, 202L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("只能由来源仓库发货");
    }

    @Test
    @DisplayName("来源仓库不能执行门店收货")
    void shouldRejectReceiptBySourceWarehouseForWarehouseReplenishment()
    {
        SecurityContextHolder.setUserId("1");
        SecurityContextHolder.setUserName("admin");
        FakeTransferOrderMapper orderMapper = new FakeTransferOrderMapper();
        FakeTransferDetailMapper detailMapper = new FakeTransferDetailMapper();
        FakeTransferShipmentMapper shipmentMapper = new FakeTransferShipmentMapper();
        FakeTransferShipmentDetailMapper shipmentDetailMapper = new FakeTransferShipmentDetailMapper();
        InvTransferServiceImpl service = transferService(orderMapper, detailMapper,
                new FakeStatusLogMapper(), shipmentMapper, shipmentDetailMapper, new FakeStockMapper());
        orderMapper.stored = persistedWarehouseTransfer(InvStatusConstants.DELIVERED);
        InvTransferDetail detail = persistedDetail();
        detail.setDeliveredQuantity(new BigDecimal("2"));
        detailMapper.details.add(detail);
        shipmentMapper.shipments.add(pendingShipment(800L, 900L));
        shipmentDetailMapper.details.add(pendingShipmentDetail(800L, 501L, "2"));

        assertThatThrownBy(() -> service.receiveTransferShipment(800L, new InvReceiveRequest(), 301L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("只能由目标门店收货");
    }

    @Test
    @DisplayName("人工异店调货未经调出店确认不得发货")
    void shouldBlockCrossStoreDeliveryBeforeSourceConfirmation()
    {
        SecurityContextHolder.setUserId("1");
        SecurityContextHolder.setUserName("admin");
        FakeTransferOrderMapper orderMapper = new FakeTransferOrderMapper();
        FakeTransferDetailMapper detailMapper =
                new FakeTransferDetailMapper();
        FakeTransferShipmentMapper shipmentMapper =
                new FakeTransferShipmentMapper();
        InvTransferServiceImpl service = transferService(orderMapper,
                detailMapper, new FakeStatusLogMapper(), shipmentMapper,
                new FakeTransferShipmentDetailMapper(),
                new FakeStockMapper());
        orderMapper.stored = persistedTransfer(
                InvStatusConstants.APPROVED);
        orderMapper.stored.setSourceConfirmStatus(
                InvTransferSourceConfirmStatus.PENDING);
        detailMapper.details.add(persistedDetail());

        assertThatThrownBy(() -> service.deliverTransfer(900L, 201L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("调出店尚未确认");
        assertThat(shipmentMapper.shipments).isEmpty();
        assertThat(orderMapper.stored.getStatus())
                .isEqualTo(InvStatusConstants.APPROVED);
    }

    @Test
    @DisplayName("调出店全量确认后人工异店调货才可发货")
    void shouldAllowCrossStoreDeliveryAfterFullSourceConfirmation()
    {
        SecurityContextHolder.setUserId("1");
        SecurityContextHolder.setUserName("sourceStoreUser");
        FakeTransferOrderMapper orderMapper = new FakeTransferOrderMapper();
        FakeTransferDetailMapper detailMapper =
                new FakeTransferDetailMapper();
        InvTransferServiceImpl service = transferService(orderMapper,
                detailMapper);
        orderMapper.stored = persistedTransfer(
                InvStatusConstants.APPROVED);
        orderMapper.stored.setSourceConfirmStatus(
                InvTransferSourceConfirmStatus.PENDING);
        detailMapper.details.add(persistedDetail());

        InvTransferSourceConfirmResult result =
                service.confirmSourceTransfer(900L,
                        sourceConfirmRequest(501L, "2", ""), 201L);

        assertThat(result.getSourceConfirmStatus())
                .isEqualTo(InvTransferSourceConfirmStatus.CONFIRMED);
        assertThat(result.getReselectionTransferId()).isNull();
        assertThat(orderMapper.stored.getSourceConfirmStatus())
                .isEqualTo(InvTransferSourceConfirmStatus.CONFIRMED);
        assertThat(orderMapper.stored.getSourceConfirmedBy())
                .isEqualTo("sourceStoreUser");

        service.deliverTransfer(900L, 201L);

        assertThat(orderMapper.stored.getStatus())
                .isEqualTo(InvStatusConstants.DELIVERED);
    }

    @Test
    @DisplayName("调出店部分确认时保留可调数量并把余量退回调入店重选")
    void shouldSplitRemainderIntoTargetOwnedDraftAfterPartialConfirmation()
    {
        SecurityContextHolder.setUserId("1");
        SecurityContextHolder.setUserName("sourceStoreUser");
        FakeTransferOrderMapper orderMapper = new FakeTransferOrderMapper();
        FakeTransferDetailMapper detailMapper =
                new FakeTransferDetailMapper();
        FakeStatusLogMapper statusLogMapper =
                new FakeStatusLogMapper();
        InvTransferServiceImpl service = transferService(orderMapper,
                detailMapper, statusLogMapper);
        orderMapper.stored = persistedTransfer(
                InvStatusConstants.APPROVED);
        orderMapper.stored.setSourceConfirmStatus(
                InvTransferSourceConfirmStatus.PENDING);
        orderMapper.stored.setTotalQuantity(new BigDecimal("10"));
        orderMapper.stored.setTotalAmount(new BigDecimal("20"));
        InvTransferDetail sourceDetail = persistedDetail();
        sourceDetail.setQuantity(new BigDecimal("10"));
        sourceDetail.setCostPrice(new BigDecimal("2"));
        sourceDetail.setAmount(new BigDecimal("20"));
        detailMapper.details.add(sourceDetail);

        InvTransferSourceConfirmResult result =
                service.confirmSourceTransfer(900L,
                        sourceConfirmRequest(501L, "6",
                                "本店只能调出6件"),
                        201L);

        assertThat(result.getSourceConfirmStatus())
                .isEqualTo(InvTransferSourceConfirmStatus.PARTIAL);
        assertThat(result.getReselectionTransferId()).isEqualTo(901L);
        assertThat(orderMapper.stored.getStatus())
                .isEqualTo(InvStatusConstants.APPROVED);
        assertThat(orderMapper.stored.getTotalQuantity())
                .isEqualByComparingTo("6");
        assertThat(orderMapper.stored.getTotalAmount())
                .isEqualByComparingTo("12");
        assertThat(orderMapper.stored.getSourceConfirmStatus())
                .isEqualTo(InvTransferSourceConfirmStatus.PARTIAL);
        assertThat(orderMapper.insertedOrders).singleElement()
                .satisfies(draft ->
                {
                    assertThat(draft.getTransferId()).isEqualTo(901L);
                    assertThat(draft.getStatus())
                            .isEqualTo(InvStatusConstants.DRAFT);
                    assertThat(draft.getToDeptId()).isEqualTo(202L);
                    assertThat(draft.getSourceConfirmStatus()).isEqualTo(
                            InvTransferSourceConfirmStatus.RESELECT_REQUIRED);
                    assertThat(draft.getReselectionFromTransferId())
                            .isEqualTo(900L);
                    assertThat(draft.getTotalQuantity())
                            .isEqualByComparingTo("4");
                });
        assertThat(detailMapper.details).hasSize(2);
        assertThat(detailMapper.details.stream()
                .filter(detail -> Long.valueOf(900L)
                        .equals(detail.getTransferId()))
                .findFirst().orElseThrow().getQuantity())
                .isEqualByComparingTo("6");
        assertThat(detailMapper.details.stream()
                .filter(detail -> Long.valueOf(901L)
                        .equals(detail.getTransferId()))
                .findFirst().orElseThrow().getQuantity())
                .isEqualByComparingTo("4");
        assertThat(statusLogMapper.logs)
                .extracting(InvTransferStatusLog::getAction)
                .containsExactly("source_reselect_created",
                        "source_confirm");
    }

    @Test
    @DisplayName("调出店确认按物料合计校验库存避免重复明细超量")
    void shouldValidateSourceConfirmationStockByAggregatedItem()
    {
        SecurityContextHolder.setUserId("1");
        SecurityContextHolder.setUserName("sourceStoreUser");
        FakeTransferOrderMapper orderMapper = new FakeTransferOrderMapper();
        FakeTransferDetailMapper detailMapper =
                new FakeTransferDetailMapper();
        FakeStockMapper stockMapper = new FakeStockMapper();
        InvTransferServiceImpl service = transferService(orderMapper,
                detailMapper, new FakeStatusLogMapper(),
                new FakeTransferShipmentMapper(),
                new FakeTransferShipmentDetailMapper(), stockMapper);
        orderMapper.stored = persistedTransfer(
                InvStatusConstants.APPROVED);
        orderMapper.stored.setSourceConfirmStatus(
                InvTransferSourceConfirmStatus.PENDING);

        InvTransferDetail first = persistedDetail();
        first.setQuantity(new BigDecimal("6"));
        InvTransferDetail second = persistedDetail();
        second.setDetailId(502L);
        second.setQuantity(new BigDecimal("6"));
        detailMapper.details.add(first);
        detailMapper.details.add(second);

        InvTransferSourceConfirmItem firstRequest =
                new InvTransferSourceConfirmItem();
        firstRequest.setDetailId(501L);
        firstRequest.setConfirmedQuantity(new BigDecimal("6"));
        InvTransferSourceConfirmItem secondRequest =
                new InvTransferSourceConfirmItem();
        secondRequest.setDetailId(502L);
        secondRequest.setConfirmedQuantity(new BigDecimal("6"));
        InvTransferSourceConfirmRequest request =
                new InvTransferSourceConfirmRequest();
        request.setItems(List.of(firstRequest, secondRequest));

        assertThatThrownBy(() -> service.confirmSourceTransfer(
                900L, request, 201L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("合计确认数量")
                .hasMessageContaining("当前可用库存 10");
        assertThat(orderMapper.stored.getSourceConfirmStatus())
                .isEqualTo(InvTransferSourceConfirmStatus.PENDING);
    }

    @Test
    @DisplayName("已审批调拨出库时设置在途时间并写状态日志")
    void shouldSetDeliveredTimeAndStatusLogWhenDeliveringApprovedTransfer()
    {
        SecurityContextHolder.setUserId("1");
        SecurityContextHolder.setUserName("admin");
        FakeTransferOrderMapper orderMapper = new FakeTransferOrderMapper();
        FakeTransferDetailMapper detailMapper = new FakeTransferDetailMapper();
        FakeStatusLogMapper statusLogMapper = new FakeStatusLogMapper();
        InvTransferServiceImpl service = transferService(orderMapper, detailMapper, statusLogMapper);
        orderMapper.stored = persistedTransfer(InvStatusConstants.APPROVED);
        detailMapper.details.add(persistedDetail());

        service.deliverTransfer(900L, 201L);

        assertThat(orderMapper.stored.getStatus()).isEqualTo(InvStatusConstants.DELIVERED);
        assertThat(orderMapper.stored.getDeliveredTime()).isNotNull();
        assertThat(statusLogMapper.logs).hasSize(1);
        assertThat(statusLogMapper.logs.get(0).getFromStatus()).isEqualTo(InvStatusConstants.APPROVED);
        assertThat(statusLogMapper.logs.get(0).getToStatus()).isEqualTo(InvStatusConstants.DELIVERED);
        assertThat(statusLogMapper.logs.get(0).getAction()).isEqualTo("deliver");
    }

    @Test
    @DisplayName("已锁库调拨可以按待办和页面口径继续出库")
    void shouldDeliverReservedTransfer()
    {
        SecurityContextHolder.setUserId("1");
        SecurityContextHolder.setUserName("admin");
        FakeTransferOrderMapper orderMapper = new FakeTransferOrderMapper();
        FakeTransferDetailMapper detailMapper = new FakeTransferDetailMapper();
        FakeStatusLogMapper statusLogMapper = new FakeStatusLogMapper();
        InvTransferServiceImpl service = transferService(orderMapper, detailMapper, statusLogMapper);
        orderMapper.stored = persistedTransfer(InvStatusConstants.RESERVED);
        detailMapper.details.add(persistedDetail());

        service.deliverTransfer(900L, 201L);

        assertThat(orderMapper.stored.getStatus()).isEqualTo(InvStatusConstants.DELIVERED);
        assertThat(statusLogMapper.logs).singleElement().satisfies(log ->
        {
            assertThat(log.getFromStatus()).isEqualTo(InvStatusConstants.RESERVED);
            assertThat(log.getToStatus()).isEqualTo(InvStatusConstants.DELIVERED);
            assertThat(log.getAction()).isEqualTo("deliver");
        });
    }

    @Test
    @DisplayName("审批校验异常时即使单据状态为已审批也不得发货")
    void shouldRejectDeliveryWhenApprovalIntegrityCheckFails()
    {
        SecurityContextHolder.setUserId("1");
        SecurityContextHolder.setUserName("admin");
        FakeTransferOrderMapper orderMapper = new FakeTransferOrderMapper();
        FakeTransferDetailMapper detailMapper = new FakeTransferDetailMapper();
        FakeTransferShipmentMapper shipmentMapper = new FakeTransferShipmentMapper();
        InvTransferServiceImpl service = transferService(orderMapper, detailMapper,
                new FakeStatusLogMapper(), shipmentMapper,
                new FakeTransferShipmentDetailMapper(), new FakeStockMapper());
        FakeApprovalService approvalService = new FakeApprovalService();
        approvalService.deliveryError = "审批数据异常";
        ReflectionTestUtils.setField(service, "transferApprovalService", approvalService);
        orderMapper.stored = persistedTransfer(InvStatusConstants.APPROVED);
        detailMapper.details.add(persistedDetail());

        assertThatThrownBy(() -> service.deliverTransfer(900L, 201L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("审批数据异常");
        assertThat(shipmentMapper.shipments).isEmpty();
        assertThat(orderMapper.stored.getStatus()).isEqualTo(InvStatusConstants.APPROVED);
    }

    @Test
    @DisplayName("部分发货只扣本次数量并生成待收货批次")
    void shouldCreatePendingShipmentWhenPartiallyDeliveringApprovedTransfer()
    {
        SecurityContextHolder.setUserId("1");
        SecurityContextHolder.setUserName("admin");
        FakeTransferOrderMapper orderMapper = new FakeTransferOrderMapper();
        FakeTransferDetailMapper detailMapper = new FakeTransferDetailMapper();
        FakeTransferShipmentMapper shipmentMapper = new FakeTransferShipmentMapper();
        FakeTransferShipmentDetailMapper shipmentDetailMapper = new FakeTransferShipmentDetailMapper();
        FakeStockMapper stockMapper = new FakeStockMapper();
        InvTransferServiceImpl service = transferService(orderMapper, detailMapper,
                new FakeStatusLogMapper(), shipmentMapper, shipmentDetailMapper, stockMapper);
        orderMapper.stored = persistedTransfer(InvStatusConstants.APPROVED);
        InvTransferDetail detail = persistedDetail();
        detail.setDetailId(501L);
        detail.setQuantity(new BigDecimal("10"));
        detailMapper.details.add(detail);
        InvDeliverRequest request = new InvDeliverRequest();
        InvDeliverItem item = new InvDeliverItem();
        item.setDetailId(501L);
        item.setDeliverQuantity(new BigDecimal("3"));
        request.setItems(Collections.singletonList(item));

        service.deliverTransfer(900L, request, 201L);

        assertThat(stockMapper.sourceStock.getCurrentQuantity()).isEqualByComparingTo("7");
        assertThat(detail.getDeliveredQuantity()).isEqualByComparingTo("3");
        assertThat(orderMapper.stored.getStatus()).isEqualTo(InvStatusConstants.PARTIAL_DELIVERED);
        assertThat(shipmentMapper.shipments).hasSize(1);
        assertThat(shipmentMapper.shipments.get(0).getStatus()).isEqualTo(InvStatusConstants.PENDING_RECEIVE);
        assertThat(shipmentDetailMapper.details).hasSize(1);
        assertThat(shipmentDetailMapper.details.get(0).getShippedQuantity()).isEqualByComparingTo("3");
        assertThat(shipmentDetailMapper.details.get(0).getReceivedQuantity()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("源仓成本变动后调拨收货仍按发货成本入库")
    void shouldReceiveShipmentWithShipmentCostWhenSourceCostChangesAfterDelivery()
    {
        SecurityContextHolder.setUserId("1");
        SecurityContextHolder.setUserName("admin");
        FakeTransferOrderMapper orderMapper = new FakeTransferOrderMapper();
        FakeTransferDetailMapper detailMapper = new FakeTransferDetailMapper();
        FakeTransferShipmentMapper shipmentMapper = new FakeTransferShipmentMapper();
        FakeTransferShipmentDetailMapper shipmentDetailMapper = new FakeTransferShipmentDetailMapper();
        FakeStockMapper stockMapper = new FakeStockMapper();
        InvTransferServiceImpl service = transferService(orderMapper, detailMapper,
                new FakeStatusLogMapper(), shipmentMapper, shipmentDetailMapper, stockMapper);
        orderMapper.stored = persistedTransfer(InvStatusConstants.APPROVED);
        InvTransferDetail detail = persistedDetail();
        detail.setDetailId(501L);
        detail.setQuantity(new BigDecimal("3"));
        detailMapper.details.add(detail);
        stockMapper.sourceStock.setCostPrice(new BigDecimal("1.25"));

        service.deliverTransfer(900L, 201L);
        assertThat(shipmentDetailMapper.details.get(0).getCostPrice()).isEqualByComparingTo("1.25");
        stockMapper.sourceStock.setCostPrice(new BigDecimal("9.00"));
        service.receiveTransferShipment(800L, new InvReceiveRequest(), 202L);

        assertThat(stockMapper.lastIncomingCost).isEqualByComparingTo("3.75");
    }

    @Test
    @DisplayName("按发货批次收货只入库该批次数量且未收完主单不归档")
    void shouldReceiveShipmentBatchWithoutArchivingUnfinishedTransfer()
    {
        SecurityContextHolder.setUserId("1");
        SecurityContextHolder.setUserName("admin");
        FakeTransferOrderMapper orderMapper = new FakeTransferOrderMapper();
        FakeTransferDetailMapper detailMapper = new FakeTransferDetailMapper();
        FakeTransferShipmentMapper shipmentMapper = new FakeTransferShipmentMapper();
        FakeTransferShipmentDetailMapper shipmentDetailMapper = new FakeTransferShipmentDetailMapper();
        FakeStockMapper stockMapper = new FakeStockMapper();
        InvTransferServiceImpl service = transferService(orderMapper, detailMapper,
                new FakeStatusLogMapper(), shipmentMapper, shipmentDetailMapper, stockMapper);
        orderMapper.stored = persistedTransfer(InvStatusConstants.PARTIAL_DELIVERED);
        InvTransferDetail detail = persistedDetail();
        detail.setDetailId(501L);
        detail.setQuantity(new BigDecimal("10"));
        detail.setDeliveredQuantity(new BigDecimal("3"));
        detail.setReceivedQuantity(BigDecimal.ZERO);
        detailMapper.details.add(detail);
        shipmentMapper.shipments.add(pendingShipment(800L, 900L));
        shipmentDetailMapper.details.add(pendingShipmentDetail(800L, 501L, "3"));

        service.receiveTransferShipment(800L, new InvReceiveRequest(), 202L);

        assertThat(stockMapper.targetLookupShopDeptId).isEqualTo(202L);
        assertThat(stockMapper.targetLookupWarehouseId).isEqualTo(202L);
        assertThat(stockMapper.targetForUpdateLookupCalls).isEqualTo(1);
        assertThat(stockMapper.targetPlainLookupCalls).isZero();
        assertThat(stockMapper.targetStock.getCurrentQuantity()).isEqualByComparingTo("3");
        assertThat(detail.getReceivedQuantity()).isEqualByComparingTo("3");
        assertThat(shipmentMapper.shipments.get(0).getStatus()).isEqualTo(InvStatusConstants.RECEIVED);
        assertThat(shipmentDetailMapper.details.get(0).getReceivedQuantity()).isEqualByComparingTo("3");
        assertThat(orderMapper.stored.getStatus()).isEqualTo(InvStatusConstants.PARTIAL_RECEIVED);
        assertThat(orderMapper.stored.getArchivedTime()).isNull();
    }

    @Test
    @DisplayName("补发收完且剩余短差已有终态台账时调拨自动结案")
    void shouldCloseAfterReshipWhenRemainingShortfallHasTerminalDisposition()
    {
        SecurityContextHolder.setUserId("1");
        SecurityContextHolder.setUserName("admin");
        FakeTransferOrderMapper orderMapper = new FakeTransferOrderMapper();
        FakeTransferDetailMapper detailMapper = new FakeTransferDetailMapper();
        FakeTransferShipmentMapper shipmentMapper = new FakeTransferShipmentMapper();
        FakeTransferShipmentDetailMapper shipmentDetailMapper =
                new FakeTransferShipmentDetailMapper();
        InvTransferServiceImpl service = transferService(orderMapper,
                detailMapper, new FakeStatusLogMapper(), shipmentMapper,
                shipmentDetailMapper, new FakeStockMapper());
        orderMapper.stored = persistedTransfer(InvStatusConstants.DELIVERED);
        InvTransferDetail detail = persistedDetail();
        detail.setDetailId(501L);
        detail.setQuantity(new BigDecimal("10"));
        detail.setDeliveredQuantity(new BigDecimal("10"));
        detail.setReceivedQuantity(new BigDecimal("8"));
        detailMapper.details.add(detail);
        shipmentMapper.shipments.add(pendingShipment(802L, 900L));
        shipmentDetailMapper.details.add(
                pendingShipmentDetail(802L, 501L, "1"));

        InvTransferDiscrepancy discrepancy = new InvTransferDiscrepancy();
        discrepancy.setDiscrepancyId(71L);
        discrepancy.setTransferId(900L);
        discrepancy.setStatus("RESOLVED");
        InvTransferDiscrepancyDetail discrepancyDetail =
                new InvTransferDiscrepancyDetail();
        discrepancyDetail.setDiscrepancyDetailId(711L);
        discrepancyDetail.setTransferDetailId(501L);
        InvTransferDiscrepancyDisposition disposition =
                new InvTransferDiscrepancyDisposition();
        disposition.setDiscrepancyDetailId(711L);
        disposition.setCategory("REJECTED");
        disposition.setDecision("WRITE_OFF");
        disposition.setQuantity(BigDecimal.ONE);
        InvTransferDiscrepancyMapper discrepancyMapper = mock(
                InvTransferDiscrepancyMapper.class);
        InvTransferDiscrepancyDispositionMapper dispositionMapper = mock(
                InvTransferDiscrepancyDispositionMapper.class);
        org.mockito.Mockito.when(discrepancyMapper.selectByTransferId(900L))
                .thenReturn(List.of(discrepancy));
        org.mockito.Mockito.when(discrepancyMapper.selectDetails(71L))
                .thenReturn(List.of(discrepancyDetail));
        org.mockito.Mockito.when(dispositionMapper
                .selectLatestByDiscrepancyId(71L))
                .thenReturn(List.of(disposition));
        ReflectionTestUtils.setField(service, "transferDiscrepancyMapper",
                discrepancyMapper);
        ReflectionTestUtils.setField(service,
                "transferDiscrepancyDispositionMapper", dispositionMapper);

        service.receiveTransferShipment(802L, new InvReceiveRequest(), 202L);

        assertThat(detail.getReceivedQuantity()).isEqualByComparingTo("9");
        assertThat(orderMapper.stored.getStatus())
                .isEqualTo(InvStatusConstants.CLOSED);
        assertThat(orderMapper.stored.getArchivedTime()).isNotNull();
    }

    @Test
    @DisplayName("目标库存并发创建兜底后继续锁读库存行")
    void shouldLockTargetStockAfterDuplicateCreateFallback()
    {
        SecurityContextHolder.setUserId("1");
        SecurityContextHolder.setUserName("admin");
        FakeTransferOrderMapper orderMapper = new FakeTransferOrderMapper();
        FakeTransferDetailMapper detailMapper = new FakeTransferDetailMapper();
        FakeTransferShipmentMapper shipmentMapper = new FakeTransferShipmentMapper();
        FakeTransferShipmentDetailMapper shipmentDetailMapper = new FakeTransferShipmentDetailMapper();
        FakeStockMapper stockMapper = new FakeStockMapper();
        InvTransferServiceImpl service = transferService(orderMapper, detailMapper,
                new FakeStatusLogMapper(), shipmentMapper, shipmentDetailMapper, stockMapper);
        orderMapper.stored = persistedTransfer(InvStatusConstants.DELIVERED);
        InvTransferDetail detail = persistedDetail();
        detail.setDeliveredQuantity(new BigDecimal("2"));
        detailMapper.details.add(detail);
        InvTransferShipmentDetail shipmentDetail = pendingShipmentDetail(800L, 501L, "2");
        shipmentDetail.setCostPrice(new BigDecimal("1.50"));
        shipmentMapper.shipments.add(pendingShipment(800L, 900L));
        shipmentDetailMapper.details.add(shipmentDetail);
        InvStock concurrentTargetStock = new InvStock();
        concurrentTargetStock.setStockId(2L);
        concurrentTargetStock.setProductId(1001L);
        concurrentTargetStock.setShopDeptId(202L);
        concurrentTargetStock.setWarehouseId(202L);
        concurrentTargetStock.setCurrentQuantity(new BigDecimal("5"));
        concurrentTargetStock.setAvailableQuantity(new BigDecimal("5"));
        concurrentTargetStock.setCostPrice(new BigDecimal("2"));
        stockMapper.targetStock = null;
        stockMapper.duplicateOnInsert = true;
        stockMapper.duplicateTargetStock = concurrentTargetStock;

        service.receiveTransferShipment(800L, new InvReceiveRequest(), 202L);

        assertThat(stockMapper.targetForUpdateLookupCalls).isEqualTo(2);
        assertThat(stockMapper.targetPlainLookupCalls).isZero();
        assertThat(stockMapper.targetStock.getCurrentQuantity()).isEqualByComparingTo("7");
        assertThat(stockMapper.lastIncomingCost).isEqualByComparingTo("3.00");
    }

    @Test
    @DisplayName("显式调拨收货请求不能包含非本批次明细")
    void shouldRejectExplicitReceiptRequestWithUnknownShipmentDetail()
    {
        SecurityContextHolder.setUserId("1");
        SecurityContextHolder.setUserName("admin");
        FakeTransferOrderMapper orderMapper = new FakeTransferOrderMapper();
        FakeTransferDetailMapper detailMapper = new FakeTransferDetailMapper();
        FakeTransferShipmentMapper shipmentMapper = new FakeTransferShipmentMapper();
        FakeTransferShipmentDetailMapper shipmentDetailMapper = new FakeTransferShipmentDetailMapper();
        InvTransferServiceImpl service = transferService(orderMapper, detailMapper,
                new FakeStatusLogMapper(), shipmentMapper, shipmentDetailMapper, new FakeStockMapper());
        orderMapper.stored = persistedTransfer(InvStatusConstants.PARTIAL_DELIVERED);
        InvTransferDetail detail = persistedDetail();
        detail.setDeliveredQuantity(new BigDecimal("3"));
        detailMapper.details.add(detail);
        shipmentMapper.shipments.add(pendingShipment(800L, 900L));
        shipmentDetailMapper.details.add(pendingShipmentDetail(800L, 501L, "3"));

        assertThatThrownBy(() -> service.receiveTransferShipment(800L, receiveRequest(999L, "3"), 202L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("收货明细不属于当前发货批次");
    }

    @Test
    @DisplayName("显式调拨收货请求必须覆盖本批次所有明细")
    void shouldRejectExplicitReceiptRequestMissingShipmentDetail()
    {
        SecurityContextHolder.setUserId("1");
        SecurityContextHolder.setUserName("admin");
        FakeTransferOrderMapper orderMapper = new FakeTransferOrderMapper();
        FakeTransferDetailMapper detailMapper = new FakeTransferDetailMapper();
        FakeTransferShipmentMapper shipmentMapper = new FakeTransferShipmentMapper();
        FakeTransferShipmentDetailMapper shipmentDetailMapper = new FakeTransferShipmentDetailMapper();
        InvTransferServiceImpl service = transferService(orderMapper, detailMapper,
                new FakeStatusLogMapper(), shipmentMapper, shipmentDetailMapper, new FakeStockMapper());
        orderMapper.stored = persistedTransfer(InvStatusConstants.PARTIAL_DELIVERED);
        InvTransferDetail first = persistedDetail();
        first.setDetailId(501L);
        first.setDeliveredQuantity(new BigDecimal("3"));
        InvTransferDetail second = persistedDetail();
        second.setDetailId(502L);
        second.setDeliveredQuantity(new BigDecimal("2"));
        detailMapper.details.add(first);
        detailMapper.details.add(second);
        shipmentMapper.shipments.add(pendingShipment(800L, 900L));
        shipmentDetailMapper.details.add(pendingShipmentDetail(800L, 501L, "3"));
        shipmentDetailMapper.details.add(pendingShipmentDetail(800L, 502L, "2"));

        assertThatThrownBy(() -> service.receiveTransferShipment(800L, receiveRequest(501L, "3"), 202L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("收货明细不完整");
    }

    @Test
    @DisplayName("零验收入库代表全量差异且必须填写说明或凭证")
    void shouldRequireEvidenceWhenExplicitReceiptAcceptsZeroQuantity()
    {
        SecurityContextHolder.setUserId("1");
        SecurityContextHolder.setUserName("admin");
        FakeTransferOrderMapper orderMapper = new FakeTransferOrderMapper();
        FakeTransferDetailMapper detailMapper = new FakeTransferDetailMapper();
        FakeTransferShipmentMapper shipmentMapper = new FakeTransferShipmentMapper();
        FakeTransferShipmentDetailMapper shipmentDetailMapper = new FakeTransferShipmentDetailMapper();
        InvTransferServiceImpl service = transferService(orderMapper, detailMapper,
                new FakeStatusLogMapper(), shipmentMapper, shipmentDetailMapper, new FakeStockMapper());
        orderMapper.stored = persistedTransfer(InvStatusConstants.PARTIAL_DELIVERED);
        InvTransferDetail detail = persistedDetail();
        detail.setDeliveredQuantity(new BigDecimal("3"));
        detailMapper.details.add(detail);
        shipmentMapper.shipments.add(pendingShipment(800L, 900L));
        shipmentDetailMapper.details.add(pendingShipmentDetail(800L, 501L, "3"));

        assertThatThrownBy(() -> service.receiveTransferShipment(800L, receiveRequest(501L, "0"), 202L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("存在差异，请填写说明或上传凭证");
    }

    @Test
    @DisplayName("调拨入库完成时设置收货时间和归档时间并写状态日志")
    void shouldSetReceivedAndArchivedTimeAndStatusLogWhenReceivingTransfer()
    {
        SecurityContextHolder.setUserId("1");
        SecurityContextHolder.setUserName("admin");
        FakeTransferOrderMapper orderMapper = new FakeTransferOrderMapper();
        FakeTransferDetailMapper detailMapper = new FakeTransferDetailMapper();
        FakeTransferShipmentMapper shipmentMapper = new FakeTransferShipmentMapper();
        FakeTransferShipmentDetailMapper shipmentDetailMapper = new FakeTransferShipmentDetailMapper();
        FakeStockMapper stockMapper = new FakeStockMapper();
        FakeStatusLogMapper statusLogMapper = new FakeStatusLogMapper();
        InvTransferServiceImpl service = transferService(orderMapper, detailMapper,
                statusLogMapper, shipmentMapper, shipmentDetailMapper, stockMapper);
        orderMapper.stored = persistedTransfer(InvStatusConstants.DELIVERED);
        orderMapper.stored.setToWarehouseId(202L);
        InvTransferDetail detail = persistedDetail();
        detail.setDeliveredQuantity(new BigDecimal("2"));
        detailMapper.details.add(detail);
        shipmentMapper.shipments.add(pendingShipment(800L, 900L));
        shipmentDetailMapper.details.add(pendingShipmentDetail(800L, 501L, "2"));

        service.receiveTransfer(900L, 202L);

        assertThat(orderMapper.stored.getStatus()).isEqualTo(InvStatusConstants.RECEIVED);
        assertThat(orderMapper.stored.getReceivedTime()).isNotNull();
        assertThat(orderMapper.stored.getArchivedTime()).isNotNull();
        assertThat(statusLogMapper.logs).hasSize(1);
        assertThat(statusLogMapper.logs.get(0).getFromStatus()).isEqualTo(InvStatusConstants.DELIVERED);
        assertThat(statusLogMapper.logs.get(0).getToStatus()).isEqualTo(InvStatusConstants.RECEIVED);
        assertThat(statusLogMapper.logs.get(0).getAction()).isEqualTo("receive");
    }

    @Test
    @DisplayName("取消调拨单时设置归档时间并写状态日志")
    void shouldSetArchivedTimeAndStatusLogWhenCancellingTransfer()
    {
        SecurityContextHolder.setUserId("1");
        SecurityContextHolder.setUserName("admin");
        FakeTransferOrderMapper orderMapper = new FakeTransferOrderMapper();
        FakeTransferDetailMapper detailMapper = new FakeTransferDetailMapper();
        FakeStatusLogMapper statusLogMapper = new FakeStatusLogMapper();
        InvTransferServiceImpl service = transferService(orderMapper, detailMapper, statusLogMapper);
        orderMapper.stored = persistedTransfer(InvStatusConstants.DRAFT);

        service.cancelTransfer(900L, 202L);

        assertThat(orderMapper.stored.getStatus()).isEqualTo(InvStatusConstants.CANCELLED);
        assertThat(orderMapper.stored.getArchivedTime()).isNotNull();
        assertThat(statusLogMapper.logs).hasSize(1);
        assertThat(statusLogMapper.logs.get(0).getFromStatus()).isEqualTo(InvStatusConstants.DRAFT);
        assertThat(statusLogMapper.logs.get(0).getToStatus()).isEqualTo(InvStatusConstants.CANCELLED);
        assertThat(statusLogMapper.logs.get(0).getAction()).isEqualTo("cancel");
    }

    @Test
    @DisplayName("取消已提交旧审批调拨时同步终止审批")
    void shouldTerminateLegacyApprovalWhenCancellingSubmittedTransfer()
    {
        SecurityContextHolder.setUserId("1");
        SecurityContextHolder.setUserName("admin");
        FakeTransferOrderMapper orderMapper = new FakeTransferOrderMapper();
        FakeTransferDetailMapper detailMapper = new FakeTransferDetailMapper();
        FakeStatusLogMapper statusLogMapper = new FakeStatusLogMapper();
        FakeApprovalService approvalService = new FakeApprovalService();
        InvTransferServiceImpl service = transferService(orderMapper,
                detailMapper, statusLogMapper);
        ReflectionTestUtils.setField(service, "transferApprovalService",
                approvalService);
        orderMapper.stored = persistedTransfer(InvStatusConstants.SUBMITTED);

        service.cancelTransfer(900L, 202L);

        assertThat(approvalService.cancelledTransferIds)
                .containsExactly(900L);
        assertThat(orderMapper.stored.getStatus())
                .isEqualTo(InvStatusConstants.CANCELLED);
    }

    @Test
    @DisplayName("统一审批中的调拨必须撤回而不能直接取消")
    void shouldRequireWithdrawalForNativeApprovalCancellation()
    {
        SecurityContextHolder.setUserId("1");
        SecurityContextHolder.setUserName("admin");
        FakeTransferOrderMapper orderMapper = new FakeTransferOrderMapper();
        FakeTransferDetailMapper detailMapper = new FakeTransferDetailMapper();
        FakeApprovalService approvalService = new FakeApprovalService();
        InvTransferServiceImpl service = transferService(orderMapper,
                detailMapper);
        ReflectionTestUtils.setField(service, "transferApprovalService",
                approvalService);
        orderMapper.stored = persistedTransfer(InvStatusConstants.SUBMITTED);
        orderMapper.stored.setApprovalEngine(
                InventoryUnifiedApprovalService.ENGINE_NATIVE);

        assertThatThrownBy(() -> service.cancelTransfer(900L, 202L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("撤回审批");

        assertThat(approvalService.cancelledTransferIds).isEmpty();
        assertThat(orderMapper.stored.getStatus())
                .isEqualTo(InvStatusConstants.SUBMITTED);
    }

    @Test
    @DisplayName("异店调货来源门店不能取消目标门店发起的草稿")
    void shouldRejectCancellingCrossStoreDraftFromSourceStore()
    {
        SecurityContextHolder.setUserId("1");
        SecurityContextHolder.setUserName("admin");
        FakeTransferOrderMapper orderMapper = new FakeTransferOrderMapper();
        FakeTransferDetailMapper detailMapper = new FakeTransferDetailMapper();
        FakeStatusLogMapper statusLogMapper = new FakeStatusLogMapper();
        InvTransferServiceImpl service = transferService(orderMapper,
                detailMapper, statusLogMapper);
        orderMapper.stored = persistedTransfer(InvStatusConstants.DRAFT);

        assertThatThrownBy(() -> service.cancelTransfer(900L, 201L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("只有调入门店");
        assertThat(orderMapper.stored.getStatus())
                .isEqualTo(InvStatusConstants.DRAFT);
        assertThat(statusLogMapper.logs).isEmpty();
    }

    @Test
    @DisplayName("异店调货来源门店不能删除目标门店发起的草稿")
    void shouldRejectDeletingCrossStoreDraftFromSourceStore()
    {
        SecurityContextHolder.setUserId("1");
        SecurityContextHolder.setUserName("admin");
        FakeTransferOrderMapper orderMapper = new FakeTransferOrderMapper();
        FakeTransferDetailMapper detailMapper = new FakeTransferDetailMapper();
        InvTransferServiceImpl service = transferService(orderMapper,
                detailMapper);
        orderMapper.stored = persistedTransfer(InvStatusConstants.DRAFT);
        detailMapper.details.add(persistedDetail());

        assertThatThrownBy(() -> service.deleteTransfer(900L, 201L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("只有调入门店");
        assertThat(orderMapper.deleteCalls).isZero();
        assertThat(detailMapper.details).hasSize(1);
    }

    @Test
    @DisplayName("删除只依据加锁后的当前状态，等待锁期间已提交则拒绝")
    void deletionUsesLockedStateInsteadOfOldDraftSnapshot()
    {
        SecurityContextHolder.setUserId("1");
        FakeTransferOrderMapper orders = new FakeTransferOrderMapper();
        FakeTransferDetailMapper details = new FakeTransferDetailMapper();
        orders.stored = persistedTransfer(InvStatusConstants.DRAFT);
        orders.lockedReadOverride = persistedTransfer(InvStatusConstants.SUBMITTED);
        details.details.add(persistedDetail());
        assertThatThrownBy(() -> transferService(orders, details).deleteTransfer(900L, 202L))
                .hasMessageContaining("当前状态不允许删除");
        assertThat(orders.deleteCalls).isZero();
        assertThat(details.details).hasSize(1);
    }

    @Test
    @DisplayName("已取消但存在历史预留的单据不能被删除")
    void deletionRetainsCancelledTransferWithReservationHistory()
    {
        SecurityContextHolder.setUserId("1");
        FakeTransferOrderMapper orders = new FakeTransferOrderMapper();
        FakeTransferDetailMapper details = new FakeTransferDetailMapper();
        orders.stored = persistedTransfer(InvStatusConstants.CANCELLED);
        orders.deletionReservationIds = List.of(71L);
        details.details.add(persistedDetail());
        assertThatThrownBy(() -> transferService(orders, details).deleteTransfer(900L, 202L))
                .hasMessageContaining("已有预留");
        assertThat(orders.deleteCalls).isZero();
        assertThat(details.details).hasSize(1);
    }

    @Test
    @DisplayName("无业务事实的草稿按加锁状态和版本删除")
    void unusedDraftDeletionCarriesLockedVersion()
    {
        SecurityContextHolder.setUserId("1");
        FakeTransferOrderMapper orders = new FakeTransferOrderMapper();
        FakeTransferDetailMapper details = new FakeTransferDetailMapper();
        orders.stored = persistedTransfer(InvStatusConstants.DRAFT);
        orders.stored.setVersion(7L);
        details.details.add(persistedDetail());
        transferService(orders, details).deleteTransfer(900L, 202L);
        assertThat(orders.lastDeleteExpectedVersion).isEqualTo(7L);
        assertThat(orders.deleteCalls).isEqualTo(1);
        assertThat(orders.stored).isNull();
        assertThat(details.details).isEmpty();
    }

    @Test
    @DisplayName("已提交调拨单不能物理删除")
    void shouldRejectDeletingSubmittedTransfer()
    {
        SecurityContextHolder.setUserId("1");
        SecurityContextHolder.setUserName("admin");
        FakeTransferOrderMapper orderMapper = new FakeTransferOrderMapper();
        FakeTransferDetailMapper detailMapper = new FakeTransferDetailMapper();
        InvTransferServiceImpl service = transferService(orderMapper, detailMapper);
        orderMapper.stored = persistedTransfer(InvStatusConstants.SUBMITTED);
        detailMapper.details.add(persistedDetail());

        assertThatThrownBy(() -> service.deleteTransfer(900L, 201L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("当前状态不允许删除");
        assertThat(orderMapper.deleteCalls).isZero();
        assertThat(detailMapper.details).hasSize(1);
    }

    @Test
    @DisplayName("出库只校验发货门店可见性")
    void shouldRequireSourceShopForTransferDelivery()
    {
        RecordingTransferService service = new RecordingTransferService();
        InvTransferOrder order = transferOrder();

        service.assertTransferDeliverScope(order, 99L);

        assertThat(service.checkedDeptIds).containsExactly(201L);
        assertThat(service.messages).containsExactly("无权操作该调拨单的发货仓库");
    }

    @Test
    @DisplayName("入库只校验目标门店可见性")
    void shouldRequireTargetShopForTransferReceive()
    {
        RecordingTransferService service = new RecordingTransferService();
        InvTransferOrder order = transferOrder();

        service.assertTransferReceiveScope(order, 99L);

        assertThat(service.checkedDeptIds).containsExactly(202L);
        assertThat(service.messages).containsExactly("无权操作该调拨单的目标门店");
    }

    private InvTransferOrder transferOrder()
    {
        InvTransferOrder order = new InvTransferOrder();
        order.setFromDeptId(201L);
        order.setToDeptId(202L);
        order.setTransferType("cross_store");
        return order;
    }

    private InvTransferOrder salesDeliveryTransfer()
    {
        InvTransferOrder order = transferOrder();
        order.setPurchaseId(null);
        order.setFromWarehouseId(301L);
        order.setToWarehouseId(202L);
        order.setSourceBusinessType(
                InvTransferTypes.SOURCE_SALES_DELIVERY_NOTICE);
        order.setSourceBusinessId(101L);
        return order;
    }

    private InvTransferDetail frozenDetail(String quantity,
            String costPrice, String amount)
    {
        InvTransferDetail detail = detail();
        detail.setQuantity(new BigDecimal(quantity));
        detail.setCostPrice(costPrice == null ? null
                : new BigDecimal(costPrice));
        detail.setAmount(amount == null ? null : new BigDecimal(amount));
        return detail;
    }

    private InvTransferOrder warehouseReplenishmentOrder(Long toDeptId)
    {
        InvTransferOrder order = new InvTransferOrder();
        order.setFromDeptId(301L);
        order.setFromWarehouseId(301L);
        order.setToDeptId(toDeptId);
        order.setToWarehouseId(toDeptId);
        order.setTransferType("warehouse");
        return order;
    }

    private InvTransferOrder persistedTransfer(String status)
    {
        InvTransferOrder order = transferOrder();
        order.setTransferId(900L);
        order.setOrderNo("TF202606060001");
        order.setStatus(status);
        order.setFromDeptName("门店201");
        order.setToDeptName("门店202");
        order.setFromWarehouseId(201L);
        order.setToWarehouseId(202L);
        order.setSourceConfirmStatus(
                InvTransferSourceConfirmStatus.CONFIRMED);
        return order;
    }

    private InvTransferOrder draftEdit(Long version, String remark)
    {
        InvTransferOrder order = transferOrder();
        order.setTransferId(900L);
        order.setVersion(version);
        order.setRemark(remark);
        return order;
    }

    private Object saveDraftInThread(InvTransferServiceImpl service,
            InvTransferOrder order)
    {
        SecurityContextHolder.setUserId("9");
        SecurityContextHolder.setUserName("storeUser");
        try
        {
            return service.saveDraft(order, null, 202L);
        }
        catch (ServiceException ex)
        {
            return ex;
        }
        finally
        {
            SecurityContextHolder.remove();
        }
    }

    private InvTransferOrder persistedWarehouseTransfer(String status)
    {
        InvTransferOrder order = warehouseReplenishmentOrder(202L);
        order.setTransferId(900L);
        order.setOrderNo("TF202606060001");
        order.setStatus(status);
        order.setFromDeptName("仓库301");
        order.setToDeptName("门店202");
        return order;
    }

    private InvTransferDetail detail()
    {
        InvTransferDetail detail = new InvTransferDetail();
        detail.setProductId(1001L);
        detail.setProductName("测试商品");
        detail.setQuantity(new BigDecimal("2"));
        return detail;
    }

    private InvTransferDetail persistedDetail()
    {
        InvTransferDetail detail = detail();
        detail.setDetailId(501L);
        detail.setTransferId(900L);
        detail.setDeliveredQuantity(BigDecimal.ZERO);
        detail.setReceivedQuantity(BigDecimal.ZERO);
        return detail;
    }

    private InvTransferShipment pendingShipment(Long shipmentId, Long transferId)
    {
        InvTransferShipment shipment = new InvTransferShipment();
        shipment.setShipmentId(shipmentId);
        shipment.setTransferId(transferId);
        shipment.setShipmentNo("TS202606060001");
        shipment.setWarehouseId(201L);
        shipment.setWarehouseDeptId(201L);
        shipment.setStatus(InvStatusConstants.PENDING_RECEIVE);
        return shipment;
    }

    private InvTransferShipmentDetail pendingShipmentDetail(Long shipmentId, Long transferDetailId, String quantity)
    {
        InvTransferShipmentDetail detail = new InvTransferShipmentDetail();
        detail.setShipmentDetailId(810L);
        detail.setShipmentId(shipmentId);
        detail.setTransferId(900L);
        detail.setTransferDetailId(transferDetailId);
        detail.setProductId(1001L);
        detail.setProductName("测试商品");
        detail.setPlannedQuantity(new BigDecimal("10"));
        detail.setShippedQuantity(new BigDecimal(quantity));
        detail.setReceivedQuantity(BigDecimal.ZERO);
        detail.setCostPrice(BigDecimal.ONE);
        return detail;
    }

    private InvReceiveRequest receiveRequest(Long detailId, String quantity)
    {
        InvReceiveRequest request = new InvReceiveRequest();
        com.erp.inventory.domain.dto.InvReceiveItem item = new com.erp.inventory.domain.dto.InvReceiveItem();
        item.setDetailId(detailId);
        item.setReceiveQuantity(new BigDecimal(quantity));
        request.setItems(Collections.singletonList(item));
        return request;
    }

    private InvTransferSourceConfirmRequest sourceConfirmRequest(
            Long detailId, String quantity, String remark)
    {
        InvTransferSourceConfirmItem item =
                new InvTransferSourceConfirmItem();
        item.setDetailId(detailId);
        item.setConfirmedQuantity(new BigDecimal(quantity));
        InvTransferSourceConfirmRequest request =
                new InvTransferSourceConfirmRequest();
        request.setItems(Collections.singletonList(item));
        request.setRemark(remark);
        return request;
    }

    private InvTransferServiceImpl transferService(FakeTransferOrderMapper orderMapper, FakeTransferDetailMapper detailMapper)
    {
        return transferService(orderMapper, detailMapper, new FakeStatusLogMapper());
    }

    private InvTransferServiceImpl transferService(FakeTransferOrderMapper orderMapper, FakeTransferDetailMapper detailMapper,
            FakeStatusLogMapper statusLogMapper)
    {
        return transferService(orderMapper, detailMapper, statusLogMapper,
                new FakeTransferShipmentMapper(), new FakeTransferShipmentDetailMapper(), new FakeStockMapper());
    }

    private InvTransferServiceImpl transferService(FakeTransferOrderMapper orderMapper, FakeTransferDetailMapper detailMapper,
            FakeDeptScopeMapper deptScopeMapper)
    {
        InvTransferServiceImpl service = transferService(orderMapper, detailMapper);
        ReflectionTestUtils.setField(service, "deptScopeMapper", deptScopeMapper);
        return service;
    }

    private InvTransferServiceImpl transferService(FakeTransferOrderMapper orderMapper, FakeTransferDetailMapper detailMapper,
            FakeStatusLogMapper statusLogMapper, FakeTransferShipmentMapper shipmentMapper,
            FakeTransferShipmentDetailMapper shipmentDetailMapper, FakeStockMapper stockMapper)
    {
        InvTransferServiceImpl service = new InvTransferServiceImpl();
        ReflectionTestUtils.setField(service, "transferOrderMapper", orderMapper);
        ReflectionTestUtils.setField(service, "transferDetailMapper", detailMapper);
        ReflectionTestUtils.setField(service, "numberSequenceMapper", new FakeNumberSequenceMapper());
        ReflectionTestUtils.setField(service, "deptScopeMapper", new FakeDeptScopeMapper());
        ReflectionTestUtils.setField(service, "transferApprovalService", new FakeApprovalService());
        configureLegacyUnifiedApproval(service);
        ReflectionTestUtils.setField(service, "statusLogMapper", statusLogMapper);
        ReflectionTestUtils.setField(service, "transferShipmentMapper", shipmentMapper);
        ReflectionTestUtils.setField(service, "transferShipmentDetailMapper", shipmentDetailMapper);
        ReflectionTestUtils.setField(service, "transferDiscrepancyMapper",
                mock(com.erp.inventory.mapper.InvTransferDiscrepancyMapper.class));
        ReflectionTestUtils.setField(service,
                "transferDiscrepancyDispositionMapper",
                mock(com.erp.inventory.mapper.InvTransferDiscrepancyDispositionMapper.class));
        ReflectionTestUtils.setField(service, "stockMapper", stockMapper);
        ReflectionTestUtils.setField(service, "transferReservationService",
                new FakeTransferReservationService(stockMapper));
        ReflectionTestUtils.setField(service, "transferRevisionService",
                mock(InvTransferRevisionService.class));
        ReflectionTestUtils.setField(service, "stockLogMapper", new FakeStockLogMapper());
        ReflectionTestUtils.setField(service, "itemResolver", itemResolver());
        return service;
    }

    private static void configureLegacyUnifiedApproval(InvTransferServiceImpl service)
    {
        ReflectionTestUtils.setField(service, "unifiedApprovalService",
                mock(InventoryUnifiedApprovalService.class));
    }

    private InventoryItemResolver itemResolver()
    {
        return itemResolver(301L);
    }

    private InventoryItemResolver itemResolver(Long ownerDeptId)
    {
        return itemResolverSequence(ownerDeptId);
    }

    private InventoryItemResolver itemResolverSequence(
            Long... ownerDeptIds)
    {
        return new InventoryItemResolver()
        {
            // This fixture supplies catalog snapshots; real master locking is exercised by the MySQL suite.
            @Override public void lockReferences(java.util.Collection<InventoryItemResolver.ReferenceKey> keys) {}
            private int invocation;

            @Override
            public InventoryItemSnapshot resolve(String itemType, Long itemId, Long productId)
            {
                String normalizedType = com.erp.inventory.constant.InvItemTypes
                        .normalize(itemType);
                Long resolvedItemId = com.erp.inventory.constant.InvItemTypes
                        .resolveItemId(normalizedType, itemId, productId);
                InventoryItemSnapshot item = new InventoryItemSnapshot();
                item.setItemType(normalizedType);
                item.setItemId(resolvedItemId);
                item.setProductId("product".equals(normalizedType)
                        ? resolvedItemId : null);
                item.setItemCode("oe".equals(normalizedType)
                        ? "OE-0031" : "P-1001");
                item.setItemName("oe".equals(normalizedType)
                        ? "测试OE器皿" : "测试商品");
                item.setUnit("件");
                item.setStatus("0");
                int index = Math.min(invocation,
                        ownerDeptIds.length - 1);
                item.setOwnerDeptId(ownerDeptIds[index]);
                invocation++;
                item.setCostPrice(BigDecimal.ONE);
                return item;
            }
        };
    }

    private static class RecordingTransferService extends InvTransferServiceImpl
    {
        private final List<Long> checkedDeptIds = new ArrayList<>();
        private final List<String> messages = new ArrayList<>();

        @Override
        protected void assertShopVisible(Long shopDeptId, Long selectedShopDeptId, String errorMsg)
        {
            checkedDeptIds.add(shopDeptId);
            messages.add(errorMsg);
        }
    }

    private static class FakeTransferOrderMapper implements InvTransferOrderMapper
    {
        private InvTransferOrder stored;
        private List<InvTransferOrder> listRows;
        private InvTransferOrder lastListQuery;
        private InvTransferOrder lastOpsQuery;
        private final List<InvTransferOrder> updates = new ArrayList<>();
        private final List<InvTransferOrder> insertedOrders =
                new ArrayList<>();
        private Runnable beforeDraftCas;
        private CyclicBarrier draftCasBarrier;
        private Long lastDraftExpectedVersion;
        private int deleteCalls;
        private InvTransferOrder lockedReadOverride;
        private List<Long> deletionReservationIds = List.of();
        private Long lastDeleteExpectedVersion;
        private InvTransferOrder concurrentSalesDelivery;
        private int sourceForUpdateQueries;

        @Override
        public InvTransferOrder selectInvTransferOrderById(Long transferId)
        {
            return stored;
        }

        @Override
        public InvTransferOrder selectInvTransferOrderByIdForUpdate(Long transferId)
        {
            return lockedReadOverride == null ? stored : lockedReadOverride;
        }

        @Override
        public List<InvTransferOrder> selectInvTransferOrderList(InvTransferOrder order)
        {
            lastListQuery = order;
            return listRows != null ? listRows : Collections.singletonList(stored);
        }

        @Override
        public com.erp.inventory.domain.vo.InvTransferOpsSummaryVo selectOpsSummary(
                InvTransferOrder order)
        {
            lastOpsQuery = order;
            return new com.erp.inventory.domain.vo.InvTransferOpsSummaryVo();
        }

        @Override
        public int insertInvTransferOrder(InvTransferOrder order)
        {
            if (concurrentSalesDelivery != null)
            {
                stored = concurrentSalesDelivery;
                concurrentSalesDelivery = null;
                throw new org.springframework.dao.DuplicateKeyException(
                        "duplicate sales delivery notice source");
            }
            order.setTransferId(stored == null
                    ? 900L
                    : 900L + Math.max(1, insertedOrders.size()));
            insertedOrders.add(order);
            if (stored == null)
            {
                stored = order;
            }
            return 1;
        }

        @Override
        public int updateInvTransferOrder(InvTransferOrder order)
        {
            updates.add(order);
            if (order.getStatus() != null) stored.setStatus(order.getStatus());
            if (order.getTotalQuantity() != null) stored.setTotalQuantity(order.getTotalQuantity());
            if (order.getTotalAmount() != null) stored.setTotalAmount(order.getTotalAmount());
            if (order.getApprovalInstanceId() != null) stored.setApprovalInstanceId(order.getApprovalInstanceId());
            if (order.getApprovalRound() != null) stored.setApprovalRound(order.getApprovalRound());
            if (order.getApprovalEngine() != null) stored.setApprovalEngine(order.getApprovalEngine());
            if (order.getSubmittedTime() != null) stored.setSubmittedTime(order.getSubmittedTime());
            if (order.getSubmittedByUserId() != null)
            {
                stored.setSubmittedByUserId(order.getSubmittedByUserId());
                stored.setSubmittedByName(order.getSubmittedByName());
            }
            if (order.getApprovedTime() != null) stored.setApprovedTime(order.getApprovedTime());
            if (order.getDeliveredTime() != null) stored.setDeliveredTime(order.getDeliveredTime());
            if (order.getReceivedTime() != null) stored.setReceivedTime(order.getReceivedTime());
            if (order.getArchivedTime() != null) stored.setArchivedTime(order.getArchivedTime());
            if (order.getCloseReason() != null) stored.setCloseReason(order.getCloseReason());
            if (order.getSourceConfirmStatus() != null) stored.setSourceConfirmStatus(order.getSourceConfirmStatus());
            if (order.getSourceConfirmedUserId() != null) stored.setSourceConfirmedUserId(order.getSourceConfirmedUserId());
            if (order.getSourceConfirmedBy() != null) stored.setSourceConfirmedBy(order.getSourceConfirmedBy());
            if (order.getSourceConfirmedTime() != null) stored.setSourceConfirmedTime(order.getSourceConfirmedTime());
            if (order.getSourceConfirmRemark() != null) stored.setSourceConfirmRemark(order.getSourceConfirmRemark());
            if (order.getReselectionFromTransferId() != null) stored.setReselectionFromTransferId(order.getReselectionFromTransferId());
            if (order.getUpdateBy() != null) stored.setUpdateBy(order.getUpdateBy());
            return 1;
        }

        @Override
        public int updateDraftIfVersionMatches(InvTransferOrder order)
        {
            Runnable concurrentChange;
            synchronized (this)
            {
                concurrentChange = beforeDraftCas;
                beforeDraftCas = null;
            }
            if (concurrentChange != null)
            {
                concurrentChange.run();
            }
            CyclicBarrier barrier = draftCasBarrier;
            if (barrier != null)
            {
                try
                {
                    barrier.await(5, TimeUnit.SECONDS);
                }
                catch (Exception ex)
                {
                    throw new AssertionError("并发草稿CAS测试未能同步", ex);
                }
            }
            synchronized (this)
            {
                lastDraftExpectedVersion = order.getVersion();
                Long currentVersion = stored == null || stored.getVersion() == null
                        ? 0L : stored.getVersion();
                if (stored == null
                        || !InvStatusConstants.DRAFT.equals(stored.getStatus())
                        || !java.util.Objects.equals(currentVersion, order.getVersion()))
                {
                    return 0;
                }
                updates.add(order);
                if (order.getFromDeptId() != null) stored.setFromDeptId(order.getFromDeptId());
                if (order.getFromDeptName() != null) stored.setFromDeptName(order.getFromDeptName());
                if (order.getFromWarehouseId() != null) stored.setFromWarehouseId(order.getFromWarehouseId());
                if (order.getToDeptId() != null) stored.setToDeptId(order.getToDeptId());
                if (order.getToDeptName() != null) stored.setToDeptName(order.getToDeptName());
                if (order.getToWarehouseId() != null) stored.setToWarehouseId(order.getToWarehouseId());
                if (order.getTransferType() != null) stored.setTransferType(order.getTransferType());
                if (order.getSourceConfirmStatus() != null) stored.setSourceConfirmStatus(order.getSourceConfirmStatus());
                if (order.getTotalQuantity() != null) stored.setTotalQuantity(order.getTotalQuantity());
                if (order.getTotalAmount() != null) stored.setTotalAmount(order.getTotalAmount());
                if (order.getRemark() != null) stored.setRemark(order.getRemark());
                if (order.getUpdateBy() != null) stored.setUpdateBy(order.getUpdateBy());
                stored.setVersion(currentVersion + 1);
                return 1;
            }
        }

        @Override
        public synchronized int finalizeNativeApprovalStart(Long transferId,
                Integer businessRound, Long expectedVersion, Long instanceId,
                String updateBy)
        {
            Long currentVersion = stored == null || stored.getVersion() == null
                    ? 0L : stored.getVersion();
            if (stored == null || !transferId.equals(stored.getTransferId())
                    || !InvStatusConstants.SUBMITTED.equals(stored.getStatus())
                    || !InventoryUnifiedApprovalService.ENGINE_NATIVE.equals(
                            stored.getApprovalEngine())
                    || !java.util.Objects.equals(businessRound,
                            stored.getApprovalRound())
                    || !java.util.Objects.equals(expectedVersion,
                            currentVersion)
                    || stored.getApprovalInstanceId() != null)
            {
                return 0;
            }
            stored.setApprovalInstanceId(instanceId);
            stored.setVersion(currentVersion + 1);
            stored.setUpdateBy(updateBy);
            return 1;
        }

        @Override
        public List<Long> selectShipmentIdsForDeletionForUpdate(Long transferId) { return List.of(); }
        @Override
        public List<Long> selectDiscrepancyIdsForDeletionForUpdate(Long transferId) { return List.of(); }
        @Override
        public List<Long> selectReservationIdsForDeletionForUpdate(Long transferId) { return deletionReservationIds; }

        @Override
        public int deleteDraftIfVersionMatches(Long transferId, String expectedStatus, Long expectedVersion)
        {
            lastDeleteExpectedVersion = expectedVersion;
            if (stored == null || !java.util.Objects.equals(stored.getStatus(), expectedStatus)
                    || !java.util.Objects.equals(stored.getVersion() == null ? 0L : stored.getVersion(), expectedVersion)) return 0;
            return deleteInvTransferOrderById(transferId);
        }

        @Override
        public int deleteInvTransferOrderById(Long transferId)
        {
            deleteCalls++;
            stored = null;
            return 1;
        }

        @Override
        public InvTransferOrder selectByPurchaseId(Long purchaseId)
        {
            return stored;
        }

        @Override
        public InvTransferOrder selectBySourceBusinessTypeIdWarehouse(
                String sourceBusinessType, Long sourceBusinessId,
                Long fromWarehouseId)
        {
            if (stored == null
                    || !java.util.Objects.equals(sourceBusinessType,
                            stored.getSourceBusinessType())
                    || !java.util.Objects.equals(sourceBusinessId,
                            stored.getSourceBusinessId())
                    || !java.util.Objects.equals(fromWarehouseId,
                            stored.getFromWarehouseId()))
            {
                return null;
            }
            return stored;
        }

        @Override
        public InvTransferOrder selectBySourceBusinessTypeIdWarehouseForUpdate(
                String sourceBusinessType, Long sourceBusinessId,
                Long fromWarehouseId)
        {
            sourceForUpdateQueries++;
            return selectBySourceBusinessTypeIdWarehouse(sourceBusinessType,
                    sourceBusinessId, fromWarehouseId);
        }
    }

    private static class FakeTransferDetailMapper implements InvTransferDetailMapper
    {
        private final List<InvTransferDetail> details = new ArrayList<>();
        private int deleteCalls;

        @Override
        public List<InvTransferDetail> selectByTransferId(Long transferId)
        {
            return details;
        }

        @Override
        public List<InvTransferDetail> selectByTransferIdForUpdate(Long transferId)
        {
            return details;
        }

        @Override
        public int insertInvTransferDetail(InvTransferDetail detail)
        {
            details.add(detail);
            return 1;
        }

        @Override
        public int batchInsertInvTransferDetail(List<InvTransferDetail> details)
        {
            long nextDetailId = 501L + this.details.size();
            for (InvTransferDetail detail : details)
            {
                if (detail.getDetailId() == null)
                {
                    detail.setDetailId(nextDetailId++);
                }
            }
            this.details.addAll(details);
            return details.size();
        }

        @Override
        public int updateDeliveredQuantity(InvTransferDetail detail)
        {
            return 1;
        }

        @Override
        public int decreaseDeliveredQuantity(InvTransferDetail detail)
        {
            return 1;
        }

        @Override
        public int updateReceivedQuantity(InvTransferDetail detail)
        {
            return 1;
        }

        @Override
        public int deleteByTransferId(Long transferId)
        {
            deleteCalls++;
            details.clear();
            return 1;
        }
    }

    private static class FakeTransferShipmentMapper implements InvTransferShipmentMapper
    {
        @Override public List<InvTransferShipment> selectByIds(List<Long> shipmentIds) { return java.util.Collections.emptyList(); }
        private final List<InvTransferShipment> shipments = new ArrayList<>();

        @Override
        public InvTransferShipment selectById(Long shipmentId)
        {
            return shipments.stream().filter(item -> shipmentId.equals(item.getShipmentId())).findFirst().orElse(null);
        }

        @Override
        public InvTransferShipment selectByIdForUpdate(Long shipmentId)
        {
            return selectById(shipmentId);
        }

        @Override
        public List<InvTransferShipment> selectByTransferId(Long transferId)
        {
            List<InvTransferShipment> result = new ArrayList<>();
            for (InvTransferShipment shipment : shipments)
            {
                if (transferId.equals(shipment.getTransferId())) result.add(shipment);
            }
            return result;
        }

        @Override
        public int insertShipment(InvTransferShipment shipment)
        {
            shipment.setShipmentId(800L + shipments.size());
            shipment.setShipmentNo("TS20260606000" + (shipments.size() + 1));
            shipments.add(shipment);
            return 1;
        }

        @Override
        public int updateShipment(InvTransferShipment shipment)
        {
            InvTransferShipment stored = selectById(shipment.getShipmentId());
            if (stored == null) return 0;
            if (shipment.getStatus() != null) stored.setStatus(shipment.getStatus());
            if (shipment.getReceivedBy() != null) stored.setReceivedBy(shipment.getReceivedBy());
            if (shipment.getReceivedTime() != null) stored.setReceivedTime(shipment.getReceivedTime());
            return 1;
        }
    }

    private static class FakeTransferShipmentDetailMapper implements InvTransferShipmentDetailMapper
    {
        private final List<InvTransferShipmentDetail> details = new ArrayList<>();

        @Override
        public List<InvTransferShipmentDetail> selectByShipmentId(Long shipmentId)
        {
            List<InvTransferShipmentDetail> result = new ArrayList<>();
            for (InvTransferShipmentDetail detail : details)
            {
                if (shipmentId.equals(detail.getShipmentId())) result.add(detail);
            }
            return result;
        }

        @Override
        public List<InvTransferShipmentDetail> selectByTransferId(Long transferId)
        {
            List<InvTransferShipmentDetail> result = new ArrayList<>();
            for (InvTransferShipmentDetail detail : details)
            {
                if (transferId.equals(detail.getTransferId())) result.add(detail);
            }
            return result;
        }

        @Override
        public int insertShipmentDetail(
                InvTransferShipmentDetail shipmentDetail)
        {
            shipmentDetail.setShipmentDetailId(900L + details.size());
            details.add(shipmentDetail);
            return 1;
        }

        @Override
        public int batchInsertShipmentDetail(List<InvTransferShipmentDetail> shipmentDetails)
        {
            details.addAll(shipmentDetails);
            return shipmentDetails.size();
        }

        @Override
        public int updateReceivedQuantity(InvTransferShipmentDetail detail)
        {
            return 1;
        }
    }

    private static class FakeApprovalService implements IInvTransferApprovalService
    {
        private InvTransferOrder submittedTransfer;
        private String instanceStatus = "running";
        private List<String> approvalWarnings = Collections.emptyList();
        private Map<Long, InvTransferApprovalSummary> summaries = Collections.emptyMap();
        private final List<List<Long>> summaryRequests = new ArrayList<>();
        private final InvTransferApprovalTrack track = new InvTransferApprovalTrack();
        private InvTransferOrder trackTransfer;
        private String deliveryError;
        private final List<Long> cancelledTransferIds = new ArrayList<>();

        @Override
        public InvTransferApprovalInstance createInstanceForSubmit(InvTransferOrder transfer)
        {
            submittedTransfer = transfer;
            InvTransferApprovalInstance instance = new InvTransferApprovalInstance();
            instance.setInstanceId(700L);
            instance.setStatus(instanceStatus);
            instance.setApprovalWarnings(approvalWarnings);
            return instance;
        }

        @Override
        public void cancelRunning(Long transferId)
        {
            cancelledTransferIds.add(transferId);
        }

        @Override
        public boolean canApproveTask(InvTransferApprovalTask task, Long userId)
        {
            return false;
        }

        @Override
        public void assertApprovedForDelivery(InvTransferOrder transfer)
        {
            if (deliveryError != null)
            {
                throw new ServiceException(deliveryError);
            }
        }

        @Override
        public void approve(com.erp.inventory.domain.dto.InvTransferApprovalRequest request, Long selectedShopDeptId)
        {
        }

        @Override
        public com.erp.inventory.domain.vo.InvTransferApprovalTrack selectTrack(InvTransferOrder transfer)
        {
            trackTransfer = transfer;
            return track;
        }

        @Override
        public Map<Long, com.erp.inventory.domain.vo.InvTransferApprovalSummary> selectApprovalSummaries(
                List<Long> transferIds)
        {
            summaryRequests.add(new ArrayList<>(transferIds));
            return summaries;
        }

        @Override
        public InvTransferApprovalPreview previewCandidates(Long ruleId, Long targetDeptId,
                Long selectedShopDeptId)
        {
            return new InvTransferApprovalPreview();
        }
    }

    private static class FakeStatusLogMapper implements InvTransferStatusLogMapper
    {
        private final List<InvTransferStatusLog> logs = new ArrayList<>();

        @Override
        public int insertLog(InvTransferStatusLog log)
        {
            logs.add(log);
            return 1;
        }

        @Override
        public List<InvTransferStatusLog> selectLogsByTransferId(Long transferId)
        {
            return logs.stream()
                    .filter(log -> transferId.equals(log.getTransferId()))
                    .collect(java.util.stream.Collectors.toList());
        }
    }

    private static class FakeNumberSequenceMapper implements InvNumberSequenceMapper
    {
        @Override
        public String selectCurrentSequence(String seqName, String currentDate)
        {
            return null;
        }

        @Override
        public int insertOrUpdateSequence(String seqName, String currentDate, int currentSeq, String prefix)
        {
            return 1;
        }

        @Override
        public int incrementAndGetSequence(String seqName, String currentDate)
        {
            return 1;
        }

        @Override
        public Long selectLastInsertId()
        {
            return 1L;
        }
    }

    private static class FakeDeptScopeMapper implements InvDeptScopeMapper
    {
        private final java.util.Map<Long, String> deptTypes = new java.util.HashMap<>();
        private final java.util.Map<Long, Long> businessRootDeptIds =
                new java.util.HashMap<>();
        private final Set<Long> deniedUserScopeDeptIds = new HashSet<>();
        private final Set<String> deniedDeptScopePairs = new HashSet<>();

        private FakeDeptScopeMapper()
        {
            deptTypes.put(201L, "STORE");
            deptTypes.put(202L, "STORE");
            deptTypes.put(203L, "STORE");
            deptTypes.put(301L, "WAREHOUSE");
            deptTypes.put(302L, "WAREHOUSE");
        }

        @Override
        public List<Long> selectUserAuthorizedInventoryDeptIds(Long userId)
        {
            return authorizedInventoryDeptIds();
        }

        @Override
        public List<Long> selectAllActiveInventoryDeptIds()
        {
            return authorizedInventoryDeptIds();
        }

        private List<Long> authorizedInventoryDeptIds()
        {
            return deptTypes.keySet().stream()
                    .filter(deptId -> !deniedUserScopeDeptIds
                            .contains(deptId))
                    .sorted()
                    .toList();
        }

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
            return businessRootDeptIds.getOrDefault(deptId, 100L);
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
            if (deniedDeptScopePairs.contains(scopeDeptId + ":" + targetDeptId))
            {
                return 0;
            }
            return 1;
        }

        @Override
        public int countUserShopScope(Long userId, Long deptId)
        {
            if (deniedUserScopeDeptIds.contains(deptId))
            {
                return 0;
            }
            return 1;
        }

        @Override
        public String selectDeptNameById(Long deptId)
        {
            return "门店" + deptId;
        }

        @Override
        public String selectDeptTypeById(Long deptId)
        {
            return deptTypes.getOrDefault(deptId, "STORE");
        }
    }

    private static class FakeStockMapper implements InvStockMapper
    {
        private InvStock sourceStock;
        private InvStock targetStock;
        private Long targetLookupShopDeptId;
        private Long targetLookupWarehouseId;
        private BigDecimal lastIncomingCost;
        private int targetPlainLookupCalls;
        private int targetForUpdateLookupCalls;
        private boolean duplicateOnInsert;
        private InvStock duplicateTargetStock;

        private FakeStockMapper()
        {
            sourceStock = new InvStock();
            sourceStock.setStockId(1L);
            sourceStock.setProductId(1001L);
            sourceStock.setShopDeptId(201L);
            sourceStock.setWarehouseId(301L);
            sourceStock.setCurrentQuantity(new BigDecimal("10"));
            sourceStock.setLockedQuantity(BigDecimal.ZERO);
            sourceStock.setAvailableQuantity(new BigDecimal("10"));
            sourceStock.setCostPrice(new BigDecimal("1"));
            targetStock = new InvStock();
            targetStock.setStockId(2L);
            targetStock.setProductId(1001L);
            targetStock.setShopDeptId(202L);
            targetStock.setWarehouseId(null);
            targetStock.setCurrentQuantity(BigDecimal.ZERO);
            targetStock.setLockedQuantity(BigDecimal.ZERO);
            targetStock.setAvailableQuantity(BigDecimal.ZERO);
            targetStock.setCostPrice(BigDecimal.ONE);
        }

        @Override
        public List<InvStock> selectInvStockList(InvStock stock)
        {
            return Collections.singletonList(sourceStock);
        }

        @Override
        public InvStockSummary selectInvStockSummary(InvStock stock)
        {
            return null;
        }

        @Override
        public InvStock selectInvStockById(Long stockId)
        {
            if (Long.valueOf(2L).equals(stockId)) return targetStock;
            return sourceStock;
        }

        @Override
        public InvStock selectInvStockByIdForUpdate(Long stockId)
        {
            return selectInvStockById(stockId);
        }

        @Override
        public InvStock selectInvStockByProductAndShop(Long productId, Long shopDeptId)
        {
            return Long.valueOf(202L).equals(shopDeptId) ? targetStock : sourceStock;
        }

        @Override
        public InvStock selectInvStockByProductAndShopForUpdate(Long productId, Long shopDeptId)
        {
            return Long.valueOf(202L).equals(shopDeptId) ? targetStock : sourceStock;
        }

        @Override
        public InvStock selectInvStockByProductShopWarehouse(Long productId, Long shopDeptId, Long warehouseId)
        {
            if (Long.valueOf(202L).equals(shopDeptId)) {
                targetPlainLookupCalls++;
                targetLookupShopDeptId = shopDeptId;
                targetLookupWarehouseId = warehouseId;
                return targetStock;
            }
            return sourceStock;
        }

        @Override
        public InvStock selectInvStockByProductShopWarehouseForUpdate(Long productId, Long shopDeptId, Long warehouseId)
        {
            if (Long.valueOf(202L).equals(shopDeptId)) {
                targetForUpdateLookupCalls++;
                targetLookupShopDeptId = shopDeptId;
                targetLookupWarehouseId = warehouseId;
                return targetStock;
            }
            return sourceStock;
        }

        @Override
        public InvStock selectInvStockByItemShopWarehouse(String itemType, Long itemId, Long shopDeptId, Long warehouseId)
        {
            return selectInvStockByProductShopWarehouse(itemId, shopDeptId, warehouseId);
        }

        @Override
        public InvStock selectInvStockByItemShopWarehouseForUpdate(String itemType, Long itemId, Long shopDeptId, Long warehouseId)
        {
            return selectInvStockByProductShopWarehouseForUpdate(itemId, shopDeptId, warehouseId);
        }

        @Override
        public int insertInvStock(InvStock stock)
        {
            if (duplicateOnInsert) {
                targetStock = duplicateTargetStock;
                duplicateOnInsert = false;
                throw new org.springframework.dao.DuplicateKeyException("duplicate stock");
            }
            stock.setVersion(stock.getVersion() == null ? 0L : stock.getVersion());
            this.targetStock = stock;
            return 1;
        }

        @Override
        public int updateInvStock(InvStock stock)
        {
            if (Long.valueOf(2L).equals(stock.getStockId())) {
                this.targetStock = stock;
            } else {
                this.sourceStock = stock;
            }
            return 1;
        }

        @Override
        public int addInvStock(Long stockId, Long version, BigDecimal quantity, String updateBy)
        {
            InvStock stock = Long.valueOf(2L).equals(stockId) ? targetStock : sourceStock;
            stock.setCurrentQuantity(stock.getCurrentQuantity().add(quantity));
            stock.setAvailableQuantity(stock.getAvailableQuantity().add(quantity));
            stock.setVersion((stock.getVersion() == null ? 0L : stock.getVersion()) + 1);
            return 1;
        }

        @Override
        public int addInvStockWithCost(Long stockId, Long version, BigDecimal quantity, BigDecimal incomingCost, String updateBy)
        {
            lastIncomingCost = incomingCost;
            return addInvStock(stockId, version, quantity, updateBy);
        }

        @Override
        public int deductInvStock(Long stockId, Long version, BigDecimal quantity, String updateBy)
        {
            sourceStock.setCurrentQuantity(sourceStock.getCurrentQuantity().subtract(quantity));
            sourceStock.setAvailableQuantity(sourceStock.getAvailableQuantity().subtract(quantity));
            sourceStock.setVersion((sourceStock.getVersion() == null ? 0L : sourceStock.getVersion()) + 1);
            return 1;
        }

        @Override
        public int deductInvStockWithCost(Long stockId, Long version, BigDecimal quantity, BigDecimal deductCost, String updateBy)
        {
            return deductInvStock(stockId, version, quantity, updateBy);
        }
    }

    private static class FakeTransferReservationService
            extends InvTransferReservationService
    {
        private final FakeStockMapper stockMapper;

        private FakeTransferReservationService(FakeStockMapper stockMapper)
        {
            super(mock(InvTransferReservationMapper.class));
            this.stockMapper = stockMapper;
        }

        @Override
        public void reserveForSubmission(InvTransferOrder order,
                List<InvTransferDetail> details, String username)
        {
            BigDecimal required = details.stream()
                    .map(InvTransferDetail::getQuantity)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            InvStock stock = stockMapper.sourceStock;
            if (stock == null || stock.getAvailableQuantity() == null
                    || stock.getAvailableQuantity().compareTo(required) < 0)
            {
                throw new ServiceException("物料可用库存不足");
            }
            stock.setLockedQuantity(stock.getLockedQuantity().add(required));
            stock.setAvailableQuantity(
                    stock.getAvailableQuantity().subtract(required));
        }

        @Override
        public StockConsumption consumeForShipment(InvTransferOrder order,
                InvTransferDetail detail, BigDecimal quantity,
                String username)
        {
            InvStock stock = stockMapper.sourceStock;
            if (stock == null || stock.getAvailableQuantity() == null)
            {
                throw new ServiceException("仓库库存不足，当前可用: 0");
            }
            BigDecimal locked = stock.getLockedQuantity() == null
                    ? BigDecimal.ZERO : stock.getLockedQuantity();
            if (locked.compareTo(quantity) < 0)
            {
                BigDecimal additionallyRequired = quantity.subtract(locked);
                if (stock.getAvailableQuantity()
                        .compareTo(additionallyRequired) < 0)
                {
                    throw new ServiceException("仓库库存不足，当前可用: "
                            + stock.getAvailableQuantity());
                }
                stock.setAvailableQuantity(stock.getAvailableQuantity()
                        .subtract(additionallyRequired));
                stock.setLockedQuantity(locked.add(additionallyRequired));
            }
            BigDecimal before = stock.getCurrentQuantity();
            BigDecimal costPrice = stock.getCostPrice() == null
                    ? BigDecimal.ZERO : stock.getCostPrice();
            stock.setCurrentQuantity(before.subtract(quantity));
            stock.setLockedQuantity(
                    stock.getLockedQuantity().subtract(quantity));
            stock.setVersion((stock.getVersion() == null
                    ? 0L : stock.getVersion()) + 1);
            return new StockConsumption(stock.getStockId(), before,
                    stock.getCurrentQuantity(), costPrice,
                    quantity.multiply(costPrice));
        }

        @Override
        public void releaseAllRemaining(InvTransferOrder order,
                String username)
        {
            InvStock stock = stockMapper.sourceStock;
            if (stock == null) return;
            BigDecimal locked = stock.getLockedQuantity() == null
                    ? BigDecimal.ZERO : stock.getLockedQuantity();
            stock.setLockedQuantity(BigDecimal.ZERO);
            stock.setAvailableQuantity(
                    stock.getAvailableQuantity().add(locked));
        }
    }

    private static class FakeStockLogMapper implements InvStockLogMapper
    {
        private final List<InvStockLog> logs = new ArrayList<>();

        @Override
        public List<InvStockLog> selectInvStockLogList(InvStockLog stockLog)
        {
            return logs;
        }

        @Override
        public int insertInvStockLog(InvStockLog stockLog)
        {
            logs.add(stockLog);
            return 1;
        }
    }
}
