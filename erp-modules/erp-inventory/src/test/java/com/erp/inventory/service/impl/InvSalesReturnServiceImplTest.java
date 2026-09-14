package com.erp.inventory.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.eq;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import com.erp.common.core.constant.SecurityConstants;
import com.erp.common.core.context.SecurityContextHolder;
import com.erp.common.core.exception.ServiceException;
import com.erp.inventory.domain.InvSalesDetail;
import com.erp.inventory.domain.InvSalesOrder;
import com.erp.inventory.domain.InvSalesReturn;
import com.erp.inventory.domain.InvSalesReturnDetail;
import com.erp.inventory.domain.InvStock;
import com.erp.inventory.domain.InvStockLog;
import com.erp.inventory.mapper.InvDeptScopeMapper;
import com.erp.inventory.mapper.InvNumberSequenceMapper;
import com.erp.inventory.mapper.InvOutboundRecordMapper;
import com.erp.inventory.mapper.InvSalesDetailMapper;
import com.erp.inventory.mapper.InvSalesOrderMapper;
import com.erp.inventory.mapper.InvSalesReturnDetailMapper;
import com.erp.inventory.mapper.InvSalesReturnMapper;
import com.erp.inventory.mapper.InvStockLogMapper;
import com.erp.inventory.mapper.InvStockMapper;
import com.erp.system.api.domain.SysUser;
import com.erp.system.api.model.LoginUser;

@DisplayName("销售退货服务")
class InvSalesReturnServiceImplTest
{
    @AfterEach
    void tearDown()
    {
        SecurityContextHolder.remove();
    }

