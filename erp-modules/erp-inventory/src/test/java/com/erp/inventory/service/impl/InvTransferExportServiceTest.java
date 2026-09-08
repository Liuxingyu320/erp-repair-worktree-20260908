package com.erp.inventory.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.security.shop.ShopScopeService;
import com.erp.inventory.domain.InvTransferOrder;
import com.erp.inventory.mapper.InvDeptScopeMapper;
import com.erp.inventory.mapper.InvTransferOrderMapper;

@DisplayName("调拨同步导出查询")
class InvTransferExportServiceTest
{
    @Test
    @DisplayName("导出带入当前组织范围并只探测 10001 行")
    void shouldApplyOrganizationScopeAndBoundedProbe()
    {
        InvTransferOrderMapper orderMapper = mock(
                InvTransferOrderMapper.class);
        InvDeptScopeMapper scopeMapper = mock(InvDeptScopeMapper.class);
        ShopScopeService shopScopeService = mock(ShopScopeService.class);
        when(shopScopeService.resolveRequiredShopDept(301L)).thenReturn(301L);
        when(scopeMapper.selectSubDeptIds(301L)).thenReturn(
                List.of(301L, 302L));
        when(orderMapper.selectInvTransferOrderList(
                org.mockito.ArgumentMatchers.any(InvTransferOrder.class)))
                .thenReturn(List.of(new InvTransferOrder()));
        InvTransferExportService service = service(orderMapper, scopeMapper,
                shopScopeService);

        InvTransferOrder query = new InvTransferOrder();
        query.getParams().put("dataScope", "or 1 = 1");

        List<InvTransferOrder> rows = service.selectForExport(query, 301L);

        assertThat(rows).hasSize(1);
        ArgumentCaptor<InvTransferOrder> captor = ArgumentCaptor.forClass(
                InvTransferOrder.class);
        verify(orderMapper).selectInvTransferOrderList(captor.capture());
        assertThat(captor.getValue().getParams().get("scopeDeptIds"))
                .isEqualTo(List.of(301L, 302L));
        assertThat(captor.getValue().getParams().get("rowLimit"))
                .isEqualTo(10_001);
        assertThat(captor.getValue().getParams())
                .doesNotContainKey("dataScope");
        verify(shopScopeService).resolveRequiredShopDept(eq(301L));
        verify(scopeMapper).selectSubDeptIds(eq(301L));
    }

    @Test
    @DisplayName("空组织范围失败关闭而不扩大为全量导出")
    void shouldFailClosedForEmptyResolvedScope()
    {
        InvTransferOrderMapper orderMapper = mock(
                InvTransferOrderMapper.class);
        InvDeptScopeMapper scopeMapper = mock(InvDeptScopeMapper.class);
        ShopScopeService shopScopeService = mock(ShopScopeService.class);
        when(shopScopeService.resolveRequiredShopDept(301L)).thenReturn(301L);
        when(scopeMapper.selectSubDeptIds(301L)).thenReturn(List.of());
        when(orderMapper.selectInvTransferOrderList(
                org.mockito.ArgumentMatchers.any(InvTransferOrder.class)))
                .thenAnswer(invocation -> {
                    InvTransferOrder query = invocation.getArgument(0);
                    assertThat(query.getParams().get("scopeDeptIds"))
                            .isEqualTo(List.of(-1L));
                    return List.of();
                });
        InvTransferExportService service = service(orderMapper, scopeMapper,
                shopScopeService);

        assertThat(service.selectForExport(new InvTransferOrder(), 301L))
                .isEmpty();
    }

    @Test
    @DisplayName("超过 10000 行时拒绝生成同步文件")
    void shouldRejectRowsBeyondSynchronousLimit()
    {
        InvTransferOrderMapper orderMapper = mock(
                InvTransferOrderMapper.class);
        InvDeptScopeMapper scopeMapper = mock(InvDeptScopeMapper.class);
        ShopScopeService shopScopeService = mock(ShopScopeService.class);
        when(shopScopeService.resolveRequiredShopDept(301L)).thenReturn(301L);
        when(scopeMapper.selectSubDeptIds(301L)).thenReturn(List.of(301L));
        List<InvTransferOrder> rows = new ArrayList<>(
                InvTransferExportService.EXPORT_PROBE_ROWS);
        for (int index = 0;
                index < InvTransferExportService.EXPORT_PROBE_ROWS; index++)
        {
            rows.add(new InvTransferOrder());
        }
        when(orderMapper.selectInvTransferOrderList(
                org.mockito.ArgumentMatchers.any(InvTransferOrder.class)))
                .thenReturn(rows);
        InvTransferExportService service = service(orderMapper, scopeMapper,
                shopScopeService);

        assertThatThrownBy(() -> service.selectForExport(
                new InvTransferOrder(), 301L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("10000");
    }

    private InvTransferExportService service(
            InvTransferOrderMapper orderMapper,
            InvDeptScopeMapper scopeMapper,
            ShopScopeService shopScopeService)
    {
        InvTransferExportService service = new InvTransferExportService();
        ReflectionTestUtils.setField(service, "transferOrderMapper",
                orderMapper);
        ReflectionTestUtils.setField(service, "deptScopeMapper", scopeMapper);
        ReflectionTestUtils.setField(service, "shopScopeService",
                shopScopeService);
        return service;
    }
}
