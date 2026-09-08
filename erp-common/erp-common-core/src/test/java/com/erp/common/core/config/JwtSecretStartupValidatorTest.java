package com.erp.common.core.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.support.GenericApplicationContext;

class JwtSecretStartupValidatorTest
{
    private static final String REQUIRED_SECRET_MESSAGE = "生产环境必须配置外部JWT密钥";

    private static final String VALIDATOR_CLASS = "com.erp.common.core.config.JwtSecretStartupValidator";

    private static final String AUTO_CONFIGURATION_IMPORTS =
            "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports";

    private String originalErpJwtSecret;

    private String originalJwtSecret;

    private String originalTokenSecret;

    private String originalActiveProfiles;

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withPropertyValues("ERP_JWT_SECRET=")
            .withInitializer(registerJwtValidatorFromAutoConfigurationImports());

    @BeforeEach
    void setUp()
    {
        originalErpJwtSecret = System.getProperty("erp.jwt.secret");
        originalJwtSecret = System.getProperty("jwt.secret");
        originalTokenSecret = System.getProperty("token.secret");
        originalActiveProfiles = System.getProperty("spring.profiles.active");
        System.clearProperty("erp.jwt.secret");
        System.clearProperty("jwt.secret");
        System.clearProperty("token.secret");
        System.clearProperty("spring.profiles.active");
    }

    @AfterEach
    void tearDown()
    {
        restoreSystemProperty("erp.jwt.secret", originalErpJwtSecret);
        restoreSystemProperty("jwt.secret", originalJwtSecret);
        restoreSystemProperty("token.secret", originalTokenSecret);
        restoreSystemProperty("spring.profiles.active", originalActiveProfiles);
    }

    @Test
    void autoConfigurationImportsJwtSecretStartupValidator()
    {
        assertThat(autoConfigurationImports()).contains(VALIDATOR_CLASS);
    }

    @Test
    void productionProfileWithoutExternalSecretFailsContextStartup()
    {
        contextRunner.withInitializer(activeProfiles("prod")).run(context -> {
            assertThat(context).hasFailed();
            assertThat(failureMessages(context.getStartupFailure()))
                    .contains(REQUIRED_SECRET_MESSAGE);
        });
    }

    @Test
    void productionNameProfileWithoutExternalSecretFailsContextStartup()
    {
        contextRunner.withInitializer(activeProfiles("production")).run(context -> {
            assertThat(context).hasFailed();
            assertThat(failureMessages(context.getStartupFailure()))
                    .contains(REQUIRED_SECRET_MESSAGE);
        });
    }

    @Test
    void unknownNonDevelopmentProfileWithoutExternalSecretFailsContextStartup()
    {
        contextRunner.withInitializer(activeProfiles("prd")).run(context -> {
            assertThat(context).hasFailed();
            assertThat(failureMessages(context.getStartupFailure()))
                    .contains(REQUIRED_SECRET_MESSAGE);
        });
    }

    @Test
    void localProfileWithoutExternalSecretStartsContext()
    {
        contextRunner.withInitializer(activeProfiles("local")).run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void devAndTestProfilesWithoutExternalSecretStartContext()
    {
        contextRunner.withInitializer(activeProfiles("dev")).run(context -> assertThat(context).hasNotFailed());
        contextRunner.withInitializer(activeProfiles("test")).run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void contextWithoutProfileFailsWithoutExternalSecret()
    {
        contextRunner.run(context -> {
            assertThat(context).hasFailed();
            assertThat(failureMessages(context.getStartupFailure()))
                    .contains(REQUIRED_SECRET_MESSAGE);
        });
    }

    @Test
    void productionProfileWithErpJwtSecretStartsContext()
    {
        contextRunner.withInitializer(activeProfiles("prod"))
                .withPropertyValues("erp.jwt.secret=configured-secret")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void productionProfileWithEnvironmentSecretStartsContext()
    {
        contextRunner.withInitializer(activeProfiles("prod"))
                .withPropertyValues("ERP_JWT_SECRET=configured-env-secret")
                .run(context -> assertThat(context).hasNotFailed());
    }

    private static ApplicationContextInitializer<ConfigurableApplicationContext> activeProfiles(String... profiles)
    {
        return context -> context.getEnvironment().setActiveProfiles(profiles);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static ApplicationContextInitializer<ConfigurableApplicationContext>
            registerJwtValidatorFromAutoConfigurationImports()
    {
        return context -> {
            if (!autoConfigurationImports().contains(VALIDATOR_CLASS))
            {
                return;
            }
            try
            {
                Class validatorClass = Class.forName(VALIDATOR_CLASS);
                ((GenericApplicationContext) context).registerBean(validatorClass);
            }
            catch (ClassNotFoundException e)
            {
                throw new IllegalStateException("JWT startup validator is imported but missing", e);
            }
        };
    }

    private static List<String> autoConfigurationImports()
    {
        try
        {
            Enumeration<URL> resources = Thread.currentThread().getContextClassLoader()
                    .getResources(AUTO_CONFIGURATION_IMPORTS);
            List<String> imports = new ArrayList<>();
            while (resources.hasMoreElements())
            {
                imports.addAll(readImports(resources.nextElement()));
            }
            return imports;
        }
        catch (IOException e)
        {
            throw new IllegalStateException("Unable to read auto-configuration imports", e);
        }
    }

    private static List<String> readImports(URL resource) throws IOException
    {
        List<String> imports = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.openStream(),
                StandardCharsets.UTF_8)))
        {
            String line;
            while ((line = reader.readLine()) != null)
            {
                String trimmedLine = line.trim();
                if (!trimmedLine.isEmpty() && !trimmedLine.startsWith("#"))
                {
                    imports.add(trimmedLine);
                }
            }
        }
        return imports;
    }

    private static String failureMessages(Throwable throwable)
    {
        StringBuilder messages = new StringBuilder();
        Throwable current = throwable;
        while (current != null)
        {
            if (current.getMessage() != null)
            {
                messages.append(current.getMessage()).append('\n');
            }
            current = current.getCause();
        }
        return messages.toString();
    }

    private static void restoreSystemProperty(String key, String value)
    {
        if (value == null)
        {
            System.clearProperty(key);
        }
        else
        {
            System.setProperty(key, value);
        }
    }
}
