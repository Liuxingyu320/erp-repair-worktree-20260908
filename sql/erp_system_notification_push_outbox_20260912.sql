-- Explicit opt-in outbox for NEW targeted in-app messages only.
-- Apply before setting erp.push.in-app.enabled=true. No historical data is read or inserted.
-- Rollback: disable IN_APP_MOBILE_PUSH_ENABLED first; keep this table for audit/retry evidence.
CREATE TABLE IF NOT EXISTS sys_user_notification_push_outbox (
    outbox_id bigint NOT NULL AUTO_INCREMENT COMMENT '站内消息推送任务ID',
    notification_id bigint NOT NULL COMMENT '本次新增的站内消息ID',
    payload_json text NOT NULL COMMENT '固定收件人和业务键的推送载荷',
    status varchar(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/SENDING/SENT/RETRY/SKIPPED/DEAD',
    attempt_count int NOT NULL DEFAULT 0 COMMENT '领取次数，上限12次',
    version bigint NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    next_attempt_at datetime DEFAULT NULL COMMENT '下次尝试时间',
    last_result varchar(100) DEFAULT NULL COMMENT '不含令牌和消息正文的结果码',
    created_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (outbox_id),
    UNIQUE KEY uk_notification_push_outbox_notification (notification_id),
    KEY idx_notification_push_outbox_due (status, next_attempt_at, outbox_id),
    KEY idx_notification_push_outbox_stale (status, updated_time, outbox_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='仅新增站内消息的移动推送任务';
