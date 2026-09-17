package com.erp.inventory.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import com.erp.common.core.web.domain.AjaxResult;

/** Binding failures happen before the draft controller and its business transaction run. */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = {InvPurchaseController.class,
        InvPurchaseReturnController.class, InvSalesReturnController.class})
public class InventoryDraftValidationAdvice
{
    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class,
            HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class, MissingRequestHeaderException.class})
    public AjaxResult rejected(Exception error, HttpServletRequest request)
    {
        String message = "请求内容格式不正确，请检查必填项、日期和数量后重试";
        if (error instanceof BindException binding && binding.getBindingResult().hasErrors())
        {
            message = binding.getBindingResult().getAllErrors().get(0).getDefaultMessage();
        }
        AjaxResult result = AjaxResult.error(message);
        String path = request.getRequestURI();
        if ("POST".equals(request.getMethod()) && path.matches("(?:.*/)?(?:purchase|purchaseReturn|salesReturn)/(?:save|submit)"))
        {
            result.put("draftOutcome", "REJECTED");
        }
        return result;
    }
}
