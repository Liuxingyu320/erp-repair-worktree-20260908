package com.erp.inventory.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;
import com.erp.common.core.exception.ServiceException;
import com.erp.inventory.domain.transfer.InvShipmentPlanningItemKey;
import com.erp.inventory.domain.transfer.InvTransferReceiptDerivedLotKey;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyFacts;
import com.erp.inventory.domain.transfer.InvTransferReceiptGeneratedId;
import com.erp.inventory.domain.transfer.InvTransferShipmentReceiptPreparedMutation;
import com.erp.inventory.domain.vo.InvTransferReceiptDiscrepancyReturnReceiptCreationVo;
import com.erp.inventory.domain.vo.InvTransferShipmentReceiptCreationVo;
import com.erp.inventory.mapper.InvTransferShipmentReceiptPersistenceMapper;

/**
 * The only component allowed to execute a prepared V2 receipt mutation.
 *
 * <p>It owns no transaction and performs no reads. The creation service owns
 * the mandatory outer transaction. All key relations are resolved before the
 * first DML, and every single-row write must affect exactly one row.</p>
 */
@Service
public class InvTransferShipmentReceiptMutationExecutor
{
    private final InvTransferShipmentReceiptPersistenceMapper mapper;

    public InvTransferShipmentReceiptMutationExecutor(
            InvTransferShipmentReceiptPersistenceMapper mapper)
    {
        this.mapper = mapper;
    }

    public InvTransferShipmentReceiptCreationVo execute(
            InvTransferShipmentReceiptPreparedMutation mutation)
    {
        Resolution resolution = Resolution.resolve(mutation);
        InvTransferShipmentReceiptPreparedMutation.Receipt receipt =
                mutation.receipt();

        Long receiptId = insertReceipt(receipt);
        applyTargetStocks(mutation, receipt);
        Map<InvTransferReceiptDerivedLotKey, Long> lotIds =
                applyTargetLots(mutation, receipt);
        Map<InvTransferShipmentReceiptPreparedMutation.BalanceKey, Long>
                balanceIds = applyTargetBalances(mutation, receipt,
                        resolution, lotIds);
        Map<InvShipmentPlanningItemKey, Long> stockLogIds =
                insertStockLogs(mutation, receipt);
        Map<Long, Long> receiptAllocationIds = insertReceiptAllocations(
                mutation, receipt, receiptId, lotIds, balanceIds);
        insertDiscrepancyCases(mutation, receipt, receiptId,
                receiptAllocationIds);

        applySerials(mutation, receipt, receiptId, receiptAllocationIds,
                lotIds, balanceIds);
        applyAllocationProgress(mutation, receipt);
        applyShipmentDetailProgress(mutation, receipt, resolution);
        applyTransferDetailProgress(mutation, receipt);
        insertLedgers(mutation, receipt, receiptId, receiptAllocationIds,
                lotIds, balanceIds, stockLogIds);
        applyLifecycle(receipt, mutation.lifecycle());

        return new InvTransferShipmentReceiptCreationVo(
                identifier(receiptId), receipt.receiptNo(),
                identifier(receipt.shipmentId()),
                identifier(receipt.transferId()), receipt.receiptStatus(),
                receipt.receiptPlanVersion(),
                decimal(receipt.acceptedQuantity()),
                decimal(receipt.damagedQuantity()),
                decimal(receipt.shortageQuantity()),
                decimal(receipt.remainingQuantityAfter()),
                DateTimeFormatter.ISO_INSTANT.format(
                        receipt.createdTime()));
    }

    public InvTransferReceiptDiscrepancyReturnReceiptCreationVo
            executeReturnReceipt(
                    InvTransferShipmentReceiptPreparedMutation mutation)
    {
        if (mutation == null || mutation.receipt() == null
                || mutation.receipt().receiptSemantic()
                        != InvTransferShipmentReceiptPreparedMutation
                                .ReceiptSemantic.FIXED_RETURN)
        {
            throw new ServiceException("退回收货执行器拒绝非专属写意图");
        }
        InvTransferShipmentReceiptCreationVo result = execute(mutation);
        return new InvTransferReceiptDiscrepancyReturnReceiptCreationVo(
                result.receiptId(), result.receiptNo(), result.shipmentId(),
                result.transferId(), result.status(),
                result.receiptPlanVersion(), result.damagedQuantity(),
                result.shortageQuantity(), result.remainingQuantity(),
                result.createdTime(),
                "return-receipt-atomic-mutation-v1");
    }

    private Long insertReceipt(
            InvTransferShipmentReceiptPreparedMutation.Receipt receipt)
    {
        InvTransferReceiptGeneratedId id = new InvTransferReceiptGeneratedId();
        requireOne(mapper.insertReceipt(receipt, id), "收货单写入冲突");
        return requireGenerated(id, "收货单主键生成失败");
    }

    private void applyTargetStocks(
            InvTransferShipmentReceiptPreparedMutation mutation,
            InvTransferShipmentReceiptPreparedMutation.Receipt receipt)
    {
        for (InvTransferShipmentReceiptPreparedMutation.TargetStock stock
                : mutation.targetStocks())
        {
            if (stock.existingStockId() == null)
            {
                InvTransferReceiptGeneratedId id =
                        new InvTransferReceiptGeneratedId();
                requireOne(mapper.insertTargetStock(stock,
                        receipt.targetWarehouseId(), receipt.createBy(), id),
                        "目标汇总库存新增冲突");
                requireGenerated(id, "目标汇总库存主键生成失败");
            }
            else
            {
                requireOne(mapper.addTargetStock(stock.existingStockId(),
                        receipt.targetWarehouseId(),
                        receipt.targetWarehouseId(), stock.expectedVersion(),
                        stock.acceptedQuantity(), stock.damagedQuantity(),
                        stock.incomingCost(), receipt.createBy()),
                        "目标汇总库存条件更新冲突");
            }
        }
    }

