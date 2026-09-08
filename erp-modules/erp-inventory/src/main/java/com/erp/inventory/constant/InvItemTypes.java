package com.erp.inventory.constant;

import com.erp.common.core.exception.ServiceException;

public final class InvItemTypes
{
    private InvItemTypes() {}

    public static final String PRODUCT = "product";
    public static final String OE = "oe";
    public static final String GIFT = "gift";

    public static String normalize(String itemType)
    {
        if (itemType == null || itemType.trim().isEmpty())
        {
            return PRODUCT;
        }
        String normalized = itemType.trim().toLowerCase();
        if (PRODUCT.equals(normalized) || OE.equals(normalized) || GIFT.equals(normalized))
        {
            return normalized;
        }
        throw new ServiceException("不支持的物料类型: " + itemType);
    }

    public static Long resolveItemId(String itemType, Long itemId, Long productId)
    {
        String normalized = normalize(itemType);
        if (PRODUCT.equals(normalized) && itemId == null)
        {
            return productId;
        }
        return itemId;
    }

    public static boolean isProduct(String itemType)
    {
        return PRODUCT.equals(normalize(itemType));
    }

    public static boolean isOe(String itemType)
    {
        return OE.equals(normalize(itemType));
    }

    public static boolean isGift(String itemType)
    {
        return GIFT.equals(normalize(itemType));
    }
}
