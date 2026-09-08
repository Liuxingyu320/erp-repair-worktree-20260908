package com.erp.inventory.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import com.erp.common.core.context.SecurityContextHolder;
import com.erp.common.core.exception.ServiceException;
import com.erp.inventory.domain.InvOeCategory;
import com.erp.inventory.domain.InvOeItem;
import com.erp.inventory.mapper.InvDeptScopeMapper;
import com.erp.inventory.mapper.InvOeCategoryMapper;
import com.erp.inventory.mapper.InvOeMapper;
import com.erp.inventory.mapper.InvSupplierMapper;

@DisplayName("OE同款购买参考服务")
class InvOeServiceImplTest
{
    private static final Long WAREHOUSE_ID = 900L;

    private InvOeServiceImpl service;
    private InvOeMapper oeMapper;
    private InvOeCategoryMapper categoryMapper;

    @BeforeEach
    void setUp()
    {
        SecurityContextHolder.setUserId("1");
        SecurityContextHolder.setUserName("admin");
        service = new InvOeServiceImpl();
        oeMapper = mock(InvOeMapper.class);
        categoryMapper = mock(InvOeCategoryMapper.class);
        InvSupplierMapper supplierMapper = mock(InvSupplierMapper.class);
        InvDeptScopeMapper deptScopeMapper = mock(InvDeptScopeMapper.class);
        ReflectionTestUtils.setField(service, "oeMapper", oeMapper);
        ReflectionTestUtils.setField(service, "categoryMapper", categoryMapper);
        ReflectionTestUtils.setField(service, "supplierMapper", supplierMapper);
        ReflectionTestUtils.setField(service, "deptScopeMapper", deptScopeMapper);
        ReflectionTestUtils.setField(service, "purchaseReferenceAllowedHosts", "shop.example.com");
        when(deptScopeMapper.selectDeptTypeById(WAREHOUSE_ID)).thenReturn("WAREHOUSE");
        when(categoryMapper.selectInvOeCategoryById(10L)).thenReturn(activeCategory());
        when(oeMapper.countOeCode(any(), any())).thenReturn(0);
    }

    @AfterEach
    void tearDown()
    {
        SecurityContextHolder.remove();
    }

    @Test
    @DisplayName("普通编辑未触碰同款资料时不重写审计且不受空白名单影响")
    void shouldPreserveReferenceAuditWhenReferenceWasNotTouched()
    {
        InvOeItem db = completeOe();
        when(oeMapper.selectInvOeById(1L)).thenReturn(db);
        when(oeMapper.countActiveFixedAssetConfigByOeItemId(1L)).thenReturn(1);
        ReflectionTestUtils.setField(service, "purchaseReferenceAllowedHosts", "");
        InvOeItem request = updateRequest(db);
        request.setPurchaseReferenceTouched(Boolean.FALSE);

        service.saveOe(request, WAREHOUSE_ID);

        ArgumentCaptor<InvOeItem> captor = ArgumentCaptor.forClass(InvOeItem.class);
        verify(oeMapper).updateInvOe(captor.capture());
        assertThat(captor.getValue().getPurchaseReferenceTouched()).isFalse();
        assertThat(captor.getValue().getPurchaseReferenceUpdatedBy()).isNull();
        assertThat(captor.getValue().getPurchaseReferenceUpdatedTime()).isNull();
    }

    @Test
    @DisplayName("启用固定资产引用的OE不能主动清空同款资料")
    void shouldRejectClearingReferenceUsedByActiveFixedAsset()
    {
        InvOeItem db = completeOe();
        when(oeMapper.selectInvOeById(1L)).thenReturn(db);
        when(oeMapper.countActiveFixedAssetConfigByOeItemId(1L)).thenReturn(1);
        InvOeItem request = updateRequest(db);
        request.setPurchaseReferenceTouched(Boolean.TRUE);
        request.setPurchaseReferenceUrl(" ");
        request.setPurchaseReferenceNote("");

        assertThatThrownBy(() -> service.saveOe(request, WAREHOUSE_ID))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("同款购买链接")
                .hasMessageContaining("购买说明")
                .hasMessageContaining("先停用对应固定资产配置");
        verify(oeMapper, never()).updateInvOe(any());
    }

