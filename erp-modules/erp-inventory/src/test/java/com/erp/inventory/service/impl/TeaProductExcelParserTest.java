package com.erp.inventory.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.erp.inventory.domain.InvProduct;

@DisplayName("茶叶商品 Excel 解析")
class TeaProductExcelParserTest
{
    @Test
    @DisplayName("识别茶叶商品表头并映射字段")
    void shouldParseTeaProductRows() throws Exception
    {
        TeaProductExcelParser.ParseResult result = TeaProductExcelParser.parse(workbookBytes());

        assertThat(result.isTeaProductSheet()).isTrue();
        assertThat(result.getSkippedHeaderRows()).isEqualTo(1);
        assertThat(result.getSkippedBlankRows()).isEqualTo(1);
        assertThat(result.getProducts()).hasSize(2);

        InvProduct first = result.getProducts().get(0);
        assertThat(first.getCategoryName()).isEqualTo("乌龙茶");
        assertThat(first.getProductName()).isEqualTo("清香铁观音");
        assertThat(first.getGrade()).isEqualTo("一级");
        assertThat(first.getSpec()).isEqualTo("250g/罐");
        assertThat(first.getSize()).isEqualTo("65*85*160mm");
        assertThat(first.getUnit()).isEqualTo("斤");
        assertThat(first.getPurchasePrice()).isEqualByComparingTo(new BigDecimal("26.50"));
        assertThat(first.getSalePrice250g()).isEqualByComparingTo(new BigDecimal("60"));
        assertThat(first.getSalePrice500g()).isEqualByComparingTo(new BigDecimal("120"));
        assertThat(first.getSalesPrice()).isEqualByComparingTo(new BigDecimal("120"));
        assertThat(first.getCostPrice()).isEqualByComparingTo(new BigDecimal("84"));
        assertThat(first.getSupplierName()).isEqualTo("泉州安溪今世缘茶业有限公司");
        assertThat(first.getSupplierPhone()).isEqualTo("13950190015");
        assertThat(first.getSupplierRemark()).isEqualTo("含专票、含运费、含袋子");
        assertThat(first.getInternalTeaName()).isEqualTo("001铁观音");
        assertThat(first.getProductCode()).isEqualTo("TGY0103001");
        assertThat(first.getProductDescription()).contains("铁观音茶介于绿茶和红茶之间");
        assertThat(first.getPackageImageUrl()).isEqualTo("=DISPIMG(\"ID_PACKAGE\",1)");

        InvProduct second = result.getProducts().get(1);
        assertThat(second.getCategoryName()).isEqualTo("乌龙茶");
        assertThat(second.getProductName()).isEqualTo("武夷大红袍");
        assertThat(second.getSalesPrice()).isEqualByComparingTo(new BigDecimal("270"));
    }

    @Test
    @DisplayName("非茶叶商品格式返回未识别")
    void shouldIgnoreNonTeaSheet() throws Exception
    {
        try (XSSFWorkbook workbook = new XSSFWorkbook())
        {
            Sheet sheet = workbook.createSheet("普通模板");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("商品名称");
            header.createCell(1).setCellValue("商品编码");
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            workbook.write(output);

            TeaProductExcelParser.ParseResult result = TeaProductExcelParser.parse(new ByteArrayInputStream(output.toByteArray()));

            assertThat(result.isTeaProductSheet()).isFalse();
            assertThat(result.getProducts()).isEmpty();
        }
    }

    private ByteArrayInputStream workbookBytes() throws Exception
    {
        XSSFWorkbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("上线官网资料 (2)");
        sheet.createRow(0);
        sheet.createRow(1);
        Row header = sheet.createRow(2);
        set(header, 1, "上线分类");
        set(header, 2, "产品售卖名称");
        set(header, 3, "等级");
        set(header, 4, "规格");
        set(header, 5, "尺寸");
        set(header, 6, "订购单位");
        set(header, 8, "产品描述");
        set(header, 9, "产品外包装图片");
        set(header, 14, "规格");
        set(header, 15, "尺寸");
        set(header, 17, "单位");
        set(header, 18, "进货价格");
        set(header, 19, "售价250g");
        set(header, 20, "售价500g");
        set(header, 21, "成本500g");
        set(header, 22, "产品描述");
        set(header, 23, "供应商名称");
        set(header, 24, "备注");
        set(header, 26, "手机");
        set(header, 27, "茶种名称（内部信息，客户不可见）");
        set(header, 28, "编号");

        Row first = sheet.createRow(3);
        set(first, 1, "乌龙茶");
        set(first, 2, "清香铁观音");
        set(first, 3, "一级");
        set(first, 4, "250g/罐");
        set(first, 5, "65*85*160mm");
        set(first, 9, "=DISPIMG(\"ID_PACKAGE\",1)");
        set(first, 17, "斤");
        set(first, 18, 26.5);
        set(first, 19, 60);
        set(first, 20, 120);
        set(first, 21, 84);
        set(first, 22, "铁观音茶介于绿茶和红茶之间");
        set(first, 23, "泉州安溪今世缘茶业有限公司");
        set(first, 24, "含专票、含运费、含袋子");
        set(first, 26, "13950190015");
        set(first, 27, "001铁观音");
        set(first, 28, "TGY0103001");

        Row second = sheet.createRow(4);
        set(second, 2, "武夷大红袍");
        set(second, 3, "一级");
        set(second, 14, "150g/罐");
        set(second, 17, "斤");
        set(second, 18, 61);
        set(second, 19, 135);
        set(second, 20, 270);
        set(second, 21, 189);
        set(second, 23, "武夷山市大齐茶业有限公司");
        set(second, 28, "DHP0103001");

        Row repeatedHeader = sheet.createRow(5);
        set(repeatedHeader, 1, "上线分类");
        set(repeatedHeader, 2, "产品售卖名称");
        sheet.createRow(6);

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        workbook.write(output);
        workbook.close();
        return new ByteArrayInputStream(output.toByteArray());
    }

    private void set(Row row, int index, String value)
    {
        row.createCell(index).setCellValue(value);
    }

    private void set(Row row, int index, double value)
    {
        row.createCell(index).setCellValue(value);
    }
}
