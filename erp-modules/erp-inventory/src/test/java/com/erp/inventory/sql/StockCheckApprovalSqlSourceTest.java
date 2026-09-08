package com.erp.inventory.sql;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("盘点审批数据库迁移")
class StockCheckApprovalSqlSourceTest
{
    private static final String FILE_NAME = "erp_inventory_stock_check_approval_20260710.sql";

    @Test
    @DisplayName("普通迁移与 Docker 初始化脚本字节一致")
    void shouldKeepSqlCopiesIdentical() throws Exception
    {
        assertThat(Files.readAllBytes(sqlPath("sql")))
                .isEqualTo(Files.readAllBytes(sqlPath("docker/mysql/db")));
    }

    @Test
    @DisplayName("审批新表显式使用 MySQL 5.7 兼容排序规则")
    void shouldCreateApprovalTablesWithExplicitCompatibleCollation() throws Exception
    {
        assertThat(count(sql(), "ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci"))
                .isEqualTo(2);
    }

    @Test
    @DisplayName("已存在的审批表会收敛到 MySQL 5.7 兼容排序规则")
    void shouldNormalizeExistingApprovalTableCollations() throws Exception
    {
        String normalized = sql().toLowerCase().replaceAll("\\s+", " ");

        assertThat(normalized).contains(
                "create procedure normalize_stock_check_table_collation_if_needed",
                "table_collation <> 'utf8mb4_general_ci'");
        assertThat(count(normalized,
                "convert to character set utf8mb4 collate utf8mb4_general_ci"))
                .isEqualTo(2);
    }

    @Test
    @DisplayName("迁移以可重复执行方式创建审批字段表和唯一轮次")
    void shouldCreateApprovalSchemaRepeatSafely() throws Exception
    {
        String sql = sql();
        assertThat(sql).contains(
                "information_schema.COLUMNS",
                "CREATE TABLE IF NOT EXISTS inv_stock_check_approval_instance",
                "CREATE TABLE IF NOT EXISTS inv_stock_check_approval_task",
                "approval_instance_id",
                "approval_round",
                "last_reject_reason",
                "last_invalid_detail_snapshot",
                "adjustment_result_snapshot",
                "uk_inv_stock_check_approval_round");
    }

    @Test
    @DisplayName("审批权限只授予运营总监并停用旧确认权限")
    void shouldGrantApprovalOnlyToOperationsDirector() throws Exception
    {
        String sql = sql();
        assertThat(sql).contains(
                "inv:stockCheck:approve",
                "r.role_key = 'yyzj'",
                "inv:stockCheck:confirm");
        assertThat(sql).doesNotContain(
                "r.role_key = 'admin'",
                "r.role_key = 'dz'",
                "r.role_key = 'ckgly'");
    }

    @Test
    @DisplayName("重复执行不会重复追加旧确认权限停用备注")
    void shouldDisableLegacyPermissionRepeatSafely() throws Exception
    {
        String normalized = sql().toLowerCase().replaceAll("\\s+", " ");

        assertThat(normalized).contains(
                "when locate('盘点审批升级后停用', coalesce(remark, '')) > 0 then remark");
        assertThat(normalized).doesNotContain(
                "remark = concat_ws('；', nullif(remark, ''), '盘点审批升级后停用')");
    }

    @Test
    @DisplayName("历史盘点按完整性快照和差异分类且不直接改库存")
    void shouldClassifyLegacyChecksWithoutInventoryMutation() throws Exception
    {
        String sql = sql();
        assertThat(sql).contains(
                "inv_stock_check_backup_20260710",
                "inv_stock_check_detail_backup_20260710",
                "历史待确认数据实盘不完整，已退回草稿",
                "历史盘点库存快照已变化，需要重新盘点",
                "历史无差异盘点自动完成",
                "审批制度升级，需重新提交",
                "status = 'invalidated'",
                "status = 'completed'",
                "status = 'rejected'");

        String normalized = sql.toLowerCase().replaceAll("\\s+", " ");
        assertThat(normalized).doesNotContain(
                "update inv_stock set",
                "insert into inv_stock_log",
                "group_concat(json_object");
        assertThat(normalized).contains("json_arrayagg(json_object");
    }

    private static String sql() throws Exception
    {
        return Files.readString(sqlPath("sql"), StandardCharsets.UTF_8);
    }

    private static int count(String value, String needle)
    {
        int count = 0;
        int offset = 0;
        while ((offset = value.indexOf(needle, offset)) >= 0)
        {
            count++;
            offset += needle.length();
        }
        return count;
    }

    private static Path sqlPath(String directory)
    {
        return Path.of(System.getProperty("user.dir"), "..", "..", directory, FILE_NAME)
                .normalize();
    }
}
