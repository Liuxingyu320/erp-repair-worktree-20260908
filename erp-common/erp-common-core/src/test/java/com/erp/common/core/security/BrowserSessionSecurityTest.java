package com.erp.common.core.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import org.junit.jupiter.api.Test;

class BrowserSessionSecurityTest
{
    private static final String SECRET = "erp-new-2-browser-csrf-test-secret-0001";

    @Test
    void csrfProofIsStableForOneSessionAndBoundToItsAccessToken()
    {
        String first = BrowserSessionSecurity.csrfToken("signed-access-token-a", SECRET);
        String repeated = BrowserSessionSecurity.csrfToken("signed-access-token-a", SECRET);
        String otherSession = BrowserSessionSecurity.csrfToken("signed-access-token-b", SECRET);

        assertThat(first).isEqualTo(repeated).hasSize(43);
        assertThat(otherSession).isNotEqualTo(first);
        assertThat(BrowserSessionSecurity.matches("signed-access-token-a", first, SECRET)).isTrue();
        assertThat(BrowserSessionSecurity.matches("signed-access-token-b", first, SECRET)).isFalse();
    }

    @Test
    void rejectsMissingOrShortIndependentSecret()
    {
        assertThatIllegalStateException()
                .isThrownBy(() -> BrowserSessionSecurity.csrfToken("signed-token", ""))
                .withMessageContaining("未配置");
        assertThatIllegalStateException()
                .isThrownBy(() -> BrowserSessionSecurity.csrfToken("signed-token", "too-short"))
                .withMessageContaining("32 字节");
        assertThatIllegalStateException()
                .isThrownBy(() -> BrowserSessionSecurity.csrfToken("signed-token", " ".repeat(32)))
                .withMessageContaining("未配置");
    }

    @Test
    void rejectsMissingSessionTokenAndNeverAcceptsMissingOrTamperedProof()
    {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> BrowserSessionSecurity.csrfToken(" ", SECRET));

        String csrf = BrowserSessionSecurity.csrfToken("signed-token", SECRET);
        assertThat(BrowserSessionSecurity.matches("signed-token", null, SECRET)).isFalse();
        assertThat(BrowserSessionSecurity.matches("signed-token", " ", SECRET)).isFalse();
        assertThat(BrowserSessionSecurity.matches("signed-token", csrf + "x", SECRET)).isFalse();
        assertThat(BrowserSessionSecurity.matches("signed-token", " " + csrf, SECRET)).isFalse();
    }
}
