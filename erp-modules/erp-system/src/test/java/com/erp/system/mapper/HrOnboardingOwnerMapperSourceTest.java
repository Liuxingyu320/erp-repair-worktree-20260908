package com.erp.system.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.apache.ibatis.io.Resources;
import org.junit.jupiter.api.Test;

class HrOnboardingOwnerMapperSourceTest
{
    @Test
    void ownerPickerQueryIsScopedBoundAndReturnsOnlyTheMinimalMaskedProjection() throws Exception
    {
        String xml = new String(Resources.getResourceAsStream(
                "mapper/system/HrOnboardingMapper.xml").readAllBytes(), StandardCharsets.UTF_8);
        String sql = xml.substring(xml.indexOf("id=\"selectScopedOwnerOptions\""),
                xml.indexOf("id=\"selectOnboardingList\""));

        assertThat(sql)
                .contains("${params.dataScope}")
                .contains("u.del_flag = '0'")
                .contains("u.status = '0'")
                .contains("u.nick_name like concat('%', #{keyword}, '%')")
                .contains("p.employee_no like concat('%', #{keyword}, '%')")
                .contains("u.phonenumber like concat('%', #{keyword}, '%')")
                .contains("find_in_set(#{deptId}, d.ancestors)")
                .contains("<foreach collection=\"userIds\"")
                .contains("concat(left(u.phonenumber, 3), '****', right(u.phonenumber, 4))")
                .doesNotContain("u.user_name as", "u.email as", "u.phonenumber as phonenumber");
    }
}
