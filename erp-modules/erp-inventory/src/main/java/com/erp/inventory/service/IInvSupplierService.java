package com.erp.inventory.service;

import java.util.List;
import com.erp.inventory.domain.InvProduct;
import com.erp.inventory.domain.InvSupplier;

public interface IInvSupplierService
{
    List<InvSupplier> selectSupplierList(InvSupplier supplier, Long selectedShopDeptId);
    InvSupplier selectSupplierById(Long supplierId, Long selectedShopDeptId);
    List<InvProduct> selectSupplierProductList(Long supplierId, Long selectedShopDeptId);
    InvSupplier saveSupplier(InvSupplier supplier, Long selectedShopDeptId);
    void deleteSupplierByIds(Long[] supplierIds, Long selectedShopDeptId);
}
