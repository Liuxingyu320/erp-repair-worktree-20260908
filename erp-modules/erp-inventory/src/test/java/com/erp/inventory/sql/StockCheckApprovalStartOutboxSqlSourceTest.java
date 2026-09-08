package com.erp.inventory.sql;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("盘点审批发起发件箱迁移")
class StockCheckApprovalStartOutboxSqlSourceTest
{
    private static final String FILE_NAME =
            "erp_inventory_stock_check_approval_start_outbox_20260716.sql";

    @Test
    @DisplayName("迁移提供业务轮次和远端幂等唯一约束")
    void shouldCreateDurableIdempotentSchema() throws Exception
    {
        String sql = sql().toLowerCase();

        assertThat(sql).contains(
                "create table if not exists `inv_stock_check_approval_start_outbox`",
                "unique key `uk_stock_check_approval_round` (`check_id`, `business_round`)",
                "unique key `uk_stock_check_approval_idempotency` (`idempotency_key`)",
                "key `idx_stock_check_approval_dispatch` (`status`, `next_retry_time`, `update_time`)",
                "`remote_instance_id` bigint",
                "`remote_business_round` int",
                "`check_row_version` bigint not null",
                "`version` bigint not null default 0",
                "from information_schema.columns",
                "column_name = 'remote_business_round'",
                "alter table inv_stock_check_approval_start_outbox add column remote_business_round int default null",
                "collate=utf8mb4_general_ci");
    }

    @Test
    @DisplayName("迁移只建表不写入样例或敏感数据")
    void shouldContainNoSeedOrSensitiveData() throws Exception
    {
        String normalized = sql().toLowerCase().replaceAll("\\s+", " ");

        assertThat(normalized).doesNotContain(
                "insert into", "replace into", "password",
                "access_token", "secret_key");
    }

    private static String sql() throws Exception
    {
        Path path = Path.of(System.getProperty("user.dir"), "..", "..",
                "sql", FILE_NAME).normalize();
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
