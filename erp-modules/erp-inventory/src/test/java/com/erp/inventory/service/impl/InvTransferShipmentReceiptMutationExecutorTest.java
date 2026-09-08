package com.erp.inventory.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;
import com.erp.common.core.exception.ServiceException;
import com.erp.inventory.domain.transfer.InvShipmentPlanningItemKey;
import com.erp.inventory.domain.transfer.InvTransferReceiptDerivedLotKey;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyFacts;
import com.erp.inventory.domain.transfer.InvTransferReceiptGeneratedId;
import com.erp.inventory.domain.transfer.InvTransferShipmentReceiptPreparedMutation;
import com.erp.inventory.domain.vo.InvTransferShipmentReceiptCreationVo;
import com.erp.inventory.mapper.InvTransferShipmentReceiptPersistenceMapper;

@DisplayName("V2调拨收货唯一Mutation Executor")
class InvTransferShipmentReceiptMutationExecutorTest
{
    private static final String HASH = "a".repeat(64);
    private static final Instant ARRIVED =
            Instant.parse("2026-08-02T10:00:00Z");
    private static final Instant CREATED =
            Instant.parse("2026-08-02T10:05:00Z");
    private static final InvShipmentPlanningItemKey ITEM =
            new InvShipmentPlanningItemKey("product", 10L);
    private static final InvTransferReceiptDerivedLotKey ACCEPTED_LOT =
            new InvTransferReceiptDerivedLotKey(302L, "product", 10L,
                    701L, "accepted");
    private static final InvTransferShipmentReceiptPreparedMutation.BalanceKey
            ACCEPTED_BALANCE =
                    new InvTransferShipmentReceiptPreparedMutation.BalanceKey(
                            ACCEPTED_LOT, 901L);
    private static final InvTransferReceiptDerivedLotKey DAMAGED_LOT =
            new InvTransferReceiptDerivedLotKey(302L, "product", 10L,
                    701L, "damaged");
    private static final InvTransferShipmentReceiptPreparedMutation.BalanceKey
            DAMAGED_BALANCE =
                    new InvTransferShipmentReceiptPreparedMutation.BalanceKey(
                            DAMAGED_LOT, 902L);

    @Test
    @DisplayName("按固定顺序执行全部单行写并返回精确回执")
    void shouldExecuteEveryMutationInFixedOrder()
    {
        InvTransferShipmentReceiptPersistenceMapper mapper = mock(
                InvTransferShipmentReceiptPersistenceMapper.class);
        stubAcceptedSuccess(mapper);
        InvTransferShipmentReceiptMutationExecutor executor =
                new InvTransferShipmentReceiptMutationExecutor(mapper);

        InvTransferShipmentReceiptCreationVo result = executor.execute(
                acceptedMutation());

        assertThat(result.receiptId()).isEqualTo("501");
        assertThat(result.receiptNo()).isEqualTo("TRR-TEST");
        assertThat(result.shipmentId()).isEqualTo("91");
        assertThat(result.transferId()).isEqualTo("900");
        assertThat(result.status()).isEqualTo("completed");
        assertThat(result.acceptedQuantity()).isEqualTo("2");
        assertThat(result.damagedQuantity()).isEqualTo("0");
        assertThat(result.shortageQuantity()).isEqualTo("0");
        assertThat(result.remainingQuantity()).isEqualTo("0");
        assertThat(result.createdTime()).isEqualTo(
                "2026-08-02T10:05:00Z");

        InOrder order = inOrder(mapper);
        order.verify(mapper).insertReceipt(any(), any());
        order.verify(mapper).addTargetStock(any(), any(), any(), any(),
                any(), any(), any(), any());
        order.verify(mapper).addTargetBalance(any(), any(), any(), any(),
                any(), any(), any(), any(), any());
        order.verify(mapper).insertStockLog(any(), any(), any());
        order.verify(mapper).insertReceiptAllocation(any(), any(), any(),
                any(), any(), any(), any(), any(), any());
        order.verify(mapper).updateAllocationProgress(any(), any(), any(),
                any(), any(), any());
        order.verify(mapper).addShipmentDetailFulfilled(any(), any(), any(),
                any());
        order.verify(mapper).addTransferDetailFulfilled(any(), any());
        order.verify(mapper).insertReceiptLedger(any(), any(), any(), any(),
                any(), any(), any());
        order.verify(mapper).updateShipmentLifecycle(any(), any(), any(),
                any(), any(), any());
        order.verify(mapper).updateTransferLifecycle(any(), any(), any(),
                any(), any(), any());
        order.verify(mapper).insertTransferStatusLog(any(), any(), any(),
                any(), any(), any(), any());
        order.verifyNoMoreInteractions();
    }

