package com.erp.gateway.handler;

import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.HandlerFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import com.erp.common.core.exception.CaptchaException;
import com.erp.common.core.web.domain.AjaxResult;
import com.erp.gateway.service.ValidateCodeService;
import reactor.core.publisher.Mono;

/**
 * 验证码获取
 *
 * @author erp
 */
@Component
public class ValidateCodeHandler implements HandlerFunction<ServerResponse>
{
    private static final Logger log = LoggerFactory.getLogger(ValidateCodeHandler.class);

    private final ValidateCodeService validateCodeService;

    public ValidateCodeHandler(ValidateCodeService validateCodeService)
    {
        this.validateCodeService = validateCodeService;
    }

    @Override
    public Mono<ServerResponse> handle(ServerRequest serverRequest)
    {
        AjaxResult ajax;
        try
        {
            ajax = validateCodeService.createCaptcha();
        }
        catch (CaptchaException | IOException e)
        {
            log.error("ERP-NEW_2 验证码生成失败，异常类型: {}", e.getClass().getSimpleName());
            ajax = AjaxResult.error("验证码生成失败");
        }
        return ServerResponse.status(HttpStatus.OK)
                .header(HttpHeaders.CACHE_CONTROL, "no-store, max-age=0")
                .header(HttpHeaders.PRAGMA, "no-cache")
                .body(BodyInserters.fromValue(ajax));
    }
}
