-- ERP-NEW_2 物料发货履约策略增量表。
-- 只创建默认停用的策略结构；不写入样例、业务配置或仓库模式。

CREATE TABLE IF NOT EXISTS inv_item_fulfillment_policy (
    policy_id             bigint       NOT NULL AUTO_INCREMENT,
    item_type             varchar(20)  NOT NULL COMMENT 'product/oe/gift',
    item_id               bigint       NOT NULL,
    allocation_policy     varchar(10)  NOT NULL COMMENT 'FEFO/FIFO',
    tracking_policy       varchar(10)  NOT NULL COMMENT 'lot/serial',
    status                char(1)      NOT NULL DEFAULT '1' COMMENT '0启用 1停用',
    version               bigint       NOT NULL DEFAULT 0,
    create_by             varchar(64)  DEFAULT NULL,
    create_time           datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by             varchar(64)  DEFAULT NULL,
    update_time           datetime     DEFAULT NULL,
    remark                varchar(500) DEFAULT NULL,
    PRIMARY KEY (policy_id),
    UNIQUE KEY uk_item_fulfillment_policy (item_type, item_id),
    KEY idx_item_fulfillment_policy_status (status, allocation_policy, tracking_policy)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='物料批次分配与序列号跟踪策略';
