package com.erp.file.drive.sql;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("云盘数据库迁移与数据源接线")
class CloudDriveSqlSourceTest
{
    private static final String SQL_FILE = "erp_cloud_drive_20260711.sql";
    private static final String QUOTA_SQL_FILE = "erp_cloud_drive_organization_quota_20260713.sql";

    @Test
    @DisplayName("普通迁移与 Docker 初始化脚本字节一致")
    void shouldKeepSqlCopiesIdentical() throws Exception
    {
        Path source = repoRoot().resolve("sql").resolve(SQL_FILE);
        Path mirror = repoRoot().resolve("docker/mysql/db").resolve(SQL_FILE);
        Path quotaSource = repoRoot().resolve("sql").resolve(QUOTA_SQL_FILE);
        Path quotaMirror = repoRoot().resolve("docker/mysql/db").resolve(QUOTA_SQL_FILE);

        assertThat(source).exists().isRegularFile();
        assertThat(mirror).exists().isRegularFile();
        assertThat(Files.readAllBytes(mirror)).isEqualTo(Files.readAllBytes(source));
        assertThat(quotaSource).exists().isRegularFile();
        assertThat(quotaMirror).exists().isRegularFile();
        assertThat(Files.readAllBytes(quotaMirror)).isEqualTo(Files.readAllBytes(quotaSource));
    }

    @Test
    @DisplayName("组织盘与额度迁移应保持兼容、可重复且默认安全关闭")
    void shouldCreateOrganizationAndQuotaControlPlane() throws Exception
    {
        String sql = Files.readString(repoRoot().resolve("sql").resolve(QUOTA_SQL_FILE),
                StandardCharsets.UTF_8);

        assertThat(sql).contains(
                "information_schema.COLUMNS",
                "CREATE TABLE IF NOT EXISTS drive_personal_quota_policy",
                "CREATE TABLE IF NOT EXISTS drive_org_type_rule",
                "CREATE TABLE IF NOT EXISTS drive_org_space_config",
                "CREATE TABLE IF NOT EXISTS drive_capacity_config",
                "CREATE TABLE IF NOT EXISTS drive_upload_reservation",
                "uk_drive_personal_quota_subject",
                "uk_drive_org_space_dept",
                "uk_drive_upload_reservation_storage",
                "idx_drive_upload_reservation_cleanup",
                "quota_source_type",
                "quota_synced_time",
                "'PERMISSION_ONLY'",
                "'WARN'",
                "('COMPANY', 0",
                "('STORE', 0",
                "INSERT IGNORE INTO drive_org_space_config");
        assertThat(sql).doesNotContain("DROP TABLE", "DELETE FROM drive_space", "DELETE FROM drive_node");
    }

    @Test
    @DisplayName("迁移应可重复创建云盘基础表与最终菜单")
    void shouldCreateCloudDriveSchemaAndFinalMenu() throws Exception
    {
        Path source = repoRoot().resolve("sql").resolve(SQL_FILE);
        assertThat(source).exists().isRegularFile();

        String sql = Files.readString(source, StandardCharsets.UTF_8);
        assertThat(sql).contains(
                "CREATE TABLE IF NOT EXISTS drive_space",
                "CREATE TABLE IF NOT EXISTS drive_node",
                "CREATE TABLE IF NOT EXISTS drive_operation_log",
                "uk_drive_node_active_name",
                "idx_drive_node_purge",
                "'COMPANY:ROOT'",
                "(9600, '云盘'",
                "'drive', 'drive/index'",
                "'drive:access'",
                "role_key = 'admin'");
    }

    @Test
    @DisplayName("文件服务应启用数据源并声明云盘所需依赖")
    void shouldEnableDatasourceAndPersistenceDependencies() throws Exception
    {
        Path module = repoRoot().resolve("erp-modules/erp-file");
        String application = Files.readString(
                module.resolve("src/main/java/com/erp/file/ErpFileApplication.java"),
                StandardCharsets.UTF_8);
        String pom = Files.readString(module.resolve("pom.xml"), StandardCharsets.UTF_8);

        assertThat(application).doesNotContain("DataSourceAutoConfiguration.class");
        assertThat(pom).contains(
                "mysql-connector-j",
                "erp-common-datasource",
                "erp-common-log",
                "erp-common-swagger");
    }

    private static Path repoRoot()
    {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        if (Files.exists(current.resolve("erp-modules/erp-file/pom.xml")))
        {
            return current;
        }
        if (Files.exists(current.resolve("pom.xml"))
                && current.endsWith(Path.of("erp-modules", "erp-file")))
        {
            return current.getParent().getParent();
        }
        throw new IllegalStateException("Cannot locate repository root from " + current);
    }
}