    private Map<InvTransferReceiptDerivedLotKey, Long> applyTargetLots(
            InvTransferShipmentReceiptPreparedMutation mutation,
            InvTransferShipmentReceiptPreparedMutation.Receipt receipt)
    {
        Map<InvTransferReceiptDerivedLotKey, Long> result =
                new LinkedHashMap<>();
        for (InvTransferShipmentReceiptPreparedMutation.TargetLot lot
                : mutation.targetLots())
        {
            Long lotId = lot.existingLotId();
            if (lotId == null)
            {
                InvTransferReceiptGeneratedId id =
                        new InvTransferReceiptGeneratedId();
                requireOne(mapper.insertTargetLot(lot, receipt.transferId(),
                        receipt.createBy(), id), "目标派生批次新增冲突");
                lotId = requireGenerated(id, "目标派生批次主键生成失败");
            }
            result.put(lot.key(), lotId);
        }
        return result;
    }

    private Map<InvTransferShipmentReceiptPreparedMutation.BalanceKey, Long>
            applyTargetBalances(
                    InvTransferShipmentReceiptPreparedMutation mutation,
                    InvTransferShipmentReceiptPreparedMutation.Receipt
                            receipt,
                    Resolution resolution,
                    Map<InvTransferReceiptDerivedLotKey, Long> lotIds)
    {
        Map<InvTransferShipmentReceiptPreparedMutation.BalanceKey, Long>
                result = new LinkedHashMap<>();
        for (InvTransferShipmentReceiptPreparedMutation.TargetBalance balance
                : mutation.targetBalances())
        {
            Long lotId = required(lotIds, balance.key().lotKey(),
                    "目标余额缺少派生批次主键");
            Long balanceId = balance.existingBalanceId();
            if (balanceId == null)
            {
                InvTransferReceiptGeneratedId id =
                        new InvTransferReceiptGeneratedId();
                Long productId = resolution.targetLots()
                        .get(balance.key().lotKey()).productId();
                requireOne(mapper.insertTargetBalance(balance, lotId,
                        productId, receipt.targetWarehouseId(),
                        receipt.createBy(), id), "目标明细库存新增冲突");
                balanceId = requireGenerated(id,
                        "目标明细库存主键生成失败");
            }
            else
            {
                requireOne(mapper.addTargetBalance(balanceId,
                        receipt.targetWarehouseId(), lotId,
                        balance.key().locationId(), balance.expectedVersion(),
                        balance.availableQuantity(),
                        balance.quarantineQuantity(), balance.incomingCost(),
                        receipt.createBy()), "目标明细库存条件更新冲突");
            }
            result.put(balance.key(), balanceId);
        }
        return result;
    }

    private Map<InvShipmentPlanningItemKey, Long> insertStockLogs(
            InvTransferShipmentReceiptPreparedMutation mutation,
            InvTransferShipmentReceiptPreparedMutation.Receipt receipt)
    {
        Map<InvShipmentPlanningItemKey, Long> result =
                new LinkedHashMap<>();
        for (InvTransferShipmentReceiptPreparedMutation.StockLog log
                : mutation.stockLogs())
        {
            InvTransferReceiptGeneratedId id =
                    new InvTransferReceiptGeneratedId();
            requireOne(mapper.insertStockLog(log, receipt, id),
                    "目标库存汇总流水写入冲突");
            result.put(log.itemKey(), requireGenerated(id,
                    "目标库存汇总流水主键生成失败"));
        }
        return result;
    }

    private Map<Long, Long> insertReceiptAllocations(
            InvTransferShipmentReceiptPreparedMutation mutation,
            InvTransferShipmentReceiptPreparedMutation.Receipt receipt,
            Long receiptId,
            Map<InvTransferReceiptDerivedLotKey, Long> lotIds,
            Map<InvTransferShipmentReceiptPreparedMutation.BalanceKey, Long>
                    balanceIds)
    {
        Map<Long, Long> result = new LinkedHashMap<>();
        for (InvTransferShipmentReceiptPreparedMutation.Allocation allocation
                : mutation.allocations())
        {
            Long acceptedLotId = nullable(lotIds,
                    allocation.acceptedLotKey());
            Long damagedLotId = nullable(lotIds,
                    allocation.damagedLotKey());
            Long acceptedBalanceId = balanceId(balanceIds,
                    allocation.acceptedLotKey(),
                    allocation.acceptedLocationId());
            Long damagedBalanceId = balanceId(balanceIds,
                    allocation.damagedLotKey(),
                    allocation.quarantineLocationId());
            InvTransferReceiptGeneratedId id =
                    new InvTransferReceiptGeneratedId();
            requireOne(mapper.insertReceiptAllocation(allocation, receiptId,
                    receipt.shipmentId(), acceptedLotId, acceptedBalanceId,
                    damagedLotId, damagedBalanceId, receipt.createBy(), id),
                    "收货分配审计写入冲突");
            result.put(allocation.shipmentAllocationId(),
                    requireGenerated(id, "收货分配审计主键生成失败"));
        }
        return result;
    }

    private void insertDiscrepancyCases(
            InvTransferShipmentReceiptPreparedMutation mutation,
            InvTransferShipmentReceiptPreparedMutation.Receipt receipt,
            Long receiptId, Map<Long, Long> receiptAllocationIds)
    {
        for (InvTransferShipmentReceiptPreparedMutation.DiscrepancyCase value
                : mutation.discrepancyCases())
        {
            Long receiptAllocationId = required(receiptAllocationIds,
                    value.shipmentAllocationId(),
                    "差异事项缺少收货分配主键");
            InvTransferReceiptGeneratedId id =
                    new InvTransferReceiptGeneratedId();
            requireOne(mapper.insertReceiptDiscrepancyCase(value, receipt,
                    receiptId, receiptAllocationId, id),
                    "收货差异事项写入冲突");
            requireGenerated(id, "收货差异事项主键生成失败");
        }
    }

