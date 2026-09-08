package com.erp.inventory.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import com.erp.common.core.domain.todo.TodoQuery;
import com.erp.common.core.domain.todo.TodoSummary;
import com.erp.common.core.utils.ShopHeaderUtils;
import com.erp.common.security.annotation.RequiresLogin;
import com.erp.common.security.annotation.RequiresPermissions;
import com.erp.inventory.service.IInvTodoService;

class InvTodoControllerTest
{
    @Test
    void controllerExposesLoginOnlySummaryAndListPaths() throws Exception
    {
        Class<InvTodoController> type = InvTodoController.class;
        assertThat(type.getAnnotation(RequestMapping.class).value()).containsExactly("/todo");

        Method summary = type.getMethod("summary", TodoQuery.class,
                jakarta.servlet.http.HttpServletRequest.class, jakarta.servlet.http.HttpServletResponse.class);
        Method list = type.getMethod("list", TodoQuery.class,
                jakarta.servlet.http.HttpServletRequest.class, jakarta.servlet.http.HttpServletResponse.class);
        assertThat(summary.getAnnotation(GetMapping.class).value()).containsExactly("/summary");
        assertThat(list.getAnnotation(GetMapping.class).value()).containsExactly("/list");
        assertThat(summary.getAnnotation(RequiresLogin.class)).isNotNull();
        assertThat(list.getAnnotation(RequiresLogin.class)).isNotNull();
        assertThat(summary.getAnnotation(RequiresPermissions.class)).isNull();
        assertThat(list.getAnnotation(RequiresPermissions.class)).isNull();
        assertThat(type.getAnnotation(RequiresPermissions.class)).isNull();
    }

    @Test
    void forwardsDeptHeaderAndLeavesPaginationInsideService()
    {
        IInvTodoService service = mock(IInvTodoService.class);
        when(service.selectSummary(any(), eq(88L))).thenReturn(new TodoSummary());
        when(service.selectTodoList(any(), eq(88L))).thenReturn(Collections.emptyList());
        InvTodoController controller = new InvTodoController();
        ReflectionTestUtils.setField(controller, "todoService", service);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.addHeader(ShopHeaderUtils.SHOP_HEADER, "88");
        TodoQuery query = new TodoQuery();
        query.setPriority("urgent");

        assertThat(controller.summary(query, request, response).get("data")).isInstanceOf(TodoSummary.class);
        assertThat(controller.list(query, request, response).getTotal()).isZero();
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store, max-age=0");
        assertThat(response.getHeader("Pragma")).isEqualTo("no-cache");
        verify(service).selectSummary(query, 88L);
        verify(service).selectTodoList(query, 88L);
        assertThat(query.getPriority()).isEqualTo("urgent");
    }
}
