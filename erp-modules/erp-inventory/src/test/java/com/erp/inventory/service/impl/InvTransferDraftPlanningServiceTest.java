package com.erp.inventory.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.erp.common.core.context.SecurityContextHolder;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.security.shop.ShopScopeService;
import com.erp.inventory.domain.InvTransferDetail;
import com.erp.inventory.domain.InvTransferOrder;
import com.erp.inventory.domain.dto.InvTransferDraftPlanningRequest;
import com.erp.inventory.domain.vo.InventoryItemSnapshot;
import com.erp.inventory.domain.vo.InvTransferDraftPlanningVo;
import com.erp.inventory.mapper.InvDeptScopeMapper;

@DisplayName("调拨草稿权威库存规划")
class InvTransferDraftPlanningServiceTest
{
    private final InventoryItemResolver itemResolver =
            mock(InventoryItemResolver.class);
    private final InvTransferReservationService reservationService =
            mock(InvTransferReservationService.class);
    private final InvDeptScopeMapper deptScopeMapper =
            mock(InvDeptScopeMapper.class);
    private final ShopScopeService shopScopeService =
            mock(ShopScopeService.class);
    private InvTransferDraftPlanningService service;

    @BeforeEach
    void setUp()
    {
        SecurityContextHolder.setUserId("7");
        SecurityContextHolder.setUserName("store-manager");
        when(shopScopeService.resolveRequiredShopDept(301L))
                .thenReturn(301L);
        when(shopScopeService.hasUserShopScope(7L, 301L))
                .thenReturn(true);
        when(deptScopeMapper.selectUserAuthorizedInventoryDeptIds(7L))
                .thenReturn(List.of(201L, 301L));
        when(deptScopeMapper.selectActiveRelatedDeptIdsForReplenishment(
                201L))
                .thenReturn(List.of(201L));
        when(deptScopeMapper.selectRawBusinessRootDeptId(anyLong()))
                .thenReturn(100L);
        when(deptScopeMapper.selectDeptTypeById(100L))
                .thenReturn("GROUP");
        when(deptScopeMapper.selectDeptTypeById(201L))
                .thenReturn("WAREHOUSE");
        when(deptScopeMapper.selectDeptTypeById(301L))
                .thenReturn("STORE");
        when(deptScopeMapper.selectDeptNameById(201L))
                .thenReturn("苏州中心仓");
        when(deptScopeMapper.selectDeptNameById(301L))
                .thenReturn("华东示范门店");
        when(itemResolver.resolve("product", 501L, 501L))
                .thenReturn(product("0"));
        service = new InvTransferDraftPlanningService(itemResolver,
                reservationService, deptScopeMapper, shopScopeService,
                Clock.fixed(Instant.parse("2026-08-09T02:30:00Z"),
                        ZoneOffset.UTC));
    }

    @AfterEach
    void tearDown()
    {
        SecurityContextHolder.remove();
    }

    @Test
    @DisplayName("缺货时返回服务端缺口并失败关闭提交")
    void shouldReturnAuthoritativeShortageAndBlockSubmission()
    {
        when(reservationService.previewForDraft(
                org.mockito.ArgumentMatchers.any(InvTransferOrder.class),
                org.mockito.ArgumentMatchers.<InvTransferDetail>anyList()))
                .thenReturn(List.of(availability("7", "5", "2", 3L)));

        InvTransferDraftPlanningVo result = service.getPlanning(request("7"),
                301L);

        assertThat(result.planVersion()).matches("[a-f0-9]{64}");
        assertThat(result.transferType()).isEqualTo("warehouse");
        assertThat(result.source()).isEqualTo(
                new InvTransferDraftPlanningVo.Organization(
                        "201", "苏州中心仓", "warehouse"));
        assertThat(result.destination()).isEqualTo(
                new InvTransferDraftPlanningVo.Organization(
                        "301", "华东示范门店", "store"));
        assertThat(result.generatedAt())
                .isEqualTo("2026-08-09T02:30:00Z");
        assertThat(result.canSubmit()).isFalse();
        assertThat(result.blockingReasons())
                .containsExactly("物料 [精品冷萃咖啡液 250ml] 可用库存不足，缺口 2");
        assertThat(result.lines()).singleElement().satisfies(line -> {
            assertThat(line.itemId()).isEqualTo("501");
            assertThat(line.productId()).isEqualTo("501");
            assertThat(line.requestedQuantity()).isEqualTo("7");
            assertThat(line.availableQuantity()).isEqualTo("5");
            assertThat(line.shortageQuantity()).isEqualTo("2");
            assertThat(line.availabilityStatus())
                    .isEqualTo("insufficient");
        });
        assertThat(result.dataSource()).isEqualTo("server");
    }

