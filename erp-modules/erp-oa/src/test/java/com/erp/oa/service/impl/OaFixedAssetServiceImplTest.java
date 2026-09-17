package com.erp.oa.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import com.erp.common.core.context.SecurityContextHolder;
import com.erp.common.core.exception.ServiceException;
import com.erp.oa.domain.OaFixedAssetConfig;
import com.erp.oa.domain.OaFixedAssetQuota;
import com.erp.oa.domain.OaFixedAssetQuotaLedger;
import com.erp.oa.domain.OaFixedAssetQuotaMonth;
import com.erp.oa.domain.OaFixedAssetRepair;
import com.erp.oa.domain.dto.OaFixedAssetRepairBatchItem;
import com.erp.oa.domain.dto.OaFixedAssetRepairBatchRequest;
import com.erp.oa.domain.vo.OaFixedAssetQuotaSummary;
import com.erp.oa.exception.OaFixedAssetValidationException;
import com.erp.oa.mapper.OaDeptScopeMapper;
import com.erp.oa.mapper.OaFixedAssetConfigMapper;
import com.erp.oa.mapper.OaFixedAssetQuotaLedgerMapper;
import com.erp.oa.mapper.OaFixedAssetQuotaMapper;
import com.erp.oa.mapper.OaFixedAssetQuotaMonthMapper;
import com.erp.oa.mapper.OaFixedAssetRepairMapper;

@DisplayName("OA固定资产服务")
class OaFixedAssetServiceImplTest
{
    @AfterEach
    void tearDown()
    {
        SecurityContextHolder.remove();
    }

    @Test
    @DisplayName("维修上报按OE单价和坏掉数量自动计算占用额度")
    void shouldCalculateRepairUsageFromAssetPriceAndQuantity()
    {
        loginAsStoreUser();
        FakeFixedAssetMappers mappers = seededMappers();
        mappers.configMapper.assetUnitPrice = new BigDecimal("1000.00");
        mappers.ledgerMapper.usedAmount = BigDecimal.ZERO;
        OaFixedAssetServiceImpl service = service(mappers, "2026-03-15T00:00:00Z");

        OaFixedAssetRepair repair = repair(201L);
        repair.setRepairQuantity(new BigDecimal("2.00"));
        OaFixedAssetRepair saved = service.submitRepair(repair, 201L);

        assertThat(saved.getStatus()).isEqualTo("submitted");
        assertThat(saved.getEstimatedRepairAmount()).isEqualByComparingTo("2000.00");
        assertThat(saved.getRepairQuantity()).isEqualByComparingTo("2.00");
        assertThat(mappers.repairMapper.repairs).hasSize(1);
        assertThat(mappers.ledgerMapper.ledgers).hasSize(1);
        assertThat(mappers.ledgerMapper.ledgers.get(0).getAmount()).isEqualByComparingTo("2000.00");
        assertThat(mappers.ledgerMapper.ledgers.get(0).getMovementType()).isEqualTo("normal_submit");
    }

    @Test
    @DisplayName("维修上报超过当前累计可用额度时拒绝")
    void shouldRejectRepairWhenComputedUsageExceedsAvailableQuota()
    {
        loginAsStoreUser();
        FakeFixedAssetMappers mappers = seededMappers();
        mappers.configMapper.assetUnitPrice = new BigDecimal("1000.00");
        mappers.ledgerMapper.usedAmount = new BigDecimal("5500.00");
        OaFixedAssetServiceImpl service = service(mappers, "2026-03-15T00:00:00Z");

        OaFixedAssetRepair repair = repair(201L);
        repair.setRepairQuantity(new BigDecimal("2.00"));

        assertThatThrownBy(() -> service.submitRepair(repair, 201L))
                .isInstanceOfSatisfying(OaFixedAssetValidationException.class, failure -> {
                    assertThat(failure.getErrorCode()).isEqualTo("FIXED_ASSET_QUOTA_EXCEEDED");
                    assertThat(failure.getMessage()).contains("当前可用额度不足", "自行购买且无需上报");
                    assertThat(failure.getPrecheck()).isNotNull();
                });
        assertThat(mappers.repairMapper.repairs).isEmpty();
        assertThat(mappers.ledgerMapper.ledgers).isEmpty();
    }

