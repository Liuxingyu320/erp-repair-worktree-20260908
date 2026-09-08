package com.erp.inventory.sql;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("V2调拨差异责任与短缺损失台账加法迁移")
class TransferDiscrepancyAtomicLedgerV2SqlSourceTest
{
    private static final String FILE_NAME =
            "erp_inventory_transfer_discrepancy_atomic_ledger_v2_20260803.sql";

    @Test
    @DisplayName("迁移只创建两个不可变业务台账且不写业务数据")
    void shouldCreateOnlyAdditiveImmutableLedgers() throws Exception
    {
        String sql = normalized();

        assertThat(sql).contains(
                "create table if not exists inv_transfer_receipt_discrepancy_responsibility_ledger",
                "create table if not exists inv_transfer_receipt_discrepancy_shortage_loss_ledger",
                "request_id varchar(128)",
                "action_version_before bigint not null",
                "action_version_after bigint not null",
                "case_version_before bigint not null",
                "case_version_after bigint not null",
                "case_status_before varchar(32) not null",
                "case_status_after varchar(32) not null",
                "plan_status_before varchar(32) not null",
                "plan_status_after varchar(32) not null",
                "action_status_before varchar(32) not null",
                "action_status_after varchar(32) not null",
                "receipt_allocation_id bigint not null",
                "shipment_allocation_id bigint not null",
                "item_type varchar(20) not null",
                "source_cost_price decimal(18,6) not null",
                "decision_fingerprint char(64)",
                "execution_fingerprint char(64)",
                "adjudication_note varchar(500) not null",
                "action_note varchar(500) not null",
                "evidence_refs varchar(2000) not null",
                "required_permission varchar(128) not null",
                "unique key uk_transfer_discrepancy_responsibility_request",
                "unique key uk_transfer_discrepancy_responsibility_action_version",
                "unique key uk_transfer_discrepancy_shortage_loss_request",
                "unique key uk_transfer_discrepancy_shortage_loss_action_version");
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
