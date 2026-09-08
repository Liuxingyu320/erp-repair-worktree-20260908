package com.erp.oa.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("费用报销数据库迁移")
class OaReimbursementMigrationTest
{
    private static final String MIGRATION =
            "erp_oa_reimbursement_20260730.sql";

    @Test
    @DisplayName("三份部署脚本一致且包含完整业务、审批和权限定义")
    void shouldShipOneRepeatSafeMigration() throws Exception
    {
        String sql = readRepoFile("sql/" + MIGRATION);
        String docker = readRepoFile("docker/mysql/db/" + MIGRATION);
        String resource = readRepoFile(
                "erp-modules/erp-oa/src/main/resources/db/migration/"
                        + MIGRATION);
        String bootstrap = readRepoFile(
                "docker/mysql/bootstrap-files.list");

        assertThat(docker).isEqualTo(sql);
        assertThat(resource).isEqualTo(sql);
        assertThat(bootstrap.lines().filter(MIGRATION::equals).count())
                .isEqualTo(1L);
        assertThat(sql)
                .contains(
                        "CREATE TABLE IF NOT EXISTS oa_reimbursement (",
                        "CREATE TABLE IF NOT EXISTS oa_reimbursement_approval_start_outbox (",
                        "CREATE TABLE IF NOT EXISTS oa_reimbursement_item (",
                        "CREATE TABLE IF NOT EXISTS oa_reimbursement_invoice (",
                        "CREATE TABLE IF NOT EXISTS oa_reimbursement_invoice_recognition (",
                        "CREATE TABLE IF NOT EXISTS oa_reimbursement_export_batch (",
                        "CREATE TABLE IF NOT EXISTS oa_reimbursement_export_batch_item (",
                        "'feature.oa.reimbursement.enabled'",
                        "'OA_REIMBURSEMENT'",
                        "'DEPARTMENT_LEADER'",
                        "'FINANCE_LEADER'",
                        "\"permissionKey\":\"oa:reimbursement:finance:approve\"",
                        "'oa:reimbursement:self'",
                        "'oa:reimbursement:approve'",
                        "'oa:reimbursement:finance:list'",
                        "'oa:reimbursement:finance:export'",
                        "'oa:reimbursement:approvalStartOutbox:list'",
                        "'oa:reimbursement:approvalStartOutbox:replay'",
                        "'{\"mode\":\"finance\"}'",
                        "recognition_engine varchar(16)",
                        "invoice_total_amount decimal(12,2)",
                        "UNIQUE KEY uk_oa_reimbursement_invoice_claim_hash",
                        "KEY idx_oa_reimbursement_invoice_hash (sha256, reimbursement_id)",
                        "information_schema.statistics",
                        "oa_reimbursement_invoice has duplicate reimbursement_id + sha256 rows",
                        "SIGNAL SQLSTATE '45000'",
                        "ALTER TABLE oa_reimbursement_item",
                        "MODIFY claimed_amount decimal(12,2) DEFAULT NULL",
                        "leader_dept.leader_user_id",
                        "permission_menu.perms IN ('oa:salary:export', 'system:salary:export')")
                .doesNotContain(
                        "DROP TABLE oa_reimbursement",
                        "DELETE FROM oa_reimbursement");
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
