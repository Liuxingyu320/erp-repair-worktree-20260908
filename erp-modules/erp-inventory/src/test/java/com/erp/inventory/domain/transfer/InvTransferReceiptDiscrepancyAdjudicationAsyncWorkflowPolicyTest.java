package com.erp.inventory.domain.transfer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Date;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.erp.common.core.exception.ServiceException;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationAsyncWorkflowPolicy.Child;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationAsyncWorkflowPolicy.PreparedLink;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationAsyncWorkflowPolicy.Source;

@DisplayName("V2调拨差异裁决异步子调拨纯契约")
class
        InvTransferReceiptDiscrepancyAdjudicationAsyncWorkflowPolicyTest
{
    private static final Instant NOW = Instant.parse(
            "2026-08-03T06:00:00.123456789Z");

    @Test
    @DisplayName("补发保持原方向和类型并绑定可用库存子调拨")
    void shouldPrepareReshipLink()
    {
        Source source = source("shortage", "reship", "warehouse",
                null, null, null);
        Child child = dispatchChild(9101L, "warehouse",
                "transfer_discrepancy_reship", 900L, 301L, 302L,
                "available_stock");

        PreparedLink prepared =
                InvTransferReceiptDiscrepancyAdjudicationAsyncWorkflowPolicy
                        .prepare(source, child);

        assertThat(prepared.workflowType()).isEqualTo("reship");
        assertThat(prepared.effectKind()).isEqualTo("reship_workflow");
        assertThat(prepared.childSourceLocationDeptId()).isEqualTo(301L);
        assertThat(prepared.childTargetLocationDeptId()).isEqualTo(302L);
        assertThat(prepared.childTransferType()).isEqualTo("warehouse");
        assertThat(prepared.inventorySource()).isEqualTo("available_stock");
        assertThat(prepared.effectReference())
                .isEqualTo("reship_transfer:9101");
        assertThat(prepared.quarantineBalanceId()).isNull();
        assertThat(prepared.createdAt())
                .isEqualTo(Instant.parse("2026-08-03T06:00:00Z"));
        assertThat(prepared.workflowFingerprint())
                .matches("[a-f0-9]{64}");
    }

    @Test
    @DisplayName("退回反转方向和仓店类型并锚定隔离明细")
    void shouldPrepareReturnLink()
    {
        Source source = source("damaged", "return_to_source", "warehouse",
                7001L, 7002L, 7003L);
        Child child = dispatchChild(9102L, "store_return",
                "transfer_discrepancy_return", 900L, 302L, 301L,
                "quarantine_detail");

        PreparedLink prepared =
                InvTransferReceiptDiscrepancyAdjudicationAsyncWorkflowPolicy
                        .prepare(source, child);

        assertThat(prepared.workflowType()).isEqualTo("return");
        assertThat(prepared.effectKind()).isEqualTo("return_workflow");
        assertThat(prepared.childSourceLocationDeptId()).isEqualTo(302L);
        assertThat(prepared.childTargetLocationDeptId()).isEqualTo(301L);
        assertThat(prepared.childTransferType()).isEqualTo("store_return");
        assertThat(prepared.inventorySource())
                .isEqualTo("quarantine_detail");
        assertThat(prepared.effectReference())
                .isEqualTo("return_transfer:9102");
        assertThat(prepared.quarantineBalanceId()).isEqualTo(7001L);
        assertThat(prepared.quarantineLotId()).isEqualTo(7002L);
        assertThat(prepared.quarantineLocationId()).isEqualTo(7003L);
    }

    @Test
    @DisplayName("退回缺少隔离锚点或子调拨方向漂移时失败关闭")
    void shouldRejectInvalidReturnFacts()
    {
        Source missingQuarantine = source("damaged", "return_to_source",
                "cross_store", null, 7002L, 7003L);
        Child expected = dispatchChild(9102L, "cross_store",
                "transfer_discrepancy_return", 900L, 302L, 301L,
                "quarantine_detail");

        assertThatThrownBy(() ->
                InvTransferReceiptDiscrepancyAdjudicationAsyncWorkflowPolicy
                        .prepare(missingQuarantine, expected))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("不支持该异步裁决工作流");

        Source source = source("damaged", "return_to_source",
                "cross_store", 7001L, 7002L, 7003L);
        Child wrongDirection = dispatchChild(9102L, "cross_store",
                "transfer_discrepancy_return", 900L, 301L, 302L,
                "quarantine_detail");
        assertThatThrownBy(() ->
                InvTransferReceiptDiscrepancyAdjudicationAsyncWorkflowPolicy
                        .prepare(source, wrongDirection))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("创建或冻结事实无效");
    }

    @Test
    @DisplayName("未精确冻结数量或已有发货事实时不能绑定")
    void shouldRejectUnfrozenOrStartedChild()
    {
        Source source = source("shortage", "reship", "warehouse",
                null, null, null);
        Child unfrozen = child(9101L, "warehouse",
                "transfer_discrepancy_reship", 900L, 301L, 302L,
                "submitted", "available_stock", new BigDecimal("0.5000"),
                BigDecimal.ZERO, 0, 0, 0, 0);
        Child started = child(9101L, "warehouse",
                "transfer_discrepancy_reship", 900L, 301L, 302L,
                "submitted", "available_stock", new BigDecimal("1.0000"),
                BigDecimal.ZERO, 1, 0, 0, 0);

        assertThatThrownBy(() ->
                InvTransferReceiptDiscrepancyAdjudicationAsyncWorkflowPolicy
                        .prepare(source, unfrozen))
                .isInstanceOf(ServiceException.class);
        assertThatThrownBy(() ->
                InvTransferReceiptDiscrepancyAdjudicationAsyncWorkflowPolicy
                        .prepare(source, started))
                .isInstanceOf(ServiceException.class);
    }

    @Test
    @DisplayName("同一子调拨只有形成纯V2终态证据后才能完成")
    void shouldVerifyAuthoritativeCompletion()
    {
        Source source = source("shortage", "reship", "warehouse",
                null, null, null);
        PreparedLink link =
                InvTransferReceiptDiscrepancyAdjudicationAsyncWorkflowPolicy
                        .prepare(source, dispatchChild(9101L, "warehouse",
                                "transfer_discrepancy_reship", 900L, 301L,
                                302L, "available_stock"));
        Child completed = child(9101L, "warehouse",
                "transfer_discrepancy_reship", 900L, 301L, 302L,
                "received", "available_stock", new BigDecimal("1.0000"),
                new BigDecimal("1.0000"), 2, 0, 2, 0);

        var proof =
                InvTransferReceiptDiscrepancyAdjudicationAsyncWorkflowPolicy
                        .verifyCompletion(link, completed);

        assertThat(proof.actionId()).isEqualTo(900L);
        assertThat(proof.childTransferId()).isEqualTo(9101L);
        assertThat(proof.effectReference())
                .isEqualTo("reship_transfer:9101");
        assertThat(proof.workflowFingerprint())
                .isEqualTo(link.workflowFingerprint());
    }

    @Test
    @DisplayName("旧发货未收货未全量或开放差异均不能完成")
    void shouldRejectIncompleteOrLegacyCompletion()
    {
        Source source = source("shortage", "reship", "warehouse",
                null, null, null);
        PreparedLink link =
                InvTransferReceiptDiscrepancyAdjudicationAsyncWorkflowPolicy
                        .prepare(source, dispatchChild(9101L, "warehouse",
                                "transfer_discrepancy_reship", 900L, 301L,
                                302L, "available_stock"));

        assertIncomplete(link, child(9101L, "warehouse",
                "transfer_discrepancy_reship", 900L, 301L, 302L,
                "delivered", "available_stock", new BigDecimal("1.0000"),
                new BigDecimal("1.0000"), 1, 0, 0, 0));
        assertIncomplete(link, child(9101L, "warehouse",
                "transfer_discrepancy_reship", 900L, 301L, 302L,
                "received", "available_stock", new BigDecimal("1.0000"),
                new BigDecimal("0.5000"), 1, 0, 1, 0));
        assertIncomplete(link, child(9101L, "warehouse",
                "transfer_discrepancy_reship", 900L, 301L, 302L,
                "received", "available_stock", new BigDecimal("1.0000"),
                new BigDecimal("1.0000"), 1, 1, 1, 0));
        assertIncomplete(link, child(9101L, "warehouse",
                "transfer_discrepancy_reship", 900L, 301L, 302L,
                "received", "available_stock", new BigDecimal("1.0000"),
                new BigDecimal("1.0000"), 1, 0, 1, 1));
    }

    @Test
    @DisplayName("已释放或未全量消费的实际预留不能形成完成证据")
    void shouldRejectReleasedOrPartiallyConsumedReservationCompletion()
    {
        Source source = source("shortage", "reship", "warehouse",
                null, null, null);
        PreparedLink link =
                InvTransferReceiptDiscrepancyAdjudicationAsyncWorkflowPolicy
                        .prepare(source, dispatchChild(9101L, "warehouse",
                                "transfer_discrepancy_reship", 900L, 301L,
                                302L, "available_stock"));
        Child completed = child(9101L, "warehouse",
                "transfer_discrepancy_reship", 900L, 301L, 302L,
                "received", "available_stock", new BigDecimal("1.0000"),
                new BigDecimal("1.0000"), 2, 0, 2, 0);

        assertIncomplete(link, withReservationLifecycle(completed,
                InvTransferReceiptDiscrepancyReturnReservationLifecyclePolicy
                        .RELEASED,
                BigDecimal.ZERO, new BigDecimal("1.0000")));
        assertIncomplete(link, withReservationLifecycle(completed,
                InvTransferReceiptDiscrepancyReturnReservationLifecyclePolicy
                        .PARTIAL,
                new BigDecimal("0.5000"), BigDecimal.ZERO));
    }

    @Test
    @DisplayName("工作流指纹确定且对请求和子调拨敏感")
    void shouldCreateDeterministicSensitiveFingerprint()
    {
        Source source = source("shortage", "reship", "warehouse",
                null, null, null);
        Child child = dispatchChild(9101L, "warehouse",
                "transfer_discrepancy_reship", 900L, 301L, 302L,
                "available_stock");

        String first =
                InvTransferReceiptDiscrepancyAdjudicationAsyncWorkflowPolicy
                        .prepare(source, child).workflowFingerprint();
        String second =
                InvTransferReceiptDiscrepancyAdjudicationAsyncWorkflowPolicy
                        .prepare(source, child).workflowFingerprint();
        String changed =
                InvTransferReceiptDiscrepancyAdjudicationAsyncWorkflowPolicy
                        .prepare(source, dispatchChild(9102L, "warehouse",
                                "transfer_discrepancy_reship", 900L, 301L,
                                302L, "available_stock"))
                        .workflowFingerprint();

        assertThat(first).isEqualTo(second).isNotEqualTo(changed);
    }

    @Test
    @DisplayName("持久化关系Bean逐字段还原纯策略契约")
    void shouldRehydrateStoredLinkWithoutFieldDrift()
    {
        PreparedLink prepared =
                InvTransferReceiptDiscrepancyAdjudicationAsyncWorkflowPolicy
                        .prepare(source("damaged", "return_to_source",
                                "warehouse", 7001L, 7002L, 7003L),
                                dispatchChild(9102L, "store_return",
                                        "transfer_discrepancy_return",
                                        900L, 302L, 301L,
                                        "quarantine_detail"));
        var stored =
                new InvTransferReceiptDiscrepancyAdjudicationAsyncWorkflowLink();
        stored.setWorkflowId(1L);
        stored.setRequestId(prepared.requestId());
        stored.setCaseId(prepared.caseId());
        stored.setCaseVersionBefore(prepared.caseVersionBefore());
        stored.setAdjudicationId(prepared.adjudicationId());
        stored.setActionId(prepared.actionId());
        stored.setActionVersionBefore(prepared.actionVersionBefore());
        stored.setDiscrepancyType(prepared.discrepancyType());
        stored.setActionType(prepared.actionType());
        stored.setEffectKind(prepared.effectKind());
        stored.setWorkflowType(prepared.workflowType());
        stored.setParentTransferId(prepared.parentTransferId());
        stored.setParentTransferType(prepared.parentTransferType());
        stored.setChildTransferId(prepared.childTransferId());
        stored.setChildTransferType(prepared.childTransferType());
        stored.setOriginalSourceLocationDeptId(
                prepared.originalSourceLocationDeptId());
        stored.setOriginalTargetLocationDeptId(
                prepared.originalTargetLocationDeptId());
        stored.setChildSourceLocationDeptId(
                prepared.childSourceLocationDeptId());
        stored.setChildTargetLocationDeptId(
                prepared.childTargetLocationDeptId());
        stored.setReceiptAllocationId(prepared.receiptAllocationId());
        stored.setShipmentAllocationId(prepared.shipmentAllocationId());
        stored.setItemType(prepared.itemType());
        stored.setItemId(prepared.itemId());
        stored.setProductId(prepared.productId());
        stored.setTrackingPolicy(prepared.trackingPolicy());
        stored.setInventorySource(prepared.inventorySource());
        stored.setQuarantineBalanceId(prepared.quarantineBalanceId());
        stored.setQuarantineLotId(prepared.quarantineLotId());
        stored.setQuarantineLocationId(prepared.quarantineLocationId());
        stored.setQuantity(prepared.quantity());
        stored.setSourceCostPrice(prepared.sourceCostPrice());
        stored.setAmount(prepared.amount());
        stored.setSourceBusinessType(prepared.sourceBusinessType());
        stored.setSourceBusinessId(prepared.sourceBusinessId());
        stored.setEffectReference(prepared.effectReference());
        stored.setDispatchChildStatus(prepared.dispatchChildStatus());
        stored.setReservationCount(prepared.reservationCount());
        stored.setReservedQuantity(prepared.reservedQuantity());
        stored.setDecisionFingerprint(prepared.decisionFingerprint());
        stored.setWorkflowFingerprint(prepared.workflowFingerprint());
        stored.setExecutorUserId(prepared.executorUserId());
        stored.setExecutorName(prepared.executorName());
        stored.setCreateTime(Date.from(prepared.createdAt()));

        assertThat(stored.toPolicyLink()).isEqualTo(prepared);
    }

    @Test
    @DisplayName("子调拨事实Bean逐字段还原纯策略契约")
    void shouldRehydrateChildFactWithoutFieldDrift()
    {
        Child child = child(9101L, "warehouse",
                "transfer_discrepancy_reship", 900L, 301L, 302L,
                "received", "available_stock",
                new BigDecimal("1.0000"), new BigDecimal("1.0000"),
                2, 0, 2, 0);
        var fact =
                new InvTransferReceiptDiscrepancyAdjudicationAsyncWorkflowChildFact();
        fact.setTransferId(child.transferId());
        fact.setTransferType(child.transferType());
        fact.setSourceBusinessType(child.sourceBusinessType());
        fact.setSourceBusinessId(child.sourceBusinessId());
        fact.setSourceLocationDeptId(child.sourceLocationDeptId());
        fact.setTargetLocationDeptId(child.targetLocationDeptId());
        fact.setStatus(child.status());
        fact.setDetailCount(child.detailCount());
        fact.setItemType(child.itemType());
        fact.setItemId(child.itemId());
        fact.setProductId(child.productId());
        fact.setRequestedQuantity(child.requestedQuantity());
        fact.setDeliveredQuantity(child.deliveredQuantity());
        fact.setReservationKind(child.reservationKind());
        fact.setReservationCount(child.reservationCount());
        fact.setReservedQuantity(child.reservedQuantity());
        fact.setReservationStatus(child.reservationStatus());
        fact.setConsumedQuantity(child.consumedQuantity());
        fact.setReleasedQuantity(child.releasedQuantity());
        fact.setV2ShipmentCount(child.v2ShipmentCount());
        fact.setLegacyShipmentCount(child.legacyShipmentCount());
        fact.setReceiptCount(child.receiptCount());
        fact.setOpenDiscrepancyCount(child.openDiscrepancyCount());

        assertThat(fact.toPolicyChild()).isEqualTo(child);
    }

    private static void assertIncomplete(PreparedLink link, Child child)
    {
        assertThatThrownBy(() ->
                InvTransferReceiptDiscrepancyAdjudicationAsyncWorkflowPolicy
                        .verifyCompletion(link, child))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("尚未形成权威完成事实");
    }

    private static Source source(String discrepancyType, String actionType,
            String transferType, Long balanceId, Long lotId,
            Long locationId)
    {
        return new Source("workflow-dispatch-0001", 700L, 5L, 800L,
                900L, 0L, discrepancyType, actionType, 600L,
                transferType, 301L, 302L, 5001L, 5002L, "product",
                4001L, 4001L, "serial", balanceId, lotId, locationId,
                new BigDecimal("1.0000"), new BigDecimal("10.000000"),
                new BigDecimal("10.000000"), "a".repeat(64), 99L,
                "workflow-user", NOW);
    }

    private static Child dispatchChild(Long transferId, String transferType,
            String sourceBusinessType, Long sourceBusinessId,
            Long sourceDeptId, Long targetDeptId, String reservationKind)
    {
        return child(transferId, transferType, sourceBusinessType,
                sourceBusinessId, sourceDeptId, targetDeptId, "submitted",
                reservationKind, new BigDecimal("1.0000"), BigDecimal.ZERO,
                0, 0, 0, 0);
    }

    private static Child child(Long transferId, String transferType,
            String sourceBusinessType, Long sourceBusinessId,
            Long sourceDeptId, Long targetDeptId, String status,
            String reservationKind, BigDecimal reservedQuantity,
            BigDecimal deliveredQuantity, int v2Shipments,
            int legacyShipments, int receipts, int openDiscrepancies)
    {
        String reservationStatus;
        if (deliveredQuantity.compareTo(reservedQuantity) == 0)
        {
            reservationStatus =
                    InvTransferReceiptDiscrepancyReturnReservationLifecyclePolicy
                            .CONSUMED;
        }
        else if (deliveredQuantity.signum() > 0)
        {
            reservationStatus =
                    InvTransferReceiptDiscrepancyReturnReservationLifecyclePolicy
                            .PARTIAL;
        }
        else
        {
            reservationStatus =
                    InvTransferReceiptDiscrepancyReturnReservationLifecyclePolicy
                            .ACTIVE;
        }
        return new Child(transferId, transferType, sourceBusinessType,
                sourceBusinessId, sourceDeptId, targetDeptId, status, 1,
                "product", 4001L, 4001L, new BigDecimal("1.0000"),
                deliveredQuantity, reservationKind, 1, reservedQuantity,
                reservationStatus, deliveredQuantity, BigDecimal.ZERO,
                v2Shipments, legacyShipments, receipts, openDiscrepancies);
    }

    private static Child withReservationLifecycle(Child child, String status,
            BigDecimal consumed, BigDecimal released)
    {
        return new Child(child.transferId(), child.transferType(),
                child.sourceBusinessType(), child.sourceBusinessId(),
                child.sourceLocationDeptId(), child.targetLocationDeptId(),
                child.status(), child.detailCount(), child.itemType(),
                child.itemId(), child.productId(), child.requestedQuantity(),
                child.deliveredQuantity(), child.reservationKind(),
                child.reservationCount(), child.reservedQuantity(), status,
                consumed, released, child.v2ShipmentCount(),
                child.legacyShipmentCount(), child.receiptCount(),
                child.openDiscrepancyCount());
    }
}
