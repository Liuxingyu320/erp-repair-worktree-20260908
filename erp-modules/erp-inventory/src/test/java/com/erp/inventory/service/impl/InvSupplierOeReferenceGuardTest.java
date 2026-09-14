package com.erp.inventory.service.impl;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.util.List;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import com.erp.common.core.context.SecurityContextHolder;
import com.erp.common.core.exception.ServiceException;
import com.erp.inventory.domain.*;
import com.erp.inventory.mapper.*;

class InvSupplierOeReferenceGuardTest
{
    final InvSupplierMapper suppliers = mock(InvSupplierMapper.class);
    final InvOeMapper oe = mock(InvOeMapper.class);
    final InvOeCategoryMapper categories = mock(InvOeCategoryMapper.class);
    final InvSupplierServiceImpl supplierService = new InvSupplierServiceImpl();
    final InvOeServiceImpl oeService = new InvOeServiceImpl();

    @BeforeEach void setup()
    {
        SecurityContextHolder.setUserId("1"); SecurityContextHolder.setUserName("f1-test");
        InvDeptScopeMapper scope = mock(InvDeptScopeMapper.class);
        when(scope.selectRelatedDeptIds(20L)).thenReturn(List.of(20L));
        when(scope.selectDeptTypeById(20L)).thenReturn("WAREHOUSE");
        for (Object service : List.of(supplierService, oeService))
        {
            ReflectionTestUtils.setField(service, "supplierMapper", suppliers);
            ReflectionTestUtils.setField(service, "deptScopeMapper", scope);
        }
        ReflectionTestUtils.setField(oeService, "oeMapper", oe);
        ReflectionTestUtils.setField(oeService, "categoryMapper", categories);
        InvOeCategory category = new InvOeCategory(); category.setCategoryId(10L);
        category.setCategoryCode("C"); category.setStatus("0");
        when(categories.selectInvOeCategoryById(10L)).thenReturn(category);
        when(suppliers.selectInvSupplierByIdForUpdate(1L)).thenAnswer(call -> supplier("供应商A"));
        when(suppliers.selectInvSupplierById(1L)).thenAnswer(call -> supplier("供应商B"));
        when(suppliers.updateInvSupplier(any())).thenReturn(1);
        when(suppliers.selectInvSupplierByNameAndShop("供应商A", 20L)).thenAnswer(call -> supplier("供应商A"));
    }
    @AfterEach void clear() { SecurityContextHolder.remove(); }

    @ParameterizedTest @ValueSource(booleans = {true, false})
    void oeOnlyReferenceBlocksRenameAndDelete(boolean rename)
    {
        when(suppliers.selectReferencingOeIdsForUpdate("供应商A")).thenReturn(List.of(7L));
        assertThatThrownBy(() -> mutate(rename)).isInstanceOf(ServiceException.class).hasMessageContaining("OE引用");
        verify(suppliers, never()).updateInvSupplier(any()); verify(suppliers, never()).deleteInvSupplierByIds(any());
        var order = inOrder(suppliers);
        order.verify(suppliers).selectInvSupplierByIdForUpdate(1L);
        order.verify(suppliers).selectReferencingOeIdsForUpdate("供应商A");
    }

    @ParameterizedTest @ValueSource(booleans = {true, false})
    void existingProductOrPurchaseProtectionIsUnchanged(boolean rename)
    {
        when(suppliers.countSupplierReferences(1L, "供应商A", List.of(20L))).thenReturn(1);
        assertThatThrownBy(() -> mutate(rename)).isInstanceOf(ServiceException.class).hasMessageContaining("商品或采购历史引用");
        verify(suppliers, never()).updateInvSupplier(any()); verify(suppliers, never()).deleteInvSupplierByIds(any());
    }

