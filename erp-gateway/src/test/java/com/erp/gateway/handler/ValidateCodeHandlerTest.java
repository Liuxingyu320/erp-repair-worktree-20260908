package com.erp.gateway.handler;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import com.erp.common.core.web.domain.AjaxResult;
import com.erp.gateway.config.RouterFunctionConfiguration;
import com.erp.gateway.service.ValidateCodeService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

class ValidateCodeHandlerTest
{
    @Test
    void jsonClientReceivesANonCacheableCaptchaContract() throws Exception
    {
        ValidateCodeService service = mock(ValidateCodeService.class);
        AjaxResult result = AjaxResult.success();
        result.put("captchaEnabled", true);
        result.put("uuid", "0123456789abcdef0123456789abcdef");
        result.put("img", "ZmFrZS1qcGVn");
        when(service.createCaptcha()).thenReturn(result);

        ValidateCodeHandler handler = new ValidateCodeHandler(service);
        WebTestClient client = WebTestClient.bindToRouterFunction(
                new RouterFunctionConfiguration(handler).routerFunction())
                .build();

        client.get()
                .uri("/code")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("Cache-Control", "no-store, max-age=0")
                .expectHeader().valueEquals("Pragma", "no-cache")
                .expectBody()
                .jsonPath("$.code").isEqualTo(200)
                .jsonPath("$.captchaEnabled").isEqualTo(true)
                .jsonPath("$.uuid").isEqualTo("0123456789abcdef0123456789abcdef")
                .jsonPath("$.img").isEqualTo("ZmFrZS1qcGVn");
    }

    @Test
    void captchaGenerationFailureIsGenericAndNonCacheable() throws Exception
    {
        ValidateCodeService service = mock(ValidateCodeService.class);
        when(service.createCaptcha()).thenThrow(new IOException("sensitive image failure"));
        ValidateCodeHandler handler = new ValidateCodeHandler(service);
        WebTestClient client = WebTestClient.bindToRouterFunction(
                new RouterFunctionConfiguration(handler).routerFunction())
                .build();

        client.get()
                .uri("/code")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("Cache-Control", "no-store, max-age=0")
                .expectHeader().valueEquals("Pragma", "no-cache")
                .expectBody()
                .jsonPath("$.code").isEqualTo(500)
                .jsonPath("$.msg").isEqualTo("验证码生成失败")
                .jsonPath("$.msg").value(message -> {
                    if (String.valueOf(message).contains("sensitive"))
                    {
                        throw new AssertionError("internal failure details must not reach clients");
                    }
                });
    }
}
