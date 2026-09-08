package com.erp.inventory.service.impl;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.security.utils.SecurityUtils;
import com.erp.inventory.domain.InvOeCategory;
import com.erp.inventory.domain.InvOeItem;
import com.erp.inventory.domain.InvSupplier;
import com.erp.inventory.domain.vo.InvOePurchaseReferencePolicyVo;
import com.erp.inventory.mapper.InvOeCategoryMapper;
import com.erp.inventory.mapper.InvOeMapper;
import com.erp.inventory.mapper.InvSupplierMapper;
import com.erp.inventory.service.IInvOeService;
import com.erp.inventory.util.InventoryCodeUtils;

@Service
public class InvOeServiceImpl extends InvBaseService implements IInvOeService
{
    @Autowired
    private InvOeMapper oeMapper;

    @Autowired
    private InvOeCategoryMapper categoryMapper;

    @Autowired
    private InvSupplierMapper supplierMapper;

    @Value("${inventory.oe.purchase-reference.allowed-hosts:}")
    private String purchaseReferenceAllowedHosts;

    @Override
    public List<InvOeItem> selectOeList(InvOeItem item)
    {
        return oeMapper.selectInvOeList(item == null ? new InvOeItem() : item);
    }

    @Override
    public InvOeItem selectOeById(Long oeItemId)
    {
        return assertAndGetOe(oeItemId);
    }

