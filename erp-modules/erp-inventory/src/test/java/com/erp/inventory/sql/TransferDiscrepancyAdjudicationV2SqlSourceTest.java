package com.erp.inventory.sql;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("V2调拨差异独立裁决计划加法迁移")
class TransferDiscrepancyAdjudicationV2SqlSourceTest
{
    private static final String FILE_NAME =
            "erp_inventory_transfer_discrepancy_adjudication_v2_20260802.sql";

    @Test
    @DisplayName("迁移只创建计划头和动作行且不写业务数据")
    void shouldRemainAdditiveAndInert() throws Exception
    {
        String sql = normalized();

        assertThat(sql).contains(
                "create table if not exists inv_transfer_receipt_discrepancy_adjudication",
                "create table if not exists inv_transfer_receipt_discrepancy_adjudication_action",
                "source_confirmation_event_id bigint not null",
                "target_confirmation_event_id bigint not null",
                "decision_fingerprint char(64)",
                "required_permission varchar(128) not null",
                "plan_status varchar(32) not null default 'adjudication_planned'",
                "unique key uk_transfer_discrepancy_adjudication_request (request_id)",
                "(discrepancy_case_id, case_version_before)",
                "action_sequence int not null",
                "coverage_kind varchar(16) not null",
                "resolution/responsibility",
                "action_quantity decimal(18,4) not null",
                "action_amount decimal(18,6) not null",
                "execution_status varchar(32) not null default 'pending'",
                "(adjudication_id, action_sequence)");
        assertThat(count(sql, "create table if not exists")).isEqualTo(2);
        assertThat(sql).doesNotContain(
                " use ", "insert into", "replace into", "update ",
                "delete from", "truncate table", "drop table",
                "alter table", "inv_transfer_discrepancy ",
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
