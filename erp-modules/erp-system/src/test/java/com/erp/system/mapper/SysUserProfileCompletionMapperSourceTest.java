package com.erp.system.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.apache.ibatis.io.Resources;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("员工自助补资料持久化边界")
class SysUserProfileCompletionMapperSourceTest
{
    @Test
    @DisplayName("自助更新SQL只写个人白名单字段")
    void completionUpdateShouldNotWriteHrManagedFields() throws Exception
    {
        String xml = new String(Resources.getResourceAsStream("mapper/system/SysUserProfileMapper.xml").readAllBytes(),
                StandardCharsets.UTF_8);
        int start = xml.indexOf("id=\"updateProfileCompletionFields\"");
        String updateSql = start < 0 ? "" : xml.substring(start, xml.indexOf("</update>", start));

        assertThat(updateSql)
                .contains("birth_date = #{birthDate}")
                .contains("id_number = #{idNumber}")
                .contains("where user_id = #{userId}")
                .doesNotContain("emergency_contact")
                .doesNotContain("bank_name")
                .doesNotContain("bank_account")
                .doesNotContain("employee_no")
                .doesNotContain("employee_status")
                .doesNotContain("employee_category")
                .doesNotContain("entry_date")
                .doesNotContain("contract_")
                .doesNotContain("job_grade")
                .doesNotContain("direct_supervisor");
    }
}
