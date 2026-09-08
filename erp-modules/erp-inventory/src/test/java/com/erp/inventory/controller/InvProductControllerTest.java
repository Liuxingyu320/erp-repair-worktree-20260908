package com.erp.inventory.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import com.erp.common.core.constant.SecurityConstants;
import com.erp.common.core.context.SecurityContextHolder;
import com.erp.common.core.web.domain.AjaxResult;
import com.erp.inventory.domain.InvProduct;
import com.erp.inventory.service.IInvProductService;
import com.erp.system.api.model.LoginUser;

@DisplayName("商品 Controller 成本字段权限")
class InvProductControllerTest
{
    @AfterEach
    void clearSecurityContext()
    {
        SecurityContextHolder.remove();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    @DisplayName("无成本权限的商品导出必须隐藏参考成本价")
    void exportShouldHideReferenceCostWithoutPermission() throws Exception
    {
        InvProductController controller = new InvProductController();
        FakeProductService productService = new FakeProductService();
        productService.product = productWithCost();
        ReflectionTestUtils.setField(controller, "productService", productService);
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.export(response, new InvProduct(), new MockHttpServletRequest());

        assertThat(exportedReferenceCost(response)).isBlank();
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store, max-age=0");
    }

    @Test
    @DisplayName("具有成本权限的商品导出保留参考成本价")
    void exportShouldRetainReferenceCostWithPermission() throws Exception
    {
        setCostViewer();
        InvProductController controller = new InvProductController();
        FakeProductService productService = new FakeProductService();
        productService.product = productWithCost();
        ReflectionTestUtils.setField(controller, "productService", productService);
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.export(response, new InvProduct(), new MockHttpServletRequest());

        assertThat(exportedReferenceCost(response)).isEqualTo("8.0");
    }

    @Test
    @DisplayName("商品列表关键字只匹配业务可见字段")
    void productListKeywordShouldSearchVisibleProductFieldsOnly() throws Exception
    {
        String domainSource = Files.readString(Path.of("src/main/java/com/erp/inventory/domain/InvProduct.java"),
                StandardCharsets.UTF_8);
        String mapperSource = Files.readString(Path.of("src/main/resources/mapper/inventory/InvProductMapper.xml"),
                StandardCharsets.UTF_8);

        assertThat(domainSource).contains("private String keyword;");
        assertThat(domainSource).contains("public String getKeyword()");
        assertThat(domainSource).contains("public void setKeyword(String keyword)");
        assertThat(mapperSource).contains("keyword != null and keyword != ''");
        assertThat(mapperSource).contains("p.product_name like concat('%', #{keyword}, '%')");
        assertThat(mapperSource).contains("p.product_code like concat('%', #{keyword}, '%')");
        assertThat(mapperSource).contains("p.spec like concat('%', #{keyword}, '%')");
        assertThat(mapperSource).doesNotContain("p.supplier_name like concat('%', #{keyword}, '%')");
        assertThat(mapperSource).doesNotContain("p.sku like concat('%', #{keyword}, '%')");
    }

    @Test
    @DisplayName("商品详情无成本权限时同时隐藏采购价与成本价")
    void getInfoShouldHideAllCostFieldsWithoutCostPermission()
    {
        InvProductController controller = new InvProductController();
        FakeProductService productService = new FakeProductService();
        productService.product = productWithCost();
        ReflectionTestUtils.setField(controller, "productService", productService);
        MockHttpServletResponse response = new MockHttpServletResponse();

        AjaxResult result = controller.getInfo(10L, new MockHttpServletRequest(), response);

        InvProduct data = (InvProduct) result.get(AjaxResult.DATA_TAG);
        assertThat(data.getPurchasePrice()).isNull();
        assertThat(data.getCostPrice()).isNull();
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store, max-age=0");
        assertThat(response.getHeader("Pragma")).isEqualTo("no-cache");
    }

    @Test
    @DisplayName("商品列表无成本权限时同时隐藏价格并禁止缓存")
    void listShouldHideAllCostFieldsAndDisableCachingWithoutCostPermission()
    {
        InvProductController controller = new InvProductController();
        FakeProductService productService = new FakeProductService();
        productService.product = productWithCost();
        ReflectionTestUtils.setField(controller, "productService", productService);
        MockHttpServletRequest request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.list(new InvProduct(), request, response);

        assertThat(productService.product.getPurchasePrice()).isNull();
        assertThat(productService.product.getCostPrice()).isNull();
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store, max-age=0");
        assertThat(response.getHeader("Pragma")).isEqualTo("no-cache");
    }

    @Test
    @DisplayName("具有成本权限时商品详情保留采购价与成本价")
    void getInfoShouldRetainAllCostFieldsWithCostPermission()
    {
        setCostViewer();
        InvProductController controller = new InvProductController();
        FakeProductService productService = new FakeProductService();
        productService.product = productWithCost();
        ReflectionTestUtils.setField(controller, "productService", productService);

        AjaxResult result = controller.getInfo(10L, new MockHttpServletRequest(),
                new MockHttpServletResponse());

        InvProduct data = (InvProduct) result.get(AjaxResult.DATA_TAG);
        assertThat(data.getPurchasePrice()).isEqualByComparingTo("9.00");
        assertThat(data.getCostPrice()).isEqualByComparingTo("8.00");
    }

    @Test
    @DisplayName("具有成本权限时商品列表保留采购价与成本价")
    void listShouldRetainAllCostFieldsWithCostPermission()
    {
        setCostViewer();
        InvProductController controller = new InvProductController();
        FakeProductService productService = new FakeProductService();
        productService.product = productWithCost();
        ReflectionTestUtils.setField(controller, "productService", productService);
        MockHttpServletRequest request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        controller.list(new InvProduct(), request, new MockHttpServletResponse());

        assertThat(productService.product.getPurchasePrice()).isEqualByComparingTo("9.00");
        assertThat(productService.product.getCostPrice()).isEqualByComparingTo("8.00");
    }

    @Test
    @DisplayName("无成本权限时商品新增必须同时阻止成本写入和响应泄露")
    void addShouldDropRequestAndResponseCostFieldsWithoutCostPermission()
    {
        InvProductController controller = new InvProductController();
        FakeProductService productService = new FakeProductService();
        productService.saveResult = productWithCost();
        ReflectionTestUtils.setField(controller, "productService", productService);
        InvProduct requestProduct = productWithCost();
        MockHttpServletResponse response = new MockHttpServletResponse();

        AjaxResult result = controller.add(requestProduct, new MockHttpServletRequest(), response);

        assertThat(productService.savedProduct.getPurchasePrice()).isNull();
        assertThat(productService.savedProduct.getCostPrice()).isNull();
        InvProduct data = (InvProduct) result.get(AjaxResult.DATA_TAG);
        assertThat(data.getPurchasePrice()).isNull();
        assertThat(data.getCostPrice()).isNull();
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store, max-age=0");
        assertThat(response.getHeader("Pragma")).isEqualTo("no-cache");
    }

    @Test
    @DisplayName("具有成本权限时商品编辑保留成本请求和响应")
    void editShouldRetainCostFieldsWithCostPermission()
    {
        setCostViewer();
        InvProductController controller = new InvProductController();
        FakeProductService productService = new FakeProductService();
        productService.saveResult = productWithCost();
        ReflectionTestUtils.setField(controller, "productService", productService);
        InvProduct requestProduct = productWithCost();

        AjaxResult result = controller.edit(requestProduct, new MockHttpServletRequest(),
                new MockHttpServletResponse());

        assertThat(productService.savedProduct.getPurchasePrice()).isEqualByComparingTo("9.00");
        assertThat(productService.savedProduct.getCostPrice()).isEqualByComparingTo("8.00");
        InvProduct data = (InvProduct) result.get(AjaxResult.DATA_TAG);
        assertThat(data.getPurchasePrice()).isEqualByComparingTo("9.00");
        assertThat(data.getCostPrice()).isEqualByComparingTo("8.00");
    }

    @Test
    @DisplayName("商品删除成功响应必须禁止缓存")
    void removeShouldDisableCaching()
    {
        InvProductController controller = new InvProductController();
        FakeProductService productService = new FakeProductService();
        ReflectionTestUtils.setField(controller, "productService", productService);
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.remove(new Long[]{10L}, new MockHttpServletRequest(), response);

        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store, max-age=0");
        assertThat(response.getHeader("Pragma")).isEqualTo("no-cache");
    }

    @Test
    @DisplayName("商品更新映射必须持久化普通维护备注")
    void productUpdateMapperShouldPersistRemark() throws Exception
    {
        String mapperSource = Files.readString(
                Path.of("src/main/resources/mapper/inventory/InvProductMapper.xml"),
                StandardCharsets.UTF_8);

        assertThat(mapperSource).contains("<if test=\"remark != null\">remark = #{remark},</if>");
    }

    @Test
    @DisplayName("商品更新映射必须显式清空可选销售与库存数值")
    void productUpdateMapperShouldClearOptionalOperationalValuesExplicitly() throws Exception
    {
        String domainSource = Files.readString(Path.of("src/main/java/com/erp/inventory/domain/InvProduct.java"),
                StandardCharsets.UTF_8);
        String mapperSource = Files.readString(
                Path.of("src/main/resources/mapper/inventory/InvProductMapper.xml"),
                StandardCharsets.UTF_8);

        assertThat(domainSource).contains("getClearSalesPrice()", "getClearSalePrice250g()",
                "getClearSalePrice500g()", "getClearSafetyStockMin()", "getClearSafetyStockMax()");
        assertThat(domainSource).contains("JsonProperty.Access.WRITE_ONLY");
        assertThat(mapperSource).contains(
                "<when test=\"clearSalesPrice == true\">sales_price = null,</when>",
                "<when test=\"clearSalePrice250g == true\">sale_price_250g = null,</when>",
                "<when test=\"clearSalePrice500g == true\">sale_price_500g = null,</when>",
                "<when test=\"clearSafetyStockMin == true\">safety_stock_min = null,</when>",
                "<when test=\"clearSafetyStockMax == true\">safety_stock_max = null,</when>");
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

    private static String exportedReferenceCost(MockHttpServletResponse response) throws Exception
    {
        try (Workbook workbook = WorkbookFactory.create(
                new ByteArrayInputStream(response.getContentAsByteArray())))
        {
            Sheet sheet = workbook.getSheetAt(0);
            Row row = sheet.getRow(3);
            Cell cell = row.getCell(7);
            return cell == null ? "" : cell.toString();
        }
    }

    private static void setCostViewer()
    {
        LoginUser loginUser = new LoginUser();
        loginUser.setUserid(42L);
        loginUser.setUsername("product-cost-viewer");
        loginUser.setPermissions(Set.of("inv:cost:view"));
        SecurityContextHolder.setUserId("42");
        SecurityContextHolder.set(SecurityConstants.LOGIN_USER, loginUser);
    }

    private static class FakeProductService implements IInvProductService
    {
        private InvProduct product;
        private InvProduct savedProduct;
        private InvProduct saveResult;

        @Override
        public List<InvProduct> selectProductList(InvProduct product, Long selectedShopDeptId)
        {
            return Collections.singletonList(this.product);
        }

        @Override
        public InvProduct selectProductById(Long productId, Long selectedShopDeptId)
        {
            return product;
        }

        @Override
        public InvProduct saveProduct(InvProduct product, Long selectedShopDeptId)
        {
            savedProduct = product;
            return saveResult == null ? product : saveResult;
        }

        @Override
        public void deleteProductByIds(Long[] productIds, Long selectedShopDeptId)
        {
        }

        @Override
        public String importProduct(List<InvProduct> productList, boolean updateSupport, Long selectedShopDeptId)
        {
            return "";
        }
    }
}
