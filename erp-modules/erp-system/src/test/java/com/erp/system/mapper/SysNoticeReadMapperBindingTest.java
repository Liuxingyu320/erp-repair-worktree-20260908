package com.erp.system.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.erp.system.domain.SysNotice;
import com.erp.system.domain.SysNoticeRead;

@DisplayName("公告已读 Mapper 绑定")
class SysNoticeReadMapperBindingTest
{
    private static final String MAPPER_CLASS = "com.erp.system.mapper.SysNoticeReadMapper";
    private static final String MAPPER_XML = "mapper/system/SysNoticeReadMapper.xml";

    @Test
    @DisplayName("全部已读接口仅绑定当前用户ID")
    void insertAllUnreadNoticesShouldExposeUserOnlyContract() throws Exception
    {
        java.lang.reflect.Method method = SysNoticeReadMapper.class
                .getMethod("insertAllUnreadNotices", Long.class);

        assertThat(method.getParameterCount()).isEqualTo(1);
        assertThat(method.getParameters()[0].getAnnotation(Param.class).value()).isEqualTo("userId");
        assertThat(method.getReturnType()).isEqualTo(int.class);
    }

    @Test
    @DisplayName("全部已读SQL只插入接收人快照内仍在线且当前用户尚未读取的公告")
    void insertAllUnreadNoticesShouldBindIdempotentActiveUnreadInsert() throws Exception
    {
        Configuration configuration = new Configuration();
        // Explicit aliases avoid MyBatis VFS scanning the large dirty build tree.
        configuration.getTypeAliasRegistry().registerAlias("SysNotice", SysNotice.class);
        configuration.getTypeAliasRegistry().registerAlias("SysNoticeRead", SysNoticeRead.class);
        try (InputStream input = Resources.getResourceAsStream(MAPPER_XML))
        {
            new XMLMapperBuilder(input, configuration, MAPPER_XML, configuration.getSqlFragments()).parse();
        }
        Map<String, Object> params = new HashMap<>();
        params.put("userId", 42L);

        BoundSql boundSql = configuration.getMappedStatement(MAPPER_CLASS + ".insertAllUnreadNotices")
                .getBoundSql(params);
        String sql = boundSql.getSql().replaceAll("\\s+", " ").trim().toLowerCase();

        assertThat(sql).startsWith("insert ignore into sys_notice_read (notice_id, user_id, read_time)")
                .contains("select n.notice_id, ?, sysdate() from sys_notice_recipient nr")
                .contains("inner join sys_notice n on n.notice_id = nr.notice_id")
                .contains("where nr.user_id = ?")
                .contains("n.lifecycle_status = 'published'")
                .contains("n.status = '0'")
                .contains("n.expire_time is null or n.expire_time > sysdate()")
                .contains("not exists")
                .contains("r.notice_id = n.notice_id")
                .contains("r.user_id = ?")
                .doesNotContain(" in (");
        assertThat(boundSql.getParameterMappings()).hasSize(3)
                .allSatisfy(mapping -> assertThat(mapping.getProperty()).isEqualTo("userId"));
    }

    @Test
    @DisplayName("未读数量与全部已读使用相同的发布快照和有效期口径")
    void unreadCountShouldUseSameActiveUnreadPredicate() throws Exception
    {
        String xml;
        try (InputStream input = Resources.getResourceAsStream(MAPPER_XML))
        {
            xml = new String(input.readAllBytes(), StandardCharsets.UTF_8).replaceAll("\\s+", " ").toLowerCase();
        }

        assertThat(xml).contains("<insert id=\"insertallunreadnotices\">")
                .contains("<select id=\"selectunreadcount\" resulttype=\"int\">");
        assertThat(countOccurrences(xml, "from sys_notice_recipient nr")).isGreaterThanOrEqualTo(6);
        assertThat(countOccurrences(xml, "n.lifecycle_status = 'published'")).isGreaterThanOrEqualTo(6);
        assertThat(countOccurrences(xml, "n.expire_time is null or n.expire_time &gt; sysdate()"))
                .isGreaterThanOrEqualTo(6);
    }

    private static int countOccurrences(String source, String value)
    {
        int count = 0;
        int index = 0;
        while ((index = source.indexOf(value, index)) >= 0)
        {
            count++;
            index += value.length();
        }
        return count;
    }
}
