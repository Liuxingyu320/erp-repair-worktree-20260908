package com.erp.system.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.apache.ibatis.io.Resources;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("用户档案派生字段写入边界")
class SysUserProfileDerivedWriteBoundaryTest
{
    private static final String[] DERIVED_COLUMNS = {
            "company_name",
            "dept_level1_name",
            "dept_level2_name",
            "dept_level3_name",
            "store_name",
            "position_names",
            "department_supervisor",
            "work_years",
            "company_years",
            "contract_term"
    };

    @Test
    @DisplayName("新增和修改档案不写入实时派生列")
    void writesShouldExcludeDerivedColumns() throws Exception
    {
        String xml = mapperXml();
        String insert = fragment(xml, "<insert id=\"insertUserProfile\"", "</insert>");
        String update = fragment(xml, "<update id=\"updateUserProfile\"", "</update>");

        assertThat(insert).doesNotContain(DERIVED_COLUMNS);
        assertThat(update).doesNotContain(DERIVED_COLUMNS);
    }

    @Test
    @DisplayName("历史派生列仍保留只读查询能力")
    void selectsShouldRetainDerivedColumnsForAudit() throws Exception
    {
        String select = fragment(mapperXml(), "<sql id=\"selectUserProfileVo\"", "</sql>");

        assertThat(select).contains(DERIVED_COLUMNS);
    }

    private static String mapperXml() throws Exception
    {
        return new String(Resources.getResourceAsStream("mapper/system/SysUserProfileMapper.xml").readAllBytes(),
                StandardCharsets.UTF_8);
    }

    private static String fragment(String source, String start, String end)
    {
        int from = source.indexOf(start);
        int to = source.indexOf(end, from);
        assertThat(from).isGreaterThanOrEqualTo(0);
        assertThat(to).isGreaterThan(from);
        return source.substring(from, to + end.length());
    }
}
