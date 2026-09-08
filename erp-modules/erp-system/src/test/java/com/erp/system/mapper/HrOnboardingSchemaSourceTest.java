package com.erp.system.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

class HrOnboardingSchemaSourceTest
{
    @Test
    void readinessReportIsReadOnlyAndCoversPairsRolesAndDictionaryRouting() throws Exception
    {
        Path root = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (root.getParent() != null && !Files.exists(root.resolve("sql"))) root = root.getParent();
        String sql = Files.readString(root.resolve("scripts/hr-onboarding-position-config-readiness.sql"));
        String executable = sql.replaceAll("(?s)/\\*.*?\\*/", " ")
                .replaceAll("(?m)--.*$", " ").toLowerCase();

        assertThat(executable).doesNotContain("insert ", "update ", "delete ", "replace ",
                "alter ", "create ", "drop ", "truncate ");
        assertThat(executable).contains(
                "sys_post", "hr_onboarding_position_config", "hr_onboarding_position_config_role",
                "employee_category", "missing_mapping", "disabled_mapping", "missing_default_roles",
                "hr.onboarding.dict_type.employeecategory", "sys_config", "sys_dict_type",
                "missing_dictionary_routing");
    }

    @Test
    void migrationDefinesOnboardingAggregateAndPermissions() throws Exception
    {
        Path root = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (root.getParent() != null && !Files.exists(root.resolve("sql")))
        {
            root = root.getParent();
        }
        Path onboardingSql = root.resolve("sql/erp_user_hr_onboarding_20260710.sql");
        Path onboardingDockerSql = root.resolve("docker/mysql/db/erp_user_hr_onboarding_20260710.sql");
        Path profileSql = root.resolve("sql/erp_user_employee_profile_20260706.sql");
        Path profileDockerSql = root.resolve("docker/mysql/db/erp_user_employee_profile_20260706.sql");

        byte[] onboardingBytes = Files.readAllBytes(onboardingSql);
        byte[] profileBytes = Files.readAllBytes(profileSql);
        assertThat(onboardingBytes).containsExactly(Files.readAllBytes(onboardingDockerSql));
        assertThat(profileBytes).containsExactly(Files.readAllBytes(profileDockerSql));

        String sql = new String(onboardingBytes, java.nio.charset.StandardCharsets.UTF_8);
        String profile = new String(profileBytes, java.nio.charset.StandardCharsets.UTF_8);
        assertThat(profile)
                .contains("direct_supervisor_user_id bigint(20) DEFAULT NULL")
                .doesNotContain("ADD COLUMN direct_supervisor_user_id");

        Matcher permissionMatcher = Pattern.compile("'(hr:[a-zA-Z:]+)'").matcher(sql);
        List<String> permissions = permissionMatcher.results().map(result -> result.group(1)).toList();
        assertThat(permissions).containsExactlyInAnyOrder(
                "hr:onboarding:workbench",
                "hr:onboarding:list",
                "hr:onboarding:query",
                "hr:onboarding:add",
                "hr:onboarding:edit",
                "hr:onboarding:ready",
                "hr:onboarding:return",
                "hr:onboarding:confirm",
                "hr:onboarding:cancel",
                "hr:onboarding:restore",
                "hr:onboarding:import:preview",
                "hr:onboarding:import:confirm",
                "hr:onboarding:import:template",
                "hr:onboarding:config",
                "hr:employee:list",
                "hr:employee:query",
                "hr:employee:add",
                "hr:employee:edit",
                "hr:employee:export",
                "hr:employee:sensitive:view",
                "hr:employee:export:sensitive",
                "hr:completeness:list",
                "hr:import:preview",
                "hr:import:confirm",
                "hr:import:template");

        int lockIndex = sql.indexOf("GET_LOCK(");
        int localGuardIndex = sql.indexOf("PREPARE hr_onboarding_lock_guard");
        int firstTableIndex = sql.indexOf("CREATE TABLE", lockIndex);
        int firstProcedureIndex = sql.indexOf("DROP PROCEDURE IF EXISTS", lockIndex);
        int sharedObjectIndex = Math.min(firstTableIndex, firstProcedureIndex);
        assertThat(lockIndex).isGreaterThanOrEqualTo(0);
        assertThat(localGuardIndex).isGreaterThan(lockIndex);
        assertThat(sharedObjectIndex).isGreaterThan(localGuardIndex);
        assertThat(sql.substring(lockIndex, sharedObjectIndex)).contains(
                "PREPARE hr_onboarding_lock_guard",
                "EXECUTE hr_onboarding_lock_guard",
                "erp_hr_onboarding_lock_not_acquired");

        assertThat(sql).contains(
                "CREATE TABLE IF NOT EXISTS hr_onboarding",
                "CREATE TABLE IF NOT EXISTS hr_onboarding_operation_log",
                "CREATE TABLE IF NOT EXISTS hr_onboarding_position_config",
                "CREATE TABLE IF NOT EXISTS hr_onboarding_position_config_role",
                "CREATE TABLE IF NOT EXISTS hr_onboarding_import_batch",
                "CREATE TABLE IF NOT EXISTS hr_onboarding_import_row",
                "CREATE TABLE IF NOT EXISTS hr_sensitive_access_log",
                "direct_supervisor_user_id",
                "confirm_idempotency_key",
                "account_configuration_status",
                "uk_hr_onboarding_no",
                "idx_hr_onboarding_status_date",
                "hr:onboarding:workbench",
                "hr:onboarding:list",
                "hr:onboarding:query",
                "hr:onboarding:add",
                "hr:onboarding:edit",
                "hr:onboarding:ready",
                "hr:onboarding:return",
                "hr:onboarding:confirm",
                "hr:onboarding:cancel",
                "hr:onboarding:restore",
                "hr:onboarding:import:preview",
                "hr:onboarding:import:confirm",
                "hr:onboarding:import:template",
                "hr:onboarding:config",
                "hr:employee:list",
                "hr:employee:query",
                "hr:employee:add",
                "hr:employee:edit",
                "hr:employee:export",
                "hr:employee:sensitive:view",
                "hr:employee:export:sensitive",
                "hr:completeness:list",
                "hr:import:preview",
                "hr:import:confirm",
                "hr:import:template",
                "hr.onboarding.post_entry_due_days",
                "hr.onboarding.import_retention_days",
                "hr.employee.no.prefix",
                "GET_LOCK(",
                "LOCK TABLES sys_menu WRITE",
                "sys_config WRITE",
                "SIGNAL SQLSTATE '45000'",
                "ambiguous HR menu seed",
                "ambiguous HR config key",
                "CONVERT(path USING utf8mb4) COLLATE utf8mb4_unicode_ci",
                "CONVERT(current_path USING utf8mb4) COLLATE utf8mb4_unicode_ci",
                "CONVERT(menu_name USING utf8mb4) COLLATE utf8mb4_unicode_ci",
                "CONVERT(current_menu_name USING utf8mb4) COLLATE utf8mb4_unicode_ci",
                "CONVERT(seed_key USING utf8mb4) COLLATE utf8mb4_unicode_ci",
                "CONVERT(current_seed_key USING utf8mb4) COLLATE utf8mb4_unicode_ci",
                "dedicated client connection",
                "close the connection on any error",
                "UNLOCK TABLES",
                "RELEASE_LOCK(");
    }

