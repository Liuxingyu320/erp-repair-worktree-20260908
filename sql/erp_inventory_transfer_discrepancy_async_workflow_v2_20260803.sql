-- ERP-NEW_2 V2 调拨差异裁决异步子调拨唯一关系（MySQL 5.7）。
-- 只供未来在 ERP-NEW_2 专属数据库 roll-forward；本文件不连接数据库、
-- 不创建子调拨、不冻结库存、不写业务数据、不启用执行 Gate。

CREATE TABLE IF NOT EXISTS
    inv_transfer_receipt_discrepancy_adjudication_workflow_link (
    workflow_id bigint NOT NULL AUTO_INCREMENT,
    request_id varchar(128)
        CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    discrepancy_case_id bigint NOT NULL,
    case_version_before bigint NOT NULL,
    adjudication_id bigint NOT NULL,
    adjudication_action_id bigint NOT NULL,
    action_version_before bigint NOT NULL,
    discrepancy_type varchar(16) NOT NULL,
    action_type varchar(32) NOT NULL,
    effect_kind varchar(64) NOT NULL,
    workflow_type varchar(16) NOT NULL COMMENT 'reship/return',
    parent_transfer_id bigint NOT NULL,
    parent_transfer_type varchar(32) NOT NULL,
    child_transfer_id bigint NOT NULL,
    child_transfer_type varchar(32) NOT NULL,
    original_source_location_dept_id bigint NOT NULL,
    original_target_location_dept_id bigint NOT NULL,
    child_source_location_dept_id bigint NOT NULL,
    child_target_location_dept_id bigint NOT NULL,
    receipt_allocation_id bigint NOT NULL,
    shipment_allocation_id bigint NOT NULL,
    item_type varchar(32) NOT NULL,
    item_id bigint NOT NULL,
    product_id bigint DEFAULT NULL,
    tracking_policy varchar(16) NOT NULL COMMENT 'lot/serial',
    inventory_source varchar(32) NOT NULL
        COMMENT 'available_stock/quarantine_detail',
    quarantine_balance_id bigint DEFAULT NULL,
    quarantine_lot_id bigint DEFAULT NULL,
    quarantine_location_id bigint DEFAULT NULL,
    action_quantity decimal(18,4) NOT NULL,
    source_cost_price decimal(18,6) NOT NULL,
    action_amount decimal(18,6) NOT NULL,
    source_business_type varchar(64) NOT NULL,
    source_business_id bigint NOT NULL,
    effect_reference varchar(128)
        CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    dispatch_child_status varchar(32) NOT NULL,
    reservation_count int NOT NULL,
    reserved_quantity decimal(18,4) NOT NULL,
    decision_fingerprint char(64)
        CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    workflow_fingerprint char(64)
        CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    executor_user_id bigint NOT NULL,
    executor_name varchar(64) NOT NULL,
    create_by varchar(64) NOT NULL,
    create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (workflow_id),
    UNIQUE KEY uk_transfer_discrepancy_workflow_request (request_id),
    UNIQUE KEY uk_transfer_discrepancy_workflow_action
        (adjudication_action_id),
    UNIQUE KEY uk_transfer_discrepancy_workflow_child
        (child_transfer_id),
    UNIQUE KEY uk_transfer_discrepancy_workflow_reference
        (effect_reference),
    UNIQUE KEY uk_transfer_discrepancy_workflow_source
        (source_business_type, source_business_id),
    UNIQUE KEY uk_transfer_discrepancy_workflow_fingerprint
        (workflow_fingerprint),
    KEY idx_transfer_discrepancy_workflow_case
        (discrepancy_case_id, workflow_id),
    KEY idx_transfer_discrepancy_workflow_parent
        (parent_transfer_id, workflow_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COMMENT='V2调拨差异裁决动作与异步子调拨不可变唯一关系';
