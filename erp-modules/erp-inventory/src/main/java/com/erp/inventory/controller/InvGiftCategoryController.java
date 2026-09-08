package com.erp.inventory.controller;

import java.util.List;
import jakarta.servlet.http.HttpServletRequest;
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
import com.erp.inventory.domain.InvGiftCategory;
import com.erp.inventory.service.IInvGiftCategoryService;

@RestController
@RequestMapping("/gift/category")
public class InvGiftCategoryController extends InvBaseController
{
    @Autowired
    private IInvGiftCategoryService categoryService;

    @RequiresPermissions(value = { "inv:giftCategory:list", "inv:giftCategory:tree",
            "inv:transfer:list", "inv:transfer:add" }, logical = Logical.OR)
    @GetMapping("/tree")
    public AjaxResult tree()
    {
        List<InvGiftCategory> list = categoryService.selectCategoryTree();
        return success(list);
    }

    @RequiresPermissions("inv:giftCategory:query")
    @GetMapping("/{categoryId}")
    public AjaxResult getInfo(@PathVariable("categoryId") Long categoryId)
    {
        return success(categoryService.selectCategoryById(categoryId));
    }

    @RequiresPermissions("inv:giftCategory:add")
    @IdempotentSubmit(timeout = 30)
    @Log(title = "礼盒分类", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Validated @RequestBody InvGiftCategory category, HttpServletRequest request)
    {
        return success(categoryService.saveCategory(category, resolveShopDeptId(request)));
    }

    @RequiresPermissions("inv:giftCategory:edit")
    @IdempotentSubmit(timeout = 30)
    @Log(title = "礼盒分类", businessType = BusinessType.UPDATE)
    @PostMapping("/update")
    public AjaxResult edit(@Validated @RequestBody InvGiftCategory category, HttpServletRequest request)
    {
        return success(categoryService.saveCategory(category, resolveShopDeptId(request)));
    }

    @RequiresPermissions("inv:giftCategory:remove")
    @IdempotentSubmit(timeout = 30)
    @Log(title = "礼盒分类", businessType = BusinessType.DELETE)
    @DeleteMapping("/{categoryId}")
    public AjaxResult remove(@PathVariable Long categoryId, HttpServletRequest request)
    {
        categoryService.deleteCategoryById(categoryId, resolveShopDeptId(request));
        return success();
    }
}