    @Test
    @DisplayName("销售退货当前单据重复商品行需合并校验")
    void shouldAggregateDuplicateProductRowsWhenSubmittingSalesReturn() throws Exception
    {
        loginAsAdmin();
        InvSalesReturnServiceImpl service = salesReturnServiceWithQuantityFixtures();

        assertThatThrownBy(() -> service.submitReturn(salesReturn(), List.of(
                salesReturnDetail(101L, "3.00"), salesReturnDetail(101L, "3.00")), 10L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("超过原销售明细已出库数量");
    }

    @Test
    @DisplayName("销售退货数量必须大于0")
    void shouldRejectNonPositiveSalesReturnQuantity() throws Exception
    {
        loginAsAdmin();
        InvSalesReturnServiceImpl service = salesReturnServiceWithQuantityFixtures();

        assertThatThrownBy(() -> service.submitReturn(salesReturn(),
                List.of(salesReturnDetail(101L, "0.00")), 10L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("退货数量必须大于0");
    }

    @Test
    @DisplayName("销售退货明细由原销售单重算商品信息和金额")
    void shouldNormalizeSalesReturnDetailsFromOriginalSalesOrder() throws Exception
    {
        loginAsAdmin();
        SalesReturnFixture fixture = salesReturnServiceWithOriginalSalesDetail(originalSalesDetail());
        InvSalesReturnDetail input = salesReturnDetail(101L, "2.00");
        input.setProductName("前端伪造商品");
        input.setSku("BAD-SKU");
        input.setSpec("错误规格");
        input.setUnit("错误单位");
        input.setUnitPrice(new BigDecimal("1.00"));
        input.setAmount(new BigDecimal("1.00"));

        fixture.service.submitReturn(salesReturn(), List.of(input), 10L);

        InvSalesReturnDetail savedDetail = fixture.detailsRef.get().get(0);
        assertThat(savedDetail.getProductName()).isEqualTo("原销售商品");
        assertThat(savedDetail.getSku()).isEqualTo("SKU-SALE");
        assertThat(savedDetail.getSpec()).isEqualTo("250g");
        assertThat(savedDetail.getUnit()).isEqualTo("罐");
        assertThat(savedDetail.getUnitPrice()).isEqualByComparingTo("12.50");
        assertThat(savedDetail.getAmount()).isEqualByComparingTo("25.00");
        assertThat(fixture.returnRef.get().getTotalAmount()).isEqualByComparingTo("25.00");
        assertThat(fixture.returnRef.get().getSalesOrderNo()).isEqualTo("SO-SOURCE");
        assertThat(fixture.returnRef.get().getCustomerName()).isEqualTo("来源客户");
        assertThat(fixture.returnRef.get().getReturnDate()).isNotNull();
    }

    @Test
    @DisplayName("销售退货重复商品按原销售明细行重算金额")
    void shouldNormalizeDuplicateSalesReturnDetailsBySourceDetailId() throws Exception
    {
        loginAsAdmin();
        InvSalesDetail firstLine = originalSalesDetail(601L, "第一行商品", "12.50", "5.00");
        InvSalesDetail secondLine = originalSalesDetail(602L, "第二行商品", "21.00", "4.00");
        SalesReturnFixture fixture = salesReturnServiceWithOriginalSalesDetail(firstLine, secondLine);
        InvSalesReturnDetail input = salesReturnDetail(101L, "2.00");
        input.setSalesDetailId(602L);

        fixture.service.submitReturn(salesReturn(), List.of(input), 10L);

        InvSalesReturnDetail savedDetail = fixture.detailsRef.get().get(0);
        assertThat(savedDetail.getSalesDetailId()).isEqualTo(602L);
        assertThat(savedDetail.getProductName()).isEqualTo("第二行商品");
        assertThat(savedDetail.getUnitPrice()).isEqualByComparingTo("21.00");
        assertThat(savedDetail.getAmount()).isEqualByComparingTo("42.00");
        assertThat(fixture.returnRef.get().getTotalAmount()).isEqualByComparingTo("42.00");
    }

    @Test
    @DisplayName("销售退货重复商品缺少原明细行时拒绝保存")
    void shouldRejectAmbiguousSalesReturnDetailWithoutSourceDetailId() throws Exception
    {
        loginAsAdmin();
        InvSalesDetail firstLine = originalSalesDetail(601L, "第一行商品", "12.50", "5.00");
        InvSalesDetail secondLine = originalSalesDetail(602L, "第二行商品", "21.00", "4.00");
        SalesReturnFixture fixture = salesReturnServiceWithOriginalSalesDetail(firstLine, secondLine);

        assertThatThrownBy(() -> fixture.service.submitReturn(salesReturn(),
                List.of(salesReturnDetail(101L, "2.00")), 10L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("请选择原销售明细");
    }

    @Test
    @DisplayName("销售退货必须在门店上下文创建")
    void shouldRejectWarehouseContextWhenSavingSalesReturn() throws Exception
    {
        loginAsAdmin();
        FakeDeptScopeMapper deptScopeMapper = new FakeDeptScopeMapper();
        deptScopeMapper.deptTypes.put(10L, "WAREHOUSE");
        SalesReturnFixture fixture = salesReturnServiceWithOriginalSalesDetail(deptScopeMapper, originalSalesDetail());

        assertThatThrownBy(() -> fixture.service.saveDraft(salesReturn(),
                List.of(salesReturnDetail(101L, "1.00")), 10L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("请选择门店");
    }

    @Test
    @DisplayName("销售退货优先按原销售出库成本冲回，不使用当前库存均价")
    void shouldUseOriginalOutboundCostWhenConfirmingSalesReturn() throws Exception
    {
        assertOriginalCostForMaterial("product");
    }

    @Test
    void shouldReturnGiftStockAndCostWithoutUsingProductIdentity() throws Exception
    {
        assertOriginalCostForMaterial("gift");
    }

    private void assertOriginalCostForMaterial(String type) throws Exception
    {
        loginAsAdmin();
        Long productId = "product".equals(type) ? 101L : null;
        InvSalesReturnServiceImpl service = new InvSalesReturnServiceImpl();
        InvSalesReturnMapper returnMapper = mock(InvSalesReturnMapper.class);
        when(returnMapper.updateInvSalesReturn(any())).thenReturn(1);
        InvSalesReturnDetailMapper detailMapper = mock(InvSalesReturnDetailMapper.class);
        InvSalesDetailMapper salesDetailMapper = mock(InvSalesDetailMapper.class);
        FakeStockMapper stockMapper = new FakeStockMapper();
        FakeStockLogMapper stockLogMapper = new FakeStockLogMapper();

        InvSalesReturn salesReturn = salesReturn();
        salesReturn.setReturnId(1L);
        salesReturn.setShopDeptId(10L);
        salesReturn.setStatus("submitted");
        salesReturn.setReturnNo("SR001");
        InvSalesReturnDetail detail = salesReturnDetail(productId, "2.00");
        detail.setDetailId(9001L);
        detail.setItemType(type);
        detail.setItemId(101L);
        detail.setReturnId(1L);
        detail.setReturnedQuantity(BigDecimal.ZERO);
        detail.setUnitPrice(new BigDecimal("20.00"));
        InvSalesDetail original = originalSalesDetail(601L, "原销售商品", "20.00", "2.00");
        original.setItemType(type);
        original.setItemId(101L);
        original.setProductId(productId);
        InvStock stock = new InvStock();
        stock.setStockId(7001L);
        stock.setProductId(productId);
        stock.setItemType(type);
        stock.setItemId(101L);
        stock.setShopDeptId(10L);
        stock.setWarehouseId(10L);
        stock.setCurrentQuantity(new BigDecimal("3.00"));
        stock.setAvailableQuantity(new BigDecimal("3.00"));
        stock.setCostPrice(new BigDecimal("8.00"));
        stock.setTotalCost(new BigDecimal("24.00"));
        stockMapper.stock = stock;
        InvStockLog outboundLog = new InvStockLog();
        outboundLog.setBusinessType("sales");
        outboundLog.setBusinessId(10L);
        outboundLog.setProductId(productId);
        outboundLog.setItemType(type);
        outboundLog.setItemId(101L);
        outboundLog.setMovementType("sales_out");
        outboundLog.setChangeQuantity(new BigDecimal("-2.00"));
        outboundLog.setCostPrice(new BigDecimal("6.00"));
        stockLogMapper.queryResult = List.of(outboundLog);

        when(returnMapper.selectInvSalesReturnById(1L)).thenReturn(salesReturn);
        when(returnMapper.selectInvSalesReturnByIdForUpdate(1L)).thenReturn(salesReturn);
        when(salesDetailMapper.selectInvSalesDetailByOrderId(10L)).thenReturn(List.of(original));
        when(detailMapper.selectInvSalesReturnDetailByReturnId(1L)).thenReturn(List.of(detail));
        when(detailMapper.selectInvSalesReturnDetailByReturnIdForUpdate(1L)).thenReturn(List.of(detail));
        when(detailMapper.updateInvSalesReturnDetail(any())).thenReturn(1);
        when(detailMapper.sumHistoricalReturnQuantity(10L, 101L, 1L)).thenReturn(BigDecimal.ZERO);
        when(detailMapper.sumHistoricalReturnQuantityBySalesDetailId(10L, 601L, 1L)).thenReturn(BigDecimal.ZERO);

        ReflectionTestUtils.setField(service, "salesReturnMapper", returnMapper);
        ReflectionTestUtils.setField(service, "salesReturnDetailMapper", detailMapper);
        ReflectionTestUtils.setField(service, "salesDetailMapper", salesDetailMapper);
        ReflectionTestUtils.setField(service, "salesOrderMapper", sourceSalesOrderMapper());
        ReflectionTestUtils.setField(service, "stockMapper", stockMapper);
        ReflectionTestUtils.setField(service, "stockLogMapper", stockLogMapper);
        ReflectionTestUtils.setField(service, "outboundRecordMapper", mock(InvOutboundRecordMapper.class));
        ReflectionTestUtils.setField(service, "deptScopeMapper", new FakeDeptScopeMapper());

        service.confirmReturn(1L, 10L);

        assertThat(stockMapper.addedCost).isEqualByComparingTo("12.00");
        assertThat(stockLogMapper.inserted.getCostPrice()).isEqualByComparingTo("6.00");
        assertThat(stockLogMapper.inserted.getCostAmount()).isEqualByComparingTo("12.00");
        assertThat(detail.getReturnedCostAmount()).isEqualByComparingTo("12.00");
        assertThat(detail.getSalesDetailId()).isEqualTo(601L);
        assertThat(stockLogMapper.lastQuery.getBusinessId()).isEqualTo(10L);
        assertThat(stockLogMapper.lastQuery.getProductId()).isEqualTo(productId);
        assertThat(stockLogMapper.lastQuery.getItemType()).isEqualTo(type);
        assertThat(stockLogMapper.lastQuery.getItemId()).isEqualTo(101L);
        assertThat(stockLogMapper.inserted.getItemType()).isEqualTo(type);
        assertThat(stockLogMapper.inserted.getProductId()).isEqualTo(productId);
        assertThat(stock.getCurrentQuantity()).isEqualByComparingTo("5");
        assertThat(stockLogMapper.lastQuery.getMovementType()).isEqualTo("sales_out");
    }

    @Test
    void shouldKeepProductAndGiftWithSameNumericIdOnSeparateSourceLines() throws Exception
    {
        loginAsAdmin();
        InvSalesDetail product = originalSalesDetail(601L, "商品", "12.50", "5");
        InvSalesDetail gift = originalSalesDetail(602L, "礼盒", "80.00", "2");
        gift.setProductId(null);
        gift.setItemType("gift");
        gift.setItemId(101L);
        gift.setItemName("礼盒");
        SalesReturnFixture fixture = salesReturnServiceWithOriginalSalesDetail(product, gift);
        InvSalesReturnDetail giftInput = new InvSalesReturnDetail();
        giftInput.setSalesDetailId(602L);
        giftInput.setItemType("gift");
        giftInput.setItemId(101L);
        giftInput.setQuantity(new BigDecimal("2"));
        fixture.service.submitReturn(salesReturn(), List.of(salesReturnDetail(101L, "3"), giftInput), 10L);
        assertThat(fixture.detailsRef.get()).hasSize(2);
        assertThat(giftInput.getProductId()).isNull();
        assertThat(giftInput.getItemType()).isEqualTo("gift");
        assertThat(giftInput.getItemId()).isEqualTo(101L);
        assertThat(giftInput.getAmount()).isEqualByComparingTo("160");
        assertThat(fixture.returnRef.get().getTotalAmount()).isEqualByComparingTo("197.50");
    }

    @Test
    void shouldRejectReturnBeyondHistoricalSourceAllowance() throws Exception
    {
        loginAsAdmin();
        SalesReturnFixture fixture = salesReturnServiceWithOriginalSalesDetail(originalSalesDetail());
        InvSalesReturnDetailMapper mapper = (InvSalesReturnDetailMapper) ReflectionTestUtils.getField(fixture.service, "salesReturnDetailMapper");
        when(mapper.sumHistoricalReturnQuantityBySalesDetailId(10L, 601L, 1L)).thenReturn(new BigDecimal("4"));
        assertThatThrownBy(() -> fixture.service.submitReturn(salesReturn(), List.of(salesReturnDetail(101L, "2")), 10L))
                .isInstanceOf(ServiceException.class).hasMessageContaining("超过原销售明细已出库数量");
    }

    @Test
    void shouldRejectGiftSpoofingProductSourceIdentity() throws Exception
    {
        loginAsAdmin();
        SalesReturnFixture fixture = salesReturnServiceWithOriginalSalesDetail(originalSalesDetail());
        InvSalesReturnDetail input = salesReturnDetail(101L, "1");
        input.setSalesDetailId(601L);
        input.setItemType("gift");
        input.setItemId(101L);
        assertThatThrownBy(() -> fixture.service.saveDraft(salesReturn(), List.of(input), 10L))
                .isInstanceOf(ServiceException.class).hasMessageContaining("不一致");
    }

    @Test
    void shouldRejectCancelAfterConfirmationWonTheRowLock()
    {
        loginAsAdmin();
        InvSalesReturnServiceImpl service = new InvSalesReturnServiceImpl();
        InvSalesReturnMapper mapper = mock(InvSalesReturnMapper.class);
        InvSalesReturn locked = salesReturn();
        locked.setShopDeptId(10L);
        locked.setStatus("returned");
        when(mapper.selectInvSalesReturnById(1L)).thenReturn(locked);
        when(mapper.selectInvSalesReturnByIdForUpdate(1L)).thenReturn(locked);
        ReflectionTestUtils.setField(service, "salesOrderMapper", sourceSalesOrderMapper());
        ReflectionTestUtils.setField(service, "salesReturnMapper", mapper);
        ReflectionTestUtils.setField(service, "deptScopeMapper", new FakeDeptScopeMapper());
        assertThatThrownBy(() -> service.cancelReturn(1L, 10L)).isInstanceOf(ServiceException.class);
        verify(mapper, never()).updateInvSalesReturn(any());
        verify(mapper).selectInvSalesReturnByIdForUpdate(1L);
    }

    @Test
    void shouldRejectStateCompareAndSetFailureInsteadOfReportingCancelled()
    {
        loginAsAdmin();
        InvSalesReturnServiceImpl service = new InvSalesReturnServiceImpl();
        InvSalesReturnMapper mapper = mock(InvSalesReturnMapper.class);
        InvSalesReturn locked = salesReturn();
        locked.setShopDeptId(10L);
        locked.setStatus("submitted");
        when(mapper.selectInvSalesReturnById(1L)).thenReturn(locked);
        when(mapper.selectInvSalesReturnByIdForUpdate(1L)).thenReturn(locked);
        ReflectionTestUtils.setField(service, "salesOrderMapper", sourceSalesOrderMapper());
        ReflectionTestUtils.setField(service, "salesReturnMapper", mapper);
        ReflectionTestUtils.setField(service, "deptScopeMapper", new FakeDeptScopeMapper());
        assertThatThrownBy(() -> service.cancelReturn(1L, 10L))
                .isInstanceOf(ServiceException.class).hasMessageContaining("状态已变化");
        org.mockito.ArgumentCaptor<InvSalesReturn> update = org.mockito.ArgumentCaptor.forClass(InvSalesReturn.class);
        verify(mapper).updateInvSalesReturn(update.capture());
        assertThat(update.getValue().getParams().get("expectedStatus")).isEqualTo("submitted");
    }

    @Test
    void shouldExcludeCurrentReturnWhenShowingItsEditableAllowance() throws Exception
    {
        loginAsAdmin();
        SalesReturnFixture fixture = salesReturnServiceWithOriginalSalesDetail(originalSalesDetail());
        fixture.service.saveDraft(salesReturn(), List.of(salesReturnDetail(101L, "2")), 10L);
        InvSalesReturnDetailMapper mapper = (InvSalesReturnDetailMapper) ReflectionTestUtils.getField(fixture.service, "salesReturnDetailMapper");
        when(mapper.sumHistoricalReturnQuantityBySalesDetailId(10L, 601L, 1L)).thenReturn(new BigDecimal("3"));
        InvSalesReturn result = fixture.service.getReturnDetail(1L, 10L);
        assertThat(result.getDetails().get(0).getReturnableQuantity()).isEqualByComparingTo("2");
        verify(mapper).sumHistoricalReturnQuantityBySalesDetailId(10L, 601L, 1L);
    }

    private static InvSalesReturnServiceImpl salesReturnServiceWithQuantityFixtures() throws Exception
    {
        InvSalesReturnServiceImpl service = new InvSalesReturnServiceImpl();
        InvSalesReturnMapper returnMapper = mock(InvSalesReturnMapper.class);
        when(returnMapper.updateInvSalesReturn(any())).thenReturn(1);
        InvSalesReturnDetailMapper detailMapper = mock(InvSalesReturnDetailMapper.class);
        InvSalesDetailMapper salesDetailMapper = mock(InvSalesDetailMapper.class);
        InvNumberSequenceMapper numberSequenceMapper = mock(InvNumberSequenceMapper.class);
        AtomicReference<InvSalesReturn> storedReturn = new AtomicReference<>();
        AtomicReference<List<InvSalesReturnDetail>> detailsRef = new AtomicReference<>(new ArrayList<>());

        doAnswer(invocation -> {
            InvSalesReturn salesReturn = invocation.getArgument(0);
            salesReturn.setReturnId(1L);
            storedReturn.set(salesReturn);
            return 1;
        }).when(returnMapper).insertInvSalesReturn(any(InvSalesReturn.class));
        doAnswer(invocation -> {
            List<InvSalesReturnDetail> details = new ArrayList<>(invocation.getArgument(0));
            detailsRef.set(details);
            return details.size();
        }).when(detailMapper).batchInsertInvSalesReturnDetail(any(List.class));
        when(returnMapper.selectInvSalesReturnById(1L)).thenAnswer(invocation -> storedReturn.get());
        when(detailMapper.selectInvSalesReturnDetailByReturnId(1L)).thenAnswer(invocation -> detailsRef.get());
        when(numberSequenceMapper.selectLastInsertId()).thenReturn(1L);
        InvSalesDetail salesDetail = new InvSalesDetail();
        salesDetail.setDetailId(601L);
        salesDetail.setProductId(101L);
        salesDetail.setProductName("测试商品");
        salesDetail.setDeliveredQuantity(new BigDecimal("5.00"));
        when(salesDetailMapper.selectInvSalesDetailByOrderId(10L)).thenReturn(List.of(salesDetail));
        when(detailMapper.sumHistoricalReturnQuantity(10L, 101L, 1L)).thenReturn(BigDecimal.ZERO);

        ReflectionTestUtils.setField(service, "salesReturnMapper", returnMapper);
        ReflectionTestUtils.setField(service, "salesReturnDetailMapper", detailMapper);
        ReflectionTestUtils.setField(service, "salesDetailMapper", salesDetailMapper);
        ReflectionTestUtils.setField(service, "salesOrderMapper", sourceSalesOrderMapper());
        ReflectionTestUtils.setField(service, "numberSequenceMapper", numberSequenceMapper);
        ReflectionTestUtils.setField(service, "deptScopeMapper", new FakeDeptScopeMapper());
        return service;
    }

    private static SalesReturnFixture salesReturnServiceWithOriginalSalesDetail(InvSalesDetail... salesDetails) throws Exception
    {
        return salesReturnServiceWithOriginalSalesDetail(new FakeDeptScopeMapper(), salesDetails);
    }

    private static SalesReturnFixture salesReturnServiceWithOriginalSalesDetail(InvDeptScopeMapper deptScopeMapper,
            InvSalesDetail... salesDetails) throws Exception
    {
        InvSalesReturnServiceImpl service = new InvSalesReturnServiceImpl();
        InvSalesReturnMapper returnMapper = mock(InvSalesReturnMapper.class);
        when(returnMapper.updateInvSalesReturn(any())).thenReturn(1);
        InvSalesReturnDetailMapper detailMapper = mock(InvSalesReturnDetailMapper.class);
        InvSalesDetailMapper salesDetailMapper = mock(InvSalesDetailMapper.class);
        InvNumberSequenceMapper numberSequenceMapper = mock(InvNumberSequenceMapper.class);
        AtomicReference<InvSalesReturn> storedReturn = new AtomicReference<>();
        AtomicReference<List<InvSalesReturnDetail>> detailsRef = new AtomicReference<>(new ArrayList<>());

        doAnswer(invocation -> {
            InvSalesReturn salesReturn = invocation.getArgument(0);
            salesReturn.setReturnId(1L);
            storedReturn.set(salesReturn);
            return 1;
        }).when(returnMapper).insertInvSalesReturn(any(InvSalesReturn.class));
        doAnswer(invocation -> {
            List<InvSalesReturnDetail> details = new ArrayList<>(invocation.getArgument(0));
            detailsRef.set(details);
            return details.size();
        }).when(detailMapper).batchInsertInvSalesReturnDetail(any(List.class));
        when(returnMapper.selectInvSalesReturnById(1L)).thenAnswer(invocation -> storedReturn.get());
        when(detailMapper.selectInvSalesReturnDetailByReturnId(1L)).thenAnswer(invocation -> detailsRef.get());
        when(numberSequenceMapper.selectLastInsertId()).thenReturn(1L);
        when(salesDetailMapper.selectInvSalesDetailByOrderId(10L)).thenReturn(List.of(salesDetails));
        when(detailMapper.sumHistoricalReturnQuantity(10L, 101L, 1L)).thenReturn(BigDecimal.ZERO);

        ReflectionTestUtils.setField(service, "salesReturnMapper", returnMapper);
        ReflectionTestUtils.setField(service, "salesReturnDetailMapper", detailMapper);
        ReflectionTestUtils.setField(service, "salesDetailMapper", salesDetailMapper);
        ReflectionTestUtils.setField(service, "salesOrderMapper", sourceSalesOrderMapper());
        ReflectionTestUtils.setField(service, "numberSequenceMapper", numberSequenceMapper);
        ReflectionTestUtils.setField(service, "deptScopeMapper", deptScopeMapper);
        return new SalesReturnFixture(service, storedReturn, detailsRef);
    }

    private static InvSalesDetail originalSalesDetail()
    {
        return originalSalesDetail(601L, "原销售商品", "12.50", "5.00");
    }

    private static InvSalesDetail originalSalesDetail(Long detailId, String productName, String unitPrice,
            String deliveredQuantity)
    {
        InvSalesDetail salesDetail = new InvSalesDetail();
        salesDetail.setDetailId(detailId);
        salesDetail.setProductId(101L);
        salesDetail.setProductName(productName);
        salesDetail.setSku("SKU-SALE");
        salesDetail.setSpec("250g");
        salesDetail.setUnit("罐");
        salesDetail.setUnitPrice(new BigDecimal(unitPrice));
        salesDetail.setDeliveredQuantity(new BigDecimal(deliveredQuantity));
        return salesDetail;
    }

    private static void loginAsAdmin()
    {
        SecurityContextHolder.setUserId("1");
        SecurityContextHolder.setUserName("admin");
        LoginUser loginUser = new LoginUser();
        SysUser sysUser = new SysUser();
        sysUser.setDeptId(10L);
        loginUser.setSysUser(sysUser);
        SecurityContextHolder.set(SecurityConstants.LOGIN_USER, loginUser);
    }

    private static InvSalesReturn salesReturn()
    {
        InvSalesReturn salesReturn = new InvSalesReturn();
        salesReturn.setSalesOrderId(10L);
        salesReturn.setReturnTitle("销售退货");
        salesReturn.setCustomerName("客户A");
        return salesReturn;
    }

    private static InvSalesOrderMapper sourceSalesOrderMapper()
    {
        InvSalesOrderMapper mapper = mock(InvSalesOrderMapper.class);
        InvSalesOrder source = new InvSalesOrder();
        source.setOrderId(10L);
        source.setOrderNo("SO-SOURCE");
        source.setCustomerName("来源客户");
        source.setShopDeptId(10L);
        when(mapper.selectInvSalesOrderById(10L)).thenReturn(source);
        when(mapper.selectInvSalesOrderByIdForUpdate(10L)).thenReturn(source);
        return mapper;
    }

    private static InvSalesReturnDetail salesReturnDetail(Long productId, String quantity)
    {
        InvSalesReturnDetail detail = new InvSalesReturnDetail();
        detail.setProductId(productId);
        detail.setProductName("测试商品");
        detail.setQuantity(new BigDecimal(quantity));
        detail.setUnitPrice(BigDecimal.ONE);
        return detail;
    }

    private record SalesReturnFixture(
            InvSalesReturnServiceImpl service,
            AtomicReference<InvSalesReturn> returnRef,
            AtomicReference<List<InvSalesReturnDetail>> detailsRef)
    {
    }

    private static class FakeDeptScopeMapper implements InvDeptScopeMapper
    {
        @Override public List<Long> selectUserAuthorizedInventoryDeptIds(Long userId) { return java.util.Collections.emptyList(); }
        @Override public List<Long> selectAllActiveInventoryDeptIds() { return java.util.Collections.emptyList(); }
        private final java.util.Map<Long, String> deptTypes = new java.util.HashMap<>();

        @Override
        public List<Long> selectSubDeptIds(Long deptId)
        {
            return Collections.singletonList(deptId);
        }

        @Override
        public List<Long> selectRelatedDeptIds(Long deptId)
        {
            return Collections.singletonList(deptId);
        }

        @Override
        public List<Long> selectActiveRelatedDeptIdsForReplenishment(
                Long deptId)
        {
            return selectRelatedDeptIds(deptId);
        }

        @Override
        public List<Long> selectAncestorDeptIds(Long deptId)
        {
            return Collections.emptyList();
        }

        @Override
        public Long selectRawBusinessRootDeptId(Long deptId)
        {
            return deptId;
        }

        @Override
        public List<Long> selectUserStoreScopeDeptIds(Long userId)
        {
            return Collections.emptyList();
        }

        @Override
        public List<Long> selectAllStoreDeptIds()
        {
            return Collections.emptyList();
        }

        @Override
        public int countDeptInScope(Long scopeDeptId, Long targetDeptId)
        {
            return 1;
        }

        @Override
        public int countUserShopScope(Long userId, Long deptId)
        {
            return 1;
        }

        @Override
        public String selectDeptNameById(Long deptId)
        {
            return "测试组织";
        }

        @Override
        public String selectDeptTypeById(Long deptId)
        {
            return deptTypes.getOrDefault(deptId, "STORE");
        }
    }

    private static class FakeStockMapper implements InvStockMapper
    {
        private InvStock stock;
        private BigDecimal addedCost;

        @Override
        public List<InvStock> selectInvStockList(InvStock stock)
        {
            return Collections.emptyList();
        }

        @Override
        public com.erp.inventory.domain.vo.InvStockSummary selectInvStockSummary(InvStock stock)
        {
            return null;
        }

        @Override
        public InvStock selectInvStockById(Long stockId)
        {
            return stock;
        }

        @Override
        public InvStock selectInvStockByIdForUpdate(Long stockId)
        {
            return selectInvStockById(stockId);
        }

        @Override
        public InvStock selectInvStockByProductAndShop(Long productId, Long shopDeptId)
        {
            return stock;
        }

        @Override
        public InvStock selectInvStockByProductAndShopForUpdate(Long productId, Long shopDeptId)
        {
            return stock;
        }

        @Override
        public InvStock selectInvStockByProductShopWarehouse(Long productId, Long shopDeptId, Long warehouseId)
        {
            return stock;
        }

        @Override
        public InvStock selectInvStockByProductShopWarehouseForUpdate(Long productId, Long shopDeptId, Long warehouseId)
        {
            return stock;
        }

        @Override
        public InvStock selectInvStockByItemShopWarehouse(String itemType, Long itemId, Long shopDeptId, Long warehouseId)
        {
            return stock;
        }

        @Override
        public InvStock selectInvStockByItemShopWarehouseForUpdate(String itemType, Long itemId, Long shopDeptId, Long warehouseId)
        {
            return stock;
        }

        @Override
        public int insertInvStock(InvStock stock)
        {
            stock.setVersion(stock.getVersion() == null ? 0L : stock.getVersion());
            this.stock = stock;
            return 1;
        }

        @Override
        public int updateInvStock(InvStock stock)
        {
            stock.setVersion(stock.getVersion() == null ? 1L : stock.getVersion() + 1);
            this.stock = stock;
            return 1;
        }

        @Override
        public int addInvStock(Long stockId, Long version, BigDecimal quantity, String updateBy)
        {
            stock.setVersion((stock.getVersion() == null ? 0L : stock.getVersion()) + 1);
            return 1;
        }

        @Override
        public int addInvStockWithCost(Long stockId, Long version, BigDecimal quantity, BigDecimal incomingCost, String updateBy)
        {
            this.addedCost = incomingCost;
            stock.setCurrentQuantity(stock.getCurrentQuantity().add(quantity));
            stock.setAvailableQuantity(stock.getAvailableQuantity().add(quantity));
            stock.setTotalCost(stock.getTotalCost().add(incomingCost));
            stock.setVersion((stock.getVersion() == null ? 0L : stock.getVersion()) + 1);
            return 1;
        }

        @Override
        public int deductInvStock(Long stockId, Long version, BigDecimal quantity, String updateBy)
        {
            stock.setVersion((stock.getVersion() == null ? 0L : stock.getVersion()) + 1);
            return 1;
        }

        @Override
        public int deductInvStockWithCost(Long stockId, Long version, BigDecimal quantity, BigDecimal deductCost, String updateBy)
        {
            stock.setVersion((stock.getVersion() == null ? 0L : stock.getVersion()) + 1);
            return 1;
        }
    }

    private static class FakeStockLogMapper implements InvStockLogMapper
    {
        private InvStockLog inserted;
        private InvStockLog lastQuery;
        private List<InvStockLog> queryResult = Collections.emptyList();

        @Override
        public List<InvStockLog> selectInvStockLogList(InvStockLog stockLog)
        {
            lastQuery = stockLog;
            return queryResult;
        }

        @Override
        public int insertInvStockLog(InvStockLog stockLog)
        {
            inserted = stockLog;
            return 1;
        }
    }
}
