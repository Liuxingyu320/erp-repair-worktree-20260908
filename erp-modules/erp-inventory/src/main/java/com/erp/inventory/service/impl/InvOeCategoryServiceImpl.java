package com.erp.inventory.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.security.utils.SecurityUtils;
import com.erp.inventory.domain.InvOeCategory;
import com.erp.inventory.mapper.InvOeCategoryMapper;
import com.erp.inventory.service.IInvOeCategoryService;
import com.erp.inventory.util.InventoryCodeUtils;

@Service
public class InvOeCategoryServiceImpl extends InvBaseService implements IInvOeCategoryService
{
    @Autowired
    private InvOeCategoryMapper categoryMapper;

    @Override
    public List<InvOeCategory> selectCategoryTree()
    {
        InvOeCategory query = new InvOeCategory();
        List<InvOeCategory> all = categoryMapper.selectInvOeCategoryList(query);
        return buildTree(all);
    }

    @Override
    public InvOeCategory selectCategoryById(Long categoryId)
    {
        return assertAndGetCategory(categoryId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InvOeCategory saveCategory(InvOeCategory category, Long selectedDeptId)
    {
        requireWarehouseContext(selectedDeptId, "请先选择仓库后再维护OE分类");
        category.setCategoryName(trimToNull(category.getCategoryName()));
        if (category.getCategoryId() == null)
        {
            category.setStatus(defaultStatus(category.getStatus()));
            category.setCategoryCode(generateCategoryCode(category.getCategoryName(), null));
            category.setCreateBy(SecurityUtils.getUsername());
            if (category.getParentId() != null && category.getParentId() > 0)
            {
                InvOeCategory parent = assertAndGetCategory(category.getParentId());
                category.setAncestors(parent.getAncestors() + "," + parent.getCategoryId());
            }
            else
            {
                category.setParentId(0L);
                category.setAncestors("0");
            }
            categoryMapper.insertInvOeCategory(category);
        }
        else
        {
            InvOeCategory db = assertAndGetCategory(category.getCategoryId());
            category.setCategoryCode(isBlank(db.getCategoryCode()) || !same(category.getCategoryName(), db.getCategoryName())
                    ? generateCategoryCode(category.getCategoryName(), category.getCategoryId()) : db.getCategoryCode());
            category.setParentId(null);
            category.setAncestors(null);
            category.setUpdateBy(SecurityUtils.getUsername());
            categoryMapper.updateInvOeCategory(category);
        }
        return categoryMapper.selectInvOeCategoryById(category.getCategoryId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteCategoryById(Long categoryId, Long selectedDeptId)
    {
        requireWarehouseContext(selectedDeptId, "请先选择仓库后再维护OE分类");
        assertAndGetCategory(categoryId);
        if (categoryMapper.countChildCategory(categoryId) > 0)
        {
            throw new ServiceException("分类下存在子分类，不能删除");
        }
        if (categoryMapper.countOeByCategoryId(categoryId) > 0)
        {
            throw new ServiceException("分类已被OE器皿引用，不能删除，请先调整OE分类或停用分类");
        }
        categoryMapper.deleteInvOeCategoryById(categoryId);
    }

    private List<InvOeCategory> buildTree(List<InvOeCategory> list)
    {
        List<InvOeCategory> roots = list.stream()
                .filter(c -> c.getParentId() == null || c.getParentId() == 0)
                .collect(Collectors.toList());
        List<InvOeCategory> nonRoots = list.stream()
                .filter(c -> c.getParentId() != null && c.getParentId() > 0)
                .collect(Collectors.toList());
        for (InvOeCategory root : roots)
        {
            root.setChildren(findChildren(root, nonRoots));
        }
        return roots;
    }

    private List<InvOeCategory> findChildren(InvOeCategory parent, List<InvOeCategory> all)
    {
        List<InvOeCategory> children = new ArrayList<>();
        for (InvOeCategory category : all)
        {
            if (category.getParentId().equals(parent.getCategoryId()))
            {
                category.setChildren(findChildren(category, all));
                children.add(category);
            }
        }
        return children;
    }

    private InvOeCategory assertAndGetCategory(Long categoryId)
    {
        InvOeCategory category = categoryMapper.selectInvOeCategoryById(categoryId);
        if (category == null)
        {
            throw new ServiceException("OE分类不存在");
        }
        return category;
    }

    private String generateCategoryCode(String categoryName, Long excludeCategoryId)
    {
        return InventoryCodeUtils.resolveCategoryCode(categoryName,
                code -> categoryMapper.countCategoryCode(code, excludeCategoryId) > 0);
    }

    private String defaultStatus(String status)
    {
        return isBlank(status) ? "0" : status;
    }

    private boolean same(String a, String b)
    {
        return String.valueOf(trimToNull(a)).equals(String.valueOf(trimToNull(b)));
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
}
