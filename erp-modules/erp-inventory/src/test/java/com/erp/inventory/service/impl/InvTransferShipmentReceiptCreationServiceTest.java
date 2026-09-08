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
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
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
import com.erp.inventory.constant.InvStatusConstants;
import com.erp.inventory.domain.InvTransferOrder;
import com.erp.inventory.domain.InvTransferShipment;
import com.erp.inventory.domain.dto.InvTransferShipmentReceiptAllocationRequest;
import com.erp.inventory.domain.dto.InvTransferShipmentReceiptCreateRequest;
import com.erp.inventory.domain.transfer.InvTransferReceiptLocationCandidate;
import com.erp.inventory.domain.transfer.InvTransferReceiptPlanningAllocationFact;
import com.erp.inventory.domain.transfer.InvTransferShipmentReceiptPlanComposer;
import com.erp.inventory.domain.transfer.InvTransferShipmentReceiptLockedBoundary;
import com.erp.inventory.domain.transfer.InvTransferShipmentReceiptMutationPlanner;
import com.erp.inventory.domain.transfer.InvTransferShipmentReceiptPreparedMutation;
import com.erp.inventory.domain.transfer.InvWarehouseStockMode;
import com.erp.inventory.domain.vo.InvTransferShipmentReceiptCreationVo;
import com.erp.inventory.mapper.InvDeptScopeMapper;

@DisplayName("V2调拨收货原子写事务所有者")
class InvTransferShipmentReceiptCreationServiceTest
{
    private static final String PLAN_VERSION = "a".repeat(64);

