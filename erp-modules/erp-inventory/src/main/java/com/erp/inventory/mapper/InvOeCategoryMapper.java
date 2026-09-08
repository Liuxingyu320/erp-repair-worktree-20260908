package com.erp.inventory.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.erp.inventory.domain.InvOeCategory;

public interface InvOeCategoryMapper
{
    List<InvOeCategory> selectInvOeCategoryList(InvOeCategory category);
    InvOeCategory selectInvOeCategoryById(Long categoryId);
    int countCategoryCode(@Param("categoryCode") String categoryCode, @Param("excludeCategoryId") Long excludeCategoryId);
    int countChildCategory(@Param("categoryId") Long categoryId);
    int countOeByCategoryId(@Param("categoryId") Long categoryId);
    int insertInvOeCategory(InvOeCategory category);
    int updateInvOeCategory(InvOeCategory category);
    int deleteInvOeCategoryById(Long categoryId);
}
