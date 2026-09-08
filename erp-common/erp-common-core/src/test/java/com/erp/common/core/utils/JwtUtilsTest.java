package com.erp.common.core.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.erp.common.core.constant.TokenConstants;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

public class JwtUtilsTest
{
    private static final String REQUIRED_SECRET_MESSAGE = "生产环境必须配置外部JWT密钥";

    private String originalErpJwtSecret;

    private String originalJwtSecret;

    private String originalTokenSecret;

    private String originalActiveProfiles;

    private String originalJwtUtilsSecret;

    @BeforeEach
    public void setUp()
    {
        saveOriginalSystemProperties();
        clearJwtSystemProperties();
        originalJwtUtilsSecret = JwtUtils.secret;
        JwtUtils.secret = TokenConstants.SECRET;
    }

    @AfterEach
    public void tearDown()
    {
        JwtUtils.secret = originalJwtUtilsSecret;
        restoreSystemProperties();
    }

    @Test
    public void testSecretUsesSystemPropertyBeforeFallback()
    {
        String configuredSecret = "configured-secret-for-jwt-tests";
        System.setProperty("erp.jwt.secret", configuredSecret);

        assertThat(TokenConstants.SECRET).isNotEqualTo("abcdefghijklmnopqrstuvwxyz");
        assertThat(JwtUtils.resolveSecret(null)).isEqualTo(configuredSecret);
    }

    @Test
    public void publicStaticSecretStartsFromSafeFallbackValue()
    {
        assertThat(JwtUtils.secret).isEqualTo(TokenConstants.SECRET);
    }

    @Test
    public void resolveSecretUsesProvidedEnvironmentSecretInProductionProfile()
    {
        System.setProperty("spring.profiles.active", "prod");

        assertThat(JwtUtils.resolveSecret("configured-env-secret")).isEqualTo("configured-env-secret");
    }

    @Test
    public void resolveSecretRejectsProdProfileWithoutExternalSecret()
    {
        System.setProperty("spring.profiles.active", "local,prod");

        assertThatThrownBy(() -> JwtUtils.resolveSecret(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(REQUIRED_SECRET_MESSAGE);
    }

    @Test
    public void resolveSecretRejectsProductionProfileWithoutExternalSecret()
    {
        System.setProperty("spring.profiles.active", "production");

        assertThatThrownBy(() -> JwtUtils.resolveSecret(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(REQUIRED_SECRET_MESSAGE);
    }

    @Test
    public void resolveSecretRejectsUnknownNonDevelopmentProfileWithoutExternalSecret()
    {
        System.setProperty("spring.profiles.active", "prd");

        assertThatThrownBy(() -> JwtUtils.resolveSecret(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(REQUIRED_SECRET_MESSAGE);
    }

    @Test
    public void resolveSecretFallsBackForDevelopmentProfilesWithoutExternalSecret()
    {
        for (String profile : new String[] { "dev", "local", "test" })
        {
            System.setProperty("spring.profiles.active", profile);

            assertThat(JwtUtils.resolveSecret(null)).isEqualTo(TokenConstants.SECRET);
        }
    }

    @Test
    public void resolveSecretRejectsMissingProfileWithoutExternalSecret()
    {
        assertThatThrownBy(() -> JwtUtils.resolveSecret(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(REQUIRED_SECRET_MESSAGE);
    }

    @Test
    public void validateExternalSecretForProductionRejectsProdProfileWithoutExternalSecret()
    {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");

        assertThatThrownBy(() -> JwtUtils.validateExternalSecretForProduction(environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(REQUIRED_SECRET_MESSAGE);
    }

    @Test
    public void validateExternalSecretForProductionAllowsEnvironmentSecret()
    {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("ERP_JWT_SECRET", "configured-env-secret");
        environment.setActiveProfiles("prod");

        assertThatCode(() -> JwtUtils.validateExternalSecretForProduction(environment))
                .doesNotThrowAnyException();
    }

    private static void clearJwtSystemProperties()
    {
        System.clearProperty("erp.jwt.secret");
        System.clearProperty("jwt.secret");
        System.clearProperty("token.secret");
        System.clearProperty("spring.profiles.active");
    }

    private void saveOriginalSystemProperties()
    {
        originalErpJwtSecret = System.getProperty("erp.jwt.secret");
        originalJwtSecret = System.getProperty("jwt.secret");
        originalTokenSecret = System.getProperty("token.secret");
        originalActiveProfiles = System.getProperty("spring.profiles.active");
    }

    private void restoreSystemProperties()
    {
        restoreSystemProperty("erp.jwt.secret", originalErpJwtSecret);
        restoreSystemProperty("jwt.secret", originalJwtSecret);
        restoreSystemProperty("token.secret", originalTokenSecret);
        restoreSystemProperty("spring.profiles.active", originalActiveProfiles);
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
