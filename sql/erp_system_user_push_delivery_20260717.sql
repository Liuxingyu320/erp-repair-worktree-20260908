-- Durable mobile-push delivery ledger (MySQL 5.7, idempotent forward migration).
--
-- One row represents one recipient/channel/business event. business_key_hash keeps
-- the unique index below the MySQL 5.7 utf8mb4 index-size limit; application code
-- must compare both the original business key and payload hash after a hash hit.

CREATE TABLE IF NOT EXISTS sys_user_push_delivery (
    delivery_id bigint NOT NULL AUTO_INCREMENT COMMENT '移动推送投递ID',
    user_id bigint NOT NULL COMMENT '接收用户ID',
    channel varchar(20) NOT NULL COMMENT '投递渠道，固定MOBILE_PUSH',
    business_key varchar(180) NOT NULL COMMENT '原始业务幂等键',
    business_key_hash char(64) NOT NULL COMMENT '业务幂等键SHA-256',
    payload_hash char(64) NOT NULL COMMENT '规范化推送载荷SHA-256',
    status varchar(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/SENDING/SENT/RETRY/DEAD',
    attempt_count int NOT NULL DEFAULT 0 COMMENT '领取投递次数',
    last_result varchar(1000) DEFAULT NULL COMMENT '最后成功结果',
    last_error varchar(1000) DEFAULT NULL COMMENT '最后失败结果',
    version bigint NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    created_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (delivery_id),
    UNIQUE KEY uk_sys_user_push_delivery_business
        (user_id, channel, business_key_hash),
    KEY idx_sys_user_push_delivery_status
        (status, updated_time, delivery_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='移动推送持久幂等投递账本';
