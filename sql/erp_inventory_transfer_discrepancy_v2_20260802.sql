-- ERP-NEW_2 V2 调拨收货差异事项加法结构（MySQL 5.7，可重复执行）。
-- 只供未来在 ERP-NEW_2 专属数据库 roll-forward；本文件不连接数据库、
-- 不创建确认或裁决结果、不启用 Gate、不写业务数据。

CREATE TABLE IF NOT EXISTS inv_transfer_receipt_discrepancy_case (
    discrepancy_case_id bigint NOT NULL AUTO_INCREMENT,
    receipt_id bigint NOT NULL,
    receipt_allocation_id bigint NOT NULL,
    shipment_id bigint NOT NULL,
    transfer_id bigint NOT NULL,
    shipment_allocation_id bigint NOT NULL,
    discrepancy_type varchar(16) NOT NULL
        COMMENT 'damaged/shortage',
    discrepancy_quantity decimal(18,4) NOT NULL,
    source_cost_price decimal(18,6) NOT NULL,
    discrepancy_amount decimal(18,6) NOT NULL,
    fact_fingerprint char(64)
        CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    discrepancy_note varchar(500) NOT NULL,
    attachment_refs varchar(2000) NOT NULL,
    status varchar(32) NOT NULL DEFAULT 'awaiting_confirmation',
    case_version bigint NOT NULL DEFAULT 0,
    create_by varchar(64) NOT NULL DEFAULT '',
    create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (discrepancy_case_id),
    UNIQUE KEY uk_transfer_receipt_discrepancy_dimension
        (receipt_allocation_id, discrepancy_type),
    KEY idx_transfer_receipt_discrepancy_receipt
        (receipt_id, discrepancy_case_id),
    KEY idx_transfer_receipt_discrepancy_shipment
        (shipment_id, discrepancy_case_id),
    KEY idx_transfer_receipt_discrepancy_transfer_status
        (transfer_id, status, discrepancy_case_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COMMENT='V2调拨收货不可变差异事项';
