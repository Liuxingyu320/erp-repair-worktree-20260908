package com.erp.system.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("健康证审批发起发件箱 Mapper 绑定")
class HrHealthCertificateApprovalStartOutboxMapperBindingTest
{
    private static final String NAMESPACE =
            "com.erp.system.mapper.HrHealthCertificateApprovalStartOutboxMapper";
    private static final String OUTBOX_XML =
            "mapper/system/HrHealthCertificateApprovalStartOutboxMapper.xml";
    private static final String CERTIFICATE_XML =
            "mapper/system/HrHealthCertificateMapper.xml";

    @Test
    void everyMapperMethodHasXmlStatement() throws Exception
    {
        Configuration configuration = new Configuration();
        try (InputStream input = Resources.getResourceAsStream(OUTBOX_XML))
        {
            new XMLMapperBuilder(input, configuration, OUTBOX_XML,
                    configuration.getSqlFragments()).parse();
        }

        Arrays.stream(HrHealthCertificateApprovalStartOutboxMapper.class
                .getDeclaredMethods()).forEach(method ->
                assertThat(configuration.hasStatement(
                        NAMESPACE + "." + method.getName()))
                        .as(method.getName()).isTrue());
    }

    @Test
    void remoteSuccessAndStaleSubmittingAreRecoverable() throws Exception
    {
        String xml = normalized(resourceText(OUTBOX_XML));

        assertThat(xml).contains(
                "status in ('pending', 'retry')",
                "status = 'remote_succeeded'",
                "status = 'submitting'",
                "update_time &lt;= #{stalesubmittingbefore}",
                "set status = 'remote_succeeded'",
                "remote_instance_id = #{remoteinstanceid}",
                "remote_business_round = #{remotebusinessround}",
                "remote_succeeded_time = sysdate()",
                "status = #{expectedstatus}",
                "version = #{version}",
                "case when o.remote_instance_id is not null and o.remote_business_round = o.business_round then 'remote_succeeded' else 'retry' end",
                "coalesce(o.last_error_code, '') not in (",
                "'remote_round_mismatch'", "'instance_conflict'",
                "'certificate_not_found'", "'certificate_state_changed'",
                "'certificate_version_conflict'",
                "'certificate_round_changed'",
                "<select id=\"selectscopedbyidforupdate\"",
                "${query.params.datascope}", "for update");

        assertThat(between(xml, "<select id=\"selectopsoutboxes\"",
                "</select>")).contains(
                        "left join hr_employee_health_certificate c",
                        "left join sys_user u", "${params.datascope}");
        assertThat(between(xml, "<select id=\"selectsummary\"",
                "</select>")).contains(
                        "left join hr_employee_health_certificate c",
                        "left join sys_user u", "${params.datascope}");
        assertThat(between(xml,
                "<select id=\"selectscopedbyidforupdate\"",
                "</select>")).contains(
                        "inner join hr_employee_health_certificate c",
                        "inner join sys_user u", "${params.datascope}");
    }

    @Test
    void opsProjectionNeverReturnsRequestSnapshot() throws Exception
    {
        String xml = normalized(resourceText(OUTBOX_XML));
        String ops = between(xml, "<select id=\"selectopsoutboxes\"",
                "</select>");

        assertThat(ops).doesNotContain("request_json");
        assertThat(ops).contains("last_error_code", "remote_instance_id",
                "remotebusinessround", "${params.datascope}");
    }

    @Test
    void submittingClearsPreviousInstanceBeforeNewRound() throws Exception
    {
        String xml = normalized(resourceText(CERTIFICATE_XML));
        String submit = between(xml,
                "<update id=\"markapprovalsubmitting\">", "</update>");

        assertThat(submit).contains(
                "review_status = 'approval_submitting'",
                "approval_instance_id = null",
                "last_approval_event_key = null",
                "approval_round = #{approvalround}",
                "version = #{version}",
                "version = version + 1");
    }

    private static String resourceText(String resource) throws Exception
    {
        try (InputStream input = Resources.getResourceAsStream(resource))
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
