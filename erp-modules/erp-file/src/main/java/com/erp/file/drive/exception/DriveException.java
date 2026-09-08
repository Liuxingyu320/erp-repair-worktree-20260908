package com.erp.file.drive.exception;

/**
 * 只携带稳定错误码和用户安全消息的云盘业务异常。
 */
public class DriveException extends RuntimeException
{
    private static final long serialVersionUID = 1L;

    private final String businessCode;

    public DriveException(String businessCode, String message)
    {
        super(message);
        if (businessCode == null || businessCode.isBlank())
        {
            throw new IllegalArgumentException("businessCode must not be blank");
        }
        this.businessCode = businessCode;
    }

    public String getBusinessCode()
    {
        return businessCode;
    }
}
