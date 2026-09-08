package com.erp.inventory.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.erp.inventory.constant.InvTransferTypes;

@DisplayName("进销存数据库迁移")
class InventorySchemaMigrationTest
{
    @Test
    @DisplayName("库存流水业务类型容量覆盖所有调拨分类")
    void shouldExpandStockLogBusinessTypeForTransferCategories() throws Exception
    {
        String migrationSql = sqlFile(
                "erp_inventory_stock_log_business_type_20260804.sql");

        assertThat(migrationSql)
                .contains(
                        "character_maximum_length",
                        "COALESCE(@business_type_length, 0) < 40",
                        "MODIFY COLUMN business_type varchar(40)",
                        "warehouse_replenishment",
                        "cross_store_transfer")
                .doesNotContain("USE ");

        List<String> transferBusinessTypes = List.of(
                InvTransferTypes.stockBusinessType(
                        InvTransferTypes.WAREHOUSE, null),
                InvTransferTypes.stockBusinessType(
                        InvTransferTypes.STORE_RETURN, null),
                InvTransferTypes.stockBusinessType(
                        InvTransferTypes.CROSS_STORE, null),
                InvTransferTypes.stockBusinessType(
                        InvTransferTypes.WAREHOUSE, "oe_replenishment"));

        assertThat(transferBusinessTypes)
                .containsExactly(
                        "warehouse_replenishment",
                        "store_return",
                        "cross_store_transfer",
                        "oe_replenishment")
                .allSatisfy(value -> assertThat(value.length())
                        .isLessThanOrEqualTo(40));
    }

    @Test
    @DisplayName("关键库存关系有外键兜底")
    void shouldAddForeignKeysForCriticalInventoryRelationships() throws Exception
    {
        String migrationSql = sqlFile("erp_inventory_fk_constraints_20260612.sql");

        assertThat(migrationSql)
                .contains(
                        "fk_inv_stock_product",
                        "FOREIGN KEY (product_id) REFERENCES inv_product(product_id)");

        assertThat(migrationSql)
                .contains(
                        "fk_inv_purchase_detail_order",
                        "FOREIGN KEY (order_id) REFERENCES inv_purchase_order(order_id)",
                        "fk_inv_sales_detail_order",
                        "FOREIGN KEY (order_id) REFERENCES inv_sales_order(order_id)");

        assertThat(migrationSql)
                .contains(
                        "fk_inv_transfer_detail_transfer",
                        "FOREIGN KEY (transfer_id) REFERENCES inv_transfer_order(transfer_id)",
                        "fk_inv_transfer_shipment_transfer",
                        "fk_inv_transfer_shipment_detail_shipment",
                        "FOREIGN KEY (shipment_id) REFERENCES inv_transfer_shipment(shipment_id)",
                        "fk_inv_transfer_shipment_detail_transfer_detail",
                        "FOREIGN KEY (transfer_detail_id) REFERENCES inv_transfer_detail(detail_id)");
    }

    @Test
    @DisplayName("关键库存外键迁移前有只读孤儿数据检查")
    void shouldCheckOrphansBeforeAddingInventoryForeignKeys() throws Exception
    {
        String preflightSql = sqlFile("erp_inventory_fk_preflight_orphan_check_20260612.sql");

        assertThat(preflightSql)
                .doesNotContain("DELETE ", "UPDATE ", "INSERT ", "ALTER TABLE ");

        assertThat(preflightSql)
                .contains(
                        "SELECT 'inv_stock_product'",
                        "FROM inv_stock s",
                        "LEFT JOIN inv_product p ON p.product_id = s.product_id",
                        "SELECT 'inv_stock_warehouse'",
                        "LEFT JOIN sys_dept warehouse_dept ON warehouse_dept.dept_id = s.warehouse_id");

        assertThat(preflightSql)
                .contains(
                        "SELECT 'inv_product_category_shop_dept'",
                        "SELECT 'inv_purchase_detail_order'",
                        "LEFT JOIN inv_purchase_order o ON o.order_id = d.order_id",
                        "SELECT 'inv_sales_detail_order'",
                        "LEFT JOIN inv_sales_order o ON o.order_id = d.order_id",
                        "SELECT 'inv_transfer_detail_transfer'",
                        "LEFT JOIN inv_transfer_order o ON o.transfer_id = d.transfer_id");

        assertThat(preflightSql)
                .contains(
                        "SELECT 'inv_product_supplier_name'",
                        "LEFT JOIN inv_supplier s ON s.supplier_name = p.supplier_name",
                        "SELECT 'inv_sales_order_customer_name'",
                        "LEFT JOIN inv_customer c ON c.customer_name = o.customer_name",
                        "SELECT 'inv_purchase_order_supplier_name'",
                        "LEFT JOIN inv_supplier s ON s.supplier_name = o.supplier_name");
    }

