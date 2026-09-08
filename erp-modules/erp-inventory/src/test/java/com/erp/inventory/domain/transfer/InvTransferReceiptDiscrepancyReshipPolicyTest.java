package com.erp.inventory.domain.transfer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.erp.common.core.exception.ServiceException;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationAsyncWorkflowPolicy.Source;

@DisplayName("V2调拨差异裁决补发子调拨纯构造策略")
class InvTransferReceiptDiscrepancyReshipPolicyTest
{
    private static final Instant NOW = Instant.parse(
            "2026-08-03T07:00:00Z");

    @Test
    @DisplayName("仓库补发保持原方向并由原目标门店提交")
    void shouldPrepareWarehouseReshipFromTargetContext()
    {
        InvTransferReceiptDiscrepancyReshipFact fact = fact("warehouse");

        var prepared = InvTransferReceiptDiscrepancyReshipPolicy.prepare(
                source(fact), fact, 302L);

        assertThat(prepared.transferType()).isEqualTo("warehouse");
        assertThat(prepared.fromDeptId()).isEqualTo(301L);
        assertThat(prepared.fromWarehouseId()).isEqualTo(301L);
        assertThat(prepared.toDeptId()).isEqualTo(302L);
        assertThat(prepared.sourceBusinessType())
                .isEqualTo("transfer_discrepancy_reship");
        assertThat(prepared.sourceBusinessId()).isEqualTo(900L);
        assertThat(prepared.quantity())
                .isEqualByComparingTo("1.0000");
        assertThat(prepared.referenceCostPrice())
                .isEqualByComparingTo("10.123456");
    }

    @Test
    @DisplayName("返仓补发保持原返仓原因并由原来源门店提交")
    void shouldPrepareStoreReturnReshipFromSourceContext()
    {
        InvTransferReceiptDiscrepancyReshipFact fact =
                fact("store_return");
        fact.setReturnReasonCode("OTHER");
        fact.setReturnReasonText("裁决补发原返仓");

        var prepared = InvTransferReceiptDiscrepancyReshipPolicy.prepare(
                source(fact), fact, 301L);

        assertThat(prepared.transferType()).isEqualTo("store_return");
        assertThat(prepared.returnReasonCode()).isEqualTo("OTHER");
        assertThat(prepared.returnReasonText()).isEqualTo("裁决补发原返仓");
    }

    @Test
    @DisplayName("异店补发保持原方向并由来源门店提交")
    void shouldPrepareCrossStoreReshipFromSourceContext()
    {
        InvTransferReceiptDiscrepancyReshipFact fact =
                fact("cross_store");

        var prepared = InvTransferReceiptDiscrepancyReshipPolicy.prepare(
                source(fact), fact, 301L);

        assertThat(prepared.transferType()).isEqualTo("cross_store");
        assertThat(prepared.fromDeptId()).isEqualTo(301L);
        assertThat(prepared.toDeptId()).isEqualTo(302L);
    }

    @Test
    @DisplayName("错误发起组织原始方向或非补发动作均失败关闭")
    void shouldRejectContextDirectionAndActionDrift()
    {
        InvTransferReceiptDiscrepancyReshipFact fact = fact("warehouse");
        assertThatThrownBy(() ->
                InvTransferReceiptDiscrepancyReshipPolicy.prepare(
                        source(fact), fact, 301L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("来源或组织事实无效");

        Source original = source(fact);
        fact.setToWarehouseId(303L);
        assertThatThrownBy(() ->
                InvTransferReceiptDiscrepancyReshipPolicy.prepare(
                        original, fact, 302L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("来源或组织事实无效");

        InvTransferReceiptDiscrepancyReshipFact damaged = fact("warehouse");
        damaged.setDiscrepancyType("damaged");
        damaged.setActionType("return_to_source");
        assertThatThrownBy(() ->
                InvTransferReceiptDiscrepancyReshipPolicy.prepare(
                        source(damaged), damaged, 302L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("不支持该异步裁决工作流");
    }

    @Test
    @DisplayName("返仓补发缺少原返仓原因时失败关闭")
    void shouldRejectStoreReturnWithoutOriginalReason()
    {
        InvTransferReceiptDiscrepancyReshipFact fact =
                fact("store_return");

        assertThatThrownBy(() ->
                InvTransferReceiptDiscrepancyReshipPolicy.prepare(
                        source(fact), fact, 301L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("缺少原返仓原因");
    }

    private static Source source(
            InvTransferReceiptDiscrepancyReshipFact fact)
    {
        return fact.toWorkflowSource("reship-dispatch-0001", 99L,
                "reship-user", NOW);
    }

    private static InvTransferReceiptDiscrepancyReshipFact fact(
            String transferType)
    {
        InvTransferReceiptDiscrepancyReshipFact fact =
                new InvTransferReceiptDiscrepancyReshipFact();
        fact.setCaseId(700L);
        fact.setCaseVersionBefore(5L);
        fact.setAdjudicationId(800L);
        fact.setActionId(900L);
        fact.setActionVersionBefore(0L);
        fact.setDiscrepancyType("shortage");
        fact.setActionType("reship");
        fact.setParentTransferId(600L);
        fact.setParentTransferType(transferType);
        fact.setFromDeptId(301L);
        fact.setFromWarehouseId(301L);
        fact.setToDeptId(302L);
        fact.setToWarehouseId(302L);
        fact.setReceiptAllocationId(5001L);
        fact.setShipmentAllocationId(5002L);
        fact.setItemType("product");
        fact.setItemId(4001L);
        fact.setProductId(4001L);
        fact.setTrackingPolicy("serial");
        fact.setQuantity(new BigDecimal("1.0000"));
        fact.setSourceCostPrice(new BigDecimal("10.123456"));
        fact.setAmount(new BigDecimal("10.123456"));
        fact.setDecisionFingerprint("a".repeat(64));
        return fact;
    }
}
