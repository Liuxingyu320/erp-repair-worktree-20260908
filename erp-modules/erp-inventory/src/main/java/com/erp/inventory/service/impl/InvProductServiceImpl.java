package com.erp.inventory.service.impl;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.erp.common.core.constant.CacheConstants;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.redis.service.RedisService;
import com.erp.common.security.utils.SecurityUtils;
import com.erp.inventory.domain.InvProduct;
import com.erp.inventory.domain.InvProductCategory;
import com.erp.inventory.domain.InvSupplier;
import com.erp.inventory.mapper.InvProductCategoryMapper;
import com.erp.inventory.mapper.InvProductMapper;
import com.erp.inventory.mapper.InvSupplierMapper;
import com.erp.inventory.service.IInvProductService;
import com.erp.inventory.util.InventoryCodeUtils;

@Service
public class InvProductServiceImpl extends InvBaseService implements IInvProductService
{
    @Autowired
    private InvProductMapper productMapper;

    @Autowired
    private InvProductCategoryMapper categoryMapper;

    @Autowired
    private InvSupplierMapper supplierMapper;

    @Autowired
    private RedisService redisService;

    @Override
    public List<InvProduct> selectProductList(InvProduct product, Long selectedShopDeptId)
    {
        appendRelatedShopScope(product, selectedShopDeptId);
        return productMapper.selectInvProductList(product);
    }

