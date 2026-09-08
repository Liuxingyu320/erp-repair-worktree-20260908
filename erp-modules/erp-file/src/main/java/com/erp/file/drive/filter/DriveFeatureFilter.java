package com.erp.file.drive.filter;

import java.io.IOException;
import com.erp.common.core.web.domain.AjaxResult;
import com.erp.file.drive.config.DriveProperties;
import com.erp.file.drive.constant.DriveErrorCodes;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

/**
 * 在控制器权限切面之前关闭全部内部云盘接口。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class DriveFeatureFilter extends OncePerRequestFilter
{
    private static final String DRIVE_PATH = "/drive";

    private final DriveProperties properties;
    private final ObjectMapper objectMapper;

    public DriveFeatureFilter(DriveProperties properties, ObjectMapper objectMapper)
    {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request)
    {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath))
        {
            uri = uri.substring(contextPath.length());
        }
        return !(DRIVE_PATH.equals(uri) || uri.startsWith(DRIVE_PATH + "/"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException
    {
        if (properties.isEnabled())
        {
            filterChain.doFilter(request, response);
            return;
        }

        response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        response.setCharacterEncoding("UTF-8");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("Cache-Control", "no-store");
        AjaxResult result = AjaxResult.error(HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                "云盘功能尚未开启")
                .put("businessCode", DriveErrorCodes.DRIVE_DISABLED);
        objectMapper.writeValue(response.getWriter(), result);
    }
}
