package com.erp.inventory.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
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
import com.erp.inventory.domain.InvStockCheck;
import com.erp.inventory.domain.InvStockCheckApprovalInstance;
import com.erp.inventory.domain.InvStockCheckDetail;
import com.erp.inventory.domain.dto.InvStockCheckApprovalRequest;
import com.erp.inventory.domain.dto.InvStockCheckAssignmentRequest;
import com.erp.inventory.service.IInvStockCheckApprovalService;
import com.erp.inventory.service.IInvStockCheckService;
import com.erp.system.api.model.LoginUser;

@DisplayName("库存盘点 Controller 安全响应")
class InvStockCheckControllerTest
{
    @AfterEach
    void tearDown()
    {
        SecurityContextHolder.remove();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    @DisplayName("无成本权限时详情与历史快照脱敏并禁止缓存")
    @SuppressWarnings("unchecked")
    void shouldRedactDetailAndApprovalSnapshotsWithoutCostPermission()
    {
        IInvStockCheckService service = mock(IInvStockCheckService.class);
        IInvStockCheckApprovalService approvalService =
                mock(IInvStockCheckApprovalService.class);
        InvStockCheck check = checkWithCost();
        InvStockCheckApprovalInstance instance = approvalInstance();
        when(service.getCheckDetail(1001L, null)).thenReturn(check);
        when(approvalService.selectTrack(1001L, null))
                .thenReturn(List.of(instance));
        InvStockCheckController controller = controller(service, approvalService);
        MockHttpServletRequest request = requestContext();

        MockHttpServletResponse detailResponse = new MockHttpServletResponse();
        AjaxResult detailResult = controller.detail(1001L, request, detailResponse);
        InvStockCheck detail = (InvStockCheck) detailResult.get(AjaxResult.DATA_TAG);
        assertThat(detail.getDetails().get(0).getCostPrice()).isNull();
        assertNoStore(detailResponse);

        MockHttpServletResponse trackResponse = new MockHttpServletResponse();
        AjaxResult trackResult = controller.approvalTrack(1001L, request, trackResponse);
        List<InvStockCheckApprovalInstance> track =
                (List<InvStockCheckApprovalInstance>) trackResult.get(AjaxResult.DATA_TAG);
        assertThat(track.get(0).getDetailSnapshot()).isNull();
        assertNoStore(trackResponse);
    }

    @Test
    @DisplayName("具有成本权限时保留盘点明细成本和历史快照")
    @SuppressWarnings("unchecked")
    void shouldRetainCostsForAuthorizedViewer()
    {
        setCostViewer();
        IInvStockCheckService service = mock(IInvStockCheckService.class);
        IInvStockCheckApprovalService approvalService =
                mock(IInvStockCheckApprovalService.class);
        when(service.getCheckDetail(1001L, null)).thenReturn(checkWithCost());
        when(approvalService.selectTrack(1001L, null))
                .thenReturn(List.of(approvalInstance()));
        InvStockCheckController controller = controller(service, approvalService);
        MockHttpServletRequest request = requestContext();

        InvStockCheck detail = (InvStockCheck) controller.detail(1001L, request,
                new MockHttpServletResponse()).get(AjaxResult.DATA_TAG);
        List<InvStockCheckApprovalInstance> track =
                (List<InvStockCheckApprovalInstance>) controller.approvalTrack(
                        1001L, request, new MockHttpServletResponse())
                        .get(AjaxResult.DATA_TAG);

        assertThat(detail.getDetails().get(0).getCostPrice())
                .isEqualByComparingTo("8.00");
        assertThat(track.get(0).getDetailSnapshot()).contains("costPrice");
    }

    @Test
    @DisplayName("盘点全部 JSON 读写端点和导出都禁止缓存")
    void allResponsesShouldDisableCaching()
    {
        IInvStockCheckService service = mock(IInvStockCheckService.class);
        IInvStockCheckApprovalService approvalService =
                mock(IInvStockCheckApprovalService.class);
        InvStockCheck check = new InvStockCheck();
        when(service.selectCheckList(any(InvStockCheck.class), isNull()))
                .thenReturn(List.of());
        when(service.selectApprovalTodoList(any(InvStockCheck.class), isNull()))
                .thenReturn(List.of());
        when(service.createCheck(any(InvStockCheck.class), isNull()))
                .thenReturn(check);
        when(service.assignCounter(anyLong(), any(InvStockCheckAssignmentRequest.class), isNull()))
                .thenReturn(check);
        when(service.inputActualQty(anyLong(), any(), isNull())).thenReturn(check);
        when(service.getCheckDetail(anyLong(), isNull())).thenReturn(check);
        when(service.withdrawApproval(anyLong(), isNull())).thenReturn("已提交");
        when(approvalService.selectTrack(anyLong(), isNull())).thenReturn(List.of());
        InvStockCheckController controller = controller(service, approvalService);
        MockHttpServletRequest request = requestContext();
        InvStockCheckApprovalRequest approval = new InvStockCheckApprovalRequest();
        InvStockCheckAssignmentRequest assignment = new InvStockCheckAssignmentRequest();

        assertNoStoreAfter(response -> controller.list(new InvStockCheck(), request, response));
        assertNoStoreAfter(response -> controller.detail(1001L, request, response));
        assertNoStoreAfter(response -> controller.create(new InvStockCheck(), request, response));
        assertNoStoreAfter(response -> controller.counterCandidates(null, null, request, response));
        assertNoStoreAfter(response -> controller.assign(1001L, assignment, request, response));
        assertNoStoreAfter(response -> controller.input(1001L, new InvStockCheck(), request, response));
        assertNoStoreAfter(response -> controller.submit(1001L, new InvStockCheck(), request, response));
        assertNoStoreAfter(response -> controller.restart(1001L, request, response));
        assertNoStoreAfter(response -> controller.approvalTodo(new InvStockCheck(), request, response));
        assertNoStoreAfter(response -> controller.approve(1001L, approval, request, response));
        assertNoStoreAfter(response -> controller.reject(1001L, approval, request, response));
        assertNoStoreAfter(response -> controller.approvalTrack(1001L, request, response));
        assertNoStoreAfter(response -> controller.cancel(1001L, request, response));
        assertNoStoreAfter(response -> controller.withdraw(1001L, request, response));
        assertNoStoreAfter(response -> controller.delete(new Long[] { 1001L }, request, response));
        assertNoStoreAfter(response -> controller.export(response, new InvStockCheck(), request));
    }

    private static InvStockCheckController controller(IInvStockCheckService service,
            IInvStockCheckApprovalService approvalService)
    {
        InvStockCheckController controller = new InvStockCheckController();
        ReflectionTestUtils.setField(controller, "stockCheckService", service);
        ReflectionTestUtils.setField(controller, "stockCheckApprovalService", approvalService);
        return controller;
    }

    private static MockHttpServletRequest requestContext()
    {
        MockHttpServletRequest request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        return request;
    }

    private static InvStockCheck checkWithCost()
    {
        InvStockCheckDetail detail = new InvStockCheckDetail();
        detail.setDetailId(501L);
        detail.setCostPrice(new BigDecimal("8.00"));
        InvStockCheck check = new InvStockCheck();
        check.setCheckId(1001L);
        check.setDetails(List.of(detail));
        return check;
    }

    private static InvStockCheckApprovalInstance approvalInstance()
    {
        InvStockCheckApprovalInstance instance = new InvStockCheckApprovalInstance();
        instance.setInstanceId(41L);
        instance.setDetailSnapshot("[{\"detailId\":501,\"costPrice\":8.00}]");
        return instance;
    }

    private static void setCostViewer()
    {
        LoginUser loginUser = new LoginUser();
        loginUser.setUserid(42L);
        loginUser.setUsername("stock-check-cost-viewer");
        loginUser.setPermissions(Set.of("inv:cost:view"));
        SecurityContextHolder.setUserId("42");
        SecurityContextHolder.set(SecurityConstants.LOGIN_USER, loginUser);
    }

    private static void assertNoStoreAfter(Consumer<MockHttpServletResponse> action)
    {
        MockHttpServletResponse response = new MockHttpServletResponse();
        action.accept(response);
        assertNoStore(response);
    }

    private static void assertNoStore(MockHttpServletResponse response)
    {
        assertThat(response.getHeader("Cache-Control"))
                .isEqualTo("no-store, max-age=0");
        assertThat(response.getHeader("Pragma")).isEqualTo("no-cache");
    }
}
