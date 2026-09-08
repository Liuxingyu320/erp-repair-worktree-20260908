package com.erp.inventory.sql;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("V2调拨收货差异事项加法迁移")
class TransferDiscrepancyV2SqlSourceTest
{
    private static final String FILE_NAME =
            "erp_inventory_transfer_discrepancy_v2_20260802.sql";

    @Test
    @DisplayName("迁移只创建V2不可变差异事项且不执行或启用业务写入")
    void shouldRemainInertAndAdditive() throws Exception
    {
        String sql = normalized();

        assertThat(sql).contains(
                "create table if not exists inv_transfer_receipt_discrepancy_case",
                "receipt_allocation_id", "discrepancy_type",
                "discrepancy_quantity decimal(18,4)",
                "source_cost_price decimal(18,6)",
                "discrepancy_amount decimal(18,6)",
                "fact_fingerprint char(64)",
                "status varchar(32) not null default 'awaiting_confirmation'",
                "case_version bigint not null default 0",
                "unique key uk_transfer_receipt_discrepancy_dimension (receipt_allocation_id, discrepancy_type)");
        assertThat(count(sql, "create table if not exists")).isEqualTo(1);
        assertThat(sql).doesNotContain(
                " use ", "insert into", "replace into", "update ",
                "delete from", "truncate table", "drop table",
                "inv_transfer_discrepancy ",
                "write-enabled=true", "persistence-ready=true",
                "password", "secret_key", "access_token",
                "docker/mysql/db", "bosserp_new");

        Path dockerCopy = Path.of(System.getProperty("user.dir"), "..",
                "..", "docker", "mysql", "db", FILE_NAME).normalize();
        assertThat(dockerCopy).doesNotExist();
    }

    private static String normalized() throws Exception
    {
        Path path = Path.of(System.getProperty("user.dir"), "..", "..",
                "sql", FILE_NAME).normalize();
        return Files.readString(path, StandardCharsets.UTF_8)
                .toLowerCase().replaceAll("\\s+", " ");
    }

    private static int count(String value, String token)
    {
        return value.split(java.util.regex.Pattern.quote(token), -1).length
                - 1;
    }
}
