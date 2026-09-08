package com.erp.inventory.sql;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("物料发货履约策略迁移")
class ItemFulfillmentPolicySqlSourceTest
{
    private static final String FILE_NAME =
            "erp_inventory_item_fulfillment_policy_20260802.sql";

    @Test
    @DisplayName("迁移只创建默认停用结构且不写样例或仓库模式")
    void shouldCreateDisabledPolicySchemaWithoutSeedData() throws Exception
    {
        String normalized = sql().toLowerCase().replaceAll("\\s+", " ");

        assertThat(normalized).contains(
                "create table if not exists inv_item_fulfillment_policy",
                "unique key uk_item_fulfillment_policy (item_type, item_id)",
                "allocation_policy",
                "tracking_policy",
                "status char(1) not null default '1'",
                "collate=utf8mb4_general_ci");
        assertThat(normalized).doesNotContain(
                "insert into", "replace into", "update inv_warehouse_stock_mode",
                "password", "access_token", "secret_key");
    }

    private static String sql() throws Exception
    {
        Path path = Path.of(System.getProperty("user.dir"), "..", "..",
                "sql", FILE_NAME).normalize();
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
