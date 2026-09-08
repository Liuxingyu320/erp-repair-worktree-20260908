package com.erp.inventory.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.erp.inventory.domain.InvOeItem;

public interface InvOeMapper
{
    List<InvOeItem> selectInvOeList(InvOeItem item);
    InvOeItem selectInvOeById(Long oeItemId);
    InvOeItem selectInvOeByCode(@Param("oeItemCode") String oeItemCode);
    InvOeItem selectInvOeByNaturalKey(@Param("categoryId") Long categoryId,
                                      @Param("oeTypeName") String oeTypeName,
                                      @Param("oeItemName") String oeItemName,
                                      @Param("itemDescription") String itemDescription);
    int countOeCode(@Param("oeItemCode") String oeItemCode, @Param("excludeOeItemId") Long excludeOeItemId);
    int countFixedAssetConfigByOeItemId(@Param("oeItemId") Long oeItemId);
    int countActiveFixedAssetConfigByOeItemId(@Param("oeItemId") Long oeItemId);
    int insertInvOe(InvOeItem item);
    int updateInvOe(InvOeItem item);
    int deleteInvOeByIds(Long[] oeItemIds);
}
