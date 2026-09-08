package com.erp.system.service;

import com.erp.common.core.domain.todo.TodoQuery;
import com.erp.common.core.domain.todo.TodoSummary;
import com.erp.system.domain.vo.SysTodoPage;

public interface ISysTodoService
{
    TodoSummary selectSummary(TodoQuery query, Long selectedDeptId);

    SysTodoPage selectTodoPage(TodoQuery query, Long selectedDeptId);
}
