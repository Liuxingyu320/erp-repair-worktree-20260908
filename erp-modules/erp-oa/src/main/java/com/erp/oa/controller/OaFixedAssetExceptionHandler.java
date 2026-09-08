package com.erp.oa.controller;

import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import com.erp.common.core.web.domain.AjaxResult;
import com.erp.oa.exception.OaFixedAssetValidationException;

@RestControllerAdvice(basePackages = "com.erp.oa.controller")
public class OaFixedAssetExceptionHandler
{
    @ExceptionHandler(OaFixedAssetValidationException.class)
    public AjaxResult handleFixedAssetValidation(
            OaFixedAssetValidationException failure)
    {
        return AjaxResult.error(failure.getMessage())
                .put("errorCode", failure.getErrorCode())
                .put("precheck", failure.getPrecheck());
    }
}
