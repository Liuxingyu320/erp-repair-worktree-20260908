package com.erp.inventory.sql;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("V2调拨差异双方确认事件加法迁移")
class TransferDiscrepancyConfirmationV2SqlSourceTest
{
    private static final String FILE_NAME =
            "erp_inventory_transfer_discrepancy_confirmation_v2_20260802.sql";

    @Test
    @DisplayName("迁移只创建追加事件且不写业务数据或更新事项")
    void shouldRemainAppendOnlyAndInert() throws Exception
    {
        String sql = normalized();

        assertThat(sql).contains(
                "create table if not exists inv_transfer_receipt_discrepancy_confirmation_event",
                "request_id varchar(128)",
                "case_version bigint not null",
                "fact_fingerprint char(64)",
                "party_role varchar(16) not null",
                "party_dept_id bigint not null",
                "decision varchar(16) not null",
                "confirmation_note varchar(500)",
                "operator_user_id bigint not null",
                "unique key uk_transfer_discrepancy_confirmation_request (request_id)",
                "(discrepancy_case_id, party_role, confirmation_event_id)");
        assertThat(count(sql, "create table if not exists")).isEqualTo(1);
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