    @Test
    @DisplayName("新建目标维度时逐层传递生成键")
    void shouldThreadGeneratedTargetIdsThroughDependentWrites()
    {
        InvTransferShipmentReceiptPersistenceMapper mapper = mock(
                InvTransferShipmentReceiptPersistenceMapper.class);
        stubAcceptedSuccess(mapper);
        doAnswer(invocation -> {
            ((InvTransferReceiptGeneratedId) invocation.getArgument(3))
                    .setValue(401L);
            return 1;
        }).when(mapper).insertTargetStock(any(), any(), any(), any());
        doAnswer(invocation -> {
            ((InvTransferReceiptGeneratedId) invocation.getArgument(3))
                    .setValue(801L);
            return 1;
        }).when(mapper).insertTargetLot(any(), any(), any(), any());
        doAnswer(invocation -> {
            ((InvTransferReceiptGeneratedId) invocation.getArgument(5))
                    .setValue(9011L);
            return 1;
        }).when(mapper).insertTargetBalance(any(), any(), any(), any(),
                any(), any());
        InvTransferShipmentReceiptMutationExecutor executor =
                new InvTransferShipmentReceiptMutationExecutor(mapper);

        InvTransferShipmentReceiptCreationVo result = executor.execute(
                newTargetMutation());

        assertThat(result.receiptId()).isEqualTo("501");
        verify(mapper).insertTargetStock(any(), eq(302L), eq("receiver"),
                any());
        verify(mapper).insertTargetLot(any(), eq(900L), eq("receiver"),
                any());
        verify(mapper).insertTargetBalance(any(), eq(801L), eq(10L),
                eq(302L), eq("receiver"), any());
        verify(mapper).insertReceiptAllocation(any(), eq(501L), eq(91L),
                eq(801L), eq(9011L), isNull(), isNull(), eq("receiver"),
                any());
        verify(mapper).insertReceiptLedger(any(), any(), eq(501L),
                eq(701L), eq(9011L), eq(801L), eq(601L));
    }

    @Test
    @DisplayName("任一条件写影响零行时立即停止后续写入")
    void shouldStopOnFirstConditionalWriteConflict()
    {
        InvTransferShipmentReceiptPersistenceMapper mapper = mock(
                InvTransferShipmentReceiptPersistenceMapper.class);
        generated(mapper, 501L, 601L, 701L);
        when(mapper.addTargetStock(any(), any(), any(), any(), any(), any(),
                any(), any())).thenReturn(0);
        InvTransferShipmentReceiptMutationExecutor executor =
                new InvTransferShipmentReceiptMutationExecutor(mapper);

        assertThatThrownBy(() -> executor.execute(acceptedMutation()))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("目标汇总库存条件更新冲突")
                .hasMessageContaining("已回滚");

        verify(mapper).insertReceipt(any(), any());
        verify(mapper).addTargetStock(any(), any(), any(), any(), any(),
                any(), any(), any());
        verifyNoMoreInteractions(mapper);
    }

    @Test
    @DisplayName("写入成功但生成键无效时立即失败")
    void shouldStopWhenGeneratedKeyIsMissing()
    {
        InvTransferShipmentReceiptPersistenceMapper mapper = mock(
                InvTransferShipmentReceiptPersistenceMapper.class);
        when(mapper.insertReceipt(any(), any())).thenReturn(1);
        InvTransferShipmentReceiptMutationExecutor executor =
                new InvTransferShipmentReceiptMutationExecutor(mapper);

        assertThatThrownBy(() -> executor.execute(acceptedMutation()))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("收货单主键生成失败");
        verify(mapper).insertReceipt(any(), any());
        verifyNoMoreInteractions(mapper);
    }

    @Test
    @DisplayName("单行写异常返回多行时同样拒绝")
    void shouldRejectUnexpectedMultiRowMutation()
    {
        InvTransferShipmentReceiptPersistenceMapper mapper = mock(
                InvTransferShipmentReceiptPersistenceMapper.class);
        when(mapper.insertReceipt(any(), any())).thenReturn(2);
        InvTransferShipmentReceiptMutationExecutor executor =
                new InvTransferShipmentReceiptMutationExecutor(mapper);

        assertThatThrownBy(() -> executor.execute(acceptedMutation()))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("收货单写入冲突")
                .hasMessageContaining("已回滚");
        verify(mapper).insertReceipt(any(), any());
        verifyNoMoreInteractions(mapper);
    }

