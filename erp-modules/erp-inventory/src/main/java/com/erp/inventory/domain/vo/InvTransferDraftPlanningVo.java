package com.erp.inventory.domain.vo;

import java.util.List;

/**
 * Immutable, cost-free read model for a draft stock preview. Identifiers and
 * decimals are strings so JavaScript clients cannot lose precision.
 */
public record InvTransferDraftPlanningVo(
        String planVersion,
        String transferType,
        Organization source,
        Organization destination,
        String generatedAt,
        List<Line> lines,
        boolean canSubmit,
        List<String> blockingReasons,
        String dataSource)
{
    public record Organization(String id, String name, String kind)
    {
    }

    public record Line(
            String itemType,
            String itemId,
            String productId,
            String itemCode,
            String itemName,
            String spec,
            String unit,
            String requestedQuantity,
            String availableQuantity,
            String shortageQuantity,
            String availabilityStatus,
            List<String> blockers)
    {
    }
}