    @Test
    @DisplayName("核心库存主数据和仓库关系有外键兜底")
    void shouldAddForeignKeysForInventoryMasterAndWarehouseRelationships() throws Exception
    {
        String migrationSql = sqlFile("erp_inventory_master_warehouse_fk_constraints_20260612.sql");

        assertThat(migrationSql)
                .contains(
                        "fk_inv_product_category",
                        "FOREIGN KEY (category_id) REFERENCES inv_product_category(category_id)",
                        "fk_inv_product_shop_dept",
                        "ALTER TABLE inv_product ADD CONSTRAINT fk_inv_product_shop_dept");

        assertThat(migrationSql)
                .contains(
                        "fk_inv_customer_shop_dept",
                        "ALTER TABLE inv_customer ADD CONSTRAINT fk_inv_customer_shop_dept",
                        "fk_inv_supplier_shop_dept",
                        "ALTER TABLE inv_supplier ADD CONSTRAINT fk_inv_supplier_shop_dept");

        assertThat(migrationSql)
                .contains(
                        "fk_inv_stock_shop_dept",
                        "FOREIGN KEY (shop_dept_id) REFERENCES sys_dept(dept_id)",
                        "fk_inv_stock_warehouse",
                        "FOREIGN KEY (warehouse_id) REFERENCES sys_dept(dept_id)",
                        "fk_inv_purchase_detail_warehouse",
                        "fk_inv_sales_detail_warehouse");

        assertThat(migrationSql)
                .contains(
                        "fk_inv_transfer_order_purchase",
                        "FOREIGN KEY (purchase_id) REFERENCES inv_purchase_order(order_id)",
                        "fk_inv_transfer_order_from_dept",
                        "fk_inv_transfer_order_to_dept",
                        "fk_inv_transfer_order_from_warehouse",
                        "fk_inv_transfer_order_to_warehouse",
                        "fk_inv_transfer_shipment_warehouse",
                        "fk_inv_transfer_shipment_warehouse_dept");
    }

    @Test
    @DisplayName("核心业务单据状态变化有数据库审计兜底")
    void shouldAuditCoreDocumentStatusChangesInDatabase() throws Exception
    {
        String migrationSql = sqlFile("erp_inventory_status_audit_20260612.sql");

        assertThat(migrationSql)
                .contains(
                        "CREATE TABLE IF NOT EXISTS inv_document_status_log",
                        "previous_status",
                        "new_status",
                        "operator_name",
                        "operate_time");

        assertThat(migrationSql)
                .contains(
                        "trg_inv_purchase_order_status_au",
                        "OLD.qc_status",
                        "NEW.qc_status",
                        "trg_inv_sales_order_status_au",
                        "trg_inv_purchase_return_status_au",
                        "trg_inv_sales_return_status_au",
                        "trg_inv_stock_check_status_au",
                        "trg_inv_delivery_notice_status_au");
    }

    @Test
    @DisplayName("销售采购单据关联客户供应商主数据且只安全回填")
    void shouldLinkOrderPartyMasterDataWithSafeBackfill() throws Exception
    {
        String migrationSql = sqlFile("erp_inventory_order_party_reference_20260713.sql");

        assertThat(migrationSql)
                .contains(
                        "ADD COLUMN customer_id bigint NULL",
                        "ADD COLUMN supplier_id bigint NULL",
                        "idx_inv_sales_order_customer",
                        "idx_inv_purchase_order_supplier");

        assertThat(migrationSql)
                .contains(
                        "HAVING COUNT(*) = 1",
                        "c.shop_dept_id = o.shop_dept_id",
                        "WHERE o.customer_id IS NULL",
                        "WHERE o.supplier_id IS NULL");

        assertThat(migrationSql)
                .contains(
                        "fk_inv_sales_order_customer",
                        "FOREIGN KEY (customer_id) REFERENCES inv_customer(customer_id)",
                        "fk_inv_purchase_order_supplier",
                        "FOREIGN KEY (supplier_id) REFERENCES inv_supplier(supplier_id)",
                        "ON DELETE SET NULL");
    }

    @Test
    @DisplayName("调拨身份快照迁移双份一致、幂等且不回填账号冒充姓名")
    void shouldKeepTransferAuditIdentityMigrationSafeAndByteIdentical() throws Exception
    {
        String sourceSql = sqlFile(
                "erp_inventory_transfer_audit_identity_20260807.sql");
        String dockerSql = dockerSqlFile(
                "erp_inventory_transfer_audit_identity_20260807.sql");

        assertThat(dockerSql).isEqualTo(sourceSql);
        assertThat(sourceSql)
                .contains(
                        "information_schema.columns",
                        "created_by_user_id",
                        "created_by_name",
                        "submitted_by_user_id",
                        "submitted_by_name",
                        "PREPARE stmt",
                        "DEALLOCATE PREPARE stmt",
                        "inv_transfer_status_log",
                        "action IN ('submit', 'auto_approve')",
                        "sys_user u",
                        "CONVERT(u.user_name USING utf8mb4) COLLATE utf8mb4_general_ci",
                        "CONVERT(o.create_by USING utf8mb4) COLLATE utf8mb4_general_ci",
                        "TRIM(u.nick_name)",
                        "newer.log_id > latest.log_id")
                .doesNotContain(
                        "DROP TABLE",
                        "TRUNCATE TABLE",
                        "DELETE FROM",
                        "CREATE PROCEDURE",
                        "DROP PROCEDURE",
                        "utf8mb4_0900_ai_ci",
                        "created_by_name = o.create_by",
                        "submitted_by_name = l.operator_name");
    }

    private static String sqlFile(String fileName) throws Exception
    {
        Path sqlPath = Path.of(System.getProperty("user.dir"), "..", "..", "sql", fileName).normalize();
        return Files.readString(sqlPath, StandardCharsets.UTF_8);
    }

    private static String dockerSqlFile(String fileName) throws Exception
    {
        Path sqlPath = Path.of(System.getProperty("user.dir"), "..", "..",
                "docker", "mysql", "db", fileName).normalize();
        return Files.readString(sqlPath, StandardCharsets.UTF_8);
    }
}
