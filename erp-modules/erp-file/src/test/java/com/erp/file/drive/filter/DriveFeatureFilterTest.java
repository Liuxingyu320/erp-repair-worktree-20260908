package com.erp.file.drive.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.erp.file.drive.config.DriveProperties;
import com.erp.file.drive.constant.DriveErrorCodes;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@DisplayName("云盘功能开关前置过滤器")
class DriveFeatureFilterTest
{
    @Test
    @DisplayName("关闭时 /drive 请求在权限切面前返回 503 JSON")
    void shouldRejectDriveRequestWhenDisabled() throws Exception
    {
        DriveProperties properties = new DriveProperties();
        DriveFeatureFilter filter = new DriveFeatureFilter(properties, new ObjectMapper());
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/drive/spaces");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getContentType()).startsWith("application/json");
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
        JsonNode body = new ObjectMapper().readTree(response.getContentAsByteArray());
        assertThat(body.path("businessCode").asText()).isEqualTo(DriveErrorCodes.DRIVE_DISABLED);
        assertThat(body.path("msg").asText()).isEqualTo("云盘功能尚未开启");
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    @DisplayName("开启时 /drive 请求继续进入后续链")
    void shouldContinueDriveRequestWhenEnabled() throws Exception
    {
        DriveProperties properties = new DriveProperties();
        properties.setEnabled(true);
        DriveFeatureFilter filter = new DriveFeatureFilter(properties, new ObjectMapper());
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/drive/spaces");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(response.getContentAsByteArray()).isEmpty();
    }

    @Test
    @DisplayName("旧上传接口始终绕过云盘开关")
    void shouldAlwaysBypassLegacyUpload() throws Exception
    {
        DriveProperties properties = new DriveProperties();
        DriveFeatureFilter filter = new DriveFeatureFilter(properties, new ObjectMapper());
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/upload");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }
}
