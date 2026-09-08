package com.erp.system.exception;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class HrOnboardingValidationException extends RuntimeException
{
    private static final long serialVersionUID = 1L;
    private final String errorCode;
    private final Map<String, String> fieldErrors;
    private final List<String> blockingCodes;

    public HrOnboardingValidationException(String errorCode, String message)
    {
        this(errorCode, message, Collections.emptyMap(), Collections.emptyList());
    }

    public HrOnboardingValidationException(String errorCode, String message,
            Map<String, String> fieldErrors, List<String> blockingCodes)
    {
        super(message);
        this.errorCode = errorCode;
        this.fieldErrors = fieldErrors == null ? Collections.emptyMap() : fieldErrors;
        this.blockingCodes = blockingCodes == null ? Collections.emptyList() : blockingCodes;
    }

    public String getErrorCode() { return errorCode; }
    public Map<String, String> getFieldErrors() { return fieldErrors; }
    public List<String> getBlockingCodes() { return blockingCodes; }
}
