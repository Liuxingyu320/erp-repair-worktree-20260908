package com.erp.inventory.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.List;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import com.erp.inventory.domain.InvProduct;

@DisplayName("商品采购表导出")
class InvProductPurchaseExcelExporterTest
{
    @Test
    @DisplayName("按采购表样式导出商品并合并连续分类")
    void shouldExportPurchaseSheetLayout()
            throws Exception
    {
        InvProduct first = product(2L, 1L, "乌龙茶", "大红袍", "一级", "150g/罐", "斤", "大齐茶业", "13305995232");
        InvProduct second = product(1L, 1L, "乌龙茶", "铁观音", "一级", "250g/罐", "斤", "今世缘", "13950190015");

        MockHttpServletResponse response = new MockHttpServletResponse();
        InvProductPurchaseExcelExporter.export(response, List.of(first, second));

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(response.getContentAsByteArray())))
        {
            var sheet = workbook.getSheet("供应链平台产品2026版");
            assertThat(sheet).isNotNull();
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("茶叶采购表（最终样）");
            assertThat(sheet.getRow(2).getCell(0).getStringCellValue()).isEqualTo("序号");
            assertThat(sheet.getRow(2).getCell(7).getStringCellValue()).isEqualTo("参考成本价");
            assertThat(sheet.getRow(3).getCell(2).getStringCellValue()).isEqualTo("铁观音");
            assertThat(sheet.getRow(4).getCell(2).getStringCellValue()).isEqualTo("大红袍");
            assertThat(sheet.getMergedRegions()).contains(new CellRangeAddress(3, 4, 1, 1));
        }
    }

    @Test
    @DisplayName("参考成本价不再按权限留空")
    void shouldAlwaysExportReferenceCost()
            throws Exception
    {
        MockHttpServletResponse response = new MockHttpServletResponse();
        InvProductPurchaseExcelExporter.export(response, List.of(product(1L, 1L, "乌龙茶", "铁观音",
                "一级", "250g/罐", "斤", "今世缘", "13950190015")));

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(response.getContentAsByteArray())))
        {
            assertThat(workbook.getSheetAt(0).getRow(3).getCell(7).getNumericCellValue()).isEqualTo(40.63D);
        }
    }

    private static InvProduct product(Long productId, Long categoryId, String categoryName, String productName,
                                      String grade, String spec, String unit, String supplierName, String supplierPhone)
    {
        InvProduct product = new InvProduct();
        product.setProductId(productId);
        product.setCategoryId(categoryId);
        product.setCategoryName(categoryName);
        product.setProductName(productName);
        product.setGrade(grade);
        product.setSpec(spec);
        product.setUnit(unit);
        product.setProductDescription("产品描述");
        product.setCostPrice(new BigDecimal("40.63"));
        product.setSupplierName(supplierName);
        product.setSupplierPhone(supplierPhone);
        return product;
    }
}