    @Test
    @DisplayName("数据库唯一冲突原样传播以触发外层事务回滚")
    void shouldPropagateDatabaseException()
    {
        InvTransferShipmentReceiptPersistenceMapper mapper = mock(
                InvTransferShipmentReceiptPersistenceMapper.class);
        DuplicateKeyException duplicate =
                new DuplicateKeyException("duplicate receipt request");
        when(mapper.insertReceipt(any(), any())).thenThrow(duplicate);
        InvTransferShipmentReceiptMutationExecutor executor =
                new InvTransferShipmentReceiptMutationExecutor(mapper);

        assertThatThrownBy(() -> executor.execute(acceptedMutation()))
                .isSameAs(duplicate);
        verify(mapper).insertReceipt(any(), any());
        verifyNoMoreInteractions(mapper);
    }

    @Test
    @DisplayName("最后一步状态日志失败也向事务所有者传播")
    void shouldPropagateLateFailureForWholeTransactionRollback()
    {
        InvTransferShipmentReceiptPersistenceMapper mapper = mock(
                InvTransferShipmentReceiptPersistenceMapper.class);
        stubAcceptedSuccess(mapper);
        when(mapper.insertTransferStatusLog(any(), any(), any(), any(), any(),
                any(), any())).thenReturn(0);
        InvTransferShipmentReceiptMutationExecutor executor =
                new InvTransferShipmentReceiptMutationExecutor(mapper);

        assertThatThrownBy(() -> executor.execute(acceptedMutation()))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("调拨收货状态日志写入冲突")
                .hasMessageContaining("已回滚");
        verify(mapper).insertReceiptLedger(any(), any(), any(), any(), any(),
                any(), any());
        verify(mapper).insertTransferStatusLog(any(), any(), any(), any(),
                any(), any(), any());
    }

    @Test
    @DisplayName("纯短缺序列号只写审计且保持来源位置")
    void shouldAuditShortageSerialWithoutMovingIt()
    {
        InvTransferShipmentReceiptPersistenceMapper mapper = mock(
                InvTransferShipmentReceiptPersistenceMapper.class);
        generated(mapper, 501L, 601L, 701L);
        generatedDiscrepancy(mapper, 801L);
        when(mapper.insertReceiptSerial(any(), any(), any(), any(), any(),
                any(), any())).thenReturn(1);
        when(mapper.updateAllocationProgress(any(), any(), any(), any(),
                any(), any())).thenReturn(1);
        stubLifecycle(mapper);
        InvTransferShipmentReceiptMutationExecutor executor =
                new InvTransferShipmentReceiptMutationExecutor(mapper);

        InvTransferShipmentReceiptCreationVo result = executor.execute(
                shortageMutation());

        assertThat(result.status()).isEqualTo("discrepancy");
        verify(mapper, never()).moveSerialToTarget(any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any());
        InOrder order = inOrder(mapper);
        order.verify(mapper).insertReceipt(any(), any());
        order.verify(mapper).insertReceiptAllocation(any(), any(), any(),
                any(), any(), any(), any(), any(), any());
        order.verify(mapper).insertReceiptDiscrepancyCase(any(), any(),
                any(), any(), any());
        order.verify(mapper).insertReceiptSerial(any(), any(), any(), any(),
                any(), any(), any());
    }

    @Test
    @DisplayName("差异事项条件写零行时整次收货立即停止")
    void shouldStopWhenDiscrepancyCaseInsertIsRejected()
    {
        InvTransferShipmentReceiptPersistenceMapper mapper = mock(
                InvTransferShipmentReceiptPersistenceMapper.class);
        generated(mapper, 501L, 601L, 701L);
        InvTransferShipmentReceiptMutationExecutor executor =
                new InvTransferShipmentReceiptMutationExecutor(mapper);

        assertThatThrownBy(() -> executor.execute(shortageMutation()))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("收货差异事项写入冲突")
                .hasMessageContaining("已回滚");

        verify(mapper).insertReceiptDiscrepancyCase(any(), any(), any(),
                any(), any());
        verify(mapper, never()).insertReceiptSerial(any(), any(), any(),
                any(), any(), any(), any());
    }

    @Test
    @DisplayName("差异事项生成键缺失时在序列号和累计写入前停止")
    void shouldStopWhenDiscrepancyCaseKeyIsMissing()
    {
        InvTransferShipmentReceiptPersistenceMapper mapper = mock(
                InvTransferShipmentReceiptPersistenceMapper.class);
        generated(mapper, 501L, 601L, 701L);
        when(mapper.insertReceiptDiscrepancyCase(any(), any(), any(), any(),
                any())).thenReturn(1);
        InvTransferShipmentReceiptMutationExecutor executor =
                new InvTransferShipmentReceiptMutationExecutor(mapper);

        assertThatThrownBy(() -> executor.execute(shortageMutation()))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("收货差异事项主键生成失败");

        verify(mapper).insertReceiptDiscrepancyCase(any(), any(), any(),
                any(), any());
        verify(mapper, never()).insertReceiptSerial(any(), any(), any(),
                any(), any(), any(), any());
        verify(mapper, never()).updateAllocationProgress(any(), any(), any(),
                any(), any(), any());
    }

