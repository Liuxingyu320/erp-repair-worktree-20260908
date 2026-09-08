package com.erp.approval.controller;

import java.util.List;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.erp.approval.service.ApprovalTodoService;
import com.erp.common.core.domain.todo.TodoItem;
import com.erp.common.core.domain.todo.TodoQuery;
import com.erp.common.core.web.controller.BaseController;
import com.erp.common.core.web.domain.AjaxResult;
import com.erp.common.core.web.page.TableDataInfo;
import com.erp.common.security.annotation.RequiresLogin;
import com.erp.common.security.utils.SecurityUtils;

@RestController
@RequestMapping("/todo")
public class ApprovalTodoController extends BaseController
{
    private final ApprovalTodoService todoService;
    public ApprovalTodoController(ApprovalTodoService todoService)
    {
        this.todoService = todoService;
    }

    @RequiresLogin
    @GetMapping("/summary")
    public AjaxResult summary(TodoQuery query, HttpServletResponse response)
    {
        disableCaching(response);
        return success(todoService.summary(query, SecurityUtils.getUserId()));
    }

    @RequiresLogin
    @GetMapping({"", "/list"})
    public TableDataInfo list(TodoQuery query, HttpServletResponse response)
    {
        disableCaching(response);
        startPage();
        List<TodoItem> rows = todoService.list(query, SecurityUtils.getUserId());
        return getDataTable(rows);
    }

    private static void disableCaching(HttpServletResponse response)
    {
        response.setHeader("Cache-Control", "no-store, max-age=0");
        response.setHeader("Pragma", "no-cache");
    }
}
