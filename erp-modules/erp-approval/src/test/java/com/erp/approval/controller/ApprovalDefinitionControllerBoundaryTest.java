package com.erp.approval.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import com.erp.approval.domain.ApprovalRule;
import com.erp.approval.domain.ApprovalTemplate;
import com.erp.approval.service.ApprovalDefinitionService;
import com.erp.common.security.annotation.IdempotentSubmit;

class ApprovalDefinitionControllerBoundaryTest
{
    private static final Set<String> MUTATING_METHODS = Set.of(
            "createRule", "updateRule", "draft", "saveVersion",
            "publish", "disable");

    @Test
    void persistentDefinitionCommandsRequireIdempotency()
    {
        List<String> missing = MUTATING_METHODS.stream()
                .map(ApprovalDefinitionControllerBoundaryTest::methodNamed)
                .filter(method -> method.getAnnotation(IdempotentSubmit.class) == null)
                .map(Method::getName)
                .sorted()
                .toList();

        assertThat(missing)
                .as("approval definition writes should reject duplicate request identities")
                .isEmpty();
    }

    @Test
    void crossOrganizationDefinitionReadsDisableCaching()
    {
        ApprovalDefinitionService service = mock(ApprovalDefinitionService.class);
        when(service.listTemplates(any())).thenReturn(List.of());
        when(service.listRules(any())).thenReturn(List.of());
        ApprovalDefinitionController controller = new ApprovalDefinitionController(service);
        MockHttpServletRequest request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        try
        {
            assertNoStore(response -> controller.templates(new ApprovalTemplate(), response));
            assertNoStore(response -> controller.template(6102L, response));
            assertNoStore(response -> controller.rules(new ApprovalRule(), response));
            assertNoStore(response -> controller.rule(7101L, response));
        }
        finally
        {
            RequestContextHolder.resetRequestAttributes();
        }
    }

    private static Method methodNamed(String name)
    {
        return List.of(ApprovalDefinitionController.class.getDeclaredMethods()).stream()
                .filter(method -> method.getName().equals(name))
                .findFirst()
                .orElseThrow();
    }

    private static void assertNoStore(ResponseCall call)
    {
        MockHttpServletResponse response = new MockHttpServletResponse();
        call.invoke(response);
        assertThat(response.getHeader("Cache-Control"))
                .isEqualTo("no-store, max-age=0");
        assertThat(response.getHeader("Pragma")).isEqualTo("no-cache");
    }

    @FunctionalInterface
    private interface ResponseCall
    {
        void invoke(HttpServletResponse response);
    }
}
