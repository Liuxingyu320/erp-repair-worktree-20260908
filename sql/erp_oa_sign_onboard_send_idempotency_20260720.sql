-- Durable idempotency ledger for HR onboarding-data-request send commands.
-- MySQL 5.7/8.x compatible. No foreign key is intentional: request IDs remain
-- consumed even if an old import batch is later administratively removed.
CREATE TABLE IF NOT EXISTS oa_sign_onboard_send_request (
    operation_id bigint NOT NULL AUTO_INCREMENT COMMENT '发送操作ID',
    request_id varchar(64) NOT NULL COMMENT '客户端幂等请求ID',
    batch_id bigint NOT NULL COMMENT '入职签约导入批次ID',
    operator_user_id bigint NOT NULL COMMENT '发起HR用户ID',
    payload_hash char(64) NOT NULL COMMENT '规范化请求载荷SHA-256',
    claim_token varchar(64) NOT NULL COMMENT '首次执行事务令牌',
    replay_count int NOT NULL DEFAULT 0 COMMENT '后续重放次数',
    create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (operation_id),
    UNIQUE KEY uk_oa_sign_onboard_send_request (request_id),
    KEY idx_oa_sign_onboard_send_batch (batch_id, operation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='入职签约补资料发送幂等台账';
