package com.erp.common.log.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.erp.common.core.exception.ServiceException;

@DisplayName("审计日志正文脱敏")
class AuditPayloadSanitizerTest
{
    private final AuditPayloadSanitizer sanitizer = new AuditPayloadSanitizer();

    @Test
    @DisplayName("只保存显式允许且不是敏感字段的标量")
    void shouldKeepOnlyExplicitSafeScalarFields()
    {
        AuditRequest request = new AuditRequest(42L, "secret-password", "6222020202020202",
                "data:image/png;base64,AAAA", "eyJhbGciOiJIUzI1NiJ9.payload.signature");

        String json = sanitizer.sanitizeAllowedFields(new Object[] { request }, new String[] { "request" },
                new String[] { "recordId", "password", "bankAccount", "signatureDataUrl", "token" });

        assertThat(json).isEqualTo("{\"recordId\":42}");
    }

    @Test
    @DisplayName("伪装在普通字段中的长 Base64 和私有路径不会落日志")
    void shouldRejectSuspiciousValuesEvenWhenFieldNameLooksSafe()
    {
        String base64 = "A".repeat(160);
        String json = sanitizer.sanitizeAllowedFields(
                new Object[] { new DisguisedRequest(base64, "/Users/example/private/contracts/a.pdf") },
                new String[] { "request" }, new String[] { "reference", "location" });

        assertThat(json).isNull();
    }

    @Test
    @DisplayName("错误摘要只保留类型和稳定错误码")
    void shouldNotPersistExceptionMessage()
    {
        String summary = sanitizer.sanitizeError(new ServiceException("身份证号 110101199001011234", 409));

        assertThat(summary)
                .isEqualTo("ServiceException(code=409)")
                .doesNotContain("身份证", "110101199001011234");
    }

    private static final class AuditRequest
    {
        private final Long recordId;
        private final String password;
        private final String bankAccount;
        private final String signatureDataUrl;
        private final String token;

        private AuditRequest(Long recordId, String password, String bankAccount, String signatureDataUrl, String token)
        {
            this.recordId = recordId;
            this.password = password;
            this.bankAccount = bankAccount;
            this.signatureDataUrl = signatureDataUrl;
            this.token = token;
        }

        public Long getRecordId()
        {
            return recordId;
        }

        public String getPassword()
        {
            return password;
        }

        public String getBankAccount()
        {
            return bankAccount;
        }

        public String getSignatureDataUrl()
        {
            return signatureDataUrl;
        }

        public String getToken()
        {
            return token;
        }
    }

    private static final class DisguisedRequest
    {
        private final String reference;
        private final String location;

        private DisguisedRequest(String reference, String location)
        {
            this.reference = reference;
            this.location = location;
        }

        public String getReference()
        {
            return reference;
        }

        public String getLocation()
        {
            return location;
        }
    }
}
