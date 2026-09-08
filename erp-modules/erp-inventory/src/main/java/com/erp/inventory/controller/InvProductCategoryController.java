package com.erp.inventory.controller;

import java.util.List;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.erp.common.core.web.domain.AjaxResult;
import com.erp.common.log.annotation.Log;
import com.erp.common.log.enums.BusinessType;
import com.erp.common.security.annotation.IdempotentSubmit;
import com.erp.common.security.annotation.Logical;
import com.erp.common.security.annotation.RequiresPermissions;
import com.erp.inventory.domain.InvProductCategory;
import com.erp.inventory.service.IInvProductCategoryService;

@RestController
@RequestMapping("/category")
public class InvProductCategoryController extends InvBaseController
{
    @Autowired
    private IInvProductCategoryService categoryService;

    @RequiresPermissions(value = { "inv:category:list", "inv:category:tree" }, logical = Logical.OR)
    @GetMapping("/tree")
    public AjaxResult tree(HttpServletRequest request, HttpServletResponse response)
    {
        disableCaching(response);
        List<InvProductCategory> list = categoryService.selectCategoryTree(resolveShopDeptId(request));
        return success(list);
    }

    @RequiresPermissions("inv:category:query")
    @GetMapping("/{categoryId}")
    public AjaxResult getInfo(@PathVariable("categoryId") Long categoryId, HttpServletRequest request,
            HttpServletResponse response)
    {
        disableCaching(response);
        return success(categoryService.selectCategoryById(categoryId, resolveShopDeptId(request)));
    }

    @RequiresPermissions("inv:category:add")
    @IdempotentSubmit(timeout = 30)
    @Log(title = "分类管理", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Validated @RequestBody InvProductCategory category, HttpServletRequest request,
            HttpServletResponse response)
    {
        disableCaching(response);
        return success(categoryService.saveCategory(category, resolveShopDeptId(request)));
    }

    @RequiresPermissions("inv:category:edit")
    @IdempotentSubmit(timeout = 30)
    @Log(title = "分类管理", businessType = BusinessType.UPDATE)
    @PostMapping("/update")
    public AjaxResult edit(@Validated @RequestBody InvProductCategory category, HttpServletRequest request,
            HttpServletResponse response)
    {
        disableCaching(response);
        return success(categoryService.saveCategory(category, resolveShopDeptId(request)));
    }

    @RequiresPermissions("inv:category:remove")
    @IdempotentSubmit(timeout = 30)
    @Log(title = "分类管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{categoryId}")
    public AjaxResult remove(@PathVariable Long categoryId, HttpServletRequest request,
            HttpServletResponse response)
    {
        disableCaching(response);
        categoryService.deleteCategoryById(categoryId, resolveShopDeptId(request));
        return success();
    }

    private void disableCaching(HttpServletResponse response)
    {
        response.setHeader("Cache-Control", "no-store, max-age=0");
        response.setHeader("Pragma", "no-cache");
    }
}
