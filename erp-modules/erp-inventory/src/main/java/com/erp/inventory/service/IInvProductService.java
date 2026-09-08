package com.erp.inventory.service;

import java.util.List;
import com.erp.inventory.domain.InvProduct;

public interface IInvProductService
{
    List<InvProduct> selectProductList(InvProduct product, Long selectedShopDeptId);
    InvProduct selectProductById(Long productId, Long selectedShopDeptId);
    InvProduct saveProduct(InvProduct product, Long selectedShopDeptId);
    void deleteProductByIds(Long[] productIds, Long selectedShopDeptId);
    String importProduct(List<InvProduct> productList, boolean updateSupport, Long selectedShopDeptId);
}
