-- Durable replay, ownership and recovery ledger for administrator task hard-delete commands.
-- MySQL 5.7/8.x compatible. No foreign key is intentional: the command result
-- must survive deletion of every task and package named by the request.
CREATE TABLE IF NOT EXISTS oa_sign_task_hard_delete_operation (
    operation_id bigint NOT NULL AUTO_INCREMENT COMMENT '硬删除操作ID',
    request_id varchar(64) NOT NULL COMMENT '客户端幂等请求ID',
    administrator_user_id bigint NOT NULL COMMENT '发起系统管理员ID',
    payload_hash char(64) NOT NULL COMMENT '规范化删除载荷SHA-256',
    status varchar(16) NOT NULL COMMENT 'PROCESSING/RETRY/COMPLETED',
    claim_token varchar(64) DEFAULT NULL COMMENT '当前执行者所有权令牌',
    lease_expires_time datetime DEFAULT NULL COMMENT '执行租约到期时间',
    total_count int NOT NULL COMMENT '请求任务总数',
    processed_count int NOT NULL DEFAULT 0 COMMENT '已持久结果数',
    result_json json NOT NULL COMMENT '可重放的批量结果快照',
    replay_count int NOT NULL DEFAULT 0 COMMENT '同请求编号重放次数',
    last_error varchar(64) DEFAULT NULL COMMENT '最近中断类型',
    version bigint NOT NULL DEFAULT 0 COMMENT '进度CAS版本',
    created_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    completed_time datetime DEFAULT NULL COMMENT '完成时间',
    PRIMARY KEY (operation_id),
    UNIQUE KEY uk_oa_sign_task_hard_delete_request (request_id),
    KEY idx_oa_sign_task_hard_delete_lease (status, lease_expires_time),
    KEY idx_oa_sign_task_hard_delete_admin (administrator_user_id, created_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='签约任务批量硬删除持久幂等台账';
