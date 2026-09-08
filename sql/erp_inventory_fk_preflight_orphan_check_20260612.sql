-- Read-only orphan checks for inventory foreign key migrations.
-- Run this script before applying inventory FK constraint scripts.

SELECT 'inv_stock_product' AS check_name,
       COUNT(*) AS orphan_count,
       SUBSTRING_INDEX(GROUP_CONCAT(CAST(s.stock_id AS CHAR) ORDER BY s.stock_id SEPARATOR ','), ',', 20) AS sample_keys
FROM inv_stock s
LEFT JOIN inv_product p ON p.product_id = s.product_id
WHERE s.product_id IS NOT NULL
  AND p.product_id IS NULL;

SELECT 'inv_stock_shop_dept' AS check_name,
       COUNT(*) AS orphan_count,
       SUBSTRING_INDEX(GROUP_CONCAT(CAST(s.stock_id AS CHAR) ORDER BY s.stock_id SEPARATOR ','), ',', 20) AS sample_keys
FROM inv_stock s
LEFT JOIN sys_dept shop_dept ON shop_dept.dept_id = s.shop_dept_id
WHERE s.shop_dept_id IS NOT NULL
  AND shop_dept.dept_id IS NULL;

SELECT 'inv_stock_warehouse' AS check_name,
       COUNT(*) AS orphan_count,
       SUBSTRING_INDEX(GROUP_CONCAT(CAST(s.stock_id AS CHAR) ORDER BY s.stock_id SEPARATOR ','), ',', 20) AS sample_keys
FROM inv_stock s
LEFT JOIN sys_dept warehouse_dept ON warehouse_dept.dept_id = s.warehouse_id
WHERE s.warehouse_id IS NOT NULL
  AND warehouse_dept.dept_id IS NULL;

SELECT 'inv_product_category' AS check_name,
       COUNT(*) AS orphan_count,
       SUBSTRING_INDEX(GROUP_CONCAT(CAST(p.product_id AS CHAR) ORDER BY p.product_id SEPARATOR ','), ',', 20) AS sample_keys
FROM inv_product p
LEFT JOIN inv_product_category c ON c.category_id = p.category_id
WHERE p.category_id IS NOT NULL
  AND c.category_id IS NULL;

SELECT 'inv_product_category_shop_dept' AS check_name,
       COUNT(*) AS orphan_count,
       SUBSTRING_INDEX(GROUP_CONCAT(CAST(c.category_id AS CHAR) ORDER BY c.category_id SEPARATOR ','), ',', 20) AS sample_keys
FROM inv_product_category c
LEFT JOIN sys_dept shop_dept ON shop_dept.dept_id = c.shop_dept_id
WHERE c.shop_dept_id IS NOT NULL
  AND shop_dept.dept_id IS NULL;

SELECT 'inv_product_shop_dept' AS check_name,
       COUNT(*) AS orphan_count,
       SUBSTRING_INDEX(GROUP_CONCAT(CAST(p.product_id AS CHAR) ORDER BY p.product_id SEPARATOR ','), ',', 20) AS sample_keys
FROM inv_product p
LEFT JOIN sys_dept shop_dept ON shop_dept.dept_id = p.shop_dept_id
WHERE p.shop_dept_id IS NOT NULL
  AND shop_dept.dept_id IS NULL;

SELECT 'inv_product_supplier_name' AS check_name,
       COUNT(*) AS orphan_count,
       SUBSTRING_INDEX(GROUP_CONCAT(CAST(p.product_id AS CHAR) ORDER BY p.product_id SEPARATOR ','), ',', 20) AS sample_keys
FROM inv_product p
LEFT JOIN inv_supplier s ON s.supplier_name = p.supplier_name
                         AND s.shop_dept_id = p.shop_dept_id
                         AND s.status = '0'
                         AND s.cooperation_status = '0'
WHERE p.supplier_name IS NOT NULL
  AND p.supplier_name <> ''
  AND p.del_flag = '0'
  AND s.supplier_id IS NULL;

SELECT 'inv_customer_shop_dept' AS check_name,
       COUNT(*) AS orphan_count,
       SUBSTRING_INDEX(GROUP_CONCAT(CAST(c.customer_id AS CHAR) ORDER BY c.customer_id SEPARATOR ','), ',', 20) AS sample_keys
