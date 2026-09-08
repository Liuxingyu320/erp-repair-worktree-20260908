package com.erp.inventory.domain.transfer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.erp.common.core.exception.ServiceException;
import com.erp.inventory.domain.dto.InvTransferReceiptDiscrepancyReturnReceiptAllocationRequest;
import com.erp.inventory.domain.dto.InvTransferReceiptDiscrepancyReturnReceiptCreateRequest;

@DisplayName("V2差异退回专属收货服务端请求边界")
class InvTransferReceiptDiscrepancyReturnReceiptRequestPolicyTest
{
    private static final String PLAN_VERSION = "a".repeat(64);

    @Test
    @DisplayName("预期退回实收形成稳定命令且不产生差异证据")
    void shouldNormalizeExpectedReturn()
    {
        var request = request(allocation("2", "0"), true);
        request.setRemark("  隔离收货完成  ");

        var first = validate(request, plan("lot"));
        var second = validate(request, plan("lot"));

        assertThat(first.requestFingerprint()).matches("[a-f0-9]{64}")
                .isEqualTo(second.requestFingerprint());
        assertThat(first.returnedQuantity()).isEqualByComparingTo("2");
        assertThat(first.shortageQuantity()).isZero();
        assertThat(first.remainingQuantityAfter()).isZero();
        assertThat(first.allocations().get(0).discrepancyNote()).isNull();
        assertThat(first.remark()).isEqualTo("隔离收货完成");
    }

    @Test
    @DisplayName("陈旧规划与普通收货依据必须拒绝")
    void shouldRejectStaleOrOrdinaryPlanEnvelope()
    {
        var stale = request(allocation("2", "0"), true);
        stale.setReturnReceiptPlanVersion("b".repeat(64));
        assertThatThrownBy(() -> validate(stale, plan("lot")))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("规划已变化");

        var ordinary = request(allocation("2", "0"), true);
        ordinary.setBasis("server-recommendation");
        assertThatThrownBy(() -> validate(ordinary, plan("lot")))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("依据无效");
    }

    @Test
    @DisplayName("只有意外短缺必须提供差异证据")
    void shouldRequireEvidenceOnlyForUnexpectedShortage()
    {
        var expected = allocation("1", "0");
        expected.setDiscrepancyNote("不得夹带");
        assertThatThrownBy(() -> validate(request(expected, false),
                plan("lot"))).isInstanceOf(ServiceException.class)
                .hasMessageContaining("不能夹带差异证据");

        var shortage = allocation("1", "1");
        assertThatThrownBy(() -> validate(request(shortage, true),
                plan("lot"))).isInstanceOf(ServiceException.class)
                .hasMessageContaining("说明和附件");
        evidence(shortage);
        assertThat(validate(request(shortage, true), plan("lot"))
                .shortageQuantity()).isEqualByComparingTo("1");
    }

    @Test
    @DisplayName("隔离库位必须与退回实收数量严格对应")
    void shouldValidateQuarantineLocation()
    {
        var smuggled = allocation("0", "1");
        smuggled.setQuarantineLocationId(902L);
        evidence(smuggled);
        assertThatThrownBy(() -> validate(request(smuggled, false),
                plan("lot"))).isInstanceOf(ServiceException.class)
                .hasMessageContaining("零退回实收不能夹带");

        var outside = allocation("1", "0");
        outside.setQuarantineLocationId(999L);
        assertThatThrownBy(() -> validate(request(outside, false),
                plan("lot"))).isInstanceOf(ServiceException.class)
                .hasMessageContaining("不在服务端边界");
    }

    @Test
    @DisplayName("数量必须大于零且不能越过待收边界")
    void shouldRejectZeroOrExcessClassification()
    {
        assertThatThrownBy(() -> validate(request(
                allocation("0", "0"), false), plan("lot")))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("必须大于零");
        assertThatThrownBy(() -> validate(request(
                allocation("3", "0"), true), plan("lot")))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("不能超过待收数量");
    }

    @Test
    @DisplayName("批次跟踪禁止夹带序列号")
    void shouldRejectSerialsForLotTracking()
    {
        var value = allocation("1", "0");
        value.setReturnedSerialIds(List.of(51L));

        assertThatThrownBy(() -> validate(request(value, false),
                plan("lot"))).isInstanceOf(ServiceException.class)
                .hasMessageContaining("不得提交序列号");
    }

