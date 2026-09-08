package com.erp.inventory.sql;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("V2差异退回隔离预留生命周期加法迁移")
class TransferDiscrepancyReturnLifecycleV2SqlSourceTest
{
    private static final String FILE_NAME =
            "erp_inventory_transfer_discrepancy_return_lifecycle_v2_20260803.sql";

    @Test
    @DisplayName("迁移只增加消费释放证据和序列号生命周期且保持惰性")
    void shouldRemainAdditiveAndInert() throws Exception
    {
        String sql = normalized();

        assertThat(sql).contains(
                "create table if not exists inv_transfer_quarantine_reservation_consumption",
                "consumption_id bigint not null auto_increment",
                "quarantine_reservation_id bigint not null",
                "shipment_id bigint not null",
                "shipment_detail_id bigint not null",
                "consumed_quantity decimal(18,4) not null",
                "consumed_amount decimal(18,6) not null",
                "unique key uk_transfer_quarantine_consumption_request (request_id)",
                "create table if not exists inv_transfer_quarantine_reservation_release",
                "release_id bigint not null auto_increment",
                "reason_code varchar(32) not null",
                "released_quantity decimal(18,4) not null",
                "unique key uk_transfer_quarantine_release_reservation (quarantine_reservation_id)",
                "alter table inv_transfer_quarantine_reservation_serial",
                "add column lifecycle_status varchar(16) not null default 'active'",
                "add column consumption_id bigint default null",
                "add column release_id bigint default null",
                "add column consumed_shipment_id bigint default null",
                "add column version bigint not null default 0",
                "engine=innodb");
        assertThat(count(sql, "create table if not exists")).isEqualTo(2);
        assertThat(count(sql, "alter table")).isEqualTo(1);
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
