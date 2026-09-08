-- Roll back erp_inventory_product_master_20260711.sql from its in-database snapshots.
SET NAMES utf8mb4;
USE `BossERP_NEW`;

DROP PROCEDURE IF EXISTS rollback_product_master_20260711;
DELIMITER $$
CREATE PROCEDURE rollback_product_master_20260711()
BEGIN
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;
    IF (SELECT COUNT(*) FROM backup_inv_product_before_20260711_refresh) <> 167
       OR (SELECT COUNT(*) FROM backup_inv_product_category_before_20260711_refresh) <> 23
       OR (SELECT COUNT(*) FROM backup_inv_supplier_before_20260711_refresh) <> 87 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'rollback failed: backup row counts do not match';
    END IF;

    START TRANSACTION;
    UPDATE inv_product target
    JOIN backup_inv_product_before_20260711_refresh backup ON backup.product_id = target.product_id
    SET target.product_name = backup.product_name,
        target.product_code = backup.product_code,
        target.category_id = backup.category_id,
        target.grade = backup.grade,
        target.sku = backup.sku,
        target.spec = backup.spec,
        target.size = backup.size,
        target.unit = backup.unit,
        target.purchase_price = backup.purchase_price,
        target.sales_price = backup.sales_price,
        target.sale_price_250g = backup.sale_price_250g,
        target.sale_price_500g = backup.sale_price_500g,
        target.cost_price = backup.cost_price,
        target.safety_stock_min = backup.safety_stock_min,
        target.safety_stock_max = backup.safety_stock_max,
        target.supplier_name = backup.supplier_name,
        target.supplier_phone = backup.supplier_phone,
        target.supplier_remark = backup.supplier_remark,
        target.internal_tea_name = backup.internal_tea_name,
        target.product_description = backup.product_description,
        target.barcode = backup.barcode,
        target.image_url = backup.image_url,
        target.package_image_url = backup.package_image_url,
        target.dry_tea_image_url = backup.dry_tea_image_url,
        target.tea_soup_image_url = backup.tea_soup_image_url,
        target.leaf_bottom_image_url = backup.leaf_bottom_image_url,
        target.extra_image_url = backup.extra_image_url,
        target.shop_dept_id = backup.shop_dept_id,
        target.status = backup.status,
        target.del_flag = backup.del_flag,
        target.create_by = backup.create_by,
        target.create_time = backup.create_time,
        target.update_by = backup.update_by,
        target.update_time = backup.update_time,
        target.remark = backup.remark
    WHERE target.shop_dept_id = 100;

    UPDATE inv_product_category target
    JOIN backup_inv_product_category_before_20260711_refresh backup ON backup.category_id = target.category_id
    SET target.parent_id = backup.parent_id,
        target.ancestors = backup.ancestors,
        target.category_name = backup.category_name,
        target.category_code = backup.category_code,
        target.order_num = backup.order_num,
        target.shop_dept_id = backup.shop_dept_id,
        target.status = backup.status,
        target.del_flag = backup.del_flag,
        target.create_by = backup.create_by,
        target.create_time = backup.create_time,
        target.update_by = backup.update_by,
        target.update_time = backup.update_time,
        target.remark = backup.remark
    WHERE target.shop_dept_id = 100;

    UPDATE inv_supplier target
    JOIN backup_inv_supplier_before_20260711_refresh backup ON backup.supplier_id = target.supplier_id
    SET target.supplier_name = backup.supplier_name,
        target.supplier_code = backup.supplier_code,
        target.contact_person = backup.contact_person,
        target.contact_phone = backup.contact_phone,
        target.contact_email = backup.contact_email,
        target.address = backup.address,
        target.settlement_method = backup.settlement_method,
        target.cooperation_status = backup.cooperation_status,
        target.shop_dept_id = backup.shop_dept_id,
        target.status = backup.status,
        target.create_by = backup.create_by,
        target.create_time = backup.create_time,
        target.update_by = backup.update_by,
        target.update_time = backup.update_time,
        target.remark = backup.remark
    WHERE target.shop_dept_id = 100;
    COMMIT;
END$$
DELIMITER ;
CALL rollback_product_master_20260711();
DROP PROCEDURE rollback_product_master_20260711;