    @Test
    @DisplayName("序列号数量范围与规范顺序必须守恒")
    void shouldValidateSerialReturnAndShortage()
    {
        var value = allocation("1", "1");
        value.setReturnedSerialIds(List.of(52L));
        value.setShortageSerialIds(List.of(51L));
        evidence(value);

        var command = validate(request(value, true), plan("serial"));
        assertThat(command.allocations().get(0).returnedSerialIds())
                .containsExactly(52L);
        assertThat(command.allocations().get(0).shortageSerialIds())
                .containsExactly(51L);

        value.setReturnedSerialIds(List.of(999L));
        assertThatThrownBy(() -> validate(request(value, true),
                plan("serial"))).isInstanceOf(ServiceException.class)
                .hasMessageContaining("不在专属退回待收边界");
        value.setReturnedSerialIds(List.of());
        assertThatThrownBy(() -> validate(request(value, true),
                plan("serial"))).isInstanceOf(ServiceException.class)
                .hasMessageContaining("数量不守恒");
    }

    @Test
    @DisplayName("完成标志必须与全批分类后剩余量一致")
    void shouldRequireExactFinalizeState()
    {
        assertThat(validate(request(allocation("1", "0"), false),
                plan("lot")).remainingQuantityAfter())
                .isEqualByComparingTo("1");
        assertThatThrownBy(() -> validate(request(
                allocation("1", "0"), true), plan("lot")))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("仍有退回待收数量");
        assertThatThrownBy(() -> validate(request(
                allocation("2", "0"), false), plan("lot")))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("必须确认完成");
    }

    private static InvTransferReceiptDiscrepancyReturnReceiptValidatedCommand
            validate(
                    InvTransferReceiptDiscrepancyReturnReceiptCreateRequest
                            request,
                    InvTransferReceiptDiscrepancyReturnReceiptPolicy.Plan plan)
    {
        return InvTransferReceiptDiscrepancyReturnReceiptRequestPolicy
                .validate(request, plan);
    }

    private static InvTransferReceiptDiscrepancyReturnReceiptCreateRequest
            request(
                    InvTransferReceiptDiscrepancyReturnReceiptAllocationRequest
                            allocation,
                    boolean finalize)
    {
        var value =
                new InvTransferReceiptDiscrepancyReturnReceiptCreateRequest();
        value.setReturnReceiptPlanVersion(PLAN_VERSION);
        value.setBasis("return-receipt-quarantine-plan-v1");
        value.setArrivedTime(Date.from(Instant.parse(
                "2026-08-03T00:00:00Z")));
        value.setFinalizeShipment(finalize);
        value.setAllocations(List.of(allocation));
        return value;
    }

    private static InvTransferReceiptDiscrepancyReturnReceiptAllocationRequest
            allocation(String returned, String shortage)
    {
        var value =
                new InvTransferReceiptDiscrepancyReturnReceiptAllocationRequest();
        value.setShipmentAllocationId(81L);
        value.setReturnedQuantity(new BigDecimal(returned));
        value.setShortageQuantity(new BigDecimal(shortage));
        value.setQuarantineLocationId(new BigDecimal(returned).signum() > 0
                ? 902L : null);
        return value;
    }

    private static void evidence(
            InvTransferReceiptDiscrepancyReturnReceiptAllocationRequest value)
    {
        value.setDiscrepancyNote("退回到货短缺");
        value.setAttachmentRefs("upload://return-shortage-proof-1");
    }

    private static InvTransferReceiptDiscrepancyReturnReceiptPolicy.Plan plan(
            String tracking)
    {
        var fact = new InvTransferReceiptPlanningAllocationFact();
        fact.setAllocationId(81L);
        fact.setTrackingPolicy(tracking);
        var serials = "serial".equals(tracking)
                ? List.of(serial(51L), serial(52L)) : List
                        .<InvTransferReceiptPlanningSerialFact>of();
        var line = new InvTransferReceiptDiscrepancyReturnReceiptPolicy.Line(
                fact, BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("2"), new BigDecimal("2"), serials,
                "ready", List.of());
        var location = new InvTransferReceiptLocationCandidate();
        location.setLocationId(902L);
        location.setLocationType("quarantine");
        var mode = new InvWarehouseStockMode();
        return new InvTransferReceiptDiscrepancyReturnReceiptPolicy.Plan(
                PLAN_VERSION, mode, List.of(line), List.of(location), true,
                List.of());
    }

    private static InvTransferReceiptPlanningSerialFact serial(Long id)
    {
        var value = new InvTransferReceiptPlanningSerialFact();
        value.setSerialId(id);
        return value;
    }
}
