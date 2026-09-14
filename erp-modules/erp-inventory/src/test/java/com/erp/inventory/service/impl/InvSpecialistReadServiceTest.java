package com.erp.inventory.service.impl;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import com.github.pagehelper.PageHelper;
import com.erp.common.core.context.SecurityContextHolder;
import com.erp.common.core.exception.ServiceException;
import com.erp.inventory.domain.*;
import com.erp.inventory.domain.dto.InvSalesReturnSourceQuery;
import com.erp.inventory.mapper.*;

class InvSpecialistReadServiceTest
{
    Map<Class<?>,Object> mocks;
    InvPurchaseServiceImpl purchase;
    InvPurchaseReturnServiceImpl purchaseReturn;
    InvSalesReturnServiceImpl salesReturn;
    InvDeptScopeMapper scope;
    @BeforeEach void setup()
    {
        mocks=new HashMap<>(); purchase=wire(new InvPurchaseServiceImpl());purchaseReturn=wire(new InvPurchaseReturnServiceImpl());salesReturn=wire(new InvSalesReturnServiceImpl());
        scope=mockOf(InvDeptScopeMapper.class);SecurityContextHolder.setUserId("42");SecurityContextHolder.setUserName("specialist");
        when(scope.countUserShopScope(42L,20L)).thenReturn(1);when(scope.countDeptInScope(anyLong(),anyLong())).thenReturn(1);
        when(scope.selectDeptTypeById(20L)).thenReturn("WAREHOUSE");
    }
    @AfterEach void clear(){SecurityContextHolder.remove();PageHelper.clearPage();}
    @SuppressWarnings("unchecked") <T> T mockOf(Class<T> type){return (T)mocks.computeIfAbsent(type,k -> mock(k));}
    <T> T wire(T target)
    {
        for(Class<?> type=target.getClass();type!=Object.class;type=type.getSuperclass()) for(var field:type.getDeclaredFields())
            if(field.getType().getName().startsWith("com.erp.inventory.mapper.")) ReflectionTestUtils.setField(target,field.getName(),mockOf(field.getType()));
        return target;
    }
    @ParameterizedTest @ValueSource(strings={"purchase","purchaseReturn","salesReturn"})
    void purposeDraftReadsOnlyCurrentContextDraft(String feature)
    {
        for(String status:List.of("draft","submitted","cancelled","returned"))
        {
            setupHeader(feature,status,20L);
            if(status.equals("draft")) assertThat(readDraft(feature)).isNotNull();
            else assertThatThrownBy(() -> readDraft(feature)).isInstanceOf(ServiceException.class);
        }
    }
    @ParameterizedTest @ValueSource(strings={"purchase","purchaseReturn","salesReturn"})
    void dutyDraftReadRejectsWrongObjectAndUnauthorizedContext(String feature)
    {
        setupHeader(feature,"draft",99L);
        assertThatThrownBy(() -> readDraft(feature)).isInstanceOf(ServiceException.class);
        setupHeader(feature,"draft",20L);when(scope.countUserShopScope(42L,20L)).thenReturn(0);
        assertThatThrownBy(() -> readDraft(feature)).isInstanceOf(ServiceException.class);
    }
    @Test void receiveContextRequiresWarehouseAndSubmittedButDoesNotBlockFullyReceivedRecovery()
    {
        setupHeader("purchase","submitted",20L);
        InvPurchaseOrder result=purchase.getReceiveContext(1L,20L);assertThat(result.getStatus()).isEqualTo("submitted");
        setupHeader("purchase","draft",20L);assertThatThrownBy(() -> purchase.getReceiveContext(1L,20L)).isInstanceOf(ServiceException.class);
        setupHeader("purchase","submitted",20L);when(scope.selectDeptTypeById(20L)).thenReturn("STORE");
        assertThatThrownBy(() -> purchase.getReceiveContext(1L,20L)).isInstanceOf(ServiceException.class);
    }
    @Test void returnSourceScopeIsResolvedBeforePaginationAndPageStateAlwaysCleared()
    {
        when(scope.selectDeptTypeById(20L)).thenReturn("STORE");
        when(mockOf(InvSalesOrderMapper.class).selectReturnableSalesOrderList(any(),eq(20L))).thenAnswer(call -> {
            assertThat(PageHelper.getLocalPage().getPageNum()).isEqualTo(2);assertThat(PageHelper.getLocalPage().getPageSize()).isEqualTo(7);return new ArrayList<>();
        });
        InvSalesReturnSourceQuery query=new InvSalesReturnSourceQuery();query.setPageNum(2);query.setPageSize(7);
        salesReturn.selectReturnableSourceOrders(query,20L);
        var order=inOrder(scope,mockOf(InvSalesOrderMapper.class));order.verify(scope).countUserShopScope(42L,20L);order.verify(scope).selectDeptTypeById(20L);order.verify(mockOf(InvSalesOrderMapper.class)).selectReturnableSalesOrderList(query,20L);
        assertThat((Object)PageHelper.getLocalPage()).isNull();
        when(mockOf(InvSalesOrderMapper.class).selectReturnableSalesOrderList(any(),any())).thenThrow(new ServiceException("injected"));
        assertThatThrownBy(() -> salesReturn.selectReturnableSourceOrders(query,20L)).isInstanceOf(ServiceException.class);
        assertThat((Object)PageHelper.getLocalPage()).isNull();
    }
    @Test void sourceDetailUsesHistoricalReservationsAndMaterialCap()
    {
        sourceFixture();var history=mockOf(InvSalesReturnDetailMapper.class);
        when(history.sumHistoricalReturnQuantityBySalesDetailId(10L,11L,null)).thenReturn(new BigDecimal("1"));
        when(history.sumHistoricalReturnQuantityByItem(10L,"product",1L,null)).thenReturn(new BigDecimal("2"));
        var detail=salesReturn.getReturnableSourceOrder(10L,20L).getDetails().get(0);
        assertThat(detail.getReturnableQuantity()).isEqualByComparingTo("1");assertThat(detail.getDetailId()).isEqualTo(11L);
        when(history.sumHistoricalReturnQuantityByItem(10L,"product",1L,null)).thenReturn(new BigDecimal("3"));
        assertThatThrownBy(() -> salesReturn.getReturnableSourceOrder(10L,20L)).isInstanceOf(ServiceException.class).hasMessageContaining("暂无可退");
    }
    @ParameterizedTest @ValueSource(strings={"draft","cancelled","foreign","missing"})
    void sourceDetailCannotReadNonReturnableOrForeignHeader(String invalid)
    {
        sourceFixture();InvSalesOrder source=source();
        if(invalid.equals("foreign")) source.setShopDeptId(99L);else source.setStatus(invalid);
        when(mockOf(InvSalesOrderMapper.class).selectInvSalesOrderById(10L)).thenReturn(invalid.equals("missing")?null:source);
        assertThatThrownBy(() -> salesReturn.getReturnableSourceOrder(10L,20L)).isInstanceOf(ServiceException.class);
    }
    @Test void savedSubmitUsesLockedDetailsAndOnlyStatusMutation()
    {
        sourceFixture();InvSalesReturn draft=returnDraft();var returns=mockOf(InvSalesReturnMapper.class);var details=mockOf(InvSalesReturnDetailMapper.class);
        when(returns.selectInvSalesReturnById(1L)).thenReturn(draft);when(returns.selectInvSalesReturnByIdForUpdate(1L)).thenReturn(draft);
        when(details.selectInvSalesReturnDetailByReturnIdForUpdate(1L)).thenReturn(List.of(returnLine()));when(returns.updateInvSalesReturn(any())).thenReturn(1);
        var result=salesReturn.submitSavedReturn(1L,20L);assertThat(result.getStatus()).isEqualTo("submitted");assertThat(result.getReturnTitle()).isEqualTo("frozen title");
        var capture=org.mockito.ArgumentCaptor.forClass(InvSalesReturn.class);verify(returns).updateInvSalesReturn(capture.capture());
        assertThat(capture.getValue().getParams()).containsEntry("expectedStatus","draft").doesNotContainKey("updateContent");assertThat(capture.getValue().getReturnTitle()).isNull();
        verify(details,never()).deleteInvSalesReturnDetailByReturnId(any());verify(details,never()).batchInsertInvSalesReturnDetail(any());
        verify(details,never()).selectInvSalesReturnDetailByReturnId(1L);
        var order=inOrder(mockOf(InvSalesOrderMapper.class),returns,details);
        order.verify(mockOf(InvSalesOrderMapper.class)).selectInvSalesOrderByIdForUpdate(10L);order.verify(returns).selectInvSalesReturnByIdForUpdate(1L);order.verify(details).selectInvSalesReturnDetailByReturnIdForUpdate(1L);
    }
    @ParameterizedTest @ValueSource(strings={"submitted","empty","oversized","title","foreign","sourceChanged","stateRace"})
    void invalidSavedDraftCannotSubmit(String invalid)
    {
        sourceFixture();InvSalesReturn before=returnDraft(),locked=returnDraft();InvSalesReturnDetail detail=returnLine();
        if(invalid.equals("submitted")) locked.setStatus("submitted");if(invalid.equals("title")) locked.setReturnTitle(" ");
        if(invalid.equals("foreign")) locked.setShopDeptId(99L);if(invalid.equals("sourceChanged")) locked.setSalesOrderId(12L);
        if(invalid.equals("oversized")) detail.setQuantity(new BigDecimal("4"));
        var returns=mockOf(InvSalesReturnMapper.class);when(returns.selectInvSalesReturnById(1L)).thenReturn(before);when(returns.selectInvSalesReturnByIdForUpdate(1L)).thenReturn(locked);
        when(mockOf(InvSalesReturnDetailMapper.class).selectInvSalesReturnDetailByReturnIdForUpdate(1L)).thenReturn(invalid.equals("empty")?List.of():List.of(detail));
        when(returns.updateInvSalesReturn(any())).thenReturn(invalid.equals("stateRace")?0:1);
        assertThatThrownBy(() -> salesReturn.submitSavedReturn(1L,20L)).isInstanceOf(ServiceException.class);
        if(!invalid.equals("stateRace")) verify(returns,never()).updateInvSalesReturn(any());
    }
    @ParameterizedTest @ValueSource(strings={"purchase","purchaseReturn","salesReturn"})
    void actionContextOnlyReadsExistingScopedHeaderNeverDetails(String feature) throws Exception
    {
        setupHeader(feature,"submitted",20L);
        var header=readAction(feature);var json=new com.fasterxml.jackson.databind.ObjectMapper().readTree(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(header));
        assertThat(json.get("status").asText()).isEqualTo("submitted");assertThat(json.get("shopDeptId").asText()).isEqualTo("20");assertThat(json.get("_specialistSummaryOnly").asBoolean()).isTrue();
        assertThat(json.has("details")).isFalse();assertThat(json.has("customerName")).isFalse();assertThat(json.has("supplierName")).isFalse();assertThat(json.has("totalAmount")).isFalse();
        verifyNoInteractions(mockOf(InvPurchaseDetailMapper.class),mockOf(InvPurchaseReturnDetailMapper.class),mockOf(InvSalesReturnDetailMapper.class));
        setupHeader(feature,"submitted",99L);assertThatThrownBy(()->readAction(feature)).isInstanceOf(ServiceException.class);
        setupHeader(feature,"submitted",20L);when(scope.countUserShopScope(42L,20L)).thenReturn(0);assertThatThrownBy(()->readAction(feature)).isInstanceOf(ServiceException.class);
    }
    Object readAction(String feature)
    { return feature.equals("purchase")?purchase.getActionContext(1L,20L):feature.equals("purchaseReturn")?purchaseReturn.getActionContext(1L,20L):salesReturn.getActionContext(1L,20L); }
    Object readDraft(String feature)
    { return feature.equals("purchase")?purchase.getPurchaseDraft(1L,20L):feature.equals("purchaseReturn")?purchaseReturn.getReturnDraft(1L,20L):salesReturn.getReturnDraft(1L,20L); }
    void setupHeader(String feature,String status,Long dept)
    {
        when(scope.selectDeptTypeById(20L)).thenReturn(feature.equals("salesReturn")?"STORE":"WAREHOUSE");
        if(feature.equals("purchase")) { InvPurchaseOrder row=new InvPurchaseOrder();row.setOrderId(1L);row.setStatus(status);row.setShopDeptId(dept);when(mockOf(InvPurchaseOrderMapper.class).selectInvPurchaseOrderById(1L)).thenReturn(row); }
        else if(feature.equals("purchaseReturn")) { InvPurchaseReturn row=new InvPurchaseReturn();row.setReturnId(1L);row.setStatus(status);row.setShopDeptId(dept);when(mockOf(InvPurchaseReturnMapper.class).selectInvPurchaseReturnById(1L)).thenReturn(row); }
        else { InvSalesReturn row=new InvSalesReturn();row.setReturnId(1L);row.setStatus(status);row.setShopDeptId(dept);when(mockOf(InvSalesReturnMapper.class).selectInvSalesReturnById(1L)).thenReturn(row); }
    }
    void sourceFixture()
    {
        when(scope.selectDeptTypeById(20L)).thenReturn("STORE");when(mockOf(InvSalesOrderMapper.class).selectInvSalesOrderById(10L)).thenReturn(source());when(mockOf(InvSalesOrderMapper.class).selectInvSalesOrderByIdForUpdate(10L)).thenReturn(source());
        when(mockOf(InvSalesDetailMapper.class).selectInvSalesDetailByOrderId(10L)).thenReturn(List.of(sourceLine()));when(mockOf(InvSalesDetailMapper.class).selectInvSalesDetailByOrderIdForUpdate(10L)).thenReturn(List.of(sourceLine()));
    }
    static InvSalesOrder source(){InvSalesOrder s=new InvSalesOrder();s.setOrderId(10L);s.setShopDeptId(20L);s.setStatus("submitted");return s;}
    static InvSalesDetail sourceLine(){InvSalesDetail d=new InvSalesDetail();d.setDetailId(11L);d.setItemType("product");d.setItemId(1L);d.setProductId(1L);d.setDeliveredQuantity(new BigDecimal("3"));return d;}
    static InvSalesReturn returnDraft(){InvSalesReturn d=new InvSalesReturn();d.setReturnId(1L);d.setSalesOrderId(10L);d.setShopDeptId(20L);d.setStatus("draft");d.setReturnTitle("frozen title");d.setCustomerName("customer");return d;}
    static InvSalesReturnDetail returnLine(){InvSalesReturnDetail d=new InvSalesReturnDetail();d.setSalesDetailId(11L);d.setProductId(1L);d.setQuantity(BigDecimal.ONE);return d;}
}
