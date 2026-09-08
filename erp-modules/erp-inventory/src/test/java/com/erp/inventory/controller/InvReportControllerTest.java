package com.erp.inventory.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
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
import com.erp.inventory.domain.vo.InvReportProductOption;
import com.erp.inventory.domain.vo.InvReportSummary;
import com.erp.inventory.service.IInvReportService;
import com.erp.system.api.model.LoginUser;

@DisplayName("库存报表 Controller 安全响应")
class InvReportControllerTest
{
    @AfterEach
    void tearDown()
    {
        SecurityContextHolder.remove();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    @DisplayName("无成本权限时汇总保留安全指标并清空所有成本值")
    void shouldRedactCostMetricsWithoutPermission()
    {
        IInvReportService service = mock(IInvReportService.class);
        InvReportSummary summary = summaryWithCosts();
        when(service.selectReportSummary(any(InvStock.class), isNull())).thenReturn(summary);
        InvReportController controller = controller(service);
        MockHttpServletResponse response = new MockHttpServletResponse();

        AjaxResult result = controller.summary(new InvStock(), requestContext(), response);
        InvReportSummary returned = (InvReportSummary) result.get(AjaxResult.DATA_TAG);

        assertThat(returned.getStockItemCount()).isEqualTo(8L);
        assertThat(returned.getSalesAmount()).isEqualByComparingTo("5200.40");
        assertThat(returned.getTotalStockCost()).isNull();
        assertThat(returned.getPurchaseAmount()).isNull();
        assertThat(returned.getSalesCost()).isNull();
        assertThat(returned.getGrossMargin()).isNull();
        assertNoStore(response);
    }

    @Test
    @DisplayName("具有成本权限时汇总保留成本值")
    void shouldRetainCostMetricsForAuthorizedViewer()
    {
        setCostViewer();
        IInvReportService service = mock(IInvReportService.class);
        when(service.selectReportSummary(any(InvStock.class), isNull()))
                .thenReturn(summaryWithCosts());
        InvReportController controller = controller(service);

        InvReportSummary returned = (InvReportSummary) controller.summary(
                new InvStock(), requestContext(), new MockHttpServletResponse())
                .get(AjaxResult.DATA_TAG);

        assertThat(returned.getTotalStockCost()).isEqualByComparingTo("8300.50");
        assertThat(returned.getPurchaseAmount()).isEqualByComparingTo("3200.25");
        assertThat(returned.getSalesCost()).isEqualByComparingTo("2700.15");
        assertThat(returned.getGrossMargin()).isEqualByComparingTo("2500.25");
    }

    @Test
    @DisplayName("预警商品候选列表和导出均禁止缓存")
    void allReportSurfacesShouldDisableCaching()
    {
        IInvReportService service = mock(IInvReportService.class);
        when(service.selectStockWarningList(any(InvStock.class), isNull()))
                .thenReturn(Collections.emptyList());
        when(service.selectProductOptions(eq("龙井"), eq(20), isNull()))
                .thenReturn(List.of(new InvReportProductOption()));
        InvReportController controller = controller(service);
        MockHttpServletRequest request = requestContext();

        MockHttpServletResponse warningResponse = new MockHttpServletResponse();
        controller.stockWarning(new InvStock(), request, warningResponse);
        assertNoStore(warningResponse);

        MockHttpServletResponse optionResponse = new MockHttpServletResponse();
        controller.productOptions("龙井", 20, request, optionResponse);
        assertNoStore(optionResponse);

        MockHttpServletResponse exportResponse = new MockHttpServletResponse();
        controller.exportStockWarning(exportResponse, new InvStock(), request);
        assertNoStore(exportResponse);
    }

    private static InvReportController controller(IInvReportService service)
    {
        InvReportController controller = new InvReportController();
        ReflectionTestUtils.setField(controller, "reportService", service);
        return controller;
    }

    private static MockHttpServletRequest requestContext()
    {
        MockHttpServletRequest request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        return request;
    }

    private static InvReportSummary summaryWithCosts()
    {
        InvReportSummary summary = new InvReportSummary();
        summary.setStockItemCount(8L);
        summary.setSalesAmount(new BigDecimal("5200.40"));
        summary.setTotalStockCost(new BigDecimal("8300.50"));
        summary.setPurchaseAmount(new BigDecimal("3200.25"));
        summary.setSalesCost(new BigDecimal("2700.15"));
        summary.setGrossMargin(new BigDecimal("2500.25"));
        return summary;
    }

    private static void setCostViewer()
    {
        LoginUser loginUser = new LoginUser();
        loginUser.setUserid(42L);
        loginUser.setUsername("report-cost-viewer");
        loginUser.setPermissions(Set.of("inv:cost:view"));
        SecurityContextHolder.setUserId("42");
        SecurityContextHolder.set(SecurityConstants.LOGIN_USER, loginUser);
    }

    private static void assertNoStore(MockHttpServletResponse response)
    {
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store, max-age=0");
        assertThat(response.getHeader("Pragma")).isEqualTo("no-cache");
    }
}
