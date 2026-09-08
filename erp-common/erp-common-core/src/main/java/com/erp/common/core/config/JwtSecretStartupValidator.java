package com.erp.common.core.config;

import com.erp.common.core.utils.JwtUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * JWT密钥启动校验
 */
@Configuration(proxyBeanMethods = false)
public class JwtSecretStartupValidator implements InitializingBean
{
    private final Environment environment;

    public JwtSecretStartupValidator(Environment environment)
    {
        this.environment = environment;
    }

    @Override
    public void afterPropertiesSet()
    {
        JwtUtils.validateExternalSecretForProduction(environment);
    }
}
