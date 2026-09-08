package com.erp.inventory.sql;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("V2调拨差异裁决不透明依据令牌加法迁移")
class TransferDiscrepancyAdjudicationBasisV2SqlSourceTest
{
    private static final String FILE_NAME =
            "erp_inventory_transfer_discrepancy_adjudication_basis_v2_20260803.sql";

    @Test
    @DisplayName("迁移只创建服务端摘要绑定和一次性消费字段")
    void shouldRemainAdditiveHashedAndInert() throws Exception
    {
        String sql = normalized();

        assertThat(sql).contains(
                "create table if not exists inv_transfer_receipt_discrepancy_adjudication_basis",
                "token_hash char(64)",
                "fact_fingerprint char(64)",
                "source_confirmation_event_id bigint not null",
                "target_confirmation_event_id bigint not null",
                "selected_shop_dept_id bigint not null",
                "scope_digest char(64)",
                "required_permission varchar(128) not null",
                "adjudicator_user_id bigint not null",
                "basis_status varchar(16) not null default 'issued'",
                "issued_at datetime not null",
                "expires_at datetime not null",
                "consumed_at datetime null",
                "consumed_request_id varchar(128)",
                "consumed_adjudication_id bigint null",
                "unique key uk_transfer_discrepancy_adjudication_basis_token (token_hash)",
                "unique key uk_transfer_discrepancy_adjudication_basis_request (consumed_request_id)",
                "unique key uk_transfer_discrepancy_adjudication_basis_adjudication (consumed_adjudication_id)",
                "(discrepancy_case_id, case_version, basis_status)");
        assertThat(count(sql, "create table if not exists")).isEqualTo(1);
        assertThat(sql).doesNotContain(
                " basis_token ", " raw_token ", " token_value ",
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
