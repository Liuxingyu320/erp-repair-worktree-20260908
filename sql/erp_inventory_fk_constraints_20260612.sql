-- Add critical foreign keys for inventory parent-child and product references.
-- If historical orphan data exists, the ALTER TABLE statement will fail and expose it.

SET @schema_name = CONVERT(DATABASE() USING utf8mb3) COLLATE utf8mb3_tolower_ci;

SET @ddl := (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE inv_stock ADD CONSTRAINT fk_inv_stock_product FOREIGN KEY (product_id) REFERENCES inv_product(product_id) ON DELETE RESTRICT ON UPDATE RESTRICT',
        'SELECT 1'
    )
    FROM information_schema.REFERENTIAL_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = @schema_name
      AND CONSTRAINT_NAME = 'fk_inv_stock_product'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE inv_purchase_detail ADD CONSTRAINT fk_inv_purchase_detail_order FOREIGN KEY (order_id) REFERENCES inv_purchase_order(order_id) ON DELETE CASCADE ON UPDATE RESTRICT',
        'SELECT 1'
    )
    FROM information_schema.REFERENTIAL_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = @schema_name
      AND CONSTRAINT_NAME = 'fk_inv_purchase_detail_order'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE inv_purchase_detail ADD CONSTRAINT fk_inv_purchase_detail_product FOREIGN KEY (product_id) REFERENCES inv_product(product_id) ON DELETE RESTRICT ON UPDATE RESTRICT',
        'SELECT 1'
    )
    FROM information_schema.REFERENTIAL_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = @schema_name
      AND CONSTRAINT_NAME = 'fk_inv_purchase_detail_product'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE inv_sales_detail ADD CONSTRAINT fk_inv_sales_detail_order FOREIGN KEY (order_id) REFERENCES inv_sales_order(order_id) ON DELETE CASCADE ON UPDATE RESTRICT',
        'SELECT 1'
    )
    FROM information_schema.REFERENTIAL_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = @schema_name
      AND CONSTRAINT_NAME = 'fk_inv_sales_detail_order'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE inv_sales_detail ADD CONSTRAINT fk_inv_sales_detail_product FOREIGN KEY (product_id) REFERENCES inv_product(product_id) ON DELETE RESTRICT ON UPDATE RESTRICT',
        'SELECT 1'
    )
    FROM information_schema.REFERENTIAL_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = @schema_name
      AND CONSTRAINT_NAME = 'fk_inv_sales_detail_product'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE inv_transfer_detail ADD CONSTRAINT fk_inv_transfer_detail_transfer FOREIGN KEY (transfer_id) REFERENCES inv_transfer_order(transfer_id) ON DELETE CASCADE ON UPDATE RESTRICT',
        'SELECT 1'
    )
    FROM information_schema.REFERENTIAL_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = @schema_name
      AND CONSTRAINT_NAME = 'fk_inv_transfer_detail_transfer'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE inv_transfer_detail ADD CONSTRAINT fk_inv_transfer_detail_product FOREIGN KEY (product_id) REFERENCES inv_product(product_id) ON DELETE RESTRICT ON UPDATE RESTRICT',
        'SELECT 1'
    )
    FROM information_schema.REFERENTIAL_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = @schema_name
      AND CONSTRAINT_NAME = 'fk_inv_transfer_detail_product'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE inv_transfer_shipment ADD CONSTRAINT fk_inv_transfer_shipment_transfer FOREIGN KEY (transfer_id) REFERENCES inv_transfer_order(transfer_id) ON DELETE RESTRICT ON UPDATE RESTRICT',
        'SELECT 1'
    )
    FROM information_schema.REFERENTIAL_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = @schema_name
      AND CONSTRAINT_NAME = 'fk_inv_transfer_shipment_transfer'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE inv_transfer_shipment_detail ADD CONSTRAINT fk_inv_transfer_shipment_detail_shipment FOREIGN KEY (shipment_id) REFERENCES inv_transfer_shipment(shipment_id) ON DELETE CASCADE ON UPDATE RESTRICT',
        'SELECT 1'
    )
    FROM information_schema.REFERENTIAL_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = @schema_name
      AND CONSTRAINT_NAME = 'fk_inv_transfer_shipment_detail_shipment'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE inv_transfer_shipment_detail ADD CONSTRAINT fk_inv_transfer_shipment_detail_transfer FOREIGN KEY (transfer_id) REFERENCES inv_transfer_order(transfer_id) ON DELETE RESTRICT ON UPDATE RESTRICT',
        'SELECT 1'
    )
    FROM information_schema.REFERENTIAL_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = @schema_name
      AND CONSTRAINT_NAME = 'fk_inv_transfer_shipment_detail_transfer'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE inv_transfer_shipment_detail ADD CONSTRAINT fk_inv_transfer_shipment_detail_transfer_detail FOREIGN KEY (transfer_detail_id) REFERENCES inv_transfer_detail(detail_id) ON DELETE RESTRICT ON UPDATE RESTRICT',
        'SELECT 1'
    )
    FROM information_schema.REFERENTIAL_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = @schema_name
      AND CONSTRAINT_NAME = 'fk_inv_transfer_shipment_detail_transfer_detail'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE inv_transfer_shipment_detail ADD CONSTRAINT fk_inv_transfer_shipment_detail_product FOREIGN KEY (product_id) REFERENCES inv_product(product_id) ON DELETE RESTRICT ON UPDATE RESTRICT',
        'SELECT 1'
    )
    FROM information_schema.REFERENTIAL_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = @schema_name
      AND CONSTRAINT_NAME = 'fk_inv_transfer_shipment_detail_product'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
