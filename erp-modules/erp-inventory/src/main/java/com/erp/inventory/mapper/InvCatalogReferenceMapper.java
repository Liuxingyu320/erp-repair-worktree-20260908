package com.erp.inventory.mapper;

import org.apache.ibatis.annotations.Param;

public interface InvCatalogReferenceMapper
{
    String selectStatusForUpdate(@Param("itemType") String itemType, @Param("itemId") Long itemId);
    long countReferences(@Param("itemType") String itemType, @Param("itemId") Long itemId);
    int deleteUnreferenced(@Param("itemType") String itemType, @Param("itemId") Long itemId);
}
