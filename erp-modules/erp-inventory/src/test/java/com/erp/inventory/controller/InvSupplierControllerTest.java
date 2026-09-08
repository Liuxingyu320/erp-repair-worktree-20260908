package com.erp.inventory.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import com.erp.inventory.domain.InvProduct;
import com.erp.inventory.domain.InvSupplier;
import com.erp.inventory.service.IInvSupplierService;
import com.erp.common.core.constant.SecurityConstants;
import com.erp.common.core.context.SecurityContextHolder;
import com.erp.common.core.web.domain.AjaxResult;
import com.erp.system.api.model.LoginUser;

@DisplayName("供应商 Controller 读取边界")
class InvSupplierControllerTest
{
    @AfterEach
    void clearRequestContext()
    {
        SecurityContextHolder.remove();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    @DisplayName("供应商详情、写响应与删除响应必须禁止缓存")
    void detailAndMutationResponsesShouldDisableCaching()
    {
        InvSupplierController controller = new InvSupplierController();
        ReflectionTestUtils.setField(controller, "supplierService", new FakeSupplierService());
        MockHttpServletRequest request = new MockHttpServletRequest();
        InvSupplier supplier = supplier();

        MockHttpServletResponse detailResponse = new MockHttpServletResponse();
        controller.getInfo(8L, request, detailResponse);
        assertNoStore(detailResponse);

        MockHttpServletResponse addResponse = new MockHttpServletResponse();
        controller.add(supplier, request, addResponse);
        assertNoStore(addResponse);

        MockHttpServletResponse editResponse = new MockHttpServletResponse();
        controller.edit(supplier, request, editResponse);
        assertNoStore(editResponse);

        MockHttpServletResponse deleteResponse = new MockHttpServletResponse();
        controller.remove(new Long[]{8L}, request, deleteResponse);
        assertNoStore(deleteResponse);
    }

    @Test
    @DisplayName("无成本权限时供应商品必须同时隐藏采购价与成本价")
    @SuppressWarnings("unchecked")
    void productsShouldHideCostsWithoutPermission()
    {
        InvSupplierController controller = new InvSupplierController();
        FakeSupplierService service = new FakeSupplierService();
        service.products = List.of(productWithCost());
        ReflectionTestUtils.setField(controller, "supplierService", service);
        MockHttpServletResponse response = new MockHttpServletResponse();

        AjaxResult result = controller.products(8L, new MockHttpServletRequest(), response);

        List<InvProduct> products = (List<InvProduct>) result.get(AjaxResult.DATA_TAG);
        assertThat(products).hasSize(1);
        assertThat(products.get(0).getPurchasePrice()).isNull();
        assertThat(products.get(0).getCostPrice()).isNull();
        assertNoStore(response);
    }

    @Test
    @DisplayName("具有成本权限时供应商品保留采购价与成本价")
    @SuppressWarnings("unchecked")
    void productsShouldRetainCostsWithPermission()
    {
        setCostViewer();
        InvSupplierController controller = new InvSupplierController();
        FakeSupplierService service = new FakeSupplierService();
        service.products = List.of(productWithCost());
        ReflectionTestUtils.setField(controller, "supplierService", service);

        AjaxResult result = controller.products(8L, new MockHttpServletRequest(),
                new MockHttpServletResponse());

        List<InvProduct> products = (List<InvProduct>) result.get(AjaxResult.DATA_TAG);
        assertThat(products.get(0).getPurchasePrice()).isEqualByComparingTo("9.00");
        assertThat(products.get(0).getCostPrice()).isEqualByComparingTo("8.00");
    }

    private static void assertNoStore(MockHttpServletResponse response)
    {
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store, max-age=0");
        assertThat(response.getHeader("Pragma")).isEqualTo("no-cache");
    }

    private static InvSupplier supplier()
    {
        InvSupplier supplier = new InvSupplier();
        supplier.setSupplierId(8L);
        supplier.setSupplierName("测试供应商");
        return supplier;
    }

    private static InvProduct productWithCost()
    {
        InvProduct product = new InvProduct();
        product.setProductId(10L);
        product.setProductName("龙井");
        product.setPurchasePrice(new BigDecimal("9.00"));
        product.setCostPrice(new BigDecimal("8.00"));
        return product;
    }

    private static void setCostViewer()
    {
        LoginUser loginUser = new LoginUser();
        loginUser.setUserid(42L);
        loginUser.setUsername("supplier-cost-viewer");
        loginUser.setPermissions(Set.of("inv:cost:view"));
        SecurityContextHolder.setUserId("42");
        SecurityContextHolder.set(SecurityConstants.LOGIN_USER, loginUser);
    }

    @Test
    @DisplayName("供应商列表成功响应必须禁止缓存")
    void listShouldDisableCaching()
    {
        InvSupplierController controller = new InvSupplierController();
        ReflectionTestUtils.setField(controller, "supplierService", new FakeSupplierService());
        MockHttpServletRequest request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.list(new InvSupplier(), request, response);

        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store, max-age=0");
        assertThat(response.getHeader("Pragma")).isEqualTo("no-cache");
    }

    private static class FakeSupplierService implements IInvSupplierService
    {
        private List<InvProduct> products = Collections.emptyList();

        @Override
        public List<InvSupplier> selectSupplierList(InvSupplier supplier, Long selectedShopDeptId)
        {
            return Collections.emptyList();
        }

        @Override
        public InvSupplier selectSupplierById(Long supplierId, Long selectedShopDeptId)
        {
            return supplier();
        }

        @Override
        public List<InvProduct> selectSupplierProductList(Long supplierId, Long selectedShopDeptId)
        {
            return products;
        }

        @Override
        public InvSupplier saveSupplier(InvSupplier supplier, Long selectedShopDeptId)
        {
            return supplier;
        }

        @Override
        public void deleteSupplierByIds(Long[] supplierIds, Long selectedShopDeptId)
        {
        }
    }
}
