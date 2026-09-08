package com.erp.common.core.domain.todo;

import java.io.Serializable;
import java.util.regex.Pattern;

/**
 * Query criteria shared by unified todo providers.
 */
public class TodoQuery implements Serializable
{
    private static final long serialVersionUID = 1L;

    private static final int DEFAULT_PAGE_NUM = 1;
    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 100;
    private static final Pattern TYPE_PATTERN = Pattern.compile("^[A-Z][A-Z0-9_]{0,63}$");

    private String type;
    private String category;
    private String source;
    private String scopeMode = TodoConstants.SCOPE_ACTIONABLE;
    private String keyword;
    private String priority;
    private Integer pageNum = DEFAULT_PAGE_NUM;
    private Integer pageSize = DEFAULT_PAGE_SIZE;

    public String getType()
    {
        return type;
    }

    public void setType(String type)
    {
        String normalized = normalize(type);
        if (normalized != null && !TYPE_PATTERN.matcher(normalized).matches())
        {
            throw new IllegalArgumentException("invalid todo type");
        }
        this.type = normalized;
    }

    public String getCategory()
    {
        return category;
    }

    public void setCategory(String category)
    {
        this.category = normalize(category);
    }

    public String getSource()
    {
        return source;
    }

    public void setSource(String source)
    {
        this.source = normalize(source);
    }

    public String getScopeMode()
    {
        return scopeMode;
    }

    public void setScopeMode(String scopeMode)
    {
        this.scopeMode = normalize(scopeMode);
    }

    public String getKeyword()
    {
        return keyword;
    }

    public void setKeyword(String keyword)
    {
        this.keyword = normalize(keyword);
    }

    public String getPriority()
    {
        return priority;
    }

    public void setPriority(String priority)
    {
        this.priority = normalize(priority);
    }

    public Integer getPageNum()
    {
        return pageNum;
    }

    public void setPageNum(Integer pageNum)
    {
        this.pageNum = pageNum == null ? DEFAULT_PAGE_NUM : Math.max(DEFAULT_PAGE_NUM, pageNum);
    }

    public Integer getPageSize()
    {
        return pageSize;
    }

    public void setPageSize(Integer pageSize)
    {
        this.pageSize = pageSize == null
                ? DEFAULT_PAGE_SIZE
                : Math.max(1, Math.min(MAX_PAGE_SIZE, pageSize));
    }

    private static String normalize(String value)
    {
        if (value == null)
        {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
