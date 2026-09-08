-- Authoritative cross-module renewal guard.
-- System reserves an employee/action; OA activates it with a task and releases it only at terminal state.

CREATE TABLE IF NOT EXISTS sys_hr_renewal_guard (
    employee_id bigint NOT NULL COMMENT '员工用户ID',
    scenario varchar(32) NOT NULL DEFAULT 'RENEWAL' COMMENT '签约场景',
    status varchar(16) NOT NULL DEFAULT 'IDLE' COMMENT 'IDLE/RESERVED/ACTIVE',
    action_id bigint DEFAULT NULL COMMENT 'System最终续签动作ID',
    task_id bigint DEFAULT NULL COMMENT 'OA签约任务ID',
    version bigint NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (employee_id, scenario),
    KEY idx_sys_hr_renewal_guard_status (status, update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='员工续签任务权威门闩';

-- Recover the earliest final action whose OA delivery is still recoverable.
-- Later unresolved outboxes remain retryable and are admitted after the canonical task reaches terminal.
INSERT IGNORE INTO sys_hr_renewal_guard (
    employee_id, scenario, status, action_id, task_id, version, create_time, update_time
)
SELECT a.employee_id, 'RENEWAL', 'RESERVED', a.action_id, NULL, 0, SYSDATE(), SYSDATE()
FROM sys_hr_lifecycle_action a
LEFT JOIN sys_hr_sign_event_outbox o
       ON o.action_id = a.action_id
      AND o.event_version = a.version
WHERE a.action_type IN ('RENEWAL_CONFIRMED', 'RENEWAL_DECLINED')
  AND (o.outbox_id IS NULL OR o.status IN ('PENDING', 'RETRY', 'SENDING', 'DEAD'))
  AND NOT EXISTS (
      SELECT 1
      FROM sys_hr_lifecycle_action older
      LEFT JOIN sys_hr_sign_event_outbox older_o
             ON older_o.action_id = older.action_id
            AND older_o.event_version = older.version
      WHERE older.employee_id = a.employee_id
        AND older.action_type IN ('RENEWAL_CONFIRMED', 'RENEWAL_DECLINED')
        AND older.action_id < a.action_id
        AND (older_o.outbox_id IS NULL
             OR older_o.status IN ('PENDING', 'RETRY', 'SENDING', 'DEAD'))
  );

-- OA is optional in some deployments. Build the backfill only when its task table exists.
SET @erp_guard_schema = DATABASE();
SELECT COUNT(*) INTO @erp_guard_oa_table_exists
FROM information_schema.tables
WHERE table_schema = @erp_guard_schema
  AND table_name = 'oa_sign_task';

SET @erp_guard_oa_backfill_sql = IF(
    @erp_guard_oa_table_exists > 0,
    'INSERT INTO sys_hr_renewal_guard (employee_id, scenario, status, action_id, task_id, version, create_time, update_time) SELECT t.employee_id, ''RENEWAL'', ''ACTIVE'', a.action_id, t.task_id, 0, SYSDATE(), SYSDATE() FROM oa_sign_task t INNER JOIN (SELECT employee_id, MIN(task_id) AS task_id FROM oa_sign_task WHERE UPPER(scenario) = ''RENEWAL'' AND status NOT IN (''SIGNED'', ''REFUSED'', ''EXPIRED'', ''CANCELLED'', ''NO_ACTION'') AND source_business_id REGEXP ''^[1-9][0-9]*$'' GROUP BY employee_id) latest ON latest.task_id = t.task_id INNER JOIN sys_hr_lifecycle_action a ON a.action_id = CAST(t.source_business_id AS UNSIGNED) AND a.employee_id = t.employee_id AND a.action_type IN (''RENEWAL_CONFIRMED'', ''RENEWAL_DECLINED'') ON DUPLICATE KEY UPDATE status = VALUES(status), action_id = VALUES(action_id), task_id = VALUES(task_id)',
    'SELECT 1'
);
PREPARE erp_guard_oa_backfill_stmt FROM @erp_guard_oa_backfill_sql;
EXECUTE erp_guard_oa_backfill_stmt;
DEALLOCATE PREPARE erp_guard_oa_backfill_stmt;