    @Test
    @DisplayName("库存充足时允许提交但仍只返回只读规划")
    void shouldAllowSubmissionForSufficientPreview()
    {
        when(reservationService.previewForDraft(
                org.mockito.ArgumentMatchers.any(InvTransferOrder.class),
                org.mockito.ArgumentMatchers.<InvTransferDetail>anyList()))
                .thenReturn(List.of(availability("4", "5", "0", 3L)));

        InvTransferDraftPlanningVo result = service.getPlanning(request("4"),
                301L);

        assertThat(result.canSubmit()).isTrue();
        assertThat(result.blockingReasons()).isEmpty();
        assertThat(result.lines().get(0).availabilityStatus())
                .isEqualTo("sufficient");
    }

    @Test
    @DisplayName("门店要货规划拒绝其他业务根的来源仓库")
    void shouldRejectWarehouseOutsideTargetStoreBusinessRoot()
    {
        when(deptScopeMapper.selectRawBusinessRootDeptId(301L))
                .thenReturn(200L);
        when(deptScopeMapper.selectDeptTypeById(200L))
                .thenReturn("GROUP");

        assertThatThrownBy(() -> service.getPlanning(request("4"), 301L))
                .isInstanceOf(ServiceException.class)
                .hasMessage("当前门店不允许向该仓库要货");
        verify(reservationService, never()).previewForDraft(
                org.mockito.ArgumentMatchers.any(InvTransferOrder.class),
                org.mockito.ArgumentMatchers.<InvTransferDetail>anyList());
    }

    @Test
    @DisplayName("门店返仓规划携带返仓原因并沿用当前门店方向")
    void shouldPlanStoreReturnWithItsRequiredReason()
    {
        when(reservationService.previewForDraft(
                org.mockito.ArgumentMatchers.any(InvTransferOrder.class),
                org.mockito.ArgumentMatchers.<InvTransferDetail>anyList()))
                .thenReturn(List.of(availability("4", "5", "0", 3L)));
        InvTransferDraftPlanningRequest request = request("4");
        request.setTransferType("store_return");
        request.setFromDeptId(301L);
        request.setToDeptId(201L);
        request.setReturnReasonCode("EXCESS_STOCK");

        InvTransferDraftPlanningVo result = service.getPlanning(request,
                301L);

        assertThat(result.transferType()).isEqualTo("store_return");
        assertThat(result.source().id()).isEqualTo("301");
        assertThat(result.destination().id()).isEqualTo("201");
        assertThat(result.canSubmit()).isTrue();
    }

    @Test
    @DisplayName("门店补货规划不要求用户绑定来源仓库")
    void shouldPlanWarehouseReplenishmentWithoutSourceWarehouseScope()
    {
        when(deptScopeMapper.selectUserAuthorizedInventoryDeptIds(7L))
                .thenReturn(List.of(301L));
        when(reservationService.previewForDraft(
                org.mockito.ArgumentMatchers.any(InvTransferOrder.class),
                org.mockito.ArgumentMatchers.<InvTransferDetail>anyList()))
                .thenReturn(List.of(availability("4", "5", "0", 3L)));

        InvTransferDraftPlanningVo result = service.getPlanning(
                request("4"), 301L);

        assertThat(result.source().id()).isEqualTo("201");
        assertThat(result.destination().id()).isEqualTo("301");
        assertThat(result.canSubmit()).isTrue();
        verify(deptScopeMapper, never())
                .selectUserAuthorizedInventoryDeptIds(7L);
    }

    @Test
    @DisplayName("仓库补货物料范围只从来源仓库计算")
    void shouldResolveItemScopeFromSourceWarehouseOnly()
    {
        when(reservationService.previewForDraft(
                org.mockito.ArgumentMatchers.any(InvTransferOrder.class),
                org.mockito.ArgumentMatchers.<InvTransferDetail>anyList()))
                .thenReturn(List.of(availability("4", "5", "0", 3L)));

        InvTransferDraftPlanningVo result = service.getPlanning(request("4"),
                301L);

        assertThat(result.source().id()).isEqualTo("201");
        assertThat(result.canSubmit()).isTrue();
        verify(deptScopeMapper)
                .selectActiveRelatedDeptIdsForReplenishment(201L);
        verify(deptScopeMapper, never())
                .selectActiveRelatedDeptIdsForReplenishment(301L);
    }

