-- 调拨差异逐项处置和冻结成本台账（MySQL 5.7/8.0，可重复执行）。
-- 每一条记录对应一个 discrepancy/detail/category/requestId 处置命令；
-- 只有真实库存移动由应用写入库存流水，其他决定只写本台账。

CREATE TABLE IF NOT EXISTS inv_transfer_discrepancy_disposition (
    disposition_id bigint(20) NOT NULL AUTO_INCREMENT COMMENT '处置台账ID',
    discrepancy_id bigint(20) NOT NULL COMMENT '差异主表ID',
    discrepancy_detail_id bigint(20) NOT NULL COMMENT '差异明细ID',
    shipment_detail_id bigint(20) NOT NULL COMMENT '原发货明细ID',
    category varchar(16) NOT NULL COMMENT 'SHORTAGE/REJECTED/DAMAGED',
    decision varchar(32) NOT NULL COMMENT '处置决定',
    quantity decimal(16,2) NOT NULL COMMENT '本次处置数量',
    cost_price decimal(16,2) NOT NULL COMMENT '原发货批次冻结成本价',
    amount decimal(20,2) NOT NULL COMMENT '冻结成本金额',
    inventory_impact varchar(32) NOT NULL COMMENT '库存影响类型',
    source_location_dept_id bigint(20) DEFAULT NULL COMMENT '来源库存位置快照',
    target_location_dept_id bigint(20) DEFAULT NULL COMMENT '目标库存位置快照',
    responsible_party varchar(32) NOT NULL COMMENT '责任方快照',
    note varchar(1000) DEFAULT NULL COMMENT '逐项处置说明',
    attachment_refs varchar(2000) DEFAULT NULL COMMENT '受控附件引用',
    request_id varchar(64) NOT NULL COMMENT '客户端幂等请求ID',
    handled_by_user_id bigint(20) DEFAULT NULL COMMENT '操作人ID',
    handled_by_name varchar(64) DEFAULT NULL COMMENT '操作人姓名快照',
    handled_time datetime NOT NULL COMMENT '操作时间',
    version bigint(20) NOT NULL DEFAULT 0 COMMENT '处置版本',
    PRIMARY KEY (disposition_id),
    UNIQUE KEY uk_inv_transfer_disposition_request_category
        (discrepancy_id, request_id, discrepancy_detail_id, category),
    KEY idx_inv_transfer_disposition_detail
        (discrepancy_detail_id, category, disposition_id),
    KEY idx_inv_transfer_disposition_request
        (discrepancy_id, request_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='调拨差异逐项处置成本台账';
