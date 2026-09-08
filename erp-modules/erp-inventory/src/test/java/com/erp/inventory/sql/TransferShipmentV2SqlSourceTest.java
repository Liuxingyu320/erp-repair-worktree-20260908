package com.erp.inventory.sql;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("V2调拨发货加法迁移")
class TransferShipmentV2SqlSourceTest
{
    private static final String FILE_NAME =
            "erp_inventory_transfer_shipment_v2_20260802.sql";

    @Test
    @DisplayName("迁移只增加审计结构和精度且不启用或写入业务数据")
    void shouldRemainInertAndAdditive() throws Exception
    {
        String sql = normalized();

        assertThat(sql).contains(
                "inventory_write_version",
                "command_request_id",
                "plan_version",
                "sealed_revision_id",
                "create table if not exists inv_transfer_shipment_allocation",
                "unique key uk_inv_transfer_shipment_allocation_balance (shipment_detail_id, balance_id)",
                "create table if not exists inv_transfer_shipment_serial",
                "shipment_allocation_id",
                "decimal(18,4)", "decimal(18,6)");
        assertThat(sql).doesNotContain(
                " use ", "insert into", "replace into",
                "update inv_warehouse_stock_mode", "delete from",
                "truncate table", "drop table", "password", "secret_key",
                "access_token", "docker/mysql/db", "bosserp_new");
    }

    private static String normalized() throws Exception
    {
        Path path = Path.of(System.getProperty("user.dir"), "..", "..",
                "sql", FILE_NAME).normalize();
        return Files.readString(path, StandardCharsets.UTF_8)
                .toLowerCase().replaceAll("\\s+", " ");
    }
}
