package com.erp.file.filter;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 防盗链过滤器
 * 
 * @author erp
 */
public class RefererFilter implements Filter
{
    /**
     * 允许的域名列表
     */
    public List<String> allowedDomains = Collections.emptyList();

    @Override
    public void init(FilterConfig filterConfig) throws ServletException
    {
        String domains = filterConfig.getInitParameter("allowedDomains");
        List<String> normalizedDomains = new ArrayList<String>();
        if (domains != null)
        {
            for (String domain : domains.split(","))
            {
                String normalizedDomain = domain.trim().toLowerCase(Locale.ROOT);
                if (!normalizedDomain.isEmpty())
                {
                    normalizedDomains.add(normalizedDomain);
                }
            }
        }
        this.allowedDomains = normalizedDomains;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException
    {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;

        String referer = req.getHeader("Referer");

        // 如果Referer为空，兼容直接打开或下载文件
        if (referer == null || referer.trim().isEmpty())
        {
            chain.doFilter(request, response);
            return;
        }

        boolean allowed = isAllowedReferer(referer);

        // 根据检查结果决定是否放行
        if (allowed)
        {
            chain.doFilter(request, response);
        }
        else
        {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN, "Access denied: Referer '" + referer + "' is not allowed");
        }
    }

    private boolean isAllowedReferer(String referer)
    {
        String host = parseHost(referer);
        if (host == null)
        {
            return false;
        }

        for (String domain : allowedDomains)
        {
            if (host.equals(domain) || host.endsWith("." + domain))
            {
                return true;
            }
        }
        return false;
    }

    private String parseHost(String referer)
    {
        try
        {
            String host = new URI(referer).getHost();
            return host == null ? null : host.toLowerCase(Locale.ROOT);
        }
        catch (URISyntaxException ex)
        {
            return null;
        }
    }

    @Override
    public void destroy()
    {

    }
}
