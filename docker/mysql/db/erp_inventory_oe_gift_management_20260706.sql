-- OE utensil and gift-box catalog management.
-- Safe to re-run except the fixed-asset rename section, which must be
-- executed after confirming the backup tables were created. MySQL DDL may
-- auto-commit, so take a full database backup before applying in production.

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
    cost_price decimal(16,2) DEFAULT NULL COMMENT '参考成本价（元/盒）',
    guide_price_1 decimal(16,2) DEFAULT NULL COMMENT '指导售价1',
    guide_price_2 decimal(16,2) DEFAULT NULL COMMENT '指导售价2',
    supplier_name varchar(128) DEFAULT NULL COMMENT '供应商名称',
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
    KEY idx_inv_gift_box_supplier (supplier_name),
    KEY idx_inv_gift_box_status (status, del_flag)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='礼盒档案';

CREATE TABLE IF NOT EXISTS oa_fixed_asset_config_backup_20260706 AS
SELECT * FROM oa_fixed_asset_config;

CREATE TABLE IF NOT EXISTS oa_fixed_asset_repair_backup_20260706 AS
SELECT * FROM oa_fixed_asset_repair;

CREATE TABLE IF NOT EXISTS oa_fixed_asset_quota_backup_20260706 AS
SELECT * FROM oa_fixed_asset_quota;

CREATE TABLE IF NOT EXISTS oa_fixed_asset_quota_ledger_backup_20260706 AS
SELECT * FROM oa_fixed_asset_quota_ledger;

DELETE FROM oa_fixed_asset_quota_ledger;
DELETE FROM oa_fixed_asset_repair;
DELETE FROM oa_fixed_asset_quota;
DELETE FROM oa_fixed_asset_config;

-- If a target database has already been migrated, skip these CHANGE COLUMN
-- statements. Kept explicit for auditability:
-- alter table oa_fixed_asset_config change column product_id oe_item_id bigint(20) not null comment 'OE器皿ID';
-- alter table oa_fixed_asset_repair change column product_id oe_item_id bigint(20) not null comment 'OE器皿ID';
-- alter table oa_fixed_asset_repair change column product_name oe_item_name varchar(120) default null comment 'OE器皿名称快照';

ALTER TABLE oa_fixed_asset_config
    CHANGE COLUMN product_id oe_item_id bigint(20) NOT NULL COMMENT 'OE器皿ID';

ALTER TABLE oa_fixed_asset_repair
    CHANGE COLUMN product_id oe_item_id bigint(20) NOT NULL COMMENT 'OE器皿ID',
    CHANGE COLUMN product_name oe_item_name varchar(120) DEFAULT NULL COMMENT 'OE器皿名称快照';

DROP INDEX idx_oa_fixed_asset_config_product ON oa_fixed_asset_config;
CREATE INDEX idx_oa_fixed_asset_config_oe ON oa_fixed_asset_config (oe_item_id);

DROP INDEX idx_oa_fixed_asset_repair_product ON oa_fixed_asset_repair;
CREATE INDEX idx_oa_fixed_asset_repair_oe ON oa_fixed_asset_repair (oe_item_id);

