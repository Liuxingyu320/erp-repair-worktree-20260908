package com.erp.oa.exception;

import com.erp.oa.domain.vo.OaFixedAssetRepairPrecheckVo;

/** 带稳定业务错误码的固定资产校验异常。 */
public class OaFixedAssetValidationException extends RuntimeException
{
    private static final long serialVersionUID = 1L;

    private final String errorCode;
    private final OaFixedAssetRepairPrecheckVo precheck;

    public OaFixedAssetValidationException(String errorCode, String message)
    {
        this(errorCode, message, null);
    }

    public OaFixedAssetValidationException(String errorCode, String message,
            OaFixedAssetRepairPrecheckVo precheck)
    {
        super(message);
        this.errorCode = errorCode;
        this.precheck = precheck;
    }

    public String getErrorCode() { return errorCode; }
    public OaFixedAssetRepairPrecheckVo getPrecheck() { return precheck; }
}
