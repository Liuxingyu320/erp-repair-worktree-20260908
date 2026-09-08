-- HR department leader stable identity (MySQL 5.7 compatible)
-- Adds schema only. Existing leader names are intentionally not matched to employee accounts.

SET @hr_schema = DATABASE();

SET @hr_leader_id_column_exists = (
    SELECT COUNT(1)
    FROM information_schema.columns
    WHERE table_schema = @hr_schema
      AND table_name = 'sys_dept'
      AND column_name = 'leader_user_id'
);
SET @hr_ddl = IF(
    @hr_leader_id_column_exists = 0,
    'ALTER TABLE sys_dept ADD COLUMN leader_user_id bigint(20) DEFAULT NULL COMMENT ''负责人用户ID'' AFTER leader',
    'SELECT ''sys_dept.leader_user_id already exists'''
);
PREPARE hr_stmt FROM @hr_ddl;
EXECUTE hr_stmt;
DEALLOCATE PREPARE hr_stmt;

SET @hr_leader_id_index_exists = (
    SELECT COUNT(1)
    FROM information_schema.statistics
    WHERE table_schema = @hr_schema
      AND table_name = 'sys_dept'
      AND index_name = 'idx_sys_dept_leader_user_id'
);
SET @hr_ddl = IF(
    @hr_leader_id_index_exists = 0,
    'ALTER TABLE sys_dept ADD INDEX idx_sys_dept_leader_user_id (leader_user_id)',
    'SELECT ''sys_dept.idx_sys_dept_leader_user_id already exists'''
);
PREPARE hr_stmt FROM @hr_ddl;
EXECUTE hr_stmt;
DEALLOCATE PREPARE hr_stmt;

-- Read-only verification.
SELECT column_name, column_type, is_nullable, column_comment
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'sys_dept'
  AND column_name = 'leader_user_id';

SELECT index_name, column_name, non_unique
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name = 'sys_dept'
  AND index_name = 'idx_sys_dept_leader_user_id';

-- Read-only remediation queue; no automatic name-to-account guessing.
SELECT dept_id,
       dept_name,
       dept_type,
       leader,
       leader_user_id,
       CASE
           WHEN leader_user_id IS NOT NULL THEN '已绑定负责人账号'
           WHEN leader IS NULL OR TRIM(leader) = '' THEN '负责人缺失'
           ELSE '历史负责人待人工确认'
       END AS leader_identity_state
FROM sys_dept
WHERE del_flag = '0'
ORDER BY parent_id, order_num, dept_id;

