-- Add unified todo threshold configuration without replacing administrator overrides.

-- Stable HR organization ownership. Ambiguous legacy rows remain null and are excluded from scoped todos.
SET @todo_profile_dept_column = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE sys_user_profile ADD COLUMN dept_id BIGINT NULL COMMENT ''档案所属组织ID'' AFTER user_id',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'sys_user_profile' AND column_name = 'dept_id'
);
PREPARE todo_profile_dept_stmt FROM @todo_profile_dept_column;
EXECUTE todo_profile_dept_stmt;
DEALLOCATE PREPARE todo_profile_dept_stmt;

SET @todo_profile_dept_index = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE sys_user_profile ADD INDEX idx_user_profile_dept (dept_id)',
        'SELECT 1')
    FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'sys_user_profile' AND index_name = 'idx_user_profile_dept'
);
PREPARE todo_profile_dept_index_stmt FROM @todo_profile_dept_index;
EXECUTE todo_profile_dept_index_stmt;
DEALLOCATE PREPARE todo_profile_dept_index_stmt;

UPDATE sys_user_profile p
INNER JOIN sys_user u ON u.user_id = p.user_id
SET p.dept_id = u.dept_id
WHERE p.dept_id IS NULL AND u.dept_id IS NOT NULL;

UPDATE sys_user_profile p
INNER JOIN (
    SELECT dept_name, MIN(dept_id) AS dept_id
    FROM sys_dept
    WHERE del_flag = '0' AND status = '0'
    GROUP BY dept_name
    HAVING COUNT(*) = 1
) unique_dept ON unique_dept.dept_name COLLATE utf8mb4_general_ci = p.store_name COLLATE utf8mb4_general_ci
SET p.dept_id = unique_dept.dept_id
WHERE p.dept_id IS NULL AND p.store_name IS NOT NULL AND p.store_name <> '';

INSERT INTO sys_config
    (config_name, config_key, config_value, config_type, create_by, create_time, remark)
SELECT
    '统一待办审批紧急阈值（小时）', 'todo.approval.urgent.hours', '24', 'Y', 'system', NOW(),
    '审批待办超过该小时数后标记为紧急'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM sys_config WHERE config_key = 'todo.approval.urgent.hours'
);

INSERT INTO sys_config
    (config_name, config_key, config_value, config_type, create_by, create_time, remark)
SELECT
    '统一待办合同预警阈值（天）', 'todo.contract.warning.days', '30', 'Y', 'system', NOW(),
    '合同到期前进入预警待办的天数'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM sys_config WHERE config_key = 'todo.contract.warning.days'
);

INSERT INTO sys_config
    (config_name, config_key, config_value, config_type, create_by, create_time, remark)
SELECT
    '统一待办合同紧急阈值（天）', 'todo.contract.urgent.days', '7', 'Y', 'system', NOW(),
    '合同到期前标记为紧急待办的天数'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM sys_config WHERE config_key = 'todo.contract.urgent.days'
);

INSERT INTO sys_config
    (config_name, config_key, config_value, config_type, create_by, create_time, remark)
SELECT
    '统一待办摘要最近条数', 'todo.summary.recent.limit', '5', 'Y', 'system', NOW(),
    '统一待办摘要返回的最近待办条数'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM sys_config WHERE config_key = 'todo.summary.recent.limit'
);