    @Test void nonNameEditDoesNotRejectOeReference()
    {
        supplierService.saveSupplier(supplier("供应商A"), 20L);
        verify(suppliers).updateInvSupplier(any()); verify(suppliers, never()).selectReferencingOeIdsForUpdate(any());
    }
    @Test void noReferencesAllowsRename()
    {
        supplierService.saveSupplier(supplier("供应商B"), 20L); verify(suppliers).updateInvSupplier(any());
    }
    @Test void foreignSupplierIsRejectedBeforeReferenceRead()
    {
        InvSupplier foreign = supplier("供应商A"); foreign.setShopDeptId(99L);
        when(suppliers.selectInvSupplierByIdForUpdate(1L)).thenReturn(foreign);
        assertThatThrownBy(() -> mutate(false)).isInstanceOf(ServiceException.class).hasMessageContaining("无权");
        verify(suppliers, never()).selectReferencingOeIdsForUpdate(any());
    }
    @Test void deleteLocksDistinctSupplierIdsInOrder()
    {
        InvSupplier two = supplier("供应商C"); two.setSupplierId(2L);
        when(suppliers.selectInvSupplierByIdForUpdate(2L)).thenReturn(two);
        supplierService.deleteSupplierByIds(new Long[] {2L,1L,2L},20L);
        var order = inOrder(suppliers);
        order.verify(suppliers).selectInvSupplierByIdForUpdate(1L);
        order.verify(suppliers).selectInvSupplierByIdForUpdate(2L);
    }

    @ParameterizedTest @ValueSource(strings = {"removed", "renamed", "disabled", "cooperation", "moved"})
    void staleSupplierLookupCannotWriteOe(String change)
    {
        InvSupplier locked = supplier("供应商A");
        if (change.equals("renamed")) locked.setSupplierName("供应商B");
        if (change.equals("disabled")) locked.setStatus("1");
        if (change.equals("cooperation")) locked.setCooperationStatus("1");
        if (change.equals("moved")) locked.setShopDeptId(99L);
        when(suppliers.selectInvSupplierByIdForUpdate(1L)).thenReturn(change.equals("removed") ? null : locked);
        assertThatThrownBy(() -> oeService.saveOe(item(),20L)).isInstanceOf(ServiceException.class).hasMessageContaining("重新选择供应商");
        verify(oe, never()).insertInvOe(any()); verify(oe, never()).updateInvOe(any());
    }
    @Test void successfulOeUsesLockedPhoneAndLocksSupplierBeforeOe()
    {
        InvSupplier locked = supplier("供应商A"); locked.setContactPhone("new-phone");
        when(suppliers.selectInvSupplierByIdForUpdate(1L)).thenReturn(locked);
        InvOeItem item = item(); item.setOeItemId(8L); when(oe.selectInvOeByIdForUpdate(8L)).thenReturn(item());
        oeService.saveOe(item,20L);
        assertThat(item.getSupplierPhone()).isEqualTo("new-phone");
        var order = inOrder(suppliers,oe);
        order.verify(suppliers).selectInvSupplierByIdForUpdate(1L);
        order.verify(oe).selectInvOeByIdForUpdate(8L);
        order.verify(oe).updateInvOe(any());
    }
    @Test void importRejectsStaleSupplierWithoutInsertingRow()
    {
        when(suppliers.selectInvSupplierByIdForUpdate(1L)).thenReturn(null);
        assertThat(oeService.importOe(List.of(item()),false,20L)).contains("失败1条", "重新选择供应商");
        verify(oe, never()).insertInvOe(any());
    }
    @Test void importDoesNotSwallowTransactionLockFailure()
    {
        when(suppliers.selectInvSupplierByIdForUpdate(1L))
                .thenThrow(new org.springframework.dao.CannotAcquireLockException("lock conflict"));
        assertThatThrownBy(() -> oeService.importOe(List.of(item()),false,20L))
                .isInstanceOf(org.springframework.dao.ConcurrencyFailureException.class);
    }
    private void mutate(boolean rename)
    { if (rename) supplierService.saveSupplier(supplier("供应商B"),20L); else supplierService.deleteSupplierByIds(new Long[]{1L},20L); }
    static InvSupplier supplier(String name)
    {
        InvSupplier s = new InvSupplier(); s.setSupplierId(1L); s.setSupplierName(name); s.setShopDeptId(20L);
        s.setStatus("0"); s.setCooperationStatus("0"); return s;
    }
    static InvOeItem item()
    {
        InvOeItem item = new InvOeItem(); item.setCategoryId(10L); item.setOeItemName("cup"); item.setOeItemCode("OE-CUP");
        item.setSupplierName("供应商A"); item.setStatus("0"); return item;
    }
}
