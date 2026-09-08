package com.erp.system.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("签约档案字典迁移脚本")
class SigningProfileDictionaryMigrationTest
{
    @Test
    @DisplayName("迁移具有幂等快照、未知值保护和受限映射")
    void shouldShipSafeIdempotentDictionaryMigration() throws Exception
    {
        String sql = readRepoFile("sql/erp_sign_profile_dictionary_20260711.sql");
        String dockerSql = readRepoFile("docker/mysql/db/erp_sign_profile_dictionary_20260711.sql");

        assertThat(dockerSql).isEqualTo(sql);
        assertThat(sql)
                .contains("CREATE TABLE IF NOT EXISTS bak_sys_user_profile_signing_20260711")
                .contains("LIKE sys_user_profile")
                .contains("LEFT JOIN bak_sys_user_profile_signing_20260711 b ON b.user_id = p.user_id")
                .contains("WHERE b.user_id IS NULL")
                .contains("SIGNAL SQLSTATE '45000'")
                .contains("未知合同或社保值")
                .contains("contract_type NOT IN")
                .contains("social_type NOT IN")
                .contains("WHEN contract_type IN ('固定期限劳动合同', '劳动合同')")
                .contains("THEN 'FIXED_TERM'")
                .contains("WHEN contract_type = '无固定期限劳动合同'")
                .contains("THEN 'OPEN_ENDED'")
                .contains("THEN 'LABOR_CONTRACT'")
                .contains("THEN 'SERVICE_CONTRACT'")
                .contains("THEN 'INTERNSHIP_AGREEMENT'")
                .contains("THEN 'OUTSOURCING_CONTRACT'")
                .contains("THEN 'SOCIAL_INSURED'")
                .contains("THEN 'SOCIAL_UNINSURED'")
                .contains("THEN 'DISPATCHED'")
                .contains("THEN 'PENDING_CONFIRMATION'");
    }

    @Test
    @DisplayName("准备度审计只读并按门店输出所有阻断项")
    void shouldShipReadOnlySigningReadinessAudit() throws Exception
    {
        String sql = readRepoFile("sql/erp_sign_profile_readiness_audit_20260711.sql");
        String normalized = sql.toLowerCase(Locale.ROOT);

        assertThat(normalized)
                .contains("employee_total")
                .contains("missing_phone_count")
                .contains("missing_id_number_count")
                .contains("missing_address_count")
                .contains("missing_post_count")
                .contains("missing_grade_count")
                .contains("missing_legal_entity_count")
                .contains("missing_contract_dates_count")
                .contains("missing_contract_type_count")
                .contains("missing_social_type_count")
                .contains("unknown_contract_type_count")
                .contains("unknown_social_type_count")
                .contains("legacy_pending_contract_count")
                .contains("template_source_missing_count")
                .doesNotContain("update ")
                .doesNotContain("delete ")
                .doesNotContain("insert ");
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
