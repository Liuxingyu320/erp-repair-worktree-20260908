package com.erp.system.domain.vo;

import java.util.ArrayList;
import java.util.List;
import com.erp.common.core.domain.todo.TodoItem;

/** In-memory page of grouped system reminders. */
public class SysTodoPage
{
    private final List<TodoItem> rows;
    private final long total;

    public SysTodoPage(List<TodoItem> rows, long total)
    {
        this.rows = rows == null ? new ArrayList<>() : new ArrayList<>(rows);
        this.total = total;
    }

    public List<TodoItem> getRows() { return new ArrayList<>(rows); }
    public long getTotal() { return total; }
}
