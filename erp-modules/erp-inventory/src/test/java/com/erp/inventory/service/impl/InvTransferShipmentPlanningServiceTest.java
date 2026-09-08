package com.erp.inventory.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.erp.common.core.context.SecurityContextHolder;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.security.shop.ShopScopeService;
import com.erp.inventory.constant.InvTransferRevisionStatuses;
import com.erp.inventory.domain.InvTransferDetail;
import com.erp.inventory.domain.InvTransferOrder;
import com.erp.inventory.domain.transfer.InvItemFulfillmentPolicy;
import com.erp.inventory.domain.transfer.InvShipmentAllocationCandidate;
import com.erp.inventory.domain.transfer.InvShipmentSerialCandidate;
import com.erp.inventory.domain.transfer.InvWarehouseStockMode;
import com.erp.inventory.domain.vo.InvTransferRevisionHistoryVo;
import com.erp.inventory.domain.vo.InvTransferRevisionVo;
import com.erp.inventory.domain.vo.InvTransferShipmentPlanningVo;
import com.erp.inventory.mapper.InvDeptScopeMapper;
import com.erp.inventory.mapper.InvTransferDetailMapper;
import com.erp.inventory.mapper.InvTransferOrderMapper;
import com.erp.inventory.mapper.InvTransferShipmentPlanningMapper;

@DisplayName("调拨真实发货规划只读服务")
class InvTransferShipmentPlanningServiceTest
{
    private final InvTransferOrderMapper orderMapper =
            mock(InvTransferOrderMapper.class);
    private final InvTransferDetailMapper detailMapper =
            mock(InvTransferDetailMapper.class);
    private final InvTransferShipmentPlanningMapper planningMapper =
            mock(InvTransferShipmentPlanningMapper.class);
    private final InvTransferRevisionService revisionService =
            mock(InvTransferRevisionService.class);
    private final InvDeptScopeMapper deptScopeMapper =
            mock(InvDeptScopeMapper.class);
    private final ShopScopeService shopScopeService =
            mock(ShopScopeService.class);
    private InvTransferShipmentPlanningService service;
    private InvTransferOrder order;

    @BeforeEach
    void setUp()
    {
        SecurityContextHolder.setUserId("7");
        SecurityContextHolder.setUserName("warehouse-operator");
        when(shopScopeService.resolveRequiredShopDept(301L))
                .thenReturn(301L);
        when(shopScopeService.hasUserShopScope(7L, 301L))
                .thenReturn(true);
        when(deptScopeMapper.countDeptInScope(301L, 301L))
                .thenReturn(1);
        order = order();
        when(orderMapper.selectInvTransferOrderById(900L))
                .thenReturn(order);
        when(revisionService.getHistory(order)).thenReturn(history(
                InvTransferRevisionStatuses.APPROVED));
        service = new InvTransferShipmentPlanningService(orderMapper,
                detailMapper, planningMapper, revisionService,
                deptScopeMapper, shopScopeService,
                Clock.fixed(Instant.parse("2026-08-02T09:30:00Z"),
                        ZoneOffset.UTC));
    }

    @AfterEach
    void tearDown()
    {
        SecurityContextHolder.remove();
    }

    @Test
    @DisplayName("权威明细库存批量形成建议并允许跳过单行阻断")
    void shouldCreateAuthoritativePartialPlan()
    {
        InvTransferDetail food = detail(11L, 1001L, "food", "8", "0");
        InvTransferDetail serial = detail(12L, 1002L, "device", "2", "0");
        InvTransferDetail missingPolicy = detail(13L, 1003L,
                "missing", "1", "0");
        InvTransferDetail complete = detail(14L, 1004L,
                "complete", "2", "2");
        when(detailMapper.selectByTransferId(900L)).thenReturn(List.of(
                food, serial, missingPolicy, complete));
        when(planningMapper.selectWarehouseMode(301L))
                .thenReturn(mode("dual", "detail", "passed"));
        when(planningMapper.selectFulfillmentPolicies(anyList()))
                .thenReturn(List.of(
                        policy(1001L, "FEFO", "lot"),
                        policy(1002L, "FIFO", "serial")));
        InvShipmentAllocationCandidate foodLater = candidate(2L, 1001L,
                "2026-10-01", "2026-07-01", "10", 3L);
        InvShipmentAllocationCandidate foodFirst = candidate(1L, 1001L,
                "2026-09-01", "2026-07-10", "3", 4L);
        InvShipmentAllocationCandidate device = candidate(3L, 1002L,
                null, "2026-07-05", "2", 5L);
        when(planningMapper.selectEligibleBalances(eq(301L), anyList()))
                .thenReturn(List.of(foodLater, foodFirst, device));
        when(planningMapper.selectAvailableSerials(anyList()))
                .thenReturn(List.of(
                        serial(32L, 3L, "DEVICE-1002"),
                        serial(31L, 3L, "DEVICE-1001")));

        InvTransferShipmentPlanningVo result = service.getPlanning(900L,
                301L);

        assertThat(result.canCreateShipment()).isTrue();
        assertThat(result.blockingReasons()).isEmpty();
        assertThat(result.dataSource()).isEqualTo("server");
        assertThat(result.planVersion()).matches("[a-f0-9]{64}");
        assertThat(result.stockAuthority().writeMode()).isEqualTo("dual");
        assertThat(result.stockAuthority().readMode()).isEqualTo("detail");
        assertThat(result.stockAuthority().reconcileStatus())
                .isEqualTo("passed");
        assertThat(result.lines()).extracting(
                InvTransferShipmentPlanningVo.Line::recommendationStatus)
                .containsExactly("ready", "ready", "blocked", "complete");
        assertThat(result.lines().get(0).allocations()).extracting(
                InvTransferShipmentPlanningVo.Allocation::balanceId)
                .containsExactly("1", "2");
        assertThat(result.lines().get(1).allocations().get(0).serials())
                .extracting(InvTransferShipmentPlanningVo.Serial::serialLabel)
                .containsExactly("SN…1001", "SN…1002");
        assertThat(result.lines().get(2).blockers())
                .containsExactly("物料履约策略未配置");
        assertThat(result.lines().get(3).allocationPolicy()).isNull();
        verify(planningMapper).selectEligibleBalances(eq(301L), anyList());
        verify(planningMapper).selectAvailableSerials(anyList());
    }

