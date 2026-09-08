-- Repair database drift found after the OE/gift and sign-plan code landed.
-- This script is intentionally non-destructive:
--   * no DELETE/TRUNCATE against business tables
--   * fixed-asset product_id values are preserved as oe_item_id values
--   * legacy fixed-asset products are inserted into inv_oe_item for joins

SET @erp_db = DATABASE();

CREATE TABLE IF NOT EXISTS inv_oe_category (
    category_id bigint(20) NOT NULL AUTO_INCREMENT COMMENT '分类ID',
    parent_id bigint(20) NOT NULL DEFAULT 0 COMMENT '父分类ID',
    ancestors varchar(500) NOT NULL DEFAULT '0' COMMENT '祖级列表',
    category_name varchar(64) NOT NULL COMMENT '分类名称',
    category_code varchar(64) DEFAULT NULL COMMENT '分类编码',
    order_num int DEFAULT 0 COMMENT '显示顺序',
    status char(1) NOT NULL DEFAULT '0' COMMENT '状态（0正常 1停用）',
    del_flag char(1) NOT NULL DEFAULT '0' COMMENT '删除标志（0存在 2删除）',
    create_by varchar(64) DEFAULT '' COMMENT '创建者',
    create_time datetime DEFAULT NULL COMMENT '创建时间',
    update_by varchar(64) DEFAULT '' COMMENT '更新者',
    update_time datetime DEFAULT NULL COMMENT '更新时间',
    remark varchar(500) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (category_id),
    UNIQUE KEY uk_inv_oe_category_code (category_code),
    KEY idx_inv_oe_category_parent (parent_id),
    KEY idx_inv_oe_category_status (status, del_flag)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OE分类';

CREATE TABLE IF NOT EXISTS inv_oe_item (
    oe_item_id bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'OE器皿ID',
    oe_item_code varchar(64) DEFAULT NULL COMMENT 'OE编码',
    category_id bigint(20) DEFAULT NULL COMMENT 'OE分类ID',
    oe_type_name varchar(128) DEFAULT NULL COMMENT '产品类别名称',
    oe_item_name varchar(128) NOT NULL COMMENT '物品名称',
    item_description varchar(1000) DEFAULT NULL COMMENT '产品描述',
    order_unit varchar(32) DEFAULT NULL COMMENT '订货单位',
    cost_price decimal(16,2) DEFAULT NULL COMMENT '成本价',
    supplier_name varchar(128) DEFAULT NULL COMMENT '供应商名称',
    supplier_phone varchar(64) DEFAULT NULL COMMENT '供应商电话',
    image_url varchar(1000) DEFAULT NULL COMMENT '图片URL',
    status char(1) NOT NULL DEFAULT '0' COMMENT '状态（0正常 1停用）',
    del_flag char(1) NOT NULL DEFAULT '0' COMMENT '删除标志（0存在 2删除）',
    create_by varchar(64) DEFAULT '' COMMENT '创建者',
    create_time datetime DEFAULT NULL COMMENT '创建时间',
    update_by varchar(64) DEFAULT '' COMMENT '更新者',
    update_time datetime DEFAULT NULL COMMENT '更新时间',
    remark varchar(500) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (oe_item_id),
    UNIQUE KEY uk_inv_oe_item_code (oe_item_code),
    KEY idx_inv_oe_item_category (category_id),
    KEY idx_inv_oe_item_supplier (supplier_name),
    KEY idx_inv_oe_item_status (status, del_flag)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OE器皿档案';

CREATE TABLE IF NOT EXISTS inv_gift_category (
    category_id bigint(20) NOT NULL AUTO_INCREMENT COMMENT '分类ID',
    parent_id bigint(20) NOT NULL DEFAULT 0 COMMENT '父分类ID',
    ancestors varchar(500) NOT NULL DEFAULT '0' COMMENT '祖级列表',
    category_name varchar(64) NOT NULL COMMENT '分类名称',
    category_code varchar(64) DEFAULT NULL COMMENT '分类编码',
    order_num int DEFAULT 0 COMMENT '显示顺序',
    status char(1) NOT NULL DEFAULT '0' COMMENT '状态（0正常 1停用）',
    del_flag char(1) NOT NULL DEFAULT '0' COMMENT '删除标志（0存在 2删除）',
    create_by varchar(64) DEFAULT '' COMMENT '创建者',
    create_time datetime DEFAULT NULL COMMENT '创建时间',
    update_by varchar(64) DEFAULT '' COMMENT '更新者',
    update_time datetime DEFAULT NULL COMMENT '更新时间',
    remark varchar(500) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (category_id),
    UNIQUE KEY uk_inv_gift_category_code (category_code),
    KEY idx_inv_gift_category_parent (parent_id),
    KEY idx_inv_gift_category_status (status, del_flag)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='礼盒分类';

CREATE TABLE IF NOT EXISTS inv_gift_box (
    gift_id bigint(20) NOT NULL AUTO_INCREMENT COMMENT '礼盒ID',
    gift_code varchar(64) DEFAULT NULL COMMENT '礼盒编码',
    category_id bigint(20) DEFAULT NULL COMMENT '礼盒分类ID',
    gift_name varchar(128) NOT NULL COMMENT '产品名称',
    grade varchar(64) DEFAULT NULL COMMENT '等级',
    spec varchar(128) DEFAULT NULL COMMENT '规格',
    product_description varchar(1000) DEFAULT NULL COMMENT '产品描述',
    replenishment_unit varchar(32) DEFAULT NULL COMMENT '补货单位',
    guide_price_1 decimal(16,2) DEFAULT NULL COMMENT '指导售价1',
    guide_price_2 decimal(16,2) DEFAULT NULL COMMENT '指导售价2',
    image_url varchar(1000) DEFAULT NULL COMMENT '礼盒图片URL',
    status char(1) NOT NULL DEFAULT '0' COMMENT '状态（0正常 1停用）',
    del_flag char(1) NOT NULL DEFAULT '0' COMMENT '删除标志（0存在 2删除）',
    create_by varchar(64) DEFAULT '' COMMENT '创建者',
    create_time datetime DEFAULT NULL COMMENT '创建时间',
    update_by varchar(64) DEFAULT '' COMMENT '更新者',
    update_time datetime DEFAULT NULL COMMENT '更新时间',
    remark varchar(500) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (gift_id),
    UNIQUE KEY uk_inv_gift_box_code (gift_code),
    KEY idx_inv_gift_box_category (category_id),
    KEY idx_inv_gift_box_status (status, del_flag)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='礼盒档案';

CREATE TABLE IF NOT EXISTS oa_sign_plan (
    plan_id bigint(20) NOT NULL AUTO_INCREMENT COMMENT '签约方案ID',
    plan_name varchar(120) NOT NULL COMMENT '方案名称',
    scenario varchar(32) NOT NULL DEFAULT 'onboard' COMMENT '签约场景',
    post_name varchar(64) DEFAULT NULL COMMENT '适用岗位',
    employment_type varchar(32) DEFAULT NULL COMMENT '用工类型',
    social_type varchar(20) DEFAULT NULL COMMENT '社保口径',
    service_person_type varchar(32) DEFAULT NULL COMMENT '劳务人员类型',
    insurance_type varchar(64) DEFAULT NULL COMMENT '保险类型',
    post_level_snapshot varchar(32) DEFAULT NULL COMMENT '岗位等级快照',
    salary_version varchar(20) DEFAULT NULL COMMENT '薪酬版本',
    entry_date varchar(10) DEFAULT NULL COMMENT '入职日期',
    contract_start_date varchar(10) DEFAULT NULL COMMENT '合同开始日期',
    contract_end_date varchar(10) DEFAULT NULL COMMENT '合同结束日期',
    probation_start_date varchar(10) DEFAULT NULL COMMENT '试用期开始日期',
    probation_end_date varchar(10) DEFAULT NULL COMMENT '试用期结束日期',
    base_salary decimal(16,2) DEFAULT NULL COMMENT '基本工资',
    post_salary decimal(16,2) DEFAULT NULL COMMENT '岗位工资',
    field_allowance decimal(16,2) DEFAULT NULL COMMENT '综合驻外补贴',
    salary_total decimal(16,2) DEFAULT NULL COMMENT '工资合计',
    shop_dept_id bigint(20) DEFAULT NULL COMMENT '所属店铺/组织ID',
    status char(1) NOT NULL DEFAULT '0' COMMENT '状态（0启用 1停用）',
    sort_order int(11) DEFAULT 100 COMMENT '排序',
    create_by varchar(64) DEFAULT '' COMMENT '创建者',
    create_time datetime DEFAULT NULL COMMENT '创建时间',
    update_by varchar(64) DEFAULT '' COMMENT '更新者',
    update_time datetime DEFAULT NULL COMMENT '更新时间',
    remark varchar(500) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (plan_id),
    KEY idx_oa_sign_plan_shop (shop_dept_id, status),
    KEY idx_oa_sign_plan_post (post_name, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OA员工签约方案';

CREATE TABLE IF NOT EXISTS oa_sign_plan_template (
    id bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
    plan_id bigint(20) NOT NULL COMMENT '签约方案ID',
    template_id bigint(20) NOT NULL COMMENT '签约模板ID',
    template_type varchar(64) NOT NULL COMMENT '模板类型',
    sort_order int(11) DEFAULT 100 COMMENT '排序',
    create_time datetime DEFAULT NULL COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_oa_sign_plan_template_plan (plan_id, sort_order),
    KEY idx_oa_sign_plan_template_template (template_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OA签约方案模板';

SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_sign_package' AND COLUMN_NAME = 'source_plan_id') = 0,
    'ALTER TABLE oa_sign_package ADD COLUMN source_plan_id bigint(20) DEFAULT NULL COMMENT ''来源签约方案ID'' AFTER shop_dept_name',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_sign_package' AND COLUMN_NAME = 'source_plan_name') = 0
    AND (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_sign_package' AND COLUMN_NAME = 'source_plan_id') > 0,
    'ALTER TABLE oa_sign_package ADD COLUMN source_plan_name varchar(120) DEFAULT NULL COMMENT ''来源签约方案名称快照'' AFTER source_plan_id',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_sign_package' AND INDEX_NAME = 'idx_oa_sign_package_employee_plan_status') = 0
    AND (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_sign_package' AND COLUMN_NAME = 'source_plan_id') > 0,
    'ALTER TABLE oa_sign_package ADD INDEX idx_oa_sign_package_employee_plan_status (employee_id, source_plan_id, status, package_id)',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

DROP TEMPORARY TABLE IF EXISTS tmp_fixed_asset_legacy_refs;
CREATE TEMPORARY TABLE tmp_fixed_asset_legacy_refs (
    oe_item_id bigint(20) NOT NULL,
    oe_item_name varchar(128) DEFAULT NULL,
    PRIMARY KEY (oe_item_id)
) ENGINE=MEMORY;

SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_fixed_asset_config' AND COLUMN_NAME = 'product_id') > 0,
    'INSERT IGNORE INTO tmp_fixed_asset_legacy_refs (oe_item_id) SELECT product_id FROM oa_fixed_asset_config WHERE product_id IS NOT NULL',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_fixed_asset_config' AND COLUMN_NAME = 'oe_item_id') > 0,
    'INSERT IGNORE INTO tmp_fixed_asset_legacy_refs (oe_item_id) SELECT oe_item_id FROM oa_fixed_asset_config WHERE oe_item_id IS NOT NULL',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_fixed_asset_repair' AND COLUMN_NAME = 'product_id') > 0
    AND (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_fixed_asset_repair' AND COLUMN_NAME = 'product_name') > 0,
    'INSERT INTO tmp_fixed_asset_legacy_refs (oe_item_id, oe_item_name) SELECT product_id, MAX(NULLIF(product_name, '''')) FROM oa_fixed_asset_repair WHERE product_id IS NOT NULL GROUP BY product_id ON DUPLICATE KEY UPDATE oe_item_name = COALESCE(VALUES(oe_item_name), oe_item_name)',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_fixed_asset_repair' AND COLUMN_NAME = 'oe_item_id') > 0
    AND (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_fixed_asset_repair' AND COLUMN_NAME = 'oe_item_name') > 0,
    'INSERT INTO tmp_fixed_asset_legacy_refs (oe_item_id, oe_item_name) SELECT oe_item_id, MAX(NULLIF(oe_item_name, '''')) FROM oa_fixed_asset_repair WHERE oe_item_id IS NOT NULL GROUP BY oe_item_id ON DUPLICATE KEY UPDATE oe_item_name = COALESCE(VALUES(oe_item_name), oe_item_name)',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

INSERT IGNORE INTO inv_oe_item (
    oe_item_id, oe_item_code, category_id, oe_type_name, oe_item_name,
    item_description, order_unit, cost_price, supplier_name, supplier_phone,
    image_url, status, del_flag, create_by, create_time, update_by, update_time, remark
)
SELECT
    refs.oe_item_id,
    CONCAT('LEGACY-PRODUCT-', refs.oe_item_id),
    NULL,
    pc.category_name,
    COALESCE(NULLIF(p.product_name, ''), refs.oe_item_name, CONCAT('历史固定资产-', refs.oe_item_id)),
    p.product_description,
    p.unit,
    COALESCE(p.cost_price, p.purchase_price, 0),
    p.supplier_name,
    p.supplier_phone,
    COALESCE(p.image_url, p.package_image_url, p.dry_tea_image_url),
    COALESCE(p.status, '0'),
    COALESCE(p.del_flag, '0'),
    'migration_20260709',
    NOW(),
    'migration_20260709',
    NOW(),
    '从固定资产旧product_id迁移生成的OE档案'
FROM tmp_fixed_asset_legacy_refs refs
JOIN inv_product p ON p.product_id = refs.oe_item_id
LEFT JOIN inv_product_category pc ON pc.category_id = p.category_id;

INSERT IGNORE INTO inv_oe_item (
    oe_item_id, oe_item_code, category_id, oe_type_name, oe_item_name,
    status, del_flag, create_by, create_time, update_by, update_time, remark
)
SELECT
    refs.oe_item_id,
    CONCAT('LEGACY-FIXED-ASSET-', refs.oe_item_id),
    NULL,
    NULL,
    COALESCE(refs.oe_item_name, CONCAT('历史固定资产-', refs.oe_item_id)),
    '0',
    '0',
    'migration_20260709',
    NOW(),
    'migration_20260709',
    NOW(),
    '固定资产历史记录缺少对应商品时生成的OE占位档案'
FROM tmp_fixed_asset_legacy_refs refs
LEFT JOIN inv_product p ON p.product_id = refs.oe_item_id
WHERE p.product_id IS NULL;

SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_fixed_asset_config' AND COLUMN_NAME = 'product_id') > 0
    AND (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_fixed_asset_config' AND COLUMN_NAME = 'oe_item_id') = 0,
    'ALTER TABLE oa_fixed_asset_config CHANGE COLUMN product_id oe_item_id bigint(20) NOT NULL COMMENT ''OE器皿ID''',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_fixed_asset_repair' AND COLUMN_NAME = 'product_id') > 0
    AND (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_fixed_asset_repair' AND COLUMN_NAME = 'oe_item_id') = 0,
    'ALTER TABLE oa_fixed_asset_repair CHANGE COLUMN product_id oe_item_id bigint(20) NOT NULL COMMENT ''OE器皿ID''',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_fixed_asset_repair' AND COLUMN_NAME = 'product_name') > 0
    AND (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_fixed_asset_repair' AND COLUMN_NAME = 'oe_item_name') = 0,
    'ALTER TABLE oa_fixed_asset_repair CHANGE COLUMN product_name oe_item_name varchar(120) DEFAULT NULL COMMENT ''OE器皿名称快照''',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_fixed_asset_config' AND INDEX_NAME = 'idx_oa_fixed_asset_config_product') > 0,
    'ALTER TABLE oa_fixed_asset_config DROP INDEX idx_oa_fixed_asset_config_product',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_fixed_asset_config' AND INDEX_NAME = 'idx_oa_fixed_asset_config_oe') = 0,
    'ALTER TABLE oa_fixed_asset_config ADD INDEX idx_oa_fixed_asset_config_oe (oe_item_id)',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_fixed_asset_repair' AND INDEX_NAME = 'idx_oa_fixed_asset_repair_product') > 0,
    'ALTER TABLE oa_fixed_asset_repair DROP INDEX idx_oa_fixed_asset_repair_product',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_fixed_asset_repair' AND INDEX_NAME = 'idx_oa_fixed_asset_repair_oe') = 0,
    'ALTER TABLE oa_fixed_asset_repair ADD INDEX idx_oa_fixed_asset_repair_oe (oe_item_id)',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

INSERT INTO sys_menu
    (menu_id, menu_name, parent_id, order_num, path, component, query, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT 4700, 'OE分类', 4308, 11, 'oe-category', 'inventory/oe/category/index', NULL,
       'InventoryOeCategory', 1, 0, 'C', '0', '0', 'inv:oeCategory:list', '#', 'system', NOW(), '仓库管理OE分类维护'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 4700 OR perms = 'inv:oeCategory:list');

INSERT INTO sys_menu
    (menu_id, menu_name, parent_id, order_num, path, component, query, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT 4710, 'OE管理', 4308, 12, 'oe', 'inventory/oe/index', NULL,
       'InventoryOe', 1, 0, 'C', '0', '0', 'inv:oe:list', '#', 'system', NOW(), '仓库管理OE器皿维护'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 4710 OR perms = 'inv:oe:list');

INSERT INTO sys_menu
    (menu_id, menu_name, parent_id, order_num, path, component, query, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT 4720, '礼盒分类', 4308, 13, 'gift-category', 'inventory/gift/category/index', NULL,
       'InventoryGiftCategory', 1, 0, 'C', '0', '0', 'inv:giftCategory:list', '#', 'system', NOW(), '仓库管理礼盒分类维护'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 4720 OR perms = 'inv:giftCategory:list');

INSERT INTO sys_menu
    (menu_id, menu_name, parent_id, order_num, path, component, query, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT 4730, '礼盒管理', 4308, 14, 'gift', 'inventory/gift/index', NULL,
       'InventoryGift', 1, 0, 'C', '0', '0', 'inv:gift:list', '#', 'system', NOW(), '仓库管理礼盒资料维护'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 4730 OR perms = 'inv:gift:list');

INSERT INTO sys_menu
    (menu_id, menu_name, parent_id, order_num, path, component, query, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT 4740, 'OE资料查询', 4000, 96, 'oe-query', 'inventory/oe/index', '{"mode":"readonly"}',
       'InventoryOeQuery', 1, 0, 'C', '0', '0', 'inv:oe:list', '#', 'system', NOW(), '进销存OE资料只读查询'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 4740 OR route_name = 'InventoryOeQuery');

INSERT INTO sys_menu
    (menu_id, menu_name, parent_id, order_num, path, component, query, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT 4741, '礼盒资料查询', 4000, 97, 'gift-query', 'inventory/gift/index', '{"mode":"readonly"}',
       'InventoryGiftQuery', 1, 0, 'C', '0', '0', 'inv:gift:list', '#', 'system', NOW(), '进销存礼盒资料只读查询'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 4741 OR route_name = 'InventoryGiftQuery');

INSERT INTO sys_menu
    (menu_id, menu_name, parent_id, order_num, path, component, query, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT child.menu_id, child.menu_name, child.parent_id, child.order_num, '', NULL, NULL, '',
       1, 0, 'F', '0', '0', child.perms, '#', 'system', NOW(), ''
FROM (
    SELECT 4701 AS menu_id, 'OE分类树查询' AS menu_name, 4700 AS parent_id, 1 AS order_num, 'inv:oeCategory:tree' AS perms UNION ALL
    SELECT 4702, 'OE分类详情', 4700, 2, 'inv:oeCategory:query' UNION ALL
    SELECT 4703, 'OE分类新增', 4700, 3, 'inv:oeCategory:add' UNION ALL
    SELECT 4704, 'OE分类编辑', 4700, 4, 'inv:oeCategory:edit' UNION ALL
    SELECT 4705, 'OE分类删除', 4700, 5, 'inv:oeCategory:remove' UNION ALL
    SELECT 4711, 'OE详情', 4710, 1, 'inv:oe:query' UNION ALL
    SELECT 4712, 'OE新增', 4710, 2, 'inv:oe:add' UNION ALL
    SELECT 4713, 'OE编辑', 4710, 3, 'inv:oe:edit' UNION ALL
    SELECT 4714, 'OE删除', 4710, 4, 'inv:oe:remove' UNION ALL
    SELECT 4715, 'OE导入', 4710, 5, 'inv:oe:import' UNION ALL
    SELECT 4716, 'OE导出', 4710, 6, 'inv:oe:export' UNION ALL
    SELECT 4717, 'OE模板', 4710, 7, 'inv:oe:template' UNION ALL
    SELECT 4721, '礼盒分类树查询', 4720, 1, 'inv:giftCategory:tree' UNION ALL
    SELECT 4722, '礼盒分类详情', 4720, 2, 'inv:giftCategory:query' UNION ALL
    SELECT 4723, '礼盒分类新增', 4720, 3, 'inv:giftCategory:add' UNION ALL
    SELECT 4724, '礼盒分类编辑', 4720, 4, 'inv:giftCategory:edit' UNION ALL
    SELECT 4725, '礼盒分类删除', 4720, 5, 'inv:giftCategory:remove' UNION ALL
    SELECT 4731, '礼盒详情', 4730, 1, 'inv:gift:query' UNION ALL
    SELECT 4732, '礼盒新增', 4730, 2, 'inv:gift:add' UNION ALL
    SELECT 4733, '礼盒编辑', 4730, 3, 'inv:gift:edit' UNION ALL
    SELECT 4734, '礼盒删除', 4730, 4, 'inv:gift:remove' UNION ALL
    SELECT 4735, '礼盒导入', 4730, 5, 'inv:gift:import' UNION ALL
    SELECT 4736, '礼盒导出', 4730, 6, 'inv:gift:export' UNION ALL
    SELECT 4737, '礼盒模板', 4730, 7, 'inv:gift:template'
) child
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu existing
    WHERE existing.menu_id = child.menu_id OR existing.perms = child.perms
);

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT 1, menu_id
FROM sys_menu
WHERE perms IN (
    'inv:oeCategory:list', 'inv:oeCategory:tree', 'inv:oeCategory:query',
    'inv:oeCategory:add', 'inv:oeCategory:edit', 'inv:oeCategory:remove',
    'inv:oe:list', 'inv:oe:query', 'inv:oe:add', 'inv:oe:edit',
    'inv:oe:remove', 'inv:oe:import', 'inv:oe:export', 'inv:oe:template',
    'inv:giftCategory:list', 'inv:giftCategory:tree', 'inv:giftCategory:query',
    'inv:giftCategory:add', 'inv:giftCategory:edit', 'inv:giftCategory:remove',
    'inv:gift:list', 'inv:gift:query', 'inv:gift:add', 'inv:gift:edit',
    'inv:gift:remove', 'inv:gift:import', 'inv:gift:export', 'inv:gift:template'
);

DROP TEMPORARY TABLE IF EXISTS tmp_fixed_asset_legacy_refs;
