package com.erp.common.log.sanitize;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class SensitiveLogSanitizerTest
{
    private final SensitiveLogSanitizer sanitizer = new SensitiveLogSanitizer();

    @Test
    void shouldRedactSecretAliasesAndMaskPersonalDataRecursively()
    {
        Map<String, Object> child = new LinkedHashMap<>();
        child.put("Access_Token", "access-token-value");
        child.put("phoneNumber", "13812345678");
        child.put("id_number", "110101199001011234");
        child.put("bank-account", "6222021234567890123");

        Map<String, Object> source = new LinkedHashMap<>();
        source.put("PASSWORD", "raw-password");
        source.put("items", List.of(child));

        String result = sanitizer.sanitizeToJson(source, 10000);

        assertThat(result)
                .contains("[REDACTED]", "138****5678", "1101****1234", "6222****0123")
                .doesNotContain("raw-password", "access-token-value", "13812345678",
                        "110101199001011234", "6222021234567890123");
    }

    @Test
    void shouldApplyCallerSuppliedSecretNamesToBeansAndMaps()
    {
        CredentialBean bean = new CredentialBean();
        bean.setCustomCredential("custom-secret");
        bean.setNested(Map.of("custom_credential", "nested-secret"));

        String result = sanitizer.sanitizeToJson(bean, 10000, "customCredential");

        assertThat(result).contains("[REDACTED]")
                .doesNotContain("custom-secret", "nested-secret");
    }

    @Test
    void shouldBoundCollectionsStringsDepthAndCircularReferences()
    {
        Map<String, Object> circular = new LinkedHashMap<>();
        circular.put("self", circular);
        circular.put("longText", "x".repeat(1500));
        List<Integer> many = new ArrayList<>();
        for (int index = 0; index < 60; index++)
        {
            many.add(index);
        }
        circular.put("many", many);

        String result = sanitizer.sanitizeToJson(circular, 10000);

        assertThat(result).contains("[CIRCULAR]", "[TRUNCATED]");
        assertThat(result).doesNotContain("x".repeat(1100));
    }

    @Test
    void shouldUseSafeFileAndThrowableSummaries()
    {
        MockMultipartFile file = new MockMultipartFile("file", "report.pdf", "application/pdf",
                new byte[] { 1, 2, 3 });
        IllegalStateException error = new IllegalStateException(
                "authorization=Bearer abc.def and password=top-secret");

        String result = sanitizer.sanitizeToJson(Map.of("file", file, "error", error), 10000);

        assertThat(result).contains("report.pdf", "IllegalStateException", "[REDACTED]")
                .doesNotContain("abc.def", "top-secret");
    }

    @Test
    void shouldSanitizeHistoricalJsonAndMalformedText()
    {
        String validJson = "{\"newPassword\":\"new-secret\",\"nested\":{\"token\":\"token-secret\"}}";
        String invalidJson = "password=legacy-secret; Authorization: Bearer legacy-token";

        assertThat(sanitizer.sanitizeJsonText(validJson))
                .contains("[REDACTED]")
                .doesNotContain("new-secret", "token-secret");
        assertThat(sanitizer.sanitizeJsonText(invalidJson))
                .contains("[REDACTED]")
                .doesNotContain("legacy-secret", "legacy-token");
    }

    @Test
    void shouldMaskStandalonePersonalDataInExceptionText()
    {
        String message = "手机号13812345678，身份证110101199001011234，银行卡6222021234567890123";

        String result = sanitizer.sanitizeText(message);

        assertThat(result)
                .contains("138****5678", "1101****1234", "6222****0123")
                .doesNotContain("13812345678", "110101199001011234", "6222021234567890123");
    }

    static class CredentialBean
    {
        private String customCredential;
        private Map<String, Object> nested;

        public String getCustomCredential()
        {
            return customCredential;
        }

        public void setCustomCredential(String customCredential)
        {
            this.customCredential = customCredential;
        }

        public Map<String, Object> getNested()
        {
            return nested;
        }

        public void setNested(Map<String, Object> nested)
        {
            this.nested = nested;
        }
    }
}
