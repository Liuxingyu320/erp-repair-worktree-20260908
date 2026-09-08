package com.erp.inventory.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("批次库位迁移安全门")
class LotLocationMigrationSafetyTest
{
    @Test
    @DisplayName("新模型为增量建表且仓库默认保持 legacy")
    void shouldKeepNewStockModelDisabledByDefault() throws Exception
    {
        String schema = sqlFile("erp_inventory_lot_location_schema_20260713.sql");

        assertThat(schema).contains(
                "CREATE TABLE IF NOT EXISTS inv_warehouse_location",
                "CREATE TABLE IF NOT EXISTS inv_inventory_lot",
                "CREATE TABLE IF NOT EXISTS inv_stock_balance_detail",
                "CREATE TABLE IF NOT EXISTS inv_inventory_serial",
                "CREATE TABLE IF NOT EXISTS inv_stock_ledger_detail",
                "CREATE TABLE IF NOT EXISTS inv_warehouse_task",
                "CREATE TABLE IF NOT EXISTS inv_warehouse_stock_mode");
        assertThat(schema).contains("'legacy', 'legacy', 'not_run'");
        assertThat(schema).doesNotContain("DROP TABLE", "TRUNCATE TABLE");
    }

    @Test
    @DisplayName("回填默认 dry-run 且每个写路径受阻断门保护")
    void shouldGateHistoricalBackfill() throws Exception
    {
        String backfill = sqlFile("erp_inventory_lot_location_backfill_20260713.sql");

        assertThat(backfill).contains(
                "SET @enable_lot_location_backfill = COALESCE(@enable_lot_location_backfill, 0)",
                "@backfill_blocker_count = 0",
                "SET @backfill_can_write",
                "WHERE @backfill_can_write = 1",
                "BACKFILL_COMPLETED_KEEP_LEGACY_MODE");
        assertThat(backfill).doesNotContain("DELETE FROM inv_stock", "UPDATE inv_stock\n", "DROP TABLE");
    }

    @Test
    @DisplayName("对账脚本只读且覆盖数量、成本、维度和流水")
    void shouldKeepReconcileReadOnlyAndComplete() throws Exception
    {
        String reconcile = sqlFile("erp_inventory_lot_location_reconcile_20260713.sql");

        assertThat(reconcile).doesNotContain(
                "INSERT INTO", "UPDATE ", "DELETE FROM", "ALTER TABLE", "DROP TABLE", "CREATE TABLE");
        assertThat(reconcile).contains(
                "legacy_detail_diff_count",
                "detail_invariant_issue_count",
                "orphan_dimension_issue_count",
                "source_mapping_issue_count",
                "serial_issue_count",
                "lot_issue_count",
                "ledger_issue_count",
                "task_issue_count",
                "unsafe_mode_issue_count");
    }

    @Test
    @DisplayName("回退只切回旧读写且保留不可变事实")
    void shouldRollbackModeWithoutDeletingFacts() throws Exception
    {
        String rollback = sqlFile("erp_inventory_lot_location_rollback_20260713.sql");

        assertThat(rollback).contains(
                "SET @enable_lot_location_rollback = COALESCE(@enable_lot_location_rollback, 0)",
                "@target_warehouse_id IS NOT NULL",
                "write_mode = 'legacy'",
                "read_mode = 'legacy'",
                "inv_warehouse_stock_mode_history",
                "ROLLED_BACK_TO_LEGACY_KEEP_FACTS");
        assertThat(rollback).doesNotContain("DELETE FROM", "DROP TABLE", "TRUNCATE TABLE");
    }

    @Test
    @DisplayName("每日健康检查保持只读并覆盖核心业务链")
    void shouldKeepDailyHealthcheckReadOnly() throws Exception
    {
        String healthcheck = sqlFile("erp_inventory_daily_healthcheck_20260713.sql");

        assertThat(healthcheck).doesNotContain(
                "INSERT INTO", "UPDATE ", "DELETE FROM", "ALTER TABLE", "DROP TABLE", "TRUNCATE TABLE");
        assertThat(healthcheck).contains(
                "inv_transfer_approval_instance",
                "purchase_qc_broken_count",
                "receipt_quality_broken_count",
                "stock_broken_count",
                "stock_log_broken_count",
                "stale_stock_check_draft_count");
    }

    @Test
    @DisplayName("高频索引迁移幂等且不改写业务数据")
    void shouldAddOnlyIdempotentPerformanceIndexes() throws Exception
    {
        String indexes = sqlFile("erp_inventory_performance_indexes_20260713.sql");

        assertThat(indexes).contains(
                "information_schema.STATISTICS",
                "idx_inbound_qc_order_warehouse_time",
                "idx_stock_log_warehouse_time",
                "idx_stock_log_business_movement",
                "idx_purchase_warehouse_stage_time",
                "idx_stock_check_warehouse_status_due",
                "ALGORITHM=INPLACE, LOCK=NONE");
        assertThat(indexes).doesNotContain("UPDATE inv_", "DELETE FROM inv_", "TRUNCATE TABLE", "DROP TABLE inv_");
    }

    private static String sqlFile(String fileName) throws Exception
    {
        Path sqlPath = Path.of(System.getProperty("user.dir"), "..", "..", "sql", fileName).normalize();
        return Files.readString(sqlPath, StandardCharsets.UTF_8);
    }
}
