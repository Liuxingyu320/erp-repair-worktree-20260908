package com.erp.inventory.service.impl;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.erp.common.core.exception.ServiceException;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationExecutionPolicy.PreparedExecution;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyDamageWriteOffFact;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyDamageWriteOffPolicy;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyDamageWriteOffPolicy.Prepared;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyDamageWriteOffPolicy.Serial;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyDamageWriteOffSerialFact;
import com.erp.inventory.domain.transfer.InvTransferReceiptLocationCandidate;
import com.erp.inventory.domain.transfer.InvTransferReceiptTargetBalance;
import com.erp.inventory.domain.transfer.InvTransferReceiptTargetLot;
import com.erp.inventory.domain.transfer.InvTransferReceiptTargetStock;
import com.erp.inventory.mapper.InvTransferReceiptDiscrepancyDamageWriteOffMapper;

/**
 * Sole damaged-stock DML owner reserved for the future execution transaction.
 * It has no runtime caller while the adjudication execution entry is closed.
 */
@Service
public class InvTransferReceiptDiscrepancyDamageWriteOffEffectOwner
{
    private final InvTransferReceiptDiscrepancyDamageWriteOffMapper mapper;

    public InvTransferReceiptDiscrepancyDamageWriteOffEffectOwner(
            InvTransferReceiptDiscrepancyDamageWriteOffMapper mapper)
    {
        this.mapper = mapper;
    }

    @Transactional(propagation = Propagation.MANDATORY,
            rollbackFor = Exception.class)
    public void apply(PreparedExecution execution)
    {
        InvTransferReceiptDiscrepancyDamageWriteOffPolicy
                .validateExecution(execution);
        InvTransferReceiptDiscrepancyDamageWriteOffFact fact =
                mapper.selectFactForUpdate(
                        execution.caseId());
        if (fact == null)
        {
            throw new ServiceException("受损写销收货分配锁定事实不存在");
        }
        BigDecimal writtenOff = mapper.selectWrittenOffQuantity(
                fact.getReceiptAllocationId());
        InvTransferReceiptTargetStock stock = mapper.selectStockForUpdate(fact);
        InvTransferReceiptTargetLot lot = mapper.selectLotForUpdate(fact);
        InvTransferReceiptLocationCandidate location =
                mapper.selectLocationForUpdate(fact);
        InvTransferReceiptTargetBalance balance =
                mapper.selectBalanceForUpdate(fact);
        List<InvTransferReceiptDiscrepancyDamageWriteOffSerialFact> serials =
                mapper.selectEligibleSerialsForUpdate(fact);

        Prepared effect =
                InvTransferReceiptDiscrepancyDamageWriteOffPolicy.prepare(
                        execution, fact, writtenOff, stock, lot, location,
                        balance, serials);
        requireOne(mapper.decrementStock(effect), "受损写销汇总库存更新冲突");
        requireOne(mapper.decrementBalance(effect), "受损写销隔离余额更新冲突");
        for (Serial serial : effect.serials())
        {
            requireOne(mapper.scrapSerial(effect, serial),
                    "受损写销序列号报废冲突");
        }
        requireOne(mapper.insertStockLedger(effect),
                "受损写销库存流水写入冲突");
        requireOne(mapper.insertDamageLossLedger(effect),
                "受损写销损失台账写入冲突");
        for (Serial serial : effect.serials())
        {
            requireOne(mapper.insertDamageLossSerial(effect, serial),
                    "受损写销序列号台账写入冲突");
        }
    }

    private static void requireOne(int rows, String message)
    {
        if (rows != 1)
        {
            throw new ServiceException(message + "，业务操作已回滚");
        }
    }
}
