package com.erp.oa.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import com.erp.oa.domain.OaSignTask;

class OaSignOnboardCompanyWorkBulkMapperBindingTest
{
    @Test
    void companyWorkRowsAreFilteredAndCappedInOneQuery() throws Exception
    {
        Configuration configuration = configuration(
                "mapper/oa/OaSignOnboardImportRowMapper.xml");
        BoundSql bound = configuration.getMappedStatement(
                "com.erp.oa.mapper.OaSignOnboardImportRowMapper"
                        + ".selectSignatureFirstCompanyWorkRows")
                .getBoundSql(Map.of("assignedHrUserId", 101L,
                        "scopeDeptIds", List.of(1171L, 1172L),
                        "maxRows", 100));
        String sql = normalized(bound);

        assertThat(sql)
                .startsWith("select r.* from oa_sign_onboard_import_row r")
                .contains("d.request_id = r.data_request_id")
                .contains("upper(trim(d.signing_sequence)) = 'SIGNATURE_FIRST'")
                .contains("d.signature_sample_bytes is not null")
                .contains("r.status not in ('GENERATING', 'GENERATED', 'SENT', 'PARTIAL_SENT')")
                .contains("(r.task_id is null or t.assigned_hr_user_id = ?)")
                .contains("b.shop_dept_id in ( ? , ? )")
                .contains("order by b.batch_id desc, r.source_row_number asc, r.row_id asc")
                .endsWith("limit ?")
                .doesNotContain("b.status = 'GENERATING'", "r.update_time <=");
    }

    @Test
    void systemAdminCanOmitOwnerFilterWhileKeepingOrganizationScope() throws Exception
    {
        Configuration configuration = configuration(
                "mapper/oa/OaSignOnboardImportRowMapper.xml");
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("assignedHrUserId", null);
        parameters.put("scopeDeptIds", List.of(1171L));
        parameters.put("maxRows", 100);
        String sql = normalized(configuration.getMappedStatement(
                "com.erp.oa.mapper.OaSignOnboardImportRowMapper"
                        + ".selectSignatureFirstCompanyWorkRows")
                .getBoundSql(parameters));

        assertThat(sql)
                .contains("b.shop_dept_id in ( ? )")
                .doesNotContain("t.assigned_hr_user_id = ?");
    }

    @Test
    void supportingRelationsUseSetQueriesAndDoNotLoadSignatureBlobs() throws Exception
    {
        String batches = normalized(configuration(
                "mapper/oa/OaSignOnboardImportBatchMapper.xml").getMappedStatement(
                        "com.erp.oa.mapper.OaSignOnboardImportBatchMapper.selectByIds")
                        .getBoundSql(Map.of("batchIds", List.of(31L, 32L))));
        String staleBatches = normalized(configuration(
                "mapper/oa/OaSignOnboardImportBatchMapper.xml").getMappedStatement(
                        "com.erp.oa.mapper.OaSignOnboardImportBatchMapper"
                                + ".selectStaleGeneratingBatchIds")
                        .getBoundSql(Map.of("staleBefore", new Date(), "maxRows", 100)));
        String requests = normalized(configuration(
                "mapper/oa/OaSignOnboardDataRequestMapper.xml").getMappedStatement(
                        "com.erp.oa.mapper.OaSignOnboardDataRequestMapper.selectSummariesByIds")
                        .getBoundSql(Map.of("requestIds", List.of(501L, 502L))));
        String tasks = normalized(configuration(
                "mapper/oa/OaSignTaskMapper.xml").getMappedStatement(
                        "com.erp.oa.mapper.OaSignTaskMapper.selectOaSignTasksByIds")
                        .getBoundSql(Map.of("taskIds", List.of(801L, 802L))));
        String sourceTasks = normalized(configuration(
                "mapper/oa/OaSignTaskMapper.xml").getMappedStatement(
                        "com.erp.oa.mapper.OaSignTaskMapper.selectOaSignTasksBySourceEvents")
                        .getBoundSql(Map.of("sourceEvents", List.of(
                                sourceTask(957L, 1876L, 3L),
                                sourceTask(958L, 1877L, 4L)))));
        Configuration plansConfiguration = configuration(
                "mapper/oa/OaSignPlanVersionMapper.xml");
        String plans = normalized(plansConfiguration.getMappedStatement(
                "com.erp.oa.mapper.OaSignPlanVersionMapper.selectPlanVersionsByIds")
                .getBoundSql(Map.of("versionIds", List.of(91L, 92L))));
        String templates = normalized(plansConfiguration.getMappedStatement(
                "com.erp.oa.mapper.OaSignPlanVersionMapper.selectTemplatesByVersionIds")
                .getBoundSql(Map.of("versionIds", List.of(91L, 92L))));

        assertThat(batches).contains("where batch_id in ( ? , ? )");
        assertThat(staleBatches)
                .contains("b.status = 'GENERATING'", "b.update_time <= ?")
                .contains("r.status = 'GENERATING'", "r.update_time <= ?")
                .endsWith("limit ?");
        assertThat(requests)
                .contains("null as signature_sample_bytes")
                .contains("where r.request_id in ( ? , ? )");
        assertThat(tasks).contains("where task_id in ( ? , ? )");
        assertThat(sourceTasks)
                .contains("where ( (upper(trim(scenario)) = upper(trim(?)) and employee_id = ?")
                .contains("cast(trim(source_business_id) as binary) = cast(trim(?) as binary)")
                .contains("cast(trim(source_event_version) as binary) = cast(trim(?) as binary)")
                .contains("or (upper(trim(scenario)) = upper(trim(?)) and employee_id = ?")
                .contains("order by case when package_id is not null")
                .doesNotContain("limit 1");
        assertThat(plans).contains("where version_id in ( ? , ? )");
        assertThat(templates)
                .contains("where plan_version_id in ( ? , ? )")
                .contains("order by plan_version_id asc");
    }

    private OaSignTask sourceTask(Long employeeId, Long rowId, Long eventVersion)
    {
        OaSignTask task = new OaSignTask();
        task.setScenario("ONBOARD");
        task.setEmployeeId(employeeId);
        task.setSourceType("MANUAL_SIGN_EXCEL_IMPORT");
        task.setSourceBusinessId(String.valueOf(rowId));
        task.setSourceEventVersion(String.valueOf(eventVersion));
        return task;
    }

    private String normalized(BoundSql bound)
    {
        return bound.getSql().replaceAll("\\s+", " ").trim();
    }

    private Configuration configuration(String resource) throws Exception
    {
        Configuration configuration = new Configuration();
        try (InputStream input = Resources.getResourceAsStream(resource))
        {
            new XMLMapperBuilder(input, configuration, resource,
                    configuration.getSqlFragments()).parse();
        }
        return configuration;
    }
}
