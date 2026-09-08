-- 盘点单发起统一审批可恢复发件箱（MySQL 5.7+）
-- 仅存储业务请求快照与可观测状态，不包含种子数据。

CREATE TABLE IF NOT EXISTS `inv_stock_check_approval_start_outbox` (
  `outbox_id` bigint NOT NULL AUTO_INCREMENT COMMENT '发件箱ID',
  `check_id` bigint NOT NULL COMMENT '盘点单ID',
  `business_round` int NOT NULL COMMENT '审批轮次',
  `idempotency_key` varchar(128) NOT NULL COMMENT '审批中心幂等键',
  `request_json` mediumtext NOT NULL COMMENT '发起请求快照',
  `status` varchar(32) NOT NULL DEFAULT 'PENDING' COMMENT '处理状态',
  `retry_count` int NOT NULL DEFAULT 0 COMMENT '自动重试次数',
  `next_retry_time` datetime DEFAULT NULL COMMENT '下次重试时间',
  `last_http_status` int DEFAULT NULL COMMENT '最后远端状态码',
  `last_error_code` varchar(64) DEFAULT NULL COMMENT '最后错误码',
  `last_error_message` varchar(255) DEFAULT NULL COMMENT '脱敏错误摘要',
  `remote_instance_id` bigint DEFAULT NULL COMMENT '审批中心实例ID',
  `remote_status` varchar(32) DEFAULT NULL COMMENT '审批中心初始状态',
  `remote_business_round` int DEFAULT NULL COMMENT '审批中心实际返回业务轮次',
  `check_row_version` bigint NOT NULL COMMENT '入队时盘点单版本',
  `version` bigint NOT NULL DEFAULT 0 COMMENT '发件箱CAS版本',
  `remote_succeeded_time` datetime DEFAULT NULL COMMENT '远端成功固化时间',
  `completed_time` datetime DEFAULT NULL COMMENT '本地关联完成时间',
  `manual_replay_count` int NOT NULL DEFAULT 0 COMMENT '人工重放次数',
  `manual_replay_by` varchar(64) DEFAULT NULL COMMENT '最后人工重放人',
  `manual_replay_time` datetime DEFAULT NULL COMMENT '最后人工重放时间',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`outbox_id`),
  UNIQUE KEY `uk_stock_check_approval_round` (`check_id`, `business_round`),
  UNIQUE KEY `uk_stock_check_approval_idempotency` (`idempotency_key`),
  KEY `idx_stock_check_approval_dispatch` (`status`, `next_retry_time`, `update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='盘点统一审批发起发件箱';


SET @stock_check_remote_round_exists := (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'inv_stock_check_approval_start_outbox'
      AND column_name = 'remote_business_round'
);
SET @stock_check_remote_round_ddl := IF(
    @stock_check_remote_round_exists = 0,
    'ALTER TABLE inv_stock_check_approval_start_outbox ADD COLUMN remote_business_round int DEFAULT NULL COMMENT ''审批中心实际返回业务轮次'' AFTER remote_status',
    'SELECT 1'
);
PREPARE stock_check_remote_round_stmt FROM @stock_check_remote_round_ddl;
EXECUTE stock_check_remote_round_stmt;
DEALLOCATE PREPARE stock_check_remote_round_stmt;