    @Test
    @DisplayName("固定资产配置变动只更新当前月和未来月额度快照")
    void shouldOnlyUpdateCurrentAndFutureMonthlyQuotaSnapshotsWhenConfigChanges()
    {
        loginAsAdmin();
        FakeFixedAssetMappers mappers = seededMappers();
        mappers.monthMapper.putMonthlyQuota(201L, 2026, 1, new BigDecimal("83.33"));
        mappers.monthMapper.putMonthlyQuota(201L, 2026, 2, new BigDecimal("83.33"));
        OaFixedAssetServiceImpl service = service(mappers, "2026-03-15T00:00:00Z");

        OaFixedAssetConfig config = new OaFixedAssetConfig();
        config.setShopDeptId(201L);
        config.setOeItemId(1001L);
        config.setAssetQuantity(new BigDecimal("4.00"));
        config.setAnnualRepairRatio(new BigDecimal("20.00"));

        service.saveConfig(config, 201L);

        assertThat(mappers.monthMapper.monthlyQuota(201L, 2026, 1)).isEqualByComparingTo("83.33");
        assertThat(mappers.monthMapper.monthlyQuota(201L, 2026, 2)).isEqualByComparingTo("83.33");
        assertThat(mappers.monthMapper.monthlyQuota(201L, 2026, 3)).isEqualByComparingTo("66.67");
        assertThat(mappers.monthMapper.monthlyQuota(201L, 2026, 12)).isEqualByComparingTo("66.67");
    }

    @Test
    @DisplayName("启用固定资产配置前要求OE同款资料完整")
    void shouldRejectActiveConfigWhenPurchaseReferenceIsIncomplete()
    {
        loginAsAdmin();
        FakeFixedAssetMappers mappers = seededMappers();
        mappers.configMapper.configuredAssets.get(1001L).setPurchaseReferenceUrl(null);
        OaFixedAssetServiceImpl service = service(mappers, "2026-03-15T00:00:00Z");
        OaFixedAssetConfig config = new OaFixedAssetConfig();
        config.setShopDeptId(201L);
        config.setOeItemId(1001L);
        config.setAssetQuantity(new BigDecimal("1.00"));
        config.setStatus("0");

        assertThatThrownBy(() -> service.saveConfig(config, 201L))
                .isInstanceOfSatisfying(OaFixedAssetValidationException.class, failure -> {
                    assertThat(failure.getErrorCode()).isEqualTo("FIXED_ASSET_PURCHASE_REFERENCE_INCOMPLETE");
                    assertThat(failure.getMessage()).contains("同款购买链接", "OE管理");
                });
        assertThat(config.getConfigId()).isNull();
    }

    @Test
    @DisplayName("当前可用额度等于已释放月份快照累计减去已上报占用")
    void shouldCalculateAvailableQuotaFromMonthlySnapshotsAndUsedLedger()
    {
        loginAsStoreUser();
        FakeFixedAssetMappers mappers = seededMappers();
        mappers.monthMapper.putMonthlyQuota(201L, 2026, 1, new BigDecimal("83.33"));
        mappers.monthMapper.putMonthlyQuota(201L, 2026, 2, new BigDecimal("83.33"));
        mappers.monthMapper.putMonthlyQuota(201L, 2026, 3, new BigDecimal("66.67"));
        mappers.ledgerMapper.usedAmount = new BigDecimal("100.00");
        OaFixedAssetServiceImpl service = service(mappers, "2026-03-15T00:00:00Z");

        OaFixedAssetQuotaSummary summary = service.getQuotaSummary(201L, 2026, 201L);

        assertThat(summary.getReleasedQuotaAmount()).isEqualByComparingTo("233.33");
        assertThat(summary.getAvailableQuotaAmount()).isEqualByComparingTo("133.33");
    }

    @Test
    @DisplayName("月度额度从固定资产配置生效月释放且最后五天顺延到下月")
    void shouldReleaseQuotaFromAssetEffectiveMonthAndDelayLastFiveDays()
    {
        loginAsStoreUser();
        FakeFixedAssetMappers mappers = seededMappers();
        mappers.quotaMapper.quota.setAnnualRepairRatio(new BigDecimal("100.00"));
        mappers.configMapper.clearConfigs();
        mappers.configMapper.addConfiguredAsset(201L, 1001L, "收银机", new BigDecimal("1.00"),
                new BigDecimal("120.00"), "2026-07-09T00:00:00Z");
        mappers.configMapper.addConfiguredAsset(201L, 1002L, "冰箱", new BigDecimal("1.00"),
                new BigDecimal("240.00"), "2026-07-27T00:00:00Z");
        OaFixedAssetServiceImpl service = service(mappers, "2026-08-15T00:00:00Z");

        OaFixedAssetQuotaSummary summary = service.getQuotaSummary(201L, 2026, 201L);

        assertThat(mappers.monthMapper.monthlyQuota(201L, 2026, 6)).isEqualByComparingTo("0.00");
        assertThat(mappers.monthMapper.monthlyQuota(201L, 2026, 7)).isEqualByComparingTo("10.00");
        assertThat(mappers.monthMapper.monthlyQuota(201L, 2026, 8)).isEqualByComparingTo("30.00");
        assertThat(summary.getReleasedQuotaAmount()).isEqualByComparingTo("40.00");
        assertThat(summary.getAvailableQuotaAmount()).isEqualByComparingTo("40.00");
    }

