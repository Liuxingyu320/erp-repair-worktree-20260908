package com.erp.inventory.util;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.RegionUtil;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import com.erp.inventory.domain.InvProduct;

public final class InvProductPurchaseExcelExporter
{
    private static final String SHEET_NAME = "供应链平台产品2026版";
    private static final String TITLE = "茶叶采购表（最终样）";
    private static final String[] HEADERS = {
            "序号", "上线分类", "产品类别名称", "等级", "规格",
            "产品描述", "补货单位", "参考成本价", "供应商名称", "手机"
    };
    private static final int HEADER_ROW_INDEX = 2;
    private static final int DATA_ROW_INDEX = 3;

    private InvProductPurchaseExcelExporter()
    {
    }

    public static void export(HttpServletResponse response, List<InvProduct> products) throws IOException
    {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");

        try (XSSFWorkbook workbook = new XSSFWorkbook())
        {
            Sheet sheet = workbook.createSheet(SHEET_NAME);
            Styles styles = createStyles(workbook);
            configureSheet(sheet);
            createTitle(sheet, styles.title);
            createHeader(sheet, styles);
            List<InvProduct> rows = sortedProducts(products);
            writeDataRows(sheet, rows, styles);
            mergeRepeatedCategoryCells(sheet, rows);
            mergeRepeatedSupplierCells(sheet, rows);
            workbook.write(response.getOutputStream());
        }
    }

    private static void configureSheet(Sheet sheet)
    {
        int[] widths = {9, 13, 22, 11, 13, 54, 13, 21, 26, 25};
        for (int i = 0; i < widths.length; i++)
        {
            sheet.setColumnWidth(i, widths[i] * 256);
        }
        sheet.createFreezePane(0, DATA_ROW_INDEX);
    }

    private static void createTitle(Sheet sheet, CellStyle style)
    {
        Row row = sheet.createRow(0);
        row.setHeightInPoints(24);
        Cell cell = row.createCell(0);
        cell.setCellValue(TITLE);
        cell.setCellStyle(style);
        sheet.addMergedRegion(new CellRangeAddress(0, 1, 0, HEADERS.length - 1));
    }

    private static void createHeader(Sheet sheet, Styles styles)
    {
        Row row = sheet.createRow(HEADER_ROW_INDEX);
        row.setHeightInPoints(22);
        for (int i = 0; i < HEADERS.length; i++)
        {
            Cell cell = row.createCell(i);
            cell.setCellValue(HEADERS[i]);
            cell.setCellStyle(i == 2 || i == 7 ? styles.greenHeader : styles.yellowHeader);
        }
    }

    private static List<InvProduct> sortedProducts(List<InvProduct> products)
    {
        List<InvProduct> rows = new ArrayList<>(products == null ? List.of() : products);
        rows.sort(Comparator.comparing(InvProduct::getCategoryId, Comparator.nullsLast(Long::compareTo))
                .thenComparing(InvProduct::getProductId, Comparator.nullsLast(Long::compareTo)));
        return rows;
    }

    private static void writeDataRows(Sheet sheet, List<InvProduct> rows, Styles styles)
    {
        for (int i = 0; i < rows.size(); i++)
        {
            InvProduct product = rows.get(i);
            Row row = sheet.createRow(DATA_ROW_INDEX + i);
            row.setHeightInPoints(30);
            setNumberCell(row, 0, i + 1, styles.center);
            setStringCell(row, 1, product.getCategoryName(), styles.center);
            setStringCell(row, 2, product.getProductName(), styles.center);
            setStringCell(row, 3, product.getGrade(), styles.center);
            setStringCell(row, 4, product.getSpec(), styles.center);
            setStringCell(row, 5, product.getProductDescription(), styles.description);
            setStringCell(row, 6, product.getUnit(), styles.center);
            setMoneyCell(row, 7, product.getCostPrice(), styles.money);
            setStringCell(row, 8, product.getSupplierName(), styles.boldWrapCenter);
            setStringCell(row, 9, product.getSupplierPhone(), styles.boldWrapCenter);
        }
    }

    private static void mergeRepeatedCategoryCells(Sheet sheet, List<InvProduct> rows)
    {
        mergeRuns(sheet, rows, 1, (left, right) -> Objects.equals(left.getCategoryId(), right.getCategoryId())
                && Objects.equals(text(left.getCategoryName()), text(right.getCategoryName())));
    }

    private static void mergeRepeatedSupplierCells(Sheet sheet, List<InvProduct> rows)
    {
        mergeRuns(sheet, rows, 8, InvProductPurchaseExcelExporter::sameSupplierRun);
        mergeRuns(sheet, rows, 9, InvProductPurchaseExcelExporter::sameSupplierRun);
    }

    private static boolean sameSupplierRun(InvProduct left, InvProduct right)
    {
        return Objects.equals(left.getCategoryId(), right.getCategoryId())
                && Objects.equals(text(left.getSupplierName()), text(right.getSupplierName()))
                && Objects.equals(text(left.getSupplierPhone()), text(right.getSupplierPhone()));
    }

