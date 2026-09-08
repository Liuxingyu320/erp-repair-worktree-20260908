package com.erp.inventory.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.erp.common.core.constant.SecurityConstants;
import com.erp.common.core.context.SecurityContextHolder;
import com.erp.common.core.exception.ServiceException;
import com.erp.inventory.domain.InvProduct;
import com.erp.inventory.domain.InvStock;
import com.erp.inventory.domain.InvStockLog;
import com.erp.inventory.domain.vo.InvStockSummary;
import com.erp.inventory.mapper.InvDeptScopeMapper;
import com.erp.inventory.mapper.InvProductMapper;
import com.erp.inventory.mapper.InvStockLogMapper;
import com.erp.inventory.mapper.InvStockMapper;
import com.erp.system.api.model.LoginUser;

@DisplayName("库存服务仓库维度")
class InvStockServiceImplWarehouseTest
{
    @AfterEach
    void tearDown()
    {
        SecurityContextHolder.remove();
        System.clearProperty("erp.security.admin-role-bypass-enabled");
    }

    @Test
    @DisplayName("库存调整按仓库维度新增库存并写入日志")
    void shouldAdjustStockWithWarehouseContext() throws Exception
    {
        setAdminContext();

        InvStockServiceImpl service = new InvStockServiceImpl();
        FakeStockMapper stockMapper = new FakeStockMapper();
        FakeStockLogMapper stockLogMapper = new FakeStockLogMapper();
        FakeProductMapper productMapper = new FakeProductMapper();
        productMapper.products.put(3L, productCost("8.00"));
        inject(service, "stockMapper", stockMapper);
        inject(service, "stockLogMapper", stockLogMapper);
        injectIfPresent(service, "productMapper", productMapper);

        service.adjustStock(3L, 201L, 201L, new BigDecimal("5.00"), "仓库调整", 201L);

        assertThat(stockMapper.lookupShopDeptId).isEqualTo(201L);
        assertThat(stockMapper.lookupWarehouseId).isEqualTo(201L);
        assertThat(stockMapper.inserted.getShopDeptId()).isEqualTo(201L);
        assertThat(stockMapper.inserted.getWarehouseId()).isEqualTo(201L);
        assertThat(stockMapper.inserted.getLockedQuantity()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(stockMapper.inserted.getCostPrice()).isEqualByComparingTo("8.00");
        assertThat(stockMapper.inserted.getTotalCost()).isEqualByComparingTo("40.00");
        assertThat(stockMapper.inserted.getLastInTime()).isNotNull();
        assertThat(stockLogMapper.inserted.getShopDeptId()).isEqualTo(201L);
        assertThat(stockLogMapper.inserted.getWarehouseId()).isEqualTo(201L);
        assertThat(stockLogMapper.inserted.getAfterQuantity()).isEqualByComparingTo("5.00");
    }

    @Test
    @DisplayName("已有库存正向调整时同步更新库存成本")
    void shouldUpdateCostWhenIncreasingExistingStock() throws Exception
    {
        setAdminContext();

        InvStockServiceImpl service = new InvStockServiceImpl();
        FakeStockMapper stockMapper = new FakeStockMapper();
        FakeStockLogMapper stockLogMapper = new FakeStockLogMapper();
        InvStock existingStock = new InvStock();
        existingStock.setStockId(1001L);
        existingStock.setProductId(3L);
        existingStock.setShopDeptId(201L);
        existingStock.setWarehouseId(201L);
        existingStock.setCurrentQuantity(new BigDecimal("5.00"));
        existingStock.setAvailableQuantity(new BigDecimal("5.00"));
        existingStock.setLockedQuantity(BigDecimal.ZERO);
        existingStock.setCostPrice(new BigDecimal("7.00"));
        existingStock.setTotalCost(new BigDecimal("35.00"));
        stockMapper.productShopWarehouseStock = existingStock;
        inject(service, "stockMapper", stockMapper);
        inject(service, "stockLogMapper", stockLogMapper);

        service.adjustStock(3L, 201L, 201L, new BigDecimal("2.00"), "盘盈", 201L);

        assertThat(stockMapper.addedCost).isEqualByComparingTo("14.00");
        assertThat(stockMapper.updated).isNull();
        assertThat(stockLogMapper.inserted.getCostPrice()).isEqualByComparingTo("7.00");
        assertThat(stockLogMapper.inserted.getAfterQuantity()).isEqualByComparingTo("7.00");
    }

    @Test
    @DisplayName("库存汇总使用当前库存组织作为查询范围")
    void shouldSummarizeStockWithSelectedWarehouseContext() throws Exception
    {
        setAdminContext();

        InvStockServiceImpl service = new InvStockServiceImpl();
        FakeStockMapper stockMapper = new FakeStockMapper();
        stockMapper.summary = new InvStockSummary();
        stockMapper.summary.setStockItemCount(3L);
        stockMapper.summary.setMyWarehouseQuantity(new BigDecimal("12.00"));
        stockMapper.summary.setOtherShopQuantity(new BigDecimal("8.00"));
        inject(service, "stockMapper", stockMapper);

        InvStock query = new InvStock();
        query.setOwnOnly(true);
        InvStockSummary summary = service.selectStockSummary(query, 201L);

        assertThat(stockMapper.summaryQuery.getSelectedWarehouseId()).isEqualTo(201L);
        assertThat(stockMapper.summaryQuery.getShopDeptId()).isEqualTo(201L);
        assertThat(stockMapper.summaryQuery.getWarehouseId()).isEqualTo(201L);
        assertThat(summary.getStockItemCount()).isEqualTo(3L);
        assertThat(summary.getMyWarehouseQuantity()).isEqualByComparingTo("12.00");
        assertThat(summary.getOtherShopQuantity()).isEqualByComparingTo("8.00");
    }

    @Test
    @DisplayName("普通用户库存列表只查询当前库存组织")
    void shouldLimitStockListToSelectedInventoryDeptForRegularUser() throws Exception
    {
        SecurityContextHolder.setUserId("200");
        SecurityContextHolder.setUserName("storeUser");

        InvStockServiceImpl service = new InvStockServiceImpl();
        FakeStockMapper stockMapper = new FakeStockMapper();
        FakeDeptScopeMapper deptScopeMapper = new FakeDeptScopeMapper();
        deptScopeMapper.subDeptIds = Arrays.asList(201L, 202L);
        inject(service, "stockMapper", stockMapper);
        inject(service, "deptScopeMapper", deptScopeMapper);

        InvStock query = new InvStock();
        query.setShopDeptId(999L);
        query.setWarehouseId(888L);
        query.setStockScope("other");
        query.setOwnOnly(true);

        service.selectStockList(query, 201L);

        assertThat(stockMapper.listQuery.getShopDeptId()).isEqualTo(201L);
        assertThat(stockMapper.listQuery.getWarehouseId()).isEqualTo(201L);
        assertThat(stockMapper.listQuery.getSelectedWarehouseId()).isEqualTo(201L);
        assertThat(stockMapper.listQuery.getStockScope()).isNull();
        assertThat(stockMapper.listQuery.getParams()).doesNotContainKey("scopeDeptIds");
    }

    @Test
    @DisplayName("进销存库存可切换到当前用户授权门店")
    void shouldAllowStoreEntryToQueryAnotherAuthorizedStore() throws Exception
    {
        SecurityContextHolder.setUserId("200");
        SecurityContextHolder.setUserName("storeManager");

        InvStockServiceImpl service = new InvStockServiceImpl();
        FakeStockMapper stockMapper = new FakeStockMapper();
        FakeDeptScopeMapper deptScopeMapper = new FakeDeptScopeMapper();
        deptScopeMapper.deptTypes.put(10L, "STORE");
        deptScopeMapper.deptTypes.put(11L, "STORE");
        inject(service, "stockMapper", stockMapper);
        inject(service, "deptScopeMapper", deptScopeMapper);

        InvStock query = new InvStock();
        query.setOwnOnly(false);
        query.setShopDeptId(11L);

        service.selectStockList(query, 10L);

        assertThat(stockMapper.listQuery.getShopDeptId()).isEqualTo(11L);
        assertThat(stockMapper.listQuery.getWarehouseId()).isEqualTo(11L);
        assertThat(stockMapper.listQuery.getParams()).doesNotContainKey("scopeDeptIds");
    }

    @Test
    @DisplayName("进销存库存全部店铺只查询当前用户授权门店")
    void shouldLimitStoreEntryAllStoresToAuthorizedStores() throws Exception
    {
        SecurityContextHolder.setUserId("200");
        SecurityContextHolder.setUserName("storeManager");

        InvStockServiceImpl service = new InvStockServiceImpl();
        FakeStockMapper stockMapper = new FakeStockMapper();
        FakeDeptScopeMapper deptScopeMapper = new FakeDeptScopeMapper();
        deptScopeMapper.deptTypes.put(10L, "STORE");
        deptScopeMapper.userStoreScopeDeptIds = Arrays.asList(10L, 11L);
        inject(service, "stockMapper", stockMapper);
        inject(service, "deptScopeMapper", deptScopeMapper);

        InvStock query = new InvStock();
        query.setOwnOnly(false);
        query.setShopDeptId(0L);

        service.selectStockList(query, 10L);

        assertThat(stockMapper.listQuery.getShopDeptId()).isNull();
        assertThat(stockMapper.listQuery.getWarehouseId()).isNull();
        assertThat(stockMapper.listQuery.getParams()).containsEntry("scopeDeptIds", Arrays.asList(10L, 11L));
    }

    @Test
    @DisplayName("仓库可见库存包含同一业务根下的门店库存")
    void shouldIncludeSiblingStoresWhenWarehouseViewsVisibleStock() throws Exception
    {
        SecurityContextHolder.setUserId("200");
        SecurityContextHolder.setUserName("warehouseUser");

        InvStockServiceImpl service = new InvStockServiceImpl();
        FakeStockMapper stockMapper = new FakeStockMapper();
        FakeDeptScopeMapper deptScopeMapper = new FakeDeptScopeMapper();
        deptScopeMapper.deptTypes.put(1245L, "WAREHOUSE");
        deptScopeMapper.ancestorDeptIds = Arrays.asList(1123L, 100L);
        deptScopeMapper.subDeptIds = Arrays.asList(100L, 1206L, 1245L);
        inject(service, "stockMapper", stockMapper);
        inject(service, "deptScopeMapper", deptScopeMapper);

        InvStock query = new InvStock();
        query.setOwnOnly(false);

        service.selectStockList(query, 1245L);

        assertThat(deptScopeMapper.ancestorLookupDeptId).isEqualTo(1245L);
        assertThat(deptScopeMapper.subDeptLookupDeptId).isEqualTo(100L);
        assertThat(stockMapper.listQuery.getSelectedWarehouseId()).isEqualTo(1245L);
        assertThat(stockMapper.listQuery.getShopDeptId()).isNull();
        assertThat(stockMapper.listQuery.getWarehouseId()).isNull();
        assertThat(stockMapper.listQuery.getParams()).containsEntry("scopeDeptIds", Arrays.asList(100L, 1206L, 1245L));
    }

    @Test
    @DisplayName("门店发起要货选商品时查询所选仓库库存")
    void shouldQuerySelectedWarehouseStockForStoreTransferSourcePicker() throws Exception
    {
        SecurityContextHolder.setUserId("200");
        SecurityContextHolder.setUserName("storeUser");

        InvStockServiceImpl service = new InvStockServiceImpl();
        FakeStockMapper stockMapper = new FakeStockMapper();
        FakeDeptScopeMapper deptScopeMapper = new FakeDeptScopeMapper();
        deptScopeMapper.deptTypes.put(10L, "STORE");
        deptScopeMapper.deptTypes.put(20L, "WAREHOUSE");
        deptScopeMapper.relatedDeptIds = Arrays.asList(100L, 20L);
        inject(service, "stockMapper", stockMapper);
        inject(service, "deptScopeMapper", deptScopeMapper);

        InvStock query = new InvStock();
        query.setShopDeptId(20L);
        query.setWarehouseId(20L);
        query.setStockStatus("available");
        query.setTransferSource(true);

        service.selectStockList(query, 10L);

        assertThat(stockMapper.listQuery.getShopDeptId()).isEqualTo(20L);
        assertThat(stockMapper.listQuery.getWarehouseId()).isEqualTo(20L);
        assertThat(stockMapper.listQuery.getSelectedWarehouseId()).isEqualTo(20L);
        assertThat(stockMapper.listQuery.getStockStatus()).isEqualTo("available");
        assertThat(stockMapper.listQuery.getParams()).doesNotContainKey("scopeDeptIds");
        assertThat(stockMapper.listQuery.getParams())
                .containsEntry("transferSourceItemQualified", Boolean.TRUE)
                .containsEntry("itemOwnerScopeDeptIds", Set.of(100L, 20L));
        assertThat(deptScopeMapper.relatedLookupDeptId).isEqualTo(20L);
    }

    @Test
    @DisplayName("异店调货选商品时可查询授权来源门店库存")
    void shouldQueryAuthorizedSourceStoreStockForCrossStorePicker()
            throws Exception
    {
        SecurityContextHolder.setUserId("200");
        SecurityContextHolder.setUserName("storeManager");

        InvStockServiceImpl service = new InvStockServiceImpl();
        FakeStockMapper stockMapper = new FakeStockMapper();
        FakeDeptScopeMapper deptScopeMapper = new FakeDeptScopeMapper();
        deptScopeMapper.deptTypes.put(10L, "STORE");
        deptScopeMapper.deptTypes.put(11L, "STORE");
        inject(service, "stockMapper", stockMapper);
        inject(service, "deptScopeMapper", deptScopeMapper);

        InvStock query = new InvStock();
        query.setShopDeptId(11L);
        query.setWarehouseId(11L);
        query.setStockStatus("available");
        query.setTransferSource(true);

        service.selectStockList(query, 10L);

        assertThat(stockMapper.listQuery.getShopDeptId()).isEqualTo(11L);
        assertThat(stockMapper.listQuery.getWarehouseId()).isEqualTo(11L);
        assertThat(stockMapper.listQuery.getSelectedWarehouseId())
                .isEqualTo(11L);
        assertThat(stockMapper.listQuery.getStockStatus())
                .isEqualTo("available");
        assertThat(stockMapper.listQuery.getParams())
                .containsEntry("transferSourceItemQualified", Boolean.TRUE)
                .doesNotContainKeys("scopeDeptIds", "itemOwnerScopeDeptIds");
        assertThat(stockMapper.listQuery.getParams())
                .doesNotContainKey("itemOwnerScopeDeptIds");
    }

    @Test
    @DisplayName("异店调货选商品时拒绝查询未授权来源门店库存")
    void shouldRejectUnauthorizedSourceStoreStockForCrossStorePicker()
            throws Exception
    {
        SecurityContextHolder.setUserId("200");
        SecurityContextHolder.setUserName("storeManager");

        InvStockServiceImpl service = new InvStockServiceImpl();
        FakeStockMapper stockMapper = new FakeStockMapper();
        FakeDeptScopeMapper deptScopeMapper = new FakeDeptScopeMapper();
        deptScopeMapper.deptTypes.put(10L, "STORE");
        deptScopeMapper.deptTypes.put(11L, "STORE");
        deptScopeMapper.unauthorizedDeptIds.add(11L);
        inject(service, "stockMapper", stockMapper);
        inject(service, "deptScopeMapper", deptScopeMapper);

        InvStock query = new InvStock();
        query.setShopDeptId(11L);
        query.setWarehouseId(11L);
        query.setTransferSource(true);

        assertThatThrownBy(() -> service.selectStockList(query, 10L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("无权选择该店铺");
        assertThat(stockMapper.listQuery).isNull();
    }

    @Test
    @DisplayName("门店发起要货选商品时可查询未绑定仓库库存")
    void shouldAllowUnboundWarehouseForStoreTransferSourcePicker() throws Exception
    {
        SecurityContextHolder.setUserId("200");
        SecurityContextHolder.setUserName("storeUser");

        InvStockServiceImpl service = new InvStockServiceImpl();
        FakeStockMapper stockMapper = new FakeStockMapper();
        FakeDeptScopeMapper deptScopeMapper = new FakeDeptScopeMapper();
        deptScopeMapper.deptTypes.put(10L, "STORE");
        deptScopeMapper.deptTypes.put(20L, "WAREHOUSE");
        deptScopeMapper.relatedDeptIds = Collections.singletonList(20L);
        inject(service, "stockMapper", stockMapper);
        inject(service, "deptScopeMapper", deptScopeMapper);

        InvStock query = new InvStock();
        query.setShopDeptId(20L);
        query.setWarehouseId(20L);
        query.setTransferSource(true);

        service.selectStockList(query, 10L);

        assertThat(stockMapper.listQuery.getShopDeptId()).isEqualTo(20L);
        assertThat(stockMapper.listQuery.getWarehouseId()).isEqualTo(20L);
        assertThat(stockMapper.listQuery.getSelectedWarehouseId()).isEqualTo(20L);
        assertThat(stockMapper.listQuery.getStockStatus()).isEqualTo("available");
        assertThat(stockMapper.listQuery.getParams()).doesNotContainKey("scopeDeptIds");
        assertThat(stockMapper.listQuery.getParams())
                .containsEntry("transferSourceItemQualified", Boolean.TRUE)
                .containsEntry("itemOwnerScopeDeptIds", Set.of(20L));
    }

    @Test
    @DisplayName("门店发起要货选商品时库存返回参考成本价但不暴露仓库内部字段")
    void shouldHideSensitiveWarehouseStockFieldsForTransferSourcePicker() throws Exception
    {
        SecurityContextHolder.setUserId("200");
        SecurityContextHolder.setUserName("storeUser");

        InvStockServiceImpl service = new InvStockServiceImpl();
        FakeStockMapper stockMapper = new FakeStockMapper();
        InvStock stock = new InvStock();
        stock.setStockId(1001L);
        stock.setProductId(3L);
        stock.setProductName("龙井");
        stock.setProductCode("TEA001");
        stock.setShopDeptId(20L);
        stock.setWarehouseId(20L);
        stock.setCurrentQuantity(new BigDecimal("10.00"));
        stock.setLockedQuantity(new BigDecimal("2.00"));
        stock.setAvailableQuantity(new BigDecimal("8.00"));
        stock.setCostPrice(new BigDecimal("7.00"));
        stock.setTotalCost(new BigDecimal("70.00"));
        stock.setBatchNo("BATCH-1");
        stock.setSerialNo("SERIAL-1");
        stock.setLocationCode("A-01");
        stock.setLocationName("一号货架");
        stock.setVersion(5L);
        stock.setRemark("内部备注");
        stockMapper.stockList = Collections.singletonList(stock);
        FakeDeptScopeMapper deptScopeMapper = new FakeDeptScopeMapper();
        deptScopeMapper.deptTypes.put(10L, "STORE");
        deptScopeMapper.deptTypes.put(20L, "WAREHOUSE");
        deptScopeMapper.relatedDeptIds = Collections.singletonList(20L);
        inject(service, "stockMapper", stockMapper);
        inject(service, "deptScopeMapper", deptScopeMapper);

        InvStock query = new InvStock();
        query.setWarehouseId(20L);
        query.setTransferSource(true);

        List<InvStock> result = service.selectStockList(query, 10L);

        assertThat(result).hasSize(1);
        InvStock visible = result.get(0);
        assertThat(visible.getProductId()).isEqualTo(3L);
        assertThat(visible.getAvailableQuantity()).isEqualByComparingTo("8.00");
        assertThat(visible.getCostPrice()).isEqualByComparingTo("7.00");
        assertThat(visible.getTotalCost()).isNull();
        assertThat(visible.getCurrentQuantity()).isNull();
        assertThat(visible.getLockedQuantity()).isNull();
        assertThat(visible.getBatchNo()).isNull();
        assertThat(visible.getSerialNo()).isNull();
        assertThat(visible.getLocationCode()).isNull();
        assertThat(visible.getLocationName()).isNull();
        assertThat(visible.getVersion()).isNull();
        assertThat(visible.getRemark()).isNull();
    }

    @Test
    @DisplayName("管理员也只能查询同一业务根的要货来源库存")
    void shouldAllowAdminToQuerySameRootWarehouseForTransferSourcePicker() throws Exception
    {
        setAdminContext();

        InvStockServiceImpl service = new InvStockServiceImpl();
        FakeStockMapper stockMapper = new FakeStockMapper();
        FakeDeptScopeMapper deptScopeMapper = new FakeDeptScopeMapper();
        deptScopeMapper.deptTypes.put(10L, "STORE");
        deptScopeMapper.deptTypes.put(20L, "WAREHOUSE");
        deptScopeMapper.relatedDeptIds = Collections.singletonList(20L);
        inject(service, "stockMapper", stockMapper);
        inject(service, "deptScopeMapper", deptScopeMapper);

        InvStock query = new InvStock();
        query.setWarehouseId(20L);
        query.setTransferSource(true);

        service.selectStockList(query, 10L);

        assertThat(stockMapper.listQuery.getShopDeptId()).isEqualTo(20L);
        assertThat(stockMapper.listQuery.getWarehouseId()).isEqualTo(20L);
        assertThat(stockMapper.listQuery.getSelectedWarehouseId()).isEqualTo(20L);
        assertThat(stockMapper.listQuery.getStockStatus()).isEqualTo("available");
        assertThat(stockMapper.listQuery.getParams()).doesNotContainKey("scopeDeptIds");
    }

    @Test
    @DisplayName("门店要货拒绝查询其他业务根的仓库库存")
    void shouldRejectWarehouseOutsideStoreBusinessRoot() throws Exception
    {
        SecurityContextHolder.setUserId("200");
        SecurityContextHolder.setUserName("storeUser");

        InvStockServiceImpl service = new InvStockServiceImpl();
        FakeStockMapper stockMapper = new FakeStockMapper();
        FakeDeptScopeMapper deptScopeMapper = new FakeDeptScopeMapper();
        deptScopeMapper.deptTypes.put(10L, "STORE");
        deptScopeMapper.deptTypes.put(20L, "WAREHOUSE");
        deptScopeMapper.businessRootDeptIds.put(10L, 100L);
        deptScopeMapper.businessRootDeptIds.put(20L, 200L);
        inject(service, "stockMapper", stockMapper);
        inject(service, "deptScopeMapper", deptScopeMapper);

        InvStock query = new InvStock();
        query.setWarehouseId(20L);
        query.setTransferSource(true);

        assertThatThrownBy(() -> service.selectStockList(query, 10L))
                .isInstanceOf(ServiceException.class)
                .hasMessage("当前门店不允许向该仓库要货");
        assertThat(stockMapper.listQuery).isNull();
    }

    @Test
    @DisplayName("仓库上下文可以查看可见门店库存详情")
    void shouldAllowWarehouseContextToReadVisibleStoreStockDetail() throws Exception
    {
        setAdminContext();

        InvStockServiceImpl service = new InvStockServiceImpl();
        FakeStockMapper stockMapper = new FakeStockMapper();
        InvStock storeStock = new InvStock();
        storeStock.setStockId(1001L);
        storeStock.setProductId(3L);
        storeStock.setShopDeptId(10L);
        storeStock.setWarehouseId(10L);
        stockMapper.selectedById = storeStock;
        FakeDeptScopeMapper deptScopeMapper = new FakeDeptScopeMapper();
        deptScopeMapper.deptTypes.put(20L, "WAREHOUSE");
        deptScopeMapper.deptTypes.put(10L, "STORE");
        inject(service, "stockMapper", stockMapper);
        inject(service, "deptScopeMapper", deptScopeMapper);

        InvStock result = service.selectStockById(1001L, 20L);

        assertThat(result).isSameAs(storeStock);
    }

    @Test
    @DisplayName("仓库上下文不能手动调整门店库存")
    void shouldRejectWarehouseAdjustingStoreStock()
    {
        setAdminContext();

        InvStockServiceImpl service = new InvStockServiceImpl();
        FakeDeptScopeMapper deptScopeMapper = new FakeDeptScopeMapper();
        deptScopeMapper.deptTypes.put(20L, "WAREHOUSE");
        deptScopeMapper.deptTypes.put(10L, "STORE");
        try
        {
            inject(service, "stockMapper", new FakeStockMapper());
            inject(service, "stockLogMapper", new FakeStockLogMapper());
            inject(service, "deptScopeMapper", deptScopeMapper);
        }
        catch (Exception e)
        {
            throw new AssertionError(e);
        }

        assertThatThrownBy(() -> service.adjustStock(3L, 10L, 10L, new BigDecimal("1.00"), "跨店调整", 20L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("只能调整当前组织库存");
    }

    @Test
    @DisplayName("手动调减不能超过可用库存")
    void shouldRejectNegativeAdjustmentWhenAvailableStockInsufficient() throws Exception
    {
        setAdminContext();

        InvStockServiceImpl service = new InvStockServiceImpl();
        FakeStockMapper stockMapper = new FakeStockMapper();
        InvStock existingStock = new InvStock();
        existingStock.setStockId(1001L);
        existingStock.setProductId(3L);
        existingStock.setShopDeptId(201L);
        existingStock.setWarehouseId(201L);
        existingStock.setCurrentQuantity(new BigDecimal("5.00"));
        existingStock.setLockedQuantity(new BigDecimal("4.00"));
        existingStock.setAvailableQuantity(new BigDecimal("1.00"));
        existingStock.setCostPrice(BigDecimal.ZERO);
        stockMapper.productShopWarehouseStock = existingStock;
        inject(service, "stockMapper", stockMapper);
        inject(service, "stockLogMapper", new FakeStockLogMapper());

        assertThatThrownBy(() -> service.adjustStock(3L, 201L, 201L, new BigDecimal("-3.00"),
                "调减超过可用库存", 201L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("可用库存不足");

        assertThat(stockMapper.updated).isNull();
    }

    @Test
    @DisplayName("门店上下文不能通过仓库参数调整其它组织库存")
    void shouldRejectStoreAdjustingAnotherInventoryDept()
    {
        setAdminContext();

        InvStockServiceImpl service = new InvStockServiceImpl();
        FakeDeptScopeMapper deptScopeMapper = new FakeDeptScopeMapper();
        deptScopeMapper.deptTypes.put(10L, "STORE");
        deptScopeMapper.deptTypes.put(20L, "WAREHOUSE");
        try
        {
            inject(service, "stockMapper", new FakeStockMapper());
            inject(service, "stockLogMapper", new FakeStockLogMapper());
            inject(service, "deptScopeMapper", deptScopeMapper);
        }
        catch (Exception e)
        {
            throw new AssertionError(e);
        }

        assertThatThrownBy(() -> service.adjustStock(3L, 20L, 20L, new BigDecimal("1.00"), "跨组织调整", 10L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("只能调整当前组织库存");
    }

    private void inject(Object target, String fieldName, Object value) throws Exception
    {
        Field field = findField(target.getClass(), fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private void setAdminContext()
    {
        SecurityContextHolder.setUserId("1");
        SecurityContextHolder.setUserName("admin");
        System.setProperty("erp.security.admin-role-bypass-enabled", "true");
        LoginUser loginUser = new LoginUser();
        loginUser.setRoles(new HashSet<>(Collections.singletonList("admin")));
        SecurityContextHolder.set(SecurityConstants.LOGIN_USER, loginUser);
    }

    private void injectIfPresent(Object target, String fieldName, Object value) throws Exception
    {
        try
        {
            inject(target, fieldName, value);
        }
        catch (NoSuchFieldException ignored)
        {
        }
    }

    private InvProduct productCost(String cost)
    {
        InvProduct product = new InvProduct();
        product.setProductId(3L);
        product.setCostPrice(new BigDecimal(cost));
        return product;
    }

    private Field findField(Class<?> type, String fieldName) throws NoSuchFieldException
    {
        Class<?> current = type;
        while (current != null)
        {
            try
            {
                return current.getDeclaredField(fieldName);
            }
            catch (NoSuchFieldException ignored)
            {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(fieldName);
    }

    private static class FakeStockMapper implements InvStockMapper
    {
        private Long lookupWarehouseId;
        private Long lookupShopDeptId;
        private InvStock inserted;
        private InvStock updated;
        private InvStock selectedById;
        private InvStock productShopWarehouseStock;
        private InvStock listQuery;
        private List<InvStock> stockList = Collections.emptyList();
        private InvStock summaryQuery;
        private InvStockSummary summary;
        private BigDecimal addedCost;
        private BigDecimal deductedCost;

        @Override
        public List<InvStock> selectInvStockList(InvStock stock)
        {
            listQuery = stock;
            return stockList;
        }

        @Override
        public InvStock selectInvStockById(Long stockId)
        {
            if (selectedById != null)
            {
                return selectedById;
            }
            if (productShopWarehouseStock != null)
            {
                return productShopWarehouseStock;
            }
            return inserted;
        }

        @Override
        public InvStock selectInvStockByIdForUpdate(Long stockId)
        {
            return selectInvStockById(stockId);
        }

        @Override
        public InvStockSummary selectInvStockSummary(InvStock stock)
        {
            summaryQuery = stock;
            return summary;
        }

        @Override
        public InvStock selectInvStockByProductAndShop(Long productId, Long shopDeptId)
        {
            return null;
        }

        @Override
        public InvStock selectInvStockByProductAndShopForUpdate(Long productId, Long shopDeptId)
        {
            return null;
        }

        @Override
        public InvStock selectInvStockByProductShopWarehouse(Long productId, Long shopDeptId, Long warehouseId)
        {
            this.lookupShopDeptId = shopDeptId;
            this.lookupWarehouseId = warehouseId;
            return productShopWarehouseStock;
        }

        @Override
        public InvStock selectInvStockByProductShopWarehouseForUpdate(Long productId, Long shopDeptId, Long warehouseId)
        {
            this.lookupShopDeptId = shopDeptId;
            this.lookupWarehouseId = warehouseId;
            return productShopWarehouseStock;
        }

        @Override
        public InvStock selectInvStockByItemShopWarehouse(String itemType, Long itemId, Long shopDeptId, Long warehouseId)
        {
            return selectInvStockByProductShopWarehouse(itemId, shopDeptId, warehouseId);
        }

        @Override
        public InvStock selectInvStockByItemShopWarehouseForUpdate(String itemType, Long itemId, Long shopDeptId, Long warehouseId)
        {
            return selectInvStockByProductShopWarehouseForUpdate(itemId, shopDeptId, warehouseId);
        }

        @Override
        public int insertInvStock(InvStock stock)
        {
            stock.setStockId(1L);
            stock.setVersion(stock.getVersion() == null ? 0L : stock.getVersion());
            inserted = stock;
            return 1;
        }

        @Override
        public int updateInvStock(InvStock stock)
        {
            stock.setVersion(stock.getVersion() == null ? 1L : stock.getVersion() + 1);
            updated = stock;
            return 1;
        }

        @Override
        public int addInvStock(Long stockId, Long version, BigDecimal quantity, String updateBy)
        {
            return 1;
        }

        @Override
        public int addInvStockWithCost(Long stockId, Long version, BigDecimal quantity, BigDecimal incomingCost, String updateBy)
        {
            addedCost = incomingCost;
            productShopWarehouseStock.setCurrentQuantity(productShopWarehouseStock.getCurrentQuantity().add(quantity));
            productShopWarehouseStock.setAvailableQuantity(productShopWarehouseStock.getAvailableQuantity().add(quantity));
            productShopWarehouseStock.setTotalCost(productShopWarehouseStock.getTotalCost().add(incomingCost));
            productShopWarehouseStock.setVersion((productShopWarehouseStock.getVersion() == null ? 0L : productShopWarehouseStock.getVersion()) + 1);
            return 1;
        }

        @Override
        public int deductInvStock(Long stockId, Long version, BigDecimal quantity, String updateBy)
        {
            return 1;
        }

        @Override
        public int deductInvStockWithCost(Long stockId, Long version, BigDecimal quantity, BigDecimal deductCost, String updateBy)
        {
            deductedCost = deductCost;
            productShopWarehouseStock.setVersion((productShopWarehouseStock.getVersion() == null ? 0L : productShopWarehouseStock.getVersion()) + 1);
            return 1;
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
        private List<Long> subDeptIds = Collections.emptyList();
        private List<Long> relatedDeptIds = Collections.emptyList();
        private List<Long> ancestorDeptIds = Collections.emptyList();
        private List<Long> userStoreScopeDeptIds = Collections.emptyList();
        private Map<Long, String> deptTypes = new HashMap<>();
        private Map<Long, Long> businessRootDeptIds = new HashMap<>();
        private final java.util.Set<Long> unauthorizedDeptIds =
                new HashSet<>();
        private Long subDeptLookupDeptId;
        private Long ancestorLookupDeptId;
        private Long relatedLookupDeptId;

        @Override
        public List<Long> selectSubDeptIds(Long deptId)
        {
            subDeptLookupDeptId = deptId;
            return subDeptIds;
        }

        @Override
        public List<Long> selectRelatedDeptIds(Long deptId)
        {
            relatedLookupDeptId = deptId;
            return relatedDeptIds;
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
            ancestorLookupDeptId = deptId;
            return ancestorDeptIds;
        }

        @Override
        public Long selectRawBusinessRootDeptId(Long deptId)
        {
            return businessRootDeptIds.getOrDefault(deptId, 100L);
        }

        @Override
        public List<Long> selectUserStoreScopeDeptIds(Long userId)
        {
            return userStoreScopeDeptIds;
        }

        @Override
        public List<Long> selectAllStoreDeptIds()
        {
            return userStoreScopeDeptIds;
        }

        @Override
        public int countDeptInScope(Long scopeDeptId, Long targetDeptId)
        {
            return 1;
        }

        @Override
        public int countUserShopScope(Long userId, Long deptId)
        {
            return unauthorizedDeptIds.contains(deptId) ? 0 : 1;
        }

        @Override
        public String selectDeptNameById(Long deptId)
        {
            return "测试组织";
        }

        @Override
        public String selectDeptTypeById(Long deptId)
        {
            return deptTypes.getOrDefault(deptId, "WAREHOUSE");
        }
    }

    private static class FakeStockLogMapper implements InvStockLogMapper
    {
        private InvStockLog inserted;

        @Override
        public List<InvStockLog> selectInvStockLogList(InvStockLog stockLog)
        {
            return Collections.emptyList();
        }

        @Override
        public int insertInvStockLog(InvStockLog stockLog)
        {
            inserted = stockLog;
            return 1;
        }
    }
}
