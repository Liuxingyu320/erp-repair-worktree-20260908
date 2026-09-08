-- 归一化库存归属：
-- 1. inv_stock 按 商品 + 实际库存组织 合并，只保留一条库存现量。
-- 2. inv_stock_log 只修正组织坐标，不改历史数量。
-- 实际库存组织 = warehouse_id 非空时取 warehouse_id，否则取 shop_dept_id。

CREATE TABLE IF NOT EXISTS inv_stock_cleanup_backup_20260607 LIKE inv_stock;

INSERT INTO inv_stock_cleanup_backup_20260607
SELECT s.*
FROM inv_stock s
WHERE (s.warehouse_id IS NULL OR s.warehouse_id = 0 OR s.shop_dept_id <> s.warehouse_id)
  AND NOT EXISTS (
      SELECT 1
      FROM inv_stock_cleanup_backup_20260607 b
      WHERE b.stock_id = s.stock_id
  );

CREATE TABLE IF NOT EXISTS inv_stock_log_cleanup_backup_20260607 LIKE inv_stock_log;

INSERT INTO inv_stock_log_cleanup_backup_20260607
SELECT l.*
FROM inv_stock_log l
WHERE (l.warehouse_id IS NULL OR l.warehouse_id = 0 OR l.shop_dept_id <> l.warehouse_id)
  AND NOT EXISTS (
      SELECT 1
      FROM inv_stock_log_cleanup_backup_20260607 b
      WHERE b.log_id = l.log_id
  );

START TRANSACTION;

DROP TEMPORARY TABLE IF EXISTS tmp_inv_stock_normalized;
CREATE TEMPORARY TABLE tmp_inv_stock_normalized AS
SELECT stock_id,
       product_id,
       COALESCE(NULLIF(warehouse_id, 0), shop_dept_id) AS target_dept_id
FROM inv_stock;

DROP TEMPORARY TABLE IF EXISTS tmp_inv_stock_rollup;
CREATE TEMPORARY TABLE tmp_inv_stock_rollup AS
SELECT n.product_id,
       n.target_dept_id,
       COALESCE(
           MIN(CASE WHEN s.warehouse_id = n.target_dept_id THEN s.stock_id END),
           MIN(s.stock_id)
       ) AS primary_stock_id,
       COALESCE(SUM(s.current_quantity), 0) AS current_quantity,
       COALESCE(SUM(s.locked_quantity), 0) AS locked_quantity,
       COALESCE(SUM(s.available_quantity), 0) AS available_quantity,
       COALESCE(SUM(s.total_cost), 0) AS total_cost,
       MAX(s.last_in_time) AS last_in_time,
       MAX(s.last_out_time) AS last_out_time
FROM tmp_inv_stock_normalized n
     INNER JOIN inv_stock s ON s.stock_id = n.stock_id
GROUP BY n.product_id, n.target_dept_id;

CREATE INDEX idx_tmp_inv_stock_normalized ON tmp_inv_stock_normalized(product_id, target_dept_id, stock_id);
CREATE INDEX idx_tmp_inv_stock_rollup ON tmp_inv_stock_rollup(product_id, target_dept_id, primary_stock_id);

UPDATE inv_stock s
     INNER JOIN tmp_inv_stock_rollup r ON r.primary_stock_id = s.stock_id
SET s.shop_dept_id = r.target_dept_id,
    s.warehouse_id = r.target_dept_id,
    s.current_quantity = r.current_quantity,
    s.locked_quantity = r.locked_quantity,
    s.available_quantity = r.available_quantity,
    s.total_cost = r.total_cost,
    s.cost_price = CASE
        WHEN r.current_quantity <> 0 THEN ROUND(r.total_cost / r.current_quantity, 2)
        ELSE s.cost_price
    END,
    s.last_in_time = r.last_in_time,
    s.last_out_time = r.last_out_time,
    s.update_by = 'stock_scope_cleanup',
    s.update_time = NOW();

DELETE s
FROM inv_stock s
     INNER JOIN tmp_inv_stock_normalized n ON n.stock_id = s.stock_id
     INNER JOIN tmp_inv_stock_rollup r
         ON r.product_id = n.product_id
        AND r.target_dept_id = n.target_dept_id
WHERE s.stock_id <> r.primary_stock_id;

UPDATE inv_stock s
SET s.shop_dept_id = COALESCE(NULLIF(s.warehouse_id, 0), s.shop_dept_id),
    s.warehouse_id = COALESCE(NULLIF(s.warehouse_id, 0), s.shop_dept_id),
    s.update_by = 'stock_scope_cleanup',
    s.update_time = NOW()
WHERE s.warehouse_id IS NULL
   OR s.warehouse_id = 0
   OR s.shop_dept_id <> s.warehouse_id;

UPDATE inv_stock_log l
SET l.shop_dept_id = COALESCE(NULLIF(l.warehouse_id, 0), l.shop_dept_id),
    l.warehouse_id = COALESCE(NULLIF(l.warehouse_id, 0), l.shop_dept_id)
WHERE l.warehouse_id IS NULL
   OR l.warehouse_id = 0
   OR l.shop_dept_id <> l.warehouse_id;

COMMIT;
