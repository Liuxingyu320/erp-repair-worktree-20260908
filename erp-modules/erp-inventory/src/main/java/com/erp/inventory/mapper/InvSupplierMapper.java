package com.erp.inventory.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.erp.inventory.domain.InvSupplier;

public interface InvSupplierMapper
{
    List<InvSupplier> selectInvSupplierList(InvSupplier supplier);
    InvSupplier selectInvSupplierById(Long supplierId);
    InvSupplier selectInvSupplierByIdForUpdate(Long supplierId);
    List<Long> selectReferencingOeIdsForUpdate(@Param("supplierName") String supplierName);
    InvSupplier selectInvSupplierByNameAndShop(@Param("supplierName") String supplierName, @Param("shopDeptId") Long shopDeptId);
    InvSupplier selectActiveSupplierByNameInDeptChain(@Param("supplierName") String supplierName, @Param("deptId") Long deptId);
    int countDuplicateSupplierName(@Param("supplierName") String supplierName,
            @Param("shopDeptId") Long shopDeptId, @Param("excludeSupplierId") Long excludeSupplierId);
    int countDuplicateSupplierCode(@Param("supplierCode") String supplierCode,
            @Param("shopDeptId") Long shopDeptId, @Param("excludeSupplierId") Long excludeSupplierId);
    int countSupplierReferences(@Param("supplierId") Long supplierId,
            @Param("supplierName") String supplierName, @Param("scopeDeptIds") List<Long> scopeDeptIds);
    int insertInvSupplier(InvSupplier supplier);
    int updateInvSupplier(InvSupplier supplier);
    int deleteInvSupplierByIds(Long[] supplierIds);
}
