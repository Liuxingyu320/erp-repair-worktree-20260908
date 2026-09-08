package com.erp.oa.controller;

import java.util.List;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.erp.common.core.domain.todo.TodoItem;
import com.erp.common.core.domain.todo.TodoQuery;
import com.erp.common.core.web.domain.AjaxResult;
import com.erp.common.core.web.page.TableDataInfo;
import com.erp.common.security.annotation.RequiresLogin;
import com.erp.oa.service.IOaTodoService;

@RestController
@RequestMapping("/todo")
public class OaTodoController extends OaBaseController
{
    @Autowired
    private IOaTodoService todoService;

    @RequiresLogin
    @GetMapping("/summary")
    public AjaxResult summary(TodoQuery query, HttpServletRequest request, HttpServletResponse response)
    {
        disableCaching(response);
        return success(todoService.selectSummary(query, resolveShopDeptId(request)));
    }

    @RequiresLogin
    @GetMapping("/list")
    public TableDataInfo list(TodoQuery query, HttpServletRequest request, HttpServletResponse response)
    {
        disableCaching(response);
        List<TodoItem> rows = todoService.selectTodoList(query, resolveShopDeptId(request));
        return getDataTable(rows);
    }

    private static void disableCaching(HttpServletResponse response)
    {
        response.setHeader("Cache-Control", "no-store, max-age=0");
        response.setHeader("Pragma", "no-cache");
    }
}
