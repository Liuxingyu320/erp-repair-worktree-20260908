package com.erp.inventory.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.security.utils.SecurityUtils;
import com.erp.inventory.domain.InvProduct;
import com.erp.inventory.domain.InvSupplier;
import com.erp.inventory.mapper.InvProductMapper;
import com.erp.inventory.mapper.InvSupplierMapper;
import com.erp.inventory.service.IInvSupplierService;

@Service
public class InvSupplierServiceImpl extends InvBaseService implements IInvSupplierService
{
    @Autowired
    private InvSupplierMapper supplierMapper;

    @Autowired
    private InvProductMapper productMapper;

    @Override
    public List<InvSupplier> selectSupplierList(InvSupplier supplier, Long selectedShopDeptId)
    {
        if (supplier == null)
        {
            throw new ServiceException("供应商查询条件不能为空");
        }
        supplier.getParams().put("scopeDeptIds", resolveRequiredScopeDeptIds(selectedShopDeptId));
        return supplierMapper.selectInvSupplierList(supplier);
    }

    @Override
    public InvSupplier selectSupplierById(Long supplierId, Long selectedShopDeptId)
    {
        return assertAndGetScopedSupplier(supplierId, selectedShopDeptId);
    }

    @Override
    public List<InvProduct> selectSupplierProductList(Long supplierId, Long selectedShopDeptId)
    {
        InvSupplier supplier = assertAndGetScopedSupplier(supplierId, selectedShopDeptId);
        List<Long> scopeDeptIds = resolveRequiredScopeDeptIds(selectedShopDeptId);
        return productMapper.selectInvProductListBySupplier(supplier.getSupplierName(), scopeDeptIds);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InvSupplier saveSupplier(InvSupplier supplier, Long selectedShopDeptId)
    {
        if (supplier == null)
        {
            throw new ServiceException("供应商资料不能为空");
        }
        normalizeAndValidateSupplier(supplier);
        Long shopDeptId = resolveAndValidateShopDept(selectedShopDeptId);
        if (supplier.getSupplierId() == null)
        {
            applyCreateDefaults(supplier);
            assertUniqueSupplier(supplier, shopDeptId, null);
            supplier.setShopDeptId(shopDeptId);
            supplier.setCreateBy(SecurityUtils.getUsername());
            if (supplierMapper.insertInvSupplier(supplier) <= 0)
            {
                throw new ServiceException("供应商新增失败");
            }
        }
        else
        {
            InvSupplier current = assertAndGetScopedSupplier(supplier.getSupplierId(), selectedShopDeptId);
            if (!current.getSupplierName().equals(supplier.getSupplierName())
                    && countReferences(current, selectedShopDeptId) > 0)
            {
                throw new ServiceException("供应商已被商品或采购历史引用，不能修改名称，请停用供应商或先治理引用");
            }
            assertUniqueSupplier(supplier, current.getShopDeptId(), supplier.getSupplierId());
            supplier.setShopDeptId(current.getShopDeptId());
            supplier.setUpdateBy(SecurityUtils.getUsername());
            if (supplierMapper.updateInvSupplier(supplier) <= 0)
            {
                throw new ServiceException("供应商更新失败");
            }
        }
        InvSupplier saved = supplierMapper.selectInvSupplierById(supplier.getSupplierId());
        if (saved == null)
        {
            throw new ServiceException("供应商保存结果不存在");
        }
        return saved;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteSupplierByIds(Long[] supplierIds, Long selectedShopDeptId)
    {
        if (supplierIds == null || supplierIds.length == 0)
        {
            throw new ServiceException("请选择要删除的供应商");
        }
        for (Long supplierId : supplierIds)
        {
            if (supplierId == null || supplierId <= 0)
            {
                throw new ServiceException("供应商标识无效");
            }
            InvSupplier supplier = assertAndGetScopedSupplier(supplierId, selectedShopDeptId);
            if (countReferences(supplier, selectedShopDeptId) > 0)
            {
                throw new ServiceException("供应商已被商品或采购历史引用，不能删除，请停用供应商");
            }
        }
        supplierMapper.deleteInvSupplierByIds(supplierIds);
    }

    private InvSupplier assertAndGetScopedSupplier(Long supplierId, Long selectedShopDeptId)
    {
        InvSupplier db = supplierMapper.selectInvSupplierById(supplierId);
        if (db == null)
        {
            throw new ServiceException("供应商不存在");
        }
        List<Long> scopeDeptIds = resolveRequiredScopeDeptIds(selectedShopDeptId);
        if (!scopeDeptIds.contains(db.getShopDeptId()))
        {
            throw new ServiceException("无权访问该店铺供应商");
        }
        return db;
    }

    private List<Long> resolveRequiredScopeDeptIds(Long selectedShopDeptId)
    {
        List<Long> scopeDeptIds = resolveRelatedScopeDeptIds(selectedShopDeptId);
        if (scopeDeptIds == null || scopeDeptIds.isEmpty())
        {
            throw new ServiceException("当前无可用供应商组织范围");
        }
        return scopeDeptIds;
    }

    private int countReferences(InvSupplier supplier, Long selectedShopDeptId)
    {
        return supplierMapper.countSupplierReferences(supplier.getSupplierId(),
                supplier.getSupplierName(), resolveRequiredScopeDeptIds(selectedShopDeptId));
    }

    private void assertUniqueSupplier(InvSupplier supplier, Long shopDeptId, Long excludeSupplierId)
    {
        if (supplierMapper.countDuplicateSupplierName(supplier.getSupplierName(), shopDeptId,
                excludeSupplierId) > 0)
        {
            throw new ServiceException("当前组织已存在同名供应商");
        }
        if (supplier.getSupplierCode() != null && !supplier.getSupplierCode().isEmpty()
                && supplierMapper.countDuplicateSupplierCode(supplier.getSupplierCode(), shopDeptId,
                        excludeSupplierId) > 0)
        {
            throw new ServiceException("当前组织已存在相同供应商编码");
        }
    }

    private void applyCreateDefaults(InvSupplier supplier)
    {
        if (supplier.getSupplierCode() == null) supplier.setSupplierCode("");
        if (supplier.getContactPerson() == null) supplier.setContactPerson("");
        if (supplier.getContactPhone() == null) supplier.setContactPhone("");
        if (supplier.getContactEmail() == null) supplier.setContactEmail("");
        if (supplier.getAddress() == null) supplier.setAddress("");
        if (supplier.getSettlementMethod() == null) supplier.setSettlementMethod("");
        if (supplier.getCooperationStatus() == null) supplier.setCooperationStatus("0");
        if (supplier.getStatus() == null) supplier.setStatus("0");
        if (supplier.getRemark() == null) supplier.setRemark("");
    }

    private void normalizeAndValidateSupplier(InvSupplier supplier)
    {
        supplier.setSupplierName(requiredText(supplier.getSupplierName(), 128, "供应商名称"));
        supplier.setSupplierCode(optionalText(supplier.getSupplierCode(), 64, "供应商编码"));
        supplier.setContactPerson(optionalText(supplier.getContactPerson(), 64, "联系人"));
        supplier.setContactPhone(optionalText(supplier.getContactPhone(), 32, "联系电话"));
        supplier.setContactEmail(optionalText(supplier.getContactEmail(), 64, "电子邮箱"));
        supplier.setAddress(optionalText(supplier.getAddress(), 256, "地址"));
        supplier.setSettlementMethod(optionalText(supplier.getSettlementMethod(), 64, "结算方式"));
        supplier.setRemark(optionalText(supplier.getRemark(), 500, "备注"));
        if (supplier.getContactEmail() != null && !supplier.getContactEmail().isEmpty()
                && !supplier.getContactEmail().matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))
        {
            throw new ServiceException("电子邮箱格式无效");
        }
        if (supplier.getCooperationStatus() != null
                && !supplier.getCooperationStatus().matches("[012]"))
        {
            throw new ServiceException("合作状态无效");
        }
        if (supplier.getStatus() != null && !supplier.getStatus().matches("[01]"))
        {
            throw new ServiceException("供应商状态无效");
        }
    }

    private String requiredText(String value, int maximum, String label)
    {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty())
        {
            throw new ServiceException(label + "不能为空");
        }
        if (normalized.length() > maximum)
        {
            throw new ServiceException(label + "不能超过" + maximum + "个字符");
        }
        return normalized;
    }

    private String optionalText(String value, int maximum, String label)
    {
        if (value == null)
        {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maximum)
        {
            throw new ServiceException(label + "不能超过" + maximum + "个字符");
        }
        return normalized;
    }
}