    @Test
    @DisplayName("非权威仓库不查询余额或序列号且逐项说明阻断")
    void shouldNotReadDetailFactsBeforeAuthorityCutover()
    {
        when(detailMapper.selectByTransferId(900L)).thenReturn(List.of(
                detail(11L, 1001L, "food", "8", "0")));
        when(planningMapper.selectWarehouseMode(301L))
                .thenReturn(mode("legacy", "shadow", "failed"));
        when(planningMapper.selectFulfillmentPolicies(anyList()))
                .thenReturn(List.of(policy(1001L, "FEFO", "lot")));

        InvTransferShipmentPlanningVo result = service.getPlanning(900L,
                301L);

        assertThat(result.canCreateShipment()).isFalse();
        assertThat(result.blockingReasons()).containsExactly(
                "来源仓库尚未启用 dual 明细库存双写",
                "来源仓库尚未切换 detail 明细库存读取",
                "来源仓库明细库存对账尚未通过");
        assertThat(result.lines().get(0).recommendationStatus())
                .isEqualTo("blocked");
        assertThat(result.lines().get(0).blockers())
                .containsExactlyElementsOf(result.blockingReasons());
        verify(planningMapper, never())
                .selectEligibleBalances(301L, List.of());
        verify(planningMapper, never()).selectEligibleBalances(
                org.mockito.ArgumentMatchers.anyLong(), anyList());
        verify(planningMapper, never()).selectAvailableSerials(anyList());
    }

    @Test
    @DisplayName("未知库存模式值降级到保守枚举并保持前端可解析")
    void shouldNormalizeUnknownAuthorityModesToBlockedFallbacks()
    {
        when(detailMapper.selectByTransferId(900L)).thenReturn(List.of(
                detail(11L, 1001L, "food", "8", "0")));
        InvWarehouseStockMode invalid = mode("future-write",
                "future-read", "future-result");
        invalid.setLastReconcileBatch("  ");
        when(planningMapper.selectWarehouseMode(301L)).thenReturn(invalid);
        when(planningMapper.selectFulfillmentPolicies(anyList()))
                .thenReturn(List.of(policy(1001L, "FEFO", "lot")));

        InvTransferShipmentPlanningVo result = service.getPlanning(900L,
                301L);

        assertThat(result.stockAuthority().writeMode()).isEqualTo("legacy");
        assertThat(result.stockAuthority().readMode()).isEqualTo("legacy");
        assertThat(result.stockAuthority().reconcileStatus())
                .isEqualTo("not_run");
        assertThat(result.stockAuthority().lastReconcileBatch()).isNull();
        assertThat(result.canCreateShipment()).isFalse();
        verify(planningMapper, never()).selectEligibleBalances(
                org.mockito.ArgumentMatchers.anyLong(), anyList());
    }

