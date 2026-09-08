package com.erp.system.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.apache.ibatis.io.Resources;
import org.junit.jupiter.api.Test;
import com.erp.common.core.annotation.Excel;
import com.erp.system.domain.vo.SysUserPiiExportVo;

class SysUserPiiExportMapperSourceTest
{
    @Test
    void sensitiveExportUsesExplicitMappingForEveryExcelColumn() throws Exception
    {
        String xml = new String(Resources.getResourceAsStream(
                "mapper/system/SysUserMapper.xml").readAllBytes(), StandardCharsets.UTF_8);

        String mapping = xml.substring(xml.indexOf("id=\"SysUserPiiExportResult\""),
                xml.indexOf("</resultMap>", xml.indexOf("id=\"SysUserPiiExportResult\"")));
        String select = xml.substring(xml.indexOf("id=\"selectUserPiiExportList\""),
                xml.indexOf("</select>", xml.indexOf("id=\"selectUserPiiExportList\"")));

        assertThat(select).contains("resultMap=\"SysUserPiiExportResult\"")
                .doesNotContain("resultType=\"com.erp.system.domain.vo.SysUserPiiExportVo\"");

        Arrays.stream(SysUserPiiExportVo.class.getDeclaredFields())
                .filter(field -> field.getAnnotation(Excel.class) != null)
                .map(Field::getName)
                .forEach(field -> assertThat(mapping)
                        .as("explicit mapping for export field %s", field)
                        .contains("property=\"" + field + "\""));

        assertThat(mapping)
                .contains("property=\"userId\"                     column=\"user_id\"")
                .contains("property=\"userName\"                   column=\"user_name\"")
                .contains("property=\"birthDate\"                  column=\"birth_date\"")
                .contains("property=\"idNumber\"                   column=\"id_number\"")
                .contains("property=\"bankAccount\"                column=\"bank_account\"");
    }
}
