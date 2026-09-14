-- Local synthetic business fixture; no production data or schema claim.
-- Used after 04A and 04C fixtures. Stock money precision from schema-only
-- scripts/fixtures/repair-purchase-facts-20260909.sql (2026-09-09 local capture).
-- Movement/notice money precision from sales_delivery_transfer_consistency_20260805
-- and sales_return_item_20260909 migrations. 8.0 collation normalized for MySQL 5.7.
ALTER TABLE inv_stock MODIFY cost_price decimal(16,2), MODIFY total_cost decimal(16,2);
ALTER TABLE inv_stock_log MODIFY cost_price decimal(16,2), MODIFY cost_amount decimal(20,2);
CREATE INDEX idx_ipr_purchase ON inv_purchase_return(purchase_order_id);
CREATE INDEX idx_iprd_return ON inv_purchase_return_detail(return_id);
CREATE TABLE `inv_customer` (
  `customer_id` bigint NOT NULL AUTO_INCREMENT COMMENT '客户ID',
  `customer_name` varchar(128) NOT NULL COMMENT '客户名称',
  `customer_code` varchar(64) DEFAULT '' COMMENT '客户编码',
  `contact_person` varchar(64) DEFAULT '' COMMENT '联系人',
  `contact_phone` varchar(32) DEFAULT '' COMMENT '联系电话',
  `contact_email` varchar(64) DEFAULT '' COMMENT '电子邮箱',
  `address` varchar(256) DEFAULT '' COMMENT '地址',
  `credit_limit` decimal(16,2) DEFAULT '0.00' COMMENT '信用额度',
  `credit_used` decimal(16,2) DEFAULT '0.00' COMMENT '已用额度',
  `payment_terms` varchar(64) DEFAULT '' COMMENT '账期（如：月结30天）',
  `customer_level` varchar(32) DEFAULT '普通客户' COMMENT '客户等级（普通客户/银牌客户/金牌客户/战略客户）',
  `shop_dept_id` bigint NOT NULL COMMENT '所属店铺部门ID',
  `status` char(1) DEFAULT '0' COMMENT '状态（0正常 1停用）',
  `create_by` varchar(64) DEFAULT '',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` varchar(64) DEFAULT '',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `remark` varchar(500) DEFAULT '',
  PRIMARY KEY (`customer_id`),
  KEY `idx_icus_shop` (`shop_dept_id`),
  KEY `idx_icus_name` (`customer_name`),
  KEY `idx_icus_code` (`customer_code`),
  CONSTRAINT `fk_inv_customer_shop_dept` FOREIGN KEY (`shop_dept_id`) REFERENCES `sys_dept` (`dept_id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='客户档案表';

CREATE TABLE `inv_sales_order` (
  `order_id` bigint NOT NULL AUTO_INCREMENT COMMENT '销售单ID',
  `version` bigint NOT NULL DEFAULT 0 COMMENT '20260913 sales mutation revision',
  `order_no` varchar(64) NOT NULL COMMENT '销售单号',
  `order_title` varchar(120) DEFAULT '' COMMENT '销售标题',
  `customer_id` bigint DEFAULT NULL COMMENT '客户档案ID',
  `customer_name` varchar(128) DEFAULT '' COMMENT '客户名称',
  `total_amount` decimal(16,2) DEFAULT '0.00' COMMENT '销售总金额',
  `order_date` date DEFAULT NULL COMMENT '销售日期',
  `status` varchar(20) DEFAULT 'draft' COMMENT '状态(draft/submitted/delivered/cancelled)',
  `shop_dept_id` bigint NOT NULL COMMENT '所属店铺部门ID',
  `target_dept_id` bigint DEFAULT NULL COMMENT '跨店销售目标门店',
  `applicant_id` bigint NOT NULL COMMENT '操作人ID',
  `applicant_name` varchar(64) DEFAULT '' COMMENT '操作人账号',
  `applicant_dept_id` bigint DEFAULT NULL COMMENT '操作人部门ID',
  `create_by` varchar(64) DEFAULT '',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` varchar(64) DEFAULT '',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `remark` varchar(500) DEFAULT '',
  PRIMARY KEY (`order_id`),
  KEY `idx_isord_shop` (`shop_dept_id`),
  KEY `idx_isord_no` (`order_no`),
  KEY `idx_isord_status` (`status`),
  KEY `idx_inv_sales_order_customer` (`customer_id`),
  CONSTRAINT `fk_inv_sales_order_customer` FOREIGN KEY (`customer_id`) REFERENCES `inv_customer` (`customer_id`) ON DELETE SET NULL ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='销售订单表';

CREATE TABLE `inv_sales_return` (
  `return_id` bigint NOT NULL AUTO_INCREMENT COMMENT '退货单ID',
  `return_no` varchar(32) NOT NULL COMMENT '退货单号（SR+日期+序号）',
  `sales_order_id` bigint NOT NULL COMMENT '原销售单ID',
  `sales_order_no` varchar(32) NOT NULL COMMENT '原销售单号',
  `return_title` varchar(128) NOT NULL COMMENT '退货主题',
  `customer_name` varchar(128) NOT NULL COMMENT '客户名称',
  `total_amount` decimal(16,2) DEFAULT '0.00' COMMENT '退货总金额',
  `return_date` date DEFAULT NULL COMMENT '退货日期',
  `status` varchar(16) DEFAULT 'draft' COMMENT '状态（draft=草稿 submitted=已提交 returned=已退货 cancelled=已取消）',
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
  KEY `idx_isr_shop` (`shop_dept_id`),
  KEY `idx_isr_no` (`return_no`),
  KEY `idx_isr_sales` (`sales_order_id`),
  KEY `idx_isr_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='销售退货单主表';

CREATE TABLE `inv_sales_detail` (
  `detail_id` bigint NOT NULL AUTO_INCREMENT COMMENT '明细ID',
  `order_id` bigint NOT NULL COMMENT '销售单ID',
  `item_type` varchar(20) NOT NULL DEFAULT 'product' COMMENT '物料类型：product/gift',
  `item_id` bigint NOT NULL COMMENT '物料ID',
  `item_code` varchar(64) DEFAULT '' COMMENT '物料编码快照',
  `item_name` varchar(128) DEFAULT '' COMMENT '物料名称快照',
  `product_id` bigint DEFAULT NULL COMMENT '兼容商品ID，非商品物料为空',
  `warehouse_id` bigint DEFAULT NULL COMMENT '发货仓库ID',
  `product_name` varchar(128) DEFAULT '' COMMENT '商品名称（冗余）',
  `sku` varchar(64) DEFAULT '' COMMENT 'SKU（冗余）',
  `spec` varchar(256) DEFAULT '' COMMENT '规格（冗余）',
  `unit` varchar(32) DEFAULT '' COMMENT '单位（冗余）',
  `quantity` decimal(16,2) DEFAULT '0.00' COMMENT '销售数量',
  `unit_price` decimal(16,2) DEFAULT '0.00' COMMENT '销售单价',
  `amount` decimal(16,2) DEFAULT '0.00' COMMENT '小计金额',
  `delivered_quantity` decimal(16,2) DEFAULT '0.00' COMMENT '已出库数量',
  PRIMARY KEY (`detail_id`),
  KEY `idx_isdet_order` (`order_id`),
  KEY `idx_isdet_product` (`product_id`),
  KEY `idx_isdet_wh` (`warehouse_id`),
  KEY `idx_inv_sales_detail_item` (`item_type`,`item_id`),
  CONSTRAINT `fk_inv_sales_detail_order` FOREIGN KEY (`order_id`) REFERENCES `inv_sales_order` (`order_id`) ON DELETE CASCADE ON UPDATE RESTRICT,
  CONSTRAINT `fk_inv_sales_detail_product` FOREIGN KEY (`product_id`) REFERENCES `inv_product` (`product_id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_inv_sales_detail_warehouse` FOREIGN KEY (`warehouse_id`) REFERENCES `sys_dept` (`dept_id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='销售订单明细表';

CREATE TABLE `inv_sales_return_detail` (
  `detail_id` bigint NOT NULL AUTO_INCREMENT COMMENT '明细ID',
  `return_id` bigint NOT NULL COMMENT '退货单ID',
  `sales_detail_id` bigint DEFAULT NULL COMMENT '原销售明细ID',
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
  KEY `idx_isrd_return` (`return_id`),
  KEY `idx_isrd_product` (`product_id`),
  KEY `idx_sales_return_detail_source` (`sales_detail_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='销售退货单明细表';

ALTER TABLE inv_sales_return_detail ADD COLUMN returned_cost_amount decimal(20,2) DEFAULT NULL;
CREATE TABLE inv_delivery_notice (
 notice_id bigint PRIMARY KEY AUTO_INCREMENT, notice_no varchar(64), sales_order_id bigint,
 sales_order_no varchar(64), customer_name varchar(100), status varchar(20), shop_dept_id bigint,
 warehouse_id bigint, create_by varchar(64), create_time datetime, update_by varchar(64), update_time datetime, remark varchar(500)
) ENGINE=InnoDB;
CREATE TABLE inv_delivery_notice_detail (
 detail_id bigint PRIMARY KEY AUTO_INCREMENT, notice_id bigint, sales_detail_id bigint, warehouse_id bigint,
 item_type varchar(20), item_id bigint, item_code varchar(64), item_name varchar(200), product_id bigint,
 product_name varchar(200), notice_qty decimal(16,2), delivered_qty decimal(16,2), delivered_cost_amount decimal(20,2),
 KEY idx_notice_detail_notice(notice_id)
) ENGINE=InnoDB;
CREATE TABLE inv_outbound_record (
 outbound_id bigint PRIMARY KEY AUTO_INCREMENT, sales_order_id bigint, order_no varchar(64), item_type varchar(20), item_id bigint,
 product_id bigint, shop_dept_id bigint, quantity decimal(16,2), create_by varchar(64), create_time datetime,
 remark varchar(500), notice_id bigint, notice_detail_id bigint, cost_price decimal(16,2), cost_amount decimal(20,2)
) ENGINE=InnoDB;
-- Current sales-return item contract, from erp_inventory_sales_return_item_20260909.sql.
ALTER TABLE inv_sales_return_detail ADD COLUMN item_type varchar(20) DEFAULT 'product', ADD COLUMN item_id bigint,
 ADD COLUMN item_code varchar(64), ADD COLUMN item_name varchar(200);
