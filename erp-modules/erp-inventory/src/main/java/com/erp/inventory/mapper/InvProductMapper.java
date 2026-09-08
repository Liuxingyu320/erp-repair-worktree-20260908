package com.erp.inventory.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.erp.inventory.domain.InvProduct;

public interface InvProductMapper
{
    List<InvProduct> selectInvProductList(InvProduct product);
    List<InvProduct> selectInvProductListBySupplier(@Param("supplierName") String supplierName, @Param("scopeDeptIds") List<Long> scopeDeptIds);
    InvProduct selectInvProductById(Long productId);
    InvProduct selectInvProductByCodeAndShop(@Param("productCode") String productCode, @Param("shopDeptId") Long shopDeptId);
    InvProduct selectInvProductByNaturalKey(@Param("categoryId") Long categoryId, @Param("productName") String productName, @Param("spec") String spec, @Param("shopDeptId") Long shopDeptId);
    int countBusinessReferenceByProductId(@Param("productId") Long productId);
    int insertInvProduct(InvProduct product);
    int updateInvProduct(InvProduct product);
    int deleteInvProductByIds(Long[] productIds);
}