    private void applySerials(
            InvTransferShipmentReceiptPreparedMutation mutation,
            InvTransferShipmentReceiptPreparedMutation.Receipt receipt,
            Long receiptId, Map<Long, Long> receiptAllocationIds,
            Map<InvTransferReceiptDerivedLotKey, Long> lotIds,
            Map<InvTransferShipmentReceiptPreparedMutation.BalanceKey, Long>
                    balanceIds)
    {
        for (InvTransferShipmentReceiptPreparedMutation.Serial serial
                : mutation.serials())
        {
            Long receiptAllocationId = required(receiptAllocationIds,
                    serial.shipmentAllocationId(),
                    "序列号审计缺少收货分配主键");
            Long targetLotId = nullable(lotIds, serial.targetLotKey());
            Long targetBalanceId = balanceId(balanceIds,
                    serial.targetLotKey(), serial.targetLocationId());
            if (!"shortage".equals(serial.disposition()))
            {
                requireOne(mapper.moveSerialToTarget(serial.serialId(),
                        serial.sourceWarehouseId(), serial.sourceBalanceId(),
                        serial.sourceLotId(), serial.sourceLocationId(),
                        receipt.targetWarehouseId(), targetBalanceId,
                        targetLotId, serial.targetLocationId(),
                        serial.statusAfter(), receipt.createBy()),
                        "序列号目标迁移冲突");
            }
            requireOne(mapper.insertReceiptSerial(serial, receiptId,
                    receipt.shipmentId(), receiptAllocationId,
                    targetBalanceId, targetLotId, receipt.createBy()),
                    "序列号收货审计写入冲突");
        }
    }

    private void applyAllocationProgress(
            InvTransferShipmentReceiptPreparedMutation mutation,
            InvTransferShipmentReceiptPreparedMutation.Receipt receipt)
    {
        for (InvTransferShipmentReceiptPreparedMutation.Allocation allocation
                : mutation.allocations())
        {
            requireOne(mapper.updateAllocationProgress(
                    allocation.shipmentAllocationId(), receipt.shipmentId(),
                    allocation.expectedReceiptVersion(),
                    allocation.acceptedQuantity(),
                    allocation.damagedQuantity(),
                    allocation.shortageQuantity()), "来源分配收货累计冲突");
        }
    }

    private void applyShipmentDetailProgress(
            InvTransferShipmentReceiptPreparedMutation mutation,
            InvTransferShipmentReceiptPreparedMutation.Receipt receipt,
            Resolution resolution)
    {
        for (InvTransferShipmentReceiptPreparedMutation.DetailProgress detail
                : mutation.shipmentDetails())
        {
            Long transferDetailId = required(
                    resolution.transferDetailByShipmentDetail(),
                    detail.detailId(), "发货明细缺少调拨明细映射");
            requireOne(mapper.addShipmentDetailFulfilled(detail,
                    receipt.shipmentId(), receipt.transferId(),
                    transferDetailId), "发货明细收货履约累计冲突");
        }
    }

    private void applyTransferDetailProgress(
            InvTransferShipmentReceiptPreparedMutation mutation,
            InvTransferShipmentReceiptPreparedMutation.Receipt receipt)
    {
        for (InvTransferShipmentReceiptPreparedMutation.DetailProgress detail
                : mutation.transferDetails())
        {
            requireOne(mapper.addTransferDetailFulfilled(detail,
                    receipt.transferId()), "调拨明细收货履约累计冲突");
        }
    }

    private void insertLedgers(
            InvTransferShipmentReceiptPreparedMutation mutation,
            InvTransferShipmentReceiptPreparedMutation.Receipt receipt,
            Long receiptId, Map<Long, Long> receiptAllocationIds,
            Map<InvTransferReceiptDerivedLotKey, Long> lotIds,
            Map<InvTransferShipmentReceiptPreparedMutation.BalanceKey, Long>
                    balanceIds,
            Map<InvShipmentPlanningItemKey, Long> stockLogIds)
    {
        for (InvTransferShipmentReceiptPreparedMutation.Ledger ledger
                : mutation.ledgers())
        {
            Long receiptAllocationId = required(receiptAllocationIds,
                    ledger.shipmentAllocationId(),
                    "收货台账缺少收货分配主键");
            Long targetLotId = required(lotIds,
                    ledger.targetBalanceKey().lotKey(),
                    "收货台账缺少目标批次主键");
            Long targetBalanceId = required(balanceIds,
                    ledger.targetBalanceKey(),
                    "收货台账缺少目标余额主键");
            Long stockLogId = required(stockLogIds, ledger.itemKey(),
                    "收货台账缺少汇总流水主键");
            requireOne(mapper.insertReceiptLedger(ledger, receipt, receiptId,
                    receiptAllocationId, targetBalanceId, targetLotId,
                    stockLogId), "收货明细台账写入冲突");
        }
    }

