package com.erp.inventory.domain.transfer;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import com.erp.common.core.exception.ServiceException;
import com.erp.inventory.constant.InvItemTypes;
import com.erp.inventory.constant.InvStatusConstants;
import com.erp.inventory.constant.InvTransferTypes;

/**
 * Pure contract for binding and completing adjudication-owned child transfers.
 */
public final class
        InvTransferReceiptDiscrepancyAdjudicationAsyncWorkflowPolicy
{
    public static final String WORKFLOW_RESHIP = "reship";
    public static final String WORKFLOW_RETURN = "return";
    public static final String INVENTORY_AVAILABLE = "available_stock";
    public static final String INVENTORY_QUARANTINE = "quarantine_detail";
    public static final String SOURCE_RESHIP =
            "transfer_discrepancy_reship";
    public static final String SOURCE_RETURN =
            "transfer_discrepancy_return";

    private static final Pattern REQUEST_ID = Pattern.compile(
            "[A-Za-z0-9][A-Za-z0-9._:-]{7,127}");
    private static final Pattern HASH = Pattern.compile("[a-f0-9]{64}");
    private static final Set<String> ITEM_TYPES = Set.of(
            InvItemTypes.PRODUCT, InvItemTypes.OE, InvItemTypes.GIFT);
    private static final Set<String> TRACKING_POLICIES =
            Set.of("lot", "serial");
    private static final Set<String> DISPATCH_STATUSES = Set.of(
            InvStatusConstants.SUBMITTED, InvStatusConstants.APPROVED,
            InvStatusConstants.RESERVED);

    private InvTransferReceiptDiscrepancyAdjudicationAsyncWorkflowPolicy()
    {
    }

    public static PreparedLink prepare(Source source, Child child)
    {
        requireSource(source);
        Expected expected = expected(source);
        requireDispatchChild(source, child, expected);

        String effectReference = expected.referencePrefix()
                + child.transferId();
        Instant createdAt = source.createdAt().truncatedTo(
                ChronoUnit.SECONDS);
        PreparedLink candidate = new PreparedLink(
                source.requestId(), source.caseId(),
                source.caseVersionBefore(), source.adjudicationId(),
                source.actionId(), source.actionVersionBefore(),
                source.discrepancyType(), source.actionType(),
                expected.effectKind(), expected.workflowType(),
                source.parentTransferId(), source.parentTransferType(),
                child.transferId(), expected.childTransferType(),
                source.originalSourceLocationDeptId(),
                source.originalTargetLocationDeptId(),
                expected.childSourceLocationDeptId(),
                expected.childTargetLocationDeptId(),
                source.receiptAllocationId(),
                source.shipmentAllocationId(), source.itemType(),
                source.itemId(), source.productId(),
                source.trackingPolicy(), expected.inventorySource(),
                expected.quarantineBalanceId(),
                expected.quarantineLotId(),
                expected.quarantineLocationId(), source.quantity(),
                source.sourceCostPrice(), source.amount(),
                expected.sourceBusinessType(), source.actionId(),
                effectReference, child.status(),
                child.reservationCount(), child.reservedQuantity(),
                source.decisionFingerprint(), source.executorUserId(),
                source.executorName().trim(), createdAt, null);
        return withFingerprint(candidate, fingerprint(candidate));
    }

    public static DispatchSpec plan(Source source)
    {
        requireSource(source);
        Expected expected = expected(source);
        return new DispatchSpec(expected.workflowType(),
                expected.effectKind(), expected.inventorySource(),
                expected.sourceBusinessType(), expected.referencePrefix(),
                expected.childTransferType(),
                expected.childSourceLocationDeptId(),
                expected.childTargetLocationDeptId(),
                expected.quarantineBalanceId(),
                expected.quarantineLotId(),
                expected.quarantineLocationId());
    }

    public static PreparedLink verifyLink(PreparedLink link)
    {
        requireLink(link);
        return link;
    }

    public static CompletionProof verifyCompletion(PreparedLink link,
            Child child)
    {
        requireLink(link);
        if (child == null || !Objects.equals(link.childTransferId(),
                child.transferId())
                || !Objects.equals(link.childTransferType(),
                        child.transferType())
                || !Objects.equals(link.sourceBusinessType(),
                        child.sourceBusinessType())
                || !Objects.equals(link.sourceBusinessId(),
                        child.sourceBusinessId())
                || !Objects.equals(link.childSourceLocationDeptId(),
                        child.sourceLocationDeptId())
                || !Objects.equals(link.childTargetLocationDeptId(),
                        child.targetLocationDeptId())
                || !InvStatusConstants.RECEIVED.equals(child.status())
                || child.detailCount() == null
                || child.detailCount() != 1
                || !Objects.equals(link.itemType(), child.itemType())
                || !Objects.equals(link.itemId(), child.itemId())
                || !Objects.equals(link.productId(), child.productId())
                || !equal(link.quantity(), child.requestedQuantity())
                || !equal(link.quantity(), child.deliveredQuantity())
                || !Objects.equals(link.inventorySource(),
                        child.reservationKind())
                || child.reservationCount() == null
                || child.reservationCount() != 1
                || !equal(link.reservedQuantity(),
                        child.reservedQuantity())
                || !InvTransferReceiptDiscrepancyReturnReservationLifecyclePolicy
                        .CONSUMED.equals(child.reservationStatus())
                || !equal(link.reservedQuantity(),
                        child.consumedQuantity())
                || !zero(child.releasedQuantity())
                || child.v2ShipmentCount() == null
                || child.v2ShipmentCount() <= 0
                || child.legacyShipmentCount() == null
                || child.legacyShipmentCount() != 0
                || child.receiptCount() == null
                || child.receiptCount() <= 0
                || child.openDiscrepancyCount() == null
                || child.openDiscrepancyCount() != 0)
        {
            throw new ServiceException("异步裁决子调拨尚未形成权威完成事实");
        }
        return new CompletionProof(link.actionId(),
                link.childTransferId(), link.effectReference(),
                link.workflowFingerprint());
    }

    private static void requireSource(Source source)
    {
        if (source == null || !requestId(source.requestId())
                || !positive(source.caseId())
                || !nonNegative(source.caseVersionBefore())
                || !positive(source.adjudicationId())
                || !positive(source.actionId())
                || !nonNegative(source.actionVersionBefore())
                || !positive(source.parentTransferId())
                || !transferType(source.parentTransferType())
                || !positive(source.originalSourceLocationDeptId())
                || !positive(source.originalTargetLocationDeptId())
                || Objects.equals(source.originalSourceLocationDeptId(),
                        source.originalTargetLocationDeptId())
                || !positive(source.receiptAllocationId())
                || !positive(source.shipmentAllocationId())
                || !ITEM_TYPES.contains(source.itemType())
                || !positive(source.itemId())
                || (InvItemTypes.PRODUCT.equals(source.itemType())
                        && !positive(source.productId()))
                || !TRACKING_POLICIES.contains(source.trackingPolicy())
                || !quantity(source.quantity())
                || !amount(source.sourceCostPrice())
                || !amount(source.amount())
                || source.amount().compareTo(source.sourceCostPrice()
                        .multiply(source.quantity())
                        .setScale(6, RoundingMode.HALF_UP)) != 0
                || !hash(source.decisionFingerprint())
                || !positive(source.executorUserId())
                || source.executorName() == null
                || source.executorName().isBlank()
                || source.executorName().trim().length() > 64
                || source.createdAt() == null)
        {
            throw new ServiceException("异步裁决工作流来源事实无效");
        }
        if (!InvTransferTypes.allowsItemType(source.parentTransferType(),
                source.itemType()))
        {
            throw new ServiceException("异步裁决工作流物料类型与调拨类型不兼容");
        }
    }

    private static Expected expected(Source source)
    {
        if ("shortage".equals(source.discrepancyType())
                && "reship".equals(source.actionType()))
        {
            return new Expected(WORKFLOW_RESHIP,
                    InvTransferReceiptDiscrepancyAdjudicationExecutionPolicy
                            .EFFECT_RESHIP,
                    INVENTORY_AVAILABLE, SOURCE_RESHIP,
                    "reship_transfer:", source.parentTransferType(),
                    source.originalSourceLocationDeptId(),
                    source.originalTargetLocationDeptId(), null, null, null);
        }
        if ("damaged".equals(source.discrepancyType())
                && "return_to_source".equals(source.actionType())
                && positive(source.quarantineBalanceId())
                && positive(source.quarantineLotId())
                && positive(source.quarantineLocationId()))
        {
            return new Expected(WORKFLOW_RETURN,
                    InvTransferReceiptDiscrepancyAdjudicationExecutionPolicy
                            .EFFECT_RETURN,
                    INVENTORY_QUARANTINE, SOURCE_RETURN,
                    "return_transfer:",
                    reverse(source.parentTransferType()),
                    source.originalTargetLocationDeptId(),
                    source.originalSourceLocationDeptId(),
                    source.quarantineBalanceId(),
                    source.quarantineLotId(),
                    source.quarantineLocationId());
        }
        throw new ServiceException("差异类型不支持该异步裁决工作流");
    }

    private static void requireDispatchChild(Source source, Child child,
            Expected expected)
    {
        if (child == null || !positive(child.transferId())
                || Objects.equals(source.parentTransferId(),
                        child.transferId())
                || !Objects.equals(expected.childTransferType(),
                        child.transferType())
                || !Objects.equals(expected.sourceBusinessType(),
                        child.sourceBusinessType())
                || !Objects.equals(source.actionId(),
                        child.sourceBusinessId())
                || !Objects.equals(expected.childSourceLocationDeptId(),
                        child.sourceLocationDeptId())
                || !Objects.equals(expected.childTargetLocationDeptId(),
                        child.targetLocationDeptId())
                || !DISPATCH_STATUSES.contains(child.status())
                || child.detailCount() == null || child.detailCount() != 1
                || !Objects.equals(source.itemType(), child.itemType())
                || !Objects.equals(source.itemId(), child.itemId())
                || !Objects.equals(source.productId(), child.productId())
                || !equal(source.quantity(), child.requestedQuantity())
                || !zero(child.deliveredQuantity())
                || !Objects.equals(expected.inventorySource(),
                        child.reservationKind())
                || child.reservationCount() == null
                || child.reservationCount() != 1
                || !equal(source.quantity(), child.reservedQuantity())
                || !InvTransferReceiptDiscrepancyReturnReservationLifecyclePolicy
                        .ACTIVE.equals(child.reservationStatus())
                || !zero(child.consumedQuantity())
                || !zero(child.releasedQuantity())
                || !zeroCount(child.v2ShipmentCount())
                || !zeroCount(child.legacyShipmentCount())
                || !zeroCount(child.receiptCount())
                || !zeroCount(child.openDiscrepancyCount()))
        {
            throw new ServiceException("异步裁决子调拨创建或冻结事实无效");
        }
        if (!InvTransferTypes.allowsItemType(child.transferType(),
                child.itemType()))
        {
            throw new ServiceException("异步裁决子调拨物料类型无效");
        }
    }

    private static void requireLink(PreparedLink link)
    {
        if (link == null || !requestId(link.requestId())
                || !positive(link.caseId())
                || !nonNegative(link.caseVersionBefore())
                || !positive(link.adjudicationId())
                || !positive(link.actionId())
                || !nonNegative(link.actionVersionBefore())
                || !positive(link.parentTransferId())
                || !positive(link.childTransferId())
                || Objects.equals(link.parentTransferId(),
                        link.childTransferId())
                || !transferType(link.parentTransferType())
                || !transferType(link.childTransferType())
                || !positive(link.originalSourceLocationDeptId())
                || !positive(link.originalTargetLocationDeptId())
                || Objects.equals(link.originalSourceLocationDeptId(),
                        link.originalTargetLocationDeptId())
                || !positive(link.childSourceLocationDeptId())
                || !positive(link.childTargetLocationDeptId())
                || Objects.equals(link.childSourceLocationDeptId(),
                        link.childTargetLocationDeptId())
                || !positive(link.receiptAllocationId())
                || !positive(link.shipmentAllocationId())
                || !ITEM_TYPES.contains(link.itemType())
                || !positive(link.itemId())
                || (InvItemTypes.PRODUCT.equals(link.itemType())
                        && !positive(link.productId()))
                || !TRACKING_POLICIES.contains(link.trackingPolicy())
                || !quantity(link.quantity())
                || !amount(link.sourceCostPrice())
                || !amount(link.amount())
                || link.amount().compareTo(link.sourceCostPrice()
                        .multiply(link.quantity())
                        .setScale(6, RoundingMode.HALF_UP)) != 0
                || !positive(link.sourceBusinessId())
                || !Objects.equals(link.sourceBusinessId(),
                        link.actionId())
                || !DISPATCH_STATUSES.contains(link.dispatchChildStatus())
                || link.reservationCount() == null
                || link.reservationCount() != 1
                || !equal(link.quantity(), link.reservedQuantity())
                || !hash(link.decisionFingerprint())
                || !positive(link.executorUserId())
                || link.executorName() == null
                || link.executorName().isBlank()
                || link.executorName().trim().length() > 64
                || !link.executorName().equals(link.executorName().trim())
                || link.createdAt() == null
                || !link.createdAt().equals(link.createdAt().truncatedTo(
                        ChronoUnit.SECONDS))
                || !hash(link.workflowFingerprint())
                || !Objects.equals(link.workflowFingerprint(),
                        fingerprint(withFingerprint(link, null))))
        {
            throw new ServiceException("异步裁决工作流关系不可核验");
        }
        if (!InvTransferTypes.allowsItemType(link.parentTransferType(),
                link.itemType())
                || !InvTransferTypes.allowsItemType(
                        link.childTransferType(), link.itemType()))
        {
            throw new ServiceException("异步裁决工作流关系物料类型无效");
        }
        ExpectedLink expected = expected(link);
        if (!Objects.equals(expected.effectKind(), link.effectKind())
                || !Objects.equals(expected.workflowType(),
                        link.workflowType())
                || !Objects.equals(expected.inventorySource(),
                        link.inventorySource())
                || !Objects.equals(expected.sourceBusinessType(),
                        link.sourceBusinessType())
                || !Objects.equals(expected.childTransferType(),
                        link.childTransferType())
                || !Objects.equals(expected.childSourceLocationDeptId(),
                        link.childSourceLocationDeptId())
                || !Objects.equals(expected.childTargetLocationDeptId(),
                        link.childTargetLocationDeptId())
                || !Objects.equals(expected.referencePrefix()
                        + link.childTransferId(), link.effectReference())
                || !Objects.equals(expected.quarantineBalanceId(),
                        link.quarantineBalanceId())
                || !Objects.equals(expected.quarantineLotId(),
                        link.quarantineLotId())
                || !Objects.equals(expected.quarantineLocationId(),
                        link.quarantineLocationId())
                || (!Objects.equals(link.inventorySource(),
                        INVENTORY_AVAILABLE)
                    && !Objects.equals(link.inventorySource(),
                            INVENTORY_QUARANTINE)))
        {
            throw new ServiceException("异步裁决工作流关系语义漂移");
        }
    }

    private static ExpectedLink expected(PreparedLink link)
    {
        if ("shortage".equals(link.discrepancyType())
                && "reship".equals(link.actionType()))
        {
            return new ExpectedLink(WORKFLOW_RESHIP,
                    InvTransferReceiptDiscrepancyAdjudicationExecutionPolicy
                            .EFFECT_RESHIP,
                    INVENTORY_AVAILABLE, SOURCE_RESHIP,
                    "reship_transfer:", link.parentTransferType(),
                    link.originalSourceLocationDeptId(),
                    link.originalTargetLocationDeptId(), null, null, null);
        }
        if ("damaged".equals(link.discrepancyType())
                && "return_to_source".equals(link.actionType())
                && positive(link.quarantineBalanceId())
                && positive(link.quarantineLotId())
                && positive(link.quarantineLocationId()))
        {
            return new ExpectedLink(WORKFLOW_RETURN,
                    InvTransferReceiptDiscrepancyAdjudicationExecutionPolicy
                            .EFFECT_RETURN,
                    INVENTORY_QUARANTINE, SOURCE_RETURN,
                    "return_transfer:", reverse(link.parentTransferType()),
                    link.originalTargetLocationDeptId(),
                    link.originalSourceLocationDeptId(),
                    link.quarantineBalanceId(), link.quarantineLotId(),
                    link.quarantineLocationId());
        }
        throw new ServiceException("异步裁决工作流关系类型无效");
    }

    private static String reverse(String parentTransferType)
    {
        return switch (parentTransferType)
        {
            case InvTransferTypes.WAREHOUSE -> InvTransferTypes.STORE_RETURN;
            case InvTransferTypes.STORE_RETURN -> InvTransferTypes.WAREHOUSE;
            case InvTransferTypes.CROSS_STORE -> InvTransferTypes.CROSS_STORE;
            default -> throw new ServiceException("原调拨类型不可反向");
        };
    }

    private static PreparedLink withFingerprint(PreparedLink value,
            String fingerprint)
    {
        return new PreparedLink(value.requestId(), value.caseId(),
                value.caseVersionBefore(), value.adjudicationId(),
                value.actionId(), value.actionVersionBefore(),
                value.discrepancyType(), value.actionType(),
                value.effectKind(), value.workflowType(),
                value.parentTransferId(), value.parentTransferType(),
                value.childTransferId(), value.childTransferType(),
                value.originalSourceLocationDeptId(),
                value.originalTargetLocationDeptId(),
                value.childSourceLocationDeptId(),
                value.childTargetLocationDeptId(),
                value.receiptAllocationId(), value.shipmentAllocationId(),
                value.itemType(), value.itemId(), value.productId(),
                value.trackingPolicy(), value.inventorySource(),
                value.quarantineBalanceId(), value.quarantineLotId(),
                value.quarantineLocationId(), value.quantity(),
                value.sourceCostPrice(), value.amount(),
                value.sourceBusinessType(), value.sourceBusinessId(),
                value.effectReference(), value.dispatchChildStatus(),
                value.reservationCount(), value.reservedQuantity(),
                value.decisionFingerprint(), value.executorUserId(),
                value.executorName(), value.createdAt(), fingerprint);
    }

    private static String fingerprint(PreparedLink value)
    {
        String canonical = String.join("\n",
                field(value.requestId()), field(value.caseId()),
                field(value.caseVersionBefore()),
                field(value.adjudicationId()), field(value.actionId()),
                field(value.actionVersionBefore()),
                field(value.discrepancyType()), field(value.actionType()),
                field(value.effectKind()), field(value.workflowType()),
                field(value.parentTransferId()),
                field(value.parentTransferType()),
                field(value.childTransferId()),
                field(value.childTransferType()),
                field(value.originalSourceLocationDeptId()),
                field(value.originalTargetLocationDeptId()),
                field(value.childSourceLocationDeptId()),
                field(value.childTargetLocationDeptId()),
                field(value.receiptAllocationId()),
                field(value.shipmentAllocationId()),
                field(value.itemType()), field(value.itemId()),
                field(value.productId()), field(value.trackingPolicy()),
                field(value.inventorySource()),
                field(value.quarantineBalanceId()),
                field(value.quarantineLotId()),
                field(value.quarantineLocationId()),
                decimal(value.quantity()), decimal(value.sourceCostPrice()),
                decimal(value.amount()), field(value.sourceBusinessType()),
                field(value.sourceBusinessId()),
                field(value.effectReference()),
                field(value.dispatchChildStatus()),
                field(value.reservationCount()),
                decimal(value.reservedQuantity()),
                field(value.decisionFingerprint()),
                field(value.executorUserId()), field(value.executorName()),
                field(value.createdAt()));
        try
        {
            return HexFormat.of().formatHex(MessageDigest
                    .getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        }
        catch (NoSuchAlgorithmException exception)
        {
            throw new IllegalStateException("SHA-256 is unavailable",
                    exception);
        }
    }

    private static boolean requestId(String value)
    {
        return value != null && REQUEST_ID.matcher(value).matches();
    }

    private static boolean hash(String value)
    {
        return value != null && HASH.matcher(value).matches();
    }

    private static boolean transferType(String value)
    {
        return Set.of(InvTransferTypes.WAREHOUSE,
                InvTransferTypes.STORE_RETURN,
                InvTransferTypes.CROSS_STORE).contains(value);
    }

    private static boolean positive(Long value)
    {
        return value != null && value > 0;
    }

    private static boolean nonNegative(Long value)
    {
        return value != null && value >= 0;
    }

    private static boolean quantity(BigDecimal value)
    {
        return value != null && value.signum() > 0 && value.scale() <= 4;
    }

    private static boolean amount(BigDecimal value)
    {
        return value != null && value.signum() >= 0 && value.scale() <= 6;
    }

    private static boolean equal(BigDecimal left, BigDecimal right)
    {
        return left != null && right != null && left.compareTo(right) == 0;
    }

    private static boolean zero(BigDecimal value)
    {
        return value != null && value.signum() == 0;
    }

    private static boolean zeroCount(Integer value)
    {
        return value != null && value == 0;
    }

    private static String decimal(BigDecimal value)
    {
        if (value == null)
        {
            return "";
        }
        BigDecimal normalized = value.stripTrailingZeros();
        return normalized.signum() == 0 ? "0" : normalized.toPlainString();
    }

    private static String field(Object value)
    {
        return value == null ? "" : String.valueOf(value);
    }

    private record Expected(String workflowType, String effectKind,
            String inventorySource, String sourceBusinessType,
            String referencePrefix, String childTransferType,
            Long childSourceLocationDeptId,
            Long childTargetLocationDeptId, Long quarantineBalanceId,
            Long quarantineLotId, Long quarantineLocationId)
    {
    }

    private record ExpectedLink(String workflowType, String effectKind,
            String inventorySource, String sourceBusinessType,
            String referencePrefix, String childTransferType,
            Long childSourceLocationDeptId,
            Long childTargetLocationDeptId, Long quarantineBalanceId,
            Long quarantineLotId, Long quarantineLocationId)
    {
    }

    public record Source(String requestId, Long caseId,
            Long caseVersionBefore, Long adjudicationId, Long actionId,
            Long actionVersionBefore, String discrepancyType,
            String actionType, Long parentTransferId,
            String parentTransferType, Long originalSourceLocationDeptId,
            Long originalTargetLocationDeptId, Long receiptAllocationId,
            Long shipmentAllocationId, String itemType, Long itemId,
            Long productId, String trackingPolicy,
            Long quarantineBalanceId, Long quarantineLotId,
            Long quarantineLocationId, BigDecimal quantity,
            BigDecimal sourceCostPrice, BigDecimal amount,
            String decisionFingerprint, Long executorUserId,
            String executorName, Instant createdAt)
    {
    }

    public record Child(Long transferId, String transferType,
            String sourceBusinessType, Long sourceBusinessId,
            Long sourceLocationDeptId, Long targetLocationDeptId,
            String status, Integer detailCount, String itemType,
            Long itemId, Long productId, BigDecimal requestedQuantity,
            BigDecimal deliveredQuantity, String reservationKind,
            Integer reservationCount, BigDecimal reservedQuantity,
            String reservationStatus, BigDecimal consumedQuantity,
            BigDecimal releasedQuantity,
            Integer v2ShipmentCount, Integer legacyShipmentCount,
            Integer receiptCount, Integer openDiscrepancyCount)
    {
    }

    public record PreparedLink(String requestId, Long caseId,
            Long caseVersionBefore, Long adjudicationId, Long actionId,
            Long actionVersionBefore, String discrepancyType,
            String actionType, String effectKind, String workflowType,
            Long parentTransferId, String parentTransferType,
            Long childTransferId, String childTransferType,
            Long originalSourceLocationDeptId,
            Long originalTargetLocationDeptId,
            Long childSourceLocationDeptId,
            Long childTargetLocationDeptId, Long receiptAllocationId,
            Long shipmentAllocationId, String itemType, Long itemId,
            Long productId, String trackingPolicy, String inventorySource,
            Long quarantineBalanceId, Long quarantineLotId,
            Long quarantineLocationId, BigDecimal quantity,
            BigDecimal sourceCostPrice, BigDecimal amount,
            String sourceBusinessType, Long sourceBusinessId,
            String effectReference, String dispatchChildStatus,
            Integer reservationCount, BigDecimal reservedQuantity,
            String decisionFingerprint, Long executorUserId,
            String executorName, Instant createdAt,
            String workflowFingerprint)
    {
    }

    public record CompletionProof(Long actionId, Long childTransferId,
            String effectReference, String workflowFingerprint)
    {
    }

    public record DispatchSpec(String workflowType, String effectKind,
            String inventorySource, String sourceBusinessType,
            String referencePrefix, String childTransferType,
            Long childSourceLocationDeptId,
            Long childTargetLocationDeptId, Long quarantineBalanceId,
            Long quarantineLotId, Long quarantineLocationId)
    {
    }
}
