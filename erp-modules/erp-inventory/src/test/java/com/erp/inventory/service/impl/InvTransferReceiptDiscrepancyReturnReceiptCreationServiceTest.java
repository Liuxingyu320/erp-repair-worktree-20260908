package com.erp.inventory.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.security.shop.ShopScopeService;
import com.erp.common.security.utils.SecurityUtils;
import com.erp.inventory.constant.InvTransferShipmentWriteVersions;
import com.erp.inventory.constant.InvTransferTypes;
import com.erp.inventory.domain.InvTransferOrder;
import com.erp.inventory.domain.InvTransferShipment;
import com.erp.inventory.domain.dto.InvTransferReceiptDiscrepancyReturnReceiptAllocationRequest;
import com.erp.inventory.domain.dto.InvTransferReceiptDiscrepancyReturnReceiptCreateRequest;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyReturnReceiptLockedBoundary;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyReturnReceiptPolicy;
import com.erp.inventory.domain.transfer.InvTransferReceiptLocationCandidate;
import com.erp.inventory.domain.transfer.InvTransferReceiptPlanningAllocationFact;
import com.erp.inventory.domain.transfer.InvTransferShipmentReceiptLockedBoundary;
import com.erp.inventory.domain.transfer.InvTransferShipmentReceiptMutationPlanner;
import com.erp.inventory.domain.transfer.InvTransferShipmentReceiptPreparedMutation;
import com.erp.inventory.domain.transfer.InvWarehouseStockMode;
import com.erp.inventory.domain.vo.InvTransferReceiptDiscrepancyReturnReceiptCreationVo;
import com.erp.inventory.mapper.InvDeptScopeMapper;

@DisplayName("V2差异退回专属收货原子参与者")
class InvTransferReceiptDiscrepancyReturnReceiptCreationServiceTest
{
    private static final String PLAN = "a".repeat(64);
    private static final Instant NOW = Instant.parse(
            "2026-08-03T01:00:00Z");

