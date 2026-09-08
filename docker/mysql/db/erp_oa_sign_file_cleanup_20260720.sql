-- Durable, retryable cleanup ledger for files owned by hard-deleted signing tasks.
-- No foreign key is intentional: the row must survive deletion of its task/package.
CREATE TABLE IF NOT EXISTS oa_sign_file_cleanup (
    cleanup_id bigint NOT NULL AUTO_INCREMENT COMMENT '清理台账ID',
    task_id bigint NOT NULL COMMENT '已删除的签约任务ID',
    package_id bigint NOT NULL COMMENT '已删除的签约包ID',
    file_references_json json NOT NULL COMMENT '删除前校验过的受管文件引用',
    status varchar(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/PROCESSING/RETRY/COMPLETED',
    retry_count int NOT NULL DEFAULT 0 COMMENT '重试次数',
    next_retry_time datetime DEFAULT NULL COMMENT '下次重试时间',
    processing_token varchar(64) DEFAULT NULL COMMENT '当前处理器租约令牌',
    lease_expires_time datetime DEFAULT NULL COMMENT '处理租约到期时间',
    last_error varchar(128) DEFAULT NULL COMMENT '最近失败类型',
    version bigint NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    created_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (cleanup_id),
    UNIQUE KEY uk_oa_sign_file_cleanup_task_package (task_id, package_id),
    KEY idx_oa_sign_file_cleanup_due (status, next_retry_time),
    KEY idx_oa_sign_file_cleanup_lease (status, lease_expires_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='签约任务文件清理可重试台账';
