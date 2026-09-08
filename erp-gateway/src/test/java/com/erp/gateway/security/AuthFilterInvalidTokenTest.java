package com.erp.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.erp.common.core.constant.SecurityConstants;
import com.erp.common.core.constant.TokenConstants;
import com.erp.gateway.config.properties.IgnoreWhiteProperties;
import com.erp.gateway.config.properties.AuthSessionProperties;
import com.erp.gateway.filter.AuthFilter;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;

class AuthFilterInvalidTokenTest
{
    private String originalActiveProfile;

    @BeforeEach
    void setLocalProfile()
    {
        originalActiveProfile = System.getProperty("spring.profiles.active");
        System.setProperty("spring.profiles.active", "local");
    }

    @AfterEach
    void restoreActiveProfile()
    {
        if (originalActiveProfile == null)
        {
            System.clearProperty("spring.profiles.active");
        }
        else
        {
            System.setProperty("spring.profiles.active", originalActiveProfile);
        }
    }

    @Test
    void invalidJwtReturnsUnauthorizedInsteadOfGatewayError()
    {
        AuthFilter filter = new AuthFilter();
        IgnoreWhiteProperties ignoreWhite = new IgnoreWhiteProperties();
        ReflectionTestUtils.setField(filter, "ignoreWhite", ignoreWhite);
        ReflectionTestUtils.setField(filter, "sessionProperties", new AuthSessionProperties());

        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/system/user/getInfo")
                .header(SecurityConstants.AUTHORIZATION_HEADER, TokenConstants.PREFIX + "bad-token"));

        Mono<Void> result = filter.filter(exchange, next -> Mono.error(new AssertionError("request should stop at auth")));

        result.block(Duration.ofSeconds(1));
        String responseBody = exchange.getResponse().getBodyAsString().block();
        assertThat(exchange.getResponse().getStatusCode())
                .isEqualTo(org.springframework.http.HttpStatus.UNAUTHORIZED);
        assertThat(responseBody).contains("\"code\":401");
        assertThat(responseBody).contains("令牌已过期或验证不正确");
    }
}
