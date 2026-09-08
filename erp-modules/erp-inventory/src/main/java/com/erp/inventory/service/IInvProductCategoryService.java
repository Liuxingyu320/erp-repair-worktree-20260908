package com.erp.inventory.service;

import java.util.List;
import com.erp.inventory.domain.InvProductCategory;

public interface IInvProductCategoryService
{
    List<InvProductCategory> selectCategoryTree(Long selectedShopDeptId);
    InvProductCategory selectCategoryById(Long categoryId, Long selectedShopDeptId);
    InvProductCategory saveCategory(InvProductCategory category, Long selectedShopDeptId);
    void deleteCategoryById(Long categoryId, Long selectedShopDeptId);
}
