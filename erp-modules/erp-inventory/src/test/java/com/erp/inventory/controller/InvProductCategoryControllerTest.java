package com.erp.inventory.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import com.erp.inventory.domain.InvProductCategory;
import com.erp.inventory.service.IInvProductCategoryService;

@DisplayName("商品分类 Controller 只读响应")
class InvProductCategoryControllerTest
{
    @Test
    @DisplayName("分类树响应禁止缓存")
    void treeShouldDisableCaching()
    {
        IInvProductCategoryService service = mock(IInvProductCategoryService.class);
        when(service.selectCategoryTree(isNull())).thenReturn(Collections.emptyList());
        InvProductCategoryController controller = new InvProductCategoryController();
        ReflectionTestUtils.setField(controller, "categoryService", service);
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.tree(new MockHttpServletRequest(), response);

        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store, max-age=0");
        assertThat(response.getHeader("Pragma")).isEqualTo("no-cache");
    }

    @Test
    @DisplayName("分类详情与写响应全部禁止缓存")
    void detailAndMutationResponsesShouldDisableCaching()
    {
        IInvProductCategoryService service = mock(IInvProductCategoryService.class);
        InvProductCategory category = new InvProductCategory();
        category.setCategoryId(8L);
        category.setCategoryName("测试分类");
        when(service.selectCategoryById(eq(8L), isNull())).thenReturn(category);
        when(service.saveCategory(any(InvProductCategory.class), isNull())).thenReturn(category);
        InvProductCategoryController controller = new InvProductCategoryController();
        ReflectionTestUtils.setField(controller, "categoryService", service);
        MockHttpServletRequest request = new MockHttpServletRequest();

        MockHttpServletResponse detailResponse = new MockHttpServletResponse();
        controller.getInfo(8L, request, detailResponse);
        assertNoStore(detailResponse);

        MockHttpServletResponse addResponse = new MockHttpServletResponse();
        controller.add(category, request, addResponse);
        assertNoStore(addResponse);

        MockHttpServletResponse editResponse = new MockHttpServletResponse();
        controller.edit(category, request, editResponse);
        assertNoStore(editResponse);

        MockHttpServletResponse deleteResponse = new MockHttpServletResponse();
        controller.remove(8L, request, deleteResponse);
        assertNoStore(deleteResponse);
    }

    private void assertNoStore(MockHttpServletResponse response)
    {
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store, max-age=0");
        assertThat(response.getHeader("Pragma")).isEqualTo("no-cache");
    }
}
