package com.erp.inventory.sql;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("V2调拨收货加法迁移")
class TransferReceiptV2SqlSourceTest
{
    private static final String FILE_NAME =
            "erp_inventory_transfer_receipt_v2_20260802.sql";

    @Test
    @DisplayName("迁移只增加专属收货事实且不启用或写入业务数据")
    void shouldRemainInertAndAdditive() throws Exception
    {
        String sql = normalized();

        assertThat(sql).contains(
                "accepted_received_quantity",
                "damaged_received_quantity",
                "shortage_reported_quantity",
                "receipt_version",
                "create table if not exists inv_transfer_shipment_receipt",
                "create table if not exists inv_transfer_shipment_receipt_allocation",
                "create table if not exists inv_transfer_shipment_receipt_serial",
                "unique key uk_transfer_receipt_request (command_request_id)",
                "source_lot_id", "receipt_disposition",
                "quarantine_quantity",
                "uk_inventory_lot_transfer_receipt",
                "uk_stock_ledger_receipt_disposition",
                "unique key uk_transfer_receipt_serial_once (shipment_id, shipment_serial_id)",
                "erp_new_2_receipt_add_column",
                "erp_new_2_receipt_add_index");
        assertThat(count(sql, "'quarantine_quantity'")).isEqualTo(2);
        assertThat(sql).doesNotContain(
                " use ", "insert into", "replace into", "update ",
                "delete from", "truncate table", "drop table",
                "sys_config", "write-enabled=true",
                "persistence-ready=true", "password", "secret_key",
                "access_token", "docker/mysql/db", "bosserp_new");
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
