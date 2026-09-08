package com.erp.oa.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.lang.reflect.Method;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import com.erp.common.core.domain.todo.TodoQuery;
import com.erp.common.core.domain.todo.TodoConstants;
import com.erp.common.core.domain.todo.TodoSummary;
import com.erp.common.core.utils.ShopHeaderUtils;
import com.erp.common.security.annotation.RequiresLogin;
import com.erp.common.security.annotation.RequiresPermissions;
import com.erp.oa.service.IOaTodoService;

class OaTodoControllerTest
{
    @Test
    void exposesLoginOnlySummaryAndList()
            throws Exception
    {
        assertThat(OaTodoController.class.getAnnotation(RequestMapping.class).value())
                .containsExactly("/todo");
        Method summary = OaTodoController.class.getMethod("summary", TodoQuery.class,
                jakarta.servlet.http.HttpServletRequest.class, jakarta.servlet.http.HttpServletResponse.class);
        Method list = OaTodoController.class.getMethod("list", TodoQuery.class,
                jakarta.servlet.http.HttpServletRequest.class, jakarta.servlet.http.HttpServletResponse.class);
        assertThat(summary.getAnnotation(GetMapping.class).value()).containsExactly("/summary");
        assertThat(list.getAnnotation(GetMapping.class).value()).containsExactly("/list");
        assertThat(summary.getAnnotation(RequiresLogin.class)).isNotNull();
        assertThat(list.getAnnotation(RequiresLogin.class)).isNotNull();
        assertThat(summary.getAnnotation(RequiresPermissions.class)).isNull();
        assertThat(list.getAnnotation(RequiresPermissions.class)).isNull();
        assertThat(OaTodoController.class.getAnnotation(RequiresPermissions.class)).isNull();
    }

    @Test
    void forwardsOrganizationHeaderAndLeavesPaginationInService()
    {
        IOaTodoService service = mock(IOaTodoService.class);
        when(service.selectSummary(any(), eq(88L))).thenReturn(new TodoSummary());
        when(service.selectTodoList(any(), eq(88L))).thenReturn(Collections.emptyList());
        OaTodoController controller = new OaTodoController();
        ReflectionTestUtils.setField(controller, "todoService", service);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.addHeader(ShopHeaderUtils.SHOP_HEADER, "88");
        TodoQuery query = new TodoQuery();
        query.setPriority("important");

        assertThat(controller.summary(query, request, response).get("data")).isInstanceOf(TodoSummary.class);
        assertThat(controller.list(query, request, response).getTotal()).isZero();
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store, max-age=0");
        assertThat(response.getHeader("Pragma")).isEqualTo("no-cache");
        verify(service).selectSummary(query, 88L);
        verify(service).selectTodoList(query, 88L);
        assertThat(query.getPriority()).isEqualTo("important");
    }

    @Test
    void bindsActionableScopeFromTheHttpQueryWithoutRenamingIt() throws Exception
    {
        IOaTodoService service = mock(IOaTodoService.class);
        when(service.selectSummary(any(), eq(88L))).thenReturn(new TodoSummary());
        OaTodoController controller = new OaTodoController();
        ReflectionTestUtils.setField(controller, "todoService", service);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build();

        mvc.perform(get("/todo/summary")
                        .param("scopeMode", " actionable ")
                        .header(ShopHeaderUtils.SHOP_HEADER, "88"))
                .andExpect(status().isOk());

        verify(service).selectSummary(argThat(query -> TodoConstants.SCOPE_ACTIONABLE
                .equals(query.getScopeMode())), eq(88L));
    }
}
