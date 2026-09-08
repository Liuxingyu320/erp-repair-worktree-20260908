package com.erp.inventory.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
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
import com.erp.inventory.domain.InvTransferDetail;
import com.erp.inventory.domain.InvTransferDiscrepancy;
import com.erp.inventory.domain.InvTransferDiscrepancyDisposition;
import com.erp.inventory.domain.InvTransferOrder;
import com.erp.inventory.domain.InvTransferShipment;
import com.erp.inventory.domain.InvTransferShipmentDetail;
import com.erp.inventory.domain.vo.InvTransferRevisionDetailVo;
import com.erp.inventory.domain.vo.InvTransferRevisionHeaderVo;
import com.erp.inventory.domain.vo.InvTransferRevisionHistoryVo;
import com.erp.inventory.domain.vo.InvTransferRevisionVo;
import com.erp.inventory.service.IInvTransferService;
import com.erp.inventory.service.impl.InvTransferCostVisibilityPolicy;
import com.erp.inventory.service.impl.InvTransferCommandService;
import com.erp.inventory.service.impl.InvTransferExportService;
import com.erp.system.api.model.LoginUser;

@DisplayName("调拨 Controller 成本响应安全")
class InvTransferControllerCostSecurityTest
{
    @AfterEach
    void tearDown()
    {
        SecurityContextHolder.remove();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    @DisplayName("无成本权限的列表、详情和修订响应在服务端脱敏并禁止缓存")
    void shouldRedactReadsAndDisableCaching()
    {
        IInvTransferService transferService = mock(
                IInvTransferService.class);
        InvTransferOrder listOrder = orderWithCosts();
        InvTransferOrder detailOrder = orderWithCosts();
        InvTransferRevisionHistoryVo history = historyWithCosts();
        when(transferService.selectTransferList(any(InvTransferOrder.class),
                isNull())).thenReturn(List.of(listOrder));
        when(transferService.getTransferDetail(7001L, null))
                .thenReturn(detailOrder);
        when(transferService.getRevisionHistory(7001L, null))
                .thenReturn(history);
        InvTransferController controller = controller(transferService,
                mock(InvTransferExportService.class));
        MockHttpServletRequest request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(request));

        MockHttpServletResponse listResponse =
                new MockHttpServletResponse();
        controller.list(new InvTransferOrder(), request, listResponse);
        assertThat(listOrder.getTotalAmount()).isNull();
        assertNoStore(listResponse);

        MockHttpServletResponse detailResponse =
                new MockHttpServletResponse();
        AjaxResult detailResult = controller.detail(7001L, request,
                detailResponse);
        InvTransferOrder detail = (InvTransferOrder) detailResult.get(
                AjaxResult.DATA_TAG);
        assertThat(detail.getTotalAmount()).isNull();
        assertThat(detail.getDetails().get(0).getCostPrice()).isNull();
        assertThat(detail.getDetails().get(0).getAmount()).isNull();
        assertThat(detail.getShipments().get(0).getDetails().get(0)
                .getCostPrice()).isNull();
        assertNoStore(detailResponse);

        MockHttpServletResponse revisionResponse =
                new MockHttpServletResponse();
        controller.revisions(7001L, request, revisionResponse);
        assertThat(history.getRevisions().get(0).getHeader()
                .getTotalAmount()).isNull();
        assertThat(history.getRevisions().get(0).getDetails().get(0)
                .getCostPrice()).isNull();
        assertThat(history.getRevisions().get(0).getDetails().get(0)
                .getAmount()).isNull();
        assertNoStore(revisionResponse);
    }

