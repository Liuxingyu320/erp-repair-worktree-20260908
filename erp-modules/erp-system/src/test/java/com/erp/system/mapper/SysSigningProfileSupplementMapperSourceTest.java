package com.erp.system.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

class SysSigningProfileSupplementMapperSourceTest
{
    @Test
    void mappersBindIdempotencyLockAndClosedProfileUpdate() throws Exception
    {
        Configuration configuration = new Configuration();
        parse(configuration, "mapper/system/SysSignProfileSupplementAuditMapper.xml");
        parse(configuration, "mapper/system/SysUserProfileMapper.xml");

        assertThat(configuration.hasStatement(
                "com.erp.system.mapper.SysSignProfileSupplementAuditMapper.insertIfAbsent")).isTrue();
        assertThat(configuration.hasStatement(
                "com.erp.system.mapper.SysSignProfileSupplementAuditMapper.selectByRequestIdForUpdate")).isTrue();
        assertThat(configuration.hasStatement(
                "com.erp.system.mapper.SysUserProfileMapper.updateReviewedSigningFacts")).isTrue();

        String audit = text("mapper/system/SysSignProfileSupplementAuditMapper.xml");
        assertThat(audit).contains("on duplicate key update request_id = values(request_id)",
                "where request_id = #{requestId}", "for update", "status = 'COMPLETED'",
                "and del_flag = '0'", "and status = '0'");
        assertThat(configuration.hasStatement(
                "com.erp.system.mapper.SysUserProfileMapper.lockSigningProfileByUserId")).isTrue();
        String profile = fragment(text("mapper/system/SysUserProfileMapper.xml"),
                "<update id=\"updateReviewedSigningFacts\">", "</update>");
        assertThat(profile).contains("current_address", "student_status", "school_name",
                "retirement_status", "income_start_year_month", "request.studentStatus == 'NON_STUDENT'",
                "request.studentStatus == 'STUDENT'", "request.schoolName != null",
                "where user_id = #{request.employeeId}")
                .doesNotContain("contract_type", "contract_start_date", "contract_end_date",
                        "base_salary", "salary_total", "job_grade", "legal_entity", "social_type",
                        "insurance", "service_person");
    }

    @Test
    void signingCandidateReadReturnsReviewedFacts() throws Exception
    {
        String users = text("mapper/system/SysUserMapper.xml");
        String query = fragment(users, "<select id=\"selectSignCandidateUsers\"", "</select>");
        assertThat(query).contains("p.current_address", "p.student_status", "p.school_name",
                "p.retirement_status", "p.income_start_year_month");
    }

    private void parse(Configuration configuration, String resource) throws Exception
    {
        try (var stream = Resources.getResourceAsStream(resource))
        {
            new XMLMapperBuilder(stream, configuration, resource, configuration.getSqlFragments()).parse();
        }
    }

    private String text(String resource) throws Exception
    {
        try (var stream = Resources.getResourceAsStream(resource))
        {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private String fragment(String source, String start, String end)
    {
        int from = source.indexOf(start);
        int to = source.indexOf(end, from);
        assertThat(from).isGreaterThanOrEqualTo(0);
        assertThat(to).isGreaterThan(from);
        return source.substring(from, to + end.length());
    }
}
