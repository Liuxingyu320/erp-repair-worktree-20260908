package com.erp.common.security.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class WebMvcConfigTest
{
    @Test
    void refreshAndLogoutLoadSessionContextWithoutCredentialStateBlocking()
    {
        assertThat(WebMvcConfig.headerExcludeUrls)
                .containsExactly("/login")
                .doesNotContain("/logout", "/refresh");
        assertThat(WebMvcConfig.credentialExcludeUrls)
                .contains("/login", "/logout", "/refresh");
    }
}