    @Override
    public InvProduct selectProductById(Long productId, Long selectedShopDeptId)
    {
        String cacheKey = CacheConstants.INV_PRODUCT_KEY + productId + ":" + selectedShopDeptId;
        InvProduct product = assertAndGetScopedProduct(productId, selectedShopDeptId);
        redisService.setCacheObject(cacheKey, product, CacheConstants.EXPIRATION, TimeUnit.MINUTES);
        return product;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InvProduct saveProduct(InvProduct product, Long selectedShopDeptId)
    {
        Long shopDeptId = resolveAndValidateShopDept(selectedShopDeptId);
        product.setProductName(trimToNull(product.getProductName()));
        product.setProductCode(null);
        validateOperationalValues(product);
        InvProductCategory category = assertAndGetProductCategory(product.getCategoryId(), selectedShopDeptId);
        String categoryCode = ensureCategoryCode(category);
        applySupplierFromCatalog(product, shopDeptId);
        if (product.getProductId() == null)
        {
            product.setProductCode(generateProductCode(categoryCode, shopDeptId, null));
            product.setShopDeptId(shopDeptId);
            applyDefaultProductStatus(product);
            product.setCreateBy(SecurityUtils.getUsername());
            productMapper.insertInvProduct(product);
        }
        else
        {
            InvProduct db = assertAndGetScopedProduct(product.getProductId(), selectedShopDeptId);
            validateEffectiveSafetyStockBounds(product, db);
            if (shouldRegenerateProductCode(product, db, categoryCode))
            {
                product.setProductCode(generateProductCode(categoryCode, shopDeptId, product.getProductId()));
            }
            else
            {
                product.setProductCode(db.getProductCode());
            }
            product.setUpdateBy(SecurityUtils.getUsername());
            productMapper.updateInvProduct(product);
            clearProductCache(product.getProductId());
        }
        return productMapper.selectInvProductById(product.getProductId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteProductByIds(Long[] productIds, Long selectedShopDeptId)
    {
        for (Long productId : productIds)
        {
            assertAndGetScopedProduct(productId, selectedShopDeptId);
            if (productMapper.countBusinessReferenceByProductId(productId) > 0)
            {
                throw new ServiceException("商品已被库存或历史单据引用，不能删除，请停用商品");
            }
            clearProductCache(productId);
        }
        productMapper.deleteInvProductByIds(productIds);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String importProduct(List<InvProduct> productList, boolean updateSupport, Long selectedShopDeptId)
    {
        if (productList == null || productList.isEmpty())
        {
            return "导入数据为空";
        }
        Long shopDeptId = resolveAndValidateShopDept(selectedShopDeptId);
        int successCount = 0;
        int updateCount = 0;
        int failCount = 0;
        int completionCount = 0;
        StringBuilder failMsg = new StringBuilder();
        String username = SecurityUtils.getUsername();
        for (int i = 0; i < productList.size(); i++)
        {
            InvProduct product = productList.get(i);
            try
            {
                normalizeImportedProduct(product);
                validateOperationalValues(product);
                if (isBlank(product.getProductName()))
                {
                    failCount++;
                    failMsg.append("<br/>第").append(i + 1).append("行：商品名称不能为空");
                    continue;
                }
                product.setCategoryId(resolveCategoryId(product, shopDeptId));
                InvProductCategory category = assertAndGetProductCategory(product.getCategoryId(), selectedShopDeptId);
                String categoryCode = ensureCategoryCode(category);
                applySupplierFromCatalog(product, shopDeptId, false);
                product.setShopDeptId(shopDeptId);
                InvProduct existing = findExistingProduct(product, shopDeptId);
                if (existing != null)
                {
                    if (!updateSupport)
                    {
                        failCount++;
                        failMsg.append("<br/>第").append(i + 1).append("行：商品已存在，勾选更新后可覆盖");
                        continue;
                    }
                    product.setProductId(existing.getProductId());
                    validateEffectiveSafetyStockBounds(product, existing);
                    if (shouldRegenerateProductCode(product, existing, categoryCode))
                    {
                        product.setProductCode(generateProductCode(categoryCode, shopDeptId, product.getProductId()));
                    }
                    else
                    {
                        product.setProductCode(existing.getProductCode());
                    }
                    product.setUpdateBy(username);
                    productMapper.updateInvProduct(product);
                    clearProductCache(product.getProductId());
                    updateCount++;
                }
                else
                {
                    product.setProductCode(generateProductCode(categoryCode, shopDeptId, null));
                    product.setCreateBy(username);
                    productMapper.insertInvProduct(product);
                    successCount++;
                }
                if (needsCompletion(product))
                {
                    completionCount++;
                }
            }
            catch (Exception e)
            {
                failCount++;
                failMsg.append("<br/>第").append(i + 1).append("行：").append(e.getMessage());
            }
        }
        return "成功导入" + successCount + "条，更新" + updateCount + "条，失败" + failCount + "条，待完善" + completionCount + "条" + failMsg;
    }

    private void normalizeImportedProduct(InvProduct product)
    {
        product.setProductName(trimToNull(product.getProductName()));
        product.setProductCode(trimToNull(product.getProductCode()));
        product.setCategoryName(trimToNull(product.getCategoryName()));
        product.setGrade(trimToNull(product.getGrade()));
        product.setSku(trimToNull(product.getSku()));
        product.setSpec(trimToNull(product.getSpec()));
        product.setSize(trimToNull(product.getSize()));
        product.setUnit(trimToNull(product.getUnit()));
        product.setSupplierName(trimToNull(product.getSupplierName()));
        product.setSupplierPhone(trimToNull(product.getSupplierPhone()));
        product.setSupplierRemark(trimToNull(product.getSupplierRemark()));
        product.setInternalTeaName(trimToNull(product.getInternalTeaName()));
        product.setProductDescription(trimToNull(product.getProductDescription()));
        product.setBarcode(trimToNull(product.getBarcode()));
        product.setImageUrl(trimToNull(product.getImageUrl()));
        product.setPackageImageUrl(trimToNull(product.getPackageImageUrl()));
        product.setDryTeaImageUrl(trimToNull(product.getDryTeaImageUrl()));
        product.setTeaSoupImageUrl(trimToNull(product.getTeaSoupImageUrl()));
        product.setLeafBottomImageUrl(trimToNull(product.getLeafBottomImageUrl()));
        product.setExtraImageUrl(trimToNull(product.getExtraImageUrl()));
        if (product.getSalePrice500g() != null)
        {
            product.setSalesPrice(product.getSalePrice500g());
        }
        else if (product.getSalePrice250g() != null)
        {
            product.setSalesPrice(product.getSalePrice250g());
        }
        if (isBlank(product.getStatus()))
        {
            product.setStatus("0");
        }
    }

    private Long resolveCategoryId(InvProduct product, Long shopDeptId)
    {
        if (product.getCategoryId() != null)
        {
            return product.getCategoryId();
        }
        if (isBlank(product.getCategoryName()))
        {
            return null;
        }
        String categoryName = product.getCategoryName().trim();
        InvProductCategory query = new InvProductCategory();
        query.setCategoryName(categoryName);
        query.setShopDeptId(shopDeptId);
        List<InvProductCategory> categories = categoryMapper.selectInvProductCategoryList(query);
        for (InvProductCategory category : categories)
        {
            if (categoryName.equals(category.getCategoryName()))
            {
                return category.getCategoryId();
            }
        }
        InvProductCategory category = new InvProductCategory();
        category.setParentId(0L);
        category.setAncestors("0");
        category.setCategoryName(categoryName);
        category.setCategoryCode(generateCategoryCode(categoryName, shopDeptId, null));
        category.setOrderNum(0);
        category.setShopDeptId(shopDeptId);
        category.setStatus("0");
        category.setCreateBy(SecurityUtils.getUsername());
        categoryMapper.insertInvProductCategory(category);
        clearCategoryCache();
        return category.getCategoryId();
    }

    private InvProduct findExistingProduct(InvProduct product, Long shopDeptId)
    {
        if (!isBlank(product.getProductCode()))
        {
            InvProduct existing = productMapper.selectInvProductByCodeAndShop(product.getProductCode().trim(), shopDeptId);
            if (existing != null)
            {
                return existing;
            }
        }
        if (product.getCategoryId() != null && !isBlank(product.getProductName()))
        {
            return productMapper.selectInvProductByNaturalKey(product.getCategoryId(), product.getProductName().trim(), product.getSpec(), shopDeptId);
        }
        return null;
    }

    private boolean needsCompletion(InvProduct product)
    {
        return isBlank(product.getSupplierName())
                || product.getSalesPrice() == null;
    }

    private void applyDefaultProductStatus(InvProduct product)
    {
        if (isBlank(product.getStatus()))
        {
            product.setStatus("0");
        }
    }

    private void validateOperationalValues(InvProduct product)
    {
        if (product == null)
        {
            throw new ServiceException("商品维护数据不能为空");
        }
        if (isNegative(product.getPurchasePrice())
                || isNegative(product.getSalesPrice())
                || isNegative(product.getSalePrice250g())
                || isNegative(product.getSalePrice500g())
                || isNegative(product.getCostPrice()))
        {
            throw new ServiceException("商品价格不能为负数");
        }
        if (isNegative(product.getSafetyStockMin()) || isNegative(product.getSafetyStockMax()))
        {
            throw new ServiceException("安全库存不能为负数");
        }
        validateSafetyStockBounds(product.getSafetyStockMin(), product.getSafetyStockMax());
        if (!isBlank(product.getStatus())
                && !"0".equals(product.getStatus())
                && !"1".equals(product.getStatus()))
        {
            throw new ServiceException("商品状态只能是启用或停用");
        }
    }

    private boolean isNegative(java.math.BigDecimal value)
    {
        return value != null && value.signum() < 0;
    }

    private void validateEffectiveSafetyStockBounds(InvProduct product, InvProduct persisted)
    {
        java.math.BigDecimal minimum = Boolean.TRUE.equals(product.getClearSafetyStockMin())
                ? null
                : product.getSafetyStockMin() == null
                        ? persisted.getSafetyStockMin()
                        : product.getSafetyStockMin();
        java.math.BigDecimal maximum = Boolean.TRUE.equals(product.getClearSafetyStockMax())
                ? null
                : product.getSafetyStockMax() == null
                        ? persisted.getSafetyStockMax()
                        : product.getSafetyStockMax();
        validateSafetyStockBounds(minimum, maximum);
    }

    private void validateSafetyStockBounds(java.math.BigDecimal minimum, java.math.BigDecimal maximum)
    {
        if (minimum != null && maximum != null && maximum.compareTo(minimum) < 0)
        {
            throw new ServiceException("安全库存上限不能低于下限");
        }
    }

    private void applySupplierFromCatalog(InvProduct product, Long shopDeptId)
    {
        applySupplierFromCatalog(product, shopDeptId, true);
    }

    private void applySupplierFromCatalog(InvProduct product, Long shopDeptId, boolean requireSupplier)
    {
        String supplierName = trimToNull(product.getSupplierName());
        if (supplierName == null)
        {
            if (!requireSupplier)
            {
                product.setSupplierName(null);
                product.setSupplierPhone(null);
                return;
            }
            throw new ServiceException("请选择供应商，请先在供应商管理维护供应商档案");
        }
        InvSupplier supplier = supplierMapper.selectInvSupplierByNameAndShop(supplierName, shopDeptId);
        if (supplier == null)
        {
            supplier = supplierMapper.selectActiveSupplierByNameInDeptChain(supplierName, shopDeptId);
        }
        if (supplier == null)
        {
            throw new ServiceException("供应商不存在、已停用或非合作中，请先在供应商管理维护后再选择");
        }
        product.setSupplierName(supplier.getSupplierName());
        product.setSupplierPhone(supplier.getContactPhone());
    }

    private boolean isBlank(String value)
    {
        return value == null || value.trim().isEmpty();
    }

    private String trimToNull(String value)
    {
        if (value == null)
        {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private InvProduct assertAndGetScopedProduct(Long productId, Long selectedShopDeptId)
    {
        InvProduct db = productMapper.selectInvProductById(productId);
        if (db == null)
        {
            throw new ServiceException("商品不存在");
        }
        Long rootDeptId = selectedShopDeptId == null || selectedShopDeptId == 0
                ? SecurityUtils.getLoginUser().getSysUser().getDeptId()
                : selectedShopDeptId;
        List<Long> scopeDeptIds = resolveRelatedScopeDeptIds(rootDeptId);
        if (scopeDeptIds == null || !scopeDeptIds.contains(db.getShopDeptId()))
        {
            throw new ServiceException("无权访问该店铺商品");
        }
        return db;
    }

    private InvProductCategory assertAndGetProductCategory(Long categoryId, Long selectedShopDeptId)
    {
        if (categoryId == null || categoryId <= 0)
        {
            throw new ServiceException("请选择商品分类");
        }
        InvProductCategory category = categoryMapper.selectInvProductCategoryById(categoryId);
        if (category == null)
        {
            throw new ServiceException("商品分类不存在");
        }
        if (!"0".equals(category.getStatus()))
        {
            throw new ServiceException("商品分类已停用");
        }
        List<Long> scopeDeptIds = resolveRelatedScopeDeptIds(selectedShopDeptId);
        if (scopeDeptIds == null || !scopeDeptIds.contains(category.getShopDeptId()))
        {
            throw new ServiceException("无权使用该商品分类");
        }
        return category;
    }

    private boolean shouldRegenerateProductCode(InvProduct product, InvProduct db, String categoryCode)
    {
        if (isBlank(db.getProductCode()))
        {
            return true;
        }
        if (product.getCategoryId() != null && !product.getCategoryId().equals(db.getCategoryId()))
        {
            return true;
        }
        return !db.getProductCode().startsWith(categoryCode + "-");
    }

    private String ensureCategoryCode(InvProductCategory category)
    {
        if (!isBlank(category.getCategoryCode()))
        {
            return category.getCategoryCode();
        }
        String categoryCode = generateCategoryCode(category.getCategoryName(), category.getShopDeptId(), category.getCategoryId());
        category.setCategoryCode(categoryCode);
        category.setUpdateBy(SecurityUtils.getUsername());
        categoryMapper.updateInvProductCategory(category);
        clearCategoryCache();
        return categoryCode;
    }

    private String generateCategoryCode(String categoryName, Long shopDeptId, Long excludeCategoryId)
    {
        return InventoryCodeUtils.resolveCategoryCode(categoryName,
                code -> categoryMapper.countCategoryCodeByShop(code, shopDeptId, excludeCategoryId) > 0);
    }

    private String generateProductCode(String categoryCode, Long shopDeptId, Long excludeProductId)
    {
        return InventoryCodeUtils.generateProductCode(categoryCode, code -> {
            InvProduct existing = productMapper.selectInvProductByCodeAndShop(code, shopDeptId);
            return existing != null && (excludeProductId == null || !excludeProductId.equals(existing.getProductId()));
        });
    }

    private void clearProductCache(Long productId)
    {
        if (productId == null)
        {
            return;
        }
        redisService.deleteObject(CacheConstants.INV_PRODUCT_KEY + productId);
        Collection<String> keys = redisService.keys(CacheConstants.INV_PRODUCT_KEY + productId + ":*");
        if (keys != null && !keys.isEmpty())
        {
            redisService.deleteObject(keys);
        }
    }

    private void clearCategoryCache()
    {
        Collection<String> keys = redisService.keys(CacheConstants.INV_CATEGORY_KEY + "tree:*");
        if (keys != null && !keys.isEmpty())
        {
            redisService.deleteObject(keys);
        }
    }

}
