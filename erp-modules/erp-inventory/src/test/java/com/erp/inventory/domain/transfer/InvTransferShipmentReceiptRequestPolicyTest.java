package com.erp.inventory.domain.transfer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.erp.common.core.exception.ServiceException;
import com.erp.inventory.domain.dto.InvTransferShipmentReceiptAllocationRequest;
import com.erp.inventory.domain.dto.InvTransferShipmentReceiptCreateRequest;

@DisplayName("V2调拨收货服务端请求边界")
class InvTransferShipmentReceiptRequestPolicyTest
{
    private static final String PLAN_VERSION = "a".repeat(64);

    @Test
    @DisplayName("合格整批收货形成稳定规范化命令")
    void shouldNormalizeAcceptedReceipt()
    {
        InvTransferShipmentReceiptCreateRequest request = request(
                allocation("2", "0", "0"), true);
        request.setRemark("  现场验收完成  ");

        InvTransferShipmentReceiptValidatedCommand first =
                validate(request, plan("lot"));
        InvTransferShipmentReceiptValidatedCommand second =
                validate(request, plan("lot"));

        assertThat(first.requestFingerprint()).matches("[a-f0-9]{64}")
                .isEqualTo(second.requestFingerprint());
        assertThat(first.acceptedQuantity()).isEqualByComparingTo("2");
        assertThat(first.remainingQuantityAfter())
                .isEqualByComparingTo("0");
        assertThat(first.remark()).isEqualTo("现场验收完成");
    }

    @Test
    @DisplayName("陈旧规划版本必须拒绝")
    void shouldRejectStalePlanVersion()
    {
        InvTransferShipmentReceiptCreateRequest request = request(
                allocation("2", "0", "0"), true);
        request.setReceiptPlanVersion("b".repeat(64));

        assertThatThrownBy(() -> validate(request, plan("lot")))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("规划已变化");
    }

    @Test
    @DisplayName("来源分配重复或越界必须拒绝")
    void shouldRejectDuplicateOrUnknownAllocation()
    {
        InvTransferShipmentReceiptAllocationRequest first = allocation(
                "1", "0", "0");
        InvTransferShipmentReceiptCreateRequest duplicate = request(first,
                false);
        duplicate.setAllocations(List.of(first, allocation("1", "0", "0")));
        InvTransferShipmentReceiptAllocationRequest unknown = allocation(
                "1", "0", "0");
        unknown.setShipmentAllocationId(999L);

        assertThatThrownBy(() -> validate(duplicate, plan("lot")))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("重复或无效");
        assertThatThrownBy(() -> validate(request(unknown, false),
                plan("lot"))).isInstanceOf(ServiceException.class)
                .hasMessageContaining("不在服务端可执行边界");
    }

    @Test
    @DisplayName("分类数量必须为正且不能超过当前剩余")
    void shouldRejectInvalidClassifiedQuantity()
    {
        assertThatThrownBy(() -> validate(request(
                allocation("0", "0", "0"), false), plan("lot")))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("必须大于零");
        assertThatThrownBy(() -> validate(request(
                allocation("3", "0", "0"), true), plan("lot")))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("不能超过待收数量");
    }

    @Test
    @DisplayName("目标库位必须与数量和服务端候选严格对应")
    void shouldValidateTargetLocations()
    {
        InvTransferShipmentReceiptAllocationRequest smuggled = allocation(
                "0", "0", "1");
        smuggled.setAcceptedLocationId(901L);
        evidence(smuggled);
        InvTransferShipmentReceiptAllocationRequest outside = allocation(
                "1", "0", "0");
        outside.setAcceptedLocationId(999L);

        assertThatThrownBy(() -> validate(request(smuggled, false),
                plan("lot"))).isInstanceOf(ServiceException.class)
                .hasMessageContaining("零数量不能夹带");
        assertThatThrownBy(() -> validate(request(outside, false),
                plan("lot"))).isInstanceOf(ServiceException.class)
                .hasMessageContaining("不在服务端边界");
    }

    @Test
    @DisplayName("残损或短缺必须同时提供说明与附件引用")
    void shouldRequireDiscrepancyEvidence()
    {
        InvTransferShipmentReceiptAllocationRequest damaged = allocation(
                "0", "1", "0");

        assertThatThrownBy(() -> validate(request(damaged, false),
                plan("lot"))).isInstanceOf(ServiceException.class)
                .hasMessageContaining("说明和附件");

        evidence(damaged);
        assertThat(validate(request(damaged, false), plan("lot"))
                .damagedQuantity()).isEqualByComparingTo("1");
    }

    @Test
    @DisplayName("批次跟踪分配不能夹带序列号")
    void shouldRejectSerialsForLotTracking()
    {
        InvTransferShipmentReceiptAllocationRequest value = allocation(
                "1", "0", "0");
        value.setAcceptedSerialIds(List.of(51L));

        assertThatThrownBy(() -> validate(request(value, false),
                plan("lot"))).isInstanceOf(ServiceException.class)
                .hasMessageContaining("不得提交序列号");
    }

    @Test
    @DisplayName("序列号分类数量、范围和全请求唯一性必须守恒")
    void shouldValidateSerialClassification()
    {
        InvTransferShipmentReceiptAllocationRequest value = allocation(
                "2", "0", "0");
        value.setAcceptedSerialIds(List.of(52L, 51L));

        InvTransferShipmentReceiptValidatedCommand command = validate(
                request(value, true), plan("serial"));
        assertThat(command.allocations().get(0).acceptedSerialIds())
                .containsExactly(51L, 52L);

        value.setAcceptedSerialIds(List.of(51L, 999L));
        assertThatThrownBy(() -> validate(request(value, true),
                plan("serial"))).isInstanceOf(ServiceException.class)
                .hasMessageContaining("不在服务端待收边界");

        value.setAcceptedSerialIds(List.of(51L));
        assertThatThrownBy(() -> validate(request(value, true),
                plan("serial"))).isInstanceOf(ServiceException.class)
                .hasMessageContaining("数量不守恒");
    }

