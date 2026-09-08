package com.erp.inventory.domain.transfer;

import java.math.BigDecimal;
import java.util.Objects;
import com.erp.common.core.exception.ServiceException;
import com.erp.inventory.constant.InvTransferTypes;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationAsyncWorkflowPolicy.DispatchSpec;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationAsyncWorkflowPolicy.Source;

/** Pure construction policy for a shortage-adjudication reship child. */
public final class InvTransferReceiptDiscrepancyReshipPolicy
{
    private InvTransferReceiptDiscrepancyReshipPolicy()
    {
    }

    public static PreparedChild prepare(Source source,
            InvTransferReceiptDiscrepancyReshipFact fact,
            Long selectedShopDeptId)
    {
        DispatchSpec spec =
                InvTransferReceiptDiscrepancyAdjudicationAsyncWorkflowPolicy
                        .plan(source);
        if (fact == null || !positive(selectedShopDeptId)
                || !Objects.equals(spec.workflowType(),
                        InvTransferReceiptDiscrepancyAdjudicationAsyncWorkflowPolicy
                                .WORKFLOW_RESHIP)
                || !Objects.equals(spec.inventorySource(),
                        InvTransferReceiptDiscrepancyAdjudicationAsyncWorkflowPolicy
                                .INVENTORY_AVAILABLE)
                || !Objects.equals(spec.sourceBusinessType(),
                        InvTransferReceiptDiscrepancyAdjudicationAsyncWorkflowPolicy
                                .SOURCE_RESHIP)
                || !matches(source, fact)
                || !positive(fact.getFromDeptId())
                || !positive(fact.getToDeptId())
                || !warehouse(fact.getFromWarehouseId())
                || !warehouse(fact.getToWarehouseId())
                || Objects.equals(source.originalSourceLocationDeptId(),
                        source.originalTargetLocationDeptId())
                || !Objects.equals(selectedShopDeptId,
                        initiatingDept(source)))
        {
            throw new ServiceException("补发子调拨来源或组织事实无效");
        }
        if (InvTransferTypes.STORE_RETURN.equals(
                source.parentTransferType())
                && (blank(fact.getReturnReasonCode())
                    || ("OTHER".equals(fact.getReturnReasonCode())
                        && blank(fact.getReturnReasonText()))))
        {
            throw new ServiceException("补发返仓子调拨缺少原返仓原因");
        }
        return new PreparedChild(spec.childTransferType(),
                fact.getFromDeptId(), fact.getFromWarehouseId(),
                fact.getToDeptId(), fact.getToWarehouseId(),
                fact.getReturnReasonCode(), fact.getReturnReasonText(),
                spec.sourceBusinessType(), source.actionId(),
                source.itemType(), source.itemId(), source.productId(),
                source.quantity(), source.sourceCostPrice(),
                "V2 discrepancy reship action=" + source.actionId());
    }

    private static boolean matches(Source source,
            InvTransferReceiptDiscrepancyReshipFact fact)
    {
        return Objects.equals(source.caseId(), fact.getCaseId())
                && Objects.equals(source.caseVersionBefore(),
                        fact.getCaseVersionBefore())
                && Objects.equals(source.adjudicationId(),
                        fact.getAdjudicationId())
                && Objects.equals(source.actionId(), fact.getActionId())
                && Objects.equals(source.actionVersionBefore(),
                        fact.getActionVersionBefore())
                && Objects.equals(source.discrepancyType(),
                        fact.getDiscrepancyType())
                && Objects.equals(source.actionType(),
                        fact.getActionType())
                && Objects.equals(source.parentTransferId(),
                        fact.getParentTransferId())
                && Objects.equals(source.parentTransferType(),
                        fact.getParentTransferType())
                && Objects.equals(source.originalSourceLocationDeptId(),
                        location(fact.getFromWarehouseId(),
                                fact.getFromDeptId()))
                && Objects.equals(source.originalTargetLocationDeptId(),
                        location(fact.getToWarehouseId(),
                                fact.getToDeptId()))
                && Objects.equals(source.receiptAllocationId(),
                        fact.getReceiptAllocationId())
                && Objects.equals(source.shipmentAllocationId(),
                        fact.getShipmentAllocationId())
                && Objects.equals(source.itemType(), fact.getItemType())
                && Objects.equals(source.itemId(), fact.getItemId())
                && Objects.equals(source.productId(), fact.getProductId())
                && Objects.equals(source.trackingPolicy(),
                        fact.getTrackingPolicy())
                && equal(source.quantity(), fact.getQuantity())
                && equal(source.sourceCostPrice(),
                        fact.getSourceCostPrice())
                && equal(source.amount(), fact.getAmount())
                && Objects.equals(source.decisionFingerprint(),
                        fact.getDecisionFingerprint());
    }

    private static Long initiatingDept(Source source)
    {
        return InvTransferTypes.WAREHOUSE.equals(source.parentTransferType())
                ? source.originalTargetLocationDeptId()
                : source.originalSourceLocationDeptId();
    }

    private static Long location(Long warehouseId, Long deptId)
    {
        return warehouseId != null && warehouseId != 0
                ? warehouseId : deptId;
    }

    private static boolean positive(Long value)
    {
        return value != null && value > 0;
    }

    private static boolean warehouse(Long value)
    {
        return value == null || value >= 0;
    }

    private static boolean blank(String value)
    {
        return value == null || value.isBlank();
    }

    private static boolean equal(BigDecimal left, BigDecimal right)
    {
        return left != null && right != null && left.compareTo(right) == 0;
    }

    public record PreparedChild(String transferType, Long fromDeptId,
            Long fromWarehouseId, Long toDeptId, Long toWarehouseId,
            String returnReasonCode, String returnReasonText,
            String sourceBusinessType, Long sourceBusinessId,
            String itemType, Long itemId, Long productId,
            BigDecimal quantity, BigDecimal referenceCostPrice,
            String remark)
    {
    }
}
