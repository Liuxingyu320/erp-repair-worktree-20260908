package com.erp.inventory.sql;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("调拨库存预留迁移")
class TransferReservationSqlSourceTest
{
    private static final String FILE_NAME =
            "erp_inventory_transfer_reservation_20260802.sql";

    @Test
    @DisplayName("迁移提供明细唯一归属和数量守恒字段")
    void shouldCreateOwnedReservationLedger() throws Exception
    {
        String sql = normalized(sql());

        assertThat(sql).contains(
                "create table if not exists inv_transfer_reservation",
                "unique key uk_inv_transfer_reservation_detail_round (transfer_id, transfer_detail_id, reservation_round)",
                "key idx_inv_transfer_reservation_transfer (transfer_id, reservation_round, stock_id, reservation_id)",
                "reserved_quantity decimal(18,4) not null default 0",
                "consumed_quantity decimal(18,4) not null default 0",
                "released_quantity decimal(18,4) not null default 0",
                "consumed_quantity + released_quantity <= reserved_quantity");
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
