package com.erp.system.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.erp.common.core.constant.HttpStatus;
import com.erp.common.core.domain.todo.TodoQuery;
import com.erp.common.core.utils.ShopHeaderUtils;
import com.erp.common.core.web.controller.BaseController;
import com.erp.common.core.web.domain.AjaxResult;
import com.erp.common.core.web.page.TableDataInfo;
import com.erp.common.security.annotation.RequiresLogin;
import com.erp.system.domain.vo.SysTodoPage;
import com.erp.system.service.ISysTodoService;

@RestController
@RequestMapping("/todo")
public class SysTodoController extends BaseController
{
    @Autowired
    private ISysTodoService todoService;

    @RequiresLogin
    @GetMapping("/summary")
    public AjaxResult summary(TodoQuery query, HttpServletRequest request, HttpServletResponse response)
    {
        disableCaching(response);
        return success(todoService.selectSummary(query, ShopHeaderUtils.resolveShopDeptId(request)));
    }

    @RequiresLogin
    @GetMapping("/list")
    public TableDataInfo list(TodoQuery query, HttpServletRequest request, HttpServletResponse response)
    {
        disableCaching(response);
        SysTodoPage page = todoService.selectTodoPage(query, ShopHeaderUtils.resolveShopDeptId(request));
        TableDataInfo result = new TableDataInfo(page.getRows(), page.getTotal());
        result.setCode(HttpStatus.SUCCESS);
        result.setMsg("查询成功");
        return result;
    }

    private static void disableCaching(HttpServletResponse response)
    {
        response.setHeader("Cache-Control", "no-store, max-age=0");
        response.setHeader("Pragma", "no-cache");
    }
}
