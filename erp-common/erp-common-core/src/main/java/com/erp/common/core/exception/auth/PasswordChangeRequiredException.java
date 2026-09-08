package com.erp.common.core.exception.auth;

/**
 * 当前账号必须先修改一次性密码。
 */
public class PasswordChangeRequiredException extends RuntimeException
{
    private static final long serialVersionUID = 1L;

    public PasswordChangeRequiredException()
    {
        super("当前账号必须先修改密码");
    }
}
