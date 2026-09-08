package com.erp.approval.domain.vo;

import java.io.Serializable;

/** Aggregated todo count returned directly by the database. */
public class ApprovalTodoCountRow implements Serializable
{
    private static final long serialVersionUID = 1L;

    private String todoType;
    private Long todoCount;

    public String getTodoType()
    {
        return todoType;
    }

    public void setTodoType(String todoType)
    {
        this.todoType = todoType;
    }

    public Long getTodoCount()
    {
        return todoCount;
    }

    public void setTodoCount(Long todoCount)
    {
        this.todoCount = todoCount;
    }
}
