package com.erp.gateway.config.properties;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Configuration;
import com.erp.common.core.utils.StringUtils;

/**
 * 放行白名单配置
 * 
 * @author erp
 */
@Configuration
@RefreshScope
@ConfigurationProperties(prefix = "security.ignore")
public class IgnoreWhiteProperties
{
    /**
     * 放行白名单配置，网关不校验此处的白名单
     */
    private List<String> whites = new ArrayList<>();

    /**
     * 白名单排除配置，匹配时即使命中白名单也必须鉴权
     */
    private List<String> excludes = new ArrayList<>();

    public List<String> getWhites()
    {
        return whites;
    }

    public void setWhites(List<String> whites)
    {
        this.whites = whites;
    }

    public List<String> getExcludes()
    {
        return excludes;
    }

    public void setExcludes(List<String> excludes)
    {
        this.excludes = excludes;
    }

    public boolean isWhitelisted(String url)
    {
        return StringUtils.matches(url, whites) && !StringUtils.matches(url, excludes);
    }
}
