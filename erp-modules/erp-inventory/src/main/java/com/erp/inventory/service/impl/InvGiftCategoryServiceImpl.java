package com.erp.inventory.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.security.utils.SecurityUtils;
import com.erp.inventory.domain.InvGiftCategory;
import com.erp.inventory.mapper.InvGiftCategoryMapper;
import com.erp.inventory.service.IInvGiftCategoryService;
import com.erp.inventory.util.InventoryCodeUtils;

@Service
public class InvGiftCategoryServiceImpl extends InvBaseService implements IInvGiftCategoryService
{
    @Autowired
    private InvGiftCategoryMapper categoryMapper;

    @Override
    public List<InvGiftCategory> selectCategoryTree()
    {
        InvGiftCategory query = new InvGiftCategory();
        return buildTree(categoryMapper.selectInvGiftCategoryList(query));
    }

    @Override
    public InvGiftCategory selectCategoryById(Long categoryId)
    {
        return assertAndGetCategory(categoryId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InvGiftCategory saveCategory(InvGiftCategory category, Long selectedDeptId)
    {
        requireWarehouseContext(selectedDeptId, "请先选择仓库后再维护礼盒分类");
        category.setCategoryName(trimToNull(category.getCategoryName()));
        if (category.getCategoryId() == null)
        {
            category.setStatus(isBlank(category.getStatus()) ? "0" : category.getStatus());
            category.setCategoryCode(generateCategoryCode(category.getCategoryName(), null));
            category.setCreateBy(SecurityUtils.getUsername());
            if (category.getParentId() != null && category.getParentId() > 0)
            {
                InvGiftCategory parent = assertAndGetCategory(category.getParentId());
                category.setAncestors(parent.getAncestors() + "," + parent.getCategoryId());
            }
            else
            {
                category.setParentId(0L);
                category.setAncestors("0");
            }
            categoryMapper.insertInvGiftCategory(category);
        }
        else
        {
            InvGiftCategory db = assertAndGetCategory(category.getCategoryId());
            category.setCategoryCode(isBlank(db.getCategoryCode()) || !same(category.getCategoryName(), db.getCategoryName())
                    ? generateCategoryCode(category.getCategoryName(), category.getCategoryId()) : db.getCategoryCode());
            category.setParentId(null);
            category.setAncestors(null);
            category.setUpdateBy(SecurityUtils.getUsername());
            categoryMapper.updateInvGiftCategory(category);
        }
        return categoryMapper.selectInvGiftCategoryById(category.getCategoryId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteCategoryById(Long categoryId, Long selectedDeptId)
    {
        requireWarehouseContext(selectedDeptId, "请先选择仓库后再维护礼盒分类");
        assertAndGetCategory(categoryId);
        if (categoryMapper.countChildCategory(categoryId) > 0)
        {
            throw new ServiceException("分类下存在子分类，不能删除");
        }
        if (categoryMapper.countGiftByCategoryId(categoryId) > 0)
        {
            throw new ServiceException("分类已被礼盒引用，不能删除，请先调整礼盒分类或停用分类");
        }
        categoryMapper.deleteInvGiftCategoryById(categoryId);
    }

    private List<InvGiftCategory> buildTree(List<InvGiftCategory> list)
    {
        List<InvGiftCategory> roots = list.stream()
                .filter(c -> c.getParentId() == null || c.getParentId() == 0)
                .collect(Collectors.toList());
        List<InvGiftCategory> nonRoots = list.stream()
                .filter(c -> c.getParentId() != null && c.getParentId() > 0)
                .collect(Collectors.toList());
        for (InvGiftCategory root : roots)
        {
            root.setChildren(findChildren(root, nonRoots));
        }
        return roots;
    }

    private List<InvGiftCategory> findChildren(InvGiftCategory parent, List<InvGiftCategory> all)
    {
        List<InvGiftCategory> children = new ArrayList<>();
        for (InvGiftCategory category : all)
        {
            if (category.getParentId().equals(parent.getCategoryId()))
            {
                category.setChildren(findChildren(category, all));
                children.add(category);
            }
        }
        return children;
    }

    private InvGiftCategory assertAndGetCategory(Long categoryId)
    {
        InvGiftCategory category = categoryMapper.selectInvGiftCategoryById(categoryId);
        if (category == null)
        {
            throw new ServiceException("礼盒分类不存在");
        }
        return category;
    }

    private String generateCategoryCode(String categoryName, Long excludeCategoryId)
    {
        return InventoryCodeUtils.resolveCategoryCode(categoryName,
                code -> categoryMapper.countCategoryCode(code, excludeCategoryId) > 0);
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