FROM inv_customer c
LEFT JOIN sys_dept shop_dept ON shop_dept.dept_id = c.shop_dept_id
WHERE c.shop_dept_id IS NOT NULL
  AND shop_dept.dept_id IS NULL;

SELECT 'inv_supplier_shop_dept' AS check_name,
       COUNT(*) AS orphan_count,
       SUBSTRING_INDEX(GROUP_CONCAT(CAST(s.supplier_id AS CHAR) ORDER BY s.supplier_id SEPARATOR ','), ',', 20) AS sample_keys
FROM inv_supplier s
LEFT JOIN sys_dept shop_dept ON shop_dept.dept_id = s.shop_dept_id
WHERE s.shop_dept_id IS NOT NULL
  AND shop_dept.dept_id IS NULL;

SELECT 'inv_purchase_order_supplier_name' AS check_name,
       COUNT(*) AS orphan_count,
       SUBSTRING_INDEX(GROUP_CONCAT(CAST(o.order_id AS CHAR) ORDER BY o.order_id SEPARATOR ','), ',', 20) AS sample_keys
FROM inv_purchase_order o
LEFT JOIN inv_supplier s ON s.supplier_name = o.supplier_name
                         AND s.shop_dept_id = o.shop_dept_id
                         AND s.status = '0'
                         AND s.cooperation_status = '0'
WHERE o.supplier_name IS NOT NULL
  AND o.supplier_name <> ''
  AND s.supplier_id IS NULL;

SELECT 'inv_sales_order_customer_name' AS check_name,
       COUNT(*) AS orphan_count,
       SUBSTRING_INDEX(GROUP_CONCAT(CAST(o.order_id AS CHAR) ORDER BY o.order_id SEPARATOR ','), ',', 20) AS sample_keys
FROM inv_sales_order o
LEFT JOIN inv_customer c ON c.customer_name = o.customer_name
                         AND c.shop_dept_id = o.shop_dept_id
                         AND c.status = '0'
WHERE o.customer_name IS NOT NULL
  AND o.customer_name <> ''
  AND c.customer_id IS NULL;

SELECT 'inv_purchase_detail_order' AS check_name,
       COUNT(*) AS orphan_count,
       SUBSTRING_INDEX(GROUP_CONCAT(CAST(d.detail_id AS CHAR) ORDER BY d.detail_id SEPARATOR ','), ',', 20) AS sample_keys
FROM inv_purchase_detail d
LEFT JOIN inv_purchase_order o ON o.order_id = d.order_id
WHERE d.order_id IS NOT NULL
  AND o.order_id IS NULL;

SELECT 'inv_purchase_detail_product' AS check_name,
       COUNT(*) AS orphan_count,
       SUBSTRING_INDEX(GROUP_CONCAT(CAST(d.detail_id AS CHAR) ORDER BY d.detail_id SEPARATOR ','), ',', 20) AS sample_keys
FROM inv_purchase_detail d
LEFT JOIN inv_product p ON p.product_id = d.product_id
WHERE d.product_id IS NOT NULL
  AND p.product_id IS NULL;

SELECT 'inv_purchase_detail_warehouse' AS check_name,
       COUNT(*) AS orphan_count,
       SUBSTRING_INDEX(GROUP_CONCAT(CAST(d.detail_id AS CHAR) ORDER BY d.detail_id SEPARATOR ','), ',', 20) AS sample_keys
FROM inv_purchase_detail d
LEFT JOIN sys_dept warehouse_dept ON warehouse_dept.dept_id = d.warehouse_id
WHERE d.warehouse_id IS NOT NULL
  AND warehouse_dept.dept_id IS NULL;

SELECT 'inv_sales_detail_order' AS check_name,
       COUNT(*) AS orphan_count,
       SUBSTRING_INDEX(GROUP_CONCAT(CAST(d.detail_id AS CHAR) ORDER BY d.detail_id SEPARATOR ','), ',', 20) AS sample_keys
FROM inv_sales_detail d
LEFT JOIN inv_sales_order o ON o.order_id = d.order_id
WHERE d.order_id IS NOT NULL
  AND o.order_id IS NULL;

SELECT 'inv_sales_detail_product' AS check_name,
       COUNT(*) AS orphan_count,
       SUBSTRING_INDEX(GROUP_CONCAT(CAST(d.detail_id AS CHAR) ORDER BY d.detail_id SEPARATOR ','), ',', 20) AS sample_keys