    @Test
    @DisplayName("最新业务版本不是已审批版本时拒绝读取库存事实")
    void shouldRejectUnapprovedRevisionBeforeStockRead()
    {
        when(revisionService.getHistory(order)).thenReturn(history(
                InvTransferRevisionStatuses.SUBMITTED));

        assertThatThrownBy(() -> service.getPlanning(900L, 301L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("未审批或不可核验");

        verify(planningMapper, never()).selectWarehouseMode(301L);
        verify(detailMapper, never()).selectByTransferId(900L);
    }

    @Test
    @DisplayName("跨调拨或重复明细在读取库存模式前即被拒绝")
    void shouldRejectMismatchedTransferDetailsBeforeStockRead()
    {
        InvTransferDetail mismatched = detail(11L, 1001L, "food",
                "8", "0");
        mismatched.setTransferId(901L);
        when(detailMapper.selectByTransferId(900L)).thenReturn(List.of(
                mismatched));

        assertThatThrownBy(() -> service.getPlanning(900L, 301L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("明细归属");

        verify(planningMapper, never()).selectWarehouseMode(301L);
    }

    @Test
    @DisplayName("余额版本变化会改变计划版本以供后续写入重校验")
    void shouldIncludeBalanceVersionInPlanVersion()
    {
        when(detailMapper.selectByTransferId(900L)).thenReturn(List.of(
                detail(11L, 1001L, "food", "2", "0")));
        when(planningMapper.selectWarehouseMode(301L))
                .thenReturn(mode("dual", "detail", "passed"));
        when(planningMapper.selectFulfillmentPolicies(anyList()))
                .thenReturn(List.of(policy(1001L, "FIFO", "lot")));
        InvShipmentAllocationCandidate balance = candidate(1L, 1001L,
                null, "2026-07-01", "2", 1L);
        when(planningMapper.selectEligibleBalances(eq(301L), anyList()))
                .thenReturn(List.of(balance));
        when(planningMapper.selectAvailableSerials(anyList()))
                .thenReturn(List.of());

        String first = service.getPlanning(900L, 301L).planVersion();
        balance.setVersion(2L);
        String second = service.getPlanning(900L, 301L).planVersion();

        assertThat(second).isNotEqualTo(first);
    }

    private static InvTransferOrder order()
    {
        InvTransferOrder value = new InvTransferOrder();
        value.setTransferId(900L);
        value.setOrderNo("TF202608020001");
        value.setFromDeptId(201L);
        value.setFromDeptName("苏州中心仓");
        value.setFromWarehouseId(301L);
        value.setToDeptId(202L);
        value.setToDeptName("南京示范门店");
        value.setToWarehouseId(302L);
        value.setStatus("approved");
        value.setVersion(4L);
        return value;
    }

    private static InvTransferRevisionHistoryVo history(String status)
    {
        InvTransferRevisionVo revision = new InvTransferRevisionVo();
        revision.setRevisionId(700L);
        revision.setRevisionNo(3);
        revision.setStatus(status);
        revision.setSnapshotHash("a".repeat(64));
        InvTransferRevisionHistoryVo history =
                new InvTransferRevisionHistoryVo();
        history.setTransferId(900L);
        history.setOrderNo("TF202608020001");
        history.setCurrentRevisionNo(3);
        history.setCurrentRevisionStatus(status);
        history.setRevisions(List.of(revision));
        return history;
    }

    private static InvTransferDetail detail(Long detailId, Long itemId,
            String code, String approved, String shipped)
    {
        InvTransferDetail value = new InvTransferDetail();
        value.setDetailId(detailId);
        value.setTransferId(900L);
        value.setItemType("product");
        value.setItemId(itemId);
        value.setProductId(itemId);
        value.setItemCode("SKU-" + code);
        value.setItemName("物料-" + code);
        value.setUnit("件");
        value.setQuantity(new BigDecimal(approved));
        value.setDeliveredQuantity(new BigDecimal(shipped));
        return value;
    }

    private static InvWarehouseStockMode mode(String writeMode,
            String readMode, String reconcileStatus)
    {
        InvWarehouseStockMode mode = new InvWarehouseStockMode();
        mode.setWarehouseId(301L);
        mode.setWriteMode(writeMode);
        mode.setReadMode(readMode);
        mode.setReconcileStatus(reconcileStatus);
        mode.setLastReconcileBatch("reconcile-20260802-01");
        return mode;
    }

    private static InvItemFulfillmentPolicy policy(Long itemId,
            String allocation, String tracking)
    {
        InvItemFulfillmentPolicy value = new InvItemFulfillmentPolicy();
        value.setPolicyId(itemId + 5000L);
        value.setItemType("product");
        value.setItemId(itemId);
        value.setAllocationPolicy(allocation);
        value.setTrackingPolicy(tracking);
        value.setStatus("0");
        value.setVersion(1L);
        return value;
    }

    private static InvShipmentAllocationCandidate candidate(Long balanceId,
            Long itemId, String expiry, String received,
            String available, Long version)
    {
        InvShipmentAllocationCandidate value =
                new InvShipmentAllocationCandidate();
        value.setBalanceId(balanceId);
        value.setItemType("product");
        value.setItemId(itemId);
        value.setWarehouseId(301L);
        value.setLotId(100L + balanceId);
        value.setLotNo("LOT-" + balanceId);
        value.setExpiryDate(expiry == null ? null : date(expiry));
        value.setReceivedAt(date(received));
        value.setLocationId(200L + balanceId);
        value.setLocationCode("A-" + balanceId);
        value.setAvailableQuantity(new BigDecimal(available));
        value.setVersion(version);
        return value;
    }

    private static InvShipmentSerialCandidate serial(Long serialId,
            Long balanceId, String serialNo)
    {
        InvShipmentSerialCandidate value =
                new InvShipmentSerialCandidate();
        value.setSerialId(serialId);
        value.setBalanceId(balanceId);
        value.setSerialNo(serialNo);
        value.setSerialStatus("available");
        return value;
    }

    private static Date date(String value)
    {
        return Date.from(LocalDate.parse(value).atStartOfDay(
                ZoneId.of("Asia/Shanghai")).toInstant());
    }
}
