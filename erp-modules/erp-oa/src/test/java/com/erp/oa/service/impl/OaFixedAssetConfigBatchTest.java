package com.erp.oa.service.impl;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import com.erp.common.security.utils.SecurityUtils;
import com.erp.common.security.auth.AuthUtil;
import com.erp.common.security.shop.ShopScopeService;
import com.erp.oa.domain.*;
import com.erp.oa.domain.dto.OaFixedAssetConfigBatchRequest;
import com.erp.oa.mapper.*;
class OaFixedAssetConfigBatchTest {
    final OaFixedAssetConfigMapper rows=mock(OaFixedAssetConfigMapper.class);
    final OaFixedAssetConfigCommandMapper commands=mock(OaFixedAssetConfigCommandMapper.class);
    final OaFixedAssetQuotaMapper quotas=mock(OaFixedAssetQuotaMapper.class);
    final OaDeptScopeMapper scope=mock(OaDeptScopeMapper.class);
    final Map<Long,OaFixedAssetConfig> state=new LinkedHashMap<>();
    final Map<String,OaFixedAssetConfigCommand> receipts=new HashMap<>();
    long version=0, nextId=100;
    OaFixedAssetServiceImpl service;
    @BeforeEach void setup() {
        service=new OaFixedAssetServiceImpl(); ReflectionTestUtils.setField(service,"configMapper",rows); ReflectionTestUtils.setField(service,"configCommandMapper",commands); ReflectionTestUtils.setField(service,"quotaMapper",quotas); ReflectionTestUtils.setField(service,"monthMapper",mock(OaFixedAssetQuotaMonthMapper.class)); ReflectionTestUtils.setField(service,"ledgerMapper",mock(OaFixedAssetQuotaLedgerMapper.class)); ReflectionTestUtils.setField(service,"shopScopeService",mock(ShopScopeService.class)); ReflectionTestUtils.setField(service,"deptScopeMapper",scope);
        when(scope.countUserShopScope(anyLong(),anyLong())).thenReturn(1);
        when(commands.lockScope(10L)).thenAnswer(c->version); when(commands.selectVersion(10L)).thenAnswer(c->version);
        when(commands.advanceVersion(eq(10L),anyLong())).thenAnswer(c->{ if (version!=c.<Long>getArgument(1)) return 0; version++;return 1; });
        when(commands.claimCommand(eq(10L),anyLong(),anyString(),anyString())).thenAnswer(c->{receipts.computeIfAbsent(c.getArgument(1)+":"+c.getArgument(2),k->{var r=new OaFixedAssetConfigCommand();r.setPayloadHash(c.getArgument(3));return r;});return 1;});
        when(commands.lockCommand(eq(10L),anyLong(),anyString())).thenAnswer(c->receipts.get(c.getArgument(1)+":"+c.getArgument(2)));
        when(commands.selectCommand(eq(10L),anyLong(),anyString())).thenAnswer(c->receipts.get(c.getArgument(1)+":"+c.getArgument(2)));
        when(commands.completeCommand(eq(10L),anyLong(),anyString(),anyString(),anyString())).thenAnswer(c->{receipts.get(c.getArgument(1)+":"+c.getArgument(2)).setResultJson(c.getArgument(4));return 1;});
        when(commands.lockConfigRows(10L)).thenAnswer(c->new ArrayList<>(state.values())); when(rows.selectConfigList(any())).thenAnswer(c->new ArrayList<>(state.values())); when(rows.selectConfigById(anyLong())).thenAnswer(c->state.get(c.getArgument(0)));
        when(rows.insertConfig(any())).thenAnswer(c->{OaFixedAssetConfig r=c.getArgument(0);r.setConfigId(++nextId);state.put(r.getConfigId(),r);return 1;}); when(rows.updateConfig(any())).thenAnswer(c->{OaFixedAssetConfig r=c.getArgument(0);state.put(r.getConfigId(),r);return 1;}); when(rows.deleteConfigById(anyLong())).thenAnswer(c->state.remove(c.getArgument(0))!=null?1:0);
        when(rows.sumAssetAmountByShop(anyLong())).thenAnswer(c->state.values().stream().map(OaFixedAssetConfig::getAssetAmount).reduce(BigDecimal.ZERO,BigDecimal::add));
        when(rows.selectOeItemSnapshotForUpdate(anyLong())).thenAnswer(c->{var r=row(c.getArgument(0),null);r.setOeItemCode("OE");r.setOeItemName("设备");r.setItemDescription("规格");r.setOrderUnit("个");r.setImageUrl("https://example.com/img.png");r.setPurchaseReferenceUrl("https://example.com/item");r.setPurchaseReferenceNote("同款说明");return r;});
        var quota=new OaFixedAssetQuota();quota.setQuotaId(1L);quota.setAnnualRepairRatio(new BigDecimal("20"));when(quotas.selectQuota(anyLong(),anyInt())).thenReturn(quota);
    }
    OaFixedAssetConfig row(Long oe,Long id){var r=new OaFixedAssetConfig();r.setConfigId(id);r.setShopDeptId(10L);r.setOeItemId(oe);r.setAssetQuantity(new BigDecimal("2.00"));r.setAssetUnitPrice(new BigDecimal("3.00"));r.setAssetAmount(new BigDecimal("6.00"));r.setStatus("0");return r;}
    OaFixedAssetConfigBatchRequest request(String id,OaFixedAssetConfig...items){var r=new OaFixedAssetConfigBatchRequest();r.setRequestId(id);r.setShopDeptId(10L);r.setExpectedVersion(version);r.setAnnualRepairRatio(new BigDecimal("20.00"));r.setRows(Arrays.asList(items));return r;}
    org.mockito.MockedStatic<SecurityUtils> login(boolean admin){var s=mockStatic(SecurityUtils.class);s.when(SecurityUtils::isAdmin).thenReturn(admin);s.when(SecurityUtils::getUserId).thenReturn(7L);s.when(SecurityUtils::getUsername).thenReturn("tester");return s;}
    @Test void secondInvalidRowPreventsAllConfigMutationsAndQuotaRebuild() {try(var s=login(true)){state.put(1L,row(11L,1L));var a=row(12L,null);var b=row(13L,null);b.setAssetQuantity(BigDecimal.ZERO);assertThatThrownBy(()->service.saveConfigBatch(request("invalid",a,b),10L)).hasMessageContaining("第2行数量");assertThat(state.keySet()).containsExactly(1L);verify(rows,never()).deleteConfigById(any());verify(rows,never()).insertConfig(any());verify(quotas,never()).updateQuota(any());}}
    @Test void overflowingCalculatedAmountIsRejectedBeforeAnyWrite() {try(var s=login(true)){var r=row(11L,null);r.setAssetQuantity(new BigDecimal("99999999999999"));r.setAssetUnitPrice(new BigDecimal("99"));assertThatThrownBy(()->service.saveConfigBatch(request("overflow",r),10L)).hasMessageContaining("金额超出");verify(rows,never()).insertConfig(any());verify(quotas,never()).updateQuota(any());}}
    @Test void sameCommandReturnsOriginalIdsAndDoesNotRebuildOrIncrementAgain() {try(var s=login(true)){var r=request("one",row(11L,null),row(12L,null));var first=service.saveConfigBatch(r,10L);assertThat(first.getRows()).hasSize(2);assertThat(first.getRows()).allMatch(x->x.getConfigId()!=null);assertThat(first.getVersion()).isEqualTo("1");var repeat=service.saveConfigBatch(r,10L);assertThat(repeat.getRows()).extracting(OaFixedAssetConfig::getConfigId).containsExactlyElementsOf(first.getRows().stream().map(OaFixedAssetConfig::getConfigId).toList());assertThat(version).isEqualTo(1);verify(quotas,times(1)).updateQuota(any());assertThat(service.selectConfigCommand("one",10L,10L).getRequestId()).isEqualTo("one");}}
    @Test void changedPayloadAndStaleVersionAreRejectedWithoutReplacingOriginalReceipt() {try(var s=login(true)){var r=request("one",row(11L,null));service.saveConfigBatch(r,10L);r.getRows().get(0).setAssetQuantity(new BigDecimal("4"));assertThatThrownBy(()->service.saveConfigBatch(r,10L)).hasMessageContaining("不同的配置");var old=request("other",row(12L,null));old.setExpectedVersion(0L);assertThatThrownBy(()->service.saveConfigBatch(old,10L)).hasMessageContaining("其他操作修改");assertThat(state).hasSize(1);assertThat(version).isEqualTo(1);}}
    @Test void originalReceiptIsMarkedHistoricalAfterAnotherVersionAndOldIndividualWriteSharesVersionGate() {try(var s=login(true)){var r=request("one",row(11L,null));var first=service.saveConfigBatch(r,10L);var next=request("two",first.getRows().get(0));service.saveConfigBatch(next,10L);var replay=service.saveConfigBatch(r,10L);assertThat(replay.getVersion()).isEqualTo("1");assertThat(replay.getCurrentVersion()).isEqualTo("2");var old=row(12L,null);old.setExpectedVersion(0L);assertThatThrownBy(()->service.saveConfig(old,10L)).hasMessageContaining("其他操作修改");assertThatThrownBy(()->service.deleteConfigById(101L,10L,0L)).hasMessageContaining("其他操作修改");assertThat(state).hasSize(1);}}
    @Test void missingDeletePermissionAndForeignConfigCannotPartiallySave() {try(var s=login(false);var auth=mockStatic(AuthUtil.class)){auth.when(()->AuthUtil.hasPermi("oa:fixedAsset:config:edit")).thenReturn(true);state.put(1L,row(11L,1L));assertThatThrownBy(()->service.saveConfigBatch(request("no-delete",row(12L,null)),10L)).hasMessageContaining("config:delete");assertThatThrownBy(()->service.saveConfigBatch(request("foreign",row(12L,999L)),10L)).hasMessageContaining("不属于当前店铺");verify(rows,never()).deleteConfigById(any());verify(rows,never()).insertConfig(any());}}
}
