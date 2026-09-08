package com.erp.system.service.impl;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Component;
import com.erp.common.core.exception.ServiceException;

/** Streaming, formula-safe sensitive workbook generator. */
@Component
public class HrSensitiveWorkbookGenerator
{
    public byte[] generate(List<String> columns,List<Map<String,Object>> rows)
    {
        SXSSFWorkbook workbook=new SXSSFWorkbook(100);
        workbook.setCompressTempFiles(true);
        try(ByteArrayOutputStream output=new ByteArrayOutputStream())
        {
            Sheet sheet=workbook.createSheet("员工档案");
            Row header=sheet.createRow(0);
            for(int i=0;i<columns.size();i++)
                header.createCell(i,CellType.STRING).setCellValue(columns.get(i));
            for(int r=0;r<rows.size();r++)
            {
                Row row=sheet.createRow(r+1);
                for(int c=0;c<columns.size();c++)
                {
                    Object value=rows.get(r).get(columns.get(c));
                    row.createCell(c,CellType.STRING).setCellValue(safeText(value));
                }
            }
            workbook.write(output);
            return output.toByteArray();
        }
        catch(Exception failure)
        {
            throw new ServiceException("敏感导出工作簿生成失败");
        }
        finally
        {
            try{workbook.close();}catch(Exception ignored){ }
            workbook.dispose();
        }
    }

    private String safeText(Object value)
    {
        String text=value==null?"":String.valueOf(value);
        if(!text.isEmpty()&&"=+-@".indexOf(text.charAt(0))>=0)return "'"+text;
        return text;
    }
}
