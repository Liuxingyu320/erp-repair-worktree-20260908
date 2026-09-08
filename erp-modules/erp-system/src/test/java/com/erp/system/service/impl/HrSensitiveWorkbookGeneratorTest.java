package com.erp.system.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class HrSensitiveWorkbookGeneratorTest
{
    @Test
    void generatesStreamingStringCellsAndNeutralizesFormulaInjection() throws Exception
    {
        byte[] bytes=new HrSensitiveWorkbookGenerator().generate(List.of("employeeName","bankAccount"),
                List.of(Map.of("employeeName","=HYPERLINK(\"bad\")","bankAccount","+6222")));
        try(XSSFWorkbook workbook=new XSSFWorkbook(new ByteArrayInputStream(bytes)))
        {
            var row=workbook.getSheetAt(0).getRow(1);
            assertThat(row.getCell(0).getCellType()).isEqualTo(CellType.STRING);
            assertThat(row.getCell(0).getStringCellValue()).isEqualTo("'=HYPERLINK(\"bad\")");
            assertThat(row.getCell(1).getStringCellValue()).isEqualTo("'+6222");
        }
        String source=Files.readString(Paths.get(System.getProperty("user.dir"))
                .resolve("src/main/java/com/erp/system/service/impl/HrSensitiveWorkbookGenerator.java"),
                StandardCharsets.UTF_8);
        assertThat(source).contains("SXSSFWorkbook","setCompressTempFiles(true)","workbook.dispose()")
                .doesNotContain("XSSFWorkbook workbook=new XSSFWorkbook");
    }
}