    @Test
    @DisplayName("缺少差异事项时在第一条DML前拒绝")
    void shouldRequireEveryDiscrepancyDimensionBeforeFirstDml()
    {
        InvTransferShipmentReceiptPreparedMutation valid =
                shortageMutation();
        InvTransferShipmentReceiptPreparedMutation broken =
                new InvTransferShipmentReceiptPreparedMutation(
                        valid.receipt(), valid.targetStocks(),
                        valid.stockLogs(), valid.targetLots(),
                        valid.targetBalances(), valid.allocations(),
                        List.of(), valid.serials(), valid.ledgers(),
                        valid.shipmentDetails(), valid.transferDetails(),
                        valid.lifecycle());
        InvTransferShipmentReceiptPersistenceMapper mapper = mock(
                InvTransferShipmentReceiptPersistenceMapper.class);
        InvTransferShipmentReceiptMutationExecutor executor =
                new InvTransferShipmentReceiptMutationExecutor(mapper);

        assertThatThrownBy(() -> executor.execute(broken))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("收货差异事项维度不完整");
        verifyNoInteractions(mapper);
    }

    @Test
    @DisplayName("差异事实指纹不匹配时在第一条DML前拒绝")
    void shouldRejectMismatchedDiscrepancyFingerprintBeforeFirstDml()
    {
        InvTransferShipmentReceiptPreparedMutation valid =
                shortageMutation();
        InvTransferShipmentReceiptPreparedMutation.DiscrepancyCase source =
                valid.discrepancyCases().get(0);
        InvTransferShipmentReceiptPreparedMutation.DiscrepancyCase forged =
                new InvTransferShipmentReceiptPreparedMutation
                        .DiscrepancyCase(source.shipmentAllocationId(),
                                source.discrepancyType(),
                                source.discrepancyQuantity(),
                                source.sourceCostPrice(),
                                source.discrepancyAmount(), "b".repeat(64),
                                source.discrepancyNote(),
                                source.attachmentRefs());
        InvTransferShipmentReceiptPreparedMutation broken =
                new InvTransferShipmentReceiptPreparedMutation(
                        valid.receipt(), valid.targetStocks(),
                        valid.stockLogs(), valid.targetLots(),
                        valid.targetBalances(), valid.allocations(),
                        List.of(forged), valid.serials(), valid.ledgers(),
                        valid.shipmentDetails(), valid.transferDetails(),
                        valid.lifecycle());
        InvTransferShipmentReceiptPersistenceMapper mapper = mock(
                InvTransferShipmentReceiptPersistenceMapper.class);
        InvTransferShipmentReceiptMutationExecutor executor =
                new InvTransferShipmentReceiptMutationExecutor(mapper);

        assertThatThrownBy(() -> executor.execute(broken))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("收货差异事项写意图不可核验");
        verifyNoInteractions(mapper);
    }

    @Test
    @DisplayName("关联不完整时在第一条DML前拒绝")
    void shouldResolveAllRelationsBeforeFirstDml()
    {
        InvTransferShipmentReceiptPersistenceMapper mapper = mock(
                InvTransferShipmentReceiptPersistenceMapper.class);
        InvTransferShipmentReceiptPreparedMutation valid =
                acceptedMutation();
        InvTransferShipmentReceiptPreparedMutation broken =
                new InvTransferShipmentReceiptPreparedMutation(
                        valid.receipt(), valid.targetStocks(),
                        valid.stockLogs(), valid.targetLots(),
                        valid.targetBalances(), valid.allocations(),
                        valid.discrepancyCases(), valid.serials(), List.of(),
                        valid.shipmentDetails(), valid.transferDetails(),
                        valid.lifecycle());
        InvTransferShipmentReceiptMutationExecutor executor =
                new InvTransferShipmentReceiptMutationExecutor(mapper);

        assertThatThrownBy(() -> executor.execute(broken))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("台账处置维度不完整");
        verifyNoInteractions(mapper);
    }

    @Test
    @DisplayName("预期退回通过唯一执行器写隔离库存且不新增残损差异")
    void shouldExecuteExpectedReturnWithoutDamagedDiscrepancy()
    {
        var mapper = mock(InvTransferShipmentReceiptPersistenceMapper.class);
        stubAcceptedSuccess(mapper);
        var executor = new InvTransferShipmentReceiptMutationExecutor(mapper);

        var result = executor.executeReturnReceipt(fixedReturnMutation());

        assertThat(result.returnedQuantity()).isEqualTo("2");
        assertThat(result.shortageQuantity()).isEqualTo("0");
        assertThat(result.receiptStatus()).isEqualTo("completed");
        assertThat(result.dataSource())
                .isEqualTo("return-receipt-atomic-mutation-v1");
        verify(mapper, never()).insertReceiptDiscrepancyCase(any(), any(),
                any(), any(), any());
        verify(mapper).addShipmentDetailFulfilled(any(), any(), any(),
                any());
        verify(mapper).addTransferDetailFulfilled(any(), any());
    }

