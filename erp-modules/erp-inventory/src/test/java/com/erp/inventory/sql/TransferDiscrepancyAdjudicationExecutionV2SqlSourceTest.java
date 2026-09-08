package com.erp.inventory.sql;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("V2调拨差异裁决动作执行事件加法迁移")
class TransferDiscrepancyAdjudicationExecutionV2SqlSourceTest
{
    private static final String FILE_NAME =
            "erp_inventory_transfer_discrepancy_adjudication_execution_v2_20260803.sql";

    @Test
    @DisplayName("迁移只增加动作版本和追加式事件结构且不写业务数据")
    void shouldRemainAdditiveIdempotentAndInert() throws Exception
    {
        String sql = normalized();

        assertThat(sql).contains(
                "create procedure erp_new_2_adjudication_execution_add_column",
                "from information_schema.columns",
                "table_schema = database()",
                "alter table `",
                "'inv_transfer_receipt_discrepancy_adjudication_action'",
                "'execution_version'",
                "bigint not null default 0",
                "'execution_effect_reference'",
                "varchar(128) default null",
                "'inv_transfer_receipt_discrepancy_adjudication_execution_event'",
                "'source_cost_price'",
                "decimal(18,6) not null comment ''裁决冻结源成本价''",
                "create procedure erp_new_2_adjudication_execution_add_index",
                "from information_schema.statistics",
                "uk_transfer_discrepancy_action_effect_reference",
                "create table if not exists "
                    + "inv_transfer_receipt_discrepancy_adjudication_execution_event",
                "request_id varchar(128)",
                "decision_fingerprint char(64)",
                "execution_command varchar(16) not null",
                "effect_kind varchar(64) not null",
                "effect_reference varchar(128) default null",
                "action_status_before varchar(32) not null",
                "action_status_after varchar(32) not null",
                "action_version_before bigint not null",
                "action_version_after bigint not null",
                "coverage_kind varchar(16) not null",
                "source_cost_price decimal(18,6) not null",
                "event_fingerprint char(64)",
                "required_permission varchar(128) not null",
                "unique key uk_transfer_discrepancy_execution_request (request_id)",
                "(adjudication_action_id, action_version_before)",
                "idx_transfer_discrepancy_execution_effect_reference",
                "(effect_kind, effect_reference, execution_event_id)");
        assertThat(count(sql, "create table if not exists"))
                .isEqualTo(1);
        assertThat(count(sql, "call erp_new_2_adjudication_execution_add_column"))
                .isEqualTo(3);
        assertThat(count(sql, "call erp_new_2_adjudication_execution_add_index"))
                .isEqualTo(1);
        assertThat(sql).doesNotContain(
                "unique key uk_transfer_discrepancy_execution_effect_reference");
        assertThat(sql).doesNotContain(
                " use ", "insert into", "replace into", "update ",
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
