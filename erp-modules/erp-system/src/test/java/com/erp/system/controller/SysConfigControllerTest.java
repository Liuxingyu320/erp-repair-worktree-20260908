package com.erp.system.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.erp.common.security.annotation.RequiresPermissions;
import com.erp.system.config.SysConfigDescriptorRegistry;
import com.erp.system.service.ISysConfigService;

class SysConfigControllerTest
{
    @Test
    void descriptorsUseTheirOwnProtectedRouteAndNeverReadStoredValues() throws Exception
    {
        SysConfigController controller = new SysConfigController();
        ISysConfigService service = mock(ISysConfigService.class);
        ReflectionTestUtils.setField(controller, "configService", service);
        ReflectionTestUtils.setField(controller, "configDescriptorRegistry",
                new SysConfigDescriptorRegistry(new ObjectMapper()));
        MockMvcBuilders.standaloneSetup(controller).build().perform(get("/config/descriptors"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].configKey").value("audit.retention.days"))
                .andExpect(jsonPath("$.data[0].configValue").doesNotExist());
        verifyNoInteractions(service);
        assertThat(SysConfigController.class.getMethod("descriptors")
                .getAnnotation(RequiresPermissions.class).value()).containsExactly("system:config:list");
    }
}
