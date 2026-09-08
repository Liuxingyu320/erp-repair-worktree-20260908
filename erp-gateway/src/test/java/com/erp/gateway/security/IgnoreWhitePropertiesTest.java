package com.erp.gateway.security;

import java.lang.reflect.Method;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import com.erp.gateway.config.properties.IgnoreWhiteProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

public class IgnoreWhitePropertiesTest
{
    @Test
    public void testOnlyExplicitPublicFilesCanBeAnonymous() throws Exception
    {
        IgnoreWhiteProperties properties = new IgnoreWhiteProperties();
        properties.setWhites(Arrays.asList("/file/public/**"));

        Method setExcludes = IgnoreWhiteProperties.class.getMethod("setExcludes", java.util.List.class);
        setExcludes.invoke(properties, Arrays.asList("/file/upload", "/file/delete"));

        Method isWhitelisted = IgnoreWhiteProperties.class.getMethod("isWhitelisted", String.class);
        assertFalse((Boolean) isWhitelisted.invoke(properties, "/file/upload"),
                "/file/upload must not be anonymous");
        assertFalse((Boolean) isWhitelisted.invoke(properties, "/file/delete"),
                "/file/delete must not be anonymous");
        assertFalse((Boolean) isWhitelisted.invoke(properties, "/file/labor-contract/100/archive.docx"),
                "labor contract files must not be anonymous");
        assertTrue((Boolean) isWhitelisted.invoke(properties, "/file/public/demo.png"),
                "explicit public files can remain anonymous");
    }

    @Test
    public void testApiDocsAreNotAnonymousInGatewayBootstrap() throws Exception
    {
        String bootstrap = Files.readString(Path.of("src/main/resources/bootstrap.yml"));

        assertFalse(bootstrap.contains("- /*/v2/api-docs"),
                "v2 api docs must not be in the anonymous gateway whitelist");
        assertFalse(bootstrap.contains("- /*/v3/api-docs"),
                "v3 api docs must not be in the anonymous gateway whitelist");
    }

    @Test
    public void testCaptchaRoutesAreAnonymousInGatewayBootstrap() throws Exception
    {
        IgnoreWhiteProperties properties = loadGatewayIgnoreProperties();

        assertTrue(properties.isWhitelisted("/code"),
                "the gateway captcha handler must be reachable before authentication");
        assertTrue(properties.isWhitelisted("/auth/captchaImage"),
                "the legacy captcha path must remain compatible");
        assertFalse(properties.isWhitelisted("/code/other"),
                "only the exact captcha handler path should be anonymous");
    }

    private static IgnoreWhiteProperties loadGatewayIgnoreProperties() throws IOException
    {
        StandardEnvironment environment = new StandardEnvironment();
        for (PropertySource<?> source : new YamlPropertySourceLoader()
                .load("bootstrap.yml", new ClassPathResource("bootstrap.yml")))
        {
            environment.getPropertySources().addLast(source);
        }
        return Binder.get(environment)
                .bind("security.ignore", Bindable.of(IgnoreWhiteProperties.class))
                .orElseThrow(() -> new AssertionError("security.ignore must be configured"));
    }

    private static void assertTrue(boolean condition, String message)
    {
        if (!condition)
        {
            throw new AssertionError(message);
        }
    }

    private static void assertFalse(boolean condition, String message)
    {
        assertTrue(!condition, message);
    }
}
