package com.erp.inventory.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import com.erp.common.core.constant.SecurityConstants;
import com.erp.common.core.context.SecurityContextHolder;
import com.erp.common.core.exception.ServiceException;
import com.erp.inventory.constant.InvStatusConstants;
import com.erp.inventory.domain.InvCustomer;
import com.erp.inventory.domain.InvProduct;
import com.erp.inventory.domain.InvSalesDetail;
import com.erp.inventory.domain.InvSalesOrder;
import com.erp.inventory.mapper.InvDeptScopeMapper;
import com.erp.inventory.mapper.InvCustomerMapper;
import com.erp.inventory.mapper.InvNumberSequenceMapper;
import com.erp.inventory.mapper.InvProductMapper;
import com.erp.inventory.mapper.InvSalesDetailMapper;
import com.erp.inventory.mapper.InvSalesOrderMapper;
import com.erp.system.api.domain.SysUser;
import com.erp.system.api.model.LoginUser;

@DisplayName("销售服务")
class InvSalesServiceImplTest
{
    @AfterEach
    void tearDown()
    {
        SecurityContextHolder.remove();
    }

    @Test
    @DisplayName("保存销售草稿时必须包含明细")
    void shouldRejectSalesDraftWithoutDetails()
    {
        loginAsAdmin();
        InvSalesServiceImpl service = salesService(new FakeSalesOrderMapper(), new FakeSalesDetailMapper(),
                new FakeProductMapper());

        assertThatThrownBy(() -> service.saveDraft(salesOrder(), Collections.emptyList(), 201L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("请添加至少一条销售明细");
    }

    @Test
    @DisplayName("保存销售草稿时按商品目录补齐明细并重算金额")
    void shouldApplyCatalogProductAndRecalculateSalesAmount()
    {
        loginAsAdmin();
        FakeSalesOrderMapper orderMapper = new FakeSalesOrderMapper();
        FakeSalesDetailMapper detailMapper = new FakeSalesDetailMapper();
        FakeProductMapper productMapper = new FakeProductMapper();
        productMapper.products.put(1001L, product());
        InvSalesServiceImpl service = salesService(orderMapper, detailMapper, productMapper);
        InvSalesDetail detail = salesDetail();
        detail.setProductName("前端商品名");
        detail.setUnitPrice(null);
        detail.setAmount(new BigDecimal("1.00"));
        detail.setDeliveredQuantity(new BigDecimal("99"));

        InvSalesOrder saved = service.saveDraft(salesOrder(), Collections.singletonList(detail), 201L);

        InvSalesDetail inserted = detailMapper.details.get(0);
        assertThat(inserted.getProductName()).isEqualTo("目录商品");
        assertThat(inserted.getSku()).isEqualTo("SKU-001");
        assertThat(inserted.getSpec()).isEqualTo("500g");
        assertThat(inserted.getUnit()).isEqualTo("盒");
        assertThat(inserted.getUnitPrice()).isEqualByComparingTo("12.50");
        assertThat(inserted.getAmount()).isEqualByComparingTo("25.00");
        assertThat(inserted.getDeliveredQuantity()).isEqualByComparingTo("0");
        assertThat(saved.getTotalAmount()).isEqualByComparingTo("25.00");
        assertThat(saved.getCustomerId()).isEqualTo(301L);
        assertThat(saved.getCustomerName()).isEqualTo("档案客户A");
    }

    @Test
    @DisplayName("普通销售拒绝零售价商品")
    void shouldRejectZeroPriceSalesItem()
    {
        loginAsAdmin();
        FakeProductMapper productMapper = new FakeProductMapper();
        InvProduct zeroPriceProduct = product();
        zeroPriceProduct.setSalesPrice(BigDecimal.ZERO);
        productMapper.products.put(1001L, zeroPriceProduct);
        InvSalesServiceImpl service = salesService(new FakeSalesOrderMapper(), new FakeSalesDetailMapper(),
                productMapper);

        assertThatThrownBy(() -> service.saveDraft(salesOrder(), Collections.singletonList(salesDetail()), 201L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("售价必须大于0");
    }

    @Test
    @DisplayName("销售单必须在门店上下文创建")
    void shouldRejectWarehouseContextWhenSavingSalesDraft()
    {
        loginAsAdmin();
        FakeProductMapper productMapper = new FakeProductMapper();
        productMapper.products.put(1001L, product());
        FakeDeptScopeMapper deptScopeMapper = new FakeDeptScopeMapper();
        deptScopeMapper.deptTypes.put(201L, "WAREHOUSE");
        InvSalesServiceImpl service = salesService(new FakeSalesOrderMapper(), new FakeSalesDetailMapper(),
                productMapper, deptScopeMapper);

        assertThatThrownBy(() -> service.saveDraft(salesOrder(), Collections.singletonList(salesDetail()), 201L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("请选择门店");
    }

    @Test
    @DisplayName("销售单暂不接受隐藏目标门店字段")
    void shouldRejectUnsupportedTargetStoreOnSalesDraft()
    {
        loginAsAdmin();
        FakeProductMapper productMapper = new FakeProductMapper();
        productMapper.products.put(1001L, product());
        InvSalesServiceImpl service = salesService(new FakeSalesOrderMapper(), new FakeSalesDetailMapper(),
                productMapper);
        InvSalesOrder order = salesOrder();
        order.setTargetDeptId(202L);
        order.setTargetDeptName("目标门店");

        assertThatThrownBy(() -> service.saveDraft(order, Collections.singletonList(salesDetail()), 201L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("暂不支持跨门店销售");
    }

    @Test
    @DisplayName("销售保存允许使用当前门店相关组织的共享商品")
    void shouldAllowRelatedOrganizationProductWhenSavingSalesDraft()
    {
        loginAsAdmin();
        FakeProductMapper productMapper = new FakeProductMapper();
        InvProduct sharedProduct = product();
        sharedProduct.setShopDeptId(100L);
        productMapper.products.put(1001L, sharedProduct);
        FakeDeptScopeMapper deptScopeMapper = new FakeDeptScopeMapper();
        deptScopeMapper.relatedDeptIds = Arrays.asList(100L, 201L);
        deptScopeMapper.hiddenDeptIds.put(100L, true);
        InvSalesServiceImpl service = salesService(new FakeSalesOrderMapper(), new FakeSalesDetailMapper(),
                productMapper, deptScopeMapper);

        InvSalesOrder saved = service.saveDraft(salesOrder(), Collections.singletonList(salesDetail()), 201L);

        assertThat(saved.getOrderId()).isEqualTo(900L);
    }

    @Test
    @DisplayName("销售保存拒绝无关组织商品并提示商品和组织")
    void shouldRejectUnrelatedOrganizationProductWithBusinessContext()
    {
        loginAsAdmin();
        FakeProductMapper productMapper = new FakeProductMapper();
        InvProduct outsideProduct = product();
        outsideProduct.setShopDeptId(302L);
        productMapper.products.put(1001L, outsideProduct);
        FakeDeptScopeMapper deptScopeMapper = new FakeDeptScopeMapper();
        deptScopeMapper.relatedDeptIds = Arrays.asList(100L, 201L);
        deptScopeMapper.hiddenDeptIds.put(302L, true);
        InvSalesServiceImpl service = salesService(new FakeSalesOrderMapper(), new FakeSalesDetailMapper(),
                productMapper, deptScopeMapper);

        assertThatThrownBy(() -> service.saveDraft(salesOrder(), Collections.singletonList(salesDetail()), 201L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("目录商品")
                .hasMessageContaining("商品所属组织")
                .hasMessageContaining("当前组织");
    }

    @Test
    @DisplayName("销售保存拒绝当前门店范围外的出库仓库")
    void shouldRejectSalesWarehouseOutsideCurrentStoreScope()
    {
        loginAsAdmin();
        FakeProductMapper productMapper = new FakeProductMapper();
        productMapper.products.put(1001L, product());
        FakeDeptScopeMapper deptScopeMapper = new FakeDeptScopeMapper();
        deptScopeMapper.deptTypes.put(301L, "WAREHOUSE");
        deptScopeMapper.hiddenDeptIds.put(301L, true);
        InvSalesServiceImpl service = salesService(new FakeSalesOrderMapper(), new FakeSalesDetailMapper(),
                productMapper, deptScopeMapper);
        InvSalesDetail detail = salesDetail();
        detail.setWarehouseId(301L);

        assertThatThrownBy(() -> service.saveDraft(salesOrder(), Collections.singletonList(detail), 201L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("无权使用所选出库仓库");
    }

    @Test
    @DisplayName("销售详情投影每行历史已退和剩余可退数量")
    void shouldProjectHistoricalAndReturnableSalesQuantities()
    {
        InvSalesDetail first = new InvSalesDetail();
        first.setDetailId(11L);
        first.setDeliveredQuantity(new BigDecimal("5"));
        InvSalesDetail second = new InvSalesDetail();
        second.setDetailId(12L);
        second.setDeliveredQuantity(new BigDecimal("1"));

        InvSalesServiceImpl.applyReturnableQuantities(
                Arrays.asList(first, second),
                Map.of(11L, new BigDecimal("2"), 12L, new BigDecimal("3")));

        assertThat(first.getHistoricalReturnedQuantity()).isEqualByComparingTo("2");
        assertThat(first.getReturnableQuantity()).isEqualByComparingTo("3");
        assertThat(second.getHistoricalReturnedQuantity()).isEqualByComparingTo("3");
        assertThat(second.getReturnableQuantity()).isEqualByComparingTo("0");
    }

    private InvSalesServiceImpl salesService(FakeSalesOrderMapper orderMapper, FakeSalesDetailMapper detailMapper,
            FakeProductMapper productMapper)
    {
        return salesService(orderMapper, detailMapper, productMapper, new FakeDeptScopeMapper());
    }

    private InvSalesServiceImpl salesService(FakeSalesOrderMapper orderMapper, FakeSalesDetailMapper detailMapper,
            FakeProductMapper productMapper, InvDeptScopeMapper deptScopeMapper)
    {
        InvSalesServiceImpl service = new InvSalesServiceImpl();
        ReflectionTestUtils.setField(service, "salesOrderMapper", orderMapper);
        ReflectionTestUtils.setField(service, "salesDetailMapper", detailMapper);
        ReflectionTestUtils.setField(service, "numberSequenceMapper", new FakeNumberSequenceMapper());
        ReflectionTestUtils.setField(service, "deptScopeMapper", deptScopeMapper);
        InvCustomerMapper customerMapper = mock(InvCustomerMapper.class);
        when(customerMapper.selectInvCustomerById(301L)).thenReturn(customer());
        ReflectionTestUtils.setField(service, "customerMapper", customerMapper);
        injectIfPresent(service, "productMapper", productMapper);
        InventoryItemResolver itemResolver = new InventoryItemResolver();
        ReflectionTestUtils.setField(itemResolver, "productMapper", productMapper);
        ReflectionTestUtils.setField(service, "itemResolver", itemResolver);
        return service;
    }

    private void injectIfPresent(Object target, String fieldName, Object value)
    {
        try
        {
            ReflectionTestUtils.setField(target, fieldName, value);
        }
        catch (IllegalArgumentException ignored)
        {
        }
    }

    private void loginAsAdmin()
    {
        SecurityContextHolder.setUserId("1");
        SecurityContextHolder.setUserName("admin");
        LoginUser loginUser = new LoginUser();
        SysUser sysUser = new SysUser();
        sysUser.setDeptId(201L);
        loginUser.setSysUser(sysUser);
        SecurityContextHolder.set(SecurityConstants.LOGIN_USER, loginUser);
    }

    private InvSalesOrder salesOrder()
    {
        InvSalesOrder order = new InvSalesOrder();
        order.setOrderTitle("销售单");
        order.setCustomerId(301L);
        order.setCustomerName("前端伪造客户名");
        order.setTotalAmount(BigDecimal.ONE);
        return order;
    }

    private InvCustomer customer()
    {
        InvCustomer customer = new InvCustomer();
        customer.setCustomerId(301L);
        customer.setCustomerName("档案客户A");
        customer.setShopDeptId(201L);
        customer.setStatus("0");
        return customer;
    }

    private InvSalesDetail salesDetail()
    {
        InvSalesDetail detail = new InvSalesDetail();
        detail.setProductId(1001L);
        detail.setQuantity(new BigDecimal("2"));
        return detail;
    }

    private InvProduct product()
    {
        InvProduct product = new InvProduct();
        product.setProductId(1001L);
        product.setProductName("目录商品");
        product.setSku("SKU-001");
        product.setSpec("500g");
        product.setUnit("盒");
        product.setSalesPrice(new BigDecimal("12.50"));
        product.setStatus("0");
        product.setShopDeptId(201L);
        return product;
    }

    private static class FakeSalesOrderMapper implements InvSalesOrderMapper
    {
        private InvSalesOrder stored;

        @Override
        public InvSalesOrder selectInvSalesOrderById(Long orderId)
        {
            return stored;
        }

        @Override
        public InvSalesOrder selectInvSalesOrderByIdForUpdate(Long orderId)
        {
            return stored;
        }

        @Override
        public List<InvSalesOrder> selectInvSalesOrderList(InvSalesOrder order)
        {
            return Collections.emptyList();
        }

        @Override
        public List<InvSalesOrder> selectMyInvSalesOrderList(InvSalesOrder order)
        {
            return Collections.emptyList();
        }

        @Override
        public int countByCustomerNameAndShop(String customerName, Long shopDeptId)
        {
            return 0;
        }

        @Override
        public int insertInvSalesOrder(InvSalesOrder order)
        {
            order.setOrderId(900L);
            stored = order;
            return 1;
        }

        @Override
        public int updateInvSalesOrder(InvSalesOrder order)
        {
            if (order.getStatus() != null) stored.setStatus(order.getStatus());
            if (order.getTotalAmount() != null) stored.setTotalAmount(order.getTotalAmount());
            if (order.getUpdateBy() != null) stored.setUpdateBy(order.getUpdateBy());
            return 1;
        }

        @Override
        public int deleteInvSalesOrderByIds(Long[] orderIds)
        {
            stored = null;
            return 1;
        }
    }

    private static class FakeSalesDetailMapper implements InvSalesDetailMapper
    {
        private final List<InvSalesDetail> details = new ArrayList<>();

        @Override
        public List<InvSalesDetail> selectInvSalesDetailByOrderId(Long orderId)
        {
            return details;
        }

        @Override
        public List<InvSalesDetail> selectInvSalesDetailByOrderIdForUpdate(Long orderId)
        {
            return details;
        }

        @Override
        public int insertInvSalesDetail(InvSalesDetail detail)
        {
            details.add(detail);
            return 1;
        }

        @Override
        public int updateInvSalesDetail(InvSalesDetail detail)
        {
            return 1;
        }

        @Override
        public int deleteInvSalesDetailByOrderId(Long orderId)
        {
            details.clear();
            return 1;
        }

        @Override
        public int batchInsertInvSalesDetail(List<InvSalesDetail> details)
        {
            this.details.addAll(details);
            return details.size();
        }
    }

    private static class FakeProductMapper implements InvProductMapper
    {
        private final Map<Long, InvProduct> products = new HashMap<>();

        @Override
        public List<InvProduct> selectInvProductList(InvProduct product)
        {
            return Collections.emptyList();
        }

        @Override
        public List<InvProduct> selectInvProductListBySupplier(String supplierName, List<Long> scopeDeptIds)
        {
            return Collections.emptyList();
        }

        @Override
        public InvProduct selectInvProductById(Long productId)
        {
            return products.get(productId);
        }

        @Override
        public InvProduct selectInvProductByCodeAndShop(String productCode, Long shopDeptId)
        {
            return null;
        }

        @Override
        public InvProduct selectInvProductByNaturalKey(Long categoryId, String productName, String spec, Long shopDeptId)
        {
            return null;
        }

        @Override
        public int countBusinessReferenceByProductId(Long productId)
        {
            return 0;
        }

        @Override
        public int insertInvProduct(InvProduct product)
        {
            return 0;
        }

        @Override
        public int updateInvProduct(InvProduct product)
        {
            return 0;
        }

        @Override
        public int deleteInvProductByIds(Long[] productIds)
        {
            return 0;
        }
    }

    private static class FakeDeptScopeMapper implements InvDeptScopeMapper
    {
        @Override public List<Long> selectUserAuthorizedInventoryDeptIds(Long userId) { return java.util.Collections.emptyList(); }
        @Override public List<Long> selectAllActiveInventoryDeptIds() { return java.util.Collections.emptyList(); }
        private final Map<Long, String> deptTypes = new HashMap<>();
        private final Map<Long, Boolean> hiddenDeptIds = new HashMap<>();
        private List<Long> relatedDeptIds;

        @Override
        public List<Long> selectSubDeptIds(Long deptId)
        {
            return Collections.singletonList(deptId);
        }

        @Override
        public List<Long> selectRelatedDeptIds(Long deptId)
        {
            return relatedDeptIds != null ? relatedDeptIds : Collections.singletonList(deptId);
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
            if (Boolean.TRUE.equals(hiddenDeptIds.get(targetDeptId)))
            {
                return 0;
            }
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
            return "门店" + deptId;
        }

        @Override
        public String selectDeptTypeById(Long deptId)
        {
            return deptTypes.getOrDefault(deptId, "STORE");
        }
    }

    private static class FakeNumberSequenceMapper implements InvNumberSequenceMapper
    {
        @Override
        public String selectCurrentSequence(String seqName, String currentDate)
        {
            return null;
        }

        @Override
        public int insertOrUpdateSequence(String seqName, String currentDate, int currentSeq, String prefix)
        {
            return 1;
        }

        @Override
        public int incrementAndGetSequence(String seqName, String currentDate)
        {
            return 1;
        }

        @Override
        public Long selectLastInsertId()
        {
            return 1L;
        }
    }
}