    @Test
    @DisplayName("完成标志必须与全批提交后剩余量一致")
    void shouldRequireExactFinalizeState()
    {
        InvTransferShipmentReceiptValidatedCommand partial = validate(
                request(allocation("1", "0", "0"), false), plan("lot"));
        assertThat(partial.remainingQuantityAfter())
                .isEqualByComparingTo("1");

        assertThatThrownBy(() -> validate(request(
                allocation("1", "0", "0"), true), plan("lot")))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("仍有待收数量");
        assertThatThrownBy(() -> validate(request(
                allocation("2", "0", "0"), false), plan("lot")))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("必须确认完成");
    }

    @Test
    @DisplayName("序列号输入顺序不影响请求指纹")
    void shouldFingerprintCanonicalSerialOrder()
    {
        InvTransferShipmentReceiptAllocationRequest first = allocation(
                "2", "0", "0");
        first.setAcceptedSerialIds(List.of(51L, 52L));
        InvTransferShipmentReceiptAllocationRequest second = allocation(
                "2", "0", "0");
        second.setAcceptedSerialIds(List.of(52L, 51L));

        assertThat(validate(request(first, true), plan("serial"))
                .requestFingerprint()).isEqualTo(validate(
                        request(second, true), plan("serial"))
                                .requestFingerprint());
    }

    private static InvTransferShipmentReceiptValidatedCommand validate(
            InvTransferShipmentReceiptCreateRequest request,
            InvTransferShipmentReceiptPlanComposer.Composition plan)
    {
        return InvTransferShipmentReceiptRequestPolicy.validate(request,
                plan);
    }

    private static InvTransferShipmentReceiptCreateRequest request(
            InvTransferShipmentReceiptAllocationRequest allocation,
            boolean finalizeShipment)
    {
        InvTransferShipmentReceiptCreateRequest value =
                new InvTransferShipmentReceiptCreateRequest();
        value.setReceiptPlanVersion(PLAN_VERSION);
        value.setBasis("server-recommendation");
        value.setArrivedTime(Date.from(Instant.parse(
                "2026-08-02T10:00:00Z")));
        value.setFinalizeShipment(finalizeShipment);
        value.setAllocations(List.of(allocation));
        return value;
    }

    private static InvTransferShipmentReceiptAllocationRequest allocation(
            String accepted, String damaged, String shortage)
    {
        InvTransferShipmentReceiptAllocationRequest value =
                new InvTransferShipmentReceiptAllocationRequest();
        value.setShipmentAllocationId(81L);
        value.setAcceptedQuantity(new BigDecimal(accepted));
        value.setAcceptedLocationId(new BigDecimal(accepted).signum() > 0
                ? 901L : null);
        value.setDamagedQuantity(new BigDecimal(damaged));
        value.setQuarantineLocationId(new BigDecimal(damaged).signum() > 0
                ? 902L : null);
        value.setShortageQuantity(new BigDecimal(shortage));
        value.setAcceptedSerialIds(List.of());
        value.setDamagedSerialIds(List.of());
        value.setShortageSerialIds(List.of());
        return value;
    }

    private static void evidence(
            InvTransferShipmentReceiptAllocationRequest value)
    {
        value.setDiscrepancyNote("现场差异");
        value.setAttachmentRefs("upload://receipt-proof-1");
    }

    private static InvTransferShipmentReceiptPlanComposer.Composition plan(
            String tracking)
    {
        InvTransferReceiptPlanningAllocationFact fact =
                new InvTransferReceiptPlanningAllocationFact();
        fact.setAllocationId(81L);
        fact.setTrackingPolicy(tracking);
        List<InvTransferReceiptPlanningSerialFact> serials =
                "serial".equals(tracking)
                        ? new ArrayList<>(List.of(serial(51L), serial(52L)))
                        : List.of();
        InvTransferShipmentReceiptPlanComposer.Line line =
                new InvTransferShipmentReceiptPlanComposer.Line(fact,
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                        new BigDecimal("2"), new BigDecimal("2"), serials,
                        "ready", List.of());
        InvWarehouseStockMode mode = new InvWarehouseStockMode();
        mode.setWarehouseId(302L);
        mode.setWriteMode("dual");
        mode.setReadMode("detail");
        mode.setReconcileStatus("passed");
        mode.setLastReconcileBatch("target-reconcile-1");
        return new InvTransferShipmentReceiptPlanComposer.Composition(
                PLAN_VERSION, mode, List.of(line),
                List.of(location(901L, "storage")),
                List.of(location(902L, "quarantine")), true, List.of());
    }

    private static InvTransferReceiptPlanningSerialFact serial(Long id)
    {
        InvTransferReceiptPlanningSerialFact value =
                new InvTransferReceiptPlanningSerialFact();
        value.setSerialId(id);
        return value;
    }

    private static InvTransferReceiptLocationCandidate location(Long id,
            String type)
    {
        InvTransferReceiptLocationCandidate value =
                new InvTransferReceiptLocationCandidate();
        value.setLocationId(id);
        value.setLocationType(type);
        return value;
    }
}
