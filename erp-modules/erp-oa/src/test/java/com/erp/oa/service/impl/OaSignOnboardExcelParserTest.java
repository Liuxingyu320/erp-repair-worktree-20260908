package com.erp.oa.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

@DisplayName("入职签约 Excel 严格解析")
class OaSignOnboardExcelParserTest
{
    private static final List<String> REQUIRED_HEADERS = List.of(
            "姓名", "社保类型", "员工岗位", "城市等级", "合同类型", "员工职级", "工作地",
            "身份证号", "联系电话", "劳动合同期限形式", "劳动合同起始日期", "劳动合同结束日期",
            "工时制度", "综合工资", "底薪", "综合岗位津贴", "综合驻外补贴", "月度绩效津贴");

    @Test
    @DisplayName("按表头名读取重排列并保留十一位数值手机号")
    void shouldReadReorderedRequiredHeadersAndNumericPhone() throws Exception
    {
        List<String> headers = new ArrayList<>(REQUIRED_HEADERS);
        Collections.reverse(headers);

        OaSignOnboardExcelParser.ParsedRow row = new OaSignOnboardExcelParser()
                .parse(workbook(headers, validValues(), true, null)).getRows().get(0);

        assertThat(row.getErrors()).isEmpty();
        assertThat(row.getSourceRowNumber()).isEqualTo(2);
        assertThat(row.getSnapshot().getEmployeeName()).isEqualTo("李曼");
        assertThat(row.getSnapshot().getPhone()).isEqualTo("13800001111");
        assertThat(row.getSnapshot().getJobGradeCode()).isEqualTo("9");
        assertThat(row.getSnapshot().getSalaryTotal()).isEqualByComparingTo("8000.00");
        assertThat(row.getRowHash()).hasSize(64);
    }

    @Test
    @DisplayName("数据公式不求值并形成明确的行级硬错误")
    void shouldRejectFormulaCellsWithoutEvaluatingThem() throws Exception
    {
        OaSignOnboardExcelParser.ParsedRow row = new OaSignOnboardExcelParser()
                .parse(workbook(REQUIRED_HEADERS, validValues(), false, "综合工资"))
                .getRows().get(0);

        assertThat(row.getErrors()).containsExactly("FORMULA_NOT_ALLOWED:综合工资");
        assertThat(row.getSnapshot().getSalaryTotal()).isNull();
    }

    @Test
    @DisplayName("员工等级只接受2至9且不把10级静默归入最高档")
    void shouldRejectGradeOutsideTwoThroughNine() throws Exception
    {
        Map<String, String> values = validValues();
        values.put("员工职级", "10级");

        OaSignOnboardExcelParser.ParsedRow row = new OaSignOnboardExcelParser()
                .parse(workbook(REQUIRED_HEADERS, values, false, null)).getRows().get(0);

        assertThat(row.getErrors()).contains("INVALID_JOB_GRADE");
        assertThat(row.getSnapshot().getJobGradeCode()).isNull();
    }

    private MockMultipartFile workbook(List<String> headers, Map<String, String> values,
            boolean numericPhone, String formulaHeader) throws Exception
    {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
                ByteArrayOutputStream output = new ByteArrayOutputStream())
        {
            Sheet sheet = workbook.createSheet(OaSignOnboardExcelParser.SHEET_NAME);
            Row header = sheet.createRow(0);
            Row data = sheet.createRow(1);
            for (int index = 0; index < headers.size(); index++)
            {
                String name = headers.get(index);
                header.createCell(index).setCellValue(name);
                if (name.equals(formulaHeader))
                {
                    data.createCell(index).setCellFormula("6000+1000+500+500");
                }
                else if (numericPhone && "联系电话".equals(name))
                {
                    data.createCell(index).setCellValue(13800001111D);
                }
                else
                {
                    data.createCell(index).setCellValue(values.get(name));
                }
            }
            workbook.write(output);
            return new MockMultipartFile("file", "签约数据.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    output.toByteArray());
        }
    }

    private Map<String, String> validValues()
    {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("姓名", "李曼");
        values.put("社保类型", "有社保");
        values.put("员工岗位", "区域运营总监");
        values.put("城市等级", "二线");
        values.put("合同类型", "劳动合同");
        values.put("员工职级", "9级");
        values.put("工作地", "北京");
        values.put("身份证号", "330102199001011234");
        values.put("联系电话", "13800001111");
        values.put("劳动合同期限形式", "固定期限");
        values.put("劳动合同起始日期", "2026-07-18");
        values.put("劳动合同结束日期", "2029-07-17");
        values.put("工时制度", "标准工时制");
        values.put("综合工资", "8000.00");
        values.put("底薪", "6000.00");
        values.put("综合岗位津贴", "1000.00");
        values.put("综合驻外补贴", "500.00");
        values.put("月度绩效津贴", "500.00");
        return values;
    }
}
