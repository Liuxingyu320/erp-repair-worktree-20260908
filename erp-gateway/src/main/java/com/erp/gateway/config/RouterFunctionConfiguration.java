package com.erp.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RequestPredicates;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import com.erp.gateway.handler.ValidateCodeHandler;

/**
 * 路由配置信息
 * 
 * @author erp
 */
@Configuration
public class RouterFunctionConfiguration
{
    private final ValidateCodeHandler validateCodeHandler;

    public RouterFunctionConfiguration(ValidateCodeHandler validateCodeHandler)
    {
        this.validateCodeHandler = validateCodeHandler;
    }

    @SuppressWarnings("rawtypes")
    @Bean
    public RouterFunction routerFunction()
    {
        return RouterFunctions.route(
                RequestPredicates.GET("/code"),
                validateCodeHandler);
    }
}