    @Test
    @DisplayName("默认关闭时立即拒绝且不进入锁边界")
    void shouldRejectWhenWriteIsDisabled()
    {
        Fixture fixture = fixture(false, false);

        assertThatThrownBy(() -> fixture.service().create("receipt-1",
                91L, new InvTransferShipmentReceiptCreateRequest(), 302L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("尚未启用");
        verifyNoInteractions(fixture.lockGateway());
    }

    @Test
    @DisplayName("写开关不能绕过持久化就绪门")
    void shouldRequirePersistenceReadiness()
    {
        Fixture fixture = fixture(true, false);

        assertThatThrownBy(() -> fixture.service().create("receipt-1",
                91L, new InvTransferShipmentReceiptCreateRequest(), 302L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("持久化边界尚未就绪");
        verifyNoInteractions(fixture.lockGateway());
    }

    @Test
    @DisplayName("双开关为真时在同一事务内规划并调用唯一执行器")
    void shouldPlanAndDelegateToOnlyMutationExecutor()
    {
        Fixture fixture = fixture(true, true);
        InvTransferShipment shipment = shipment();
        InvTransferOrder order = order();
        InvTransferShipmentReceiptLockGateway.LockedHeader header =
                new InvTransferShipmentReceiptLockGateway.LockedHeader(
                        shipment, order);
        InvTransferShipmentReceiptLockedBoundary boundary = boundary();
        when(fixture.shopScopeService().resolveRequiredShopDept(302L))
                .thenReturn(302L);
        when(fixture.shopScopeService().hasUserShopScope(77L, 302L))
                .thenReturn(true);
        when(fixture.deptScopeMapper().countDeptInScope(302L, 302L))
                .thenReturn(1);
        when(fixture.deptScopeMapper().selectDeptTypeById(302L))
                .thenReturn("STORE");
        when(fixture.lockGateway().lockHeader(91L)).thenReturn(header);
        InvTransferShipmentReceiptCreateRequest request = request();
        when(fixture.lockGateway().lockAndCompose(header, 302L, request))
                .thenReturn(boundary);
        InvTransferShipmentReceiptPreparedMutation mutation = mock(
                InvTransferShipmentReceiptPreparedMutation.class);
        InvTransferShipmentReceiptCreationVo expected = mock(
                InvTransferShipmentReceiptCreationVo.class);
        when(fixture.mutationExecutor().execute(mutation))
                .thenReturn(expected);

        InvTransferShipmentReceiptCreationVo actual;
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
                    .prepare(any(), any(), any(), any(), any(), any(),
                            any())).thenReturn(mutation);
            actual = fixture.service().create("receipt-1", 91L, request,
                    302L);
        }

        assertThat(actual).isSameAs(expected);
        verify(fixture.lockGateway()).lockHeader(91L);
        verify(fixture.lockGateway()).lockAndCompose(header, 302L, request);
        verify(fixture.mutationExecutor()).execute(mutation);
    }

    @Test
    @DisplayName("事务所有者只持有Gate、窄化锁网关与唯一执行器")
    void shouldHaveOnlyNarrowDependenciesAndRequireExistingTransaction()
            throws Exception
    {
        Field[] fields = InvTransferShipmentReceiptCreationService.class
                .getDeclaredFields();
        assertThat(Arrays.stream(fields).map(Field::getType))
                .containsExactly(InvTransferShipmentReceiptWriteGate.class,
                        InvTransferShipmentReceiptLockGateway.class,
                        InvTransferShipmentReceiptMutationExecutor.class);
        assertThat(Arrays.stream(fields).map(Field::getType)
                .map(Class::getSimpleName))
                .noneMatch(name -> name.endsWith("Mapper"));

        Method method = InvTransferShipmentReceiptCreationService.class
                .getDeclaredMethod("create", String.class, Long.class,
                        InvTransferShipmentReceiptCreateRequest.class,
                        Long.class);
        Transactional transaction = method.getAnnotation(
                Transactional.class);
        assertThat(transaction).isNotNull();
        assertThat(transaction.propagation()).isEqualTo(
                Propagation.MANDATORY);
        assertThat(transaction.rollbackFor()).contains(Exception.class);
    }

    private static Fixture fixture(boolean writeEnabled,
            boolean persistenceReady)
    {
        InvTransferShipmentReceiptLockGateway lockGateway = mock(
                InvTransferShipmentReceiptLockGateway.class);
        InvDeptScopeMapper deptScopeMapper = mock(InvDeptScopeMapper.class);
        ShopScopeService shopScopeService = mock(ShopScopeService.class);
        InvTransferShipmentReceiptMutationExecutor mutationExecutor = mock(
                InvTransferShipmentReceiptMutationExecutor.class);
        InvTransferShipmentReceiptCreationService service =
                new InvTransferShipmentReceiptCreationService(
                        new InvTransferShipmentReceiptWriteGate(writeEnabled,
                                persistenceReady),
                        lockGateway, mutationExecutor, deptScopeMapper,
                        shopScopeService);
        return new Fixture(service, lockGateway, deptScopeMapper,
                shopScopeService, mutationExecutor);
    }

    private static InvTransferShipmentReceiptCreateRequest request()
    {
        InvTransferShipmentReceiptAllocationRequest allocation =
                new InvTransferShipmentReceiptAllocationRequest();
        allocation.setShipmentAllocationId(81L);
        allocation.setAcceptedLocationId(901L);
        allocation.setAcceptedQuantity(new BigDecimal("2"));
        allocation.setDamagedQuantity(BigDecimal.ZERO);
        allocation.setShortageQuantity(BigDecimal.ZERO);
        InvTransferShipmentReceiptCreateRequest value =
                new InvTransferShipmentReceiptCreateRequest();
        value.setReceiptPlanVersion(PLAN_VERSION);
        value.setBasis("server-recommendation");
        value.setArrivedTime(Date.from(Instant.parse(
                "2026-08-02T10:00:00Z")));
        value.setFinalizeShipment(true);
        value.setAllocations(List.of(allocation));
        return value;
    }

    private static InvTransferShipment shipment()
    {
        InvTransferShipment value = new InvTransferShipment();
        value.setShipmentId(91L);
        value.setTransferId(900L);
        value.setInventoryWriteVersion(
                InvTransferShipmentWriteVersions.V2_DETAIL);
        return value;
    }

    private static InvTransferOrder order()
    {
        InvTransferOrder value = new InvTransferOrder();
        value.setTransferId(900L);
        value.setToDeptId(302L);
        value.setTransferType(InvTransferTypes.WAREHOUSE);
        return value;
    }

    private static InvTransferShipmentReceiptPlanComposer.Composition
            composition()
    {
        InvTransferReceiptPlanningAllocationFact fact =
                new InvTransferReceiptPlanningAllocationFact();
        fact.setAllocationId(81L);
        fact.setTrackingPolicy("lot");
        InvTransferShipmentReceiptPlanComposer.Line line =
                new InvTransferShipmentReceiptPlanComposer.Line(fact,
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                        new BigDecimal("2"), new BigDecimal("2"), List.of(),
                        "ready", List.of());
        InvWarehouseStockMode mode = new InvWarehouseStockMode();
        mode.setWarehouseId(302L);
        return new InvTransferShipmentReceiptPlanComposer.Composition(
                PLAN_VERSION, mode, List.of(line),
                List.of(location(901L, "storage")),
                List.of(location(902L, "quarantine")), true, List.of());
    }

    private static InvTransferShipmentReceiptLockedBoundary boundary()
    {
        InvTransferShipmentReceiptLockedBoundary.Header header =
                new InvTransferShipmentReceiptLockedBoundary.Header(91L,
                        900L, 301L, 301L, 302L, "SHP-91",
                        "b".repeat(64), 11L, "source-reconcile",
                        "target-reconcile", InvStatusConstants.PENDING_RECEIVE,
                        InvStatusConstants.DELIVERED, 4L);
        return new InvTransferShipmentReceiptLockedBoundary(header,
                composition(), Map.of(), Map.of(), Map.of(), Map.of(),
                Map.of(), Map.of(), Map.of(), Map.of());
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

    private record Fixture(
            InvTransferShipmentReceiptCreationService service,
            InvTransferShipmentReceiptLockGateway lockGateway,
            InvDeptScopeMapper deptScopeMapper,
            ShopScopeService shopScopeService,
            InvTransferShipmentReceiptMutationExecutor mutationExecutor)
    {
    }
}
