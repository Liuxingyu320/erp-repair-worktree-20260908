-- Add critical inventory master data, shop department, and warehouse foreign keys.
-- Run erp_inventory_fk_preflight_orphan_check_20260612.sql before this script.

SET @schema_name = CONVERT(DATABASE() USING utf8mb3) COLLATE utf8mb3_tolower_ci;

SET @ddl := (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE inv_product ADD CONSTRAINT fk_inv_product_category FOREIGN KEY (category_id) REFERENCES inv_product_category(category_id) ON DELETE RESTRICT ON UPDATE RESTRICT',
        'SELECT 1'
    )
    FROM information_schema.REFERENTIAL_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = @schema_name
      AND CONSTRAINT_NAME = 'fk_inv_product_category'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE inv_product ADD CONSTRAINT fk_inv_product_shop_dept FOREIGN KEY (shop_dept_id) REFERENCES sys_dept(dept_id) ON DELETE RESTRICT ON UPDATE RESTRICT',
        'SELECT 1'
    )
    FROM information_schema.REFERENTIAL_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = @schema_name
      AND CONSTRAINT_NAME = 'fk_inv_product_shop_dept'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE inv_product_category ADD CONSTRAINT fk_inv_product_category_shop_dept FOREIGN KEY (shop_dept_id) REFERENCES sys_dept(dept_id) ON DELETE RESTRICT ON UPDATE RESTRICT',
        'SELECT 1'
    )
    FROM information_schema.REFERENTIAL_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = @schema_name
      AND CONSTRAINT_NAME = 'fk_inv_product_category_shop_dept'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE inv_customer ADD CONSTRAINT fk_inv_customer_shop_dept FOREIGN KEY (shop_dept_id) REFERENCES sys_dept(dept_id) ON DELETE RESTRICT ON UPDATE RESTRICT',
        'SELECT 1'
    )
    FROM information_schema.REFERENTIAL_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = @schema_name
      AND CONSTRAINT_NAME = 'fk_inv_customer_shop_dept'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE inv_supplier ADD CONSTRAINT fk_inv_supplier_shop_dept FOREIGN KEY (shop_dept_id) REFERENCES sys_dept(dept_id) ON DELETE RESTRICT ON UPDATE RESTRICT',
        'SELECT 1'
    )
    FROM information_schema.REFERENTIAL_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = @schema_name
      AND CONSTRAINT_NAME = 'fk_inv_supplier_shop_dept'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE inv_stock ADD CONSTRAINT fk_inv_stock_shop_dept FOREIGN KEY (shop_dept_id) REFERENCES sys_dept(dept_id) ON DELETE RESTRICT ON UPDATE RESTRICT',
        'SELECT 1'
    )
    FROM information_schema.REFERENTIAL_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = @schema_name
      AND CONSTRAINT_NAME = 'fk_inv_stock_shop_dept'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE inv_stock ADD CONSTRAINT fk_inv_stock_warehouse FOREIGN KEY (warehouse_id) REFERENCES sys_dept(dept_id) ON DELETE RESTRICT ON UPDATE RESTRICT',
        'SELECT 1'
    )
    FROM information_schema.REFERENTIAL_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = @schema_name
      AND CONSTRAINT_NAME = 'fk_inv_stock_warehouse'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE inv_purchase_detail ADD CONSTRAINT fk_inv_purchase_detail_warehouse FOREIGN KEY (warehouse_id) REFERENCES sys_dept(dept_id) ON DELETE RESTRICT ON UPDATE RESTRICT',
        'SELECT 1'
    )
    FROM information_schema.REFERENTIAL_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = @schema_name
      AND CONSTRAINT_NAME = 'fk_inv_purchase_detail_warehouse'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE inv_sales_detail ADD CONSTRAINT fk_inv_sales_detail_warehouse FOREIGN KEY (warehouse_id) REFERENCES sys_dept(dept_id) ON DELETE RESTRICT ON UPDATE RESTRICT',
        'SELECT 1'
    )
    FROM information_schema.REFERENTIAL_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = @schema_name
      AND CONSTRAINT_NAME = 'fk_inv_sales_detail_warehouse'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE inv_transfer_order ADD CONSTRAINT fk_inv_transfer_order_purchase FOREIGN KEY (purchase_id) REFERENCES inv_purchase_order(order_id) ON DELETE RESTRICT ON UPDATE RESTRICT',
        'SELECT 1'
    )
    FROM information_schema.REFERENTIAL_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = @schema_name
      AND CONSTRAINT_NAME = 'fk_inv_transfer_order_purchase'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE inv_transfer_order ADD CONSTRAINT fk_inv_transfer_order_from_dept FOREIGN KEY (from_dept_id) REFERENCES sys_dept(dept_id) ON DELETE RESTRICT ON UPDATE RESTRICT',
        'SELECT 1'
    )
    FROM information_schema.REFERENTIAL_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = @schema_name
      AND CONSTRAINT_NAME = 'fk_inv_transfer_order_from_dept'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE inv_transfer_order ADD CONSTRAINT fk_inv_transfer_order_to_dept FOREIGN KEY (to_dept_id) REFERENCES sys_dept(dept_id) ON DELETE RESTRICT ON UPDATE RESTRICT',
        'SELECT 1'
    )
    FROM information_schema.REFERENTIAL_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = @schema_name
      AND CONSTRAINT_NAME = 'fk_inv_transfer_order_to_dept'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE inv_transfer_order ADD CONSTRAINT fk_inv_transfer_order_from_warehouse FOREIGN KEY (from_warehouse_id) REFERENCES sys_dept(dept_id) ON DELETE RESTRICT ON UPDATE RESTRICT',
        'SELECT 1'
    )
    FROM information_schema.REFERENTIAL_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = @schema_name
      AND CONSTRAINT_NAME = 'fk_inv_transfer_order_from_warehouse'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE inv_transfer_order ADD CONSTRAINT fk_inv_transfer_order_to_warehouse FOREIGN KEY (to_warehouse_id) REFERENCES sys_dept(dept_id) ON DELETE RESTRICT ON UPDATE RESTRICT',
        'SELECT 1'
    )
    FROM information_schema.REFERENTIAL_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = @schema_name
      AND CONSTRAINT_NAME = 'fk_inv_transfer_order_to_warehouse'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE inv_transfer_shipment ADD CONSTRAINT fk_inv_transfer_shipment_warehouse FOREIGN KEY (warehouse_id) REFERENCES sys_dept(dept_id) ON DELETE RESTRICT ON UPDATE RESTRICT',
        'SELECT 1'
    )
    FROM information_schema.REFERENTIAL_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = @schema_name
      AND CONSTRAINT_NAME = 'fk_inv_transfer_shipment_warehouse'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE inv_transfer_shipment ADD CONSTRAINT fk_inv_transfer_shipment_warehouse_dept FOREIGN KEY (warehouse_dept_id) REFERENCES sys_dept(dept_id) ON DELETE RESTRICT ON UPDATE RESTRICT',
        'SELECT 1'
    )
    FROM information_schema.REFERENTIAL_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = @schema_name
      AND CONSTRAINT_NAME = 'fk_inv_transfer_shipment_warehouse_dept'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