    @Test
    @DisplayName("导出列使用权限白名单并排除跨单位总数量")
    void exportsShouldUsePermissionAwareColumnAllowList() throws Exception
    {
        InvTransferExportService exportService = mock(
                InvTransferExportService.class);
        InvTransferOrder ordinaryRow = exportRow();
        when(exportService.selectForExport(any(InvTransferOrder.class),
                isNull())).thenReturn(List.of(ordinaryRow));
        InvTransferController ordinaryController = controller(
                mock(IInvTransferService.class), exportService);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse ordinaryResponse =
                new MockHttpServletResponse();

        ordinaryController.export(ordinaryResponse, new InvTransferOrder(),
                request);

        Set<String> ordinaryText = workbookText(
                ordinaryResponse.getContentAsByteArray());
        assertThat(ordinaryText).contains("明细行数");
        assertThat(ordinaryText).doesNotContain("参考总价", "总数量");
        assertThat(ordinaryRow.getTotalAmount()).isNull();
        assertNoStore(ordinaryResponse);

        setCostViewer();
        InvTransferOrder authorizedRow = exportRow();
        when(exportService.selectForExport(any(InvTransferOrder.class),
                isNull())).thenReturn(List.of(authorizedRow));
        InvTransferController authorizedController = controller(
                mock(IInvTransferService.class), exportService);
        MockHttpServletResponse authorizedResponse =
                new MockHttpServletResponse();

        authorizedController.export(authorizedResponse,
                new InvTransferOrder(), request);

        Set<String> authorizedText = workbookText(
                authorizedResponse.getContentAsByteArray());
        assertThat(authorizedText).contains("明细行数", "参考总价");
        assertThat(authorizedText).doesNotContain("总数量");
        assertThat(authorizedRow.getTotalAmount())
                .isEqualByComparingTo("88.00");
        assertNoStore(authorizedResponse);
    }

    @Test
    @DisplayName("无成本权限的保存和提交响应必须在 Controller 边界脱敏")
    void writesShouldRedactReturnedCostsWithoutPermission()
    {
        InvTransferCommandService commandService = mock(
                InvTransferCommandService.class);
        InvTransferOrder saveResult = orderWithCosts();
        InvTransferOrder submitResult = orderWithCosts();
        when(commandService.saveDraft(
                org.mockito.ArgumentMatchers.eq("save-1"),
                any(InvTransferOrder.class),
                org.mockito.ArgumentMatchers.anyList(), isNull()))
                .thenReturn(saveResult);
        when(commandService.submit(
                org.mockito.ArgumentMatchers.eq("submit-1"),
                any(InvTransferOrder.class),
                org.mockito.ArgumentMatchers.anyList(), isNull()))
                .thenReturn(submitResult);
        InvTransferController controller = controller(
                mock(IInvTransferService.class),
                mock(InvTransferExportService.class));
        ReflectionTestUtils.setField(controller, "transferCommandService",
                commandService);
        InvTransferOrder requestOrder = orderWithCosts();

        AjaxResult saved = controller.save(requestOrder, "save-1",
                new MockHttpServletRequest());
        AjaxResult submitted = controller.submit(requestOrder, "submit-1",
                new MockHttpServletRequest());

        assertOrderRedacted((InvTransferOrder) saved.get(AjaxResult.DATA_TAG));
        assertOrderRedacted((InvTransferOrder) submitted.get(
                AjaxResult.DATA_TAG));
    }

    @Test
    @DisplayName("无成本权限的差异列表和详情必须脱敏处置台账")
    void discrepancyReadsShouldRedactLedgerCostsWithoutPermission()
    {
        IInvTransferService transferService = mock(IInvTransferService.class);
        InvTransferDiscrepancy listItem = discrepancyWithCosts();
        InvTransferDiscrepancy detail = discrepancyWithCosts();
        when(transferService.getTransferDiscrepancies(7001L, null))
                .thenReturn(List.of(listItem));
        when(transferService.getTransferDiscrepancy(8001L, null))
                .thenReturn(detail);
        InvTransferController controller = controller(transferService,
                mock(InvTransferExportService.class));
        MockHttpServletRequest request = new MockHttpServletRequest();

        controller.discrepancies(7001L, request,
                new MockHttpServletResponse());
        controller.discrepancy(8001L, request,
                new MockHttpServletResponse());

        assertThat(listItem.getDispositions().get(0).getCostPrice()).isNull();
        assertThat(listItem.getDispositions().get(0).getAmount()).isNull();
        assertThat(detail.getDispositions().get(0).getCostPrice()).isNull();
        assertThat(detail.getDispositions().get(0).getAmount()).isNull();
    }