    @Test
    @DisplayName("专属入口拒绝普通语义且在第一条DML前停止")
    void shouldRejectOrdinaryMutationAtReturnEntry()
    {
        var mapper = mock(InvTransferShipmentReceiptPersistenceMapper.class);
        var executor = new InvTransferShipmentReceiptMutationExecutor(mapper);

        assertThatThrownBy(() -> executor.executeReturnReceipt(
                acceptedMutation())).isInstanceOf(ServiceException.class)
                .hasMessageContaining("拒绝非专属写意图");
        verifyNoInteractions(mapper);
    }

    @Test
    @DisplayName("伪造退回残损差异或序列号数量漂移均在首条DML前拒绝")
    void shouldRejectForgedReturnDiscrepancyOrSerialCount()
    {
        var valid = fixedReturnMutation();
        BigDecimal quantity = new BigDecimal("2");
        BigDecimal price = new BigDecimal("10.000000");
        BigDecimal amount = new BigDecimal("20.000000");
        var forgedCase = new InvTransferShipmentReceiptPreparedMutation
                .DiscrepancyCase(81L, "damaged", quantity, price, amount,
                        InvTransferReceiptDiscrepancyFacts.fingerprint(HASH,
                                81L, "damaged", quantity, price, amount,
                                null, null),
                        null, null);
        var forged = new InvTransferShipmentReceiptPreparedMutation(
                valid.receipt(), valid.targetStocks(), valid.stockLogs(),
                valid.targetLots(), valid.targetBalances(),
                valid.allocations(), List.of(forgedCase), valid.serials(),
                valid.ledgers(), valid.shipmentDetails(),
                valid.transferDetails(), valid.lifecycle());
        var mapper = mock(InvTransferShipmentReceiptPersistenceMapper.class);
        var executor = new InvTransferShipmentReceiptMutationExecutor(mapper);

        assertThatThrownBy(() -> executor.executeReturnReceipt(forged))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("差异事项写意图不可核验");
        verifyNoInteractions(mapper);

        var serial = shortageMutation();
        var missingSerial = new InvTransferShipmentReceiptPreparedMutation(
                serial.receipt(), serial.targetStocks(), serial.stockLogs(),
                serial.targetLots(), serial.targetBalances(),
                serial.allocations(), serial.discrepancyCases(), List.of(),
                serial.ledgers(), serial.shipmentDetails(),
                serial.transferDetails(), serial.lifecycle());
        assertThatThrownBy(() -> executor.execute(missingSerial))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("序列号收货数量与处置维度不守恒");
        verifyNoInteractions(mapper);
    }

    @Test
    @DisplayName("执行器只持有一个Mapper且不声明独立事务")
    void shouldRemainNarrowAndTransactionAgnostic() throws Exception
    {
        Field[] fields = InvTransferShipmentReceiptMutationExecutor.class
                .getDeclaredFields();
        assertThat(Arrays.stream(fields).map(Field::getType))
                .containsExactly(
                        InvTransferShipmentReceiptPersistenceMapper.class);
        Method execute = InvTransferShipmentReceiptMutationExecutor.class
                .getDeclaredMethod("execute",
                        InvTransferShipmentReceiptPreparedMutation.class);
        assertThat(execute.getAnnotation(Transactional.class)).isNull();
    }

    private static void stubAcceptedSuccess(
            InvTransferShipmentReceiptPersistenceMapper mapper)
    {
        generated(mapper, 501L, 601L, 701L);
        when(mapper.addTargetStock(any(), any(), any(), any(), any(), any(),
                any(), any())).thenReturn(1);
        when(mapper.addTargetBalance(any(), any(), any(), any(), any(), any(),
                any(), any(), any())).thenReturn(1);
        when(mapper.updateAllocationProgress(any(), any(), any(), any(),
                any(), any())).thenReturn(1);
        when(mapper.addShipmentDetailFulfilled(any(), any(), any(), any()))
                .thenReturn(1);
        when(mapper.addTransferDetailFulfilled(any(), any())).thenReturn(1);
        when(mapper.insertReceiptLedger(any(), any(), any(), any(), any(),
                any(), any())).thenReturn(1);
        stubLifecycle(mapper);
    }

