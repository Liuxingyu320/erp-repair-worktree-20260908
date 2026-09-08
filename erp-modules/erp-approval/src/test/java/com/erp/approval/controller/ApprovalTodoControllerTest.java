package com.erp.approval.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.Collections;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import com.erp.approval.service.ApprovalTodoService;
import com.erp.common.core.domain.todo.TodoQuery;
import com.erp.common.core.domain.todo.TodoSummary;
import com.erp.common.security.annotation.RequiresLogin;
import com.erp.common.security.annotation.RequiresPermissions;
import com.erp.common.security.utils.SecurityUtils;

class ApprovalTodoControllerTest
{
    @Test
    void exposesLoginOnlySummaryAndList() throws Exception
    {
        assertThat(ApprovalTodoController.class.getAnnotation(RequestMapping.class).value())
                .containsExactly("/todo");
        Method summary = ApprovalTodoController.class.getMethod("summary", TodoQuery.class,
                HttpServletResponse.class);
        Method list = ApprovalTodoController.class.getMethod("list", TodoQuery.class,
                HttpServletResponse.class);
        assertThat(summary.getAnnotation(GetMapping.class).value()).containsExactly("/summary");
        assertThat(list.getAnnotation(GetMapping.class).value()).containsExactly("", "/list");
        assertThat(summary.getAnnotation(RequiresLogin.class)).isNotNull();
        assertThat(list.getAnnotation(RequiresLogin.class)).isNotNull();
        assertThat(summary.getAnnotation(RequiresPermissions.class)).isNull();
        assertThat(list.getAnnotation(RequiresPermissions.class)).isNull();
        assertThat(ApprovalTodoController.class.getAnnotation(RequiresPermissions.class)).isNull();
    }

    @Test
    void forwardsCurrentUserAndDisablesCaching()
    {
        ApprovalTodoService service = mock(ApprovalTodoService.class);
        TodoQuery query = new TodoQuery();
        when(service.summary(eq(query), eq(7L))).thenReturn(new TodoSummary());
        when(service.list(eq(query), eq(7L))).thenReturn(Collections.emptyList());
        ApprovalTodoController controller = new ApprovalTodoController(service);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class))
        {
            security.when(SecurityUtils::getUserId).thenReturn(7L);

            assertThat(controller.summary(query, response).get("data")).isInstanceOf(TodoSummary.class);
            assertThat(controller.list(query, response).getTotal()).isZero();
        }
        finally
        {
            RequestContextHolder.resetRequestAttributes();
        }

        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store, max-age=0");
        assertThat(response.getHeader("Pragma")).isEqualTo("no-cache");
        verify(service).summary(query, 7L);
        verify(service).list(query, 7L);
    }
}
