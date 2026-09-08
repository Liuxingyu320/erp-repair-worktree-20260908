package com.erp.inventory.sql;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("V2调拨受损隔离库存原子写销加法迁移")
class TransferDiscrepancyDamageWriteOffV2SqlSourceTest
{
    private static final String FILE_NAME =
            "erp_inventory_transfer_discrepancy_damage_write_off_v2_20260803.sql";

    @Test
    @DisplayName("迁移只创建损失头与序列号消费台账且不写业务数据")
    void shouldCreateOnlyAdditiveImmutableLedgers() throws Exception
    {
        String sql = normalized();

        assertThat(sql).contains(
                "create table if not exists inv_transfer_receipt_discrepancy_damage_loss_ledger",
                "create table if not exists inv_transfer_receipt_discrepancy_damage_loss_serial",
                "request_id varchar(128)",
                "stock_ledger_request_id varchar(100)",
                "allocation_written_off_before decimal(18,4) not null",
                "allocation_written_off_after decimal(18,4) not null",
                "stock_version_before bigint not null",
                "stock_version_after bigint not null",
                "stock_quarantine_before decimal(18,4) not null",
                "stock_quarantine_after decimal(18,4) not null",
                "balance_version_before bigint not null",
                "balance_version_after bigint not null",
                "balance_quarantine_before decimal(18,4) not null",
                "balance_quarantine_after decimal(18,4) not null",
                "unique key uk_transfer_discrepancy_damage_loss_request",
                "unique key uk_transfer_discrepancy_damage_stock_request",
                "unique key uk_transfer_discrepancy_damage_loss_action_version",
                "unique key uk_transfer_discrepancy_damage_serial_receipt",
                "unique key uk_transfer_discrepancy_damage_serial_inventory");
        assertThat(count(sql, "create table if not exists")).isEqualTo(2);
        assertThat(sql).doesNotContain(
                " use ", "insert into", "replace into", "update ",
                "delete from", "truncate table", "drop table",
                "write-enabled=true", "effect-boundary-ready=true",
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
