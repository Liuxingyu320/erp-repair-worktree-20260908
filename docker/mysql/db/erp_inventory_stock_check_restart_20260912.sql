-- 盘点重启参考和输入版本；MySQL 5.7 / 8.0 幂等增量。先迁移后发布，不修改历史数量。
SET @stock_check_restart_ddl := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE inv_stock_check ADD COLUMN restart_reference_snapshot LONGTEXT NULL COMMENT ''重启版本及各轮原始明细，仅服务端使用''',
        'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'inv_stock_check' AND COLUMN_NAME = 'restart_reference_snapshot'
);
PREPARE stock_check_restart_stmt FROM @stock_check_restart_ddl;
EXECUTE stock_check_restart_stmt;
DEALLOCATE PREPARE stock_check_restart_stmt;
