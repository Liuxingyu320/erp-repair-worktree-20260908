package com.erp.inventory.controller;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.security.annotation.IdempotentSubmit;
import com.erp.common.security.annotation.RequiresPermissions;
import com.erp.inventory.annotation.PersistentCommand;
import com.erp.inventory.domain.dto.InvPurchaseReceiveResult;
import com.erp.inventory.domain.dto.InvReceiveRequest;
import com.erp.inventory.service.IInvPurchaseService;

class InvPurchaseReceiveCommandBoundaryTest
{
    private static final String BODY = """
            {"warehouseId":20,"arrivedTime":"2026-09-12 12:00:00",
             "items":[{"detailId":201,"receiveQuantity":20}]}
            """;

    @Test void requiredHeaderRejectsBeforeBusinessService() throws Exception
    {
        IInvPurchaseService service = mock(IInvPurchaseService.class);
        var mvc = MockMvcBuilders.standaloneSetup(controller(service)).build();
        mvc.perform(post("/purchase/receive/10").contentType("application/json").content(BODY))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test void receiveReturnsOriginalBatchAndDisablesCaching() throws Exception
    {
        IInvPurchaseService service = mock(IInvPurchaseService.class);
        InvPurchaseReceiveResult result = new InvPurchaseReceiveResult();
        result.setRequestId("receive-http-01"); result.setReceiptBatchId(901L);
        result.setReceivedQuantity(new BigDecimal("20"));
        when(service.receivePurchase(eq(10L), any(), eq(20L), eq("receive-http-01"))).thenReturn(result);
        var mvc = MockMvcBuilders.standaloneSetup(controller(service)).build();
        mvc.perform(post("/purchase/receive/10").header("X-Request-Id", "receive-http-01")
                        .header("Dept-NumId", "20").contentType("application/json").content(BODY))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.receiptBatchId").value(901))
                .andExpect(jsonPath("$.data.requestId").value("receive-http-01"))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")));
        verify(service).receivePurchase(eq(10L), any(), eq(20L), eq("receive-http-01"));
    }

    @Test void persistedResultFailureIsNotReclassifiedAsDefinitelyUnexecuted()
    {
        IInvPurchaseService service = mock(IInvPurchaseService.class);
        when(service.receivePurchase(any(), any(), any(), any()))
                .thenThrow(new ServiceException("质检命令历史结果无法安全重放"));
        assertThatThrownBy(() -> controller(service).receive(10L, new InvReceiveRequest(),
                "receive-http-01", new MockHttpServletRequest(), new MockHttpServletResponse()))
                .isInstanceOf(ServiceException.class).hasMessageContaining("无法安全重放");
    }

    @Test void persistentBoundaryRetainsPermissionAndHasNoShortTermCache() throws Exception
    {
        var method = InvPurchaseController.class.getMethod("receive", Long.class, InvReceiveRequest.class,
                String.class, jakarta.servlet.http.HttpServletRequest.class, jakarta.servlet.http.HttpServletResponse.class);
        assertThat(method.getAnnotation(PersistentCommand.class)).isNotNull();
        assertThat(method.getAnnotation(IdempotentSubmit.class)).isNull();
        assertThat(method.getAnnotation(RequiresPermissions.class).value()).containsExactly("inv:purchase:receive");
    }

    private static InvPurchaseController controller(IInvPurchaseService service)
    {
        InvPurchaseController controller = new InvPurchaseController();
        ReflectionTestUtils.setField(controller, "purchaseService", service);
        return controller;
    }
}
