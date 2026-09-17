-- R07 synthetic schema additions. Base tables use purchase-receive-b04a-baseline.sql.
-- Return definitions follow the 2026-09-09 local schema fixture; only collation is normalized for MySQL 5.7.
-- Lock indexes are deliberately absent so the production migration is exercised.

CREATE TABLE `inv_purchase_return` (
  version BIGINT NOT NULL DEFAULT 0,
  `return_id` bigint NOT NULL AUTO_INCREMENT COMMENT '退货单ID',
  `return_no` varchar(32) NOT NULL COMMENT '退货单号（PR+日期+序号）',
  `purchase_order_id` bigint NOT NULL COMMENT '原采购单ID',
  `purchase_order_no` varchar(32) NOT NULL COMMENT '原采购单号',
  `return_title` varchar(128) NOT NULL COMMENT '退货主题',
  `supplier_name` varchar(128) NOT NULL COMMENT '供应商名称',
  `total_amount` decimal(16,2) DEFAULT '0.00' COMMENT '退货总金额',
  `return_date` date DEFAULT NULL COMMENT '退货日期',
  `status` varchar(16) DEFAULT 'draft' COMMENT '状态（draft=草稿 submitted=已提交 returned=已退货 cancelled=已取消）',
  `return_reason` varchar(500) DEFAULT NULL COMMENT '退货原因',
  `responsibility` varchar(32) DEFAULT NULL COMMENT '责任归属',
  `attachment_urls` text COMMENT '图片及附件URL',
  `shop_dept_id` bigint NOT NULL COMMENT '所属店铺部门ID',
  `applicant_id` bigint DEFAULT NULL COMMENT '申请人ID',
  `applicant_name` varchar(64) DEFAULT '' COMMENT '申请人姓名',
  `applicant_dept_id` bigint DEFAULT NULL COMMENT '申请人部门ID',
  `create_by` varchar(64) DEFAULT '',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` varchar(64) DEFAULT '',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `remark` varchar(500) DEFAULT '',
  PRIMARY KEY (`return_id`),
  KEY `idx_ipr_shop` (`shop_dept_id`),
  KEY `idx_ipr_no` (`return_no`),
  KEY `idx_ipr_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='采购退货单主表';

CREATE TABLE `inv_purchase_return_detail` (
  `detail_id` bigint NOT NULL AUTO_INCREMENT COMMENT '明细ID',
  `return_id` bigint NOT NULL COMMENT '退货单ID',
  `purchase_detail_id` bigint DEFAULT NULL COMMENT '原采购明细ID',
  `item_type` varchar(32) DEFAULT NULL COMMENT '物料类型',
  `item_id` bigint DEFAULT NULL COMMENT '物料ID',
  `item_code` varchar(128) DEFAULT NULL COMMENT '物料编码快照',
  `item_name` varchar(255) DEFAULT NULL COMMENT '物料名称快照',
  `product_id` bigint NOT NULL COMMENT '商品ID',
  `product_name` varchar(128) NOT NULL COMMENT '商品名称',
  `sku` varchar(64) DEFAULT '' COMMENT 'SKU',
  `spec` varchar(256) DEFAULT '' COMMENT '规格型号',
  `unit` varchar(32) DEFAULT '' COMMENT '计量单位',
  `quantity` decimal(16,2) NOT NULL COMMENT '退货数量',
  `unit_price` decimal(16,2) NOT NULL COMMENT '单价',
  `amount` decimal(16,2) NOT NULL COMMENT '金额',
  `returned_quantity` decimal(16,2) DEFAULT '0.00' COMMENT '已退数量',
  PRIMARY KEY (`detail_id`),
  KEY `idx_iprd_product` (`product_id`),
  KEY `idx_purchase_return_detail_source` (`purchase_detail_id`),
  KEY `idx_purchase_return_item` (`item_type`,`item_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='采购退货单明细表';

ALTER TABLE inv_inbound_record ADD COLUMN receipt_batch_id bigint, ADD COLUMN receipt_batch_detail_id bigint, ADD COLUMN quality_inspection_id bigint, ADD COLUMN inspected_quantity decimal(18,4), ADD COLUMN accepted_quantity decimal(18,4), ADD COLUMN rejected_quantity decimal(18,4), ADD COLUMN concession_quantity decimal(18,4);