FROM inv_sales_detail d
LEFT JOIN inv_product p ON p.product_id = d.product_id
WHERE d.product_id IS NOT NULL
  AND p.product_id IS NULL;

SELECT 'inv_sales_detail_warehouse' AS check_name,
       COUNT(*) AS orphan_count,
       SUBSTRING_INDEX(GROUP_CONCAT(CAST(d.detail_id AS CHAR) ORDER BY d.detail_id SEPARATOR ','), ',', 20) AS sample_keys
FROM inv_sales_detail d
LEFT JOIN sys_dept warehouse_dept ON warehouse_dept.dept_id = d.warehouse_id
WHERE d.warehouse_id IS NOT NULL
  AND warehouse_dept.dept_id IS NULL;

SELECT 'inv_transfer_order_purchase' AS check_name,
       COUNT(*) AS orphan_count,
       SUBSTRING_INDEX(GROUP_CONCAT(CAST(o.transfer_id AS CHAR) ORDER BY o.transfer_id SEPARATOR ','), ',', 20) AS sample_keys
FROM inv_transfer_order o
LEFT JOIN inv_purchase_order p ON p.order_id = o.purchase_id
WHERE o.purchase_id IS NOT NULL
  AND p.order_id IS NULL;

SELECT 'inv_transfer_order_from_dept' AS check_name,
       COUNT(*) AS orphan_count,
       SUBSTRING_INDEX(GROUP_CONCAT(CAST(o.transfer_id AS CHAR) ORDER BY o.transfer_id SEPARATOR ','), ',', 20) AS sample_keys
FROM inv_transfer_order o
LEFT JOIN sys_dept from_dept ON from_dept.dept_id = o.from_dept_id
WHERE o.from_dept_id IS NOT NULL
  AND from_dept.dept_id IS NULL;

SELECT 'inv_transfer_order_to_dept' AS check_name,
       COUNT(*) AS orphan_count,
       SUBSTRING_INDEX(GROUP_CONCAT(CAST(o.transfer_id AS CHAR) ORDER BY o.transfer_id SEPARATOR ','), ',', 20) AS sample_keys
FROM inv_transfer_order o
LEFT JOIN sys_dept to_dept ON to_dept.dept_id = o.to_dept_id
WHERE o.to_dept_id IS NOT NULL
  AND to_dept.dept_id IS NULL;

SELECT 'inv_transfer_order_from_warehouse' AS check_name,
       COUNT(*) AS orphan_count,
       SUBSTRING_INDEX(GROUP_CONCAT(CAST(o.transfer_id AS CHAR) ORDER BY o.transfer_id SEPARATOR ','), ',', 20) AS sample_keys
FROM inv_transfer_order o
LEFT JOIN sys_dept from_warehouse ON from_warehouse.dept_id = o.from_warehouse_id
WHERE o.from_warehouse_id IS NOT NULL
  AND from_warehouse.dept_id IS NULL;

SELECT 'inv_transfer_order_to_warehouse' AS check_name,
       COUNT(*) AS orphan_count,
       SUBSTRING_INDEX(GROUP_CONCAT(CAST(o.transfer_id AS CHAR) ORDER BY o.transfer_id SEPARATOR ','), ',', 20) AS sample_keys
FROM inv_transfer_order o
LEFT JOIN sys_dept to_warehouse ON to_warehouse.dept_id = o.to_warehouse_id
WHERE o.to_warehouse_id IS NOT NULL
  AND to_warehouse.dept_id IS NULL;

SELECT 'inv_transfer_detail_transfer' AS check_name,
       COUNT(*) AS orphan_count,
       SUBSTRING_INDEX(GROUP_CONCAT(CAST(d.detail_id AS CHAR) ORDER BY d.detail_id SEPARATOR ','), ',', 20) AS sample_keys
FROM inv_transfer_detail d
LEFT JOIN inv_transfer_order o ON o.transfer_id = d.transfer_id
WHERE d.transfer_id IS NOT NULL
  AND o.transfer_id IS NULL;