    @Test
    void repositoryDefinesFailClosedMysqlMigrationIntegrationProfile() throws Exception
    {
        Path root = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (root.getParent() != null && !Files.exists(root.resolve("sql")))
        {
            root = root.getParent();
        }

        Path runnerPath = root.resolve("scripts/verify-hr-onboarding-migration.sh");
        assertThat(Files.exists(runnerPath))
                .as("the explicit HR onboarding migration integration runner must exist")
                .isTrue();
        assertThat(Files.isExecutable(runnerPath))
                .as("the integration runner must be directly executable by CI")
                .isTrue();

        String runner = Files.readString(runnerPath);
        String modulePom = Files.readString(root.resolve("erp-modules/erp-system/pom.xml"));
        assertThat(modulePom).contains(
                "<id>hr-onboarding-migration-it</id>",
                "<phase>integration-test</phase>",
                "verify-hr-onboarding-migration.sh");
        assertThat(runner).contains(
                "ERP_IT_MYSQL_HOST",
                "ERP_IT_MYSQL_USER",
                "EXPECTED_MYSQL_VERSION_PREFIX",
                "ERP_IT_ALLOW_REMOTE_NONPROD",
                "erp_it_${RUN_ID}_hr_onboarding_migration",
                "run_native_mysql_suite",
                "assert_lock_guard_zero_writes",
                "assert_menu_ambiguity_zero_writes",
                "assert_config_ambiguity_zero_writes",
                "assert_concurrent_migration_preserves_sentinel",
                "assert_global_identity_lock_serialization",
                "assert_global_identity_lock_serialization \"${target}\"",
                "FORCE INDEX (PRIMARY)",
                "innodb_lock_wait_timeout",
                "global identity lock must block cross-department confirmation",
                "erp_hr_onboarding_lock_not_acquired",
                "hr.onboarding.post_entry_due_days=7",
                "hr.onboarding.import_retention_days=30",
                "hr.employee.no.prefix=E");
        assertThat(runner).doesNotContain("docker ", "mysql:5.7", "mysql:8.0", "Testcontainers");
    }
}
