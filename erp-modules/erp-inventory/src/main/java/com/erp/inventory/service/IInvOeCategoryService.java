package com.erp.inventory.service;

import java.util.List;
import com.erp.inventory.domain.InvOeCategory;

public interface IInvOeCategoryService
{
    List<InvOeCategory> selectCategoryTree();
    InvOeCategory selectCategoryById(Long categoryId);
    InvOeCategory saveCategory(InvOeCategory category, Long selectedDeptId);
    void deleteCategoryById(Long categoryId, Long selectedDeptId);
}