    private static void mergeRuns(Sheet sheet, List<InvProduct> rows, int column, ProductRunMatcher matcher)
    {
        int start = 0;
        for (int index = 1; index <= rows.size(); index++)
        {
            boolean sameRun = index < rows.size() && matcher.matches(rows.get(index - 1), rows.get(index));
            if (sameRun)
            {
                continue;
            }
            int end = index - 1;
            if (end > start)
            {
                CellRangeAddress region = new CellRangeAddress(DATA_ROW_INDEX + start, DATA_ROW_INDEX + end, column, column);
                sheet.addMergedRegion(region);
                applyMergedBorder(region, sheet);
            }
            start = index;
        }
    }

    private static void applyMergedBorder(CellRangeAddress region, Sheet sheet)
    {
        RegionUtil.setBorderTop(BorderStyle.THIN, region, sheet);
        RegionUtil.setBorderBottom(BorderStyle.THIN, region, sheet);
        RegionUtil.setBorderLeft(BorderStyle.THIN, region, sheet);
        RegionUtil.setBorderRight(BorderStyle.THIN, region, sheet);
    }

    private static void setStringCell(Row row, int column, String value, CellStyle style)
    {
        Cell cell = row.createCell(column);
        cell.setCellValue(text(value));
        cell.setCellStyle(style);
    }

    private static void setNumberCell(Row row, int column, int value, CellStyle style)
    {
        Cell cell = row.createCell(column);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    private static void setMoneyCell(Row row, int column, BigDecimal value, CellStyle style)
    {
        Cell cell = row.createCell(column);
        if (value != null)
        {
            cell.setCellValue(value.doubleValue());
        }
        cell.setCellStyle(style);
    }

    private static String text(String value)
    {
        return value == null ? "" : value;
    }

    private static Styles createStyles(XSSFWorkbook workbook)
    {
        Font titleFont = workbook.createFont();
        titleFont.setFontName("宋体");
        titleFont.setFontHeightInPoints((short) 16);
        titleFont.setBold(true);

        Font normalFont = workbook.createFont();
        normalFont.setFontName("宋体");
        normalFont.setFontHeightInPoints((short) 11);

        Font boldFont = workbook.createFont();
        boldFont.setFontName("宋体");
        boldFont.setFontHeightInPoints((short) 11);
        boldFont.setBold(true);

        CellStyle title = workbook.createCellStyle();
        title.setFont(titleFont);
        title.setAlignment(HorizontalAlignment.CENTER);
        title.setVerticalAlignment(VerticalAlignment.CENTER);

        CellStyle center = bordered(workbook);
        center.setFont(normalFont);
        center.setAlignment(HorizontalAlignment.CENTER);
        center.setVerticalAlignment(VerticalAlignment.CENTER);

        CellStyle description = bordered(workbook);
        description.setFont(normalFont);
        description.setAlignment(HorizontalAlignment.LEFT);
        description.setVerticalAlignment(VerticalAlignment.CENTER);
        description.setWrapText(true);

        DataFormat dataFormat = workbook.createDataFormat();
        CellStyle money = bordered(workbook);
        money.setFont(normalFont);
        money.setAlignment(HorizontalAlignment.CENTER);
        money.setVerticalAlignment(VerticalAlignment.CENTER);
        money.setDataFormat(dataFormat.getFormat("#,##0.00"));

        CellStyle boldWrapCenter = bordered(workbook);
        boldWrapCenter.setFont(boldFont);
        boldWrapCenter.setAlignment(HorizontalAlignment.CENTER);
        boldWrapCenter.setVerticalAlignment(VerticalAlignment.CENTER);
        boldWrapCenter.setWrapText(true);

        CellStyle yellowHeader = headerStyle(workbook, normalFont, new byte[]{(byte) 255, (byte) 255, 0});
        CellStyle greenHeader = headerStyle(workbook, normalFont, new byte[]{(byte) 146, (byte) 208, 80});

        return new Styles(title, yellowHeader, greenHeader, center, description, money, boldWrapCenter);
    }

    private static CellStyle headerStyle(XSSFWorkbook workbook, Font font, byte[] rgb)
    {
        XSSFCellStyle style = (XSSFCellStyle) bordered(workbook);
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setFillForegroundColor(new XSSFColor(rgb, null));
        return style;
    }

    private static CellStyle bordered(Workbook workbook)
    {
        CellStyle style = workbook.createCellStyle();
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setTopBorderColor(IndexedColors.BLACK.getIndex());
        style.setBottomBorderColor(IndexedColors.BLACK.getIndex());
        style.setLeftBorderColor(IndexedColors.BLACK.getIndex());
        style.setRightBorderColor(IndexedColors.BLACK.getIndex());
        return style;
    }

    @FunctionalInterface
    private interface ProductRunMatcher
    {
        boolean matches(InvProduct left, InvProduct right);
    }

    private record Styles(CellStyle title, CellStyle yellowHeader, CellStyle greenHeader, CellStyle center,
                          CellStyle description, CellStyle money, CellStyle boldWrapCenter)
    {
    }
}
