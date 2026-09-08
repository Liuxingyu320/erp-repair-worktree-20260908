package com.erp.inventory.sql;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("调拨持久化命令迁移")
class TransferCommandSqlSourceTest
{
    private static final String FILE_NAME =
            "erp_inventory_transfer_command_20260802.sql";

    @Test
    @DisplayName("迁移建立全局唯一请求和首次结果台账")
    void shouldCreatePersistentCommandLedger() throws Exception
    {
        String sql = normalized(sql());

        assertThat(sql).contains(
                "create table if not exists inv_transfer_command",
                "request_id varchar(128) character set ascii collate ascii_bin not null",
                "request_fingerprint char(64) character set ascii collate ascii_bin not null",
                "unique key uk_inv_transfer_command_request (request_id)",
                "result_payload longtext",
                "check (status in ('pending', 'succeeded'))");
        assertThat(sql).doesNotContain("result_payload longtext default");
    }

    @Test
    @DisplayName("迁移不选择数据库且不写入样例或秘密")
    void shouldRemainEnvironmentAgnosticAndSeedFree() throws Exception
    {
        String sql = normalized(sql());

        assertThat(sql).doesNotContain(
                "use ", "insert into", "replace into", "password",
                "access_token", "secret_key", "bosserp_new");
    }

    private static String sql() throws Exception
    {
        Path path = Path.of(System.getProperty("user.dir"), "..", "..",
                "sql", FILE_NAME).normalize();
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    private static String normalized(String value)
    {
        return value.toLowerCase().replaceAll("\\s+", " ").trim();
    }
}