SELECT 'inv_transfer_detail_product' AS check_name,
       COUNT(*) AS orphan_count,
       SUBSTRING_INDEX(GROUP_CONCAT(CAST(d.detail_id AS CHAR) ORDER BY d.detail_id SEPARATOR ','), ',', 20) AS sample_keys
FROM inv_transfer_detail d
LEFT JOIN inv_product p ON p.product_id = d.product_id
WHERE d.product_id IS NOT NULL
  AND p.product_id IS NULL;

SELECT 'inv_transfer_shipment_transfer' AS check_name,
       COUNT(*) AS orphan_count,
       SUBSTRING_INDEX(GROUP_CONCAT(CAST(s.shipment_id AS CHAR) ORDER BY s.shipment_id SEPARATOR ','), ',', 20) AS sample_keys
FROM inv_transfer_shipment s
LEFT JOIN inv_transfer_order o ON o.transfer_id = s.transfer_id
WHERE s.transfer_id IS NOT NULL
  AND o.transfer_id IS NULL;

SELECT 'inv_transfer_shipment_warehouse' AS check_name,
       COUNT(*) AS orphan_count,
       SUBSTRING_INDEX(GROUP_CONCAT(CAST(s.shipment_id AS CHAR) ORDER BY s.shipment_id SEPARATOR ','), ',', 20) AS sample_keys
FROM inv_transfer_shipment s
LEFT JOIN sys_dept warehouse_dept ON warehouse_dept.dept_id = s.warehouse_id
WHERE s.warehouse_id IS NOT NULL
  AND warehouse_dept.dept_id IS NULL;

SELECT 'inv_transfer_shipment_warehouse_dept' AS check_name,
       COUNT(*) AS orphan_count,
       SUBSTRING_INDEX(GROUP_CONCAT(CAST(s.shipment_id AS CHAR) ORDER BY s.shipment_id SEPARATOR ','), ',', 20) AS sample_keys
FROM inv_transfer_shipment s
LEFT JOIN sys_dept warehouse_dept ON warehouse_dept.dept_id = s.warehouse_dept_id
WHERE s.warehouse_dept_id IS NOT NULL
  AND warehouse_dept.dept_id IS NULL;

SELECT 'inv_transfer_shipment_detail_shipment' AS check_name,
       COUNT(*) AS orphan_count,
       SUBSTRING_INDEX(GROUP_CONCAT(CAST(d.shipment_detail_id AS CHAR) ORDER BY d.shipment_detail_id SEPARATOR ','), ',', 20) AS sample_keys
FROM inv_transfer_shipment_detail d
LEFT JOIN inv_transfer_shipment s ON s.shipment_id = d.shipment_id
WHERE d.shipment_id IS NOT NULL
  AND s.shipment_id IS NULL;

SELECT 'inv_transfer_shipment_detail_transfer' AS check_name,
       COUNT(*) AS orphan_count,
       SUBSTRING_INDEX(GROUP_CONCAT(CAST(d.shipment_detail_id AS CHAR) ORDER BY d.shipment_detail_id SEPARATOR ','), ',', 20) AS sample_keys
FROM inv_transfer_shipment_detail d
LEFT JOIN inv_transfer_order o ON o.transfer_id = d.transfer_id
WHERE d.transfer_id IS NOT NULL
  AND o.transfer_id IS NULL;

SELECT 'inv_transfer_shipment_detail_transfer_detail' AS check_name,
       COUNT(*) AS orphan_count,
       SUBSTRING_INDEX(GROUP_CONCAT(CAST(d.shipment_detail_id AS CHAR) ORDER BY d.shipment_detail_id SEPARATOR ','), ',', 20) AS sample_keys
FROM inv_transfer_shipment_detail d
LEFT JOIN inv_transfer_detail td ON td.detail_id = d.transfer_detail_id
WHERE d.transfer_detail_id IS NOT NULL
  AND td.detail_id IS NULL;

SELECT 'inv_transfer_shipment_detail_product' AS check_name,
       COUNT(*) AS orphan_count,
       SUBSTRING_INDEX(GROUP_CONCAT(CAST(d.shipment_detail_id AS CHAR) ORDER BY d.shipment_detail_id SEPARATOR ','), ',', 20) AS sample_keys
FROM inv_transfer_shipment_detail d
LEFT JOIN inv_product p ON p.product_id = d.product_id
WHERE d.product_id IS NOT NULL
  AND p.product_id IS NULL;
