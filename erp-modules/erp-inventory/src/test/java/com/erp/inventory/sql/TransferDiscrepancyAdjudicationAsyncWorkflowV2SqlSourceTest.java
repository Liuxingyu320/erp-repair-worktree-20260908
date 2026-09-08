package com.erp.inventory.sql;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("V2调拨差异裁决异步子调拨唯一关系加法迁移")
class TransferDiscrepancyAdjudicationAsyncWorkflowV2SqlSourceTest
{
    private static final String FILE_NAME =
            "erp_inventory_transfer_discrepancy_async_workflow_v2_20260803.sql";

    @Test
    @DisplayName("迁移只增加不可变唯一关系且不写业务数据")
    void shouldRemainAdditiveIdempotentAndInert() throws Exception
    {
        String sql = normalized();

        assertThat(sql).contains(
                "create table if not exists inv_transfer_receipt_discrepancy_adjudication_workflow_link",
                "workflow_id bigint not null auto_increment",
                "request_id varchar(128)",
                "adjudication_action_id bigint not null",
                "parent_transfer_id bigint not null",
                "child_transfer_id bigint not null",
                "workflow_type varchar(16) not null",
                "inventory_source varchar(32) not null",
                "quarantine_balance_id bigint default null",
                "quarantine_lot_id bigint default null",
                "quarantine_location_id bigint default null",
                "action_quantity decimal(18,4) not null",
                "source_cost_price decimal(18,6) not null",
                "action_amount decimal(18,6) not null",
                "source_business_type varchar(64) not null",
                "effect_reference varchar(128)",
                "decision_fingerprint char(64)",
                "workflow_fingerprint char(64)",
                "unique key uk_transfer_discrepancy_workflow_request (request_id)",
                "unique key uk_transfer_discrepancy_workflow_action (adjudication_action_id)",
                "unique key uk_transfer_discrepancy_workflow_child (child_transfer_id)",
                "unique key uk_transfer_discrepancy_workflow_reference (effect_reference)",
                "unique key uk_transfer_discrepancy_workflow_source (source_business_type, source_business_id)",
                "unique key uk_transfer_discrepancy_workflow_fingerprint (workflow_fingerprint)",
                "engine=innodb");
        assertThat(count(sql, "create table if not exists")).isEqualTo(1);
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
