package com.erp.system.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import com.erp.common.core.constant.HttpStatus;
import com.erp.common.core.web.domain.AjaxResult;
import com.erp.system.domain.dto.SysSortChangeRequest;
import com.erp.system.service.ISysDeptService;
import com.erp.system.service.ISysMenuService;
import com.erp.system.service.impl.SysSortConflictException;

@DisplayName("排序控制器契约")
class SysSortControllerTest
{
    @Test
    @DisplayName("部门排序并发冲突返回409业务码和最小冲突ID")
    void departmentSortConflictShouldReturnStableBusinessCode()
    {
        ISysDeptService service = mock(ISysDeptService.class);
        SysSortChangeRequest request = new SysSortChangeRequest();
        doThrow(new SysSortConflictException(List.of(20L))).when(service).updateDeptSort(request);
        SysDeptController controller = new SysDeptController();
        ReflectionTestUtils.setField(controller, "deptService", service);

        AjaxResult result = controller.updateSort(request);

        assertThat(result.get(AjaxResult.CODE_TAG)).isEqualTo(HttpStatus.CONFLICT);
        assertThat(result.get("businessCode")).isEqualTo("SORT_CONFLICT");
        assertThat(result.get("conflictIds")).isEqualTo(List.of(20L));
    }

    @Test
    @DisplayName("菜单排序成功仅转交固定DTO")
    void menuSortShouldDelegateValidatedRequest()
    {
        ISysMenuService service = mock(ISysMenuService.class);
        SysMenuController controller = new SysMenuController();
        ReflectionTestUtils.setField(controller, "menuService", service);
        SysSortChangeRequest request = new SysSortChangeRequest();

        AjaxResult result = controller.updateSort(request);

        verify(service).updateMenuSort(request);
        assertThat(result.get(AjaxResult.CODE_TAG)).isEqualTo(HttpStatus.SUCCESS);
    }
}
