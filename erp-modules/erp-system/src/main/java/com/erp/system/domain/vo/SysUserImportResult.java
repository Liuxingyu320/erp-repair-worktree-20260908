package com.erp.system.domain.vo;

import java.util.ArrayList;
import java.util.List;

/**
 * 用户导入结果，凭据集合只存在于当前响应内。
 */
public class SysUserImportResult
{
    private String message;
    private int successCount;
    private int failureCount;
    private List<SysUserTemporaryCredential> credentials = new ArrayList<>();

    public String getMessage()
    {
        return message;
    }

    public void setMessage(String message)
    {
        this.message = message;
    }

    public int getSuccessCount()
    {
        return successCount;
    }

    public void setSuccessCount(int successCount)
    {
        this.successCount = successCount;
    }

    public int getFailureCount()
    {
        return failureCount;
    }

    public void setFailureCount(int failureCount)
    {
        this.failureCount = failureCount;
    }

    public List<SysUserTemporaryCredential> getCredentials()
    {
        return credentials;
    }

    public void setCredentials(List<SysUserTemporaryCredential> credentials)
    {
        this.credentials = credentials;
    }
}
