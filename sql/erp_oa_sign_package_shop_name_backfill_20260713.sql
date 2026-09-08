-- Backfill the canonical business-context name for legacy signing packages.
-- Idempotent: populated snapshots are preserved.
UPDATE oa_sign_package package_row
INNER JOIN sys_dept department ON department.dept_id = package_row.shop_dept_id
SET package_row.shop_dept_name = department.dept_name
WHERE (package_row.shop_dept_name IS NULL OR TRIM(package_row.shop_dept_name) = '')
  AND department.del_flag = '0';
