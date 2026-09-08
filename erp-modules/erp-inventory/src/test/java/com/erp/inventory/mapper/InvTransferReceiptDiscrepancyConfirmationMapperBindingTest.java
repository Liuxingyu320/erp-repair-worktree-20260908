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

@DisplayName("V2调拨差异确认唯一写 Mapper")
class InvTransferReceiptDiscrepancyConfirmationMapperBindingTest
{
    private static final String NAMESPACE =
            "com.erp.inventory.mapper.InvTransferReceiptDiscrepancyConfirmationMapper";
    private static final String XML =
            "mapper/inventory/InvTransferReceiptDiscrepancyConfirmationMapper.xml";

    @Test
    @DisplayName("全部窄方法均绑定到可解析XML")
    void shouldBindEveryNarrowMethod() throws Exception
    {
        Configuration configuration = new Configuration();
        configuration.addMapper(
                InvTransferReceiptDiscrepancyConfirmationMapper.class);
        try (InputStream input = Resources.getResourceAsStream(XML))
        {
            new XMLMapperBuilder(input, configuration, XML,
                    configuration.getSqlFragments()).parse();
        }

        assertThat(Arrays.stream(
                InvTransferReceiptDiscrepancyConfirmationMapper.class
                        .getDeclaredMethods())
                .map(method -> NAMESPACE + "." + method.getName()))
                .allMatch(configuration::hasStatement)
                .containsExactlyInAnyOrder(
                        NAMESPACE + ".selectCaseIdForUpdate",
                        NAMESPACE + ".selectByRequestIdForUpdate",
                        NAMESPACE + ".insertConfirmationEvent");
    }

    @Test
    @DisplayName("锁序和唯一DML保持关系约束追加语义")
    void shouldKeepLocksAndOnlyConditionalAppend() throws Exception
    {
        String xml;
        try (InputStream input = Resources.getResourceAsStream(XML))
        {
            xml = new String(input.readAllBytes(), StandardCharsets.UTF_8)
                    .toLowerCase().replaceAll("\\s+", " ");
        }
        String caseLock = between(xml,
                "<select id=\"selectcaseidforupdate\"", "</select>");
        String requestLock = between(xml,
                "<select id=\"selectbyrequestidforupdate\"", "</select>");
        String insert = between(xml,
                "<insert id=\"insertconfirmationevent\"", "</insert>");

        assertThat(caseLock).contains(
                "from inv_transfer_receipt_discrepancy_case",
                "where discrepancy_case_id = #{caseid}", "for update");
        assertThat(requestLock).contains(
                "from inv_transfer_receipt_discrepancy_confirmation_event",
                "where request_id = #{requestid}", "for update");
        assertThat(insert).contains(
                "keyproperty=\"generatedid.value\"",
                "insert into inv_transfer_receipt_discrepancy_confirmation_event",
                "from inv_transfer_receipt_discrepancy_case c",
                "inner join inv_transfer_shipment_receipt r",
                "inner join inv_transfer_order o",
                "c.case_version = #{confirmation.caseversion}",
                "c.fact_fingerprint = #{confirmation.factfingerprint}",
                "c.status = 'awaiting_confirmation'",
                "#{confirmation.decision} in ('confirmed', 'disputed')",
                "#{confirmation.note} is null",
                "length(#{confirmation.note}) between 1 and 500",
                "#{confirmation.partyrole} = 'source'",
                "#{confirmation.partyrole} = 'target'",
                "from_warehouse_id", "to_warehouse_id",
                "not exists ( select 1 from inv_transfer_receipt_discrepancy_confirmation_event prior",
                "prior.request_id = #{confirmation.requestid}");
        assertThat(count(xml, "<insert ")).isEqualTo(1);
        assertThat(xml).doesNotContain("<update ", "<delete ",
                "inv_transfer_discrepancy ");
    }

    private static String between(String source, String start, String end)
    {
        int from = source.indexOf(start);
        int to = source.indexOf(end, from);
        assertThat(from).isGreaterThanOrEqualTo(0);
        assertThat(to).isGreaterThan(from);
        return source.substring(from, to + end.length());
    }

    private static int count(String value, String token)
    {
        return value.split(java.util.regex.Pattern.quote(token), -1).length
                - 1;
    }
}