    @Test
    @DisplayName("默认关闭时在任何业务行锁前拒绝")
    void shouldRejectBeforeLocksWhenGateIsClosed()
    {
        Fixture fixture = fixture(false, false);

        assertThatThrownBy(() -> fixture.service().create(
                "return-receipt-0001", 91L, request(), 101L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("尚未启用");
        verifyNoInteractions(fixture.lockGateway());
        verifyNoInteractions(fixture.mutationExecutor());
    }

    @Test
    @DisplayName("锁定专属规划后只委托唯一收货执行器")
    void shouldPlanAndDelegateToOnlyReceiptExecutor()
    {
        Fixture fixture = fixture(true, true);
        var shipment = shipment();
        var order = order();
        var header = new InvTransferShipmentReceiptLockGateway.LockedHeader(
                shipment, order);
        var boundary = new
                InvTransferReceiptDiscrepancyReturnReceiptLockedBoundary(
                        mock(InvTransferShipmentReceiptLockedBoundary.class),
                        plan());
        var request = request();
        var mutation = mock(
                InvTransferShipmentReceiptPreparedMutation.class);
        var expected = mock(
                InvTransferReceiptDiscrepancyReturnReceiptCreationVo.class);

        when(fixture.shopScopeService().resolveRequiredShopDept(101L))
                .thenReturn(101L);
        when(fixture.shopScopeService().hasUserShopScope(77L, 101L))
                .thenReturn(true);
        when(fixture.deptScopeMapper().countDeptInScope(101L, 101L))
                .thenReturn(1);
        when(fixture.deptScopeMapper().selectDeptTypeById(101L))
                .thenReturn("WAREHOUSE");
        when(fixture.lockGateway().lockHeader(91L)).thenReturn(header);
        when(fixture.lockGateway().lockAndComposeReturnReceipt(header, 101L,
                request)).thenReturn(boundary);
        when(fixture.mutationExecutor().executeReturnReceipt(mutation))
                .thenReturn(expected);

        InvTransferReceiptDiscrepancyReturnReceiptCreationVo actual;
        try (MockedStatic<SecurityUtils> security =
                mockStatic(SecurityUtils.class);
                MockedStatic<InvTransferShipmentReceiptMutationPlanner>
                        planner = mockStatic(
                                InvTransferShipmentReceiptMutationPlanner.class))
        {
            security.when(SecurityUtils::isAdmin).thenReturn(false);
            security.when(SecurityUtils::getUserId).thenReturn(77L);
            security.when(SecurityUtils::getUsername).thenReturn("receiver");
            planner.when(() -> InvTransferShipmentReceiptMutationPlanner
                    .prepareReturn(any(), any(), any(), any(), any(), any(),
                            any())).thenReturn(mutation);

            actual = fixture.service().create("return-receipt-0001", 91L,
                    request, 101L);
        }

        assertThat(actual).isSameAs(expected);
        verify(fixture.lockGateway()).lockHeader(91L);
        verify(fixture.lockGateway()).lockAndComposeReturnReceipt(header,
                101L, request);
        verify(fixture.mutationExecutor()).executeReturnReceipt(mutation);
    }

    @Test
    @DisplayName("参与者强制外层事务并保持零生产调用点")
    void shouldRequireOuterTransactionAndRemainUnwired() throws Exception
    {
        Field[] fields =
                InvTransferReceiptDiscrepancyReturnReceiptCreationService
                        .class.getDeclaredFields();
        assertThat(Arrays.stream(fields).map(Field::getType))
                .containsExactly(
                        InvTransferReceiptDiscrepancyReturnReceiptWriteGate
                                .class,
                        InvTransferShipmentReceiptLockGateway.class,
                        InvTransferShipmentReceiptMutationExecutor.class,
                        Clock.class);
        assertThat(Arrays.stream(fields).map(Field::getType)
                .map(Class::getSimpleName))
                .noneMatch(name -> name.endsWith("Mapper"));

        Method method =
                InvTransferReceiptDiscrepancyReturnReceiptCreationService
                        .class.getMethod("create", String.class, Long.class,
                                InvTransferReceiptDiscrepancyReturnReceiptCreateRequest
                                        .class,
                                Long.class);
        Transactional transaction = method.getAnnotation(
                Transactional.class);
        assertThat(transaction).isNotNull();
        assertThat(transaction.propagation())
                .isEqualTo(Propagation.MANDATORY);
        assertThat(transaction.rollbackFor()).contains(Exception.class);
        assertThat(productionReferences(
                InvTransferReceiptDiscrepancyReturnReceiptCreationService
                        .class.getSimpleName()))
                .containsExactly(Path.of("service", "impl",
                        "InvTransferReceiptDiscrepancyReturnReceiptCreationService.java"));
    }

    private static Fixture fixture(boolean enabled, boolean ready)
    {
        var gateway = mock(InvTransferShipmentReceiptLockGateway.class);
        var executor = mock(
                InvTransferShipmentReceiptMutationExecutor.class);
        var deptScopeMapper = mock(InvDeptScopeMapper.class);
        var shopScopeService = mock(ShopScopeService.class);
        var service =
                new InvTransferReceiptDiscrepancyReturnReceiptCreationService(
                        new InvTransferReceiptDiscrepancyReturnReceiptWriteGate(
                                enabled, ready),
                        gateway, executor, deptScopeMapper, shopScopeService,
                        Clock.fixed(NOW, ZoneOffset.UTC));
        return new Fixture(service, gateway, executor, deptScopeMapper,
                shopScopeService);
    }

    private static InvTransferReceiptDiscrepancyReturnReceiptCreateRequest
            request()
    {
        var allocation =
                new InvTransferReceiptDiscrepancyReturnReceiptAllocationRequest();
        allocation.setShipmentAllocationId(81L);
        allocation.setQuarantineLocationId(902L);
        allocation.setReturnedQuantity(new BigDecimal("2"));
        allocation.setShortageQuantity(BigDecimal.ZERO);

        var request =
                new InvTransferReceiptDiscrepancyReturnReceiptCreateRequest();
        request.setReturnReceiptPlanVersion(PLAN);
        request.setBasis(
                InvTransferReceiptDiscrepancyReturnReceiptPolicy.DATA_SOURCE);
        request.setArrivedTime(Date.from(NOW));
        request.setFinalizeShipment(true);
        request.setAllocations(List.of(allocation));
        return request;
    }

    private static InvTransferReceiptDiscrepancyReturnReceiptPolicy.Plan plan()
    {
        var fact = new InvTransferReceiptPlanningAllocationFact();
        fact.setAllocationId(81L);
        fact.setTrackingPolicy("lot");
        var line = new InvTransferReceiptDiscrepancyReturnReceiptPolicy.Line(
                fact, BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("2"), new BigDecimal("2"), List.of(),
                "ready", List.of());
        var location = new InvTransferReceiptLocationCandidate();
        location.setLocationId(902L);
        location.setLocationType("quarantine");
        return new InvTransferReceiptDiscrepancyReturnReceiptPolicy.Plan(
                PLAN, new InvWarehouseStockMode(), List.of(line),
                List.of(location), true, List.of());
    }

    private static InvTransferShipment shipment()
    {
        var value = new InvTransferShipment();
        value.setShipmentId(91L);
        value.setTransferId(900L);
        value.setInventoryWriteVersion(
                InvTransferShipmentWriteVersions.V2_DETAIL);
        return value;
    }

    private static InvTransferOrder order()
    {
        var value = new InvTransferOrder();
        value.setTransferId(900L);
        value.setToDeptId(101L);
        value.setTransferType(InvTransferTypes.STORE_RETURN);
        return value;
    }

    private static List<Path> productionReferences(String value)
            throws Exception
    {
        Path root = Path.of("src", "main", "java",
                "com", "erp", "inventory");
        try (var files = Files.walk(root))
        {
            return files.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> {
                        try
                        {
                            return Files.readString(path,
                                    StandardCharsets.UTF_8).contains(value);
                        }
                        catch (java.io.IOException exception)
                        {
                            throw new IllegalStateException(exception);
                        }
                    })
                    .map(root::relativize)
                    .toList();
        }
    }

    private record Fixture(
            InvTransferReceiptDiscrepancyReturnReceiptCreationService service,
            InvTransferShipmentReceiptLockGateway lockGateway,
            InvTransferShipmentReceiptMutationExecutor mutationExecutor,
            InvDeptScopeMapper deptScopeMapper,
            ShopScopeService shopScopeService)
    {
    }
}
