package com.erp.system.controller;

import org.springframework.web.bind.annotation.RestControllerAdvice;
import com.erp.common.core.web.domain.AjaxResult;
import com.erp.system.exception.HrOnboardingValidationException;

@RestControllerAdvice(basePackages = "com.erp.system.controller")
public class HrOnboardingExceptionHandler
{
    @org.springframework.web.bind.annotation.ExceptionHandler(HrOnboardingValidationException.class)
    public AjaxResult handleValidation(HrOnboardingValidationException failure)
    {
        return AjaxResult.error(failure.getMessage())
                .put("errorCode", failure.getErrorCode())
                .put("fieldErrors", failure.getFieldErrors())
                .put("blockingCodes", failure.getBlockingCodes());
    }
}
