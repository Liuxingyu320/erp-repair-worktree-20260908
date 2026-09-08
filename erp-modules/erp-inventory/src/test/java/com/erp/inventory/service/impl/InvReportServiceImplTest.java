package com.erp.inventory.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.security.shop.ShopScopeService;
import com.erp.inventory.domain.InvStock;
import com.erp.inventory.domain.vo.InvReportProductOption;
import com.erp.inventory.mapper.InvReportMapper;

@DisplayName("库存报表服务边界")
class InvReportServiceImplTest
{
    @Test
    @DisplayName("商品候选使用当前精确组织并规范化关键词和上限")
    void shouldScopeAndNormalizeProductOptions()
    {
        InvReportMapper mapper = mock(InvReportMapper.class);
        ShopScopeService shopScopeService = mock(ShopScopeService.class);
        when(shopScopeService.resolveRequiredShopDept(301L)).thenReturn(301L);
        when(mapper.selectReportProductOptions(any(InvStock.class)))
                .thenReturn(List.of(new InvReportProductOption()));
        InvReportServiceImpl service = service(mapper, shopScopeService);

        service.selectProductOptions("  龙井  ", 20, 301L);

        ArgumentCaptor<InvStock> captor = ArgumentCaptor.forClass(InvStock.class);
        verify(mapper).selectReportProductOptions(captor.capture());
        InvStock query = captor.getValue();
        assertThat(query.getShopDeptId()).isEqualTo(301L);
        assertThat(query.getWarehouseId()).isEqualTo(301L);
        assertThat(query.getParams()).containsEntry("keyword", "龙井");
        assertThat(query.getParams()).containsEntry("optionLimit", 20);
    }

    @Test
    @DisplayName("报表拒绝缺失组织、反向日期和越界候选参数")
    void shouldRejectUnsafeReportQueries()
    {
        InvReportServiceImpl service = service(mock(InvReportMapper.class),
                mock(ShopScopeService.class));
        InvStock reverseDates = new InvStock();
        reverseDates.getParams().put("beginTime", "2026-08-10");
        reverseDates.getParams().put("endTime", "2026-08-01");
        InvStock invalidStatus = new InvStock();
        invalidStatus.setStockStatus("normal");

        assertThatThrownBy(() -> service.selectReportSummary(new InvStock(), null))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("选择店铺或仓库");
        assertThatThrownBy(() -> service.selectReportSummary(reverseDates, 301L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("开始日期不能晚于结束日期");
        assertThatThrownBy(() -> service.selectStockWarningList(invalidStatus, 301L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("预警状态无效");
        assertThatThrownBy(() -> service.selectProductOptions("茶".repeat(101), 20, 301L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("长度不能超过100个字符");
        assertThatThrownBy(() -> service.selectProductOptions("龙井", 51, 301L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("不能超过50");
    }

    private static InvReportServiceImpl service(InvReportMapper mapper,
            ShopScopeService shopScopeService)
    {
        InvReportServiceImpl service = new InvReportServiceImpl();
        ReflectionTestUtils.setField(service, "reportMapper", mapper);
        ReflectionTestUtils.setField(service, "shopScopeService", shopScopeService);
        return service;
    }
}
