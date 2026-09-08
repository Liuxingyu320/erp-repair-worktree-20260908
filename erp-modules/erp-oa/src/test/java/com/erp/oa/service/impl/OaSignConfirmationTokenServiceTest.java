package com.erp.oa.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.erp.common.core.exception.ServiceException;

@DisplayName("签约任务确认令牌")
class OaSignConfirmationTokenServiceTest
{
    private String originalSecret;

    @BeforeEach
    void setUp()
    {
        originalSecret = System.getProperty("erp.jwt.secret");
        System.setProperty("erp.jwt.secret", "sign-confirmation-token-test-secret-20260712");
    }

    @AfterEach
    void tearDown()
    {
        if (originalSecret == null)
        {
            System.clearProperty("erp.jwt.secret");
        }
        else
        {
            System.setProperty("erp.jwt.secret", originalSecret);
        }
    }

    @Test
    @DisplayName("令牌绑定任务、HR、文档版本和当前快照但不包含原摘要")
    void shouldBindConfirmationContextWithoutExposingRawSnapshot()
    {
        OaSignConfirmationTokenService service = new OaSignConfirmationTokenService(Clock.systemUTC());
        String snapshotHash = "a".repeat(64);

        String token = service.issue(9L, 101L, "SP-90-V1", snapshotHash);

        assertThat(token).doesNotContain(snapshotHash);
        service.verify(token, 9L, 101L, "SP-90-V1", snapshotHash);
        assertThatThrownBy(() -> service.verify(token, 9L, 101L, "SP-90-V1", "b".repeat(64)))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("快照已变化");
        assertThatThrownBy(() -> service.verify(token, 9L, 102L, "SP-90-V1", snapshotHash))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("确认令牌");
        assertThatThrownBy(() -> service.verify(token, 9L, 101L, "SP-90-V2", snapshotHash))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("确认令牌");
    }

    @Test
    @DisplayName("篡改或过期的确认令牌必须拒绝")
    void shouldRejectTamperedOrExpiredToken()
    {
        OaSignConfirmationTokenService current = new OaSignConfirmationTokenService(Clock.systemUTC());
        String snapshotHash = "c".repeat(64);
        String token = current.issue(9L, 101L, "SP-90-V1", snapshotHash);
        int signatureStart = token.lastIndexOf('.') + 1;
        char replacement = token.charAt(signatureStart) == 'a' ? 'b' : 'a';
        String tampered = token.substring(0, signatureStart) + replacement
                + token.substring(signatureStart + 1);

        assertThatThrownBy(() -> current.verify(tampered, 9L, 101L, "SP-90-V1", snapshotHash))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("无效或已过期");

        Clock expiredClock = Clock.fixed(Instant.now().minusSeconds(11 * 60), ZoneOffset.UTC);
        String expired = new OaSignConfirmationTokenService(expiredClock)
                .issue(9L, 101L, "SP-90-V1", snapshotHash);
        assertThatThrownBy(() -> current.verify(expired, 9L, 101L, "SP-90-V1", snapshotHash))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("无效或已过期");
    }
}
