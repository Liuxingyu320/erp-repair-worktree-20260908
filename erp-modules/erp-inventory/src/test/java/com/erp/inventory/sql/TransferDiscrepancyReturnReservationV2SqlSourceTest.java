package com.erp.inventory.sql;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("V2差异裁决退回隔离预留加法迁移")
class TransferDiscrepancyReturnReservationV2SqlSourceTest
{
    private static final String FILE_NAME =
            "erp_inventory_transfer_discrepancy_return_reservation_v2_20260803.sql";

    @Test
    @DisplayName("迁移只新增专属预留与序列号绑定且保持惰性")
    void shouldRemainAdditiveIdempotentAndInert() throws Exception
    {
        String sql = normalized();

        assertThat(sql).contains(
                "create table if not exists inv_transfer_quarantine_reservation",
                "quarantine_reservation_id bigint not null auto_increment",
                "request_id varchar(128)",
                "adjudication_action_id bigint not null",
                "child_transfer_id bigint not null",
                "child_transfer_detail_id bigint not null",
                "receipt_allocation_id bigint not null",
                "quarantine_balance_id bigint not null",
                "quarantine_lot_id bigint not null",
                "quarantine_location_id bigint not null",
                "reserved_quantity decimal(18,4) not null",
                "consumed_quantity decimal(18,4) not null",
                "released_quantity decimal(18,4) not null",
                "decision_fingerprint char(64)",
                "unique key uk_transfer_quarantine_reservation_request (request_id)",
                "unique key uk_transfer_quarantine_reservation_action (adjudication_action_id)",
                "create table if not exists inv_transfer_quarantine_reservation_serial",
                "receipt_serial_id bigint not null",
                "serial_id bigint not null",
                "unique key uk_transfer_quarantine_reservation_receipt_serial (receipt_serial_id)",
                "unique key uk_transfer_quarantine_reservation_serial (serial_id)",
                "engine=innodb");
        assertThat(count(sql, "create table if not exists")).isEqualTo(2);
        assertThat(sql).doesNotContain(
                " use ", "insert into", "replace into", "update inv_",
                "delete from", "truncate table", "drop table",
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
