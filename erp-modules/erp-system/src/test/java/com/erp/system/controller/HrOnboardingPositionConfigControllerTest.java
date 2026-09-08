package com.erp.system.controller;

import static org.assertj.core.api.Assertions.assertThat;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import com.erp.common.security.annotation.RequiresPermissions;
import com.erp.system.domain.HrOnboardingPositionConfig;
import com.erp.system.domain.vo.HrOnboardingVersionRequest;

class HrOnboardingPositionConfigControllerTest
{
    @Test
    void everyEndpointUsesTheDedicatedConfigPermission() throws Exception
    {
        assertEndpoint("list", new Class<?>[] { HrOnboardingPositionConfig.class }, GetMapping.class, "/list");
        assertEndpoint("get", new Class<?>[] { Long.class }, GetMapping.class, "/{id}");
        assertEndpoint("create", new Class<?>[] { HrOnboardingPositionConfig.class }, PostMapping.class, "");
        assertEndpoint("update", new Class<?>[] { Long.class, HrOnboardingPositionConfig.class }, PutMapping.class, "/{id}");
        assertEndpoint("disable", new Class<?>[] { Long.class, HrOnboardingVersionRequest.class },
                PostMapping.class, "/{id}/disable");
        assertEndpoint("options", new Class<?>[0], GetMapping.class, "/options");
    }

    private void assertEndpoint(String name, Class<?>[] args, Class<?> mapping, String path) throws Exception
    {
        Method method = HrOnboardingPositionConfigController.class.getMethod(name, args);
        RequiresPermissions permissions = method.getAnnotation(RequiresPermissions.class);
        assertThat(permissions).isNotNull();
        assertThat(permissions.value()).containsExactly("hr:onboarding:config");
        String[] values;
        if (mapping == GetMapping.class) values = method.getAnnotation(GetMapping.class).value();
        else if (mapping == PostMapping.class) values = method.getAnnotation(PostMapping.class).value();
        else values = method.getAnnotation(PutMapping.class).value();
        if (path.isEmpty()) assertThat(values).isEmpty();
        else assertThat(values).containsExactly(path);
    }
}
