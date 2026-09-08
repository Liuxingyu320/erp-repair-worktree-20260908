-- Add database-level status audit for core inventory documents.
-- Run this script with a MySQL client that supports DELIMITER.

CREATE TABLE IF NOT EXISTS inv_document_status_log (
  log_id bigint NOT NULL AUTO_INCREMENT COMMENT 'log id',
  document_type varchar(32) NOT NULL COMMENT 'document type',
  document_id bigint NOT NULL COMMENT 'document id',
  document_no varchar(64) DEFAULT '' COMMENT 'document number',
  shop_dept_id bigint DEFAULT NULL COMMENT 'shop dept id',
  previous_status varchar(128) DEFAULT '' COMMENT 'previous status',
  new_status varchar(128) DEFAULT '' COMMENT 'new status',
  action varchar(64) DEFAULT 'status_update' COMMENT 'audit action',
  operator_name varchar(64) DEFAULT '' COMMENT 'operator username',
  operate_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'operate time',
  remark varchar(500) DEFAULT '' COMMENT 'remark',
  PRIMARY KEY (log_id),
  KEY idx_inv_doc_status_log_doc (document_type, document_id, operate_time),
  KEY idx_inv_doc_status_log_shop (shop_dept_id, operate_time),
  KEY idx_inv_doc_status_log_action (action, operate_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='inventory document status audit log';

DROP TRIGGER IF EXISTS trg_inv_purchase_order_status_au;
DROP TRIGGER IF EXISTS trg_inv_sales_order_status_au;
DROP TRIGGER IF EXISTS trg_inv_purchase_return_status_au;
DROP TRIGGER IF EXISTS trg_inv_sales_return_status_au;
DROP TRIGGER IF EXISTS trg_inv_stock_check_status_au;
DROP TRIGGER IF EXISTS trg_inv_delivery_notice_status_au;

DELIMITER $$

CREATE TRIGGER trg_inv_purchase_order_status_au
AFTER UPDATE ON inv_purchase_order
FOR EACH ROW
BEGIN
  IF NOT (OLD.status <=> NEW.status) OR NOT (OLD.qc_status <=> NEW.qc_status) THEN
    INSERT INTO inv_document_status_log (
      document_type, document_id, document_no, shop_dept_id,
      previous_status, new_status, action, operator_name, operate_time, remark
    ) VALUES (
      'purchase_order', NEW.order_id, NEW.order_no, NEW.shop_dept_id,
      concat('status=', coalesce(OLD.status, ''), ';qc=', coalesce(OLD.qc_status, '')),
      concat('status=', coalesce(NEW.status, ''), ';qc=', coalesce(NEW.qc_status, '')),
      CASE
        WHEN NOT (OLD.status <=> NEW.status) AND NOT (OLD.qc_status <=> NEW.qc_status) THEN 'status_qc_update'
        WHEN NOT (OLD.qc_status <=> NEW.qc_status) THEN 'qc_status_update'
        ELSE 'status_update'
      END,
      coalesce(nullif(NEW.update_by, ''), nullif(NEW.create_by, ''), ''),
      CURRENT_TIMESTAMP,
      coalesce(NEW.remark, '')
    );
  END IF;
END$$

CREATE TRIGGER trg_inv_sales_order_status_au
AFTER UPDATE ON inv_sales_order
FOR EACH ROW
BEGIN
  IF NOT (OLD.status <=> NEW.status) THEN
    INSERT INTO inv_document_status_log (
      document_type, document_id, document_no, shop_dept_id,
      previous_status, new_status, action, operator_name, operate_time, remark
    ) VALUES (
      'sales_order', NEW.order_id, NEW.order_no, NEW.shop_dept_id,
      coalesce(OLD.status, ''),
      coalesce(NEW.status, ''),
      'status_update',
      coalesce(nullif(NEW.update_by, ''), nullif(NEW.create_by, ''), ''),
      CURRENT_TIMESTAMP,
      coalesce(NEW.remark, '')
    );
  END IF;
END$$

CREATE TRIGGER trg_inv_purchase_return_status_au
AFTER UPDATE ON inv_purchase_return
FOR EACH ROW
BEGIN
  IF NOT (OLD.status <=> NEW.status) THEN
    INSERT INTO inv_document_status_log (
      document_type, document_id, document_no, shop_dept_id,
      previous_status, new_status, action, operator_name, operate_time, remark
    ) VALUES (
      'purchase_return', NEW.return_id, NEW.return_no, NEW.shop_dept_id,
      coalesce(OLD.status, ''),
      coalesce(NEW.status, ''),
      'status_update',
      coalesce(nullif(NEW.update_by, ''), nullif(NEW.create_by, ''), ''),
      CURRENT_TIMESTAMP,
      coalesce(NEW.remark, '')
    );
  END IF;
END$$

CREATE TRIGGER trg_inv_sales_return_status_au
AFTER UPDATE ON inv_sales_return
FOR EACH ROW
BEGIN
  IF NOT (OLD.status <=> NEW.status) THEN
    INSERT INTO inv_document_status_log (
      document_type, document_id, document_no, shop_dept_id,
      previous_status, new_status, action, operator_name, operate_time, remark
    ) VALUES (
      'sales_return', NEW.return_id, NEW.return_no, NEW.shop_dept_id,
      coalesce(OLD.status, ''),
      coalesce(NEW.status, ''),
      'status_update',
      coalesce(nullif(NEW.update_by, ''), nullif(NEW.create_by, ''), ''),
      CURRENT_TIMESTAMP,
      coalesce(NEW.remark, '')
    );
  END IF;
END$$

CREATE TRIGGER trg_inv_stock_check_status_au
AFTER UPDATE ON inv_stock_check
FOR EACH ROW
BEGIN
  IF NOT (OLD.status <=> NEW.status) THEN
    INSERT INTO inv_document_status_log (
      document_type, document_id, document_no, shop_dept_id,
      previous_status, new_status, action, operator_name, operate_time, remark
    ) VALUES (
      'stock_check', NEW.check_id, NEW.check_no, NEW.shop_dept_id,
      coalesce(OLD.status, ''),
      coalesce(NEW.status, ''),
      'status_update',
      coalesce(nullif(NEW.update_by, ''), nullif(NEW.create_by, ''), ''),
      CURRENT_TIMESTAMP,
      coalesce(NEW.remark, '')
    );
  END IF;
END$$

CREATE TRIGGER trg_inv_delivery_notice_status_au
AFTER UPDATE ON inv_delivery_notice
FOR EACH ROW
BEGIN
  IF NOT (OLD.status <=> NEW.status) THEN
    INSERT INTO inv_document_status_log (
      document_type, document_id, document_no, shop_dept_id,
      previous_status, new_status, action, operator_name, operate_time, remark
    ) VALUES (
      'delivery_notice', NEW.notice_id, NEW.notice_no, NEW.shop_dept_id,
      coalesce(OLD.status, ''),
      coalesce(NEW.status, ''),
      'status_update',
      coalesce(nullif(NEW.update_by, ''), nullif(NEW.create_by, ''), ''),
      CURRENT_TIMESTAMP,
      coalesce(NEW.remark, '')
    );
  END IF;
END$$

DELIMITER ;
