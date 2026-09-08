package com.erp.inventory.service.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.erp.common.core.constant.CacheConstants;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.redis.service.RedisService;
import com.erp.common.security.utils.SecurityUtils;
import com.erp.inventory.domain.InvProductCategory;
import com.erp.inventory.mapper.InvProductCategoryMapper;
import com.erp.inventory.service.IInvProductCategoryService;
import com.erp.inventory.util.InventoryCodeUtils;

@Service
public class InvProductCategoryServiceImpl extends InvBaseService implements IInvProductCategoryService
{
    @Autowired
    private InvProductCategoryMapper categoryMapper;

    @Autowired
    private RedisService redisService;

    @Override
    public List<InvProductCategory> selectCategoryTree(Long selectedShopDeptId)
    {
        InvProductCategory query = new InvProductCategory();
        appendRelatedShopScope(query, selectedShopDeptId);
        String cacheKey = CacheConstants.INV_CATEGORY_KEY + "tree:" + selectedShopDeptId;
        List<InvProductCategory> cached = redisService.getCacheObject(cacheKey);
        if (cached != null)
        {
            return cached;
        }
        List<InvProductCategory> all = categoryMapper.selectInvProductCategoryList(query);
        List<InvProductCategory> tree = buildTree(all);
        redisService.setCacheObject(cacheKey, tree, CacheConstants.EXPIRATION, TimeUnit.MINUTES);
        return tree;
    }

    private void clearCategoryCache()
    {
        Collection<String> keys = redisService.keys(CacheConstants.INV_CATEGORY_KEY + "tree:*");
        if (keys != null && !keys.isEmpty())
        {
            redisService.deleteObject(keys);
        }
    }

    @Override
    public InvProductCategory selectCategoryById(Long categoryId, Long selectedShopDeptId)
    {
        return assertAndGetScopedCategory(categoryId, selectedShopDeptId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InvProductCategory saveCategory(InvProductCategory category, Long selectedShopDeptId)
    {
        Long shopDeptId = resolveAndValidateShopDept(selectedShopDeptId);
        category.setCategoryName(trimToNull(category.getCategoryName()));
        validateCategoryValues(category);
        if (category.getCategoryId() == null)
        {
            if (category.getStatus() == null || category.getStatus().trim().length() == 0)
            {
                category.setStatus("0");
            }
            if (category.getOrderNum() == null)
            {
                category.setOrderNum(0);
            }
            category.setShopDeptId(shopDeptId);
            category.setCategoryCode(generateCategoryCode(category.getCategoryName(), shopDeptId, null));
            category.setCreateBy(SecurityUtils.getUsername());
            if (category.getParentId() != null && category.getParentId() > 0)
            {
                InvProductCategory parent = assertAndGetScopedCategory(category.getParentId(), selectedShopDeptId);
                if (!"0".equals(parent.getStatus()))
                {
                    throw new ServiceException("上级分类已停用，不能新增子分类");
                }
                category.setAncestors(parent.getAncestors() + "," + parent.getCategoryId());
            }
            else
            {
                category.setAncestors("0");
                category.setParentId(0L);
            }
            categoryMapper.insertInvProductCategory(category);
        }
        else
        {
            InvProductCategory db = assertAndGetScopedCategory(category.getCategoryId(), selectedShopDeptId);
            if (shouldRegenerateCategoryCode(category, db))
            {
                category.setCategoryCode(generateCategoryCode(category.getCategoryName(), db.getShopDeptId(), category.getCategoryId()));
            }
            else
            {
                category.setCategoryCode(db.getCategoryCode());
            }
            category.setParentId(null);
            category.setAncestors(null);
            category.setUpdateBy(SecurityUtils.getUsername());
            categoryMapper.updateInvProductCategory(category);
        }
        clearCategoryCache();
        return categoryMapper.selectInvProductCategoryById(category.getCategoryId());
    }

    private void validateCategoryValues(InvProductCategory category)
    {
        if (category.getOrderNum() != null && category.getOrderNum() < 0)
        {
            throw new ServiceException("分类排序不能小于0");
        }
        if (category.getParentId() != null && category.getParentId() < 0)
        {
            throw new ServiceException("上级分类标识不能小于0");
        }
        if (category.getStatus() != null)
        {
            String status = category.getStatus().trim();
            if (!status.isEmpty() && !"0".equals(status) && !"1".equals(status))
            {
                throw new ServiceException("分类状态仅支持启用或停用");
            }
            category.setStatus(status.isEmpty() ? null : status);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteCategoryById(Long categoryId, Long selectedShopDeptId)
    {
        assertAndGetScopedCategory(categoryId, selectedShopDeptId);
        if (categoryMapper.countChildCategory(categoryId) > 0)
        {
            throw new ServiceException("分类下存在子分类，不能删除");
        }
        if (categoryMapper.countProductByCategoryId(categoryId) > 0)
        {
            throw new ServiceException("分类已被商品引用，不能删除，请先调整商品分类或停用分类");
        }
        categoryMapper.deleteInvProductCategoryById(categoryId);
        clearCategoryCache();
    }

    private List<InvProductCategory> buildTree(List<InvProductCategory> list)
    {
        List<InvProductCategory> roots = list.stream()
                .filter(c -> c.getParentId() == null || c.getParentId() == 0)
                .collect(Collectors.toList());
        List<InvProductCategory> nonRoots = list.stream()
                .filter(c -> c.getParentId() != null && c.getParentId() > 0)
                .collect(Collectors.toList());
        for (InvProductCategory root : roots)
        {
            root.setChildren(findChildren(root, nonRoots));
        }
        return roots;
    }

    private List<InvProductCategory> findChildren(InvProductCategory parent, List<InvProductCategory> all)
    {
        List<InvProductCategory> children = new ArrayList<>();
        for (InvProductCategory c : all)
        {
            if (c.getParentId().equals(parent.getCategoryId()))
            {
                c.setChildren(findChildren(c, all));
                children.add(c);
            }
        }
        return children;
    }

    private InvProductCategory assertAndGetScopedCategory(Long categoryId, Long selectedShopDeptId)
    {
        InvProductCategory db = categoryMapper.selectInvProductCategoryById(categoryId);
        if (db == null)
        {
            throw new ServiceException("分类不存在");
        }
        List<Long> scopeDeptIds = resolveRelatedScopeDeptIds(selectedShopDeptId);
        if (scopeDeptIds == null || !scopeDeptIds.contains(db.getShopDeptId()))
        {
            throw new ServiceException("无权访问该店铺分类");
        }
        return db;
    }

    private boolean shouldRegenerateCategoryCode(InvProductCategory category, InvProductCategory db)
    {
        if (db.getCategoryCode() == null || db.getCategoryCode().trim().isEmpty())
        {
            return true;
        }
        String newName = category.getCategoryName() == null ? "" : category.getCategoryName().trim();
        String oldName = db.getCategoryName() == null ? "" : db.getCategoryName().trim();
        return !newName.equals(oldName);
    }

    private String generateCategoryCode(String categoryName, Long shopDeptId, Long excludeCategoryId)
    {
        return InventoryCodeUtils.resolveCategoryCode(categoryName,
                code -> categoryMapper.countCategoryCodeByShop(code, shopDeptId, excludeCategoryId) > 0);
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

}