    @Test
    @DisplayName("批量维修上报按总占用额度校验并生成多条维修记录")
    void shouldSubmitBatchRepairRowsWhenTotalUsageIsInsideAvailableQuota()
    {
        loginAsStoreUser();
        FakeFixedAssetMappers mappers = seededMappers();
        mappers.quotaMapper.quota.setAnnualRepairRatio(new BigDecimal("1000.00"));
        mappers.configMapper.clearConfigs();
        mappers.configMapper.addConfiguredAsset(201L, 1001L, "收银机", new BigDecimal("1.00"),
                new BigDecimal("10.00"), "2026-01-01T00:00:00Z");
        mappers.configMapper.addConfiguredAsset(201L, 1002L, "冰箱", new BigDecimal("2.00"),
                new BigDecimal("20.00"), "2026-01-01T00:00:00Z");
        OaFixedAssetServiceImpl service = service(mappers, "2026-03-15T00:00:00Z");
        OaFixedAssetRepairBatchRequest request = new OaFixedAssetRepairBatchRequest();
        request.setShopDeptId(201L);
        request.setFaultDescription("批量破损");
        OaFixedAssetRepairBatchItem first = batchItem(1001L, new BigDecimal("1.00"));
        OaFixedAssetRepairBatchItem second = batchItem(1002L, new BigDecimal("1.00"));
        request.setItems(Arrays.asList(first, second));

        List<OaFixedAssetRepair> savedRows = service.submitRepairBatch(request, 201L);

        assertThat(savedRows).hasSize(2);
        assertThat(mappers.repairMapper.repairs).hasSize(2);
        assertThat(mappers.repairMapper.repairs.get(0).getEstimatedRepairAmount()).isEqualByComparingTo("10.00");
        assertThat(mappers.repairMapper.repairs.get(1).getEstimatedRepairAmount()).isEqualByComparingTo("20.00");
        assertThat(mappers.ledgerMapper.ledgers).hasSize(2);
        assertThat(mappers.ledgerMapper.ledgers.get(0).getAmount()).isEqualByComparingTo("10.00");
        assertThat(mappers.ledgerMapper.ledgers.get(1).getAmount()).isEqualByComparingTo("20.00");
    }

    @Test
    @DisplayName("异常批准入口下线且不能再生成待确认记录")
    void shouldRejectDisabledExceptionApproval()
    {
        loginAsAdmin();
        FakeFixedAssetMappers mappers = seededMappers();
        mappers.configMapper.assetUnitPrice = new BigDecimal("1000.00");
        OaFixedAssetServiceImpl service = service(mappers, "2026-03-15T00:00:00Z");

        OaFixedAssetRepair request = repair(201L);
        request.setRepairQuantity(new BigDecimal("2.00"));
        assertThatThrownBy(() -> service.approveExceptionRepair(request, 201L))
                .isInstanceOfSatisfying(OaFixedAssetValidationException.class, failure -> {
                    assertThat(failure.getErrorCode()).isEqualTo("FIXED_ASSET_EXCEPTION_APPROVAL_DISABLED");
                    assertThat(failure.getMessage()).contains("异常批准已下线", "自行购买且无需上报");
                });
        assertThat(mappers.repairMapper.repairs).isEmpty();
        assertThat(mappers.ledgerMapper.ledgers).isEmpty();
    }

    @Test
    @DisplayName("任何异常类型都被统一下线")
    void shouldRejectAllExceptionApprovalTypes()
    {
        loginAsAdmin();
        FakeFixedAssetMappers mappers = seededMappers();
        OaFixedAssetServiceImpl service = service(mappers, "2026-03-15T00:00:00Z");

        OaFixedAssetRepair request = repair(201L);
        request.setRepairQuantity(new BigDecimal("1.00"));
        request.setExceptionType("advance_future_months");

        assertThatThrownBy(() -> service.approveExceptionRepair(request, 201L))
                .isInstanceOfSatisfying(OaFixedAssetValidationException.class, failure ->
                    assertThat(failure.getErrorCode()).isEqualTo("FIXED_ASSET_EXCEPTION_APPROVAL_DISABLED"));
        assertThat(mappers.repairMapper.repairs).isEmpty();
        assertThat(mappers.ledgerMapper.ledgers).isEmpty();
    }

