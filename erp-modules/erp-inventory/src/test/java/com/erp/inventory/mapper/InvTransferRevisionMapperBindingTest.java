package com.erp.inventory.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.erp.inventory.domain.InvTransferRevision;

@DisplayName("调拨业务版本 Mapper 绑定")
class InvTransferRevisionMapperBindingTest
{
    private static final String NAMESPACE =
            "com.erp.inventory.mapper.InvTransferRevisionMapper";
    private static final String XML =
            "mapper/inventory/InvTransferRevisionMapper.xml";

    @Test
    @DisplayName("版本 Mapper 的所有方法均有可解析 XML 语句")
    void shouldBindEveryMapperMethod() throws Exception
    {
        Configuration configuration = new Configuration();
        configuration.getTypeAliasRegistry().registerAlias(
                "InvTransferRevision", InvTransferRevision.class);
        try (InputStream input = Resources.getResourceAsStream(XML))
        {
            new XMLMapperBuilder(input, configuration, XML,
                    configuration.getSqlFragments()).parse();
        }

        Arrays.stream(InvTransferRevisionMapper.class.getDeclaredMethods())
                .forEach(method -> assertThat(configuration.hasStatement(
                        NAMESPACE + "." + method.getName()))
                        .as(method.getName()).isTrue());
    }

    @Test
    @DisplayName("提交后快照字段不可被审批结果更新覆盖")
    void shouldKeepSubmittedSnapshotsImmutable() throws Exception
    {
        String xml = normalized(resourceText());
        String transition = between(xml,
                "<update id=\"transitionrevision\"", "</update>");
        String draftUpdate = between(xml,
                "<update id=\"updatedraftsnapshot\"", "</update>");

        assertThat(transition).doesNotContain(
                "header_snapshot =", "detail_snapshot =",
                "snapshot_hash =");
        assertThat(transition).contains(
                "and status = #{expectedstatus}");
        assertThat(draftUpdate).contains("and status = 'draft'");
    }

    private static String resourceText() throws Exception
    {
        try (InputStream input = Resources.getResourceAsStream(XML))
        {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String normalized(String value)
    {
        return value.toLowerCase().replaceAll("\\s+", " ").trim();
    }

    private static String between(String value, String start, String end)
    {
        int from = value.indexOf(start);
        assertThat(from).as(start).isGreaterThanOrEqualTo(0);
        int to = value.indexOf(end, from);
        assertThat(to).as(end).isGreaterThan(from);
        return value.substring(from, to + end.length());
    }
}
