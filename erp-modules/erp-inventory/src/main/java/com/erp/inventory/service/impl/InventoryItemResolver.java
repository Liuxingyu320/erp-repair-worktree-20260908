package com.erp.inventory.service.impl;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Comparator;
import java.util.Objects;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.erp.inventory.mapper.InvCatalogReferenceMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.erp.common.core.exception.ServiceException;
import com.erp.inventory.constant.InvItemTypes;
import com.erp.inventory.domain.InvGiftBox;
import com.erp.inventory.domain.InvOeItem;
import com.erp.inventory.domain.InvProduct;
import com.erp.inventory.domain.vo.InventoryItemSnapshot;
import com.erp.inventory.mapper.InvGiftMapper;
import com.erp.inventory.mapper.InvOeMapper;
import com.erp.inventory.mapper.InvProductMapper;

@Component
public class InventoryItemResolver
{
    @Autowired
    private InvCatalogReferenceMapper catalogReferenceMapper;

    @Autowired
    private InvProductMapper productMapper;

    @Autowired
    private InvOeMapper oeMapper;

    @Autowired
    private InvGiftMapper giftMapper;

    public record ReferenceKey(String itemType, Long itemId) {}

    public static ReferenceKey referenceKey(String itemType, Long itemId, Long productId)
    {
        String type = InvItemTypes.normalize(itemType);
        Long id = InvItemTypes.resolveItemId(type, itemId, productId);
        if (id == null || id <= 0) throw new ServiceException("请选择有效物料");
        return new ReferenceKey(type, id);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void lockReferences(Collection<ReferenceKey> keys)
    {
        if (keys == null) return;
        for (ReferenceKey key : keys.stream().filter(Objects::nonNull)
                .filter(key -> !InvItemTypes.PRODUCT.equals(key.itemType()))
                .distinct().sorted(Comparator.comparing(ReferenceKey::itemType).thenComparing(ReferenceKey::itemId)).toList())
        {
            if (!java.util.List.of(InvItemTypes.OE, InvItemTypes.GIFT).contains(key.itemType()))
                throw new ServiceException("物料类型无效");
            String status = catalogReferenceMapper.selectStatusForUpdate(key.itemType(), key.itemId());
            if (!"0".equals(status)) throw new ServiceException("物料不存在、已删除或已停用，请重新选择");
        }
    }

    public InventoryItemSnapshot resolve(String itemType, Long itemId, Long productId)
    {
        String normalizedType = InvItemTypes.normalize(itemType);
        Long resolvedItemId = InvItemTypes.resolveItemId(normalizedType, itemId, productId);
        if (resolvedItemId == null)
        {
            throw new ServiceException("请选择物料");
        }
        if (InvItemTypes.PRODUCT.equals(normalizedType))
        {
            return resolveProduct(resolvedItemId);
        }
        if (InvItemTypes.OE.equals(normalizedType))
        {
            return resolveOe(resolvedItemId);
        }
        return resolveGift(resolvedItemId);
    }

    private InventoryItemSnapshot resolveProduct(Long productId)
    {
        InvProduct product = productMapper.selectInvProductById(productId);
        if (product == null)
        {
            throw new ServiceException("商品不存在: " + productId);
        }
        InventoryItemSnapshot snapshot = new InventoryItemSnapshot();
        snapshot.setItemType(InvItemTypes.PRODUCT);
        snapshot.setItemId(product.getProductId());
        snapshot.setProductId(product.getProductId());
        snapshot.setOwnerDeptId(product.getShopDeptId());
        snapshot.setItemCode(product.getProductCode() != null && !product.getProductCode().isBlank()
                ? product.getProductCode() : product.getSku());
        snapshot.setItemName(product.getProductName());
        snapshot.setSpec(product.getSpec());
        snapshot.setUnit(product.getUnit());
        snapshot.setGrade(product.getGrade());
        snapshot.setSupplierName(product.getSupplierName());
        snapshot.setStatus(product.getStatus());
        snapshot.setPurchasePrice(product.getPurchasePrice());
        snapshot.setSalesPrice(product.getSalesPrice());
        snapshot.setCostPrice(product.getCostPrice());
        return snapshot;
    }

    private InventoryItemSnapshot resolveOe(Long oeItemId)
    {
        InvOeItem item = oeMapper.selectInvOeById(oeItemId);
        if (item == null)
        {
            throw new ServiceException("OE不存在: " + oeItemId);
        }
        InventoryItemSnapshot snapshot = new InventoryItemSnapshot();
        snapshot.setItemType(InvItemTypes.OE);
        snapshot.setItemId(item.getOeItemId());
        snapshot.setItemCode(item.getOeItemCode());
        snapshot.setItemName(item.getOeItemName());
        snapshot.setSpec(item.getItemDescription());
        snapshot.setUnit(item.getOrderUnit());
        snapshot.setSupplierName(item.getSupplierName());
        snapshot.setStatus(item.getStatus());
        snapshot.setPurchasePrice(item.getCostPrice());
        snapshot.setCostPrice(item.getCostPrice());
        return snapshot;
    }

    private InventoryItemSnapshot resolveGift(Long giftId)
    {
        InvGiftBox gift = giftMapper.selectInvGiftById(giftId);
        if (gift == null)
        {
            throw new ServiceException("礼盒不存在: " + giftId);
        }
        InventoryItemSnapshot snapshot = new InventoryItemSnapshot();
        snapshot.setItemType(InvItemTypes.GIFT);
        snapshot.setItemId(gift.getGiftId());
        snapshot.setItemCode(gift.getGiftCode());
        snapshot.setItemName(gift.getGiftName());
        snapshot.setSpec(gift.getSpec());
        snapshot.setUnit(gift.getReplenishmentUnit());
        snapshot.setGrade(gift.getGrade());
        snapshot.setSupplierName("礼盒");
        snapshot.setStatus(gift.getStatus());
        snapshot.setPurchasePrice(BigDecimal.ZERO);
        snapshot.setSalesPrice(gift.getGuidePrice1() != null ? gift.getGuidePrice1() : gift.getGuidePrice2());
        snapshot.setCostPrice(BigDecimal.ZERO);
        return snapshot;
    }
}
