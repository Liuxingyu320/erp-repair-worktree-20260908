package com.erp.inventory.service.impl;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import com.erp.inventory.domain.InvProduct;

public class TeaProductExcelParser
{
    private static final int HEADER_ROW_INDEX = 2;
    private static final int DATA_START_ROW_INDEX = 3;

    private TeaProductExcelParser()
    {
    }

    public static ParseResult parse(InputStream inputStream) throws Exception
    {
        DataFormatter formatter = new DataFormatter();
        try (Workbook workbook = WorkbookFactory.create(inputStream))
        {
            Sheet sheet = findTeaSheet(workbook, formatter);
            if (sheet == null)
            {
                return ParseResult.notTeaSheet();
            }
            List<InvProduct> products = new ArrayList<>();
            String currentCategory = "";
            int skippedHeaderRows = 0;
            int skippedBlankRows = 0;
            for (int rowIndex = DATA_START_ROW_INDEX; rowIndex <= sheet.getLastRowNum(); rowIndex++)
            {
                Row row = sheet.getRow(rowIndex);
                if (isRowBlank(row, formatter))
                {
                    skippedBlankRows++;
                    continue;
                }
                String category = text(row, 1, formatter);
                String productName = text(row, 2, formatter);
                if ("上线分类".equals(category) || "产品售卖名称".equals(productName))
                {
                    skippedHeaderRows++;
                    continue;
                }
                if (!category.isEmpty())
                {
                    currentCategory = category;
                }
                if (productName.isEmpty() || currentCategory.isEmpty())
                {
                    skippedBlankRows++;
                    continue;
                }
                products.add(toProduct(row, currentCategory, productName, formatter));
            }
            return ParseResult.teaSheet(products, skippedHeaderRows, skippedBlankRows);
        }
    }

    private static Sheet findTeaSheet(Workbook workbook, DataFormatter formatter)
    {
        for (int i = 0; i < workbook.getNumberOfSheets(); i++)
        {
            Sheet sheet = workbook.getSheetAt(i);
            if (isTeaHeader(sheet.getRow(HEADER_ROW_INDEX), formatter))
            {
                return sheet;
            }
        }
        return null;
    }

    private static InvProduct toProduct(Row row, String currentCategory, String productName, DataFormatter formatter)
    {
        InvProduct product = new InvProduct();
        product.setCategoryName(currentCategory);
        product.setProductName(productName);
        product.setGrade(text(row, 3, formatter));
        product.setSpec(firstText(row, 4, 14, formatter));
        product.setSize(firstText(row, 5, 15, formatter));
        product.setUnit(firstText(row, 17, 6, formatter));
        product.setPurchasePrice(decimal(row, 18, formatter));
        product.setSalePrice250g(decimal(row, 19, formatter));
        product.setSalePrice500g(decimal(row, 20, formatter));
        product.setCostPrice(decimal(row, 21, formatter));
        product.setSalesPrice(firstDecimal(product.getSalePrice500g(), product.getSalePrice250g()));
        product.setProductDescription(firstText(row, 22, 8, formatter));
        product.setSupplierName(text(row, 23, formatter));
        product.setSupplierRemark(text(row, 24, formatter));
        product.setSupplierPhone(text(row, 26, formatter));
        product.setInternalTeaName(text(row, 27, formatter));
        product.setProductCode(text(row, 28, formatter));
        product.setPackageImageUrl(text(row, 9, formatter));
        product.setDryTeaImageUrl(text(row, 10, formatter));
        product.setTeaSoupImageUrl(text(row, 11, formatter));
        product.setLeafBottomImageUrl(text(row, 12, formatter));
        product.setExtraImageUrl(text(row, 13, formatter));
        product.setStatus("0");
        return product;
    }

    private static boolean isTeaHeader(Row header, DataFormatter formatter)
    {
        return header != null
                && "上线分类".equals(text(header, 1, formatter))
                && "产品售卖名称".equals(text(header, 2, formatter));
    }

    private static boolean isRowBlank(Row row, DataFormatter formatter)
    {
        if (row == null || row.getFirstCellNum() < 0)
        {
            return true;
        }
        for (int i = row.getFirstCellNum(); i < row.getLastCellNum(); i++)
        {
            if (!text(row, i, formatter).isEmpty())
            {
                return false;
            }
        }
        return true;
    }

    private static String firstText(Row row, int firstIndex, int fallbackIndex, DataFormatter formatter)
    {
        String first = text(row, firstIndex, formatter);
        return first.isEmpty() ? text(row, fallbackIndex, formatter) : first;
    }

    private static String text(Row row, int index, DataFormatter formatter)
    {
        if (row == null || index < 0)
        {
            return "";
        }
        Cell cell = row.getCell(index);
        if (cell == null)
        {
            return "";
        }
        if (cell.getCellType() == CellType.FORMULA)
        {
            String formula = cell.getCellFormula();
            if (formula != null && formula.startsWith("DISPIMG"))
            {
                return "=" + formula;
            }
            return formatter.formatCellValue(cell).trim();
        }
        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell))
        {
            return formatter.formatCellValue(cell).trim();
        }
        return formatter.formatCellValue(cell).trim();
    }

    private static BigDecimal decimal(Row row, int index, DataFormatter formatter)
    {
        String value = text(row, index, formatter);
        if (value.isEmpty())
        {
            return null;
        }
        String normalized = value.replace(",", "").replace("￥", "").replace("¥", "").replace("元", "").trim();
        try
        {
            return new BigDecimal(normalized);
        }
        catch (NumberFormatException e)
        {
            return null;
        }
    }

    private static BigDecimal firstDecimal(BigDecimal first, BigDecimal fallback)
    {
        return first != null ? first : fallback;
    }

    public static class ParseResult
    {
        private final boolean teaProductSheet;
        private final List<InvProduct> products;
        private final int skippedHeaderRows;
        private final int skippedBlankRows;

        private ParseResult(boolean teaProductSheet, List<InvProduct> products, int skippedHeaderRows, int skippedBlankRows)
        {
            this.teaProductSheet = teaProductSheet;
            this.products = products;
            this.skippedHeaderRows = skippedHeaderRows;
            this.skippedBlankRows = skippedBlankRows;
        }

        public static ParseResult notTeaSheet()
        {
            return new ParseResult(false, List.of(), 0, 0);
        }

        public static ParseResult teaSheet(List<InvProduct> products, int skippedHeaderRows, int skippedBlankRows)
        {
            return new ParseResult(true, products, skippedHeaderRows, skippedBlankRows);
        }

        public boolean isTeaProductSheet()
        {
            return teaProductSheet;
        }

        public List<InvProduct> getProducts()
        {
            return products;
        }

        public int getSkippedHeaderRows()
        {
            return skippedHeaderRows;
        }

        public int getSkippedBlankRows()
        {
            return skippedBlankRows;
        }

        public String toNoticeMessage()
        {
            if (!teaProductSheet)
            {
                return "";
            }
            return "，跳过重复表头" + skippedHeaderRows + "行，跳过空行" + skippedBlankRows + "行";
        }
    }
}
