package com.erp.inventory.service.impl;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import com.erp.common.core.context.SecurityContextHolder;
import com.erp.common.core.exception.ServiceException;
import com.erp.inventory.domain.*;
import com.erp.inventory.mapper.*;

class InvStockCheckRestartServiceTest
{
    final InvStockCheckMapper headers=mock(InvStockCheckMapper.class);
    final InvStockCheckDetailMapper details=mock(InvStockCheckDetailMapper.class);
    final InvStockMapper stocks=mock(InvStockMapper.class);
    final InvStockCheck check=new InvStockCheck();
    final List<InvStockCheckDetail> rows=new ArrayList<>();
    final InvStockCheckServiceImpl service=new InvStockCheckServiceImpl() {
        @Override protected void assertShopVisible(Long id,Long selected,String message) { if(!Objects.equals(id,selected))throw new ServiceException(message); }
        @Override protected void assertWritableInventoryDept(Long id,Long selected,String message) { if(!Objects.equals(id,selected))throw new ServiceException(message); }
    };
    @BeforeEach void setup()
    {
        SecurityContextHolder.setUserId("7");SecurityContextHolder.setUserName("counter");
        check.setCheckId(1L);check.setShopDeptId(20L);check.setWarehouseId(20L);check.setCounterUserId(7L);check.setStatus("invalidated");
        when(headers.selectInvStockCheckById(1L)).thenReturn(check);when(headers.selectInvStockCheckByIdForUpdate(1L)).thenReturn(check);
        when(details.selectInvStockCheckDetailByCheckIdForUpdate(1L)).thenReturn(rows);when(details.selectInvStockCheckDetailByCheckId(1L)).thenReturn(rows);
        ReflectionTestUtils.setField(service,"checkMapper",headers);ReflectionTestUtils.setField(service,"checkDetailMapper",details);ReflectionTestUtils.setField(service,"stockMapper",stocks);
        for(long id=1;id<=3;id++) { var row=InvStockCheckRestartPolicyTest.row(id,"8"); row.setRecountQty(new BigDecimal("9"));row.setRecountBy("prior");rows.add(row);
            InvStock stock=new InvStock();stock.setCurrentQuantity(new BigDecimal(id==1?"11":"10.125"));stock.setCostPrice(new BigDecimal("2"));when(stocks.selectInvStockByItemShopWarehouseForUpdate("gift",id,20L,20L)).thenReturn(stock); }
        check.setLastInvalidDetailSnapshot("[{\"detailId\":2,\"bookQuantity\":10.125,\"currentQuantity\":11}]");
    }
    @AfterEach void clear(){ SecurityContextHolder.remove(); }
    @Test void restartClearsCurrentAndPreviouslyProvenChangesWhilePreservingOtherCounts()
    {
        service.restartCheck(1L,20L);
        verify(details).resetSnapshot(1L,new BigDecimal("11"),new BigDecimal("2"));
        verify(details).resetSnapshot(2L,new BigDecimal("10.125"),new BigDecimal("2"));
        verify(details).refreshSnapshotCost(3L,new BigDecimal("2"));verify(details,never()).resetSnapshot(eq(3L),any(),any());
        ArgumentCaptor<InvStockCheck> update=ArgumentCaptor.forClass(InvStockCheck.class);verify(headers).updateInvStockCheck(update.capture());
        assertThat(update.getValue().getLastInvalidDetailSnapshot()).isNull();assertThat(update.getValue().getStatus()).isEqualTo("draft");
        check.setRestartReferenceSnapshot(update.getValue().getRestartReferenceSnapshot());
        InvStockCheckRestartPolicy.decorate(check,rows);assertThat(rows.get(2).getActualQty()).isEqualByComparingTo("8");assertThat(rows.get(2).getRecountBy()).isEqualTo("prior");assertThat(rows.get(0).getPreviousRecountQty()).isEqualByComparingTo("9");
    }
    @Test void invalidLastRowPreventsEveryBusinessWriteIncludingHeader()
    {
        check.setStatus("draft");check.setRestartReferenceSnapshot(InvStockCheckRestartPolicy.appendRound(check,rows,Set.of(1L),"counter"));
        var first=InvStockCheckRestartPolicyTest.row(1,"5");first.setSnapshotVersion(InvStockCheckRestartPolicy.version(check));
        InvStockCheck input=new InvStockCheck();input.setCheckId(1L);input.setRemark("must not write");input.setDetails(List.of(first,InvStockCheckRestartPolicyTest.row(2,"8")));
        assertThatThrownBy(() -> service.saveDraft(input,20L)).hasMessageContaining("重新");verify(headers,never()).updateInvStockCheck(any());verify(details,never()).updateInvStockCheckDetail(any());
    }
    @Test void submitWithStaleInputAlsoRejectsBeforeAnyWrite()
    {
        check.setStatus("draft");check.setRestartReferenceSnapshot(InvStockCheckRestartPolicy.appendRound(check,rows,Set.of(1L),"counter"));
        assertThatThrownBy(() -> service.submitCheck(1L,List.of(InvStockCheckRestartPolicyTest.row(1,"8")),20L)).hasMessageContaining("重新");
        verify(headers,never()).updateInvStockCheck(any());verify(details,never()).updateInvStockCheckDetail(any());
    }
    @Test void currentTokenCanSaveAndReturnedBlindResponseNeverLeaksBookReferences()
    {
        check.setStatus("returned");check.setBlindCheck("1");check.setRestartReferenceSnapshot(InvStockCheckRestartPolicy.appendRound(check,rows,Set.of(1L),"counter"));
        var row=InvStockCheckRestartPolicyTest.row(1,"0");row.setSnapshotVersion(InvStockCheckRestartPolicy.version(check));
        InvStockCheck input=new InvStockCheck();input.setCheckId(1L);input.setDetails(List.of(row));
        InvStockCheck result=service.saveDraft(input,20L);verify(details).updateInvStockCheckDetail(rows.get(0));
        assertThat(result.getLastInvalidDetailSnapshot()).isNull();assertThat(result.getDetails()).allSatisfy(r -> { assertThat(r.getBookQty()).isNull();assertThat(r.getPreviousBookQty()).isNull();assertThat(r.getDiffQty()).isNull(); });
        assertThat(result.getDetails().get(0).getPreviousActualQty()).isEqualByComparingTo("8");
    }
    @Test void anotherOrganizationFailsBeforeHeaderLockOrStockReads()
    {
        assertThatThrownBy(() -> service.restartCheck(1L,30L)).isInstanceOf(ServiceException.class);
        verify(headers,never()).selectInvStockCheckByIdForUpdate(anyLong());verifyNoInteractions(stocks);
    }
    @Test void unknownAndDuplicateRowsRejectBeforeAnyWrite()
    {
        check.setStatus("draft");var input=new InvStockCheck();input.setCheckId(1L);input.setDetails(List.of(InvStockCheckRestartPolicyTest.row(9,"0")));
        assertThatThrownBy(() -> service.saveDraft(input,20L)).hasMessageContaining("不存在");
        input.setDetails(List.of(rows.get(0),rows.get(0)));assertThatThrownBy(() -> service.saveDraft(input,20L)).hasMessageContaining("重复");
        verify(headers,never()).updateInvStockCheck(any());verify(details,never()).updateInvStockCheckDetail(any());
    }
}
