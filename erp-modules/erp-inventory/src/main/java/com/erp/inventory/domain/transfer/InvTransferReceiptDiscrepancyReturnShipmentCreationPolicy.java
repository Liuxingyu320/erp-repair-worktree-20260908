package com.erp.inventory.domain.transfer;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import com.erp.common.core.exception.ServiceException;
import com.erp.inventory.constant.InvStatusConstants;
import com.erp.inventory.constant.InvTransferRevisionStatuses;
import com.erp.inventory.constant.InvTransferTypes;
import com.erp.inventory.domain.InvTransferDetail;
import com.erp.inventory.domain.InvTransferOrder;
import com.erp.inventory.domain.InvTransferRevision;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyReturnReservationLifecyclePolicy.ConsumptionPlan;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyReturnReservationLifecyclePolicy.QuantityTransition;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyReturnShipmentPolicy.PreparedPlan;

/** Pure command policy for one fixed adjudication return shipment. */
public final class
        InvTransferReceiptDiscrepancyReturnShipmentCreationPolicy
{
    public static final String BASIS =
            "return-quarantine-reservation-v2";
    public static final String ALLOCATION_POLICY = "FIXED_RETURN";
    public static final String DATA_SOURCE =
            "return-quarantine-reservation-v2";

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    private InvTransferReceiptDiscrepancyReturnShipmentCreationPolicy()
    {
    }

    public static PreparedCreation prepare(String requestId,
            String expectedPlanVersion, String basis,
            BigDecimal inputQuantity, Long actorUserId, String actorName,
            Instant occurredAt, InvTransferOrder order,
            InvTransferRevision revision, InvTransferDetail detail,
            InvWarehouseStockMode mode, PreparedPlan lockedPlan,
            ConsumptionPlan requestedConsumption)
    {
        Command command = command(requestId, expectedPlanVersion, basis,
                inputQuantity, actorUserId, actorName, occurredAt);
        if (lockedPlan == null || lockedPlan.fact() == null
                || lockedPlan.consumption() == null
                || !Objects.equals(expectedPlanVersion,
                        lockedPlan.planVersion()))
        {
            throw stalePlan();
        }
        InvTransferReceiptDiscrepancyReturnReservationLifecycleFact fact =
                lockedPlan.fact();
        requireOrder(order, fact);
        requireRevision(revision, fact);
        requireDetail(detail, fact, lockedPlan);
        requireMode(mode, fact);
        requireConsumption(command, lockedPlan.consumption(),
                requestedConsumption, fact.getSourceCostPrice());

        BigDecimal remaining = quantity(
                lockedPlan.consumption().quantity(),
                "退回发货规划剩余数量无效");
        if (command.quantity().compareTo(remaining) > 0)
        {
            throw new ServiceException("退回发货超过当前隔离预留剩余数量");
        }
        BigDecimal deliveredBefore = quantity(detail.getDeliveredQuantity(),
                "退回子调拨累计发货数量无效", true);
        BigDecimal deliveredAfter = deliveredBefore.add(command.quantity())
                .setScale(4);
        BigDecimal requested = quantity(detail.getQuantity(),
                "退回子调拨审批数量无效");
        if (deliveredAfter.compareTo(requested) > 0)
        {
            throw new ServiceException("退回发货超过子调拨审批数量");
        }
        BigDecimal receivedBefore = quantity(detail.getReceivedQuantity(),
                "退回子调拨累计收货数量无效", true);
        if (receivedBefore.compareTo(deliveredBefore) > 0)
        {
            throw new ServiceException("退回子调拨累计收货超过累计发货");
        }
        String statusAfter = InvTransferLifecyclePolicy.afterDelivery(
                order.getStatus(), deliveredAfter.compareTo(requested) == 0);
        BigDecimal amount = requestedConsumption.amount();
        return new PreparedCreation(command, fact, order, revision, detail,
                mode, requestedConsumption, command.quantity(), amount,
                deliveredBefore,
                deliveredAfter, receivedBefore, order.getStatus(),
                statusAfter, order.getVersion(), order.getVersion() + 1,
                shipmentNo(command.requestId(), command.occurredAt()));
    }

    private static Command command(String requestId,
            String expectedPlanVersion, String basis,
            BigDecimal inputQuantity, Long actorUserId, String actorName,
            Instant occurredAt)
    {
        String normalizedRequestId = requestId == null ? null
                : requestId.trim();
        String normalizedActor = actorName == null ? null : actorName.trim();
        if (normalizedRequestId == null || normalizedRequestId.length() < 8
                || normalizedRequestId.length() > 128
                || !normalizedRequestId.matches(
                        "[A-Za-z0-9][A-Za-z0-9._:-]{7,127}")
                || expectedPlanVersion == null
                || !expectedPlanVersion.matches("[a-f0-9]{64}")
                || !BASIS.equals(basis) || actorUserId == null
                || actorUserId <= 0 || normalizedActor == null
                || normalizedActor.isEmpty() || normalizedActor.length() > 64
                || !normalizedActor.equals(actorName)
                || occurredAt == null || !occurredAt.equals(
                        occurredAt.truncatedTo(ChronoUnit.SECONDS)))
        {
            throw new ServiceException("退回发货命令上下文无效");
        }
        return new Command(normalizedRequestId, expectedPlanVersion,
                quantity(inputQuantity, "退回发货数量无效"), actorUserId,
                normalizedActor, occurredAt);
    }

    private static void requireOrder(InvTransferOrder order,
            InvTransferReceiptDiscrepancyReturnReservationLifecycleFact fact)
    {
        Long sourceWarehouseId = order == null ? null
                : location(order.getFromWarehouseId(), order.getFromDeptId());
        Long targetWarehouseId = order == null ? null
                : location(order.getToWarehouseId(), order.getToDeptId());
        if (order == null || order.getTransferId() == null
                || !Objects.equals(order.getTransferId(),
                        fact.getChildTransferId())
                || order.getVersion() == null || order.getVersion() < 0
                || !Objects.equals(order.getSourceBusinessType(),
                        fact.getSourceBusinessType())
                || !Objects.equals(order.getSourceBusinessId(),
                        fact.getSourceBusinessId())
                || !Objects.equals(sourceWarehouseId,
                        fact.getSourceWarehouseId())
                || targetWarehouseId == null || targetWarehouseId <= 0
                || Objects.equals(sourceWarehouseId, targetWarehouseId)
                || order.getOrderNo() == null || order.getOrderNo().isBlank()
                || !java.util.Set.of(InvTransferTypes.WAREHOUSE,
                        InvTransferTypes.STORE_RETURN,
                        InvTransferTypes.CROSS_STORE).contains(
                                order.getTransferType())
                || (InvTransferTypes.STORE_RETURN.equals(
                        order.getTransferType())
                        && blank(order.getReturnReasonCode()))
                || !Objects.equals(order.getApprovalRound(),
                        fact.getReservationRound())
                || !Objects.equals(order.getStatus(), fact.getChildStatus())
                || !java.util.Set.of(InvStatusConstants.APPROVED,
                        InvStatusConstants.PARTIAL_DELIVERED).contains(
                                order.getStatus()))
        {
            throw new ServiceException("退回子调拨发货归属、状态或版本无效");
        }
    }

    private static void requireRevision(InvTransferRevision revision,
            InvTransferReceiptDiscrepancyReturnReservationLifecycleFact fact)
    {
        if (revision == null || revision.getRevisionId() == null
                || revision.getRevisionId() <= 0
                || !Objects.equals(revision.getTransferId(),
                        fact.getChildTransferId())
                || !Objects.equals(revision.getApprovalRound(),
                        fact.getReservationRound())
                || !InvTransferRevisionStatuses.APPROVED.equals(
                        revision.getStatus())
                || revision.getSnapshotHash() == null
                || !revision.getSnapshotHash().matches("[a-f0-9]{64}"))
        {
            throw new ServiceException("退回子调拨当前审批修订无效");
        }
    }

    private static void requireDetail(InvTransferDetail detail,
            InvTransferReceiptDiscrepancyReturnReservationLifecycleFact fact,
            PreparedPlan lockedPlan)
    {
        if (detail == null || !Objects.equals(detail.getDetailId(),
                fact.getChildTransferDetailId())
                || !Objects.equals(detail.getTransferId(),
                        fact.getChildTransferId())
                || !Objects.equals(detail.getItemType(), fact.getItemType())
                || !Objects.equals(detail.getItemId(), fact.getItemId())
                || !Objects.equals(detail.getProductId(),
                        fact.getProductId())
                || !same(detail.getQuantity(), fact.getRequestedQuantity())
                || !same(detail.getDeliveredQuantity(),
                        fact.getDeliveredQuantity())
                || !same(detail.getDeliveredQuantity(),
                        lockedPlan.consumption().consumedBefore())
                || blank(detail.getItemCode()) || blank(detail.getItemName())
                || blank(detail.getUnit()))
        {
            throw new ServiceException("退回子调拨唯一明细事实无效");
        }
    }

    private static void requireMode(InvWarehouseStockMode mode,
            InvTransferReceiptDiscrepancyReturnReservationLifecycleFact fact)
    {
        if (mode == null || !Objects.equals(mode.getWarehouseId(),
                fact.getSourceWarehouseId())
                || !InvTransferShipmentPlanComposer.canReadDetailStock(mode,
                        fact.getSourceWarehouseId())
                || blank(mode.getLastReconcileBatch()))
        {
            throw new ServiceException("退回来源仓明细库存权威模式无效");
        }
    }

    private static void requireConsumption(Command command,
            ConsumptionPlan available, ConsumptionPlan requested,
            BigDecimal sourceCostPrice)
    {
        BigDecimal costPrice = decimal(sourceCostPrice, 6,
                "退回发货来源成本无效", true);
        BigDecimal expectedAmount = command.quantity().multiply(costPrice)
                .setScale(6, RoundingMode.HALF_UP);
        int serialCount = requested == null || requested.serials() == null
                ? -1 : requested.serials().size();
        if (available == null || requested == null
                || !same(requested.quantity(), command.quantity())
                || !same(requested.amount(), expectedAmount)
                || !same(requested.reservedQuantity(),
                        available.reservedQuantity())
                || !same(requested.consumedBefore(),
                        available.consumedBefore())
                || !same(requested.releasedQuantity(),
                        available.releasedQuantity())
                || !Objects.equals(requested.statusBefore(),
                        available.statusBefore())
                || !Objects.equals(requested.versionBefore(),
                        available.versionBefore())
                || !sameTransitionBefore(requested.stock(),
                        available.stock())
                || !sameTransitionBefore(requested.balance(),
                        available.balance())
                || serialCount < 0
                || serialCount > available.serials().size()
                || !available.serials().subList(0, serialCount)
                        .equals(requested.serials()))
        {
            throw new ServiceException("退回发货本批消费预演无效");
        }
    }

    private static boolean sameTransitionBefore(QuantityTransition left,
            QuantityTransition right)
    {
        return left != null && right != null
                && same(left.currentBefore(), right.currentBefore())
                && same(left.availableBefore(), right.availableBefore())
                && same(left.lockedBefore(), right.lockedBefore())
                && same(left.quarantineBefore(), right.quarantineBefore())
                && same(left.totalCostBefore(), right.totalCostBefore())
                && Objects.equals(left.versionBefore(),
                        right.versionBefore());
    }

    private static BigDecimal quantity(BigDecimal value, String message)
    {
        return quantity(value, message, false);
    }

    private static BigDecimal quantity(BigDecimal value, String message,
            boolean zeroAllowed)
    {
        if (value == null || value.scale() > 4
                || (zeroAllowed ? value.signum() < 0
                        : value.signum() <= 0))
        {
            throw new ServiceException(message);
        }
        return value.setScale(4);
    }

    private static BigDecimal decimal(BigDecimal value, int scale,
            String message, boolean zeroAllowed)
    {
        if (value == null || value.scale() > scale
                || (zeroAllowed ? value.signum() < 0
                        : value.signum() <= 0))
        {
            throw new ServiceException(message);
        }
        return value.setScale(scale);
    }

    private static boolean same(BigDecimal left, BigDecimal right)
    {
        return left != null && right != null && left.compareTo(right) == 0;
    }

    private static Long location(Long warehouseId, Long deptId)
    {
        return warehouseId != null && warehouseId > 0 ? warehouseId : deptId;
    }

    private static boolean blank(String value)
    {
        return value == null || value.isBlank();
    }

    private static ServiceException stalePlan()
    {
        return new ServiceException("退回发货规划已变化，请重新获取后提交");
    }

    private static String shipmentNo(String requestId, Instant occurredAt)
    {
        String date = LocalDate.ofInstant(occurredAt, BUSINESS_ZONE)
                .format(DateTimeFormatter.BASIC_ISO_DATE);
        return "TSR2" + date + sha256("return-shipment-v1|" + requestId)
                .substring(0, 32);
    }

    private static String sha256(String value)
    {
        try
        {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(
                    value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(64);
            for (byte item : bytes)
            {
                result.append(String.format("%02x", item & 0xff));
            }
            return result.toString();
        }
        catch (NoSuchAlgorithmException impossible)
        {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    public record Command(String requestId, String expectedPlanVersion,
            BigDecimal quantity, Long actorUserId, String actorName,
            Instant occurredAt)
    {
    }

    public record PreparedCreation(Command command,
            InvTransferReceiptDiscrepancyReturnReservationLifecycleFact fact,
            InvTransferOrder order, InvTransferRevision revision,
            InvTransferDetail detail, InvWarehouseStockMode mode,
            ConsumptionPlan consumption,
            BigDecimal quantity, BigDecimal amount,
            BigDecimal deliveredBefore, BigDecimal deliveredAfter,
            BigDecimal receivedBefore, String statusBefore,
            String statusAfter, Long orderVersionBefore,
            Long orderVersionAfter, String shipmentNo)
    {
    }
}
