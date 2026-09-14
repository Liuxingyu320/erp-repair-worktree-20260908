-- 应用回退保留此可空列及全部重启历史；旧应用会忽略该列。
-- 已产生重启记录时禁止删除列或运行不验证重启版本的旧写入端。
-- 本回退文件故意不删除证据、不恢复旧实盘数量。需恢复应用时先停止盘点写入口。
SELECT COUNT(*) AS restart_history_rows_to_preserve FROM inv_stock_check WHERE restart_reference_snapshot IS NOT NULL;