    private void applyLifecycle(
            InvTransferShipmentReceiptPreparedMutation.Receipt receipt,
            InvTransferShipmentReceiptPreparedMutation.Lifecycle lifecycle)
    {
        requireOne(mapper.updateShipmentLifecycle(receipt.shipmentId(),
                receipt.transferId(), lifecycle.expectedShipmentStatus(),
                lifecycle.shipmentStatusAfter(), receipt.createBy(),
                receipt.arrivedTime()), "发货批次收货状态更新冲突");
        requireOne(mapper.updateTransferLifecycle(receipt.transferId(),
                lifecycle.expectedTransferStatus(),
                lifecycle.expectedTransferVersion(),
                lifecycle.transferStatusAfter(), receipt.arrivedTime(),
                receipt.createBy()), "调拨单收货状态更新冲突");
        requireOne(mapper.insertTransferStatusLog(receipt.transferId(),
                lifecycle.expectedTransferStatus(),
                lifecycle.transferStatusAfter(), lifecycle.action(),
                receipt.operatorUserId(), receipt.operatorName(),
                "V2 transfer receipt " + receipt.receiptNo()),
                "调拨收货状态日志写入冲突");
    }

    private static Long balanceId(
            Map<InvTransferShipmentReceiptPreparedMutation.BalanceKey, Long>
                    balanceIds,
            InvTransferReceiptDerivedLotKey lotKey, Long locationId)
    {
        return lotKey == null ? null : required(balanceIds,
                new InvTransferShipmentReceiptPreparedMutation.BalanceKey(
                        lotKey, locationId), "缺少目标余额主键");
    }

    private static <K> Long nullable(Map<K, Long> values, K key)
    {
        return key == null ? null : required(values, key, "缺少目标主键");
    }

    private static <K, V> V required(Map<K, V> values, K key,
            String message)
    {
        V value = values.get(key);
        if (value == null)
        {
            throw new ServiceException(message);
        }
        return value;
    }

    private static Long requireGenerated(InvTransferReceiptGeneratedId id,
            String message)
    {
        if (id.getValue() == null || id.getValue() <= 0)
        {
            throw new ServiceException(message);
        }
        return id.getValue();
    }

    private static void requireOne(int rows, String message)
    {
        if (rows != 1)
        {
            throw new ServiceException(message + "，业务操作已回滚");
        }
    }

    private static String identifier(Long value)
    {
        return Long.toString(value);
    }

    private static String decimal(BigDecimal value)
    {
        BigDecimal normalized = value.stripTrailingZeros();
        return normalized.signum() == 0 ? "0" : normalized.toPlainString();
    }