    @Test
    @DisplayName("来源仓库上级组织归属的共享商品可以参与规划")
    void shouldAllowItemOwnedBySourceWarehouseAncestor()
    {
        InventoryItemSnapshot shared = product("0");
        shared.setOwnerDeptId(100L);
        when(itemResolver.resolve("product", 501L, 501L))
                .thenReturn(shared);
        when(deptScopeMapper.selectActiveRelatedDeptIdsForReplenishment(
                201L))
                .thenReturn(List.of(100L, 201L));
        when(reservationService.previewForDraft(
                org.mockito.ArgumentMatchers.any(InvTransferOrder.class),
                org.mockito.ArgumentMatchers.<InvTransferDetail>anyList()))
                .thenReturn(List.of(availability("4", "5", "0", 3L)));

        InvTransferDraftPlanningVo result = service.getPlanning(request("4"),
                301L);

        assertThat(result.canSubmit()).isTrue();
    }

    @Test
    @DisplayName("仓库补货目标不是当前门店时在库存读取前被拒绝")
    void shouldRejectWarehouseReplenishmentForAnotherTargetStore()
    {
        InvTransferDraftPlanningRequest request = request("4");
        request.setToDeptId(302L);
        when(deptScopeMapper.selectDeptTypeById(302L)).thenReturn("STORE");

        assertThatThrownBy(() -> service.getPlanning(request, 301L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("补货目标必须为当前门店");

        verify(reservationService, never()).previewForDraft(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    @DisplayName("停用物料在库存读取前被拒绝")
    void shouldRejectDisabledItemBeforeStockRead()
    {
        when(itemResolver.resolve("product", 501L, 501L))
                .thenReturn(product("1"));

        assertThatThrownBy(() -> service.getPlanning(request("4"), 301L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("已停用");

        verify(reservationService, never()).previewForDraft(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    @DisplayName("未授权归属组织的物料在库存读取前被拒绝")
    void shouldRejectItemOwnedByUnauthorizedOrganizationBeforeStockRead()
    {
        InventoryItemSnapshot unauthorized = product("0");
        unauthorized.setOwnerDeptId(999L);
        when(itemResolver.resolve("product", 501L, 501L))
                .thenReturn(unauthorized);

        assertThatThrownBy(() -> service.getPlanning(request("4"), 301L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("无权规划该调拨物料");

        verify(reservationService, never()).previewForDraft(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    @DisplayName("商品标识与物料标识不一致时在库存读取前被拒绝")
    void shouldRejectMismatchedProductIdentityBeforeStockRead()
    {
        InvTransferDraftPlanningRequest request = request("4");
        request.getDetails().get(0).setProductId(999L);
        when(itemResolver.resolve("product", 501L, 999L))
                .thenReturn(product("0"));

        assertThatThrownBy(() -> service.getPlanning(request, 301L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("商品标识与物料主数据不一致");

        verify(reservationService, never()).previewForDraft(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyList());
    }

    private static InvTransferDraftPlanningRequest request(String quantity)
    {
        InvTransferDraftPlanningRequest request =
                new InvTransferDraftPlanningRequest();
        request.setTransferType("warehouse");
        request.setFromDeptId(201L);
        request.setToDeptId(301L);
        InvTransferDraftPlanningRequest.Line line =
                new InvTransferDraftPlanningRequest.Line();
        line.setItemType("product");
        line.setItemId(501L);
        line.setProductId(501L);
        line.setQuantity(new BigDecimal(quantity));
        request.setDetails(List.of(line));
        return request;
    }

    private static InventoryItemSnapshot product(String status)
    {
        InventoryItemSnapshot item = new InventoryItemSnapshot();
        item.setItemType("product");
        item.setItemId(501L);
        item.setProductId(501L);
        item.setOwnerDeptId(201L);
        item.setItemCode("SPU-COF-001");
        item.setItemName("精品冷萃咖啡液 250ml");
        item.setSpec("250ml");
        item.setUnit("瓶");
        item.setStatus(status);
        return item;
    }

    private static InvTransferReservationService.DraftAvailability
            availability(String requested, String available,
                    String shortage, Long version)
    {
        return new InvTransferReservationService.DraftAvailability(
                "product", 501L, new BigDecimal(requested),
                new BigDecimal(available), new BigDecimal(shortage),
                9001L, version);
    }
}