    private static void generated(
            InvTransferShipmentReceiptPersistenceMapper mapper,
            Long receiptId, Long stockLogId, Long allocationId)
    {
        doAnswer(invocation -> {
            ((InvTransferReceiptGeneratedId) invocation.getArgument(1))
                    .setValue(receiptId);
            return 1;
        }).when(mapper).insertReceipt(any(), any());
        doAnswer(invocation -> {
            ((InvTransferReceiptGeneratedId) invocation.getArgument(2))
                    .setValue(stockLogId);
            return 1;
        }).when(mapper).insertStockLog(any(), any(), any());
        doAnswer(invocation -> {
            ((InvTransferReceiptGeneratedId) invocation.getArgument(8))
                    .setValue(allocationId);
            return 1;
        }).when(mapper).insertReceiptAllocation(any(), any(), any(), any(),
                any(), any(), any(), any(), any());
    }

    private static void generatedDiscrepancy(
            InvTransferShipmentReceiptPersistenceMapper mapper, Long caseId)
    {
        doAnswer(invocation -> {
            ((InvTransferReceiptGeneratedId) invocation.getArgument(4))
                    .setValue(caseId);
            return 1;
        }).when(mapper).insertReceiptDiscrepancyCase(any(), any(), any(),
                any(), any());
    }

    private static void stubLifecycle(
            InvTransferShipmentReceiptPersistenceMapper mapper)
    {
        when(mapper.updateShipmentLifecycle(any(), any(), any(), any(),
                any(), any())).thenReturn(1);
        when(mapper.updateTransferLifecycle(any(), any(), any(), any(),
                any(), any())).thenReturn(1);
        when(mapper.insertTransferStatusLog(any(), any(), any(), any(), any(),
                any(), any())).thenReturn(1);
    }

    private static InvTransferShipmentReceiptPreparedMutation
            acceptedMutation()
    {
        InvTransferShipmentReceiptPreparedMutation.Receipt receipt = receipt(
                new BigDecimal("2"), BigDecimal.ZERO, BigDecimal.ZERO,
                "completed");
        InvTransferShipmentReceiptPreparedMutation.TargetStock stock =
                new InvTransferShipmentReceiptPreparedMutation.TargetStock(
                        ITEM, 10L, 401L, 2L, new BigDecimal("10"),
                        new BigDecimal("12"), new BigDecimal("2"),
                        BigDecimal.ZERO, new BigDecimal("20.000000"));
        InvTransferShipmentReceiptPreparedMutation.StockLog log =
                new InvTransferShipmentReceiptPreparedMutation.StockLog(
                        ITEM, 10L, new BigDecimal("2"),
                        new BigDecimal("10"), new BigDecimal("12"),
                        new BigDecimal("10.000000"), new BigDecimal("2"),
                        BigDecimal.ZERO);
        InvTransferShipmentReceiptPreparedMutation.TargetLot lot =
                new InvTransferShipmentReceiptPreparedMutation.TargetLot(
                        ACCEPTED_LOT, 10L, 801L, "LOT-A", "SUP-A", null,
                        null, "passed", "active");
        InvTransferShipmentReceiptPreparedMutation.TargetBalance balance =
                new InvTransferShipmentReceiptPreparedMutation.TargetBalance(
                        ACCEPTED_BALANCE, 9011L, 3L, new BigDecimal("5"),
                        new BigDecimal("7"), new BigDecimal("2"),
                        BigDecimal.ZERO, new BigDecimal("20.000000"));
        InvTransferShipmentReceiptPreparedMutation.Allocation allocation =
                new InvTransferShipmentReceiptPreparedMutation.Allocation(
                        81L, 82L, 83L, ITEM, 10L, "lot", 711L, 701L,
                        7011L, new BigDecimal("10.000000"),
                        new BigDecimal("2"), BigDecimal.ZERO,
                        BigDecimal.ZERO, new BigDecimal("20.000000"),
                        BigDecimal.ZERO.setScale(6), 4L, 901L,
                        ACCEPTED_LOT, new BigDecimal("5"),
                        new BigDecimal("7"), 3L, 4L, null, null, null,
                        null, null, null, null, null);
        InvTransferShipmentReceiptPreparedMutation.Ledger ledger =
                new InvTransferShipmentReceiptPreparedMutation.Ledger(
                        "RCL-accepted", 81L, "accepted", ITEM, 10L,
                        ACCEPTED_BALANCE, new BigDecimal("2"),
                        new BigDecimal("5"), new BigDecimal("7"),
                        new BigDecimal("10.000000"),
                        new BigDecimal("20.000000"));
        return new InvTransferShipmentReceiptPreparedMutation(receipt,
                List.of(stock), List.of(log), List.of(lot),
                List.of(balance), List.of(allocation), List.of(), List.of(),
                List.of(ledger), List.of(detail(82L)),
                List.of(detail(83L)), lifecycle("received", "received"));
    }

