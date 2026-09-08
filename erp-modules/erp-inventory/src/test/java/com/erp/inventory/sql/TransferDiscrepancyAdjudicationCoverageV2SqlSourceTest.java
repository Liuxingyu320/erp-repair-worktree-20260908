package com.erp.inventory.sql;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("V2调拨差异裁决双覆盖维度加法迁移")
class TransferDiscrepancyAdjudicationCoverageV2SqlSourceTest
{
    private static final String FILE_NAME =
            "erp_inventory_transfer_discrepancy_adjudication_coverage_v2_20260803.sql";

    @Test
    @DisplayName("迁移只回填服务端推导维度且不会推进业务或库存状态")
    void shouldRemainAdditiveIdempotentAndNarrow() throws Exception
    {
        String sql = normalized();

        assertThat(sql).contains(
                "create procedure erp_new_2_adjudication_coverage_add_column",
                "from information_schema.columns",
                "table_schema = database()",
                "'inv_transfer_receipt_discrepancy_adjudication_action'",
                "'inv_transfer_receipt_discrepancy_adjudication_execution_event'",
                "'coverage_kind'",
                "varchar(16) default null",
                "update inv_transfer_receipt_discrepancy_adjudication_action action inner join inv_transfer_receipt_discrepancy_adjudication adjudication",
                "when adjudication.discrepancy_type = 'damaged' and action.action_type = 'responsibility_adjustment' then 'responsibility' else 'resolution' end",
                "update inv_transfer_receipt_discrepancy_adjudication_execution_event event inner join inv_transfer_receipt_discrepancy_adjudication_action action",
                "set event.coverage_kind = action.coverage_kind",
                "create procedure erp_new_2_adjudication_coverage_validate",
                "left join inv_transfer_receipt_discrepancy_adjudication adjudication",
                "where adjudication.adjudication_id is null or action.coverage_kind is null",
                "action.action_type not in ( 'reship', 'return_to_source', 'damage_write_off', 'responsibility_adjustment', 'transport_loss_write_off')",
                "sum(case when action.coverage_kind = 'resolution' then action.action_quantity else 0 end)",
                "sum(case when action.coverage_kind = 'responsibility' then action.action_quantity else 0 end)",
                "left join inv_transfer_receipt_discrepancy_adjudication_action action",
                "where action.adjudication_action_id is null or event.coverage_kind is null",
                "signal sqlstate '45000'",
                "modify column coverage_kind varchar(16) not null");
        assertThat(count(sql,
                "call erp_new_2_adjudication_coverage_add_column"))
                .isEqualTo(2);
        assertThat(count(sql, "update ")).isEqualTo(2);
        assertThat(count(sql, "modify column coverage_kind"))
                .isEqualTo(2);
        assertThat(sql).doesNotContain(
                "insert into", "replace into", "delete from",
                "truncate table", "drop table", "set status =",
                "set plan_status =", "set execution_status =",
                " inv_stock ", " inv_stock_balance_detail ",
                " inv_inventory_lot ", " inv_inventory_location ",
                " inv_inventory_serial ", "finance", "bosserp_new",
                "docker/mysql/db", "password", "secret_key",
                "access_token");

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
