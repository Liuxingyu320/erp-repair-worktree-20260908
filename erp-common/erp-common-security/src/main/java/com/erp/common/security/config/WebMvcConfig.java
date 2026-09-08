package com.erp.common.security.config;

import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import com.erp.common.security.interceptor.HeaderInterceptor;
import com.erp.common.security.interceptor.CredentialStateInterceptor;

/**
 * 拦截器配置
 *
 * @author erp
 */
public class WebMvcConfig implements WebMvcConfigurer
{
    /** 登录入口没有现成会话；注销与刷新必须先装载登录上下文。 */
    public static final String[] headerExcludeUrls = { "/login" };

    /** 注销与刷新不应被临时凭据状态门禁阻断。 */
    public static final String[] credentialExcludeUrls = { "/login", "/logout", "/refresh" };

    @Override
    public void addInterceptors(InterceptorRegistry registry)
    {
        registry.addInterceptor(getHeaderInterceptor())
                .addPathPatterns("/**")
                .excludePathPatterns(headerExcludeUrls)
                .order(-10);
        registry.addInterceptor(getCredentialStateInterceptor())
                .addPathPatterns("/**")
                .excludePathPatterns(credentialExcludeUrls)
                .order(-9);
    }

    /**
     * 自定义请求头拦截器
     */
    public HeaderInterceptor getHeaderInterceptor()
    {
        return new HeaderInterceptor();
    }

    public CredentialStateInterceptor getCredentialStateInterceptor()
    {
        return new CredentialStateInterceptor();
    }
}