    private static InvTransferShipmentReceiptPreparedMutation
            shortageMutation()
    {
        InvTransferShipmentReceiptPreparedMutation.Allocation allocation =
                new InvTransferShipmentReceiptPreparedMutation.Allocation(
                        81L, 82L, 83L, ITEM, 10L, "serial", 711L, 701L,
                        7011L, new BigDecimal("10.000000"), BigDecimal.ZERO,
                        BigDecimal.ZERO, BigDecimal.ONE,
                        BigDecimal.ZERO.setScale(6),
                        BigDecimal.ZERO.setScale(6), 4L, null, null, null,
                        null, null, null, null, null, null, null, null, null,
                        "missing", "attachment-1");
        InvTransferShipmentReceiptPreparedMutation.Serial serial =
                new InvTransferShipmentReceiptPreparedMutation.Serial(
                        91L, 81L, 1001L, "SN-1001", "shortage", 301L,
                        711L, 701L, 7011L, null, null, "shipped",
                        "shipped");
        return new InvTransferShipmentReceiptPreparedMutation(
                receipt(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ONE,
                        "discrepancy"),
                List.of(), List.of(), List.of(), List.of(),
                List.of(allocation), List.of(discrepancyCase()),
                List.of(serial), List.of(), List.of(), List.of(),
                lifecycle("discrepancy", "discrepancy"));
    }

    private static InvTransferShipmentReceiptPreparedMutation
            fixedReturnMutation()
    {
        var receipt = new InvTransferShipmentReceiptPreparedMutation.Receipt(
                "return-receipt-request-1", HASH, "TRR-R-TEST",
                InvTransferShipmentReceiptPreparedMutation.ReceiptSemantic
                        .FIXED_RETURN,
                91L, 900L, HASH, HASH, "source-batch", 302L,
                "target-batch", ARRIVED, CREATED, true, BigDecimal.ZERO,
                new BigDecimal("2"), BigDecimal.ZERO, BigDecimal.ZERO,
                "completed", 77L, "receiver", "receiver", null);
        var stock = new InvTransferShipmentReceiptPreparedMutation.TargetStock(
                ITEM, 10L, 401L, 2L, new BigDecimal("10"),
                new BigDecimal("12"), BigDecimal.ZERO,
                new BigDecimal("2"), new BigDecimal("20.000000"));
        var log = new InvTransferShipmentReceiptPreparedMutation.StockLog(
                ITEM, 10L, new BigDecimal("2"), new BigDecimal("10"),
                new BigDecimal("12"), new BigDecimal("10.000000"),
                BigDecimal.ZERO, new BigDecimal("2"));
        var lot = new InvTransferShipmentReceiptPreparedMutation.TargetLot(
                DAMAGED_LOT, 10L, 802L, "LOT-D", "SUP-D", null, null,
                "quarantine", "active");
        var balance =
                new InvTransferShipmentReceiptPreparedMutation.TargetBalance(
                        DAMAGED_BALANCE, 9021L, 3L,
                        new BigDecimal("5"), new BigDecimal("7"),
                        BigDecimal.ZERO, new BigDecimal("2"),
                        new BigDecimal("20.000000"));
        var allocation =
                new InvTransferShipmentReceiptPreparedMutation.Allocation(
                        81L, 82L, 83L, ITEM, 10L, "lot", 711L, 701L,
                        7011L, new BigDecimal("10.000000"), BigDecimal.ZERO,
                        new BigDecimal("2"), BigDecimal.ZERO,
                        BigDecimal.ZERO.setScale(6),
                        new BigDecimal("20.000000"), 4L, null, null, null,
                        null, null, null, 902L, DAMAGED_LOT,
                        new BigDecimal("5"), new BigDecimal("7"), 3L, 4L,
                        null, null);
        var ledger = new InvTransferShipmentReceiptPreparedMutation.Ledger(
                "RCL-damaged", 81L, "damaged", ITEM, 10L,
                DAMAGED_BALANCE, new BigDecimal("2"),
                new BigDecimal("5"), new BigDecimal("7"),
                new BigDecimal("10.000000"),
                new BigDecimal("20.000000"));
        var lifecycle =
                new InvTransferShipmentReceiptPreparedMutation.Lifecycle(
                        "pending_receive", "delivered", 4L, "received",
                        "received", "return_receipt_completed");
        return new InvTransferShipmentReceiptPreparedMutation(receipt,
                List.of(stock), List.of(log), List.of(lot),
                List.of(balance), List.of(allocation), List.of(), List.of(),
                List.of(ledger), List.of(detail(82L)),
                List.of(detail(83L)), lifecycle);
    }