    @Test
    @DisplayName("非授权用户不能在无权门店上下文提交维修上报")
    void shouldRejectRepairWhenCurrentStoreIsOutsideUserScope()
    {
        loginAsStoreUser();
        FakeFixedAssetMappers mappers = seededMappers();
        OaFixedAssetServiceImpl service = service(mappers, "2026-03-15T00:00:00Z");

        assertThatThrownBy(() -> service.submitRepair(repair(202L), 202L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("当前用户无权选择该店铺");
        assertThat(mappers.repairMapper.repairs).isEmpty();
    }

    @Test
    @DisplayName("维修上报只能提交当前门店，不能通过请求体切换到其他可见店铺")
    void shouldRejectRepairForAnotherVisibleShopWhenCurrentStoreIsSelected()
    {
        loginAsStoreUser();
        FakeFixedAssetMappers mappers = seededMappers();
        mappers.deptScopeMapper.authorizeShop(202L);
        mappers.configMapper.authorizeConfiguredShop(202L);
        OaFixedAssetServiceImpl service = service(mappers, "2026-03-15T00:00:00Z");

        assertThatThrownBy(() -> service.submitRepair(repair(202L), 201L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("只能上报当前门店");
        assertThat(mappers.repairMapper.repairs).isEmpty();
        assertThat(mappers.ledgerMapper.ledgers).isEmpty();
    }

    @Test
    @DisplayName("维修上报列表只能查询当前门店，不能通过参数切换到其他可见店铺")
    void shouldRejectRepairListForAnotherVisibleShopWhenCurrentStoreIsSelected()
    {
        loginAsStoreUser();
        FakeFixedAssetMappers mappers = seededMappers();
        mappers.deptScopeMapper.authorizeShop(202L);
        OaFixedAssetServiceImpl service = service(mappers, "2026-03-15T00:00:00Z");
        OaFixedAssetRepair query = new OaFixedAssetRepair();
        query.setShopDeptId(202L);

        assertThatThrownBy(() -> service.selectRepairList(query, 201L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("只能查看当前门店");
    }

    @Test
    @DisplayName("用户管理多个店铺时可按目标店铺查询固定资产额度")
    void shouldReadQuotaForAnotherManagedShop()
    {
        loginAsStoreUser();
        FakeFixedAssetMappers mappers = seededMappers();
        mappers.deptScopeMapper.authorizeShop(202L);
        OaFixedAssetServiceImpl service = service(mappers, "2026-03-15T00:00:00Z");

        OaFixedAssetQuotaSummary summary = service.getQuotaSummary(202L, 2026, 201L);

        assertThat(summary.getShopDeptId()).isEqualTo(202L);
    }

    @Test
    @DisplayName("固定资产列表未指定店铺时按用户管理店铺集合过滤")
    void shouldScopeConfigListToManagedShops()
    {
        loginAsStoreUser();
        FakeFixedAssetMappers mappers = seededMappers();
        mappers.deptScopeMapper.authorizeShop(202L);
        OaFixedAssetServiceImpl service = service(mappers, "2026-03-15T00:00:00Z");

        service.selectConfigList(new OaFixedAssetConfig(), 201L);
        service.selectConfigStoreList(new OaFixedAssetConfig(), 201L);

        assertThat(mappers.configMapper.lastListQuery.getParams().get("scopeDeptIds"))
                .isEqualTo(Arrays.asList(201L, 202L));
    }

    @Test
    void storePaginationIsNotConsumedByPermissionLookups()
    {
        loginAsStoreUser();
        FakeFixedAssetMappers mappers = seededMappers();
        OaFixedAssetServiceImpl service = service(mappers, "2026-03-15T00:00:00Z");
        com.github.pagehelper.Page<Object> page = com.github.pagehelper.PageHelper.startPage(2, 1);
        try {
            service.selectConfigStoreList(new OaFixedAssetConfig(), 201L);
            assertThat(com.github.pagehelper.PageHelper.getLocalPage()).isSameAs(page);
            assertThat(mappers.configMapper.lastListQuery.getParams().get("scopeDeptIds")).isNotNull();
        } finally { com.github.pagehelper.PageHelper.clearPage(); }
    }

    @Test
    @DisplayName("未配置到当前店铺的OE器皿不能提交维修上报")
    void shouldRejectRepairForUnconfiguredOeItem()
    {
        loginAsStoreUser();
        FakeFixedAssetMappers mappers = seededMappers();
        mappers.configMapper.clearConfigs();
        mappers.configMapper.addConfiguredAsset(201L, 2002L, "冰箱", new BigDecimal("1.00"),
                new BigDecimal("1000.00"), "2026-01-01T00:00:00Z");
        OaFixedAssetServiceImpl service = service(mappers, "2026-03-15T00:00:00Z");

        assertThatThrownBy(() -> service.submitRepair(repair(201L), 201L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("请选择本店已配置的OE固定资产");
    }

    private OaFixedAssetServiceImpl service(FakeFixedAssetMappers mappers, String instant)
    {
        OaFixedAssetServiceImpl service = new OaFixedAssetServiceImpl();
        OaShopScopeService shopScopeService = new OaShopScopeService();
        ReflectionTestUtils.setField(shopScopeService, "deptScopeMapper", mappers.deptScopeMapper);
        ReflectionTestUtils.setField(service, "configMapper", mappers.configMapper);
        var commands = org.mockito.Mockito.mock(com.erp.oa.mapper.OaFixedAssetConfigCommandMapper.class);
        org.mockito.Mockito.when(commands.lockScope(org.mockito.ArgumentMatchers.anyLong())).thenReturn(0L);
        org.mockito.Mockito.when(commands.advanceVersion(org.mockito.ArgumentMatchers.anyLong(),org.mockito.ArgumentMatchers.anyLong())).thenReturn(1);
        ReflectionTestUtils.setField(service, "configCommandMapper", commands);
        ReflectionTestUtils.setField(service, "quotaMapper", mappers.quotaMapper);
        ReflectionTestUtils.setField(service, "repairMapper", mappers.repairMapper);
        ReflectionTestUtils.setField(service, "ledgerMapper", mappers.ledgerMapper);
        ReflectionTestUtils.setField(service, "monthMapper", mappers.monthMapper);
        ReflectionTestUtils.setField(service, "deptScopeMapper", mappers.deptScopeMapper);
        ReflectionTestUtils.setField(service, "shopScopeService", shopScopeService);
        ReflectionTestUtils.setField(service, "clock", Clock.fixed(Instant.parse(instant), ZoneId.of("UTC")));
        return service;
    }

    private FakeFixedAssetMappers seededMappers()
    {
        FakeFixedAssetMappers mappers = new FakeFixedAssetMappers();
        OaFixedAssetQuota quota = new OaFixedAssetQuota();
        quota.setQuotaId(1L);
        quota.setShopDeptId(201L);
        quota.setQuotaYear(2026);
        quota.setAnnualRepairRatio(new BigDecimal("20.00"));
        quota.setAssetTotalAmount(new BigDecimal("120000.00"));
        quota.setAnnualQuotaAmount(new BigDecimal("24000.00"));
        quota.setMonthlyQuotaAmount(new BigDecimal("2000.00"));
        mappers.quotaMapper.quota = quota;
        return mappers;
    }

    private OaFixedAssetRepair repair(Long shopDeptId)
    {
        OaFixedAssetRepair repair = new OaFixedAssetRepair();
        repair.setShopDeptId(shopDeptId);
        repair.setOeItemId(1001L);
        repair.setOeItemName("收银机");
        repair.setFaultDescription("无法开机");
        return repair;
    }

    private OaFixedAssetRepairBatchItem batchItem(Long oeItemId, BigDecimal repairQuantity)
    {
        OaFixedAssetRepairBatchItem item = new OaFixedAssetRepairBatchItem();
        item.setOeItemId(oeItemId);
        item.setRepairQuantity(repairQuantity);
        return item;
    }

    private void loginAsStoreUser()
    {
        SecurityContextHolder.setUserId("9");
        SecurityContextHolder.setUserName("storeUser");
    }

    private void loginAsAdmin()
    {
        SecurityContextHolder.setUserId("1");
        SecurityContextHolder.setUserName("admin");
    }

    private static class FakeFixedAssetMappers
    {
        private final FakeConfigMapper configMapper = new FakeConfigMapper();
        private final FakeQuotaMapper quotaMapper = new FakeQuotaMapper();
        private final FakeRepairMapper repairMapper = new FakeRepairMapper();
        private final FakeLedgerMapper ledgerMapper = new FakeLedgerMapper();
        private final FakeMonthMapper monthMapper = new FakeMonthMapper();
        private final FakeDeptScopeMapper deptScopeMapper = new FakeDeptScopeMapper();
    }

    private static class FakeConfigMapper implements OaFixedAssetConfigMapper
    {
        private OaFixedAssetConfig lastListQuery;
        private final List<Long> configuredShopDeptIds = new ArrayList<>(Collections.singletonList(201L));
        private final Map<Long, OaFixedAssetConfig> configuredAssets = new HashMap<>();
        private BigDecimal assetUnitPrice = new BigDecimal("1000.00");
        private BigDecimal assetQuantity = new BigDecimal("120.00");

        private FakeConfigMapper()
        {
            addConfiguredAsset(201L, 1001L, "收银机", assetQuantity, assetUnitPrice, "2026-01-01T00:00:00Z");
        }

        private void authorizeConfiguredShop(Long shopDeptId)
        {
            if (!configuredShopDeptIds.contains(shopDeptId))
            {
                configuredShopDeptIds.add(shopDeptId);
            }
        }

        private void clearConfigs()
        {
            configuredAssets.clear();
        }

        private void addConfiguredAsset(Long shopDeptId, Long oeItemId, String oeItemName, BigDecimal quantity,
                BigDecimal unitPrice, String createTime)
        {
            OaFixedAssetConfig config = new OaFixedAssetConfig();
            config.setShopDeptId(shopDeptId);
            config.setOeItemId(oeItemId);
            config.setOeItemCode("OE-" + oeItemId);
            config.setOeItemName(oeItemName);
            config.setItemDescription("标准规格");
            config.setOrderUnit("个");
            config.setImageUrl("https://cdn.example.com/oe/" + oeItemId + ".jpg");
            config.setPurchaseReferenceUrl("https://shop.example.com/item/" + oeItemId);
            config.setPurchaseReferenceNote("按图片、规格和型号购买");
            config.setAssetQuantity(quantity);
            config.setAssetUnitPrice(unitPrice);
            config.setAssetAmount(unitPrice.multiply(quantity).setScale(2));
            config.setStatus("0");
            config.setCreateTime(Date.from(Instant.parse(createTime)));
            configuredAssets.put(oeItemId, config);
            authorizeConfiguredShop(shopDeptId);
        }

        @Override
        public List<com.erp.oa.domain.vo.OaFixedAssetStoreSummary> selectConfigStoreList(OaFixedAssetConfig config)
        {
            lastListQuery = config;
            return java.util.Collections.emptyList();
        }

        @Override
        public List<OaFixedAssetConfig> selectConfigList(OaFixedAssetConfig config)
        {
            lastListQuery = config;
            if (config == null || config.getShopDeptId() == null)
            {
                return new ArrayList<>(configuredAssets.values());
            }
            List<OaFixedAssetConfig> rows = new ArrayList<>();
            for (OaFixedAssetConfig item : configuredAssets.values())
            {
                if (config.getShopDeptId().equals(item.getShopDeptId()))
                {
                    rows.add(item);
                }
            }
            return rows;
        }

        @Override
        public OaFixedAssetConfig selectConfigById(Long configId)
        {
            return null;
        }

        @Override
        public BigDecimal sumAssetAmountByShop(Long shopDeptId)
        {
            return configuredAssets.values().stream()
                    .filter(item -> shopDeptId.equals(item.getShopDeptId()))
                    .map(OaFixedAssetConfig::getAssetAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        @Override
        public OaFixedAssetConfig selectOeItemSnapshotForUpdate(Long oeItemId) { return selectOeItemSnapshot(oeItemId); }

        @Override
        public OaFixedAssetConfig selectOeItemSnapshot(Long oeItemId)
        {
            if (!Long.valueOf(1001L).equals(oeItemId) && !configuredAssets.containsKey(oeItemId))
            {
                return null;
            }
            OaFixedAssetConfig config = new OaFixedAssetConfig();
            config.setOeItemId(oeItemId);
            config.setOeItemCode("OE-001");
            OaFixedAssetConfig existing = configuredAssets.get(oeItemId);
            config.setOeItemName(existing == null ? "收银机" : existing.getOeItemName());
            config.setItemDescription(existing == null ? "标准规格" : existing.getItemDescription());
            config.setOrderUnit(existing == null ? "个" : existing.getOrderUnit());
            config.setImageUrl(existing == null ? "https://cdn.example.com/oe/default.jpg" : existing.getImageUrl());
            config.setPurchaseReferenceUrl(existing == null
                    ? "https://shop.example.com/item/default" : existing.getPurchaseReferenceUrl());
            config.setPurchaseReferenceNote(existing == null
                    ? "按图片、规格和型号购买" : existing.getPurchaseReferenceNote());
            config.setAssetUnitPrice(existing == null ? assetUnitPrice : existing.getAssetUnitPrice());
            return config;
        }

        @Override
        public OaFixedAssetConfig selectActiveConfigByShopAndOeItem(Long shopDeptId, Long oeItemId)
        {
            OaFixedAssetConfig existing = configuredAssets.get(oeItemId);
            if (!configuredShopDeptIds.contains(shopDeptId)
                    || existing == null
                    || !shopDeptId.equals(existing.getShopDeptId()))
            {
                return null;
            }
            OaFixedAssetConfig config = new OaFixedAssetConfig();
            config.setShopDeptId(shopDeptId);
            config.setOeItemId(oeItemId);
            config.setOeItemCode(existing.getOeItemCode());
            config.setOeItemName(existing.getOeItemName());
            config.setItemDescription(existing.getItemDescription());
            config.setOrderUnit(existing.getOrderUnit());
            config.setImageUrl(existing.getImageUrl());
            config.setPurchaseReferenceUrl(existing.getPurchaseReferenceUrl());
            config.setPurchaseReferenceNote(existing.getPurchaseReferenceNote());
            config.setAssetQuantity(existing.getAssetQuantity());
            config.setAssetUnitPrice(existing.getAssetUnitPrice());
            config.setAssetAmount(existing.getAssetAmount());
            config.setCreateTime(existing.getCreateTime());
            return config;
        }

        @Override
        public int insertConfig(OaFixedAssetConfig config)
        {
            if (config.getAssetQuantity() != null)
            {
                assetQuantity = config.getAssetQuantity();
            }
            if (config.getAssetUnitPrice() != null)
            {
                assetUnitPrice = config.getAssetUnitPrice();
            }
            config.setConfigId(10L);
            addConfiguredAsset(config.getShopDeptId(), config.getOeItemId(), "收银机",
                    assetQuantity, assetUnitPrice, "2026-01-01T00:00:00Z");
            return 1;
        }

        @Override
        public int updateConfig(OaFixedAssetConfig config)
        {
            if (config.getAssetQuantity() != null)
            {
                assetQuantity = config.getAssetQuantity();
            }
            if (config.getAssetUnitPrice() != null)
            {
                assetUnitPrice = config.getAssetUnitPrice();
            }
            return 1;
        }

        @Override
        public int deleteConfigById(Long configId)
        {
            return 1;
        }
    }

    private static class FakeQuotaMapper implements OaFixedAssetQuotaMapper
    {
        private OaFixedAssetQuota quota;

        @Override
        public OaFixedAssetQuota selectQuota(Long shopDeptId, Integer quotaYear)
        {
            return quota;
        }

        @Override
        public OaFixedAssetQuota selectQuotaForUpdate(Long shopDeptId, Integer quotaYear)
        {
            return quota;
        }

        @Override
        public int insertQuotaIfAbsent(Long shopDeptId, Integer quotaYear,
                BigDecimal annualRepairRatio, String createBy)
        {
            if (quota != null)
            {
                return 0;
            }
            quota = new OaFixedAssetQuota();
            quota.setQuotaId(1L);
            quota.setShopDeptId(shopDeptId);
            quota.setQuotaYear(quotaYear);
            quota.setAnnualRepairRatio(annualRepairRatio);
            return 1;
        }

        @Override
        public int insertQuota(OaFixedAssetQuota quota)
        {
            this.quota = quota;
            return 1;
        }

        @Override
        public int updateQuota(OaFixedAssetQuota quota)
        {
            this.quota = quota;
            return 1;
        }
    }

    private static class FakeRepairMapper implements OaFixedAssetRepairMapper
    {
        private final List<OaFixedAssetRepair> repairs = new ArrayList<>();

        @Override
        public List<OaFixedAssetRepair> selectRepairList(OaFixedAssetRepair repair)
        {
            return repairs;
        }

        @Override
        public OaFixedAssetRepair selectRepairById(Long repairId)
        {
            return repairs.stream().filter(item -> repairId.equals(item.getRepairId())).findFirst().orElse(null);
        }

        @Override
        public int insertRepair(OaFixedAssetRepair repair)
        {
            repair.setRepairId((long) repairs.size() + 1);
            repairs.add(repair);
            return 1;
        }

        @Override
        public int updateRepair(OaFixedAssetRepair repair)
        {
            return 1;
        }
    }

    private static class FakeLedgerMapper implements OaFixedAssetQuotaLedgerMapper
    {
        private BigDecimal usedAmount = BigDecimal.ZERO;
        private final List<OaFixedAssetQuotaLedger> ledgers = new ArrayList<>();

        @Override
        public BigDecimal sumUsedQuotaAmount(Long shopDeptId, Integer quotaYear)
        {
            return usedAmount;
        }

        @Override
        public int insertLedger(OaFixedAssetQuotaLedger ledger)
        {
            ledgers.add(ledger);
            return 1;
        }
    }

    private static class FakeMonthMapper implements OaFixedAssetQuotaMonthMapper
    {
        private final Map<String, OaFixedAssetQuotaMonth> monthQuotas = new HashMap<>();

        private void putMonthlyQuota(Long shopDeptId, Integer quotaYear, Integer quotaMonth, BigDecimal amount)
        {
            OaFixedAssetQuotaMonth monthQuota = new OaFixedAssetQuotaMonth();
            monthQuota.setShopDeptId(shopDeptId);
            monthQuota.setQuotaYear(quotaYear);
            monthQuota.setQuotaMonth(quotaMonth);
            monthQuota.setAnnualRepairRatio(new BigDecimal("20.00"));
            monthQuota.setAssetTotalAmount(new BigDecimal("5000.00"));
            monthQuota.setMonthlyQuotaAmount(amount);
            monthQuotas.put(key(shopDeptId, quotaYear, quotaMonth), monthQuota);
        }

        private BigDecimal monthlyQuota(Long shopDeptId, Integer quotaYear, Integer quotaMonth)
        {
            OaFixedAssetQuotaMonth monthQuota = selectMonthQuota(shopDeptId, quotaYear, quotaMonth);
            return monthQuota == null ? null : monthQuota.getMonthlyQuotaAmount();
        }

        @Override
        public OaFixedAssetQuotaMonth selectMonthQuota(Long shopDeptId, Integer quotaYear, Integer quotaMonth)
        {
            return monthQuotas.get(key(shopDeptId, quotaYear, quotaMonth));
        }

        @Override
        public BigDecimal sumReleasedQuotaAmount(Long shopDeptId, Integer quotaYear, Integer throughMonth)
        {
            return monthQuotas.values().stream()
                    .filter(item -> shopDeptId.equals(item.getShopDeptId()))
                    .filter(item -> quotaYear.equals(item.getQuotaYear()))
                    .filter(item -> item.getQuotaMonth() <= throughMonth)
                    .map(OaFixedAssetQuotaMonth::getMonthlyQuotaAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        @Override
        public int insertMonthQuota(OaFixedAssetQuotaMonth monthQuota)
        {
            monthQuotas.put(key(monthQuota.getShopDeptId(), monthQuota.getQuotaYear(), monthQuota.getQuotaMonth()), monthQuota);
            return 1;
        }

        @Override
        public int updateMonthQuota(OaFixedAssetQuotaMonth monthQuota)
        {
            monthQuotas.put(key(monthQuota.getShopDeptId(), monthQuota.getQuotaYear(), monthQuota.getQuotaMonth()), monthQuota);
            return 1;
        }

        private String key(Long shopDeptId, Integer quotaYear, Integer quotaMonth)
        {
            return shopDeptId + ":" + quotaYear + ":" + quotaMonth;
        }
    }

    private static class FakeDeptScopeMapper implements OaDeptScopeMapper
    {
        private final List<Long> authorizedShopDeptIds = new ArrayList<>(Collections.singletonList(201L));

        private void authorizeShop(Long deptId)
        {
            if (!authorizedShopDeptIds.contains(deptId))
            {
                authorizedShopDeptIds.add(deptId);
            }
        }

        @Override
        public List<Long> selectSubDeptIds(Long deptId)
        {
            return Collections.singletonList(deptId);
        }

        @Override
        public List<Long> selectUserShopDeptIds(Long userId)
        {
            assertThat(com.github.pagehelper.PageHelper.getLocalPage()).isNull();
            return Long.valueOf(9L).equals(userId) ? authorizedShopDeptIds : Collections.emptyList();
        }

        @Override
        public List<Long> selectUserAuthorizedOaDeptIds(Long userId)
        {
            return selectUserShopDeptIds(userId);
        }

        @Override
        public List<Long> selectAllActiveOaDeptIds()
        {
            return authorizedShopDeptIds;
        }

        @Override
        public String selectDeptName(Long deptId)
        {
            return "测试门店";
        }

        @Override
        public com.erp.oa.domain.vo.OaLegalEntityCandidate selectLegalEntityCandidate(Long deptId)
        {
            return null;
        }

        @Override
        public List<com.erp.system.api.domain.SysLegalEntity> selectActiveLegalEntities()
        {
            return Collections.emptyList();
        }

        @Override
        public com.erp.system.api.domain.SysLegalEntity selectActiveLegalEntityById(Long legalEntityId)
        {
            return null;
        }

        @Override
        public int countDeptInScope(Long scopeDeptId, Long targetDeptId)
        {
            return scopeDeptId != null && scopeDeptId.equals(targetDeptId) ? 1 : 0;
        }

        @Override
        public int countUserShopScope(Long userId, Long deptId)
        {
            return Long.valueOf(9L).equals(userId) && authorizedShopDeptIds.contains(deptId) ? 1 : 0;
        }

        @Override
        public int countActiveStoreDept(Long deptId)
        {
            return authorizedShopDeptIds.contains(deptId) ? 1 : 0;
        }
    }
}
