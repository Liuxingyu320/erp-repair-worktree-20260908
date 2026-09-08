package com.erp.system.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("人事生命周期动作和签约事件发件箱")
class HrLifecycleActionMigrationTest
{
    @Test
    @DisplayName("迁移脚本创建不可变动作和可重试发件箱并保持两份一致")
    void shouldCreateLifecycleActionAndReliableOutboxTables() throws Exception
    {
        Path sqlPath = repoFile("sql/erp_hr_lifecycle_action_20260711.sql");
        Path dockerSqlPath = repoFile("docker/mysql/db/erp_hr_lifecycle_action_20260711.sql");

        assertThat(sqlPath).exists();
        assertThat(dockerSqlPath).exists();

        String sql = Files.readString(sqlPath, StandardCharsets.UTF_8);
        String dockerSql = Files.readString(dockerSqlPath, StandardCharsets.UTF_8);
        assertThat(dockerSql).isEqualTo(sql);
        assertThat(sql)
                .contains("CREATE TABLE IF NOT EXISTS sys_hr_lifecycle_action")
                .contains("action_type varchar(32) NOT NULL")
                .contains("employee_id bigint NOT NULL")
                .contains("before_snapshot_json json")
                .contains("after_snapshot_json json")
                .contains("effective_date date")
                .contains("business_status varchar(32) NOT NULL")
                .contains("risk_level varchar(16) NOT NULL")
                .contains("risk_codes_json json")
                .contains("request_id varchar(64) NOT NULL")
                .contains("operator_type varchar(16) NOT NULL")
                .contains("operator_user_id bigint DEFAULT NULL")
                .contains("UNIQUE KEY uk_sys_hr_lifecycle_action_request (request_id)")
                .contains("CREATE TABLE IF NOT EXISTS sys_hr_sign_event_outbox")
                .contains("action_id bigint NOT NULL")
                .contains("event_version bigint NOT NULL")
                .contains("payload_json json NOT NULL")
                .contains("status varchar(16) NOT NULL DEFAULT 'PENDING'")
                .contains("retry_count int NOT NULL DEFAULT 0")
                .contains("UNIQUE KEY uk_sys_hr_sign_event_action_version (action_id, event_version)")
                .contains("KEY idx_sys_hr_sign_event_due (status, next_retry_time, outbox_id)");
    }

    @Test
    @DisplayName("Mapper绑定动作幂等写入和发件箱乐观锁状态流转")
    void shouldBindLifecycleActionAndOutboxMappers() throws Exception
    {
        Configuration configuration = new Configuration();
        parseMapper(configuration, "mapper/system/SysHrLifecycleActionMapper.xml");
        parseMapper(configuration, "mapper/system/SysHrSignEventOutboxMapper.xml");

        assertMapped(configuration, "com.erp.system.mapper.SysHrLifecycleActionMapper", "insertAction");
        assertMapped(configuration, "com.erp.system.mapper.SysHrLifecycleActionMapper", "selectById");
        assertMapped(configuration, "com.erp.system.mapper.SysHrLifecycleActionMapper", "selectByRequestId");
        assertMapped(configuration, "com.erp.system.mapper.SysHrSignEventOutboxMapper", "insertOutbox");
        assertMapped(configuration, "com.erp.system.mapper.SysHrSignEventOutboxMapper",
                "selectByActionAndEventVersion");
        assertMapped(configuration, "com.erp.system.mapper.SysHrSignEventOutboxMapper", "selectDueOutboxes");
        assertMapped(configuration, "com.erp.system.mapper.SysHrSignEventOutboxMapper", "claimForSending");
        assertMapped(configuration, "com.erp.system.mapper.SysHrSignEventOutboxMapper", "markSent");
        assertMapped(configuration, "com.erp.system.mapper.SysHrSignEventOutboxMapper", "markRetry");
        assertMapped(configuration, "com.erp.system.mapper.SysHrSignEventOutboxMapper", "markDead");

        String outboxXml = resourceText("mapper/system/SysHrSignEventOutboxMapper.xml");
        String actionXml = resourceText("mapper/system/SysHrLifecycleActionMapper.xml");
        assertThat(outboxXml)
                .contains("insert into sys_hr_sign_event_outbox")
                .doesNotContain("insert ignore into sys_hr_sign_event_outbox")
                .contains("status in ('PENDING', 'RETRY')")
                .contains("status = 'SENDING'")
                .contains("version = version + 1")
                .contains("and version = #{version}");
        Class<?> actionType = Class.forName("com.erp.system.domain.SysHrLifecycleAction");
        assertThat(fieldNames(actionType)).contains("operatorType", "operatorUserId");
        assertThat(actionXml)
                .contains("property=\"operatorType\" column=\"operator_type\"")
                .contains("#{operatorType}, #{operatorUserId}");
    }

