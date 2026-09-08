package com.erp.system.sql;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("健康证审批发起发件箱迁移")
class HrHealthCertificateApprovalStartOutboxSqlSourceTest
{
    private static final String FILE_NAME =
            "erp_hr_health_certificate_approval_start_outbox_20260716.sql";

    @Test
    void createsDurableRoundAndIdempotencyConstraints() throws Exception
    {
        String sql = sql().toLowerCase();

        assertThat(sql).contains(
                "create table if not exists `hr_health_certificate_approval_start_outbox`",
                "unique key `uk_hr_health_approval_round` (`certificate_id`, `business_round`)",
                "unique key `uk_hr_health_approval_idempotency` (`idempotency_key`)",
                "key `idx_hr_health_approval_dispatch` (`status`, `next_retry_time`, `update_time`)",
                "`remote_instance_id` bigint",
                "`remote_business_round` int",
                "`certificate_version` bigint not null",
                "`version` bigint not null default 0");
    }

    @Test
    void containsNoSeedOrCredentialData() throws Exception
    {
        String normalized = sql().toLowerCase().replaceAll("\\s+", " ");

        assertThat(normalized).doesNotContain(
                "insert into", "replace into", "password", "access_token",
                "secret_key");
    }

    private static String sql() throws Exception
    {
        Path path = Path.of(System.getProperty("user.dir"), "..", "..",
                "sql", FILE_NAME).normalize();
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
