-- ERP-NEW_2 调拨商品级预留台账（MySQL 5.7，可重复执行）。
-- 仅供 ERP-NEW_2 独立数据库未来 roll-forward；本文件不会自动连接或修改任何数据库。

CREATE TABLE IF NOT EXISTS inv_transfer_reservation (
    reservation_id bigint(20) NOT NULL AUTO_INCREMENT COMMENT '调拨预留ID',
    transfer_id bigint(20) NOT NULL COMMENT '调拨单ID',
    transfer_detail_id bigint(20) NOT NULL COMMENT '不可变调拨明细ID',
    reservation_round int NOT NULL COMMENT '审批/预留轮次',
    stock_id bigint(20) NOT NULL COMMENT '来源商品级库存ID',
    item_type varchar(32) NOT NULL COMMENT 'product/oe/gift',
    item_id bigint(20) NOT NULL COMMENT '物料ID',
    source_location_dept_id bigint(20) NOT NULL COMMENT '来源库存组织/仓库ID',
    reserved_quantity decimal(18,4) NOT NULL DEFAULT 0 COMMENT '累计预留数量',
    consumed_quantity decimal(18,4) NOT NULL DEFAULT 0 COMMENT '累计发货消耗数量',
    released_quantity decimal(18,4) NOT NULL DEFAULT 0 COMMENT '累计释放数量',
    status varchar(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/PARTIAL/CONSUMED/RELEASED/CLOSED',
    version bigint(20) NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    create_by varchar(64) NOT NULL DEFAULT '' COMMENT '创建人',
    create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by varchar(64) NOT NULL DEFAULT '' COMMENT '更新人',
    update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (reservation_id),
    UNIQUE KEY uk_inv_transfer_reservation_detail_round (transfer_id, transfer_detail_id, reservation_round),
    KEY idx_inv_transfer_reservation_transfer (transfer_id, reservation_round, stock_id, reservation_id),
    KEY idx_inv_transfer_reservation_stock (stock_id, status, reservation_id),
    CONSTRAINT chk_inv_transfer_reservation_nonnegative CHECK (
        reserved_quantity >= 0
        AND consumed_quantity >= 0
        AND released_quantity >= 0
        AND consumed_quantity + released_quantity <= reserved_quantity
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='调拨商品级库存预留归属台账';
