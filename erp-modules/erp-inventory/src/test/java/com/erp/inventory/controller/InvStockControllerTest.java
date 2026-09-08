package com.erp.inventory.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import com.erp.common.core.constant.SecurityConstants;
import com.erp.common.core.context.SecurityContextHolder;
import com.erp.common.core.web.domain.AjaxResult;
import com.erp.inventory.domain.InvStock;
import com.erp.inventory.domain.InvStockLog;
import com.erp.inventory.domain.dto.InvExistingStockAdjustRequest;
import com.erp.inventory.domain.dto.InvStockAdjustRequest;
import com.erp.inventory.domain.vo.InvStockSummary;
import com.erp.inventory.service.IInvStockService;
import com.erp.system.api.model.LoginUser;

@DisplayName("库存 Controller 安全响应")
class InvStockControllerTest
{
    @AfterEach
    void tearDown()
    {
        SecurityContextHolder.remove();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    @DisplayName("无成本权限时列表详情汇总和流水均脱敏并禁止缓存")
    void shouldRedactAllCostSurfacesAndDisableCaching()
    {
        IInvStockService service = mock(IInvStockService.class);
        InvStock listStock = stockWithCost();
        InvStock detailStock = stockWithCost();
        InvStockSummary summary = new InvStockSummary();
        summary.setTotalCost(new BigDecimal("88.00"));
        InvStockLog log = new InvStockLog();
        log.setCostPrice(new BigDecimal("8.00"));
        when(service.selectStockList(any(InvStock.class), isNull()))
                .thenReturn(Collections.singletonList(listStock));
        when(service.selectStockById(eq(1001L), isNull())).thenReturn(detailStock);
        when(service.selectStockSummary(any(InvStock.class), isNull())).thenReturn(summary);
        when(service.selectStockLogList(any(InvStockLog.class), isNull()))
                .thenReturn(Collections.singletonList(log));
        InvStockController controller = controller(service);
        MockHttpServletRequest request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        MockHttpServletResponse listResponse = new MockHttpServletResponse();
        controller.list(new InvStock(), request, listResponse);
        assertThat(listStock.getCostPrice()).isNull();
        assertThat(listStock.getTotalCost()).isNull();
        assertNoStore(listResponse);

        MockHttpServletResponse detailResponse = new MockHttpServletResponse();
        AjaxResult detailResult = controller.getInfo(1001L, request, detailResponse);
        InvStock detail = (InvStock) detailResult.get(AjaxResult.DATA_TAG);
        assertThat(detail.getCostPrice()).isNull();
        assertThat(detail.getTotalCost()).isNull();
        assertNoStore(detailResponse);

        MockHttpServletResponse summaryResponse = new MockHttpServletResponse();
        controller.summary(new InvStock(), request, summaryResponse);
        assertThat(summary.getTotalCost()).isNull();
        assertNoStore(summaryResponse);

        MockHttpServletResponse logResponse = new MockHttpServletResponse();
        controller.logList(new InvStockLog(), request, logResponse);
        assertThat(log.getCostPrice()).isNull();
        assertNoStore(logResponse);
    }

    @Test
    @DisplayName("具有成本权限时详情保留成本字段")
    void shouldRetainCostsForAuthorizedViewer()
    {
        setCostViewer();
        IInvStockService service = mock(IInvStockService.class);
        when(service.selectStockById(eq(1001L), isNull())).thenReturn(stockWithCost());
        InvStockLog log = new InvStockLog();
        log.setCostPrice(new BigDecimal("8.00"));
        when(service.selectStockLogList(any(InvStockLog.class), isNull()))
                .thenReturn(Collections.singletonList(log));
        InvStockController controller = controller(service);
        MockHttpServletRequest request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        AjaxResult result = controller.getInfo(1001L, request,
                new MockHttpServletResponse());
        controller.logList(new InvStockLog(), request,
                new MockHttpServletResponse());

        InvStock stock = (InvStock) result.get(AjaxResult.DATA_TAG);
        assertThat(stock.getCostPrice()).isEqualByComparingTo("8.00");
        assertThat(stock.getTotalCost()).isEqualByComparingTo("88.00");
        assertThat(log.getCostPrice()).isEqualByComparingTo("8.00");
    }

    @Test
    @DisplayName("新旧调整响应均禁止缓存且新接口响应执行成本脱敏")
    void mutationResponsesShouldDisableCachingAndRedactResult()
    {
        IInvStockService service = mock(IInvStockService.class);
        when(service.adjustExistingStock(eq(1001L), any(InvExistingStockAdjustRequest.class), isNull()))
                .thenReturn(stockWithCost());
        InvStockController controller = controller(service);
        MockHttpServletRequest request = new MockHttpServletRequest();

        InvExistingStockAdjustRequest strictRequest = new InvExistingStockAdjustRequest();
        strictRequest.setExpectedVersion(1L);
        strictRequest.setAdjustQuantity(BigDecimal.ONE);
        strictRequest.setReason("复核盘盈");
        MockHttpServletResponse strictResponse = new MockHttpServletResponse();
        AjaxResult strictResult = controller.adjustExisting(1001L, strictRequest, request,
                strictResponse);
        assertThat(((InvStock) strictResult.get(AjaxResult.DATA_TAG)).getCostPrice()).isNull();
        assertNoStore(strictResponse);

        InvStockAdjustRequest legacyRequest = new InvStockAdjustRequest();
        legacyRequest.setAdjustQuantity(BigDecimal.ONE);
        MockHttpServletResponse legacyResponse = new MockHttpServletResponse();
        controller.adjust(legacyRequest, request, legacyResponse);
        assertNoStore(legacyResponse);
    }

    @Test
    @DisplayName("无成本权限时库存与流水导出均脱敏并禁止缓存")
    void exportsShouldRedactCostsAndDisableCaching()
    {
        IInvStockService service = mock(IInvStockService.class);
        InvStock stock = stockWithCost();
        InvStockLog log = new InvStockLog();
        log.setCostPrice(new BigDecimal("8.00"));
        when(service.selectStockList(any(InvStock.class), isNull()))
                .thenReturn(Collections.singletonList(stock));
        when(service.selectStockLogList(any(InvStockLog.class), isNull()))
                .thenReturn(Collections.singletonList(log));
        InvStockController controller = controller(service);
        MockHttpServletRequest request = new MockHttpServletRequest();

        MockHttpServletResponse stockResponse = new MockHttpServletResponse();
        controller.export(stockResponse, new InvStock(), request);
        assertThat(stock.getCostPrice()).isNull();
        assertThat(stock.getTotalCost()).isNull();
        assertNoStore(stockResponse);

        MockHttpServletResponse logResponse = new MockHttpServletResponse();
        controller.exportLog(logResponse, new InvStockLog(), request);
        assertThat(log.getCostPrice()).isNull();
        assertNoStore(logResponse);
    }

    private InvStockController controller(IInvStockService service)
    {
        InvStockController controller = new InvStockController();
        ReflectionTestUtils.setField(controller, "stockService", service);
        return controller;
    }

    private InvStock stockWithCost()
    {
        InvStock stock = new InvStock();
        stock.setStockId(1001L);
        stock.setCostPrice(new BigDecimal("8.00"));
        stock.setTotalCost(new BigDecimal("88.00"));
        return stock;
    }

    private void setCostViewer()
    {
        LoginUser loginUser = new LoginUser();
        loginUser.setUserid(42L);
        loginUser.setUsername("stock-cost-viewer");
        loginUser.setPermissions(Set.of("inv:cost:view"));
        SecurityContextHolder.setUserId("42");
        SecurityContextHolder.set(SecurityConstants.LOGIN_USER, loginUser);
    }

    private void assertNoStore(MockHttpServletResponse response)
    {
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store, max-age=0");
        assertThat(response.getHeader("Pragma")).isEqualTo("no-cache");
    }
}