    private static InvTransferShipmentReceiptPreparedMutation
            newTargetMutation()
    {
        InvTransferShipmentReceiptPreparedMutation.TargetStock stock =
                new InvTransferShipmentReceiptPreparedMutation.TargetStock(
                        ITEM, 10L, null, null, BigDecimal.ZERO,
                        new BigDecimal("2"), new BigDecimal("2"),
                        BigDecimal.ZERO, new BigDecimal("20.000000"));
        InvTransferShipmentReceiptPreparedMutation.StockLog log =
                new InvTransferShipmentReceiptPreparedMutation.StockLog(
                        ITEM, 10L, new BigDecimal("2"), BigDecimal.ZERO,
                        new BigDecimal("2"),
                        new BigDecimal("10.000000"), new BigDecimal("2"),
                        BigDecimal.ZERO);
        InvTransferShipmentReceiptPreparedMutation.TargetLot lot =
                new InvTransferShipmentReceiptPreparedMutation.TargetLot(
                        ACCEPTED_LOT, 10L, null, "LOT-A", "SUP-A", null,
                        null, "passed", "active");
        InvTransferShipmentReceiptPreparedMutation.TargetBalance balance =
                new InvTransferShipmentReceiptPreparedMutation.TargetBalance(
                        ACCEPTED_BALANCE, null, null, BigDecimal.ZERO,
                        new BigDecimal("2"), new BigDecimal("2"),
                        BigDecimal.ZERO, new BigDecimal("20.000000"));
        InvTransferShipmentReceiptPreparedMutation.Allocation allocation =
                new InvTransferShipmentReceiptPreparedMutation.Allocation(
                        81L, 82L, 83L, ITEM, 10L, "lot", 711L, 701L,
                        7011L, new BigDecimal("10.000000"),
                        new BigDecimal("2"), BigDecimal.ZERO,
                        BigDecimal.ZERO, new BigDecimal("20.000000"),
                        BigDecimal.ZERO.setScale(6), 4L, 901L,
                        ACCEPTED_LOT, BigDecimal.ZERO,
                        new BigDecimal("2"), null, 0L, null, null, null,
                        null, null, null, null, null);
        InvTransferShipmentReceiptPreparedMutation.Ledger ledger =
                new InvTransferShipmentReceiptPreparedMutation.Ledger(
                        "RCL-accepted", 81L, "accepted", ITEM, 10L,
                        ACCEPTED_BALANCE, new BigDecimal("2"),
                        BigDecimal.ZERO, new BigDecimal("2"),
                        new BigDecimal("10.000000"),
                        new BigDecimal("20.000000"));
        return new InvTransferShipmentReceiptPreparedMutation(
                receipt(new BigDecimal("2"), BigDecimal.ZERO,
                        BigDecimal.ZERO, "completed"),
                List.of(stock), List.of(log), List.of(lot),
                List.of(balance), List.of(allocation), List.of(), List.of(),
                List.of(ledger), List.of(detail(82L)),
                List.of(detail(83L)), lifecycle("received", "received"));
    }

    private static InvTransferShipmentReceiptPreparedMutation.DiscrepancyCase
            discrepancyCase()
    {
        BigDecimal quantity = BigDecimal.ONE;
        BigDecimal price = new BigDecimal("10.000000");
        BigDecimal amount = new BigDecimal("10.000000");
        return new InvTransferShipmentReceiptPreparedMutation.DiscrepancyCase(
                81L, "shortage", quantity, price, amount,
                InvTransferReceiptDiscrepancyFacts.fingerprint(HASH, 81L,
                        "shortage", quantity, price, amount, "missing",
                        "attachment-1"),
                "missing", "attachment-1");
    }

    private static InvTransferShipmentReceiptPreparedMutation.Receipt receipt(
            BigDecimal accepted, BigDecimal damaged, BigDecimal shortage,
            String status)
    {
        return new InvTransferShipmentReceiptPreparedMutation.Receipt(
                "receipt-request-1", HASH, "TRR-TEST",
                InvTransferShipmentReceiptPreparedMutation.ReceiptSemantic
                        .ORDINARY,
                91L, 900L, HASH,
                HASH, "source-batch", 302L, "target-batch", ARRIVED,
                CREATED, true, accepted, damaged, shortage, BigDecimal.ZERO,
                status, 77L, "receiver", "receiver", null);
    }

    private static InvTransferShipmentReceiptPreparedMutation.DetailProgress
            detail(Long id)
    {
        return new InvTransferShipmentReceiptPreparedMutation.DetailProgress(
                id, BigDecimal.ZERO, new BigDecimal("2"),
                new BigDecimal("2"));
    }

    private static InvTransferShipmentReceiptPreparedMutation.Lifecycle
            lifecycle(String shipmentStatus, String transferStatus)
    {
        return new InvTransferShipmentReceiptPreparedMutation.Lifecycle(
                "pending_receive", "delivered", 4L, shipmentStatus,
                transferStatus, "receipt_completed");
    }
}
