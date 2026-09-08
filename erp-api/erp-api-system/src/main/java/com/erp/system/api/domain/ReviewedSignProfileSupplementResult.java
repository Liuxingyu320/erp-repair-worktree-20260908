package com.erp.system.api.domain;

/**
 * Privacy-safe result of applying one HR-reviewed signing profile supplement.
 */
public class ReviewedSignProfileSupplementResult
{
    private boolean applied;

    private boolean replayed;

    private String beforeHash;

    private String afterHash;

    private String fieldMask;

    public ReviewedSignProfileSupplementResult()
    {
    }

    public ReviewedSignProfileSupplementResult(boolean applied, boolean replayed,
            String beforeHash, String afterHash, String fieldMask)
    {
        this.applied = applied;
        this.replayed = replayed;
        this.beforeHash = beforeHash;
        this.afterHash = afterHash;
        this.fieldMask = fieldMask;
    }

    public boolean isApplied()
    {
        return applied;
    }

    public void setApplied(boolean applied)
    {
        this.applied = applied;
    }

    public boolean isReplayed()
    {
        return replayed;
    }

    public void setReplayed(boolean replayed)
    {
        this.replayed = replayed;
    }

    public String getBeforeHash()
    {
        return beforeHash;
    }

    public void setBeforeHash(String beforeHash)
    {
        this.beforeHash = beforeHash;
    }

    public String getAfterHash()
    {
        return afterHash;
    }

    public void setAfterHash(String afterHash)
    {
        this.afterHash = afterHash;
    }

    public String getFieldMask()
    {
        return fieldMask;
    }

    public void setFieldMask(String fieldMask)
    {
        this.fieldMask = fieldMask;
    }
}