    private InvTransferController controller(
            IInvTransferService transferService,
            InvTransferExportService exportService)
    {
        InvTransferController controller = new InvTransferController();
        ReflectionTestUtils.setField(controller, "transferService",
                transferService);
        ReflectionTestUtils.setField(controller, "transferExportService",
                exportService);
        ReflectionTestUtils.setField(controller, "costVisibilityPolicy",
                new InvTransferCostVisibilityPolicy());
        return controller;
    }

    private InvTransferOrder exportRow()
    {
        InvTransferOrder order = new InvTransferOrder();
        order.setOrderNo("TF-7001");
        order.setFromDeptName("一号仓");
        order.setToDeptName("二号店");
        order.setStatus("submitted");
        order.setTransferType("warehouse");
        order.setDetailLineCount(3L);
        order.setTotalQuantity(new BigDecimal("11.00"));
        order.setTotalAmount(new BigDecimal("88.00"));
        return order;
    }

    private InvTransferOrder orderWithCosts()
    {
        InvTransferDetail detail = new InvTransferDetail();
        detail.setCostPrice(new BigDecimal("8.00"));
        detail.setAmount(new BigDecimal("88.00"));
        InvTransferShipmentDetail shipmentDetail =
                new InvTransferShipmentDetail();
        shipmentDetail.setCostPrice(new BigDecimal("8.00"));
        InvTransferShipment shipment = new InvTransferShipment();
        shipment.setDetails(List.of(shipmentDetail));
        InvTransferOrder order = exportRow();
        order.setDetails(List.of(detail));
        order.setShipments(List.of(shipment));
        return order;
    }

    private InvTransferDiscrepancy discrepancyWithCosts()
    {
        InvTransferDiscrepancyDisposition disposition =
                new InvTransferDiscrepancyDisposition();
        disposition.setCostPrice(new BigDecimal("8.00"));
        disposition.setAmount(new BigDecimal("88.00"));
        InvTransferDiscrepancy discrepancy = new InvTransferDiscrepancy();
        discrepancy.setDispositions(List.of(disposition));
        return discrepancy;
    }

    private void assertOrderRedacted(InvTransferOrder order)
    {
        assertThat(order.getTotalAmount()).isNull();
        assertThat(order.getDetails().get(0).getCostPrice()).isNull();
        assertThat(order.getDetails().get(0).getAmount()).isNull();
    }

    private InvTransferRevisionHistoryVo historyWithCosts()
    {
        InvTransferRevisionHeaderVo header =
                new InvTransferRevisionHeaderVo();
        header.setTotalAmount("88.00");
        InvTransferRevisionDetailVo detail =
                new InvTransferRevisionDetailVo();
        detail.setCostPrice("8.00");
        detail.setAmount("88.00");
        InvTransferRevisionVo revision = new InvTransferRevisionVo();
        revision.setHeader(header);
        revision.setDetails(List.of(detail));
        InvTransferRevisionHistoryVo history =
                new InvTransferRevisionHistoryVo();
        history.setRevisions(List.of(revision));
        return history;
    }

    private Set<String> workbookText(byte[] bytes) throws Exception
    {
        Set<String> values = new LinkedHashSet<>();
        try (Workbook workbook = WorkbookFactory.create(
                new ByteArrayInputStream(bytes)))
        {
            for (Sheet sheet : workbook)
            {
                for (Row row : sheet)
                {
                    for (Cell cell : row)
                    {
                        values.add(cell.toString());
                    }
                }
            }
        }
        return values;
    }

    private void setCostViewer()
    {
        LoginUser loginUser = new LoginUser();
        loginUser.setUserid(42L);
        loginUser.setUsername("transfer-cost-viewer");
        loginUser.setPermissions(Set.of(
                InvTransferCostVisibilityPolicy.COST_VIEW_PERMISSION));
        SecurityContextHolder.setUserId("42");
        SecurityContextHolder.set(SecurityConstants.LOGIN_USER, loginUser);
    }

    private void assertNoStore(MockHttpServletResponse response)
    {
        assertThat(response.getHeader("Cache-Control"))
                .isEqualTo("no-store, max-age=0");
        assertThat(response.getHeader("Pragma")).isEqualTo("no-cache");
    }
}
