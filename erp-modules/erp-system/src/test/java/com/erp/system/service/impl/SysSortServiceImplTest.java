package com.erp.system.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import com.erp.common.core.exception.ServiceException;
import com.erp.system.api.domain.SysDept;
import com.erp.system.domain.SysMenu;
import com.erp.system.domain.dto.SysSortChangeItem;
import com.erp.system.domain.dto.SysSortChangeRequest;
import com.erp.system.mapper.SysDeptMapper;
import com.erp.system.mapper.SysMenuMapper;

@DisplayName("部门与菜单乐观排序")
class SysSortServiceImplTest
{
    @Test
    @DisplayName("部门排序按固定ID加锁且任一基线冲突时一项也不更新")
    void departmentConflictShouldLockInStableOrderAndWriteNothing()
    {
        SysDeptMapper mapper = mock(SysDeptMapper.class);
        when(mapper.selectDeptOrdersForUpdate(any())).thenReturn(List.of(
                dept(10L, 1), dept(20L, 99)));
        SysDeptServiceImpl service = new SysDeptServiceImpl()
        {
            @Override
            public void checkDeptDataScope(Long deptId)
            {
                // Scope verification is covered separately; this test isolates lock/compare/write ordering.
            }
        };
        ReflectionTestUtils.setField(service, "deptMapper", mapper);

        assertThatThrownBy(() -> service.updateDeptSort(request(
                change(20L, 2, 8), change(10L, 1, 7))))
                .isInstanceOfSatisfying(SysSortConflictException.class,
                        conflict -> assertThat(conflict.getConflictIds()).containsExactly(20L));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Long>> ids = ArgumentCaptor.forClass(List.class);
        verify(mapper).selectDeptOrdersForUpdate(ids.capture());
        assertThat(ids.getValue()).containsExactly(10L, 20L);
        verify(mapper, never()).updateDeptSort(any());
    }

    @Test
    @DisplayName("菜单排序全部基线一致后才按固定ID顺序写入")
    void menuSortShouldCompareAllRowsBeforeWriting()
    {
        SysMenuMapper mapper = mock(SysMenuMapper.class);
        when(mapper.selectMenuOrdersForUpdate(any())).thenReturn(List.of(
                menu(10L, 1), menu(20L, 2)));
        when(mapper.updateMenuSort(any())).thenReturn(1);
        SysMenuServiceImpl service = new SysMenuServiceImpl();
        ReflectionTestUtils.setField(service, "menuMapper", mapper);

        service.updateMenuSort(request(change(20L, 2, 8), change(10L, 1, 7)));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Long>> ids = ArgumentCaptor.forClass(List.class);
        verify(mapper).selectMenuOrdersForUpdate(ids.capture());
        assertThat(ids.getValue()).containsExactly(10L, 20L);
        ArgumentCaptor<SysMenu> updates = ArgumentCaptor.forClass(SysMenu.class);
        verify(mapper, times(2)).updateMenuSort(updates.capture());
        assertThat(updates.getAllValues()).extracting(SysMenu::getMenuId).containsExactly(10L, 20L);
        assertThat(updates.getAllValues()).extracting(SysMenu::getOrderNum).containsExactly(7, 8);
    }

    @Test
    @DisplayName("重复ID在加锁和写入前被拒绝")
    void duplicateIdsShouldBeRejectedBeforeDatabaseAccess()
    {
        SysMenuMapper mapper = mock(SysMenuMapper.class);
        SysMenuServiceImpl service = new SysMenuServiceImpl();
        ReflectionTestUtils.setField(service, "menuMapper", mapper);

        assertThatThrownBy(() -> service.updateMenuSort(request(
                change(10L, 1, 2), change(10L, 2, 3))))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("不能重复");

        verify(mapper, never()).selectMenuOrdersForUpdate(any());
        verify(mapper, never()).updateMenuSort(any());
    }

    private static SysSortChangeRequest request(SysSortChangeItem... changes)
    {
        SysSortChangeRequest request = new SysSortChangeRequest();
        request.setChanges(Arrays.asList(changes));
        return request;
    }

    private static SysSortChangeItem change(long id, int expected, int next)
    {
        SysSortChangeItem item = new SysSortChangeItem();
        item.setId(id);
        item.setExpectedOrderNum(expected);
        item.setNewOrderNum(next);
        return item;
    }

    private static SysDept dept(long id, int orderNum)
    {
        SysDept dept = new SysDept();
        dept.setDeptId(id);
        dept.setOrderNum(orderNum);
        return dept;
    }

    private static SysMenu menu(long id, int orderNum)
    {
        SysMenu menu = new SysMenu();
        menu.setMenuId(id);
        menu.setOrderNum(orderNum);
        return menu;
    }
}
