package com.erp.oa.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("劳动合同期限类型快照迁移")
class OaSignContractTermSnapshotMigrationTest
{
    private static final String MIGRATION = "erp_oa_sign_contract_term_snapshot_20260721.sql";

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
    @DisplayName("迁移只从不可变导入事实回填未签署入职包")
    void shouldBackfillOnlyUnsignedOnboardPackagesFromImmutableImportFacts() throws Exception
    {
        String sql = readRepoFile(
                "erp-modules/erp-oa/src/main/resources/db/migration/" + MIGRATION);

        assertThat(sql)
                .contains("ADD COLUMN contract_term_code_snapshot varchar(32) DEFAULT NULL")
                .contains("INNER JOIN oa_sign_onboard_import_row import_row")
                .contains("JSON_EXTRACT(import_row.snapshot_json, '$.contractTermCode')")
                .contains("UPPER(TRIM(package_row.scenario)) = 'ONBOARD'")
                .contains("LOWER(TRIM(package_row.status)) <> 'signed'")
                .contains("IN ('FIXED_TERM', 'OPEN_ENDED')")
                .doesNotContain("DELETE FROM oa_sign_package");
    }

    @Test
    @DisplayName("MyBatis 的查询、新增与两种更新路径都保存合同期限快照")
    void shouldBindContractTermSnapshotAcrossPackageMapperPaths() throws Exception
    {
        String mapper = readRepoFile(
                "erp-modules/erp-oa/src/main/resources/mapper/oa/OaSignPackageMapper.xml");

        assertThat(mapper)
                .contains("<result property=\"contractTermCodeSnapshot\" column=\"contract_term_code_snapshot\"/>")
                .contains("scenario, employment_type, contract_term_code_snapshot")
                .contains("employment_type, contract_term_code_snapshot, social_type")
                .contains("#{employmentType}, #{contractTermCodeSnapshot}, #{socialType}")
                .contains("<if test=\"contractTermCodeSnapshot != null\">contract_term_code_snapshot = #{contractTermCodeSnapshot},</if>")
                .contains("contract_term_code_snapshot = #{contractTermCodeSnapshot},");
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