    @Test
    @DisplayName("共享事件契约使用固定事件字段和显式员工快照")
    void shouldExposeTypedHrSigningEventContract() throws Exception
    {
        Class<?> eventType = Class.forName("com.erp.oa.api.domain.HrSignBusinessEvent");
        Class<?> snapshotType = Class.forName("com.erp.oa.api.domain.HrEmployeeSigningSnapshot");

        assertThat(fieldNames(eventType)).containsExactlyInAnyOrder(
                "eventId", "scenario", "employeeId", "sourceType", "sourceBusinessId",
                "sourceEventVersion", "occurredTime", "operatorUserId", "beforeSnapshot",
                "afterSnapshot", "attributes");
        assertThat(eventType.getDeclaredField("beforeSnapshot").getType()).isEqualTo(snapshotType);
        assertThat(eventType.getDeclaredField("afterSnapshot").getType()).isEqualTo(snapshotType);
        assertThat(fieldNames(snapshotType)).contains(
                "employeeId", "employeeNo", "employeeName", "phone", "idType", "idNumber",
                "currentAddress", "employeeStatus", "employeeCategory",
                "shopDeptId", "shopDeptName", "deptId", "deptName",
                "legalEntityId", "legalEntityCode", "legalEntityName",
                "postId", "postCode", "postName", "jobGradeCode", "jobGradeName",
                "directSupervisorId", "directSupervisorName",
                "departmentSupervisorId", "departmentSupervisorName",
                "workLocation", "workCityLevel", "contractTypeCode", "contractTermCode",
                "socialTypeCode", "entryDate", "contractStartDate", "contractEndDate",
                "probationStartDate", "probationEndDate", "actualRegularizationDate", "leaveDate",
                "baseSalary", "postSalary", "fieldAllowance", "performanceSalary", "salaryTotal",
                "salaryVersion");
        assertThat(fieldNames(snapshotType)).contains(
                "accountStatus", "offboardingType", "leaveReason",
                "salarySettlementStatus", "assetHandoverStatus",
                "nonCompeteDecision", "compensationAmount", "compensationNote");
        assertThat(fieldNames(snapshotType)).doesNotContain("snapshotJson", "payloadJson", "dataJson");
    }

    @Test
    @DisplayName("OA共享API模块只直接依赖core且业务模块依赖方向正确")
    void shouldWireOaApiModuleWithoutBusinessDependencies() throws Exception
    {
        String apiParentPom = fileText("erp-api/pom.xml");
        String apiPom = fileText("erp-api/erp-api-oa/pom.xml");
        String systemPom = fileText("erp-modules/erp-system/pom.xml");
        String oaPom = fileText("erp-modules/erp-oa/pom.xml");
        String serviceConstants = fileText(
                "erp-common/erp-common-core/src/main/java/com/erp/common/core/constant/ServiceNameConstants.java");

        assertThat(apiParentPom).contains("<module>erp-api-oa</module>");
        assertThat(apiPom)
                .contains("<artifactId>erp-common-core</artifactId>")
                .doesNotContain("erp-api-system", "erp-modules-system", "erp-modules-oa");
        assertThat(systemPom).contains("<artifactId>erp-api-oa</artifactId>");
        assertThat(oaPom).contains("<artifactId>erp-api-oa</artifactId>");
        assertThat(serviceConstants).contains("public static final String OA_SERVICE = \"erp-oa\"");
    }

    @Test
    @DisplayName("共享API自动注册签约服务降级工厂")
    void shouldRegisterRemoteSignTaskFallbackFactory() throws Exception
    {
        Path importsPath = repoFile(
                "erp-api/erp-api-oa/src/main/resources/META-INF/spring/"
                        + "org.springframework.boot.autoconfigure.AutoConfiguration.imports");

        assertThat(importsPath).exists();
        assertThat(Files.readString(importsPath, StandardCharsets.UTF_8))
                .contains("com.erp.oa.api.factory.RemoteSignTaskFallbackFactory");
    }

    @Test
    @DisplayName("阶段计划中的聚焦测试命令可在多模块reactor原样执行")
    void shouldDocumentRunnableFocusedTestGate() throws Exception
    {
        String plan = fileText("docs/superpowers/plans/2026-07-11-contract-automation-phase-3-scenarios.md");

        assertThat(plan).contains(
                "mvn -pl erp-modules/erp-system -am -Dtest=HrLifecycleActionMigrationTest "
                        + "-Dsurefire.failIfNoSpecifiedTests=false test");
    }

    private static Set<String> fieldNames(Class<?> type)
    {
        return Arrays.stream(type.getDeclaredFields())
                .map(Field::getName)
                .filter(name -> !"serialVersionUID".equals(name))
                .collect(Collectors.toSet());
    }

    private static void parseMapper(Configuration configuration, String resource) throws Exception
    {
        try (InputStream inputStream = Resources.getResourceAsStream(resource))
        {
            XMLMapperBuilder parser = new XMLMapperBuilder(inputStream, configuration, resource,
                    configuration.getSqlFragments());
            parser.parse();
        }
    }

    private static void assertMapped(Configuration configuration, String mapperType, String statementId)
    {
        assertThat(configuration.hasStatement(mapperType + "." + statementId))
                .as("mapped statement %s.%s", mapperType, statementId)
                .isTrue();
    }

    private static String resourceText(String resource) throws Exception
    {
        try (InputStream inputStream = Resources.getResourceAsStream(resource))
        {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String fileText(String relativePath) throws Exception
    {
        return Files.readString(repoFile(relativePath), StandardCharsets.UTF_8);
    }

    private static Path repoFile(String relativePath)
    {
        Path base = Paths.get(System.getProperty("user.dir"));
        Path path = base.resolve(relativePath);
        if (!Files.exists(path))
        {
            path = base.resolve("../..").resolve(relativePath).normalize();
        }
        return path;
    }
}
