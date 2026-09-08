CREATE TABLE sys_config (
    config_id bigint NOT NULL AUTO_INCREMENT,
    config_name varchar(100) NOT NULL,
    config_key varchar(100) NOT NULL,
    config_value varchar(500) NOT NULL,
    config_type char(1) NOT NULL DEFAULT 'N',
    create_by varchar(64) DEFAULT '',
    create_time datetime DEFAULT NULL,
    update_by varchar(64) DEFAULT '',
    update_time datetime DEFAULT NULL,
    remark varchar(500) DEFAULT NULL,
    PRIMARY KEY (config_id),
    UNIQUE KEY uk_sys_config_key (config_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE sys_menu (
    menu_id bigint NOT NULL,
    menu_name varchar(50) NOT NULL,
    parent_id bigint NOT NULL DEFAULT 0,
    order_num int NOT NULL DEFAULT 0,
    path varchar(200) DEFAULT '',
    component varchar(255) DEFAULT NULL,
    query varchar(255) DEFAULT NULL,
    route_name varchar(50) DEFAULT '',
    is_frame char(1) DEFAULT '1',
    is_cache char(1) DEFAULT '0',
    menu_type char(1) DEFAULT '',
    visible char(1) DEFAULT '0',
    status char(1) DEFAULT '0',
    perms varchar(100) DEFAULT NULL,
    icon varchar(100) DEFAULT '#',
    create_by varchar(64) DEFAULT '',
    create_time datetime DEFAULT NULL,
    update_by varchar(64) DEFAULT '',
    update_time datetime DEFAULT NULL,
    remark varchar(500) DEFAULT '',
    PRIMARY KEY (menu_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE sys_role (
    role_id bigint NOT NULL,
    role_name varchar(64) NOT NULL,
    role_key varchar(100) NOT NULL,
    status char(1) NOT NULL DEFAULT '0',
    del_flag char(1) NOT NULL DEFAULT '0',
    PRIMARY KEY (role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE sys_role_menu (
    role_id bigint NOT NULL,
    menu_id bigint NOT NULL,
    PRIMARY KEY (role_id, menu_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE sys_dept (
    dept_id bigint NOT NULL,
    parent_id bigint NOT NULL DEFAULT 0,
    ancestors varchar(255) DEFAULT '',
    dept_name varchar(100) NOT NULL,
    order_num int NOT NULL DEFAULT 0,
    leader varchar(20) DEFAULT NULL,
    status char(1) NOT NULL DEFAULT '0',
    dept_type varchar(32) NOT NULL DEFAULT 'DEPT',
    del_flag char(1) NOT NULL DEFAULT '0',
    PRIMARY KEY (dept_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE sys_user (
    user_id bigint NOT NULL AUTO_INCREMENT,
    dept_id bigint DEFAULT NULL,
    user_name varchar(64) NOT NULL,
    status char(1) NOT NULL DEFAULT '0',
    del_flag char(1) NOT NULL DEFAULT '0',
    update_by varchar(64) DEFAULT '',
    update_time datetime DEFAULT NULL,
    PRIMARY KEY (user_id),
    UNIQUE KEY uk_sys_user_name (user_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE sys_user_profile (
    profile_id bigint NOT NULL AUTO_INCREMENT,
    user_id bigint NOT NULL,
    employee_no varchar(64) DEFAULT NULL,
    employee_status varchar(32) DEFAULT NULL,
    entry_date date DEFAULT NULL,
    actual_regularization_date date DEFAULT NULL,
    current_position_start_date date DEFAULT NULL,
    create_by varchar(64) DEFAULT '',
    create_time datetime DEFAULT NULL,
    update_by varchar(64) DEFAULT '',
    update_time datetime DEFAULT NULL,
    PRIMARY KEY (profile_id),
    UNIQUE KEY uk_user_profile_user_id (user_id),
    UNIQUE KEY uk_user_profile_employee_no (employee_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE sys_post (
    post_id bigint NOT NULL,
    post_code varchar(64) DEFAULT NULL,
    post_name varchar(100) DEFAULT NULL,
    post_sort int DEFAULT 0,
    status char(1) DEFAULT '0',
    update_by varchar(64) DEFAULT '',
    update_time datetime DEFAULT NULL,
    PRIMARY KEY (post_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE sys_user_role (
    user_id bigint NOT NULL,
    role_id bigint NOT NULL,
    PRIMARY KEY (user_id, role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE sys_user_post (
    user_id bigint NOT NULL,
    post_id bigint NOT NULL,
    PRIMARY KEY (user_id, post_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE sys_user_shop (
    user_id bigint NOT NULL,
    dept_id bigint NOT NULL,
    PRIMARY KEY (user_id, dept_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE inv_oe_item (
    oe_item_id bigint NOT NULL AUTO_INCREMENT,
    oe_item_code varchar(64) DEFAULT NULL,
    category_id bigint DEFAULT NULL,
    oe_type_name varchar(128) DEFAULT NULL,
    oe_item_name varchar(128) NOT NULL,
    item_description varchar(1000) DEFAULT NULL,
    order_unit varchar(32) DEFAULT NULL,
    cost_price decimal(16,2) DEFAULT NULL,
    supplier_name varchar(128) DEFAULT NULL,
    supplier_phone varchar(64) DEFAULT NULL,
    image_url varchar(1000) DEFAULT NULL,
    status char(1) NOT NULL DEFAULT '0',
    del_flag char(1) NOT NULL DEFAULT '0',
    create_by varchar(64) DEFAULT '',
    create_time datetime DEFAULT NULL,
    update_by varchar(64) DEFAULT '',
    update_time datetime DEFAULT NULL,
    remark varchar(500) DEFAULT NULL,
    PRIMARY KEY (oe_item_id),
    UNIQUE KEY uk_inv_oe_item_code (oe_item_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE oa_fixed_asset_config (
    config_id bigint NOT NULL AUTO_INCREMENT,
    shop_dept_id bigint NOT NULL,
    oe_item_id bigint NOT NULL,
    asset_quantity decimal(16,2) NOT NULL DEFAULT 1.00,
    asset_unit_price decimal(16,2) NOT NULL DEFAULT 0.00,
    asset_amount decimal(16,2) NOT NULL DEFAULT 0.00,
    status char(1) NOT NULL DEFAULT '0',
    create_time datetime DEFAULT NULL,
    PRIMARY KEY (config_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE oa_fixed_asset_quota (
    quota_id bigint NOT NULL AUTO_INCREMENT,
    shop_dept_id bigint NOT NULL,
    quota_year int NOT NULL,
    annual_repair_ratio decimal(8,2) NOT NULL DEFAULT 20.00,
    asset_total_amount decimal(16,2) NOT NULL DEFAULT 0.00,
    annual_quota_amount decimal(16,2) NOT NULL DEFAULT 0.00,
    monthly_quota_amount decimal(16,2) NOT NULL DEFAULT 0.00,
    create_time datetime DEFAULT NULL,
    PRIMARY KEY (quota_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE oa_fixed_asset_repair (
    repair_id bigint NOT NULL AUTO_INCREMENT,
    shop_dept_id bigint NOT NULL,
    oe_item_id bigint NOT NULL,
    oe_item_name varchar(120) DEFAULT NULL,
    repair_quantity decimal(16,2) NOT NULL DEFAULT 1.00,
    estimated_repair_amount decimal(16,2) NOT NULL DEFAULT 0.00,
    available_quota_amount decimal(16,2) NOT NULL DEFAULT 0.00,
    fault_description varchar(1000) NOT NULL,
    attachment_refs varchar(2000) DEFAULT NULL,
    exception_approved char(1) NOT NULL DEFAULT 'N',
    exception_type varchar(32) DEFAULT NULL,
    status varchar(32) NOT NULL DEFAULT 'submitted',
    applicant_id bigint DEFAULT NULL,
    applicant_name varchar(64) DEFAULT NULL,
    submitted_time datetime DEFAULT NULL,
    create_time datetime DEFAULT NULL,
    PRIMARY KEY (repair_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE oa_purchase (
    purchase_id bigint NOT NULL AUTO_INCREMENT,
    status varchar(32) NOT NULL DEFAULT 'draft',
    create_time datetime DEFAULT CURRENT_TIMESTAMP,
    update_time datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (purchase_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE inv_transfer_order (
    transfer_id bigint NOT NULL AUTO_INCREMENT,
    order_no varchar(64) NOT NULL,
    purchase_id bigint DEFAULT NULL,
    from_dept_id bigint NOT NULL,
    from_dept_name varchar(128) DEFAULT '',
    from_warehouse_id bigint DEFAULT NULL,
    to_dept_id bigint NOT NULL,
    to_dept_name varchar(128) DEFAULT '',
    to_warehouse_id bigint DEFAULT NULL,
    approval_instance_id bigint DEFAULT NULL,
    status varchar(20) DEFAULT 'draft',
    close_reason varchar(500) DEFAULT '',
    total_quantity decimal(16,2) DEFAULT 0.00,
    transfer_type varchar(32) DEFAULT 'warehouse',
    recipient_name varchar(64) DEFAULT NULL,
    recipient_phone varchar(32) DEFAULT NULL,
    shipping_address varchar(500) DEFAULT NULL,
    create_by varchar(64) DEFAULT '',
    create_time datetime DEFAULT CURRENT_TIMESTAMP,
    submitted_time datetime DEFAULT NULL,
    update_by varchar(64) DEFAULT '',
    update_time datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    remark varchar(500) DEFAULT '',
    PRIMARY KEY (transfer_id),
    UNIQUE KEY uk_itr_order_no (order_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE inv_transfer_detail (
    detail_id bigint NOT NULL AUTO_INCREMENT,
    transfer_id bigint NOT NULL,
    product_id bigint NOT NULL,
    product_name varchar(128) DEFAULT '',
    product_code varchar(64) DEFAULT '',
    quantity decimal(16,2) DEFAULT 0.00,
    delivered_quantity decimal(16,2) DEFAULT 0.00,
    received_quantity decimal(16,2) DEFAULT 0.00,
    rejected_quantity decimal(16,2) DEFAULT 0.00,
    PRIMARY KEY (detail_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE inv_transfer_shipment (
    shipment_id bigint NOT NULL AUTO_INCREMENT,
    transfer_id bigint NOT NULL,
    shipment_no varchar(64) NOT NULL,
    warehouse_id bigint DEFAULT NULL,
    warehouse_dept_id bigint DEFAULT NULL,
    status varchar(32) DEFAULT 'pending_receive',
    create_time datetime DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (shipment_id),
    UNIQUE KEY uk_its_shipment_no (shipment_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE inv_transfer_shipment_detail (
    shipment_detail_id bigint NOT NULL AUTO_INCREMENT,
    shipment_id bigint NOT NULL,
    transfer_id bigint NOT NULL,
    transfer_detail_id bigint NOT NULL,
    product_id bigint NOT NULL,
    product_name varchar(128) DEFAULT '',
    planned_quantity decimal(16,2) DEFAULT 0.00,
    shipped_quantity decimal(16,2) DEFAULT 0.00,
    received_quantity decimal(16,2) DEFAULT 0.00,
    PRIMARY KEY (shipment_detail_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE inv_transfer_approval_rule (
    rule_id bigint NOT NULL AUTO_INCREMENT,
    rule_name varchar(128) NOT NULL,
    document_type varchar(32) NOT NULL DEFAULT 'transfer',
    transfer_type varchar(32) NOT NULL DEFAULT 'all',
    scope_type varchar(32) NOT NULL DEFAULT 'all',
    priority int NOT NULL DEFAULT 100,
    status char(1) NOT NULL DEFAULT '0',
    PRIMARY KEY (rule_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE inv_stock_check (
    check_id bigint NOT NULL AUTO_INCREMENT,
    check_no varchar(64) NOT NULL,
    status varchar(32) NOT NULL DEFAULT 'draft',
    approval_instance_id bigint DEFAULT NULL,
    approval_round int NOT NULL DEFAULT 0,
    submitted_time datetime DEFAULT NULL,
    create_time datetime DEFAULT CURRENT_TIMESTAMP,
    update_time datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (check_id),
    UNIQUE KEY uk_inv_stock_check_no (check_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE inv_customer (
    customer_id bigint NOT NULL AUTO_INCREMENT,
    customer_name varchar(128) NOT NULL,
    customer_code varchar(64) DEFAULT '',
    contact_person varchar(64) DEFAULT '',
    contact_phone varchar(32) DEFAULT '',
    contact_email varchar(64) DEFAULT '',
    address varchar(256) DEFAULT '',
    credit_limit decimal(16,2) DEFAULT 0.00,
    credit_used decimal(16,2) DEFAULT 0.00,
    payment_terms varchar(64) DEFAULT '',
    customer_level varchar(32) DEFAULT '普通客户',
    shop_dept_id bigint NOT NULL,
    status char(1) DEFAULT '0',
    create_by varchar(64) DEFAULT '',
    create_time datetime DEFAULT CURRENT_TIMESTAMP,
    update_by varchar(64) DEFAULT '',
    update_time datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    remark varchar(500) DEFAULT '',
    PRIMARY KEY (customer_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO sys_menu
    (menu_id, menu_name, parent_id, order_num, path, component, menu_type,
     visible, status, perms)
VALUES
    (100, '人事管理', 0, 1, 'hr', NULL, 'M', '0', '0', NULL),
    (101, '员工档案', 100, 1, 'employee', 'hr/employee/index', 'C', '0', '0',
     'hr:employee:list'),
    (200, '库存管理', 0, 2, 'inventory', NULL, 'M', '0', '0', NULL),
    (201, '调拨管理', 200, 1, 'transfer', 'inventory/transfer/index', 'C',
     '0', '0', 'inv:transfer:list'),
    (202, '调拨发货', 201, 1, '', NULL, 'F', '0', '0',
     'inv:transfer:deliver'),
    (203, '调拨收货', 201, 2, '', NULL, 'F', '0', '0',
     'inv:transfer:receive'),
    (204, '客户管理', 200, 2, 'customer', 'inventory/customer/index', 'C',
     '0', '0', 'inv:customer:list'),
    (205, '客户查询', 204, 1, '', NULL, 'F', '0', '0',
     'inv:customer:query'),
    (206, '客户新增', 204, 2, '', NULL, 'F', '0', '0',
     'inv:customer:add'),
    (207, '客户编辑', 204, 3, '', NULL, 'F', '0', '0',
     'inv:customer:edit'),
    (208, '客户删除', 204, 4, '', NULL, 'F', '0', '0',
     'inv:customer:remove'),
    (209, '销售管理', 200, 3, 'sales', 'inventory/sales/index', 'C',
     '0', '0', 'inv:sales:list'),
    (300, '固定资产配置', 0, 3, 'fixedAssetConfig',
     'oa/fixedAsset/config/index', 'C', '0', '0',
     'oa:fixedAsset:config:list'),
    (301, '异常批准', 300, 1, '', NULL, 'F', '0', '0',
     'oa:fixedAsset:config:approve'),
    (302, '确认异常上报', 300, 2, '', NULL, 'F', '0', '0',
     'oa:fixedAsset:repair:confirm');

INSERT INTO sys_role (role_id, role_name, role_key, status, del_flag)
VALUES
    (1, '管理员', 'admin', '0', '0'),
    (2, '店长', 'store_manager', '0', '0'),
    (3, '主管', 'area_supervisor', '0', '0'),
    (4, '仓库', 'warehouse', '0', '0'),
    (5, '茶艺师', 'tea_artist', '0', '0');

INSERT INTO sys_role_menu (role_id, menu_id)
VALUES
    (1, 101), (1, 201), (1, 202), (1, 203), (1, 204), (1, 208),
    (1, 300), (1, 301), (1, 302),
    (2, 101), (2, 204), (2, 208), (2, 209),
    (3, 101),
    (4, 201), (4, 202),
    (5, 204), (5, 209);

INSERT INTO inv_oe_item
    (oe_item_id, oe_item_code, oe_item_name, image_url, status, del_flag)
VALUES (1, 'OE-IT-001', '测试茶器', 'https://example.test/oe.png', '0', '0');

INSERT INTO oa_fixed_asset_config
    (config_id, shop_dept_id, oe_item_id, status, create_time)
VALUES (1, 9001, 1, '0', NOW());

INSERT INTO inv_transfer_approval_rule
    (rule_id, rule_name, document_type, transfer_type, scope_type, priority, status)
VALUES (1, '通用调拨审批', 'transfer', 'all', 'all', 100, '0');

INSERT INTO inv_customer
    (customer_id, customer_name, customer_code, contact_phone, shop_dept_id,
     status, create_time)
VALUES (1, '迁移测试客户', 'C-IT-001', '13800000000', 9001, '0', NOW());
