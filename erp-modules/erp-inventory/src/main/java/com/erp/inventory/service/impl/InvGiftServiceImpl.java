package com.erp.inventory.service.impl;

import java.util.List;
import com.erp.common.core.utils.file.ImageUrlList;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.security.utils.SecurityUtils;
import com.erp.inventory.domain.InvGiftBox;
import com.erp.inventory.domain.InvGiftCategory;
import com.erp.inventory.mapper.InvGiftCategoryMapper;
import com.erp.inventory.mapper.InvGiftMapper;
import com.erp.inventory.service.IInvGiftService;
import com.erp.inventory.util.InventoryCodeUtils;

@Service
public class InvGiftServiceImpl extends InvBaseService implements IInvGiftService
{
    @Autowired
    private InvCatalogDeletionService catalogDeletionService;

    @Autowired
    private InvGiftMapper giftMapper;

    @Autowired
    private InvGiftCategoryMapper categoryMapper;

    @Override
    public List<InvGiftBox> selectGiftList(InvGiftBox gift)
    {
        return giftMapper.selectInvGiftList(gift == null ? new InvGiftBox() : gift);
    }

    @Override
    public InvGiftBox selectGiftById(Long giftId)
    {
        return assertAndGetGift(giftId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InvGiftBox saveGift(InvGiftBox gift, Long selectedDeptId)
    {
        requireWarehouseContext(selectedDeptId, "请先选择仓库后再维护礼盒资料");
        normalize(gift);
        InvGiftCategory category = assertAndGetCategory(gift.getCategoryId());
        String categoryCode = ensureCategoryCode(category);
        if (gift.getGiftId() == null)
        {
            prepareImages(gift, null);
            gift.setGiftCode(resolveCreateCode(gift.getGiftCode(), categoryCode, null));
            gift.setStatus(defaultStatus(gift.getStatus()));
            gift.setCreateBy(SecurityUtils.getUsername());
            giftMapper.insertInvGift(gift);
        }
        else
        {
            InvGiftBox db = lockGift(gift.getGiftId());
            prepareImages(gift, db);
            gift.setGiftCode(resolveUpdateCode(gift.getGiftCode(), categoryCode, gift.getGiftId(), db));
            gift.setUpdateBy(SecurityUtils.getUsername());
            giftMapper.updateInvGift(gift);
        }
        return giftMapper.selectInvGiftById(gift.getGiftId());
    }

    @Override
    public void deleteGiftByIds(Long[] giftIds, Long selectedDeptId)
    {
        catalogDeletionService.delete("gift", giftIds, selectedDeptId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String importGift(List<InvGiftBox> gifts, boolean updateSupport, Long selectedDeptId)
    {
        requireWarehouseContext(selectedDeptId, "请先选择仓库后再导入礼盒资料");
        if (gifts == null || gifts.isEmpty())
        {
            return "导入数据为空";
        }
        int successCount = 0;
        int updateCount = 0;
        int failCount = 0;
        StringBuilder failMsg = new StringBuilder();
        String username = SecurityUtils.getUsername();
        for (int i = 0; i < gifts.size(); i++)
        {
            InvGiftBox gift = gifts.get(i);
            try
            {
                normalize(gift);
                if (isBlank(gift.getGiftName()))
                {
                    failCount++;
                    failMsg.append("<br/>第").append(i + 1).append("行：产品名称不能为空");
                    continue;
                }
                gift.setCategoryId(resolveCategoryId(gift));
                InvGiftCategory category = assertAndGetCategory(gift.getCategoryId());
                String categoryCode = ensureCategoryCode(category);
                InvGiftBox existing = findExisting(gift);
                if (existing != null)
                {
                    if (!updateSupport)
                    {
                        failCount++;
                        failMsg.append("<br/>第").append(i + 1).append("行：礼盒已存在，勾选更新后可覆盖");
                        continue;
                    }
                    existing = lockGift(existing.getGiftId());
                    gift.setGiftId(existing.getGiftId());
                    prepareImages(gift, existing);
                    gift.setGiftCode(resolveUpdateCode(gift.getGiftCode(), categoryCode, gift.getGiftId(), existing));
                    gift.setUpdateBy(username);
                    giftMapper.updateInvGift(gift);
                    updateCount++;
                }
                else
                {
                    prepareImages(gift, null);
                    gift.setGiftCode(resolveCreateCode(gift.getGiftCode(), categoryCode, null));
                    gift.setStatus(defaultStatus(gift.getStatus()));
                    gift.setCreateBy(username);
                    giftMapper.insertInvGift(gift);
                    successCount++;
                }
            }
            catch (Exception e)
            {
                failCount++;
                failMsg.append("<br/>第").append(i + 1).append("行：").append(e.getMessage());
            }
        }
        return "成功导入" + successCount + "条，更新" + updateCount + "条，失败" + failCount + "条" + failMsg;
    }

    private InvGiftBox lockGift(Long id)
    {
        InvGiftBox persisted = giftMapper.selectInvGiftByIdForUpdate(id);
        if (persisted == null) throw new ServiceException("资料不存在或已删除");
        return persisted;
    }

    private void prepareImages(InvGiftBox gift, InvGiftBox persisted)
    {
        String images = ImageUrlList.prepare(gift.getImageUrlsText(), gift.getRawImageUrl(),
                persisted == null ? null : persisted.getImageUrlsText(),
                persisted == null ? null : persisted.getRawImageUrl());
        if (persisted == null && images == null) images = "[]";
        gift.setImageUrlsText(images);
        gift.setImageUrl(images == null ? null : ImageUrlList.cover(images, null));
    }

    private void normalize(InvGiftBox gift)
    {
        gift.setGiftCode(trimToNull(gift.getGiftCode()));
        gift.setCategoryName(trimToNull(gift.getCategoryName()));
        gift.setGiftName(trimToNull(gift.getGiftName()));
        gift.setGrade(trimToNull(gift.getGrade()));
        gift.setSpec(trimToNull(gift.getSpec()));
        gift.setProductDescription(trimToNull(gift.getProductDescription()));
        gift.setReplenishmentUnit(trimToNull(gift.getReplenishmentUnit()));
        gift.setSupplierName(trimToNull(gift.getSupplierName()));
        gift.setRemark(trimToNull(gift.getRemark()));
        gift.setStatus(defaultStatus(gift.getStatus()));
    }

    private Long resolveCategoryId(InvGiftBox gift)
    {
        if (gift.getCategoryId() != null)
        {
            return gift.getCategoryId();
        }
        if (isBlank(gift.getCategoryName()))
        {
            throw new ServiceException("请选择礼盒分类");
        }
        InvGiftCategory query = new InvGiftCategory();
        query.setCategoryName(gift.getCategoryName());
        List<InvGiftCategory> categories = categoryMapper.selectInvGiftCategoryList(query);
        for (InvGiftCategory category : categories)
        {
            if (gift.getCategoryName().equals(category.getCategoryName()))
            {
                return category.getCategoryId();
            }
        }
        InvGiftCategory category = new InvGiftCategory();
        category.setParentId(0L);
        category.setAncestors("0");
        category.setCategoryName(gift.getCategoryName());
        category.setCategoryCode(generateCategoryCode(gift.getCategoryName(), null));
        category.setOrderNum(0);
        category.setStatus("0");
        category.setCreateBy(SecurityUtils.getUsername());
        categoryMapper.insertInvGiftCategory(category);
        return category.getCategoryId();
    }

    private InvGiftBox findExisting(InvGiftBox gift)
    {
        if (!isBlank(gift.getGiftCode()))
        {
            InvGiftBox existing = giftMapper.selectInvGiftByCode(gift.getGiftCode());
            if (existing != null)
            {
                return existing;
            }
        }
        return giftMapper.selectInvGiftByNaturalKey(gift.getCategoryId(), gift.getGiftName(), gift.getGrade(), gift.getSpec());
    }

    private InvGiftCategory assertAndGetCategory(Long categoryId)
    {
        if (categoryId == null || categoryId <= 0)
        {
            throw new ServiceException("请选择礼盒分类");
        }
        InvGiftCategory category = categoryMapper.selectInvGiftCategoryById(categoryId);
        if (category == null)
        {
            throw new ServiceException("礼盒分类不存在");
        }
        if (!"0".equals(category.getStatus()))
        {
            throw new ServiceException("礼盒分类已停用");
        }
        return category;
    }

    private InvGiftBox assertAndGetGift(Long giftId)
    {
        InvGiftBox gift = giftMapper.selectInvGiftById(giftId);
        if (gift == null)
        {
            throw new ServiceException("礼盒不存在");
        }
        return gift;
    }

    private String ensureCategoryCode(InvGiftCategory category)
    {
        if (!isBlank(category.getCategoryCode()))
        {
            return category.getCategoryCode();
        }
        String categoryCode = generateCategoryCode(category.getCategoryName(), category.getCategoryId());
        category.setCategoryCode(categoryCode);
        category.setUpdateBy(SecurityUtils.getUsername());
        categoryMapper.updateInvGiftCategory(category);
        return categoryCode;
    }

    private String resolveCreateCode(String requestedCode, String categoryCode, Long excludeGiftId)
    {
        if (!isBlank(requestedCode))
        {
            assertGiftCodeAvailable(requestedCode, excludeGiftId);
            return requestedCode;
        }
        return generateGiftCode(categoryCode, excludeGiftId);
    }

    private String resolveUpdateCode(String requestedCode, String categoryCode, Long excludeGiftId, InvGiftBox db)
    {
        if (!isBlank(requestedCode))
        {
            assertGiftCodeAvailable(requestedCode, excludeGiftId);
            return requestedCode;
        }
        if (!isBlank(db.getGiftCode()))
        {
            return db.getGiftCode();
        }
        return generateGiftCode(categoryCode, excludeGiftId);
    }

    private void assertGiftCodeAvailable(String giftCode, Long excludeGiftId)
    {
        if (giftMapper.countGiftCode(giftCode, excludeGiftId) > 0)
        {
            throw new ServiceException("礼盒编码已存在");
        }
    }

    private String generateGiftCode(String categoryCode, Long excludeGiftId)
    {
        return InventoryCodeUtils.generateProductCode("LH-" + categoryCode, code -> giftMapper.countGiftCode(code, excludeGiftId) > 0);
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
