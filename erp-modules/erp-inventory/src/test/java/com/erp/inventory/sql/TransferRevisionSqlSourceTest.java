package com.erp.inventory.sql;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("调拨不可变业务版本迁移")
class TransferRevisionSqlSourceTest
{
    private static final String FILE_NAME =
            "erp_inventory_transfer_revision_20260802.sql";

    @Test
    @DisplayName("迁移建立稳定主单下的版本链和审批轮次唯一性")
    void shouldCreateImmutableRevisionLedger() throws Exception
    {
        String sql = normalized(sql());

        assertThat(sql).contains(
                "create table if not exists inv_transfer_revision",
                "unique key uk_inv_transfer_revision_no (transfer_id, revision_no)",
                "unique key uk_inv_transfer_revision_round (transfer_id, approval_round)",
                "parent_revision_id bigint default null",
                "header_snapshot longtext not null",
                "detail_snapshot longtext not null",
                "snapshot_hash char(64) character set ascii collate ascii_bin not null");
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
