package com.erp.file.config;

import java.io.File;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 通用映射配置
 * 
 * @author erp
 */
@Configuration
public class ResourcesConfig implements WebMvcConfigurer
{
    /**
     * 上传文件存储在本地的根路径
     */
    @Value("${file.path}")
    private String localFilePath;

    /**
     * 资源映射路径 前缀
     */
    @Value("${file.prefix}")
    public String localFilePrefix;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry)
    {
        /** 仅公开明确public目录，业务私有文件必须走鉴权接口下载 */
        registry.addResourceHandler(localFilePrefix + "/public/**")
                .addResourceLocations("file:" + localFilePath + File.separator + "public" + File.separator);
    }
    
    /**
     * 开启跨域
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // 设置允许跨域的路由
        registry.addMapping(localFilePrefix + "/public/**")
                // 使用 allowedOriginPatterns 兼容不同 localhost/127.0.0.1 端口
                .allowedOriginPatterns("*")
                // 设置允许的方法
                .allowedMethods("GET", "OPTIONS")
                .allowCredentials(false);
    }
}
