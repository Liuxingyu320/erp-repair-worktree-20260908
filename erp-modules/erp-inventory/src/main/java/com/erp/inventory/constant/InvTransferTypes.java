package com.erp.inventory.constant;

import java.util.Set;
import com.erp.common.core.exception.ServiceException;

/** 调拨类型、方向和物料策略的唯一来源。 */
public final class InvTransferTypes
{
    public static final String WAREHOUSE = "warehouse";
    public static final String STORE_RETURN = "store_return";
    public static final String CROSS_STORE = "cross_store";
    public static final String SOURCE_SALES_DELIVERY = "sales_delivery";
    public static final String SOURCE_SALES_DELIVERY_NOTICE = "sales_delivery_notice";

    private static final Set<String> SUPPORTED = Set.of(
            WAREHOUSE, STORE_RETURN, CROSS_STORE);

    private InvTransferTypes() {}

    public static String requireSupported(String value)
    {
        String normalized = value == null || value.isBlank()
                ? WAREHOUSE : value.trim().toLowerCase();
        if (!SUPPORTED.contains(normalized))
        {
            throw new ServiceException("不支持的调拨类型");
        }
        return normalized;
    }

    public static boolean allowsItemType(String transferType,
            String itemType)
    {
        requireSupported(transferType);
        String item = InvItemTypes.normalize(itemType);
        return InvItemTypes.PRODUCT.equals(item)
                || InvItemTypes.GIFT.equals(item);
    }

    public static String stockBusinessType(String transferType,
            String sourceBusinessType)
    {
        if ("oe_replenishment".equals(sourceBusinessType))
        {
            return "oe_replenishment";
        }
        return switch (requireSupported(transferType))
        {
            case WAREHOUSE -> "warehouse_replenishment";
            case STORE_RETURN -> "store_return";
            default -> "cross_store_transfer";
        };
    }

    public static boolean requiresSourceConfirmation(String transferType,
            String sourceBusinessType)
    {
        return CROSS_STORE.equals(requireSupported(transferType))
                && !SOURCE_SALES_DELIVERY.equals(sourceBusinessType)
                && !SOURCE_SALES_DELIVERY_NOTICE.equals(sourceBusinessType);
    }
}
