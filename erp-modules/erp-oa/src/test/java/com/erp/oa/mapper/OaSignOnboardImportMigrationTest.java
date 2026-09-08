package com.erp.oa.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("入职签约 Excel 导入迁移")
class OaSignOnboardImportMigrationTest
{
    private static final String MIGRATION = "erp_oa_sign_onboard_import_20260718.sql";

    @Test
    @DisplayName("模块、SQL 与 Docker 三份迁移完全一致且进入自动初始化清单")
    void shouldShipOneIdenticalMigrationThroughEveryDeploymentPath() throws Exception
    {
        String moduleSql = readRepoFile(
                "erp-modules/erp-oa/src/main/resources/db/migration/" + MIGRATION);
        String releaseSql = readRepoFile("sql/" + MIGRATION);
        String dockerSql = readRepoFile("docker/mysql/db/" + MIGRATION);
        String bootstrapFiles = readRepoFile("docker/mysql/bootstrap-files.list");

        assertThat(releaseSql).isEqualTo(moduleSql);
        assertThat(dockerSql).isEqualTo(moduleSql);
        assertThat(bootstrapFiles.lines().filter(MIGRATION::equals).count()).isEqualTo(1L);
    }

    @Test
    @DisplayName("迁移建立导入与补资料事实表并兼容完整地址和公司推荐快照")
    void shouldCreateImportFactsAndCompatiblePackageSnapshots() throws Exception
    {
        String sql = readRepoFile(
                "erp-modules/erp-oa/src/main/resources/db/migration/" + MIGRATION);

        assertThat(sql)
                .contains("CREATE TABLE IF NOT EXISTS oa_sign_onboard_import_batch")
                .contains("CREATE TABLE IF NOT EXISTS oa_sign_onboard_import_row")
                .contains("CREATE TABLE IF NOT EXISTS oa_sign_onboard_data_request")
                .contains("source_event_version bigint DEFAULT NULL")
                .contains("recommended_company_snapshot varchar(255)")
                .contains("recommended_legal_representative_snapshot varchar(128)")
                .contains("recommended_registered_address_snapshot varchar(500)")
                .contains("MODIFY COLUMN employee_address_snapshot varchar(255)")
                .doesNotContain("UPDATE oa_sign_package")
                .doesNotContain("DELETE FROM oa_sign_package");
    }

    @Test
    @DisplayName("补资料历史轮次迁移兼容旧草稿且可重复执行")
    void shouldUpgradeOldSingleRequestDraftWithoutDeletingApprovalFacts() throws Exception
    {
        String sql = readRepoFile(
                "erp-modules/erp-oa/src/main/resources/db/migration/" + MIGRATION);

        assertThat(sql)
                .contains("approved_hr_values_json json DEFAULT NULL")
                .contains("ADD COLUMN approved_hr_values_json json DEFAULT NULL")
                .contains("KEY idx_oa_sign_onboard_data_request_row_history (row_id, request_id)")
                .contains("IF v_history_index = 0 THEN")
                .contains("ADD INDEX idx_oa_sign_onboard_data_request_row_history (row_id, request_id)")
                .contains("IF v_legacy_unique > 0 THEN")
                .contains("DROP INDEX uk_oa_sign_onboard_data_request_row")
                .doesNotContain("UNIQUE KEY uk_oa_sign_onboard_data_request_row (row_id)")
                .doesNotContain("DELETE FROM oa_sign_onboard_data_request")
                .doesNotContain("UPDATE oa_sign_onboard_data_request");
    }

    @Test
    @DisplayName("新库导入迁移会 fail-closed 安装开放 ONBOARD 任务唯一约束")
    void shouldInstallOpenOnboardTaskGuardForFreshInstallations() throws Exception
    {
        String sql = readRepoFile(
                "erp-modules/erp-oa/src/main/resources/db/migration/" + MIGRATION);

        assertThat(sql)
                .contains("HAVING COUNT(*) > 1")
                .contains("duplicate open ONBOARD tasks must be resolved")
                .contains("ADD COLUMN open_onboard_employee_id bigint GENERATED ALWAYS AS")
                .contains("status NOT IN (''SIGNED'', ''REFUSED'', ''EXPIRED'', ''CANCELLED'', ''NO_ACTION'')")
                .contains("ADD UNIQUE INDEX uk_oa_sign_task_open_onboard_employee (open_onboard_employee_id)")
                .contains("incompatible oa_sign_task.open_onboard_employee_id definition")
                .contains("incompatible ONBOARD open-task unique index")
                .doesNotContain("UPDATE oa_sign_task")
                .doesNotContain("DELETE FROM oa_sign_task");
    }

    private String readRepoFile(String relativePath) throws Exception
    {
        Path base = Paths.get(System.getProperty("user.dir"));
        Path path = base.resolve(relativePath);
        if (!Files.exists(path))
        {
            path = base.resolve("../..").resolve(relativePath).normalize();
        }
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