    @Override
    public InvOePurchaseReferencePolicyVo getPurchaseReferencePolicy()
    {
        List<String> allowedHosts = getPurchaseReferenceAllowedHosts();
        InvOePurchaseReferencePolicyVo policy = new InvOePurchaseReferencePolicyVo();
        policy.setConfigured(!allowedHosts.isEmpty());
        policy.setAllowedHosts(allowedHosts);
        return policy;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InvOeItem saveOe(InvOeItem item, Long selectedDeptId)
    {
        requireWarehouseContext(selectedDeptId, "请先选择仓库后再维护OE器皿");
        normalize(item);
        InvOeCategory category = assertAndGetCategory(item.getCategoryId());
        String categoryCode = ensureCategoryCode(category);
        applySupplierFromCatalog(item, selectedDeptId, false);
        if (item.getOeItemId() == null)
        {
            prepareNewPurchaseReference(item);
            item.setOeItemCode(resolveCreateCode(item.getOeItemCode(), categoryCode, null));
            item.setStatus(defaultStatus(item.getStatus()));
            item.setCreateBy(SecurityUtils.getUsername());
            oeMapper.insertInvOe(item);
        }
        else
        {
            InvOeItem db = assertAndGetOe(item.getOeItemId());
            preparePurchaseReferenceUpdate(item, db);
            item.setOeItemCode(resolveUpdateCode(item.getOeItemCode(), categoryCode, item.getOeItemId(), db));
            assertActiveFixedAssetReferenceComplete(item, db);
            item.setUpdateBy(SecurityUtils.getUsername());
            oeMapper.updateInvOe(item);
        }
        return oeMapper.selectInvOeById(item.getOeItemId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteOeByIds(Long[] oeItemIds, Long selectedDeptId)
    {
        requireWarehouseContext(selectedDeptId, "请先选择仓库后再维护OE器皿");
        for (Long oeItemId : oeItemIds)
        {
            assertAndGetOe(oeItemId);
            if (oeMapper.countFixedAssetConfigByOeItemId(oeItemId) > 0)
            {
                throw new ServiceException("OE器皿已被固定资产配置引用，不能删除，请先停用");
            }
        }
        oeMapper.deleteInvOeByIds(oeItemIds);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String importOe(List<InvOeItem> items, boolean updateSupport, Long selectedDeptId)
    {
        requireWarehouseContext(selectedDeptId, "请先选择仓库后再导入OE器皿");
        if (items == null || items.isEmpty())
        {
            return "导入数据为空";
        }
        int successCount = 0;
        int updateCount = 0;
        int failCount = 0;
        int completionCount = 0;
        StringBuilder failMsg = new StringBuilder();
        String username = SecurityUtils.getUsername();
        for (int i = 0; i < items.size(); i++)
        {
            InvOeItem item = items.get(i);
            try
            {
                normalize(item);
                if (isBlank(item.getOeItemName()))
                {
                    failCount++;
                    failMsg.append("<br/>第").append(i + 1).append("行：物品名称不能为空");
                    continue;
                }
                item.setCategoryId(resolveCategoryId(item));
                InvOeCategory category = assertAndGetCategory(item.getCategoryId());
                String categoryCode = ensureCategoryCode(category);
                applySupplierFromCatalog(item, selectedDeptId, false);
                InvOeItem existing = findExisting(item);
                if (existing != null)
                {
                    if (!updateSupport)
                    {
                        failCount++;
                        failMsg.append("<br/>第").append(i + 1).append("行：OE器皿已存在，勾选更新后可覆盖");
                        continue;
                    }
                    item.setOeItemId(existing.getOeItemId());
                    preparePurchaseReferenceUpdate(item, existing);
                    item.setOeItemCode(resolveUpdateCode(item.getOeItemCode(), categoryCode, item.getOeItemId(), existing));
                    assertActiveFixedAssetReferenceComplete(item, existing);
                    item.setUpdateBy(username);
                    oeMapper.updateInvOe(item);
                    updateCount++;
                }
                else
                {
                    prepareNewPurchaseReference(item);
                    item.setOeItemCode(resolveCreateCode(item.getOeItemCode(), categoryCode, null));
                    item.setStatus(defaultStatus(item.getStatus()));
                    item.setCreateBy(username);
                    oeMapper.insertInvOe(item);
                    successCount++;
                }
                if (isBlank(item.getSupplierName()) || item.getCostPrice() == null)
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

    private void normalize(InvOeItem item)
    {
        item.setOeItemCode(trimToNull(item.getOeItemCode()));
        item.setCategoryName(trimToNull(item.getCategoryName()));
        item.setOeTypeName(trimToNull(item.getOeTypeName()));
        item.setOeItemName(trimToNull(item.getOeItemName()));
        item.setItemDescription(trimToNull(item.getItemDescription()));
        item.setOrderUnit(trimToNull(item.getOrderUnit()));
        item.setSupplierName(trimToNull(item.getSupplierName()));
        item.setSupplierPhone(trimToNull(item.getSupplierPhone()));
        item.setImageUrl(trimToNull(item.getImageUrl()));
        item.setPurchaseReferenceUrl(trimToNull(item.getPurchaseReferenceUrl()));
        item.setPurchaseReferenceNote(trimToNull(item.getPurchaseReferenceNote()));
        item.setStatus(defaultStatus(item.getStatus()));
    }

    private Long resolveCategoryId(InvOeItem item)
    {
        if (item.getCategoryId() != null)
        {
            return item.getCategoryId();
        }
        if (isBlank(item.getCategoryName()))
        {
            throw new ServiceException("请选择OE分类");
        }
        InvOeCategory query = new InvOeCategory();
        query.setCategoryName(item.getCategoryName());
        List<InvOeCategory> categories = categoryMapper.selectInvOeCategoryList(query);
        for (InvOeCategory category : categories)
        {
            if (item.getCategoryName().equals(category.getCategoryName()))
            {
                return category.getCategoryId();
            }
        }
        InvOeCategory category = new InvOeCategory();
        category.setParentId(0L);
        category.setAncestors("0");
        category.setCategoryName(item.getCategoryName());
        category.setCategoryCode(generateCategoryCode(item.getCategoryName(), null));
        category.setOrderNum(0);
        category.setStatus("0");
        category.setCreateBy(SecurityUtils.getUsername());
        categoryMapper.insertInvOeCategory(category);
        return category.getCategoryId();
    }

    private InvOeItem findExisting(InvOeItem item)
    {
        if (!isBlank(item.getOeItemCode()))
        {
            InvOeItem existing = oeMapper.selectInvOeByCode(item.getOeItemCode());
            if (existing != null)
            {
                return existing;
            }
        }
        return oeMapper.selectInvOeByNaturalKey(item.getCategoryId(), item.getOeTypeName(), item.getOeItemName(), item.getItemDescription());
    }

    private void applySupplierFromCatalog(InvOeItem item, Long selectedDeptId, boolean requireSupplier)
    {
        String supplierName = trimToNull(item.getSupplierName());
        if (supplierName == null)
        {
            if (requireSupplier)
            {
                throw new ServiceException("请选择供应商，请先在供应商管理维护供应商档案");
            }
            item.setSupplierPhone(null);
            return;
        }
        Long deptId = requireSelectedShopDept(selectedDeptId);
        InvSupplier supplier = supplierMapper.selectInvSupplierByNameAndShop(supplierName, deptId);
        if (supplier == null)
        {
            supplier = supplierMapper.selectActiveSupplierByNameInDeptChain(supplierName, deptId);
        }
        if (supplier == null)
        {
            throw new ServiceException("供应商不存在、已停用或非合作中，请先在供应商管理维护后再选择");
        }
        item.setSupplierName(supplier.getSupplierName());
        item.setSupplierPhone(supplier.getContactPhone());
    }

    private InvOeCategory assertAndGetCategory(Long categoryId)
    {
        if (categoryId == null || categoryId <= 0)
        {
            throw new ServiceException("请选择OE分类");
        }
        InvOeCategory category = categoryMapper.selectInvOeCategoryById(categoryId);
        if (category == null)
        {
            throw new ServiceException("OE分类不存在");
        }
        if (!"0".equals(category.getStatus()))
        {
            throw new ServiceException("OE分类已停用");
        }
        return category;
    }

    private InvOeItem assertAndGetOe(Long oeItemId)
    {
        InvOeItem item = oeMapper.selectInvOeById(oeItemId);
        if (item == null)
        {
            throw new ServiceException("OE器皿不存在");
        }
        return item;
    }

    private String ensureCategoryCode(InvOeCategory category)
    {
        if (!isBlank(category.getCategoryCode()))
        {
            return category.getCategoryCode();
        }
        String categoryCode = generateCategoryCode(category.getCategoryName(), category.getCategoryId());
        category.setCategoryCode(categoryCode);
        category.setUpdateBy(SecurityUtils.getUsername());
        categoryMapper.updateInvOeCategory(category);
        return categoryCode;
    }

    private String resolveCreateCode(String requestedCode, String categoryCode, Long excludeOeItemId)
    {
        if (!isBlank(requestedCode))
        {
            assertOeCodeAvailable(requestedCode, excludeOeItemId);
            return requestedCode;
        }
        return generateOeCode(categoryCode, excludeOeItemId);
    }

    private String resolveUpdateCode(String requestedCode, String categoryCode, Long excludeOeItemId, InvOeItem db)
    {
        if (!isBlank(requestedCode))
        {
            assertOeCodeAvailable(requestedCode, excludeOeItemId);
            return requestedCode;
        }
        if (!isBlank(db.getOeItemCode()))
        {
            return db.getOeItemCode();
        }
        return generateOeCode(categoryCode, excludeOeItemId);
    }

    private void assertOeCodeAvailable(String oeItemCode, Long excludeOeItemId)
    {
        if (oeMapper.countOeCode(oeItemCode, excludeOeItemId) > 0)
        {
            throw new ServiceException("OE编码已存在");
        }
    }

    private String generateOeCode(String categoryCode, Long excludeOeItemId)
    {
        return InventoryCodeUtils.generateProductCode("OE-" + categoryCode, code -> oeMapper.countOeCode(code, excludeOeItemId) > 0);
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

    private void prepareNewPurchaseReference(InvOeItem item)
    {
        validatePurchaseReference(item);
        if (item.getPurchaseReferenceUrl() != null
                || item.getPurchaseReferenceNote() != null)
        {
            markPurchaseReferenceAudit(item);
        }
    }

    private void preparePurchaseReferenceUpdate(InvOeItem item, InvOeItem db)
    {
        Boolean touchedFlag = item.getPurchaseReferenceTouched();
        boolean touched = Boolean.TRUE.equals(touchedFlag)
                || (touchedFlag == null
                    && (item.getPurchaseReferenceUrl() != null
                        || item.getPurchaseReferenceNote() != null));
        if (!touched)
        {
            item.setPurchaseReferenceTouched(Boolean.FALSE);
            return;
        }
        boolean changed = !Objects.equals(item.getPurchaseReferenceUrl(), db.getPurchaseReferenceUrl())
                || !Objects.equals(item.getPurchaseReferenceNote(), db.getPurchaseReferenceNote());
        if (!changed)
        {
            item.setPurchaseReferenceTouched(Boolean.FALSE);
            return;
        }
        validatePurchaseReference(item);
        item.setPurchaseReferenceTouched(Boolean.TRUE);
        markPurchaseReferenceAudit(item);
    }

    private void markPurchaseReferenceAudit(InvOeItem item)
    {
        item.setPurchaseReferenceUpdatedBy(SecurityUtils.getUsername());
        item.setPurchaseReferenceUpdatedTime(new Date());
    }

    private void assertActiveFixedAssetReferenceComplete(InvOeItem item, InvOeItem db)
    {
        if (oeMapper.countActiveFixedAssetConfigByOeItemId(item.getOeItemId()) <= 0)
        {
            return;
        }
        boolean referenceTouched = Boolean.TRUE.equals(item.getPurchaseReferenceTouched());
        String referenceUrl = referenceTouched ? item.getPurchaseReferenceUrl() : db.getPurchaseReferenceUrl();
        String referenceNote = referenceTouched ? item.getPurchaseReferenceNote() : db.getPurchaseReferenceNote();
        List<String> missingFields = new ArrayList<>();
        addMissing(missingFields, "器皿编码", effectiveString(item.getOeItemCode(), db.getOeItemCode()));
        addMissing(missingFields, "器皿名称", effectiveString(item.getOeItemName(), db.getOeItemName()));
        addMissing(missingFields, "器皿图片", effectiveString(item.getImageUrl(), db.getImageUrl()));
        addMissing(missingFields, "规格/描述", effectiveString(item.getItemDescription(), db.getItemDescription()));
        addMissing(missingFields, "领用单位", effectiveString(item.getOrderUnit(), db.getOrderUnit()));
        if (!isHttpsPurchaseReference(referenceUrl))
        {
            missingFields.add("同款购买链接");
        }
        addMissing(missingFields, "购买说明", referenceNote);
        if (!missingFields.isEmpty())
        {
            throw new ServiceException("该OE已被启用的固定资产配置引用，缺少："
                    + String.join("、", missingFields)
                    + "；请先完善同款资料，或先停用对应固定资产配置");
        }
    }

    private void addMissing(List<String> missingFields, String label, String value)
    {
        if (isBlank(value))
        {
            missingFields.add(label);
        }
    }

    private String effectiveString(String requested, String persisted)
    {
        return requested == null ? persisted : requested;
    }

    private boolean isHttpsPurchaseReference(String rawUrl)
    {
        return !isBlank(rawUrl) && rawUrl.trim().toLowerCase(Locale.ROOT).startsWith("https://");
    }

    private void validatePurchaseReference(InvOeItem item)
    {
        String rawUrl = item.getPurchaseReferenceUrl();
        String note = item.getPurchaseReferenceNote();
        if (rawUrl != null && rawUrl.length() > 1000)
        {
            throw new ServiceException("同款购买链接不能超过1000个字符");
        }
        if (note != null && note.length() > 500)
        {
            throw new ServiceException("购买说明不能超过500个字符");
        }
        if (isBlank(rawUrl))
        {
            return;
        }
        final URI uri;
        try
        {
            uri = new URI(rawUrl.trim());
        }
        catch (URISyntaxException invalid)
        {
            throw new ServiceException("同款购买链接格式不正确");
        }
        if (!"https".equalsIgnoreCase(uri.getScheme())
                || isBlank(uri.getHost()))
        {
            throw new ServiceException("同款购买链接必须使用HTTPS完整地址");
        }
        List<String> allowedHosts = getPurchaseReferenceAllowedHosts();
        if (allowedHosts.isEmpty())
        {
            throw new ServiceException("未配置同款购买链接可信域名白名单");
        }
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        boolean trusted = allowedHosts.stream()
                .anyMatch(allowed -> host.equals(allowed)
                        || host.endsWith("." + allowed));
        if (!trusted)
        {
            throw new ServiceException("同款购买链接域名不在可信白名单");
        }
        String path = uri.getPath();
        if (isBlank(path) || "/".equals(path))
        {
            throw new ServiceException("同款购买链接必须指向具体商品或采购页面");
        }
    }

    private List<String> getPurchaseReferenceAllowedHosts()
    {
        return Arrays.stream(purchaseReferenceAllowedHosts == null ? new String[0]
                        : purchaseReferenceAllowedHosts.split(","))
                .map(String::trim)
                .filter(host -> !host.isEmpty())
                .map(host -> host.toLowerCase(Locale.ROOT))
                .distinct()
                .toList();
    }
}
