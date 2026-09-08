package com.erp.oa.service;

import java.util.List;
import com.erp.common.core.domain.todo.TodoItem;
import com.erp.common.core.domain.todo.TodoQuery;
import com.erp.common.core.domain.todo.TodoSummary;

public interface IOaTodoService
{
    TodoSummary selectSummary(TodoQuery query, Long selectedDeptId);

    List<TodoItem> selectTodoList(TodoQuery query, Long selectedDeptId);
}
