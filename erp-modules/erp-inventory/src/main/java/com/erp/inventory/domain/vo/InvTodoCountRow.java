package com.erp.inventory.domain.vo;

import java.io.Serializable;

/**
 * Aggregate count for one inventory todo type, category, and priority.
 */
public class InvTodoCountRow implements Serializable
{
    private static final long serialVersionUID = 1L;

    private String type;
    private String category;
    private String priority;
    private long count;

    public String getType()
    {
        return type;
    }

    public void setType(String type)
    {
        this.type = type;
    }

    public String getCategory()
    {
        return category;
    }

    public void setCategory(String category)
    {
        this.category = category;
    }

    public String getPriority()
    {
        return priority;
    }

    public void setPriority(String priority)
    {
        this.priority = priority;
    }

    public long getCount()
    {
        return count;
    }

    public void setCount(long count)
    {
        this.count = count;
    }
}
