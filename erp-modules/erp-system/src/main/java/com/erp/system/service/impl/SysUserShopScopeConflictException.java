package com.erp.system.service.impl;

/** Raised when organization authorization changed after the operator previewed it. */
public class SysUserShopScopeConflictException extends RuntimeException
{
    private static final long serialVersionUID = 1L;

    public SysUserShopScopeConflictException()
    {
        super("组织授权已被其他管理员修改，请重新加载并预览");
    }
}
