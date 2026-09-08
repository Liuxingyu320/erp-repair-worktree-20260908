-- OA fixed asset configuration and repair reporting.

CREATE TABLE IF NOT EXISTS oa_fixed_asset_config (
    config_id bigint(20) NOT NULL AUTO_INCREMENT COMMENT '配置ID',
    shop_dept_id bigint(20) NOT NULL COMMENT '店铺部门ID',
    product_id bigint(20) NOT NULL COMMENT '固定资产商品ID',
    asset_quantity decimal(16,2) NOT NULL DEFAULT 1.00 COMMENT '资产数量',
    asset_unit_price decimal(16,2) NOT NULL DEFAULT 0.00 COMMENT '资产单价',
    asset_amount decimal(16,2) NOT NULL DEFAULT 0.00 COMMENT '资产金额',
    status char(1) NOT NULL DEFAULT '0' COMMENT '状态（0正常 1停用）',
    create_by varchar(64) DEFAULT '' COMMENT '创建者',
    create_time datetime DEFAULT NULL COMMENT '创建时间',
    update_by varchar(64) DEFAULT '' COMMENT '更新者',
    update_time datetime DEFAULT NULL COMMENT '更新时间',
    remark varchar(500) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (config_id),
    KEY idx_oa_fixed_asset_config_shop (shop_dept_id, status),
    KEY idx_oa_fixed_asset_config_product (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OA固定资产配置';

CREATE TABLE IF NOT EXISTS oa_fixed_asset_quota (
    quota_id bigint(20) NOT NULL AUTO_INCREMENT COMMENT '额度ID',
    shop_dept_id bigint(20) NOT NULL COMMENT '店铺部门ID',
    quota_year int NOT NULL COMMENT '额度年份',
    annual_repair_ratio decimal(8,2) NOT NULL DEFAULT 20.00 COMMENT '年度申报比例',
    asset_total_amount decimal(16,2) NOT NULL DEFAULT 0.00 COMMENT '固定资产总金额',
    annual_quota_amount decimal(16,2) NOT NULL DEFAULT 0.00 COMMENT '年度维修额度',
    monthly_quota_amount decimal(16,2) NOT NULL DEFAULT 0.00 COMMENT '月度释放额度',
    create_by varchar(64) DEFAULT '' COMMENT '创建者',
    create_time datetime DEFAULT NULL COMMENT '创建时间',
    update_by varchar(64) DEFAULT '' COMMENT '更新者',
    update_time datetime DEFAULT NULL COMMENT '更新时间',
    remark varchar(500) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (quota_id),
    UNIQUE KEY uk_oa_fixed_asset_quota_shop_year (shop_dept_id, quota_year)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OA固定资产年度额度';

CREATE TABLE IF NOT EXISTS oa_fixed_asset_quota_month (
    month_id bigint(20) NOT NULL AUTO_INCREMENT COMMENT '月度额度ID',
    shop_dept_id bigint(20) NOT NULL COMMENT '店铺部门ID',
    quota_year int NOT NULL COMMENT '额度年份',
    quota_month int NOT NULL COMMENT '额度月份（1-12）',
    annual_repair_ratio decimal(8,2) NOT NULL DEFAULT 20.00 COMMENT '年度申报比例快照',
    asset_total_amount decimal(16,2) NOT NULL DEFAULT 0.00 COMMENT '固定资产总金额快照',
    monthly_quota_amount decimal(16,2) NOT NULL DEFAULT 0.00 COMMENT '月度释放额度快照',
    create_by varchar(64) DEFAULT '' COMMENT '创建者',
    create_time datetime DEFAULT NULL COMMENT '创建时间',
    update_by varchar(64) DEFAULT '' COMMENT '更新者',
    update_time datetime DEFAULT NULL COMMENT '更新时间',
    remark varchar(500) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (month_id),
    UNIQUE KEY uk_oa_fixed_asset_quota_month (shop_dept_id, quota_year, quota_month),
    KEY idx_oa_fixed_asset_quota_month_shop_year (shop_dept_id, quota_year)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OA固定资产月度额度快照';

CREATE TABLE IF NOT EXISTS oa_fixed_asset_repair (
    repair_id bigint(20) NOT NULL AUTO_INCREMENT COMMENT '维修上报ID',
    shop_dept_id bigint(20) NOT NULL COMMENT '店铺部门ID',
    product_id bigint(20) NOT NULL COMMENT '固定资产商品ID',
    product_name varchar(120) DEFAULT NULL COMMENT '固定资产商品名称快照',
    repair_quantity decimal(16,2) NOT NULL DEFAULT 1.00 COMMENT '坏掉数量',
    estimated_repair_amount decimal(16,2) NOT NULL DEFAULT 0.00 COMMENT '系统计算占用额度',
    available_quota_amount decimal(16,2) NOT NULL DEFAULT 0.00 COMMENT '上报时可用额度快照',
    fault_description varchar(1000) NOT NULL COMMENT '故障说明',
    image_urls varchar(1000) DEFAULT NULL COMMENT '图片或附件URL',
    exception_approved char(1) NOT NULL DEFAULT 'N' COMMENT '是否异常批准（Y是 N否）',
    exception_type varchar(32) DEFAULT NULL COMMENT '异常类型：advance_future_months/special_extra',
    advance_months int DEFAULT NULL COMMENT '透支未来月份数',
    status varchar(32) NOT NULL DEFAULT 'submitted' COMMENT '状态：draft/pending_confirm/submitted/rejected/cancelled',
    applicant_id bigint(20) DEFAULT NULL COMMENT '申请人ID',
    applicant_name varchar(64) DEFAULT NULL COMMENT '申请人账号',
    approved_by varchar(64) DEFAULT NULL COMMENT '批准人账号',
    approved_time datetime DEFAULT NULL COMMENT '批准时间',
    submitted_time datetime DEFAULT NULL COMMENT '上报时间',
    create_by varchar(64) DEFAULT '' COMMENT '创建者',
    create_time datetime DEFAULT NULL COMMENT '创建时间',
    update_by varchar(64) DEFAULT '' COMMENT '更新者',
    update_time datetime DEFAULT NULL COMMENT '更新时间',
    remark varchar(500) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (repair_id),
    KEY idx_oa_fixed_asset_repair_shop (shop_dept_id, status),
    KEY idx_oa_fixed_asset_repair_product (product_id),
    KEY idx_oa_fixed_asset_repair_exception (exception_approved, exception_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OA固定资产维修上报';

CREATE TABLE IF NOT EXISTS oa_fixed_asset_quota_ledger (
    ledger_id bigint(20) NOT NULL AUTO_INCREMENT COMMENT '流水ID',
    repair_id bigint(20) NOT NULL COMMENT '维修上报ID',
    shop_dept_id bigint(20) NOT NULL COMMENT '店铺部门ID',
    quota_year int NOT NULL COMMENT '额度年份',
    movement_type varchar(32) NOT NULL COMMENT '流水类型：normal_submit/advance_future_months/special_extra',
    amount decimal(16,2) NOT NULL DEFAULT 0.00 COMMENT '金额',
    create_by varchar(64) DEFAULT '' COMMENT '创建者',
    create_time datetime DEFAULT NULL COMMENT '创建时间',
    remark varchar(500) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (ledger_id),
    KEY idx_oa_fixed_asset_ledger_shop_year (shop_dept_id, quota_year),
    KEY idx_oa_fixed_asset_ledger_repair (repair_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OA固定资产维修额度流水';

INSERT INTO sys_menu
    (menu_id, menu_name, parent_id, order_num, path, component, query, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
VALUES
    (3310, '固定资产管理', 3000, 10, 'fixed-asset', NULL, NULL,
     'OaFixedAsset', 1, 0, 'M', '0', '0', 'oa:fixedAsset:list', 'component', 'system', NOW(), '门店固定资产配置和维修上报')
ON DUPLICATE KEY UPDATE
    menu_name = VALUES(menu_name),
    parent_id = VALUES(parent_id),
    order_num = VALUES(order_num),
    path = VALUES(path),
    component = VALUES(component),
    route_name = VALUES(route_name),
    perms = VALUES(perms),
    icon = VALUES(icon),
    update_time = NOW();

INSERT INTO sys_menu
    (menu_id, menu_name, parent_id, order_num, path, component, query, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
VALUES
    (3311, '固定资产配置', 3310, 1, 'config', 'oa/fixedAsset/config/index', NULL,
     'OaFixedAssetConfig', 1, 0, 'C', '0', '0', 'oa:fixedAsset:config:list', 'component', 'system', NOW(), '固定资产和年度额度配置'),
    (3320, '固定资产维修上报', 3310, 2, 'repair', 'oa/fixedAsset/repair/index', NULL,
     'OaFixedAssetRepair', 1, 0, 'C', '0', '0', 'oa:fixedAsset:repair:list', 'tool', 'system', NOW(), '固定资产维修上报')
ON DUPLICATE KEY UPDATE
    menu_name = VALUES(menu_name),
    parent_id = VALUES(parent_id),
    order_num = VALUES(order_num),
    path = VALUES(path),
    component = VALUES(component),
    route_name = VALUES(route_name),
    perms = VALUES(perms),
    icon = VALUES(icon),
    update_time = NOW();

INSERT INTO sys_menu
    (menu_id, menu_name, parent_id, order_num, path, component, query, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
VALUES
    (3312, '固定资产配置查询', 3311, 1, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'oa:fixedAsset:config:query', '#', 'system', NOW(), ''),
    (3313, '固定资产配置保存', 3311, 2, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'oa:fixedAsset:config:edit', '#', 'system', NOW(), ''),
    (3314, '固定资产配置删除', 3311, 3, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'oa:fixedAsset:config:delete', '#', 'system', NOW(), ''),
    (3315, '固定资产异常批准', 3311, 4, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'oa:fixedAsset:config:approve', '#', 'system', NOW(), ''),
    (3316, '固定资产配置导出', 3311, 5, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'oa:fixedAsset:config:export', '#', 'system', NOW(), ''),
    (3321, '维修上报查询', 3320, 1, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'oa:fixedAsset:repair:query', '#', 'system', NOW(), ''),
    (3322, '维修上报新增', 3320, 2, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'oa:fixedAsset:repair:add', '#', 'system', NOW(), ''),
    (3323, '维修确认上报', 3320, 3, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'oa:fixedAsset:repair:confirm', '#', 'system', NOW(), ''),
    (3324, '维修上报导出', 3320, 4, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'oa:fixedAsset:repair:export', '#', 'system', NOW(), '')
ON DUPLICATE KEY UPDATE
    menu_name = VALUES(menu_name),
    parent_id = VALUES(parent_id),
    order_num = VALUES(order_num),
    perms = VALUES(perms),
    update_time = NOW();

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
VALUES
    (1, 3310),
    (1, 3311),
    (1, 3312),
    (1, 3313),
    (1, 3314),
    (1, 3315),
    (1, 3316),
    (1, 3320),
    (1, 3321),
    (1, 3322),
    (1, 3323),
    (1, 3324);
