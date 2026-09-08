package com.erp.inventory.service.impl;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.erp.common.core.exception.ServiceException;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyReturnReservationLifecycleFact;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyReturnReservationSerialLifecycleFact;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyReturnShipmentPolicy;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyReturnShipmentPolicy.PreparedPlan;
import com.erp.inventory.domain.transfer.InvTransferReceiptTargetBalance;
import com.erp.inventory.domain.transfer.InvTransferReceiptTargetStock;
import com.erp.inventory.domain.vo.InvTransferReceiptDiscrepancyReturnShipmentPlanningVo;
import com.erp.inventory.domain.vo.InvTransferReceiptDiscrepancyReturnShipmentPlanningVo.Allocation;
import com.erp.inventory.domain.vo.InvTransferReceiptDiscrepancyReturnShipmentPlanningVo.Serial;
import com.erp.inventory.mapper.InvTransferReceiptDiscrepancyReturnShipmentPlanningMapper;

/** Unwired read-only owner of adjudication return shipment suggestions. */
@Service
public class
        InvTransferReceiptDiscrepancyReturnShipmentPlanningService
{
    private static final String DATA_SOURCE =
            "return-quarantine-reservation-v2";

    private final InvTransferReceiptDiscrepancyReturnShipmentPlanningMapper
            mapper;
    private final Clock clock;

    @Autowired
    public InvTransferReceiptDiscrepancyReturnShipmentPlanningService(
            InvTransferReceiptDiscrepancyReturnShipmentPlanningMapper mapper)
    {
        this(mapper, Clock.systemUTC());
    }

    InvTransferReceiptDiscrepancyReturnShipmentPlanningService(
            InvTransferReceiptDiscrepancyReturnShipmentPlanningMapper mapper,
            Clock clock)
    {
        this.mapper = mapper;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public InvTransferReceiptDiscrepancyReturnShipmentPlanningVo plan(
            Long childTransferId)
    {
        if (childTransferId == null || childTransferId <= 0)
        {
            throw new ServiceException("退回子调拨标识无效");
        }
        InvTransferReceiptDiscrepancyReturnReservationLifecycleFact fact =
                mapper.selectReservation(childTransferId);
        if (fact == null)
        {
            throw new ServiceException("退回子调拨隔离预留规划事实不存在");
        }
        InvTransferReceiptTargetStock stock = mapper.selectStock(fact);
        if (stock == null)
        {
            throw new ServiceException("退回子调拨汇总库存规划事实不存在");
        }
        InvTransferReceiptTargetBalance balance = mapper.selectBalance(fact);
        if (balance == null)
        {
            throw new ServiceException("退回子调拨批次库位规划事实不存在");
        }
        List<InvTransferReceiptDiscrepancyReturnReservationSerialLifecycleFact>
                serials = mapper.selectActiveSerials(fact);
        PreparedPlan prepared =
                InvTransferReceiptDiscrepancyReturnShipmentPolicy.prepare(
                        now(), fact, stock, balance,
                        serials == null ? List.of() : serials);
        return view(prepared);
    }

    private Instant now()
    {
        return clock.instant().truncatedTo(ChronoUnit.SECONDS);
    }

    private static InvTransferReceiptDiscrepancyReturnShipmentPlanningVo view(
            PreparedPlan prepared)
    {
        var fact = prepared.fact();
        var consumption = prepared.consumption();
        List<Serial> serials = consumption.serials().stream()
                .map(value -> new Serial(identifier(value.bindingId()),
                        identifier(value.receiptSerialId()),
                        identifier(value.serialId()), value.serialNo(),
                        value.lifecycleBefore(), value.physicalBefore(),
                        version(value.versionBefore())))
                .toList();
        Allocation allocation = new Allocation(
                identifier(fact.getBalanceId()), identifier(fact.getLotId()),
                identifier(fact.getLocationId()),
                decimal(consumption.quantity()), serials);
        return new InvTransferReceiptDiscrepancyReturnShipmentPlanningVo(
                identifier(fact.getChildTransferId()),
                identifier(fact.getChildTransferDetailId()),
                identifier(fact.getActionId()), prepared.planVersion(),
                fact.getChildStatus(), identifier(fact.getReservationId()),
                fact.getStatus(), version(fact.getVersion()),
                identifier(fact.getSourceWarehouseId()), fact.getItemType(),
                identifier(fact.getItemId()), nullableIdentifier(
                        fact.getProductId()), fact.getTrackingPolicy(),
                decimal(consumption.reservedQuantity()),
                decimal(consumption.consumedBefore()),
                decimal(consumption.releasedQuantity()),
                decimal(consumption.quantity()),
                decimal(consumption.quantity()),
                decimal(fact.getSourceCostPrice()),
                decimal(consumption.amount()), allocation, true, List.of(),
                prepared.generatedAt().toString(), DATA_SOURCE);
    }

    private static String identifier(Long value)
    {
        if (value == null || value <= 0)
        {
            throw new ServiceException("退回发货规划包含无效业务标识");
        }
        return value.toString();
    }

    private static String nullableIdentifier(Long value)
    {
        return value == null ? null : identifier(value);
    }

    private static String version(Long value)
    {
        if (value == null || value < 0)
        {
            throw new ServiceException("退回发货规划包含无效版本");
        }
        return value.toString();
    }

    private static String decimal(BigDecimal value)
    {
        if (value == null)
        {
            throw new ServiceException("退回发货规划包含无效数量或金额");
        }
        BigDecimal normalized = value.stripTrailingZeros();
        return normalized.signum() == 0 ? "0" : normalized.toPlainString();
    }
}