    private record Resolution(
            Map<InvTransferReceiptDerivedLotKey,
                    InvTransferShipmentReceiptPreparedMutation.TargetLot>
                            targetLots,
            Map<Long, Long> transferDetailByShipmentDetail)
    {
        private static Resolution resolve(
                InvTransferShipmentReceiptPreparedMutation mutation)
        {
            requireHeader(mutation);
            Map<InvShipmentPlanningItemKey,
                    InvTransferShipmentReceiptPreparedMutation.TargetStock>
                            stocks = uniqueStocks(mutation.targetStocks());
            Map<InvShipmentPlanningItemKey,
                    InvTransferShipmentReceiptPreparedMutation.StockLog>
                            logs = uniqueLogs(mutation.stockLogs());
            Map<InvTransferReceiptDerivedLotKey,
                    InvTransferShipmentReceiptPreparedMutation.TargetLot>
                            lots = uniqueLots(mutation.targetLots());
            Map<InvTransferShipmentReceiptPreparedMutation.BalanceKey,
                    InvTransferShipmentReceiptPreparedMutation.TargetBalance>
                            balances = uniqueBalances(
                                    mutation.targetBalances(), lots);
            Map<Long, InvTransferShipmentReceiptPreparedMutation.Allocation>
                    allocations = uniqueAllocations(mutation.allocations());
            validateSemantic(mutation.receipt(), allocations);

            Set<InvShipmentPlanningItemKey> expectedItems =
                    new LinkedHashSet<>();
            Set<InvTransferReceiptDerivedLotKey> expectedLots =
                    new LinkedHashSet<>();
            Set<InvTransferShipmentReceiptPreparedMutation.BalanceKey>
                    expectedBalances = new LinkedHashSet<>();
            Map<InvTransferShipmentReceiptPreparedMutation.BalanceKey,
                    BigDecimal> runningBalances = new LinkedHashMap<>();
            balances.forEach((key, value) -> runningBalances.put(key,
                    value.currentQuantityBefore()));
            Map<Long, Long> detailRelations = new LinkedHashMap<>();

            for (InvTransferShipmentReceiptPreparedMutation.Allocation value
                    : mutation.allocations())
            {
                Long previous = detailRelations.putIfAbsent(
                        value.shipmentDetailId(), value.transferDetailId());
                if (previous != null
                        && !Objects.equals(previous, value.transferDetailId()))
                {
                    throw new ServiceException("发货明细映射到多个调拨明细");
                }
                if (value.acceptedQuantity().signum() > 0
                        || value.damagedQuantity().signum() > 0)
                {
                    expectedItems.add(value.itemKey());
                }
                validateDisposition(value.acceptedQuantity(),
                        value.acceptedLocationId(), value.acceptedLotKey(),
                        value.acceptedBalanceQuantityBefore(),
                        value.acceptedBalanceQuantityAfter(),
                        value.acceptedBalanceVersionBefore(),
                        value.acceptedBalanceVersionAfter(), value, balances,
                        expectedLots, expectedBalances, runningBalances,
                        "accepted");
                validateDisposition(value.damagedQuantity(),
                        value.quarantineLocationId(), value.damagedLotKey(),
                        value.damagedBalanceQuantityBefore(),
                        value.damagedBalanceQuantityAfter(),
                        value.damagedBalanceVersionBefore(),
                        value.damagedBalanceVersionAfter(), value, balances,
                        expectedLots, expectedBalances, runningBalances,
                        "damaged");
            }
            if (!expectedItems.equals(stocks.keySet())
                    || !expectedItems.equals(logs.keySet())
                    || !expectedLots.equals(lots.keySet())
                    || !expectedBalances.equals(balances.keySet()))
            {
                throw new ServiceException("目标库存写意图关系不完整");
            }
            validateStockLogs(stocks, logs);
            balances.forEach((key, value) -> requireSame(
                    value.currentQuantityAfter(), runningBalances.get(key),
                    "目标余额分配累计不守恒"));
            validateSerials(mutation.serials(), allocations, balances);
            validateLedgers(mutation.ledgers(), allocations, balances, logs);
            validateDiscrepancyCases(mutation.receipt(),
                    mutation.discrepancyCases(), allocations);
            validateDetails(mutation, allocations,
                    mutation.receipt().receiptSemantic());
            return new Resolution(Map.copyOf(lots),
                    Map.copyOf(detailRelations));
        }

        private static void requireHeader(
                InvTransferShipmentReceiptPreparedMutation mutation)
        {
            if (mutation == null || mutation.receipt() == null
                    || mutation.lifecycle() == null
                    || !positive(mutation.receipt().shipmentId())
                    || !positive(mutation.receipt().transferId())
                    || !positive(mutation.receipt().targetWarehouseId())
                    || mutation.receipt().receiptSemantic() == null
                    || mutation.receipt().createdTime() == null
                    || mutation.receipt().arrivedTime() == null
                    || mutation.allocations() == null
                    || mutation.allocations().isEmpty())
            {
                throw new ServiceException("收货原子变更缺少可信头事实");
            }
        }

        private static Map<InvShipmentPlanningItemKey,
                InvTransferShipmentReceiptPreparedMutation.TargetStock>
                uniqueStocks(List<InvTransferShipmentReceiptPreparedMutation
                        .TargetStock> values)
        {
            Map<InvShipmentPlanningItemKey,
                    InvTransferShipmentReceiptPreparedMutation.TargetStock>
                            result = new LinkedHashMap<>();
            for (InvTransferShipmentReceiptPreparedMutation.TargetStock value
                    : values)
            {
                if (value == null || value.itemKey() == null
                        || result.putIfAbsent(value.itemKey(), value) != null)
                {
                    throw new ServiceException("目标汇总库存写意图重复或无效");
                }
            }
            return result;
        }

        private static Map<InvShipmentPlanningItemKey,
                InvTransferShipmentReceiptPreparedMutation.StockLog>
                uniqueLogs(List<InvTransferShipmentReceiptPreparedMutation
                        .StockLog> values)
        {
            Map<InvShipmentPlanningItemKey,
                    InvTransferShipmentReceiptPreparedMutation.StockLog>
                            result = new LinkedHashMap<>();
            for (InvTransferShipmentReceiptPreparedMutation.StockLog value
                    : values)
            {
                if (value == null || value.itemKey() == null
                        || result.putIfAbsent(value.itemKey(), value) != null)
                {
                    throw new ServiceException("目标汇总流水写意图重复或无效");
                }
            }
            return result;
        }

        private static Map<InvTransferReceiptDerivedLotKey,
                InvTransferShipmentReceiptPreparedMutation.TargetLot>
                uniqueLots(List<InvTransferShipmentReceiptPreparedMutation
                        .TargetLot> values)
        {
            Map<InvTransferReceiptDerivedLotKey,
                    InvTransferShipmentReceiptPreparedMutation.TargetLot>
                            result = new LinkedHashMap<>();
            for (InvTransferShipmentReceiptPreparedMutation.TargetLot value
                    : values)
            {
                if (value == null || value.key() == null
                        || result.putIfAbsent(value.key(), value) != null)
                {
                    throw new ServiceException("目标派生批次写意图重复或无效");
                }
            }
            return result;
        }

        private static Map<InvTransferShipmentReceiptPreparedMutation
                .BalanceKey,
                InvTransferShipmentReceiptPreparedMutation.TargetBalance>
                uniqueBalances(List<InvTransferShipmentReceiptPreparedMutation
                        .TargetBalance> values,
                        Map<InvTransferReceiptDerivedLotKey,
                                InvTransferShipmentReceiptPreparedMutation
                                        .TargetLot> lots)
        {
            Map<InvTransferShipmentReceiptPreparedMutation.BalanceKey,
                    InvTransferShipmentReceiptPreparedMutation.TargetBalance>
                            result = new LinkedHashMap<>();
            for (InvTransferShipmentReceiptPreparedMutation.TargetBalance
                    value : values)
            {
                if (value == null || value.key() == null
                        || !lots.containsKey(value.key().lotKey())
                        || result.putIfAbsent(value.key(), value) != null)
                {
                    throw new ServiceException("目标余额写意图重复或无效");
                }
            }
            return result;
        }

        private static Map<Long,
                InvTransferShipmentReceiptPreparedMutation.Allocation>
                uniqueAllocations(List<InvTransferShipmentReceiptPreparedMutation
                        .Allocation> values)
        {
            Map<Long, InvTransferShipmentReceiptPreparedMutation.Allocation>
                    result = new LinkedHashMap<>();
            for (InvTransferShipmentReceiptPreparedMutation.Allocation value
                    : values)
            {
                if (value == null || !positive(value.shipmentAllocationId())
                        || !positive(value.shipmentDetailId())
                        || !positive(value.transferDetailId())
                        || value.itemKey() == null
                        || !quantity(value.acceptedQuantity())
                        || !quantity(value.damagedQuantity())
                        || !quantity(value.shortageQuantity())
                        || value.acceptedQuantity().add(
                                value.damagedQuantity()).add(
                                        value.shortageQuantity()).signum() <= 0
                        || result.putIfAbsent(
                                value.shipmentAllocationId(), value) != null)
                {
                    throw new ServiceException("收货分配写意图重复或无效");
                }
            }
            return result;
        }

        private static void validateSemantic(
                InvTransferShipmentReceiptPreparedMutation.Receipt receipt,
                Map<Long, InvTransferShipmentReceiptPreparedMutation
                        .Allocation> allocations)
        {
            BigDecimal accepted = BigDecimal.ZERO;
            BigDecimal damaged = BigDecimal.ZERO;
            BigDecimal shortage = BigDecimal.ZERO;
            boolean fixedReturn = receipt.receiptSemantic()
                    == InvTransferShipmentReceiptPreparedMutation
                            .ReceiptSemantic.FIXED_RETURN;
            for (InvTransferShipmentReceiptPreparedMutation.Allocation value
                    : allocations.values())
            {
                accepted = accepted.add(value.acceptedQuantity());
                damaged = damaged.add(value.damagedQuantity());
                shortage = shortage.add(value.shortageQuantity());
                if (fixedReturn && (value.acceptedQuantity().signum() != 0
                        || (value.shortageQuantity().signum() == 0
                                && (value.discrepancyNote() != null
                                        || value.attachmentRefs() != null))))
                {
                    throw new ServiceException("专属退回收货写意图混入普通处置");
                }
            }
            if (!quantity(receipt.acceptedQuantity())
                    || !quantity(receipt.damagedQuantity())
                    || !quantity(receipt.shortageQuantity())
                    || !quantity(receipt.remainingQuantityAfter()))
            {
                throw new ServiceException("收货语义与原子写意图合计不一致");
            }
            boolean discrepancy = shortage.signum() > 0
                    || (!fixedReturn && damaged.signum() > 0);
            String expectedStatus = discrepancy ? "discrepancy"
                    : receipt.remainingQuantityAfter().signum() == 0
                            ? "completed" : "partial";
            if (!same(accepted, receipt.acceptedQuantity())
                    || !same(damaged, receipt.damagedQuantity())
                    || !same(shortage, receipt.shortageQuantity())
                    || (fixedReturn
                            && receipt.acceptedQuantity().signum() != 0)
                    || !Objects.equals(expectedStatus,
                            receipt.receiptStatus()))
            {
                throw new ServiceException("收货语义与原子写意图合计不一致");
            }
        }

        private static void validateDisposition(BigDecimal quantity,
                Long locationId, InvTransferReceiptDerivedLotKey lotKey,
                BigDecimal before, BigDecimal after, Long versionBefore,
                Long versionAfter,
                InvTransferShipmentReceiptPreparedMutation.Allocation
                        allocation,
                Map<InvTransferShipmentReceiptPreparedMutation.BalanceKey,
                        InvTransferShipmentReceiptPreparedMutation
                                .TargetBalance> balances,
                Set<InvTransferReceiptDerivedLotKey> expectedLots,
                Set<InvTransferShipmentReceiptPreparedMutation.BalanceKey>
                        expectedBalances,
                Map<InvTransferShipmentReceiptPreparedMutation.BalanceKey,
                        BigDecimal> runningBalances,
                String disposition)
        {
            if (quantity.signum() == 0)
            {
                if (locationId != null || lotKey != null || before != null
                        || after != null || versionBefore != null
                        || versionAfter != null)
                {
                    throw new ServiceException("零数量处置夹带目标库存写意图");
                }
                return;
            }
            InvTransferShipmentReceiptPreparedMutation.BalanceKey key =
                    new InvTransferShipmentReceiptPreparedMutation.BalanceKey(
                            lotKey, locationId);
            InvTransferShipmentReceiptPreparedMutation.TargetBalance balance =
                    balances.get(key);
            if (lotKey == null || !disposition.equals(lotKey.disposition())
                    || !Objects.equals(lotKey.itemType(),
                            allocation.itemKey().itemType())
                    || !Objects.equals(lotKey.itemId(),
                            allocation.itemKey().itemId())
                    || balance == null
                    || !same(before, runningBalances.get(key))
                    || !same(after, before.add(quantity))
                    || !Objects.equals(versionBefore,
                            balance.expectedVersion())
                    || !Objects.equals(versionAfter,
                            balance.existingBalanceId() == null ? 0L
                                    : balance.expectedVersion() + 1))
            {
                throw new ServiceException("分配目标余额写意图不连续");
            }
            runningBalances.put(key, after);
            expectedLots.add(lotKey);
            expectedBalances.add(key);
        }

        private static void validateStockLogs(
                Map<InvShipmentPlanningItemKey,
                        InvTransferShipmentReceiptPreparedMutation
                                .TargetStock> stocks,
                Map<InvShipmentPlanningItemKey,
                        InvTransferShipmentReceiptPreparedMutation.StockLog>
                                logs)
        {
            stocks.forEach((key, stock) -> {
                InvTransferShipmentReceiptPreparedMutation.StockLog log =
                        logs.get(key);
                if (log == null
                        || !Objects.equals(stock.productId(), log.productId())
                        || !same(stock.currentQuantityBefore(),
                                log.beforeQuantity())
                        || !same(stock.currentQuantityAfter(),
                                log.afterQuantity())
                        || !same(stock.acceptedQuantity()
                                .add(stock.damagedQuantity()),
                                log.changeQuantity()))
                {
                    throw new ServiceException("目标汇总流水与库存写意图不一致");
                }
            });
        }

        private static void validateSerials(
                List<InvTransferShipmentReceiptPreparedMutation.Serial>
                        serials,
                Map<Long, InvTransferShipmentReceiptPreparedMutation
                        .Allocation> allocations,
                Map<InvTransferShipmentReceiptPreparedMutation.BalanceKey,
                        InvTransferShipmentReceiptPreparedMutation
                                .TargetBalance> balances)
        {
            Set<Long> serialIds = new LinkedHashSet<>();
            Map<String, Integer> counts = new LinkedHashMap<>();
            for (InvTransferShipmentReceiptPreparedMutation.Serial value
                    : serials)
            {
                if (value == null)
                {
                    throw new ServiceException("序列号收货写意图不可核验");
                }
                InvTransferShipmentReceiptPreparedMutation.Allocation source =
                        allocations.get(value.shipmentAllocationId());
                boolean shortage = "shortage".equals(value.disposition());
                boolean accepted = "accepted".equals(value.disposition());
                boolean damaged = "damaged".equals(value.disposition());
                InvTransferShipmentReceiptPreparedMutation.BalanceKey key =
                        shortage ? null
                                : new InvTransferShipmentReceiptPreparedMutation
                                        .BalanceKey(value.targetLotKey(),
                                                value.targetLocationId());
                if (source == null || !"serial".equals(
                        source.trackingPolicy())
                        || (!accepted && !damaged && !shortage)
                        || !positive(value.shipmentSerialId())
                        || !positive(value.serialId())
                        || !serialIds.add(value.serialId())
                        || value.serialNo() == null
                        || value.serialNo().isBlank()
                        || !positive(value.sourceWarehouseId())
                        || !Objects.equals(source.sourceBalanceId(),
                                value.sourceBalanceId())
                        || !Objects.equals(source.sourceLotId(),
                                value.sourceLotId())
                        || !Objects.equals(source.sourceLocationId(),
                                value.sourceLocationId())
                        || (shortage && (value.targetLotKey() != null
                                || value.targetLocationId() != null))
                        || (!shortage && (!balances.containsKey(key)
                                || value.targetLotKey() == null
                                || !Objects.equals(value.disposition(),
                                        value.targetLotKey().disposition())
                                || !Objects.equals(source.itemKey().itemType(),
                                        value.targetLotKey().itemType())
                                || !Objects.equals(source.itemKey().itemId(),
                                        value.targetLotKey().itemId())))
                        || !"shipped".equals(value.statusBefore())
                        || !Objects.equals(shortage ? "shipped"
                                : accepted ? "available" : "quarantine",
                                value.statusAfter()))
                {
                    throw new ServiceException("序列号收货写意图不可核验");
                }
                counts.merge(value.shipmentAllocationId() + "|"
                        + value.disposition(), 1, Integer::sum);
            }
            for (InvTransferShipmentReceiptPreparedMutation.Allocation value
                    : allocations.values())
            {
                if ("lot".equals(value.trackingPolicy()))
                {
                    if (counts.keySet().stream().anyMatch(key -> key
                            .startsWith(value.shipmentAllocationId() + "|")))
                    {
                        throw new ServiceException("批次跟踪分配夹带序列号写意图");
                    }
                    continue;
                }
                if (!"serial".equals(value.trackingPolicy())
                        || count(value.acceptedQuantity()) != counts
                                .getOrDefault(value.shipmentAllocationId()
                                        + "|accepted", 0)
                        || count(value.damagedQuantity()) != counts
                                .getOrDefault(value.shipmentAllocationId()
                                        + "|damaged", 0)
                        || count(value.shortageQuantity()) != counts
                                .getOrDefault(value.shipmentAllocationId()
                                        + "|shortage", 0))
                {
                    throw new ServiceException("序列号收货数量与处置维度不守恒");
                }
            }
        }

        private static int count(BigDecimal quantity)
        {
            try
            {
                return quantity.intValueExact();
            }
            catch (ArithmeticException ex)
            {
                throw new ServiceException("序列号收货数量必须为整数");
            }
        }

        private static void validateLedgers(
                List<InvTransferShipmentReceiptPreparedMutation.Ledger>
                        ledgers,
                Map<Long, InvTransferShipmentReceiptPreparedMutation
                        .Allocation> allocations,
                Map<InvTransferShipmentReceiptPreparedMutation.BalanceKey,
                        InvTransferShipmentReceiptPreparedMutation
                                .TargetBalance> balances,
                Map<InvShipmentPlanningItemKey,
                        InvTransferShipmentReceiptPreparedMutation.StockLog>
                                logs)
        {
            Set<String> actual = new LinkedHashSet<>();
            Set<String> expected = new LinkedHashSet<>();
            allocations.values().forEach(value -> {
                if (value.acceptedQuantity().signum() > 0)
                {
                    expected.add(value.shipmentAllocationId() + "|accepted");
                }
                if (value.damagedQuantity().signum() > 0)
                {
                    expected.add(value.shipmentAllocationId() + "|damaged");
                }
            });
            for (InvTransferShipmentReceiptPreparedMutation.Ledger value
                    : ledgers)
            {
                InvTransferShipmentReceiptPreparedMutation.Allocation source =
                        allocations.get(value.shipmentAllocationId());
                String dimension = value.shipmentAllocationId() + "|"
                        + value.disposition();
                if (source == null || !actual.add(dimension)
                        || !Objects.equals(source.itemKey(), value.itemKey())
                        || !balances.containsKey(value.targetBalanceKey())
                        || !logs.containsKey(value.itemKey()))
                {
                    throw new ServiceException("收货台账写意图不可核验");
                }
            }
            if (!expected.equals(actual))
            {
                throw new ServiceException("收货台账处置维度不完整");
            }
        }

        private static void validateDetails(
                InvTransferShipmentReceiptPreparedMutation mutation,
                Map<Long, InvTransferShipmentReceiptPreparedMutation
                        .Allocation> allocations,
                InvTransferShipmentReceiptPreparedMutation.ReceiptSemantic
                        semantic)
        {
            Set<Long> expectedShipment = new LinkedHashSet<>();
            Set<Long> expectedTransfer = new LinkedHashSet<>();
            allocations.values().stream()
                    .filter(value -> semantic
                            == InvTransferShipmentReceiptPreparedMutation
                                    .ReceiptSemantic.FIXED_RETURN
                                            ? value.damagedQuantity()
                                                    .signum() > 0
                                            : value.acceptedQuantity()
                                                    .signum() > 0)
                    .forEach(value -> {
                        expectedShipment.add(value.shipmentDetailId());
                        expectedTransfer.add(value.transferDetailId());
                    });
            if (!expectedShipment.equals(detailIds(
                    mutation.shipmentDetails()))
                    || !expectedTransfer.equals(detailIds(
                            mutation.transferDetails())))
            {
                throw new ServiceException("收货履约明细累计维度不完整");
            }
        }

        private static void validateDiscrepancyCases(
                InvTransferShipmentReceiptPreparedMutation.Receipt receipt,
                List<InvTransferShipmentReceiptPreparedMutation
                        .DiscrepancyCase> cases,
                Map<Long, InvTransferShipmentReceiptPreparedMutation
                        .Allocation> allocations)
        {
            Set<String> expected = new LinkedHashSet<>();
            boolean fixedReturn = receipt.receiptSemantic()
                    == InvTransferShipmentReceiptPreparedMutation
                            .ReceiptSemantic.FIXED_RETURN;
            allocations.values().forEach(value -> {
                if (!fixedReturn && value.damagedQuantity().signum() > 0)
                {
                    expected.add(value.shipmentAllocationId() + "|damaged");
                }
                if (value.shortageQuantity().signum() > 0)
                {
                    expected.add(value.shipmentAllocationId() + "|shortage");
                }
            });
            Set<String> actual = new LinkedHashSet<>();
            for (InvTransferShipmentReceiptPreparedMutation.DiscrepancyCase
                    value : cases)
            {
                InvTransferShipmentReceiptPreparedMutation.Allocation source =
                        value == null ? null : allocations.get(
                                value.shipmentAllocationId());
                String type = value == null ? null
                        : value.discrepancyType();
                BigDecimal expectedQuantity = source == null ? null
                        : "damaged".equals(type)
                                ? source.damagedQuantity()
                                : "shortage".equals(type)
                                        ? source.shortageQuantity() : null;
                BigDecimal expectedAmount = source == null
                        || expectedQuantity == null ? null
                                : source.sourceCostPrice()
                                        .multiply(expectedQuantity)
                                        .setScale(6, RoundingMode.HALF_UP);
                String fingerprint = value == null ? null
                        : InvTransferReceiptDiscrepancyFacts.fingerprint(
                                receipt.receiptPlanVersion(),
                                value.shipmentAllocationId(), type,
                                value.discrepancyQuantity(),
                                value.sourceCostPrice(),
                                value.discrepancyAmount(),
                                value.discrepancyNote(),
                                value.attachmentRefs());
                String dimension = value == null ? null
                        : value.shipmentAllocationId() + "|" + type;
                if (source == null || expectedQuantity == null
                        || expectedQuantity.signum() <= 0
                        || value.discrepancyQuantity() == null
                        || value.discrepancyQuantity().scale() > 4
                        || !same(value.discrepancyQuantity(),
                                expectedQuantity)
                        || !same(value.sourceCostPrice(),
                                source.sourceCostPrice())
                        || value.sourceCostPrice().scale() > 6
                        || !same(value.discrepancyAmount(), expectedAmount)
                        || value.discrepancyAmount().scale() > 6
                        || value.discrepancyNote() == null
                        || value.discrepancyNote().isBlank()
                        || !Objects.equals(value.discrepancyNote(),
                                source.discrepancyNote())
                        || value.attachmentRefs() == null
                        || value.attachmentRefs().isBlank()
                        || !Objects.equals(value.attachmentRefs(),
                                source.attachmentRefs())
                        || !Objects.equals(value.factFingerprint(),
                                fingerprint)
                        || !actual.add(dimension))
                {
                    throw new ServiceException("收货差异事项写意图不可核验");
                }
            }
            if (!expected.equals(actual))
            {
                throw new ServiceException("收货差异事项维度不完整");
            }
        }

        private static Set<Long> detailIds(
                List<InvTransferShipmentReceiptPreparedMutation
                        .DetailProgress> values)
        {
            Set<Long> result = new LinkedHashSet<>();
            for (InvTransferShipmentReceiptPreparedMutation.DetailProgress
                    value : values)
            {
                if (value == null || !positive(value.detailId())
                        || !result.add(value.detailId())
                        || !quantity(value.expectedReceivedQuantity())
                        || !quantity(value.fulfillmentDelta())
                        || value.fulfillmentDelta().signum() <= 0
                        || !quantity(value.receivedQuantityAfter())
                        || !same(value.receivedQuantityAfter(),
                                value.expectedReceivedQuantity()
                                        .add(value.fulfillmentDelta())))
                {
                    throw new ServiceException("收货明细累计写意图不可核验");
                }
            }
            return result;
        }

        private static void requireSame(BigDecimal left, BigDecimal right,
                String message)
        {
            if (!same(left, right))
            {
                throw new ServiceException(message);
            }
        }

        private static boolean quantity(BigDecimal value)
        {
            return value != null && value.signum() >= 0
                    && value.scale() <= 6;
        }

        private static boolean positive(Long value)
        {
            return value != null && value > 0;
        }

        private static boolean same(BigDecimal left, BigDecimal right)
        {
            return left != null && right != null
                    && left.compareTo(right) == 0;
        }
    }
}
