package com.erp.inventory.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.erp.inventory.domain.InvGiftCategory;

public interface InvGiftCategoryMapper
{
    List<InvGiftCategory> selectInvGiftCategoryList(InvGiftCategory category);
    InvGiftCategory selectInvGiftCategoryById(Long categoryId);
    int countCategoryCode(@Param("categoryCode") String categoryCode, @Param("excludeCategoryId") Long excludeCategoryId);
    int countChildCategory(@Param("categoryId") Long categoryId);
    int countGiftByCategoryId(@Param("categoryId") Long categoryId);
    int insertInvGiftCategory(InvGiftCategory category);
    int updateInvGiftCategory(InvGiftCategory category);
    int deleteInvGiftCategoryById(Long categoryId);
}
