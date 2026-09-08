package com.erp.system.domain.vo;

import java.util.ArrayList;
import java.util.List;

/** Structured import result; all strings must be rendered as text by clients. */
public class SysUserImportResultVo
{
    private int totalCount;

    private int createdCount;

    private int updatedCount;

    private int failureCount;

    private boolean committed;

    private final List<SysTemporaryCredentialVo> temporaryCredentials = new ArrayList<>();

    private final List<Failure> failures = new ArrayList<>();

    public int getTotalCount()
    {
        return totalCount;
    }

    public void setTotalCount(int totalCount)
    {
        this.totalCount = totalCount;
    }

    public int getCreatedCount()
    {
        return createdCount;
    }

    public void incrementCreatedCount()
    {
        this.createdCount++;
    }

    public int getUpdatedCount()
    {
        return updatedCount;
    }

    public void incrementUpdatedCount()
    {
        this.updatedCount++;
    }

    public int getFailureCount()
    {
        return failureCount;
    }

    public boolean isCommitted()
    {
        return committed;
    }

    public void markCommitted()
    {
        this.committed = true;
    }

    public void markRolledBack()
    {
        this.committed = false;
        this.createdCount = 0;
        this.updatedCount = 0;
        this.temporaryCredentials.clear();
    }

    public List<SysTemporaryCredentialVo> getTemporaryCredentials()
    {
        return temporaryCredentials;
    }

    public List<Failure> getFailures()
    {
        return failures;
    }

    public void addTemporaryCredential(SysTemporaryCredentialVo credential)
    {
        temporaryCredentials.add(credential);
    }

    public void addFailure(int rowNumber, String userName, String message)
    {
        failureCount++;
        failures.add(new Failure(rowNumber, userName, message));
    }

    public static class Failure
    {
        private final int rowNumber;

        private final String userName;

        private final String message;

        public Failure(int rowNumber, String userName, String message)
        {
            this.rowNumber = rowNumber;
            this.userName = userName;
            this.message = message;
        }

        public int getRowNumber()
        {
            return rowNumber;
        }

        public String getUserName()
        {
            return userName;
        }

        public String getMessage()
        {
            return message;
        }
    }
}
