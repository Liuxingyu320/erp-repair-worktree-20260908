package com.erp.system.service.impl;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.erp.common.core.web.domain.AjaxResult;
import com.erp.common.security.utils.DictUtils;
import com.erp.system.api.domain.SysDictType;
import com.erp.system.api.domain.SysDictData;
import com.erp.system.mapper.SysDictTypeMapper;
import com.erp.system.mapper.SysDictDataMapper;
import com.erp.system.service.support.DictCacheCoordinator;

class DictionaryMutationRecoveryTest {
    private SysDictType type(long id, String name) { SysDictType value = new SysDictType(); value.setDictId(id); value.setDictName(name); value.setDictType(name); return value; }
    private SysDictTypeServiceImpl service(SysDictTypeMapper types, SysDictDataMapper data, DictCacheCoordinator cache) {
        SysDictTypeServiceImpl service = new SysDictTypeServiceImpl();
        ReflectionTestUtils.setField(service, "dictTypeMapper", types); ReflectionTestUtils.setField(service, "dictDataMapper", data); ReflectionTestUtils.setField(service, "dictCache", cache); return service;
    }
    @Test void allParentsAreLockedInStableOrderBeforeAnyDeleteAndUsedTypeBlocksWholeBatch() {
        SysDictTypeMapper types = mock(SysDictTypeMapper.class); SysDictDataMapper data = mock(SysDictDataMapper.class); DictCacheCoordinator cache = mock(DictCacheCoordinator.class);
        when(types.lockDictTypeById(1L)).thenReturn(type(1,"empty")); when(types.lockDictTypeById(2L)).thenReturn(type(2,"used")); when(data.lockDictDataIdsByType("used")).thenReturn(List.of(10L));
        SysDictTypeServiceImpl service = service(types,data,cache);
        for (Long[] ids : List.of(new Long[]{2L,1L,1L}, new Long[]{1L,2L})) assertThatThrownBy(() -> service.deleteDictTypeByIds(ids)).hasMessageContaining("used").hasMessageContaining("整批");
        var order = inOrder(types); order.verify(types).lockDictTypeById(1L); order.verify(types).lockDictTypeById(2L);
        verify(types,never()).deleteDictTypeById(any()); verify(cache,never()).afterCommit(any());
        assertThatThrownBy(() -> service.deleteDictTypeByIds(new Long[0])).hasMessageContaining("有效字典编号");
    }
    @Test void cacheInvalidationIsAfterCommitOnlyAndRedisFailureIsCommittedWarningWithBoundedRetry() {
        DictCacheCoordinator cache = new DictCacheCoordinator();
        try (var redis = mockStatic(DictUtils.class)) {
            TransactionSynchronizationManager.initSynchronization();
            try {
                cache.afterCommit(List.of("old","new")); redis.verifyNoInteractions();
                redis.when(() -> DictUtils.removeDictCache("old")).thenThrow(new IllegalStateException("offline"));
                for (TransactionSynchronization callback : TransactionSynchronizationManager.getSynchronizations()) callback.afterCommit();
                assertThat(cache.attachOutcome(AjaxResult.success()).get("cacheRefreshPending")).isEqualTo(true);
                assertThat(cache.isPending("old")).isTrue();
                for (int i=0;i<10;i++) cache.retryPending();
                redis.verify(() -> DictUtils.removeDictCache("old"),times(5));
                redis.when(() -> DictUtils.removeDictCache("old")).thenAnswer(call -> null);
                cache.clearAll(); assertThat(cache.isPending("old")).isFalse();
            } finally { TransactionSynchronizationManager.clearSynchronization(); }
        }
    }
    @Test void rollbackDoesNotInvalidateAndRenamedOldKeyCannotBeReturnedEvenIfCacheWasRepopulated() {
        DictCacheCoordinator cache = new DictCacheCoordinator();
        try (var redis = mockStatic(DictUtils.class)) {
            TransactionSynchronizationManager.initSynchronization();
            try { cache.afterCommit(List.of("old")); for (var callback : TransactionSynchronizationManager.getSynchronizations()) callback.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK); redis.verifyNoInteractions(); }
            finally { TransactionSynchronizationManager.clearSynchronization(); }
            SysDictTypeMapper types=mock(SysDictTypeMapper.class); SysDictDataMapper data=mock(SysDictDataMapper.class);
            var service=service(types,data,cache); when(types.selectDictTypeByType("old")).thenReturn(null); when(types.selectDictTypeByType("new")).thenReturn(type(1,"new"));
            List<SysDictData> current=List.of(new SysDictData()); when(data.selectDictDataByType("new")).thenReturn(current);
            redis.when(() -> DictUtils.getDictCache("old")).thenReturn(List.of(new SysDictData()));
            assertThat(service.selectDictDataByType("old")).isNull(); assertThat(service.selectDictDataByType("new")).isSameAs(current);
            redis.verify(() -> DictUtils.getDictCache("old"),never()); verify(data,never()).selectDictDataByType("old");
        }
    }
    @Test void renameInvalidatesBothKeysOnlyAfterRowsSavedAndMissingParentRejectsItemInsert() {
        SysDictTypeMapper types=mock(SysDictTypeMapper.class); SysDictDataMapper data=mock(SysDictDataMapper.class); DictCacheCoordinator cache=mock(DictCacheCoordinator.class);
        when(types.lockDictTypeById(1L)).thenReturn(type(1,"old")); when(types.updateDictType(any())).thenReturn(1);
        service(types,data,cache).updateDictType(type(1,"new"));
        var order=inOrder(data,types,cache); order.verify(data).updateDictDataType("old","new"); order.verify(types).updateDictType(any()); order.verify(cache).afterCommit(List.of("old","new"));
        var itemService=new SysDictDataServiceImpl(); ReflectionTestUtils.setField(itemService,"dictTypeMapper",types); ReflectionTestUtils.setField(itemService,"dictDataMapper",data); ReflectionTestUtils.setField(itemService,"dictCache",cache);
        SysDictData item=new SysDictData(); item.setDictType("missing");
        assertThatThrownBy(() -> itemService.insertDictData(item)).hasMessageContaining("已不存在"); verify(data,never()).insertDictData(any());
    }
}
