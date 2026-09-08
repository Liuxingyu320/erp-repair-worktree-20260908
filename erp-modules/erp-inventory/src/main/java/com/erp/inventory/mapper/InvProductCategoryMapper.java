package com.erp.inventory.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.erp.inventory.domain.InvProductCategory;

public interface InvProductCategoryMapper
{
    List<InvProductCategory> selectInvProductCategoryList(InvProductCategory category);
    InvProductCategory selectInvProductCategoryById(Long categoryId);
    int countCategoryCodeByShop(@Param("categoryCode") String categoryCode, @Param("shopDeptId") Long shopDeptId, @Param("excludeCategoryId") Long excludeCategoryId);
    int countChildCategory(@Param("categoryId") Long categoryId);
    int countProductByCategoryId(@Param("categoryId") Long categoryId);
    int insertInvProductCategory(InvProductCategory category);
    int updateInvProductCategory(InvProductCategory category);
    int deleteInvProductCategoryById(Long categoryId);
}