INSERT INTO sys_menu
    (menu_id, menu_name, parent_id, order_num, path, component, query, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
VALUES
    (4520, 'OE分类', 4308, 11, 'oe-category', 'inventory/oe/category/index', NULL,
     'InventoryOeCategory', 1, 0, 'C', '0', '0', 'inv:oeCategory:list', '#', 'system', NOW(), '仓库管理OE分类维护'),
    (4525, 'OE管理', 4308, 12, 'oe', 'inventory/oe/index', NULL,
     'InventoryOe', 1, 0, 'C', '0', '0', 'inv:oe:list', '#', 'system', NOW(), '仓库管理OE器皿维护'),
    (4535, '礼盒分类', 4308, 13, 'gift-category', 'inventory/gift/category/index', NULL,
     'InventoryGiftCategory', 1, 0, 'C', '0', '0', 'inv:giftCategory:list', '#', 'system', NOW(), '仓库管理礼盒分类维护'),
    (4540, '礼盒管理', 4308, 14, 'gift', 'inventory/gift/index', NULL,
     'InventoryGift', 1, 0, 'C', '0', '0', 'inv:gift:list', '#', 'system', NOW(), '仓库管理礼盒资料维护'),
    (4550, 'OE资料查询', 4000, 96, 'oe-query', 'inventory/oe/index', '{"mode":"readonly"}',
     'InventoryOeQuery', 1, 0, 'C', '0', '0', 'inv:oe:list', '#', 'system', NOW(), '进销存OE资料只读查询'),
    (4555, '礼盒资料查询', 4000, 97, 'gift-query', 'inventory/gift/index', '{"mode":"readonly"}',
     'InventoryGiftQuery', 1, 0, 'C', '0', '0', 'inv:gift:list', '#', 'system', NOW(), '进销存礼盒资料只读查询')
ON DUPLICATE KEY UPDATE
    menu_name = VALUES(menu_name),
    parent_id = VALUES(parent_id),
    order_num = VALUES(order_num),
    path = VALUES(path),
    component = VALUES(component),
    query = VALUES(query),
    route_name = VALUES(route_name),
    is_frame = VALUES(is_frame),
    is_cache = VALUES(is_cache),
    menu_type = VALUES(menu_type),
    visible = VALUES(visible),
    status = VALUES(status),
    perms = VALUES(perms),
    icon = VALUES(icon),
    update_time = NOW();

INSERT INTO sys_menu
    (menu_id, menu_name, parent_id, order_num, path, component, query, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
VALUES
    (4521, 'OE分类树查询', 4520, 1, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'inv:oeCategory:tree', '#', 'system', NOW(), ''),
    (4522, 'OE分类详情', 4520, 2, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'inv:oeCategory:query', '#', 'system', NOW(), ''),
    (4523, 'OE分类新增', 4520, 3, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'inv:oeCategory:add', '#', 'system', NOW(), ''),
    (4524, 'OE分类编辑', 4520, 4, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'inv:oeCategory:edit', '#', 'system', NOW(), ''),
    (4526, 'OE分类删除', 4520, 5, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'inv:oeCategory:remove', '#', 'system', NOW(), ''),
    (4527, 'OE详情', 4525, 1, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'inv:oe:query', '#', 'system', NOW(), ''),
    (4528, 'OE新增', 4525, 2, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'inv:oe:add', '#', 'system', NOW(), ''),
    (4529, 'OE编辑', 4525, 3, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'inv:oe:edit', '#', 'system', NOW(), ''),
    (4530, 'OE删除', 4525, 4, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'inv:oe:remove', '#', 'system', NOW(), ''),
    (4531, 'OE导入', 4525, 5, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'inv:oe:import', '#', 'system', NOW(), ''),
    (4532, 'OE导出', 4525, 6, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'inv:oe:export', '#', 'system', NOW(), ''),
    (4533, 'OE模板', 4525, 7, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'inv:oe:template', '#', 'system', NOW(), ''),
    (4536, '礼盒分类树查询', 4535, 1, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'inv:giftCategory:tree', '#', 'system', NOW(), ''),
    (4537, '礼盒分类详情', 4535, 2, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'inv:giftCategory:query', '#', 'system', NOW(), ''),
    (4538, '礼盒分类新增', 4535, 3, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'inv:giftCategory:add', '#', 'system', NOW(), ''),
    (4539, '礼盒分类编辑', 4535, 4, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'inv:giftCategory:edit', '#', 'system', NOW(), ''),
    (4541, '礼盒分类删除', 4535, 5, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'inv:giftCategory:remove', '#', 'system', NOW(), ''),
    (4542, '礼盒详情', 4540, 1, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'inv:gift:query', '#', 'system', NOW(), ''),
    (4543, '礼盒新增', 4540, 2, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'inv:gift:add', '#', 'system', NOW(), ''),
    (4544, '礼盒编辑', 4540, 3, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'inv:gift:edit', '#', 'system', NOW(), ''),
    (4545, '礼盒删除', 4540, 4, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'inv:gift:remove', '#', 'system', NOW(), ''),
    (4546, '礼盒导入', 4540, 5, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'inv:gift:import', '#', 'system', NOW(), ''),
    (4547, '礼盒导出', 4540, 6, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'inv:gift:export', '#', 'system', NOW(), ''),
    (4548, '礼盒模板', 4540, 7, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'inv:gift:template', '#', 'system', NOW(), '')
ON DUPLICATE KEY UPDATE
    menu_name = VALUES(menu_name),
    parent_id = VALUES(parent_id),
    order_num = VALUES(order_num),
    perms = VALUES(perms),
    update_time = NOW();

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
VALUES
    (1, 4520), (1, 4521), (1, 4522), (1, 4523), (1, 4524), (1, 4526),
    (1, 4525), (1, 4527), (1, 4528), (1, 4529), (1, 4530), (1, 4531), (1, 4532), (1, 4533),
    (1, 4535), (1, 4536), (1, 4537), (1, 4538), (1, 4539), (1, 4541),
    (1, 4540), (1, 4542), (1, 4543), (1, 4544), (1, 4545), (1, 4546), (1, 4547), (1, 4548),
    (1, 4550), (1, 4555);
