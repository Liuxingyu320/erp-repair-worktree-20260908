package com.erp.inventory.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.erp.common.core.exception.ServiceException;
import com.erp.inventory.domain.InvTransferOrder;
import com.erp.inventory.domain.vo.InventoryItemSnapshot;
import com.erp.inventory.mapper.InvDeptScopeMapper;

@DisplayName("补货来源仓库物料范围策略")
class InvTransferSourceItemScopePolicyTest
{
    private final InvDeptScopeMapper deptScopeMapper =
            mock(InvDeptScopeMapper.class);
    private final InvTransferSourceItemScopePolicy policy =
            new InvTransferSourceItemScopePolicy(deptScopeMapper);

    @Test
    @DisplayName("物料范围以实际来源仓库而不是目标门店为锚点")
    void shouldResolveOwnerScopeFromActualSourceWarehouse()
    {
        allowSameBusinessRoot();
        when(deptScopeMapper.selectActiveRelatedDeptIdsForReplenishment(
                201L)).thenReturn(
                        Arrays.asList(null, -1L, 100L, 201L, 100L));

        Set<Long> result = policy.resolveOwnerScopeDeptIds(
                replenishmentOrder());

        assertThat(result).containsExactlyInAnyOrder(100L, 201L);
        verify(deptScopeMapper)
                .selectActiveRelatedDeptIdsForReplenishment(201L);
    }

    @Test
    @DisplayName("来源范围未包含来源仓库时失败关闭")
    void shouldFailClosedWhenSourceWarehouseIsMissingFromScope()
    {
        allowSameBusinessRoot();
        when(deptScopeMapper.selectActiveRelatedDeptIdsForReplenishment(
                201L))
                .thenReturn(List.of(100L));

        assertThatThrownBy(() -> policy.resolveOwnerScopeDeptIds(
                replenishmentOrder()))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("来源仓库物料范围无效");
    }

    @Test
    @DisplayName("停用或删除的归属组织不能从通用关系范围回流")
    void shouldUseOnlyActiveReplenishmentOwnerScope()
    {
        allowSameBusinessRoot();
        when(deptScopeMapper.selectRelatedDeptIds(201L))
                .thenReturn(List.of(100L, 201L, 205L, 206L));
        when(deptScopeMapper.selectActiveRelatedDeptIdsForReplenishment(
                201L)).thenReturn(List.of(100L, 201L));

        Set<Long> ownerScope = policy.resolveOwnerScopeDeptIds(
                replenishmentOrder());
        InventoryItemSnapshot disabledOwnerItem = new InventoryItemSnapshot();
        disabledOwnerItem.setItemType("product");
        disabledOwnerItem.setOwnerDeptId(205L);
        InventoryItemSnapshot deletedOwnerItem = new InventoryItemSnapshot();
        deletedOwnerItem.setItemType("product");
        deletedOwnerItem.setOwnerDeptId(206L);

        assertThat(ownerScope).doesNotContain(205L, 206L);
        assertThatThrownBy(() -> policy.assertEligible("warehouse",
                disabledOwnerItem, ownerScope, "无权调拨该物料"))
                .isInstanceOf(ServiceException.class);
        assertThatThrownBy(() -> policy.assertEligible("warehouse",
                deletedOwnerItem, ownerScope, "无权调拨该物料"))
                .isInstanceOf(ServiceException.class);
    }

    @Test
    @DisplayName("门店要货只允许选择同一原始业务根的来源仓库")
    void shouldRejectWarehouseOutsideTargetStoreBusinessRoot()
    {
        when(deptScopeMapper.selectRawBusinessRootDeptId(201L))
                .thenReturn(100L);
        when(deptScopeMapper.selectRawBusinessRootDeptId(301L))
                .thenReturn(200L);
        when(deptScopeMapper.selectDeptTypeById(100L))
                .thenReturn("GROUP");
        when(deptScopeMapper.selectDeptTypeById(200L))
                .thenReturn("GROUP");

        assertThatThrownBy(() -> policy.resolveOwnerScopeDeptIds(
                replenishmentOrder()))
                .isInstanceOf(ServiceException.class)
                .hasMessage("当前门店不允许向该仓库要货");
    }

    @Test
    @DisplayName("原始业务根缺失或停用时补货失败关闭")
    void shouldRejectMissingOrDisabledRawBusinessRoot()
    {
        when(deptScopeMapper.selectRawBusinessRootDeptId(201L))
                .thenReturn(999L);
        when(deptScopeMapper.selectDeptTypeById(999L)).thenReturn(null);

        assertThatThrownBy(() -> policy.resolveOwnerScopeDeptIds(
                replenishmentOrder()))
                .isInstanceOf(ServiceException.class)
                .hasMessage("补货供货关系无效");
    }

    @Test
    @DisplayName("仓库补货商品必须有来源归属，礼盒和返仓保持独立")
    void shouldApplyOwnerScopeOnlyToWarehouseProducts()
    {
        InventoryItemSnapshot item = new InventoryItemSnapshot();
        item.setItemType("product");
        item.setOwnerDeptId(999L);

        assertThatThrownBy(() -> policy.assertEligible("warehouse", item,
                Set.of(201L), "无权调拨该物料"))
                .isInstanceOf(ServiceException.class)
                .hasMessage("无权调拨该物料");
        item.setOwnerDeptId(null);
        assertThatThrownBy(() -> policy.assertEligible("warehouse", item,
                Set.of(201L), "无权调拨该物料"))
                .isInstanceOf(ServiceException.class);

        assertThatCode(() -> policy.assertEligible("store_return", item,
                Set.of(), "无权调拨该物料"))
                .doesNotThrowAnyException();
        item.setItemType("gift");
        assertThatCode(() -> policy.assertEligible("warehouse", item,
                Set.of(), "无权调拨该物料"))
                .doesNotThrowAnyException();
    }

    private void allowSameBusinessRoot()
    {
        when(deptScopeMapper.selectRawBusinessRootDeptId(201L))
                .thenReturn(100L);
        when(deptScopeMapper.selectRawBusinessRootDeptId(301L))
                .thenReturn(100L);
        when(deptScopeMapper.selectDeptTypeById(100L))
                .thenReturn("GROUP");
    }

    private static InvTransferOrder replenishmentOrder()
    {
        InvTransferOrder order = new InvTransferOrder();
        order.setTransferType("warehouse");
        order.setFromDeptId(999L);
        order.setFromWarehouseId(201L);
        order.setToDeptId(301L);
        order.setToWarehouseId(301L);
        return order;
    }
}
