package com.erp.inventory.service.impl;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.erp.common.core.exception.ServiceException;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyReturnReservationLifecycleFact;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyReturnReservationLifecyclePolicy;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyReturnReservationLifecyclePolicy.ConsumptionPlan;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyReturnReservationLifecyclePolicy.PreparedConsumption;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyReturnReservationLifecyclePolicy.PreparedRelease;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyReturnReservationLifecyclePolicy.SerialTransition;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyReturnReservationSerialLifecycleFact;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyReturnShipmentPolicy;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyReturnShipmentPolicy.PreparedPlan;
import com.erp.inventory.domain.transfer.InvTransferReceiptGeneratedId;
import com.erp.inventory.domain.transfer.InvTransferReceiptTargetBalance;
import com.erp.inventory.domain.transfer.InvTransferReceiptTargetStock;
import com.erp.inventory.mapper.InvTransferReceiptDiscrepancyReturnReservationLifecycleMapper;

/** Unwired transactional owner of return quarantine consumption and release. */
@Service
public class
        InvTransferReceiptDiscrepancyReturnReservationLifecycleService
{
    private final
            InvTransferReceiptDiscrepancyReturnReservationLifecycleMapper
                    mapper;
    private final Clock clock;

    @Autowired
    public InvTransferReceiptDiscrepancyReturnReservationLifecycleService(
            InvTransferReceiptDiscrepancyReturnReservationLifecycleMapper
                    mapper)
    {
        this(mapper, Clock.systemUTC());
    }

    InvTransferReceiptDiscrepancyReturnReservationLifecycleService(
            InvTransferReceiptDiscrepancyReturnReservationLifecycleMapper
                    mapper,
            Clock clock)
    {
        this.mapper = mapper;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY,
            rollbackFor = Exception.class)
    public ConsumptionResult consume(String requestId, Long childTransferId,
            Long shipmentId, Long shipmentDetailId, BigDecimal quantity,
            String operator)
    {
        Locked locked = lock(childTransferId);
        PreparedConsumption prepared =
                InvTransferReceiptDiscrepancyReturnReservationLifecyclePolicy
                        .prepareConsumption(requestId, shipmentId,
                                shipmentDetailId, quantity, operator, now(),
                                locked.fact(), locked.stock(),
                                locked.balance(), locked.serials());
        return applyConsumption(prepared, childTransferId);
    }

    LockedConsumption lockForConsumption(Long childTransferId,
            String expectedPlanVersion, Instant occurredAt)
    {
        if (expectedPlanVersion == null
                || !expectedPlanVersion.matches("[a-f0-9]{64}"))
        {
            throw new ServiceException("退回发货规划版本格式无效");
        }
        Locked locked = lock(childTransferId);
        PreparedPlan prepared =
                InvTransferReceiptDiscrepancyReturnShipmentPolicy.prepare(
                        occurredAt, locked.fact(), locked.stock(),
                        locked.balance(), locked.serials());
        if (!Objects.equals(expectedPlanVersion, prepared.planVersion()))
        {
            throw new ServiceException("退回发货规划已变化，请重新获取后提交");
        }
        return new LockedConsumption(prepared, locked.stock(),
                locked.balance(), locked.serials());
    }

    ConsumptionResult consumeLocked(String requestId, Long shipmentId,
            Long shipmentDetailId, BigDecimal quantity, String operator,
            Instant occurredAt, LockedConsumption locked)
    {
        if (locked == null || locked.prepared() == null
                || locked.prepared().fact() == null)
        {
            throw new ServiceException("退回发货缺少已锁定隔离预留计划");
        }
        PreparedPlan plan = locked.prepared();
        PreparedConsumption prepared =
                InvTransferReceiptDiscrepancyReturnReservationLifecyclePolicy
                        .prepareConsumption(requestId, shipmentId,
                                shipmentDetailId, quantity, operator,
                                occurredAt, plan.fact(), locked.stock(),
                                locked.balance(), locked.serials());
        return applyConsumption(prepared, plan.fact().getChildTransferId());
    }

    ConsumptionPlan planLockedConsumption(BigDecimal quantity,
            LockedConsumption locked)
    {
        if (locked == null || locked.prepared() == null
                || locked.prepared().fact() == null)
        {
            throw new ServiceException("退回发货缺少已锁定隔离预留计划");
        }
        return InvTransferReceiptDiscrepancyReturnReservationLifecyclePolicy
                .planConsumption(quantity, locked.prepared().fact(),
                        locked.stock(), locked.balance(), locked.serials());
    }

    private ConsumptionResult applyConsumption(
            PreparedConsumption prepared, Long childTransferId)
    {
        requireOne(mapper.consumeStock(prepared),
                "退回隔离汇总冻结库存消费冲突");
        requireOne(mapper.consumeBalance(prepared),
                "退回隔离批次库位冻结余额消费冲突");
        for (SerialTransition serial : prepared.serials())
        {
            requireOne(mapper.consumePhysicalSerial(prepared, serial),
                    "退回隔离序列号消费冲突");
        }
        InvTransferReceiptGeneratedId generatedId =
                new InvTransferReceiptGeneratedId();
        requireOne(mapper.insertConsumption(prepared, generatedId),
                "退回隔离预留消费事件写入冲突");
        Long consumptionId = requireGeneratedId(generatedId,
                "退回隔离预留消费事件标识缺失");
        for (SerialTransition serial : prepared.serials())
        {
            requireOne(mapper.consumeSerialBinding(prepared, serial,
                    consumptionId), "退回隔离序列号消费证据冲突");
        }
        requireOne(mapper.updateConsumedReservation(prepared),
                "退回隔离预留累计消费冲突");
        verifyConsumption(prepared, lockFact(childTransferId));
        return new ConsumptionResult(consumptionId, prepared.fact()
                .getReservationId(), prepared.quantity(), prepared.amount(),
                prepared.statusAfter(), prepared.remainingAfter(),
                prepared.stock(), prepared.balance(), prepared.serials());
    }

    @Transactional(propagation = Propagation.MANDATORY,
            rollbackFor = Exception.class)
    public ReleaseResult releaseAllRemaining(String requestId,
            Long childTransferId, String reasonCode, String reasonReference,
            String operator)
    {
        Locked locked = lock(childTransferId);
        PreparedRelease prepared =
                InvTransferReceiptDiscrepancyReturnReservationLifecyclePolicy
                        .prepareRelease(requestId, reasonCode,
                                reasonReference, operator, now(),
                                locked.fact(), locked.stock(),
                                locked.balance(), locked.serials());
        requireOne(mapper.releaseStock(prepared),
                "退回隔离汇总冻结库存释放冲突");
        requireOne(mapper.releaseBalance(prepared),
                "退回隔离批次库位冻结余额释放冲突");
        for (SerialTransition serial : prepared.serials())
        {
            requireOne(mapper.releasePhysicalSerial(prepared, serial),
                    "退回隔离序列号释放冲突");
        }
        InvTransferReceiptGeneratedId generatedId =
                new InvTransferReceiptGeneratedId();
        requireOne(mapper.insertRelease(prepared, generatedId),
                "退回隔离预留释放事件写入冲突");
        Long releaseId = requireGeneratedId(generatedId,
                "退回隔离预留释放事件标识缺失");
        for (SerialTransition serial : prepared.serials())
        {
            requireOne(mapper.releaseSerialBinding(prepared, serial,
                    releaseId), "退回隔离序列号释放证据冲突");
        }
        requireOne(mapper.updateReleasedReservation(prepared),
                "退回隔离预留累计释放冲突");
        verifyRelease(prepared, lockFact(childTransferId));
        return new ReleaseResult(releaseId, prepared.fact()
                .getReservationId(), prepared.quantity(),
                prepared.statusAfter(), prepared.stock(),
                prepared.balance(), prepared.serials());
    }

    private Locked lock(Long childTransferId)
    {
        if (childTransferId == null || childTransferId <= 0)
        {
            throw new ServiceException("退回子调拨标识无效");
        }
        InvTransferReceiptDiscrepancyReturnReservationLifecycleFact fact =
                lockFact(childTransferId);
        InvTransferReceiptTargetStock stock = mapper.selectStockForUpdate(
                fact);
        InvTransferReceiptTargetBalance balance =
                mapper.selectBalanceForUpdate(fact);
        List<InvTransferReceiptDiscrepancyReturnReservationSerialLifecycleFact>
                serials = mapper.selectSerialsForUpdate(fact);
        return new Locked(fact, stock, balance,
                serials == null ? List.of() : List.copyOf(serials));
    }

    private InvTransferReceiptDiscrepancyReturnReservationLifecycleFact
            lockFact(Long childTransferId)
    {
        InvTransferReceiptDiscrepancyReturnReservationLifecycleFact fact =
                mapper.selectReservationForUpdate(childTransferId);
        if (fact == null)
        {
            throw new ServiceException("退回子调拨隔离预留事实不存在");
        }
        return fact;
    }

    private static void verifyConsumption(PreparedConsumption expected,
            InvTransferReceiptDiscrepancyReturnReservationLifecycleFact actual)
    {
        if (!Objects.equals(actual.getReservationId(),
                expected.fact().getReservationId())
                || !same(actual.getReservedQuantity(),
                        expected.reservedQuantity())
                || !same(actual.getConsumedQuantity(),
                        expected.consumedAfter())
                || !same(actual.getReleasedQuantity(),
                        expected.releasedQuantity())
                || !Objects.equals(actual.getStatus(),
                        expected.statusAfter())
                || !Objects.equals(actual.getVersion(),
                        expected.versionAfter()))
        {
            throw new ServiceException("退回隔离预留消费结果回读不一致");
        }
    }

    private static void verifyRelease(PreparedRelease expected,
            InvTransferReceiptDiscrepancyReturnReservationLifecycleFact actual)
    {
        if (!Objects.equals(actual.getReservationId(),
                expected.fact().getReservationId())
                || !same(actual.getReservedQuantity(),
                        expected.reservedQuantity())
                || !same(actual.getConsumedQuantity(),
                        expected.consumedQuantity())
                || !same(actual.getReleasedQuantity(),
                        expected.releasedAfter())
                || !Objects.equals(actual.getStatus(),
                        expected.statusAfter())
                || !Objects.equals(actual.getVersion(),
                        expected.versionAfter()))
        {
            throw new ServiceException("退回隔离预留释放结果回读不一致");
        }
    }

    private Instant now()
    {
        return clock.instant().truncatedTo(ChronoUnit.SECONDS);
    }

    private static Long requireGeneratedId(
            InvTransferReceiptGeneratedId generatedId, String message)
    {
        if (generatedId == null || generatedId.getValue() == null
                || generatedId.getValue() <= 0)
        {
            throw new ServiceException(message + "，业务操作已回滚");
        }
        return generatedId.getValue();
    }

    private static void requireOne(int rows, String message)
    {
        if (rows != 1)
        {
            throw new ServiceException(message + "，业务操作已回滚");
        }
    }

    private static boolean same(BigDecimal left, BigDecimal right)
    {
        return left != null && right != null && left.compareTo(right) == 0;
    }

    private record Locked(
            InvTransferReceiptDiscrepancyReturnReservationLifecycleFact fact,
            InvTransferReceiptTargetStock stock,
            InvTransferReceiptTargetBalance balance,
            List<InvTransferReceiptDiscrepancyReturnReservationSerialLifecycleFact>
                    serials)
    {
    }

    record LockedConsumption(PreparedPlan prepared,
            InvTransferReceiptTargetStock stock,
            InvTransferReceiptTargetBalance balance,
            List<InvTransferReceiptDiscrepancyReturnReservationSerialLifecycleFact>
                    serials)
    {
        LockedConsumption
        {
            serials = List.copyOf(serials);
        }
    }

    public record ConsumptionResult(Long consumptionId, Long reservationId,
            BigDecimal quantity, BigDecimal amount, String status,
            BigDecimal remainingQuantity,
            InvTransferReceiptDiscrepancyReturnReservationLifecyclePolicy
                    .QuantityTransition stock,
            InvTransferReceiptDiscrepancyReturnReservationLifecyclePolicy
                    .QuantityTransition balance,
            List<SerialTransition> serials)
    {
        public ConsumptionResult
        {
            serials = List.copyOf(serials);
        }
    }

    public record ReleaseResult(Long releaseId, Long reservationId,
            BigDecimal quantity, String status,
            InvTransferReceiptDiscrepancyReturnReservationLifecyclePolicy
                    .QuantityTransition stock,
            InvTransferReceiptDiscrepancyReturnReservationLifecyclePolicy
                    .QuantityTransition balance,
            List<SerialTransition> serials)
    {
        public ReleaseResult
        {
            serials = List.copyOf(serials);
        }
    }
}
