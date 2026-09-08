package com.erp.system.service.impl;

/**
 * 公告状态机可安全回显的业务异常。
 */
public class SysNoticeWorkflowException extends RuntimeException
{
    private static final long serialVersionUID = 1L;

    private final int code;
    private final String businessCode;

    public SysNoticeWorkflowException(int code, String businessCode, String message)
    {
        super(message);
        this.code = code;
        this.businessCode = businessCode;
    }

    public int getCode() { return code; }
    public String getBusinessCode() { return businessCode; }
}