    @Test
    @DisplayName("未被启用固定资产引用时允许明确清空并记录维护人")
    void shouldExplicitlyClearInactiveReferenceAndAuditOperator()
    {
        InvOeItem db = completeOe();
        when(oeMapper.selectInvOeById(1L)).thenReturn(db);
        when(oeMapper.countActiveFixedAssetConfigByOeItemId(1L)).thenReturn(0);
        InvOeItem request = updateRequest(db);
        request.setPurchaseReferenceTouched(Boolean.TRUE);
        request.setPurchaseReferenceUrl("");
        request.setPurchaseReferenceNote(" ");

        service.saveOe(request, WAREHOUSE_ID);

        ArgumentCaptor<InvOeItem> captor = ArgumentCaptor.forClass(InvOeItem.class);
        verify(oeMapper).updateInvOe(captor.capture());
        InvOeItem saved = captor.getValue();
        assertThat(saved.getPurchaseReferenceTouched()).isTrue();
        assertThat(saved.getPurchaseReferenceUrl()).isNull();
        assertThat(saved.getPurchaseReferenceNote()).isNull();
        assertThat(saved.getPurchaseReferenceUpdatedBy()).isEqualTo("admin");
        assertThat(saved.getPurchaseReferenceUpdatedTime()).isNotNull();
    }

    @Test
    @DisplayName("同款链接必须属于配置的可信域名")
    void shouldRejectUntrustedPurchaseReferenceHost()
    {
        InvOeItem db = completeOe();
        when(oeMapper.selectInvOeById(1L)).thenReturn(db);
        InvOeItem request = updateRequest(db);
        request.setPurchaseReferenceTouched(Boolean.TRUE);
        request.setPurchaseReferenceUrl("https://untrusted.example/item/1");

        assertThatThrownBy(() -> service.saveOe(request, WAREHOUSE_ID))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("域名不在可信白名单");
        verify(oeMapper, never()).updateInvOe(any());
    }

    @Test
    @DisplayName("维护策略返回去重后的可信域名")
    void shouldExposeConfiguredPurchaseReferencePolicy()
    {
        ReflectionTestUtils.setField(service, "purchaseReferenceAllowedHosts",
                "SHOP.EXAMPLE.COM, shop.example.com, procurement.example.com");

        assertThat(service.getPurchaseReferencePolicy().isConfigured()).isTrue();
        assertThat(service.getPurchaseReferencePolicy().getAllowedHosts())
                .containsExactly("shop.example.com", "procurement.example.com");
    }

    private InvOeCategory activeCategory()
    {
        InvOeCategory category = new InvOeCategory();
        category.setCategoryId(10L);
        category.setCategoryCode("TEAWARE");
        category.setCategoryName("茶器");
        category.setStatus("0");
        return category;
    }

    private InvOeItem completeOe()
    {
        InvOeItem item = new InvOeItem();
        item.setOeItemId(1L);
        item.setOeItemCode("OE-TEAWARE-001");
        item.setCategoryId(10L);
        item.setOeTypeName("盖碗");
        item.setOeItemName("白瓷盖碗");
        item.setItemDescription("100ml 白瓷");
        item.setOrderUnit("个");
        item.setImageUrl("https://cdn.example.com/oe/1.jpg");
        item.setPurchaseReferenceUrl("https://shop.example.com/item/1");
        item.setPurchaseReferenceNote("按100ml白瓷规格购买");
        item.setStatus("0");
        return item;
    }

    private InvOeItem updateRequest(InvOeItem db)
    {
        InvOeItem item = new InvOeItem();
        item.setOeItemId(db.getOeItemId());
        item.setOeItemCode(db.getOeItemCode());
        item.setCategoryId(db.getCategoryId());
        item.setOeTypeName(db.getOeTypeName());
        item.setOeItemName(db.getOeItemName());
        item.setItemDescription(db.getItemDescription());
        item.setOrderUnit(db.getOrderUnit());
        item.setImageUrl(db.getImageUrl());
        item.setPurchaseReferenceUrl(db.getPurchaseReferenceUrl());
        item.setPurchaseReferenceNote(db.getPurchaseReferenceNote());
        item.setStatus(db.getStatus());
        return item;
    }
}
