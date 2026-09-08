package com.erp.inventory.service;

import java.util.List;
import com.erp.inventory.domain.InvGiftCategory;

public interface IInvGiftCategoryService
{
    List<InvGiftCategory> selectCategoryTree();
    InvGiftCategory selectCategoryById(Long categoryId);
    InvGiftCategory saveCategory(InvGiftCategory category, Long selectedDeptId);
    void deleteCategoryById(Long categoryId, Long selectedDeptId);
}
